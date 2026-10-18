import type { ApiResponse } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/** 院点 AppService 前缀随 VITE_ZXPLAT_PROJECT 变化。本页只在国基后端存在。 */
const SITE =
  String(import.meta.env.VITE_ZXPLAT_PROJECT ?? 'H22010202493').trim() || 'H22010202493';
const BASE = `/${SITE.toLowerCase()}HongqiContractResettle`;

export interface HongqiResettlePatient {
  patientId?: string;
  name?: string;
  gender?: string;
  phone?: string;
  contracts?: string;
  paymentCount?: number;
  departments?: string;
  firstVisitTime?: string;
  lastVisitTime?: string;
  medfeeSum?: number | string;
  fundSum?: number | string;
  acctSum?: number | string;
  refundName?: string;
  collectName?: string;
  progressDetail?: string;
  settleNeed?: number;
  settleDone?: number;
  registerNeed?: number;
  registerDone?: number;
  collectNeed?: number;
  collectDone?: number;
}

export interface HongqiResettleLine {
  itemName?: string;
  quantity?: number | string;
  unit?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  registration?: boolean;
}

export interface HongqiResettleBatch {
  paymentId?: string;
  encounterId?: string;
  paymentNo?: string;
  billTime?: string;
  visitTime?: string;
  encounterNo?: string;
  department?: string;
  contractName?: string;
  contractBusNo?: string;
  insutypeName?: string;
  setlId?: string;
  setlStatusName?: string;
  medfee?: number | string;
  fundPay?: number | string;
  acctPay?: number | string;
  cashPay?: number | string;
  lineTotal?: number | string;
  lines?: HongqiResettleLine[];
}

export interface HongqiResettleDetail {
  patient?: HongqiResettlePatient;
  registrationTotal?: number | string;
  clinicTotal?: number | string;
  registrationBatches?: HongqiResettleBatch[];
  clinicBatches?: HongqiResettleBatch[];
}

export function listHongqiResettlePatientsApi(keyword: string) {
  return publicRequestClient.post<ApiResponse<HongqiResettlePatient[]>>(`${BASE}/listPatients`, {
    keyword,
  });
}

export function loadHongqiResettleDetailApi(patientId: string) {
  return publicRequestClient.post<ApiResponse<HongqiResettleDetail>>(`${BASE}/loadDetail`, {
    patientId,
  });
}

export interface HongqiResettleRevokeResult {
  contractBusNo?: string;
  infNo?: string;
  revokedCount?: number;
  messages?: string[];
}

export function revokeHongqiSettleApi(paymentId: string) {
  return publicRequestClient.post<ApiResponse<HongqiResettleRevokeResult>>(`${BASE}/revokeSettle`, {
    paymentId,
  });
}

function postGuoji(path: string, encounterId: string, paymentId: string, contractBusNo: string) {
  return publicRequestClient.post<ApiResponse<HongqiResettleRevokeResult>>(`${BASE}/${path}`, {
    encounterId,
    paymentId,
    contractBusNo,
  });
}

export function registerGuojiApi(encounterId: string, paymentId: string, contractBusNo: string) {
  return postGuoji('registerGuoji', encounterId, paymentId, contractBusNo);
}

export function chargeGuojiRegistrationApi(encounterId: string, paymentId: string, contractBusNo: string) {
  return postGuoji('chargeGuojiRegistration', encounterId, paymentId, contractBusNo);
}

export function chargeGuojiClinicApi(encounterId: string, paymentId: string, contractBusNo: string) {
  return postGuoji('chargeGuojiClinic', encounterId, paymentId, contractBusNo);
}

export function revokeHongqiRegistrationApi(encounterId: string, contractBusNo: string) {
  return publicRequestClient.post<ApiResponse<HongqiResettleRevokeResult>>(
    `${BASE}/revokeRegistration`,
    {
      encounterId,
      contractBusNo,
    },
  );
}
