package org.mindrot;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.*;

public class NaverCommerceApiClient {

    private static String DISPATCH_API_URL = "https://api.commerce.naver.com/external/v1/pay-order/seller/product-orders/dispatch";
    
    private static final String OAUTH_URL = "https://api.commerce.naver.com/external/v1/oauth2/token";
    private static final String ORDER_API_URL = "https://api.commerce.naver.com/external/v1/pay-order/seller/product-orders";
    
    public static void main(String[] args) {
        try {
            // 명령행 인수 확인
            if (args.length > 0 && "processDispatchOrders".equals(args[0])) {
                // 발송 처리 모드
                processDispatchOrders();
            } else {
                // 기존 주문 조회 모드
                processOrderInquiry();
            }
            
        } catch (Exception e) {
            log("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 기존 주문 조회 처리 로직
     */
    private static void processOrderInquiry() {
        try {
            // 로그 파일 초기화
            initializeLogFile();
            
            log("=== Naver Commerce API 클라이언트 시작 ===");
            
            // 1. SignatureGenerator로 필요한 값들 생성
            String clientId = System.getenv().getOrDefault("NAVER_CLIENT_ID", "");
            String clientSecret = System.getenv().getOrDefault("NAVER_CLIENT_SECRET", "");
            Long timestamp = System.currentTimeMillis();
            
            String signature = SignatureGenerator.generateSignature(clientId, clientSecret, timestamp);
            
            log("=== OAuth Token 발급 요청 ===");
            log("ClientId: " + clientId);
            log("Timestamp: " + timestamp);
            log("Signature: " + signature);
            
            // 2. OAuth 토큰 발급
            String accessToken = getAccessToken(clientId, timestamp, signature);
            
            if (accessToken != null) {
                log("Access Token 발급 성공: " + accessToken);
                
                // 3. 상품 주문 정보 조회 (3분 전부터 현재까지)
                String fromDate = getThreeMinutesAgoDateString();
                log("조회 시작 날짜 (3분 전): " + fromDate);
                
                List<OrderInfo> orderInfos = getProductOrderIds(accessToken, fromDate);
                
                // 4. 주문 정보를 JSON 형태로 출력 (Node.js에서 파싱용)
                log("=== Order Information JSON ===");
                if (!orderInfos.isEmpty()) {
                    StringBuilder jsonBuilder = new StringBuilder();
                    jsonBuilder.append("[");
                    
                    for (int i = 0; i < orderInfos.size(); i++) {
                        OrderInfo orderInfo = orderInfos.get(i);
                        jsonBuilder.append("{");
                        jsonBuilder.append("\"productOrderId\":\"").append(escapeJson(orderInfo.getProductOrderId())).append("\",");
                        jsonBuilder.append("\"orderId\":\"").append(escapeJson(orderInfo.getOrderId())).append("\",");
                        jsonBuilder.append("\"ordererName\":\"").append(escapeJson(orderInfo.getOrdererName())).append("\",");
                        jsonBuilder.append("\"ordererTel\":\"").append(escapeJson(orderInfo.getOrdererTel())).append("\",");
                        jsonBuilder.append("\"email\":\"").append(escapeJson(orderInfo.getEmail() != null ? orderInfo.getEmail() : "")).append("\",");
                        jsonBuilder.append("\"productName\":\"").append(escapeJson(orderInfo.getProductName() != null ? orderInfo.getProductName() : "")).append("\",");
                        jsonBuilder.append("\"day\":").append(orderInfo.getDay() != null ? orderInfo.getDay() : 1).append(",");
                        jsonBuilder.append("\"quantity\":").append(orderInfo.getQuantity() != null ? orderInfo.getQuantity() : 1);
                        jsonBuilder.append("}");
                        
                        if (i < orderInfos.size() - 1) {
                            jsonBuilder.append(",");
                        }
                    }
                    
                    jsonBuilder.append("]");
                    
                    // JSON 데이터를 표준 출력으로 출력 (Node.js에서 파싱)
                    System.out.println("NAVER_ORDER_DATA_START");
                    System.out.println(jsonBuilder.toString());
                    System.out.println("NAVER_ORDER_DATA_END");
                    
                    log("총 " + orderInfos.size() + "개의 주문 정보를 JSON으로 출력했습니다.");
                } else {
                    System.out.println("NAVER_ORDER_DATA_START");
                    System.out.println("[]");
                    System.out.println("NAVER_ORDER_DATA_END");
                    log("주문 데이터가 없습니다.");
                }
                
            } else {
                log("Access Token 발급 실패");
            }
            
            log("=== 프로그램 실행 완료 ===");
            
        } catch (Exception e) {
            log("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * OAuth 액세스 토큰을 발급받습니다.
     */
    private static String getAccessToken(String clientId, Long timestamp, String signature) throws IOException {
        URL url = new URL(OAUTH_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            // 요청 설정
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            
            // 요청 바디 구성
            String requestBody = String.format(
                "client_id=%s&timestamp=%d&grant_type=client_credentials&client_secret_sign=%s&type=SELF",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8.name()),
                timestamp,
                URLEncoder.encode(signature, StandardCharsets.UTF_8.name())
            );
            
            log("OAuth 요청 바디: " + requestBody);
            
            // 요청 전송
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8.name());
                os.write(input, 0, input.length);
            }
            
            // 응답 처리
            int responseCode = conn.getResponseCode();
            log("OAuth 응답 코드: " + responseCode);
            
            String response;
            if (responseCode == 200) {
                response = readResponse(conn.getInputStream());
            } else {
                response = readResponse(conn.getErrorStream());
                log("OAuth 오류 응답: " + response);
                return null;
            }
            
            log("OAuth 응답: " + response);
            
            // access_token 추출
            return extractAccessToken(response);
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * 상품 주문 정보를 조회하고 productOrderId 목록을 반환합니다.
     */
    private static List<OrderInfo> getProductOrderIds(String accessToken, String fromDate) throws IOException {
        String urlWithParams = ORDER_API_URL + "?from=" + fromDate;
        URL url = new URL(urlWithParams);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            // 요청 설정
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            
            log("주문 조회 요청 URL: " + urlWithParams);
            
            // 응답 처리
            int responseCode = conn.getResponseCode();
            log("주문 조회 응답 코드: " + responseCode);
            
            String response;
            if (responseCode == 200) {
                response = readResponse(conn.getInputStream());
            } else {
                response = readResponse(conn.getErrorStream());
                log("주문 조회 오류 응답: " + response);
                return new ArrayList<>();
            }
            
            log("주문 조회 응답: " + response);
            
            // 주문 정보 추출
            return extractOrderInfos(response);
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * HTTP 응답을 문자열로 읽어옵니다.
     */
    private static String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8.name()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    /**
     * JSON 응답에서 access_token을 추출합니다.
     */
    private static String extractAccessToken(String jsonResponse) {
        // 간단한 정규식으로 access_token 추출
        Pattern pattern = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(jsonResponse);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * JSON 응답에서 주문 정보를 추출합니다.
     */
    private static List<OrderInfo> extractOrderInfos(String jsonResponse) {
        List<OrderInfo> orderInfos = new ArrayList<>();
        
        // 전체 주문 블록을 찾기 위한 개선된 정규식
        Pattern orderPattern = Pattern.compile("\"productOrderId\"\\s*:\\s*\"([^\"]+)\".*?\"content\"\\s*:\\s*\\{(.*?)\\}\\s*\\}");
        Matcher orderMatcher = orderPattern.matcher(jsonResponse);
        
        while (orderMatcher.find()) {
            String productOrderId = orderMatcher.group(1);
            String contentBlock = orderMatcher.group(2);
            
            log("처리 중인 productOrderId: " + productOrderId);
            
            // order 블록에서 기본 정보 추출
            Pattern orderBlockPattern = Pattern.compile("\"order\"\\s*:\\s*\\{([^}]*?)\\}");
            Matcher orderBlockMatcher = orderBlockPattern.matcher(contentBlock);
            
            String orderId = productOrderId; // 기본값
            String ordererName = "고객_" + productOrderId.substring(0, Math.min(8, productOrderId.length()));
            String ordererTel = "0000000000";
            String email = "";
            String productName = "";
            Integer day = 1; // 기본값
            String shippingName = "";
            String shippingTel = "";
            
            if (orderBlockMatcher.find()) {
                String orderContent = orderBlockMatcher.group(1);
                
                // orderId 추출
                String extractedOrderId = extractJsonValue(orderContent, "orderId");
                if (extractedOrderId != null && !extractedOrderId.isEmpty()) {
                    orderId = extractedOrderId;
                }
                
                // ordererName 추출
                String extractedOrdererName = extractJsonValue(orderContent, "ordererName");
                if (extractedOrdererName != null && !extractedOrdererName.isEmpty()) {
                    ordererName = extractedOrdererName;
                }
                
                // ordererTel 추출
                String extractedOrdererTel = extractJsonValue(orderContent, "ordererTel");
                if (extractedOrdererTel != null && !extractedOrdererTel.isEmpty()) {
                    ordererTel = extractedOrdererTel;
                }
                
                // email 추출
                String extractedEmail = extractJsonValue(orderContent, "email");
                if (extractedEmail != null && !extractedEmail.isEmpty()) {
                    email = extractedEmail;
                }
            }
            
            // shippingAddress 블록에서 배송지 정보 추출
            Pattern shippingAddressPattern = Pattern.compile("\"shippingAddress\"\\s*:\\s*\\{([^}]*?)\\}");
            Matcher shippingAddressMatcher = shippingAddressPattern.matcher(contentBlock);
            
            if (shippingAddressMatcher.find()) {
                String shippingAddressContent = shippingAddressMatcher.group(1);
                
                // shippingAddress의 name 추출
                String extractedShippingName = extractJsonValue(shippingAddressContent, "name");
                if (extractedShippingName != null && !extractedShippingName.isEmpty()) {
                    shippingName = extractedShippingName;
                    // ordererName을 shippingAddress의 name으로 변경
                    ordererName = extractedShippingName;
                }
                
                // shippingAddress의 tel1 추출
                String extractedShippingTel = extractJsonValue(shippingAddressContent, "tel1");
                if (extractedShippingTel != null && !extractedShippingTel.isEmpty()) {
                    shippingTel = extractedShippingTel;
                    // ordererTel을 shippingAddress의 tel1로 변경
                    ordererTel = extractedShippingTel;
                }
            }
            
            // productOrder 블록에서 추가 정보 추출
            Pattern productOrderPattern = Pattern.compile("\"productOrder\"\\s*:\\s*\\{([^}]*?)\\}");
            Matcher productOrderMatcher = productOrderPattern.matcher(contentBlock);
            
            Integer quantity = 1; // 기본값
            String productOption = "";
            
            if (productOrderMatcher.find()) {
                String productOrderContent = productOrderMatcher.group(1);
                
                // quantity 추출
                String quantityStr = extractJsonValue(productOrderContent, "quantity");
                if (quantityStr != null && !quantityStr.isEmpty()) {
                    try {
                        quantity = Integer.parseInt(quantityStr);
                    } catch (NumberFormatException e) {
                        quantity = 1;
                    }
                }
                
                // productOption 추출
                String extractedProductOption = extractJsonValue(productOrderContent, "productOption");
                if (extractedProductOption != null) {
                    productOption = extractedProductOption;
                }
                
                // productName 추출
                String extractedProductName = extractJsonValue(productOrderContent, "productName");
                if (extractedProductName != null && !extractedProductName.isEmpty()) {
                    productName = extractedProductName;
                }
                
                // productOption에서 productName 추출 (eSIM 데이터 사용량 선택 부분)
                if (productOption != null && productOption.contains("eSIM 데이터 사용량 선택")) {
                    String[] parts = productOption.split(" / ");
                    for (String part : parts) {
                        if (part.contains("eSIM 데이터 사용량 선택") && part.contains(":")) {
                            String productNamePart = part.split(":")[1].trim();
                            if (!productNamePart.equals(".") && !productNamePart.isEmpty()) {
                                productName = productNamePart;
                                break;
                            }
                        }
                    }
                }
                
                // day 추출
                String dayStr = extractJsonValue(productOrderContent, "day");
                if (dayStr != null && !dayStr.isEmpty()) {
                    try {
                        day = Integer.parseInt(dayStr);
                    } catch (NumberFormatException e) {
                        day = 1;
                    }
                }
                
                // productOption에서 day 추출 (사용일수 선택 부분)
                if (productOption != null && productOption.contains("사용일수 선택")) {
                    String[] parts = productOption.split(" / ");
                    for (String part : parts) {
                        if (part.contains("사용일수 선택") && part.contains(":")) {
                            String dayPart = part.split(":")[1].trim();
                            if (!dayPart.equals(".") && !dayPart.isEmpty()) {
                                // "5일" 등에서 숫자만 추출
                                String numberOnly = dayPart.replaceAll("[^0-9]", "");
                                if (!numberOnly.isEmpty()) {
                                    try {
                                        day = Integer.parseInt(numberOnly);
                                    } catch (NumberFormatException e) {
                                        day = 1;
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
            
            // productOption에서 email 추출 (productOrder 처리 후)
            if (productOption != null && productOption.contains("이메일")) {
                String[] parts = productOption.split(" / ");
                for (String part : parts) {
                    if (part.contains("이메일") && part.contains(":")) {
                        // 마지막 ':' 뒤에 있는 문자열을 추출
                        String emailPart = part.substring(part.lastIndexOf(":") + 1).trim();
                    
                        if (!emailPart.equals(".") && emailPart.contains("@")) {
                            email = emailPart;
                            break;
                        }
                    }
                }
            }
            
            log("추출된 정보 - OrderId: " + orderId + ", Name: " + ordererName + ", Tel: " + ordererTel + ", Email: " + email + ", ProductName: " + productName + ", Day: " + day + ", Quantity: " + quantity);
            log("ProductOption: " + productOption);
            
            // 확장된 생성자로 OrderInfo 생성
            OrderInfo orderInfo = new OrderInfo(productOrderId, orderId, ordererName, ordererTel, 
                                               email, day, productName, quantity, productOption);
            
            orderInfos.add(orderInfo);
        }
        
        return orderInfos;
    }
    
    /**
     * JSON 문자열에서 특정 키의 값을 추출합니다.
     */
    private static String extractJsonValue(String json, String key) {
        // 문자열 값 추출 (따옴표로 감싸진 값)
        Pattern stringPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher stringMatcher = stringPattern.matcher(json);
        if (stringMatcher.find()) {
            return stringMatcher.group(1);
        }
        
        // 숫자 값 추출 (따옴표 없는 값)
        Pattern numberPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
        Matcher numberMatcher = numberPattern.matcher(json);
        if (numberMatcher.find()) {
            return numberMatcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 로그 파일을 초기화합니다.
     */
    private static void initializeLogFile() {
        try {
            String logFileName = "naver_commerce_api_" + getCurrentDateString() + ".log";
            File logFile = new File(logFileName);
            
            if (!logFile.exists()) {
                logFile.createNewFile();
                log("로그 파일 생성: " + logFileName);
            }
        } catch (IOException e) {
            System.err.println("로그 파일 초기화 실패: " + e.getMessage());
        }
    }
    
    /**
     * 로그를 파일과 콘솔에 기록합니다.
     */
    private static void log(String message) {
        String timestamp = getCurrentDateTimeString();
        String logMessage = "[" + timestamp + "] " + message;
        
        // 콘솔에 출력
        System.out.println(logMessage);
        
        // 파일에 기록
        try {
            String logFileName = "naver_commerce_api_" + getCurrentDateString() + ".log";
            try (FileWriter fw = new FileWriter(logFileName, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.println(logMessage);
            }
        } catch (IOException e) {
            System.err.println("로그 파일 기록 실패: " + e.getMessage());
        }
    }
    
    /**
     * 현재 날짜를 문자열로 반환합니다 (YYYY-MM-DD).
     */
    private static String getCurrentDateString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    /**
     * 현재 날짜와 시간을 문자열로 반환합니다 (YYYY-MM-DD HH:mm:ss).
     */
    private static String getCurrentDateTimeString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 오늘 날짜를 네이버 API 형식으로 반환합니다.
     */
    private static String getTodayDateString() {
        LocalDateTime now = LocalDateTime.now();
        // 오늘 자정부터 시작
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        
        // ISO 8601 형식으로 변환하고 URL 인코딩
        String isoDate = todayStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        try {
            return URLEncoder.encode(isoDate + "+09:00", StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            // 인코딩 실패 시 기본값 반환
            return "2025-01-27T00:00:00.000%2B09:00";
        }
    }
    
    /**
     * 3분 전 날짜를 네이버 API 형식으로 반환합니다.
     */
    private static String getThreeMinutesAgoDateString() {
        // 한국 표준시(KST, UTC+9) 기준
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        ZonedDateTime threeMinutesAgo = now.minusMinutes(3);
    
        // ISO 8601 형식 +09:00 포함
        String isoDate = threeMinutesAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    
        try {
            return URLEncoder.encode(isoDate, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "2025-01-27T00:00:00.000%2B09:00";
        }
    }

    private static String getYesterdayDateString() {
        LocalDateTime now = LocalDateTime.now();
        // 하루 전 자정부터 시작
        LocalDateTime yesterdayStart = now.toLocalDate().minusDays(1).atStartOfDay();
        
        // ISO 8601 형식으로 변환하고 URL 인코딩
        String isoDate = yesterdayStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        try {
            return URLEncoder.encode(isoDate + "+09:00", StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            // 인코딩 실패 시 기본값 반환
            return "2025-01-26T00:00:00.000%2B09:00";
        }
    }
    
    /**
     * 발송 처리할 주문들을 조회하고 발송 처리합니다.
     * dispatchStatus = 0인 주문들을 처리
     */
    public static void processDispatchOrders() {
        try {
            // 로그 파일 초기화
            initializeLogFile();
            
            log("=== 발송 처리 시작 ===");
            
            // 1. SignatureGenerator로 필요한 값들 생성
            String clientId = System.getenv().getOrDefault("NAVER_CLIENT_ID", "");
            String clientSecret = System.getenv().getOrDefault("NAVER_CLIENT_SECRET", "");
            Long timestamp = System.currentTimeMillis();
            
            String signature = SignatureGenerator.generateSignature(clientId, clientSecret, timestamp);
            
            // 2. OAuth 토큰 발급
            String accessToken = getAccessToken(clientId, timestamp, signature);
            
            if (accessToken != null) {
                log("Access Token 발급 성공: " + accessToken);
                
                // 3. 발송 처리할 주문들 조회 (dispatchStatus = 0)
                List<String> dispatchOrderIds = getDispatchPendingOrderIds();
                
                if (!dispatchOrderIds.isEmpty()) {
                    log("발송 처리할 주문 수: " + dispatchOrderIds.size());
                    
                    for (String productOrderId : dispatchOrderIds) {
                        try {
                            log("발송 처리 중: productOrderId=" + productOrderId);
                            
                            // 발송 상태를 1(발송중)으로 변경
                            updateDispatchStatus(productOrderId, 1);
                            
                            // 네이버에 발송 처리 요청 (QR 발송 완료)
                            boolean success = dispatchProductOrderForQR(accessToken, productOrderId);
                            
                            if (success) {
                                // 발송 상태를 2(발송완료)로 변경
                                updateDispatchStatus(productOrderId, 2);
                                log("발송 처리 성공: " + productOrderId);
                            } else {
                                // 발송 상태를 0(발송대기)로 되돌림
                                updateDispatchStatus(productOrderId, 0);
                                log("발송 처리 실패: " + productOrderId);
                            }
                            
                            // API 호출 간격 조절
                            Thread.sleep(1000);
                            
                        } catch (Exception e) {
                            log("발송 처리 중 오류: " + productOrderId + " - " + e.getMessage());
                            // 오류 발생 시 상태를 0으로 되돌림
                            updateDispatchStatus(productOrderId, 0);
                        }
                    }
                } else {
                    log("발송 처리할 주문이 없습니다.");
                }
                
            } else {
                log("Access Token 발급 실패");
            }
            
            log("=== 발송 처리 완료 ===");
            
        } catch (Exception e) {
            log("발송 처리 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 발송 처리할 주문 ID들을 데이터베이스에서 조회합니다.
     * 조건: dispatchStatus = 0 (발송대기)
     */
    private static List<String> getDispatchPendingOrderIds() {
        List<String> orderIds = new ArrayList<>();
        
        try (Connection conn = MySQLConfig.getConnection()) {
            String sql = "SELECT productOrderId FROM ringtalk.user " +
                        "WHERE dispatchStatus = 0 " +
                        "AND productOrderId NOT LIKE 'manual%' " +
                        "ORDER BY created_at ASC " +
                        "LIMIT 10";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                
                while (rs.next()) {
                    orderIds.add(rs.getString("productOrderId"));
                }
            }
            
        } catch (SQLException e) {
            log("발송 대기 주문 조회 실패: " + e.getMessage());
            e.printStackTrace();
        }
        
        return orderIds;
    }
    
    /**
     * QR 발송 완료된 주문을 네이버에 발송 처리 요청합니다.
     * deliveryMethod: "NOTHING" 사용
     */
    private static boolean dispatchProductOrderForQR(String accessToken, String productOrderId) {
        try {
            URL url = new URL(DISPATCH_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // 요청 설정
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);
            
            // 현재 시간을 ISO 8601 형식으로 생성
            String currentDateTime = getCurrentDateTimeISO();
            
            // 요청 바디 구성 (QR 발송 완료)
            String requestBody = String.format(
                "{\n" +
                "  \"dispatchProductOrders\": [\n" +
                "    {\n" +
                "      \"productOrderId\": \"%s\",\n" +
                "      \"deliveryMethod\": \"NOTHING\",\n" +
                "      \"dispatchDate\": \"%s\"\n" +
                "    }\n" +
                "  ]\n" +
                "}",
                productOrderId,
                currentDateTime
            );
            
            log("발송 처리 요청 바디: " + requestBody);
            
            // 요청 전송
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8.name());
                os.write(input, 0, input.length);
            }
            
            // 응답 처리
            int responseCode = conn.getResponseCode();
            String response;
            if (responseCode == 200) {
                response = readResponse(conn.getInputStream());
            } else {
                response = readResponse(conn.getErrorStream());
            }
            
            log("발송처리 응답 코드: " + responseCode);
            log("발송처리 응답: " + response);
            
            return responseCode == 200;
            
        } catch (Exception e) {
            log("발송처리 오류: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 발송 상태를 업데이트합니다.
     * 0: 발송대기, 1: 발송중, 2: 발송완료
     */
    private static void updateDispatchStatus(String productOrderId, int status) {
        try (Connection conn = MySQLConfig.getConnection()) {
            String sql = "UPDATE ringtalk.user SET dispatchStatus = ?, updated_at = NOW() WHERE productOrderId = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, status);
                pstmt.setString(2, productOrderId);
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    String statusText = status == 0 ? "발송대기" : status == 1 ? "발송중" : "발송완료";
                    log("발송 상태 업데이트 성공: " + productOrderId + " -> " + statusText);
                } else {
                    log("발송 상태 업데이트 실패: " + productOrderId + " - 해당 레코드를 찾을 수 없음");
                }
            }
            
        } catch (SQLException e) {
            log("발송 상태 업데이트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 현재 날짜와 시간을 ISO 8601 형식으로 반환합니다.
     */
    private static String getCurrentDateTimeISO() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }
    
    /**
     * JSON 문자열 이스케이프 처리
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}

