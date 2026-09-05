package org.mindrot;

import java.sql.*;
import java.util.List;

/**
 * user 테이블에 네이버 커머스 주문 정보를 저장하는 DAO
 * 실제 데이터베이스 구조에 맞게 작성됨
 */
public class UserOrderDAO {
    
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "ringtalk");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul";
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");
    
    /**
     * 데이터베이스 연결을 가져옵니다.
     */
    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC 드라이버를 찾을 수 없습니다.", e);
        }
    }
    
    /**
     * productOrderId를 user 테이블에 저장합니다.
     * 실제 데이터베이스 구조에 맞게 수정됨
     */
    public static boolean saveProductOrderIdToUser(String productOrderId, String customerName, String customerTel, String customerEmail) {
        String insertOrUpdateSQL = 
            "INSERT INTO user (productOrderId, orderId, ordererName, ordererTel, productName, day, snPin, QR, created_at, kakaoSendYN) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?) " +
            "ON DUPLICATE KEY UPDATE " +
                "ordererName = VALUES(ordererName), " +
                "ordererTel = VALUES(ordererTel), " +
                "productName = VALUES(productName), " +
                "day = VALUES(day), " +
                "snPin = VALUES(snPin), " +
                "QR = VALUES(QR), " +
                "created_at = NOW()";
        
        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(insertOrUpdateSQL)) {
                
                // 데이터베이스 구조에 맞게 설정
                pstmt.setString(1, productOrderId);           // productOrderId (varchar)
                pstmt.setString(2, productOrderId);           // orderId (varchar) - productOrderId와 동일
                pstmt.setString(3, customerName);             // ordererName (varchar)
                
                // ordererTel을 int로 변환 (데이터베이스가 int 타입)
                int ordererTelInt = 0;
                if (customerTel != null && !customerTel.trim().isEmpty()) {
                    try {
                        ordererTelInt = Integer.parseInt(customerTel);
                    } catch (NumberFormatException e) {
                        // 전화번호가 숫자가 아니면 기본값 사용
                        ordererTelInt = 0;
                    }
                }
                pstmt.setInt(4, ordererTelInt);               // ordererTel (int)
                
                pstmt.setString(5, "eSIM 상품");              // productName (varchar)
                pstmt.setString(6, "2025-08-29");            // day (varchar)
                pstmt.setString(9, "N");                     // kakaoSendYN (varchar) - NOT NULL
                
                int result = pstmt.executeUpdate();
                System.out.println("사용자 정보 저장 완료 (productOrderId: " + productOrderId + ")");
                return result > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("사용자 정보 저장 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * productOrderId로 user 정보를 조회합니다.
     */
    public static void getUserByProductOrderId(String productOrderId) {
        String selectSQL = "SELECT * FROM user WHERE productOrderId = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setString(1, productOrderId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                System.out.println("=== 사용자 정보 ===");
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    System.out.println(columnName + ": " + (value != null ? value.toString() : "NULL"));
                }
            } else {
                System.out.println("productOrderId '" + productOrderId + "'에 해당하는 사용자를 찾을 수 없습니다.");
            }
            
        } catch (SQLException e) {
            System.err.println("사용자 조회 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 모든 주문 정보를 저장합니다.
     */
    public static void saveOrderInfos(List<OrderInfo> orderInfos) {
        System.out.println("=== user 테이블에 주문 정보 저장 시작 ===");
        
        int successCount = 0;
        for (OrderInfo orderInfo : orderInfos) {
            // 실제 주문 정보로 저장
            boolean success = saveOrderInfoToUser(orderInfo);
            
            if (success) {
                successCount++;
            }
        }
        
        System.out.println("총 " + orderInfos.size() + "개 중 " + successCount + "개 저장 완료");
    }
    
    /**
     * OrderInfo 객체를 user 테이블에 저장합니다.
     */
    public static boolean saveOrderInfoToUser(OrderInfo orderInfo) {
        // OrderInfo에서 productOption 정보 추출
        orderInfo.extractAllFromOption();
        
        String insertOrUpdateSQL = 
            "INSERT INTO user (productOrderId, orderId, ordererName, ordererTel, email, productName, day, quantity, snPin, QR, created_at, kakaoSendYN) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?) " +
            "ON DUPLICATE KEY UPDATE " +
                "ordererName = VALUES(ordererName), " +
                "ordererTel = VALUES(ordererTel), " +
                "email = VALUES(email), " +
                "productName = VALUES(productName), " +
                "day = VALUES(day), " +
                "quantity = VALUES(quantity), " +
                "snPin = VALUES(snPin), " +
                "QR = VALUES(QR), " +
                "created_at = NOW()";
        
        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(insertOrUpdateSQL)) {
                
                // OrderInfo에서 실제 데이터 사용
                pstmt.setString(1, orderInfo.getProductOrderId());     // productOrderId (varchar)
                pstmt.setString(2, orderInfo.getOrderId());            // orderId (varchar)
                pstmt.setString(3, orderInfo.getOrdererName());        // ordererName (varchar)
                
                // ordererTel을 int로 변환 (데이터베이스가 int 타입)
                int ordererTelInt = 0;
                String ordererTel = orderInfo.getOrdererTel();
                if (ordererTel != null && !ordererTel.trim().isEmpty()) {
                    try {
                        // 전화번호에서 숫자만 추출
                        String cleanTel = ordererTel.replaceAll("[^0-9]", "");
                        if (!cleanTel.isEmpty()) {
                            ordererTelInt = Integer.parseInt(cleanTel);
                        }
                    } catch (NumberFormatException e) {
                        // 전화번호가 숫자가 아니거나 너무 크면 기본값 사용
                        ordererTelInt = 0;
                    }
                }
                pstmt.setInt(4, ordererTelInt);                        // ordererTel (int)
                
                // 추출된 데이터 또는 기본값 사용
                pstmt.setString(5, orderInfo.getEmail() != null ? orderInfo.getEmail() : "");  // email (varchar)
                pstmt.setString(6, orderInfo.getProductName() != null ? orderInfo.getProductName() : "eSIM 상품");  // productName (varchar)
                pstmt.setInt(7, orderInfo.getDay() != null ? orderInfo.getDay() : 1);   // day (int)
                pstmt.setInt(8, orderInfo.getQuantity() != null ? orderInfo.getQuantity() : 1); // quantity (int)
                
                pstmt.setString(9, null);
                pstmt.setString(10, null);
                pstmt.setString(11, "N");                              // kakaoSendYN (varchar) - NOT NULL
                
                int result = pstmt.executeUpdate();
                System.out.println("주문 정보 저장 완료 - " + orderInfo.toString());
                return result > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("주문 정보 저장 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 연결 테스트
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("MySQL 연결 테스트 실패: " + e.getMessage());
            return false;
        }
    }
}
