/**
 * 事前提醒主路径门禁 — {@code POST /api/app/insuranceDetailAuditGate/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

import type { AuditAnalyzeResult, AuditFeedback3103Params } from './detail-audit-try';

const BASE = '/insuranceDetailAuditGate';

export interface DetailAuditOpGateParams {
  contractBusNo?: string;
  contractId?: number | string;
  encounterId: number | string;
  chargeItemIds?: Array<number | string>;
  trigScen: string;
  syscode?: string;
  medinsType?: string;
  medinsLv?: string;
  brdy?: string;
  psnNo?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  mdtrtCertType?: string;
  mdtrtCertNo?: string;
}

export interface DetailAuditIpGateParams {
  contractBusNo?: string;
  contractId?: number | string;
  encounterId: number | string;
  chargeItemIds?: Array<number | string>;
  settleMode?: string;
  trigScen?: string;
  syscode?: string;
  medinsType?: string;
  medinsLv?: string;
  psnNo?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  mdtrtCertType?: string;
  mdtrtCertNo?: string;
}

export interface DetailAuditGateResult {
  skipped?: boolean;
  skipReason?: string;
  mdtrtId?: string;
  trigScen?: string;
  contractBusNo?: string;
  analyzeResult?: AuditAnalyzeResult;
}

/** 门禁 AppService POST（长超时）。 */
async function postGate<T>(method: string, body: unknown): Promise<T> {
  const res = await requestClient.post<ApiResponse<T>>(
    `${BASE}/${method}`,
    body,
    chsLongRequestConfig(),
  );
  return res.data as T;
}

export function analyzeOpGateApi(params: DetailAuditOpGateParams) {
  return postGate<DetailAuditGateResult>('analyzeOpGate', params);
}

export function analyzeIpGateApi(params: DetailAuditIpGateParams) {
  return postGate<DetailAuditGateResult>('analyzeIpGate', params);
}

export function gateFeedback3103Api(params: AuditFeedback3103Params) {
  return postGate<void>('feedback3103', params);
}

export function gateHasViolations(result?: DetailAuditGateResult | null): boolean {
  if (!result || result.skipped) {
    return false;
  }
  const list = result.analyzeResult?.violations;
  return Boolean(list && list.length > 0);
}
