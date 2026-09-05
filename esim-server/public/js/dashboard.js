let allOrders = [];

async function loadData() {
    try {
        console.log('데이터 로딩 시작...');
        const response = await fetch('/api/admin/orders');
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        console.log('받은 데이터:', data);
        
        if (data.error) {
            throw new Error(data.message || data.error);
        }
        
        allOrders = data.orders || [];
        console.log('주문 데이터 개수:', allOrders.length);
        
        // 통계 업데이트
        updateStats(data.stats || { total: 0, sent: 0, pending: 0, today: 0, todayRevenue: 0 });
        
        // 테이블 업데이트
        updateTable(allOrders);
        
        // 마지막 업데이트 시간
        document.getElementById('lastUpdated').textContent = 
            `마지막 업데이트: ${new Date().toLocaleString('ko-KR')}`;
            
    } catch (error) {
        console.error('데이터 로딩 실패:', error);
        document.getElementById('orderTableBody').innerHTML = 
            `<tr><td colspan="10" style="text-align: center; color: #ef4444;">
                데이터 로딩에 실패했습니다.<br>
                <small>${error.message}</small>
            </td></tr>`;
        
        // 통계도 초기화
        updateStats({ total: 0, sent: 0, pending: 0, today: 0, todayRevenue: 0 });
    }
}

function updateStats(stats) {
    document.getElementById('totalOrders').textContent = stats.total;
    document.getElementById('sentMessages').textContent = stats.sent;
    document.getElementById('pendingMessages').textContent = stats.pending;
    document.getElementById('todayOrders').textContent = stats.today;
    document.getElementById('todayRevenue').textContent = formatCurrency(parseInt(stats.todayRevenue) || 0);
}

function updateTable(orders) {
    const tbody = document.getElementById('orderTableBody');
    
    if (orders.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" style="text-align: center; color: #666;">주문 데이터가 없습니다.</td></tr>';
        return;
    }
    
    tbody.innerHTML = orders.map(order => `
        <tr>
            <td>${order.productOrderId}</td>
            <td>${order.ordererName}</td>
            <td>${formatPhoneNumber(order.ordererTel)}</td>
            <td>${generateEsimUrlButton(order.productOrderId)}</td>
            <td>${order.productName || '-'}</td>
            <td>${order.day || '-'}일</td>
            <td>${order.quantity || '-'}</td>
            <td>${order.snCode ? order.snCode.replace(/\|/g, '<br>') : '-'}</td>
            <td>${order.QR ? `<a href="${order.QR}" target="_blank" class="qr-link">QR 보기</a>` : '<span class="no-qr">없음</span>'}</td>
            <td>${getStatusBadge(order.QR)}</td>
            <td>${formatDateTime(order.created_at)}</td>
        </tr>
    `).join('');
}

function getStatusBadge(qr) {
    if (qr && qr.trim() !== '') {
        return '<span class="status-badge status-sent">전송완료</span>';
    } else {
        return '<span class="status-badge status-pending">대기중</span>';
    }
}

function generateEsimUrlButton(productOrderId) {
    return `
        <button class="esim-url-btn" data-order-id="${productOrderId}" title="eSIM URL 복사">
            📋 복사
        </button>
    `;
}

async function copyEsimUrls(productOrderId, buttonElement) {
    try {
        const response = await fetch(`/api/admin/esim-urls/${productOrderId}`);
        const data = await response.json();
        
        if (data.urls && data.urls.length > 0) {
            // 모든 URL을 줄바꿈으로 구분하여 복사
            const allUrls = data.urls.map(item => item.url).join('\n');
            await navigator.clipboard.writeText(allUrls);
            
            // 버튼 텍스트를 "복사됨!"으로 변경
            buttonElement.textContent = '✅ 복사됨!';
            buttonElement.style.backgroundColor = '#10b981';
            buttonElement.style.color = 'white';
            
            // 2초 후 원래 상태로 복구
            setTimeout(() => {
                buttonElement.textContent = '📋 복사';
                buttonElement.style.backgroundColor = '';
                buttonElement.style.color = '';
            }, 2000);
        } else {
            // URL이 없는 경우
            buttonElement.textContent = '❌ 없음';
            buttonElement.style.backgroundColor = '#ef4444';
            buttonElement.style.color = 'white';
            
            setTimeout(() => {
                buttonElement.textContent = '📋 복사';
                buttonElement.style.backgroundColor = '';
                buttonElement.style.color = '';
            }, 2000);
        }
    } catch (error) {
        console.error('URL 복사 실패:', error);
        buttonElement.textContent = '❌ 실패';
        buttonElement.style.backgroundColor = '#ef4444';
        buttonElement.style.color = 'white';
        
        setTimeout(() => {
            buttonElement.textContent = '📋 복사';
            buttonElement.style.backgroundColor = '';
            buttonElement.style.color = '';
        }, 2000);
    }
}

function formatPhoneNumber(tel) {
    let telStr = String(tel);
    
    // 숫자만 추출
    telStr = telStr.replace(/[^0-9]/g, '');
    
    // 맨 앞에 0이 없으면 추가 (한국 전화번호 형식)
    if (telStr.length > 0 && !telStr.startsWith('0')) {
        telStr = '0' + telStr;
    }
    
    // 길이에 따라 처리
    if (telStr.length === 11) {
        // 11자리: 010-1234-5678 형식
        return telStr.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3');
    } else if (telStr.length === 10) {
        // 10자리: 010-123-4567 형식
        return telStr.replace(/(\d{3})(\d{3})(\d{4})/, '$1-$2-$3');
    } else if (telStr.length === 9) {
        // 9자리: 010-123-456 형식
        return telStr.replace(/(\d{3})(\d{3})(\d{3})/, '$1-$2-$3');
    } else if (telStr.length === 8) {
        // 8자리: 010-1234 형식
        return telStr.replace(/(\d{3})(\d{4})/, '$1-$2');
    } else {
        // 그 외: 원본 반환
        return telStr;
    }
}

function formatDateTime(dateStr) {
    const date = new Date(dateStr);
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatCurrency(amount) {
    return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount);
}

// 수동 발송 모달 관련 함수들
function openManualDispatchModal() {
    const modal = document.getElementById('manualDispatchModal');
    modal.style.display = 'block';
    
    // 폼 초기화
    document.getElementById('manualDispatchForm').reset();
}

function closeManualDispatchModal() {
    const modal = document.getElementById('manualDispatchModal');
    modal.style.display = 'none';
    
    // 폼 초기화
    document.getElementById('manualDispatchForm').reset();
}

async function submitManualDispatch(event) {
    event.preventDefault();
    
    const formData = new FormData(event.target);
    const data = {
        name: formData.get('name'),
        tel: formData.get('tel'),
        product: formData.get('product'),
        days: parseInt(formData.get('days')),
        quantity: parseInt(formData.get('quantity'))
    };
    
    // 필수 필드 검증
    if (!data.name || !data.tel || !data.product || !data.days || !data.quantity) {
        alert('모든 필드를 입력해주세요.');
        return;
    }
    
    // 전화번호 형식 검증 (숫자만)
    const telRegex = /^[0-9]+$/;
    if (!telRegex.test(data.tel)) {
        alert('전화번호는 숫자만 입력해주세요.');
        return;
    }
    
    const submitBtn = document.getElementById('submitManualDispatch');
    const originalText = submitBtn.textContent;
    
    try {
        // 버튼 비활성화 및 로딩 상태
        submitBtn.disabled = true;
        submitBtn.textContent = '전송 중...';
        
        const response = await fetch('/api/admin/manual-dispatch', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('수동 발송 데이터가 성공적으로 저장되었습니다.');
            closeManualDispatchModal();
            // 데이터 새로고침
            loadData();
        } else {
            alert(`저장 실패: ${result.message}`);
        }
        
    } catch (error) {
        console.error('수동 발송 저장 실패:', error);
        alert('수동 발송 데이터 저장 중 오류가 발생했습니다.');
    } finally {
        // 버튼 상태 복구
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
    }
}

// 일별 매출 모달 관련 함수들
function openDailyRevenueModal() {
    const modal = document.getElementById('dailyRevenueModal');
    modal.style.display = 'block';
    
    // 기본 날짜 설정 (최근 7일)
    const today = new Date();
    const weekAgo = new Date(today);
    weekAgo.setDate(today.getDate() - 7);
    
    document.getElementById('endDate').value = today.toISOString().split('T')[0];
    document.getElementById('startDate').value = weekAgo.toISOString().split('T')[0];
    
    // 자동으로 데이터 로드
    loadDailyRevenue();
}

function closeDailyRevenueModal() {
    const modal = document.getElementById('dailyRevenueModal');
    modal.style.display = 'none';
}

async function loadDailyRevenue() {
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    if (!startDate || !endDate) {
        alert('시작일과 종료일을 모두 선택해주세요.');
        return;
    }
    
    if (new Date(startDate) > new Date(endDate)) {
        alert('시작일은 종료일보다 이전이어야 합니다.');
        return;
    }
    
    try {
        const response = await fetch(`/api/admin/daily-revenue?startDate=${startDate}&endDate=${endDate}`);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        
        if (data.error) {
            throw new Error(data.message || data.error);
        }
        
        displayDailyRevenue(data.dailyRevenue || []);
        
    } catch (error) {
        console.error('일별 매출 데이터 로딩 실패:', error);
        document.getElementById('revenueChart').innerHTML = 
            `<div class="chart-placeholder" style="color: #ef4444;">
                데이터 로딩에 실패했습니다.<br>
                <small>${error.message}</small>
            </div>`;
    }
}

function displayDailyRevenue(dailyRevenue) {
    const chartContainer = document.getElementById('revenueChart');
    const summaryContainer = document.getElementById('revenueSummary');
    
    if (dailyRevenue.length === 0) {
        chartContainer.innerHTML = 
            `<div class="chart-placeholder">
                선택한 기간에 매출 데이터가 없습니다.
            </div>`;
        summaryContainer.style.display = 'none';
        return;
    }
    
    // 총 매출과 총 주문 건수 계산
    const totalRevenue = dailyRevenue.reduce((sum, item) => sum + (parseInt(item.dailyRevenue) || 0), 0);
    const totalOrders = dailyRevenue.reduce((sum, item) => sum + (parseInt(item.orderCount) || 0), 0);
    
    // 요약 정보 표시
    document.getElementById('totalRevenueAmount').textContent = formatCurrency(totalRevenue);
    document.getElementById('totalOrderCount').textContent = totalOrders.toLocaleString() + '건';
    summaryContainer.style.display = 'grid';
    
    // 일별 매출 차트 생성
    const chartHTML = dailyRevenue.map(item => {
        const date = new Date(item.date);
        const formattedDate = date.toLocaleDateString('ko-KR', {
            month: 'short',
            day: 'numeric',
            weekday: 'short'
        });
        
        return `
                <div class="revenue-item">
                    <div class="revenue-date">${formattedDate}</div>
                    <div class="revenue-info">
                        <span class="revenue-amount">${formatCurrency(parseInt(item.dailyRevenue) || 0)}</span>
                        <span class="revenue-count">${parseInt(item.orderCount) || 0}건</span>
                    </div>
                </div>
        `;
    }).join('');
    
    chartContainer.innerHTML = chartHTML;
}

// 이벤트 리스너 등록
function initializeEventListeners() {
    // 수동 발송 버튼 이벤트
    const manualDispatchBtn = document.getElementById('manualDispatchBtn');
    if (manualDispatchBtn) {
        manualDispatchBtn.addEventListener('click', openManualDispatchModal);
    }
    
    // 수동 발송 모달 닫기 버튼 이벤트
    const closeManualDispatchBtn = document.getElementById('closeManualDispatchModal');
    if (closeManualDispatchBtn) {
        closeManualDispatchBtn.addEventListener('click', closeManualDispatchModal);
    }
    
    // 수동 발송 취소 버튼 이벤트
    const cancelManualDispatchBtn = document.getElementById('cancelManualDispatch');
    if (cancelManualDispatchBtn) {
        cancelManualDispatchBtn.addEventListener('click', closeManualDispatchModal);
    }
    
    // 수동 발송 폼 제출 이벤트
    const manualDispatchForm = document.getElementById('manualDispatchForm');
    if (manualDispatchForm) {
        manualDispatchForm.addEventListener('submit', submitManualDispatch);
    }
    
    // 수동 발송 모달 외부 클릭 시 닫기
    const manualDispatchModal = document.getElementById('manualDispatchModal');
    if (manualDispatchModal) {
        manualDispatchModal.addEventListener('click', function(event) {
            if (event.target === manualDispatchModal) {
                closeManualDispatchModal();
            }
        });
    }
    
    // 매출 카드 클릭 이벤트
    const revenueCard = document.getElementById('revenueCard');
    if (revenueCard) {
        revenueCard.style.cursor = 'pointer';
        revenueCard.addEventListener('click', openDailyRevenueModal);
    }
    
    // 새로고침 버튼 이벤트
    const refreshBtn = document.getElementById('refreshBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadData);
    }
    
    // 검색 박스 이벤트
    const searchBox = document.getElementById('searchBox');
    if (searchBox) {
        searchBox.addEventListener('keyup', filterTable);
    }
    
    // 모달 닫기 버튼 이벤트
    const closeModal = document.getElementById('closeModal');
    if (closeModal) {
        closeModal.addEventListener('click', closeDailyRevenueModal);
    }
    
    // 매출 조회 버튼 이벤트
    const searchRevenueBtn = document.getElementById('searchRevenueBtn');
    if (searchRevenueBtn) {
        searchRevenueBtn.addEventListener('click', loadDailyRevenue);
    }
    
    // 모달 외부 클릭 시 닫기
    const modal = document.getElementById('dailyRevenueModal');
    if (modal) {
        modal.addEventListener('click', function(event) {
            if (event.target === modal) {
                closeDailyRevenueModal();
            }
        });
    }
    
    // 로그아웃 버튼 이벤트
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', logout);
    }
    
    // eSIM URL 버튼 클릭 이벤트 (동적으로 생성되는 버튼들)
    document.addEventListener('click', function(event) {
        if (event.target.classList.contains('esim-url-btn')) {
            const productOrderId = event.target.getAttribute('data-order-id');
            copyEsimUrls(productOrderId, event.target);
        }
    });
}

function filterTable() {
    const searchTerm = document.getElementById('searchBox').value.toLowerCase();
    const filteredOrders = allOrders.filter(order => {
        return (
            order.productOrderId.toLowerCase().includes(searchTerm) ||
            order.ordererName.toLowerCase().includes(searchTerm) ||
            String(order.ordererTel).includes(searchTerm) ||
            (order.email && order.email.toLowerCase().includes(searchTerm))
        );
    });
    updateTable(filteredOrders);
}

// 페이지 로드 시 데이터 로딩 및 사용자 정보 확인
document.addEventListener('DOMContentLoaded', async function() {
    console.log('DOM 로드 완료, 데이터 로딩 시작');
    
    // 이벤트 리스너 초기화
    initializeEventListeners();
    
    // 사용자 정보 확인
    try {
        const authResponse = await fetch('/api/auth/status');
        const authData = await authResponse.json();
        
        if (authData.isAuthenticated && authData.username) {
            document.getElementById('userInfo').textContent = authData.username;
        }
    } catch (error) {
        console.error('사용자 정보 확인 실패:', error);
    }
    
    loadData();
});

// 5분마다 자동 새로고침
setInterval(loadData, 5 * 60 * 1000);

// 로그아웃 함수
async function logout() {
    if (confirm('정말 로그아웃 하시겠습니까?')) {
        try {
            const response = await fetch('/api/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });
            
            const data = await response.json();
            
            if (data.success) {
                // 로그아웃 성공 시 로그인 페이지로 이동
                window.location.href = '/login';
            } else {
                alert('로그아웃에 실패했습니다.');
            }
        } catch (error) {
            console.error('로그아웃 오류:', error);
            alert('로그아웃 중 오류가 발생했습니다.');
        }
    }
}

// 전역 함수로 노출 (디버깅용)
window.loadData = loadData;
window.logout = logout;
