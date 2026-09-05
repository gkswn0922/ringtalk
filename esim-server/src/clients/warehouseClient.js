import axios from "axios";
import { config } from "../config/index.js";
import { generateWarehouseSignature } from "../utils/signatures.js";

function buildWarehouseHeaders(bodyString) {
  const signature = generateWarehouseSignature(bodyString);
  return {
    "Content-Type": "application/json",
    "Customer-Code": config.warehouse.customerCode,
    "Customer-Auth": config.warehouse.customerAuth,
    "Signature": signature,
  };
}

export async function submitEsimOrder(orderPayload) {
  if (!config.warehouse.baseURL) throw new Error("Missing WAREHOUSE_BASE_URL");
  const bodyString = JSON.stringify(orderPayload);
  const headers = buildWarehouseHeaders(bodyString);
  // 실제 엔드포인트 경로는 파트너사 문서 기준으로 수정 필요
  const url = `${config.warehouse.baseURL}/api/esim/order/submit`;
  const { data } = await axios.post(url, orderPayload, { headers, timeout: 15000 });
  return data;
}


