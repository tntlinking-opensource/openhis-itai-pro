import type {
  AdvicePackageChildItemDto,
  ServiceRequestDetailSummaryDto,
} from '#/api/doctor-station/advice.types';

/** 执行 Tab：未执行 / 已执行 / 不执行 / 取消执行（后端无 all） */
export type NurseStationOrderExecuteTab = 'cancelled' | 'executed' | 'pending' | 'skipped';

/** 长临筛选：全部 / 长期 / 临时 */
export type NurseStationOrderExecuteTherapyFilter = 'all' | 'long' | 'temp';

/**
 * 列表接口中西药 Tab（后端单值）：west 西药+全部诊疗 / tcm 仅中药方。
 * 前端「项目类型」多选见页面 map 层 NurseStationOrderExecuteProjectType，再映射到本字段拉取后本地过滤。
 */
export type NurseStationOrderExecuteMedicineCategoryTab = 'tcm' | 'west';

/** 写操作锚点：药品申请 / 诊疗申请 */
export type NurseStationOrderExecuteOrderType = 'MED_REQUEST' | 'SERVICE_REQUEST';

/** 医嘱执行列表查询 */
export interface ListNurseStationOrderExecuteRequest {
  encounterIds: string[];
  executeTab: NurseStationOrderExecuteTab;
  therapyType?: NurseStationOrderExecuteTherapyFilter;
  medicineCategoryTab?: NurseStationOrderExecuteMedicineCategoryTab;
  executionDeadline: string;
  /** 开嘱日起（含）YYYY-MM-DD HH:mm:ss；空表示不下界过滤 */
  reqOpenedTimeFrom?: string;
  /** 开嘱日止（含）YYYY-MM-DD HH:mm:ss；空表示不上界过滤 */
  reqOpenedTimeTo?: string;
}

/** 执行槽位（与后端 NurseStationOrderExecuteSlotDto 对齐） */
export interface NurseStationOrderExecuteSlotDto {
  planTime?: string;
  procedureId?: string;
  statusEnum?: string;
  recordedTime?: string;
  /**
   * 待执行建议实际执行时分（写路径 actualTime 默认）。
   * 开嘱日首次 pending 槽 = 开嘱时刻；其余为空则回落 planTime。
   */
  defaultActualTime?: string;
  /** 操作人展示名（已执行/不执行/取消执行；待执行为空） */
  operatorName?: string;
}

/** 绑定项（列表回传、写路径原样回传） */
export interface NurseStationOrderExecuteBindItemDto {
  bindType?: string;
  bindingId?: string;
  activityId?: string;
  deviceId?: string;
  /** 展示名（与后端 displayName 对齐） */
  displayName?: string;
  /** 兼容旧字段 */
  bindDisplayName?: string;
  busNo?: string;
  specificationText?: string;
  unitPrice?: number;
  chargeItemDefinitionId?: string;
  quantityNumerator?: number;
  quantityDenominator?: number;
  unitCode?: string;
  unitCode_dictText?: string;
  priority?: number;
}

/** 列表行（与后端 NurseStationOrderExecuteRowDto 对齐） */
export interface NurseStationOrderExecuteRowDto {
  listRowKey?: string;
  orderId?: string;
  orderType?: NurseStationOrderExecuteOrderType | string;
  encounterId?: string;
  patientId?: string;
  statusEnum?: string;
  /** 请求状态展示（与后端 statusEnumText 对齐） */
  statusEnumText?: string;
  therapyEnum?: string;
  /** 长临展示（与后端 therapyEnumText 对齐） */
  therapyEnumText?: string;
  adviceDisplayName?: string;
  /** 医嘱备注（随医嘱内容展示） */
  remark?: string;
  specificationText?: string;
  /**
   * 拆零比（目录 package_unit_ratio：1 包装 = N 拆零；仅药嘱）。
   */
  partPercent?: number | string | null;
  /**
   * 包装装量后缀（如 `1片x100`；仅药嘱）。
   */
  packageSplitRatioText?: string | null;
  /**
   * 包装单位展示名（目录包装单位术语；仅药嘱）。
   * 摆药单一盒一点总量拼接用，禁止前端写死「盒」。
   */
  packageUnitDisplay?: string | null;
  dosageDisplay?: string;
  /**
   * 开立总量展示（如 `1盒`；仅临时/出院带药有值；长期为空）。
   * 摆药单/用药单「总量」列优先用本字段，禁止用 dosageDisplay（每次量）冒充。
   */
  quantityDisplay?: string | null;
  /**
   * 日剂量友好展示（如 `每日三片`；长期药且频次可解析每日次数时有值）。
   */
  dailyDoseDisplay?: string | null;
  usageDisplay?: string;
  /** 给药途径/用法编码（仅药嘱；筛选输液等须用 code，禁止用 usageDisplay） */
  methodCode?: string;
  /** 是否输液/注射相关（仅药嘱 infusion_flag） */
  infusionFlag?: boolean;
  frequencyDisplay?: string;
  /** 输液滴速编码（仅药嘱 speed_code） */
  speedCode?: string;
  /** 输液滴速展示名 */
  speedDisplay?: string;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  stopTime?: string;
  requesterName?: string;
  stopperId?: string;
  stopperName?: string;
  /** 校对科室展示名（执行科室，与后端 performerCheckOrgDisplay 对齐） */
  performerCheckOrgDisplay?: string;
  performerLocationDisplay?: string;
  categoryEnum?: string;
  /** 类型列展示：药品/诊疗/出院/转科/护理/嘱托（与校对同源） */
  orderKindDisplay?: string;
  /** 药品成组号（与医生站 group_id 同源；未成组可空） */
  groupId?: string;
  groupNo?: number;
  sortNumber?: number;
  pendingSlots?: NurseStationOrderExecuteSlotDto[];
  executedSlots?: NurseStationOrderExecuteSlotDto[];
  skippedSlots?: NurseStationOrderExecuteSlotDto[];
  cancelledSlots?: NurseStationOrderExecuteSlotDto[];
  bindItems?: NurseStationOrderExecuteBindItemDto[];
  /**
   * 诊疗套餐子项（检查/检验申请单打印「明细」列）。
   * 执行列表页不展示。
   */
  packageChildren?: AdvicePackageChildItemDto[];
  /**
   * 服务申请明细摘要（部位/标本；申请单打印用）。
   * 执行列表页不展示。
   */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
  /** 是否中药饮片方（方级折叠行） */
  tcmFlag?: boolean;
  prescriptionNo?: string;
  /** 方内全部药品申请 id（整方执行扩行） */
  lineRequestIds?: string[];
  sufferingFlag?: boolean;
  chineseHerbsDoseQuantity?: number | string;
}

/** 按就诊分组的医嘱列表 */
export interface NurseStationOrderExecutePatientGroupDto {
  encounterId?: string;
  orders?: NurseStationOrderExecuteRowDto[];
}

/** 医嘱执行列表响应 */
export interface ListNurseStationOrderExecuteResponse {
  groups?: NurseStationOrderExecutePatientGroupDto[];
}

/** 写路径槽位（执行 / 不执行） */
export interface NurseStationOrderExecuteWriteSlotDto {
  orderId: string;
  orderType: NurseStationOrderExecuteOrderType;
  planTime: string;
  /** 执行时必填：患者账户主键（来自在院患者队列 accountId） */
  accountId?: string;
  actualTime?: string;
  bindItems?: NurseStationOrderExecuteBindItemDto[];
  prescriptionNo?: string;
  /** 中药方内全部药品申请主键（列表 lineRequestIds 回传） */
  lineRequestIds?: string[];
}

/** 批量执行医嘱 */
export interface ExecuteNurseStationOrdersRequest {
  items: NurseStationOrderExecuteWriteSlotDto[];
}

export interface ExecuteNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
  /** Soft tips (e.g. long-term skin-test cross-lot); empty if none */
  warnings?: string[];
}

/** 批量登记不执行 */
export interface SkipNurseStationOrderSlotsRequest {
  items: NurseStationOrderExecuteWriteSlotDto[];
  /** 不执行原因（必填） */
  statusReasonText: string;
}

export interface SkipNurseStationOrderSlotsResponse {
  success?: boolean;
  processedCount?: number;
}
