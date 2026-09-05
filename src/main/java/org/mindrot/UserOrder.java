package org.mindrot;

public class UserOrder {
    private String productOrderId;
    private String orderId;
    private String ordererName;
    private String ordererTel;
    private String email;
    private Integer quantity;
    private String productName;
    private int day;
    
    // Getters and Setters
    public String getProductOrderId() { return productOrderId; }
    public void setProductOrderId(String productOrderId) { this.productOrderId = productOrderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getOrdererName() { return ordererName; }
    public void setOrdererName(String ordererName) { this.ordererName = ordererName; }
    
    public String getOrdererTel() { return ordererTel; }
    public void setOrdererTel(String ordererTel) { this.ordererTel = ordererTel; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getProductName() { return productName; }
    public int getDay() { return day; }
    public void setProductName(String string) { this.productName = string;  }
    public void setDay(int int1) {
        this.day = int1;
    }
}
