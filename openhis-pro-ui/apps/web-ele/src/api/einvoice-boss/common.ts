/**
 * 博思电子票据 — 通用票开具 API（P3）
 */
import { publicRequestClient } from '#/api/request';

const COMMON_BASE = '/einvoiceBossCommon';

export interface CommonLineRequest {
  chargeCode?: string;
  chargeName?: string;
  amount?: number;
  quantity?: number;
  unit?: string;
}

export interface IssueCommonRequest {
  commonBusType?: string;
  payer?: string;
  payerPhone?: string;
  payerIdCard?: string;
  payerType?: string;
  payerSex?: string;
  payerAge?: string;
  remark?: string;
  operatorName?: string;
  totalAmt?: number;
  lines?: CommonLineRequest[];
}

export interface IssueCommonResponse {
  documentId?: string;
  busNo?: string;
  billNo?: string;
  billBatchCode?: string;
  pictureUrl?: string;
  pictureNetUrl?: string;
  success?: boolean;
  message?: string;
}

export function issueCommonApi(request: IssueCommonRequest) {
  return publicRequestClient.post<IssueCommonResponse>(`${COMMON_BASE}/issueCommon`, request);
}
