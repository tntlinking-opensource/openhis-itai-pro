/**
 * 药房损益单 — 对接 Whale {@code POST /api/app/inventoryPharmacyGainLoss/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_PHARMACIES_LABEL,
  mapLocationOptionsToCabinetRows,
  prependAllLocationsCabinetOption,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import { mapGainLossListRowToQueueCard } from '#/api/inventory/warehouse/gain-loss';

const BASE = '/inventoryPharmacyGainLoss';

export const PHA_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface PharmacyGainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadPharmacyGainLossFormInitRequest {}

export interface LoadPharmacyGainLossFormInitResponse {
  pharmacyLocationOptions?: PharmacyGainLossOptionDto[];
  inboundItemTableTypeOptions?: PharmacyGainLossOptionDto[];
  statusOptions?: PharmacyGainLossOptionDto[];
  pharmacistOptions?: PharmacyGainLossOptionDto[];
  warehouseKeeperOptions?: PharmacyGainLossOptionDto[];
}

export interface PharmacyGainLossListRowDto {
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

export interface PagePharmacyGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PagePharmacyGainLosssResponse {
  total?: number;
  totalCount?: number;
  content?: PharmacyGainLossListRowDto[];
}

export interface GetPharmacyGainLossDetailRequest {
  busNo: string;
}

export interface PharmacyGainLossDetailLineDto {
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

export interface GetPharmacyGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  lines: PharmacyGainLossDetailLineDto[];
}

export interface PharmacyGainLossLineDto {
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

export interface CreatePharmacyGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: PharmacyGainLossLineDto[];
}

export interface CreatePharmacyGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdatePharmacyGainLossRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: PharmacyGainLossLineDto[];
}

export interface UpdatePharmacyGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitPharmacyGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitPharmacyGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawPharmacyGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawPharmacyGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreePharmacyGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectPharmacyGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemovePharmacyGainLossRequest {
  busNo: string;
}

export interface PageProductsForPharmacyGainLossRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  /** true=仅返回账面量>0；false/未传=含零库存 */
  positiveQuantityOnly?: boolean;
  pageNo: number;
  pageSize: number;
}

export interface PageProductsForPharmacyGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
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

export async function loadPharmacyGainLossFormInitApi(body?: LoadPharmacyGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadPharmacyGainLossFormInitResponse>>(
    `${BASE}/loadPharmacyGainLossFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyGainLosssApi(body: PagePharmacyGainLosssRequest) {
  return requestClient.post<ApiResponse<PagePharmacyGainLosssResponse>>(
    `${BASE}/pagePharmacyGainLosss`,
    body,
  );
}

export async function getPharmacyGainLossDetailApi(body: GetPharmacyGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyGainLossDetailResponse>>(
    `${BASE}/getPharmacyGainLossDetail`,
    body,
  );
}

export async function createPharmacyGainLossApi(body: CreatePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyGainLossResponse>>(
    `${BASE}/createPharmacyGainLoss`,
    body,
  );
}

export async function updatePharmacyGainLossApi(body: UpdatePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<UpdatePharmacyGainLossResponse>>(
    `${BASE}/updatePharmacyGainLoss`,
    body,
  );
}

export async function submitPharmacyGainLossApprovalApi(
  body: SubmitPharmacyGainLossApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitPharmacyGainLossApprovalResponse>>(
    `${BASE}/submitPharmacyGainLossApproval`,
    body,
  );
}

export async function withdrawPharmacyGainLossApprovalApi(
  body: WithdrawPharmacyGainLossApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawPharmacyGainLossApprovalResponse>>(
    `${BASE}/withdrawPharmacyGainLossApproval`,
    body,
  );
}

export async function agreePharmacyGainLossApi(body: AgreePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreePharmacyGainLoss`,
    body,
  );
}

export async function rejectPharmacyGainLossApi(body: RejectPharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyGainLoss`,
    body,
  );
}

export async function removePharmacyGainLossApi(body: RemovePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyGainLoss`,
    body,
  );
}

export async function pageProductsForPharmacyGainLossApi(
  body: PageProductsForPharmacyGainLossRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyGainLossResponse>>(
    `${BASE}/pageProductsForPharmacyGainLoss`,
    body,
  );
}

export function mapPharmacyGainLossInitOptionsToApiItems(
  list?: PharmacyGainLossOptionDto[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

export function mapPharmacyGainLossInitToCabinetRows(
  list?: PharmacyGainLossOptionDto[],
): Record<string, unknown>[] {
  const rows = mapLocationOptionsToCabinetRows(
    (list ?? []).map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_PHARMACIES_LABEL,
  );
  return prependAllLocationsCabinetOption(rows, INV_ALL_PHARMACIES_LABEL);
}

export async function pagePharmacyGainLossWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const res = await pagePharmacyGainLosssApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params?.purposeLocationId),
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PharmacyGainLossListRowDto>(res.data);
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
