import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/** 组套种类 */
export const ORDER_TEMPLATE_KIND_GENERAL = 'GENERAL';
export const ORDER_TEMPLATE_KIND_TCM = 'TCM';
export const ORDER_TEMPLATE_KIND_PRICING = 'PRICING';
/** 医生站申请单组套（检查/检验/治疗） */
export const ORDER_TEMPLATE_KIND_SERVICE_APPLY = 'SERVICE_APPLY';

/** 申请单组套行类别，与申请单目录 categoryEnum 一致，落在行 groupKey */
export const SERVICE_APPLY_TEMPLATE_CATEGORY_EXAM = 'TEST';
export const SERVICE_APPLY_TEMPLATE_CATEGORY_LAB = 'PROOF';
export const SERVICE_APPLY_TEMPLATE_CATEGORY_TREATMENT = 'TREATMENT';

export const SERVICE_APPLY_TEMPLATE_CATEGORY_OPTIONS = [
  { value: SERVICE_APPLY_TEMPLATE_CATEGORY_EXAM, label: '检查' },
  { value: SERVICE_APPLY_TEMPLATE_CATEGORY_LAB, label: '检验' },
  { value: SERVICE_APPLY_TEMPLATE_CATEGORY_TREATMENT, label: '治疗' },
];

/** 行类型 */
export const ORDER_TEMPLATE_LINE_KIND_MEDICATION = 'MEDICATION';
export const ORDER_TEMPLATE_LINE_KIND_SERVICE = 'SERVICE';
export const ORDER_TEMPLATE_LINE_KIND_DEVICE = 'DEVICE';

/** 生命周期动作 */
export const ORDER_TEMPLATE_LIFECYCLE_PUBLISH = 'PUBLISH';
export const ORDER_TEMPLATE_LIFECYCLE_RETIRE = 'RETIRE';
export const ORDER_TEMPLATE_LIFECYCLE_REACTIVATE = 'REACTIVATE';
export const ORDER_TEMPLATE_LIFECYCLE_DELETE = 'DELETE';

export interface OrderTemplateHeaderItem {
  orderTemplateId?: string;
  name?: string;
  templateCode?: string;
  bindingEnum?: string;
  bindingEnumText?: string;
  /** 归属对象 id：个人=医务人员，科室/全院/协定=组织 */
  objectId?: string;
  /** 归属对象展示名 */
  objectName?: string;
  sceneEnum?: string;
  sceneEnumText?: string;
  templateKindEnum?: string;
  templateKindEnumText?: string;
  templateSourceEnum?: string;
  templateSourceEnumText?: string;
  statusEnum?: string;
  statusEnumText?: string;
  sortWeight?: number;
  defaultChineseHerbsDoseQuantity?: number;
  defaultSufferingFlag?: boolean;
  description?: string;
  remark?: string;
}

/** 详情读模型，对齐后端 OrderTemplateLineRowDto（IF-03 响应 lines） */
export interface OrderTemplateLineRowItem {
  lineId?: string;
  lineKindEnum?: string;
  lineKindEnumText?: string;
  medicationDefinitionId?: string;
  activityDefinitionId?: string;
  deviceDefinitionId?: string;
  productTableName?: string;
  productName?: string;
  productSpec?: string;
  groupKey?: string;
  sortOrder?: number;
  defaultQuantity?: number;
  defaultUnitCode?: string;
  defaultUnitName?: string;
  defaultDays?: number;
  frequencyId?: string;
  frequencyText?: string;
  routeCode?: string;
  routeName?: string;
  dosageText?: string;
  defaultPerformOrgId?: string;
  defaultPerformOrgName?: string;
  lineRemark?: string;
  chineseHerbsDoseQuantity?: number;
  sufferingFlag?: boolean;
}

/** 保存行入参，对齐后端 OrderTemplateLineEditDto（IF-05 请求 lines） */
export interface OrderTemplateLineEditItem {
  lineId?: string;
  deleted?: boolean;
  lineKindEnum: string;
  medicationDefinitionId?: string;
  activityDefinitionId?: string;
  deviceDefinitionId?: string;
  groupKey?: string;
  sortOrder: number;
  defaultQuantity?: number;
  defaultUnitCode?: string;
  defaultDays?: number;
  frequencyId?: string;
  routeCode?: string;
  dosageText?: string;
  defaultPerformOrgId?: string;
  lineRemark?: string;
  chineseHerbsDoseQuantity?: number;
  sufferingFlag?: boolean;
}

export interface OrderTemplateInitResponse {
  bindingOptions?: ApiOptionItem[];
  sceneOptions?: ApiOptionItem[];
  templateKindOptions?: ApiOptionItem[];
  lineKindOptions?: ApiOptionItem[];
  statusOptions?: ApiOptionItem[];
  /** 中药用法（tcm_method_code） */
  tcmMethodCodeOptions?: ApiOptionItem[];
  /** 用药频次（rateCode，维护页展示用；组套行仍存 frequencyId） */
  medicationRateOptions?: ApiOptionItem[];
}

export interface PageMaintainedOrderTemplateParams extends PageParams {
  templateKindEnum: string;
  bindingEnum?: string;
  sceneEnum?: string;
  statusEnum?: string;
  keyword?: string;
  searchKey?: string;
}

export interface SaveOrderTemplateHeaderParams {
  orderTemplateId?: string;
  bindingEnum: string;
  objectId?: string;
  sceneEnum: string;
  templateKindEnum: string;
  templateCode?: string;
  name: string;
  description?: string;
  sortWeight?: number;
  remark?: string;
  defaultChineseHerbsDoseQuantity?: number;
  defaultSufferingFlag?: boolean;
}

export interface SaveOrderTemplateLinesParams {
  orderTemplateId: string;
  lines: OrderTemplateLineEditItem[];
}

export interface ChangeOrderTemplateLifecycleParams {
  orderTemplateId: string;
  lifecycleActionCode: string;
}

export interface GetOrderTemplateDetailParams {
  orderTemplateId: string;
}

export interface GetOrderTemplateDetailResponse {
  header?: OrderTemplateHeaderItem;
  lines?: OrderTemplateLineRowItem[];
}

/** 模板来源 */
export const ORDER_TEMPLATE_SOURCE_MAINTAINED = 'MAINTAINED';
export const ORDER_TEMPLATE_SOURCE_FROM_ADVICE = 'FROM_ADVICE';

/** 模板绑定范围 */
export const ORDER_TEMPLATE_BINDING_PERSONAL = 'PERSONAL';
export const ORDER_TEMPLATE_BINDING_DEPARTMENT = 'DEPARTMENT';
export const ORDER_TEMPLATE_BINDING_HOSPITAL = 'HOSPITAL';
export const ORDER_TEMPLATE_BINDING_AGREEMENT = 'AGREEMENT';

/** 套用上下文 */
export const ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR = 'DOCTOR';
export const ORDER_TEMPLATE_APPLY_CONTEXT_NURSE_PRICING = 'NURSE_PRICING';

/** 医生站工作站类型 */
export const ORDER_TEMPLATE_WORKSTATION_OUTPATIENT = 'OUTPATIENT';
export const ORDER_TEMPLATE_WORKSTATION_INPATIENT = 'INPATIENT';

export interface OrderTemplateBindingGroup {
  agreementList?: OrderTemplateHeaderItem[];
  personalList?: OrderTemplateHeaderItem[];
  departmentList?: OrderTemplateHeaderItem[];
  hospitalList?: OrderTemplateHeaderItem[];
}

export interface ListDoctorStationOrderTemplateParams {
  encounterId: string;
  patientId: string;
  templateSourceEnum: string;
  templateKindEnum: string;
  searchKey?: string;
}

export interface ListDoctorStationOrderTemplateResponse {
  groups?: OrderTemplateBindingGroup;
}

export interface PreviewOrderTemplateApplyParams {
  orderTemplateId: string;
  encounterId?: string;
  patientId?: string;
  applyContextCode: string;
  workstationKindCode?: string;
}

export interface PreviewOrderTemplateApplyResponse {
  header?: OrderTemplateHeaderItem;
  lines?: OrderTemplateLineRowItem[];
}

/** IF-BAS-OTL-10：另存组套源行键 */
export interface AdviceSourceLineKeyItem {
  requestId: string;
  productTableName: string;
  listRowKey?: string;
}

export interface SaveOrderTemplateFromAdviceParams {
  encounterId: string;
  patientId: string;
  templateName: string;
  bindingEnum?: string;
  sourceLines: AdviceSourceLineKeyItem[];
}

export interface SaveOrderTemplateFromAdviceResponse {
  orderTemplateId?: string;
}

export async function initOrderTemplateMaintenanceApi() {
  return publicRequestClient.post<OrderTemplateInitResponse>(
    '/orderTemplate/initOrderTemplateMaintenance',
  );
}

export async function pageMaintainedOrderTemplatesApi(params: PageMaintainedOrderTemplateParams) {
  return publicRequestClient.post<PageApiResponse<OrderTemplateHeaderItem>>(
    '/orderTemplate/pageMaintainedOrderTemplates',
    params,
  );
}

export async function getOrderTemplateDetailApi(params: GetOrderTemplateDetailParams) {
  return publicRequestClient.post<GetOrderTemplateDetailResponse>(
    '/orderTemplate/getOrderTemplateDetail',
    { orderTemplateId: params.orderTemplateId },
  );
}

export async function saveMaintainedOrderTemplateHeaderApi(params: SaveOrderTemplateHeaderParams) {
  return publicRequestClient.post<{ orderTemplateId: string }>(
    '/orderTemplate/saveMaintainedOrderTemplateHeader',
    params,
  );
}

export async function saveMaintainedOrderTemplateLinesApi(params: SaveOrderTemplateLinesParams) {
  return publicRequestClient.post<{ savedLineCount: number }>(
    '/orderTemplate/saveMaintainedOrderTemplateLines',
    params,
  );
}

export async function changeOrderTemplateLifecycleApi(params: ChangeOrderTemplateLifecycleParams) {
  return publicRequestClient.post<{ success: boolean }>(
    '/orderTemplate/changeOrderTemplateLifecycle',
    params,
  );
}

/** IF-BAS-OTL-07：医生站可套用组套/医嘱模板列表（个人/科室/全院分段） */
export async function listDoctorStationOrderTemplatesApi(
  params: ListDoctorStationOrderTemplateParams,
) {
  return publicRequestClient.post<ListDoctorStationOrderTemplateResponse>(
    '/orderTemplate/listDoctorStationOrderTemplates',
    params,
  );
}

/** IF-BAS-OTL-08：预览组套套用明细 */
export async function previewOrderTemplateApplyApi(params: PreviewOrderTemplateApplyParams) {
  return publicRequestClient.post<PreviewOrderTemplateApplyResponse>(
    '/orderTemplate/previewOrderTemplateApply',
    params,
  );
}

/** IF-BAS-OTL-10：医生站另存为组套/医嘱模板 */
export async function saveOrderTemplateFromAdviceApi(params: SaveOrderTemplateFromAdviceParams) {
  return publicRequestClient.post<SaveOrderTemplateFromAdviceResponse>(
    '/orderTemplate/saveOrderTemplateFromAdvice',
    params,
  );
}

/** IF-BAS-OTL-11：护士站划价组套列表（固定 PRICING + MAINTAINED） */
export interface ListNurseStationPricingOrderTemplateParams {
  searchKey?: string;
}

export interface ListNurseStationPricingOrderTemplateResponse {
  groups?: OrderTemplateBindingGroup;
}

export async function listNurseStationPricingOrderTemplatesApi(
  params: ListNurseStationPricingOrderTemplateParams = {},
) {
  return publicRequestClient.post<ListNurseStationPricingOrderTemplateResponse>(
    '/orderTemplate/listNurseStationPricingOrderTemplates',
    params,
  );
}
