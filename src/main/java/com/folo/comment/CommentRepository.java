package com.folo.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByTradeIdAndDeletedFalseOrderByCreatedAtAsc(Long tradeId, Pageable pageable);

    long countByTradeIdAndDeletedFalse(Long tradeId);
}
