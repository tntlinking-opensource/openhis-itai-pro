import type {
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
  RecallInpatientDischargeRequest,
  RecallInpatientDischargeResponse,
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

/** 清床离院（结算完成后释床） */
export async function httpClearDischargedBed(
  body: ClearDischargedBedRequest,
): Promise<ClearDischargedBedResponse> {
  return publicRequestClient.post<ClearDischargedBedResponse>(`${BASE}/clearDischargedBed`, body);
}

/** 出院召回（已出院结算列表） */
export async function httpRecallInpatientDischarge(
  body: RecallInpatientDischargeRequest,
): Promise<RecallInpatientDischargeResponse> {
  return publicRequestClient.post<RecallInpatientDischargeResponse>(
    `${BASE}/recallInpatientDischarge`,
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
