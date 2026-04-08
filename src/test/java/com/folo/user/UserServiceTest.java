package com.folo.user;

import com.folo.auth.UserAuthIdentityRepository;
import com.folo.common.enums.CurrencyCode;
import com.folo.follow.SocialRelationService;
import com.folo.portfolio.Portfolio;
import com.folo.portfolio.PortfolioRepository;
import com.folo.security.FieldEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void getAndUpdateProfileExposePortfolioDisplayCurrency() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthIdentityRepository authIdentityRepository = mock(UserAuthIdentityRepository.class);
        SocialRelationService socialRelationService = mock(SocialRelationService.class);
        PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
        FieldEncryptor fieldEncryptor = mock(FieldEncryptor.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UserService userService = new UserService(
                userRepository,
                authIdentityRepository,
                socialRelationService,
                portfolioRepository,
                fieldEncryptor,
                passwordEncoder
        );

        User user = new User("user-1", "테스터", null);
        user.setId(1L);
        user.setActive(true);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 5, 12, 0));

        Portfolio portfolio = Portfolio.defaultOf(user);
        portfolio.setDisplayCurrency(CurrencyCode.USD);

        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserId(1L)).thenReturn(Optional.of(portfolio));
        when(socialRelationService.followerCount(1L)).thenReturn(10L);
        when(socialRelationService.followingCount(1L)).thenReturn(5L);

        MyProfileResponse profile = userService.getMe(1L);

        assertThat(profile.displayCurrency()).isEqualTo(CurrencyCode.USD);

        MyProfileResponse updated = userService.updateMe(
                1L,
                new UpdateMyProfileRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        CurrencyCode.KRW
                )
        );

        assertThat(portfolio.getDisplayCurrency()).isEqualTo(CurrencyCode.KRW);
        assertThat(updated.displayCurrency()).isEqualTo(CurrencyCode.KRW);
    }
}
