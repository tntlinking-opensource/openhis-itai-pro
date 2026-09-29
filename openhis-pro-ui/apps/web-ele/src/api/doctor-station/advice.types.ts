/**
 * 医生站医嘱开立应用服务契约类型（与后端 DTO 字段名对齐；Long 类主键在请求 JSON 中使用 string）。
 */

import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

/** 与后端 `AdvicePackageChildItemDto` 对齐 */
export interface AdvicePackageChildItemDto {
  childrenId?: string;
  childrenNum?: number;
  childrenName?: string;
}

/** 与后端 `AdviceRequestExtensionSnapshotDto` 对齐 */
export interface AdviceRequestExtensionSnapshotDto {
  attributes?: Record<string, unknown>;
  entries?: Record<string, unknown>[];
}

/** 与后端 `ProcedurePerformerItemDto` 对齐 */
export interface ProcedurePerformerItemDto {
  practitionerId?: string;
  practitionerName?: string;
  functionCode?: string;
  startTime?: string;
  endTime?: string;
  orgFromId?: string;
}

/** 与后端 `com.openhis.whale.core.application.dto.OptionDto` 及术语下拉 JSON 对齐 */
export interface TerminologyOptionDto {
  label?: string;
  sortOrder?: number;
  value?: string;
  busNo?: string;
  /** 拼音检索码（申请单部位/样本模糊过滤） */
  pyStr?: string;
  /** 五笔检索码（申请单部位/样本模糊过滤） */
  wbStr?: string;
  children?: TerminologyOptionDto[];
}

export interface InitDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
}

export interface InitDoctorStationAdviceResponse {
  medicationRateOptions?: TerminologyOptionDto[];
  /**
   * 频次码 → day_times 非空段数（住院「用量不一致」展开框数；BR-IP-ORD-037）。
   */
  dayTimesCountByRateCode?: Record<string, number>;
  medicationRouteOptions?: TerminologyOptionDto[];
  /** 输液滴速下拉（术语 speed_code；住院输液/注射药开立） */
  medicationSpeedOptions?: TerminologyOptionDto[];
  /** 费用性质（合同）下拉：value=contractId，label=合同名称 */
  feeNatureOptions?: TerminologyOptionDto[];
  /** 身体部位下拉（申请单检查行明细） */
  bodyStructureOptions?: TerminologyOptionDto[];
  /** 样本定义下拉（申请单检验行明细） */
  specimenDefinitionOptions?: TerminologyOptionDto[];
}

/** 与后端 `ComputeMedicationFrequencyExecutionsRequest` 对齐 */
export interface ComputeMedicationFrequencyExecutionsRequest {
  rateCode: string;
  medicationDays: number;
  /** ISO-8601；可空则由服务端取当前时刻。医生站推算总量时传「次日本地 0 点」。 */
  startAt?: string;
}

/** 与后端 `ComputeMedicationFrequencyExecutionsResponse` 对齐 */
export interface ComputeMedicationFrequencyExecutionsResponse {
  executionCount: number;
  executionAtList?: string[];
}

export interface ListDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  onlyMine?: boolean;
}

export interface DoctorStationAdviceRowDto {
  resourceType?: string;
  requestId?: string;
  encounterId?: string;
  patientId?: string;
  statusEnum?: string;
  statusEnumText?: string;
  /** 合并展示码（退费优先于请求态；列表已分列，仅历史/兼容消费者使用） */
  displayStatusEnum?: string;
  /** 合并展示文案（待退费/已退费或请求态；列表已分列，仅历史/兼容消费者使用） */
  displayStatusEnumText?: string;
  contentSummary?: string;
  prescriptionNo?: string;
  groupId?: string;
  /** 组套标识；成组药嘱与 groupId 同源，绑定耗材可对齐 */
  packageId?: string;
  /** 行级费用状态码（ChargeItemStatus） */
  chargeStatusEnum?: string;
  /** 行级费用状态文案 */
  chargeStatusEnumText?: string;
  /** 费用性质主键（药品：med_medication_request.contract_id） */
  contractId?: string;
  /** 费用性质分类展示文案（自费/公费/省医保/市医保/工伤保险） */
  feeNatureText?: string;
  skinTestFlag?: boolean;
  injectionFlag?: boolean;
  authoredById?: string;
  authoredByName?: string;
  /** 停嘱人主键（后端 stopper_id） */
  stopperId?: string;
  /** 停嘱人姓名 */
  stopperName?: string;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  /** 列表行诊断摘要（装配器若返回则展示） */
  diagnosisSummary?: string;
  /** 诊断定义名称（列表「诊断」列，由 encounterDiagnosisIds 批量解析） */
  conditionDefinitionName?: string;
  /** 绑定就诊诊断有序 ids 串（有序；主诊第一） */
  encounterDiagnosisIds?: string;
  /** 列表行金额合计（装配器若返回则展示） */
  lineTotalAmount?: number | string;
  signedById?: string;
  /** 签发时间（后端 req_authored_time） */
  reqAuthoredTime?: string;

  /** 统一列表行稳定键（新契约） */
  listRowKey?: string;
  productDefinitionId?: string;
  productTableName?: string;
  /** 目录分类展示名（列表「类型」列） */
  catalogCategoryName?: string;
  adviceDisplayName?: string;
  /** 诊疗套餐子项（B.6.2，替代 activityChildrenJson） */
  packageChildren?: AdvicePackageChildItemDto[];
  /** 请求扩展快照（B.6.2，替代 contentJson） */
  requestExtensions?: AdviceRequestExtensionSnapshotDto;
  specificationText?: string;
  quantity?: number | string;
  unitCode?: string;
  unitName?: string;
  dose?: number | string;
  doseUnitCode?: string;
  doseUnitName?: string;
  /**
   * 拆零比（目录 package_unit_ratio：1 包装 = N 拆零；未维护时为空）。
   */
  partPercent?: number | string | null;
  /**
   * 包装装量后缀（如 `1片x100`；与规格拼成 `5mg1片x100`；缺数据时为空）。
   */
  packageSplitRatioText?: string | null;
  /** 是否中药饮片行（tcm_flag） */
  tcmFlag?: boolean | null;
  /** 是否代煎（sufferingFlag；病案列表挂代煎文案用 code，禁止用展示名判断） */
  decoctionDelegatedFlag?: boolean | null;
  /** 中药付数（方头 chinese_herbs_dose_quantity；非请求总量） */
  chineseHerbsDoseQuantity?: null | number | string;
  /** 特殊煎法术语码（dosage_instruction） */
  dosageInstructionCode?: null | string;
  /** 特殊煎法展示名 */
  dosageInstructionText?: null | string;
  /**
   * 单次剂量展示串（后端）：最小单位为主，必要时括号附计量单位，末尾带 /次；单位相同时无括号。
   * 例：`1支(2ml)/次` / `1片/次`
   */
  dosageDisplay?: string;
  /**
   * 日剂量友好展示（如 `每日三片`；长期药且频次可解析每日次数时有值）。
   */
  dailyDoseDisplay?: string | null;
  /** 单次剂量对应的最小单位数量 */
  splitDose?: number | string;
  splitDoseUnitCode?: string;
  splitDoseUnitName?: string;
  /** 首日/首次用量（仅住院长期；BR-IP-ORD-030） */
  firstDose?: number | string;
  firstDoseUnitCode?: string;
  firstDoseUnitName?: string;
  /** 分次剂量序列（计量；有值时剂量列走序列；BR-IP-ORD-037） */
  doseSchedule?: string | null;
  /** 分次用量序列（拆零；与 doseSchedule 同序） */
  quantitySchedule?: string | null;
  methodCode?: string;
  methodName?: string;
  rateCode?: string;
  rateName?: string;
  /** 输液滴速编码（术语 speed_code） */
  speedCode?: string;
  /** 输液滴速展示名 */
  speedName?: string;
  /** 用药天数（展示为 (N天)） */
  dispensePerDuration?: number | string | null;
  boundConditionName?: string;
  /** 行展示单价（元，与后端 lineDisplayUnitPrice 一致） */
  lineDisplayUnitPrice?: number | string;
  /** 行展示总价（元，与后端展示口径一致） */
  lineDisplayTotalPrice?: number | string;
  skinTestFlagText?: string;
  skinTestOutcomeCode?: string;
  skinTestOutcomeText?: string;
  /** 长嘱皮试药换批提示：建议追加皮试检查（住院 IF-IP-DOR-27） */
  needAppendSkinTestFlag?: boolean;
  /** 皮试检查关联的皮试药 requestId（companion based_on） */
  basedOnMedicationRequestId?: string;
  /** 皮试检查关联的皮试药名称 */
  skinTestLinkedMedicationName?: string;
  /** 医生站主表展示排序号（AS BR-ORD-018） */
  sortNumber?: number | string | null;
  /** 诊疗执行次数（结构-only 行） */
  executionCount?: number | string | null;
  /** 目录分类码（如 2 西药、11 耗材、21 治疗、26 护理级别） */
  catalogCategoryCode?: string;
  /** 医保项目等级码（CHRGITM_LV；常见 1/2/3） */
  chrgitmLv?: number | string;
  /** 医保项目等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  /** 诊疗服务类目持久化码（如 26 护理级别、52 嘱托、出院/转科特殊类目） */
  serviceCategoryEnum?: string;
  /** 活动类型码/名（诊疗） */
  activityTypeCode?: string;
  activityTypeName?: string;
  /**
   * 嘱托医嘱正文（`wkf_service_request.entrust_content`）；
   * 嘱托行内容列优先本字段（AS-IP-DOR-003 · BR-IP-ORD-028）。
   */
  entrustContent?: string;
  /** 执行科室 */
  performLocationId?: string;
  performLocationName?: string;
  fifoDispenseLocationId?: string;
  fifoDispenseLocationName?: string;
  /** 住院：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION */
  therapyTypeCode?: string;
  /** 住院：治疗类型展示文案（长期医嘱 / 临时医嘱 / 出院带药） */
  therapyTypeText?: string;
  generateSourceEnum?: string;
  /** 停嘱时间（后端 stop_time） */
  stopTime?: string;
  conditionDefinitionId?: string;
  /** 前置服务申请主键（锚定 SR 时） */
  basedOnServiceRequestId?: string;
  /** 前置药品申请主键（皮试检查 companion 锚定皮试药） */
  basedOnMedicationRequestId?: string;
  /** 申请单行纯备注（wkf_service_request.remark） */
  remark?: string;
  /** 医嘱优先级 code（加急=EM） */
  priorityEnum?: string;
  /** 申请单部位/样本明细摘要（AR 前缀申请单） */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
}

export interface ListDoctorStationAdviceResponse {
  /** 标准分页列表（后端 PagedResult） */
  content?: DoctorStationAdviceRowDto[];
  totalCount?: number;
}

export interface PrepickedProductKeyDto {
  productDefinitionId: string;
  productTableName: string;
}

export interface PageDoctorStationAdviceCatalogRequest {
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
  pharmacyLocationId?: string;
  productTableNames?: string[];
  /** 排除药品 medication_category_code（如开立西药医嘱传 ["4"] 排除中草药） */
  excludeMedicationCategoryCodes?: string[];
  /** 申请单选品：检验 PROOF/22、检查 TEST/23、治疗 TREATMENT/21 */
  categoryEnum?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  pricingSourceCode?: string;
  pricingFlag?: number;
  prepickedProductKeys?: PrepickedProductKeyDto[];
  sortMode?: string;
  /** 仅看库存>0（药/耗后端分页过滤；诊疗不受影响） */
  positiveQuantityOnly?: boolean;
}

export interface DoctorStationAdviceInventoryRowDto {
  itemTable?: string;
  itemId?: string;
  categoryCode?: string;
  inventoryId?: string;
  quantity?: number | string;
  unitCode?: string;
  lotNumber?: string;
  locationStoreId?: string;
  locationId?: string;
  locationName?: string;
  price?: number | string;
  displayOrder?: number;
}

/** 药品多计量带（拆零 SPLIT、剂量 DOSE 等），与目录/主数据 JSON 对齐 */
export interface DoctorStationMedicationUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number;
}

export interface DoctorStationAdviceCatalogRowDto {
  adviceType?: string;
  productDefinitionId?: string;
  productTableName?: string;
  busNo?: string;
  name?: string;
  /** 商品名称（药品 merchandise_name；诊疗/耗材通常为空） */
  merchandiseName?: string;
  pyCode?: string;
  wbCode?: string;
  categoryCode?: string;
  /** 类别展示文案（如 西药/中成药） */
  categoryCodeDictText?: string;
  unitCode?: string;
  unitName?: string;
  /** 规格展示（如药品剂量规格） */
  totalVolume?: string;
  activityTypeCode?: string;
  defaultPerformOrgId?: string;
  defaultPerformOrgName?: string;
  defaultDispenseLocationId?: string;
  defaultDispenseLocationName?: string;
  defaultLotNo?: string;
  /** 药品默认用药频次编码（术语 code） */
  rateCode?: string;
  rateCodeDictText?: string;
  /** 药品默认用法（给药途径）编码 */
  methodCode?: string;
  methodCodeDictText?: string;
  /** 默认单次剂量（剂量单位） */
  dose?: number | string;
  /** 单次最小剂量（剂量单位） */
  minUnit?: number | string;
  /** 单次最大剂量（剂量单位） */
  maxUnit?: number | string;
  availableQuantity?: number | string;
  /** 满包整数 + 拆零余量展示（如 4盒6片），优先于 availableQuantity 小数盒 */
  availableQuantityText?: string;
  /**
   * 生产厂家/产地。
   */
  manufacturer?: string;
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  /** 零售价等单价（与目录接口返回字段对齐） */
  unitPrice?: number | string;
  priceListJson?: string;
  chargeItemDefinitionId?: string;
  pricingSourceCode?: string;
  /** 多计量带数组；若后端仅给 JSON 字符串则用 `unitBandsJson` */
  unitBands?: DoctorStationMedicationUnitBandDto[];
  unitBandsJson?: string;
  /** 门诊拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  partAttributeEnumDictText?: string;
  /** 住院临时医嘱拆分属性 */
  thoPartAttributeEnum?: string;
  thoPartAttributeEnumDictText?: string;
  /** 医保收费项目等级码（CHRGITM_LV，如 1/2/3） */
  chrgitmLv?: number | string;
  /** 医保等级展示名（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  /** 注射/输液药品标志（目录 med_medication_definition.inject_flag） */
  injectFlag?: boolean;
  /** 中药特殊煎法术语码（目录 dosage_instruction） */
  dosageInstruction?: string;
  /** 中药特殊煎法展示名 */
  dosageInstructionDictText?: string;
}

export interface PageDoctorStationAdviceCatalogResponse {
  rows?: DoctorStationAdviceCatalogRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface GetDoctorStationAdviceOrderBindInfoRequest {
  bindType: string;
  itemNo: string;
  itemId?: string;
  organizationUnitId?: string;
}

export interface DoctorStationAdviceOrderBindRowDto {
  bindType?: string;
  sourceItemId?: string;
  sourceItemName?: string;
  targetItemId?: string;
  targetItemName?: string;
  quantityNumerator?: number | string;
  quantityDenominator?: number | string;
  unitCode?: string;
  priority?: number;
  activeFlag?: boolean;
}

export interface GetDoctorStationAdviceOrderBindInfoResponse {
  bindRows?: DoctorStationAdviceOrderBindRowDto[];
}

/** IF-DOR-23：暂存前绑定解析请求（与 IF-DOR-14 同构） */
export interface ResolveDoctorStationAdviceDraftBindsRequest {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
}

/** IF-DOR-23：暂存前绑定解析明细 */
export interface DoctorStationAdviceDraftBindItemDto {
  bindType?: string;
  sourceItemId?: string;
  sourceItemName?: string;
  targetItemId?: string;
  targetItemName?: string;
  targetProductTableName?: string;
  quantityNumerator?: number | string;
  quantityDenominator?: number | string;
  unitCode?: string;
  unitName?: string;
  priority?: number;
  activeFlag?: boolean;
  triggerLineIndex?: number;
  triggerLineOp?: string;
  triggerProductTableName?: string;
  triggerDefinitionId?: string;
  triggerRequestId?: string;
  basedOnTable?: string;
  basedOnRequestId?: string;
  /** 合管检验设备主键（同批去重） */
  specimenTubeMergeDeviceId?: string;
}

export interface ResolveDoctorStationAdviceDraftBindsResponse {
  bindItems?: DoctorStationAdviceDraftBindItemDto[];
}

export interface GetEncounterAdviceDefaultContractRequest {
  encounterId: string;
  patientId: string;
}

export interface GetEncounterAdviceDefaultContractResponse {
  contractId?: string;
  contractBusNo?: string;
  contractName?: string;
  categoryEnum?: string;
  statusEnum?: string;
  activeFlag?: boolean;
}

export interface SaveMedicationAdviceDraftLinePayload {
  /** UPDATE 时若未重选药品可省略，由域内保留原定义主键 */
  medicationDefinitionId?: string;
  quantity: number | string;
  unitCode: string;
  /** 单次用量（数值，对应 med_medication_request.dose） */
  dose?: number | string;
  /** 单次用量单位编码（对应 dose_unit_code） */
  doseUnitCode?: string;
  /** 首日/首次用量（仅住院长期；BR-IP-ORD-030；UPDATE 清空时显式传 null） */
  firstDose?: number | string | null;
  /** 首日用量单位编码（与 firstDose 成对；清空时显式传 null） */
  firstDoseUnitCode?: string | null;
  /**
   * 分次剂量序列（计量、逗号分隔；仅长期西药/中成药；BR-IP-ORD-037；清空传 null）
   */
  doseSchedule?: string | null;
  /**
   * 分次用量序列（拆零、逗号分隔；与 doseSchedule 同序；清空传 null）
   */
  quantitySchedule?: string | null;
  /** 用药天数（对应 dispense_per_duration） */
  dispensePerDuration?: number;
  methodCode?: string;
  rateCode?: string;
  /** 输液滴速编码（术语 speed_code；可空；空串可清空） */
  speedCode?: string;
  groupId?: string;
  dispenseLocationId?: string;
  /** 绑定就诊诊断有序 ids 串（有序；主诊第一；落库 encounter_diagnosis_ids） */
  encounterDiagnosisIds?: string;
  contractId?: string;
  /** 皮试标记（与列表 `skinTestFlag` 对齐） */
  skinTestFlag?: boolean;
  /** 用药方式：LONG_TERM 长期 / TEMPORARY 临时（住院） */
  therapyEnum?: string;
  /** 医嘱备注（落库 med_medication_request.remark） */
  remark?: string;
}

export interface SaveServiceAdviceDraftLinePayload {
  activityDefinitionId: string;
  performOrgId?: string;
  /** 频次 code（与药品 `medicationPayload.rateCode` 一致） */
  rateCode?: string;
  /** 执行次数 */
  executionCount?: number;
  /** 开立数量（与 unitCode 同语义） */
  quantity: number | string;
  /** 单位 code */
  unitCode?: string;
  /** 绑定就诊诊断有序 ids 串（有序；主诊第一） */
  encounterDiagnosisIds?: string;
  /** 费用性质/合同主键（落库 `wkf_service_request.contract_id`） */
  contractId?: string;
  /** 套餐子项覆盖 JSON（仅套餐展开子项覆盖，不含频次/次数/单位） */
  childOverridesJson?: string;
  /**
   * 嘱托正文（落库 `entrust_content`）；嘱托行（类目 52 / 名「嘱托」）必填（BR-IP-ORD-028）。
   */
  entrustContent?: string;
  /**
   * 医嘱备注（落库 `wkf_service_request.remark`；与嘱托正文分立；
   * 有套餐子项覆盖时后端优先写覆盖 JSON，本字段可不生效）。
   */
  remark?: string;
  /** 医嘱优先级 code（检验/检查加急=EM；空=非急） */
  priorityEnum?: string;
}

export interface SaveDeviceAdviceDraftLinePayload {
  deviceDefinitionId: string;
  quantity: number | string;
  unitCode: string;
  performOrgId?: string;
  /** 绑定就诊诊断有序 ids 串（有序；主诊第一） */
  encounterDiagnosisIds?: string;
  /** 费用性质/合同主键（暂存挂账；与药品/诊疗同口径） */
  contractId?: string;
}

export interface DoctorStationAdviceDraftLineRequest {
  lineOp: string;
  requestId?: string;
  /** 住院批次级长临（与 medicationPayload.therapyEnum 二选一） */
  therapyTypeCode?: string;
  therapyEnum?: string;
  /** 与 `medicationPayload` 同级；与列表/目录 `productTableName` 一致 */
  productTableName?: string;
  /** 与 `medicationPayload` 同级；展示排序号（AS BR-ORD-018） */
  sortNumber?: number;
  /** 派生绑定行锚定表（IF-DOR-23 → IF-DOR-14） */
  basedOnTable?: string;
  /** 派生绑定行锚定主键（IF-DOR-23 → IF-DOR-14；成组可任取组内一条） */
  basedOnId?: string;
  /**
   * 组套标识：成组用法绑定耗材与主药 packageId/groupId 对齐；非成组可空。
   * 仅耗材草稿落库；诊疗无 package_id 列。
   */
  packageId?: string;
  medicationPayload?: SaveMedicationAdviceDraftLinePayload;
  servicePayload?: SaveServiceAdviceDraftLinePayload;
  devicePayload?: SaveDeviceAdviceDraftLinePayload;
}

export interface SaveDoctorStationAdviceDraftBatchRequest {
  encounterId: string;
  patientId: string;
  pricingSourceCode?: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  /**
   * 医生已确认皮试药可跨批号暂存（应对 EX-ORD-309 / BR-ORD-034 · IF-DOR-14）。
   * 首次保存若返回 EX-ORD-309，确认后带 true 重试。
   */
  confirmCrossLotSkinTest?: boolean;
}

export interface SaveDoctorStationAdviceDraftBatchResponse {
  success?: boolean;
  newMedicationRequestIds?: string[];
  message?: string;
}

export interface DoctorStationAdviceResourceKeyDto {
  /** 产品/申请侧表名（与 IF-DOR-14 暂存行 `productTableName` 口径一致，替代原 resourceType） */
  productTableName: string;
  requestId: string;
}

export interface SignDoctorStationAdviceBatchRequest {
  encounterId: string;
  patientId: string;
  signTargets: DoctorStationAdviceResourceKeyDto[];
  signBatchCode?: string;
  /**
   * 医生已确认皮试药可跨批号开立（应对 EX-ORD-309 / BR-ORD-034）。
   * 皮试跨批确认已在暂存完成；签发相位通常忽略本字段。
   */
  confirmCrossLotSkinTest?: boolean;
  /**
   * 医生已确认药耗 FIFO 跨批次取用及分段金额（应对 EX-ORD-310 / BR-ORD-035）。
   * 首次签发若返回 EX-ORD-310，确认后带 true 重试，本批不再提示。
   */
  confirmCrossLotPricing?: boolean;
}

export interface SignDoctorStationAdviceBatchResponse {
  success?: boolean;
  signBatchCode?: string;
  prescriptionNoByMedicationRequestId?: Record<string, string>;
  message?: string;
}

export interface WithdrawDoctorStationAdviceBatchRequest {
  encounterId: string;
  patientId: string;
  targets: DoctorStationAdviceResourceKeyDto[];
}

export interface WithdrawDoctorStationAdviceBatchResponse {
  success?: boolean;
  message?: string;
}

export interface AssignMedicationGroupPayload {
  groupId: string;
  medicationRequestIds: string[];
}

export interface UpdateDoctorStationMedicationAdviceGroupsRequest {
  encounterId: string;
  patientId: string;
  clearGroupIds?: string[];
  assignGroup?: AssignMedicationGroupPayload;
}

export interface UpdateDoctorStationMedicationAdviceGroupsResponse {
  success?: boolean;
  message?: string;
}

/** 与后端 `ListActivityExecutionLocationsRequest` 对齐：按诊疗定义查可执行科室规则 */
export interface ListActivityExecutionLocationsRequest {
  encounterId: string;
  patientId: string;
  /** 诊疗活动定义主键 */
  activityDefinitionId: string;
}

/** 与后端 `ActivityExecutionLocationRuleRowDto` 对齐 */
export interface ActivityExecutionLocationRuleRowDto {
  id?: string;
  activityDefinitionId?: string;
  chargeItemDefinitionId?: string;
  activityCategoryCode?: string;
  executionOrgId?: string;
  executionOrgName?: string;
  executionLocationId?: string;
  executionLocationName?: string;
  startTime?: string;
  endTime?: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

/** 与后端 `ListActivityExecutionLocationsResponse` 对齐 */
export interface ListActivityExecutionLocationsResponse {
  rules?: ActivityExecutionLocationRuleRowDto[];
}

/** 服务申请明细摘要（部位/样本） */
export interface ServiceRequestDetailSummaryDto {
  targetTable?: string;
  targetId?: string;
  targetDisplayName?: string;
  remark?: string;
}

/** 申请单保存：部位/样本明细行 */
export interface ServiceApplyFormDetailItemRequest {
  targetTable: string;
  targetId: string;
  remark?: string;
}

/** 申请单保存：主表一行 */
export interface ServiceApplyFormLineRequest {
  activityDefinitionId: string;
  quantity: number | string;
  unitCode: string;
  /** 本行执行次数；优先于整单 executionCount；空则后端回落整单或 1 */
  executionCount?: number;
  remark?: string;
  /** 医嘱优先级 code（按项目加急=EM） */
  priorityEnum?: string;
  detailItems?: ServiceApplyFormDetailItemRequest[];
}

/** 保存检验/检查/治疗申请单（整张） */
export interface SaveDoctorStationServiceApplyFormRequest {
  encounterId: string;
  patientId: string;
  /** 检验 PROOF/22、检查 TEST/23、治疗 TREATMENT/21 */
  categoryEnum: string;
  orgId: string;
  /**
   * 整单执行次数回落值（行未传 executionCount 时使用；空则后端回落 1）。
   * 新前端按行传 executionCount，可省略本字段。
   */
  executionCount?: number;
  /** 整单绑定就诊诊断有序 ids 串（有序；主诊第一；写入全部 ServiceRequest） */
  encounterDiagnosisIds?: string;
  /** 编辑时待替换的旧 AR 申请单号 */
  previousPrescriptionNo?: string;
  lines: ServiceApplyFormLineRequest[];
}

export interface SaveDoctorStationServiceApplyFormResponse {
  prescriptionNo?: string;
  requestIds?: string[];
}

/** 查询申请单列表 */
export interface ListDoctorStationServiceApplyFormsRequest {
  encounterId: string;
  patientId: string;
  categoryEnum: string;
}

/** 申请单明细行摘要 */
export interface ServiceApplyFormLineDto {
  requestId?: string;
  activityDefinitionId?: string;
  activityName?: string;
  quantity?: number | string;
  unitCode?: string;
  unitName?: string;
  remark?: string;
  /** 医嘱优先级 code（加急=EM） */
  priorityEnum?: string;
  details?: ServiceRequestDetailSummaryDto[];
}

/** 申请单头摘要 */
export interface ServiceApplyFormHeaderDto {
  prescriptionNo?: string;
  categoryEnum?: string;
  orgId?: string;
  orgName?: string;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  statusSummary?: string;
  lines?: ServiceApplyFormLineDto[];
}

export interface ListDoctorStationServiceApplyFormsResponse {
  forms?: ServiceApplyFormHeaderDto[];
}

/** IF-DOR-20：处方单查询范围 Tab */
export type PrescriptionScopeTabCode = 'CURRENT_ENCOUNTER' | 'HISTORY';

/** IF-DOR-20：就诊药品处方单分页请求（字段与门诊发药 slip 读口径对齐） */
export interface PageEncounterMedicationPrescriptionSlipsRequest {
  encounterId: string;
  patientId: string;
  prescriptionScopeTabCode?: PrescriptionScopeTabCode;
  orderingDepartmentId?: string;
  locationId?: string;
  tcmOnly?: boolean;
  westernOnly?: boolean;
  dispenseStatusEnumIn?: string[];
  requestStatusEnumIn?: string[];
  pageNo: number;
  pageSize: number;
}

/** 与门诊发药 IF-OPP-03 slip 行字段一致，医生站 IF-DOR-20 复用同一读模型 */
export type EncounterMedicationPrescriptionSlipRowDto = EncounterMedicationDispenseSlipRowDto;

/** IF-DOR-20：就诊药品处方单分页响应 */
export interface PageEncounterMedicationPrescriptionSlipsResponse {
  content?: EncounterMedicationPrescriptionSlipRowDto[];
  rows?: EncounterMedicationPrescriptionSlipRowDto[];
  totalCount?: number | string;
  total?: number | string;
}

/** IF-DOR-20：医嘱查询范围 Tab（与后端 DoctorStationAdviceScopeTab 对齐） */
export type DoctorStationAdviceScopeTabCode = 'CURRENT_ADVICE' | 'HISTORY';

/** 处方单 Tab → 医嘱查询范围 Tab */
export function mapPrescriptionScopeToAdviceScopeTab(
  scope: PrescriptionScopeTabCode,
): DoctorStationAdviceScopeTabCode {
  return scope === 'CURRENT_ENCOUNTER' ? 'CURRENT_ADVICE' : 'HISTORY';
}

/** 历史医嘱 / 处方单查看：分页查询请求（POST …/doctorStationAdvice/pageDoctorStationAdvice） */
export interface PageDoctorStationAdviceRequest {
  patientId: string;
  /** 当前就诊（患者门控 + 历史排除锚点） */
  encounterId: string;
  pageNo: number;
  pageSize: number;
  /** CURRENT_ADVICE=本次就诊；HISTORY=历史（排除当前 encounterId） */
  adviceScopeTabCode?: DoctorStationAdviceScopeTabCode;
}

/** 历史医嘱：单次就诊摘要 */
export interface PatientHistoryDoctorStationAdviceEncounterDto {
  encounterId?: string;
  visitTime?: string;
  departmentName?: string;
  visitTypeText?: string;
  primaryDiagnosis?: string;
  orderingDoctor?: string;
  adviceCountSummary?: string;
  isCurrentEncounter?: boolean;
}

/** 历史医嘱：单次就诊 + 医嘱行 */
export interface PatientHistoryDoctorStationAdviceBundleDto {
  encounter?: PatientHistoryDoctorStationAdviceEncounterDto;
  rows?: DoctorStationAdviceRowDto[];
}

/** 历史医嘱：分页查询响应 */
export interface PageDoctorStationAdviceResponse {
  bundles?: PatientHistoryDoctorStationAdviceBundleDto[];
  /** 分页包装或扁平 bundles / 结构-only 医嘱行 */
  content?: PatientHistoryDoctorStationAdviceBundleDto[] | DoctorStationAdviceRowDto[];
  /** 兼容：扁平 bundles 或结构-only 医嘱行 */
  rows?: PatientHistoryDoctorStationAdviceBundleDto[] | DoctorStationAdviceRowDto[];
  /** 结构-only 医嘱行 */
  adviceRows?: DoctorStationAdviceRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}
