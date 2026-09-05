import mysql from 'mysql2/promise';
import { MySQLClient } from './src/clients/mysqlClient.js';
import { BizPPurioClient } from './src/clients/bizppurioClient.js';
import { config } from './src/config/index.js';

// Mockup 데이터 생성
function generateMockupData() {
  return {
    snPinString: 'MOCK123|MOCK456|MOCK789',
    qrCodeString: '|',
    // 중복 데이터를 포함한 테스트 케이스
    snPinStringWithDuplicates: 'MOCK123|MOCK456|MOCK123',
    qrCodeStringWithDuplicates: 'L1:MOCKQR001|L1:MOCKQR002|L1:MOCKQR001',
    // 여러 개의 QR 코드
    multipleQrCodes: 'L1:QR001|L1:QR002|L1:QR003|L1:QR004|L1:QR005',
    multipleSnPins: 'SN001|SN002|SN003|SN004|SN005'
  };
}

// 테스트용 사용자 데이터 생성 (실제 DB에 미리 존재해야 함)
async function createMockUser(connection, snPin, qrCode = null) {
  const orderTid = `TEST${Date.now()}`;
  const orderId = `ORDER${Date.now()}`;
  
  // 기존 테스트 사용자 삭제
  await connection.execute(`
    DELETE FROM user WHERE orderId LIKE 'ORDER%' AND created_at < DATE_SUB(NOW(), INTERVAL 1 DAY)
  `).catch(() => {});
  
  const query = `
    INSERT INTO user (
      orderTid, orderId, productOrderId, ordererTel, ordererName, 
      productName, day, quantity, snPin, QR, kakaoSendYN, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
  `;
  
  const values = [
    orderTid,
    orderId,
    orderId,  // productOrderId는 orderId와 동일하게 설정
    '01012345678',
    '테스트 사용자',
    '테스트 eSIM 패키지',
    3,
    3,
    snPin,
    qrCode,
    'N'
  ];
  
  const [result] = await connection.execute(query, values);
  console.log('✅ Mockup 사용자 생성 완료:', { orderTid, orderId, snPin, qrCode });
  
  return { orderTid, orderId, snPin };
}

// QR 코드 업데이트 로직 (원본 함수를 그대로 복사)
async function updateQrCodeByTransId(connection, bizppurioClient, snPinString, qrCodeString) {
  try {
    // 파라미터 검증
    if (!snPinString) {
      throw new Error('snPin이 필요합니다.');
    }
    if (!qrCodeString) {
      throw new Error('qrCode가 필요합니다.');
    }

    // 연결 상태 확인
    await connection.ping().catch(() => {});

    // | 구분자로 분리하고 빈 문자열 제거
    const snPins = snPinString.split('|').filter(sn => sn.trim());
    const qrCodes = qrCodeString.split('|').filter(qr => qr.trim());

    // 중복 제거된 QR 코드들만 필터링
    const uniqueQrCodes = [...new Set(qrCodes)];
    const filteredQrCodeString = uniqueQrCodes.join('|');

    console.log('중복 제거된 QR 코드:', {
      originalCount: qrCodes.length,
      uniqueCount: uniqueQrCodes.length,
      removedDuplicates: qrCodes.length - uniqueQrCodes.length
    });

    console.log('QR 코드 업데이트 쿼리 실행:', { 
      snPinCount: snPins.length, 
      qrCodeCount: uniqueQrCodes.length,
      snPins: snPins,
      qrCodes: uniqueQrCodes
    });

    // snPin 전체 문자열로 사용자 찾기 (| 구분자로 저장된 경우)
    const query = `
      UPDATE user 
      SET QR = ?, 
          updated_at = NOW() 
      WHERE snPin = ?
    `;

    const [result] = await connection.execute(query, [filteredQrCodeString, snPinString]);
    let updatedCount = result.affectedRows;
    
    if (updatedCount > 0) {
      console.log(`✅ snPin ${snPinString}의 QR 코드가 업데이트되었습니다.`);
    } else {
      // 전체 문자열로 찾지 못한 경우, 개별 snPin으로 시도
      console.log('⚠️ 전체 snPin 문자열로 찾지 못함, 개별 snPin으로 시도');
      
      for (let i = 0; i < snPins.length; i++) {
        const snPin = snPins[i];
        const qrCode = uniqueQrCodes[i] || '';

        const individualQuery = `
          UPDATE user 
          SET QR = CASE 
            WHEN QR IS NULL OR QR = '' THEN ?
            ELSE CONCAT(QR, '|', ?)
          END,
          updated_at = NOW() 
          WHERE snPin LIKE ? OR snPin LIKE ? OR snPin LIKE ? OR snPin = ?
        `;

        const likePatterns = [
          `%|${snPin}|%`,
          `${snPin}|%`,
          `%|${snPin}`,
          snPin
        ];

        const [individualResult] = await connection.execute(individualQuery, [
          qrCode, qrCode, ...likePatterns
        ]);
        
        if (individualResult.affectedRows > 0) {
          updatedCount += individualResult.affectedRows;
          console.log(`✅ snPin ${snPin}의 QR 코드가 업데이트되었습니다.`);
        } else {
          console.warn(`⚠️ snPin ${snPin}에 해당하는 사용자를 찾을 수 없습니다.`);
        }
      }
    }

    if (updatedCount === 0) {
      throw new Error(`모든 snPin에 대해 업데이트할 수 있는 사용자를 찾을 수 없습니다.`);
    }

    console.log(`✅ 총 ${updatedCount}개의 snPin에 대해 QR 코드가 업데이트되었습니다.`);

    // QR 코드 저장 성공 후 BizPPurio API 호출 테스트
    try {
      let userQuery = `
        SELECT kakaoSendYN, orderId, ordererTel, ordererName, productName, day FROM user WHERE snPin = ?
      `;
      let [userRows] = await connection.execute(userQuery, [snPinString]);
      
      if (userRows.length === 0) {
        const validSnPin = snPins.find(sn => sn.trim());
        if (validSnPin) {
          userQuery = `
            SELECT kakaoSendYN, orderId, ordererTel, ordererName, productName, day FROM user 
            WHERE snPin LIKE ? OR snPin LIKE ? OR snPin LIKE ? OR snPin = ?
          `;
          const likePatterns = [
            `%|${validSnPin}|%`,
            `${validSnPin}|%`,
            `%|${validSnPin}`,
            validSnPin
          ];
          [userRows] = await connection.execute(userQuery, likePatterns);
        }
      }
      
      if (userRows.length > 0) {
        const user = userRows[0];
        const orderId = user.orderId;
        const phoneNumber = user.ordererTel;
        const productName = user.productName || 'eSIM 해외 데이터';
        const day = user.day || 1;
        const kakaoSendYN = user.kakaoSendYN;
        
        console.log('📱 BizPPurio API 호출 정보:', { kakaoSendYN, phoneNumber });
        console.log('📝 테스트 모드에서는 실제 API 호출을 건너뜁니다.');
        
        // 실제 API 호출 대신 로그만 출력 (테스트 시 안전)
        // await bizppurioClient.sendQrCodeMessage(transId, filteredQrCodeString, phoneNumber, orderId, productName, day);
        
        console.log('✅ BizPPurio API 호출 시뮬레이션 완료');
      } else {
        console.warn('⚠️ 사용자 정보를 찾을 수 없어 BizPPurio API 호출을 건너뜁니다.');
      }
    } catch (bizppurioError) {
      console.error('❌ BizPPurio API 호출 실패:', bizppurioError);
    }

    return { updatedCount, totalCount: snPins.length };
  } catch (error) {
    console.error('❌ QR 코드 업데이트 실패:', error);
    throw error;
  }
}

// 메인 테스트 함수
async function runTest() {
  console.log('🚀 QR 코드 업데이트 테스트 시작\n');
  
  const connection = await mysql.createConnection({
    host: config.db.host,
    user: config.db.user,
    password: config.db.password,
    database: config.db.database,
    port: config.db.port
  });

  const bizppurioClient = new BizPPurioClient();
  const mockData = generateMockupData();

  try {
    console.log('📦 Mockup 데이터 생성...\n');
    console.log('Mockup 데이터:', JSON.stringify(mockData, null, 2));
    console.log('\n' + '='.repeat(50) + '\n');

    // 테스트 케이스 1: 기본 QR 코드 업데이트
    console.log('📋 테스트 케이스 1: 기본 QR 코드 업데이트\n');
    console.log('사용자 생성 중...');
    const testUser1 = await createMockUser(connection, mockData.snPinString);
    console.log('사용자 생성 완료:', testUser1);
    await updateQrCodeByTransId(connection, bizppurioClient, mockData.snPinString, mockData.qrCodeString);
    console.log('\n' + '='.repeat(50) + '\n');

    console.log('✅ 모든 테스트 완료!');
    
  } catch (error) {
    console.error('❌ 테스트 실패:', error.message);
  } finally {
    await connection.end();
    console.log('\n🔌 데이터베이스 연결 종료');
  }
}

// 스크립트 실행
runTest().catch(console.error);
