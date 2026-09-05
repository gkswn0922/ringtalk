# 네이버 커머스 API → MySQL 통합 시스템

NaverCommerceApiClient에서 가져온 productOrderId를 MySQL의 user 테이블에 자동으로 저장하는 시스템입니다.

## 시스템 구성

### 1. 주요 클래스

- **NaverCommerceApiClient.java**: 네이버 커머스 API 호출 및 MySQL 저장 통합
- **UserOrderDAO.java**: user 테이블에 productOrderId 저장/조회 기능
- **MySQLConfig.java**: MySQL 연결 설정 (업데이트됨)
- **NaverToMySQLIntegrationTest.java**: 통합 테스트용 클래스

### 2. MySQL 설정

접속 정보는 하드코딩되어 있지 않고 환경변수로 주입합니다 (`MySQLConfig.java` 참고):

```
DB_HOST      (기본값: 127.0.0.1)
DB_PORT      (기본값: 3306)
DB_NAME      (기본값: ringtalk)
DB_USER      (기본값: root)
DB_PASSWORD  (기본값: 없음)
```

실행 전 셸에서 `export DB_HOST=... DB_USER=... DB_PASSWORD=...` 등으로 값을 설정하세요.

## 사용 방법

### 1. 통합 테스트 실행

```bash
# 컴파일 (MySQL JDBC 드라이버 필요)
javac -cp "lib/mysql-connector-j-9.4.0.jar" -d target/classes src/main/java/org/mindrot/*.java

# 통합 테스트 실행
java -cp "target/classes:lib/mysql-connector-j-9.4.0.jar" org.mindrot.NaverToMySQLIntegrationTest
```

### 2. 네이버 API → MySQL 실행

```bash
# 네이버 커머스 API 호출 및 MySQL 저장
java -cp "target/classes:lib/mysql-connector-j-9.4.0.jar" org.mindrot.NaverCommerceApiClient
```

## 시스템 동작 과정

1. **네이버 API 호출**: OAuth 토큰 발급 → 주문 정보 조회
2. **productOrderId 추출**: API 응답에서 주문 ID 목록 추출
3. **MySQL 저장**: user 테이블에 productOrderId 저장
4. **결과 확인**: 저장된 데이터 검증

## user 테이블 스키마

시스템이 자동으로 다음 컬럼을 추가합니다:

```sql
ALTER TABLE user ADD COLUMN productOrderId VARCHAR(100) NULL;
```

추가로 다음 컬럼들이 사용됩니다 (기존 테이블에 있다고 가정):
- `id`: Primary Key
- `customer_name`: 고객명
- `customer_tel`: 고객 전화번호
- `customer_email`: 고객 이메일
- `created_at`: 생성일시
- `updated_at`: 수정일시

## 저장 로직

1. **기존 사용자 확인**: 전화번호나 이메일로 기존 사용자 검색
2. **업데이트/삽입**: 
   - 기존 사용자가 있으면 productOrderId 업데이트
   - 없으면 새 사용자로 등록
3. **중복 방지**: 동일한 productOrderId는 중복 저장되지 않음

## 로그 확인

NaverCommerceApiClient 실행 시 다음 로그 파일이 생성됩니다:
```
naver_commerce_api_2025-01-27.log
```

## 문제 해결

### MySQL 연결 실패
1. 네트워크 연결 확인
2. 데이터베이스 서버 상태 확인
3. 계정 권한 확인

### JDBC 드라이버 오류
- lib/mysql-connector-j-9.4.0.jar 파일 확인
- 올바른 버전의 MySQL Connector 사용

### 컬럼 추가 실패
- DB_USER 계정에 ALTER TABLE 권한이 있는지 확인
- user 테이블이 존재하는지 확인

## 확장 가능성

1. **상세 주문 정보 저장**: ProductOrderInfo 클래스 활용하여 더 많은 정보 저장
2. **배치 처리**: 대량 데이터 처리를 위한 배치 기능 추가
3. **스케줄링**: 정기적으로 API 호출하여 데이터 동기화
4. **알림 기능**: 새 주문 등록 시 알림 발송

## 주의사항

- 네이버 API 호출 제한을 고려하여 적절한 간격으로 실행
- 운영 환경에서는 로그 레벨과 파일 로테이션 설정 필요
- 민감한 정보(API 키, DB 비밀번호)는 환경변수나 설정 파일로 관리 권장
