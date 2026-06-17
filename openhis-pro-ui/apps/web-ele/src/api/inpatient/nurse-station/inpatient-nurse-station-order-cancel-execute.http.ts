import type {
  CancelNurseStationOrderExecutionsRequest,
  CancelNurseStationOrderExecutionsResponse,
} from './inpatient-nurse-station-order-cancel-execute.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/nurseStationOrderCancelExecute';

/** 批量取消已执行医嘱计划点（单批最多 50 条 procedureId） */
export async function httpCancelNurseStationOrderExecutions(
  body: CancelNurseStationOrderExecutionsRequest,
): Promise<CancelNurseStationOrderExecutionsResponse> {
  return publicRequestClient.post<CancelNurseStationOrderExecutionsResponse>(
    `${BASE}/cancelNurseStationOrderExecutions`,
    body,
  );
}
