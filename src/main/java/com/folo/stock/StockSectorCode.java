package com.folo.stock;

public enum StockSectorCode {
    COMMUNICATION_SERVICES("커뮤니케이션 서비스"),
    CONGLOMERATES("복합기업"),
    CONSUMER_DISCRETIONARY("경기소비재"),
    CONSUMER_STAPLES("필수소비재"),
    ENERGY("에너지"),
    FINANCIALS("금융"),
    HEALTH_CARE("헬스케어"),
    HOLDING_COMPANIES("지주사"),
    INDUSTRIALS("산업재"),
    INFORMATION_TECHNOLOGY("정보기술"),
    MATERIALS("소재"),
    REAL_ESTATE("부동산"),
    UTILITIES("유틸리티"),
    OTHER("기타");

    private final String label;

    StockSectorCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String key() {
        return name().toLowerCase();
    }
}
