/**
 * 学生/教职工/客户体检历史 — {@code POST /peisExamHistory/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface ExamHistoryRowDto {
  busNo?: string;
  calendarYear?: number;
  cancelable?: boolean;
  createdAt?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  examKind?: string;
  finalReport?: boolean;
  groupFlag?: boolean;
  packageActivityId?: IdLike;
  packageName?: string;
  paid?: boolean;
  statusEnum?: string;
  statusText?: string;
}

export interface ListPatientExamHistoryRequest {
  employeeNo?: string;
  examKinds?: string[];
  onlyFinal?: boolean;
  patientId?: IdLike;
  studentNo?: string;
}

export interface ListPatientExamHistoryResponse {
  employeeNo?: string;
  patientId?: IdLike;
  rows?: ExamHistoryRowDto[];
  studentNo?: string;
  subjectName?: string;
  subjectType?: string;
}

export function listPatientExamHistoryApi(data: ListPatientExamHistoryRequest) {
  return publicRequestClient.post<ListPatientExamHistoryResponse>(
    '/peisExamHistory/listPatientExamHistory',
    data,
  );
}

export function cancelPhysicalExamApi(data: { encounterId: IdLike }) {
  return publicRequestClient.post<{ cancelled?: boolean; encounterId?: IdLike }>(
    '/peisExamHistory/cancelPhysicalExam',
    data,
  );
}

export const EXAM_KIND_LABEL: Record<string, string> = {
  personal: '个人体检',
  group: '团体体检',
  freshman: '新生体检',
  annual: '在校年检',
  staff: '教职工体检',
  other: '其他',
};
