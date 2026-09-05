# RingTalk eSIM Platform

해외 여행자를 위한 eSIM 데이터 로밍 서비스(RingTalk/링톡)의 주문 처리 백엔드입니다.
네이버 스마트스토어로 들어온 주문을 수집해서 eSIM 파트너사 API로 발주하고,
QR 코드를 발급받아 카카오 알림톡으로 고객에게 전달하며, 관리자 대시보드로 전체 흐름을 모니터링합니다.

> **참고**: 이 저장소는 보안 이슈(운영 DB/관리자 계정/외부 API 시크릿 하드코딩, 고객 개인정보가 담긴 로그 파일 커밋 등)를
> 정리한 뒤, 실제 회사/파트너사 정보를 제거하고 포트폴리오 용도로 재구성한 버전입니다. 실제 배포에 쓰인 자격증명·도메인은
> 모두 환경변수/예시값으로 교체되어 있으며, git 히스토리도 정리되었습니다.

## 전체 흐름

```
네이버 스마트스토어 주문
        │
        ▼
NaverCommerceApiClient (Java, 배치)  ──▶  MySQL(user 테이블)에 주문 저장
        │
        ▼
CustomerApiClient (Java, 배치)  ──▶  eSIM 파트너사 발주 API 호출 (orderTid 발급)
        │
        ▼
파트너사 콜백 → esim-server (/api/joytel/esim/callback 등)  ──▶  snPin/QR 저장
        │
        ▼
BizPPurio API로 카카오 알림톡 발송 (QR 링크 포함)
        │
        ▼
고객이 링크 접속 → /esim/qr-detail (QR 코드 + 설치 안내, EJS 서버 렌더링)

관리자는 /(대시보드)에서 주문 현황·매출·알림톡 발송 상태를 확인하고, 필요 시 수동 발송도 가능합니다.
```

## 폴더 구조

```
esim-server/            Node.js/Express 백엔드 (고객용 eSIM 페이지, 관리자 대시보드, 파트너사 웹훅 수신)
  src/
    index.js            앱 진입점, 라우팅
    config/              환경변수 로딩
    clients/              MySQL / BizPPurio(알림톡) / 파트너사(Warehouse, RSP+) API 클라이언트
    routes/esimCallbacks.js  파트너사 콜백(webhook) 핸들러
    middlewares/          인증(admin 세션), 보안(IP 화이트리스트) 미들웨어
    orderSchedulerMain.js / orderSchedulerOnce.js  Java 배치를 주기 실행하는 스케줄러
  views/                 EJS 템플릿 (고객용 eSIM 상세 페이지)
  public/                정적 파일 (관리자 로그인/대시보드 HTML, CSS/JS, eSIM 안내 페이지들)

src/main/java/org/mindrot/    Java 배치 클라이언트 (네이버 커머스 API 수집, 파트너사 발주 호출)
  BCrypt.java                jBCrypt(비밀번호 해싱) 벤더 라이브러리 - 프로젝트 자체 로직 아님
  NaverCommerceApiClient.java  네이버 커머스 주문 조회 / 발송 처리
  CustomerApiClient.java       파트너사 발주 API 호출, 상품코드 매핑
  MySQLConfig.java / UserOrderDAO.java  DB 연동
  SignatureGenerator.java      네이버 API 서명 생성

lib/                     MySQL JDBC 드라이버 (Maven 없이 javac/java로 직접 실행하기 위한 vendored jar)
target/                  Java 컴파일 산출물 (gitignore 처리)
```

## 기술 스택

- **Backend**: Node.js, Express 5, EJS(서버 렌더링), MySQL(mysql2), express-session
- **배치/연동**: Java (표준 라이브러리 위주, 외부 프레임워크 없이 HttpURLConnection 사용)
- **외부 연동**: 네이버 커머스 API, BizPPurio(카카오 알림톡), eSIM 파트너사 RSP+/Warehouse API
- **프로세스 관리**: PM2 (`esim-server/ecosystem.config.cjs`)

## 시작하기

```bash
cd esim-server
cp .env.example .env   # 값 채우기
npm install
npm run dev
```

자세한 환경변수/엔드포인트 설명은 [esim-server/README.md](esim-server/README.md)를 참고하세요.
네이버 연동 Java 배치는 [NAVER_MYSQL_INTEGRATION_README.md](NAVER_MYSQL_INTEGRATION_README.md)를 참고하세요.

## 라이선스

`src/main/java/org/mindrot/BCrypt.java`는 [jBCrypt](http://www.mindrot.org/projects/jBCrypt) (ISC 라이선스, [LICENSE](LICENSE) 참고)를
비밀번호 해싱 용도로 가져와 사용합니다. 나머지 코드는 이 프로젝트의 자체 구현입니다.
