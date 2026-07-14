import type { ApiOptionItem } from '#/types/api-response';

import type {
  AdjustableItemRowDto,
  InitPriceAdjustmentResponse,
  LogisticsSnapshotRowDto,
  OptionDto,
  PriceAdjustmentDocumentListRowDto,
  PriceAdjustmentDocumentResponse,
  PriceAdjustmentLineDto,
  SavePriceAdjustmentDraftLineRequest,
  SavePriceAdjustmentDraftRequest,
} from '#/api/finance/price-adjustment';

import {
  getPriceAdjustmentDocumentApi,
  initPriceAdjustmentApi,
  pagePriceAdjustmentDocumentsApi,
} from '#/api/finance/price-adjustment';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import type {
  PriceAdjustmentHeadForm,
  PriceAdjustmentLineRow,
} from '#/views/finance/price-adjustment/types';
import { resolveAdjustmentStyle } from '#/views/finance/price-adjustment/utils/price-adjustment-item-table';

export interface PriceAdjustmentInitResult {
  adjustmentReasonOptions: ApiOptionItem[];
  statusFilterOptions: ApiOptionItem[];
  itemTableTabOptions: ApiOptionItem[];
}

export interface PriceAdjustmentPageQuery {
  keyword?: string;
  submitTimeFrom?: string;
  submitTimeTo?: string;
}

function optionDtosToItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

function mapInitResponse(data?: InitPriceAdjustmentResponse): PriceAdjustmentInitResult {
  return {
    adjustmentReasonOptions: optionDtosToItems(data?.adjustmentReasonOptions),
    statusFilterOptions: optionDtosToItems(data?.statusFilterOptions),
    itemTableTabOptions: optionDtosToItems(data?.itemTableTabOptions),
  };
}

/** 列表行 → 侧栏卡片展示字段（对齐 IF-PRC-02 DTO，仅补 InvQueueCards 槽位映射） */
function mapListRowToQueue(r: PriceAdjustmentDocumentListRowDto): Record<string, unknown> {
  return {
    ...r,
    statusEnum_enumText: r.statusEnumText ?? '',
    applicantId_dictText: r.operatorName ?? '',
    approverId_dictText: r.submitterName ?? '',
    applyTime: r.adjustmentTime,
    approveTime: r.approveTime ?? r.submitTime,
    orgId: r.orgId !== undefined ? String(r.orgId) : undefined,
  };
}

function sumDetailQuantities(details?: Array<{ quantity?: number }>): number | undefined {
  if (!details?.length) return undefined;
  let total = 0;
  let found = false;
  for (const detail of details) {
    const qty = Number(detail.quantity);
    if (Number.isFinite(qty)) {
      total += qty;
      found = true;
    }
  }
  return found ? total : undefined;
}

/** 已保存行：从子表快照还原展示用当前价与库存合计 */
function resolveLineDisplayPrices(dto: PriceAdjustmentLineDto): {
  currentPurchasePrice?: number;
  currentSellingPrice?: number;
} {
  if (dto.adjustmentStyle === 'clinical') {
    const selling = dto.details?.[0]?.oldSellingAmount;
    return { currentSellingPrice: selling };
  }
  const anchor = dto.details?.[0];
  return {
    currentPurchasePrice: anchor?.oldPurchaseAmount,
    currentSellingPrice: anchor?.oldSellingAmount,
  };
}

function copyAdjustableItemMeta(item: AdjustableItemRowDto): Partial<PriceAdjustmentLineRow> {
  return {
    itemCode: item.itemCode,
    specification: item.specification,
    manufacturer: item.manufacturer,
    categoryCodeDictText: item.categoryCodeDictText,
    unitName: item.unitName,
    currentPurchasePrice: item.currentPurchasePrice,
    currentSellingPrice: item.currentSellingPrice,
  };
}

/** 详情行 DTO → 页面编辑行 */
export function mapLineDtoToRow(dto: PriceAdjustmentLineDto): PriceAdjustmentLineRow {
  const details = (dto.details ?? []).map((d) => ({
    id: d.id !== undefined ? String(d.id) : undefined,
    lotNumber: d.lotNumber,
    quantity: d.quantity,
    oldPurchaseAmount: d.oldPurchaseAmount,
    oldSellingAmount: d.oldSellingAmount,
    purchaseImpact: d.purchaseImpact,
    sellingImpact: d.sellingImpact,
  }));
  const displayPrices = resolveLineDisplayPrices(dto);
  return {
    id: dto.id !== undefined ? String(dto.id) : undefined,
    itemTable: dto.itemTable,
    itemTableText: dto.itemTableText,
    itemId: dto.itemId !== undefined ? String(dto.itemId) : undefined,
    itemPick: dto.itemId !== undefined ? { id: String(dto.itemId), name: dto.itemName } : undefined,
    itemName: dto.itemName,
    itemCode: dto.itemCode,
    specification: dto.specification,
    manufacturer: dto.manufacturer,
    ...displayPrices,
    quantity: sumDetailQuantities(details),
    newPurchaseAmount: dto.newPurchaseAmount,
    newSellingAmount: dto.newSellingAmount,
    adjustmentReason: dto.adjustmentReason,
    adjustmentReasonText: dto.adjustmentReasonText,
    adjustmentTime: dto.adjustmentTime,
    adjustmentStyle: dto.adjustmentStyle,
    adjustmentStyleText: dto.adjustmentStyleText,
    orgId: dto.orgId !== undefined ? String(dto.orgId) : undefined,
    details,
    oldSellingAmount:
      dto.adjustmentStyle === 'clinical' ? dto.details?.[0]?.oldSellingAmount : undefined,
  };
}

/** 编辑行 → 保存草稿请求行 */
export function mapRowToSaveLine(row: PriceAdjustmentLineRow): SavePriceAdjustmentDraftLineRequest {
  const itemId = preserveApiIdForRequest(row.itemId);
  if (!row.itemTable || !itemId) {
    throw new Error('明细行缺少项目表或项目标识');
  }
  const isClinical = row.adjustmentStyle === 'clinical';
  const line: SavePriceAdjustmentDraftLineRequest = {
    itemTable: row.itemTable,
    itemId,
    newPurchaseAmount: parseOptionalAmount(row.newPurchaseAmount),
    newSellingAmount: parseOptionalAmount(row.newSellingAmount),
  };
  if (isClinical) {
    line.oldSellingAmount = row.oldSellingAmount;
  } else if (row.details && row.details.length > 0) {
    line.details = row.details.map((d) => ({
      lotNumber: d.lotNumber,
      quantity: d.quantity,
      oldPurchaseAmount: d.oldPurchaseAmount,
      oldSellingAmount: d.oldSellingAmount,
    }));
  }
  return line;
}

function parseOptionalAmount(raw: unknown): number | undefined {
  if (raw === undefined || raw === null || raw === '') return undefined;
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

/** 单头 + 明细 → 保存草稿请求体 */
export function buildSaveDraftRequest(input: {
  busNo?: string;
  head: PriceAdjustmentHeadForm;
  lines: PriceAdjustmentLineRow[];
}): SavePriceAdjustmentDraftRequest {
  const orgId = preserveApiIdForRequest(input.head.orgId);
  if (!input.head.adjustmentReason?.trim()) {
    throw new Error('请选择调价原因');
  }
  if (input.lines.length === 0) {
    throw new Error('请至少添加一条调价明细');
  }
  return {
    busNo: input.busNo?.trim() || undefined,
    ...(orgId ? { orgId } : {}),
    adjustmentReason: input.head.adjustmentReason.trim(),
    adjustmentTime: input.head.adjustmentTime?.trim() || undefined,
    lines: input.lines.map((row) => mapRowToSaveLine(row)),
  };
}

/** 选品 + 物流快照 → 新明细行 */
export function buildLogisticsLineFromSnapshot(
  item: AdjustableItemRowDto,
  snapshotRows: LogisticsSnapshotRowDto[],
): PriceAdjustmentLineRow {
  const itemId = preserveApiIdForRequest(item.itemId);
  const details = snapshotRows
    .filter((r) => preserveApiIdForRequest(r.itemId) === itemId)
    .map((r) => ({
      lotNumber: r.lotNumber,
      quantity: r.quantity,
      oldPurchaseAmount: r.oldPurchaseAmount,
      oldSellingAmount: r.oldSellingAmount,
    }));
  return {
    itemTable: item.itemTable,
    itemId,
    itemPick: itemId ? { id: itemId, name: item.itemName } : undefined,
    itemName: item.itemName,
    adjustmentStyle: item.adjustmentStyle ?? resolveAdjustmentStyle(item.itemTable) ?? 'logistics',
    ...copyAdjustableItemMeta(item),
    quantity: sumDetailQuantities(details),
    newPurchaseAmount: undefined,
    newSellingAmount: undefined,
    details,
  };
}

/** 选品 + 诊疗调前价 → 新明细行 */
export function buildClinicalLine(
  item: AdjustableItemRowDto,
  oldSellingAmount?: number,
): PriceAdjustmentLineRow {
  const itemId = preserveApiIdForRequest(item.itemId);
  return {
    itemTable: item.itemTable,
    itemId,
    itemPick: itemId ? { id: itemId, name: item.itemName } : undefined,
    itemName: item.itemName,
    adjustmentStyle: item.adjustmentStyle ?? resolveAdjustmentStyle(item.itemTable) ?? 'clinical',
    ...copyAdjustableItemMeta(item),
    currentSellingPrice: item.currentSellingPrice ?? oldSellingAmount,
    oldSellingAmount,
    newSellingAmount: undefined,
    details: [],
  };
}

export async function initPriceAdjustmentPageApi() {
  const res = await initPriceAdjustmentApi();
  return mapInitResponse(res.data);
}

export async function pagePriceAdjustmentListApi(params?: PriceAdjustmentPageQuery) {
  const res = await pagePriceAdjustmentDocumentsApi({
    keyword: params?.keyword?.trim() || undefined,
    submitTimeFrom: params?.submitTimeFrom,
    submitTimeTo: params?.submitTimeTo,
    pageNo: 1,
    pageSize: 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PriceAdjustmentDocumentListRowDto>(
    res.data,
  );
  return {
    records: content.map(mapListRowToQueue),
    total: totalCount,
  };
}

export async function getPriceAdjustmentDetailApi(busNo: string) {
  const res = await getPriceAdjustmentDocumentApi({ busNo: busNo.trim() });
  const doc = res.data;
  if (!doc?.busNo) {
    return {
      head: {} as PriceAdjustmentHeadForm,
      lines: [] as PriceAdjustmentLineRow[],
      audit: {},
    };
  }
  const anchor = doc.lines?.[0];
  const head: PriceAdjustmentHeadForm = {
    busNo: doc.busNo,
    orgId: anchor?.orgId !== undefined ? String(anchor.orgId) : '',
    adjustmentReason: anchor?.adjustmentReason ?? '',
    adjustmentTime: anchor?.adjustmentTime ?? '',
  };
  const lines = (doc.lines ?? []).map(mapLineDtoToRow);
  const audit: Record<string, unknown> = {
    statusEnum: doc.statusEnum,
    statusEnumText: doc.statusEnumText,
    submitterName: doc.submitterName,
    submitTime: doc.submitTime,
    approverName: doc.approverName,
    approveTime: doc.approveTime,
    approveComment: doc.approveComment,
    totalPurchaseImpact: doc.totalPurchaseImpact,
    totalSellingImpact: doc.totalSellingImpact,
  };
  return { head, lines, audit, document: doc as PriceAdjustmentDocumentResponse };
}
