package org.mindrot;

/**
 * 네이버 커머스 주문 정보를 담는 클래스
 */
public class OrderInfo {
    private String productOrderId;
    private String orderId;
    private String ordererName;
    private String ordererTel;
    private String email;
    private Integer day;
    private String productName;
    private Integer quantity;
    private String productOption;
    
    public OrderInfo(String productOrderId, String orderId, String ordererName, String ordererTel) {
        this.productOrderId = productOrderId;
        this.orderId = orderId;
        this.ordererName = ordererName;
        this.ordererTel = ordererTel;
    }
    
    // 확장된 생성자
    public OrderInfo(String productOrderId, String orderId, String ordererName, String ordererTel, 
                     String email, Integer day, String productName, Integer quantity, String productOption) {
        this.productOrderId = productOrderId;
        this.orderId = orderId;
        this.ordererName = ordererName;
        this.ordererTel = ordererTel;
        this.email = email;
        this.day = day;
        this.productName = productName;
        this.quantity = quantity;
        this.productOption = productOption;
    }
    
    // Getters
    public String getProductOrderId() { return productOrderId; }
    public String getOrderId() { return orderId; }
    public String getOrdererName() { return ordererName; }
    public String getOrdererTel() { return ordererTel; }
    public String getEmail() { return email; }
    public Integer getDay() { return day; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public String getProductOption() { return productOption; }
    
    // Setters
    public void setProductOrderId(String productOrderId) { this.productOrderId = productOrderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setOrdererName(String ordererName) { this.ordererName = ordererName; }
    public void setOrdererTel(String ordererTel) { this.ordererTel = ordererTel; }
    public void setEmail(String email) { this.email = email; }
    public void setDay(Integer day) { this.day = day; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setProductOption(String productOption) { this.productOption = productOption; }
    
    /**
     * productOption에서 이메일을 추출합니다.
     * 예: "이메일 (ex : ring@naver.com): test@naver.com" → "test@naver.com"
     */
    public void extractEmailFromOption() {
        if (productOption != null && productOption.contains("이메일")) {
            String[] parts = productOption.split(" / ");
            for (String part : parts) {
                if (part.contains("이메일") && part.contains(":")) {
                    String emailPart = part.split(":")[2].trim();
                    if (!emailPart.equals(".") && emailPart.contains("@")) {
                        this.email = emailPart;
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * productOption에서 사용일수를 추출합니다.
     * 예: "사용일수 선택: 1일" → 1
     */
    public void extractDayFromOption() {
        if (productOption != null && productOption.contains("사용일수 선택")) {
            String[] parts = productOption.split(" / ");
            for (String part : parts) {
                if (part.contains("사용일수 선택") && part.contains(":")) {
                    String dayPart = part.split(":")[1].trim();
                    if (!dayPart.equals(".")) {
                        // "1일", "3일", "7일" 등에서 숫자만 추출
                        String numberOnly = dayPart.replaceAll("[^0-9]", "");
                        if (!numberOnly.isEmpty()) {
                            try {
                                this.day = Integer.parseInt(numberOnly);
                            } catch (NumberFormatException e) {
                                this.day = 1; // 기본값
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * productOption에서 eSIM 데이터 사용량을 추출합니다.
     */
    public void extractProductNameFromOption() {
        if (productOption != null && productOption.contains("eSIM 데이터 사용량 선택")) {
            String[] parts = productOption.split(" / ");
            for (String part : parts) {
                if (part.contains("eSIM 데이터 사용량 선택") && part.contains(":")) {
                    String productPart = part.split(":")[1].trim();
                    if (!productPart.equals(".")) {
                        this.productName = productPart;
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * productOption에서 모든 필요한 정보를 추출합니다.
     */
    public void extractAllFromOption() {
        extractEmailFromOption();
        extractDayFromOption();
        extractProductNameFromOption();
    }
    
    @Override
    public String toString() {
        return "OrderInfo{" +
                "productOrderId='" + productOrderId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", ordererName='" + ordererName + '\'' +
                ", ordererTel='" + ordererTel + '\'' +
                ", email='" + email + '\'' +
                ", day='" + day + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", productOption='" + productOption + '\'' +
                '}';
    }
}
