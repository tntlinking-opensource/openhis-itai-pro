import type { AdviceDrugItem, AdviceProductOption } from './advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  buildEncounterDiagnosisBindingPatch,
  mapEncounterRowToDiagnosisItem,
  parseEncounterDiagnosisIds,
  pickMainDiagnosisDisplayName,
  pickSoleEncounterDiagnosisIds,
  filterValidEncounterDiagnosisIds,
  resolveConditionDefinitionIdFromEncounterRows,
  resolveEncounterDiagnosisIdFromEncounterRows,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';

import { defaultContractSelection, firstTerminologyCode, terminologyLabel } from './advice-format';
import {
  defaultTotalAmountUnitCode,
  doseConvertRatioSplitToDose,
  hydrateAdviceRowDoseFromBackend,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
  totalAmountSplitToSalesFactor,
  unitCodeByBandKind,
  unitNameByBandKind,
} from './medication-unit-bands';
import { preferredTotalUnitBandKindFromOrderCalcRule } from './medication-order-total-calc';

/** 目录剂量正数解析（剂量单位）。 */
function parsePositiveCatalogDose(raw: null | number | string | undefined): number | undefined {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return undefined;
  }
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/** 按预设种类取目录剂量（剂量单位）。 */
export function resolveCatalogDoseByPresetKind(
  item: Pick<AdviceDrugItem, 'catalogDose' | 'catalogMaxUnit' | 'catalogMinUnit'>,
  kind: 'default' | 'max' | 'min',
): number | undefined {
  if (kind === 'max') {
    return parsePositiveCatalogDose(item.catalogMaxUnit);
  }
  if (kind === 'min') {
    return parsePositiveCatalogDose(item.catalogMinUnit);
  }
  return parsePositiveCatalogDose(item.catalogDose);
}

/**
 * 将目录剂量（剂量单位）写入编辑行双字段，并标记当前预设。
 * 走 hydrate 以保证拆零侧 / 剂量侧与 doseBandConvertK 一致。
 */
export function buildCatalogDosePresetPatch(
  item: Pick<AdviceDrugItem, 'unitBands'>,
  kind: 'default' | 'max' | 'min',
  doseQty: number,
): Partial<AdviceDrugItem> {
  const hydrated = hydrateAdviceRowDoseFromBackend({
    dose: doseQty,
    unitBands: item.unitBands,
  });
  return {
    ...hydrated,
    dosePresetKind: kind,
  };
}

/** 编辑卡：新建行、选品回填、诊断映射、表单数值 */

export interface WesternProductSelectPatchOptions {
  /** 总量默认计量带；未传时按门诊拆分属性推导 */
  totalUnitDefaultKind?: 'PACKAGE' | 'SALES' | 'SPLIT';
}

/** 从目录选项提取医保等级（仅展示；无值时清空，避免沿用上一项目） */
export function pickCatalogChrgitmLvPatch(
  row: Pick<AdviceProductOption, 'chrgitmLv' | 'chrgitmLvText'>,
): Pick<AdviceDrugItem, 'chrgitmLv' | 'chrgitmLvText'> {
  const lvRaw = row.chrgitmLv;
  const hasLv = lvRaw !== null && lvRaw !== undefined && String(lvRaw).trim() !== '';
  const lvText = String(row.chrgitmLvText ?? '').trim();
  return {
    chrgitmLv: hasLv ? lvRaw : undefined,
    chrgitmLvText: lvText || undefined,
  };
}

/** 选品/回填：把目录库存字段落到编辑行，供药品信息行展示。 */
export function pickCatalogStockPatch(
  row: Pick<AdviceProductOption, 'availableQuantity' | 'availableQuantityText' | 'inventoryList'>,
): Pick<AdviceDrugItem, 'availableQuantity' | 'availableQuantityText' | 'inventoryList'> {
  const qtyRaw = row.availableQuantity;
  const hasQty = qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== '';
  const text = String(row.availableQuantityText ?? '').trim();
  const inventoryList = Array.isArray(row.inventoryList) ? row.inventoryList : undefined;
  return {
    availableQuantity: hasQty ? qtyRaw : undefined,
    availableQuantityText: text || undefined,
    inventoryList,
  };
}

export interface CreateAdviceDrugItemContext {
  feeNatureOptions: TerminologyOptionDto[];
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
}

export function createAdviceDrugItem(
  seed: Partial<AdviceDrugItem> | undefined,
  ctx: CreateAdviceDrugItemContext,
): AdviceDrugItem {
  const s = seed as Partial<AdviceDrugItem> | undefined;
  const freqSeed = seed?.frequency;
  const usageSeed = seed?.usage;
  const frequency =
    freqSeed !== undefined && String(freqSeed).trim() !== '' ? String(freqSeed) : '';
  const usage =
    usageSeed !== undefined && String(usageSeed).trim() !== ''
      ? String(usageSeed)
      : firstTerminologyCode(ctx.medicationRouteOptions);

  function numFromSeed(key: keyof AdviceDrugItem): number | undefined {
    if (s === undefined || !Object.prototype.hasOwnProperty.call(s, key)) {
      return undefined;
    }
    const v = s[key];
    if (v === undefined || v === null || v === '') return undefined;
    const x = Number(v);
    return Number.isFinite(x) ? x : undefined;
  }

  const contractFromSeed = String(seed?.contractId ?? '').trim();
  const feeFromSeed = String(seed?.feeNature ?? '').trim();
  let contractId = contractFromSeed;
  let feeNature = feeFromSeed;
  if (!contractId) {
    const def = defaultContractSelection(ctx.feeNatureOptions);
    contractId = def.contractId;
    feeNature = def.feeNature;
  } else if (!feeNature) {
    const label = terminologyLabel(ctx.feeNatureOptions, contractId);
    feeNature = label === '—' ? contractId : label;
  }

  return {
    orderType: String(seed?.orderType ?? '').trim(),
    selectedId: seed?.selectedId || '',
    diagnosis: seed?.diagnosis || '',
    encounterDiagnosisIds: parseEncounterDiagnosisIds(seed?.encounterDiagnosisIds),
    contractId: contractId || undefined,
    feeNature,
    skinTest: seed?.skinTest || false,
    injectFlag: s?.injectFlag === true,
    pharmacy: String(seed?.pharmacy ?? '').trim(),
    singleDose: numFromSeed('singleDose'),
    frequency,
    usage,
    medicationDays: numFromSeed('medicationDays'),
    totalAmount: numFromSeed('totalAmount'),
    serviceQuantity: numFromSeed('serviceQuantity'),
    selectedDrugName: String(s?.selectedDrugName ?? '').trim(),
    selectedDrugSpec: String(s?.selectedDrugSpec ?? '').trim(),
    selectedDrugPrice: s?.selectedDrugPrice ?? '',
    chrgitmLv: s?.chrgitmLv,
    chrgitmLvText: String(s?.chrgitmLvText ?? '').trim() || undefined,
    doseMainUnit: String(s?.doseMainUnit ?? '').trim(),
    doseSubUnit: String(s?.doseSubUnit ?? '').trim(),
    doseConvertRatio: numFromSeed('doseConvertRatio'),
    doseBandConvertK: (() => {
      const k = Number(s?.doseBandConvertK);
      return Number.isFinite(k) && k > 0 ? k : undefined;
    })(),
    totalAmountSplitToSalesK: (() => {
      const k = Number(s?.totalAmountSplitToSalesK);
      return Number.isFinite(k) && k > 0 ? k : undefined;
    })(),
    totalAmountUnit: String(s?.totalAmountUnit ?? '').trim(),
    expanded: true,
    tcmSpecialDecoction: s?.tcmSpecialDecoction ?? '',
    productTableName: (s?.productTableName ?? '').toString().trim() || undefined,
    sortNumber: (() => {
      const sn = s?.sortNumber;
      if (sn === undefined || sn === null) return undefined;
      const snNum = Number(sn);
      return Number.isFinite(snNum) && snNum > 0 ? Math.trunc(snNum) : undefined;
    })(),
    groupId: (() => {
      const g = String(s?.groupId ?? '').trim();
      return g === '' ? undefined : g;
    })(),
    performOrgId: (() => {
      const x = String(s?.performOrgId ?? '').trim();
      return x === '' ? undefined : x;
    })(),
    performOrgName: String(s?.performOrgName ?? '').trim() || undefined,
  };
}

export function buildTcmProductSelectPatch(row: AdviceProductOption): Partial<AdviceDrugItem> {
  const specMatch = row.spec.match(/(\d+(\.\d+)?)g/i);
  const doseFromSpec = specMatch ? Number(specMatch[1]) : Number.NaN;
  return {
    singleDose: Number.isFinite(doseFromSpec) ? doseFromSpec : 0,
    doseSubUnit: specMatch ? 'g' : '',
    totalAmount: 0,
    tcmSpecialDecoction: '',
  };
}

/**
 * 耗材选品回填：总量单位默认拆零（SPLIT），不取目录包装默认单位；
 * 无拆零带时再回落售卖/包装/目录 unitCode。
 */
export function buildDeviceProductSelectPatch(row: AdviceProductOption): Partial<AdviceDrugItem> {
  const patch: Partial<AdviceDrugItem> = {};
  const bands = row.unitBands;
  patch.unitBands = bands;
  const catalogRaw = row.catalogUnitPrice ?? row.price;
  if (catalogRaw !== undefined && catalogRaw !== null && String(catalogRaw).trim() !== '') {
    const cp = Number(catalogRaw);
    if (Number.isFinite(cp)) patch.catalogUnitPrice = cp;
  }
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const salesCode = unitCodeByBandKind(bands, 'SALES');
  const salesName = unitNameByBandKind(bands, 'SALES');
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE');
  const packageName = unitNameByBandKind(bands, 'PACKAGE');
  patch.totalUnitSplitCode = splitCode;
  patch.totalUnitSplitName = splitName;
  patch.totalUnitSalesCode = salesCode;
  patch.totalUnitSalesName = salesName;
  patch.totalUnitPackageCode = packageCode;
  patch.totalUnitPackageName = packageName;
  const totalSk = totalAmountSplitToSalesFactor(bands);
  patch.totalAmountSplitToSalesK =
    totalSk !== undefined && Number.isFinite(totalSk) && totalSk > 0 ? totalSk : undefined;
  const fallbackCode = String(row.defaultUnitCode ?? '').trim();
  const fallbackName = String(row.defaultUnitName ?? '').trim() || fallbackCode;
  const totalUnit = defaultTotalAmountUnitCode(bands, 'SPLIT') || fallbackCode;
  patch.totalAmountUnit = totalUnit;
  // 无计量带时把目录默认单位塞进展示字段，供单位下拉单选项回显
  if (!splitCode && !salesCode && !packageCode && fallbackCode) {
    patch.totalUnitSalesCode = fallbackCode;
    patch.totalUnitSalesName = fallbackName;
  }
  const linePrice = resolveAdviceLineUnitPrice({
    bands,
    catalogUnitPrice: patch.catalogUnitPrice,
    totalAmountUnit: totalUnit,
  });
  if (linePrice !== undefined) {
    patch.selectedDrugPrice = linePrice;
  } else if (row.price !== undefined && row.price !== null && String(row.price).trim() !== '') {
    patch.selectedDrugPrice = row.price;
  }
  Object.assign(patch, pickCatalogChrgitmLvPatch(row));
  Object.assign(patch, pickCatalogStockPatch(row));
  return patch;
}

export function buildWesternProductSelectPatch(
  prevRow: AdviceDrugItem,
  row: AdviceProductOption,
  opts?: WesternProductSelectPatchOptions,
): Partial<AdviceDrugItem> {
  const patch: Partial<AdviceDrugItem> = {};
  const mc = row.methodCode?.trim();
  const rc = row.rateCode?.trim();
  if (mc) patch.usage = mc;
  if (rc) patch.frequency = rc;
  // 选品时同步目录注射/输液标志，供追加/成组校验
  patch.injectFlag = row.injectFlag === true;
  const bands = row.unitBands;
  patch.unitBands = bands;
  const catalogRaw = row.catalogUnitPrice ?? row.price;
  if (catalogRaw !== undefined && catalogRaw !== null && String(catalogRaw).trim() !== '') {
    const cp = Number(catalogRaw);
    if (Number.isFinite(cp)) patch.catalogUnitPrice = cp;
  }
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const doseName = unitNameByBandKind(bands, 'DOSE');
  if (splitName) patch.doseMainUnit = splitName;
  if (doseName) patch.doseSubUnit = doseName;
  const ratioK = doseConvertRatioSplitToDose(bands);
  const curSdRaw = Number(prevRow.singleDose);
  const curDqRaw = Number(prevRow.doseConvertRatio);
  const curSd = Number.isFinite(curSdRaw) && curSdRaw > 0 ? curSdRaw : Number.NaN;
  const curDq = Number.isFinite(curDqRaw) && curDqRaw > 0 ? curDqRaw : Number.NaN;
  const catalogDose = parsePositiveCatalogDose(row.dose);
  const catalogMinUnit = parsePositiveCatalogDose(row.minUnit);
  const catalogMaxUnit = parsePositiveCatalogDose(row.maxUnit);
  patch.catalogDose = catalogDose;
  patch.catalogMinUnit = catalogMinUnit;
  patch.catalogMaxUnit = catalogMaxUnit;
  const prevSelectedId = String(prevRow.selectedId ?? '').trim();
  const nextSelectedId = String(row.id ?? '').trim();
  const productChanged = !prevSelectedId || prevSelectedId !== nextSelectedId;
  const hasExistingDose =
    (Number.isFinite(curSd) && curSd > 0) || (Number.isFinite(curDq) && curDq > 0);
  // 换品或无剂量时：优先带出目录默认单次剂量（剂量单位 → 双字段换算）
  if (catalogDose !== undefined && (productChanged || !hasExistingDose)) {
    Object.assign(
      patch,
      hydrateAdviceRowDoseFromBackend({
        dose: catalogDose,
        unitBands: bands,
      }),
    );
    patch.dosePresetKind = 'default';
  } else if (ratioK !== undefined && Number.isFinite(ratioK) && ratioK > 0) {
    patch.doseBandConvertK = ratioK;
    if (Number.isFinite(curSd) && curSd > 0) {
      patch.doseConvertRatio = roundMedicationQty(curSd * ratioK);
    } else if (Number.isFinite(curDq) && curDq > 0) {
      patch.singleDose = roundMedicationQty(curDq / ratioK);
      patch.doseConvertRatio = roundMedicationQty(curDq);
    }
    if (productChanged) {
      patch.dosePresetKind = undefined;
    }
  } else {
    patch.doseBandConvertK = undefined;
    if (productChanged) {
      patch.dosePresetKind = undefined;
    }
  }
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const salesCode = unitCodeByBandKind(bands, 'SALES');
  const salesName = unitNameByBandKind(bands, 'SALES');
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE');
  const packageName = unitNameByBandKind(bands, 'PACKAGE');
  patch.totalUnitSplitCode = splitCode;
  patch.totalUnitSplitName = splitName;
  patch.totalUnitSalesCode = salesCode;
  patch.totalUnitSalesName = salesName;
  patch.totalUnitPackageCode = packageCode;
  patch.totalUnitPackageName = packageName;
  const totalSk = totalAmountSplitToSalesFactor(bands);
  patch.totalAmountSplitToSalesK =
    totalSk !== undefined && Number.isFinite(totalSk) && totalSk > 0 ? totalSk : undefined;
  const partAttr = String(row.partAttributeEnum ?? '').trim();
  if (partAttr) patch.partAttributeEnum = partAttr;
  const defaultKind =
    opts?.totalUnitDefaultKind ?? preferredTotalUnitBandKindFromOrderCalcRule(partAttr);
  const totalUnit = defaultTotalAmountUnitCode(bands, defaultKind);
  patch.totalAmountUnit = totalUnit;
  const linePrice = resolveAdviceLineUnitPrice({
    bands,
    catalogUnitPrice: patch.catalogUnitPrice,
    totalAmountUnit: totalUnit,
  });
  if (linePrice !== undefined) {
    patch.selectedDrugPrice = linePrice;
  }
  Object.assign(patch, pickCatalogChrgitmLvPatch(row));
  Object.assign(patch, pickCatalogStockPatch(row));
  return patch;
}

export function parseOptionalNumberInput(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const v = Number(value);
  return Number.isFinite(v) ? v : undefined;
}

export function inferDoseBandConvertK(row: AdviceDrugItem): AdviceDrugItem {
  if (
    row.doseBandConvertK !== undefined &&
    Number.isFinite(row.doseBandConvertK) &&
    row.doseBandConvertK > 0
  ) {
    return row;
  }
  const sd = Number(row.singleDose);
  const dq = Number(row.doseConvertRatio);
  if (Number.isFinite(sd) && sd > 0 && Number.isFinite(dq) && dq >= 0) {
    const k = dq / sd;
    if (Number.isFinite(k) && k > 0) {
      return { ...row, doseBandConvertK: k };
    }
  }
  return row;
}

export {
  filterValidEncounterDiagnosisIds,
  mapEncounterRowToDiagnosisItem,
  pickMainDiagnosisDisplayName,
  pickSoleEncounterDiagnosisIds,
  resolveConditionDefinitionIdFromEncounterRows,
  resolveEncounterDiagnosisIdFromEncounterRows,
};

/**
 * 医嘱行诊断绑定优先级：
 * 1) 行上已有 ids / 展示名可匹配
 * 2) 会话内上次选择且仍有效
 * 3) 就诊诊断恰好 1 条 → 默认该条
 * 4) 多诊断且无上次选择 → 空（不默认主诊）
 */
export function resolveDefaultDrugRowDiagnosisBinding(
  currentIds: string[] | undefined,
  currentName: string | undefined,
  encounterRows: EncounterDiagnosisRowDto[],
  lastSelectedIds?: string[] | undefined,
): { diagnosis: string; encounterDiagnosisIds: string[] } {
  const existing = filterValidEncounterDiagnosisIds(currentIds, encounterRows);
  if (existing.length > 0) {
    return buildEncounterDiagnosisBindingPatch(existing, encounterRows);
  }
  const name = String(currentName ?? '').trim();
  if (name) {
    const fromName = resolveEncounterDiagnosisIdFromEncounterRows(name, encounterRows);
    if (fromName) {
      return buildEncounterDiagnosisBindingPatch([fromName], encounterRows);
    }
    return { encounterDiagnosisIds: [], diagnosis: name };
  }
  const lastValid = filterValidEncounterDiagnosisIds(lastSelectedIds, encounterRows);
  if (lastValid.length > 0) {
    return buildEncounterDiagnosisBindingPatch(lastValid, encounterRows);
  }
  const sole = pickSoleEncounterDiagnosisIds(encounterRows);
  if (sole.length > 0) {
    return buildEncounterDiagnosisBindingPatch(sole, encounterRows);
  }
  return { encounterDiagnosisIds: [], diagnosis: '' };
}

/** 药品行诊断展示名：与绑定规则一致。 */
export function resolveDefaultDrugRowDiagnosisName(
  current: string | undefined,
  encounterRows: EncounterDiagnosisRowDto[],
  lastSelectedIds?: string[] | undefined,
): string {
  return resolveDefaultDrugRowDiagnosisBinding(undefined, current, encounterRows, lastSelectedIds)
    .diagnosis;
}

/** 组包药品草稿：按行诊断名匹配；无名称时仅唯一诊断可回落（不默认主诊）。 */
export function resolveMedicationEncounterDiagnosisId(
  diagnosisName: string,
  encounterRows: EncounterDiagnosisRowDto[] = [],
): string | undefined {
  const name = String(diagnosisName ?? '').trim();
  if (name) {
    const fromEncounter = resolveEncounterDiagnosisIdFromEncounterRows(name, encounterRows);
    if (fromEncounter) return fromEncounter;
  }
  const sole = pickSoleEncounterDiagnosisIds(encounterRows);
  return sole[0];
}

/** 从编辑行解析合同主键（落库 contract_id）。 */
export function resolveMedicationContractId(
  item: Pick<AdviceDrugItem, 'contractId'>,
): string | undefined {
  const explicit = String(item.contractId ?? '').trim();
  return explicit || undefined;
}
