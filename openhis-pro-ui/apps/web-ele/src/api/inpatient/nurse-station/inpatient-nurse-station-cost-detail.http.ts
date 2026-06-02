import type {
  LoadNurseStationCostDetailPageInitResponse,
  QueryNurseStationInpatientCostDetailRequest,
  QueryNurseStationInpatientCostDetailResponse,
} from './inpatient-nurse-station-cost-detail.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 费用明细 */
const BASE = '/nurseStationCostDetail';

/** 费用明细页初始化（默认日期区间与展示模式选项） */
export async function httpLoadNurseStationCostDetailPageInit(): Promise<LoadNurseStationCostDetailPageInitResponse> {
  return publicRequestClient.post<LoadNurseStationCostDetailPageInitResponse>(
    `${BASE}/loadNurseStationCostDetailPageInit`,
    {},
  );
}

/** 按单就诊与日期区间查询住院费用明细或汇总 */
export async function httpQueryNurseStationInpatientCostDetail(
  body: QueryNurseStationInpatientCostDetailRequest,
): Promise<QueryNurseStationInpatientCostDetailResponse> {
  return publicRequestClient.post<QueryNurseStationInpatientCostDetailResponse>(
    `${BASE}/queryNurseStationInpatientCostDetail`,
    body,
  );
}
