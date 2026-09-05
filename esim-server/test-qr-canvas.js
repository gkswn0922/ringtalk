import QRCode from "qrcode";
import { createCanvas, loadImage } from "canvas";
import path from "path";
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// QR 코드 + 로고 합성 함수
async function generateQRCodeWithLogo(text, logoPath) {
  try {
    // 1. 기본 QR 코드 생성
    const qrCodeBuffer = await QRCode.toBuffer(text, {
      width: 200,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    });
    
    // 2. Canvas 생성
    const canvas = createCanvas(200, 200);
    const ctx = canvas.getContext('2d');
    
    // 3. QR 코드 이미지 로드 및 그리기
    const qrImage = await loadImage(qrCodeBuffer);
    ctx.drawImage(qrImage, 0, 0, 200, 200);
    
    // 4. 로고 이미지 로드 및 그리기
    const logoImage = await loadImage(logoPath);
    const logoSize = 40;
    const logoX = (200 - logoSize) / 2;
    const logoY = (200 - logoSize) / 2;
    
    // 5. 로고 배경 (흰색 사각형)
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(logoX - 4, logoY - 4, logoSize + 8, logoSize + 8);
    
    // 6. 로고 그리기
    ctx.drawImage(logoImage, logoX, logoY, logoSize, logoSize);
    
    return canvas.toBuffer();
    
  } catch (error) {
    console.error('QR 코드 + 로고 생성 실패:', error);
    throw error;
  }
}

// QR 코드 생성 테스트 함수
async function testQRCodeWithLogo() {
  try {
    console.log('=== QR 코드 + 로고 생성 테스트 시작 ===');
    
    // 테스트용 활성화 코드
    const activationCode = "LPA:1$rsp.demo.com$0913F6176020B7C603E3R42B61P686D3";
    console.log('활성화 코드:', activationCode);
    
    // 로고 파일 경로
    const logoPath = path.join(__dirname, 'public/assets/logo-ringtalk.png');
    console.log('로고 경로:', logoPath);
    
    // 파일 존재 여부 확인
    if (!fs.existsSync(logoPath)) {
      console.error('❌ 로고 파일이 존재하지 않습니다:', logoPath);
      return;
    }
    console.log('✅ 로고 파일 확인됨');
    
    // QR 코드 + 로고 생성
    console.log('QR 코드 + 로고 생성 중...');
    const qrCodeBuffer = await generateQRCodeWithLogo(activationCode, logoPath);
    
    console.log('✅ QR 코드 + 로고 생성 완료');
    
    // Base64로 변환
    const base64Image = `data:image/png;base64,${qrCodeBuffer.toString('base64')}`;
    console.log('✅ Base64 변환 완료');
    
    // HTML 파일로 저장하여 테스트
    const htmlContent = `
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>QR 코드 + 로고 테스트</title>
    <style>
        body {
            font-family: 'Segoe UI', sans-serif;
            padding: 20px;
            background: #f5f5f5;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .title {
            text-align: center;
            color: #333;
            margin-bottom: 30px;
        }
        .qr-section {
            text-align: center;
            margin-bottom: 30px;
        }
        .qr-image {
            border: 2px solid #ddd;
            border-radius: 10px;
            padding: 10px;
            background: white;
        }
        .info {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 20px;
        }
        .info h3 {
            margin-top: 0;
            color: #495057;
        }
        .code {
            font-family: monospace;
            background: #e9ecef;
            padding: 10px;
            border-radius: 3px;
            word-break: break-all;
        }
        .success {
            color: #28a745;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1 class="title">🎯 QR 코드 + 로고 테스트 결과</h1>
        
        <div class="qr-section">
            <h2>생성된 QR 코드 (로고 포함)</h2>
            <img src="${base64Image}" alt="QR 코드 + 로고" class="qr-image">
        </div>
        
        <div class="info">
            <h3>📋 테스트 정보</h3>
            <p><strong>활성화 코드:</strong></p>
            <div class="code">${activationCode}</div>
            <p><strong>로고 파일:</strong> ${logoPath}</p>
            <p><strong>생성 시간:</strong> ${new Date().toLocaleString('ko-KR')}</p>
        </div>
        
        <div class="success">
            ✅ QR 코드에 RingTalk 로고가 성공적으로 적용되었습니다!
        </div>
        
        <div style="margin-top: 20px; padding: 15px; background: #d4edda; border-radius: 5px;">
            <h4>📱 테스트 방법:</h4>
            <ol>
                <li>위 QR 코드를 스마트폰으로 스캔해보세요</li>
                <li>eSIM 활성화 코드가 정상적으로 인식되는지 확인하세요</li>
                <li>로고가 QR 코드 가운데에 잘 표시되는지 확인하세요</li>
            </ol>
        </div>
        
        <div style="margin-top: 20px; padding: 15px; background: #fff3cd; border-radius: 5px;">
            <h4>🔧 기술 정보:</h4>
            <ul>
                <li><strong>QR 코드 라이브러리:</strong> qrcode</li>
                <li><strong>이미지 합성:</strong> canvas</li>
                <li><strong>로고 크기:</strong> 40x40px</li>
                <li><strong>로고 배경:</strong> 흰색 사각형</li>
                <li><strong>로고 테두리:</strong> 없음 (깔끔한 디자인)</li>
            </ul>
        </div>
    </div>
</body>
</html>`;
    
    // HTML 파일 저장
    const outputPath = path.join(__dirname, 'qr-test-result-with-logo.html');
    fs.writeFileSync(outputPath, htmlContent);
    console.log('✅ 테스트 결과 HTML 파일 저장됨:', outputPath);
    
    console.log('\n=== 테스트 완료 ===');
    console.log('브라우저에서 다음 파일을 열어서 결과를 확인하세요:');
    console.log(`file://${outputPath}`);
    
  } catch (error) {
    console.error('❌ 테스트 실패:', error);
    console.error('에러 상세:', error.message);
  }
}

// 테스트 실행
testQRCodeWithLogo();
