/**
 * 体检记录 — {@code POST /peisExamHistory/<method>}
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
  idCardNo?: string;
  packageActivityId?: IdLike;
  packageName?: string;
  paid?: boolean;
  patientId?: IdLike;
  patientName?: string;
  phone?: string;
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

export interface PageExamRecordsRequest {
  keyword?: string;
  dateFrom?: string | null;
  dateTo?: string | null;
  patientId?: IdLike | null;
  /** all / personal / group */
  groupScope?: 'all' | 'personal' | 'group' | string;
  pageNo: number;
  pageSize: number;
}

export interface PageExamRecordsResponse {
  content?: ExamHistoryRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export function listPatientExamHistoryApi(data: ListPatientExamHistoryRequest) {
  return publicRequestClient.post<ListPatientExamHistoryResponse>(
    '/peisExamHistory/listPatientExamHistory',
    data,
  );
}

export function pageExamRecordsApi(data: PageExamRecordsRequest) {
  return publicRequestClient.post<PageExamRecordsResponse>(
    '/peisExamHistory/pageExamRecords',
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
