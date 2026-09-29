/**
 * 门诊处方点评 —
 * POST /api/app/outpatientPharmacyPrescriptionReview/{methodName}
 *
 * 点评记录页 init/分页/导出；门诊处方查看批量点评、撤销；发药后自动点评；方号 enrichment。
 * 点评记录页列表/导出列与「处方点评工作表」对齐（无序号）；本页无点评/撤销写入口。
 */
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';
import type { ApiOptionItem, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/outpatientPharmacyPrescriptionReview';

/** 菜单路由 path（与 whale_menus.path 对齐）。 */
export const PRESCRIPTION_REVIEW_ROUTE_PATH = '/report/prescription-review';

/** 点评展示态 / 落库态 code（逻辑判断用，禁止用文案）。 */
export const PRESCRIPTION_REVIEW_STATUS = {
  PENDING: 'PENDING',
  REVIEWED: 'REVIEWED',
} as const;

export type PrescriptionReviewStatusCode =
  (typeof PRESCRIPTION_REVIEW_STATUS)[keyof typeof PRESCRIPTION_REVIEW_STATUS];

export interface LoadPrescriptionReviewPageInitResponse {
  statusOptions?: ApiOptionItem[];
  reasonableOptions?: ApiOptionItem[];
  /** 重点药类型（基药/监控/集采/抗生素） */
  keyDrugTypeOptions?: ApiOptionItem[];
  /** 药品性质（麻醉/精一/精二） */
  medicinePropertyOptions?: ApiOptionItem[];
  defaultReviewedDateFrom?: string;
  defaultReviewedDateTo?: string;
}

export interface PagePrescriptionReviewsRequest extends PageParams {
  statusEnum?: string;
  reviewedDateFrom?: string;
  reviewedDateTo?: string;
  /** 开方日起 yyyy-MM-dd；与止成对 */
  openedDateFrom?: string;
  /** 开方日止 yyyy-MM-dd；与起成对 */
  openedDateTo?: string;
  /** 是否合理；空=不限 */
  reasonableFlag?: boolean;
  /** 点评人主键（string 保精度） */
  reviewerId?: string;
  /** 关键字：处方号或点评业务号模糊（OR） */
  keyword?: string;
  /**
   * 重点药类型 KeyDrugType code：BASIC/NATIONAL_MONITOR/CENTRAL_PURCHASE/ANTIBIOTIC；空=不限
   */
  keyDrugType?: string;
  /**
   * 药品性质 KeyDrugType code：NARCOTIC/PSYCH_ONE/PSYCH_TWO；空=不限
   */
  medicinePropertyCode?: string;
}

/** 列表行：与工作表数据列同构（字段名我方命名）；id/busNo 等供行键与详情。 */
export interface PrescriptionReviewListRow {
  id?: string;
  /** 点评业务号（详情「点评编号」） */
  busNo?: string;
  prescriptionNo?: string;
  statusEnum?: string;
  statusText?: string;
  patientAgeText?: string;
  diagnosisName?: string;
  drugVarietyCount?: number;
  /** 抗菌药 0/1 */
  antibioticFlag?: number;
  /** 注射剂 0/1 */
  injectFlag?: number;
  basicDrugVarietyCount?: number;
  genericNameCount?: number;
  prescriptionAmount?: number | string;
  orderingPractitionerName?: string;
  reasonableFlag?: boolean;
  reasonableText?: string;
  /** 存在问题（字段名 reason） */
  reason?: string;
  remark?: string;
  reviewerId?: string;
  reviewerName?: string;
  reviewedAt?: string;
}

export interface PagePrescriptionReviewsResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  total?: number;
  content?: PrescriptionReviewListRow[];
}

/** 导出：筛选 + 当前页 pageNo/pageSize + exportColumns。 */
export type ExportPrescriptionReviewsRequest = PagePrescriptionReviewsRequest & {
  exportColumns: ExcelExportColumnPayload[];
};

export type ExportPrescriptionReviewsResponse = BinaryExportResult;

export interface BatchCreateReviewItem {
  /** 处方号（业务号由后端 PRV 日序自生成） */
  prescriptionNo: string;
}

export interface BatchCreateReviewsRequest {
  items: BatchCreateReviewItem[];
  reasonableFlag: boolean;
  reason?: string;
  remark?: string;
}

export interface BatchCreateReviewsResponse {
  createdIds?: string[];
}

export interface RevokeReviewRequest {
  id?: string;
  prescriptionNo?: string;
}

export interface RevokeReviewResponse {
  success?: boolean;
}

export interface CreateAutoReviewAfterDispenseRequest {
  prescriptionNos: string[];
  /** 发药人（点评人）；string 保精度 */
  dispenserId: string;
  autoReviewEnabled: boolean;
}

export interface CreateAutoReviewAfterDispenseResponse {
  createdIds?: string[];
  skippedCount?: number;
  softFailed?: boolean;
}

export interface PrescriptionReviewActiveDto {
  id?: string;
  prescriptionNo?: string;
  busNo?: string;
  statusEnum?: string;
  statusText?: string;
  reasonableFlag?: boolean;
  reasonableText?: string;
  reason?: string;
  remark?: string;
  reviewerId?: string;
  reviewerName?: string;
  reviewedAt?: string;
}

export interface MapActiveReviewsByPrescriptionNosRequest {
  prescriptionNos: string[];
}

export interface MapActiveReviewsByPrescriptionNosResponse {
  reviewsByPrescriptionNo?: Record<string, PrescriptionReviewActiveDto>;
}

/** 点评记录页初始化：状态/是否合理选项与默认本月点评日。 */
export async function loadPrescriptionReviewPageInitApi() {
  return publicRequestClient.post<LoadPrescriptionReviewPageInitResponse>(
    `${BASE}/loadPageInit`,
    {},
  );
}

/** 分页查询有效点评记录。 */
export async function pagePrescriptionReviewsApi(body: PagePrescriptionReviewsRequest) {
  const res = await publicRequestClient.post<PagePrescriptionReviewsResponse>(
    `${BASE}/pageReviews`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<PrescriptionReviewListRow>(res);
  return {
    ...res,
    records: content,
    total: totalCount || Number(res.total ?? res.totalCount ?? 0),
  };
}

/** 导出当前页点评记录 xlsx（条数=pageSize）。 */
export async function exportPrescriptionReviewsApi(body: ExportPrescriptionReviewsRequest) {
  return publicRequestClient.post<ExportPrescriptionReviewsResponse>(`${BASE}/exportReviews`, body);
}

/** 批量创建点评（共用是否合理/原因/备注）。 */
export async function batchCreatePrescriptionReviewsApi(body: BatchCreateReviewsRequest) {
  return publicRequestClient.post<BatchCreateReviewsResponse>(`${BASE}/batchCreateReviews`, body);
}

/** 撤销有效点评（主键优先，否则处方号）。 */
export async function revokePrescriptionReviewApi(body: RevokeReviewRequest) {
  return publicRequestClient.post<RevokeReviewResponse>(`${BASE}/revokeReview`, body);
}

/** 发药完成后自动点评；失败以 softFailed 返回，不抛业务异常。 */
export async function createAutoReviewAfterDispenseApi(body: CreateAutoReviewAfterDispenseRequest) {
  return publicRequestClient.post<CreateAutoReviewAfterDispenseResponse>(
    `${BASE}/createAutoReviewAfterDispense`,
    body,
  );
}

/** 按处方号批量映射有效点评（列表 enrichment）。 */
export async function mapActiveReviewsByPrescriptionNosApi(
  body: MapActiveReviewsByPrescriptionNosRequest,
) {
  return publicRequestClient.post<MapActiveReviewsByPrescriptionNosResponse>(
    `${BASE}/mapActiveReviewsByPrescriptionNos`,
    body,
  );
}
