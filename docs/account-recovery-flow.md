# Account Recovery Flow

FOLO의 계정 복구는 현재 "로그인 아이디 찾기"와 "임시 비밀번호 발급" 두 흐름으로 동작한다.
로그인 아이디는 별도 username이 아니라 이메일이다.

## 목적

- 가입한 이메일이 기억나지 않는 사용자가 로그인 아이디를 다시 확인할 수 있어야 한다.
- 비밀번호를 잊어버린 사용자가 메일로 받은 임시 비밀번호로 다시 로그인할 수 있어야 한다.
- 계정 존재 여부를 과도하게 노출하지 않아야 한다.
- 임시 비밀번호 발급 시 기존 세션을 모두 만료시켜야 한다.

## 현재 규칙

- 로그인 아이디는 `email`이다.
- 아이디 찾기는 `nickname` 기준으로 마스킹된 이메일을 반환한다.
- 비밀번호 찾기는 재설정 코드 방식이 아니라 임시 비밀번호 메일 발송 방식이다.
- 임시 비밀번호 발급 시 기존 refresh token은 모두 revoke 된다.
- 임시 비밀번호 발급 후에는 새 비밀번호로 다시 로그인해야 한다.

## 백엔드 API

### 1. 로그인 아이디 찾기

`POST /api/auth/find-id`

Request:

```json
{
  "nickname": "maskFinder"
}
```

Success response:

```json
{
  "success": true,
  "data": {
    "found": true,
    "maskedLoginId": "ma****@example.com"
  },
  "message": "로그인 아이디 조회가 완료되었습니다.",
  "error": null,
  "timestamp": "2026-03-22T00:00:00Z"
}
```

계정을 찾지 못한 경우도 정상 응답이며, 이때 `found=false`, `maskedLoginId=null` 이다.

### 2. 임시 비밀번호 발급

`POST /api/auth/password/reset-temp`

Request:

```json
{
  "email": "user@example.com"
}
```

Success response:

```json
{
  "success": true,
  "data": null,
  "message": "입력한 이메일로 계정이 존재하면 임시 비밀번호를 전송했습니다.",
  "error": null,
  "timestamp": "2026-03-22T00:00:00Z"
}
```

이 응답은 계정 존재 여부와 무관하게 동일하게 내려간다.
실제 계정이 있을 때만 임시 비밀번호가 생성되고 메일이 발송된다.

## 백엔드 내부 동작

### 아이디 찾기

1. `nickname` 으로 활성 사용자 조회
2. 해당 사용자의 `EMAIL` 인증 수단 조회
3. 이메일 local part 일부를 마스킹
4. `found`, `maskedLoginId` 반환

### 임시 비밀번호 발급

1. `email` 로 `EMAIL` 인증 수단 조회
2. 활성 계정인 경우에만 임시 비밀번호 생성
3. bcrypt 해시로 `password_hash` 갱신
4. 미인증 계정이면 `emailVerified=true` 처리
5. 해당 사용자의 활성 refresh token 전부 revoke
6. 임시 비밀번호 메일 발송

## 클라이언트 화면 흐름

### 로그인 화면

- 로그인 화면 하단에 `아이디 찾기`, `임시 비밀번호 받기` 링크가 있다.
- `아이디 찾기`는 `RecoverLoginId` 화면으로 이동한다.
- `임시 비밀번호 받기`는 `PasswordResetRequest` 화면으로 이동한다.

### 아이디 찾기 화면

화면 목적:

- 닉네임 기반으로 가입 이메일을 일부 마스킹해서 보여준다.

사용자 흐름:

1. 닉네임 입력
2. `POST /auth/find-id` 호출
3. `found=true` 이면 `가입 이메일은 ma****@example.com 입니다.` 표시
4. `found=false` 이면 `일치하는 가입 계정을 찾지 못했습니다.` 표시

### 임시 비밀번호 요청 화면

화면 목적:

- 사용자가 이메일 주소를 입력하면 백엔드에 임시 비밀번호 발급을 요청한다.

사용자 흐름:

1. 이메일 입력
2. `POST /auth/password/reset-temp` 호출
3. 성공 시 로그인 화면으로 이동
4. 로그인 화면 notice 영역에
   `입력한 이메일로 계정이 존재하면 임시 비밀번호를 전송했습니다. 메일함을 확인해 주세요.` 표시
5. 사용자는 메일로 받은 임시 비밀번호로 로그인

현재는 별도의 `비밀번호 재설정 확인` 화면이 없다.

## 보안 고려사항

- 비밀번호 찾기 응답은 계정 존재 여부를 노출하지 않는다.
- 임시 비밀번호는 평문으로 DB에 저장하지 않고 bcrypt 해시만 저장한다.
- 임시 비밀번호 발급 시 기존 refresh token을 전부 폐기한다.
- 로컬 fallback 메일 전송기에서도 임시 비밀번호 원문은 로그에 남기지 않는다.
- 아이디 찾기는 마스킹된 이메일만 반환하고 전체 이메일은 노출하지 않는다.

## 제품/UX 주의사항

- "아이디 찾기"는 실제로는 `가입 이메일 확인` 기능이다.
- 로그인 실패 시 기존 비밀번호는 더 이상 쓸 수 없고, 반드시 메일로 받은 임시 비밀번호로 로그인해야 한다.
- 임시 비밀번호 발급 직후 사용자가 이미 로그인 중이던 다른 기기 세션은 모두 끊긴다.
- 이메일 미인증 계정도 임시 비밀번호 메일을 받으면 로그인 가능 상태가 된다.

## 테스트 기준

- 닉네임으로 아이디 찾기 성공 시 `maskedLoginId` 가 내려온다.
- 존재하지 않는 닉네임이면 `found=false` 가 내려온다.
- 임시 비밀번호 발급 후 기존 비밀번호 로그인은 실패한다.
- 임시 비밀번호 발급 후 새 임시 비밀번호 로그인은 성공한다.
- 임시 비밀번호 발급 후 기존 refresh token으로는 토큰 갱신이 실패한다.

## 관련 파일

백엔드:

- `src/main/java/com/folo/auth/AuthController.java`
- `src/main/java/com/folo/auth/AuthService.java`
- `src/main/java/com/folo/auth/AuthDtos.java`
- `src/main/java/com/folo/auth/SmtpEmailSender.java`
- `src/test/java/com/folo/auth/AuthFlowIntegrationTest.java`

클라이언트:

- `src/api/services.ts`
- `src/api/contracts.ts`
- `src/screens/LoginScreen.tsx`
- `src/screens/RecoverLoginIdScreen.tsx`
- `src/screens/PasswordResetRequestScreen.tsx`
- `src/navigation/AppNavigator.tsx`
- `src/navigation/types.ts`
