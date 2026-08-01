import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';
import type {
  OrderTemplateApplyConfirmLineKind,
  OrderTemplateApplyConfirmPreview,
  OrderTemplateApplyConfirmTemplateRow,
} from './order-template-apply-confirm.types';

import type { OrderTemplateLineRowItem } from '#/api/basic/order-template';
import type {
  DoctorStationAdviceDraftLineRequest,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { MedicationLineDraftSlice } from '#/views/basic/ordertemplate/order-template-medication-units';

import { pageFrequencyApi } from '#/api/basic/frequency';
import {
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LINE_KIND_DEVICE,
  ORDER_TEMPLATE_LINE_KIND_MEDICATION,
  ORDER_TEMPLATE_LINE_KIND_SERVICE,
} from '#/api/basic/order-template';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
  sumAdviceCatalogInventorySplitQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  applyLineDoseFieldsFromBands,
  hydrateDraftLineDoseFromDosageText,
} from '#/views/basic/ordertemplate/order-template-medication-units';

import { fetchAdviceCatalogRowsByPrepickedKeys } from './advice-catalog-prepicked';
import { saveAdviceDraftBatchDirect } from './advice-draft-save.service';
import {
  buildDeviceProductSelectPatch,
  buildWesternProductSelectPatch,
  createAdviceDrugItem,
  inferDoseBandConvertK,
  pickMainDiagnosisDisplayName,
} from './advice-edit';
import { mapCatalogRowToOption } from './advice-list';
import { buildAdviceDraftLines } from './advice-medication-draft';
import { inferAdviceLineEditorKind, validateWesternEditLineItem } from './advice-row';
import { ADVICE_PRICING_SOURCE_CODE } from './advice-tab.constants';
import {
  resolveAdviceLineUnitPrice,
  totalAmountUnitDisplayLabel,
  unitCodeByBandKind,
} from './medication-unit-bands';

/** 组套套用：药品库存不足明细 */
export interface OrderTemplateMedicationStockIssue {
  productName: string;
  detail: string;
}

/** 组套套用入参 */
export interface ApplyOrderTemplateParams {
  templateKindEnum?: string;
  lines: OrderTemplateLineRowItem[];
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
  feeNatureOptions: TerminologyOptionDto[];
  /** 当前就诊主表行，用于药品 sortNumber 追加 */
  existingAdviceRows?: AdviceRowItem[];
  /** 已在合并弹窗确认后的暂存行（跳过组内重建） */
  preparedDraftLines?: DoctorStationAdviceDraftLineRequest[];
  /** 已在合并弹窗确认后的组套行（删行 / 改数量） */
  preConfirmedTemplateRows?: OrderTemplateApplyConfirmTemplateRow[];
}

/** 组套套用结果 */
export interface ApplyOrderTemplateResult {
  appliedLineCount: number;
}

/** 组套套用业务异常（含库存不足明细） */
export class OrderTemplateApplyError extends Error {
  stockIssues: OrderTemplateMedicationStockIssue[];

  constructor(message: string, stockIssues: OrderTemplateMedicationStockIssue[] = []) {
    super(message);
    this.name = 'OrderTemplateApplyError';
    this.stockIssues = stockIssues;
  }
}

/** 组套行 → 目录 prepicked 键 */
function prepickedKeyFromTemplateLine(
  line: OrderTemplateLineRowItem,
): null | PrepickedProductKeyDto {
  const tbl = String(line.productTableName ?? '').trim();
  let defId = '';
  switch (line.lineKindEnum) {
    case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
      defId = String(line.deviceDefinitionId ?? '').trim();

      break;
    }
    case ORDER_TEMPLATE_LINE_KIND_MEDICATION: {
      defId = String(line.medicationDefinitionId ?? '').trim();

      break;
    }
    case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
      defId = String(line.activityDefinitionId ?? '').trim();

      break;
    }
    // No default
  }
  if (!defId || !tbl) {
    return null;
  }
  return { productDefinitionId: defId, productTableName: tbl };
}

/**
 * 套用时为模板 groupKey 分配新 groupId，避免多次套用同一组套时并成一组。
 * 同一次套用内同一 groupKey 仍映射为同一新号。
 */
function remapTemplateGroupIds(items: AdviceDrugItem[]): AdviceDrugItem[] {
  const oldToNew = new Map<string, string>();
  let seq = 0;
  return items.map((item) => {
    const oldGid = String(item.groupId ?? '').trim();
    if (!oldGid) {
      return item;
    }
    if (!oldToNew.has(oldGid)) {
      oldToNew.set(oldGid, String(Date.now() + seq));
      seq += 1;
    }
    return { ...item, groupId: oldToNew.get(oldGid) };
  });
}

/** 成组行补齐组首的用法/天数/频次（与组套维护端一致） */
function normalizeTemplateLinesForApply(
  lines: OrderTemplateLineRowItem[],
): OrderTemplateLineRowItem[] {
  const sorted = [...lines].toSorted((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  const leaderByGroup = new Map<string, OrderTemplateLineRowItem>();
  for (const line of sorted) {
    const groupKey = String(line.groupKey ?? '').trim();
    if (groupKey && !leaderByGroup.has(groupKey)) {
      leaderByGroup.set(groupKey, line);
    }
  }
  return sorted.map((line) => {
    const groupKey = String(line.groupKey ?? '').trim();
    if (!groupKey) {
      return line;
    }
    const leader = leaderByGroup.get(groupKey);
    if (!leader || leader === line) {
      return line;
    }
    return {
      ...line,
      routeCode: leader.routeCode ?? line.routeCode,
      defaultDays: leader.defaultDays ?? line.defaultDays,
      frequencyId: leader.frequencyId ?? line.frequencyId,
      frequencyText: leader.frequencyText ?? line.frequencyText,
    };
  });
}

/** 批量解析 frequencyId → rateCode */
async function buildRateCodeMapByFrequencyIds(
  frequencyIds: string[],
): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  const unique = [
    ...new Set(frequencyIds.map((id) => preserveApiIdForRequest(id)).filter(Boolean)),
  ];
  if (unique.length === 0) {
    return map;
  }
  try {
    const res = await pageFrequencyApi({
      pageNo: 1,
      pageSize: Math.max(unique.length, 200),
    });
    const data = (
      res as { data?: { content?: Array<{ id?: number | string; rateCode?: string }> } }
    )?.data;
    const list = data?.content ?? [];
    for (const item of list) {
      const id = preserveApiIdForRequest(item.id);
      const rateCode = String(item.rateCode ?? '').trim();
      if (id && rateCode) {
        map.set(id, rateCode);
      }
    }
  } catch {
    /* 频次主数据不可用时回落 frequencyText 匹配 */
  }
  return map;
}

/** 解析组套行频次为医生站 rateCode */
function resolveTemplateLineRateCode(
  line: OrderTemplateLineRowItem,
  rateCodeMap: Map<string, string>,
  medicationRateOptions: TerminologyOptionDto[],
): string {
  const frequencyId = preserveApiIdForRequest(line.frequencyId);
  if (frequencyId) {
    const fromMap = rateCodeMap.get(frequencyId);
    if (fromMap) {
      return fromMap;
    }
  }
  const text = String(line.frequencyText ?? '').trim();
  if (text) {
    const hit = medicationRateOptions.find((opt) => String(opt.label ?? '').trim() === text);
    if (hit?.value) {
      return String(hit.value).trim();
    }
  }
  return '';
}

/** 单位 code → 计量带 kind */
function findBandKindByUnitCode(
  unitCode: string,
  bands: AdviceProductOption['unitBands'],
): string | undefined {
  const code = String(unitCode ?? '').trim();
  if (!code) {
    return undefined;
  }
  const hit = (bands ?? []).find((band) => String(band.unitCode ?? '').trim() === code);
  const kind = String(hit?.bandKind ?? '').trim();
  return kind ? kind.toUpperCase() : undefined;
}

/** 组套需求量换算为 PACKAGE 展示数量，便于与目录库存对比 */
function convertRequiredQtyToPackageDisplay(
  requiredQty: number,
  requiredUnitCode: string,
  unitBands: AdviceProductOption['unitBands'],
): number | undefined {
  const qty = Number(requiredQty);
  if (!Number.isFinite(qty) || qty <= 0) {
    return undefined;
  }
  const bands = normalizeInboundProductUnitBands(unitBands);
  const unitCode = String(requiredUnitCode ?? '').trim();
  const kind = findBandKindByUnitCode(unitCode, unitBands);
  if (!kind) {
    const packageCode = unitCodeByBandKind(unitBands, 'PACKAGE');
    if (packageCode && packageCode === unitCode) {
      return qty;
    }
    return undefined;
  }
  const splitQty = convertInboundQtyBetweenBandKinds(qty, kind, 'SPLIT', bands);
  if (splitQty === undefined) {
    return undefined;
  }
  return convertAdviceCatalogSplitQtyToPackageDisplay(splitQty, unitBands);
}

/** 组套行展示名（预览 enrich 后的 productName 优先） */
function resolveTemplateLineProductName(line: OrderTemplateLineRowItem, index: number): string {
  const fromLine = String(line.productName ?? '').trim();
  if (fromLine) {
    return fromLine;
  }
  return `第 ${index + 1} 项`;
}

/** 可开立目录未命中：按行类型给出友好原因（停供/停用/无库存） */
function resolveCatalogMissingApplyMessage(line: OrderTemplateLineRowItem, index: number): string {
  const name = resolveTemplateLineProductName(line, index);
  const kind = String(line.lineKindEnum ?? '')
    .trim()
    .toUpperCase();
  if (kind === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
    return `药品「${name}」暂无库存，无法套用组套`;
  }
  if (kind === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
    return `诊疗项目「${name}」已停用，无法套用组套`;
  }
  if (kind === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
    return `耗材「${name}」暂不可用，无法套用组套`;
  }
  return `「${name}」当前不可开立，无法套用组套`;
}

/** 校验单条药品行库存；不足时返回明细文案 */
function resolveMedicationStockIssue(
  line: OrderTemplateLineRowItem,
  catalog: AdviceProductOption,
): null | OrderTemplateMedicationStockIssue {
  const productName = String(line.productName ?? catalog.name ?? '').trim() || '未命名药品';
  if (isAdviceCatalogZeroStock(catalog)) {
    return {
      productName,
      detail: '暂无库存',
    };
  }

  const requiredQty = Number(line.defaultQuantity ?? 1);
  const requiredUnitCode =
    String(line.defaultUnitCode ?? catalog.defaultUnitCode ?? '').trim() ||
    unitCodeByBandKind(catalog.unitBands, 'PACKAGE') ||
    '';
  const requiredPackageQty = convertRequiredQtyToPackageDisplay(
    requiredQty,
    requiredUnitCode,
    catalog.unitBands,
  );
  const availablePackageQty = resolveAdviceCatalogPackageDisplayQty(catalog);

  if (
    requiredPackageQty !== undefined &&
    availablePackageQty !== undefined &&
    requiredPackageQty > availablePackageQty + 1e-6
  ) {
    const requiredText =
      requiredUnitCode && Number.isFinite(requiredQty)
        ? `${requiredQty} ${line.defaultUnitName ?? requiredUnitCode}`
        : String(requiredQty);
    const availableText = formatAdviceCatalogStockCellText(catalog);
    return {
      productName,
      detail: `库存不足（需要 ${requiredText}，当前 ${availableText}）`,
    };
  }

  if (
    requiredPackageQty !== undefined &&
    availablePackageQty === undefined &&
    sumAdviceCatalogInventorySplitQty(catalog.inventoryList) === undefined
  ) {
    return null;
  }

  return null;
}

/** 批量拉取目录（prepickedProductKeys） */
async function fetchCatalogOptionsByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  params: Pick<ApplyOrderTemplateParams, 'encounterId' | 'organizationUnitId' | 'patientId'>,
): Promise<AdviceProductOption[]> {
  if (keys.length === 0) {
    return [];
  }
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    return [];
  }
  const rawRows = await fetchAdviceCatalogRowsByPrepickedKeys(keys, {
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
  });
  return rawRows.map((item) => mapCatalogRowToOption(item));
}

/** 诊疗行执行机构回落：组套行 → 目录默认 → 当前登录机构 */
function applyServicePerformOrgFallback(
  item: AdviceDrugItem,
  catalog: AdviceProductOption,
  organizationUnitId?: string,
): AdviceDrugItem {
  if (inferAdviceLineEditorKind(item) !== 'SERVICE') {
    return item;
  }
  const orgId = String(
    item.performOrgId ?? catalog.defaultPerformOrgId ?? organizationUnitId ?? '',
  ).trim();
  if (!orgId) {
    return item;
  }
  return {
    ...item,
    performOrgId: orgId,
    performOrgName:
      String(item.performOrgName ?? catalog.defaultPerformOrgName ?? '').trim() || undefined,
  };
}

/** 组套行未维护 dosageText 时，按目录计量带回落默认单次剂量（对齐维护端选药默认） */
function hydrateMedicationSingleDoseIfMissing(item: AdviceDrugItem): AdviceDrugItem {
  if (Number(item.singleDose) > 0) {
    return item;
  }
  const bands = item.unitBands;
  if (!bands?.length) {
    return item;
  }
  const slice: MedicationLineDraftSlice = { _unitBands: bands };
  applyLineDoseFieldsFromBands(slice, bands, { resetDefault: true });
  return inferDoseBandConvertK({
    ...item,
    singleDose: slice._singleDose,
    doseConvertRatio: slice._doseConvertRatio,
    doseMainUnit: slice._doseMainUnit ?? item.doseMainUnit,
    doseSubUnit: slice._doseSubUnit ?? item.doseSubUnit,
    doseBandConvertK: slice._doseBandConvertK ?? item.doseBandConvertK,
  });
}

/** 套用校验失败时的可读缺口（便于定位维护端缺项） */
function resolveOrderTemplateApplyLineGap(item: AdviceDrugItem): string | undefined {
  if (!String(item.selectedId ?? '').trim()) {
    return '未选择项目';
  }
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE') {
    if (!(Number(item.totalAmount) > 0)) {
      return '耗材数量须大于 0';
    }
    if (!String(item.totalAmountUnit ?? '').trim()) {
      return '缺少耗材单位';
    }
    return undefined;
  }
  if (kind === 'SERVICE') {
    if (!(Number(item.serviceQuantity) > 0)) {
      return '诊疗数量须大于 0';
    }
    if (!(Number(item.totalAmount) > 0)) {
      return '诊疗执行次数须大于 0';
    }
    if (!String(item.performOrgId ?? '').trim()) {
      return '缺少执行机构';
    }
    return undefined;
  }
  if (!String(item.diagnosis ?? '').trim()) {
    return '缺少诊断（请先录入就诊主诊断）';
  }
  if (!String(item.contractId ?? '').trim()) {
    return '缺少费别';
  }
  if (!String(item.usage ?? '').trim()) {
    return '缺少用法';
  }
  if (!String(item.frequency ?? '').trim()) {
    return '缺少频次';
  }
  if (!(Number(item.singleDose) > 0)) {
    return '缺少单次剂量';
  }
  if (!(Number(item.totalAmount) > 0)) {
    return '总量须大于 0';
  }
  if (!String(item.totalAmountUnit ?? '').trim()) {
    return '缺少总量单位';
  }
  return undefined;
}

function resolveOrderTemplateApplyLineDisplayName(
  line: OrderTemplateLineRowItem | undefined,
  item: AdviceDrugItem | undefined,
  index: number,
): string {
  const fromLine = String(line?.productName ?? '').trim();
  if (fromLine) {
    return fromLine;
  }
  const fromCatalog = String(item?.selectedDrugName ?? '').trim();
  if (fromCatalog) {
    return fromCatalog;
  }
  return `第 ${index + 1} 项`;
}

/** 组套 dosageText → 编辑行单次剂量字段 */
function applyTemplateDosageTextToDrugItem(item: AdviceDrugItem, dosageText: string | undefined) {
  if (!String(dosageText ?? '').trim()) {
    return;
  }
  const slice: MedicationLineDraftSlice = {
    dosageText,
    _unitBands: item.unitBands,
  };
  hydrateDraftLineDoseFromDosageText(slice);
  if (slice._singleDose !== undefined) {
    item.singleDose = slice._singleDose;
  }
  if (slice._doseConvertRatio !== undefined) {
    item.doseConvertRatio = slice._doseConvertRatio;
  }
  if (slice._doseMainUnit) {
    item.doseMainUnit = slice._doseMainUnit;
  }
  if (slice._doseSubUnit) {
    item.doseSubUnit = slice._doseSubUnit;
  }
  if (slice._doseBandConvertK !== undefined) {
    item.doseBandConvertK = slice._doseBandConvertK;
  }
}

/** 组套行 + 目录行 → 医嘱编辑行（组套参数优先） */
function buildAdviceDrugItemFromTemplateLine(
  line: OrderTemplateLineRowItem,
  catalog: AdviceProductOption,
  params: ApplyOrderTemplateParams,
  rateCodeMap: Map<string, string>,
  mainDiagnosisName: string,
): AdviceDrugItem {
  const ctx = {
    feeNatureOptions: params.feeNatureOptions,
    medicationRateOptions: params.medicationRateOptions,
    medicationRouteOptions: params.medicationRouteOptions,
  };
  const seed = createAdviceDrugItem(undefined, ctx);
  const tbl = String(catalog.productTableName ?? '')
    .trim()
    .toLowerCase();
  const templateQty = (() => {
    const raw = Number(line.defaultQuantity);
    return Number.isFinite(raw) && raw > 0 ? raw : 1;
  })();
  const templateUnitCode = String(line.defaultUnitCode ?? '').trim();
  const templateUnitName = String(line.defaultUnitName ?? '').trim();
  const rateCode = resolveTemplateLineRateCode(line, rateCodeMap, params.medicationRateOptions);
  const routeCode = String(line.routeCode ?? '').trim();
  const daysRaw = Number(line.defaultDays);
  const medicationDays =
    Number.isFinite(daysRaw) && daysRaw > 0 ? Math.trunc(daysRaw) : seed.medicationDays;
  const performOrgId =
    String(line.defaultPerformOrgId ?? catalog.defaultPerformOrgId ?? '').trim() || undefined;
  const performOrgName =
    String(line.defaultPerformOrgName ?? catalog.defaultPerformOrgName ?? '').trim() || undefined;

  if (tbl === 'wkf_activity_definition') {
    const uCode = templateUnitCode || '次';
    const uName = resolveServiceTemplateUnitDisplayName(
      templateUnitName,
      templateUnitCode,
      catalog,
    );
    const orgId =
      performOrgId ||
      (params.organizationUnitId ? String(params.organizationUnitId).trim() : undefined);
    const execRaw = Number(line.defaultDays);
    const executionCount = Number.isFinite(execRaw) && execRaw > 0 ? Math.trunc(execRaw) : 1;
    return applyServicePerformOrgFallback(
      {
        ...seed,
        selectedId: String(catalog.id ?? '').trim(),
        selectedDrugName: catalog.name || '',
        selectedDrugSpec: catalog.spec || '',
        productTableName: catalog.productTableName,
        selectedDrugPrice: catalog.price ?? '',
        diagnosis: mainDiagnosisName,
        serviceQuantity: templateQty,
        totalAmount: executionCount,
        totalAmountUnit: uCode,
        totalUnitSalesCode: uCode,
        totalUnitSalesName: uName,
        frequency: rateCode || seed.frequency,
        performOrgId: orgId,
        performOrgName:
          performOrgName ||
          (orgId && orgId === params.organizationUnitId ? '当前登录机构' : undefined),
        expanded: false,
      },
      catalog,
      params.organizationUnitId,
    );
  }

  if (tbl === 'adm_device_definition') {
    const devicePatch = buildDeviceProductSelectPatch(catalog);
    const uCode = templateUnitCode || String(devicePatch.totalAmountUnit ?? '').trim() || 'U';
    let selectedDrugPrice = devicePatch.selectedDrugPrice ?? catalog.price ?? '';
    if (uCode) {
      const linePrice = resolveAdviceLineUnitPrice({
        bands: catalog.unitBands ?? devicePatch.unitBands,
        catalogUnitPrice: devicePatch.catalogUnitPrice ?? catalog.catalogUnitPrice,
        totalAmountUnit: uCode,
      });
      if (linePrice !== undefined) selectedDrugPrice = linePrice;
    }
    return {
      ...seed,
      ...devicePatch,
      selectedId: String(catalog.id ?? '').trim(),
      selectedDrugName: catalog.name || '',
      selectedDrugSpec: catalog.spec || '',
      productTableName: catalog.productTableName,
      selectedDrugPrice,
      diagnosis: mainDiagnosisName,
      totalAmount: templateQty,
      totalAmountUnit: uCode,
      performOrgId,
      performOrgName,
      expanded: false,
    };
  }

  const catalogPatch = buildWesternProductSelectPatch(seed, catalog);
  const item: AdviceDrugItem = {
    ...seed,
    ...catalogPatch,
    selectedId: String(catalog.id ?? '').trim(),
    selectedDrugName: catalog.name || seed.selectedDrugName,
    selectedDrugSpec: catalog.spec || seed.selectedDrugSpec,
    productTableName: catalog.productTableName,
    diagnosis: mainDiagnosisName,
    usage: routeCode || catalogPatch.usage || seed.usage,
    frequency: rateCode || catalogPatch.frequency || seed.frequency,
    medicationDays,
    totalAmount: templateQty,
    totalAmountUnit: templateUnitCode || catalogPatch.totalAmountUnit || seed.totalAmountUnit,
    groupId: String(line.groupKey ?? '').trim() || undefined,
    performOrgId,
    performOrgName,
    expanded: false,
  };
  applyTemplateDosageTextToDrugItem(item, line.dosageText);
  return hydrateMedicationSingleDoseIfMissing(inferDoseBandConvertK(item));
}

/** 加载就诊诊断快照（静默套用写草稿用） */
async function loadEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
): Promise<EncounterDiagnosisRowDto[]> {
  const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
  const list = res?.diagnosisList ?? res?.content ?? [];
  return Array.isArray(list) ? list : [];
}

/** 组套行 → 展示类型 */
function resolveTemplateLineKindLabel(line: OrderTemplateLineRowItem): string {
  switch (line.lineKindEnum) {
    case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
      return '耗材';
    }
    case ORDER_TEMPLATE_LINE_KIND_MEDICATION: {
      return '药品';
    }
    case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
      return '诊疗';
    }
    default: {
      return '项目';
    }
  }
}

/** 药品行剂量展示（优先组套 dosageText，回落编辑行单次剂量） */
function resolveMedicationConfirmDosageText(
  line: OrderTemplateLineRowItem,
  item: AdviceDrugItem,
): string | undefined {
  const fromLine = String(line.dosageText ?? '').trim();
  if (fromLine) {
    return fromLine;
  }
  const singleDose = Number(item.singleDose);
  if (!Number.isFinite(singleDose) || singleDose <= 0) {
    return undefined;
  }
  const doseUnit = String(item.doseMainUnit ?? item.doseSubUnit ?? '').trim();
  return doseUnit ? `${singleDose}${doseUnit}` : String(singleDose);
}

/** 诊疗组套行：预览无单位名时回落「次」，避免字典 code 当展示名 */
function resolveServiceTemplateUnitDisplayName(
  templateUnitName: string,
  templateUnitCode: string,
  catalog: AdviceProductOption,
): string {
  const fromTemplate = String(templateUnitName ?? '').trim();
  if (fromTemplate && fromTemplate !== templateUnitCode) {
    return fromTemplate;
  }
  const catalogUnitName = String(catalog.defaultUnitName ?? catalog.unitName ?? '').trim();
  const catalogUnitCode = String(catalog.defaultUnitCode ?? '').trim();
  if (catalogUnitName && catalogUnitName !== catalogUnitCode) {
    return catalogUnitName;
  }
  const code = String(templateUnitCode ?? '').trim();
  if (code && !/^\d+$/.test(code)) {
    return code;
  }
  return '次';
}

/** 确认弹窗：总量/数量单位展示名（不落字典 code） */
function resolveTemplateConfirmUnitLabel(
  item: AdviceDrugItem,
  lineKind: OrderTemplateApplyConfirmLineKind,
): string {
  const salesName = String(item.totalUnitSalesName ?? '').trim();
  const packageName = String(item.totalUnitPackageName ?? '').trim();
  const unitCode = String(item.totalAmountUnit ?? '').trim();
  const fromBands = totalAmountUnitDisplayLabel(item);
  if (lineKind === 'SERVICE') {
    if (salesName && salesName !== unitCode) {
      return salesName;
    }
    if (fromBands && fromBands !== unitCode && !/^\d+$/.test(fromBands)) {
      return fromBands;
    }
    return '次';
  }
  if (fromBands && fromBands !== unitCode) {
    return fromBands;
  }
  if (salesName && salesName !== unitCode) {
    return salesName;
  }
  if (packageName && packageName !== unitCode) {
    return packageName;
  }
  return fromBands;
}

/** 组套项目名称：规格为计价单位时不拼入名称 */
function shouldAppendTemplateProductSpec(
  baseName: string,
  spec: string,
  lineKind?: OrderTemplateApplyConfirmLineKind,
): boolean {
  const specNorm = String(spec ?? '').trim();
  if (!specNorm || baseName.includes(specNorm)) {
    return false;
  }
  if (lineKind === 'SERVICE' && ['U', '次', '项'].includes(specNorm)) {
    return false;
  }
  return true;
}

function resolveTemplateConfirmLineKind(
  item: AdviceDrugItem | undefined,
  line: OrderTemplateLineRowItem,
): OrderTemplateApplyConfirmLineKind {
  if (item) {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'SERVICE' || kind === 'DEVICE') {
      return kind;
    }
    return 'MEDICATION';
  }
  switch (line.lineKindEnum) {
    case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
      return 'DEVICE';
    }
    case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
      return 'SERVICE';
    }
    default: {
      return 'MEDICATION';
    }
  }
}

/** 组套行 + 套用编辑行 → 确认弹窗展示行 */
function buildTemplateConfirmRows(
  normalizedLines: OrderTemplateLineRowItem[],
  catalogMap: Map<string, AdviceProductOption>,
  drugItems: AdviceDrugItem[],
): OrderTemplateApplyConfirmTemplateRow[] {
  return normalizedLines.map((line, index) => {
    const key = prepickedKeyFromTemplateLine(line);
    const catalog = key
      ? catalogMap.get(`${key.productTableName}:${key.productDefinitionId}`)
      : undefined;
    const item = drugItems[index];
    const lineKind = resolveTemplateConfirmLineKind(item, line);
    const lineName = String(line.productName ?? '').trim();
    const catalogName = String(catalog?.name ?? item?.selectedDrugName ?? '').trim();
    const spec = String(catalog?.spec ?? line.productSpec ?? item?.selectedDrugSpec ?? '').trim();
    const baseName = lineName || catalogName;
    const itemName = baseName
      ? shouldAppendTemplateProductSpec(baseName, spec, lineKind)
        ? `${baseName} ${spec}`
        : baseName
      : `第 ${index + 1} 项`;

    if (lineKind === 'SERVICE' && item) {
      const quantity =
        Number.isFinite(Number(item.serviceQuantity)) && Number(item.serviceQuantity) > 0
          ? Number(item.serviceQuantity)
          : 1;
      const unitName = resolveTemplateConfirmUnitLabel(item, lineKind);
      const execCount = Number(item.totalAmount);
      const dosageText =
        Number.isFinite(execCount) && execCount > 0 ? `执行 ${execCount} 次` : undefined;
      return {
        key: `tpl-${String(line.lineId ?? index)}`,
        lineIndex: index,
        lineKind,
        itemName,
        kindLabel: resolveTemplateLineKindLabel(line),
        quantity,
        unitName,
        dosageText,
      };
    }

    if (lineKind === 'DEVICE' && item) {
      const quantity =
        Number.isFinite(Number(item.totalAmount)) && Number(item.totalAmount) > 0
          ? Number(item.totalAmount)
          : 1;
      return {
        key: `tpl-${String(line.lineId ?? index)}`,
        lineIndex: index,
        lineKind,
        itemName,
        kindLabel: resolveTemplateLineKindLabel(line),
        quantity,
        unitName: resolveTemplateConfirmUnitLabel(item, lineKind),
        dosageText: undefined,
      };
    }

    if (item) {
      const quantity =
        Number.isFinite(Number(item.totalAmount)) && Number(item.totalAmount) > 0
          ? Number(item.totalAmount)
          : 1;
      return {
        key: `tpl-${String(line.lineId ?? index)}`,
        lineIndex: index,
        lineKind,
        itemName,
        kindLabel: resolveTemplateLineKindLabel(line),
        quantity,
        unitName: resolveTemplateConfirmUnitLabel(item, lineKind),
        dosageText: resolveMedicationConfirmDosageText(line, item),
      };
    }

    const qtyRaw = Number(line.defaultQuantity);
    const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
    return {
      key: `tpl-${String(line.lineId ?? index)}`,
      lineIndex: index,
      lineKind,
      itemName,
      kindLabel: resolveTemplateLineKindLabel(line),
      quantity,
      unitName: String(line.defaultUnitName ?? '').trim() || (lineKind === 'SERVICE' ? '次' : ''),
      dosageText: String(line.dosageText ?? '').trim() || undefined,
    };
  });
}

/** 确认弹窗：按保留组套行重建暂存批次并写入修改后的数量 */
export function buildDraftLinesFromConfirmedTemplateRows(
  draftLines: DoctorStationAdviceDraftLineRequest[],
  templateRows: OrderTemplateApplyConfirmTemplateRow[],
): DoctorStationAdviceDraftLineRequest[] {
  return [...templateRows]
    .toSorted((left, right) => left.lineIndex - right.lineIndex)
    .map((row) => {
      const source = draftLines[row.lineIndex];
      if (!source) {
        return undefined;
      }
      const qty = Number.isFinite(row.quantity) && row.quantity > 0 ? row.quantity : 1;
      if (row.lineKind === 'SERVICE') {
        return {
          ...source,
          servicePayload: source.servicePayload
            ? { ...source.servicePayload, quantity: qty }
            : source.servicePayload,
        };
      }
      if (row.lineKind === 'DEVICE') {
        return {
          ...source,
          devicePayload: source.devicePayload
            ? { ...source.devicePayload, quantity: qty }
            : source.devicePayload,
        };
      }
      return {
        ...source,
        medicationPayload: source.medicationPayload
          ? { ...source.medicationPayload, quantity: qty }
          : source.medicationPayload,
      };
    })
    .filter((line): line is DoctorStationAdviceDraftLineRequest => line !== undefined);
}

interface OrderTemplateApplyDraftPack {
  draftLines: DoctorStationAdviceDraftLineRequest[];
  normalizedLines: OrderTemplateLineRowItem[];
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
}

/** 校验组套行并组装暂存批次（不含落库） */
async function buildOrderTemplateApplyDraftPack(
  params: ApplyOrderTemplateParams,
): Promise<OrderTemplateApplyDraftPack> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new OrderTemplateApplyError('缺少就诊或患者信息，无法套用组套');
  }

  const templateKind = String(params.templateKindEnum ?? '')
    .trim()
    .toUpperCase();
  if (templateKind === ORDER_TEMPLATE_KIND_TCM) {
    throw new OrderTemplateApplyError('中药组套暂不支持套用，请使用西药通用组套');
  }

  const normalizedLines = normalizeTemplateLinesForApply(params.lines);
  if (normalizedLines.length === 0) {
    throw new OrderTemplateApplyError('组套明细为空，无法套用');
  }

  const prepickedKeys = normalizedLines
    .map((line) => prepickedKeyFromTemplateLine(line))
    .filter((key): key is PrepickedProductKeyDto => key !== null);
  if (prepickedKeys.length !== normalizedLines.length) {
    throw new OrderTemplateApplyError('组套明细存在无效项目，无法套用');
  }

  const [catalogList, rateCodeMap, encounterDiagnosisRows] = await Promise.all([
    fetchCatalogOptionsByPrepickedKeys(prepickedKeys, params),
    buildRateCodeMapByFrequencyIds(
      normalizedLines.map((line) => preserveApiIdForRequest(line.frequencyId) ?? ''),
    ),
    loadEncounterDiagnosisRows(encounterId, patientId),
  ]);

  const catalogMap = new Map<string, AdviceProductOption>();
  for (const opt of catalogList) {
    const id = String(opt.productDefinitionId ?? '').trim();
    const tbl = String(opt.productTableName ?? '').trim();
    if (id && tbl) {
      catalogMap.set(`${tbl}:${id}`, opt);
    }
  }

  const mainDiagnosisName = pickMainDiagnosisDisplayName(encounterDiagnosisRows);
  const stockIssues: OrderTemplateMedicationStockIssue[] = [];
  const catalogMissingMessages: string[] = [];
  const drugItems: AdviceDrugItem[] = [];

  for (let index = 0; index < normalizedLines.length; index += 1) {
    const line = normalizedLines[index];
    const key = prepickedKeyFromTemplateLine(line);
    if (!key) {
      continue;
    }
    const catalog = catalogMap.get(`${key.productTableName}:${key.productDefinitionId}`);
    if (!catalog) {
      catalogMissingMessages.push(resolveCatalogMissingApplyMessage(line, index));
      continue;
    }

    if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
      const issue = resolveMedicationStockIssue(line, catalog);
      if (issue) {
        stockIssues.push(issue);
      }
    }

    drugItems.push(
      buildAdviceDrugItemFromTemplateLine(line, catalog, params, rateCodeMap, mainDiagnosisName),
    );
  }

  if (catalogMissingMessages.length > 0) {
    throw new OrderTemplateApplyError(catalogMissingMessages.join('；'));
  }

  if (stockIssues.length > 0) {
    const names = stockIssues.map((item) => `药品「${item.productName}」${item.detail}`).join('；');
    throw new OrderTemplateApplyError(`${names}，无法套用组套`, stockIssues);
  }

  const invalidIndex = drugItems.findIndex((item) => !validateWesternEditLineItem(item));
  if (invalidIndex !== -1) {
    const badItem = drugItems[invalidIndex];
    const badLine = normalizedLines[invalidIndex];
    const name = resolveOrderTemplateApplyLineDisplayName(badLine, badItem, invalidIndex);
    const gap = resolveOrderTemplateApplyLineGap(badItem);
    throw new OrderTemplateApplyError(
      gap ? `「${name}」${gap}，无法套用组套` : `「${name}」参数不完整，无法套用组套`,
    );
  }

  const remappedDrugItems = remapTemplateGroupIds(drugItems);

  const draftLines = (() => {
    try {
      return buildAdviceDraftLines(remappedDrugItems, [], {
        encounterDiagnosisRows,
        existingAdviceRows: params.existingAdviceRows ?? [],
      });
    } catch (error) {
      throw new OrderTemplateApplyError(error instanceof Error ? error.message : '请求组包失败');
    }
  })();

  const templateRows = buildTemplateConfirmRows(normalizedLines, catalogMap, remappedDrugItems);

  return { draftLines, normalizedLines, templateRows };
}

/** 组套套用前：组装确认弹窗所需组套行 */
export async function prepareOrderTemplateApplyConfirm(
  params: ApplyOrderTemplateParams & { templateName?: string },
): Promise<OrderTemplateApplyConfirmPreview> {
  const { draftLines, templateRows } = await buildOrderTemplateApplyDraftPack(params);

  return {
    templateName: String(params.templateName ?? '').trim(),
    templateRows,
    draftLines,
  };
}

/** 组套套用至当前就诊草稿（DIRECT_ONLY：不解析用法绑定，避免二次带出） */
export async function applyOrderTemplateToAdviceDraft(
  params: ApplyOrderTemplateParams,
): Promise<ApplyOrderTemplateResult> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new OrderTemplateApplyError('缺少就诊或患者信息，无法套用组套');
  }

  let draftLines = params.preparedDraftLines;
  if (draftLines === undefined) {
    const pack = await buildOrderTemplateApplyDraftPack(params);
    draftLines = pack.draftLines;
  }

  if (params.preConfirmedTemplateRows !== undefined) {
    draftLines = buildDraftLinesFromConfirmedTemplateRows(
      draftLines,
      params.preConfirmedTemplateRows,
    );
  }

  const saveRes = await saveAdviceDraftBatchDirect({
    encounterId,
    patientId,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
    lines: draftLines,
  });
  if (saveRes.success === false) {
    throw new OrderTemplateApplyError(String(saveRes.message ?? '').trim() || '保存医嘱草稿失败');
  }

  return { appliedLineCount: draftLines.length };
}
