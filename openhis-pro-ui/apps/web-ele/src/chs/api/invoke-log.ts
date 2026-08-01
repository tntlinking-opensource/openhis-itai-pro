/**
 * 医保调用日志 — 对接 {@code POST /api/app/insuranceInvokeLog/<method>}
 */
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insuranceInvokeLog';

export interface QueryInvokeLogPageParams extends PageParams {
  createdFrom: string;
  createdTo: string;
  infno?: string;
  /** true=成功；false=失败 */
  success?: boolean;
  msgid?: string;
  contractBusNo?: string;
  encounterBusNo?: string;
  patientName?: string;
  encounterId?: number | string;
  chrgBchno?: string;
  paymentDraftId?: number | string;
  settlementRecordId?: number | string;
  errMsgKeyword?: string;
}

export interface InvokeLogRow {
  id?: number | string;
  createdAt?: string;
  infno?: string;
  infname?: string;
  msgid?: string;
  infcode?: string;
  success?: boolean;
  errMsg?: string;
  durationMs?: number;
  contractId?: number | string;
  contractBusNo?: string;
  encounterId?: number | string;
  encounterBusNo?: string;
  patientName?: string;
  chrgBchno?: string;
  paymentDraftId?: number | string;
  preSettleId?: number | string;
  settlementRecordId?: number | string;
  requestPreview?: string;
  responsePreview?: string;
}

export interface InvokeLogDetail extends InvokeLogRow {
  requestJson?: string;
  responseJson?: string;
}

export async function queryInvokeLogPageApi(params: QueryInvokeLogPageParams) {
  return requestClient.post<ApiResponse<unknown>>(`${BASE}/queryInvokeLogPage`, params);
}

export async function pageInvokeLogApi(params: QueryInvokeLogPageParams) {
  const res = await queryInvokeLogPageApi(params);
  return parsePagedRowsFromApiData<InvokeLogRow>(res.data);
}

export async function getInvokeLogDetailApi(id: number | string): Promise<InvokeLogDetail> {
  const res = await requestClient.post<ApiResponse<InvokeLogDetail>>(`${BASE}/getInvokeLogDetail`, {
    id,
  });
  return res.data ?? {};
}
