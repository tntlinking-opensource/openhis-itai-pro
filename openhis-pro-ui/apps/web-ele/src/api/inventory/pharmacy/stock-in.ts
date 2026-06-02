/**
 * 药房入库单 — 对接 Whale {@code POST /api/app/inventoryPharmacyStockIn/<method>}
 * 契约以 AS-INV-PHA-001-SDD 与领域枚举 {@code getCode()} 字符串为准；审批时刻字段与同模块进货单请求体一致（{@code approvalClock}）。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import {
  normalizePharmacyStockInStatusSort,
  pharmacyStockInStatusLabel,
} from '#/utils/inventory/pharmacy-stock-in-doc-actions';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inventoryPharmacyStockIn';

/** 主区/侧栏用的简化库位行（由 init 的 Option 列表映射而来）。 */
export interface WarehouseOptionDto {
  locationId?: number | string;
  displayName?: string;
  active?: boolean;
}

export interface StockInFormInitOptionDto {
  sort?: string;
  value?: string;
  label?: string;
  busNo?: null | string;
  children?: null | StockInFormInitOptionDto[];
}

export interface LoadPharmacyStockInFormInitRequest {
  organizationId?: number;
}

export interface LoadPharmacyStockInFormInitResponse {
  pharmacyLocationOptions?: StockInFormInitOptionDto[];
  warehouseLocationOptions?: StockInFormInitOptionDto[];
  inboundItemTableTypeOptions?: StockInFormInitOptionDto[];
  statusOptions?: StockInFormInitOptionDto[];
  pharmacistOptions?: StockInFormInitOptionDto[];
  warehouseKeeperOptions?: StockInFormInitOptionDto[];
}

/** {@link com.openhis.health.domain.share.enums.SupplyType#PHARMACY_STOCK_IN} */
export const PHA_STOCK_IN_TYPE_ENUM = 'PHARMACY_STOCK_IN';
/** {@link com.openhis.health.domain.share.enums.SupplyRequestType#CABINET} */
export const PHA_STOCK_IN_SOURCE_TYPE_ENUM = 'CABINET';
/** {@link com.openhis.health.domain.share.enums.SupplyRequestType#PHARMACY} */
export const PHA_STOCK_IN_PURPOSE_TYPE_ENUM = 'PHARMACY';
/** {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY} */
export const PHA_STOCK_IN_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface PagePharmacyStockInsRequest {
  /** 目的药房；SDD 允许 0 占位 */
  purposeLocationId: number | string;
  sourceLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
}

export interface PharmacyStockInListRowDto {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  sourceLocationName?: string;
  purposeLocationId?: number | string;
  applyTime?: string;
  approvalTime?: string;
  applicantId?: number | string;
  approverId?: string;
  originalBusNo?: string;
}

export interface PagePharmacyStockInsResponse {
  total?: number;
  records: PharmacyStockInListRowDto[];
}

export interface GetPharmacyStockInDetailRequest {
  busNo: string;
}

export interface PharmacyStockInDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  startTime?: string;
  endTime?: string;
  productCatalog?: unknown;
}

export interface GetPharmacyStockInDetailResponse {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  originalBusNo?: string;
  lines: PharmacyStockInDetailLineDto[];
}

export interface PharmacyStockInLineDto {
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
}

export interface CreatePharmacyStockInRequest {
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum: string;
  purposeTypeEnum: string;
  categoryEnum: string;
  originalBusNo?: string;
  busNo?: string;
  lines: PharmacyStockInLineDto[];
}

export interface CreatePharmacyStockInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdatePharmacyStockInRequest {
  busNo: string;
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum: string;
  purposeTypeEnum: string;
  categoryEnum: string;
  originalBusNo?: string;
  lines: PharmacyStockInLineDto[];
}

export interface UpdatePharmacyStockInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitPharmacyStockInApprovalRequest {
  busNo: string;
}

export interface WithdrawPharmacyStockInApprovalRequest {
  busNo: string;
}

export interface AgreePharmacyStockInRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectPharmacyStockInRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemovePharmacyStockInRequest {
  busNo: string;
}

export interface PageProductsForPharmacyStockInRequest {
  sourceLocationId: number | string;
  productTableName: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface ProductDefinitionCatalogUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number | string;
  unitPrice?: number | string;
}

export interface ProductDefinitionItemDto {
  productTableName?: string;
  productDefinitionId?: number | string;
  productName?: string;
  merchandiseName?: string;
  /** 读模型可能直接给出默认单位编码 */
  unitCode?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: null | string | string[];
  availableQuantity?: number | string;
  unitBands?: ProductDefinitionCatalogUnitBandDto[];
  purchaseUnitPrice?: number | string;
  /** 与 {@code purchaseUnitPrice} 并存时的别名 */
  purchasePrice?: number | string;
  inventoryItemId?: number | string;
  /** 业务流水号展示 */
  productBusNo?: string;
}

export interface PageProductsForPharmacyStockInResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadPharmacyStockInFormInitApi(body?: LoadPharmacyStockInFormInitRequest) {
  return requestClient.post<ApiResponse<LoadPharmacyStockInFormInitResponse>>(
    `${BASE}/loadPharmacyStockInFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyStockInsApi(body: PagePharmacyStockInsRequest) {
  return requestClient.post<ApiResponse<PagePharmacyStockInsResponse>>(
    `${BASE}/pagePharmacyStockIns`,
    body,
  );
}

export async function getPharmacyStockInDetailApi(body: GetPharmacyStockInDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyStockInDetailResponse>>(
    `${BASE}/getPharmacyStockInDetail`,
    body,
  );
}

export async function createPharmacyStockInApi(body: CreatePharmacyStockInRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyStockInResponse>>(
    `${BASE}/createPharmacyStockIn`,
    body,
  );
}

export async function updatePharmacyStockInApi(body: UpdatePharmacyStockInRequest) {
  return requestClient.post<ApiResponse<UpdatePharmacyStockInResponse>>(
    `${BASE}/updatePharmacyStockIn`,
    body,
  );
}

export async function submitPharmacyStockInApprovalApi(body: SubmitPharmacyStockInApprovalRequest) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/submitPharmacyStockInApproval`,
    body,
  );
}

export async function withdrawPharmacyStockInApprovalApi(
  body: WithdrawPharmacyStockInApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/withdrawPharmacyStockInApproval`,
    body,
  );
}

export async function agreePharmacyStockInApi(body: AgreePharmacyStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreePharmacyStockIn`,
    body,
  );
}

export async function rejectPharmacyStockInApi(body: RejectPharmacyStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyStockIn`,
    body,
  );
}

export async function removePharmacyStockInApi(body: RemovePharmacyStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyStockIn`,
    body,
  );
}

export async function pageProductsForPharmacyStockInApi(
  body: PageProductsForPharmacyStockInRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyStockInResponse>>(
    `${BASE}/pageProductsForPharmacyStockIn`,
    body,
  );
}

/** init 下拉项转侧栏/工作站卡片同构行。 */
export function mapStockInInitOptionsToCabinetRows(
  opts?: StockInFormInitOptionDto[],
): Record<string, unknown>[] {
  return (opts ?? []).map((o) => {
    const v = o.value;
    return {
      id: v,
      value: v,
      name: o.label ?? String(v ?? ''),
      label: o.label ?? String(v ?? ''),
    };
  });
}

/** 与药库出库 init 的入库项目类型选项同构（选品 productTableName）。 */
export function mapInitItemTableTypesForPharmacyStockIn(
  opts: StockInFormInitOptionDto[],
): ApiOptionItem[] {
  return opts.map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

/** 单头源药库/目的药房下拉：与出库页 {@link WarehouseOptionDto} 一致。 */
export function mapWarehouseInitToLocationOptions(
  list?: StockInFormInitOptionDto[],
): WarehouseOptionDto[] {
  return (list ?? []).map((o) => ({
    locationId: o.value,
    displayName: o.label,
  }));
}

function mapPharmacyStockInListRowToQueueCard(
  r: PharmacyStockInListRowDto,
): Record<string, unknown> {
  const st = normalizePharmacyStockInStatusSort(r.statusEnum);
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: pharmacyStockInStatusLabel(st),
    applicantId_dictText: '',
  };
}

/** 工作站左侧列表：服务端分页字段与 SDD IF-PHA-IN-02 对齐。 */
export async function pagePharmacyStockInWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const pid = params?.purposeLocationId;
  const purposeLocationId =
    pid === undefined || pid === null || String(pid).trim() === '' ? '0' : String(pid).trim();

  const res = await pagePharmacyStockInsApi({
    purposeLocationId,
    sourceLocationId:
      params?.sourceLocationId !== undefined &&
      params?.sourceLocationId !== null &&
      String(params.sourceLocationId).trim() !== ''
        ? params.sourceLocationId
        : undefined,
    keyword: params?.keyword?.trim() || undefined,
    applyTimeFrom: params?.applyTimeBegin ? `${params.applyTimeBegin}T00:00:00` : undefined,
    applyTimeTo: params?.applyTimeEnd ? `${params.applyTimeEnd}T23:59:59` : undefined,
    typeEnum: PHA_STOCK_IN_TYPE_ENUM,
    pageNo: 1,
    pageSize: 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PharmacyStockInListRowDto>(res.data);
  const records = content.map((r) => mapPharmacyStockInListRowToQueueCard(r));
  return { records, total: totalCount };
}

/** 进入页面：药房入库 init（目的药房、源药库、项目类型等）。 */
export async function initPharmacyStockInWorkbenchApi(): Promise<{
  /** 侧栏「源药库」下拉（与药库出库页 cabinetListOptions 语义一致） */
  cabinetListOptions: Record<string, unknown>[];
  /** 侧栏「目的药房」下拉 */
  pharmacyCabinetListOptions: Record<string, unknown>[];
  warehouseRawOptions: WarehouseOptionDto[];
  pharmacyRawOptions: WarehouseOptionDto[];
  itemTableTypeOptions: ApiOptionItem[];
}> {
  const res = await loadPharmacyStockInFormInitApi({});
  const d = res.data;
  const pharmacyRows = mapStockInInitOptionsToCabinetRows(d?.pharmacyLocationOptions);
  const warehouseRows = mapStockInInitOptionsToCabinetRows(d?.warehouseLocationOptions);
  return {
    cabinetListOptions: warehouseRows,
    pharmacyCabinetListOptions: pharmacyRows,
    warehouseRawOptions: mapWarehouseInitToLocationOptions(d?.warehouseLocationOptions),
    pharmacyRawOptions: mapWarehouseInitToLocationOptions(d?.pharmacyLocationOptions),
    itemTableTypeOptions: mapInitItemTableTypesForPharmacyStockIn(
      d?.inboundItemTableTypeOptions ?? [],
    ),
  };
}
