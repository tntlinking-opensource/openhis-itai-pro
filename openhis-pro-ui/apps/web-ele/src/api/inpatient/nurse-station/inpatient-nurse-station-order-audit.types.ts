/** 医嘱校对 Tab：未校对 / 已校对（已停嘱校对并入已校对；无已停止/已作废 Tab） */
export type NurseStationOrderAuditTab = 'pending' | 'passed';

/** 长临筛选：全部 / 长期 / 临时 */
export type NurseStationOrderAuditTherapyFilter = 'all' | 'long' | 'temp';

/** 中西药 Tab：west 西药 / tcm 中药 */
export type NurseStationOrderAuditMedicineCategoryTab = 'tcm' | 'west';

/** 写操作锚点：药品申请 / 诊疗申请 */
export type NurseStationOrderAuditOrderType = 'MED_REQUEST' | 'SERVICE_REQUEST';

/** 医嘱校对列表查询 */
export interface ListNurseStationOrderAuditRequest {
  encounterIds: string[];
  auditTab: NurseStationOrderAuditTab;
  therapyType?: NurseStationOrderAuditTherapyFilter;
  medicineCategoryTab?: NurseStationOrderAuditMedicineCategoryTab;
}

/** 列表行（与后端 NurseStationOrderAuditRowDto 对齐） */
export interface NurseStationOrderAuditRowDto {
  listRowKey?: string;
  requestId?: string;
  orderType?: NurseStationOrderAuditOrderType | string;
  encounterId?: string;
  patientId?: string;
  statusEnum?: string;
  statusDisplay?: string;
  therapyEnum?: string;
  therapyDisplay?: string;
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
  dosageDisplay?: string;
  /**
   * 日剂量友好展示（如 `每日三片`；长期药且频次可解析每日次数时有值）。
   */
  dailyDoseDisplay?: string | null;
  /** 首日/首次用量（仅长期药品；BR-IP-ORD-030） */
  firstDose?: number | string | null;
  /** 首日用量单位编码 */
  firstDoseUnitCode?: string | null;
  /** 首日用量单位展示名 */
  firstDoseUnitName?: string | null;
  /** 分次剂量序列（计量） */
  doseSchedule?: string | null;
  /** 分次用量序列（拆零） */
  quantitySchedule?: string | null;
  usageDisplay?: string;
  frequencyDisplay?: string;
  /** 输液滴速编码（仅药嘱） */
  speedCode?: string;
  /** 输液滴速展示名 */
  speedDisplay?: string;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  stopTime?: string;
  requesterName?: string;
  stopperId?: string;
  stopperName?: string;
  performerLocationDisplay?: string;
  /** 校对人姓名（已校对及后续 Tab 展示） */
  performerCheckName?: string;
  /** 停嘱校对人主键 */
  stopPerformerCheckId?: string;
  /** 停嘱校对时间 */
  stopCheckTime?: string;
  /** 停嘱校对人姓名（打印/已停止 Tab） */
  stopCheckerName?: string;
  /**
   * 是否停嘱待校：STOPPED 且长期且尚未停嘱校对（待校对列表醒目标识）。
   */
  stopAuditPending?: boolean;
  categoryEnum?: string;
  /** 类型列展示：药品/诊疗/出院/转科/护理 */
  orderKindDisplay?: string;
  /** 药品成组号（与医生站 group_id 同源；未成组可空） */
  groupId?: string;
  groupNo?: number;
  sortNumber?: number;
  /** 是否中药饮片方（方级折叠行） */
  tcmFlag?: boolean;
  prescriptionNo?: string;
  /** 方内全部药品申请 id（整方校对/退回扩行） */
  lineRequestIds?: string[];
  sufferingFlag?: boolean;
  chineseHerbsDoseQuantity?: number | string;
  /** 是否皮试药（仅药嘱；true 时校对列表提示需皮试） */
  skinTestFlag?: boolean;
  /** 皮试临床状态 code（如 active=阳性） */
  skinTestOutcomeCode?: string;
  /** 皮试临床状态展示文案（如 阳性 / 阴性） */
  skinTestOutcomeText?: string;
  /**
   * 计划出院时间（出院医嘱 occurrence_start_time；医生填写；非清床/结算）
   */
  plannedDischargeTime?: string;
  /** 出院/转科原因 */
  reasonText?: string;
}

/** 按就诊分组的医嘱列表 */
export interface NurseStationOrderAuditPatientGroupDto {
  encounterId?: string;
  orders?: NurseStationOrderAuditRowDto[];
}

/** 医嘱校对列表响应 */
export interface ListNurseStationOrderAuditResponse {
  groups?: NurseStationOrderAuditPatientGroupDto[];
}

/** 批量写操作单行 */
export interface NurseStationOrderAuditWriteItemDto {
  orderType: NurseStationOrderAuditOrderType;
  requestId: string;
}

/** 批量校对通过 */
export interface VerifyNurseStationOrdersRequest {
  items: NurseStationOrderAuditWriteItemDto[];
}

export interface VerifyNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
}

/** 批量退回 */
export interface RejectNurseStationOrdersRequest {
  items: NurseStationOrderAuditWriteItemDto[];
  reason?: string;
}

export interface RejectNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
}
