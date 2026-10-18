/**
 * 门诊处方查看（门诊处方查询）—
 * POST /api/app/outpatientPharmacyPrescriptionQuery/{methodName}
 *
 * 列表按处方号方级展示；详情抽屉按处方号精确筛重载整方明细；打印走门诊发药 slip 同款场景。
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/outpatientPharmacyPrescriptionQuery';

/** 菜单路由 path（与 zxplat_menus.path 对齐）。 */
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
  /** 点评状态下拉（PENDING / REVIEWED） */
  reviewStatusOptions?: OptionDto[];
  /** 是否合理下拉（value 为字符串化布尔） */
  reasonableOptions?: OptionDto[];
  /** 药品类型下拉（药典 medication_category_code） */
  medicationCategoryOptions?: OptionDto[];
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
  /** 点评状态 PENDING/REVIEWED；空=不限 */
  reviewStatusEnum?: string;
  /** 是否合理；空=不限 */
  reasonableFlag?: boolean;
  /** 药品类型（药典 category_code）；空=不限 */
  medicationCategoryCode?: string;
}

export interface OutpatientPrescriptionDrugLineDto {
  medicationRequestId?: string;
  prescriptionNo?: string;
  encounterId?: string;
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
  /** 身份证号（明文；列表展示由前端脱敏） */
  idCard?: string;
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
  /**
   * 中药付数（方头 chinese_herbs_dose_quantity）。
   * 勿与 quantity（单剂×付数的请求总量）混淆。
   */
  chineseHerbsDoseQuantity?: null | number | string;
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
  /** 身份证号（明文；列表脱敏展示） */
  idCard?: string;
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
  /** 中药付数（方头；详情展示用） */
  chineseHerbsDoseQuantity?: null | number | string;
  lineCount: number;
  /** 点评 enrichment：PENDING=无有效行；REVIEWED=已点评（逻辑用 code） */
  reviewStatusEnum?: string;
  /** 点评状态展示文案 */
  reviewStatusText?: string;
  /** 点评主键（撤销用） */
  reviewId?: string;
  /** 点评业务号（PRV；详情「点评编号」） */
  reviewBusNo?: string;
  reasonableFlag?: boolean;
  reasonableText?: string;
  reviewReason?: string;
  reviewRemark?: string;
  reviewerName?: string;
  reviewedAt?: string;
}

export interface PageOutpatientPrescriptionViewResponse {
  total?: number;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  content?: OutpatientPrescriptionDrugLineDto[];
  /** 总处方量：筛选窗内处方号去重（后端 COUNT DISTINCT） */
  prescriptionCount?: number;
  /** 就诊人数：筛选窗内就诊主键去重（后端 COUNT DISTINCT） */
  visitPatientCount?: number;
}

/** 页初始化：筛选项与默认本年开方日期。 */
export async function loadOutpatientPrescriptionViewPageInitApi() {
  return reportPost<LoadOutpatientPrescriptionViewPageInitResponse>(
    `${BASE}/loadOutpatientPharmacyPrescriptionQueryPageInit`,
    {},
  );
}

/** 分页查询门诊已签发处方药品平铺明细。 */
export async function pageOutpatientPrescriptionViewApi(
  body: PageOutpatientPrescriptionViewRequest,
) {
  const res = await reportPost<PageOutpatientPrescriptionViewResponse>(
    `${BASE}/pageOutpatientPharmacyPrescriptionQuery`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<OutpatientPrescriptionDrugLineDto>(res);
  return {
    ...res,
    records: content,
    total: totalCount || Number(res.total ?? res.totalCount ?? 0),
    prescriptionCount: Number(res.prescriptionCount ?? 0),
    visitPatientCount: Number(res.visitPatientCount ?? 0),
  };
}
