/**
 * 门诊处方查看（门诊处方查询）—
 * POST /api/app/outpatientPharmacyPrescriptionQuery/{methodName}
 *
 * 列表按处方号方级展示；详情抽屉按处方号精确筛重载整方明细；打印走门诊发药 slip 同款场景。
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { reportLongRequestConfig } from '#/constants/report-request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/outpatientPharmacyPrescriptionQuery';

/** 菜单路由 path（与 whale_menus.path 对齐）。 */
export const OUTPATIENT_PRESCRIPTION_VIEW_ROUTE_PATH = '/report/outpatient-prescription-view';

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadOutpatientPrescriptionViewPageInitResponse {
  defaultOpenedDateFrom?: string;
  defaultOpenedDateTo?: string;
  chargeStatusOptions?: OptionDto[];
  dispenseStatusOptions?: OptionDto[];
  rxTypeOptions?: OptionDto[];
  medTypeOptions?: OptionDto[];
  contractCategoryOptions?: OptionDto[];
  infusionFilterOptions?: OptionDto[];
  /** 开单科室下拉（value=组织主键字符串） */
  organizationOptions?: OptionDto[];
}

export interface PageOutpatientPrescriptionViewRequest extends PageParams {
  openedDateFrom: string;
  openedDateTo: string;
  chargeStatusEnum?: string;
  dispenseStatusEnum?: string;
  infusionFilterCode?: string;
  rxTypeCode?: string;
  medTypeCode?: string;
  contractCategoryCode?: string;
  patientKeyword?: string;
  /**
   * 开单科室组织主键（药嘱 org_id）；须以 string 提交雪花 Long，禁止 Number()。
   */
  orderingDepartmentId?: string;
  /** 处方号精确匹配（详情整方重载） */
  prescriptionNo?: string;
}

export interface OutpatientPrescriptionDrugLineDto {
  medicationRequestId?: string;
  prescriptionNo?: string;
  encounterId?: string;
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
  /** 患者联系电话 */
  patientPhone?: string;
  orderingPractitionerId?: string;
  orderingPractitionerName?: string;
  /** 开单科室组织主键（药嘱 org_id） */
  orderingDepartmentId?: string;
  /** 开单科室名称 */
  orderingDepartmentName?: string;
  conditionDefinitionId?: string;
  diagnosisName?: string;
  infusionPrescriptionFlag?: boolean;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  /** 保留类型字段，UI 不展示 */
  reqAuthoredTime?: string;
  chargeStatusEnum?: string;
  chargeStatusText?: string;
  dispenseStatusEnum?: string;
  dispenseStatusText?: string;
  rxTypeCode?: string;
  rxTypeText?: string;
  /** 医保类别 code（YbMedType） */
  medTypeCode?: string;
  /** 医保类别文案 */
  medTypeText?: string;
  contractId?: string;
  contractCategoryCode?: string;
  contractCategoryText?: string;
  medicationId?: string;
  medicationName?: string;
  medicationSpec?: string;
  /** 生产厂家（目录 manufacturer_text） */
  manufacturerText?: string;
  quantity?: number | string;
  unitCode?: string;
  unitText?: string;
  injectFlag?: boolean;
  sortNumber?: number;
  groupId?: string;
  methodCode?: string;
  methodText?: string;
  rateCode?: string;
  rateText?: string;
  dose?: number | string;
  doseUnitCode?: string;
  doseUnitText?: string;
  dispensePerDuration?: number;
  dosageInstructionCode?: string;
  dosageInstructionText?: string;
  skinTestFlag?: boolean;
  skinTestFlagText?: string;
  unitPrice?: number | string;
  chargeableFlag?: boolean;
  chargeableFlagText?: string;
}

/** 列表方级摘要行（由平铺明细按处方号聚合）。 */
export interface OutpatientPrescriptionSummaryRow {
  prescriptionNo: string;
  encounterId?: string;
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
  patientPhone?: string;
  diagnosisName?: string;
  chargeStatusEnum?: string;
  chargeStatusText?: string;
  dispenseStatusEnum?: string;
  dispenseStatusText?: string;
  orderingPractitionerName?: string;
  orderingDepartmentId?: string;
  orderingDepartmentName?: string;
  reqOpenedTime?: string;
  rxTypeCode?: string;
  rxTypeText?: string;
  medTypeCode?: string;
  medTypeText?: string;
  contractCategoryText?: string;
  infusionPrescriptionFlag?: boolean;
  lineCount: number;
}

export interface PageOutpatientPrescriptionViewResponse {
  total?: number;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  records?: OutpatientPrescriptionDrugLineDto[];
  content?: OutpatientPrescriptionDrugLineDto[];
  /** 总处方量：筛选窗内处方号去重（后端 COUNT DISTINCT） */
  prescriptionCount?: number;
  /** 就诊人数：筛选窗内就诊主键去重（后端 COUNT DISTINCT） */
  visitPatientCount?: number;
}

/** 页初始化：筛选项与默认本月开方日期。 */
export async function loadOutpatientPrescriptionViewPageInitApi() {
  return publicRequestClient.post<LoadOutpatientPrescriptionViewPageInitResponse>(
    `${BASE}/loadOutpatientPharmacyPrescriptionQueryPageInit`,
    {},
  );
}

/** 分页查询门诊已签发处方药品平铺明细。 */
export async function pageOutpatientPrescriptionViewApi(
  body: PageOutpatientPrescriptionViewRequest,
) {
  const res = await publicRequestClient.post<PageOutpatientPrescriptionViewResponse>(
    `${BASE}/pageOutpatientPharmacyPrescriptionQuery`,
    body,
    reportLongRequestConfig(),
  );
  const { content, totalCount } = parsePagedRowsFromApiData<OutpatientPrescriptionDrugLineDto>(res);
  return {
    ...res,
    records: content.length > 0 ? content : (res.records ?? []),
    total: totalCount || Number(res.total ?? res.totalCount ?? 0),
    prescriptionCount: Number(res.prescriptionCount ?? 0),
    visitPatientCount: Number(res.visitPatientCount ?? 0),
  };
}
