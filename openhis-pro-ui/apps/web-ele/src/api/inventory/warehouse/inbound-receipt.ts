/**
 * 药库进货单 — 对接 Whale {@code POST /api/app/inventoryWarehouseInboundReceipt/<method>}
 * 契约见 {@code AS-INV-WHS-002-药库进货单-SDD} IF-WHS-IN-01～12。
 * baseURL 含 /api/app 时使用短路径 /inventoryWarehouseInboundReceipt/...
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseInboundReceipt';

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadInboundReceiptFormInitRequest {
  organizationId?: number;
}

export interface LoadInboundReceiptFormInitResponse {
  pharmacyLocationOptions?: OptionDto[];
  warehouseLocationOptions?: OptionDto[];
  supplierOptions?: OptionDto[];
  pharmacistOptions?: OptionDto[];
  warehouseKeeperOptions?: OptionDto[];
  inboundItemTableTypeOptions?: OptionDto[];
  statusOptions?: OptionDto[];
}

export interface PageInboundReceiptsRequest {
  /** 源药库，必填（IF-WHS-IN-02）；雪花 ID 用 string 与后端一致 */
  sourceLocationId: number | string;
  purposeLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  startDate?: string;
  endDate?: string;
  pageNo: number;
  pageSize: number;
  /** 可空；非空须为 CABINET_INBOUND_RECEIPT */
  typeEnum?: string;
}

export interface InboundReceiptListRowDto {
  busNo?: string;
  supplierName?: string;
  statusEnum?: string;
  creatorName?: string;
  approverId?: number;
  approverName?: string;
  totalAmount?: number;
  approvalTime?: string;
  applyTime?: string;
}

export interface PageInboundReceiptsResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  records: InboundReceiptListRowDto[];
}

export interface GetInboundReceiptDetailRequest {
  busNo: string;
}

export interface InboundReceiptDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  /** 计量单位展示名（与 unitCode 字典命中一致） */
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  /** 生产日期（效期起点，对应 wkf_supply_request.start_time） */
  startTime?: string;
  /** 有效期至（效期止点，对应 wkf_supply_request.end_time） */
  endTime?: string;
  remake?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  /** 明细行多计量带（与选品同源时可带回，用于切换单位换算） */
  unitBands?: InboundProductUnitBandDto[];
  unitBandsJson?: string;
}

export interface GetInboundReceiptDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: string;
  statusEnum?: string;
  supplierId?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  rowVersion?: number;
  applyTime?: string;
  applicantId?: number;
  approvalTime?: string;
  reason?: string;
  lines: InboundReceiptDetailLineDto[];
}

export async function loadInboundReceiptFormInitApi(body?: LoadInboundReceiptFormInitRequest) {
  return requestClient.post<ApiResponse<LoadInboundReceiptFormInitResponse>>(
    `${BASE}/loadInboundReceiptFormInit`,
    body ?? {},
  );
}

export async function pageInboundReceiptsApi(body: PageInboundReceiptsRequest) {
  return requestClient.post<ApiResponse<PageInboundReceiptsResponse>>(
    `${BASE}/pageInboundReceipts`,
    body,
  );
}

export async function getInboundReceiptDetailApi(body: GetInboundReceiptDetailRequest) {
  return requestClient.post<ApiResponse<GetInboundReceiptDetailResponse>>(
    `${BASE}/getInboundReceiptDetail`,
    body,
  );
}

/** IF-WHS-IN-11 选品检索请求 */
export interface PageProductsForInboundReceiptRequest {
  productTableName?: string;
  /** 目的药库，必填；雪花 ID 用 string */
  purposeLocationId: number | string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface UnitRatioItemDto {
  fromUnitCode?: string;
  toUnitCode?: string;
  ratio?: number;
}

export interface InboundProductUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number;
  unitPrice?: null | number;
}

export interface InboundProductDefinitionItemDto {
  /** 产品定义主键（雪花 Long 字符串），写入明细 itemId */
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  merchandiseName?: string;
  totalVolume?: string;
  manufacturer?: string;
  unitCode?: string;
  minUnitCode?: string;
  unitRatios?: UnitRatioItemDto[];
  categoryCode?: string;
  /** 物资分类展示名 */
  categoryCodeDictText?: string;
  price?: number;
  purchasePrice?: number;
  /** 进货选品 IF-WHS-IN-11 不汇总库存，恒为 null */
  availableQuantity?: null | number;
  pyStr?: string;
  wbStr?: string;
  /** 多计量带（PURCHASE / SPLIT / STOCK）；默认采购单位见前端 defaultInboundUnitBandKind */
  unitBands?: InboundProductUnitBandDto[];
}

export interface PageProductsForInboundReceiptResponse {
  total?: number;
  records: InboundProductDefinitionItemDto[];
}

export async function pageProductsForInboundReceiptApi(body: PageProductsForInboundReceiptRequest) {
  return requestClient.post<ApiResponse<PageProductsForInboundReceiptResponse>>(
    `${BASE}/pageProductsForInboundReceipt`,
    body,
  );
}

/** 与后端 {@code InboundReceiptLineDto} 一致（创建/更新明细行） */
export interface InboundReceiptLineDto {
  itemTable: string;
  /** 物品定义主键：与选品/详情同源，禁止前端 Number() 强转（雪花等保持原样） */
  itemId: number | string;
  itemQuantity: number;
  unitCode: string;
  lotNumber: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  /** 制单时间（与单头 applyTime 一致，ISO 本地如 2026-05-15T15:50:00） */
  occurrenceTime?: string;
  /** 生产日期（效期起点） */
  startTime?: string;
  /** 有效期至（效期止点） */
  endTime?: string;
  remake?: string;
}

/** @deprecated 使用 {@link InboundReceiptLineDto} */
export type InboundReceiptLineInput = InboundReceiptLineDto;

export interface CreateInboundReceiptRequestBody {
  originalBusNo?: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum: string;
  categoryEnum: string;
  supplierId?: number | string;
  /** 经手人：医务人员主键，与后端 {@code CreateInboundReceiptRequest.practitionerId} 一致（wkf_supply_request.practitioner_id） */
  practitionerId?: number | string;
  lines: InboundReceiptLineDto[];
}

export interface CreateInboundReceiptResponseDto {
  busNo?: string;
  statusEnum?: string;
}

export interface UpdateInboundReceiptRequestBody extends CreateInboundReceiptRequestBody {
  busNo: string;
  rowVersion?: number | string;
}

export interface UpdateInboundReceiptResponseDto {
  busNo?: string;
  statusEnum?: string;
}

export interface SubmitInboundReceiptApprovalRequestBody {
  busNo: string;
  applyClock?: string;
}

export interface SubmitInboundReceiptApprovalResponseDto {
  success?: boolean;
  statusEnum?: string;
}

export async function createInboundReceiptApi(body: CreateInboundReceiptRequestBody) {
  return requestClient.post<ApiResponse<CreateInboundReceiptResponseDto>>(
    `${BASE}/createInboundReceipt`,
    body,
  );
}

export async function updateInboundReceiptApi(body: UpdateInboundReceiptRequestBody) {
  return requestClient.post<ApiResponse<UpdateInboundReceiptResponseDto>>(
    `${BASE}/updateInboundReceipt`,
    body,
  );
}

export async function submitInboundReceiptApprovalApi(
  body: SubmitInboundReceiptApprovalRequestBody,
) {
  return requestClient.post<ApiResponse<SubmitInboundReceiptApprovalResponseDto>>(
    `${BASE}/submitInboundReceiptApproval`,
    body,
  );
}

export interface WithdrawInboundReceiptApprovalRequestBody {
  busNo: string;
}

export interface WithdrawInboundReceiptApprovalResponseDto {
  success?: boolean;
  statusEnum?: string;
}

export interface AgreeInboundReceiptRequestBody {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  /** 医保契约 busNo（上传时必填） */
  contractBusNo?: string;
}

export interface AgreeInboundReceiptResponseDto {
  success?: boolean;
}

export interface RejectInboundReceiptRequestBody {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason: string;
}

export interface RejectInboundReceiptResponseDto {
  success?: boolean;
}

export interface RemoveInboundReceiptRequestBody {
  busNo: string;
}

export interface RemoveInboundReceiptResponseDto {
  success?: boolean;
}

export async function withdrawInboundReceiptApprovalApi(
  body: WithdrawInboundReceiptApprovalRequestBody,
) {
  return requestClient.post<ApiResponse<WithdrawInboundReceiptApprovalResponseDto>>(
    `${BASE}/withdrawInboundReceiptApproval`,
    body,
  );
}

export async function agreeInboundReceiptApi(body: AgreeInboundReceiptRequestBody) {
  return requestClient.post<ApiResponse<AgreeInboundReceiptResponseDto>>(
    `${BASE}/agreeInboundReceipt`,
    body,
  );
}

export async function rejectInboundReceiptApi(body: RejectInboundReceiptRequestBody) {
  return requestClient.post<ApiResponse<RejectInboundReceiptResponseDto>>(
    `${BASE}/rejectInboundReceipt`,
    body,
  );
}

export async function removeInboundReceiptApi(body: RemoveInboundReceiptRequestBody) {
  return requestClient.post<ApiResponse<RemoveInboundReceiptResponseDto>>(
    `${BASE}/removeInboundReceipt`,
    body,
  );
}

/** 列表筛选固定进货类型（SupplyType#getCode()） */
export const WHS_IN_SUPPLY_TYPE_CODE = 'CABINET_INBOUND_RECEIPT';
