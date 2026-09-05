import { MySQLClient } from '../clients/mysqlClient.js';
import { BizPPurioClient } from '../clients/bizppurioClient.js';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

export class OrderScheduler {
  constructor() {
    this.mysqlClient = new MySQLClient();
    this.bizppurioClient = new BizPPurioClient();
    this.isRunning = false;
    this.intervalId = null;
  }

  /**
   * 스케줄러 시작 - 1분마다 실행
   */
  start() {
    if (this.isRunning) {
      console.log('스케줄러가 이미 실행 중입니다.');
      return;
    }

    console.log('주문 스케줄러 시작 - 1분마다 실행됩니다.');
    this.isRunning = true;

    // 즉시 한 번 실행
    this.processOrders();

    // 1분(60000ms)마다 실행
    this.intervalId = setInterval(() => {
      this.processOrders();
    }, 60000);
  }

  /**
   * 스케줄러 중지
   */
  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.isRunning = false;
    console.log('주문 스케줄러가 중지되었습니다.');
  }

  /**
   * 주문 처리 메인 로직
   */
  async processOrders() {
    try {
      console.log('\n=== 주문 처리 시작 ===', new Date().toISOString());

      // 1. NaverCommerceApiClient를 통해 주문 정보 가져오기
      console.log('1. 네이버 커머스 API에서 주문 정보 조회 중...');
      const orderInfos = await this.getOrderInfosFromNaverAPI();

      if (orderInfos && orderInfos.length > 0) {
        console.log(`${orderInfos.length}개의 주문 정보를 가져왔습니다.`);

        // 2. 중복 체크 후 저장
        console.log('2. 중복 체크 후 데이터베이스에 저장 중...');
        await this.saveOrderInfosWithDuplicateCheck(orderInfos);
      } else {
        console.log('새로운 주문 정보가 없습니다.');
      }

      // 3. 카카오 메시지 전송 처리
      console.log('3. 카카오 메시지 전송 대상 확인 중...');
      await this.processKakaoMessages();

      // 4. 발송 처리 (새로 추가)
      console.log('4. 발송 처리 대상 확인 중...');
      await this.processDispatchOrders();

      console.log('=== 주문 처리 완료 ===\n');

    } catch (error) {
      console.error('주문 처리 중 오류 발생:', error);
    }
  }

  /**
   * NaverCommerceApiClient Java 프로그램 실행하여 주문 정보 가져오기
   */
  async getOrderInfosFromNaverAPI() {
    try {
      // Java 프로그램 실행
      const { stdout, stderr } = await execAsync('java -cp "target/classes:lib/mysql-connector-j-9.4.0.jar" org.mindrot.NaverCommerceApiClient', {
        cwd: process.cwd() + '/..'  // jBCrypt 폴더로 이동
      });

      if (stderr) {
        console.error('Java 실행 stderr:', stderr);
      }

      console.log('Java 실행 결과:', stdout);

      // Java 출력에서 JSON 데이터 추출
      return this.parseOrderDataFromJavaOutput(stdout);

    } catch (error) {
      console.error('NaverCommerceApiClient 실행 실패:', error);
      return [];
    }
  }

  /**
   * Java 출력에서 JSON 주문 데이터 파싱
   */
  parseOrderDataFromJavaOutput(stdout) {
    try {
      const lines = stdout.split('\n');
      let isDataSection = false;
      let jsonData = '';

      for (const line of lines) {
        if (line.trim() === 'NAVER_ORDER_DATA_START') {
          isDataSection = true;
          continue;
        }
        if (line.trim() === 'NAVER_ORDER_DATA_END') {
          break;
        }
        if (isDataSection) {
          jsonData += line;
        }
      }

      if (jsonData.trim()) {
        const orderInfos = JSON.parse(jsonData.trim());
        console.log(`Java에서 ${orderInfos.length}개의 주문 정보를 파싱했습니다.`);
        return orderInfos;
      } else {
        console.log('Java 출력에서 주문 데이터를 찾을 수 없습니다.');
        return [];
      }

    } catch (error) {
      console.error('Java 출력 파싱 실패:', error);
      console.error('stdout:', stdout);
      return [];
    }
  }

  /**
   * 최근에 저장된 주문 정보 조회
   */
  async getRecentOrderInfos() {
    try {
      await this.mysqlClient.connect();

      const query = `
        SELECT productOrderId, orderId, ordererName, ordererTel, email, 
               productName, day, quantity, orderTid, kakaoSendYN, created_at
        FROM user 
        WHERE DATE(created_at) = CURDATE()
        ORDER BY created_at DESC
      `;

      const [rows] = await this.mysqlClient.connection.execute(query);
      return rows;

    } catch (error) {
      console.error('최근 주문 정보 조회 실패:', error);
      return [];
    }
  }

  /**
   * 중복 체크 후 주문 정보 저장
   */
  async saveOrderInfosWithDuplicateCheck(orderInfos) {
    try {
      await this.mysqlClient.connect();

      for (const orderInfo of orderInfos) {
        // 중복 체크
        const duplicateCheckQuery = `
          SELECT COUNT(*) as count 
          FROM user 
          WHERE productOrderId = ?
        `;

        const [duplicateResult] = await this.mysqlClient.connection.execute(
          duplicateCheckQuery, 
          [orderInfo.productOrderId, orderInfo.orderId]
        );

        if (duplicateResult[0].count > 0) {
          console.log(`중복된 주문 건너뛰기: productOrderId=${orderInfo.productOrderId}, orderId=${orderInfo.orderId}`);
          continue;
        }
        
        if(!orderInfo.productName || (!orderInfo.productName.includes('베트남') && !orderInfo.productName.includes('일본') && !orderInfo.productName.includes('중국') && !orderInfo.productName.includes('말레이시아')&& !orderInfo.productName.includes('필리핀') && !orderInfo.productName.includes('인도네시아') && !orderInfo.productName.includes('싱가폴') && !orderInfo.productName.includes('홍마') && !orderInfo.productName.includes('미국') && !orderInfo.productName.includes('태국') && !orderInfo.productName.includes('대만') && !orderInfo.productName.includes('터키') && !orderInfo.productName.includes('호뉴') && !orderInfo.productName.includes('유럽') && !orderInfo.productName.includes('아프리카'))) {
            console.log(`베트남 또는 일본 상품이 아닌 주문 건너뛰기: productOrderId=${orderInfo.productOrderId}, orderId=${orderInfo.orderId}, ordererName=${orderInfo.ordererName}, productName=${orderInfo.productName}`);
          continue;
        }

        // 중복이 아니면 저장
        const insertQuery = `
          INSERT INTO user (
            productOrderId, orderId, ordererName, ordererTel, email, 
            productName, day, quantity, snPin, QR, created_at, kakaoSendYN, dispatchStatus
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'N', 0)
        `;

        

        const [_insertResult] = await this.mysqlClient.connection.execute(insertQuery, [
          orderInfo.productOrderId,
          orderInfo.orderId,
          orderInfo.ordererName,
          parseInt(orderInfo.ordererTel.replace(/[^0-9]/g, '')) || 0,
          orderInfo.email || 'example@example.com',
          orderInfo.productName || 'eSIM 상품',
          orderInfo.day || 1,
          orderInfo.quantity || 1,
          null, // snPin
          null  // QR
        ]);

        console.log(`새 주문 저장 완료: productOrderId=${orderInfo.productOrderId}`);
      }

    } catch (error) {
      console.error('주문 정보 저장 실패:', error);
    }
  }

  /**
   * 카카오 메시지 전송 처리
   */
  async processKakaoMessages() {
    try {
      await this.mysqlClient.connect();

      // orderTid가 null이고 kakaoSendYN이 'N'인 레코드 조회
      const targetQuery = `
        SELECT productOrderId, orderId, ordererName, ordererTel, email, 
               productName, day, quantity
        FROM user 
        WHERE orderTid IS NULL AND kakaoSendYN = 'N'
      `;

      const [targetRows] = await this.mysqlClient.connection.execute(targetQuery);

      if (targetRows.length === 0) {
        console.log('카카오 메시지 전송 대상이 없습니다.');
        return;
      }

      console.log(`${targetRows.length}개의 카카오 메시지 전송 대상을 찾았습니다.`);

      for (const row of targetRows) {
        try {
          console.log(`\n처리 중: productOrderId=${row.productOrderId}`);

          // CustomerApiClient 호출하여 orderTid 생성 및 업데이트
          await this.callCustomerApiClient(row);

          // orderTid가 업데이트되었는지 확인
          const updatedOrderQuery = `
            SELECT orderTid FROM user WHERE productOrderId = ?
          `;
          const [updatedRows] = await this.mysqlClient.connection.execute(
            updatedOrderQuery, 
            [row.productOrderId]
          );

          if (updatedRows.length > 0 && updatedRows[0].orderTid) {
            console.log(`orderTid 업데이트 확인됨: ${updatedRows[0].orderTid}`);
            
            // BizPPurio 메시지 전송
            //await this.sendBizPPurioMessage(row);
          }

        } catch (error) {
          console.error(`productOrderId ${row.productOrderId} 처리 중 오류:`, error);
        }
      }

    } catch (error) {
      console.error('카카오 메시지 처리 실패:', error);
    }
  }

  /**
   * CustomerApiClient Java 프로그램 호출
   */
  async callCustomerApiClient(_orderData) {
    try {
      console.log('CustomerApiClient 호출 중...');

      // CustomerApiClient의 processPendingOrders 메서드만 실행하도록 수정된 Java 프로그램 호출
      const { stdout, stderr } = await execAsync('java -cp "target/classes:lib/mysql-connector-j-9.4.0.jar" org.mindrot.CustomerApiClient', {
        cwd: process.cwd() + '/..'  // jBCrypt 폴더로 이동
      });

      if (stderr) {
        console.error('CustomerApiClient stderr:', stderr);
      }

      console.log('CustomerApiClient 실행 결과:', stdout);

    } catch (error) {
      console.error('CustomerApiClient 실행 실패:', error);
      throw error;
    }
  }

  /**
   * BizPPurio 메시지 전송
   */
  async sendBizPPurioMessage(orderData) {
    try {
      const qrCode = "https://example.com/qr"; // 임시 QR 코드 링크
      const phoneNumber = orderData.ordererTel;
      const orderId = orderData.orderId;
      const productName = orderData.productName || 'eSIM 해외 데이터';

      console.log('BizPPurio 메시지 전송 시작:', {
        phoneNumber,
        orderId,
        productName
      });

      const result = await this.bizppurioClient.sendQrCodeMessage(
        qrCode,
        phoneNumber,
        orderId,
        productName
      );

      // "BizPPurio 메시지 전송 성공:" 메시지가 나왔는지 확인
      if (result && result.toString().includes('성공')) {
        console.log('BizPPurio 메시지 전송 성공 - kakaoSendYN 업데이트');
        
        // kakaoSendYN을 'Y'로 업데이트
        await this.updateKakaoSendYN(orderData.productOrderId);
      }

    } catch (error) {
      console.error('BizPPurio 메시지 전송 실패:', error);
      // 실패해도 다음 처리를 계속 진행
    }
  }

  /**
   * kakaoSendYN을 'Y'로 업데이트
   */
  async updateKakaoSendYN(productOrderId) {
    try {
      await this.mysqlClient.connect();

      const updateQuery = `
        UPDATE user 
        SET kakaoSendYN = 'Y', updated_at = NOW() 
        WHERE productOrderId = ?
      `;

      const [result] = await this.mysqlClient.connection.execute(updateQuery, [productOrderId]);

      if (result.affectedRows > 0) {
        console.log(`kakaoSendYN 업데이트 완료: productOrderId=${productOrderId}`);
      } else {
        console.error(`kakaoSendYN 업데이트 실패: productOrderId=${productOrderId} - 해당 레코드를 찾을 수 없음`);
      }

    } catch (error) {
      console.error('kakaoSendYN 업데이트 실패:', error);
    }
  }

  /**
   * 발송 처리 대상 확인 및 처리
   */
  async processDispatchOrders() {
    try {
      await this.mysqlClient.connect();

      // 발송 처리할 주문들 조회 (dispatchStatus = 0)
      const dispatchQuery = `
        SELECT productOrderId, orderId, ordererName, ordererTel, email, 
               productName, day, quantity, orderTid, dispatchStatus
        FROM user 
        WHERE dispatchStatus = 0
        ORDER BY created_at ASC
        LIMIT 10
      `;

      const [dispatchRows] = await this.mysqlClient.connection.execute(dispatchQuery);

      if (dispatchRows.length === 0) {
        console.log('발송 처리할 주문이 없습니다.');
        return;
      }

      console.log(`${dispatchRows.length}개의 발송 처리 대상을 찾았습니다.`);

      // NaverCommerceApiClient의 발송 처리 메서드 호출
      await this.callDispatchApiClient();

    } catch (error) {
      console.error('발송 처리 실패:', error);
    }
  }

  /**
   * NaverCommerceApiClient의 발송 처리 메서드 호출
   */
  async callDispatchApiClient() {
    try {
      console.log('NaverCommerceApiClient 발송 처리 호출 중...');

      // NaverCommerceApiClient의 processDispatchOrders 메서드 실행
      const { stdout, stderr } = await execAsync('java -cp "target/classes:lib/mysql-connector-j-9.4.0.jar" org.mindrot.NaverCommerceApiClient processDispatchOrders', {
        cwd: process.cwd() + '/..'  // jBCrypt 폴더로 이동
      });

      if (stderr) {
        console.error('NaverCommerceApiClient 발송 처리 stderr:', stderr);
      }

      console.log('NaverCommerceApiClient 발송 처리 실행 결과:', stdout);

    } catch (error) {
      console.error('NaverCommerceApiClient 발송 처리 실행 실패:', error);
      throw error;
    }
  }

  /**
   * 리소스 정리
   */
  async cleanup() {
    this.stop();
    await this.mysqlClient.disconnect();
  }
}
