import type { ApiResponse } from '#/types/api-response';

import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';
import { requestClient } from '#/api/request';

/**
 * 药品目录分页查询接口参数
 */
export interface PageMedicationCatalogParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  name?: string;
  categoryCode?: string;
}

/**
 * 药品目录项
 */
export interface MedicationCatalogItem {
  id?: string;
  name?: string;
  categoryCode?: string;
  /** PublicationStatus#getCode()，如 ACTIVE、INACTIVE（字符串，勿与枚举序列号混用） */
  statusEnum?: string;
  medicationDefId?: string | number;
  /** 医保对码等布尔标志位（与后端 Boolean JSON 一致） */
  ybMatchFlag?: boolean;
  /** 五类单位槽（采购/拆零/包装/库存/剂量），对齐门诊医生站目录 */
  unitBands?: DoctorStationMedicationUnitBandDto[];
  unitBandsJson?: string;
  [key: string]: unknown;
}

/**
 * 药品目录分页查询响应
 */
export interface PageMedicationCatalogResponse {
  content?: MedicationCatalogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/**
 * 药品目录分页查询
 */
export async function pageMedicationCatalogApi(
  params: PageMedicationCatalogParams,
): Promise<ApiResponse<PageMedicationCatalogResponse>> {
  return requestClient.post<ApiResponse<PageMedicationCatalogResponse>>(
    '/medicationCatalog/pageMedicationCatalog',
    params,
  );
}

/**
 * 药品目录更新接口参数
 */
export interface UpdateMedicationCatalogParams {
  id?: string;
  name?: string;
  categoryCode?: string;
  [key: string]: unknown;
}

/**
 * 药品目录更新
 */
export async function updateMedicationCatalogApi(
  params: UpdateMedicationCatalogParams,
): Promise<ApiResponse<MedicationCatalogItem>> {
  return requestClient.post<ApiResponse<MedicationCatalogItem>>(
    '/medicationCatalog/editMedicationCatalog',
    params,
  );
}

/**
 * 药品目录创建接口参数
 */
export interface CreateMedicationCatalogParams {
  name?: string;
  categoryCode?: string;
  [key: string]: unknown;
}

/**
 * 药品目录创建
 */
export async function createMedicationCatalogApi(
  params: CreateMedicationCatalogParams,
): Promise<ApiResponse<MedicationCatalogItem>> {
  return requestClient.post<ApiResponse<MedicationCatalogItem>>(
    '/medicationCatalog/createMedicationCatalog',
    params,
  );
}

/**
 * 获取药品目录详情
 */
export async function getMedicationCatalogApi(
  params: string,
): Promise<ApiResponse<MedicationCatalogItem>> {
  return requestClient.post<ApiResponse<MedicationCatalogItem>>(
    '/medicationCatalog/getMedicationCatalog',
    params,
  );
}

/**
 * 停用药品目录
 */
export async function stopMedicationCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/medicationCatalog/stopMedicationCatalog', ids);
}

/**
 * 启用药品目录
 */
export async function startMedicationCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/medicationCatalog/startMedicationCatalog', ids);
}

/**
 * 批量删除（废弃）药品目录
 */
export async function deleteMedicationCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/medicationCatalog/deleteMedicationCatalog', ids);
}

/**
 * 药品目录初始化响应
 */
export interface MedicationCatalogInitResponse {
  [key: string]: unknown;
}

/**
 * 药品目录初始化
 */
export async function medicationCatalogInitApi(): Promise<
  ApiResponse<MedicationCatalogInitResponse>
> {
  return requestClient.post<ApiResponse<MedicationCatalogInitResponse>>(
    '/medicationCatalog/getMedicationInit',
    {},
  );
}

/**
 * 诊疗分页查询接口参数
 */
export interface PageServiceCatalogParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  name?: string;
  categoryCode?: string;
}

/**
 * 诊疗项
 */
export interface ServiceCatalogItem {
  id?: string;
  name?: string;
  categoryCode?: string;
  [key: string]: unknown;
}

/**
 * 诊疗分页查询响应
 */
export interface PageServiceCatalogResponse {
  content?: ServiceCatalogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/**
 * 诊疗分页查询
 */
export async function pageServiceCatalogApi(
  params: PageServiceCatalogParams,
): Promise<ApiResponse<PageServiceCatalogResponse>> {
  return requestClient.post<ApiResponse<PageServiceCatalogResponse>>(
    '/serviceCatalog/pageServiceCatalog',
    params,
  );
}

/**
 * 获取诊疗详情接口参数
 */
export interface GetServiceCatalogParams {
  id: string;
}

/**
 * 获取诊疗详情
 */
export async function getServiceCatalogApi(
  params: GetServiceCatalogParams,
): Promise<ApiResponse<ServiceCatalogItem>> {
  return requestClient.post<ApiResponse<ServiceCatalogItem>>(
    '/serviceCatalog/getServiceCatalog',
    params,
  );
}

/**
 * 诊疗更新接口参数
 */
export interface UpdateServiceCatalogParams {
  id?: string;
  name?: string;
  categoryCode?: string;
  [key: string]: unknown;
}

/**
 * 诊疗更新
 */
export async function updateServiceCatalogApi(
  params: UpdateServiceCatalogParams,
): Promise<ApiResponse<ServiceCatalogItem>> {
  return requestClient.post<ApiResponse<ServiceCatalogItem>>(
    '/serviceCatalog/editServiceCatalog',
    params,
  );
}

/**
 * 诊疗创建接口参数
 */
export interface CreateServiceCatalogParams {
  name?: string;
  categoryCode?: string;
  [key: string]: unknown;
}

/**
 * 诊疗创建
 */
export async function createServiceCatalogApi(
  params: CreateServiceCatalogParams,
): Promise<ApiResponse<ServiceCatalogItem>> {
  return requestClient.post<ApiResponse<ServiceCatalogItem>>(
    '/serviceCatalog/createServiceCatalog',
    params,
  );
}

/**
 * 诊疗初始化响应
 */
export interface ServiceCatalogInitResponse {
  [key: string]: unknown;
}

/**
 * 诊疗初始化
 */
export async function serviceCatalogInitApi(): Promise<ApiResponse<ServiceCatalogInitResponse>> {
  return requestClient.post<ApiResponse<ServiceCatalogInitResponse>>(
    '/serviceCatalog/getDiseaseTreatmentInit',
    {},
  );
}

/**
 * 停用诊疗
 */
export async function stopServiceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/serviceCatalog/stopServiceCatalog', ids);
}

/**
 * 启用诊疗
 */
export async function startServiceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/serviceCatalog/startServiceCatalog', ids);
}

/**
 * 批量删除诊疗目录
 */
export async function deleteServiceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/serviceCatalog/deleteServiceCatalog', ids);
}

/**
 * 设备目录分页查询接口参数
 */
export interface PageDeviceCatalogParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  categoryCode?: string;
  /** 耗材种类（device_type_code），与列表左侧筛选一致 */
  typeCode?: string;
}

/**
 * 设备目录项
 */
export interface DeviceCatalogItem {
  id?: string;
  busNo?: string;
  name?: string;
  merchandiseName?: string;
  [key: string]: unknown;
}

/**
 * 设备目录分页查询响应
 */
export interface PageDeviceCatalogResponse {
  content?: DeviceCatalogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/**
 * 设备目录分页查询
 */
export async function pageDeviceCatalogApi(
  params: PageDeviceCatalogParams,
): Promise<ApiResponse<PageDeviceCatalogResponse>> {
  return requestClient.post<ApiResponse<PageDeviceCatalogResponse>>(
    '/deviceCatalog/pageDeviceCatalog',
    params,
  );
}

/**
 * 获取设备目录详情
 */
export async function getDeviceCatalogApi(id: string): Promise<ApiResponse<DeviceCatalogItem>> {
  return requestClient.post<ApiResponse<DeviceCatalogItem>>('/deviceCatalog/getDeviceCatalog', id);
}

/**
 * 设备目录更新接口参数
 */
export interface UpdateDeviceCatalogParams extends Partial<CreateDeviceCatalogParams> {
  id?: string;
  /** 更新门禁业务码，与详情 editabilityCode 对齐 */
  editabilityCode?: string;
  [key: string]: unknown;
}

/**
 * 设备目录更新
 */
export async function updateDeviceCatalogApi(
  params: UpdateDeviceCatalogParams,
): Promise<ApiResponse<DeviceCatalogItem>> {
  return requestClient.post<ApiResponse<DeviceCatalogItem>>(
    '/deviceCatalog/editDeviceCatalog',
    params,
  );
}

/**
 * 设备目录创建接口参数
 */
export interface CreateDeviceCatalogParams {
  busNo?: string;
  name: string;
  merchandiseName?: string;
  pyStr?: string;
  wbStr?: string;
  categoryCode?: string;
  typeCode?: string;
  /** 采购单位（持久化 purchase_unit_code） */
  purchaseUnitCode: string;
  /** 拆零单位（持久化 split_unit_code） */
  splitUnitCode: string;
  /** @deprecated 请使用 purchaseUnitCode */
  unitCode?: string;
  /** 包装规格（与药品 totalVolume 一致） */
  totalVolume?: string;
  /** @deprecated 请使用 splitUnitCode */
  minUnitCode?: string;
  /** 默认门诊/就诊单位（对齐 adm_device_definition.def_encounter_unit_code） */
  defEncounterUnitCode?: string;
  /** 默认住院单位（对齐 def_inhospital_unit_code） */
  defInhospitalUnitCode?: string;
  /** 库存单位 */
  stockUnitCode?: string;
  /** 剂量单位 */
  doseUnitCode?: string;
  orgId?: number;
  orgId_dictText?: string;
  locationId?: number;
  locationId_dictText?: string;
  modelNumber?: string;
  hvcmFlag?: boolean;
  /** 包装单位（持久化 package_unit_code） */
  packageUnitCode?: string;
  approvalNumber?: string;
  ybFlag?: boolean;
  ybNo?: string;
  ybOrgNo?: string;
  ybMatchFlag?: boolean;
  manufacturerId?: number;
  manufacturerText?: string;
  supplyId?: number;
  description?: string;
  jurisdiction?: string;
  version?: string;
  substanceText?: string;
  allergenFlag?: boolean;
  purchasePrice?: number;
  retailPrice?: number;
  maximumRetailPrice?: number;
  itemTypeCode?: string;
  ybType?: string;
  chrgitmLv?: number;
  itemMinQuantity?: number;
  itemMaxQuantity?: number;
  /** 五单位换算倍数：以拆零单位为 1，其余为倍数 */
  doseUnitRatio?: number;
  purchaseUnitRatio?: number;
  stockUnitRatio?: number;
  /** 包装单位相对拆零单位换算比 */
  packageUnitRatio?: number;
}

/**
 * 设备目录创建
 */
export async function createDeviceCatalogApi(
  params: CreateDeviceCatalogParams,
): Promise<ApiResponse<DeviceCatalogItem>> {
  return requestClient.post<ApiResponse<DeviceCatalogItem>>(
    '/deviceCatalog/createDeviceCatalog',
    params,
  );
}

/**
 * 设备目录初始化响应
 */
export interface DeviceCatalogInitResponse {
  [key: string]: unknown;
}

/**
 * 初始化设备目录（获取分类、状态、单位等选项）
 */
export async function initDeviceCatalogApi(): Promise<ApiResponse<DeviceCatalogInitResponse>> {
  return requestClient.post<ApiResponse<DeviceCatalogInitResponse>>('/deviceCatalog/init', {});
}

/**
 * 批量启用设备目录
 */
export async function batchStartDeviceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/deviceCatalog/startDeviceCatalog', ids);
}

/**
 * 批量停用设备目录
 */
export async function batchStopDeviceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/deviceCatalog/stopDeviceCatalog', ids);
}

/**
 * 批量删除设备目录（耗材定义）
 *
 * 约定：后端按 ids 批量逻辑删除/禁用；若仅支持单条删除也可传单元素数组。
 */
export async function deleteDeviceCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/deviceCatalog/deleteDeviceCatalog', ids);
}

/**
 * 诊断目录分页查询接口参数
 */
export interface PageDiagnosisCatalogParams {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  sourceEnum?: string;
}

/**
 * 诊断目录项
 */
export interface DiagnosisCatalogItem {
  id?: string;
  conditionCode?: string;
  name?: string;
  sourceEnum?: string;
  pyStr?: string;
  wbStr?: string;
  typeCode?: string;
  description?: string;
  ybFlag?: number | boolean;
  diseaseCode?: string;
  ybNo?: string;
  ybMatchFlag?: number | boolean;
  statusEnum?: string;
  [key: string]: unknown;
}

/**
 * 诊断目录分页查询响应
 */
export interface PageDiagnosisCatalogResponse {
  content?: DiagnosisCatalogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/**
 * 诊断目录分页查询
 */
export async function pageDiagnosisCatalogApi(
  params: PageDiagnosisCatalogParams,
): Promise<ApiResponse<PageDiagnosisCatalogResponse>> {
  return requestClient.post<ApiResponse<PageDiagnosisCatalogResponse>>(
    '/diagnosisCatalog/pageDiagnosisCatalog',
    params,
  );
}

/**
 * 根据 ID 获取诊断目录详情
 */
export async function getDiagnosisCatalogApi(
  id: string,
): Promise<ApiResponse<DiagnosisCatalogItem>> {
  return requestClient.post<ApiResponse<DiagnosisCatalogItem>>(
    '/diagnosisCatalog/getDiagnosisCatalog',
    id,
  );
}

/**
 * 删除诊断目录
 */
export async function deleteDiagnosisCatalogApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/diagnosisCatalog/deleteDiagnosisCatalog', id);
}

/**
 * 诊断目录初始化响应
 */
export interface DiagnosisCatalogInitResponse {
  [key: string]: unknown;
}

/**
 * 初始化诊断目录（获取分类、状态、医保诊疗类型等）
 */
export async function initDiagnosisCatalogApi(): Promise<
  ApiResponse<DiagnosisCatalogInitResponse>
> {
  return requestClient.post<ApiResponse<DiagnosisCatalogInitResponse>>(
    '/diagnosisCatalog/init',
    {},
  );
}

/**
 * 诊断目录创建接口参数
 */
export interface CreateDiagnosisCatalogParams {
  conditionCode?: string;
  name?: string;
  sourceEnum?: string;
  pyStr?: string;
  wbStr?: string;
  typeCode?: string;
  description?: string;
  ybFlag?: number;
  diseaseCode?: string;
  ybNo?: string;
  ybMatchFlag?: number;
  statusEnum?: string;
  [key: string]: unknown;
}

/**
 * 诊断目录创建
 */
export async function createDiagnosisCatalogApi(
  params: CreateDiagnosisCatalogParams,
): Promise<ApiResponse<DiagnosisCatalogItem>> {
  return requestClient.post<ApiResponse<DiagnosisCatalogItem>>(
    '/diagnosisCatalog/createDiagnosisCatalog',
    params,
  );
}

/**
 * 诊断目录更新接口参数
 */
export interface UpdateDiagnosisCatalogParams extends CreateDiagnosisCatalogParams {
  id?: string;
}

/**
 * 诊断目录更新
 */
export async function updateDiagnosisCatalogApi(
  params: UpdateDiagnosisCatalogParams,
): Promise<ApiResponse<DiagnosisCatalogItem>> {
  return requestClient.post<ApiResponse<DiagnosisCatalogItem>>(
    '/diagnosisCatalog/editDiagnosisCatalog',
    params,
  );
}

/**
 * 批量启用诊断目录
 */
export async function batchEnableDiagnosisCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/diagnosisCatalog/batchEnable', ids);
}

/**
 * 批量禁用诊断目录
 */
export async function batchDisableDiagnosisCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/diagnosisCatalog/batchDisable', ids);
}
