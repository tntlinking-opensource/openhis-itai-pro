/**
 * 西药单次剂量展示：优先后端 dosageDisplay；否则最小单位为主，括号内附计量单位。
 * 计量单位与最小单位一致时不拼括号（如 `1片`；不同则如 `1支(2ml)`）。
 * 列表/收起摘要在剂量段后保留「/次」（如 `1支(2ml)/次，共1支。`）。
 */

export interface MedicationDoseDisplayParts {
  /** 后端已拼好的单次剂量展示串（优先） */
  dosageDisplay?: null | string;
  /** 计量侧数量 */
  dose?: null | number | string;
  doseUnitCode?: null | string;
  doseUnitName?: null | string;
  /** 最小/拆零侧数量 */
  splitDose?: null | number | string;
  splitDoseUnitCode?: null | string;
  splitDoseUnitName?: null | string;
}

export interface MedicationDoseSummaryOptions extends MedicationDoseDisplayParts {
  /** 总量（请求数量） */
  quantity?: null | number | string;
  unitCode?: null | string;
  unitName?: null | string;
  /** 是否拼接「共X单位」；默认 true */
  includeTotal?: boolean;
  /** 总量单位展示名解析（缺省用 unitName/unitCode） */
  resolveTotalUnitLabel?: (unitName?: null | string, unitCode?: null | string) => string;
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 计量单位与最小单位是否同一：优先比 code，缺则比展示名 */
export function isSameMedicationDoseUnit(
  doseUnitCode?: null | string,
  doseUnitName?: null | string,
  splitUnitCode?: null | string,
  splitUnitName?: null | string,
): boolean {
  const doseCode = trimText(doseUnitCode);
  const splitCode = trimText(splitUnitCode);
  if (doseCode && splitCode) {
    return doseCode === splitCode;
  }
  const doseName = trimText(doseUnitName);
  const splitName = trimText(splitUnitName);
  if (doseName && splitName) {
    return doseName === splitName;
  }
  return false;
}

/**
 * 单次剂量主文案：优先 dosageDisplay；
 * 否则 `最小单位量+单位`，不同单位时附 `(计量量+计量单位)`。
 */
export function formatMedicationDoseDisplay(parts: MedicationDoseDisplayParts): string {
  const fromBackend = trimText(parts.dosageDisplay);
  if (fromBackend) {
    return fromBackend;
  }

  const doseQty = trimText(parts.dose);
  const doseUnit = trimText(parts.doseUnitName) || trimText(parts.doseUnitCode);
  const dosePart = doseQty ? (doseUnit ? `${doseQty}${doseUnit}` : doseQty) : '';

  const splitQty = trimText(parts.splitDose);
  const splitUnit = trimText(parts.splitDoseUnitName) || trimText(parts.splitDoseUnitCode);
  const splitPart = splitQty ? (splitUnit ? `${splitQty}${splitUnit}` : splitQty) : '';

  // 无拆零侧：仅回计量段
  if (!splitPart) {
    return dosePart;
  }
  // 单位相同或无计量侧：只回最小单位段
  if (
    !dosePart ||
    isSameMedicationDoseUnit(
      parts.doseUnitCode,
      parts.doseUnitName || doseUnit,
      parts.splitDoseUnitCode,
      parts.splitDoseUnitName || splitUnit,
    )
  ) {
    return splitPart;
  }
  return `${splitPart}(${dosePart})`;
}

/** 剂量段补上「/次」（后端已带则不重复） */
function withPerDoseSuffix(doseCore: string): string {
  const core = trimText(doseCore);
  if (!core) {
    return '';
  }
  if (core.endsWith('/次')) {
    return core;
  }
  return `${core}/次`;
}

/**
 * 列表剂量摘要行：单次剂量主文案+/次 + 可选「共总量」；末尾句号与历史口径一致。
 */
export function formatMedicationDoseSummaryLine(options: MedicationDoseSummaryOptions): string {
  const left = withPerDoseSuffix(formatMedicationDoseDisplay(options));
  const includeTotal = options.includeTotal !== false;
  let right = '';
  if (includeTotal) {
    const qty = trimText(options.quantity);
    if (qty) {
      const unit = options.resolveTotalUnitLabel
        ? trimText(options.resolveTotalUnitLabel(options.unitName, options.unitCode))
        : trimText(options.unitName) || trimText(options.unitCode);
      right = unit ? `共${qty}${unit}` : `共${qty}`;
    }
  }
  if (left && right) {
    return `${left}，${right}。`;
  }
  if (left) {
    return `${left}。`;
  }
  if (right) {
    return `${right}。`;
  }
  return '';
}

/**
 * 编辑卡收起态：本地双单位 → 最小单位为主、括号内为计量（含 /次）。
 */
export function formatWesternEditDoseSegment(item: {
  doseConvertRatio?: null | number;
  doseMainUnit?: null | string;
  doseSubUnit?: null | string;
  singleDose?: null | number;
}): string {
  const doseQtyRaw = item.doseConvertRatio;
  const doseQty =
    doseQtyRaw === undefined || doseQtyRaw === null || !Number.isFinite(Number(doseQtyRaw))
      ? ''
      : String(doseQtyRaw);
  const doseUnit = trimText(item.doseSubUnit);
  const splitQtyRaw = item.singleDose;
  const splitQty =
    splitQtyRaw === undefined || splitQtyRaw === null || !Number.isFinite(Number(splitQtyRaw))
      ? ''
      : String(splitQtyRaw);
  const splitUnit = trimText(item.doseMainUnit);

  if (splitQty || doseQty) {
    return withPerDoseSuffix(
      formatMedicationDoseDisplay({
        dose: doseQty || undefined,
        doseUnitName: doseUnit || undefined,
        splitDose: splitQty || undefined,
        splitDoseUnitName: splitUnit || undefined,
      }),
    );
  }
  return '—';
}
