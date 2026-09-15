/**
 * 科室损益单 — 对接 Whale {@code POST /api/app/inventoryDepartmentGainLoss/<method>}
 * 方法名与 {@code InventoryDepartmentGainLossAppService} 一致（无 Dept 前缀）。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  mapLocationOptionsToCabinetRows,
  pickInitOptionDtoList,
  prependAllLocationsCabinetOption,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import { mapGainLossListRowToQueueCard } from '#/api/inventory/warehouse/gain-loss';

const BASE = '/inventoryDepartmentGainLoss';

export const DEP_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface DeptGainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptGainLossFormInitRequest {}

export interface LoadDeptGainLossFormInitResponse {
  deptLocationOptions?: DeptGainLossOptionDto[];
  /** 后端 IF-DEP-GL-01 实际字段名。 */
  pharmacyLocationOptions?: DeptGainLossOptionDto[];
  inboundItemTableTypeOptions?: DeptGainLossOptionDto[];
  statusOptions?: DeptGainLossOptionDto[];
  /** 后端药师下拉 */
  pharmacistOptions?: DeptGainLossOptionDto[];
  warehouseKeeperOptions?: DeptGainLossOptionDto[];
}

export interface DeptGainLossListRowDto {
  busNo?: string;
  statusEnum?: string | number;
  statusEnumText?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  itemCount?: number;
  gainLossQuantitySum?: number;
  /** 盈亏金额合计（可负；侧栏总金额） */
  gainLossAmountSum?: number;
  actualAmountSum?: number;
  applicantId?: number | string;
  applicantName?: string;
  applyTime?: string;
  approverName?: string;
  /** 驳回态侧栏回显 */
  rejectReason?: string;
}

export interface PageDeptGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageDeptGainLosssResponse {
  total?: number;
  totalCount?: number;
  content?: DeptGainLossListRowDto[];
}

export interface GetDeptGainLossDetailRequest {
  busNo: string;
}

export interface DeptGainLossDetailLineDto {
  lineId?: number | string;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  unitCode?: string;
  bookQuantity?: number;
  gainLossQuantity?: number;
  actualQuantity?: number;
  actualAmount?: number;
  gainLossAmount?: number;
  price?: number;
  reason?: string;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: { unitBands?: unknown };
}

export interface GetDeptGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  lines: DeptGainLossDetailLineDto[];
}

export interface DeptGainLossLineDto {
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  gainLossQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  reason?: string;
  price?: number;
  gainLossAmount?: number;
  occurrenceTime?: string;
}

export interface CreateDeptGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: DeptGainLossLineDto[];
}

export interface CreateDeptGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateDeptGainLossRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: DeptGainLossLineDto[];
}

export interface UpdateDeptGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDeptGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitDeptGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawDeptGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeDeptGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectDeptGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveDeptGainLossRequest {
  busNo: string;
}

export interface PageProductsForDeptGainLossRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  /** true=仅返回账面量>0；false/未传=含零库存 */
  positiveQuantityOnly?: boolean;
  pageNo: number;
  pageSize: number;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  lotNumber?: string;
  price?: number;
}

export interface PageProductsForDeptGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadDeptGainLossFormInitApi(body?: LoadDeptGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptGainLossFormInitResponse>>(
    `${BASE}/loadGainLossFormInit`,
    body ?? {},
  );
}

export async function pageDeptGainLosssApi(body: PageDeptGainLosssRequest) {
  return requestClient.post<ApiResponse<PageDeptGainLosssResponse>>(`${BASE}/pageGainLosss`, body);
}

export async function getDeptGainLossDetailApi(body: GetDeptGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptGainLossDetailResponse>>(
    `${BASE}/getGainLossDetail`,
    body,
  );
}

export async function createDeptGainLossApi(body: CreateDeptGainLossRequest) {
  return requestClient.post<ApiResponse<CreateDeptGainLossResponse>>(
    `${BASE}/createGainLoss`,
    body,
  );
}

/** 后端方法名为 updateGainLoss（与 SDD IF-DEP-GL-05 一致） */
export async function updateDeptGainLossApi(body: UpdateDeptGainLossRequest) {
  return requestClient.post<ApiResponse<UpdateDeptGainLossResponse>>(
    `${BASE}/updateGainLoss`,
    body,
  );
}

export async function submitDeptGainLossApprovalApi(body: SubmitDeptGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptGainLossApprovalResponse>>(
    `${BASE}/submitGainLossApproval`,
    body,
  );
}

export async function withdrawDeptGainLossApprovalApi(body: WithdrawDeptGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawDeptGainLossApprovalResponse>>(
    `${BASE}/withdrawGainLossApproval`,
    body,
  );
}

export async function agreeDeptGainLossApi(body: AgreeDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeGainLoss`, body);
}

export async function rejectDeptGainLossApi(body: RejectDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectGainLoss`, body);
}

export async function removeDeptGainLossApi(body: RemoveDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeGainLoss`, body);
}

export async function pageProductsForDeptGainLossApi(body: PageProductsForDeptGainLossRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptGainLossResponse>>(
    `${BASE}/pageProductsForGainLoss`,
    body,
  );
}

export function resolveDeptGainLossLocationOptionsFromInit(
  data?: LoadDeptGainLossFormInitResponse | null,
): DeptGainLossOptionDto[] {
  return pickInitOptionDtoList(
    data as Record<string, unknown> | undefined,
    'deptLocationOptions',
    'pharmacyLocationOptions',
  ) as DeptGainLossOptionDto[];
}

export function mapDeptGainLossInitOptionsToApiItems(
  list?: DeptGainLossOptionDto[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

export function mapDeptGainLossInitToCabinetRows(
  list?: DeptGainLossOptionDto[],
): Record<string, unknown>[] {
  const rows = mapLocationOptionsToCabinetRows(
    (list ?? []).map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_DEPT_PHARMACIES_LABEL,
  );
  return prependAllLocationsCabinetOption(rows, INV_ALL_DEPT_PHARMACIES_LABEL);
}

export async function pageDeptGainLossWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const res = await pageDeptGainLosssApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params?.purposeLocationId),
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptGainLossListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered.map((r) => mapGainLossListRowToQueueCard(r)),
    params?.keyword,
  ) as Record<string, unknown>[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
