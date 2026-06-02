import type {
  ExecuteNurseStationOrdersRequest,
  ExecuteNurseStationOrdersResponse,
  ListNurseStationOrderExecuteRequest,
  ListNurseStationOrderExecuteResponse,
  SkipNurseStationOrderSlotsRequest,
  SkipNurseStationOrderSlotsResponse,
} from './inpatient-nurse-station-order-execute.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 医嘱执行 */
const BASE = '/nurseStationOrderExecute';

/** 查询多选患者的医嘱执行列表（按 Tab、长临、截止时间筛选，无分页） */
export async function httpListNurseStationOrderExecute(
  body: ListNurseStationOrderExecuteRequest,
): Promise<ListNurseStationOrderExecuteResponse> {
  return publicRequestClient.post<ListNurseStationOrderExecuteResponse>(
    `${BASE}/listNurseStationOrderExecute`,
    body,
  );
}

/** 批量执行医嘱计划点（单批最多 50 槽位） */
export async function httpExecuteNurseStationOrders(
  body: ExecuteNurseStationOrdersRequest,
): Promise<ExecuteNurseStationOrdersResponse> {
  return publicRequestClient.post<ExecuteNurseStationOrdersResponse>(
    `${BASE}/executeNurseStationOrders`,
    body,
  );
}

/** 批量登记计划点不执行（单批最多 50 槽位） */
export async function httpSkipNurseStationOrderSlots(
  body: SkipNurseStationOrderSlotsRequest,
): Promise<SkipNurseStationOrderSlotsResponse> {
  return publicRequestClient.post<SkipNurseStationOrderSlotsResponse>(
    `${BASE}/skipNurseStationOrderSlots`,
    body,
  );
}
