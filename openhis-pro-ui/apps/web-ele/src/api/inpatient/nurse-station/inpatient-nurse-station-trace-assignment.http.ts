import type {
  ListPatientTraceAssignmentGroupsRequest,
  ListPatientTraceAssignmentGroupsResponse,
  PagePatientsAssignedTraceAssignmentRequest,
  PagePatientsAssignedTraceAssignmentResponse,
  PagePatientsPendingTraceAssignmentRequest,
  PagePatientsPendingTraceAssignmentResponse,
  ResolveTraceCodesForPatientRequest,
  ResolveTraceCodesForPatientResponse,
  RevisePatientTraceAssignmentRequest,
  RevisePatientTraceAssignmentResponse,
  SavePatientTraceAssignmentRequest,
  SavePatientTraceAssignmentResponse,
} from './inpatient-nurse-station-trace-assignment.types';

import { publicRequestClient } from '#/api/request';

import {
  mockListPatientTraceAssignmentGroups,
  mockPagePatientsAssignedTraceAssignment,
  mockPagePatientsPendingTraceAssignment,
  mockResolveTraceCodesForPatient,
  mockRevisePatientTraceAssignment,
  mockSavePatientTraceAssignment,
} from './inpatient-nurse-station-trace-assignment.mock';

/** 住院追溯码分配（发药管理；后端路径仍为 nurseStationTraceAssignment） */
const BASE = '/nurseStationTraceAssignment';

/** 是否走前端内存 mock（开发联调前演示） */
function useNurseTraceAssignmentMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_NURSE_TRACE_ASSIGNMENT === 'true';
}

/** 分页查询待追溯分配患者队列 */
export async function httpPagePatientsPendingTraceAssignment(
  body: PagePatientsPendingTraceAssignmentRequest,
): Promise<PagePatientsPendingTraceAssignmentResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockPagePatientsPendingTraceAssignment(body);
  }
  return publicRequestClient.post<PagePatientsPendingTraceAssignmentResponse>(
    `${BASE}/pagePatientsPendingTraceAssignment`,
    body,
  );
}

/** 分页查询已分配、可改码患者队列（接口待后端实现） */
export async function httpPagePatientsAssignedTraceAssignment(
  body: PagePatientsAssignedTraceAssignmentRequest,
): Promise<PagePatientsAssignedTraceAssignmentResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockPagePatientsAssignedTraceAssignment(body);
  }
  return publicRequestClient.post<PagePatientsAssignedTraceAssignmentResponse>(
    `${BASE}/pagePatientsAssignedTraceAssignment`,
    body,
  );
}

/** 加载患者药批汇总待分配/已分配行 */
export async function httpListPatientTraceAssignmentGroups(
  body: ListPatientTraceAssignmentGroupsRequest,
): Promise<ListPatientTraceAssignmentGroupsResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockListPatientTraceAssignmentGroups(body);
  }
  return publicRequestClient.post<ListPatientTraceAssignmentGroupsResponse>(
    `${BASE}/listPatientTraceAssignmentGroups`,
    body,
  );
}

/** 批量解析追溯码（只读预览） */
export async function httpResolveTraceCodesForPatient(
  body: ResolveTraceCodesForPatientRequest,
): Promise<ResolveTraceCodesForPatientResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockResolveTraceCodesForPatient(body);
  }
  return publicRequestClient.post<ResolveTraceCodesForPatientResponse>(
    `${BASE}/resolveTraceCodesForPatient`,
    body,
  );
}

/** 保存患者追溯码分配 */
export async function httpSavePatientTraceAssignment(
  body: SavePatientTraceAssignmentRequest,
): Promise<SavePatientTraceAssignmentResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockSavePatientTraceAssignment(body);
  }
  return publicRequestClient.post<SavePatientTraceAssignmentResponse>(
    `${BASE}/savePatientTraceAssignment`,
    body,
  );
}

/** 更正已分配追溯码（分配错误改码；接口待后端实现） */
export async function httpRevisePatientTraceAssignment(
  body: RevisePatientTraceAssignmentRequest,
): Promise<RevisePatientTraceAssignmentResponse> {
  if (useNurseTraceAssignmentMock()) {
    return mockRevisePatientTraceAssignment(body);
  }
  return publicRequestClient.post<RevisePatientTraceAssignmentResponse>(
    `${BASE}/revisePatientTraceAssignment`,
    body,
  );
}
