import { OrderScheduler } from './schedulers/orderScheduler.js';

console.log('=== 주문 스케줄러 시작 ===');

const scheduler = new OrderScheduler();

// 프로세스 종료 시 정리 작업
process.on('SIGINT', async () => {
  console.log('\n스케줄러 종료 중...');
  await scheduler.cleanup();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('\n스케줄러 종료 중...');
  await scheduler.cleanup();
  process.exit(0);
});

// 예외 처리
process.on('uncaughtException', (error) => {
  console.error('예상치 못한 오류:', error);
  scheduler.cleanup().then(() => {
    process.exit(1);
  });
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('처리되지 않은 Promise 거부:', reason);
  console.error('Promise:', promise);
});

// 스케줄러 시작
scheduler.start();

console.log('스케줄러가 백그라운드에서 실행 중입니다. 종료하려면 Ctrl+C를 누르세요.');
