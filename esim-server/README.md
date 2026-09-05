# RingTalk eSIM Server (Express.js)

해외 eSIM 주문/QR 발급/알림톡 발송을 처리하는 백엔드입니다. 네이버 커머스에서 들어온 주문을
eSIM 파트너사 API와 연동해 QR 코드를 발급하고, 고객에게 카카오 알림톡으로 전달하며,
관리자 대시보드로 주문 현황을 확인합니다.

## 환경변수 설정

`.env.example`을 복사해 `.env`를 만들고 값을 채워주세요. (`.env`는 git에 커밋되지 않습니다)

```bash
cp .env.example .env
```

주요 값:
- `DB_*`: MySQL 접속 정보
- `ADMIN_USERNAME` / `ADMIN_PASSWORD`: 관리자 대시보드 로그인 계정
- `SESSION_SECRET`: 세션 서명용 시크릿 (반드시 운영 환경에서 무작위 값으로 변경)
- `BIZPPURIO_*`: 카카오 알림톡/문자 발송 API 인증 정보
- `WAREHOUSE_*`, `RSP_*`, `ESIM_OPENAPI_*`: eSIM 파트너사 발주/조회 API 인증 정보
- `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET`: 네이버 커머스 API 인증 정보 (Java 배치와 공유)

## 실행

```bash
npm install
npm run dev        # 개발 모드 (nodemon)
npm start           # 운영 모드

# 주문 스케줄러
npm run scheduler       # 프로덕션 모드
npm run scheduler:dev   # 개발 모드 (nodemon)
```

## 주요 엔드포인트

```
GET  /login, /                       관리자 로그인 / 대시보드 (세션 인증)
GET  /esim/qr-detail?transId=...     고객용 eSIM QR/설치 안내 페이지 (EJS 서버 렌더링)
POST /api/joytel/esim/order          eSIM 주문 접수 (Warehouse 연동)
POST /api/joytel/esim/callback       eSIM 발급 결과 콜백 (snPin/QR 저장)
POST /api/joytel/coupon/redeem       쿠폰 리딤 (RSP+)
POST /api/joytel/notify/coupon/redeem   쿠폰 리딤 결과 콜백 (QR 저장 + 알림톡 발송)
POST /api/joytel/esim/status-usage   eSIM 상태/사용량 조회
```

> `/api/joytel/*` 경로 자체는 파트너사에 이미 등록되어 있는 실 서비스 콜백 URL과 맞물려 있어
> 코드상 이름과 무관하게 유지하고 있습니다. 실제로 이 경로를 바꾸려면 파트너사와 먼저 협의하세요.

## 화면 렌더링 방식

- `/login`, `/`(대시보드): 완전히 정적인 페이지라 `public/login.html`, `public/dashboard.html`로 두고
  `res.sendFile`로 제공합니다. 데이터는 로그인 후 클라이언트 JS(`public/js/dashboard.js`)가
  `/api/admin/*` JSON API를 호출해서 채웁니다.
- `/esim/qr-detail`: 주문별 QR 이미지, 잔여 데이터 등 서버에서 계산한 값을 최초 HTML에
  바로 심어서 내려줘야 하는 페이지라, 가벼운 템플릿 엔진인 [EJS](https://ejs.co/)로 렌더링합니다
  (`views/esim-detail.ejs`, `views/esim-message.ejs`). React/Vue 같은 SPA 프레임워크 없이도
  서버 렌더링 + 약간의 인라인 스크립트만으로 충분한 페이지라 EJS를 선택했습니다.

## 주문 스케줄러 (Order Scheduler)

주문 스케줄러는 1분마다 다음 작업을 수행합니다:

1. **주문 정보 수집**: `NaverCommerceApiClient`(Java)를 실행하여 네이버 커머스 API에서 새로운 주문 정보를 가져옵니다.
2. **중복 방지**: `productOrderId`와 `orderId`를 기준으로 중복된 주문은 저장하지 않습니다.
3. **데이터베이스 저장**: 새로운 주문 정보를 `ringtalk.user` 테이블에 저장합니다.
4. **카카오 메시지 처리**:
   - `orderTid`가 null이고 `kakaoSendYN`이 `'N'`인 주문을 찾습니다.
   - `CustomerApiClient`(Java)를 호출하여 `orderTid`를 생성/업데이트합니다.
   - BizPPurio API를 통해 카카오 메시지를 전송합니다.
   - 메시지 전송 성공 시 `kakaoSendYN`을 `'Y'`로 업데이트합니다.

### 필수 요구사항
- Java 환경 설정 (`NaverCommerceApiClient`, `CustomerApiClient` 실행용, `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET` 등 동일한 env 필요)
- MySQL 데이터베이스 연결
- `ringtalk.user` 테이블 구조
- BizPPurio API 토큰 설정

### 로그 확인
스케줄러 실행 중 모든 과정이 콘솔에 로깅됩니다. 오류 발생 시 해당 단계만 건너뛰고 다음 처리를 계속 진행합니다.

## 참고

- Warehouse 서명: SHA-1(signature) / RSP+ 서명: MD5(AppId+TransId+Timestamp+AppSecret)
- 실제 엔드포인트 경로/필드명은 파트너사 API 문서 기준으로 다를 수 있으니 연동 전 확인하세요.
- 콜백에서 서명 검증이 필요하다면 파트너사가 제공하는 규약에 맞춰 `src/middlewares/security.js`에 추가하세요.
