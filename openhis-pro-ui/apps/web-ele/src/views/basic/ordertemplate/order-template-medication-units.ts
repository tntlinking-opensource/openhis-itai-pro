import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';
import type { ApiOptionItem } from '#/types/api-response';

import { ORDER_TEMPLATE_LINE_KIND_MEDICATION } from '#/api/basic/order-template';
import { getMedicationCatalogApi } from '#/api/core/system/catalog';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  convertTotalAmountWhenUnitChanges,
  defaultTotalAmountUnitCode,
  doseConvertRatioSplitToDose,
  hydrateAdviceRowDoseFromBackend,
  normalizeCatalogUnitBands,
  roundMedicationDoseDisplayQty,
  roundMedicationQty,
  roundMedicationSplitQty,
  totalAmountSplitToSalesFactor,
  totalUnitSelectOptions,
  unitCodeByBandKind,
  unitNameByBandKind,
} from '#/views/outpatient/doctor-station/advice/services/medication-unit-bands';

/** 药品行草稿扩展字段（与 DraftLine 对齐） */
export interface MedicationLineDraftSlice {
  _unitBands?: DoctorStationMedicationUnitBandDto[];
  _totalUnitSplitCode?: string;
  _totalUnitSplitName?: string;
  _totalUnitSalesCode?: string;
  _totalUnitSalesName?: string;
  _totalUnitPackageCode?: string;
  _totalUnitPackageName?: string;
  _totalAmountSplitToSalesK?: number;
  /** 单次剂量拆零侧（主单位，如片） */
  _singleDose?: number;
  /** 单次剂量剂量侧（如 mg） */
  _doseConvertRatio?: number;
  _doseMainUnit?: string;
  _doseSubUnit?: string;
  _doseBandConvertK?: number;
  routeCode?: string;
  defaultUnitCode?: string;
  defaultQuantity?: number;
  defaultDays?: number;
  dosageText?: string;
  lineKindEnum?: string;
  medicationDefinitionId?: string;
}

/** 药品目录行 → 多计量带（优先接口 unitBands，回落本地拼装） */
export function buildUnitBandsFromMedicationCatalogItem(
  item: Record<string, unknown>,
): DoctorStationMedicationUnitBandDto[] {
  const fromApi = normalizeCatalogUnitBands(
    item.unitBands as DoctorStationMedicationUnitBandDto[] | undefined,
    item.unitBandsJson as string | undefined,
  );
  if (fromApi.length > 0) {
    return fromApi;
  }
  const bands: DoctorStationMedicationUnitBandDto[] = [];
  const add = (bandKind: string, unitCode: unknown, ratio: unknown) => {
    const code = String(unitCode ?? '').trim();
    if (!code) {
      return;
    }
    const r = Number(ratio);
    bands.push({
      bandKind,
      unitCode: code,
      ratioToSplit: Number.isFinite(r) && r > 0 ? r : 1,
    });
  };
  add('SPLIT', item.splitUnitCode, 1);
  add('DOSE', item.doseUnitCode, item.doseUnitRatio);
  add('PACKAGE', item.packageUnitCode, item.packageUnitRatio);
  add('SALES', item.packageUnitCode, item.packageUnitRatio);
  add('STOCK', item.stockUnitCode, item.stockUnitRatio);
  return bands;
}

function resolveUnitLabel(unitCode: string | undefined, unitCodeOptions: ApiOptionItem[]): string {
  const code = String(unitCode ?? '').trim();
  if (!code) {
    return '';
  }
  return unitCodeOptions.find((item) => item.value === code)?.label ?? code;
}

function enrichBandUnitNames(
  bands: DoctorStationMedicationUnitBandDto[],
  unitCodeOptions: ApiOptionItem[],
): DoctorStationMedicationUnitBandDto[] {
  return bands.map((band) => ({
    ...band,
    unitName: resolveUnitLabel(String(band.unitCode ?? ''), unitCodeOptions) || band.unitName,
  }));
}

/** 解析 dosageText 中的数值与单位后缀（如「100mg」「2片」） */
export function parseDosageTextParts(dosageText: string | undefined): {
  doseQty?: number;
  unitText?: string;
} {
  if (!dosageText?.trim()) {
    return {};
  }
  const match = dosageText.trim().match(/^(\d+(?:\.\d+)?)(.*)$/);
  if (!match) {
    return {};
  }
  const doseQty = Number(match[1]);
  const unitText = String(match[2] ?? '').trim();
  return {
    doseQty: Number.isFinite(doseQty) && doseQty > 0 ? doseQty : undefined,
    unitText: unitText || undefined,
  };
}

/** 写入行内单次剂量双字段与换算系数（对齐门诊 hydrateAdviceRowDoseFromBackend） */
export function applyLineDoseFieldsFromBands(
  line: MedicationLineDraftSlice,
  bands: DoctorStationMedicationUnitBandDto[],
  options?: { doseQty?: number; doseUnitName?: string; resetDefault?: boolean },
) {
  const k = doseConvertRatioSplitToDose(bands);
  line._doseMainUnit = unitNameByBandKind(bands, 'SPLIT');
  line._doseSubUnit = unitNameByBandKind(bands, 'DOSE');
  line._doseBandConvertK = k !== undefined && Number.isFinite(k) && k > 0 ? k : undefined;

  const parsed = parseDosageTextParts(line.dosageText);
  const doseQty = options?.doseQty ?? parsed.doseQty;
  const doseUnitName = options?.doseUnitName ?? parsed.unitText;

  if (doseQty !== undefined && !options?.resetDefault) {
    const hydrated = hydrateAdviceRowDoseFromBackend({
      dose: doseQty,
      doseUnitName,
      unitBands: bands,
    });
    line._singleDose = hydrated.singleDose;
    line._doseConvertRatio = hydrated.doseConvertRatio;
    line._doseMainUnit = hydrated.doseMainUnit || line._doseMainUnit;
    line._doseSubUnit = hydrated.doseSubUnit || line._doseSubUnit;
    line._doseBandConvertK = hydrated.doseBandConvertK ?? line._doseBandConvertK;
    return;
  }

  line._singleDose = 1;
  if (line._doseBandConvertK !== undefined) {
    line._doseConvertRatio = roundMedicationQty(1 * line._doseBandConvertK);
  } else {
    line._doseConvertRatio = 1;
  }
}

/** 拆零侧变更 → 同步剂量侧（剂量存 6 位，界面另按 3 位展示） */
export function applySplitDoseToDoseSide(line: MedicationLineDraftSlice, singleDose: number) {
  const k = line._doseBandConvertK ?? doseConvertRatioSplitToDose(line._unitBands);
  if (k === undefined || !Number.isFinite(k) || k <= 0) {
    return;
  }
  const currentDose = Number(line._doseConvertRatio);
  // 剂量侧同步过来的片数不要反推剂量，避免两位片数把 35.625 抹成 35.63
  if (Number.isFinite(currentDose) && currentDose > 0) {
    const splitFromDose = roundMedicationSplitQty(currentDose / k);
    if (roundMedicationSplitQty(singleDose) === splitFromDose) {
      return;
    }
  }
  line._doseConvertRatio = roundMedicationQty(singleDose * k);
}

/** 剂量侧变更 → 同步拆零侧（最小单位保留 2 位） */
export function applyDoseQtyToSplitSide(line: MedicationLineDraftSlice, doseQty: number) {
  const k = line._doseBandConvertK ?? doseConvertRatioSplitToDose(line._unitBands);
  if (k === undefined || !Number.isFinite(k) || k <= 0) {
    return;
  }
  line._singleDose = roundMedicationSplitQty(doseQty / k);
}

/** 清空行内单次剂量字段 */
export function clearLineDoseFields(line: MedicationLineDraftSlice) {
  line._singleDose = undefined;
  line._doseConvertRatio = undefined;
  line._doseMainUnit = undefined;
  line._doseSubUnit = undefined;
  line._doseBandConvertK = undefined;
}

/** 接口行 dosageText 回填草稿单次剂量（无 unitBands 时仅解析数值） */
export function hydrateDraftLineDoseFromDosageText(line: MedicationLineDraftSlice) {
  const parsed = parseDosageTextParts(line.dosageText);
  if (parsed.doseQty === undefined) {
    return;
  }
  if (line._unitBands?.length) {
    applyLineDoseFieldsFromBands(line, line._unitBands, {
      doseQty: parsed.doseQty,
      doseUnitName: parsed.unitText,
    });
    return;
  }
  line._doseConvertRatio = parsed.doseQty;
  if (parsed.unitText) {
    line._doseSubUnit = parsed.unitText;
  }
}

/** 选中药品后回填行内单位/途径/默认总量单位（参照门诊 buildWesternProductSelectPatch） */
export function applyMedicationCatalogSelectionToLine(
  line: MedicationLineDraftSlice,
  item: Record<string, unknown>,
  unitCodeOptions: ApiOptionItem[],
  options?: { hydrateFromDosageText?: boolean },
) {
  const bands = enrichBandUnitNames(buildUnitBandsFromMedicationCatalogItem(item), unitCodeOptions);
  line._unitBands = bands;

  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const salesCode = unitCodeByBandKind(bands, 'SALES') || unitCodeByBandKind(bands, 'PACKAGE');
  const salesName = unitNameByBandKind(bands, 'SALES') || unitNameByBandKind(bands, 'PACKAGE');
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE');
  const packageName = unitNameByBandKind(bands, 'PACKAGE');

  line._totalUnitSplitCode = splitCode;
  line._totalUnitSplitName = splitName;
  line._totalUnitSalesCode = salesCode;
  line._totalUnitSalesName = salesName;
  line._totalUnitPackageCode = packageCode;
  line._totalUnitPackageName = packageName;
  line._totalAmountSplitToSalesK = totalAmountSplitToSalesFactor(bands);

  const route = String(item.methodCode ?? '').trim();
  if (route) {
    line.routeCode = route;
  }

  const totalUnit = defaultTotalAmountUnitCode(bands, 'PACKAGE');
  if (totalUnit) {
    line.defaultUnitCode = totalUnit;
  }

  applyLineDoseFieldsFromBands(line, bands, {
    resetDefault: !options?.hydrateFromDosageText,
  });

  if (line.defaultQuantity === undefined) {
    line.defaultQuantity = 1;
  }
  if (line.defaultDays === undefined) {
    line.defaultDays = 1;
  }
}

/** 加载已有组套时，按药品 id 补齐 unitBands 并回填单次剂量换算 */
export async function enrichMedicationLinesUnitBands(
  lines: MedicationLineDraftSlice[],
  unitCodeOptions: ApiOptionItem[],
): Promise<void> {
  const medicationLines = lines.filter(
    (line) =>
      line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION &&
      preserveApiIdForRequest(line.medicationDefinitionId),
  );
  await Promise.all(
    medicationLines.map(async (line) => {
      const id = preserveApiIdForRequest(line.medicationDefinitionId);
      if (!id) {
        return;
      }
      try {
        const res = await getMedicationCatalogApi(id);
        const item = res?.data as Record<string, unknown> | undefined;
        if (!item) {
          return;
        }
        applyMedicationCatalogSelectionToLine(line, item, unitCodeOptions, {
          hydrateFromDosageText: true,
        });
        const priceRaw = Number(item.retailPrice);
        if (Number.isFinite(priceRaw)) {
          (line as MedicationLineDraftSlice & { _retailPrice?: number })._retailPrice = priceRaw;
        }
      } catch {
        hydrateDraftLineDoseFromDosageText(line);
      }
    }),
  );
}

/** 总量单位下拉（PACKAGE / SALES / SPLIT） */
export function getLineTotalUnitOptions(line: MedicationLineDraftSlice) {
  const options = totalUnitSelectOptions({
    totalUnitPackageCode: line._totalUnitPackageCode,
    totalUnitPackageName: line._totalUnitPackageName,
    totalUnitSalesCode: line._totalUnitSalesCode,
    totalUnitSalesName: line._totalUnitSalesName,
    totalUnitSplitCode: line._totalUnitSplitCode,
    totalUnitSplitName: line._totalUnitSplitName,
  });
  if (options.length > 0) {
    return options;
  }
  const code = String(line.defaultUnitCode ?? '').trim();
  if (!code) {
    return [];
  }
  return [{ value: code, label: code }];
}

/** 切换总量单位时换算数量 */
export function handleLineTotalUnitChange(line: MedicationLineDraftSlice, newUnit: unknown) {
  const nextUnit = String(newUnit ?? '').trim();
  const oldUnit = String(line.defaultUnitCode ?? '').trim();
  if (!nextUnit || nextUnit === oldUnit) {
    line.defaultUnitCode = nextUnit;
    return;
  }
  const converted = convertTotalAmountWhenUnitChanges({
    oldUnit,
    newUnit: nextUnit,
    bands: line._unitBands,
    splitCode: line._totalUnitSplitCode,
    salesCode: line._totalUnitSalesCode,
    splitToSalesK: line._totalAmountSplitToSalesK,
    currentAmount: Number(line.defaultQuantity ?? 0),
  });
  if (converted !== undefined) {
    line.defaultQuantity = converted;
  }
  line.defaultUnitCode = nextUnit;
}

/** 从目录项解析划价行默认单位（优先就诊默认单位） */
export function resolvePricingDefaultUnitCodeFromCatalogItem(
  item: Record<string, unknown>,
): string {
  for (const key of [
    'defEncounterUnitCode',
    'packageUnitCode',
    'splitUnitCode',
    'unitCode',
  ] as const) {
    const code = String(item[key] ?? '').trim();
    if (code) {
      return code;
    }
  }
  return '';
}

/** 选中诊疗/耗材目录后回填划价行默认单位 */
export function applyPricingCatalogSelectionToLine(
  line: Pick<MedicationLineDraftSlice, 'defaultUnitCode'>,
  item: Record<string, unknown>,
) {
  const unitCode = resolvePricingDefaultUnitCodeFromCatalogItem(item);
  if (unitCode) {
    line.defaultUnitCode = unitCode;
  }
}

/** 选中诊疗目录后回填数量/单位 */
export function applyServiceCatalogSelectionToLine(
  line: Pick<MedicationLineDraftSlice, 'defaultDays' | 'defaultQuantity' | 'defaultUnitCode'>,
  item: Record<string, unknown>,
) {
  applyPricingCatalogSelectionToLine(line, item);
  if (line.defaultQuantity === undefined) {
    line.defaultQuantity = 1;
  }
  if (line.defaultDays === undefined) {
    line.defaultDays = 1;
  }
}

/** 选中耗材目录后回填数量/单位（对齐门诊医生站耗材行） */
export function applyDeviceCatalogSelectionToLine(
  line: Pick<MedicationLineDraftSlice, 'defaultQuantity' | 'defaultUnitCode'>,
  item: Record<string, unknown>,
) {
  applyPricingCatalogSelectionToLine(line, item);
  if (line.defaultQuantity === undefined) {
    line.defaultQuantity = 1;
  }
}

/** 划价行单位下拉（单选项，只读展示） */
export function getLinePricingUnitOptions(
  line: Pick<MedicationLineDraftSlice, 'defaultUnitCode'>,
  unitCodeOptions: ApiOptionItem[],
) {
  const code = String(line.defaultUnitCode ?? '').trim();
  if (!code) {
    return [];
  }
  const label = resolveUnitLabel(code, unitCodeOptions) || code;
  return [{ value: code, label }];
}

/** 目录下拉列表中的单位展示文案 */
export function resolvePricingCatalogUnitDisplayText(item: Record<string, unknown>): string {
  return String(
    item.defEncounterUnitCode_dictText ??
      item.packageUnitCode_dictText ??
      item.unitCode_dictText ??
      '',
  ).trim();
}

/** 保存前组装 dosageText（优先剂量侧落库 6 位，回落拆零侧 2 位） */
export function buildDosageText(line: MedicationLineDraftSlice): string | undefined {
  const doseQtyRaw = Number(line._doseConvertRatio ?? Number.NaN);
  if (Number.isFinite(doseQtyRaw) && doseQtyRaw > 0) {
    const doseQty = roundMedicationQty(doseQtyRaw);
    const subUnit = String(line._doseSubUnit ?? unitNameByBandKind(line._unitBands, 'DOSE')).trim();
    return subUnit ? `${doseQty}${subUnit}` : String(doseQty);
  }

  const singleRaw = Number(line._singleDose ?? Number.NaN);
  if (Number.isFinite(singleRaw) && singleRaw > 0) {
    const single = roundMedicationSplitQty(singleRaw);
    const mainUnit = String(
      line._doseMainUnit ?? unitNameByBandKind(line._unitBands, 'SPLIT'),
    ).trim();
    return mainUnit ? `${single}${mainUnit}` : String(single);
  }

  return line.dosageText;
}

/** 剂量单位输入框/文案展示：保留 3 位小数。 */
export function displayMedicationDoseQty(raw: number | null | undefined): number | undefined {
  if (raw === null || raw === undefined) return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n)) return undefined;
  return roundMedicationDoseDisplayQty(n);
}

/** 单次剂量展示文案（拆零 = 剂量；剂量侧固定 3 位） */
export function formatLineDoseDisplayText(line: MedicationLineDraftSlice): string {
  const mainUnit =
    String(line._doseMainUnit ?? unitNameByBandKind(line._unitBands, 'SPLIT')).trim() || '—';
  const subUnit =
    String(line._doseSubUnit ?? unitNameByBandKind(line._unitBands, 'DOSE')).trim() || '—';
  const single = line._singleDose;
  const doseQty = line._doseConvertRatio;
  const k = line._doseBandConvertK ?? doseConvertRatioSplitToDose(line._unitBands);
  const doseShown = displayMedicationDoseQty(doseQty);
  const doseText =
    doseShown === undefined ? undefined : roundMedicationDoseDisplayQty(doseShown).toFixed(3);
  const singleText =
    single === undefined || !Number.isFinite(Number(single))
      ? undefined
      : String(Number(roundMedicationSplitQty(Number(single)).toFixed(2)));

  if (
    singleText !== undefined &&
    doseText !== undefined &&
    k !== undefined &&
    k > 0 &&
    mainUnit !== '—' &&
    subUnit !== '—'
  ) {
    return `${singleText}${mainUnit} = ${doseText}${subUnit}`;
  }
  if (doseText !== undefined && subUnit !== '—') {
    return `${doseText}${subUnit}`;
  }
  if (singleText !== undefined && mainUnit !== '—') {
    return `${singleText}${mainUnit}`;
  }
  const fallback = String(line.dosageText ?? '').trim();
  return fallback || '—';
}
