package org.mindrot;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class SignatureGenerator {
    public static String generateSignature(String clientId, String clientSecret, Long timestamp) {
        // 밑줄로 연결하여 password 생성
        String password = clientId + "_" + timestamp;
        // bcrypt 해싱
        String hashedPw = BCrypt.hashpw(password, clientSecret);
        // base64 인코딩
        return Base64.getUrlEncoder().encodeToString(hashedPw.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String args[]) {
        String clientId = System.getenv().getOrDefault("NAVER_CLIENT_ID", "");
        String clientSecret = System.getenv().getOrDefault("NAVER_CLIENT_SECRET", "");
        Long timestamp = System.currentTimeMillis();
        
        System.out.println("ClientId: " + clientId);
        System.out.println("ClientSecret: " + clientSecret);
        System.out.println("Timestamp: " + timestamp);
        System.out.println("Password: " + clientId + "_" + timestamp);
        System.out.println("Signature: " + generateSignature(clientId, clientSecret, timestamp));
    }
}
