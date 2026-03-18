package com.folo.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentReminderRepository extends JpaRepository<InvestmentReminder, Long> {

    List<InvestmentReminder> findByUserIdOrderByCreatedAtDesc(Long userId);
}
