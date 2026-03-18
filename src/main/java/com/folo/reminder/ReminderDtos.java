package com.folo.reminder;

import com.folo.common.enums.MarketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

record ReminderItem(
        Long reminderId,
        String ticker,
        String name,
        BigDecimal amount,
        Integer dayOfMonth,
        boolean isActive,
        String nextReminderDate
) {
}

record ReminderListResponse(
        List<ReminderItem> reminders
) {
}

record CreateReminderRequest(
        @NotBlank String ticker,
        @NotNull MarketType market,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotNull @Min(1) @Max(28) Integer dayOfMonth
) {
}

record UpdateReminderRequest(
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Min(1) @Max(28) Integer dayOfMonth,
        Boolean isActive
) {
}
