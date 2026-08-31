/**
 * 门诊发退药记录 — POST /api/app/outpatientPharmacyDispenseRecord/<method>
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';

const BASE = '/outpatientPharmacyDispenseRecord';

export interface LoadOutpatientPharmacyDispenseRecordPageInitResponse {
  defaultDispenseDateFrom?: string;
  defaultDispenseDateTo?: string;
  pharmacyOptions?: ApiOptionItem[];
  medicationCategoryOptions?: ApiOptionItem[];
  dispenserOptions?: ApiOptionItem[];
  recordTypeOptions?: ApiOptionItem[];
}

export interface PageOutpatientPharmacyDispenseRecordsParams extends PageParams {
  dispenseDateFrom: string;
  dispenseDateTo: string;
  recordTypeCode?: string;
  pharmacyLocationId?: string;
  medicationCategoryCodes?: string[];
  dispenserPractitionerId?: string;
  drugKeyword?: string;
  patientKeyword?: string;
}

export interface OutpatientPharmacyDispenseRecordRow {
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
  encounterBusNo?: string;
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
  manufacturerText?: string;
  summaryNo?: string;
  medReqId?: string;
}

export interface PageOutpatientPharmacyDispenseRecordsResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  content?: OutpatientPharmacyDispenseRecordRow[];
}

export type ExportOutpatientPharmacyDispenseRecordsParams = Omit<
  PageOutpatientPharmacyDispenseRecordsParams,
  'pageNo' | 'pageSize'
> & {
  /** 页面可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
};

export type ExportOutpatientPharmacyDispenseRecordsResponse = BinaryExportResult;

/** 加载门诊发退药记录页筛选项与默认日期。 */
export async function loadOutpatientPharmacyDispenseRecordPageInitApi() {
  return publicRequestClient.post<LoadOutpatientPharmacyDispenseRecordPageInitResponse>(
    `${BASE}/loadOutpatientPharmacyDispenseRecordPageInit`,
    {},
  );
}

/** 分页查询门诊发退药记录。 */
export async function pageOutpatientPharmacyDispenseRecordsApi(
  body: PageOutpatientPharmacyDispenseRecordsParams,
) {
  return publicRequestClient.post<PageOutpatientPharmacyDispenseRecordsResponse>(
    `${BASE}/pageOutpatientPharmacyDispenseRecords`,
    body,
  );
}

/** 导出门诊发退药记录 xlsx。 */
export async function exportOutpatientPharmacyDispenseRecordsApi(
  body: ExportOutpatientPharmacyDispenseRecordsParams,
) {
  return publicRequestClient.post<ExportOutpatientPharmacyDispenseRecordsResponse>(
    `${BASE}/exportOutpatientPharmacyDispenseRecords`,
    body,
  );
}
