/**
 * 인증 미들웨어
 * 관리자 로그인 상태를 확인합니다.
 */

export const requireAuth = (req, res, next) => {
  // 세션에서 로그인 상태 확인
  if (req.session && req.session.isAuthenticated) {
    return next();
  }
  
  // 로그인되지 않은 경우 로그인 페이지로 리다이렉트
  res.redirect('/login');
};

/**
 * 로그인된 사용자가 로그인 페이지에 접근하는 경우 대시보드로 리다이렉트
 */
export const redirectIfAuthenticated = (req, res, next) => {
  if (req.session && req.session.isAuthenticated) {
    return res.redirect('/');
  }
  next();
};

