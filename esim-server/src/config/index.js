import dotenv from "dotenv";

dotenv.config();

export const config = {
  env: process.env.NODE_ENV || "development",
  port: Number(process.env.PORT || 3000),
  publicBaseUrl: process.env.PUBLIC_BASE_URL || "http://localhost:3000",
  sessionSecret: process.env.SESSION_SECRET || "dev-only-insecure-secret-change-me",

  // IP 화이트리스트: 콤마로 분리된 리스트
  ipWhitelist: (process.env.IP_WHITELIST || "").split(",").map(s => s.trim()).filter(Boolean),

  // 관리자 대시보드 로그인 계정 (반드시 운영 환경에서 .env로 재정의할 것)
  admin: {
    username: process.env.ADMIN_USERNAME || "admin",
    password: process.env.ADMIN_PASSWORD || "change-me",
  },

  // MySQL 연결 정보
  db: {
    host: process.env.DB_HOST || "127.0.0.1",
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || "root",
    password: process.env.DB_PASSWORD || "",
    database: process.env.DB_NAME || "ringtalk",
  },

  // 알림톡/문자 발송(BizPPurio) API
  bizppurio: {
    baseUrl: process.env.BIZPPURIO_BASE_URL || "https://api.bizppurio.com",
    account: process.env.BIZPPURIO_ACCOUNT || "",
    authToken: process.env.BIZPPURIO_AUTH_TOKEN || "",
    senderKey: process.env.BIZPPURIO_SENDER_KEY || "",
    templateCode: process.env.BIZPPURIO_TEMPLATE_CODE || "",
  },

  // 창고/발주 연동 API (Warehouse)
  warehouse: {
    baseURL: process.env.WAREHOUSE_BASE_URL || "",
    customerCode: process.env.WAREHOUSE_CUSTOMER_CODE || "",
    customerAuth: process.env.WAREHOUSE_CUSTOMER_AUTH || "",
  },

  // eSIM 원격 발급(RSP+) API - axios 기반 클라이언트용
  rsp: {
    baseURL: process.env.RSP_BASE_URL || "",
    appId: process.env.RSP_APP_ID || "",
    appSecret: process.env.RSP_APP_SECRET || "",
    notifyBaseURL: process.env.RSP_NOTIFY_BASE_URL || "",
  },

  // eSIM 사용량 조회 / 쿠폰 리딤 Open API (fetch 기반, index.js/routes에서 직접 사용)
  esimOpenApi: {
    baseUrl: process.env.ESIM_OPENAPI_BASE_URL || "",
    appId: process.env.ESIM_OPENAPI_APP_ID || "",
    appSecret: process.env.ESIM_OPENAPI_APP_SECRET || "",
  },

  // 네이버 커머스 API (Java 배치 클라이언트와 동일한 값 공유)
  naverCommerce: {
    clientId: process.env.NAVER_CLIENT_ID || "",
    clientSecret: process.env.NAVER_CLIENT_SECRET || "",
  },
};
