// 실제 배포 시 cwd 값을 서버의 실제 배포 경로로 변경하세요.
module.exports = {
  apps: [
    {
      name: "ringtalk-esim-server",            // 프로세스 이름
      script: "npm",                           // npm 명령어 실행
      args: "run start",                       // 운영 모드 실행
      cwd: "/path/to/esim-server",             // Linux 경로 (배포 환경에 맞게 수정)
      interpreter: "none",                     // pm2가 node 대신 npm 실행
      env: {
        NODE_ENV: "production",
        PORT: 3000                            // 포트 명시적 설정
      }
    },
    {
      name: "ringtalk-esim-scheduler",         // 스케줄러 프로세스
      script: "npm",
      args: "run scheduler",
      cwd: "/path/to/esim-server",
      interpreter: "none",
      env: {
        NODE_ENV: "production"
      }
    }
  ]
}