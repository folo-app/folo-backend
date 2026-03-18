package com.folo.reminder;

import com.folo.common.enums.MarketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.List;

record ReminderItem(
        Long reminderId,
        String ticker,
        String name,
        @Nullable BigDecimal amount,
        Integer dayOfMonth,
        boolean isActive,
        @Nullable String nextReminderDate
) {
}

record ReminderListResponse(
        List<ReminderItem> reminders
) {
}

record CreateReminderRequest(
        @NotBlank String ticker,
        @NotNull MarketType market,
        @Nullable @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotNull @Min(1) @Max(28) Integer dayOfMonth
) {
}

record UpdateReminderRequest(
        @Nullable @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Nullable @Min(1) @Max(28) Integer dayOfMonth,
        @Nullable Boolean isActive
) {
}
