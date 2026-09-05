import { OrderScheduler } from './schedulers/orderScheduler.js';

console.log('=== 주문 처리 1회 실행 시작 ===');

const scheduler = new OrderScheduler();

// 1회 실행 후 종료
scheduler.processOrders()
  .then(() => {
    console.log('=== 주문 처리 1회 실행 완료 ===');
    process.exit(0);
  })
  .catch((error) => {
    console.error('주문 처리 중 오류 발생:', error);
    process.exit(1);
  })
  .finally(async () => {
    // 리소스 정리
    await scheduler.cleanup();
  });
