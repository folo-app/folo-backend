package com.folo.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "중복 데이터입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료되지 않았습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    DUPLICATE_HANDLE(HttpStatus.CONFLICT, "이미 사용 중인 핸들입니다."),
    DUPLICATE_FOLLOW(HttpStatus.CONFLICT, "이미 팔로우 중입니다."),
    CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "본인을 팔로우할 수 없습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    VERIFICATION_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 코드 재발송은 잠시 후 다시 시도해주세요."),
    SOCIAL_AUTH_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
    INVALID_SOCIAL_AUTH_STATE(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 로그인 상태값입니다."),
    EXPIRED_SOCIAL_AUTH_STATE(HttpStatus.BAD_REQUEST, "만료된 소셜 로그인 요청입니다."),
    INVALID_SOCIAL_AUTH_HANDOFF(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 로그인 인계 코드입니다."),
    EXPIRED_SOCIAL_AUTH_HANDOFF(HttpStatus.BAD_REQUEST, "만료된 소셜 로그인 인계 코드입니다."),
    INVALID_SOCIAL_AUTH_PENDING(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 로그인 대기 상태입니다."),
    EXPIRED_SOCIAL_AUTH_PENDING(HttpStatus.BAD_REQUEST, "만료된 소셜 로그인 대기 상태입니다."),
    SOCIAL_ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "기존 계정과 연결 확인이 필요합니다."),
    DUPLICATE_SOCIAL_IDENTITY(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 소셜 로그인입니다."),
    INSUFFICIENT_HOLDINGS(HttpStatus.BAD_REQUEST, "보유 수량이 부족합니다."),
    TRADE_NOT_VISIBLE(HttpStatus.FORBIDDEN, "해당 거래를 조회할 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
