// eSIM QR 코드 상세페이지 관리
class ESIMQRDetail {
    constructor() {
        this.esimData = null;
        this.init();
    }

    async init() {
        await this.loadESIMData();
        this.updateUI();
        this.generateQRCode();
        this.setupEventListeners();
    }

    async loadESIMData() {
        try {
            // URL 파라미터에서 주문 ID 가져오기
            const urlParams = new URLSearchParams(window.location.search);
            const orderId = urlParams.get('orderId') || 'ORD123456';
            
            // 실제 API 호출 (현재는 샘플 데이터)
            // const response = await fetch(`/api/esim-qr-detail/${orderId}`);
            // this.esimData = await response.json();
            
            // 샘플 데이터
            this.esimData = {
                iccid: '89882470000012345678',
                customerName: '홍길동',
                ios: {
                    smDpAddress: 'rsp.esimcase.com',
                    activationCode: '$H28E-3U6QI-O513K-90TON'
                },
                android: {
                    activationCode: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON'
                },
                qrData: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON',
                usage: {
                    totalUsed: '0.00',
                    remaining: '1.00',
                    status: '상품 활성화 전입니다!'
                }
            };
        } catch (error) {
            console.error('eSIM 데이터 로딩 실패:', error);
            this.showError('eSIM 정보를 불러올 수 없습니다.');
        }
    }

    updateUI() {
        if (!this.esimData) return;

        // ICCID와 고객명 업데이트
        const iccidElement = document.getElementById('iccid');
        const customerNameElement = document.getElementById('customerName');
        
        if (iccidElement) iccidElement.textContent = this.esimData.iccid;
        if (customerNameElement) customerNameElement.textContent = this.esimData.customerName;

        // iOS 활성화 정보 업데이트
        const iosSmDpElement = document.getElementById('iosSmDp');
        const iosActivationCodeElement = document.getElementById('iosActivationCode');
        
        if (iosSmDpElement) iosSmDpElement.value = this.esimData.ios.smDpAddress;
        if (iosActivationCodeElement) iosActivationCodeElement.value = this.esimData.ios.activationCode;

        // Android 활성화 정보 업데이트
        const aosActivationCodeElement = document.getElementById('aosActivationCode');
        if (aosActivationCodeElement) aosActivationCodeElement.value = this.esimData.android.activationCode;

        // 사용량 정보 업데이트
        const statusElement = document.querySelector('.status');
        const usageAmountElement = document.querySelector('.usage-amount');
        const dataRemainingElement = document.querySelector('.data-remaining');
        
        if (statusElement) statusElement.textContent = this.esimData.usage.status;
        if (usageAmountElement) usageAmountElement.textContent = this.esimData.usage.totalUsed + ' GB';
        if (dataRemainingElement) dataRemainingElement.textContent = `잔여 데이터 ${this.esimData.usage.remaining}GB`;
    }

    async generateQRCode() {
        try {
            const canvas = document.getElementById('qrCode');
            if (!canvas) return;

            const qrData = this.esimData?.qrData || 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON';
            
            // QR 코드 생성
            await QRCode.toCanvas(canvas, qrData, {
                width: 120,
                height: 120,
                margin: 1,
                color: {
                    dark: '#000000',
                    light: '#FFFFFF'
                },
                errorCorrectionLevel: 'M'
            });
        } catch (error) {
            console.error('QR 코드 생성 실패:', error);
            const canvas = document.getElementById('qrCode');
            if (!canvas) return;

            const ctx = canvas.getContext('2d');
            
            // QR 코드 대체 이미지 그리기
            ctx.fillStyle = '#f0f0f0';
            ctx.fillRect(0, 0, 120, 120);
            
            ctx.fillStyle = '#000000';
            ctx.font = '12px Arial';
            ctx.textAlign = 'center';
            ctx.fillText('QR Code', 60, 60);
        }
    }

    setupEventListeners() {
        // 모달 외부 클릭 시 닫기
        window.addEventListener('click', (event) => {
            if (event.target.classList.contains('modal')) {
                event.target.style.display = 'none';
            }
        });

        // ESC 키로 모달 닫기
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                const modals = document.querySelectorAll('.modal');
                modals.forEach(modal => {
                    if (modal.style.display === 'block') {
                        modal.style.display = 'none';
                    }
                });
            }
        });

        // 입력 필드 클릭 시 전체 선택
        const inputFields = document.querySelectorAll('input[readonly]');
        inputFields.forEach(input => {
            input.addEventListener('click', () => {
                input.select();
            });
        });
    }

    showError(message) {
        const mainCard = document.querySelector('.main-card');
        if (!mainCard) return;

        mainCard.innerHTML = `
            <div class="error-message" style="
                padding: 40px;
                text-align: center;
                background: white;
            ">
                <h3 style="color: #e17055; margin-bottom: 16px; font-size: 18px;">⚠️ 오류 발생</h3>
                <p style="color: #636e72; margin-bottom: 20px; line-height: 1.5;">${message}</p>
                <button onclick="location.reload()" style="
                    background: #74b9ff;
                    color: white;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 8px;
                    cursor: pointer;
                    font-weight: 500;
                    font-size: 14px;
                ">다시 시도</button>
            </div>
        `;
    }
}

// 전역 함수들
function openInstallGuide() {
    const modal = document.getElementById('installGuideModal');
    if (modal) {
        modal.style.display = 'block';
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
}

function copyToClipboard(elementId) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const text = element.value || element.textContent;
    
    navigator.clipboard.writeText(text).then(() => {
        // 복사 성공 피드백
        const copyBtn = element.parentElement.querySelector('.copy-btn');
        if (!copyBtn) return;

        const originalText = copyBtn.textContent;
        const originalClass = copyBtn.className;
        
        copyBtn.textContent = '복사됨!';
        copyBtn.className += ' copy-success';
        
        setTimeout(() => {
            copyBtn.textContent = originalText;
            copyBtn.className = originalClass;
        }, 2000);
        
        // 토스트 메시지 표시
        showToast('클립보드에 복사되었습니다!');
    }).catch(err => {
        console.error('클립보드 복사 실패:', err);
        
        // 대체 방법: 텍스트 선택
        element.select();
        element.setSelectionRange(0, 99999); // 모바일에서도 작동하도록
        
        try {
            document.execCommand('copy');
            showToast('클립보드에 복사되었습니다!');
        } catch (fallbackErr) {
            showToast('복사 실패. 수동으로 복사해주세요.');
        }
    });
}

function showToast(message) {
    // 기존 토스트 제거
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }
    
    // 새 토스트 생성
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    
    document.body.appendChild(toast);
    
    // 3초 후 제거
    setTimeout(() => {
        toast.style.animation = 'toastSlideDown 0.3s ease forwards';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.remove();
            }
        }, 300);
    }, 3000);
}

// QR 코드 확대 기능
function enlargeQRCode() {
    const canvas = document.getElementById('qrCode');
    if (!canvas) return;

    // 모달 생성
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.display = 'block';
    modal.innerHTML = `
        <div class="modal-content" style="max-width: 400px;">
            <div class="modal-header">
                <h2>📱 QR 코드</h2>
                <button class="close-btn" onclick="this.closest('.modal').remove()">&times;</button>
            </div>
            <div class="modal-body" style="text-align: center; padding: 40px;">
                <canvas id="enlargedQR" width="240" height="240" style="border: 2px solid #e0e0e0; border-radius: 8px;"></canvas>
                <p style="margin-top: 16px; color: #636e72; font-size: 14px;">
                    QR 코드를 스캔하여 eSIM을 설치하세요
                </p>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    
    // 확대된 QR 코드 생성
    const enlargedCanvas = document.getElementById('enlargedQR');
    const qrData = window.esimApp?.esimData?.qrData || 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON';
    
    QRCode.toCanvas(enlargedCanvas, qrData, {
        width: 240,
        height: 240,
        margin: 2,
        color: {
            dark: '#000000',
            light: '#FFFFFF'
        },
        errorCorrectionLevel: 'M'
    });
}

// QR 코드 클릭 이벤트 추가
document.addEventListener('DOMContentLoaded', () => {
    const qrCanvas = document.getElementById('qrCode');
    if (qrCanvas) {
        qrCanvas.style.cursor = 'pointer';
        qrCanvas.addEventListener('click', enlargeQRCode);
    }
});

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => {
    window.esimApp = new ESIMQRDetail();
});

// 전역 객체로 노출 (디버깅용)
window.ESIMQRDetail = ESIMQRDetail;
