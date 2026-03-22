# FOLO Backend Research Report

## Scope

이 보고서는 현재 레포를 기준으로 다음 범위를 직접 읽고 정리한 결과다.

- `src/main/java/com/folo` 전체 패키지
- `src/main/resources` 설정 파일과 Flyway 마이그레이션
- `src/test/java` 통합 테스트
- `README.md`, `compose.yaml`, `docs/*` 문서

정리 기준 시점의 레포는 다음 성격을 가진다.

- Spring Boot 3.5.11
- Java 17 toolchain
- PostgreSQL + Redis 기반
- JWT 인증
- 거래 원본 모델 + 포트폴리오 projection
- 소셜 피드 / 댓글 / 리액션 / 알림
- Phase 2 일부 확장: reminder, CSV/OCR import, KIS key 저장, KIS sync stub

코드베이스 규모는 대략 다음과 같다.

- 메인 Java 파일: 118개
- 테스트 파일: 3개
- 최상위 애플리케이션 패키지: `auth`, `comment`, `common`, `config`, `feed`, `follow`, `importer`, `notification`, `portfolio`, `reaction`, `reminder`, `security`, `stock`, `trade`, `user`

## 1. High-Level Summary

이 프로젝트는 “투자 기록을 소셜하게 공유하는 앱”의 백엔드로 설계돼 있으며, 핵심 설계 원칙은 비교적 명확하다.

- 인증은 이메일 가입 후 인증 코드 확인을 거쳐야만 정상 JWT가 발급된다.
- `trades`가 원본 데이터다.
- `holdings`와 `portfolios`는 원본이 아니라 projection/read model이다.
- `FRIENDS_ONLY`는 단방향 팔로우가 아니라 “맞팔(mutual follow)”로 해석된다.
- 보안상 민감한 값은 DB에 평문으로 저장하지 않는다.
  - refresh token은 해시만 저장
  - KIS key는 AES-GCM으로 암호화 저장
- 삭제는 일부 엔티티에서 soft delete를 사용한다.
  - `users`, `trades`, `comments`

구조는 전형적인 Spring MVC + Service + JPA Entity 패턴이다.

- Controller: HTTP contract와 validation entrypoint
- Service: 비즈니스 규칙
- Repository: Spring Data JPA 질의
- Entity: 영속 상태 + 최소한의 도메인 메서드
- DTO: package-private record 기반 응답/요청 모델

## 2. Repository Map

### Root

- `build.gradle`
  - 의존성과 Gradle task 설정
- `compose.yaml`
  - 로컬 Postgres/Redis 기동
- `README.md`
  - 로컬 실행과 구현 범위 설명
- `docs/`
  - OpenAPI, Postman, 응답 shape 문서, 계약 차이 문서
- `src/main/resources`
  - 환경별 application 설정
  - Flyway 마이그레이션

### Source Layout

- `src/main/java/com/folo/FoloApplication.java`
  - 앱 진입점
- `src/main/java/com/folo/common`
  - 공통 응답, 예외, enum, 유틸, auditing base
- `src/main/java/com/folo/security`
  - JWT 인증 및 보안 필터 체인
- `src/main/java/com/folo/config`
  - configuration properties와 OpenAPI 설정
- 도메인 패키지
  - `auth`, `user`, `follow`
  - `stock`, `trade`, `portfolio`, `feed`
  - `comment`, `reaction`, `notification`
  - `importer`, `reminder`

## 3. Runtime and Configuration

### Application Entry

`FoloApplication`은 다음 특징을 가진다.

- `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)`
  - Spring Security 기본 유저 자동 생성 제거
- `@EnableJpaAuditing`
  - `createdAt`, `updatedAt` 자동 관리

### Profiles

#### `application.yml`

기본 설정은 다음이다.

- datasource: `jdbc:postgresql://localhost:5432/folo`
- redis: `localhost:6379`
- context path: `/api`
- Hibernate DDL: `validate`
- Flyway enabled
- JWT / email verification / field encryption / KIS stub 설정 포함

#### `application-local.yml`

최근 추가된 로컬 전용 override다.

- datasource: `localhost:55432`
- redis: `localhost:56379`

이 변경의 목적은 다른 프로젝트가 `5432`, `6379`를 쓰는 환경에서 충돌을 피하는 것이다.

#### `application-test.yml`

테스트용 설정이다.

- H2 in-memory DB를 PostgreSQL 호환 모드로 실행
- Redis는 localhost 설정이 남아 있지만, 실제 이메일 인증 store는 `test` 프로필에서 in-memory 구현이 주입된다
- JWT/field encryption secret도 test 값으로 별도 고정

### Gradle Behavior

`bootRun` task는 profile을 다음 우선순위로 선택한다.

1. JVM system property `spring.profiles.active`
2. env `SPRING_PROFILES_ACTIVE`
3. 기본값 `local`

즉 `./gradlew bootRun`만 실행해도 기본적으로 `local` profile이 활성화된다.

### Docker Compose

로컬 인프라는 두 개뿐이다.

- Postgres 16 Alpine
- Redis 7 Alpine

포트는 현재 다음과 같이 분리됐다.

- Postgres: `55432 -> 5432`
- Redis: `56379 -> 6379`

## 4. Common Layer

### API Envelope

모든 컨트롤러는 `ApiResponse<T>`를 반환한다.

- 성공
  - `success=true`
  - `data`
  - `message`
  - `timestamp`
- 실패
  - `success=false`
  - `error.code`
  - `error.message`
  - `timestamp`

이 공통 envelope은 프론트 계약의 가장 중요한 부분이다. `docs/response-shape-guide.md`도 이 기준을 설명한다.

### Error Handling

`GlobalExceptionHandler`는 다음 분류로 예외를 감싼다.

- `ApiException`
  - enum `ErrorCode` 기반 status + code + message
- validation 계열
  - `MethodArgumentNotValidException`
  - `BindException`
  - `ConstraintViolationException`
  - `IllegalArgumentException`
- `AccessDeniedException`
- 나머지 `Exception`

`ErrorCode`는 도메인에 맞춘 메시지를 일부 제공한다.

- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `DUPLICATE`
- `INVALID_CREDENTIALS`
- `EMAIL_NOT_VERIFIED`
- `INVALID_REFRESH_TOKEN`
- `EXPIRED_REFRESH_TOKEN`
- `DUPLICATE_EMAIL`
- `DUPLICATE_NICKNAME`
- `DUPLICATE_HANDLE`
- `DUPLICATE_FOLLOW`
- `CANNOT_FOLLOW_SELF`
- `INVALID_VERIFICATION_CODE`
- `VERIFICATION_RESEND_TOO_SOON`
- `INSUFFICIENT_HOLDINGS`
- `TRADE_NOT_VISIBLE`

### Base Entity

`BaseTimeEntity`는 `createdAt`, `updatedAt`을 공통으로 제공한다.

- `@CreatedDate`
- `@LastModifiedDate`

### Utilities

- `HashUtils`
  - SHA-256 hex string 생성
  - refresh token hash 저장에 사용
- `HandleGenerator`
  - nickname을 ASCII normalize 후 영숫자 핸들로 생성
  - suffix로 random UUID 일부를 붙임

## 5. Security and Authentication

### Security Filter Chain

`SecurityConfig`는 stateless JWT API 서버에 맞춰 구성돼 있다.

- CSRF 비활성화
- basic/form login 비활성화
- session stateless
- custom authentication entrypoint 사용
- `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 배치

허용된 공개 엔드포인트:

- Swagger / OpenAPI
- health endpoint
- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/email/verify`
- `POST /auth/email/confirm`
- `GET /stocks/search`
- `GET /stocks/{ticker}/price`

그 외는 인증 필요다.

주목할 점:

- `/api/...`와 `/...` 경로를 둘 다 허용 목록에 넣었는데, 서버 context path가 `/api`라서 non-`/api` 경로는 사실상 중복 성격이 강하다.

### JWT

`JwtTokenProvider`는 `jjwt`를 사용한다.

- access token / refresh token 둘 다 생성
- claim
  - `userId`
  - `type` = `access` 또는 `refresh`
  - `sub` = email
  - `iss`
  - `jti`

비밀키는 문자열을 UTF-8 bytes로 읽고, 길이가 32 미만이면 zero-pad해서 HMAC key로 만든다.

### Authentication Filter

`JwtAuthenticationFilter`는 `Authorization: Bearer ...`를 읽어 principal을 세팅한다.

- parse 성공 시 `FoloUserPrincipal(userId, email)` 저장
- parse 실패 시 context clear

중요 관찰:

- 이 필터는 token의 `type`이 `access`인지 검사하지 않는다.
- 즉 refresh token도 서명과 만료가 유효하면 일반 API 인증에 사용될 수 있다.
- 현재 `type` 검사는 `/auth/refresh` 처리 로직에서만 한다.

이건 현재 코드베이스에서 보안상 가장 중요한 세부 리스크 중 하나다.

### Current User Resolution

`SecurityUtils.currentUserId()`가 SecurityContext에서 principal을 읽는다.

- principal이 없거나 타입이 다르면 `UNAUTHORIZED`

### Authentication Entry Point

인증 실패는 HTML/redirect가 아니라 JSON envelope로 반환된다.

### Email Verification

store abstraction이 분리돼 있다.

- `EmailVerificationStore`
  - resend cooldown 체크
  - code save
  - match
  - clear
- production-ish path
  - `RedisEmailVerificationStore`
  - key prefix:
    - `auth:email:code:`
    - `auth:email:cooldown:`
- test path
  - `InMemoryEmailVerificationStore`

### Email Sender

현재 발송은 `LoggingEmailSender` 하나다.

- 실제 이메일 provider 연동이 아니라 로그에 인증 코드를 찍는다.
- 운영 전환 시 가장 먼저 교체해야 할 구성 요소다.

## 6. Auth Domain

### Data Model

#### `users`

핵심 사용자 정보와 일부 설정이 들어 있다.

- `handle`
- `nickname`
- `bio`
- `profileImageUrl`
- `portfolioVisibility`
- `returnVisibility`
- `active`
- `withdrawnAt`
- `kisAppKeyEncrypted`
- `kisAppSecretEncrypted`

기본값:

- `portfolioVisibility = FRIENDS_ONLY`
- `returnVisibility = RATE_ONLY`
- `active = true`

#### `user_auth_identities`

로그인 수단과 이메일 인증 상태를 분리했다.

- 현재 실사용 provider는 `EMAIL`
- 미래 확장 provider
  - `APPLE`
  - `GOOGLE`

#### `refresh_tokens`

refresh token 원문은 저장하지 않고 `tokenHash`만 저장한다.

- `expiresAt`
- `revokedAt`
- `deviceId`
- `deviceName`

### Auth Flow

#### Signup

`AuthService.signup`

1. 비밀번호 규칙 검증
2. 이메일 / 닉네임 중복 검사
3. `User` 생성
4. `UserAuthIdentity` 생성
5. `Portfolio.defaultOf(user)` 생성
6. `NotificationSetting.defaultOf(user)` 생성
7. 인증 코드 발송
8. token 없이 `SignupResponse` 반환

비밀번호 정규식:

- 8~100자
- 영문 포함
- 숫자 포함
- 특수문자 포함

#### Login

`AuthService.login`

- EMAIL identity 조회
- bcrypt password 검증
- 탈퇴 계정 체크
- email verified 체크
- 통과 시 access/refresh token 발급

#### Confirm Email

`AuthService.confirmEmail`

- email identity 조회
- verification store와 code match
- `identity.verifyEmail()`
- store clear
- token 발급

즉 `email/confirm`이 정상 JWT 최초 발급 지점이다.

#### Refresh

`AuthService.refresh`

1. JWT claim의 `type`이 `refresh`인지 검사
2. refresh token 원문 SHA-256 해시
3. DB에서 hash로 토큰 조회
4. revoked / expired 검사
5. 기존 refresh token revoke
6. 새 access/refresh token 발급

즉 refresh rotation이 구현돼 있다.

#### Logout

- request body에 refresh token 필요
- 현재 로그인 사용자 소유인지 확인 후 revoke

#### Withdraw

- `User.active = false`
- `withdrawnAt = now`
- 해당 유저의 revoke되지 않은 refresh token 전부 revoke

현재는 soft delete 수준이며 실제 물리 삭제 배치나 30일 뒤 제거 로직은 없다.

## 7. User and Follow Domain

### User API

- `GET /users/me`
- `PATCH /users/me`
- `GET /users/{userId}`
- `GET /users/search`
- `PATCH /users/me/kis-key`

### UserService Responsibilities

- 내 프로필 조회/수정
- KIS key 저장
- 공개 프로필 조회
- 유저 검색

### Visibility Semantics

`getProfile`은 target user의 `portfolioVisibility`를 기준으로 접근 가능 여부를 계산한다.

- `PUBLIC`
  - 누구나
- `FRIENDS_ONLY`
  - mutual follow
- `PRIVATE`
  - 본인만

응답은 프로필 자체를 막지 않고, `isAccessible`로 접근 가능성을 알려준다.

### Search

- nickname 기준 부분 검색
- 2글자 이상 필요
- followerCount와 isFollowing 포함

### KIS Keys

`PATCH /users/me/kis-key`

- app key / secret 모두 필수
- `FieldEncryptor`를 사용해 암호화 저장

### Follow Domain

`Follow`는 매우 단순한 directed graph다.

- `follower -> following`
- self follow 금지
- pair unique

`SocialRelationService`는 공통 헬퍼다.

- `isFollowing`
- `isMutualFollow`
- `followerCount`
- `followingCount`

`FRIENDS_ONLY` 의미는 이 서비스를 통해 대부분의 도메인에서 일관되게 구현된다.

### Follow Flow

#### Follow

`FollowService.follow`

- self follow 금지
- duplicate follow 금지
- 양쪽 user 존재 확인
- 관계 저장
- follow notification 생성

#### Unfollow

- 관계를 찾아 delete

#### Follower/Following Lists

- page/size 기반
- follower 목록에는 current user가 그 사람을 다시 follow 중인지 포함
- following 목록은 현재 모두 `isFollowing=true`

## 8. Stock Domain

### Tables

- `stock_symbols`
- `price_snapshots`

V1 migration에서 seed 데이터가 들어간다.

- KRX
  - `005930` 삼성전자
  - `000660` SK하이닉스
- NASDAQ
  - `AAPL`
  - `NVDA`
- NYSE
  - `VOO`

각 종목마다 `price_snapshots`도 초기 삽입된다.

### Service Behavior

#### Search

- query 2글자 이상 필요
- market = `ALL`이면 이름/티커 통합 검색
- 특정 market이면 market + 이름/티커 검색
- 응답에 현재가와 당일 수익률 포함
- snapshot이 없으면 `BigDecimal.ZERO` fallback

#### Price Detail

- `market + ticker`로 종목 조회
- 해당 종목의 snapshot을 필수로 요구

현재 이 패키지는 “master data + cached snapshot 조회기” 성격이다. 외부 시세 수집기는 아직 없다.

## 9. Trade Domain

### Trade Entity

`Trade`는 실제 원본 ledger 역할을 한다.

필드:

- user
- stockSymbol
- `tradeType`
- quantity
- price
- totalAmount
- comment
- visibility
- tradedAt
- source
- deleted / deletedAt

`source` enum:

- `MANUAL`
- `CSV_IMPORT`
- `OCR_IMPORT`
- `INITIAL_SETUP`
- `KIS_SYNC`
- `HOLDING_ADJUSTMENT`

현재 실제 쓰는 값은 `MANUAL`, `CSV_IMPORT`, `OCR_IMPORT`, `KIS_SYNC`다.

### Core Rules

#### Create Trade

`TradeService.create`

- user 존재 확인
- stock symbol 조회
- SELL이면 현재 보유 수량 선검사
- `Trade.create(...)`
- 저장 후 `PortfolioProjectionService.recalculate(userId)`

#### Detail

- deleted=false trade만 대상
- `TradeAccessService.canView` 검사
- reaction 집계
- comment count 포함

#### Update

현재 수정 가능한 것은 “표현 계층”뿐이다.

- comment
- visibility

수량/가격/시점 수정은 아직 허용하지 않는다.

#### Delete

- owner check
- soft delete
- portfolio projection 재계산

### Trade Access

`TradeAccessService`는 trade visibility의 중심이다.

- deleted trade는 항상 false
- owner는 항상 허용
- `PUBLIC` 공개
- `FRIENDS_ONLY`는 mutual follow
- `PRIVATE`는 owner 외 불가

`canInteract`도 현재는 `canView`와 동일하다.

즉 댓글과 리액션은 “조회 가능하면 상호작용 가능”이라는 규칙을 따른다.

### Trade Listing Caveat

`myTrades`는 중요한 구현 세부사항이 있다.

- DB에서 먼저 `PageRequest(page, size + 1)`로 일부만 읽음
- 그 뒤 ticker/type/date filter를 메모리에서 적용

따라서 필터가 있는 경우:

- `totalCount`가 전체 필터 결과 수가 아닐 수 있다
- `hasNext`도 정확하지 않을 수 있다

현재 레포의 실제 기능 버그 후보 중 하나다.

## 10. Portfolio and Holdings Projection

### Intent

포트폴리오 계층은 명확하게 read model이다.

- `Trade`가 진실의 원천
- `Holding`은 종목별 집계 결과
- `Portfolio`는 유저 전체 집계 결과

### Projection Algorithm

`PortfolioProjectionService.recalculate(userId)`는 다음 순서로 동작한다.

1. 유저 확인
2. 삭제되지 않은 모든 trade를 시간순(`tradedAt`, `id`)으로 조회
3. 종목별 aggregate 생성
4. 기존 holding 중 더 이상 수량이 없는 종목은 삭제
5. 각 active holding에 대해
   - 현재가 조회
   - total value 계산
   - return amount / rate 계산
   - holding 저장
6. 전체 holding을 다시 읽어 weight 계산
7. portfolio total invested/value/return/day return 갱신
8. `syncedAt = now`

### Aggregate Logic

BUY:

- `totalInvested += quantity * price`
- `quantity += quantity`
- 첫 매수일 기록

SELL:

- 현재 quantity보다 많이 팔면 `INSUFFICIENT_HOLDINGS`
- 평균단가 기준으로 `totalInvested` 감소
- quantity 감소
- quantity가 0이면 totalInvested를 0으로 정리

이 방식은 realized PnL ledger를 별도로 두지 않고, 현재 보유분의 원가 기준만 관리하는 구조다.

### Portfolio Visibility

`PortfolioService.userPortfolio`

- `PortfolioVisibility`로 접근을 막음
- 접근 가능 시에도 `ReturnVisibility`에 따라 보여주는 필드가 달라진다

#### Return Visibility

- owner view
  - 전부 공개
- `RATE_AND_AMOUNT`
  - 금액과 비율 모두 공개
- `RATE_ONLY`
  - 절대값은 숨기고 수익률만 공개
- `PRIVATE`
  - owner 아니면 접근 자체를 막는 것은 아니고, 코드상 `fullyVisible=false`로 금액 비노출

현재 구현상 `RATE_ONLY`일 때는 다음이 숨겨진다.

- quantity
- avgPrice
- totalInvested
- totalValue
- returnAmount

하지만 다음은 남는다.

- currentPrice
- returnRate
- weight

즉 포지션 비중과 종목 구성은 여전히 노출된다.

## 11. Feed Domain

### Feed Semantics

#### `/feed`

- current user가 follow 중인 사람들의 trade를 가져온다
- 즉 “내가 팔로우한 사람들의 feed”다

#### `/feed/{userId}`

- 특정 유저의 trade feed

### Visibility Enforcement

양쪽 모두 `TradeAccessService.visibleOnFeed`를 사용한다.

- `PRIVATE`는 무조건 feed 제외
- 나머지는 `canView`와 동일

즉 친구 피드라고 해도 `FRIENDS_ONLY` trade는 맞팔인 경우에만 뜬다.

### Cursor Pagination

cursor는 trade id 기반이다.

- 첫 페이지: 최신순
- 다음 페이지: `id < cursor`

응답:

- `trades`
- `nextCursor`
- `hasNext`

### Feed Item Composition

각 item에 포함되는 것:

- 작성자 기본 정보
- 종목/시장/거래 타입
- 수량/가격
- comment
- grouped reactions
- comment count
- tradedAt

## 12. Comment, Reaction, Notification

### Comments

#### Comment Rules

- trade가 보이는 경우에만 댓글 조회 가능
- trade에 상호작용 가능한 경우에만 댓글 생성 가능
- 삭제는 owner만 가능
- 삭제는 soft delete

#### Comment Ordering

- `createdAt ASC`

### Reactions

#### Reaction Rules

- trade interaction 가능해야 반응 가능
- `(trade_id, user_id)` unique
- 같은 emoji를 다시 누르면 toggle-off
- 다른 emoji를 누르면 변경(update)

즉 “한 사용자가 하나의 trade에 하나의 reaction만 가질 수 있음”이 현재 규칙이다.

### Notifications

#### Trigger Types

실제 생성되는 notification:

- follow
- reaction
- comment

enum에는 `REMINDER`, `NUDGE`도 있으나 현재 생성 로직은 없다.

#### Notification Settings

기본값은 모두 true다.

- reactionAlert
- commentAlert
- followAlert
- reminderAlert
- nudgeAlert

#### Read Flow

- `PATCH /notifications/read`
  - 전부 읽음
- `PATCH /notifications/{id}/read`
  - 단건 읽음

#### Storage Shape

notification은 다음을 가진다.

- user
- type
- actorUser
- targetType
- targetId
- message
- isRead

문구는 서버에서 완성 문자열로 저장한다.

## 13. Import Domain

### Overall Model

import는 2단계다.

1. preview 생성
   - `import_jobs`
   - `import_results`
2. confirm
   - 실제 `trades` 생성

이 구조 때문에 confirm request는 `tradeIds`가 아니라 `importResultIds`를 받는다.

### CSV Import

`POST /portfolio/import/csv`

동작:

1. `ImportJob` 생성
2. status를 `PROCESSING`으로 바꿈
3. CSV header 기반 파싱
4. 각 row를 `ImportResult`로 변환
5. 성공/실패 count 집계
6. job 완료 상태로 저장

CSV는 header name을 normalize해서 읽는다.

- 영숫자만 남김
- lower-case

필수 컬럼:

- `ticker`
- `market`
- `tradeType`
- `quantity`
- `price`
- `tradedAt`

선택 컬럼:

- `comment`
- `visibility`

### OCR Import

현재는 실제 OCR이 아니다.

- 업로드한 filename을 정규식으로 파싱하는 stub이다
- 포맷:
  - `TICKER_MARKET_TYPE_QTY_PRICE_yyyyMMddHHmmss.ext`

파싱 성공 시:

- `parsed` 객체
- `confidence = 0.95`

실패 시:

- `parsed = null`
- `confidence = 0.0`

### Confirm Import

`POST /portfolio/import/confirm`

검증:

- `importResultIds` 필수
- request 내 중복 id 금지
- 모두 존재해야 함
- 모두 같은 user 소유여야 함
- 모두 같은 import job이어야 함
- 이미 확정된 결과가 포함되면 `DUPLICATE`

확정 처리:

- valid result만 trade로 전환
- `confirmed_trade_id`, `confirmed_at` 기록
- job status를 `CONFIRMED`로 변경
- portfolio projection 재계산

응답:

- `savedTrades`
- `confirmedImportResultIds`
- `tradeIds`

중요한 세부사항:

- `ImportResult.selected` 필드는 현재 preview/DB에는 저장되지만, confirm 로직에서는 사용되지 않는다.
- 실제 선택 여부는 결국 request body의 `importResultIds` 집합이 결정한다.

## 14. Reminder Domain

### Reminder Model

`InvestmentReminder`

- user
- stockSymbol
- amount
- dayOfMonth
- active
- nextReminderDate

### Rules

- `dayOfMonth`는 1..28로 제한
- create 시 nextReminderDate 계산
- update 시 amount/dayOfMonth/isActive 일부 수정 가능
- inactive면 nextReminderDate는 null

현재는 저장/조회/수정/삭제만 있고, 실제 reminder 발행 스케줄러는 없다.

즉 notification enum과 table 준비는 되어 있지만, reminder 알림 생산자는 아직 없는 상태다.

## 15. KIS Sync Phase 2

### User Keys

KIS app key / secret는 user 테이블에 암호화된 형태로 저장된다.

- `kis_app_key_encrypted`
- `kis_app_secret_encrypted`

### Field Encryption

`FieldEncryptor`

- secret string -> SHA-256 -> AES key
- AES/GCM/NoPadding
- 12-byte IV
- Base64 encode

### Sync Flow

`POST /portfolio/sync`

1. user 조회
2. KIS encrypted key 존재 확인
3. decrypt
4. `KisSyncClient.syncTrades(appKey, appSecret)` 호출
5. payload를 trade로 저장
6. projection 재계산
7. synced summary 반환

현재 client는 `StubKisSyncClient`뿐이다.

- `integration.kis.stub-file`이 비어 있으면 빈 결과
- 파일이 있으면 JSON 배열을 payload로 읽음

중복 방지는 기존 모든 trade를 읽어 exact match 비교로 처리한다.

- ticker
- tradeType
- quantity
- price
- tradedAt

이 구현은 간단하지만 trade가 많아지면 비효율적이다.

## 16. Database and Migrations

### V1

초기 핵심 스키마를 만든다.

- users
- user_auth_identities
- refresh_tokens
- notification_settings
- follows
- stock_symbols
- price_snapshots
- portfolios
- holdings
- trades
- reactions
- comments
- notifications

그리고 seed 종목/시세도 넣는다.

### V2

Phase 2 확장 테이블:

- investment_reminders
- import_jobs
- import_results

### V3

import confirm hardening:

- `import_results.confirmed_trade_id`
- `import_results.confirmed_at`
- confirmed_trade foreign key
- unique index on `confirmed_trade_id`

즉 하나의 import result가 동일 trade에 중복 연결되는 경로를 막는다.

## 17. Tests

테스트는 총 3개이며 모두 `@SpringBootTest + MockMvc` 기반 integration test다.

### `AuthFlowIntegrationTest`

검증 범위:

- signup
- email confirm
- login
- logout
- withdraw

### `TradePortfolioIntegrationTest`

검증 범위:

- verified user 생성
- BUY trade 생성
- portfolio projection 재계산
- holding 반영 확인

### `Phase2IntegrationTest`

검증 범위:

- reminder CRUD 일부
- CSV import preview + confirm
- duplicate confirm conflict
- import 후 portfolio holding 반영
- KIS key patch endpoint

### Test Strategy Notes

- README에는 Testcontainers dependency가 있지만, 실제 테스트는 H2 기반이다.
- Redis는 `test` 프로필에서 in-memory verification store로 우회한다.
- 테스트 수는 아직 적고 happy path 중심이다.

## 18. Docs and Contract Artifacts

`docs/`에는 프론트 연동용 문서가 있다.

- `openapi.json`
- `Folo.postman_collection.json`
- `response-shape-guide.md`
- `api-contract-review.md`

특히 `api-contract-review.md`는 PDF 명세와 현재 구현의 차이를 문서화하고 있다.

핵심 차이:

- signup 즉시 token 미발급
- `importResultIds` 기반 confirm
- KIS/OCR는 실제 연동이 아니라 stub

## 19. Strengths of Current Design

- 서비스 경계가 비교적 명확하다.
- `trades`를 원본으로 두고 projection을 별도로 두는 방향이 일관적이다.
- `FRIENDS_ONLY = mutual follow` 규칙이 여러 패키지에서 재사용된다.
- refresh token을 해시로 저장한다.
- KIS key를 평문 저장하지 않는다.
- import confirm 중복 방지 장치가 있다.
- 공통 response envelope이 단순하고 프론트 친화적이다.
- 로컬 실행 환경과 테스트 프로필이 분리돼 있다.

## 20. Important Risks and Gaps

이 절은 “버그 또는 운영 리스크 후보”를 모아둔 것이다.

### 1. Refresh token이 일반 인증에 사용될 수 있음

가장 중요한 보안 이슈다.

- `JwtAuthenticationFilter`는 JWT의 `type`을 검사하지 않는다.
- 따라서 refresh token도 일반 API 인증에 통과할 수 있다.
- access-only 검증 계층이 추가되어야 한다.

### 2. `myTrades` 필터 pagination 정확도 문제

- DB pagination 후 메모리 필터를 적용한다.
- 필터가 있는 경우 `totalCount`, `hasNext`가 실제 결과와 다를 수 있다.

### 3. Trade projection은 매번 full recalc

- create/delete/import/sync 때마다 유저 전체 거래를 다시 읽어 재계산한다.
- 현재는 단순하고 안전하지만 거래가 많아지면 비용이 커진다.

### 4. KIS sync 중복 체크 효율성

- 매 sync마다 기존 trade 전체를 읽고 exact match 비교
- 대량 데이터에 취약

### 5. Email sender가 로그 기반

- 운영 환경에서 그대로 쓰면 인증 코드가 로그에 남는다.

### 6. Reminder는 CRUD만 있고 발행 엔진 없음

- nextReminderDate 저장은 되지만 실제 notification 생산자가 없다.

### 7. OCR은 실제 OCR이 아님

- 파일명 파싱 stub

### 8. 테스트 범위 부족

특히 빠진 영역:

- friends-only visibility edge case
- reaction toggle/change 케이스
- comment soft delete
- refresh rotation 실패 경로
- KIS sync dedupe
- notification setting off 상태

### 9. 일부 API path parameter 활용이 약함

- `CommentController.delete`는 path에 `tradeId`가 있지만 실제 delete 로직은 `commentId`만 본다.
- 큰 버그는 아니지만 contract 정합성 측면에서 거칠다.

### 10. 운영 관점 구성은 아직 초기 상태

- structured logging 없음
- metrics/trace/alerting 없음
- real secret manager 없음
- CI/CD 정의 없음
- 이메일/KIS/OCR 실연동 없음

## 21. What This Repo Already Does Well

현재 코드베이스는 “앱이 아직 작을 때 빠르게 기능을 검증하기 위한 MVP~초기 프로덕션 전 단계”로는 꽤 괜찮다.

- 기능 축이 대부분 연결돼 있다.
- 도메인 규칙이 흩어지지 않고 service에 모여 있다.
- 무리한 abstraction보다 구현 우선 구조를 택했다.
- 문서와 실제 API가 완전히 분리되지 않고 같이 유지되고 있다.

즉 현재 상태는 “기능 탐색과 프론트 연동을 바로 시작할 수 있는 수준”이다.

## 22. Suggested Next Technical Priorities

현재 레포를 실제 서비스 수준으로 끌고 가려면 우선순위는 다음이 적절하다.

### 1. Security hardening

- access token / refresh token type 분리 검증
- auth 관련 rate limit 강화
- real email sender 연동
- secret manager 연동

### 2. Query correctness

- `myTrades` filtering을 DB query로 이동
- feed / notification count 관련 성능 개선

### 3. Production integrations

- real KIS sync
- real OCR
- reminder scheduler + notification producer

### 4. Test expansion

- visibility / access control
- duplicate / invalid import paths
- refresh rotation negative case
- comment / reaction / notification end-to-end

### 5. Operational readiness

- CI
- Docker image
- staging/prod profile
- observability

## 23. Final Assessment

이 레포는 현재 “사회적 투자 기록 앱 백엔드”의 핵심 흐름을 이미 구현하고 있으며, 설계상 중심축은 분명하다.

- 인증은 email verify 이후 토큰 발급
- 거래는 ledger
- holdings / portfolio는 projection
- 접근 제어는 visibility + follow 관계
- import / KIS / reminder는 Phase 2 확장 포인트

가장 먼저 손봐야 할 기술 부채는 보안과 query 정확도 쪽이다.

- refresh token이 일반 인증으로 사용될 수 있는 점
- filtered trade listing의 pagination 정확도

그 외는 대부분 “아직 stub인 외부 연동을 실제 서비스 수준으로 바꾸는 작업”이다.

요약하면, 이 코드는 아직 완성형은 아니지만 구조적으로는 일관되고, 다음 단계 작업을 올리기 좋은 기반이다.
