-- ringtalk 데이터베이스의 user 테이블 생성
CREATE TABLE IF NOT EXISTS `user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `productOrderId` varchar(50) NOT NULL,
  `orderId` varchar(50) NOT NULL,
  `ordererName` varchar(100) DEFAULT NULL,
  `ordererTel` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `productName` varchar(200) DEFAULT NULL,
  `day` int(11) DEFAULT 1,
  `quantity` int(11) DEFAULT 1,
  `snPin` text DEFAULT NULL,
  `QR` text DEFAULT NULL,
  `orderTid` varchar(100) DEFAULT NULL,
  `kakaoSendYN` enum('Y','N') DEFAULT 'N',
  `dispatchStatus` int(11) DEFAULT 0 COMMENT '0:발송대기, 1:발송중, 2:발송완료',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_order` (`productOrderId`, `orderId`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_orderTid` (`orderTid`),
  KEY `idx_kakaoSendYN` (`kakaoSendYN`),
  KEY `idx_dispatchStatus` (`dispatchStatus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 테이블에 dispatchStatus 컬럼 추가 (이미 테이블이 있는 경우)
ALTER TABLE `user` 
ADD COLUMN `dispatchStatus` int(11) DEFAULT 0 COMMENT '0:발송대기, 1:발송중, 2:발송완료' AFTER `kakaoSendYN`,
ADD KEY `idx_dispatchStatus` (`dispatchStatus`);

-- 샘플 데이터 삽입 (테스트용)
INSERT INTO `user` (`productOrderId`, `orderId`, `ordererName`, `ordererTel`, `email`, `productName`, `day`, `quantity`) VALUES
('2025083176163541', '2025083152856911', '홍길동', '01011112222', 'test@example.com', '베트남 이심 eSIM', 5, 1),
('2025083176163542', '2025083152856912', '이철수', '01012345678', 'test2@example.com', '베트남 이심 eSIM', 3, 2),
('2025083176163543', '2025083152856913', '박영희', '01087654321', 'test3@example.com', '베트남 이심 eSIM', 7, 1);
