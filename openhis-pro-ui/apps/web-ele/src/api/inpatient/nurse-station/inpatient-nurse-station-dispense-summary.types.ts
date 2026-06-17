import type { PageParams } from '#/types/api-response';

/** 中西药 Tab：west 西药 / tcm 中药 */
export type NurseStationDispenseSummaryMedicineCategoryTab = 'tcm' | 'west';

/** 长临筛选：全部 / 长期 / 临时 */
export type NurseStationDispenseSummaryTherapyFilter = 'all' | 'long' | 'temp';

/** 分页查询待汇总明细 */
export interface PagePendingDispenseSummaryLinesRequest extends PageParams {
  encounterIds: string[];
  medicineCategoryTab: NurseStationDispenseSummaryMedicineCategoryTab;
  therapyFilter: NurseStationDispenseSummaryTherapyFilter;
  queryDeadline?: string;
}

/** 待汇总明细 · 可勾选时间点 */
export interface NurseStationDispenseSummaryTimePointDto {
  dispenseId?: string;
  procedureId?: string;
  plannedDispenseTime?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCodeText?: string;
  chargeUnitPrice?: number | string;
}

/** 待汇总明细 · 医嘱行 */
export interface NurseStationDispenseSummaryOrderRowDto {
  medReqId?: string;
  medicationId?: string;
  medicationName?: string;
  specificationText?: string;
  groupId?: string;
  therapyEnum?: string;
  therapyEnumText?: string;
  dosageInstruction?: string;
  methodCode?: string;
  methodCodeText?: string;
  frequencyCode?: string;
  frequencyCodeText?: string;
  locationId?: string;
  locationName?: string;
  timePoints?: NurseStationDispenseSummaryTimePointDto[];
}

/** 待汇总明细 · 就诊分组 */
export interface NurseStationDispenseSummaryPatientGroupDto {
  encounterId?: string;
  patientId?: string;
  patientName?: string;
  bedDisplay?: string;
  orderRows?: NurseStationDispenseSummaryOrderRowDto[];
}

/** 分页查询待汇总明细响应 */
export interface PagePendingDispenseSummaryLinesResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  total?: number;
  patientGroups?: NurseStationDispenseSummaryPatientGroupDto[];
}

/** 分页查询汇总单头 */
export interface PageMedicationSummarySlipHeadersRequest extends PageParams {
  purposeLocationId?: string;
  startDate?: string;
  endDate?: string;
  keyword?: string;
}

/** 汇总单头 */
export interface NurseStationDispenseSummarySlipHeaderDto {
  busNo?: string;
  applyTime?: string;
  dispenseTime?: string;
  purposeLocationId?: string;
  purposeLocationName?: string;
  applicantId?: string;
  applicantName?: string;
  receiverId?: string;
  receiverName?: string;
  supplyStatusEnum?: string;
  supplyStatusEnumText?: string;
  deliveryStatusEnum?: string;
  deliveryStatusEnumText?: string;
}

/** 分页查询汇总单头响应 */
export interface PageMedicationSummarySlipHeadersResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  total?: number;
  rows?: NurseStationDispenseSummarySlipHeaderDto[];
}

/** 查询汇总单合并明细 */
export interface GetMedicationSummarySlipDetailRequest {
  summaryNo: string;
}

/** 汇总单合并明细行 */
export interface NurseStationDispenseSummarySlipLineDto {
  supplyRequestId?: string;
  itemId?: string;
  medicationName?: string;
  specificationText?: string;
  itemQuantity?: number | string;
  unitCode?: string;
  unitCodeText?: string;
  /** 满包整数 + 拆零余量展示（如 1盒1片） */
  displayQuantityText?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
}

/** 查询汇总单合并明细响应 */
export interface GetMedicationSummarySlipDetailResponse {
  summaryNo?: string;
  header?: NurseStationDispenseSummarySlipHeaderDto;
  lines?: NurseStationDispenseSummarySlipLineDto[];
}

/** 汇总保存 · 勾选发放行 */
export interface CreateMedicationSummaryDispenseItemDto {
  dispenseId: string;
  encounterId: string;
}

/** 汇总保存 */
export interface CreateMedicationSummaryRequest {
  dispenseItems: CreateMedicationSummaryDispenseItemDto[];
}

/** 汇总保存响应 */
export interface CreateMedicationSummaryResponse {
  summaryNos?: string[];
  message?: string;
}

/** 发送汇总领药单 */
export interface SubmitMedicationSummaryRequest {
  summaryNos: string[];
}

/** 发送汇总领药单响应 */
export interface SubmitMedicationSummaryResponse {
  summaryNos?: string[];
  message?: string;
}

/** 撤销待发送汇总领药单 */
export interface CancelMedicationSummaryRequest {
  summaryNos: string[];
}

/** 撤销汇总领药单响应 */
export interface CancelMedicationSummaryResponse {
  summaryNos?: string[];
  message?: string;
}
