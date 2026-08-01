import type {
  CancelMedicationSummaryRequest,
  CancelMedicationSummaryResponse,
  CreateMedicationSummaryRequest,
  CreateMedicationSummaryResponse,
  GetMedicationSummarySlipDetailRequest,
  GetMedicationSummarySlipDetailResponse,
  GetMedicationSummarySlipPatientDetailRequest,
  GetMedicationSummarySlipPatientDetailResponse,
  PageMedicationSummarySlipHeadersRequest,
  PageMedicationSummarySlipHeadersResponse,
  PagePendingDispenseSummaryLinesRequest,
  PagePendingDispenseSummaryLinesResponse,
  SubmitMedicationSummaryRequest,
  SubmitMedicationSummaryResponse,
} from './inpatient-nurse-station-dispense-summary.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 汇总领药 */
const BASE = '/nurseStationDispenseSummary';

/** 分页查询待汇总明细（按就诊/医嘱/时间点分组） */
export async function httpPagePendingDispenseSummaryLines(
  body: PagePendingDispenseSummaryLinesRequest,
): Promise<PagePendingDispenseSummaryLinesResponse> {
  return publicRequestClient.post<PagePendingDispenseSummaryLinesResponse>(
    `${BASE}/pagePendingDispenseSummaryLines`,
    body,
  );
}

/** 分页查询已生成汇总单头 */
export async function httpPageMedicationSummarySlipHeaders(
  body: PageMedicationSummarySlipHeadersRequest,
): Promise<PageMedicationSummarySlipHeadersResponse> {
  return publicRequestClient.post<PageMedicationSummarySlipHeadersResponse>(
    `${BASE}/pageMedicationSummarySlipHeaders`,
    body,
  );
}

/** 查询汇总单合并明细（不含批号） */
export async function httpGetMedicationSummarySlipDetail(
  body: GetMedicationSummarySlipDetailRequest,
): Promise<GetMedicationSummarySlipDetailResponse> {
  return publicRequestClient.post<GetMedicationSummarySlipDetailResponse>(
    `${BASE}/getMedicationSummarySlipDetail`,
    body,
  );
}

/** 查询汇总单摆药明细（按患者分组，同 medReqId 合并） */
export async function httpGetMedicationSummarySlipPatientDetail(
  body: GetMedicationSummarySlipPatientDetailRequest,
): Promise<GetMedicationSummarySlipPatientDetailResponse> {
  return publicRequestClient.post<GetMedicationSummarySlipPatientDetailResponse>(
    `${BASE}/getMedicationSummarySlipPatientDetail`,
    body,
  );
}

/** 汇总保存（按药房拆单，同事务整批） */
export async function httpCreateMedicationSummary(
  body: CreateMedicationSummaryRequest,
): Promise<CreateMedicationSummaryResponse> {
  return publicRequestClient.post<CreateMedicationSummaryResponse>(
    `${BASE}/createMedicationSummary`,
    body,
  );
}

/** 发送汇总领药单至药房 */
export async function httpSubmitMedicationSummary(
  body: SubmitMedicationSummaryRequest,
): Promise<SubmitMedicationSummaryResponse> {
  return publicRequestClient.post<SubmitMedicationSummaryResponse>(
    `${BASE}/submitMedicationSummary`,
    body,
  );
}

/** 撤销待发送汇总领药单 */
export async function httpCancelMedicationSummary(
  body: CancelMedicationSummaryRequest,
): Promise<CancelMedicationSummaryResponse> {
  return publicRequestClient.post<CancelMedicationSummaryResponse>(
    `${BASE}/cancelMedicationSummary`,
    body,
  );
}
