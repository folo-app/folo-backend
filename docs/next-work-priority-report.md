# FOLO Next Work Priority Report

기준 문서:

- [research.md](/Users/godten/.codex/worktrees/a784/folo-backend/research.md)
- 최근 반영된 수정
  - refresh token이 일반 인증에 사용될 수 있던 문제 해결
  - `myTrades` 필터 pagination 정확도 문제 해결
  - local boot 시 stock master sync 파일 누락으로 서버가 죽던 문제 해결

작성 목적:

- 다음 작업을 바로 구현하지 않고
- 어떤 순서로 진행해야 리스크가 낮고 제품 가치가 높은지
- 의사결정용 우선순위 보고서로 정리

## 1. 현재 상태 요약

현재 백엔드는 다음 단계까지는 올라와 있다.

- 이메일 가입/인증/JWT 기반 인증 흐름 존재
- 거래 입력, 포트폴리오 projection, 피드, 댓글, 리액션, 알림 존재
- CSV/OCR import stub, KIS key 저장, KIS sync stub 존재
- 로컬 실행과 기본 문서화는 가능

다만 아직 “실서비스 운영 준비” 기준으로 보면 아래 공백이 크다.

- 보안/정합성 회귀 테스트가 충분하지 않음
- 외부 연동이 대부분 stub 수준임
- 운영 관측성/배포/비밀 관리가 약함
- 모바일 스토어 배포 준비 항목이 아직 거의 없음

즉 지금 단계는 “기능이 있는 백엔드”이고, 다음 단계는 “신뢰 가능한 서비스 백엔드”로 올리는 작업이다.

## 2. 우선순위 결론

추천 우선순위는 아래 순서다.

1. 회귀 테스트와 접근 제어 하드닝
2. 인증/보안 운영 하드닝
3. 종목 마스터/시세/KIS 연동 하드닝
4. reminder/OCR/import의 실제 서비스화
5. 배포/운영 체계와 모바일 출시 준비

핵심 판단은 이렇다.

- 지금은 새 기능을 더 얹는 것보다, 이미 있는 핵심 흐름이 다시 깨지지 않게 막는 게 먼저다.
- FOLO는 금융 기록 앱이라 “한 번 틀리는 것”의 신뢰 비용이 크다.
- 외부 연동은 테스트와 설정 체계가 약한 상태에서 붙이면 디버깅 비용이 급증한다.

## 3. Priority 0: 이미 막 고친 영역 주변 회귀 테스트 보강

### 추천 이유

막 수정한 두 이슈는 성격이 좋지 않다.

- refresh token 인증 허용 문제
  - 보안 문제
- `myTrades` pagination 부정확
  - 데이터 정합성 문제

둘 다 “한 군데만 고치고 끝내면 다른 경로에서 재발”할 확률이 높다.

### 해야 할 일

- 인증 필터 관련 integration test 추가
  - refresh token으로 일반 API 접근 시 `401`
  - access token으로는 정상 접근
  - refresh endpoint는 refresh token만 허용
- trade list integration test 추가
  - ticker filter
  - tradeType filter
  - from/to date filter
  - page/size + totalCount/hasNext 검증
- 접근 제어 integration test 추가
  - `FRIENDS_ONLY` profile/portfolio/feed/trade/comment/reaction
  - mutual follow일 때만 허용되는지 검증
- soft delete integration test 추가
  - deleted trade/comment가 기본 조회에서 빠지는지 검증

### 완료 기준

- 인증/거래/접근 제어 회귀 테스트가 핵심 happy path와 실패 path를 모두 덮음
- 이후 보안/쿼리 리팩터링을 해도 동일 테스트로 회귀 방지 가능

### 우선순위 판단

가장 먼저 해야 한다.

## 4. Priority 1: 인증/보안 운영 하드닝

### 추천 이유

FOLO는 거래 기록과 투자 성과를 다루기 때문에, 서비스 초기에 가장 치명적인 문제는 보안 사고와 계정 오남용이다.

코드 기준으로 지금 바로 강화해야 할 부분은 다음이다.

- 로그인/회원가입/이메일 인증 재시도 정책
- refresh token 수명과 회전 정책 검증
- 민감정보 노출 경로 제거
- 운영 secret 관리

### 해야 할 일

#### 1. Rate limiting

대상 endpoint:

- `/auth/signup`
- `/auth/login`
- `/auth/email/verify`
- `/auth/email/confirm`
- `/auth/refresh`

이유:

- brute force
- 인증 코드 abuse
- token refresh abuse

#### 2. Email sender 실연동

현재는 로컬/기본 구현상 logging sender 경로가 남아 있다.

해야 할 일:

- 운영/스테이징 분리
- 메일 실패 처리 정책
- 발신자 주소/도메인 검증
- 이메일 템플릿 정리

#### 3. Secret 관리 정리

대상:

- JWT secret
- field encryption secret
- SMTP credential
- KIS credential

해야 할 일:

- `.env`/로컬 값과 운영 값을 명확히 분리
- 로그 마스킹 확인
- 추후 secret manager로 올릴 기준 설계

#### 4. 감사 로그 최소 기준

이벤트:

- signup
- login success/failure
- refresh
- logout
- withdraw
- KIS key 등록/갱신

주의:

- 비밀번호/토큰 원문/KIS secret은 절대 로그에 남기지 않음

### 완료 기준

- 인증 관련 abuse 저항성이 생김
- 운영에서 평문 민감정보 노출 위험이 낮아짐
- 계정 이슈 조사 가능한 최소 로그가 생김

### 우선순위 판단

P0 바로 다음이다. 새 기능보다 먼저다.

## 5. Priority 2: 종목 마스터/시세/KIS 연동 하드닝

### 추천 이유

FOLO의 핵심 사용자 가치 중 하나는 “투자 기록이 실제 자산 데이터와 연결되어 있다”는 점이다. 현재 이 축은 가장 가치가 크면서도 아직 stub/초기 단계가 많다.

최근 local boot 이슈도 보여준 것처럼, 이 영역은 기능보다 설정과 실패 처리부터 단단해야 한다.

### 해야 할 일

#### 1. Stock master sync 운영 모드 정리

현재 쟁점:

- startup sync가 optional이어야 함
- 로컬 파일 기반 KIS master sync는 환경 의존성이 큼
- provider별 enabled/configured semantics를 더 명확히 해야 함

필요 작업:

- local/dev/stage/prod별 기본값 정리
- sync 실패 시 degrade 전략 명확화
- sync run 기록 조회/모니터링 기준 추가

#### 2. 시세 갱신 전략 정리

현재 research 기준으로 price snapshot은 정적 seed + 일부 초기 캐시 성격이 강하다.

필요 작업:

- snapshot 갱신 주기 정의
- stale price 허용 범위 정의
- 가격 조회 실패 시 fallback 정책 정리

#### 3. KIS sync 실연동 전환

현재:

- KIS key 저장 가능
- sync는 stub adapter

다음 단계:

- 실제 KIS 거래 조회 연동
- dedupe 기준 강화
- 부분 성공/재시도/중복 import 정책 정리
- 외부 API 장애 시 graceful failure 처리

### 완료 기준

- “수동 입력만 가능한 투자 기록 앱”에서 “실계좌 동기화가 가능한 앱”으로 넘어갈 기반이 생김

### 우선순위 판단

보안/회귀 테스트 다음으로 가장 가치가 큰 작업이다.

## 6. Priority 3: Import / OCR / Reminder의 실제 서비스화

### 추천 이유

현재 이 영역은 UX상 보여줄 수는 있지만, 실제 제품 경쟁력 관점에서는 아직 완성형이 아니다.

### 해야 할 일

#### 1. CSV import 품질 개선

- broker별 CSV 포맷 확장
- 컬럼 alias 체계 정리
- invalid row 에러 메시지 개선
- preview 선택값(`selected`) 의미 정리

#### 2. OCR real pipeline

현재 OCR은 파일명 파싱 stub이다.

다음 단계:

- 이미지 업로드 저장
- OCR 추출기 연결
- confidence 기준과 사용자 수정 UX를 고려한 API shape 정리

#### 3. Reminder를 실제 알림 생산 흐름으로 연결

현재:

- reminder CRUD는 존재
- 실제 reminder notification producer는 없음

다음 단계:

- scheduler 또는 batch job
- nextReminderDate advance 규칙
- notification setting과 연동
- 같은 날 중복 발송 방지

### 완료 기준

- import/reminder가 단순 데모 기능이 아니라 실제 사용자 습관 형성 도구로 전환됨

### 우선순위 판단

KIS/market-data 축보다 한 단계 뒤가 적절하다.

## 7. Priority 4: 모바일 출시 준비 작업

### 추천 이유

사용자 요청상 App Store / Google Play 배포를 염두에 두고 있으므로, 기능 개발과 별개로 출시 준비 항목을 병렬 계획해야 한다.

다만 이 영역은 너무 일찍 들어가면 제품 핵심이 흔들리고, 너무 늦게 들어가면 막판에 일정이 터진다.

### 해야 할 일

#### 1. 소셜 로그인 전략 정리

- Apple 로그인
- Google 로그인

주의:

- 실제 스토어 심사 정책은 배포 직전 최신 가이드 재확인 필요

#### 2. 계정/개인정보 흐름 정리

- 회원 탈퇴 후 데이터 처리
- KIS 연동 해제
- 개인정보/투자정보 보관 기간
- 약관/개인정보 처리방침 반영 포인트

#### 3. 푸시 알림 전략

- 앱 내 notification만으로 충분한지
- push가 필요한 이벤트 정의
- reminder/follow/comment/reaction 중 무엇을 push로 보낼지 결정

#### 4. 업로드/파일 처리 정책

- OCR 이미지 보관 기간
- 원본 저장 여부
- 악성 파일 방어 정책

### 완료 기준

- 배포 직전 정책/인증/삭제/알림 이슈로 일정이 막히지 않음

### 우선순위 판단

지금 바로 구현보다는 설계 착수가 적절하다.

## 8. Priority 5: 운영 체계와 배포 기반

### 추천 이유

기능이 늘어나도 운영 기반이 없으면 실제 서비스 안정성은 확보되지 않는다.

### 해야 할 일

- CI 파이프라인
  - build
  - test
  - lint/format
- Docker image / 배포 스크립트
- dev/stage/prod profile 분리
- structured logging
- metrics / health / error tracking
- Flyway 운영 정책 정리
- 장애 시 롤백/복구 절차 정리

### 완료 기준

- “개발 머신에서만 도는 서비스”가 아니라 “반복 배포 가능한 서비스”가 됨

### 우선순위 판단

제품 핵심 축이 조금 더 안정화된 뒤 바로 붙는 게 맞다.

## 9. 지금 당장 하지 말아야 할 것

아래는 할 수는 있지만 지금 우선순위는 낮다.

- 홈 요약 화면용 추가 지표 API
- 차트 API 고도화
- NUDGE, TODO 같은 부가 engagement 기능
- UI 편의를 위한 과한 API 확장
- 다국어/국가 확장

이유:

- 지금은 신뢰성과 핵심 데이터 흐름이 더 중요하다.

## 10. 추천 진행 순서

가장 현실적인 다음 진행 순서는 아래다.

### Step 1

회귀 테스트 보강

- auth
- access control
- trade listing/filtering
- soft delete

### Step 2

보안 운영 하드닝

- rate limit
- email sender 실연동
- secret 관리
- 감사 로그

### Step 3

market-data / KIS sync 하드닝

- startup/scheduled sync 정책
- provider 설정 분리
- 실거래 sync 설계

### Step 4

import/reminder real service화

- broker CSV 확장
- OCR 실제 처리
- reminder notification producer

### Step 5

모바일 출시 준비와 배포 체계

- 로그인/정책
- push
- CI/CD
- 운영 관측성

## 11. 개인적 추천

제 판단으로는 다음 작업을 바로 시작할 때 가장 좋은 선택은 이것이다.

### 추천 1순위

`회귀 테스트 + 접근 제어 하드닝`

이유:

- 방금 막힌 두 문제 모두 “테스트가 더 있었으면 먼저 잡혔을 문제”였다.
- FOLO는 금융 데이터와 공개 범위를 다루므로, access control과 데이터 정합성은 초기에 고정해야 한다.
- 이 작업을 먼저 해두면 이후 KIS/OCR/스토어 준비 작업 속도가 올라간다.

### 추천 2순위

`인증/보안 운영 하드닝`

이유:

- 앱 출시를 생각하면 로그인/이메일/비밀정보 관리가 다음 병목이다.

### 추천 3순위

`KIS/market-data 실전화`

이유:

- 실제 사용자 가치가 가장 크게 올라가는 구간이기 때문이다.

## 12. 검토 포인트

검토 시 아래만 먼저 결정하면 다음 작업을 바로 시작할 수 있다.

1. 다음 스프린트 목표를 “신뢰성 강화”로 둘지, “실연동 구현”으로 둘지
2. KIS sync를 OCR보다 먼저 할지
3. reminder를 앱 내 알림으로 끝낼지, push까지 바로 갈지
4. 스토어 배포 전 social login을 언제 붙일지

## 13. 최종 제안

다음 작업은 아래 한 줄로 정리할 수 있다.

`새 기능보다 먼저, 회귀 테스트와 보안/설정 하드닝으로 백엔드의 신뢰성을 고정한 뒤 KIS/market-data 실연동으로 넘어가는 것이 가장 합리적이다.`
