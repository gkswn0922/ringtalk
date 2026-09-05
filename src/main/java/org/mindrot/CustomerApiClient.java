package org.mindrot;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;

/**
 * -주문 API 호출 클라이언트
 */
public class CustomerApiClient {
    
    // 파트너사 발주 API 정보 (환경변수로 주입, 미설정 시 개발용 예시 값 사용)
    private static final String API_URL = System.getenv().getOrDefault("PARTNER_ORDER_API_URL", "https://api.example-esim-vendor.com/customerApi/customerOrder");
    private static final String CUSTOMER_CODE = System.getenv().getOrDefault("PARTNER_CUSTOMER_CODE", "");
    private static final String CUSTOMER_AUTH = System.getenv().getOrDefault("PARTNER_CUSTOMER_AUTH", "");
    private static final String WAREHOUSE = ""; // 빈 문자열
    
    public static void main(String[] args) {
        try {
            
            // 새로운 메서드: pending 주문들 처리
            System.out.println("\n=== Pending 주문들 처리 시작 ===");
            processPendingOrders();
            
        } catch (Exception e) {
            System.err.println("API 호출 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * orderTid가 null인 사용자들을 가져와서 API 호출 후 orderTid 업데이트
     */
    public static void processPendingOrders() {
        List<UserOrder> pendingUsers = getPendingUsers();
        System.out.println("처리할 pending 주문 수: " + pendingUsers.size());
        
        for (UserOrder user : pendingUsers) {
            try {
                System.out.println("\n사용자 처리 중...");
                
                // API 호출을 위한 주문 요청 생성
                OrderRequest orderRequest = new OrderRequest();
                orderRequest.customerCode = CUSTOMER_CODE;
                orderRequest.orderTid = generateOrderTid(CUSTOMER_CODE);
                orderRequest.warehouse = "上海仓库";
                orderRequest.receiveName = user.getOrdererName();
                orderRequest.phone = "0" + user.getOrdererTel(); // 앞에 0 추가
                orderRequest.timestamp = getCurrentUnixTimestamp();
                orderRequest.email = user.getEmail();
                orderRequest.type = 3;
                orderRequest.replyType = 1;
                orderRequest.productCode = getProductCode(user.getProductName(), user.getDay());
                orderRequest.quantity = user.getQuantity();
                
                // autoGraph 생성
                orderRequest.autoGraph = generateAutoGraph(orderRequest);
                
                // API 호출
                String response = sendOrderRequest(orderRequest);
                System.out.println("API 응답: " + response);
                
                // 응답에서 orderTid 추출 및 업데이트
                String newOrderTid = extractOrderTidFromResponse(response);
                System.out.println("=== 업데이트 정보 ===");
                System.out.println("추출된 newOrderTid: [" + newOrderTid + "]");
                System.out.println("사용자 이메일: [" + user.getEmail() + "]");
                
                if (newOrderTid != null && !newOrderTid.isEmpty()) {
                    System.out.println("API 응답에서 추출한 orderTid로 업데이트: " + newOrderTid);
                    updateUserOrderTidAndCost(user.getProductOrderId(), newOrderTid, orderRequest.productCode, orderRequest.quantity);
                    System.out.println("주문 ID " + user.getProductOrderId() + "의 orderTid가 " + newOrderTid + "로 업데이트되었습니다.");
                } else {
                    // 응답에서 orderTid를 못 찾았지만, 요청 시 생성한 orderTid를 사용
                    System.out.println("API 응답에서 orderTid 추출 실패, 요청 시 생성한 orderTid 사용: " + orderRequest.orderTid);
                    updateUserOrderTidAndCost(user.getProductOrderId(), orderRequest.orderTid, orderRequest.productCode, orderRequest.quantity);
                    System.out.println("주문 ID " + user.getProductOrderId() + "의 orderTid가 " + orderRequest.orderTid + "로 업데이트되었습니다.");
                }
                
                // API 호출 간격 조절 (너무 빠르게 호출하지 않도록)
                Thread.sleep(1000);
                
            } catch (Exception e) {
                System.err.println("사용자 처리 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * orderTid가 null인 사용자들을 데이터베이스에서 조회
     */
    private static List<UserOrder> getPendingUsers() {
        List<UserOrder> users = new ArrayList<>();
        
        try (Connection conn = MySQLConfig.getConnection()) {
            String sql = "SELECT productOrderId, orderId, ordererName, ordererTel, email, quantity, productName, day FROM ringtalk.user WHERE orderTid IS NULL";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                
                while (rs.next()) {
                    UserOrder user = new UserOrder();
                    user.setProductOrderId(rs.getString("productOrderId"));
                    user.setOrderId(rs.getString("orderId"));
                    user.setOrdererName(rs.getString("ordererName"));
                    user.setOrdererTel(rs.getString("ordererTel"));
                    user.setEmail(rs.getString("email"));
                    user.setQuantity(rs.getInt("quantity"));
                    user.setProductName(rs.getString("productName"));
                    user.setDay(rs.getInt("day"));
                    users.add(user);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("사용자 조회 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
        
        return users;
    }
    
    /**
     * API 응답에서 orderTid 추출
     */
    private static String extractOrderTidFromResponse(String response) {
        try {
            System.out.println("=== 응답 디버깅 ===");
            System.out.println("전체 응답: " + response);
            
            // JSON 응답에서 orderTid 필드 추출
            if (response != null && response.contains("\"orderTid\"")) {
                // "orderTid":"{customerCode}{yyyyMMddHHmmss}{6자리 랜덤 숫자}" 형태에서 추출
                int orderTidIndex = response.indexOf("\"orderTid\"");
                System.out.println("orderTid 인덱스: " + orderTidIndex);
                
                if (orderTidIndex >= 0) {  // >= 0으로 수정 (0도 유효한 인덱스)
                    int colonIndex = response.indexOf(":", orderTidIndex);
                    System.out.println("콜론 인덱스: " + colonIndex);
                    
                    if (colonIndex > 0) {
                        int valueStart = response.indexOf("\"", colonIndex) + 1;
                        System.out.println("값 시작 인덱스: " + valueStart);
                        
                        int valueEnd = response.indexOf("\"", valueStart);
                        System.out.println("값 끝 인덱스: " + valueEnd);
                        
                        if (valueEnd > valueStart) {
                            String orderTid = response.substring(valueStart, valueEnd);
                            System.out.println("추출된 orderTid: [" + orderTid + "]");
                            return orderTid;
                        }
                    }
                }
            } else {
                System.out.println("응답에서 orderTid를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            System.err.println("응답에서 orderTid 추출 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("orderTid 추출 실패");
        return null;
    }
    
    /**
     * productcode_map 테이블에서 상품코드로 원가 조회
     */
    private static Integer getProductCost(String productCode) {
        try (Connection conn = MySQLConfig.getConnection()) {
            String sql = "SELECT cost FROM ringtalk.productcode_map WHERE productCode = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, productCode);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("cost");
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("상품코드 원가 조회 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null; // 상품코드를 찾을 수 없는 경우
    }

    /**
     * 사용자의 orderTid와 cost를 데이터베이스에 업데이트
     */
    private static void updateUserOrderTidAndCost(String productOrderId, String orderTid, String productCode, int quantity) {
        try (Connection conn = MySQLConfig.getConnection()) {
            // 상품코드로 원가 조회
            Integer unitCost = getProductCost(productCode);
            Integer totalCost = null;
            
            if (unitCost != null) {
                totalCost = unitCost * quantity; // 수량에 따른 총 원가 계산
                System.out.println("상품코드: " + productCode + ", 단위원가: " + unitCost + ", 수량: " + quantity + ", 총원가: " + totalCost);
            } else {
                System.err.println("상품코드 " + productCode + "에 대한 원가를 찾을 수 없습니다.");
            }
            
            // orderTid와 cost를 함께 업데이트
            String sql = "UPDATE ringtalk.user SET orderTid = ?, cost = ? WHERE productOrderId = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, orderTid);
                pstmt.setObject(2, totalCost); // null일 수 있으므로 setObject 사용
                pstmt.setString(3, productOrderId);
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("데이터베이스 업데이트 성공: productOrderId " + productOrderId + 
                                     ", orderTid: " + orderTid + ", cost: " + totalCost);
                } else {
                    System.err.println("데이터베이스 업데이트 실패: productOrderId " + productOrderId + " - 해당 주문을 찾을 수 없습니다.");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("orderTid 및 cost 업데이트 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 사용자의 orderTid를 데이터베이스에 업데이트 (orderId 기준) - 기존 메서드 유지
     */
    private static void updateUserOrderTid(String productOrderId, String orderTid) {
        try (Connection conn = MySQLConfig.getConnection()) {
            String sql = "UPDATE ringtalk.user SET orderTid = ? WHERE productOrderId = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, orderTid);
                pstmt.setString(2, productOrderId);
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("데이터베이스 업데이트 성공: productOrderId " + productOrderId);
                } else {
                    System.err.println("데이터베이스 업데이트 실패: productOrderId " + productOrderId + " - 해당 주문을 찾을 수 없습니다.");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("orderTid 업데이트 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * orderTid를 자동으로 생성합니다.
     * 형식: customerCode + yyyyMMddHHmmss + 6자리 랜덤 숫자
     */
    private static String generateOrderTid(String customerCode) {
        String timestamp = getCurrentTimestamp();
        String randomDigits = String.format("%06d", (int)(Math.random() * 1000000));
        return customerCode + timestamp + randomDigits;
    }
    /**
     * 현재 시간을 UNIX Epoch 밀리초(13자리)로 반환
     */
    private static Long getCurrentUnixTimestamp() {
        long currentTimeMillis = System.currentTimeMillis();
        return currentTimeMillis;
    }
    
    /**
     * 현재 시간을 YYYYMMDDHHMMSS 형식으로 반환 (orderTid 생성용)
     */
    private static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(new Date());
    }
    
    /**
     * autoGraph 해시값 생성
     * @throws UnsupportedEncodingException 
     */
    private static String generateAutoGraph(OrderRequest request) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // 올바른 순서: customerCode + customerAuth + warehouse + type + orderTid + receiveName + phone + timestamp + itemList(productCode + quantity)
        String data = request.customerCode + 
                     CUSTOMER_AUTH + 
                     WAREHOUSE + 
                     request.type + 
                     request.orderTid + 
                     request.receiveName + 
                     request.phone + 
                     request.timestamp + 
                     (request.productCode + request.quantity); // itemList를 하나의 그룹으로
        
        System.out.println("해시 대상 문자열: " + data);
        
        // SHA-1 해싱
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = md.digest(data.getBytes(StandardCharsets.UTF_8.name()));
        
        // 16진수 문자열로 변환
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        
        String hash = sb.toString().toLowerCase();
        System.out.println("생성된 autoGraph: " + hash);
        
        return hash;
    }
    
    /**
     * API 요청 전송
     */
    public static String sendOrderRequest(OrderRequest request) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            // 요청 설정
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // JSON 요청 바디 생성
            String jsonBody = createJsonBody(request);
            System.out.println("요청 JSON: " + jsonBody);
            
            // 요청 전송
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8.name());
                os.write(input, 0, input.length);
            }
            
            // 응답 처리
            int responseCode = conn.getResponseCode();
            System.out.println("응답 코드: " + responseCode);
            
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                ? conn.getInputStream() 
                : conn.getErrorStream();
                
            return readResponse(inputStream);
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * JSON 요청 바디 생성
     */
    private static String createJsonBody(OrderRequest request) {
        return "{\n" +
               "  \"customerCode\": \"" + request.customerCode + "\",\n" +
               "  \"orderTid\": \"" + request.orderTid + "\",\n" +
               "  \"receiveName\": \"" + request.receiveName + "\",\n" +
               "  \"phone\": \"" + request.phone + "\",\n" +
               "  \"timestamp\": \"" + request.timestamp + "\",\n" +
               "  \"email\": \"" + request.email + "\",\n" +
               "  \"type\": " + request.type + ",\n" +
               "  \"replyType\": " + request.replyType + ",\n" +
               "  \"itemList\": [\n" +
               "    {\n" +
               "      \"productCode\": \"" + request.productCode + "\",\n" +
               "      \"quantity\": " + request.quantity + "\n" +
               "    }\n" +
               "  ],\n" +
               "  \"autoGraph\": \"" + request.autoGraph + "\"\n" +
               "}";
    }
    
    /**
     * HTTP 응답을 문자열로 읽기
     */
    private static String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8.name()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        }
    }

    /**
     * 베트남 상품명과 일수를 기반으로 productCode를 결정
     */
    private static String getProductCode(String productName, int day) {
        System.out.println("productName: " + productName);
        System.out.println("day: " + day);
        if (productName == null) return "eSIM-test";
        
        // 베트남 상품들
        if (productName.contains("베트남") || productName.contains("VN")) {
            // 베트남 1기가 상품들
            if (productName.contains("1기가")) {
                switch (day) {
                    case 1: return "eSIM-VN1G-01";
                    case 2: return "eSIM-VN1G-03";
                    case 3: return "eSIM-VN1G-03";
                    case 4: return "eSIM-VN1G-05";
                    case 5: return "eSIM-VN1G-05";
                    case 6: return "eSIM-VN1G-07";
                    case 7: return "eSIM-VN1G-07";
                    case 8: return "eSIM-VN1G-10";
                    case 9: return "eSIM-VN1G-10";
                    case 10: return "eSIM-VN1G-10";
                    case 15: return "eSIM-VN1G-15";
                    case 20: return "eSIM-VN1G-20";
                    case 30: return "eSIM-VN1G-30";
                }
            }
            
            // 베트남 3기가 상품들
            if (productName.contains("3기가")) {
                switch (day) {
                    case 1: return "eSIM-VN3G-01";
                    case 2: return "eSIM-VN3G-03";
                    case 3: return "eSIM-VN3G-03";
                    case 4: return "eSIM-VN3G-05";
                    case 5: return "eSIM-VN3G-05";
                    case 6: return "eSIM-VN3G-07";
                    case 7: return "eSIM-VN3G-07";
                    case 8: return "eSIM-VN3G-10";
                    case 9: return "eSIM-VN3G-10";
                    case 10: return "eSIM-VN3G-10";
                    case 15: return "eSIM-VN3G-15";
                    case 20: return "eSIM-VN3G-20";
                    case 30: return "eSIM-VN3G-30";
                }
            }
            
            // 베트남 5기가 상품들 (VT5G)
            if (productName.contains("5기가") && productName.contains("종료")) {
                switch (day) {
                    case 2: return "eSIM-VNVT5G-02";
                    case 3: return "eSIM-VNVT5G-03";
                    case 4: return "eSIM-VNVT5G-04";
                    case 5: return "eSIM-VNVT5G-05";
                    case 6: return "eSIM-VNVT5G-06";
                    case 7: return "eSIM-VNVT5G-07";
                    case 8: return "eSIM-VNVT5G-08";
                    case 9: return "eSIM-VNVT5G-09";
                    case 10: return "eSIM-VNVT5G-10";
                    case 15: return "eSIM-VNVT5G-15";
                    case 20: return "eSIM-VNVM-5GB-20";
                    case 30: return "eSIM-VNVT5G-30";
                }
            }
            
            // 베트남 7기가 상품들 (VT7G)
            if (productName.contains("7기가") && productName.contains("종료")) {
                switch (day) {
                    case 3: return "eSIM-VNVT7G-03";
                    case 4: return "eSIM-VNVT7G-04";
                    case 5: return "eSIM-VNVT7G-05";
                    case 6: return "eSIM-VNVT7G-06";
                    case 7: return "eSIM-VNVT7G-07";
                    case 10: return "eSIM-VNVT7G-10";
                    case 15: return "eSIM-VNVT7G-15";
                    case 20: return "eSIM-VNVT7G-20";
                    case 30: return "eSIM-VNVT7G-30";
                }
            }
            
            // 베트남 MAX 상품들 (무제한)
            if (productName.contains("MAX") || productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-VNMAX-01";
                    case 3: return "eSIM-VNMAX-03";
                    case 4: return "eSIM-VNMAX-05";
                    case 5: return "eSIM-VNMAX-05";
                    case 6: return "eSIM-VNMAX-07";
                    case 7: return "eSIM-VNMAX-07";
                    case 8: return "eSIM-VNMAX-10";
                    case 9: return "eSIM-VNMAX-10";
                    case 10: return "eSIM-VNMAX-10";
                    case 15: return "eSIM-VNMAX-15";
                    case 20: return "eSIM-VNMAX-20";
                    case 30: return "eSIM-VNMAX-30";
                }
            }
            
            // 베트남 총 데이터 상품들 (T10G, T20G, T30G, T50G)
            if (productName.contains("총 10기가")) {
                switch (day) {
                    case 3: return "eSIM-VNT10G-03";
                    case 5: return "eSIM-VNT10G-05";
                    case 7: return "eSIM-VNT10G-07";
                    case 10: return "eSIM-VNT10G-10";
                    case 15: return "eSIM-VNT10G-15";
                    case 30: return "eSIM-VNT10G-30";
                }
            }
            
            if (productName.contains("총 20기가")) {
                switch (day) {
                    case 3: return "eSIM-VNT20G-03";
                    case 5: return "eSIM-VNT20G-05";
                    case 7: return "eSIM-VNT20G-07";
                    case 10: return "eSIM-VNT20G-10";
                    case 15: return "eSIM-VNT20G-15";
                    case 30: return "eSIM-VNT20G-30";
                }
            }
            
            if (productName.contains("총 30기가")) {
                switch (day) {
                    case 3: return "eSIM-VNT30G-03";
                    case 5: return "eSIM-VNMOB30G-05";
                    case 7: return "eSIM-VNT30G-07";
                    case 10: return "eSIM-VNT30G-10";
                    case 15: return "eSIM-VNT30G-15";
                    case 30: return "eSIM-VNT30G-30";
                }
            }
            
            if (productName.contains("총 50기가")) {
                switch (day) {
                    case 3: return "eSIM-VNT50G-03";
                    case 5: return "eSIM-VNT50G-05";
                    case 7: return "eSIM-VNT50G-07";
                    case 10: return "eSIM-VNMOB50G-10";
                    case 15: return "eSIM-VNT50G-15";
                    case 30: return "eSIM-VNT50G-30";
                }
            }
        }
        
        // 일본 상품들
        if (productName.contains("일본") || productName.contains("JP")) {
            // 일본 1기가 상품들 (SB1G)
            if (productName.contains("매일 1기가")) {
                switch (day) {
                    case 1: return "eSIM -SB1G-01";
                    case 2: return "eSIM -SB1G-03";
                    case 3: return "eSIM -SB1G-03";
                    case 4: return "eSIM -SB1G-04";
                    case 5: return "eSIM -SB1G-05";
                    case 6: return "eSIM -SB1G-06";
                    case 7: return "eSIM -SB1G-07";
                    case 10: return "eSIM -SB1G-10";
                    case 15: return "eSIM -SB1G-15";
                    case 20: return "eSIM -SB1G-20";
                    case 30: return "eSIM -SB1G-30";
                }
            }
            
            // 일본 2기가 상품들 (SB2G)
            if (productName.contains("매일 2기가")) {
                switch (day) {
                    case 1: return "eSIM -SB2G-01";
                    case 2: return "eSIM -SB2G-03";
                    case 3: return "eSIM -SB2G-03";
                    case 4: return "eSIM -SB2G-04";
                    case 5: return "eSIM -SB2G-05";
                    case 6: return "eSIM -SB2G-06";
                    case 7: return "eSIM -SB2G-07";
                    case 10: return "eSIM -SB2G-10";
                    case 15: return "eSIM -SB2G-15";
                    case 20: return "eSIM -SB2G-20";
                    case 30: return "eSIM -SB2G-30";
                }
            }
            
            // 일본 3기가 상품들 (SB3G)
            if (productName.contains("매일 3기가")) {
                switch (day) {
                    case 1: return "eSIM -SB3G-01";
                    case 2: return "eSIM -SB3G-03";
                    case 3: return "eSIM -SB3G-03";
                    case 4: return "eSIM -SB3G-04";
                    case 5: return "eSIM -SB3G-05";
                    case 6: return "eSIM -SB3G-06";
                    case 7: return "eSIM -SB3G-07";
                    case 10: return "eSIM -SB3G-10";
                    case 15: return "eSIM -SB3G-15";
                    case 20: return "eSIM -SB3G-20";
                    case 30: return "eSIM -SB3G-30";
                }
            }
            
            // 일본 5기가 상품들 (SB5G)
            if (productName.contains("매일 5기가")) {
                switch (day) {
                    case 1: return "eSIM-SB5G-01";
                    case 2: return "eSIM-SB5G-03";
                    case 3: return "eSIM-SB5G-03";
                    case 4: return "eSIM-SB5G-04";
                    case 5: return "eSIM-SB5G-05";
                    case 6: return "eSIM-SB5G-06";
                    case 7: return "eSIM-SB5G-07";
                    case 10: return "eSIM-SB5G-10";
                    case 15: return "eSIM-SB5G-15";
                    case 20: return "eSIM-SB5G-20";
                    case 30: return "eSIM-SB5G-30";
                }
            }
            
            // 일본 MAX 상품들 (JPMAX)
            if (productName.contains("LTE 무제한")) {
                switch (day) {
                    case 1: return "eSIM-JPMAX-01";
                    case 2: return "eSIM-JPMAX-03";
                    case 3: return "eSIM-JPMAX-03";
                    case 4: return "eSIM-JPMAX-05";
                    case 5: return "eSIM-JPMAX-05";
                    case 6: return "eSIM-JPMAX-07";
                    case 7: return "eSIM-JPMAX-07";
                    case 10: return "eSIM-JPMAX-10";
                    case 15: return "eSIM-JPMAX-15";
                    case 20: return "eSIM-JPMAX-20";
                    case 30: return "eSIM-JPMAX-30";
                }
            }
            
            // 일본 10MAX 상품들 (JP10M)
            if (productName.contains("5G 속도 무제한")) {
                switch (day) {
                    case 1: return "eSIM-JP10M-01";
                    case 2: return "eSIM-JP10M-03";
                    case 3: return "eSIM-JP10M-03";
                    case 4: return "eSIM-JP10M-05";
                    case 5: return "eSIM-JP10M-05";
                    case 6: return "eSIM-JP10M-07";
                    case 7: return "eSIM-JP10M-07";
                    case 10: return "eSIM-JP10M-10";
                    case 15: return "eSIM-JP10M-15";
                    case 20: return "eSIM-JP10M-20";
                    case 30: return "eSIM-JP10M-30";
                }
            }
            
            // 일본 누적 3GB 상품들 (JPT3G)
            if (productName.contains("총 3기가")) {
                switch (day) {
                    case 3: return "eSIM-JPT3G-03";
                    case 4: return "eSIM-JPT3G-05";
                    case 5: return "eSIM-JPT3G-05";
                    case 6: return "eSIM-JPT3G-07";
                    case 7: return "eSIM-JPT3G-07";
                    case 10: return "eSIM-JPT3G-10";
                    case 15: return "eSIM-JPT3G-15";
                    case 30: return "eSIM-JPT3G-30";
                }
            }
            
            // 일본 누적 5GB 상품들 (JPT5G)
            if (productName.contains("총 5기가")) {
                switch (day) {
                    case 3: return "eSIM-JPT5G-03";
                    case 4: return "eSIM-JPT5G-05";
                    case 5: return "eSIM-JPT5G-05";
                    case 6: return "eSIM-JPT5G-07";
                    case 7: return "eSIM-JPT5G-07";
                    case 10: return "eSIM-JPT5G-10";
                    case 15: return "eSIM-JPT5G-15";
                    case 30: return "eSIM-JPT5G-30";
                }
            }
            
            // 일본 누적 10GB 상품들 (JPT10G)
            if (productName.contains("총 10기가")) {
                switch (day) {
                    case 3: return "eSIM-JPT10G-03";
                    case 4: return "eSIM-JPT10G-05";
                    case 5: return "eSIM-JPT10G-05";
                    case 6: return "eSIM-JPT10G-07";
                    case 7: return "eSIM-JPT10G-07";
                    case 10: return "eSIM-JPT10G-10";
                    case 15: return "eSIM-JPT10G-15";
                    case 30: return "eSIM-JPT10G-30";
                }
            }
            
            // 일본 누적 20GB 상품들 (JPT20G)
            if (productName.contains("총 20기가")) {
                switch (day) {
                    case 3: return "eSIM-JPT20G-03";
                    case 4: return "eSIM-JPT20G-05";
                    case 5: return "eSIM-JPT20G-05";
                    case 6: return "eSIM-JPT20G-07";
                    case 7: return "eSIM-JPT20G-07";
                    case 10: return "eSIM-JPT20G-10";
                    case 15: return "eSIM-JPT20G-15";
                    case 30: return "eSIM-JPT20G-30";
                }
            }
            
            // 일본 누적 30GB 상품들 (JPT30G)
            if (productName.contains("총 30기가")) {
                switch (day) {
                    case 3: return "eSIM-JPT30G-03";
                    case 4: return "eSIM-JPT30G-05";
                    case 5: return "eSIM-JPT30G-05";
                    case 6: return "eSIM-JPT30G-07";
                    case 7: return "eSIM-JPT30G-07";
                    case 10: return "eSIM-JPT30G-10";
                    case 15: return "eSIM-JPT30G-15";
                    case 30: return "eSIM-JPT30G-30";
                }
            }
        }
        
        // 말레이시아 상품들
        if (productName.contains("말레이시아") || productName.contains("MY")) {
            // 말레이시아 5G 매일 1기가 후 저속 무제한 상품들 (SM1G)
            if (productName.contains("매일 1기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM1G-01";
                    case 2: return "eSIM-SM1G-03";
                    case 3: return "eSIM-SM1G-03";
                    case 4: return "eSIM-SM1G-05";
                    case 5: return "eSIM-SM1G-05";
                    case 6: return "eSIM-SM1G-07";
                    case 7: return "eSIM-SM1G-07";
                    case 8: return "eSIM-SM1G-10";
                    case 9: return "eSIM-SM1G-10";
                    case 10: return "eSIM-SM1G-10";
                    case 15: return "eSIM-SM1G-15";
                    case 20: return "eSIM-SM1G-20";
                    case 30: return "eSIM-SM1G-30";
                }
            }
            
            // 말레이시아 5G 매일 2기가 후 저속 무제한 상품들 (SM2G)
            if (productName.contains("매일 2기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM2G-01";
                    case 2: return "eSIM-SM2G-03";
                    case 3: return "eSIM-SM2G-03";
                    case 4: return "eSIM-SM2G-05";
                    case 5: return "eSIM-SM2G-05";
                    case 6: return "eSIM-SM2G-07";
                    case 7: return "eSIM-SM2G-07";
                    case 8: return "eSIM-SM2G-10";
                    case 9: return "eSIM-SM2G-10";
                    case 10: return "eSIM-SM2G-10";
                    case 15: return "eSIM-SM2G-15";
                    case 20: return "eSIM-SM2G-20";
                    case 30: return "eSIM-SM2G-30";
                }
            }
            
            // 말레이시아 5G 매일 3기가 후 저속 무제한 상품들 (SM3G)
            if (productName.contains("매일 3기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM3G-01";
                    case 2: return "eSIM-SM3G-03";
                    case 3: return "eSIM-SM3G-03";
                    case 4: return "eSIM-SM3G-05";
                    case 5: return "eSIM-SM3G-05";
                    case 6: return "eSIM-SM3G-07";
                    case 7: return "eSIM-SM3G-07";
                    case 8: return "eSIM-SM3G-10";
                    case 9: return "eSIM-SM3G-10";
                    case 10: return "eSIM-SM3G-10";
                    case 15: return "eSIM-SM3G-15";
                    case 20: return "eSIM-SM3G-20";
                    case 30: return "eSIM-SM3G-30";
                }
            }
            
            // 말레이시아 5G 속도 무제한 상품들 (SM10M)
            if (productName.contains("5G 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-SM10M-03";
                    case 2: return "eSIM-SM10M-03";
                    case 3: return "eSIM-SM10M-03";
                    case 4: return "eSIM-SM10M-05";
                    case 5: return "eSIM-SM10M-05";
                    case 6: return "eSIM-SM10M-07";
                    case 7: return "eSIM-SM10M-07";
                    case 8: return "eSIM-SM10M-10";
                    case 9: return "eSIM-SM10M-10";
                    case 10: return "eSIM-SM10M-10";
                    case 15: return "eSIM-SM10M-15";
                    case 20: return "eSIM-SM10M-20";
                    case 30: return "eSIM-SM10M-30";
                }
            }
            
            // 말레이시아 5G 총 5기가 후 저속 무제한 상품들 (SMT5G)
            if (productName.contains("총 5기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT5G-03";
                    case 2: return "eSIM-SMT5G-03";
                    case 3: return "eSIM-SMT5G-03";
                    case 4: return "eSIM-SMT5G-05";
                    case 5: return "eSIM-SMT5G-05";
                    case 6: return "eSIM-SMT5G-07";
                    case 7: return "eSIM-SMT5G-07";
                    case 8: return "eSIM-SMT5G-10";
                    case 9: return "eSIM-SMT5G-10";
                    case 10: return "eSIM-SMT5G-10";
                    case 15: return "eSIM-SMT5G-15";
                    case 20: return "eSIM-SMT5G-30";
                    case 30: return "eSIM-SMT5G-30";
                }
            }
            
            // 말레이시아 5G 총 10기가 후 저속 무제한 상품들 (SMT10G)
            if (productName.contains("총 10기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT10G-03";
                    case 2: return "eSIM-SMT10G-03";
                    case 3: return "eSIM-SMT10G-03";
                    case 4: return "eSIM-SMT10G-05";
                    case 5: return "eSIM-SMT10G-05";
                    case 6: return "eSIM-SMT10G-07";
                    case 7: return "eSIM-SMT10G-07";
                    case 8: return "eSIM-SMT10G-10";
                    case 9: return "eSIM-SMT10G-10";
                    case 10: return "eSIM-SMT10G-10";
                    case 15: return "eSIM-SMT10G-15";
                    case 20: return "eSIM-SMT10G-30";
                    case 30: return "eSIM-SMT10G-30";
                }
            }
            
            // 말레이시아 5G 총 20기가 후 저속 무제한 상품들 (SMT20G)
            if (productName.contains("총 20기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT20G-03";
                    case 2: return "eSIM-SMT20G-03";
                    case 3: return "eSIM-SMT20G-03";
                    case 4: return "eSIM-SMT20G-05";
                    case 5: return "eSIM-SMT20G-05";
                    case 6: return "eSIM-SMT20G-07";
                    case 7: return "eSIM-SMT20G-07";
                    case 8: return "eSIM-SMT20G-10";
                    case 9: return "eSIM-SMT20G-10";
                    case 10: return "eSIM-SMT20G-10";
                    case 15: return "eSIM-SMT20G-15";
                    case 20: return "eSIM-SMT20G-30";
                    case 30: return "eSIM-SMT20G-30";
                }
            }
            
            // 말레이시아 5G 총 30기가 후 저속 무제한 상품들 (SMT30G)
            if (productName.contains("총 30기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT30G-03";
                    case 2: return "eSIM-SMT30G-03";
                    case 3: return "eSIM-SMT30G-03";
                    case 4: return "eSIM-SMT30G-05";
                    case 5: return "eSIM-SMT30G-05";
                    case 6: return "eSIM-SMT30G-07";
                    case 7: return "eSIM-SMT30G-07";
                    case 8: return "eSIM-SMT30G-10";
                    case 9: return "eSIM-SMT30G-10";
                    case 10: return "eSIM-SMT30G-10";
                    case 15: return "eSIM-SMT30G-15";
                    case 20: return "eSIM-SMT30G-30";
                    case 30: return "eSIM-SMT30G-30";
                }
            }
        }
        
        // 필리핀 상품들
        if (productName.contains("필리핀") || productName.contains("PH")) {
            // 필리핀 5G 매일 1기가 후 무제한(저속) 상품들 (PH1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PH1G-01";
                    case 2: return "eSIM-PH1G-03";
                    case 3: return "eSIM-PH1G-03";
                    case 4: return "eSIM-PH1G-05";
                    case 5: return "eSIM-PH1G-05";
                    case 6: return "eSIM-PH1G-07";
                    case 7: return "eSIM-PH1G-07";
                    case 8: return "eSIM-PH1G-10";
                    case 9: return "eSIM-PH1G-10";
                    case 10: return "eSIM-PH1G-10";
                    case 15: return "eSIM-PH1G-15";
                    case 20: return "eSIM-PH1G-20";
                    case 30: return "eSIM-PH1G-30";
                }
            }
            
            // 필리핀 5G 매일 2기가 후 무제한(저속) 상품들 (PH2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PH2G-01";
                    case 2: return "eSIM-PH2G-03";
                    case 3: return "eSIM-PH2G-03";
                    case 4: return "eSIM-PH2G-05";
                    case 5: return "eSIM-PH2G-05";
                    case 6: return "eSIM-PH2G-07";
                    case 7: return "eSIM-PH2G-07";
                    case 8: return "eSIM-PH2G-10";
                    case 9: return "eSIM-PH2G-10";
                    case 10: return "eSIM-PH2G-10";
                    case 15: return "eSIM-PH2G-15";
                    case 20: return "eSIM-PH2G-20";
                    case 30: return "eSIM-PH2G-30";
                }
            }
            
            // 필리핀 5G 매일 3기가 후 무제한(저속) 상품들 (PH3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PH3G-01";
                    case 2: return "eSIM-PH3G-03";
                    case 3: return "eSIM-PH3G-03";
                    case 4: return "eSIM-PH3G-05";
                    case 5: return "eSIM-PH3G-05";
                    case 6: return "eSIM-PH3G-07";
                    case 7: return "eSIM-PH3G-07";
                    case 8: return "eSIM-PH3G-10";
                    case 9: return "eSIM-PH3G-10";
                    case 10: return "eSIM-PH3G-10";
                    case 15: return "eSIM-PH3G-15";
                    case 20: return "eSIM-PH3G-20";
                    case 30: return "eSIM-PH3G-30";
                }
            }
            
            // 필리핀 LTE 속도 무제한 상품들 (PHMAX)
            if (productName.contains("LTE 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-PHMAX-01";
                    case 2: return "eSIM-PHMAX-03";
                    case 3: return "eSIM-PHMAX-03";
                    case 4: return "eSIM-PHMAX-05";
                    case 5: return "eSIM-PHMAX-05";
                    case 6: return "eSIM-PHMAX-07";
                    case 7: return "eSIM-PHMAX-07";
                    case 8: return "eSIM-PHMAX-10";
                    case 9: return "eSIM-PHMAX-10";
                    case 10: return "eSIM-PHMAX-10";
                    case 15: return "eSIM-PHMAX-15";
                    case 20: return "eSIM-PHMAX-20";
                    case 30: return "eSIM-PHMAX-30";
                }
            }
            
            // 필리핀 5G 총 3기가 후 무제한(저속) 상품들 (PHT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PHT3G-03";
                    case 2: return "eSIM-PHT3G-03";
                    case 3: return "eSIM-PHT3G-03";
                    case 4: return "eSIM-PHT3G-05";
                    case 5: return "eSIM-PHT3G-05";
                    case 6: return "eSIM-PHT3G-07";
                    case 7: return "eSIM-PHT3G-07";
                    case 8: return "eSIM-PHT3G-10";
                    case 9: return "eSIM-PHT3G-10";
                    case 10: return "eSIM-PHT3G-10";
                    case 15: return "eSIM-PHT3G-15";
                    case 20: return "eSIM-PHT3G-30";
                    case 30: return "eSIM-PHT3G-30";
                }
            }
            
            // 필리핀 5G 총 5기가 후 무제한(저속) 상품들 (PHT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PHT5G-03";
                    case 2: return "eSIM-PHT5G-03";
                    case 3: return "eSIM-PHT5G-03";
                    case 4: return "eSIM-PHT5G-05";
                    case 5: return "eSIM-PHT5G-05";
                    case 6: return "eSIM-PHT5G-07";
                    case 7: return "eSIM-PHT5G-07";
                    case 8: return "eSIM-PHT5G-10";
                    case 9: return "eSIM-PHT5G-10";
                    case 10: return "eSIM-PHT5G-10";
                    case 15: return "eSIM-PHT5G-15";
                    case 20: return "eSIM-PHT5G-30";
                    case 30: return "eSIM-PHT5G-30";
                }
            }
            
            // 필리핀 5G 총 10기가 후 무제한(저속) 상품들 (PHT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PHT10G-03";
                    case 2: return "eSIM-PHT10G-03";
                    case 3: return "eSIM-PHT10G-03";
                    case 4: return "eSIM-PHT10G-05";
                    case 5: return "eSIM-PHT10G-05";
                    case 6: return "eSIM-PHT10G-07";
                    case 7: return "eSIM-PHT10G-07";
                    case 8: return "eSIM-PHT10G-10";
                    case 9: return "eSIM-PHT10G-10";
                    case 10: return "eSIM-PHT10G-10";
                    case 15: return "eSIM-PHT10G-15";
                    case 20: return "eSIM-PHT10G-30";
                    case 30: return "eSIM-PHT10G-30";
                }
            }
            
            // 필리핀 5G 총 20기가 후 무제한(저속) 상품들 (PHT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PHT20G-03";
                    case 2: return "eSIM-PHT20G-03";
                    case 3: return "eSIM-PHT20G-03";
                    case 4: return "eSIM-PHT20G-05";
                    case 5: return "eSIM-PHT20G-05";
                    case 6: return "eSIM-PHT20G-07";
                    case 7: return "eSIM-PHT20G-07";
                    case 8: return "eSIM-PHT20G-10";
                    case 9: return "eSIM-PHT20G-10";
                    case 10: return "eSIM-PHT20G-10";
                    case 15: return "eSIM-PHT20G-15";
                    case 20: return "eSIM-PHT20G-30";
                    case 30: return "eSIM-PHT20G-30";
                }
            }
            
            // 필리핀 5G 총 30기가 후 무제한(저속) 상품들 (PHT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-PHT30G-03";
                    case 2: return "eSIM-PHT30G-03";
                    case 3: return "eSIM-PHT30G-03";
                    case 4: return "eSIM-PHT30G-05";
                    case 5: return "eSIM-PHT30G-05";
                    case 6: return "eSIM-PHT30G-07";
                    case 7: return "eSIM-PHT30G-07";
                    case 8: return "eSIM-PHT30G-10";
                    case 9: return "eSIM-PHT30G-10";
                    case 10: return "eSIM-PHT30G-10";
                    case 15: return "eSIM-PHT30G-15";
                    case 20: return "eSIM-PHT30G-30";
                    case 30: return "eSIM-PHT30G-30";
                }
            }
        }
        
        // 태국 상품들
        if (productName.contains("태국") || productName.contains("TH")) {
            // 태국 5G 매일 1기가 후 무제한(저속) 상품들 (XMTY1GB)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY1GB-01";
                    case 2: return "eSIM-XMTY1GB-03";
                    case 3: return "eSIM-XMTY1GB-03";
                    case 4: return "eSIM-XMTY1GB-05";
                    case 5: return "eSIM-XMTY1GB-05";
                    case 6: return "eSIM-XMTY1GB-07";
                    case 7: return "eSIM-XMTY1GB-07";
                    case 8: return "eSIM-XMTY1GB-10";
                    case 9: return "eSIM-XMTY1GB-10";
                    case 10: return "eSIM-XMTY1GB-10";
                    case 15: return "eSIM-XMTY1GB-15";
                    case 20: return "eSIM-XMTY1GB-20";
                    case 30: return "eSIM-XMTY1GB-30";
                }
            }
            
            // 태국 5G 매일 2기가 후 무제한(저속) 상품들 (XMTY2GB)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY2GB-01";
                    case 2: return "eSIM-XMTY2GB-03";
                    case 3: return "eSIM-XMTY2GB-03";
                    case 4: return "eSIM-XMTY2GB-05";
                    case 5: return "eSIM-XMTY2GB-05";
                    case 6: return "eSIM-XMTY2GB-07";
                    case 7: return "eSIM-XMTY2GB-07";
                    case 8: return "eSIM-XMTY2GB-10";
                    case 9: return "eSIM-XMTY2GB-10";
                    case 10: return "eSIM-XMTY2GB-10";
                    case 15: return "eSIM-XMTY2GB-15";
                    case 20: return "eSIM-XMTY2GB-20";
                    case 30: return "eSIM-XMTY2GB-30";
                }
            }
            
            // 태국 5G 매일 3기가 후 무제한(저속) 상품들 (XMTY3GB)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY3GB-01";
                    case 2: return "eSIM-XMTY3GB-03";
                    case 3: return "eSIM-XMTY3GB-03";
                    case 4: return "eSIM-XMTY3GB-05";
                    case 5: return "eSIM-XMTY3GB-05";
                    case 6: return "eSIM-XMTY3GB-07";
                    case 7: return "eSIM-XMTY3GB-07";
                    case 8: return "eSIM-XMTY3GB-10";
                    case 9: return "eSIM-XMTY3GB-10";
                    case 10: return "eSIM-XMTY3GB-10";
                    case 15: return "eSIM-XMTY3GB-15";
                    case 20: return "eSIM-XMTY3GB-20";
                    case 30: return "eSIM-XMTY3GB-30";
                }
            }
            
            // 태국 5G 속도 무제한 상품들 (TH10M)
            if (productName.contains("5G 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-TH10M-01";
                    case 2: return "eSIM-TH10M-03";
                    case 3: return "eSIM-TH10M-03";
                    case 4: return "eSIM-TH10M-04";
                    case 5: return "eSIM-TH10M-05";
                    case 6: return "eSIM-TH10M-06";
                    case 7: return "eSIM-TH10M-07";
                    case 8: return "eSIM-TH10M-10";
                    case 9: return "eSIM-TH10M-10";
                    case 10: return "eSIM-TH10M-10";
                    case 15: return "eSIM-TH10M-15";
                    case 20: return "eSIM-TH10M-20";
                    case 30: return "eSIM-TH10M-30";
                }
            }
            
            // 태국 5G 총 3기가 후 무제한(저속) 상품들 (XMTYT3GB)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT3GB-03";
                    case 2: return "eSIM-XMTYT3GB-03";
                    case 3: return "eSIM-XMTYT3GB-03";
                    case 4: return "eSIM-XMTYT3GB-05";
                    case 5: return "eSIM-XMTYT3GB-05";
                    case 6: return "eSIM-XMTYT3GB-07";
                    case 7: return "eSIM-XMTYT3GB-07";
                    case 8: return "eSIM-XMTYT3GB-10";
                    case 9: return "eSIM-XMTYT3GB-10";
                    case 10: return "eSIM-XMTYT3GB-10";
                    case 15: return "eSIM-XMTYT3GB-15";
                    case 20: return "eSIM-XMTYT3GB-30";
                    case 30: return "eSIM-XMTYT3GB-30";
                }
            }
            
            // 태국 5G 총 5기가 후 무제한(저속) 상품들 (XMTYT5GB)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT5GB-03";
                    case 2: return "eSIM-XMTYT5GB-03";
                    case 3: return "eSIM-XMTYT5GB-03";
                    case 4: return "eSIM-XMTYT5GB-05";
                    case 5: return "eSIM-XMTYT5GB-05";
                    case 6: return "eSIM-XMTYT5GB-07";
                    case 7: return "eSIM-XMTYT5GB-07";
                    case 8: return "eSIM-XMTYT5GB-10";
                    case 9: return "eSIM-XMTYT5GB-10";
                    case 10: return "eSIM-XMTYT5GB-10";
                    case 15: return "eSIM-XMTYT5GB-15";
                    case 20: return "eSIM-XMTYT5GB-30";
                    case 30: return "eSIM-XMTYT5GB-30";
                }
            }
            
            // 태국 5G 총 10기가 후 무제한(저속) 상품들 (XMTYT10GB)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT10GB-03";
                    case 2: return "eSIM-XMTYT10GB-03";
                    case 3: return "eSIM-XMTYT10GB-03";
                    case 4: return "eSIM-XMTYT10GB-05";
                    case 5: return "eSIM-XMTYT10GB-05";
                    case 6: return "eSIM-XMTYT10GB-07";
                    case 7: return "eSIM-XMTYT10GB-07";
                    case 8: return "eSIM-XMTYT10GB-10";
                    case 9: return "eSIM-XMTYT10GB-10";
                    case 10: return "eSIM-XMTYT10GB-10";
                    case 15: return "eSIM-XMTYT10GB-15";
                    case 20: return "eSIM-XMTYT10GB-30";
                    case 30: return "eSIM-XMTYT10GB-30";
                }
            }
            
            // 태국 5G 총 20기가 후 무제한(저속) 상품들 (XMTYT20GB)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT20GB-03";
                    case 2: return "eSIM-XMTYT20GB-03";
                    case 3: return "eSIM-XMTYT20GB-03";
                    case 4: return "eSIM-XMTYT20GB-05";
                    case 5: return "eSIM-XMTYT20GB-05";
                    case 6: return "eSIM-XMTYT20GB-07";
                    case 7: return "eSIM-XMTYT20GB-07";
                    case 8: return "eSIM-XMTYT20GB-10";
                    case 9: return "eSIM-XMTYT20GB-10";
                    case 10: return "eSIM-XMTYT20GB-10";
                    case 15: return "eSIM-XMTYT20GB-15";
                    case 20: return "eSIM-XMTYT20GB-30";
                    case 30: return "eSIM-XMTYT20GB-30";
                }
            }
            
            // 태국 5G 총 30기가 후 무제한(저속) 상품들 (XMTYT30GB)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT30GB-03";
                    case 2: return "eSIM-XMTYT30GB-03";
                    case 3: return "eSIM-XMTYT30GB-03";
                    case 4: return "eSIM-XMTYT30GB-05";
                    case 5: return "eSIM-XMTYT30GB-05";
                    case 6: return "eSIM-XMTYT30GB-07";
                    case 7: return "eSIM-XMTYT30GB-07";
                    case 8: return "eSIM-XMTYT30GB-10";
                    case 9: return "eSIM-XMTYT30GB-10";
                    case 10: return "eSIM-XMTYT30GB-10";
                    case 15: return "eSIM-XMTYT30GB-15";
                    case 20: return "eSIM-XMTYT30GB-30";
                    case 30: return "eSIM-XMTYT30GB-30";
                }
            }
            
            // 태국 5G 총 50기가 후 무제한(저속) 상품들 (XMTYT50GB)
            if (productName.contains("총 50기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT50GB-03";
                    case 2: return "eSIM-XMTYT50GB-03";
                    case 3: return "eSIM-XMTYT50GB-03";
                    case 4: return "eSIM-XMTYT50GB-05";
                    case 5: return "eSIM-XMTYT50GB-05";
                    case 6: return "eSIM-XMTYT50GB-07";
                    case 7: return "eSIM-XMTYT50GB-07";
                    case 8: return "eSIM-XMTYT50GB-10";
                    case 9: return "eSIM-XMTYT50GB-10";
                    case 10: return "eSIM-XMTYT50GB-10";
                    case 15: return "eSIM-XMTYT50GB-15";
                    case 20: return "eSIM-XMTYT50GB-30";
                    case 30: return "eSIM-XMTYT50GB-30";
                }
            }
        }
        
        // 인도네시아 상품들
        if (productName.contains("인도네시아") || productName.contains("ID")) {
            // 인도네시아 5G 매일 1기가 후 저속 무제한 상품들 (XMTY1GB)
            if (productName.contains("매일 1기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY1GB-01";
                    case 2: return "eSIM-XMTY1GB-03";
                    case 3: return "eSIM-XMTY1GB-03";
                    case 4: return "eSIM-XMTY1GB-05";
                    case 5: return "eSIM-XMTY1GB-05";
                    case 6: return "eSIM-XMTY1GB-07";
                    case 7: return "eSIM-XMTY1GB-07";
                    case 8: return "eSIM-XMTY1GB-10";
                    case 9: return "eSIM-XMTY1GB-10";
                    case 10: return "eSIM-XMTY1GB-10";
                    case 15: return "eSIM-XMTY1GB-15";
                    case 20: return "eSIM-XMTY1GB-20";
                    case 30: return "eSIM-XMTY1GB-30";
                }
            }
            
            // 인도네시아 5G 매일 2기가 후 저속 무제한 상품들 (XMTY2GB)
            if (productName.contains("매일 2기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY2GB-01";
                    case 2: return "eSIM-XMTY2GB-03";
                    case 3: return "eSIM-XMTY2GB-03";
                    case 4: return "eSIM-XMTY2GB-05";
                    case 5: return "eSIM-XMTY2GB-05";
                    case 6: return "eSIM-XMTY2GB-07";
                    case 7: return "eSIM-XMTY2GB-07";
                    case 8: return "eSIM-XMTY2GB-10";
                    case 9: return "eSIM-XMTY2GB-10";
                    case 10: return "eSIM-XMTY2GB-10";
                    case 15: return "eSIM-XMTY2GB-15";
                    case 20: return "eSIM-XMTY2GB-20";
                    case 30: return "eSIM-XMTY2GB-30";
                }
            }
            
            // 인도네시아 5G 매일 3기가 후 저속 무제한 상품들 (XMTY3GB)
            if (productName.contains("매일 3기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTY3GB-01";
                    case 2: return "eSIM-XMTY3GB-03";
                    case 3: return "eSIM-XMTY3GB-03";
                    case 4: return "eSIM-XMTY3GB-05";
                    case 5: return "eSIM-XMTY3GB-05";
                    case 6: return "eSIM-XMTY3GB-07";
                    case 7: return "eSIM-XMTY3GB-07";
                    case 8: return "eSIM-XMTY3GB-10";
                    case 9: return "eSIM-XMTY3GB-10";
                    case 10: return "eSIM-XMTY3GB-10";
                    case 15: return "eSIM-XMTY3GB-15";
                    case 20: return "eSIM-XMTY3GB-20";
                    case 30: return "eSIM-XMTY3GB-30";
                }
            }
            
            // 인도네시아 5G 속도 무제한 상품들 (XMTY10M)
            if (productName.contains("5G 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-XMTY10M-01";
                    case 2: return "eSIM-XMTY10M-03";
                    case 3: return "eSIM-XMTY10M-03";
                    case 4: return "eSIM-XMTY10M-05";
                    case 5: return "eSIM-XMTY10M-05";
                    case 6: return "eSIM-XMTY10M-07";
                    case 7: return "eSIM-XMTY10M-07";
                    case 8: return "eSIM-XMTY10M-10";
                    case 9: return "eSIM-XMTY10M-10";
                    case 10: return "eSIM-XMTY10M-10";
                    case 15: return "eSIM-XMTY10M-15";
                    case 20: return "eSIM-XMTY10M-20";
                    case 30: return "eSIM-XMTY10M-30";
                }
            }
            
            // 인도네시아 5G 총 3기가 후 저속 무제한 상품들 (XMTYT3GB)
            if (productName.contains("총 3기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT3GB-03";
                    case 2: return "eSIM-XMTYT3GB-03";
                    case 3: return "eSIM-XMTYT3GB-03";
                    case 4: return "eSIM-XMTYT3GB-05";
                    case 5: return "eSIM-XMTYT3GB-05";
                    case 6: return "eSIM-XMTYT3GB-07";
                    case 7: return "eSIM-XMTYT3GB-07";
                    case 8: return "eSIM-XMTYT3GB-10";
                    case 9: return "eSIM-XMTYT3GB-10";
                    case 10: return "eSIM-XMTYT3GB-10";
                    case 15: return "eSIM-XMTYT3GB-15";
                    case 20: return "eSIM-XMTYT3GB-30";
                    case 30: return "eSIM-XMTYT3GB-30";
                }
            }
            
            // 인도네시아 5G 총 5기가 후 저속 무제한 상품들 (XMTYT5GB)
            if (productName.contains("총 5기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT5GB-03";
                    case 2: return "eSIM-XMTYT5GB-03";
                    case 3: return "eSIM-XMTYT5GB-03";
                    case 4: return "eSIM-XMTYT5GB-05";
                    case 5: return "eSIM-XMTYT5GB-05";
                    case 6: return "eSIM-XMTYT5GB-07";
                    case 7: return "eSIM-XMTYT5GB-07";
                    case 8: return "eSIM-XMTYT5GB-10";
                    case 9: return "eSIM-XMTYT5GB-10";
                    case 10: return "eSIM-XMTYT5GB-10";
                    case 15: return "eSIM-XMTYT5GB-15";
                    case 20: return "eSIM-XMTYT5GB-30";
                    case 30: return "eSIM-XMTYT5GB-30";
                }
            }
            
            // 인도네시아 5G 총 10기가 후 저속 무제한 상품들 (XMTYT10GB)
            if (productName.contains("총 10기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT10GB-03";
                    case 2: return "eSIM-XMTYT10GB-03";
                    case 3: return "eSIM-XMTYT10GB-03";
                    case 4: return "eSIM-XMTYT10GB-05";
                    case 5: return "eSIM-XMTYT10GB-05";
                    case 6: return "eSIM-XMTYT10GB-07";
                    case 7: return "eSIM-XMTYT10GB-07";
                    case 8: return "eSIM-XMTYT10GB-10";
                    case 9: return "eSIM-XMTYT10GB-10";
                    case 10: return "eSIM-XMTYT10GB-10";
                    case 15: return "eSIM-XMTYT10GB-15";
                    case 20: return "eSIM-XMTYT10GB-30";
                    case 30: return "eSIM-XMTYT10GB-30";
                }
            }
            
            // 인도네시아 5G 총 20기가 후 저속 무제한 상품들 (XMTYT20GB)
            if (productName.contains("총 20기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT20GB-03";
                    case 2: return "eSIM-XMTYT20GB-03";
                    case 3: return "eSIM-XMTYT20GB-03";
                    case 4: return "eSIM-XMTYT20GB-05";
                    case 5: return "eSIM-XMTYT20GB-05";
                    case 6: return "eSIM-XMTYT20GB-07";
                    case 7: return "eSIM-XMTYT20GB-07";
                    case 8: return "eSIM-XMTYT20GB-10";
                    case 9: return "eSIM-XMTYT20GB-10";
                    case 10: return "eSIM-XMTYT20GB-10";
                    case 15: return "eSIM-XMTYT20GB-15";
                    case 20: return "eSIM-XMTYT20GB-30";
                    case 30: return "eSIM-XMTYT20GB-30";
                }
            }
            
            // 인도네시아 5G 총 30기가 후 저속 무제한 상품들 (XMTYT30GB)
            if (productName.contains("총 30기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-XMTYT30GB-03";
                    case 2: return "eSIM-XMTYT30GB-03";
                    case 3: return "eSIM-XMTYT30GB-03";
                    case 4: return "eSIM-XMTYT30GB-05";
                    case 5: return "eSIM-XMTYT30GB-05";
                    case 6: return "eSIM-XMTYT30GB-07";
                    case 7: return "eSIM-XMTYT30GB-07";
                    case 8: return "eSIM-XMTYT30GB-10";
                    case 9: return "eSIM-XMTYT30GB-10";
                    case 10: return "eSIM-XMTYT30GB-10";
                    case 15: return "eSIM-XMTYT30GB-15";
                    case 20: return "eSIM-XMTYT30GB-30";
                    case 30: return "eSIM-XMTYT30GB-30";
                }
            }
        }
        
        // 중국 상품들
        if (productName.contains("중국") || productName.contains("CN")) {
            // 중국 5G 매일 1기가 후 무제한(저속) 상품들 (CIMCN1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-CIMCN1G-01";
                    case 2: return "eSIM-CIMCN1G-03";
                    case 3: return "eSIM-CIMCN1G-03";
                    case 4: return "eSIM-CIMCN1G-05";
                    case 5: return "eSIM-CIMCN1G-05";
                    case 6: return "eSIM-CIMCN1G-07";
                    case 7: return "eSIM-CIMCN1G-07";
                    case 8: return "eSIM-CIMCN1G-10";
                    case 9: return "eSIM-CIMCN1G-10";
                    case 10: return "eSIM-CIMCN1G-10";
                    case 15: return "eSIM-CIMCN1G-15";
                    case 20: return "eSIM-CIMCN1G-20";
                    case 30: return "eSIM-CIMCN1G-30";
                }
            }
            
            // 중국 5G 매일 2기가 후 무제한(저속) 상품들 (CIMCN2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-CIMCN2G-01";
                    case 2: return "eSIM-CIMCN2G-03";
                    case 3: return "eSIM-CIMCN2G-03";
                    case 4: return "eSIM-CIMCN2G-05";
                    case 5: return "eSIM-CIMCN2G-05";
                    case 6: return "eSIM-CIMCN2G-07";
                    case 7: return "eSIM-CIMCN2G-07";
                    case 8: return "eSIM-CIMCN2G-10";
                    case 9: return "eSIM-CIMCN2G-10";
                    case 10: return "eSIM-CIMCN2G-10";
                    case 15: return "eSIM-CIMCN2G-15";
                    case 20: return "eSIM-CIMCN2G-20";
                    case 30: return "eSIM-CIMCN2G-30";
                }
            }
            
            // 중국 5G 매일 3기가 후 무제한(저속) 상품들 (CIMCN3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-CIMCN3G-01";
                    case 2: return "eSIM-CIMCN3G-03";
                    case 3: return "eSIM-CIMCN3G-03";
                    case 4: return "eSIM-CIMCN3G-05";
                    case 5: return "eSIM-CIMCN3G-05";
                    case 6: return "eSIM-CIMCN3G-07";
                    case 7: return "eSIM-CIMCN3G-07";
                    case 8: return "eSIM-CIMCN3G-10";
                    case 9: return "eSIM-CIMCN3G-10";
                    case 10: return "eSIM-CIMCN3G-10";
                    case 15: return "eSIM-CIMCN3G-15";
                    case 20: return "eSIM-CIMCN3G-20";
                    case 30: return "eSIM-CIMCN3G-30";
                }
            }
            
            // 중국 5G 속도 무제한 상품들 (CN10M)
            if (productName.contains("5G 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-CN10M-01";
                    case 2: return "eSIM-CN10M-03";
                    case 3: return "eSIM-CN10M-03";
                    case 4: return "eSIM-CN10M-05";
                    case 5: return "eSIM-CN10M-05";
                    case 6: return "eSIM-CN10M-07";
                    case 7: return "eSIM-CN10M-07";
                    case 8: return "eSIM-CN10M-10";
                    case 9: return "eSIM-CN10M-10";
                    case 10: return "eSIM-CN10M-10";
                    case 15: return "eSIM-CN10M-15";
                    case 20: return "eSIM-CN10M-20";
                    case 30: return "eSIM-CN10M-30";
                }
            }
            
            // 중국 총 3기가 이후 무제한(저속) 상품들 (CIMCNT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 3: return "eSIM-CIMCNT3G-03";
                    case 4: return "eSIM-CIMCNT3G-05";
                    case 5: return "eSIM-CIMCNT3G-05";
                    case 6: return "eSIM-CIMCNT3G-07";
                    case 7: return "eSIM-CIMCNT3G-07";
                    case 8: return "eSIM-CIMCNT3G-10";
                    case 9: return "eSIM-CIMCNT3G-10";
                    case 10: return "eSIM-CIMCNT3G-10";
                    case 15: return "eSIM-CIMCNT3G-15";
                    case 30: return "eSIM-CIMCNT3G-30";
                }
            }
            
            // 중국 총 5기가 이후 무제한(저속) 상품들 (CIMCNT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 3: return "eSIM-CIMCNT5G-03";
                    case 4: return "eSIM-CIMCNT5G-05";
                    case 5: return "eSIM-CIMCNT5G-05";
                    case 6: return "eSIM-CIMCNT5G-07";
                    case 7: return "eSIM-CIMCNT5G-07";
                    case 8: return "eSIM-CIMCNT5G-10";
                    case 9: return "eSIM-CIMCNT5G-10";
                    case 10: return "eSIM-CIMCNT5G-10";
                    case 15: return "eSIM-CIMCNT5G-15";
                    case 30: return "eSIM-CIMCNT5G-30";
                }
            }
            
            // 중국 총 10기가 이후 무제한(저속) 상품들 (CIMCNT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 3: return "eSIM-CIMCNT10G-03";
                    case 4: return "eSIM-CIMCNT10G-05";
                    case 5: return "eSIM-CIMCNT10G-05";
                    case 6: return "eSIM-CIMCNT10G-07";
                    case 7: return "eSIM-CIMCNT10G-07";
                    case 8: return "eSIM-CIMCNT10G-10";
                    case 9: return "eSIM-CIMCNT10G-10";
                    case 10: return "eSIM-CIMCNT10G-10";
                    case 15: return "eSIM-CIMCNT10G-15";
                    case 30: return "eSIM-CIMCNT10G-30";
                }
            }
            
            // 중국 총 20기가 이후 무제한(저속) 상품들 (CIMCNT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 3: return "eSIM-CIMCNT20G-03";
                    case 4: return "eSIM-CIMCNT20G-05";
                    case 5: return "eSIM-CIMCNT20G-05";
                    case 6: return "eSIM-CIMCNT20G-07";
                    case 7: return "eSIM-CIMCNT20G-07";
                    case 8: return "eSIM-CIMCNT20G-10";
                    case 9: return "eSIM-CIMCNT20G-10";
                    case 10: return "eSIM-CIMCNT20G-10";
                    case 15: return "eSIM-CIMCNT20G-15";
                    case 30: return "eSIM-CIMCNT20G-30";
                }
            }
            
            // 중국 총 30기가 이후 무제한(저속) 상품들 (CIMCNT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 3: return "eSIM-CIMCNT30G-03";
                    case 4: return "eSIM-CIMCNT30G-05";
                    case 5: return "eSIM-CIMCNT30G-05";
                    case 6: return "eSIM-CIMCNT30G-07";
                    case 7: return "eSIM-CIMCNT30G-07";
                    case 8: return "eSIM-CIMCNT30G-10";
                    case 9: return "eSIM-CIMCNT30G-10";
                    case 10: return "eSIM-CIMCNT30G-10";
                    case 15: return "eSIM-CIMCNT30G-15";
                    case 30: return "eSIM-CIMCNT30G-30";
                }
            }
        }
        
        // 싱가폴 상품들
        if (productName.contains("싱가폴") || productName.contains("SG")) {
            // 싱가폴 5G 매일 1기가 후 무제한(저속) 상품들 (SM1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM1G-01";
                    case 2: return "eSIM-SM1G-03";
                    case 3: return "eSIM-SM1G-03";
                    case 4: return "eSIM-SM1G-05";
                    case 5: return "eSIM-SM1G-05";
                    case 6: return "eSIM-SM1G-07";
                    case 7: return "eSIM-SM1G-07";
                    case 8: return "eSIM-SM1G-10";
                    case 9: return "eSIM-SM1G-10";
                    case 10: return "eSIM-SM1G-10";
                    case 15: return "eSIM-SM1G-15";
                    case 20: return "eSIM-SM1G-20";
                    case 30: return "eSIM-SM1G-30";
                }
            }
            
            // 싱가폴 5G 매일 2기가 후 무제한(저속) 상품들 (SM2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM2G-01";
                    case 2: return "eSIM-SM2G-03";
                    case 3: return "eSIM-SM2G-03";
                    case 4: return "eSIM-SM2G-05";
                    case 5: return "eSIM-SM2G-05";
                    case 6: return "eSIM-SM2G-07";
                    case 7: return "eSIM-SM2G-07";
                    case 8: return "eSIM-SM2G-10";
                    case 9: return "eSIM-SM2G-10";
                    case 10: return "eSIM-SM2G-10";
                    case 15: return "eSIM-SM2G-15";
                    case 20: return "eSIM-SM2G-20";
                    case 30: return "eSIM-SM2G-30";
                }
            }
            
            // 싱가폴 5G 매일 3기가 후 무제한(저속) 상품들 (SM3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SM3G-01";
                    case 2: return "eSIM-SM3G-03";
                    case 3: return "eSIM-SM3G-03";
                    case 4: return "eSIM-SM3G-05";
                    case 5: return "eSIM-SM3G-05";
                    case 6: return "eSIM-SM3G-07";
                    case 7: return "eSIM-SM3G-07";
                    case 8: return "eSIM-SM3G-10";
                    case 9: return "eSIM-SM3G-10";
                    case 10: return "eSIM-SM3G-10";
                    case 15: return "eSIM-SM3G-15";
                    case 20: return "eSIM-SM3G-20";
                    case 30: return "eSIM-SM3G-30";
                }
            }
            
            // 싱가폴 5G 속도 무제한 상품들 (SM10M)
            if (productName.contains("5G 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-SM10M-03";
                    case 2: return "eSIM-SM10M-03";
                    case 3: return "eSIM-SM10M-03";
                    case 4: return "eSIM-SM10M-05";
                    case 5: return "eSIM-SM10M-05";
                    case 6: return "eSIM-SM10M-07";
                    case 7: return "eSIM-SM10M-07";
                    case 8: return "eSIM-SM10M-10";
                    case 9: return "eSIM-SM10M-10";
                    case 10: return "eSIM-SM10M-10";
                    case 15: return "eSIM-SM10M-15";
                    case 20: return "eSIM-SM10M-20";
                    case 30: return "eSIM-SM10M-30";
                }
            }
            
            // 싱가폴 5G 총 5기가 후 무제한(저속) 상품들 (SMT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT5G-03";
                    case 2: return "eSIM-SMT5G-03";
                    case 3: return "eSIM-SMT5G-03";
                    case 4: return "eSIM-SMT5G-05";
                    case 5: return "eSIM-SMT5G-05";
                    case 6: return "eSIM-SMT5G-07";
                    case 7: return "eSIM-SMT5G-07";
                    case 8: return "eSIM-SMT5G-10";
                    case 9: return "eSIM-SMT5G-10";
                    case 10: return "eSIM-SMT5G-10";
                    case 15: return "eSIM-SMT5G-15";
                    case 20: return "eSIM-SMT5G-30";
                    case 30: return "eSIM-SMT5G-30";
                }
            }
            
            // 싱가폴 5G 총 10기가 후 무제한(저속) 상품들 (SMT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT10G-03";
                    case 2: return "eSIM-SMT10G-03";
                    case 3: return "eSIM-SMT10G-03";
                    case 4: return "eSIM-SMT10G-05";
                    case 5: return "eSIM-SMT10G-05";
                    case 6: return "eSIM-SMT10G-07";
                    case 7: return "eSIM-SMT10G-07";
                    case 8: return "eSIM-SMT10G-10";
                    case 9: return "eSIM-SMT10G-10";
                    case 10: return "eSIM-SMT10G-10";
                    case 15: return "eSIM-SMT10G-15";
                    case 20: return "eSIM-SMT10G-30";
                    case 30: return "eSIM-SMT10G-30";
                }
            }
            
            // 싱가폴 5G 총 20기가 후 무제한(저속) 상품들 (SMT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT20G-03";
                    case 2: return "eSIM-SMT20G-03";
                    case 3: return "eSIM-SMT20G-03";
                    case 4: return "eSIM-SMT20G-05";
                    case 5: return "eSIM-SMT20G-05";
                    case 6: return "eSIM-SMT20G-07";
                    case 7: return "eSIM-SMT20G-07";
                    case 8: return "eSIM-SMT20G-10";
                    case 9: return "eSIM-SMT20G-10";
                    case 10: return "eSIM-SMT20G-10";
                    case 15: return "eSIM-SMT20G-15";
                    case 20: return "eSIM-SMT20G-30";
                    case 30: return "eSIM-SMT20G-30";
                }
            }
            
            // 싱가폴 5G 총 30기가 후 무제한(저속) 상품들 (SMT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-SMT30G-03";
                    case 2: return "eSIM-SMT30G-03";
                    case 3: return "eSIM-SMT30G-03";
                    case 4: return "eSIM-SMT30G-05";
                    case 5: return "eSIM-SMT30G-05";
                    case 6: return "eSIM-SMT30G-07";
                    case 7: return "eSIM-SMT30G-07";
                    case 8: return "eSIM-SMT30G-10";
                    case 9: return "eSIM-SMT30G-10";
                    case 10: return "eSIM-SMT30G-10";
                    case 15: return "eSIM-SMT30G-15";
                    case 20: return "eSIM-SMT30G-30";
                    case 30: return "eSIM-SMT30G-30";
                }
            }
        }
        
        // 홍마 상품들
        if (productName.contains("홍마") || productName.contains("HM")) {
            // 홍마 5G 매일 1기가 후 무제한(저속) 상품들 (HM1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HM1G-01";
                    case 2: return "eSIM-HM1G-03";
                    case 3: return "eSIM-HM1G-03";
                    case 4: return "eSIM-HM1G-05";
                    case 5: return "eSIM-HM1G-05";
                    case 6: return "eSIM-HM1G-07";
                    case 7: return "eSIM-HM1G-07";
                    case 8: return "eSIM-HM1G-10";
                    case 9: return "eSIM-HM1G-10";
                    case 10: return "eSIM-HM1G-10";
                    case 15: return "eSIM-HM1G-15";
                    case 20: return "eSIM-HM1G-20";
                    case 30: return "eSIM-HM1G-30";
                }
            }
            
            // 홍마 5G 매일 2기가 후 무제한(저속) 상품들 (HM2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HM2G-01";
                    case 2: return "eSIM-HM2G-03";
                    case 3: return "eSIM-HM2G-03";
                    case 4: return "eSIM-HM2G-05";
                    case 5: return "eSIM-HM2G-05";
                    case 6: return "eSIM-HM2G-07";
                    case 7: return "eSIM-HM2G-07";
                    case 8: return "eSIM-HM2G-10";
                    case 9: return "eSIM-HM2G-10";
                    case 10: return "eSIM-HM2G-10";
                    case 15: return "eSIM-HM2G-15";
                    case 20: return "eSIM-HM2G-20";
                    case 30: return "eSIM-HM2G-30";
                }
            }
            
            // 홍마 5G 매일 3기가 후 무제한(저속) 상품들 (HM3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HM3G-01";
                    case 2: return "eSIM-HM3G-03";
                    case 3: return "eSIM-HM3G-03";
                    case 4: return "eSIM-HM3G-05";
                    case 5: return "eSIM-HM3G-05";
                    case 6: return "eSIM-HM3G-07";
                    case 7: return "eSIM-HM3G-07";
                    case 8: return "eSIM-HM3G-10";
                    case 9: return "eSIM-HM3G-10";
                    case 10: return "eSIM-HM3G-10";
                    case 15: return "eSIM-HM3G-15";
                    case 20: return "eSIM-HM3G-20";
                    case 30: return "eSIM-HM3G-30";
                }
            }
            
            // 홍마 LTE 무제한 상품들 (HMMAX)
            if (productName.contains("LTE 무제한") && productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-HMMAX-01";
                    case 2: return "eSIM-HMMAX-03";
                    case 3: return "eSIM-HMMAX-03";
                    case 4: return "eSIM-HMMAX-05";
                    case 5: return "eSIM-HMMAX-05";
                    case 6: return "eSIM-HMMAX-07";
                    case 7: return "eSIM-HMMAX-07";
                    case 8: return "eSIM-HMMAX-10";
                    case 9: return "eSIM-HMMAX-10";
                    case 10: return "eSIM-HMMAX-10";
                    case 15: return "eSIM-HMMAX-15";
                    case 20: return "eSIM-HMMAX-20";
                    case 30: return "eSIM-HMMAX-30";
                }
            }
            
            // 홍마 5G 속도 무제한 상품들 (HM10M)
            if (productName.contains("5G 속도 무제한") && productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-HM10M-01";
                    case 2: return "eSIM-HM10M-03";
                    case 3: return "eSIM-HM10M-03";
                    case 4: return "eSIM-HM10M-05";
                    case 5: return "eSIM-HM10M-05";
                    case 6: return "eSIM-HM10M-07";
                    case 7: return "eSIM-HM10M-07";
                    case 8: return "eSIM-HM10M-10";
                    case 9: return "eSIM-HM10M-10";
                    case 10: return "eSIM-HM10M-10";
                    case 15: return "eSIM-HM10M-15";
                    case 20: return "eSIM-HM10M-20";
                    case 30: return "eSIM-HM10M-30";
                }
            }
            
            // 홍마 5G 총 3기가 이후 무제한(저속) 상품들 (HMT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HMT3G-03";
                    case 2: return "eSIM-HMT3G-03";
                    case 3: return "eSIM-HMT3G-03";
                    case 4: return "eSIM-HMT3G-05";
                    case 5: return "eSIM-HMT3G-05";
                    case 6: return "eSIM-HMT3G-07";
                    case 7: return "eSIM-HMT3G-07";
                    case 8: return "eSIM-HMT3G-10";
                    case 9: return "eSIM-HMT3G-10";
                    case 10: return "eSIM-HMT3G-10";
                    case 15: return "eSIM-HMT3G-15";
                    case 20: return "eSIM-HMT3G-30";
                    case 30: return "eSIM-HMT3G-30";
                }
            }
            
            // 홍마 5G 총 5기가 이후 무제한(저속) 상품들 (HMT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HMT5G-03";
                    case 2: return "eSIM-HMT5G-03";
                    case 3: return "eSIM-HMT5G-03";
                    case 4: return "eSIM-HMT5G-05";
                    case 5: return "eSIM-HMT5G-05";
                    case 6: return "eSIM-HMT5G-07";
                    case 7: return "eSIM-HMT5G-07";
                    case 8: return "eSIM-HMT5G-10";
                    case 9: return "eSIM-HMT5G-10";
                    case 10: return "eSIM-HMT5G-10";
                    case 15: return "eSIM-HMT5G-15";
                    case 20: return "eSIM-HMT5G-30";
                    case 30: return "eSIM-HMT5G-30";
                }
            }
            
            // 홍마 5G 총 10기가 이후 무제한(저속) 상품들 (HMT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HMT10G-03";
                    case 2: return "eSIM-HMT10G-03";
                    case 3: return "eSIM-HMT10G-03";
                    case 4: return "eSIM-HMT10G-05";
                    case 5: return "eSIM-HMT10G-05";
                    case 6: return "eSIM-HMT10G-07";
                    case 7: return "eSIM-HMT10G-07";
                    case 8: return "eSIM-HMT10G-10";
                    case 9: return "eSIM-HMT10G-10";
                    case 10: return "eSIM-HMT10G-10";
                    case 15: return "eSIM-HMT10G-15";
                    case 20: return "eSIM-HMT10G-30";
                    case 30: return "eSIM-HMT10G-30";
                }
            }
            
            // 홍마 5G 총 20기가 이후 무제한(저속) 상품들 (HMT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HMT20G-03";
                    case 2: return "eSIM-HMT20G-03";
                    case 3: return "eSIM-HMT20G-03";
                    case 4: return "eSIM-HMT20G-05";
                    case 5: return "eSIM-HMT20G-05";
                    case 6: return "eSIM-HMT20G-07";
                    case 7: return "eSIM-HMT20G-07";
                    case 8: return "eSIM-HMT20G-10";
                    case 9: return "eSIM-HMT20G-10";
                    case 10: return "eSIM-HMT20G-10";
                    case 15: return "eSIM-HMT20G-15";
                    case 20: return "eSIM-HMT20G-30";
                    case 30: return "eSIM-HMT20G-30";
                }
            }
            
            // 홍마 5G 총 30기가 이후 무제한(저속) 상품들 (HMT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-HMT30G-03";
                    case 2: return "eSIM-HMT30G-03";
                    case 3: return "eSIM-HMT30G-03";
                    case 4: return "eSIM-HMT30G-05";
                    case 5: return "eSIM-HMT30G-05";
                    case 6: return "eSIM-HMT30G-07";
                    case 7: return "eSIM-HMT30G-07";
                    case 8: return "eSIM-HMT30G-10";
                    case 9: return "eSIM-HMT30G-10";
                    case 10: return "eSIM-HMT30G-10";
                    case 15: return "eSIM-HMT30G-15";
                    case 20: return "eSIM-HMT30G-30";
                    case 30: return "eSIM-HMT30G-30";
                }
            }
        }
        
        // 미국 상품들
        if (productName.contains("미국") || productName.contains("USA")) {
            // 미국 5G 매일 1기가 후 무제한(저속) 상품들 (USA1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USA1G-01";
                    case 2: return "eSIM-USA1G-03";
                    case 3: return "eSIM-USA1G-03";
                    case 4: return "eSIM-USA1G-05";
                    case 5: return "eSIM-USA1G-05";
                    case 6: return "eSIM-USA1G-07";
                    case 7: return "eSIM-USA1G-07";
                    case 8: return "eSIM-USA1G-10";
                    case 9: return "eSIM-USA1G-10";
                    case 10: return "eSIM-USA1G-10";
                    case 15: return "eSIM-USA1G-15";
                    case 20: return "eSIM-USA1G-20";
                    case 30: return "eSIM-USA1G-30";
                }
            }
            
            // 미국 5G 매일 2기가 후 무제한(저속) 상품들 (USA2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USA2G-01";
                    case 2: return "eSIM-USA2G-03";
                    case 3: return "eSIM-USA2G-03";
                    case 4: return "eSIM-USA2G-05";
                    case 5: return "eSIM-USA2G-05";
                    case 6: return "eSIM-USA2G-07";
                    case 7: return "eSIM-USA2G-07";
                    case 8: return "eSIM-USA2G-10";
                    case 9: return "eSIM-USA2G-10";
                    case 10: return "eSIM-USA2G-10";
                    case 15: return "eSIM-USA2G-15";
                    case 20: return "eSIM-USA2G-20";
                    case 30: return "eSIM-USA2G-30";
                }
            }
            
            // 미국 5G 매일 3기가 후 무제한(저속) 상품들 (USA3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USA3G-01";
                    case 2: return "eSIM-USA3G-03";
                    case 3: return "eSIM-USA3G-03";
                    case 4: return "eSIM-USA3G-05";
                    case 5: return "eSIM-USA3G-05";
                    case 6: return "eSIM-USA3G-07";
                    case 7: return "eSIM-USA3G-07";
                    case 8: return "eSIM-USA3G-10";
                    case 9: return "eSIM-USA3G-10";
                    case 10: return "eSIM-USA3G-10";
                    case 15: return "eSIM-USA3G-15";
                    case 20: return "eSIM-USA3G-20";
                    case 30: return "eSIM-USA3G-30";
                }
            }
            
            // 미국 LTE 속도 무제한 상품들 (USAMAX)
            if (productName.contains("LTE 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-USAMAX-01";
                    case 2: return "eSIM-USAMAX-03";
                    case 3: return "eSIM-USAMAX-03";
                    case 4: return "eSIM-USAMAX-05";
                    case 5: return "eSIM-USAMAX-05";
                    case 6: return "eSIM-USAMAX-07";
                    case 7: return "eSIM-USAMAX-07";
                    case 8: return "eSIM-USAMAX-10";
                    case 9: return "eSIM-USAMAX-10";
                    case 10: return "eSIM-USAMAX-10";
                    case 15: return "eSIM-USAMAX-15";
                    case 20: return "eSIM-USAMAX-20";
                    case 30: return "eSIM-USAMAX-30";
                }
            }
            
            // 미국 5G 총 5기가 후 무제한(저속) 상품들 (USAT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USAT5G-03";
                    case 2: return "eSIM-USAT5G-03";
                    case 3: return "eSIM-USAT5G-03";
                    case 4: return "eSIM-USAT5G-05";
                    case 5: return "eSIM-USAT5G-05";
                    case 6: return "eSIM-USAT5G-07";
                    case 7: return "eSIM-USAT5G-07";
                    case 8: return "eSIM-USAT5G-10";
                    case 9: return "eSIM-USAT5G-10";
                    case 10: return "eSIM-USAT5G-10";
                    case 15: return "eSIM-USAT5G-15";
                    case 20: return "eSIM-USAT5G-30";
                    case 30: return "eSIM-USAT5G-30";
                }
            }
            
            // 미국 5G 총 10기가 후 무제한(저속) 상품들 (USAT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USAT10G-03";
                    case 2: return "eSIM-USAT10G-03";
                    case 3: return "eSIM-USAT10G-03";
                    case 4: return "eSIM-USAT10G-05";
                    case 5: return "eSIM-USAT10G-05";
                    case 6: return "eSIM-USAT10G-07";
                    case 7: return "eSIM-USAT10G-07";
                    case 8: return "eSIM-USAT10G-10";
                    case 9: return "eSIM-USAT10G-10";
                    case 10: return "eSIM-USAT10G-10";
                    case 15: return "eSIM-USAT10G-15";
                    case 20: return "eSIM-USAT10G-30";
                    case 30: return "eSIM-USAT10G-30";
                }
            }
            
            // 미국 5G 총 20기가 후 무제한(저속) 상품들 (USAT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USAT20G-03";
                    case 2: return "eSIM-USAT20G-03";
                    case 3: return "eSIM-USAT20G-03";
                    case 4: return "eSIM-USAT20G-05";
                    case 5: return "eSIM-USAT20G-05";
                    case 6: return "eSIM-USAT20G-07";
                    case 7: return "eSIM-USAT20G-07";
                    case 8: return "eSIM-USAT20G-10";
                    case 9: return "eSIM-USAT20G-10";
                    case 10: return "eSIM-USAT20G-10";
                    case 15: return "eSIM-USAT20G-15";
                    case 20: return "eSIM-USAT20G-30";
                    case 30: return "eSIM-USAT20G-30";
                }
            }
            
            // 미국 5G 총 30기가 후 무제한(저속) 상품들 (USAT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한(저속)")) {
                switch (day) {
                    case 1: return "eSIM-USAT30G-03";
                    case 2: return "eSIM-USAT30G-03";
                    case 3: return "eSIM-USAT30G-03";
                    case 4: return "eSIM-USAT30G-05";
                    case 5: return "eSIM-USAT30G-05";
                    case 6: return "eSIM-USAT30G-07";
                    case 7: return "eSIM-USAT30G-07";
                    case 8: return "eSIM-USAT30G-10";
                    case 9: return "eSIM-USAT30G-10";
                    case 10: return "eSIM-USAT30G-10";
                    case 15: return "eSIM-USAT30G-15";
                    case 20: return "eSIM-USAT30G-30";
                    case 30: return "eSIM-USAT30G-30";
                }
            }
        }
        
        // 대만 상품들
        if (productName.contains("대만") || productName.contains("TW")) {
            // 대만 5G 매일 1기가 후 무제한(저속) 상품들 (TWR1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWR1G-01";
                    case 2: return "eSIM-TWR1G-03";
                    case 3: return "eSIM-TWR1G-03";
                    case 4: return "eSIM-TWR1G-05";
                    case 5: return "eSIM-TWR1G-05";
                    case 6: return "eSIM-TWR1G-07";
                    case 7: return "eSIM-TWR1G-07";
                    case 8: return "eSIM-TWR1G-10";
                    case 9: return "eSIM-TWR1G-10";
                    case 10: return "eSIM-TWR1G-10";
                    case 15: return "eSIM-TWR1G-15";
                    case 20: return "eSIM-TWR1G-20";
                    case 30: return "eSIM-TWR1G-30";
                }
            }
            
            // 대만 5G 매일 2기가 후 무제한(저속) 상품들 (TWR2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWR2G-01";
                    case 2: return "eSIM-TWR2G-03";
                    case 3: return "eSIM-TWR2G-03";
                    case 4: return "eSIM-TWR2G-05";
                    case 5: return "eSIM-TWR2G-05";
                    case 6: return "eSIM-TWR2G-07";
                    case 7: return "eSIM-TWR2G-07";
                    case 8: return "eSIM-TWR2G-10";
                    case 9: return "eSIM-TWR2G-10";
                    case 10: return "eSIM-TWR2G-10";
                    case 15: return "eSIM-TWR2G-15";
                    case 20: return "eSIM-TWR2G-20";
                    case 30: return "eSIM-TWR2G-30";
                }
            }
            
            // 대만 5G 매일 3기가 후 무제한(저속) 상품들 (TWR3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWR3G-01";
                    case 2: return "eSIM-TWR3G-03";
                    case 3: return "eSIM-TWR3G-03";
                    case 4: return "eSIM-TWR3G-05";
                    case 5: return "eSIM-TWR3G-05";
                    case 6: return "eSIM-TWR3G-07";
                    case 7: return "eSIM-TWR3G-07";
                    case 8: return "eSIM-TWR3G-10";
                    case 9: return "eSIM-TWR3G-10";
                    case 10: return "eSIM-TWR3G-10";
                    case 15: return "eSIM-TWR3G-15";
                    case 20: return "eSIM-TWR3G-20";
                    case 30: return "eSIM-TWR3G-30";
                }
            }
            
            // 대만 5G 속도 무제한 상품들 (TWR10M)
            if (productName.contains("5G 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-TWR10M-01";
                    case 2: return "eSIM-TWR10M-03";
                    case 3: return "eSIM-TWR10M-03";
                    case 4: return "eSIM-TWR10M-05";
                    case 5: return "eSIM-TWR10M-05";
                    case 6: return "eSIM-TWR10M-07";
                    case 7: return "eSIM-TWR10M-07";
                    case 8: return "eSIM-TWR10M-10";
                    case 9: return "eSIM-TWR10M-10";
                    case 10: return "eSIM-TWR10M-10";
                    case 15: return "eSIM-TWR10M-15";
                    case 20: return "eSIM-TWR10M-20";
                    case 30: return "eSIM-TWR10M-30";
                }
            }
            
            // 대만 5G 총 3기가 후 무제한(저속) 상품들 (TWRT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT3G-03";
                    case 2: return "eSIM-TWRT3G-03";
                    case 3: return "eSIM-TWRT3G-03";
                    case 4: return "eSIM-TWRT3G-05";
                    case 5: return "eSIM-TWRT3G-05";
                    case 6: return "eSIM-TWRT3G-07";
                    case 7: return "eSIM-TWRT3G-07";
                    case 8: return "eSIM-TWRT3G-10";
                    case 9: return "eSIM-TWRT3G-10";
                    case 10: return "eSIM-TWRT3G-10";
                    case 15: return "eSIM-TWRT3G-15";
                    case 20: return "eSIM-TWRT3G-30";
                    case 30: return "eSIM-TWRT3G-30";
                }
            }
            
            // 대만 5G 총 5기가 후 무제한(저속) 상품들 (TWRT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT5G-03";
                    case 2: return "eSIM-TWRT5G-03";
                    case 3: return "eSIM-TWRT5G-03";
                    case 4: return "eSIM-TWRT5G-05";
                    case 5: return "eSIM-TWRT5G-05";
                    case 6: return "eSIM-TWRT5G-07";
                    case 7: return "eSIM-TWRT5G-07";
                    case 8: return "eSIM-TWRT5G-10";
                    case 9: return "eSIM-TWRT5G-10";
                    case 10: return "eSIM-TWRT5G-10";
                    case 15: return "eSIM-TWRT5G-15";
                    case 20: return "eSIM-TWRT5G-30";
                    case 30: return "eSIM-TWRT5G-30";
                }
            }
            
            // 대만 5G 총 10기가 후 무제한(저속) 상품들 (TWRT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT10G-03";
                    case 2: return "eSIM-TWRT10G-03";
                    case 3: return "eSIM-TWRT10G-03";
                    case 4: return "eSIM-TWRT10G-05";
                    case 5: return "eSIM-TWRT10G-05";
                    case 6: return "eSIM-TWRT10G-07";
                    case 7: return "eSIM-TWRT10G-07";
                    case 8: return "eSIM-TWRT10G-10";
                    case 9: return "eSIM-TWRT10G-10";
                    case 10: return "eSIM-TWRT10G-10";
                    case 15: return "eSIM-TWRT10G-15";
                    case 20: return "eSIM-TWRT10G-30";
                    case 30: return "eSIM-TWRT10G-30";
                }
            }
            
            // 대만 5G 총 20기가 후 무제한(저속) 상품들 (TWRT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT20G-03";
                    case 2: return "eSIM-TWRT20G-03";
                    case 3: return "eSIM-TWRT20G-03";
                    case 4: return "eSIM-TWRT20G-05";
                    case 5: return "eSIM-TWRT20G-05";
                    case 6: return "eSIM-TWRT20G-07";
                    case 7: return "eSIM-TWRT20G-07";
                    case 8: return "eSIM-TWRT20G-10";
                    case 9: return "eSIM-TWRT20G-10";
                    case 10: return "eSIM-TWRT20G-10";
                    case 15: return "eSIM-TWRT20G-15";
                    case 20: return "eSIM-TWRT20G-30";
                    case 30: return "eSIM-TWRT20G-30";
                }
            }
            
            // 대만 5G 총 30기가 후 무제한(저속) 상품들 (TWRT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT30G-03";
                    case 2: return "eSIM-TWRT30G-03";
                    case 3: return "eSIM-TWRT30G-03";
                    case 4: return "eSIM-TWRT30G-05";
                    case 5: return "eSIM-TWRT30G-05";
                    case 6: return "eSIM-TWRT30G-07";
                    case 7: return "eSIM-TWRT30G-07";
                    case 8: return "eSIM-TWRT30G-10";
                    case 9: return "eSIM-TWRT30G-10";
                    case 10: return "eSIM-TWRT30G-10";
                    case 15: return "eSIM-TWRT30G-15";
                    case 20: return "eSIM-TWRT30G-30";
                    case 30: return "eSIM-TWRT30G-30";
                }
            }
            
            // 대만 5G 총 50기가 후 무제한(저속) 상품들 (TWRT50G)
            if (productName.contains("총 50기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TWRT50G-03";
                    case 2: return "eSIM-TWRT50G-03";
                    case 3: return "eSIM-TWRT50G-03";
                    case 4: return "eSIM-TWRT50G-05";
                    case 5: return "eSIM-TWRT50G-05";
                    case 6: return "eSIM-TWRT50G-07";
                    case 7: return "eSIM-TWRT50G-07";
                    case 8: return "eSIM-TWRT50G-10";
                    case 9: return "eSIM-TWRT50G-10";
                    case 10: return "eSIM-TWRT50G-10";
                    case 15: return "eSIM-TWRT50G-15";
                    case 20: return "eSIM-TWRT50G-30";
                    case 30: return "eSIM-TWRT50G-30";
                }
            }
        }
        
        // 터키 상품들
        if (productName.contains("터키") || productName.contains("TR")) {
            // 터키 5G 매일 1기가 후 무제한(저속) 상품들 (TUR1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TUR1G-01";
                    case 2: return "eSIM-TUR1G-03";
                    case 3: return "eSIM-TUR1G-03";
                    case 4: return "eSIM-TUR1G-05";
                    case 5: return "eSIM-TUR1G-05";
                    case 6: return "eSIM-TUR1G-07";
                    case 7: return "eSIM-TUR1G-07";
                    case 8: return "eSIM-TUR1G-10";
                    case 9: return "eSIM-TUR1G-10";
                    case 10: return "eSIM-TUR1G-10";
                    case 15: return "eSIM-TUR1G-15";
                    case 20: return "eSIM-TUR1G-20";
                    case 30: return "eSIM-TUR1G-30";
                }
            }
            
            // 터키 5G 매일 2기가 후 무제한(저속) 상품들 (TUR2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TUR2G-01";
                    case 2: return "eSIM-TUR2G-03";
                    case 3: return "eSIM-TUR2G-03";
                    case 4: return "eSIM-TUR2G-05";
                    case 5: return "eSIM-TUR2G-05";
                    case 6: return "eSIM-TUR2G-07";
                    case 7: return "eSIM-TUR2G-07";
                    case 8: return "eSIM-TUR2G-10";
                    case 9: return "eSIM-TUR2G-10";
                    case 10: return "eSIM-TUR2G-10";
                    case 15: return "eSIM-TUR2G-15";
                    case 20: return "eSIM-TUR2G-20";
                    case 30: return "eSIM-TUR2G-30";
                }
            }
            
            // 터키 5G 매일 3기가 후 무제한(저속) 상품들 (TUR3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TUR3G-01";
                    case 2: return "eSIM-TUR3G-03";
                    case 3: return "eSIM-TUR3G-03";
                    case 4: return "eSIM-TUR3G-05";
                    case 5: return "eSIM-TUR3G-05";
                    case 6: return "eSIM-TUR3G-07";
                    case 7: return "eSIM-TUR3G-07";
                    case 8: return "eSIM-TUR3G-10";
                    case 9: return "eSIM-TUR3G-10";
                    case 10: return "eSIM-TUR3G-10";
                    case 15: return "eSIM-TUR3G-15";
                    case 20: return "eSIM-TUR3G-20";
                    case 30: return "eSIM-TUR3G-30";
                }
            }
            
            // 터키 LTE 무제한 상품들 (TURMAX)
            if (productName.contains("LTE 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-TURMAX-01";
                    case 2: return "eSIM-TURMAX-03";
                    case 3: return "eSIM-TURMAX-03";
                    case 4: return "eSIM-TURMAX-05";
                    case 5: return "eSIM-TURMAX-05";
                    case 6: return "eSIM-TURMAX-07";
                    case 7: return "eSIM-TURMAX-07";
                    case 8: return "eSIM-TURMAX-10";
                    case 9: return "eSIM-TURMAX-10";
                    case 10: return "eSIM-TURMAX-10";
                    case 15: return "eSIM-TURMAX-15";
                    case 20: return "eSIM-TURMAX-20";
                    case 30: return "eSIM-TURMAX-30";
                }
            }
            
            // 터키 5G 총 3기가 후 무제한(저속) 상품들 (TURT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT3G-03";
                    case 2: return "eSIM-TURT3G-03";
                    case 3: return "eSIM-TURT3G-03";
                    case 4: return "eSIM-TURT3G-05";
                    case 5: return "eSIM-TURT3G-05";
                    case 6: return "eSIM-TURT3G-07";
                    case 7: return "eSIM-TURT3G-07";
                    case 8: return "eSIM-TURT3G-10";
                    case 9: return "eSIM-TURT3G-10";
                    case 10: return "eSIM-TURT3G-10";
                    case 15: return "eSIM-TURT3G-15";
                    case 20: return "eSIM-TURT3G-30";
                    case 30: return "eSIM-TURT3G-30";
                }
            }
            
            // 터키 5G 총 5기가 후 무제한(저속) 상품들 (TURT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT5G-03";
                    case 2: return "eSIM-TURT5G-03";
                    case 3: return "eSIM-TURT5G-03";
                    case 4: return "eSIM-TURT5G-05";
                    case 5: return "eSIM-TURT5G-05";
                    case 6: return "eSIM-TURT5G-07";
                    case 7: return "eSIM-TURT5G-07";
                    case 8: return "eSIM-TURT5G-10";
                    case 9: return "eSIM-TURT5G-10";
                    case 10: return "eSIM-TURT5G-10";
                    case 15: return "eSIM-TURT5G-15";
                    case 20: return "eSIM-TURT5G-30";
                    case 30: return "eSIM-TURT5G-30";
                }
            }
            
            // 터키 5G 총 10기가 후 무제한(저속) 상품들 (TURT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT10G-03";
                    case 2: return "eSIM-TURT10G-03";
                    case 3: return "eSIM-TURT10G-03";
                    case 4: return "eSIM-TURT10G-05";
                    case 5: return "eSIM-TURT10G-05";
                    case 6: return "eSIM-TURT10G-07";
                    case 7: return "eSIM-TURT10G-07";
                    case 8: return "eSIM-TURT10G-10";
                    case 9: return "eSIM-TURT10G-10";
                    case 10: return "eSIM-TURT10G-10";
                    case 15: return "eSIM-TURT10G-15";
                    case 20: return "eSIM-TURT10G-30";
                    case 30: return "eSIM-TURT10G-30";
                }
            }
            
            // 터키 5G 총 20기가 후 무제한(저속) 상품들 (TURT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT20G-03";
                    case 2: return "eSIM-TURT20G-03";
                    case 3: return "eSIM-TURT20G-03";
                    case 4: return "eSIM-TURT20G-05";
                    case 5: return "eSIM-TURT20G-05";
                    case 6: return "eSIM-TURT20G-07";
                    case 7: return "eSIM-TURT20G-07";
                    case 8: return "eSIM-TURT20G-10";
                    case 9: return "eSIM-TURT20G-10";
                    case 10: return "eSIM-TURT20G-10";
                    case 15: return "eSIM-TURT20G-15";
                    case 20: return "eSIM-TURT20G-30";
                    case 30: return "eSIM-TURT20G-30";
                }
            }
            
            // 터키 5G 총 30기가 후 무제한(저속) 상품들 (TURT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT30G-03";
                    case 2: return "eSIM-TURT30G-03";
                    case 3: return "eSIM-TURT30G-03";
                    case 4: return "eSIM-TURT30G-05";
                    case 5: return "eSIM-TURT30G-05";
                    case 6: return "eSIM-TURT30G-07";
                    case 7: return "eSIM-TURT30G-07";
                    case 8: return "eSIM-TURT30G-10";
                    case 9: return "eSIM-TURT30G-10";
                    case 10: return "eSIM-TURT30G-10";
                    case 15: return "eSIM-TURT30G-15";
                    case 20: return "eSIM-TURT30G-30";
                    case 30: return "eSIM-TURT30G-30";
                }
            }
            
            // 터키 5G 총 50기가 후 무제한(저속) 상품들 (TURT50G)
            if (productName.contains("총 50기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-TURT50G-03";
                    case 2: return "eSIM-TURT50G-03";
                    case 3: return "eSIM-TURT50G-03";
                    case 4: return "eSIM-TURT50G-05";
                    case 5: return "eSIM-TURT50G-05";
                    case 6: return "eSIM-TURT50G-07";
                    case 7: return "eSIM-TURT50G-07";
                    case 8: return "eSIM-TURT50G-10";
                    case 9: return "eSIM-TURT50G-10";
                    case 10: return "eSIM-TURT50G-10";
                    case 15: return "eSIM-TURT50G-15";
                    case 20: return "eSIM-TURT50G-30";
                    case 30: return "eSIM-TURT50G-30";
                }
            }
        }
        
        // 호뉴 상품들
        if (productName.contains("호뉴") || productName.contains("AN")) {
            // 호뉴 5G 매일 1기가 후 무제한(저속) 상품들 (AN1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AN1G-01";
                    case 2: return "eSIM-AN1G-03";
                    case 3: return "eSIM-AN1G-03";
                    case 4: return "eSIM-AN1G-05";
                    case 5: return "eSIM-AN1G-05";
                    case 6: return "eSIM-AN1G-07";
                    case 7: return "eSIM-AN1G-07";
                    case 8: return "eSIM-AN1G-10";
                    case 9: return "eSIM-AN1G-10";
                    case 10: return "eSIM-AN1G-10";
                    case 15: return "eSIM-AN1G-15";
                    case 20: return "eSIM-AN1G-20";
                    case 30: return "eSIM-AN1G-30";
                }
            }
            
            // 호뉴 5G 매일 2기가 후 무제한(저속) 상품들 (AN2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AN2G-01";
                    case 2: return "eSIM-AN2G-03";
                    case 3: return "eSIM-AN2G-03";
                    case 4: return "eSIM-AN2G-05";
                    case 5: return "eSIM-AN2G-05";
                    case 6: return "eSIM-AN2G-07";
                    case 7: return "eSIM-AN2G-07";
                    case 8: return "eSIM-AN2G-10";
                    case 9: return "eSIM-AN2G-10";
                    case 10: return "eSIM-AN2G-10";
                    case 15: return "eSIM-AN2G-15";
                    case 20: return "eSIM-AN2G-20";
                    case 30: return "eSIM-AN2G-30";
                }
            }
            
            // 호뉴 5G 매일 3기가 후 무제한(저속) 상품들 (AN3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AN3G-01";
                    case 2: return "eSIM-AN3G-03";
                    case 3: return "eSIM-AN3G-03";
                    case 4: return "eSIM-AN3G-05";
                    case 5: return "eSIM-AN3G-05";
                    case 6: return "eSIM-AN3G-07";
                    case 7: return "eSIM-AN3G-07";
                    case 8: return "eSIM-AN3G-10";
                    case 9: return "eSIM-AN3G-10";
                    case 10: return "eSIM-AN3G-10";
                    case 15: return "eSIM-AN3G-15";
                    case 20: return "eSIM-AN3G-20";
                    case 30: return "eSIM-AN3G-30";
                }
            }
            
            // 호뉴 LTE 무제한 상품들 (ANMAX)
            if (productName.contains("LTE 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-ANMAX-03";
                    case 2: return "eSIM-ANMAX-03";
                    case 3: return "eSIM-ANMAX-03";
                    case 4: return "eSIM-ANMAX-05";
                    case 5: return "eSIM-ANMAX-05";
                    case 6: return "eSIM-ANMAX-07";
                    case 7: return "eSIM-ANMAX-07";
                    case 8: return "eSIM-ANMAX-10";
                    case 9: return "eSIM-ANMAX-10";
                    case 10: return "eSIM-ANMAX-10";
                    case 15: return "eSIM-ANMAX-15";
                    case 20: return "eSIM-ANMAX-20";
                    case 30: return "eSIM-ANMAX-30";
                }
            }
            
            // 호뉴 5G 총 5기가 후 무제한(저속) 상품들 (ANT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-ANT5G-03";
                    case 2: return "eSIM-ANT5G-03";
                    case 3: return "eSIM-ANT5G-03";
                    case 4: return "eSIM-ANT5G-05";
                    case 5: return "eSIM-ANT5G-05";
                    case 6: return "eSIM-ANT5G-07";
                    case 7: return "eSIM-ANT5G-07";
                    case 8: return "eSIM-ANT5G-10";
                    case 9: return "eSIM-ANT5G-10";
                    case 10: return "eSIM-ANT5G-10";
                    case 15: return "eSIM-ANT5G-15";
                    case 20: return "eSIM-ANT5G-30";
                    case 30: return "eSIM-ANT5G-30";
                }
            }
            
            // 호뉴 5G 총 10기가 후 무제한(저속) 상품들 (ANT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-ANT10G-03";
                    case 2: return "eSIM-ANT10G-03";
                    case 3: return "eSIM-ANT10G-03";
                    case 4: return "eSIM-ANT10G-05";
                    case 5: return "eSIM-ANT10G-05";
                    case 6: return "eSIM-ANT10G-07";
                    case 7: return "eSIM-ANT10G-07";
                    case 8: return "eSIM-ANT10G-10";
                    case 9: return "eSIM-ANT10G-10";
                    case 10: return "eSIM-ANT10G-10";
                    case 15: return "eSIM-ANT10G-15";
                    case 20: return "eSIM-ANT10G-30";
                    case 30: return "eSIM-ANT10G-30";
                }
            }
            
            // 호뉴 5G 총 20기가 후 무제한(저속) 상품들 (ANT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-ANT20G-03";
                    case 2: return "eSIM-ANT20G-03";
                    case 3: return "eSIM-ANT20G-03";
                    case 4: return "eSIM-ANT20G-05";
                    case 5: return "eSIM-ANT20G-05";
                    case 6: return "eSIM-ANT20G-07";
                    case 7: return "eSIM-ANT20G-07";
                    case 8: return "eSIM-ANT20G-10";
                    case 9: return "eSIM-ANT20G-10";
                    case 10: return "eSIM-ANT20G-10";
                    case 15: return "eSIM-ANT20G-15";
                    case 20: return "eSIM-ANT20G-30";
                    case 30: return "eSIM-ANT20G-30";
                }
            }
            
            // 호뉴 5G 총 30기가 후 무제한(저속) 상품들 (ANT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-ANT30G-03";
                    case 2: return "eSIM-ANT30G-03";
                    case 3: return "eSIM-ANT30G-03";
                    case 4: return "eSIM-ANT30G-05";
                    case 5: return "eSIM-ANT30G-05";
                    case 6: return "eSIM-ANT30G-07";
                    case 7: return "eSIM-ANT30G-07";
                    case 8: return "eSIM-ANT30G-10";
                    case 9: return "eSIM-ANT30G-10";
                    case 10: return "eSIM-ANT30G-10";
                    case 15: return "eSIM-ANT30G-15";
                    case 20: return "eSIM-ANT30G-30";
                    case 30: return "eSIM-ANT30G-30";
                }
            }
            
            // 호뉴 5G 총 50기가 후 무제한(저속) 상품들 (ANT50G)
            if (productName.contains("총 50기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-ANT50G-03";
                    case 2: return "eSIM-ANT50G-03";
                    case 3: return "eSIM-ANT50G-03";
                    case 4: return "eSIM-ANT50G-05";
                    case 5: return "eSIM-ANT50G-05";
                    case 6: return "eSIM-ANT50G-07";
                    case 7: return "eSIM-ANT50G-07";
                    case 8: return "eSIM-ANT50G-10";
                    case 9: return "eSIM-ANT50G-10";
                    case 10: return "eSIM-ANT50G-10";
                    case 15: return "eSIM-ANT50G-15";
                    case 20: return "eSIM-ANT50G-30";
                    case 30: return "eSIM-ANT50G-30";
                }
            }
        }
        
        // 유럽 상품들
        if (productName.contains("유럽") || productName.contains("EU")) {
            // 유럽 5G 매일 1기가 후 무제한(저속) 상품들 (EUB1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUB1G-01";
                    case 2: return "eSIM-EUB1G-03";
                    case 3: return "eSIM-EUB1G-03";
                    case 4: return "eSIM-EUB1G-05";
                    case 5: return "eSIM-EUB1G-05";
                    case 6: return "eSIM-EUB1G-07";
                    case 7: return "eSIM-EUB1G-07";
                    case 10: return "eSIM-EUB1G-10";
                    case 15: return "eSIM-EUB1G-15";
                    case 20: return "eSIM-EUB1G-20";
                    case 30: return "eSIM-EUB1G-30";
                }
            }
            
            // 유럽 5G 매일 2기가 후 무제한(저속) 상품들 (EUB2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUB2G-01";
                    case 2: return "eSIM-EUB2G-03";
                    case 3: return "eSIM-EUB2G-03";
                    case 4: return "eSIM-EUB2G-05";
                    case 5: return "eSIM-EUB2G-05";
                    case 6: return "eSIM-EUB2G-07";
                    case 7: return "eSIM-EUB2G-07";
                    case 10: return "eSIM-EUB2G-10";
                    case 15: return "eSIM-EUB2G-15";
                    case 20: return "eSIM-EUB2G-20";
                    case 30: return "eSIM-EUB2G-30";
                }
            }
            
            // 유럽 5G 매일 3기가 후 무제한(저속) 상품들 (EUB3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUB3G-01";
                    case 2: return "eSIM-EUB3G-03";
                    case 3: return "eSIM-EUB3G-03";
                    case 4: return "eSIM-EUB3G-05";
                    case 5: return "eSIM-EUB3G-05";
                    case 6: return "eSIM-EUB3G-07";
                    case 7: return "eSIM-EUB3G-07";
                    case 10: return "eSIM-EUB3G-10";
                    case 15: return "eSIM-EUB3G-15";
                    case 20: return "eSIM-EUB3G-20";
                    case 30: return "eSIM-EUB3G-30";
                }
            }
            
            // 유럽 5G 매일 10기가 후 무제한(저속) 상품들 (EUBMAX)
            if (productName.contains("매일 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBMAX-01";
                    case 2: return "eSIM-EUBMAX-03";
                    case 3: return "eSIM-EUBMAX-03";
                    case 4: return "eSIM-EUBMAX-05";
                    case 5: return "eSIM-EUBMAX-05";
                    case 6: return "eSIM-EUBMAX-07";
                    case 7: return "eSIM-EUBMAX-07";
                    case 10: return "eSIM-EUBMAX-10";
                    case 15: return "eSIM-EUBMAX-15";
                    case 20: return "eSIM-EUBMAX-20";
                    case 30: return "eSIM-EUBMAX-30";
                }
            }
            
            // 유럽 5G 속도 무제한 상품들 (EUBM10)
            if (productName.contains("5G 속도 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-EUBM10-01";
                    case 2: return "eSIM-EUBM10-03";
                    case 3: return "eSIM-EUBM10-03";
                    case 4: return "eSIM-EUBM10-05";
                    case 5: return "eSIM-EUBM10-05";
                    case 6: return "eSIM-EUBM10-07";
                    case 7: return "eSIM-EUBM10-07";
                    case 10: return "eSIM-EUBM10-10";
                    case 15: return "eSIM-EUBM10-15";
                    case 20: return "eSIM-EUBM10-20";
                    case 30: return "eSIM-EUBM10-30";
                }
            }
            
            // 유럽 5G 총 3기가 후 저속 무제한 상품들 (EUBT3G)
            if (productName.contains("총 3기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT3G-03";
                    case 2: return "eSIM-EUBT3G-03";
                    case 3: return "eSIM-EUBT3G-03";
                    case 4: return "eSIM-EUBT3G-05";
                    case 5: return "eSIM-EUBT3G-05";
                    case 6: return "eSIM-EUBT3G-07";
                    case 7: return "eSIM-EUBT3G-07";
                    case 10: return "eSIM-EUBT3G-10";
                    case 15: return "eSIM-EUBT3G-15";
                    case 30: return "eSIM-EUBT3G-30";
                }
            }
            
            // 유럽 5G 총 5기가 후 저속 무제한 상품들 (EUBT5G)
            if (productName.contains("총 5기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT5G-03";
                    case 2: return "eSIM-EUBT5G-03";
                    case 3: return "eSIM-EUBT5G-03";
                    case 4: return "eSIM-EUBT5G-05";
                    case 5: return "eSIM-EUBT5G-05";
                    case 6: return "eSIM-EUBT5G-07";
                    case 7: return "eSIM-EUBT5G-07";
                    case 10: return "eSIM-EUBT5G-10";
                    case 15: return "eSIM-EUBT5G-15";
                    case 30: return "eSIM-EUBT5G-30";
                }
            }
            
            // 유럽 5G 총 10기가 후 저속 무제한 상품들 (EUBT10G)
            if (productName.contains("총 10기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT10G-03";
                    case 2: return "eSIM-EUBT10G-03";
                    case 3: return "eSIM-EUBT10G-03";
                    case 4: return "eSIM-EUBT10G-05";
                    case 5: return "eSIM-EUBT10G-05";
                    case 6: return "eSIM-EUBT10G-07";
                    case 7: return "eSIM-EUBT10G-07";
                    case 10: return "eSIM-EUBT10G-10";
                    case 15: return "eSIM-EUBT10G-15";
                    case 30: return "eSIM-EUBT10G-30";
                }
            }
            
            // 유럽 5G 총 20기가 후 저속 무제한 상품들 (EUBT20G)
            if (productName.contains("총 20기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT20G-03";
                    case 2: return "eSIM-EUBT20G-03";
                    case 3: return "eSIM-EUBT20G-03";
                    case 4: return "eSIM-EUBT20G-05";
                    case 5: return "eSIM-EUBT20G-05";
                    case 6: return "eSIM-EUBT20G-07";
                    case 7: return "eSIM-EUBT20G-07";
                    case 10: return "eSIM-EUBT20G-10";
                    case 15: return "eSIM-EUBT20G-15";
                    case 30: return "eSIM-EUBT20G-30";
                }
            }
            
            // 유럽 5G 총 30기가 후 저속 무제한 상품들 (EUBT30G)
            if (productName.contains("총 30기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT30G-03";
                    case 2: return "eSIM-EUBT30G-03";
                    case 3: return "eSIM-EUBT30G-03";
                    case 4: return "eSIM-EUBT30G-05";
                    case 5: return "eSIM-EUBT30G-05";
                    case 6: return "eSIM-EUBT30G-07";
                    case 7: return "eSIM-EUBT30G-07";
                    case 10: return "eSIM-EUBT30G-10";
                    case 15: return "eSIM-EUBT30G-15";
                    case 30: return "eSIM-EUBT30G-30";
                }
            }
            
            // 유럽 5G 총 50기가 후 저속 무제한 상품들 (EUBT50G)
            if (productName.contains("총 50기가") && productName.contains("저속 무제한")) {
                switch (day) {
                    case 1: return "eSIM-EUBT50G-03";
                    case 2: return "eSIM-EUBT50G-03";
                    case 3: return "eSIM-EUBT50G-03";
                    case 4: return "eSIM-EUBT50G-05";
                    case 5: return "eSIM-EUBT50G-05";
                    case 6: return "eSIM-EUBT50G-07";
                    case 7: return "eSIM-EUBT50G-07";
                    case 10: return "eSIM-EUBT50G-10";
                    case 15: return "eSIM-EUBT50G-15";
                    case 30: return "eSIM-EUBT50G-30";
                }
            }
        }
        
        // 아프리카 상품들
        if (productName.contains("아프리카") || productName.contains("AF")) {
            // 아프리카 5G 매일 1기가 후 무제한(저속) 상품들 (AFA1G)
            if (productName.contains("매일 1기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFA1G-01";
                    case 2: return "eSIM-AFA1G-03";
                    case 3: return "eSIM-AFA1G-03";
                    case 4: return "eSIM-AFA1G-05";
                    case 5: return "eSIM-AFA1G-05";
                    case 6: return "eSIM-AFA1G-07";
                    case 7: return "eSIM-AFA1G-07";
                    case 10: return "eSIM-AFA1G-10";
                    case 15: return "eSIM-AFA1G-15";
                    case 20: return "eSIM-AFA1G-20";
                    case 30: return "eSIM-AFA1G-30";
                }
            }
            
            // 아프리카 5G 매일 2기가 후 무제한(저속) 상품들 (AFA2G)
            if (productName.contains("매일 2기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFA2G-01";
                    case 2: return "eSIM-AFA2G-03";
                    case 3: return "eSIM-AFA2G-03";
                    case 4: return "eSIM-AFA2G-05";
                    case 5: return "eSIM-AFA2G-05";
                    case 6: return "eSIM-AFA2G-07";
                    case 7: return "eSIM-AFA2G-07";
                    case 10: return "eSIM-AFA2G-10";
                    case 15: return "eSIM-AFA2G-15";
                    case 20: return "eSIM-AFA2G-20";
                    case 30: return "eSIM-AFA2G-30";
                }
            }
            
            // 아프리카 5G 매일 3기가 후 무제한(저속) 상품들 (AFA3G)
            if (productName.contains("매일 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFA3G-01";
                    case 2: return "eSIM-AFA3G-03";
                    case 3: return "eSIM-AFA3G-03";
                    case 4: return "eSIM-AFA3G-05";
                    case 5: return "eSIM-AFA3G-05";
                    case 6: return "eSIM-AFA3G-07";
                    case 7: return "eSIM-AFA3G-07";
                    case 10: return "eSIM-AFA3G-10";
                    case 15: return "eSIM-AFA3G-15";
                    case 20: return "eSIM-AFA3G-20";
                    case 30: return "eSIM-AFA3G-30";
                }
            }
            
            // 아프리카 LTE 무제한 상품들 (AFAMAX)
            if (productName.contains("LTE 무제한") || productName.contains("BEST")) {
                switch (day) {
                    case 1: return "eSIM-AFAMAX-01";
                    case 2: return "eSIM-AFAMAX-03";
                    case 3: return "eSIM-AFAMAX-03";
                    case 4: return "eSIM-AFAMAX-05";
                    case 5: return "eSIM-AFAMAX-05";
                    case 6: return "eSIM-AFAMAX-07";
                    case 7: return "eSIM-AFAMAX-07";
                    case 10: return "eSIM-AFAMAX-10";
                    case 15: return "eSIM-AFAMAX-15";
                    case 20: return "eSIM-AFAMAX-20";
                    case 30: return "eSIM-AFAMAX-30";
                }
            }
            
            // 아프리카 5G 총 3기가 후 무제한(저속) 상품들 (AFAT3G)
            if (productName.contains("총 3기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT3G-03";
                    case 2: return "eSIM-AFAT3G-03";
                    case 3: return "eSIM-AFAT3G-03";
                    case 4: return "eSIM-AFAT3G-05";
                    case 5: return "eSIM-AFAT3G-05";
                    case 6: return "eSIM-AFAT3G-07";
                    case 7: return "eSIM-AFAT3G-07";
                    case 10: return "eSIM-AFAT3G-10";
                    case 15: return "eSIM-AFAT3G-15";
                    case 30: return "eSIM-AFAT3G-30";
                }
            }
            
            // 아프리카 5G 총 5기가 후 무제한(저속) 상품들 (AFAT5G)
            if (productName.contains("총 5기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT5G-03";
                    case 2: return "eSIM-AFAT5G-03";
                    case 3: return "eSIM-AFAT5G-03";
                    case 4: return "eSIM-AFAT5G-05";
                    case 5: return "eSIM-AFAT5G-05";
                    case 6: return "eSIM-AFAT5G-07";
                    case 7: return "eSIM-AFAT5G-07";
                    case 10: return "eSIM-AFAT5G-10";
                    case 15: return "eSIM-AFAT5G-15";
                    case 30: return "eSIM-AFAT5G-30";
                }
            }
            
            // 아프리카 5G 총 10기가 후 무제한(저속) 상품들 (AFAT10G)
            if (productName.contains("총 10기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT10G-03";
                    case 2: return "eSIM-AFAT10G-03";
                    case 3: return "eSIM-AFAT10G-03";
                    case 4: return "eSIM-AFAT10G-05";
                    case 5: return "eSIM-AFAT10G-05";
                    case 6: return "eSIM-AFAT10G-07";
                    case 7: return "eSIM-AFAT10G-07";
                    case 10: return "eSIM-AFAT10G-10";
                    case 15: return "eSIM-AFAT10G-15";
                    case 30: return "eSIM-AFAT10G-30";
                }
            }
            
            // 아프리카 5G 총 20기가 후 무제한(저속) 상품들 (AFAT20G)
            if (productName.contains("총 20기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT20G-03";
                    case 2: return "eSIM-AFAT20G-03";
                    case 3: return "eSIM-AFAT20G-03";
                    case 4: return "eSIM-AFAT20G-05";
                    case 5: return "eSIM-AFAT20G-05";
                    case 6: return "eSIM-AFAT20G-07";
                    case 7: return "eSIM-AFAT20G-07";
                    case 10: return "eSIM-AFAT20G-10";
                    case 15: return "eSIM-AFAT20G-15";
                    case 30: return "eSIM-AFAT20G-30";
                }
            }
            
            // 아프리카 5G 총 30기가 후 무제한(저속) 상품들 (AFAT30G)
            if (productName.contains("총 30기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT30G-03";
                    case 2: return "eSIM-AFAT30G-03";
                    case 3: return "eSIM-AFAT30G-03";
                    case 4: return "eSIM-AFAT30G-05";
                    case 5: return "eSIM-AFAT30G-05";
                    case 6: return "eSIM-AFAT30G-07";
                    case 7: return "eSIM-AFAT30G-07";
                    case 10: return "eSIM-AFAT30G-10";
                    case 15: return "eSIM-AFAT30G-15";
                    case 30: return "eSIM-AFAT30G-30";
                }
            }
            
            // 아프리카 5G 총 50기가 후 무제한(저속) 상품들 (AFAT50G)
            if (productName.contains("총 50기가") && productName.contains("무제한")) {
                switch (day) {
                    case 1: return "eSIM-AFAT50G-03";
                    case 2: return "eSIM-AFAT50G-03";
                    case 3: return "eSIM-AFAT50G-03";
                    case 4: return "eSIM-AFAT50G-05";
                    case 5: return "eSIM-AFAT50G-05";
                    case 6: return "eSIM-AFAT50G-07";
                    case 7: return "eSIM-AFAT50G-07";
                    case 10: return "eSIM-AFAT50G-10";
                    case 15: return "eSIM-AFAT50G-15";
                    case 30: return "eSIM-AFAT50G-30";
                }
            }
        }
        
        
        // 매칭되지 않는 경우 기본값
        System.out.println("매칭되지 않는 상품: " + productName + " (일수: " + day + ")");
        return "eSIM-test";
    }
    
    /**
     * 주문 요청 정보를 담는 클래스
     */
    static class OrderRequest {
        String customerCode;
        String orderTid;
        String receiveName;
        String phone;
        Long timestamp;
        String email;
        int type;
        int replyType;
        String productCode;
        int quantity;
        String autoGraph;
        String warehouse;
    }
}
