import type {
  DispenseSummaryMedicationsRequest,
  DispenseSummaryMedicationsResponse,
  GetMedicationSummaryDispenseDetailRequest,
  GetMedicationSummaryDispenseDetailResponse,
  PagePendingMedicationSummarySlipsRequest,
  PagePendingMedicationSummarySlipsResponse,
  RejectSummaryDispenseRequest,
  RejectSummaryDispenseResponse,
} from './inpatient-pharmacy-summary-dispense.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientPharmacySummaryDispense';

/** IF-IPS-01：分页查询待核发汇总单头（APPROVAL + type 27） */
export async function httpPagePendingMedicationSummarySlips(
  body: PagePendingMedicationSummarySlipsRequest,
): Promise<PagePendingMedicationSummarySlipsResponse> {
  return publicRequestClient.post<PagePendingMedicationSummarySlipsResponse>(
    `${BASE}/pagePendingMedicationSummarySlips`,
    body,
  );
}

/** IF-IPS-02：查询汇总单详情（mergedLines + dispenseItems） */
export async function httpGetMedicationSummaryDispenseDetail(
  body: GetMedicationSummaryDispenseDetailRequest,
): Promise<GetMedicationSummaryDispenseDetailResponse> {
  return publicRequestClient.post<GetMedicationSummaryDispenseDetailResponse>(
    `${BASE}/getMedicationSummaryDispenseDetail`,
    body,
  );
}

/** IF-IPS-03：批量汇总核发（扣库 + complete + Delivery + AGREE + 追溯） */
export async function httpDispenseSummaryMedications(
  body: DispenseSummaryMedicationsRequest,
): Promise<DispenseSummaryMedicationsResponse> {
  return publicRequestClient.post<DispenseSummaryMedicationsResponse>(
    `${BASE}/dispenseSummaryMedications`,
    body,
  );
}

/** IF-IPS-04：批量汇总驳回 */
export async function httpRejectSummaryDispense(
  body: RejectSummaryDispenseRequest,
): Promise<RejectSummaryDispenseResponse> {
  return publicRequestClient.post<RejectSummaryDispenseResponse>(
    `${BASE}/rejectSummaryDispense`,
    body,
  );
}
