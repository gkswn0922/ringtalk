# eSIM QR 상세페이지 동적 라우팅 테스트 가이드

## 🎯 구현 완료 사항

### 1. **동적 라우팅 구현**
- 기존 정적 HTML 파일 대신 Node.js 라우팅으로 변경
- DB에서 실시간 데이터 조회 및 렌더링

### 2. **데이터 조회 기능**
- `esim_progress_notifications` 테이블에서 데이터 조회
- `transId` 또는 `cid`로 검색 가능

### 3. **상태 표시 기능**
- `notificationPointId`에 따른 실시간 상태 표시
- 각 상태별 아이콘과 색상 구분

### 4. **QR 코드 생성**
- `qrCode` 데이터로 실제 QR 이미지 생성
- Base64 인코딩으로 인라인 이미지 제공

## 🚀 사용 방법

### **URL 구조**
```
기존: /esim/qr-detail (정적 파일)
새로운: /esim/qr-detail?transId=TXN123 또는 /esim/qr-detail?cid=CUSTOMER001
```

### **테스트 URL 예시**
```
http://localhost:3000/esim/qr-detail?transId=TXN123456789
http://localhost:3000/esim/qr-detail?cid=CUSTOMER001
```

## 📊 상태 매핑

| notificationPointId | 상태 | 아이콘 | 색상 |
|-------------------|------|--------|------|
| 1 | 단말기 호환성을 확인하는 중입니다. | 🔍 | 파란색 |
| 2 | 설치가 취소되었거나 승인되지 않았습니다. | ❌ | 빨간색 |
| 3 | eSIM 프로파일을 다운로드 중입니다. | ⬇️ | 주황색 |
| 4 | eSIM 프로파일을 설치하는 중입니다. | ⚙️ | 보라색 |
| 5 | eSIM 프로파일이 삭제되었습니다. | 🗑️ | 회색 |
| 6 | eSIM이 활성화되었습니다. | ✅ | 초록색 |
| 7 | eSIM이 비활성화되었습니다. | ⏸️ | 노란색 |
| 101 | 이 기기의 eSIM(EID)이 차단되어 사용이 불가능합니다. | 🚫 | 빨간색 |
| 102 | 해당 기종은 eSIM 사용이 제한되어 있습니다. | 📱 | 빨간색 |

## 🧪 테스트 시나리오

### 1. **정상 케이스**
```bash
# transId로 접근
curl "http://localhost:3000/esim/qr-detail?transId=TXN123456789"

# cid로 접근  
curl "http://localhost:3000/esim/qr-detail?cid=CUSTOMER001"
```

### 2. **에러 케이스**
```bash
# 파라미터 없이 접근
curl "http://localhost:3000/esim/qr-detail"
# 결과: "잘못된 접근입니다" 메시지

# 존재하지 않는 ID로 접근
curl "http://localhost:3000/esim/qr-detail?transId=NOTEXIST"
# 결과: "eSIM 정보를 찾을 수 없습니다" 메시지
```

## 📋 데이터베이스 테스트

### **테스트 데이터 삽입**
```sql
INSERT INTO esim_progress_notifications 
(transId, snPin, cid, qrCode, notificationPointId) 
VALUES 
('TXN123456789', 'SNPIN001', 'CUSTOMER001', 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON', 6),
('TXN123456790', 'SNPIN002', 'CUSTOMER002', 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON', 3),
('TXN123456791', 'SNPIN003', 'CUSTOMER003', 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON', 101);
```

### **데이터 확인**
```sql
SELECT * FROM esim_progress_notifications ORDER BY created_at DESC;
```

## 🎨 페이지 구성

### **헤더**
- RingTalk 로고
- "eSIM 설치 설명서" 배지

### **상태 배너**
- 현재 eSIM 상태 표시
- 상태별 아이콘과 색상
- 상태 메시지

### **QR 코드 섹션**
- eSIM 칩 아이콘
- QR 코드 이미지 (동적 생성)
- 스캔 안내 메시지

### **정보 섹션**
- ICCID (snPin)
- 고객 ID (cid)
- 트랜잭션 ID (transId)

### **활성화 코드 섹션**
- iOS용 SM-DP+ 주소 및 활성화 코드
- Android용 활성화 코드
- 복사 버튼 기능

### **푸터**
- 카카오톡 상담 정보

## 🔧 주요 기능

### **1. 동적 데이터 조회**
```javascript
// DB에서 최신 데이터 조회
const esimData = await getEsimProgressData(transId || cid);
```

### **2. 상태 매핑**
```javascript
// notificationPointId로 상태 정보 반환
const statusInfo = getStatusInfo(esimData.notificationPointId);
```

### **3. QR 코드 생성**
```javascript
// qrCode 데이터로 QR 이미지 생성
const qrCodeImage = await generateQRCodeImage(esimData.qrCode);
```

### **4. 복사 기능**
```javascript
// 활성화 코드 복사 기능
function copyToClipboard(input) {
  input.select();
  document.execCommand('copy');
  // 복사 성공 피드백
}
```

## 🚨 에러 처리

### **1. 파라미터 누락**
- `transId`와 `cid` 모두 없을 때
- 400 에러와 안내 메시지 표시

### **2. 데이터 없음**
- DB에서 데이터를 찾을 수 없을 때
- "eSIM 정보를 찾을 수 없습니다" 메시지

### **3. 시스템 에러**
- DB 연결 실패 등
- 500 에러와 "오류가 발생했습니다" 메시지

## 📱 반응형 디자인

- 모바일 친화적 레이아웃
- 터치 친화적 버튼 크기
- 적응형 QR 코드 크기

## 🔄 실시간 업데이트

- DB 데이터 변경 시 즉시 반영
- 상태 변경 시 실시간 표시
- QR 코드 데이터 업데이트 시 자동 재생성

이제 동적 라우팅으로 eSIM QR 상세페이지가 완전히 구현되었습니다! 🎉
