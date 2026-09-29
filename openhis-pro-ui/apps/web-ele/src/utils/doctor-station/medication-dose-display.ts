/**
 * 西药单次剂量展示：优先用结构化数量按开立口径重拼；
 * 缺数量时再回落后端 dosageDisplay。
 * 最小单位为主，括号内附计量单位；单位一致时不拼括号。
 * 小数位：计量（剂量单位）固定 3 位；最小单位 2 位。
 * 列表/收起摘要在剂量段后保留「/次」。
 * 长期药剂量位见 formatLongTermMedicationDoseLine（单次 + 日总量）。
 * 首日用量见 formatFirstDoseDisplay / appendFirstDoseToDoseLine（BR-IP-ORD-030）。
 */

import {
  roundMedicationDoseDisplayQty,
  roundMedicationSplitQty,
} from '#/utils/doctor-station/medication-unit-band-query';

export interface MedicationDoseDisplayParts {
  /** 后端已拼好的单次剂量展示串（仅缺结构化数量时回落） */
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

/** 首日/首次用量展示入参（仅长期有值时拼「首日…」） */
export interface MedicationFirstDoseDisplayParts {
  /** 首日用量数值 */
  firstDose?: null | number | string;
  /** 首日用量单位编码（无展示名时回落） */
  firstDoseUnitCode?: null | string;
  /** 首日用量单位展示名 */
  firstDoseUnitName?: null | string;
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
  /** 首日用量（可选；有值时追加「，首日…」） */
  firstDose?: null | number | string;
  firstDoseUnitCode?: null | string;
  firstDoseUnitName?: null | string;
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 解析正数数量；非法则 undefined。 */
function parsePositiveQty(value: unknown): number | undefined {
  if (value === null || value === undefined || trimText(value) === '') {
    return undefined;
  }
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) {
    return undefined;
  }
  return n;
}

/** 计量（剂量单位）展示串：固定 3 位小数。 */
export function formatDoseQtyDisplayText(value: unknown): string {
  const n = parsePositiveQty(value);
  if (n === undefined) {
    return '';
  }
  return roundMedicationDoseDisplayQty(n).toFixed(3);
}

/** 最小单位展示串：2 位小数并去尾零。 */
export function formatSplitQtyDisplayText(value: unknown): string {
  const n = parsePositiveQty(value);
  if (n === undefined) {
    return '';
  }
  const rounded = roundMedicationSplitQty(n);
  return String(Number(rounded.toFixed(2)));
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
 * 单次剂量主文案：有结构化数量时按 2/3 位重拼；
 * 否则回落 dosageDisplay。
 */
export function formatMedicationDoseDisplay(parts: MedicationDoseDisplayParts): string {
  const doseQtyText = formatDoseQtyDisplayText(parts.dose);
  const doseUnit = trimText(parts.doseUnitName) || trimText(parts.doseUnitCode);
  const dosePart = doseQtyText ? (doseUnit ? `${doseQtyText}${doseUnit}` : doseQtyText) : '';

  const splitQtyText = formatSplitQtyDisplayText(parts.splitDose);
  const splitUnit = trimText(parts.splitDoseUnitName) || trimText(parts.splitDoseUnitCode);
  const splitPart = splitQtyText ? (splitUnit ? `${splitQtyText}${splitUnit}` : splitQtyText) : '';

  if (splitPart || dosePart) {
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

  return trimText(parts.dosageDisplay);
}

/** 1～10 中文数字（与后端日剂量友好展示对齐） */
const CHINESE_DIGITS = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'] as const;

/** 正整数 1～10 → 中文；其余原样（去科学计数） */
function formatFriendlyDoseQtyText(qtyText: string): string {
  const raw = trimText(qtyText);
  if (!raw) {
    return '';
  }
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return raw;
  }
  if (Number.isInteger(n) && n >= 1 && n <= 10) {
    return CHINESE_DIGITS[n]!;
  }
  return raw;
}

/** 已是列表友好成稿（含长期「一次一片，每日三片」）则不再补 /次 */
function isReadyMadeDoseSummary(text: string): boolean {
  const core = trimText(text);
  if (!core) {
    return false;
  }
  if (core.startsWith('每日') || core.startsWith('一次')) {
    return true;
  }
  return core.includes('，每日') || core.includes('，一次');
}

/** 剂量段补上「/次」（后端已带则不重复；日剂量/长期友好文案不补） */
function withPerDoseSuffix(doseCore: string): string {
  const core = trimText(doseCore);
  if (!core) {
    return '';
  }
  if (isReadyMadeDoseSummary(core)) {
    return core;
  }
  if (core.endsWith('/次')) {
    return core;
  }
  return `${core}/次`;
}

/**
 * 长期药剂量位：单次量 + 日总量（频次已在用法行展示，不再重复）。
 * 例：一次一片，每日三片（有日剂量文案时才装配；否则返回空串由调用方回落）。
 * 可选追加首日用量：…，首日12支（BR-IP-ORD-030）。
 */
export function formatLongTermMedicationDoseLine(
  options: MedicationDoseDisplayParts &
    MedicationFirstDoseDisplayParts & {
      /** 日剂量友好文案（如 每日三片） */
      dailyDoseDisplay?: null | string;
    },
): string {
  const dailyPart = trimText(options.dailyDoseDisplay);
  if (!dailyPart) {
    // 无日剂量文案时仍可能单独展示首日用量
    return formatFirstDoseDisplay(options);
  }

  // 单次量优先后端 dosageDisplay（如 1片/次）；缺再按结构化字段重拼
  let perDoseCore = trimText(options.dosageDisplay).replace(/\/次$/u, '');
  if (!perDoseCore) {
    perDoseCore = formatMedicationDoseDisplay({
      dose: options.dose,
      doseUnitCode: options.doseUnitCode,
      doseUnitName: options.doseUnitName,
      splitDose: options.splitDose,
      splitDoseUnitCode: options.splitDoseUnitCode,
      splitDoseUnitName: options.splitDoseUnitName,
    }).replace(/\/次$/u, '');
  }
  // 列表只保留最小单位段：1片(5.000毫克) → 1片
  const parenIdx = perDoseCore.indexOf('(');
  if (parenIdx >= 0) {
    perDoseCore = trimText(perDoseCore.slice(0, parenIdx));
  }
  // 勿把日剂量文案再套成「一次…」
  if (perDoseCore && (perDoseCore.startsWith('每日') || perDoseCore === dailyPart)) {
    perDoseCore = '';
  }

  let perDosePart = '';
  if (perDoseCore) {
    // 分次 schedule（含逗号）：禁止套「一次」，否则变成「一次50微克,150微克」
    if (perDoseCore.includes(',') || perDoseCore.includes('，')) {
      perDosePart = perDoseCore.replace(/，/gu, ',');
    } else if (perDoseCore.startsWith('一次')) {
      perDosePart = perDoseCore;
    } else {
      const matched = perDoseCore.match(/^(\d+(?:\.\d+)?)(.*)$/u);
      if (matched) {
        const qtyText = formatFriendlyDoseQtyText(matched[1]!);
        perDosePart = `一次${qtyText}${matched[2] ?? ''}`;
      } else {
        perDosePart = `一次${perDoseCore}`;
      }
    }
  }

  const baseLine = [perDosePart, dailyPart].filter((p) => p.length > 0).join('，');
  return appendFirstDoseToDoseLine(baseLine, options);
}

/**
 * 首日/首次用量展示段：有正数量时返回「首日{数量}{单位}」，否则空串。
 */
export function formatFirstDoseDisplay(options: MedicationFirstDoseDisplayParts): string {
  const raw = options.firstDose;
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return '';
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return '';
  }
  const qtyText = formatFriendlyDoseQtyText(String(n));
  const unit = trimText(options.firstDoseUnitName) || trimText(options.firstDoseUnitCode);
  return unit ? `首日${qtyText}${unit}` : `首日${qtyText}`;
}

/**
 * 在剂量摘要后追加首日用量段（已含「首日」前缀时不重复拼接）。
 */
export function appendFirstDoseToDoseLine(
  baseLine: string,
  options: MedicationFirstDoseDisplayParts,
): string {
  const firstPart = formatFirstDoseDisplay(options);
  if (!firstPart) {
    return trimText(baseLine);
  }
  const base = trimText(baseLine);
  if (!base) {
    return firstPart;
  }
  // 已拼过首日段则保持原样，避免成组/二次格式化重复
  if (base.includes('首日')) {
    return base;
  }
  return `${base}，${firstPart}`;
}

/**
 * 列表剂量摘要行：单次剂量主文案+/次 + 可选「共总量」；末尾句号与历史口径一致。
 * 已是长期友好文案（一次…，每日…）时原样返回，不再补 /次。
 * 有首日用量时追加「，首日…」。
 */
export function formatMedicationDoseSummaryLine(options: MedicationDoseSummaryOptions): string {
  const readyDaily = trimText(options.dosageDisplay);
  // 长期友好成稿或旧「每日三片」：不再拼 /次 / 共
  if (isReadyMadeDoseSummary(readyDaily)) {
    return appendFirstDoseToDoseLine(readyDaily, options);
  }
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
  let base = '';
  if (left && right) {
    base = `${left}，${right}。`;
  } else if (left) {
    base = `${left}。`;
  } else if (right) {
    base = `${right}。`;
  }
  return appendFirstDoseToDoseLine(base, options);
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
  const doseUnit = trimText(item.doseSubUnit);
  const splitUnit = trimText(item.doseMainUnit);
  const hasSplit = parsePositiveQty(item.singleDose) !== undefined;
  const hasDose = parsePositiveQty(item.doseConvertRatio) !== undefined;

  if (hasSplit || hasDose) {
    return withPerDoseSuffix(
      formatMedicationDoseDisplay({
        dose: item.doseConvertRatio,
        doseUnitName: doseUnit || undefined,
        splitDose: item.singleDose,
        splitDoseUnitName: splitUnit || undefined,
      }),
    );
  }
  return '—';
}
