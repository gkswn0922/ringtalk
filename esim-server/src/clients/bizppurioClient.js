import axios from 'axios';
import { config } from '../config/index.js';

export class BizPPurioClient {
  constructor() {
    this.baseUrl = config.bizppurio.baseUrl;
    this.token = config.bizppurio.authToken;
    this.accessToken = null;
    this.tokenExpiry = null;
    this.tokenRefreshTime = 50 * 60 * 1000; // 50분을 밀리초로 변환
  }

  async getToken() {
    try {
      // 현재 시간이 토큰 만료 시간보다 이전이면 기존 토큰 사용
      if (this.accessToken && this.tokenExpiry && new Date() < this.tokenExpiry) {
        console.log('기존 BizPPurio 토큰 사용 (만료되지 않음)');
        return this.accessToken;
      }

      console.log('BizPPurio 토큰 새로 발급 시작');
      
      const response = await axios.post(`${this.baseUrl}/v1/token`, {}, {
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Authorization': `Basic ${this.token}`
        }
      });

      const result = response.data;
      this.accessToken = result.accesstoken;
      
      // 토큰 만료 시간 설정 (현재 시간 + 50분)
      this.tokenExpiry = new Date(Date.now() + this.tokenRefreshTime);
      
      console.log('BizPPurio 토큰 발급 성공, 만료 시간:', this.tokenExpiry.toISOString());
      return this.accessToken;
    } catch (error) {
      console.error('BizPPurio 토큰 발급 실패:', error.response?.data || error.message);
      throw error;
    }
  }

  async sendMessage(transId, qrCode, phoneNumber, orderId, productName, day) {
    try {

      // qrCode가 없으면 메시지 전송하지 않음
      if (!qrCode || qrCode.trim() === '' || qrCode === 'N/A') {
        console.log('QR 코드가 없어 메시지 전송을 건너뜁니다:', { qrCode, orderId });
        return { skipped: true, reason: 'QR 코드 없음' };
      }

      // qrCode로 transId 조회하여 URL 생성
      let qrUrl = 'N/A';
      try {
        
        if (transId) {
          qrUrl = `${config.publicBaseUrl}/esim/qr-detail?transId=${transId}`;
          console.log('QR URL 생성 성공:', qrUrl);
        } else {
          console.log('transId를 찾을 수 없음:', qrCode);
          qrUrl = qrCode;
        }
      } catch (dbError) {
        console.error('데이터베이스 조회 실패:', dbError);
        // DB 조회 실패 시 원본 qrCode 사용
        qrUrl = qrCode;
      }

      // 토큰이 없거나 만료되었으면 새로 발급
      if (!this.accessToken || (this.tokenExpiry && new Date() > this.tokenExpiry)) {
        await this.getToken();
      }

      // 메시지 템플릿 변수 치환
      let messageTemplate = "안녕하세요! 링톡 입니다.\n해당 eSIM은 유심교체 없이\nQR코드 스캔을 통해 해외에서 사용 가능합니다.\n\n주문번호: #{주문번호}\n상품명: #{옵션번호}\n\n[개통정보]\nQR코드확인: #{qr링크}\n\n해외 현지에서 제품사용이 안될시,\n카카오톡 [링톡]으로 꼭! 문의부탁드립니다\n(평일: 09:00 ~ 18:00)\n\n※ 취소/환불규정\n이 상품은 결제와 동시에 고객님께 주요 개통정보가 전송되는 상품으로 스캔 및 삭제 후 취소/환불이 불가합니다. 취소 시 반드시 스캔 전에 사용기종 및 유효기한을 확인하셔서 구매처에 취소요청 바랍니다.\n\n링톡과 즐거운 여행 되시길 바랍니다.";

      // 변수 치환
      const replacedMessage = messageTemplate
        .replace('#{주문번호}', orderId || 'N/A')
        .replace('#{옵션번호}', productName + ' ' + day + '일' || 'eSIM 해외 데이터')
        .replace('#{qr링크}', qrUrl);

             // 전화번호 앞에 0 추가
       const phoneNumberStr = String(phoneNumber);
       const formattedPhoneNumber = phoneNumberStr.startsWith('0') ? phoneNumberStr : `0${phoneNumberStr}`;
       
       const messageData = {
         "account": config.bizppurio.account,
         "refkey": `test1234`,
         "type": "at",
         "from": "00000000000",
         "to": formattedPhoneNumber,
        "content": {
          "at": {
            "senderkey": config.bizppurio.senderKey,
            "templatecode": config.bizppurio.templateCode,
            "message": replacedMessage
          }
        }
      };

      const response = await axios.post(`${this.baseUrl}/v3/message`, messageData, {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.accessToken}`
        }
      });

      const result = response.data;
      console.log('BizPPurio 메시지 전송 성공:', result);
      return result;
    } catch (error) {
      console.error('BizPPurio 메시지 전송 실패:', error.response?.data || error.message);
      throw error;
    }
  }

  async sendQrCodeMessage(transId, qrCode, phoneNumber, orderId, productName, day) {
    try {
      console.log('QR 코드 메시지 전송 시작:', { qrCode, phoneNumber, orderId, productName });
      
      // 토큰 발급 (캐싱된 토큰이 있으면 재사용)
      await this.getToken();
      
      // 메시지 전송
      const result = await this.sendMessage(transId, qrCode, phoneNumber, orderId, productName, day);
      
      return result;
    } catch (error) {
      console.error('QR 코드 메시지 전송 실패:', error);
      throw error;
    }
  }
}
