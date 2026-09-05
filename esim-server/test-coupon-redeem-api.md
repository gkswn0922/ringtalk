# Coupon Redeem Callback API 테스트 가이드 (업데이트)

## API 엔드포인트
```
POST /api/joytel/notify/coupon/redeem
```

## Content-Type
```
application/json
```

## 요청 예시

### 1. 기본 요청 (필수 필드)
```json
{
  "transId": "TXN123456789",
  "resultCode": "000",
  "resultMesg": "Success",
  "finishTime": "2024-01-15T10:30:00Z",
  "data": {
    "coupon": "COUPON123456",
    "qrcode": "LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON",
    "cid": "CUSTOMER001"
  }
}
```

### 2. 전체 필드 포함 요청
```json
{
  "transId": "TXN123456789",
  "resultCode": "000",
  "resultMesg": "QR code generated successfully",
  "finishTime": "2024-01-15T10:30:00Z",
  "data": {
    "coupon": "COUPON123456",
    "qrcode": "LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON",
    "cid": "CUSTOMER001"
  }
}
```

### 3. 에러 상황 요청
```json
{
  "transId": "TXN123456790",
  "resultCode": "999",
  "resultMesg": "QR code generation failed",
  "finishTime": "2024-01-15T10:35:00Z",
  "data": {
    "coupon": "COUPON123457",
    "qrcode": "",
    "cid": "CUSTOMER002"
  }
}
```

## 응답 예시

### 성공 응답
```json
{
  "ok": true,
  "message": "QR 코드가 성공적으로 저장되었습니다.",
  "transId": "TXN123456789"
}
```

### 에러 응답
```json
{
  "ok": false,
  "error": "data.qrcode가 필요합니다."
}
```

## cURL 테스트 명령어

### 기본 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/coupon/redeem \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "resultCode": "000",
    "resultMesg": "Success",
    "finishTime": "2024-01-15T10:30:00Z",
    "data": {
      "coupon": "COUPON123456",
      "qrcode": "LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON",
      "cid": "CUSTOMER001"
    }
  }'
```

### 전체 필드 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/coupon/redeem \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "resultCode": "000",
    "resultMesg": "QR code generated successfully",
    "finishTime": "2024-01-15T10:30:00Z",
    "data": {
      "coupon": "COUPON123456",
      "qrcode": "LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON",
      "cid": "CUSTOMER001"
    }
  }'
```

## 필수 필드 검증 테스트

### qrcode 누락 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/coupon/redeem \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "data": {
      "coupon": "COUPON123456",
      "cid": "CUSTOMER001"
    }
  }'
```

### cid 누락 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/coupon/redeem \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "data": {
      "coupon": "COUPON123456",
      "qrcode": "LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON"
    }
  }'
```

## 예상 동작

1. **성공 케이스**: 
   - `user` 테이블의 QR 코드 업데이트
   - `esim_progress_notifications` 테이블에 데이터 저장
   - 성공 응답 반환

2. **검증 실패 케이스**:
   - 필수 필드 누락 시 에러 응답

3. **시스템 에러 케이스**:
   - 예외 발생 시 에러 응답

## 데이터베이스 확인

### user 테이블 확인
```sql
SELECT orderTid, QR FROM user WHERE orderTid = 'COUPON123456';
```

### esim_progress_notifications 테이블 확인
```sql
SELECT * FROM esim_progress_notifications WHERE snPin = 'COUPON123456' ORDER BY created_at DESC;
```

## 로그 확인

서버 콘솔에서 다음과 같은 로그를 확인할 수 있습니다:

```
파라미터 검증 완료: {
  transId: 'TXN123456789',
  coupon: 'COUPON123456',
  qrcode: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON',
  cid: 'CUSTOMER001',
  resultCode: '000',
  resultMesg: 'Success',
  finishTime: '2024-01-15T10:30:00Z'
}

QR 코드 업데이트 및 BizPPurio API 호출 완료: {
  coupon: 'COUPON123456',
  qrcode: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON',
  resultCode: '000',
  resultMesg: 'Success',
  finishTime: '2024-01-15T10:30:00Z',
  timestamp: '2024-01-15T10:30:00.000Z'
}

eSIM Progress Notification 저장 완료: {
  transId: 'TXN123456789',
  snPin: 'COUPON123456',
  cid: 'CUSTOMER001',
  qrCode: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON',
  timestamp: '2024-01-15T10:30:00.000Z'
}
```

## 주요 변경사항

1. **새로운 필수 필드**: `data.cid` 필드가 추가되었습니다.
2. **데이터베이스 저장**: `esim_progress_notifications` 테이블에 데이터가 저장됩니다.
3. **데이터 매핑**:
   - `transId` → `esim_progress_notifications.transId`
   - `coupon` → `esim_progress_notifications.snPin`
   - `cid` → `esim_progress_notifications.cid`
   - `qrcode` → `esim_progress_notifications.qrCode`
4. **에러 처리**: Progress 테이블 저장 실패해도 전체는 성공으로 처리됩니다.
