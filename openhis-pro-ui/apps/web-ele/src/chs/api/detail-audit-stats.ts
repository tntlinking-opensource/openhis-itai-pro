/**
 * 事前提醒统计研讨 — {@code POST /api/app/insuranceDetailAuditStats/<method>}
 */
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insuranceDetailAuditStats';

export interface DetailAuditStatsPageParams extends PageParams {
  createdFrom: string;
  createdTo: string;
  drCodg?: string;
  drNameKeyword?: string;
  deptCodg?: string;
  trigScen?: string;
  sevDeg?: string;
  dspoWay?: string;
  keyword?: string;
}

export interface DetailAuditStatsHitRow {
  jrId?: string;
  createdAt?: string;
  drCodg?: string;
  drName?: string;
  deptCodg?: string;
  deptName?: string;
  mdtrtId?: string;
  patnId?: string;
  ruleId?: string;
  ruleName?: string;
  sevDeg?: string;
  volaCont?: string;
  volaAmt?: number | string;
  trigScen?: string;
  contractBusNo?: string;
  latestDspoWay?: string;
}

export interface DetailAuditStatsLineRow {
  jrdId?: string;
  jrId?: string;
  rxId?: string;
  volaItemType?: string;
  volaAmt?: number | string;
  mdtrtId?: string;
}

export interface DetailAuditStatsPageResult {
  page?: {
    content?: DetailAuditStatsHitRow[];
    totalCount?: number;
    pageNo?: number;
    pageSize?: number;
  };
  hitCount?: number;
  mdtrtCount?: number;
  doctorCount?: number;
  volaAmtSum?: number | string;
}

export async function pageDetailAuditStatsApi(params: DetailAuditStatsPageParams) {
  const res = await requestClient.post<ApiResponse<DetailAuditStatsPageResult>>(
    `${BASE}/pageHits`,
    params,
  );
  const data = res.data ?? {};
  const parsed = parsePagedRowsFromApiData<DetailAuditStatsHitRow>(data.page ?? data);
  return {
    ...parsed,
    hitCount: data.hitCount ?? 0,
    mdtrtCount: data.mdtrtCount ?? 0,
    doctorCount: data.doctorCount ?? 0,
    volaAmtSum: data.volaAmtSum ?? 0,
  };
}

export async function listDetailAuditHitLinesApi(jrId: string) {
  const res = await requestClient.post<ApiResponse<DetailAuditStatsLineRow[]>>(
    `${BASE}/listHitLines`,
    { jrId },
  );
  return res.data ?? [];
}
