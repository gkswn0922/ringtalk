import mysql from 'mysql2/promise';
import { BizPPurioClient } from './bizppurioClient.js';
import { config } from '../config/index.js';

const dbConfig = {
  host: config.db.host,
  user: config.db.user,
  password: config.db.password,
  database: config.db.database,
  port: config.db.port,
  enableKeepAlive: true,
  // 연결 풀 설정 추가
  acquireTimeout: 60000,
  timeout: 60000,
  reconnect: true
};

export class MySQLClient {
  constructor() {
    this.connection = null;
    this.bizppurioClient = new BizPPurioClient();
  }

  async connect() {
    try {
      // 기존 연결이 있으면 정리
      if (this.connection) {
        try {
          await this.connection.end();
        } catch (error) {
          console.log('기존 연결 정리 중 오류 (무시됨):', error.message);
        }
      }
      
      this.connection = await mysql.createConnection(dbConfig);
      console.log('MySQL 연결 성공');
    } catch (error) {
      console.error('MySQL 연결 실패:', error);
      throw error;
    }
  }

  async disconnect() {
    if (this.connection) {
      try {
        await this.connection.end();
        console.log('MySQL 연결 종료');
      } catch (error) {
        console.log('연결 종료 중 오류 (무시됨):', error.message);
      } finally {
        this.connection = null;
      }
    }
  }

  // 연결 상태 확인 및 재연결
  async ensureConnection() {
    try {
      // 연결이 없거나 끊어진 경우
      if (!this.connection) {
        await this.connect();
        return;
      }

      // 연결 상태 확인
      await this.connection.ping();
    } catch (error) {
      console.log('연결 상태 확인 실패, 재연결 시도:', error.message);
      await this.connect();
    }
  }

  async updateSnPinByOrderTid(orderTid, snPin) {
    try {
      await this.ensureConnection();

      const query = `
        UPDATE user 
        SET snPin = ?, 
            updated_at = NOW() 
        WHERE orderTid = ?
      `;

      const [result] = await this.connection.execute(query, [snPin, orderTid]);
      
      if (result.affectedRows === 0) {
        throw new Error(`orderTid ${orderTid}에 해당하는 주문을 찾을 수 없습니다.`);
      }

      console.log(`orderTid ${orderTid}의 snPin이 ${snPin}으로 업데이트되었습니다.`);
      return result;
    } catch (error) {
      console.error('snPin 업데이트 실패:', error);
      throw error;
    }
  }

  async updateSnCodeByOrderTid(orderTid, snCode) {
    try {
      await this.ensureConnection();

      const query = `
        UPDATE user 
        SET snCode = ?, 
            updated_at = NOW() 
        WHERE orderTid = ?
      `;

      const [result] = await this.connection.execute(query, [snCode, orderTid]);
      
      if (result.affectedRows === 0) {
        throw new Error(`orderTid ${orderTid}에 해당하는 주문을 찾을 수 없습니다.`);
      }

      console.log(`orderTid ${orderTid}의 snCode가 ${snCode}으로 업데이트되었습니다.`);
      return result;
    } catch (error) {
      console.error('snCode 업데이트 실패:', error);
      throw error;
    }
  }

  async updateQrCodeByTransId(snPinString, qrCodeString) {
    try {
      // 파라미터 검증
      if (!snPinString) {
        throw new Error('snPin이 필요합니다.');
      }
      if (!qrCodeString) {
        throw new Error('qrCode가 필요합니다.');
      }

      await this.ensureConnection();

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

      const [result] = await this.connection.execute(query, [filteredQrCodeString, snPinString]);
      let updatedCount = result.affectedRows;
      
      if (updatedCount > 0) {
        console.log(`snPin ${snPinString}의 QR 코드가 업데이트되었습니다.`);
      } else {
        // 전체 문자열로 찾지 못한 경우, 개별 snPin으로 시도
        console.log('전체 snPin 문자열로 찾지 못함, 개별 snPin으로 시도');
        
        for (let i = 0; i < snPins.length; i++) {
          const snPin = snPins[i];
          const qrCode = uniqueQrCodes[i] || ''; // 중복 제거된 QR 코드 사용

          // LIKE 패턴으로 snPin이 포함된 레코드 찾기
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
            `%|${snPin}|%`,  // 중간에 있는 경우
            `${snPin}|%`,    // 맨 앞에 있는 경우  
            `%|${snPin}`,    // 맨 뒤에 있는 경우
            snPin            // 단독인 경우
          ];

          const [individualResult] = await this.connection.execute(individualQuery, [
            qrCode, qrCode, ...likePatterns
          ]);
          
          if (individualResult.affectedRows > 0) {
            updatedCount += individualResult.affectedRows;
            console.log(`snPin ${snPin}의 QR 코드가 업데이트되었습니다.`);
          } else {
            console.warn(`snPin ${snPin}에 해당하는 사용자를 찾을 수 없습니다.`);
          }
        }
      }

      if (updatedCount === 0) {
        throw new Error(`모든 snPin에 대해 업데이트할 수 있는 사용자를 찾을 수 없습니다.`);
      }

      console.log(`총 ${updatedCount}개의 snPin에 대해 QR 코드가 업데이트되었습니다.`);

      // QR 코드 저장 성공 후 BizPPurio API 호출
      try {
        // 먼저 전체 snPin 문자열로 사용자 정보 조회
        let userQuery = `
          SELECT kakaoSendYN, orderId, ordererTel, ordererName, productName, day FROM user WHERE snPin = ?
        `;
        let [userRows] = await this.connection.execute(userQuery, [snPinString]);
        
        // 전체 문자열로 찾지 못한 경우, 개별 snPin으로 시도
        if (userRows.length === 0) {
          const validSnPin = snPins.find(sn => sn.trim());
          if (validSnPin) {
            userQuery = `
              SELECT kakaoSendYN, orderId, ordererTel, ordererName, productName, day FROM user 
              WHERE snPin LIKE ? OR snPin LIKE ? OR snPin LIKE ? OR snPin = ?
            `;
            const likePatterns = [
              `%|${validSnPin}|%`,  // 중간에 있는 경우
              `${validSnPin}|%`,    // 맨 앞에 있는 경우  
              `%|${validSnPin}`,    // 맨 뒤에 있는 경우
              validSnPin            // 단독인 경우
            ];
            [userRows] = await this.connection.execute(userQuery, likePatterns);
          }
        }
        
        if (userRows.length > 0) {
          const user = userRows[0];
          const orderId = user.orderId;
          const phoneNumber = user.ordererTel;
          const productName = user.productName || 'eSIM 해외 데이터';
          const day = user.day || 1;
          const kakaoSendYN = user.kakaoSendYN;
          
          // kakaoSendYN이 'N'이고 전화번호가 있는 경우만 메시지 전송
          console.log('kakaoSendYN:', kakaoSendYN);
          console.log('phoneNumber:', phoneNumber);
          if (phoneNumber && kakaoSendYN === 'N') {
            console.log('BizPPurio API 호출 시작:', { 
              snPinCount: snPins.length,
              qrCodeCount: uniqueQrCodes.length,
              phoneNumber, 
              orderId, 
              productName,
              kakaoSendYN
            });

            try {
              const transIdQuery = `
                SELECT transId FROM esim_progress_notifications WHERE qrCode = ?
              `;
              const [transIdRows] = await this.connection.execute(transIdQuery, [filteredQrCodeString]);
              
              if (transIdRows.length === 1) {
                const transId = transIdRows[0].transId;
                
                // BizPPurio API 호출 (필터링된 QR 코드 문자열 사용)
                await this.bizppurioClient.sendQrCodeMessage(transId, filteredQrCodeString, phoneNumber, orderId, productName, day);
               
                console.log('BizPPurio API 호출 완료');
                
                // QR 코드 개수와 quantity가 일치하는지 확인 후 kakaoSendYN을 'Y'로 업데이트
                const quantityCheckQuery = `
                  SELECT quantity, QR FROM user WHERE orderId = ?
                `;
                const [quantityRows] = await this.connection.execute(quantityCheckQuery, [orderId]);
                
                if (quantityRows.length > 0) {
                  const userQuantity = quantityRows[0].quantity;
                  const userQrCodes = quantityRows[0].QR;
                  
                  // QR 코드 개수 계산 (| 구분자로 분리)
                  const qrCodeCount = userQrCodes ? userQrCodes.split('|').filter(qr => qr.trim()).length : 0;
                  
                  console.log('QR 코드 개수와 quantity 비교:', {
                    orderId,
                    qrCodeCount,
                    userQuantity,
                    isMatch: qrCodeCount === userQuantity
                  });
                  
                  // QR 코드 개수와 quantity가 일치할 때만 업데이트
                  if (qrCodeCount === userQuantity) {
                    const updateQuery = `
                      UPDATE user 
                      SET kakaoSendYN = 'Y', updated_at = NOW() 
                      WHERE orderId = ?
                    `;
                    
                    const [updateResult] = await this.connection.execute(updateQuery, [orderId]);
                    
                    if (updateResult.affectedRows > 0) {
                      console.log('QR 코드 개수와 quantity 일치, kakaoSendYN 업데이트 완료:', orderId);
                    } else {
                      console.warn('kakaoSendYN 업데이트 실패:', orderId);
                    }
                  } else {
                    console.log('QR 코드 개수와 quantity 불일치로 kakaoSendYN 업데이트 건너뛰기:', {
                      orderId,
                      qrCodeCount,
                      userQuantity
                    });
                  }
                } else {
                  console.warn('사용자 정보를 찾을 수 없음:', orderId);
                }
                
              } else if (transIdRows.length === 0) {
                console.log('transId를 찾을 수 없음:', filteredQrCodeString);
              } else {
                console.log('transId가 여러 개 발견됨, 건너뛰기:', filteredQrCodeString, '개수:', transIdRows.length);
              }
              
            } catch (apiError) {
              console.error('BizPPurio API 호출 실패:', apiError);
              // API 호출 실패 시에도 kakaoSendYN은 업데이트하지 않음 (재시도 가능하도록)
            }
            
          } else if (phoneNumber && kakaoSendYN === 'Y') {
            console.log('이미 카카오톡 메시지가 전송되어 건너뛰기:', orderId);
          } else {
            console.warn('사용자 전화번호가 없어 BizPPurio API 호출을 건너뜁니다.');
          }
        } else {
          console.warn('사용자 정보를 찾을 수 없어 BizPPurio API 호출을 건너뜁니다.');
        }
      } catch (bizppurioError) {
        console.error('BizPPurio API 호출 실패:', bizppurioError);
        // BizPPurio API 실패해도 QR 코드 저장은 성공으로 처리
      }

      return { updatedCount, totalCount: snPins.length };
    } catch (error) {
      console.error('QR 코드 업데이트 실패:', error);
      throw error;
    }
  }
}
