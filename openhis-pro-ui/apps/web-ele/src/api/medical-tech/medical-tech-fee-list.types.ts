/**
 * 医技费用清单应用服务请求/响应类型（Long 主键一律 string）。
 */

import type { MedicalTechEncounterScope } from './medical-tech-execution.types';

/** 查询医技费用清单 */
export interface ListMedicalTechFeeItemsRequest {
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
}

export interface MedicalTechFeeItemRowDto {
  /** 计费人主键（划价/执行操作人） */
  billingPractitionerId?: null | string;
  /** 计费人展示名 */
  billingPractitionerName?: null | string;
  chargeItemId: string;
  chargeStatusEnum?: null | string;
  chargeStatusEnumText?: null | string;
  /** 确费时刻；空=未确费 */
  feeConfirmedAt?: null | string;
  /** 确费人主键；空=未确费 */
  feeConfirmerId?: null | string;
  /** 确费人展示名 */
  feeConfirmerName?: null | string;
  itemCode?: null | string;
  itemName?: null | string;
  /**
   * 结算时间（entered_date / 真正结算戳）；待结算时常为空
   */
  occurredTime?: null | string;
  /**
   * 划价时间（occurrence_time / 护士执行入账时刻）
   */
  pricedTime?: null | string;
  /**
   * 医技执行时间（诊疗 Procedure.recorded_time / 耗材 dispense_time）
   */
  medicalTechExecutedTime?: null | string;
  /** 医技执行人主键 */
  medicalTechPerformerId?: null | string;
  /** 医技执行人展示名 */
  medicalTechPerformerName?: null | string;
  quantity?: null | number | string;
  /** 开立科室展示 */
  relatedOrgDisplay?: null | string;
  serviceId?: null | string;
  serviceTable?: null | string;
  totalPrice?: null | number | string;
  unitPrice?: null | number | string;
}

export interface ListMedicalTechFeeItemsResponse {
  rows?: MedicalTechFeeItemRowDto[] | null;
  sumAmount?: null | number | string;
}

/** 一键确费 */
export interface ConfirmMedicalTechFeesRequest {
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
}

export interface ConfirmMedicalTechFeesResponse {
  confirmedCount?: null | number;
  message?: null | string;
  succeeded?: boolean | null;
}

/** 打印医技费用清单 */
export interface PrintMedicalTechFeeListRequest {
  /** 勾选收费明细；空=打印本科相关全集 */
  chargeItemIds?: null | string[];
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  scenarioCode?: null | string;
}

export interface PrintMedicalTechFeeListResponse {
  message?: null | string;
  succeeded?: boolean | null;
  traceId?: null | string;
}
