import type {
  NurseStationPricedOrderRowDto,
  NurseStationPricingCancelItemDto,
  NurseStationPricingCatalogRowDto,
  NurseStationPricingCatalogUnitBandDto,
  NurseStationPricingParentRequestAnchorDto,
  NurseStationPricingPatientGroupDto,
  NurseStationPricingWriteItemDto,
  NurseStationSupplementParentGroupDto,
  NurseStationSupplementParentOrderRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { formatDateTime } from '@zxplat/utils';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 划价确费列表行（表格展示） */
export interface PricingConfirmationTableRow {
  id: string;
  encounterId: string;
  /** 诊疗/耗材请求主键（取消划价 requestId） */
  requestId: string;
  /** SERVICE_REQUEST / DEVICE_REQUEST */
  orderType: string;
  /** 费用状态 code */
  chargeStatusEnum?: string;
  /** 是否允许取消划价（仅待结算） */
  canCancel: boolean;
  pricingContent: string;
  quantityText: string;
  unitPrice: number;
  totalPrice: number;
  pricingTime: string;
  statusText: string;
  pricingModeDisplay: string;
  parentAdviceDisplayName: string;
}

export const PRICING_CANCEL_BATCH_LIMIT = 50;

export const PRICING_PRODUCT_TABLE_DEVICE = 'adm_device_definition';
export const PRICING_PRODUCT_TABLE_SERVICE = 'wkf_activity_definition';

/** 目录未返回计价单位 code 时的临时占位（联调兜底，待目录接口补齐后移除）。 */
export const PRICING_CATALOG_FALLBACK_UNIT_CODE = 'U';

/** 划价单位槽：拆零 */
export const PRICING_BAND_KIND_SPLIT = 'SPLIT';

/** 划价单位槽：包装 */
export const PRICING_BAND_KIND_PACKAGE = 'PACKAGE';

export type PricingCatalogCategory = 'device' | 'service';

/** 已选划价行可选单位（拆零/包装） */
export interface PricingUnitBandSelectOption {
  bandKind: string;
  unitCode: string;
  unitName: string;
  unitPrice: number;
}

export type OrderBillingCategory = 'all' | 'long' | 'temp';

/** 手麻计费筛选：全部 / 手术 / 麻醉 */
export type SurgeryAnesthBillingKind = 'all' | 'anesthesia' | 'surgery';

/** 医嘱计费 Tab 表格行（父行可含 children 子项） */
export interface OrderBillingTableRow {
  id: string;
  requestId: string;
  basedOnId?: string;
  orderType: 'DEVICE_REQUEST' | 'MED_REQUEST' | 'SERVICE_REQUEST' | string;
  encounterId: string;
  patientId: string;
  isChild: boolean;
  category: Exclude<OrderBillingCategory, 'all'>;
  categoryText: string;
  /** 诊疗类目 code（手术 24 / 麻醉 25；手麻计费筛选用） */
  serviceCategoryEnum?: string;
  content: string;
  dosage: string;
  executionCount: string;
  chargeStatusEnum?: string;
  startStopAt: string;
  /** 补费/划价操作人姓名（子行） */
  requesterName?: string;
  /** 子行是否可冲销（待结算 nurse-pricing） */
  canCancel?: boolean;
  children?: OrderBillingTableRow[];
}

/** 医嘱费用明细弹窗行（来自已划价列表） */
export interface OrderChargeDetailRow {
  amount: number;
  id: string;
  itemName: string;
  orderId: string;
  statusText: string;
  unitPrice: string;
  usageCount: string;
}

function readCatalogField(row: NurseStationPricingCatalogRowDto, ...keys: string[]): unknown {
  const record = row as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return value;
    }
  }
  return undefined;
}

/** 归一化目录行字段（兼容 snake_case / productId 别名，并补齐表名与单位）。 */
export function normalizePricingCatalogRow(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): NurseStationPricingCatalogRowDto {
  const productDefinitionId = preserveApiIdForRequest(
    readCatalogField(row, 'productDefinitionId', 'product_definition_id'),
  );
  const productTableName = resolvePricingCatalogProductTableName(row, categoryHint);
  const chargeItemDefinitionId = preserveApiIdForRequest(
    readCatalogField(row, 'chargeItemDefinitionId', 'charge_item_definition_id'),
  );
  const unitCode = resolvePricingCatalogUnitCode(row);
  const productId = preserveApiIdForRequest(readCatalogField(row, 'productId', 'product_id'));
  const activityId =
    preserveApiIdForRequest(readCatalogField(row, 'activityId', 'activity_id')) ??
    (productTableName === PRICING_PRODUCT_TABLE_SERVICE
      ? (productId ?? productDefinitionId)
      : undefined);
  const deviceDefId =
    preserveApiIdForRequest(readCatalogField(row, 'deviceDefId', 'device_def_id')) ??
    (productTableName === PRICING_PRODUCT_TABLE_DEVICE
      ? (productId ?? productDefinitionId)
      : undefined);
  const defaultDispenseLocationId = preserveApiIdForRequest(
    readCatalogField(row, 'defaultDispenseLocationId', 'default_dispense_location_id'),
  );
  const defaultDispenseLocationName =
    String(
      readCatalogField(row, 'defaultDispenseLocationName', 'default_dispense_location_name') ?? '',
    ).trim() || undefined;
  const unitPriceRaw = readCatalogField(row, 'unitPrice', 'unit_price');
  const unitPrice =
    unitPriceRaw === undefined || unitPriceRaw === null
      ? row.unitPrice
      : (unitPriceRaw as number | string);
  const inventoryItemId = preserveApiIdForRequest(
    readCatalogField(row, 'inventoryItemId', 'inventory_item_id'),
  );
  const lotNumber =
    String(readCatalogField(row, 'lotNumber', 'lot_number') ?? row.lotNumber ?? '').trim() ||
    undefined;
  const remainingQuantityRaw = readCatalogField(row, 'remainingQuantity', 'remaining_quantity');
  const remainingQuantity =
    remainingQuantityRaw === undefined || remainingQuantityRaw === null
      ? row.remainingQuantity
      : (remainingQuantityRaw as number | string);
  const availableQuantityText =
    String(
      readCatalogField(row, 'availableQuantityText', 'available_quantity_text') ??
        row.availableQuantityText ??
        '',
    ).trim() || undefined;
  const expirationDate =
    String(
      readCatalogField(row, 'expirationDate', 'expiration_date') ?? row.expirationDate ?? '',
    ).trim() || undefined;

  return {
    ...row,
    productDefinitionId,
    productTableName,
    chargeItemDefinitionId,
    unitCode,
    productId,
    activityId,
    deviceDefId,
    defaultDispenseLocationId,
    defaultDispenseLocationName,
    unitPrice,
    inventoryItemId,
    lotNumber,
    remainingQuantity,
    availableQuantityText,
    expirationDate,
    busNo: String(readCatalogField(row, 'busNo', 'bus_no') ?? row.busNo ?? '').trim() || undefined,
    name: String(readCatalogField(row, 'name') ?? row.name ?? '').trim() || undefined,
    unitName:
      String(readCatalogField(row, 'unitName', 'unit_name') ?? row.unitName ?? '').trim() ||
      undefined,
  };
}

/** 解析目录行物理表名；缺失时按当前选品 Tab 回退。 */
export function resolvePricingCatalogProductTableName(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): string {
  const fromRow = String(
    readCatalogField(row, 'productTableName', 'product_table_name') ?? '',
  ).trim();
  if (fromRow) {
    return fromRow;
  }
  if (categoryHint === 'device') {
    return PRICING_PRODUCT_TABLE_DEVICE;
  }
  if (categoryHint === 'service') {
    return PRICING_PRODUCT_TABLE_SERVICE;
  }
  return '';
}

/** 解析目录行计价单位 code；优先主单位，其次 unitBands 首项，最后占位 code。 */
export function resolvePricingCatalogUnitCode(row: NurseStationPricingCatalogRowDto): string {
  const direct = String(readCatalogField(row, 'unitCode', 'unit_code') ?? '').trim();
  if (direct) {
    return direct;
  }
  for (const band of row.unitBands ?? []) {
    const bandCode = String(band.unitCode ?? '').trim();
    if (bandCode) {
      return bandCode;
    }
  }
  return PRICING_CATALOG_FALLBACK_UNIT_CODE;
}

/** 规范化 bandKind 为大写。 */
function normalizePricingBandKind(kind?: string): string {
  return String(kind ?? '')
    .trim()
    .toUpperCase();
}

/** 按 bandKind 查找单位槽。 */
function findPricingUnitBand(
  bands: NurseStationPricingCatalogRowDto['unitBands'],
  bandKind: string,
): NurseStationPricingCatalogUnitBandDto | undefined {
  const key = normalizePricingBandKind(bandKind);
  if (!key) {
    return undefined;
  }
  return (bands ?? []).find((band) => normalizePricingBandKind(band.bandKind) === key);
}

/**
 * 耗材默认划价单位槽：优先 SPLIT；无拆零则 PACKAGE；再回落首个有 unitCode 的槽。
 * 诊疗仅 PACKAGE。
 */
export function resolvePricingDefaultBandKind(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): string {
  if (!isPricingDeviceCatalogRow(row, categoryHint)) {
    return PRICING_BAND_KIND_PACKAGE;
  }
  const split = findPricingUnitBand(row.unitBands, PRICING_BAND_KIND_SPLIT);
  if (split && String(split.unitCode ?? '').trim()) {
    return PRICING_BAND_KIND_SPLIT;
  }
  const pkg = findPricingUnitBand(row.unitBands, PRICING_BAND_KIND_PACKAGE);
  if (pkg && String(pkg.unitCode ?? '').trim()) {
    return PRICING_BAND_KIND_PACKAGE;
  }
  const first = (row.unitBands ?? []).find((band) => String(band.unitCode ?? '').trim());
  return normalizePricingBandKind(first?.bandKind) || PRICING_BAND_KIND_PACKAGE;
}

/**
 * 列出可切换划价单位（仅包装/拆零；同 unitCode 去重）。
 * 单价取槽上 unitPrice（后端已按包装锚线性写入，禁止前端再 ÷ratio）。
 */
export function listPricingUnitBandSelectOptions(
  row: NurseStationPricingCatalogRowDto,
): PricingUnitBandSelectOption[] {
  const preferredKinds = [PRICING_BAND_KIND_SPLIT, PRICING_BAND_KIND_PACKAGE];
  const seenCodes = new Set<string>();
  const options: PricingUnitBandSelectOption[] = [];
  // 仅收集拆零/包装两槽，供已选清单切换
  for (const kind of preferredKinds) {
    const band = findPricingUnitBand(row.unitBands, kind);
    if (!band) {
      continue;
    }
    const unitCode = String(band.unitCode ?? '').trim();
    if (!unitCode || seenCodes.has(unitCode)) {
      continue;
    }
    const unitPrice = Number(band.unitPrice);
    if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
      continue;
    }
    seenCodes.add(unitCode);
    options.push({
      bandKind: kind,
      unitCode,
      unitName: String(band.unitName ?? '').trim() || unitCode,
      unitPrice,
    });
  }
  return options;
}

/**
 * 解析已选 band 下单价：优先槽 unitPrice（须有效正数）；否则回落行头 unitPrice。
 * <p>注意：{@code Number(null)===0}，不可把空槽价当成 0 元展示。</p>
 */
export function resolvePricingDisplayUnitPrice(
  row: NurseStationPricingCatalogRowDto,
  bandKind?: string,
): number {
  const kind = normalizePricingBandKind(bandKind) || resolvePricingDefaultBandKind(row);
  const band = findPricingUnitBand(row.unitBands, kind);
  const rawBandPrice = band?.unitPrice;
  // 槽价未装配（null/空）时不得 Number(null)→0，须回落行头包装锚价
  if (rawBandPrice !== undefined && rawBandPrice !== null && String(rawBandPrice).trim() !== '') {
    const bandPrice = Number(rawBandPrice);
    if (Number.isFinite(bandPrice) && bandPrice > 0) {
      return bandPrice;
    }
  }
  const header = Number(row.unitPrice ?? 0);
  return Number.isFinite(header) && header > 0 ? header : 0;
}

/** 解析已选 band 下 unitCode。 */
export function resolvePricingDisplayUnitCode(
  row: NurseStationPricingCatalogRowDto,
  bandKind?: string,
): string {
  const kind = normalizePricingBandKind(bandKind) || resolvePricingDefaultBandKind(row);
  const band = findPricingUnitBand(row.unitBands, kind);
  const bandCode = String(band?.unitCode ?? '').trim();
  if (bandCode) {
    return bandCode;
  }
  return resolvePricingCatalogUnitCode(row);
}

/** 解析已选 band 下单位展示名。 */
export function resolvePricingDisplayUnitName(
  row: NurseStationPricingCatalogRowDto,
  bandKind?: string,
): string {
  const kind = normalizePricingBandKind(bandKind) || resolvePricingDefaultBandKind(row);
  const band = findPricingUnitBand(row.unitBands, kind);
  const bandName = String(band?.unitName ?? '').trim();
  if (bandName) {
    return bandName;
  }
  return String(row.unitName ?? '').trim() || resolvePricingDisplayUnitCode(row, kind);
}

/**
 * 在包装/拆零之间换算数量（ratioToSplit 相对拆零）。
 * 缺 ratio 时原样返回，避免误改数量。
 */
export function convertPricingQtyBetweenBandKinds(
  quantity: number,
  fromBandKind: string,
  toBandKind: string,
  row: NurseStationPricingCatalogRowDto,
): number {
  const fromKind = normalizePricingBandKind(fromBandKind);
  const toKind = normalizePricingBandKind(toBandKind);
  if (!fromKind || !toKind || fromKind === toKind) {
    return quantity;
  }
  const fromBand = findPricingUnitBand(row.unitBands, fromKind);
  const toBand = findPricingUnitBand(row.unitBands, toKind);
  const fromRatio = Number(fromBand?.ratioToSplit);
  const toRatio = Number(toBand?.ratioToSplit);
  if (!Number.isFinite(fromRatio) || fromRatio <= 0 || !Number.isFinite(toRatio) || toRatio <= 0) {
    return quantity;
  }
  // 展示量 → 拆零 → 目标单位
  const splitQty = quantity * fromRatio;
  const converted = splitQty / toRatio;
  if (!Number.isFinite(converted) || converted <= 0) {
    return 1;
  }
  return Math.max(1, Math.round(converted * 1000) / 1000);
}

/** 目录行剩余数量展示：优先文案，其次拆零净量数字。 */
export function formatPricingCatalogRemainingQty(row: NurseStationPricingCatalogRowDto): string {
  const text = String(row.availableQuantityText ?? '').trim();
  if (text) {
    return text;
  }
  if (row.remainingQuantity === undefined || row.remainingQuantity === null) {
    return '—';
  }
  const n = Number(row.remainingQuantity);
  if (!Number.isFinite(n)) {
    return String(row.remainingQuantity);
  }
  return String(n);
}

/**
 * 目录行单价+单位拼接展示（如「2.90/支」；单位已并入剩余数量时不再单独列）。
 */
export function formatPricingCatalogUnitPriceWithUnit(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): string {
  const bandKind = resolvePricingDefaultBandKind(row, categoryHint);
  const priceText = formatMoneyDisplay(resolvePricingDisplayUnitPrice(row, bandKind));
  const unitText = String(resolvePricingDisplayUnitName(row, bandKind) ?? '').trim();
  if (!unitText) {
    return priceText;
  }
  return `${priceText}/${unitText}`;
}

/** 日期区间 → 划价时间查询边界（LocalDateTime 字符串） */
export function mapPricingDateRangeToQuery(range: string[]): {
  pricedTimeFrom?: string;
  pricedTimeTo?: string;
} {
  if (range.length !== 2) {
    return {};
  }
  const [startDate, endDate] = range;
  if (!startDate || !endDate) {
    return {};
  }
  return {
    pricedTimeFrom: `${startDate} 00:00:00`,
    pricedTimeTo: `${endDate} 23:59:59`,
  };
}

/** 目录行稳定键（品×批号/库存项去重） */
export function buildPricingCatalogRowKey(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): string {
  const tableName = resolvePricingCatalogProductTableName(row, categoryHint);
  const definitionId = preserveApiIdForRequest(row.productDefinitionId) ?? '';
  const inventoryItemId = preserveApiIdForRequest(row.inventoryItemId) ?? '';
  const lotNumber = String(row.lotNumber ?? '').trim();
  // 耗材批号行：库存项优先，其次批号；诊疗/无批号品级行仅用定义键
  if (inventoryItemId) {
    return `${tableName}:${definitionId}:${inventoryItemId}`;
  }
  if (lotNumber) {
    return `${tableName}:${definitionId}:lot:${lotNumber}`;
  }
  return `${tableName}:${definitionId}`;
}

/** 判断目录行是否为耗材 */
export function isPricingDeviceCatalogRow(
  row: NurseStationPricingCatalogRowDto,
  categoryHint?: PricingCatalogCategory,
): boolean {
  return (
    resolvePricingCatalogProductTableName(row, categoryHint).toLowerCase() ===
    PRICING_PRODUCT_TABLE_DEVICE
  );
}

/** 已划价分组列表 → 表格行 */
export function mapPricedOrderGroupsToTableRows(
  groups: NurseStationPricingPatientGroupDto[] | undefined,
): PricingConfirmationTableRow[] {
  const rows: PricingConfirmationTableRow[] = [];
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId) ?? '';
    // 按就诊分组扁平化，保留 encounterId 供多患者场景扩展。
    for (const order of group.pricedOrders ?? []) {
      rows.push(mapPricedOrderRowToTableRow(order, encounterId));
    }
  }
  return rows;
}

/** 长临 code → 前端筛选类别 */
function resolveOrderBillingCategory(therapyEnum?: string): Exclude<OrderBillingCategory, 'all'> {
  const code = String(therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'LONG' || code === 'LONG_TERM' || code.includes('LONG')) {
    return 'long';
  }
  return 'temp';
}

function readSupplementOrderField(
  dto: NurseStationSupplementParentOrderRowDto,
  ...keys: string[]
): unknown {
  const record = dto as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return value;
    }
  }
  return undefined;
}

function resolveSupplementOrderType(
  dto: NurseStationSupplementParentOrderRowDto,
  hasParent: boolean,
): OrderBillingTableRow['orderType'] {
  const orderTypeRaw = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  if (
    orderTypeRaw === 'MED_REQUEST' ||
    orderTypeRaw === 'SERVICE_REQUEST' ||
    orderTypeRaw === 'DEVICE_REQUEST'
  ) {
    return orderTypeRaw;
  }
  return hasParent ? 'SERVICE_REQUEST' : 'MED_REQUEST';
}

/** 父医嘱名称摘要：名称 + 规格 */
function buildSupplementParentContent(dto: NurseStationSupplementParentOrderRowDto): string {
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  const location = String(dto.performerLocationDisplay ?? '').trim();
  let title = name;
  if (spec) {
    title = name ? `${name}【${spec}】` : spec;
  }
  if (location) {
    title = title ? `${title}（${location}）` : location;
  }
  return title || '—';
}

/** 开立/停嘱时间展示（时间后跟人名，BR-IP-ORD-031） */
function buildSupplementParentStartStopAt(dto: NurseStationSupplementParentOrderRowDto): string {
  const openedRaw = String(dto.reqOpenedTime ?? '').trim();
  const stopRaw = String(dto.stopTime ?? '').trim();
  const requester = String(dto.requesterName ?? '').trim();
  const stopper = String(dto.stopperName ?? '').trim();
  let authoredText = openedRaw ? formatDateTime(openedRaw) : '';
  let stopText = stopRaw ? formatDateTime(stopRaw) : '';
  if (authoredText && requester) {
    authoredText = `${authoredText} ${requester}`;
  }
  if (stopText && stopper) {
    stopText = `${stopText} ${stopper}`;
  }
  if (authoredText && stopText) {
    return `${authoredText} / ${stopText}`;
  }
  return authoredText || stopText || '—';
}

/** 补费子项数量展示 */
function buildSupplementChildDosage(dto: NurseStationSupplementParentOrderRowDto): string {
  const quantity = dto.quantity;
  const unitDisplay = String(dto.unitDisplay ?? '').trim();
  if (quantity === null || quantity === undefined || quantity === '') {
    return unitDisplay || '—';
  }
  return unitDisplay ? `${quantity} ${unitDisplay}` : String(quantity);
}

/** 补费子项 DTO → 表格行 */
function mapSupplementChildOrderToTableRow(
  dto: NurseStationSupplementParentOrderRowDto,
  basedOnId: string,
  fallbackEncounterId = '',
): OrderBillingTableRow {
  const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
  const orderType = resolveSupplementOrderType(dto, true);
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (requestId && orderType ? `${requestId}\u0001${orderType}` : `supplement-child-${requestId}`);
  const pricedRaw = String(dto.pricedTime ?? '').trim();

  return {
    id,
    requestId,
    basedOnId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? fallbackEncounterId,
    patientId: preserveApiIdForRequest(dto.patientId) ?? '',
    isChild: true,
    category: 'temp',
    categoryText: String(dto.pricingModeDisplay ?? '').trim() || '补费项',
    serviceCategoryEnum: String(dto.categoryEnum ?? '').trim() || undefined,
    content: String(dto.productDisplayName ?? dto.adviceDisplayName ?? '').trim() || '—',
    dosage: buildSupplementChildDosage(dto),
    executionCount: String(dto.chargeStatusDisplay ?? dto.statusDisplay ?? '').trim() || '—',
    chargeStatusEnum: String(dto.chargeStatusEnum ?? '').trim() || undefined,
    startStopAt: pricedRaw ? formatDateTime(pricedRaw) : '—',
  };
}

/** 补费父医嘱 DTO → 表格行 */
export function mapSupplementParentOrderToTableRow(
  dto: NurseStationSupplementParentOrderRowDto,
  fallbackEncounterId = '',
): OrderBillingTableRow {
  const basedOnId = preserveApiIdForRequest(
    readSupplementOrderField(dto, 'basedOnId', 'based_on_id'),
  );
  if (basedOnId) {
    return mapSupplementChildOrderToTableRow(dto, basedOnId, fallbackEncounterId);
  }

  const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
  const orderType = resolveSupplementOrderType(dto, false);
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (requestId && orderType ? `${requestId}\u0001${orderType}` : `supplement-${requestId}`);
  const therapyDisplay = String(dto.therapyDisplay ?? '').trim();

  return {
    id,
    requestId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? fallbackEncounterId,
    patientId: preserveApiIdForRequest(dto.patientId) ?? '',
    isChild: false,
    category: resolveOrderBillingCategory(dto.therapyEnum),
    categoryText:
      therapyDisplay || (resolveOrderBillingCategory(dto.therapyEnum) === 'long' ? '长期' : '临时'),
    serviceCategoryEnum: String(dto.categoryEnum ?? '').trim() || undefined,
    content: buildSupplementParentContent(dto),
    dosage: String(dto.dosageDisplay ?? '').trim() || '—',
    executionCount: String(dto.frequencyDisplay ?? dto.statusDisplay ?? '').trim() || '—',
    startStopAt: buildSupplementParentStartStopAt(dto),
    children: [],
  };
}

/** 扁平行按 basedOnId → requestId 组装父子树 */
export function buildOrderBillingTree(flatRows: OrderBillingTableRow[]): OrderBillingTableRow[] {
  const nodeMap = new Map<string, OrderBillingTableRow>();
  const roots: OrderBillingTableRow[] = [];

  for (const row of flatRows) {
    const basedOnId = preserveApiIdForRequest(row.basedOnId);
    const node: OrderBillingTableRow = {
      ...row,
      basedOnId,
      isChild: Boolean(basedOnId),
      children: basedOnId ? undefined : [],
    };
    const mapKey = row.requestId || row.id;
    if (mapKey) {
      nodeMap.set(mapKey, node);
    }
  }

  for (const row of flatRows) {
    const mapKey = row.requestId || row.id;
    const node = mapKey ? nodeMap.get(mapKey) : undefined;
    if (!node) {
      continue;
    }
    const parentId = node.basedOnId;
    if (parentId && nodeMap.has(parentId)) {
      const parent = nodeMap.get(parentId);
      if (parent) {
        node.isChild = true;
        if (!parent.children) {
          parent.children = [];
        }
        parent.children.push(node);
      }
      continue;
    }
    if (!parentId) {
      node.isChild = false;
      roots.push(node);
      continue;
    }
    node.isChild = true;
    roots.push(node);
  }

  return roots;
}

/** 关键字筛选父子树：命中父行保留全部子行，命中子行保留其父行 */
export function filterOrderBillingTree(
  rows: OrderBillingTableRow[],
  keyword: string,
): OrderBillingTableRow[] {
  const text = keyword.trim().toLowerCase();
  if (!text) {
    return rows;
  }

  const filtered: OrderBillingTableRow[] = [];
  for (const row of rows) {
    const childRows = row.children ? filterOrderBillingTree(row.children, keyword) : [];
    const selfMatched =
      row.content.toLowerCase().includes(text) ||
      row.dosage.toLowerCase().includes(text) ||
      row.categoryText.toLowerCase().includes(text);
    if (selfMatched) {
      filtered.push({ ...row, children: row.children });
      continue;
    }
    if (childRows.length > 0) {
      filtered.push({ ...row, children: childRows });
    }
  }
  return filtered;
}

/** 手麻计费父行来源（手术/麻醉申请明细，不含嘱托与历史加收） */
export interface SurgeryAnesthBillingLineSource {
  serviceRequestId: string;
  content: string;
  quantity?: number | string;
  unitCode?: string;
  statusEnum?: string;
  /** 诊疗类目：手术 24 / 麻醉 25 */
  serviceCategoryEnum: string;
  plannedTime?: string;
  requesterName?: string;
  encounterId: string;
  patientId: string;
}

const SURGERY_ANESTH_REQUEST_STATUS_LABEL: Record<string, string> = {
  DRAFT: '待签发',
  ACTIVE: '已发送',
  VERIFIED: '已校对',
  HOLD: '暂挂',
  CANCELLED: '已取消',
  STOPPED: '已停嘱',
  COMPLETED: '已完成',
  MIXED: '状态不一致',
};

/** 嘱托、历史加收不作为计费父行（已划价明细走子行） */
export function isSurgeryAnesthBillingParentLine(lineKindCode: string | undefined) {
  const kind = String(lineKindCode ?? '')
    .trim()
    .toUpperCase();
  if (kind === 'ENTRUST' || kind === 'SURCHARGE') {
    return false;
  }
  return true;
}

/** 申请行状态码 → 与医嘱计费「频次/状态」列同位置的展示文案 */
function resolveSurgeryAnesthStatusText(statusEnum: string | undefined) {
  const code = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (!code) {
    return '—';
  }
  return SURGERY_ANESTH_REQUEST_STATUS_LABEL[code] ?? code;
}

/** 申请行数量展示 */
function buildSurgeryAnesthDosage(
  quantity: number | string | undefined,
  unitCode: string | undefined,
) {
  const unit = String(unitCode ?? '').trim();
  if (quantity === null || quantity === undefined || quantity === '') {
    return unit || '—';
  }
  return unit ? `${quantity} ${unit}` : String(quantity);
}

/** 拟手术/麻醉时间 + 开单人，对齐医嘱计费时间列 */
function buildSurgeryAnesthTime(plannedTime: string | undefined, requesterName: string | undefined) {
  const planned = String(plannedTime ?? '').trim();
  const requester = String(requesterName ?? '').trim();
  const timeText = planned ? formatDateTime(planned) : '';
  if (timeText && requester) {
    return `${timeText} ${requester}`;
  }
  return timeText || requester || '—';
}

/** 当前患者手术/麻醉申请行 + 已挂靠划价 → 与医嘱计费相同的父子树。 */
export function mapSurgeryAnesthApplyLinesToBillingTree(
  lines: SurgeryAnesthBillingLineSource[],
  pricedGroups: NurseStationPricingPatientGroupDto[] | undefined,
): OrderBillingTableRow[] {
  const parentRows: OrderBillingTableRow[] = [];
  const parentIds = new Set<string>();
  for (const line of lines) {
    const requestId = preserveApiIdForRequest(line.serviceRequestId) ?? '';
    if (!requestId || parentIds.has(requestId)) {
      continue;
    }
    parentIds.add(requestId);
    const encounterId = preserveApiIdForRequest(line.encounterId) ?? '';
    parentRows.push({
      id: `${requestId}\u0001SERVICE_REQUEST`,
      requestId,
      orderType: 'SERVICE_REQUEST',
      encounterId,
      patientId: preserveApiIdForRequest(line.patientId) ?? '',
      isChild: false,
      category: 'temp',
      categoryText: '临时',
      serviceCategoryEnum: String(line.serviceCategoryEnum ?? '').trim() || undefined,
      content: String(line.content ?? '').trim() || '—',
      dosage: buildSurgeryAnesthDosage(line.quantity, line.unitCode),
      executionCount: resolveSurgeryAnesthStatusText(line.statusEnum),
      startStopAt: buildSurgeryAnesthTime(line.plannedTime, line.requesterName),
      children: [],
    });
  }

  const flatRows: OrderBillingTableRow[] = [...parentRows];
  const seenChildIds = new Set<string>();
  for (const group of pricedGroups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId) ?? '';
    for (const pricedOrder of group.pricedOrders ?? []) {
      const childRow = mapPricedOrderRowToOrderBillingChildRow(pricedOrder, encounterId);
      const basedOnId = preserveApiIdForRequest(childRow?.basedOnId);
      if (!childRow || !basedOnId || !parentIds.has(basedOnId) || seenChildIds.has(childRow.id)) {
        continue;
      }
      flatRows.push(childRow);
      seenChildIds.add(childRow.id);
    }
  }

  return buildOrderBillingTree(flatRows);
}

/** 补费父医嘱分组 → 二级展开表格行 */
export function mapSupplementParentGroupsToTableRows(
  groups: NurseStationSupplementParentGroupDto[] | undefined,
): OrderBillingTableRow[] {
  return mergeOrderBillingTreeFromApiResponses(groups, undefined);
}

function readPricedOrderField(dto: NurseStationPricedOrderRowDto, ...keys: string[]): unknown {
  const record = dto as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return value;
    }
  }
  return undefined;
}

/** 已划价行 → 医嘱计费子行（parentRequestId/basedOnId 指向父医嘱 requestId） */
export function mapPricedOrderRowToOrderBillingChildRow(
  dto: NurseStationPricedOrderRowDto,
  fallbackEncounterId = '',
): OrderBillingTableRow | null {
  const basedOnId = preserveApiIdForRequest(
    readPricedOrderField(dto, 'parentRequestId', 'parent_request_id', 'basedOnId', 'based_on_id'),
  );
  if (!basedOnId) {
    return null;
  }

  const requestId = preserveApiIdForRequest(dto.orderId) ?? '';
  const orderTypeRaw = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  const orderType =
    orderTypeRaw === 'MED_REQUEST' ||
    orderTypeRaw === 'SERVICE_REQUEST' ||
    orderTypeRaw === 'DEVICE_REQUEST'
      ? orderTypeRaw
      : 'SERVICE_REQUEST';
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (requestId && orderType ? `${requestId}\u0001${orderType}` : `priced-child-${requestId}`);
  const pricedRaw = String(dto.pricedTime ?? '').trim();
  const unitDisplay = String(dto.unitDisplay ?? '').trim();
  const quantity = dto.quantity;
  let dosage = '—';
  if (quantity !== null && quantity !== undefined && quantity !== '') {
    dosage = unitDisplay ? `${quantity} ${unitDisplay}` : String(quantity);
  } else if (unitDisplay) {
    dosage = unitDisplay;
  }

  const chargeStatus = String(dto.chargeStatusDisplay ?? '').trim();
  const dispenseStatus = String(dto.dispenseStatusDisplay ?? '').trim();
  const statusText =
    chargeStatus && dispenseStatus
      ? `${chargeStatus} / ${dispenseStatus}`
      : chargeStatus || dispenseStatus || '—';

  return {
    id,
    requestId,
    basedOnId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? fallbackEncounterId,
    patientId: preserveApiIdForRequest(dto.patientId) ?? '',
    isChild: true,
    category: 'temp',
    categoryText: String(dto.pricingModeDisplay ?? '').trim() || '补费项',
    content: String(dto.productDisplayName ?? '').trim() || '—',
    dosage,
    executionCount: statusText,
    chargeStatusEnum: String(dto.chargeStatusEnum ?? '').trim() || undefined,
    startStopAt: pricedRaw ? formatDateTime(pricedRaw) : '—',
    requesterName: String(dto.requesterName ?? '').trim() || undefined,
    canCancel: resolvePricingRowCanCancel(dto),
  };
}

/**
 * 医嘱计费补费子行 → 冲销入参（与划价确费 IF-IP-PRC-06 同构）。
 *
 * @param row 医嘱计费子行
 */
export function mapOrderBillingChildRowToCancelItem(
  row: OrderBillingTableRow,
): NurseStationPricingCancelItemDto | null {
  if (!row?.isChild || !row.canCancel) {
    return null;
  }
  return mapPricingTableRowToCancelItem({
    id: row.id,
    encounterId: row.encounterId,
    requestId: row.requestId,
    orderType: row.orderType,
    canCancel: true,
    chargeStatusEnum: row.chargeStatusEnum,
    pricingContent: row.content,
    quantityText: row.dosage,
    unitPrice: 0,
    totalPrice: 0,
    pricingTime: row.startStopAt,
    statusText: row.executionCount,
    pricingModeDisplay: row.categoryText,
    parentAdviceDisplayName: '',
  });
}

/** 父医嘱列表 + 已划价列表 → 二级展开表格 */
export function mergeOrderBillingTreeFromApiResponses(
  supplementGroups: NurseStationSupplementParentGroupDto[] | undefined,
  pricedGroups: NurseStationPricingPatientGroupDto[] | undefined,
): OrderBillingTableRow[] {
  const flatRows: OrderBillingTableRow[] = [];
  const seenChildIds = new Set<string>();

  for (const group of supplementGroups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId) ?? '';
    for (const order of group.orders ?? []) {
      const row = mapSupplementParentOrderToTableRow(order, encounterId);
      flatRows.push(row);
      if (row.isChild) {
        seenChildIds.add(row.id);
      }
    }
  }

  for (const group of pricedGroups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId) ?? '';
    for (const pricedOrder of group.pricedOrders ?? []) {
      const childRow = mapPricedOrderRowToOrderBillingChildRow(pricedOrder, encounterId);
      if (!childRow || seenChildIds.has(childRow.id)) {
        continue;
      }
      flatRows.push(childRow);
      seenChildIds.add(childRow.id);
    }
  }

  return buildOrderBillingTree(flatRows);
}

/** 已划价行 → 医嘱费用明细弹窗行 */
export function mapPricedOrderRowToChargeDetailRow(
  dto: NurseStationPricedOrderRowDto,
  parentRequestId: string,
): OrderChargeDetailRow {
  const listRowKey = String(dto.listRowKey ?? dto.orderId ?? '').trim();
  const unitDisplay = String(dto.unitDisplay ?? '').trim();
  const quantity = dto.quantity;
  const unitPriceNum = Number(dto.unitPrice ?? 0) || 0;
  const unitPriceDisplay = formatMoneyDisplay(unitPriceNum);
  const unitPriceText = unitDisplay ? `${unitPriceDisplay} / ${unitDisplay}` : unitPriceDisplay;
  let usageCount = '—';
  if (quantity !== null && quantity !== undefined && quantity !== '') {
    usageCount = unitDisplay ? `${quantity} ${unitDisplay}` : String(quantity);
  }

  return {
    id: listRowKey || `${parentRequestId}-${dto.orderId ?? ''}`,
    orderId: parentRequestId,
    itemName: String(dto.productDisplayName ?? '').trim() || '—',
    unitPrice: unitPriceText,
    usageCount,
    amount: Number(dto.totalPrice ?? 0) || 0,
    statusText: String(dto.chargeStatusDisplay ?? '').trim() || '—',
  };
}

/** 已划价数量展示：quantity / unitDisplay 均可为空。 */
function formatPricedOrderQuantityText(
  quantity: NurseStationPricedOrderRowDto['quantity'],
  unitDisplay: NurseStationPricedOrderRowDto['unitDisplay'],
): string {
  const unitText = String(unitDisplay ?? '').trim();
  if (quantity === null || quantity === undefined || quantity === '') {
    return unitText || '—';
  }
  return unitText ? `${quantity} ${unitText}` : String(quantity);
}

/** 已划价行是否可取消（关联费用须待结算，BR-IP-PRC-023） */
export function resolvePricingRowCanCancel(dto: NurseStationPricedOrderRowDto): boolean {
  const orderType = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  if (orderType !== 'SERVICE_REQUEST' && orderType !== 'DEVICE_REQUEST') {
    return false;
  }
  const chargeCode = String(dto.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (chargeCode === 'PENDING_SETTLEMENT') {
    return true;
  }
  return false;
}

/** 单条已划价 DTO → 表格行 */
export function mapPricedOrderRowToTableRow(
  dto: NurseStationPricedOrderRowDto,
  fallbackEncounterId = '',
): PricingConfirmationTableRow {
  const encounterId = preserveApiIdForRequest(dto.encounterId) ?? fallbackEncounterId;
  const requestId = preserveApiIdForRequest(dto.orderId) ?? '';
  const orderType = String(dto.orderType ?? '').trim();
  const listRowKey = String(dto.listRowKey ?? dto.orderId ?? '').trim();
  const chargeStatus = String(dto.chargeStatusDisplay ?? '').trim();
  const dispenseStatus = String(dto.dispenseStatusDisplay ?? '').trim();
  const statusText =
    chargeStatus && dispenseStatus
      ? `${chargeStatus} / ${dispenseStatus}`
      : chargeStatus || dispenseStatus || '—';
  const quantityText = formatPricedOrderQuantityText(dto.quantity, dto.unitDisplay);

  return {
    id: listRowKey || `${encounterId}-${dto.orderId ?? ''}`,
    encounterId,
    requestId,
    orderType,
    chargeStatusEnum: String(dto.chargeStatusEnum ?? '').trim() || undefined,
    canCancel: resolvePricingRowCanCancel(dto),
    pricingContent: String(dto.productDisplayName ?? '').trim() || '—',
    quantityText,
    unitPrice: Number(dto.unitPrice ?? 0) || 0,
    totalPrice: Number(dto.totalPrice ?? 0) || 0,
    pricingTime: String(dto.pricedTime ?? '').trim() || '—',
    statusText,
    pricingModeDisplay: String(dto.pricingModeDisplay ?? '').trim() || '—',
    parentAdviceDisplayName: String(dto.parentAdviceDisplayName ?? '').trim(),
  };
}

/** 表格行 → 取消划价写行 */
export function mapPricingTableRowToCancelItem(
  row: PricingConfirmationTableRow,
): NurseStationPricingCancelItemDto | null {
  const requestId = preserveApiIdForRequest(row.requestId);
  const encounterId = preserveApiIdForRequest(row.encounterId);
  const orderType = String(row.orderType ?? '')
    .trim()
    .toUpperCase();
  if (!requestId || !encounterId) {
    return null;
  }
  if (orderType !== 'SERVICE_REQUEST' && orderType !== 'DEVICE_REQUEST') {
    return null;
  }
  return {
    orderType,
    requestId,
    encounterId,
  };
}

/**
 * 已选表格行 → 冲销 / 改划价时间 items（去重、保序）。
 * 改时与冲销行键同构，复用本映射即可。
 */
export function mapSelectedPricingRowsToCancelItems(
  rows: PricingConfirmationTableRow[],
  selectedRowIds: Set<string>,
): NurseStationPricingCancelItemDto[] {
  const items: NurseStationPricingCancelItemDto[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    if (!selectedRowIds.has(row.id) || !row.canCancel) {
      continue;
    }
    const item = mapPricingTableRowToCancelItem(row);
    if (!item) {
      continue;
    }
    const key = `${item.orderType}\u0001${item.requestId}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    items.push(item);
  }
  return items;
}

/** 解析耗材发放库房：显式入参优先，否则取目录 defaultDispenseLocationId。 */
export function resolvePricingCatalogPerformLocation(
  catalogRow: NurseStationPricingCatalogRowDto,
  performLocation?: string,
  categoryHint?: PricingCatalogCategory,
): string | undefined {
  const normalized = normalizePricingCatalogRow(catalogRow, categoryHint);
  if (!isPricingDeviceCatalogRow(normalized, categoryHint)) {
    return undefined;
  }
  return (
    preserveApiIdForRequest(performLocation) ??
    preserveApiIdForRequest(normalized.defaultDispenseLocationId) ??
    undefined
  );
}

interface BuildDirectPricingWriteItemOptions {
  encounterId: string;
  patientId: string;
  accountId: string;
  catalogRow: NurseStationPricingCatalogRowDto;
  quantity: number;
  performLocation?: string;
  categoryHint?: PricingCatalogCategory;
  /** 已选单位槽（耗材默认 SPLIT） */
  selectedBandKind?: string;
  /** 已选单位下展示单价（与 unitCode 同口径） */
  displayUnitPrice?: number;
}

/** 收集直接划价写行缺失字段（中文标签，供提交前提示）。 */
export function listDirectPricingWriteItemMissingFields(
  options: BuildDirectPricingWriteItemOptions,
): string[] {
  const { encounterId, patientId, accountId, quantity, categoryHint } = options;
  const missing: string[] = [];
  if (!preserveApiIdForRequest(encounterId)) {
    missing.push('就诊ID');
  }
  if (!preserveApiIdForRequest(patientId)) {
    missing.push('患者ID');
  }
  if (!preserveApiIdForRequest(accountId)) {
    missing.push('账户ID');
  }
  if (quantity < 1) {
    missing.push('有效数量');
  }

  const catalogRow = normalizePricingCatalogRow(options.catalogRow, categoryHint);
  const productTableName = resolvePricingCatalogProductTableName(catalogRow, categoryHint);
  if (!productTableName) {
    missing.push('产品类型');
  }
  if (!preserveApiIdForRequest(catalogRow.productDefinitionId)) {
    missing.push('产品定义ID');
  }
  if (!preserveApiIdForRequest(catalogRow.chargeItemDefinitionId)) {
    missing.push('定价定义ID');
  }
  const bandKind =
    options.selectedBandKind || resolvePricingDefaultBandKind(catalogRow, categoryHint);
  if (!resolvePricingDisplayUnitCode(catalogRow, bandKind)) {
    missing.push('计价单位');
  }

  const isDevice = isPricingDeviceCatalogRow(catalogRow, categoryHint);
  if (isDevice) {
    if (!preserveApiIdForRequest(catalogRow.deviceDefId)) {
      missing.push('耗材定义ID');
    }
    if (!resolvePricingCatalogPerformLocation(catalogRow, options.performLocation, categoryHint)) {
      missing.push('发放库房');
    }
  } else if (!preserveApiIdForRequest(catalogRow.activityId)) {
    missing.push('诊疗活动ID');
  }

  return missing;
}

/** 已选目录行 → 直接划价写请求行 */
export function buildDirectPricingWriteItem(
  options: BuildDirectPricingWriteItemOptions,
): NurseStationPricingWriteItemDto | null {
  const missingFields = listDirectPricingWriteItemMissingFields(options);
  if (missingFields.length > 0) {
    return null;
  }

  const { encounterId, patientId, accountId, quantity, performLocation, categoryHint } = options;
  const catalogRow = normalizePricingCatalogRow(options.catalogRow, categoryHint);
  const productTableName = resolvePricingCatalogProductTableName(catalogRow, categoryHint);
  const productDefinitionId = preserveApiIdForRequest(catalogRow.productDefinitionId);
  const chargeItemDefinitionId = preserveApiIdForRequest(catalogRow.chargeItemDefinitionId);
  const bandKind =
    options.selectedBandKind || resolvePricingDefaultBandKind(catalogRow, categoryHint);
  const unitCode = resolvePricingDisplayUnitCode(catalogRow, bandKind);
  const unitPriceRaw =
    options.displayUnitPrice !== undefined
      ? options.displayUnitPrice
      : resolvePricingDisplayUnitPrice(catalogRow, bandKind);

  const isDevice = isPricingDeviceCatalogRow(catalogRow, categoryHint);
  const activityId = preserveApiIdForRequest(catalogRow.activityId);
  const deviceDefId = preserveApiIdForRequest(catalogRow.deviceDefId);
  const performLocationId = resolvePricingCatalogPerformLocation(
    catalogRow,
    performLocation,
    categoryHint,
  );
  const lotNumber = String(catalogRow.lotNumber ?? '').trim() || undefined;

  if (
    !productTableName ||
    !productDefinitionId ||
    !chargeItemDefinitionId ||
    !unitCode ||
    (isDevice && !deviceDefId) ||
    (!isDevice && !activityId)
  ) {
    return null;
  }

  const totalPrice = calcMoneyLineTotal(unitPriceRaw, quantity);

  return {
    encounterId,
    patientId,
    accountId,
    productTableName,
    productDefinitionId,
    activityId: isDevice ? undefined : activityId,
    deviceDefId: isDevice ? deviceDefId : undefined,
    quantity,
    unitCode,
    chargeItemDefinitionId,
    unitPrice: String(unitPriceRaw),
    totalPrice: String(totalPrice),
    performLocation: isDevice ? performLocationId : undefined,
    lotNumber: isDevice ? lotNumber : undefined,
  };
}

interface BuildSupplementPricingWriteItemOptions extends BuildDirectPricingWriteItemOptions {
  parentRequestAnchor: NurseStationPricingParentRequestAnchorDto;
}

/** 收集医嘱补费写行缺失字段（含父锚点）。 */
export function listSupplementPricingWriteItemMissingFields(
  options: BuildSupplementPricingWriteItemOptions,
): string[] {
  const missing = listDirectPricingWriteItemMissingFields(options);
  const requestId = preserveApiIdForRequest(options.parentRequestAnchor?.requestId);
  const orderType = String(options.parentRequestAnchor?.orderType ?? '')
    .trim()
    .toUpperCase();
  if (!requestId) {
    missing.push('父医嘱ID');
  }
  if (orderType !== 'MED_REQUEST' && orderType !== 'SERVICE_REQUEST') {
    missing.push('父医嘱类型');
  }
  return missing;
}

/** 已选目录行 → 医嘱补费写请求行 */
export function buildSupplementPricingWriteItem(
  options: BuildSupplementPricingWriteItemOptions,
): NurseStationPricingWriteItemDto | null {
  const missingFields = listSupplementPricingWriteItemMissingFields(options);
  if (missingFields.length > 0) {
    return null;
  }
  const directItem = buildDirectPricingWriteItem(options);
  if (!directItem) {
    return null;
  }
  const requestId = preserveApiIdForRequest(options.parentRequestAnchor.requestId);
  const orderType = String(options.parentRequestAnchor.orderType ?? '')
    .trim()
    .toUpperCase() as 'MED_REQUEST' | 'SERVICE_REQUEST';
  if (!requestId) {
    return null;
  }
  return {
    ...directItem,
    parentRequestAnchor: {
      orderType,
      requestId,
    },
  };
}
