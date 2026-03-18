package com.folo.reaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByTradeIdAndUserId(Long tradeId, Long userId);

    List<Reaction> findByTradeId(Long tradeId);
}
