/**
 * 住院发退药记录 — POST /api/app/inpatientPharmacyDispenseRecord/<method>
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientPharmacyDispenseRecord';

export interface LoadInpatientPharmacyDispenseRecordPageInitResponse {
  defaultDispenseDateFrom?: string;
  defaultDispenseDateTo?: string;
  pharmacyOptions?: ApiOptionItem[];
  medicationCategoryOptions?: ApiOptionItem[];
  dispenserOptions?: ApiOptionItem[];
  recordTypeOptions?: ApiOptionItem[];
}

export interface PageInpatientPharmacyDispenseRecordsParams extends PageParams {
  dispenseDateFrom: string;
  dispenseDateTo: string;
  recordTypeCode?: string;
  pharmacyLocationId?: string;
  medicationCategoryCodes?: string[];
  dispenserPractitionerId?: string;
  drugKeyword?: string;
  patientKeyword?: string;
}

export interface InpatientPharmacyDispenseRecordRow {
  dispenseId?: string;
  recordTypeCode?: string;
  recordTypeText?: string;
  dispenseTime?: string;
  pharmacyLocationId?: string;
  pharmacyLocationName?: string;
  dispenserPractitionerId?: string;
  dispenserName?: string;
  patientId?: string;
  patientName?: string;
  encounterId?: string;
  inpatientNo?: string;
  wardName?: string;
  bedName?: string;
  prescriptionNo?: string;
  medicationId?: string;
  medicationCode?: string;
  medicationName?: string;
  medicationSpec?: string;
  quantity?: number | string;
  unitCode?: string;
  unitText?: string;
  unitPrice?: number | string;
  totalAmount?: number | string;
  lotNumber?: string;
  traceNo?: string;
  orderingDepartmentId?: string;
  orderingDepartmentName?: string;
  usageText?: string;
  summaryNo?: string;
  medReqId?: string;
}

export interface PageInpatientPharmacyDispenseRecordsResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  content?: InpatientPharmacyDispenseRecordRow[];
}

export type ExportInpatientPharmacyDispenseRecordsParams = Omit<
  PageInpatientPharmacyDispenseRecordsParams,
  'pageNo' | 'pageSize'
>;

export type ExportInpatientPharmacyDispenseRecordsResponse = BinaryExportResult;

/** 加载住院发退药记录页筛选项与默认日期。 */
export async function loadInpatientPharmacyDispenseRecordPageInitApi() {
  return publicRequestClient.post<LoadInpatientPharmacyDispenseRecordPageInitResponse>(
    `${BASE}/loadInpatientPharmacyDispenseRecordPageInit`,
    {},
  );
}

/** 分页查询住院发退药记录。 */
export async function pageInpatientPharmacyDispenseRecordsApi(
  body: PageInpatientPharmacyDispenseRecordsParams,
) {
  return publicRequestClient.post<PageInpatientPharmacyDispenseRecordsResponse>(
    `${BASE}/pageInpatientPharmacyDispenseRecords`,
    body,
  );
}

/** 导出住院发退药记录 xlsx。 */
export async function exportInpatientPharmacyDispenseRecordsApi(
  body: ExportInpatientPharmacyDispenseRecordsParams,
) {
  return publicRequestClient.post<ExportInpatientPharmacyDispenseRecordsResponse>(
    `${BASE}/exportInpatientPharmacyDispenseRecords`,
    body,
  );
}
