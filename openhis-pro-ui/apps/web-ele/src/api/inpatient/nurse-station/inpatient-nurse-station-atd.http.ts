import type {
  CancelInpatientNurseDischargeRequest,
  CancelInpatientNurseDischargeResponse,
  ClearDischargedBedRequest,
  ClearDischargedBedResponse,
  ExecuteBedOperationRequest,
  ExecuteBedOperationResponse,
  ExecuteInpatientDischargeRequest,
  ExecuteInpatientDischargeResponse,
  ListBedBoardRequest,
  ListBedBoardResponse,
  ListDischargePendingExecutionsRequest,
  ListDischargePendingExecutionsResponse,
  ListDischargePendingMedicationsRequest,
  ListDischargePendingMedicationsResponse,
  ListMissingDispenseTraceTipsRequest,
  ListMissingDispenseTraceTipsResponse,
} from './inpatient-nurse-station-atd.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 入出转床位管理 */
const BASE = '/nurseStationATDManager';

/** 查询床位列表（范围内全部启用床位，含占床患者块） */
export async function httpListBedBoard(body: ListBedBoardRequest): Promise<ListBedBoardResponse> {
  return publicRequestClient.post<ListBedBoardResponse>(`${BASE}/listBedBoard`, body);
}

/** 提交分床、换空床或互换床位 */
export async function httpExecuteBedOperation(
  body: ExecuteBedOperationRequest,
): Promise<ExecuteBedOperationResponse> {
  return publicRequestClient.post<ExecuteBedOperationResponse>(`${BASE}/executeBedOperation`, body);
}

/** 办理出院（护士站出 Tab） */
export async function httpExecuteInpatientDischarge(
  body: ExecuteInpatientDischargeRequest,
): Promise<ExecuteInpatientDischargeResponse> {
  return publicRequestClient.post<ExecuteInpatientDischargeResponse>(
    `${BASE}/executeInpatientDischarge`,
    body,
  );
}

/** 取消办理出院（待结算 → 待出院/已入科） */
export async function httpCancelInpatientNurseDischarge(
  body: CancelInpatientNurseDischargeRequest,
): Promise<CancelInpatientNurseDischargeResponse> {
  return publicRequestClient.post<CancelInpatientNurseDischargeResponse>(
    `${BASE}/cancelInpatientNurseDischarge`,
    body,
  );
}

/** 清床离院（结算完成后释床） */
export async function httpClearDischargedBed(
  body: ClearDischargedBedRequest,
): Promise<ClearDischargedBedResponse> {
  return publicRequestClient.post<ClearDischargedBedResponse>(`${BASE}/clearDischargedBed`, body);
}

/** 清床前缺追溯码软提示列表（不硬拦） */
export async function httpListMissingDispenseTraceTips(
  body: ListMissingDispenseTraceTipsRequest,
): Promise<ListMissingDispenseTraceTipsResponse> {
  return publicRequestClient.post<ListMissingDispenseTraceTipsResponse>(
    `${BASE}/listMissingDispenseTraceTips`,
    body,
  );
}

/** 出院前待执行医嘱列表 */
export async function httpListDischargePendingExecutions(
  body: ListDischargePendingExecutionsRequest,
): Promise<ListDischargePendingExecutionsResponse> {
  return publicRequestClient.post<ListDischargePendingExecutionsResponse>(
    `${BASE}/listDischargePendingExecutions`,
    body,
  );
}

/** 出院前待发退药列表 */
export async function httpListDischargePendingMedications(
  body: ListDischargePendingMedicationsRequest,
): Promise<ListDischargePendingMedicationsResponse> {
  return publicRequestClient.post<ListDischargePendingMedicationsResponse>(
    `${BASE}/listDischargePendingMedications`,
    body,
  );
}
