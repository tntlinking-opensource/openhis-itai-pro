/**
 * 报告打印 API（AS-PEIS-REP-001）。
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface PeisReportObservationLineDto {
  itemName?: string;
  refRangeText?: string;
  resultText?: string;
  unitText?: string;
}

export interface PeisReportPrintSlipDto {
  ageText?: string;
  busNo?: string;
  checkDateText?: string;
  conclusionText?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  genderEnumText?: string;
  hospitalName?: string;
  observationLines?: PeisReportObservationLineDto[];
  patientId?: IdLike;
  patientName?: string;
  scenarioCode?: string;
  summaryText?: string;
}

export interface PrintableReportRowDto {
  busNo?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  finalizedAt?: string;
  patientId?: IdLike;
  patientName?: string;
  pdfStorageKey?: string;
  printCount?: number;
}

export interface SearchPrintableRequest {
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  orgId?: IdLike;
  pageNo: number;
  pageSize: number;
}

export interface SearchPrintableResponse {
  records: PrintableReportRowDto[];
  total: number | string;
}

export interface PreviewReportResponse {
  ageText?: string;
  busNo?: string;
  checkDateText?: string;
  conclusionText?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  genderEnumText?: string;
  hospitalName?: string;
  observationLines?: PeisReportObservationLineDto[];
  patientName?: string;
  previewHint?: string;
  scenarioCode?: string;
  summaryText?: string;
}

export interface ConfirmPrintResponse {
  accepted?: boolean;
  diagnosticReportId?: IdLike;
  message?: string;
  printCount?: number;
}

export interface GradReportRowDto {
  busNo?: string;
  calendarYear?: number;
  createdAt?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  examKind?: string;
  finalReport?: boolean;
  groupFlag?: boolean;
  packageActivityId?: IdLike;
  packageName?: string;
  statusEnum?: string;
}

export interface ListGradReportsRequest {
  patientId?: IdLike;
  studentNo?: string;
}

export interface ListGradReportsResponse {
  patientId?: IdLike;
  rows?: GradReportRowDto[];
  studentNo?: string;
}

/** 可打印列表（REP-01） */
export function httpSearchPrintable(data: SearchPrintableRequest) {
  return publicRequestClient.post<SearchPrintableResponse>(
    '/peisReportPrint/searchPrintable',
    data,
  );
}

/** 预览报告（REP-02） */
export function httpPreviewReport(data: { encounterId: IdLike }) {
  return publicRequestClient.post<PreviewReportResponse>('/peisReportPrint/previewReport', data);
}

/** 加载体检报告打印 slip（REP-02 扩展） */
export function httpLoadReportPrintSlip(data: { encounterId: IdLike }) {
  return publicRequestClient.post<PeisReportPrintSlipDto>(
    '/peisReportPrint/loadReportPrintSlip',
    data,
  );
}

/** 确认打印（REP-03） */
export function httpConfirmPrint(data: { encounterId: IdLike }) {
  return publicRequestClient.post<ConfirmPrintResponse>('/peisReportPrint/confirmPrint', data);
}

/** 毕业合打：按学号列历年（REP-05） */
export function listGradReportsByStudentNoApi(data: ListGradReportsRequest) {
  return publicRequestClient.post<ListGradReportsResponse>(
    '/peisReportPrint/listGradReportsByStudentNo',
    data,
  );
}

/** 毕业合打确认（REP-05） */
export function printGradCombinedApi(data: { encounterIds: IdLike[] }) {
  return publicRequestClient.post<{
    accepted?: boolean;
    encounterIds?: IdLike[];
    message?: string;
  }>('/peisReportPrint/printGradCombined', data);
}
