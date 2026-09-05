import express from "express";
import cors from "cors";
import helmet from "helmet";
import session from "express-session";
import { config } from "./config/index.js";
import { ipWhitelistMiddleware } from "./middlewares/security.js";
import { requireAuth, redirectIfAuthenticated } from "./middlewares/auth.js";
import { router as esimCallbacksRouter } from "./routes/esimCallbacks.js";
import { MySQLClient } from "./clients/mysqlClient.js";
import path from "path";
import QRCode from "qrcode";
import { createCanvas, loadImage } from "canvas";
import CryptoJS from "crypto-js";

const app = express();

app.set('views', path.join(process.cwd(), 'views'));
app.set('view engine', 'ejs');

// MySQL 클라이언트 인스턴스 생성
const mysqlClient = new MySQLClient();

// 데이터 사용량 조회 API 함수
async function queryDataUsage(snPin, transId) {
  try {
    if (!snPin) {
      console.log('snPin이 없어 데이터 사용량 조회를 건너뜁니다.');
      return null;
    }

    console.log('데이터 사용량 조회 시작:', { snPin });

    // 헤더 생성
    const appId = config.esimOpenApi.appId;
    const timestamp = Date.now();
    const transId = Date.now().toString();
    const appSecret = config.esimOpenApi.appSecret;

    // MD5 해시 생성
    const str = appId + transId + timestamp + appSecret;
    const ciphertext = CryptoJS.MD5(str).toString();

    console.log('API 헤더 정보:', { appId, transId, timestamp, ciphertext });

    const response = await fetch(`${config.esimOpenApi.baseUrl}/openapi/esim/usage/query`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'AppId': appId,
        'TransId': transId,
        'Timestamp': timestamp,
        'Ciphertext': ciphertext
      },
      body: JSON.stringify({
        coupon: snPin
      })
    });

    const result = await response.json();
    console.log('데이터 사용량 조회 결과:', result);
    
    return result;
  } catch (error) {
    console.error('데이터 사용량 조회 중 오류 발생:', error);
    return null;
  }
}

// 나라명 매핑 객체
const countryMapping = {
  '베트남': 'VIETNAM',
  '일본': 'JAPAN', 
  '중국': 'CHINA',
  '말레이시아': 'MALAYSIA',
  '필리핀': 'PHILIPPINES',
  '인도네시아': 'INDONESIA',
  '싱가폴': 'SINGAPORE',
  '홍마': 'HONGKONG',
  '미국': 'USA',
  '터키': 'TURKEY',
  '대만': 'TAIWAN',
  '태국': 'THAILAND',
  '호뉴': 'AUSTRALIA',
  '유럽' : 'EUROPE',
  '이집트' : 'EGYPT'
};

// 어두운 배경을 가진 나라들 (로고와 국가명을 하얀색으로 변경)
const darkBackgroundCountries = ['HONGKONG', 'CHINA','PHILIPPINES', 'INDONESIA', 'TAIWAN', 'THAILAND'];

// 나라별 이모지 매핑
const countryEmojiMapping = {
  'VIETNAM': '../assets/VT.png',
  'JAPAN': '../assets/JP.png',
  'CHINA': '../assets/CN.png',
  'MALAYSIA': '../assets/MY.png',
  'PHILIPPINES': '../assets/PL.png',
  'INDONESIA': '../assets/ID.png',
  'SINGAPORE': '../assets/SP.png',
  'HONGKONG': '../assets/HK.png',
  'USA': '../assets/US.png',
  'TURKEY': '../assets/TR.png',
  'TAIWAN': '../assets/TW.png',
  'THAILAND': '../assets/TH.png',
  'AUSTRALIA': '../assets/AU.png',
  'EUROPE': '../assets/EU.png',
  'EGYPT': '../assets/EG.png'
};

// 나라명 추출 및 영문명 변환 함수
function extractCountryFromProductName(productName) {
  if (!productName) return null;
  
  for (const [koreanName, englishName] of Object.entries(countryMapping)) {
    if (productName.includes(koreanName)) {
      return {
        koreanName,
        englishName,
        flagClass: `flag-${englishName.toLowerCase()}`,
        emoji: countryEmojiMapping[englishName],
        isDarkBackground: darkBackgroundCountries.includes(englishName)
      };
    }
  }
  return null;
}

// 배경 이미지 변경 함수
function updateBackgroundImage(countryInfo) {
  if (!countryInfo) return;
  
  const container = document.querySelector('.container');
  if (container) {
    container.style.backgroundImage = `url('/assets/${countryInfo.englishName.toLowerCase()}.png')`;
  }
}

// QR 코드에서 활성화 코드 추출 함수
function extractActivationCode(qrCode) {
  if (!qrCode) return 'N/A';
  
  // $ 구분자로 분리하여 마지막 부분 추출
  const parts = qrCode.split('$');
  if (parts.length > 0) {
    return parts[parts.length - 1]; // 마지막 부분 반환
  }
  
  return qrCode; // $가 없으면 원본 반환
}

// 데이터 사용량 조회 함수
async function getDataUsage(snPin) {
  try {
    console.log('데이터 사용량 조회 시도:', snPin);
    
    const response = await fetch(`${config.esimOpenApi.baseUrl}/openapi/esim/usage/query`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        coupon: snPin
      })
    });
    
    const result = await response.json();
    console.log('데이터 사용량 조회 결과:', result);
    
    if (result.code === '000' && result.data && result.data.dataUsageList && result.data.dataUsageList.length > 0) {
      // 가장 최근 사용량 반환 (usageDate 기준으로 정렬)
      const sortedUsage = result.data.dataUsageList.sort((a, b) => b.usageDate.localeCompare(a.usageDate));
      const latestUsage = sortedUsage[0];
      
      return {
        success: true,
        usage: parseInt(latestUsage.usage), // 바이트 단위
        usageDate: latestUsage.usageDate,
        mcc: latestUsage.mcc
      };
    } else {
      console.log('데이터 사용량 조회 실패 또는 데이터 없음:', result);
      return {
        success: false,
        usage: 0,
        usageDate: null,
        mcc: null
      };
    }
    
  } catch (error) {
    console.error('데이터 사용량 조회 중 오류:', error);
    return {
      success: false,
      usage: 0,
      usageDate: null,
      mcc: null,
      error: error.message
    };
  }
}

// 상품명에서 데이터 용량 추출 함수 (기가바이트)
function extractDataCapacity(productName) {
  if (!productName) return 1; // 기본값 1GB
  
  // 상품명에서 숫자와 "기가", "GB", "g" 등을 찾아서 추출
  const patterns = [
    /(\d+(?:\.\d+)?)\s*기가/gi,
  ];
  
  for (const pattern of patterns) {
    const match = productName.match(pattern);
    if (match) {
      const capacity = parseFloat(match[0].replace(/[^\d.]/g, ''));
      return capacity || 1; // 숫자 추출 실패 시 기본값 1GB
    }
  }
  
  return 1; // 패턴 매칭 실패 시 기본값 1GB
}

// 바이트를 기가바이트로 변환
function bytesToGB(bytes) {
  return bytes / (1024 * 1024 * 1024);
}

// 기가바이트를 바이트로 변환
function gbToBytes(gb) {
  return gb * 1024 * 1024 * 1024;
}

// eSIM Progress 데이터 조회 함수
async function getEsimProgressData(identifier) {
  try {
    console.log(`eSIM 데이터 조회 시도: ${identifier}`);
    await mysqlClient.connect();
    
    // 1. eSIM Progress 데이터 조회
    const esimQuery = `
      SELECT 
        transId, snPin, cid, qrCode, notificationPointId,
        created_at, updated_at
      FROM esim_progress_notifications 
      WHERE transId = ? OR cid = ?
      ORDER BY created_at DESC 
      LIMIT 1
    `;
    
    const [esimRows] = await mysqlClient.connection.execute(esimQuery, [identifier, identifier]);
    console.log(`조회 결과:`, esimRows.length > 0 ? '데이터 발견' : '데이터 없음');
    
    if (esimRows.length === 0) {
      return null;
    }
    
    const esimData = esimRows[0];
    console.log('조회된 eSIM 데이터:', esimData);
    
    // 2. snPin으로 user 테이블에서 상품 정보 조회
    if (esimData.snPin) {
      const userQuery = `
        SELECT productName, day
        FROM user 
        WHERE snPin LIKE ? 
        LIMIT 1
      `;
      
      const [userRows] = await mysqlClient.connection.execute(userQuery, [`%${esimData.snPin}%`]);
      
      if (userRows.length > 0) {
        const userData = userRows[0];
        console.log('조회된 상품 정보:', userData);
        
        // 상품 정보를 esimData에 추가
        esimData.productName = userData.productName;
        esimData.day = userData.day;
        
        // 나라명 추출 및 처리
        const countryInfo = extractCountryFromProductName(userData.productName);
        if (countryInfo) {
          esimData.countryInfo = countryInfo;
          console.log('나라 정보 추출:', countryInfo);
        }
      } else {
        console.log('상품 정보를 찾을 수 없음');
        esimData.productName = null;
        esimData.day = null;
      }
    }
    
    return esimData;
  } catch (error) {
    console.error('eSIM Progress 데이터 조회 실패:', error);
    return null;
  } finally {
    await mysqlClient.disconnect();
  }
}

// 상태 매핑 함수
function getStatusInfo(notificationPointId) {
  const statusMap = {
    '1': { text: "단말기 호환성을 확인하는 중입니다.", color: "blue", icon: "🔍" },
    '2': { text: "설치가 취소되었거나 승인되지 않았습니다.", color: "red", icon: "❌" },
    '3': { text: "eSIM 프로파일을 다운로드 중입니다.", color: "orange", icon: "⬇️" },
    '4': { text: "eSIM 프로파일을 설치하는 중입니다.", color: "purple", icon: "⚙️" },
    '5': { text: "eSIM 프로파일이 삭제되었습니다.", color: "gray", icon: "🗑️" },
    '6': { text: "eSIM이 활성화되었습니다.", color: "green", icon: "✅" },
    '7': { text: "eSIM이 비활성화되었습니다.", color: "yellow", icon: "⏸️" },
    '101': { text: "이 기기의 eSIM(EID)이 차단되어 사용이 불가능합니다.", color: "red", icon: "🚫" },
    '102': { text: "해당 기종은 eSIM 사용이 제한되어 있습니다.", color: "red", icon: "📱" }
  };
  
  return statusMap[notificationPointId] || { 
    text: "상태를 확인하는 중입니다.", 
    color: "gray", 
    icon: "⏳" 
  };
}

// 상태에 따른 메시지 함수
function getStatusMessage(notificationPointId) {
  const messageMap = {
    1: "상품 사용시간(한국시간 기준) 단말기 호환성 확인 중입니다.",
    2: "상품 사용시간(한국시간 기준) 설치가 취소되었습니다.",
    3: "상품 사용시간(한국시간 기준) 프로파일 다운로드 중입니다.",
    4: "상품 사용시간(한국시간 기준) 프로파일 설치 중입니다.",
    5: "상품 사용시간(한국시간 기준) 프로파일이 삭제되었습니다.",
    6: "상품 사용시간(한국시간 기준) 활성화 완료! 사용 가능합니다.",
    7: "상품 사용시간(한국시간 기준) 비활성화 상태입니다.",
    101: "상품 사용시간(한국시간 기준) 기기 차단으로 사용 불가능합니다.",
    102: "상품 사용시간(한국시간 기준) 기종 제한으로 사용 불가능합니다."
  };
  
  return messageMap[notificationPointId] || "상품 사용시간(한국시간 기준) 상태를 확인하는 중입니다.";
}

// QR 코드 생성 함수 (로고 없이)
async function generateQRCodeWithLogo(text, logoPath) {
  console.log("generateQRCodeWithLogo");
  try {
    // QR 코드 생성 (로고 없이)
    const qrCodeBuffer = await QRCode.toBuffer(text, {
      width: 200,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    });
    
    return qrCodeBuffer;
    
  } catch (error) {
    console.error('QR 코드 생성 실패:', error);
    throw error;
  }
}

// QR 코드 생성 함수 (로고 포함)
async function generateQRCodeImage(qrCodeData) {
  console.log("generateQRCodeImage");
  if (!qrCodeData) return null;
  
  try {
    // qrCodeData가 JSON인 경우 파싱
    let qrData = qrCodeData;
    if (typeof qrCodeData === 'string') {
      try {
        qrData = JSON.parse(qrCodeData);
      } catch (e) {
        // JSON이 아닌 경우 그대로 사용
        qrData = qrCodeData;
      }
    }
    
    // 로고 파일 경로
    // const logoPath = path.join(__dirname, '../public/assets/logo-ringtalk.png');
    
    // QR 코드 생성 (로고 없이)
    const qrCodeBuffer = await generateQRCodeWithLogo(qrData, null);
    
    return `data:image/png;base64,${qrCodeBuffer.toString('base64')}`;
    
  } catch (error) {
    console.error('QR 코드 생성 실패:', error);
    return null;
  }
}

// HTML 템플릿 렌더링 함수
// eSIM 상세 페이지에 필요한 데이터를 계산해 EJS 뷰에 전달할 값들을 구성
async function buildEsimDetailViewModel(esimData) {
  console.log(esimData);
  const qrCodeImage = await generateQRCodeImage(esimData.qrCode);

  // 서버에서 데이터 사용량 조회
  let usageData = null;
  if (esimData.snPin) {
    usageData = await queryDataUsage(esimData.snPin, esimData.transId || Date.now().toString());
    console.log("usageData", usageData);

    // 데이터 사용량이 있으면 notificationPointId를 6으로 고정
    if (usageData && usageData.code === '000' && usageData.data && usageData.data.dataUsageList && usageData.data.dataUsageList.length > 0) {
      esimData.notificationPointId = '6';
      console.log("데이터 사용량 확인됨, notificationPointId를 6으로 설정");
    }
  }

  return {
    esimData,
    qrCodeImage,
    usageData,
    statusMessage: getStatusMessage(esimData.notificationPointId),
    activationCode: extractActivationCode(esimData.qrCode),
  };
}

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'", "https://fonts.googleapis.com"],
      fontSrc: ["'self'", "https://fonts.gstatic.com"],
      scriptSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", "data:", "https:"],
      connectSrc: ["'self'"]
    }
  }
}));
app.use(cors());
app.use(express.json({ limit: "1mb" }));

// 세션 설정
app.use(session({
  secret: config.sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: {
    secure: false, // HTTPS 사용 시 true로 변경
    maxAge: 24 * 60 * 60 * 1000 // 24시간
  }
}));

// IP 화이트리스트 (전역) - 테스트용으로 임시 비활성화
// app.use(ipWhitelistMiddleware);

// eSIM QR 코드 상세페이지 라우트 (동적 라우팅으로 변경)
app.get("/esim/qr-detail", async (req, res) => {
  try {
    const { transId, cid } = req.query;

    if (!transId && !cid) {
      return res.status(400).render('esim-message', {
        title: 'eSIM 정보 없음 - RingTalk',
        headingClass: 'error',
        heading: '잘못된 접근입니다',
        lines: [
          'transId 또는 cid 파라미터가 필요합니다.',
          '예: /esim/qr-detail?transId=TXN123 또는 /esim/qr-detail?cid=CUSTOMER001',
        ],
      });
    }

    // DB에서 데이터 조회
    const esimData = await getEsimProgressData(transId || cid);

    if (!esimData) {
      return res.render('esim-message', {
        title: 'eSIM 정보 없음 - RingTalk',
        headingClass: 'not-found',
        heading: 'eSIM 정보를 찾을 수 없습니다',
        lines: [
          '올바른 링크로 접근해주세요.',
          `조회한 ID: ${transId || cid}`,
        ],
      });
    }

    // eSIM 상세 페이지 렌더링 (EJS)
    const viewModel = await buildEsimDetailViewModel(esimData);
    res.render('esim-detail', viewModel);

  } catch (err) {
    console.error('eSIM QR 상세페이지 로딩 실패:', err);
    res.status(500).render('esim-message', {
      title: '오류 발생 - RingTalk',
      headingClass: 'error',
      heading: '오류가 발생했습니다',
      lines: ['잠시 후 다시 시도해주세요.'],
    });
  }
});

// eSIM 파트너사 콜백 라우트 마운트
// 주의: 경로 자체("/api/joytel")는 파트너사에 이미 등록된 실 서비스 콜백 URL과 맞물려 있어
// 임의로 변경하면 운영 중인 콜백 수신이 끊깁니다. 실제 배포 시에는 파트너사와 협의 후 변경하세요.
app.use("/api/joytel", esimCallbacksRouter);

// 정적 파일 제공 설정 수정 (라우트 설정 후에 배치)
app.use(express.static(path.join(process.cwd(), 'public'), {
  setHeaders: (res, path) => {
    if (path.endsWith('.css')) {
      res.setHeader('Content-Type', 'text/css');
    }
  }
}));

// 로그인 API
app.post("/api/login", (req, res) => {
  const { username, password } = req.body;

  // 관리자 계정 정보 (.env에서 로드)
  const ADMIN_USERNAME = config.admin.username;
  const ADMIN_PASSWORD = config.admin.password;

  if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
    req.session.isAuthenticated = true;
    req.session.username = username;
    res.json({ 
      success: true, 
      message: '로그인 성공',
      username: username 
    });
  } else {
    res.status(401).json({ 
      success: false, 
      message: '아이디 또는 비밀번호가 올바르지 않습니다.' 
    });
  }
});

// 로그아웃 API
app.post("/api/logout", (req, res) => {
  req.session.destroy((err) => {
    if (err) {
      return res.status(500).json({ success: false, message: '로그아웃 실패' });
    }
    res.json({ success: true, message: '로그아웃 성공' });
  });
});

// 로그인 상태 확인 API
app.get("/api/auth/status", (req, res) => {
  res.json({
    isAuthenticated: !!(req.session && req.session.isAuthenticated),
    username: req.session?.username || null
  });
});

app.get("/health", (req, res) => {
  res.json({ status: "ok", env: config.env });
});

// 관리자 대시보드 API
app.get("/api/admin/orders", requireAuth, async (req, res) => {
  try {
    await mysqlClient.connect();
    
    // 전체 주문 목록 조회 (최신 순)
    const ordersQuery = `
      SELECT 
        productOrderId, orderId, ordererName, ordererTel, email, 
        productName, day, quantity, snCode, QR, orderTid, kakaoSendYN, 
        created_at, updated_at, snPin
      FROM user 
      ORDER BY created_at DESC
      LIMIT 1000
    `;
    
    const [orders] = await mysqlClient.connection.execute(ordersQuery);
    
    // 통계 정보 계산 (한국시간 기준)
    const statsQuery = `
      SELECT 
        COUNT(*) as total,
        SUM(CASE WHEN QR IS NOT NULL AND QR != '' THEN 1 ELSE 0 END) as sent,
        SUM(CASE WHEN QR IS NULL OR QR = '' THEN 1 ELSE 0 END) as pending,
        SUM(CASE WHEN DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) = DATE(CONVERT_TZ(NOW(), '+00:00', '+09:00')) THEN 1 ELSE 0 END) as today,
        SUM(CASE WHEN DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) = DATE(CONVERT_TZ(NOW(), '+00:00', '+09:00')) THEN COALESCE(CAST(cost AS UNSIGNED), 0) ELSE 0 END) as todayRevenue
      FROM user
    `;
    
    const [statsRows] = await mysqlClient.connection.execute(statsQuery);
    const stats = statsRows[0];
    
    res.json({
      orders: orders,
      stats: {
        total: parseInt(stats.total),
        sent: parseInt(stats.sent),
        pending: parseInt(stats.pending),
        today: parseInt(stats.today),
        todayRevenue: parseInt(stats.todayRevenue) || 0
      },
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('관리자 대시보드 데이터 조회 실패:', error);
    res.status(500).json({ 
      error: '데이터를 불러오는데 실패했습니다.',
      message: error.message 
    });
  } finally {
    await mysqlClient.disconnect();
  }
});

// eSIM URL 조회 API
app.get("/api/admin/esim-urls/:productOrderId", requireAuth, async (req, res) => {
  try {
    await mysqlClient.connect();
    
    const { productOrderId } = req.params;
    
    // user 테이블에서 snPin 조회
    const userQuery = `SELECT snPin FROM user WHERE productOrderId = ?`;
    const [userRows] = await mysqlClient.connection.execute(userQuery, [productOrderId]);
    
    if (userRows.length === 0) {
      return res.json({ urls: [] });
    }
    
    const snPinString = userRows[0].snPin;
    if (!snPinString) {
      return res.json({ urls: [] });
    }
    
    // snPin을 |로 분리
    const snPins = snPinString.split('|').map(pin => pin.trim()).filter(pin => pin);
    
    // 각 snPin에 대해 transId 조회
    const urls = [];
    for (const snPin of snPins) {
      const transIdQuery = `SELECT transId FROM esim_progress_notifications WHERE snPin = ?`;
      const [transIdRows] = await mysqlClient.connection.execute(transIdQuery, [snPin]);
      
      if (transIdRows.length > 0) {
        urls.push({
          snPin: snPin,
          transId: transIdRows[0].transId,
          url: `${config.publicBaseUrl}/esim/qr-detail?transId=${transIdRows[0].transId}`
        });
      }
    }
    
    res.json({ urls });
    
  } catch (error) {
    console.error('eSIM URL 조회 실패:', error);
    res.json({ urls: [] });
  } finally {
    await mysqlClient.disconnect();
  }
});

// 수동 발송 API
app.post("/api/admin/manual-dispatch", async (req, res) => {
  try {
    const { name, tel, product, days, quantity } = req.body;
    
    // 필수 필드 검증
    if (!name || !tel || !product || !days || !quantity) {
      return res.status(400).json({ 
        success: false, 
        message: '모든 필드를 입력해주세요.' 
      });
    }
    
    await mysqlClient.connect();
    
    // 수동 발송 데이터 삽입
    const insertQuery = `
      INSERT INTO user (
        productOrderId, orderId, ordererName, ordererTel, email, 
        productName, day, quantity, snPin, QR, created_at, kakaoSendYN, dispatchStatus
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'N', 0)
    `;
    
    // 중복되지 않는 productOrderId 생성 (현재 시간 기반)
    const timestamp = Date.now();
    const uniqueProductOrderId = `manual_${timestamp}`;
    
    const [result] = await mysqlClient.connection.execute(insertQuery, [
      uniqueProductOrderId, // productOrderId
      'manual', // orderId
      name,     // ordererName
      tel,      // ordererTel
      'example@example.com', // email
      product,  // productName
      days,     // day
      quantity, // quantity
      null,     // snPin
      null      // QR
    ]);
    
    console.log(`수동 발송 데이터 저장 완료: ID=${result.insertId}`);
    
    res.json({
      success: true,
      message: '수동 발송 데이터가 성공적으로 저장되었습니다.',
      insertId: result.insertId
    });
    
  } catch (error) {
    console.error('수동 발송 데이터 저장 실패:', error);
    res.status(500).json({ 
      success: false, 
      message: '수동 발송 데이터 저장에 실패했습니다.',
      error: error.message 
    });
  } finally {
    await mysqlClient.disconnect();
  }
});

// 일별 매출 조회 API
app.get("/api/admin/daily-revenue", requireAuth, async (req, res) => {
  try {
    await mysqlClient.connect();
    
    const { startDate, endDate } = req.query;
    
    if (!startDate || !endDate) {
      return res.status(400).json({ error: '시작일과 종료일이 필요합니다.' });
    }
    
    // 일별 매출 조회 쿼리 (한국시간 기준)
    const dailyRevenueQuery = `
      SELECT 
        DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) as date,
        COUNT(*) as orderCount,
        SUM(COALESCE(CAST(cost AS UNSIGNED), 0)) as dailyRevenue
      FROM user 
      WHERE DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) BETWEEN ? AND ?
      GROUP BY DATE(CONVERT_TZ(created_at, '+00:00', '+09:00'))
      ORDER BY date DESC
    `;
    
    const [dailyRevenueRows] = await mysqlClient.connection.execute(dailyRevenueQuery, [startDate, endDate]);
    
    res.json({
      dailyRevenue: dailyRevenueRows,
      startDate,
      endDate,
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('일별 매출 조회 실패:', error);
    res.status(500).json({ 
      error: '일별 매출 데이터를 불러오는데 실패했습니다.',
      message: error.message 
    });
  } finally {
    await mysqlClient.disconnect();
  }
});

// 로그인 페이지
app.get("/login", redirectIfAuthenticated, (req, res) => {
  res.sendFile(path.join(process.cwd(), 'public', 'login.html'));
});

app.get("/", requireAuth, (req, res) => {
  res.sendFile(path.join(process.cwd(), 'public', 'dashboard.html'));
});

app.listen(config.port,'0.0.0.0', () => {
  console.log(`eSIM server listening on port ${config.port}`);
});


