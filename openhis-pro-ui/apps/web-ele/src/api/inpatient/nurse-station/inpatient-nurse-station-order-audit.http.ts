import type {
  ListNurseStationOrderAuditRequest,
  ListNurseStationOrderAuditResponse,
  RejectNurseStationOrdersRequest,
  RejectNurseStationOrdersResponse,
  VerifyNurseStationOrdersRequest,
  VerifyNurseStationOrdersResponse,
} from './inpatient-nurse-station-order-audit.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 医嘱校对 */
const BASE = '/nurseStationOrderAudit';

/** 查询多选患者的医嘱校对列表（按 Tab、长临筛选，无分页） */
export async function httpListNurseStationOrderAudit(
  body: ListNurseStationOrderAuditRequest,
): Promise<ListNurseStationOrderAuditResponse> {
  return publicRequestClient.post<ListNurseStationOrderAuditResponse>(
    `${BASE}/listNurseStationOrderAudit`,
    body,
  );
}

/** 批量校对通过（单批最多 50 条） */
export async function httpVerifyNurseStationOrders(
  body: VerifyNurseStationOrdersRequest,
): Promise<VerifyNurseStationOrdersResponse> {
  return publicRequestClient.post<VerifyNurseStationOrdersResponse>(
    `${BASE}/verifyNurseStationOrders`,
    body,
  );
}

/** 批量退回未校对医嘱（单批最多 50 条） */
export async function httpRejectNurseStationOrders(
  body: RejectNurseStationOrdersRequest,
): Promise<RejectNurseStationOrdersResponse> {
  return publicRequestClient.post<RejectNurseStationOrdersResponse>(
    `${BASE}/rejectNurseStationOrders`,
    body,
  );
}
