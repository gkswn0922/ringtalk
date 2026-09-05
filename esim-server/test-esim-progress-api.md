# eSIM Progress Notification API 테스트 가이드

## API 엔드포인트
```
POST /api/joytel/notify/esim/esim-progress
```

## Content-Type
```
application/json
```

## 요청 예시

### 1. 기본 요청 (필수 필드만)
```json
{
  "transId": "TXN123456789",
  "resultCode": "000",
  "resultMesg": "Success",
  "finishTime": "2024-01-15T10:30:00Z",
  "data": {
    "cid": "CUSTOMER001",
    "profileType": "eSIM",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### 2. 전체 필드 포함 요청
```json
{
  "transId": "TXN123456789",
  "resultCode": "000",
  "resultMesg": "eSIM activation completed successfully",
  "finishTime": "2024-01-15T10:30:00Z",
  "data": {
    "cid": "CUSTOMER001",
    "eid": "EID123456789",
    "profileType": "eSIM",
    "timestamp": "2024-01-15T10:30:00Z",
    "notificationPointId": "NP001",
    "resultData": {
      "activationStatus": "completed",
      "profileStatus": "active"
    },
    "notificationPointStatus": {
      "status": "completed",
      "statusCodeData": {
        "subjectCode": "SC001",
        "reasonCode": "RC001",
        "message": "eSIM profile activated successfully",
        "subjectIdentifier": "SI001"
      }
    }
  }
}
```

### 3. 에러 상황 요청
```json
{
  "transId": "TXN123456790",
  "resultCode": "999",
  "resultMesg": "eSIM activation failed",
  "finishTime": "2024-01-15T10:35:00Z",
  "data": {
    "cid": "CUSTOMER002",
    "eid": "EID123456790",
    "profileType": "eSIM",
    "timestamp": "2024-01-15T10:35:00Z",
    "notificationPointId": "NP002",
    "resultData": {
      "activationStatus": "failed",
      "errorCode": "E001"
    },
    "notificationPointStatus": {
      "status": "failed",
      "statusCodeData": {
        "subjectCode": "SC002",
        "reasonCode": "RC002",
        "message": "Network connection failed",
        "subjectIdentifier": "SI002"
      }
    }
  }
}
```

## 응답 예시

### 성공 응답
```json
{
  "code": "000",
  "mesg": "success"
}
```

### 에러 응답
```json
{
  "code": "999",
  "mesg": "System Error"
}
```

## cURL 테스트 명령어

### 기본 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/esim/esim-progress \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "resultCode": "000",
    "resultMesg": "Success",
    "finishTime": "2024-01-15T10:30:00Z",
    "data": {
      "cid": "CUSTOMER001",
      "profileType": "eSIM",
      "timestamp": "2024-01-15T10:30:00Z"
    }
  }'
```

### 전체 필드 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/esim/esim-progress \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "resultCode": "000",
    "resultMesg": "eSIM activation completed successfully",
    "finishTime": "2024-01-15T10:30:00Z",
    "data": {
      "cid": "CUSTOMER001",
      "eid": "EID123456789",
      "profileType": "eSIM",
      "timestamp": "2024-01-15T10:30:00Z",
      "notificationPointId": "NP001",
      "resultData": {
        "activationStatus": "completed",
        "profileStatus": "active"
      },
      "notificationPointStatus": {
        "status": "completed",
        "statusCodeData": {
          "subjectCode": "SC001",
          "reasonCode": "RC001",
          "message": "eSIM profile activated successfully",
          "subjectIdentifier": "SI001"
        }
      }
    }
  }'
```

## 필수 필드 검증 테스트

### transId 누락 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/esim/esim-progress \
  -H "Content-Type: application/json" \
  -d '{
    "resultCode": "000",
    "data": {
      "cid": "CUSTOMER001",
      "profileType": "eSIM"
    }
  }'
```

### cid 누락 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/esim/esim-progress \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "data": {
      "profileType": "eSIM"
    }
  }'
```

### profileType 누락 테스트
```bash
curl -X POST http://localhost:3000/api/joytel/notify/esim/esim-progress \
  -H "Content-Type: application/json" \
  -d '{
    "transId": "TXN123456789",
    "data": {
      "cid": "CUSTOMER001"
    }
  }'
```

## 예상 동작

1. **성공 케이스**: 
   - 요청이 DB에 저장됨
   - 콘솔에 이벤트 정보 출력
   - `{ "code": "000", "mesg": "success" }` 응답

2. **검증 실패 케이스**:
   - 필수 필드 누락 시 `{ "code": "999", "mesg": "System Error: [필드명] is required" }` 응답

3. **시스템 에러 케이스**:
   - 예외 발생 시 `{ "code": "999", "mesg": "System Error" }` 응답
   - DB 저장 실패해도 파트너사에는 성공 응답 전송

## 로그 확인

서버 콘솔에서 다음과 같은 로그를 확인할 수 있습니다:

```
=== eSIM Progress Notification Event ===
TransId: TXN123456789
ResultCode: 000
ResultMessage: Success
FinishTime: 2024-01-15T10:30:00Z
CID: CUSTOMER001
EID: EID123456789
ProfileType: eSIM
Timestamp: 2024-01-15T10:30:00Z
NotificationPointId: NP001
ResultData: {"activationStatus":"completed","profileStatus":"active"}
NotificationPointStatus:
  Status: completed
  StatusCodeData:
    SubjectCode: SC001
    ReasonCode: RC001
    Message: eSIM profile activated successfully
    SubjectIdentifier: SI001
==========================================
```

## 데이터베이스 확인

`esim_progress_notifications` 테이블에서 저장된 데이터를 확인할 수 있습니다:

```sql
SELECT * FROM esim_progress_notifications ORDER BY created_at DESC LIMIT 10;
```
