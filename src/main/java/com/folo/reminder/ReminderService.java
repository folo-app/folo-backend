package com.folo.reminder;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.stock.StockService;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ReminderService {

    private final InvestmentReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final StockService stockService;

    public ReminderService(InvestmentReminderRepository reminderRepository, UserRepository userRepository, StockService stockService) {
        this.reminderRepository = reminderRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public ReminderListResponse list(Long userId) {
        return new ReminderListResponse(reminderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(reminder -> new ReminderItem(
                        reminder.getId(),
                        reminder.getStockSymbol().getTicker(),
                        reminder.getStockSymbol().getName(),
                        reminder.getAmount(),
                        reminder.getDayOfMonth(),
                        reminder.isActive(),
                        reminder.getNextReminderDate() != null ? reminder.getNextReminderDate().toString() : null
                ))
                .toList());
    }

    @Transactional
    public ReminderItem create(Long userId, CreateReminderRequest request) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        StockSymbol stockSymbol = stockService.getRequiredSymbol(request.market(), request.ticker());

        InvestmentReminder reminder = new InvestmentReminder();
        reminder.setUser(user);
        reminder.setStockSymbol(stockSymbol);
        reminder.setAmount(request.amount());
        reminder.setDayOfMonth(request.dayOfMonth());
        reminder.setActive(true);
        reminder.setNextReminderDate(nextReminderDate(request.dayOfMonth(), true));
        reminderRepository.save(reminder);

        return toItem(reminder);
    }

    @Transactional
    public ReminderItem update(Long userId, Long reminderId, UpdateReminderRequest request) {
        InvestmentReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "리마인더를 찾을 수 없습니다."));
        if (!reminder.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        if (request.amount() != null) {
            reminder.setAmount(request.amount());
        }
        if (request.dayOfMonth() != null) {
            reminder.setDayOfMonth(request.dayOfMonth());
        }
        if (request.isActive() != null) {
            reminder.setActive(request.isActive());
        }
        reminder.setNextReminderDate(nextReminderDate(reminder.getDayOfMonth(), reminder.isActive()));
        return toItem(reminder);
    }

    @Transactional
    public void delete(Long userId, Long reminderId) {
        InvestmentReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "리마인더를 찾을 수 없습니다."));
        if (!reminder.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        reminderRepository.delete(reminder);
    }

    private ReminderItem toItem(InvestmentReminder reminder) {
        return new ReminderItem(
                reminder.getId(),
                reminder.getStockSymbol().getTicker(),
                reminder.getStockSymbol().getName(),
                reminder.getAmount(),
                reminder.getDayOfMonth(),
                reminder.isActive(),
                reminder.getNextReminderDate() != null ? reminder.getNextReminderDate().toString() : null
        );
    }

    private @Nullable LocalDate nextReminderDate(Integer dayOfMonth, boolean active) {
        if (!active) {
            return null;
        }
        LocalDate now = LocalDate.now();
        LocalDate candidate = now.withDayOfMonth(Math.min(dayOfMonth, now.lengthOfMonth()));
        if (candidate.isBefore(now)) {
            LocalDate nextMonth = now.plusMonths(1);
            return nextMonth.withDayOfMonth(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }
}
