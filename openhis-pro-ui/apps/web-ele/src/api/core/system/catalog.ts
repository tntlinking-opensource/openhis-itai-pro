import type { ApiOptionItem, ApiResponse } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

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
  /** 重点药类型（四旗 KeyDrugType code）；undefined 表示不限 */
  keyDrugType?: string;
  /** 药品性质（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；undefined 表示不限 */
  medicinePropertyCode?: string;
  /** 发布状态（ACTIVE/INACTIVE）；undefined 表示不限 */
  statusEnum?: string;
  /** 医保对码；undefined 表示不限；true=已对码；false=未对码 */
  ybMatchFlag?: boolean;
  /** 医保编码模糊；undefined 表示不限 */
  ybNo?: string;
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
  /** 生产厂家主键（写请求须 string） */
  manufacturerId?: string;
  /** 生产厂家展示名（只读；列表/详情由后端按 manufacturerId 回填） */
  manufacturerText?: string;
  /** 医保对码等布尔标志位（与后端 Boolean JSON 一致） */
  ybMatchFlag?: boolean;
  /** 采购→拆零换算比（1 采购单位 = N 拆零） */
  purchaseUnitRatio?: null | number | string;
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
  totalCount?: number;
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
 * 目录编辑可编辑性（validate*Edit / 详情回填）
 */
export interface CatalogEditabilityResult {
  editabilityCode?: string;
  editabilityMessage?: string;
}

/**
 * 药品目录更新接口参数
 */
export interface UpdateMedicationCatalogParams {
  id?: string;
  name?: string;
  categoryCode?: string;
  /** 更新门禁业务码，与详情 editabilityCode 对齐 */
  editabilityCode?: string;
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
 * 药品目录编辑前校验（业务引用 / 可编辑性）
 */
export async function validateMedicationCatalogEditApi(
  id: string,
): Promise<ApiResponse<CatalogEditabilityResult>> {
  return requestClient.post<ApiResponse<CatalogEditabilityResult>>(
    '/medicationCatalog/validateMedicationCatalogEdit',
    id,
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
 * 目录批号进价/售价行（当前 ACTIVE 批号维真实价）
 */
export interface CatalogLotPriceRow {
  /** 批号 */
  lotNumber?: string;
  /** 批号进价（元/包装单位，LOT_NUMBER_COST） */
  costPrice?: number | string;
  /** 批号售价（元/包装单位，LOT_NUMBER_PRICE） */
  salePrice?: number | string;
}

/**
 * 查询药品批号进价/售价请求
 */
export interface ListMedicationLotPriceParams {
  /** 药品定义主键（字符串，避免 Long 精度丢失） */
  id: string;
  /** 批号过滤（可空，空=全部批号） */
  lotNumber?: string;
}

/**
 * 查询药品当前 ACTIVE 批号进价/售价
 */
export async function listMedicationLotPricesApi(
  params: ListMedicationLotPriceParams,
): Promise<ApiResponse<CatalogLotPriceRow[]>> {
  return requestClient.post<ApiResponse<CatalogLotPriceRow[]>>(
    '/medicationCatalog/listMedicationLotPrices',
    params,
  );
}

/**
 * 药品目录初始化响应
 */
export interface MedicationCatalogInitResponse {
  /** 生产厂家下拉（cus_supplier type=1；value=id 字符串） */
  manufacturerOptions?: ApiOptionItem[];
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

/** 药品目录导入/模板文件下载响应（byte[] → base64） */
export interface MedicationCatalogImportFileResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

/** 启动异步导入响应 */
export interface StartMedicationCatalogImportResult {
  jobId?: string;
}

/** 导入进度轮询响应 */
export interface MedicationCatalogImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  message?: string;
  batchNo?: string;
  successCount?: number;
  errorFileToken?: string;
  errorCount?: number;
  errorMessages?: string[];
}

/**
 * 下载药品目录导入模板（xlsx）
 */
export async function downloadMedicationCatalogImportTemplateApi(): Promise<MedicationCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<MedicationCatalogImportFileResult>>(
    '/medicationCatalog/downloadImportTemplate',
    {},
  );
  return res.data ?? {};
}

/** 药品目录导出请求（筛选与列表一致） */
export interface ExportMedicationCatalogParams {
  keyword?: string;
  categoryCode?: string;
  /** 重点药类型（四旗 KeyDrugType code）；undefined 表示不限 */
  keyDrugType?: string;
  /** 药品性质（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；undefined 表示不限 */
  medicinePropertyCode?: string;
  /** 发布状态（ACTIVE/INACTIVE）；undefined 表示不限 */
  statusEnum?: string;
  /** 医保对码；undefined 表示不限；true=已对码；false=未对码 */
  ybMatchFlag?: boolean;
  /** 医保编码模糊；undefined 表示不限 */
  ybNo?: string;
  /** 导入模板表头序（编号 + COLUMN_HEADERS）；后端按中文表头匹配 */
  exportColumns?: ExcelExportColumnPayload[];
}

/** 药品目录导出响应 */
export interface ExportMedicationCatalogResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

/**
 * 按筛选条件导出药品目录（xlsx，列结构与导入模板一致）
 */
export async function exportMedicationCatalogApi(
  params: ExportMedicationCatalogParams,
): Promise<ExportMedicationCatalogResult> {
  const res = await requestClient.post<ApiResponse<ExportMedicationCatalogResult>>(
    '/medicationCatalog/exportMedicationCatalog',
    params,
  );
  return res.data ?? {};
}

/** 耗材目录导出请求（筛选与列表一致） */
export interface ExportDeviceCatalogParams {
  keyword?: string;
  categoryCode?: string;
  typeCode?: string;
  /** 导入模板表头序（编号 + COLUMN_HEADERS）；后端按中文表头匹配 */
  exportColumns?: ExcelExportColumnPayload[];
}

/** 耗材目录导出响应 */
export interface ExportDeviceCatalogResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

/**
 * 按筛选条件导出耗材目录（xlsx，单 Sheet；首列编号 + 导入字段序）
 */
export async function exportDeviceCatalogApi(
  params: ExportDeviceCatalogParams,
): Promise<ExportDeviceCatalogResult> {
  const res = await requestClient.post<ApiResponse<ExportDeviceCatalogResult>>(
    '/deviceCatalog/exportDeviceCatalog',
    params,
  );
  return res.data ?? {};
}

/** 诊疗目录导出请求（筛选与列表一致） */
export interface ExportServiceCatalogParams {
  keyword?: string;
  categoryCode?: string;
  statusEnum?: string;
  /** 是否已绑定套餐子项；不传表示不限 */
  childrenFlag?: boolean;
  /** 导入模板表头序（编号 + COLUMN_HEADERS）；后端按中文表头匹配 */
  exportColumns?: ExcelExportColumnPayload[];
}

/** 诊疗目录导出响应 */
export interface ExportServiceCatalogResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

/**
 * 按筛选条件导出诊疗目录（xlsx，单 Sheet；首列编号 + 导入字段序）
 */
export async function exportServiceCatalogApi(
  params: ExportServiceCatalogParams,
): Promise<ExportServiceCatalogResult> {
  const res = await requestClient.post<ApiResponse<ExportServiceCatalogResult>>(
    '/serviceCatalog/exportServiceCatalog',
    params,
  );
  return res.data ?? {};
}

/**
 * 启动药品目录异步导入（返回 jobId，配合进度轮询）
 */
export async function startImportMedicationCatalogApi(body: {
  fileBytes: string;
  fileName?: string;
}): Promise<StartMedicationCatalogImportResult> {
  const res = await requestClient.post<ApiResponse<StartMedicationCatalogImportResult>>(
    '/medicationCatalog/startImportMedicationCatalog',
    body,
  );
  return res.data ?? {};
}

/**
 * 轮询药品目录导入进度
 */
export async function getImportMedicationCatalogProgressApi(
  jobId: string,
): Promise<MedicationCatalogImportProgressResult> {
  const res = await requestClient.post<ApiResponse<MedicationCatalogImportProgressResult>>(
    '/medicationCatalog/getImportMedicationCatalogProgress',
    { jobId },
  );
  return res.data ?? {};
}

/**
 * 下载药品目录导入错误标注文件
 */
export async function downloadMedicationCatalogImportErrorFileApi(
  token: string,
): Promise<MedicationCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<MedicationCatalogImportFileResult>>(
    '/medicationCatalog/downloadImportErrorFile',
    { token },
  );
  return res.data ?? {};
}

/** 耗材目录导入/模板文件下载响应（byte[] → base64） */
export interface DeviceCatalogImportFileResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

/** 启动耗材异步导入响应 */
export interface StartDeviceCatalogImportResult {
  jobId?: string;
}

/** 耗材目录导入进度轮询响应 */
export interface DeviceCatalogImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  message?: string;
  batchNo?: string;
  successCount?: number;
  errorFileToken?: string;
  errorCount?: number;
  errorMessages?: string[];
}

/**
 * 下载耗材目录导入模板（xlsx）
 */
export async function downloadDeviceCatalogImportTemplateApi(): Promise<DeviceCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<DeviceCatalogImportFileResult>>(
    '/deviceCatalog/downloadImportTemplate',
    {},
  );
  return res.data ?? {};
}

/**
 * 启动耗材目录异步导入（返回 jobId，配合进度轮询）
 */
export async function startImportDeviceCatalogApi(body: {
  fileBytes: string;
  fileName?: string;
}): Promise<StartDeviceCatalogImportResult> {
  const res = await requestClient.post<ApiResponse<StartDeviceCatalogImportResult>>(
    '/deviceCatalog/startImportDeviceCatalog',
    body,
  );
  return res.data ?? {};
}

/**
 * 轮询耗材目录导入进度
 */
export async function getImportDeviceCatalogProgressApi(
  jobId: string,
): Promise<DeviceCatalogImportProgressResult> {
  const res = await requestClient.post<ApiResponse<DeviceCatalogImportProgressResult>>(
    '/deviceCatalog/getImportDeviceCatalogProgress',
    { jobId },
  );
  return res.data ?? {};
}

/**
 * 下载耗材目录导入错误标注文件
 */
export async function downloadDeviceCatalogImportErrorFileApi(
  token: string,
): Promise<DeviceCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<DeviceCatalogImportFileResult>>(
    '/deviceCatalog/downloadImportErrorFile',
    { token },
  );
  return res.data ?? {};
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
  statusEnum?: string;
  /** 是否已绑定套餐子项；不传表示不限 */
  childrenFlag?: boolean;
  /**
   * 是否包含已被套餐引用的子诊疗。
   * - 目录维护 / 子项选择器：true（或不传，后端目录 IF 默认 true）
   * - 医嘱组套等临床选品：false
   */
  includeReferencedPackageChildren?: boolean;
}

/**
 * 诊疗项（列表/详情共用字段子集；详情另含 children、editability* 等）
 */
export interface ServiceCatalogItem {
  id?: string;
  busNo?: string;
  name?: string;
  categoryCode?: string;
  categoryCode_dictText?: string;
  packageUnitCode?: string;
  packageUnitCode_dictText?: string;
  defEncounterUnitCode?: string;
  defEncounterUnitCode_dictText?: string;
  retailPrice?: number | string;
  statusEnum?: number;
  statusEnum_enumText?: string;
  /**
   * 是否已绑定套餐子项（对齐 `wkf_activity_definition.children_flag`）。
   * 列表为 Boolean；详情为 0/1 整型。
   */
  childrenFlag?: boolean | number;
  [key: string]: unknown;
}

/**
 * 诊疗分页查询响应
 */
export interface PageServiceCatalogResponse {
  content?: ServiceCatalogItem[];
  totalCount?: number;
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
 * 诊疗目录编辑前校验（业务引用 / 可编辑性）
 */
export async function validateServiceCatalogEditApi(
  params: GetServiceCatalogParams,
): Promise<ApiResponse<CatalogEditabilityResult>> {
  return requestClient.post<ApiResponse<CatalogEditabilityResult>>(
    '/serviceCatalog/validateServiceCatalogEdit',
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
  /** 更新门禁业务码，与详情 editabilityCode 对齐 */
  editabilityCode?: string;
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

/** 诊疗目录导入/模板文件下载响应（byte[] → base64） */
export interface ServiceCatalogImportFileResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

/** 诊疗目录批量导入成功响应 */
export interface ImportServiceCatalogResult {
  batchNo?: string;
  totalCount?: number;
  successCount?: number;
  failCount?: number;
}

/** 启动诊疗异步导入响应 */
export interface StartServiceCatalogImportResult {
  jobId?: string;
}

/** 诊疗目录导入进度轮询响应 */
export interface ServiceCatalogImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  message?: string;
  batchNo?: string;
  successCount?: number;
  errorFileToken?: string;
  errorCount?: number;
  errorMessages?: string[];
}

/**
 * 下载诊疗目录导入模板（xlsx）
 */
export async function downloadServiceCatalogImportTemplateApi(): Promise<ServiceCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<ServiceCatalogImportFileResult>>(
    '/serviceCatalog/downloadImportTemplate',
    {},
  );
  return res.data ?? {};
}

/**
 * 启动诊疗目录异步导入（返回 jobId，配合进度轮询）
 */
export async function startImportServiceCatalogApi(body: {
  fileBytes: string;
  fileName?: string;
}): Promise<StartServiceCatalogImportResult> {
  const res = await requestClient.post<ApiResponse<StartServiceCatalogImportResult>>(
    '/serviceCatalog/startImportServiceCatalog',
    body,
  );
  return res.data ?? {};
}

/**
 * 轮询诊疗目录导入进度
 */
export async function getImportServiceCatalogProgressApi(
  jobId: string,
): Promise<ServiceCatalogImportProgressResult> {
  const res = await requestClient.post<ApiResponse<ServiceCatalogImportProgressResult>>(
    '/serviceCatalog/getImportServiceCatalogProgress',
    { jobId },
  );
  return res.data ?? {};
}

/**
 * 下载诊疗目录导入错误标注文件
 */
export async function downloadServiceCatalogImportErrorFileApi(
  token: string,
): Promise<ServiceCatalogImportFileResult> {
  const res = await requestClient.post<ApiResponse<ServiceCatalogImportFileResult>>(
    '/serviceCatalog/downloadImportErrorFile',
    { token },
  );
  return res.data ?? {};
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
  totalCount?: number;
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
 * 器材目录编辑前校验（业务引用 / 可编辑性）
 */
export async function validateDeviceCatalogEditApi(
  id: string,
): Promise<ApiResponse<CatalogEditabilityResult>> {
  return requestClient.post<ApiResponse<CatalogEditabilityResult>>(
    '/deviceCatalog/validateDeviceCatalogEdit',
    id,
  );
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
  /** 包装规格（与药品 totalVolume 一致） */
  totalVolume?: string;
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
  /** 收费耗材标记（true=收费；界面「收费耗材」勾选对应 true） */
  chargeableFlag?: boolean;
  /** 病案结算类型编码（术语 mr_settlement_type_code） */
  mrSettlementTypeCode?: string;
  /** 包装单位（持久化 package_unit_code） */
  packageUnitCode?: string;
  approvalNumber?: string;
  ybFlag?: boolean;
  ybNo?: string;
  ybMatchFlag?: boolean;
  /** 生产厂家主键（cus_supplier type=1；写请求须 string，避免雪花精度丢失） */
  manufacturerId?: string;
  /** 生产厂家展示名（只读；后端按 manufacturerId 解析） */
  manufacturerText?: string;
  /** 供应商主键（写请求须 string） */
  supplyId?: string;
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
  /** 追溯码贴码单位（可游离于五类业务单位；耗材可选） */
  traceUnitCode?: string;
  /** 1 个追溯码单位对应的拆零数量；有 traceUnitCode 时建议 > 0 */
  traceUnitRatio?: number;
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
  /** 生产厂家下拉（cus_supplier type=1；value=id 字符串） */
  manufacturerOptions?: ApiOptionItem[];
  /** 供应商下拉（兼容旧字段） */
  supplierListOptions?: ApiOptionItem[];
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
 * 查询耗材批号进价/售价请求
 */
export interface ListDeviceLotPriceParams {
  /** 耗材定义主键（字符串） */
  id: string;
  /** 批号过滤（可空，空=全部批号） */
  lotNumber?: string;
}

/**
 * 查询耗材当前 ACTIVE 批号进价/售价
 */
export async function listDeviceLotPricesApi(
  params: ListDeviceLotPriceParams,
): Promise<ApiResponse<CatalogLotPriceRow[]>> {
  return requestClient.post<ApiResponse<CatalogLotPriceRow[]>>(
    '/deviceCatalog/listDeviceLotPrices',
    params,
  );
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
  ybNo?: string;
  /** 国家临床版诊断编码（国临码）；医保目录列表可逗号拼接多个 */
  clinicCode?: string;
  /** 国家临床版诊断名称；医保目录列表可逗号拼接多个 */
  clinicName?: string;
  /** 医保诊断名称（由 ybNo 反查；未对码为空） */
  ybName?: string;
  ybMatchFlag?: number | boolean;
  statusEnum?: string;
  [key: string]: unknown;
}

/**
 * 诊断目录分页查询响应
 */
export interface PageDiagnosisCatalogResponse {
  content?: DiagnosisCatalogItem[];
  totalCount?: number;
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
  ybNo?: string;
  clinicCode?: string;
  ybMatchFlag?: number;
  statusEnum?: string;
  [key: string]: unknown;
}

/** 启动异步导入 */
export interface StartClinicalV2ImportResult {
  jobId?: string;
  [key: string]: unknown;
}

/** 异步导入进度 */
export interface ClinicalV2ImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  successCount?: number;
  message?: string;
  [key: string]: unknown;
}

/**
 * 启动国家临床版 2.0 异步导入（推荐）
 */
export async function startImportClinicalV2CatalogApi(body: {
  fileBytes: string;
  fileName?: string;
}): Promise<StartClinicalV2ImportResult> {
  const res = await requestClient.post<ApiResponse<StartClinicalV2ImportResult>>(
    '/diagnosisCatalog/startImportClinicalV2Catalog',
    body,
  );
  return (res.data ?? res) as StartClinicalV2ImportResult;
}

/**
 * 查询国家临床版 2.0 异步导入进度
 */
export async function getImportClinicalV2CatalogProgressApi(
  jobId: string,
): Promise<ClinicalV2ImportProgressResult> {
  const res = await requestClient.post<ApiResponse<ClinicalV2ImportProgressResult>>(
    '/diagnosisCatalog/getImportClinicalV2CatalogProgress',
    { jobId },
  );
  return (res.data ?? res) as ClinicalV2ImportProgressResult;
}

/** 启动医保诊断目录异步导入 */
export async function startImportYbConditionCatalogApi(body: {
  sourceEnum: string;
}): Promise<StartClinicalV2ImportResult> {
  const res = await requestClient.post<ApiResponse<StartClinicalV2ImportResult>>(
    '/diagnosisCatalog/startImportYbConditionCatalog',
    body,
  );
  return (res.data ?? res) as StartClinicalV2ImportResult;
}

/** 查询医保诊断目录异步导入进度 */
export async function getImportYbConditionCatalogProgressApi(
  jobId: string,
): Promise<ClinicalV2ImportProgressResult> {
  const res = await requestClient.post<ApiResponse<ClinicalV2ImportProgressResult>>(
    '/diagnosisCatalog/getImportYbConditionCatalogProgress',
    { jobId },
  );
  return (res.data ?? res) as ClinicalV2ImportProgressResult;
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

/** 手术目录项 */
export interface ProcedureCatalogItem {
  id?: string;
  procedureCode?: string;
  name?: string;
  sourceEnum?: string;
  pyStr?: string;
  wbStr?: string;
  typeCode?: string;
  description?: string;
  ybFlag?: number | boolean;
  ybNo?: string;
  clinicCode?: string;
  ybMatchFlag?: number | boolean;
  statusEnum?: string;
  /** 国临术式挂靠的诊疗项目主键 */
  activityDefinitionId?: string;
  [key: string]: unknown;
}

export interface PageProcedureCatalogParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  sourceEnum?: string;
  [key: string]: unknown;
}

export interface ProcedureCatalogInitResponse {
  [key: string]: unknown;
}

export async function pageProcedureCatalogApi(
  params: PageProcedureCatalogParams,
): Promise<ApiResponse<{ content?: ProcedureCatalogItem[]; totalCount?: number }>> {
  return requestClient.post('/procedureCatalog/pageProcedureCatalog', params);
}

export async function getProcedureCatalogApi(
  id: string,
): Promise<ApiResponse<ProcedureCatalogItem>> {
  return requestClient.post('/procedureCatalog/getProcedureCatalog', id);
}

export async function initProcedureCatalogApi(): Promise<
  ApiResponse<ProcedureCatalogInitResponse>
> {
  return requestClient.post('/procedureCatalog/init', {});
}

export async function createProcedureCatalogApi(
  params: Partial<ProcedureCatalogItem>,
): Promise<ApiResponse<ProcedureCatalogItem>> {
  return requestClient.post('/procedureCatalog/createProcedureCatalog', params);
}

export async function updateProcedureCatalogApi(
  params: Partial<ProcedureCatalogItem>,
): Promise<ApiResponse<ProcedureCatalogItem>> {
  return requestClient.post('/procedureCatalog/editProcedureCatalog', params);
}

export async function deleteProcedureCatalogApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post('/procedureCatalog/deleteProcedureCatalog', id);
}

export async function batchEnableProcedureCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post('/procedureCatalog/batchEnable', ids);
}

export async function batchDisableProcedureCatalogApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post('/procedureCatalog/batchDisable', ids);
}

export interface ClinicalProcV3ImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  successCount?: number;
  message?: string;
  [key: string]: unknown;
}

export async function startImportClinicalProcV3CatalogApi(body: {
  fileBytes: string;
  fileName?: string;
}): Promise<{ jobId?: string }> {
  const res = await requestClient.post<ApiResponse<{ jobId?: string }>>(
    '/procedureCatalog/startImportClinicalProcV3Catalog',
    body,
  );
  return (res.data ?? res) as { jobId?: string };
}

export async function getImportClinicalProcV3CatalogProgressApi(
  jobId: string,
): Promise<ClinicalProcV3ImportProgressResult> {
  const res = await requestClient.post<ApiResponse<ClinicalProcV3ImportProgressResult>>(
    '/procedureCatalog/getImportClinicalProcV3CatalogProgress',
    { jobId },
  );
  return (res.data ?? res) as ClinicalProcV3ImportProgressResult;
}

export interface GeneralOpImportProgressResult {
  jobId?: string;
  status?: string;
  stage?: string;
  percent?: number;
  total?: number;
  processed?: number;
  successCount?: number;
  message?: string;
  [key: string]: unknown;
}

export async function startImportGeneralOpCatalogApi(): Promise<{ jobId?: string }> {
  const res = await requestClient.post<ApiResponse<{ jobId?: string }>>(
    '/procedureCatalog/startImportGeneralOpCatalog',
    {},
  );
  return (res.data ?? res) as { jobId?: string };
}

export async function getImportGeneralOpCatalogProgressApi(
  jobId: string,
): Promise<GeneralOpImportProgressResult> {
  const res = await requestClient.post<ApiResponse<GeneralOpImportProgressResult>>(
    '/procedureCatalog/getImportGeneralOpCatalogProgress',
    { jobId },
  );
  return (res.data ?? res) as GeneralOpImportProgressResult;
}
