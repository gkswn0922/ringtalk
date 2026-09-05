import { config } from "../config/index.js";

export function ipWhitelistMiddleware(req, res, next) {
  const whitelist = config.ipWhitelist;
  if (!whitelist || whitelist.length === 0) return next();
  const requesterIp = (req.headers["x-forwarded-for"] || req.socket.remoteAddress || "").toString();
  const allowed = whitelist.some(w => requesterIp.includes(w));
  if (!allowed) {
    return res.status(403).json({ message: "Forbidden: IP not whitelisted" });
  }
  next();
}

// 파트너사 콜백 보안 검증용(옵션): 필요한 경우 서명/시크릿 검증 로직을 추가
export function requireJsonContent(req, res, next) {
  if (!req.is("application/json")) {
    return res.status(415).json({ message: "Unsupported Media Type: application/json required" });
  }
  next();
}


