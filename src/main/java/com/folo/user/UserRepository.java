package com.folo.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByHandle(String handle);

    Optional<User> findByIdAndActiveTrue(Long id);

    Page<User> findByNicknameContainingIgnoreCaseAndActiveTrue(String nickname, Pageable pageable);
}
