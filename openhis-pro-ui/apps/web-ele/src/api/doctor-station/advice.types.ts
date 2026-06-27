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
  children?: TerminologyOptionDto[];
}

export interface InitDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
}

export interface InitDoctorStationAdviceResponse {
  medicationRateOptions?: TerminologyOptionDto[];
  medicationRouteOptions?: TerminologyOptionDto[];
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
  /** 列表「状态」列展示码（退费优先于请求态） */
  displayStatusEnum?: string;
  /** 列表「状态」列展示文案（待退费/已退费或请求态） */
  displayStatusEnumText?: string;
  contentSummary?: string;
  prescriptionNo?: string;
  groupId?: string;
  chargeStatusEnum?: string;
  chargeStatusEnumText?: string;
  skinTestFlag?: boolean;
  injectionFlag?: boolean;
  authoredById?: string;
  authoredByName?: string;
  authoredAt?: string;
  /** 列表行诊断摘要（装配器若返回则展示） */
  diagnosisSummary?: string;
  /** 诊断定义名称（列表「诊断」列，由 conditionDefinitionId 批量解析） */
  conditionDefinitionName?: string;
  /** 列表行金额合计（装配器若返回则展示） */
  lineTotalAmount?: number | string;
  signedById?: string;
  signedAt?: string;

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
  methodCode?: string;
  methodName?: string;
  rateCode?: string;
  rateName?: string;
  /** 用药天数（展示为 (N天)） */
  dispensePerDuration?: number | string | null;
  boundConditionName?: string;
  /** 行展示总价（元，与后端展示口径一致） */
  lineDisplayTotalPrice?: number | string;
  skinTestFlagText?: string;
  skinTestOutcomeCode?: string;
  skinTestOutcomeText?: string;
  /** 医生站主表展示排序号（AS BR-ORD-018） */
  sortNumber?: number | string | null;
  /** 诊疗执行次数（结构-only 行） */
  executionCount?: number | string | null;
  /** 目录分类码（如 2 西药、11 耗材、21 治疗） */
  catalogCategoryCode?: string;
  /** 活动类型码/名（诊疗） */
  activityTypeCode?: string;
  activityTypeName?: string;
  /** 执行科室 */
  performLocationId?: string;
  performLocationName?: string;
  fifoDispenseLocationId?: string;
  fifoDispenseLocationName?: string;
  /** 住院：长临医嘱 LONG_TERM / TEMPORARY */
  therapyTypeCode?: string;
  /** 住院：长临展示文案（如 长期、临时） */
  therapyTypeText?: string;
  generateSourceEnum?: string;
  stopTime?: string;
  conditionDefinitionId?: string;
  /** 前置服务申请主键（锚定 SR 时） */
  basedOnServiceRequestId?: string;
  /** 前置药品申请主键（皮试检查 companion 锚定皮试药） */
  basedOnMedicationRequestId?: string;
  /** 申请单行纯备注（wkf_service_request.remark） */
  remark?: string;
  /** 申请单部位/样本明细摘要（AR 前缀申请单） */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
}

export interface ListDoctorStationAdviceResponse {
  rows?: DoctorStationAdviceRowDto[];
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
  availableQuantity?: number | string;
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  /** 零售价等单价（与目录接口返回字段对齐） */
  unitPrice?: number | string;
  priceListJson?: string;
  chargeItemDefinitionId?: string;
  pricingSourceCode?: string;
  /** 多计量带数组；若后端仅给 JSON 字符串则用 `unitBandsJson` */
  unitBands?: DoctorStationMedicationUnitBandDto[];
  unitBandsJson?: string;
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
  /** 用药天数（对应 dispense_per_duration） */
  dispensePerDuration?: number;
  methodCode?: string;
  rateCode?: string;
  groupId?: string;
  dispenseLocationId?: string;
  /** 绑定诊断定义主键，与就诊诊断 `conditionDefinitionId` 一致 */
  conditionDefinitionId?: string;
  /** 就诊诊断主键（落库 `encounter_diagnosis_id`） */
  encounterDiagnosisId?: string;
  contractId?: string;
  /** 皮试标记（与列表 `skinTestFlag` 对齐） */
  skinTestFlag?: boolean;
  /** 用药方式：LONG_TERM 长期 / TEMPORARY 临时（住院） */
  therapyEnum?: string;
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
  /** 绑定诊断定义主键，与就诊诊断 `conditionDefinitionId` 一致 */
  conditionDefinitionId?: string;
  /** 套餐子项覆盖 JSON（仅套餐展开子项覆盖，不含频次/次数/单位） */
  childOverridesJson?: string;
}

export interface SaveDeviceAdviceDraftLinePayload {
  deviceDefinitionId: string;
  quantity: number | string;
  unitCode: string;
  performOrgId?: string;
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
  /** 派生绑定行锚定主键（IF-DOR-23 → IF-DOR-14） */
  basedOnId?: string;
  medicationPayload?: SaveMedicationAdviceDraftLinePayload;
  servicePayload?: SaveServiceAdviceDraftLinePayload;
  devicePayload?: SaveDeviceAdviceDraftLinePayload;
}

export interface SaveDoctorStationAdviceDraftBatchRequest {
  encounterId: string;
  patientId: string;
  pricingSourceCode?: string;
  lines: DoctorStationAdviceDraftLineRequest[];
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
  remark?: string;
  detailItems?: ServiceApplyFormDetailItemRequest[];
}

/** 保存检验/检查/治疗申请单（整张） */
export interface SaveDoctorStationServiceApplyFormRequest {
  encounterId: string;
  patientId: string;
  /** 检验 PROOF/22、检查 TEST/23、治疗 TREATMENT/21 */
  categoryEnum: string;
  orgId: string;
  /** 整单绑定诊断定义主键（与 orgId 同级，写入全部 ServiceRequest） */
  conditionDefinitionId?: string;
  /** 整单绑定就诊诊断主键 */
  encounterDiagnosisId?: string;
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
  details?: ServiceRequestDetailSummaryDto[];
}

/** 申请单头摘要 */
export interface ServiceApplyFormHeaderDto {
  prescriptionNo?: string;
  categoryEnum?: string;
  orgId?: string;
  orgName?: string;
  authoredAt?: string;
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
