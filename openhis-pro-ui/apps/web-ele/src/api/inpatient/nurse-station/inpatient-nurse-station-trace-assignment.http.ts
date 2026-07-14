import type {
  ListPatientTraceAssignmentGroupsRequest,
  ListPatientTraceAssignmentGroupsResponse,
  PagePatientsPendingTraceAssignmentRequest,
  PagePatientsPendingTraceAssignmentResponse,
  ResolveTraceCodesForPatientRequest,
  ResolveTraceCodesForPatientResponse,
  SavePatientTraceAssignmentRequest,
  SavePatientTraceAssignmentResponse,
} from './inpatient-nurse-station-trace-assignment.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 追溯码分配 */
const BASE = '/nurseStationTraceAssignment';

/** 分页查询待追溯分配患者队列 */
export async function httpPagePatientsPendingTraceAssignment(
  body: PagePatientsPendingTraceAssignmentRequest,
): Promise<PagePatientsPendingTraceAssignmentResponse> {
  return publicRequestClient.post<PagePatientsPendingTraceAssignmentResponse>(
    `${BASE}/pagePatientsPendingTraceAssignment`,
    body,
  );
}

/** 加载患者药批汇总待分配行 */
export async function httpListPatientTraceAssignmentGroups(
  body: ListPatientTraceAssignmentGroupsRequest,
): Promise<ListPatientTraceAssignmentGroupsResponse> {
  return publicRequestClient.post<ListPatientTraceAssignmentGroupsResponse>(
    `${BASE}/listPatientTraceAssignmentGroups`,
    body,
  );
}

/** 批量解析追溯码（只读预览） */
export async function httpResolveTraceCodesForPatient(
  body: ResolveTraceCodesForPatientRequest,
): Promise<ResolveTraceCodesForPatientResponse> {
  return publicRequestClient.post<ResolveTraceCodesForPatientResponse>(
    `${BASE}/resolveTraceCodesForPatient`,
    body,
  );
}

/** 保存患者追溯码分配 */
export async function httpSavePatientTraceAssignment(
  body: SavePatientTraceAssignmentRequest,
): Promise<SavePatientTraceAssignmentResponse> {
  return publicRequestClient.post<SavePatientTraceAssignmentResponse>(
    `${BASE}/savePatientTraceAssignment`,
    body,
  );
}
