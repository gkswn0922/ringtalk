import crypto from "crypto";

// Warehouse: SHA-1 signature helper
// 관례: 요청 본문 문자열 또는 특정 필드 조합을 SHA-1
export function generateWarehouseSignature(payloadString) {
  return crypto.createHash("sha1").update(payloadString, "utf8").digest("hex");
}

// RSP+: MD5 signature helper (AppId+TransId+Timestamp+AppSecret)
export function generateRspSignature(appId, transId, timestamp, appSecret) {
  const s = `${appId}${transId}${timestamp}${appSecret}`;
  return crypto.createHash("md5").update(s, "utf8").digest("hex");
}


