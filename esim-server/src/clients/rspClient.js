import axios from "axios";
import { config } from "../config/index.js";
import { generateRspSignature } from "../utils/signatures.js";

function buildRspHeaders(transId, timestamp) {
  const signature = generateRspSignature(
    config.rsp.appId,
    transId,
    timestamp,
    config.rsp.appSecret
  );
  return {
    "Content-Type": "application/json",
    "App-Id": config.rsp.appId,
    "Trans-Id": transId,
    "Timestamp": timestamp,
    "Signature": signature,
  };
}

export async function redeemCoupon(redeemPayload) {
  if (!config.rsp.baseURL) throw new Error("Missing RSP_BASE_URL");
  const transId = redeemPayload?.transId || `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const timestamp = `${Math.floor(Date.now() / 1000)}`;
  const headers = buildRspHeaders(transId, timestamp);
  // 실제 엔드포인트는 문서 기준으로 수정 필요
  const url = `${config.rsp.baseURL}/api/coupon/redeem`;
  const { data } = await axios.post(url, { ...redeemPayload, transId, timestamp }, { headers, timeout: 15000 });
  return data;
}

export async function queryEsimStatusUsage(queryPayload) {
  if (!config.rsp.baseURL) throw new Error("Missing RSP_BASE_URL");
  const transId = queryPayload?.transId || `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const timestamp = `${Math.floor(Date.now() / 1000)}`;
  const headers = buildRspHeaders(transId, timestamp);
  // 실제 엔드포인트는 문서 기준으로 수정 필요
  const url = `${config.rsp.baseURL}/api/esim/status-usage`;
  const { data } = await axios.post(url, { ...queryPayload, transId, timestamp }, { headers, timeout: 15000 });
  return data;
}


