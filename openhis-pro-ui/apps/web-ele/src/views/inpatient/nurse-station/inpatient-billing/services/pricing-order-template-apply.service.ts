import type {
  OrderTemplateBindingGroup,
  OrderTemplateHeaderItem,
  OrderTemplateLineRowItem,
} from '#/api/basic/order-template';
import type {
  NurseStationPricingCatalogRowDto,
  PrepickedProductKeyDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';
import type { PricingCatalogCategory } from './map-nurse-station-inpatient-billing';

import {
  listNurseStationPricingOrderTemplatesApi,
  ORDER_TEMPLATE_APPLY_CONTEXT_NURSE_PRICING,
  ORDER_TEMPLATE_LINE_KIND_DEVICE,
  ORDER_TEMPLATE_LINE_KIND_SERVICE,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import { httpPageNurseStationPricingCatalog } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildPricingCatalogRowKey,
  isPricingDeviceCatalogRow,
  normalizePricingCatalogRow,
  PRICING_PRODUCT_TABLE_DEVICE,
  PRICING_PRODUCT_TABLE_SERVICE,
  resolvePricingDefaultBandKind,
  resolvePricingDisplayUnitCode,
  resolvePricingDisplayUnitName,
  resolvePricingDisplayUnitPrice,
} from './map-nurse-station-inpatient-billing';

/** 与后端 PRODUCT_DEFINITION_RM_PAGE_SIZE_MAX 对齐 */
const CATALOG_PAGE_SIZE_MAX = 100;

/** 划价组套套用后写入已选清单的行 */
export interface PricingOrderTemplateSelectedItem {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
  category: PricingCatalogCategory;
  quantity: number;
  selectedBandKind: string;
  displayUnitPrice: number;
  displayUnitCode: string;
  displayUnitName: string;
}

/** 划价组套套用入参 */
export interface ApplyPricingOrderTemplateParams {
  orderTemplateId: string;
  /** 住院科室（取药模板键回落） */
  organizationUnitId?: string;
  /** 就诊主键（耗材目录优先按就诊科室取药模板） */
  encounterId?: string;
  /** 已选清单：同项目累加数量 */
  existingSelected?: PricingOrderTemplateSelectedItem[];
}

/** 划价组套套用结果 */
export interface ApplyPricingOrderTemplateResult {
  items: PricingOrderTemplateSelectedItem[];
  appliedLineCount: number;
  missingProductNames: string[];
  templateName: string;
}

/** 划价组套套用业务异常 */
export class PricingOrderTemplateApplyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PricingOrderTemplateApplyError';
  }
}

/** 拉取护士站划价组套分段列表（IF-BAS-OTL-11） */
export async function fetchNurseStationPricingOrderTemplateGroups(
  searchKey?: string,
): Promise<OrderTemplateBindingGroup | undefined> {
  const res = await listNurseStationPricingOrderTemplatesApi({
    searchKey: searchKey?.trim() || undefined,
  });
  return res?.groups;
}

/** 组套行 → 目录 prepicked 键（仅 SERVICE / DEVICE） */
function prepickedKeyFromPricingTemplateLine(
  line: OrderTemplateLineRowItem,
): null | PrepickedProductKeyDto {
  const lineKind = String(line.lineKindEnum ?? '')
    .trim()
    .toUpperCase();
  let defId = '';
  let tableName = String(line.productTableName ?? '').trim();
  if (lineKind === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
    defId = preserveApiIdForRequest(line.deviceDefinitionId) ?? '';
    tableName = tableName || PRICING_PRODUCT_TABLE_DEVICE;
  } else if (lineKind === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
    defId = preserveApiIdForRequest(line.activityDefinitionId) ?? '';
    tableName = tableName || PRICING_PRODUCT_TABLE_SERVICE;
  }
  if (!defId || !tableName) {
    return null;
  }
  return { productDefinitionId: defId, productTableName: tableName };
}

/** 去重 prepicked 键 */
function dedupePrepickedKeys(keys: PrepickedProductKeyDto[]): PrepickedProductKeyDto[] {
  const seen = new Set<string>();
  const out: PrepickedProductKeyDto[] = [];
  for (const key of keys) {
    const productDefinitionId = String(key.productDefinitionId ?? '').trim();
    const productTableName = String(key.productTableName ?? '').trim();
    if (!productDefinitionId || !productTableName) {
      continue;
    }
    const mapKey = `${productTableName}:${productDefinitionId}`;
    if (seen.has(mapKey)) {
      continue;
    }
    seen.add(mapKey);
    out.push({ productDefinitionId, productTableName });
  }
  return out;
}

/** 按物理表分组 prepicked 键 */
function groupPrepickedKeysByTable(
  keys: PrepickedProductKeyDto[],
): Map<string, PrepickedProductKeyDto[]> {
  const grouped = new Map<string, PrepickedProductKeyDto[]>();
  for (const key of keys) {
    const list = grouped.get(key.productTableName);
    if (list) {
      list.push(key);
    } else {
      grouped.set(key.productTableName, [key]);
    }
  }
  return grouped;
}

/** 按 prepickedProductKeys 批量拉划价目录（IF-IP-PRC-01） */
async function fetchPricingCatalogRowsByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  organizationUnitId?: string,
  encounterId?: string,
): Promise<NurseStationPricingCatalogRowDto[]> {
  if (keys.length === 0) {
    return [];
  }
  const uniqueKeys = dedupePrepickedKeys(keys);
  const grouped = groupPrepickedKeysByTable(uniqueKeys);
  const rows: NurseStationPricingCatalogRowDto[] = [];

  // 按表分片请求，避免三表均分 pageSize 截断同表多项
  for (const [productTableName, tableKeys] of grouped) {
    for (let offset = 0; offset < tableKeys.length; offset += CATALOG_PAGE_SIZE_MAX) {
      const chunk = tableKeys.slice(offset, offset + CATALOG_PAGE_SIZE_MAX);
      const res = await httpPageNurseStationPricingCatalog({
        pageNo: 1,
        pageSize: Math.max(chunk.length, 1),
        productTableNames: [productTableName],
        prepickedProductKeys: chunk,
        organizationUnitId,
        encounterId,
      });
      const { content } = parsePagedRowsFromApiData<NurseStationPricingCatalogRowDto>(res);
      rows.push(...content);
    }
  }
  return rows;
}

/** 解析组套行默认数量（非法则回落 1） */
function resolveTemplateLineQuantity(line: OrderTemplateLineRowItem): number {
  const raw = Number(line.defaultQuantity ?? 1);
  if (!Number.isFinite(raw) || raw < 1) {
    return 1;
  }
  return raw;
}

/** 目录行 → 划价选品类别 */
function resolveCatalogCategory(catalog: NurseStationPricingCatalogRowDto): PricingCatalogCategory {
  return isPricingDeviceCatalogRow(catalog) ? 'device' : 'service';
}

/** 组套/已选行补齐默认单位槽与展示单价。 */
function withPricingBandFields(
  item: Omit<
    PricingOrderTemplateSelectedItem,
    'selectedBandKind' | 'displayUnitPrice' | 'displayUnitCode' | 'displayUnitName'
  > &
    Partial<
      Pick<
        PricingOrderTemplateSelectedItem,
        'selectedBandKind' | 'displayUnitPrice' | 'displayUnitCode' | 'displayUnitName'
      >
    >,
): PricingOrderTemplateSelectedItem {
  const selectedBandKind =
    item.selectedBandKind || resolvePricingDefaultBandKind(item.catalog, item.category);
  return {
    rowKey: item.rowKey,
    catalog: item.catalog,
    category: item.category,
    quantity: item.quantity,
    selectedBandKind,
    displayUnitPrice:
      item.displayUnitPrice ?? resolvePricingDisplayUnitPrice(item.catalog, selectedBandKind),
    displayUnitCode:
      item.displayUnitCode ?? resolvePricingDisplayUnitCode(item.catalog, selectedBandKind),
    displayUnitName:
      item.displayUnitName ?? resolvePricingDisplayUnitName(item.catalog, selectedBandKind),
  };
}

/**
 * 套用划价组套：IF-08 预览结构 → IF-IP-PRC-01 prepicked 补价/库位 → 合并已选清单。
 * 确认划价仍由页面走 IF-IP-PRC-02/04。
 */
export async function applyPricingOrderTemplateToSelectedItems(
  params: ApplyPricingOrderTemplateParams,
): Promise<ApplyPricingOrderTemplateResult> {
  const orderTemplateId = preserveApiIdForRequest(params.orderTemplateId);
  if (!orderTemplateId) {
    throw new PricingOrderTemplateApplyError('组套无效，无法套用');
  }

  const preview = await previewOrderTemplateApplyApi({
    orderTemplateId,
    applyContextCode: ORDER_TEMPLATE_APPLY_CONTEXT_NURSE_PRICING,
  });
  const templateName = String(preview?.header?.name ?? '').trim() || '未命名组套';
  const lines = [...(preview?.lines ?? [])].toSorted(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
  );
  if (lines.length === 0) {
    throw new PricingOrderTemplateApplyError(`组套「${templateName}」明细为空，无法套用`);
  }

  // 组套行 → prepicked 键；无效行记入缺失名单
  const lineKeys: Array<{
    line: OrderTemplateLineRowItem;
    key: PrepickedProductKeyDto;
  }> = [];
  const missingProductNames: string[] = [];
  for (const line of lines) {
    const key = prepickedKeyFromPricingTemplateLine(line);
    const name = String(line.productName ?? '').trim() || '未命名项目';
    if (!key) {
      missingProductNames.push(name);
      continue;
    }
    lineKeys.push({ line, key });
  }
  if (lineKeys.length === 0) {
    throw new PricingOrderTemplateApplyError(`组套「${templateName}」无可套用的耗材/诊疗行`);
  }

  const catalogRows = await fetchPricingCatalogRowsByPrepickedKeys(
    lineKeys.map((item) => item.key),
    params.organizationUnitId,
    params.encounterId,
  );
  // 目录：定义键 → 首条批号/品级行（组套按品匹配，取首批）
  const catalogByDefinitionKey = new Map<string, NurseStationPricingCatalogRowDto>();
  for (const raw of catalogRows) {
    const categoryHint: PricingCatalogCategory =
      String(raw.productTableName ?? '').toLowerCase() === PRICING_PRODUCT_TABLE_DEVICE
        ? 'device'
        : 'service';
    const catalog = normalizePricingCatalogRow(raw, categoryHint);
    const definitionId = preserveApiIdForRequest(catalog.productDefinitionId) ?? '';
    const tableName = String(catalog.productTableName ?? '').trim();
    if (!definitionId || !tableName) {
      continue;
    }
    const definitionKey = `${tableName}:${definitionId}`;
    // 同品多批号：保留首条（FIFO 序与目录装配一致）
    if (!catalogByDefinitionKey.has(definitionKey)) {
      catalogByDefinitionKey.set(definitionKey, catalog);
    }
  }

  // 以已选清单为底，同项目累加数量
  const selectedMap = new Map<string, PricingOrderTemplateSelectedItem>();
  for (const item of params.existingSelected ?? []) {
    selectedMap.set(item.rowKey, withPricingBandFields(item));
  }

  let appliedLineCount = 0;
  for (const { line, key } of lineKeys) {
    const lookupKey = `${key.productTableName}:${key.productDefinitionId}`;
    const catalog = catalogByDefinitionKey.get(lookupKey);
    const name = String(line.productName ?? catalog?.name ?? '').trim() || '未命名项目';
    if (!catalog) {
      missingProductNames.push(name);
      continue;
    }
    const category = resolveCatalogCategory(catalog);
    const rowKey = buildPricingCatalogRowKey(catalog, category);
    const quantity = resolveTemplateLineQuantity(line);
    const existing = selectedMap.get(rowKey);
    if (existing) {
      existing.quantity += quantity;
    } else {
      selectedMap.set(
        rowKey,
        withPricingBandFields({
          rowKey,
          catalog,
          category,
          quantity,
        }),
      );
    }
    appliedLineCount += 1;
  }

  if (appliedLineCount === 0) {
    throw new PricingOrderTemplateApplyError(
      `组套「${templateName}」项目在划价目录中均未找到，请检查目录维护`,
    );
  }

  return {
    items: [...selectedMap.values()],
    appliedLineCount,
    missingProductNames,
    templateName,
  };
}

/** 在分段列表中按 id 查找组套头 */
export function findPricingOrderTemplateHeaderInGroups(
  groups: OrderTemplateBindingGroup | undefined,
  templateId: string,
): OrderTemplateHeaderItem | undefined {
  const id = preserveApiIdForRequest(templateId);
  if (!id || !groups) {
    return undefined;
  }
  const lists = [
    groups.agreementList,
    groups.personalList,
    groups.departmentList,
    groups.hospitalList,
  ];
  for (const list of lists) {
    const hit = (list ?? []).find((item) => preserveApiIdForRequest(item.orderTemplateId) === id);
    if (hit) {
      return hit;
    }
  }
  return undefined;
}
