import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';

import type { OrderTemplateLineRowItem } from '#/api/basic/order-template';
import type {
  DoctorStationAdviceCatalogRowDto,
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
import {
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationAdviceDraftBatch,
} from '#/api/doctor-station/advice.http';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
  sumAdviceCatalogInventorySplitQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';
import { hydrateDraftLineDoseFromDosageText } from '#/views/basic/ordertemplate/order-template-medication-units';

import {
  buildWesternProductSelectPatch,
  createAdviceDrugItem,
  inferDoseBandConvertK,
  pickMainDiagnosisDisplayName,
} from './advice-edit';
import { mapCatalogRowToOption } from './advice-list';
import { buildAdviceDraftLines } from './advice-medication-draft';
import { validateWesternEditLineItem } from './advice-row';
import { ADVICE_PRICING_SOURCE_CODE } from './advice-tab.constants';
import { unitCodeByBandKind } from './medication-unit-bands';

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

/** 校验单条药品行库存；不足时返回明细文案 */
function resolveMedicationStockIssue(
  line: OrderTemplateLineRowItem,
  catalog: AdviceProductOption,
): null | OrderTemplateMedicationStockIssue {
  const productName = String(line.productName ?? catalog.name ?? '').trim() || '未命名药品';
  if (isAdviceCatalogZeroStock(catalog)) {
    const availableText = formatAdviceCatalogStockCellText(catalog);
    return {
      productName,
      detail: `库存不足（当前 ${availableText}）`,
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
  const res = await httpPageDoctorStationAdviceCatalog({
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    pageNo: 1,
    pageSize: Math.max(keys.length, 20),
    prepickedProductKeys: keys,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
  });
  const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  return rawRows.map((item) => mapCatalogRowToOption(item));
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
    const uName = templateUnitName || uCode;
    const orgId =
      performOrgId ||
      (params.organizationUnitId ? String(params.organizationUnitId).trim() : undefined);
    const execRaw = Number(line.defaultDays);
    const executionCount = Number.isFinite(execRaw) && execRaw > 0 ? Math.trunc(execRaw) : 1;
    return {
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
    };
  }

  if (tbl === 'adm_device_definition') {
    const uCode = templateUnitCode || String(catalog.defaultUnitCode ?? '').trim() || 'U';
    const uName = templateUnitName || String(catalog.defaultUnitName ?? '').trim() || uCode;
    return {
      ...seed,
      selectedId: String(catalog.id ?? '').trim(),
      selectedDrugName: catalog.name || '',
      selectedDrugSpec: catalog.spec || '',
      productTableName: catalog.productTableName,
      selectedDrugPrice: catalog.price ?? '',
      unitBands: catalog.unitBands,
      diagnosis: mainDiagnosisName,
      totalAmount: templateQty,
      totalAmountUnit: uCode,
      totalUnitSalesCode: uCode,
      totalUnitSalesName: uName,
      performOrgId,
      performOrgName,
      expanded: false,
    };
  }

  const catalogPatch = buildWesternProductSelectPatch(seed, catalog, {
    totalUnitDefaultKind: 'PACKAGE',
  });
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
  return inferDoseBandConvertK(item);
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

/** 组套套用至当前就诊草稿（静默保存） */
export async function applyOrderTemplateToAdviceDraft(
  params: ApplyOrderTemplateParams,
): Promise<ApplyOrderTemplateResult> {
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
  const drugItems: AdviceDrugItem[] = [];

  for (const line of normalizedLines) {
    const key = prepickedKeyFromTemplateLine(line);
    if (!key) {
      continue;
    }
    const catalog = catalogMap.get(`${key.productTableName}:${key.productDefinitionId}`);
    if (!catalog) {
      const name = String(line.productName ?? '').trim() || '未知项目';
      throw new OrderTemplateApplyError(`目录中未找到「${name}」，无法套用组套`);
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

  if (stockIssues.length > 0) {
    const names = stockIssues.map((item) => `「${item.productName}」${item.detail}`).join('；');
    throw new OrderTemplateApplyError(`以下药品库存不足：${names}`, stockIssues);
  }

  const invalidIndex = drugItems.findIndex((item) => !validateWesternEditLineItem(item));
  if (invalidIndex !== -1) {
    const badLine = normalizedLines[invalidIndex];
    const name = String(badLine?.productName ?? '').trim() || `第 ${invalidIndex + 1} 项`;
    throw new OrderTemplateApplyError(`「${name}」参数不完整，无法套用组套`);
  }

  const draftLines = buildAdviceDraftLines(drugItems, [], {
    encounterDiagnosisRows,
    existingAdviceRows: params.existingAdviceRows ?? [],
  });
  const saveRes = await httpSaveDoctorStationAdviceDraftBatch({
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
