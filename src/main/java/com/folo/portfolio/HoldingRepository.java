package com.folo.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserIdOrderByIdAsc(Long userId);

    Optional<Holding> findByUserIdAndStockSymbolId(Long userId, Long stockSymbolId);
}
