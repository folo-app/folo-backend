package com.folo.trade;

import com.folo.comment.CommentRepository;
import com.folo.common.enums.TradeSource;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import com.folo.common.exception.ApiException;
import com.folo.portfolio.PortfolioProjectionService;
import com.folo.reaction.ReactionRepository;
import com.folo.stock.StockService;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockService stockService;

    @Mock
    private PortfolioProjectionService portfolioProjectionService;

    @Mock
    private TradeAccessService tradeAccessService;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private TradeService tradeService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    void myTradesUsesDatabasePaginationForFilteredQueries() {
        Trade trade = createTrade(101L, "AAPL", TradeType.BUY, LocalDateTime.of(2025, 3, 2, 9, 30));

        when(tradeRepository.findAll(
                any(Specification.class),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(trade), PageRequest.of(1, 1), 3));
        when(reactionRepository.findByTradeId(101L)).thenReturn(List.of());
        when(commentRepository.countByTradeIdAndDeletedFalse(101L)).thenReturn(0L);

        TradeListResponse response = tradeService.myTrades(
                1L,
                "  aapl ",
                "buy",
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 3),
                1,
                1
        );

        assertEquals(3L, response.totalCount());
        assertEquals(1, response.trades().size());
        assertEquals("AAPL", response.trades().get(0).ticker());
        assertTrue(response.hasNext());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void myTradesRejectsUnsupportedTradeType() {
        ApiException exception = assertThrows(ApiException.class, () -> tradeService.myTrades(
                1L,
                null,
                "dividend",
                null,
                null,
                0,
                20
        ));

        assertEquals("지원하지 않는 tradeType 입니다.", exception.getMessage());
    }

    @Test
    void myTradesAllowsMissingFilters() {
        when(tradeRepository.findAll(
                any(Specification.class),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        TradeListResponse response = tradeService.myTrades(1L, null, null, null, null, 0, 20);

        assertEquals(0L, response.totalCount());
        assertFalse(response.hasNext());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    private Trade createTrade(Long tradeId, String ticker, TradeType tradeType, LocalDateTime tradedAt) {
        User user = new User("alphauser1", "alpha", null);
        user.setId(1L);

        StockSymbol stockSymbol = mock(StockSymbol.class);
        when(stockSymbol.getTicker()).thenReturn(ticker);
        when(stockSymbol.getName()).thenReturn("Apple Inc.");

        Trade trade = Trade.create(
                user,
                stockSymbol,
                tradeType,
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(180),
                "memo",
                TradeVisibility.PUBLIC,
                tradedAt,
                TradeSource.MANUAL
        );
        trade.setId(tradeId);
        return trade;
    }
}
