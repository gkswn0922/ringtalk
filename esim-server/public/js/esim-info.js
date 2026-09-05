// eSIM 정보 화면 관리
class ESIMInfo {
    constructor() {
        this.esimData = null;
        this.init();
    }

    async init() {
        await this.loadESIMData();
        this.updateUI();
        this.generateQRCode();
        this.updateTime();
        this.setupEventListeners();
    }

    async loadESIMData() {
        try {
            // URL 파라미터에서 주문 ID 가져오기
            const urlParams = new URLSearchParams(window.location.search);
            const orderId = urlParams.get('orderId') || 'ORD123456';
            
            // 실제 API 호출 (현재는 샘플 데이터)
            // const response = await fetch(`/api/esim-info/${orderId}`);
            // this.esimData = await response.json();
            
            // 샘플 데이터
            this.esimData = {
                // 여행 정보
                departure: {
                    city: '북경',
                    time: '21:30',
                    date: '2019년 5월 5일 일요일'
                },
                arrival: {
                    city: '상해',
                    time: '23:30',
                    date: '2019년 5월 5일 일요일'
                },
                flightDuration: '2h20m',
                
                // 티켓 정보
                ticketNumber: '200MG',
                seatNumber: '청도항공',
                flightNumber: '5TW123',
                boardingTime: '18:10',
                seatClass: 'A20',
                gate: '경제석',
                terminal: '2SA',
                
                // eSIM 활성화 정보
                ios: {
                    smDpAddress: 'rsp.esimcase.com',
                    activationCode: '$H28E-3U6QI-O513K-90TON'
                },
                android: {
                    activationCode: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON'
                },
                
                // QR 코드 데이터
                qrData: 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON'
            };
        } catch (error) {
            console.error('eSIM 데이터 로딩 실패:', error);
            this.showError('eSIM 정보를 불러올 수 없습니다.');
        }
    }

    updateUI() {
        if (!this.esimData) return;

        // 여행 정보 업데이트
        document.getElementById('departureCity').textContent = this.esimData.departure.city;
        document.getElementById('departureTime').textContent = this.esimData.departure.time;
        document.getElementById('departureDate').textContent = this.esimData.departure.date;
        document.getElementById('arrivalCity').textContent = this.esimData.arrival.city;
        document.getElementById('arrivalTime').textContent = this.esimData.arrival.time;
        document.getElementById('arrivalDate').textContent = this.esimData.arrival.date;
        document.getElementById('flightDuration').textContent = this.esimData.flightDuration;

        // 티켓 정보 업데이트
        document.getElementById('ticketNumber').textContent = this.esimData.ticketNumber;
        document.getElementById('seatNumber').textContent = this.esimData.seatNumber;
        document.getElementById('flightNumber').textContent = this.esimData.flightNumber;
        document.getElementById('boardingTime').textContent = this.esimData.boardingTime;
        document.getElementById('seatClass').textContent = this.esimData.seatClass;
        document.getElementById('gate').textContent = this.esimData.gate;
        document.getElementById('terminal').textContent = this.esimData.terminal;

        // eSIM 활성화 정보 업데이트
        document.getElementById('iosSmDp').textContent = this.esimData.ios.smDpAddress;
        document.getElementById('iosActivationCode').textContent = this.esimData.ios.activationCode;
        document.getElementById('aosActivationCode').textContent = this.esimData.android.activationCode;
    }

    async generateQRCode() {
        try {
            const canvas = document.getElementById('qrCode');
            const qrData = this.esimData.qrData || 'LPA:1$rsp.esimcase.com$H28E-3U6QI-O513K-90TON';
            
            // 바코드 스타일로 QR 코드 생성 (가로로 긴 형태)
            await QRCode.toCanvas(canvas, qrData, {
                width: 280,
                height: 80,
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
            const ctx = canvas.getContext('2d');
            
            // 바코드 스타일의 패턴 그리기
            ctx.fillStyle = '#f0f0f0';
            ctx.fillRect(0, 0, 280, 80);
            
            // 바코드 패턴 시뮬레이션
            ctx.fillStyle = '#000000';
            for (let i = 0; i < 50; i++) {
                const x = (i * 5) + 10;
                const width = Math.random() > 0.5 ? 2 : 1;
                ctx.fillRect(x, 10, width, 60);
            }
        }
    }

    updateTime() {
        const timeElement = document.querySelector('.status-center .time');
        const updateTime = () => {
            const now = new Date();
            const timeString = now.toLocaleTimeString('ko-KR', {
                hour: '2-digit',
                minute: '2-digit',
                hour12: false
            });
            timeElement.textContent = timeString;
        };

        updateTime();
        setInterval(updateTime, 1000);
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
    }

    showError(message) {
        const ticketContainer = document.querySelector('.ticket-container');
        ticketContainer.innerHTML = `
            <div class="error-message" style="
                background: white;
                padding: 40px;
                border-radius: 16px;
                text-align: center;
                box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            ">
                <h3 style="color: #ff4757; margin-bottom: 16px;">⚠️ 오류 발생</h3>
                <p style="color: #666; margin-bottom: 20px;">${message}</p>
                <button onclick="location.reload()" style="
                    background: #74b9ff;
                    color: white;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 8px;
                    cursor: pointer;
                    font-weight: 500;
                ">다시 시도</button>
            </div>
        `;
    }
}

// 전역 함수들
function openUserGuide() {
    document.getElementById('userGuideModal').style.display = 'block';
}

function openTroubleshoot() {
    document.getElementById('troubleshootModal').style.display = 'block';
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
}

function copyToClipboard(elementId) {
    const element = document.getElementById(elementId);
    const text = element.textContent;
    
    navigator.clipboard.writeText(text).then(() => {
        // 복사 성공 피드백
        const copyBtn = element.parentElement.querySelector('.copy-btn');
        const originalText = copyBtn.textContent;
        const originalClass = copyBtn.className;
        
        copyBtn.textContent = '복사됨!';
        copyBtn.className += ' copy-success';
        
        setTimeout(() => {
            copyBtn.textContent = originalText;
            copyBtn.className = originalClass;
        }, 2000);
        
        // 토스트 메시지 표시 (옵션)
        showToast('클립보드에 복사되었습니다!');
    }).catch(err => {
        console.error('클립보드 복사 실패:', err);
        
        // 대체 방법: 텍스트 선택
        const range = document.createRange();
        range.selectNode(element);
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        
        try {
            document.execCommand('copy');
            showToast('클립보드에 복사되었습니다!');
        } catch (fallbackErr) {
            showToast('복사 실패. 수동으로 복사해주세요.');
        }
        
        window.getSelection().removeAllRanges();
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
    toast.style.cssText = `
        position: fixed;
        bottom: 100px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 12px 24px;
        border-radius: 25px;
        font-size: 14px;
        z-index: 10000;
        animation: toastSlideUp 0.3s ease;
    `;
    
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

function goBack() {
    if (document.referrer && document.referrer !== window.location.href) {
        history.back();
    } else {
        window.location.href = '/dashboard';
    }
}

// 토스트 애니메이션 CSS 추가
const toastStyle = document.createElement('style');
toastStyle.textContent = `
    @keyframes toastSlideUp {
        from {
            opacity: 0;
            transform: translate(-50%, 20px);
        }
        to {
            opacity: 1;
            transform: translate(-50%, 0);
        }
    }
    
    @keyframes toastSlideDown {
        from {
            opacity: 1;
            transform: translate(-50%, 0);
        }
        to {
            opacity: 0;
            transform: translate(-50%, 20px);
        }
    }
`;
document.head.appendChild(toastStyle);

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => {
    new ESIMInfo();
});

// 전역 객체로 노출 (디버깅용)
window.ESIMInfo = ESIMInfo;

