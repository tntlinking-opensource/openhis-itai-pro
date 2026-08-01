/**
 * 药库订货单 — 对接 Whale {@code POST /api/app/inventoryWarehousePurchaseOrder/<method>}
 * 契约见 AS-INV-WHS-001-SDD；枚举均为领域 {@code getCode()} 字符串。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inventoryWarehousePurchaseOrder';

export interface PurchaseOrderFormInitOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadPurchaseOrderFormInitRequest {
  organizationId?: number;
}

export interface LoadPurchaseOrderFormInitResponse {
  warehouseLocationOptions?: PurchaseOrderFormInitOptionDto[];
  /** 关联科室采购申请弹窗：科室药房下拉（首项全部=0） */
  departmentPharmacyLocationOptions?: PurchaseOrderFormInitOptionDto[];
  supplierOptions?: PurchaseOrderFormInitOptionDto[];
  warehouseKeeperOptions?: PurchaseOrderFormInitOptionDto[];
  statusOptions?: PurchaseOrderFormInitOptionDto[];
  purchaseItemTableTypeOptions?: PurchaseOrderFormInitOptionDto[];
}

export interface PagePurchaseOrdersRequest {
  /** 目的药库；{@link WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID}（0）表示全部 */
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PurchaseOrderListRowDto {
  busNo?: string;
  supplierName?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  statusEnum?: string;
  statusEnumText?: string;
  creatorName?: string;
  approverId?: number | string;
  approverName?: string;
  totalAmount?: number;
  approvalTime?: string;
  applyTime?: string;
  occurrenceTime?: string;
}

export interface PagePurchaseOrdersResponse {
  totalCount?: number;
  total?: number;
  pageNo?: number;
  pageSize?: number;
  content?: PurchaseOrderListRowDto[];
  /** 兼容旧分页字段 */
  records?: PurchaseOrderListRowDto[];
}

export interface GetPurchaseOrderDetailRequest {
  busNo: string;
}

export interface PurchaseOrderDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  remake?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  productCatalog?: unknown;
}

export interface GetPurchaseOrderDetailResponse {
  busNo?: string;
  /** 可选：来源科室采购申请单号 */
  originalBusNo?: string;
  typeEnum?: string;
  statusEnum?: string;
  supplierId?: number | string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  rowVersion?: number | string;
  lines: PurchaseOrderDetailLineDto[];
}

export interface PurchaseOrderLineDto {
  itemTable: string;
  itemId: number | string;
  itemQuantity: number;
  unitCode: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime: string;
  remake?: string;
}

export interface CreatePurchaseOrderRequest {
  purposeLocationId: number | string;
  /** 可选：已同意科室采购申请单号；空=手工直建 */
  originalBusNo?: string;
  categoryEnum?: string;
  supplierId?: number | string;
  practitionerId: number | string;
  lines: PurchaseOrderLineDto[];
}

export interface UpdatePurchaseOrderRequest {
  busNo: string;
  purposeLocationId: number | string;
  categoryEnum?: string;
  supplierId?: number | string;
  practitionerId: number | string;
  lines: PurchaseOrderLineDto[];
  rowVersion?: number | string;
}

export interface CreatePurchaseOrderResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface UpdatePurchaseOrderResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface SubmitPurchaseOrderApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitPurchaseOrderApprovalResponse {
  success?: boolean;
  statusEnum?: string;
}

export interface WithdrawPurchaseOrderApprovalRequest {
  busNo: string;
}

export interface WithdrawPurchaseOrderApprovalResponse {
  success?: boolean;
  statusEnum?: string;
}

export interface AgreePurchaseOrderRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectPurchaseOrderRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemovePurchaseOrderRequest {
  busNo: string;
}

export interface PageProductsForPurchaseRequest {
  purposeLocationId: number | string;
  productTableName?: string;
  /** 预警勾选带回的产品定义主键；非空时按 id 收窄选品 */
  productDefinitionIds?: string[];
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number | string;
  manufacturer?: string;
  includeInventory?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  categoryCode?: string;
  categoryCodeDictText?: string;
  price?: number;
  purchasePrice?: number;
  availableQuantity?: number | null;
  unitBands?: unknown[];
  manufacturer?: string;
  totalVolume?: string;
  pyStr?: string;
  wbStr?: string;
}

export interface PageProductsForPurchaseResponse {
  total?: number;
  totalCount?: number;
  content?: ProductDefinitionItemDto[];
  /** 兼容旧分页字段 */
  records?: ProductDefinitionItemDto[];
}

/** 预警筛：expire=效期 / lowStock=库存 / all=全部 */
export type PurchaseWarningFilter = 'all' | 'expire' | 'lowStock';

export interface PagePurchaseWarningProductsRequest {
  purposeLocationId: number | string;
  productTableName: string;
  warningFilter: PurchaseWarningFilter;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PurchaseWarningProductRowDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  specification?: string;
  hitExpire?: boolean;
  hitLowStock?: boolean;
  latestExpirationDate?: string;
  remainingDays?: number;
  hospitalWidePackageQty?: number;
}

export interface PagePurchaseWarningProductsResponse {
  totalCount?: number;
  total?: number;
  pageNo?: number;
  pageSize?: number;
  content?: PurchaseWarningProductRowDto[];
  records?: PurchaseWarningProductRowDto[];
}

/** 列表「全部药库」占位 purposeLocationId（与 init OptionDto.value 一致，字符串） */
export const WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID = '0';

/** 侧栏药库下拉「全部」占位项展示文案 */
export const WHS_PO_ALL_WAREHOUSES_LABEL = '全部库房';

/** {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY} */
export const WHS_PO_CATEGORY_STOCK = 'STOCK_SUPPLY';

export async function loadPurchaseOrderFormInitApi(body?: LoadPurchaseOrderFormInitRequest) {
  return requestClient.post<ApiResponse<LoadPurchaseOrderFormInitResponse>>(
    `${BASE}/loadPurchaseOrderFormInit`,
    body ?? {},
  );
}

export async function pagePurchaseOrdersApi(body: PagePurchaseOrdersRequest) {
  return requestClient.post<ApiResponse<PagePurchaseOrdersResponse>>(
    `${BASE}/pagePurchaseOrders`,
    body,
  );
}

function resolvePurchaseOrderListPurposeLocationId(
  purposeLocationId?: number | string,
): number | string {
  if (
    purposeLocationId === undefined ||
    purposeLocationId === null ||
    String(purposeLocationId).trim() === '' ||
    String(purposeLocationId).trim() === '0'
  ) {
    return WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID;
  }
  return purposeLocationId;
}

/** 列表行制单/申请时刻，用于前端日期筛选（待审核单 applyTime 可能为空）。 */
function resolvePurchaseOrderListRowTime(row: PurchaseOrderListRowDto): string {
  const raw = row.applyTime ?? row.occurrenceTime;
  return raw === undefined || raw === null ? '' : String(raw).trim();
}

function filterPurchaseOrderListRowsByDateRange(
  rows: PurchaseOrderListRowDto[],
  applyTimeBegin?: string,
  applyTimeEnd?: string,
): PurchaseOrderListRowDto[] {
  const begin = applyTimeBegin?.trim();
  const end = applyTimeEnd?.trim();
  if (!begin || !end) return rows;
  const startMs = new Date(`${begin}T00:00:00`).getTime();
  const endMs = new Date(`${end}T23:59:59`).getTime();
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) return rows;
  return rows.filter((row) => {
    const timeText = resolvePurchaseOrderListRowTime(row);
    if (timeText === '') return true;
    const normalized = timeText.includes('T') ? timeText : timeText.replace(' ', 'T');
    const ms = new Date(normalized).getTime();
    if (!Number.isFinite(ms)) return true;
    return ms >= startMs && ms <= endMs;
  });
}

/** 工作站左侧列表：未选药库 → purposeLocationId=0（全部）；日期在前端按 occurrence/apply 筛。 */
export async function pagePurchaseOrderWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: PurchaseOrderListRowDto[]; total: number }> {
  const res = await pagePurchaseOrdersApi({
    purposeLocationId: resolvePurchaseOrderListPurposeLocationId(params?.purposeLocationId),
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PurchaseOrderListRowDto>(res.data);
  const records = filterPurchaseOrderListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const filtered = params?.applyTimeBegin && params?.applyTimeEnd;
  return { records, total: filtered ? records.length : totalCount };
}

export async function getPurchaseOrderDetailApi(body: GetPurchaseOrderDetailRequest) {
  return requestClient.post<ApiResponse<GetPurchaseOrderDetailResponse>>(
    `${BASE}/getPurchaseOrderDetail`,
    body,
  );
}

export async function createPurchaseOrderApi(body: CreatePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<CreatePurchaseOrderResponse>>(
    `${BASE}/createPurchaseOrder`,
    body,
  );
}

export async function updatePurchaseOrderApi(body: UpdatePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<UpdatePurchaseOrderResponse>>(
    `${BASE}/updatePurchaseOrder`,
    body,
  );
}

export async function submitPurchaseOrderApprovalApi(body: SubmitPurchaseOrderApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitPurchaseOrderApprovalResponse>>(
    `${BASE}/submitPurchaseOrderApproval`,
    body,
  );
}

export async function withdrawPurchaseOrderApprovalApi(body: WithdrawPurchaseOrderApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawPurchaseOrderApprovalResponse>>(
    `${BASE}/withdrawPurchaseOrderApproval`,
    body,
  );
}

export async function agreePurchaseOrderApi(body: AgreePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreePurchaseOrder`, body);
}

export async function rejectPurchaseOrderApi(body: RejectPurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPurchaseOrder`,
    body,
  );
}

export async function removePurchaseOrderApi(body: RemovePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePurchaseOrder`,
    body,
  );
}

export async function pageProductsForPurchaseApi(body: PageProductsForPurchaseRequest) {
  return requestClient.post<ApiResponse<PageProductsForPurchaseResponse>>(
    `${BASE}/pageProductsForPurchase`,
    body,
  );
}

/** 订货预警快捷选品分页：效期/低储/并集（品种维，只读） */
export async function pagePurchaseWarningProductsApi(body: PagePurchaseWarningProductsRequest) {
  return requestClient.post<ApiResponse<PagePurchaseWarningProductsResponse>>(
    `${BASE}/pagePurchaseWarningProducts`,
    body,
  );
}

/** IF-WHS-PO-12：可关联科室采购申请分页（已同意且未被订货占用） */
export interface PageLinkableDeptPurchaseRequestsRequest {
  /** 申请科室药房；`0` 表示全部 */
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface LinkableDeptPurchaseRequestListRowDto {
  busNo?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  statusEnum?: string;
  statusEnumText?: string;
  creatorName?: string;
  approverId?: number | string;
  approverName?: string;
  totalAmount?: number;
  approvalTime?: string;
  applyTime?: string;
  occurrenceTime?: string;
}

export interface PageLinkableDeptPurchaseRequestsResponse {
  total?: number;
  totalCount?: number;
  records?: LinkableDeptPurchaseRequestListRowDto[];
  content?: LinkableDeptPurchaseRequestListRowDto[];
}

/** IF-WHS-PO-13：按申请单号拉取订货预填明细（只读） */
export interface GetLinkableDeptPurchaseRequestDetailRequest {
  originalBusNo: string;
}

export interface GetLinkableDeptPurchaseRequestDetailResponse {
  originalBusNo?: string;
  deptPurchaseRequestBusNo?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: PurchaseOrderDetailLineDto[];
}

export async function pageLinkableDeptPurchaseRequestsApi(
  body: PageLinkableDeptPurchaseRequestsRequest,
) {
  return requestClient.post<ApiResponse<PageLinkableDeptPurchaseRequestsResponse>>(
    `${BASE}/pageLinkableDeptPurchaseRequests`,
    body,
  );
}

export async function getLinkableDeptPurchaseRequestDetailApi(
  body: GetLinkableDeptPurchaseRequestDetailRequest,
) {
  return requestClient.post<ApiResponse<GetLinkableDeptPurchaseRequestDetailResponse>>(
    `${BASE}/getLinkableDeptPurchaseRequestDetail`,
    body,
  );
}

/** init OptionDto → 侧栏药库下拉行（含「全部库房」首项 value=0） */
export function mapPurchaseOrderInitToCabinetRows(
  opts?: PurchaseOrderFormInitOptionDto[],
): Record<string, unknown>[] {
  return (opts ?? []).map((o) => {
    const value = o.value;
    const isAllWarehouses =
      String(value ?? '').trim() === WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID;
    const label = isAllWarehouses ? WHS_PO_ALL_WAREHOUSES_LABEL : (o.label ?? String(value ?? ''));
    return {
      id: value,
      value,
      name: label,
      label,
    };
  });
}

/** init OptionDto → ApiOptionItem（库管员/项目类型等） */
export function mapPurchaseOrderInitToApiOptions(
  opts?: PurchaseOrderFormInitOptionDto[],
): ApiOptionItem[] {
  return (opts ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

/** 排除「全部」占位后的真实药库选项 */
export function filterPurchaseOrderWarehouseOptions(
  opts?: PurchaseOrderFormInitOptionDto[],
): PurchaseOrderFormInitOptionDto[] {
  return (opts ?? []).filter((o) => String(o.value ?? '') !== '0');
}
