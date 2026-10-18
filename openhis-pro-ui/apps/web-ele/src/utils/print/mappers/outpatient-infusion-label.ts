import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import { pageFrequencyApi } from '#/api/basic/frequency';
import { decimalDiv } from '#/utils/format/decimal-util';

/** 输液瓶签药品行（与 op/ip-infusionLabel-print 模板 medicationLines 列一致） */
export interface OutpatientInfusionLabelMedicationLine {
  drugName: string;
  specText: string;
  /**
   * 单次剂量：医生开立的计量（如 `2ml`、`1mg`）。
   * 取 dosageDisplay 括号内计量段；无括号时用整段。不印拆零单位（瓶/支）。
   */
  doseText: string;
  totalQuantityText: string;
  usageText: string;
}

/** 输液瓶签 PrintData（与 op/ip-infusionLabel-print 模板 field 一致） */
export interface OutpatientInfusionLabelPrintData {
  patientName: string;
  genderEnumText: string;
  ageText: string;
  /**
   * 时间文案：门诊模板标题「挂号时间」；住院模板标题「入院时间」。
   * 值由各入口传入（门诊挂号时间 / 住院入院时间）。
   */
  registerTimeText: string;
  /** 床位（住院瓶签仅床号如「6床」；门诊可空） */
  wardBedText: string;
  /** 频次展示（住院瓶签；组级，优先显式 freqText，否则取药品行 rateName） */
  freqText: string;
  medicationLines: OutpatientInfusionLabelMedicationLine[];
  /** 滴速（模板 field=speedCode；值为展示文案，优先 speedDisplay） */
  speedCode: string;
  executorName: string;
  executeTimeText: string;
}

/** 瓶签 mapper 入参（P：护士站当前就诊 + 输液类药品行） */
export interface OutpatientInfusionLabelPrintSource {
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  registerTimeText?: string;
  /** 床位展示（住院仅床号如「6床」） */
  wardBedText?: string;
  /** 频次展示（如「每日两次」；住院瓶签；也可由药品行 rateName 回落） */
  freqText?: string;
  medicationLines?: NurseStationTreatmentLineDto[];
  /** 滴速（组级；住院瓶签模板 field=speedCode，传展示名） */
  speedCode?: string;
  executorName?: string;
  executeTimeText?: string;
  /**
   * 本张瓶贴对应的全程拆分份数（用药天数 × 每日次数）。
   * 缺省/≤1：总量不拆；＞1：总量按份数整除后打印到本张。
   */
  quantitySplitCount?: number;
  /**
   * 每日用药次数覆盖（住院可按当日执行槽位数传入；有值且＞0 时优先生效，
   * 否则再走 rateCode → cus_frequency.dayTimes）。
   */
  timesPerDay?: number;
}

/** 常见日频次 rateCode → 每日次数（接口不可用时回落；禁止用中文名判断） */
const COMMON_DAILY_TIMES_BY_RATE_CODE: Readonly<Record<string, number>> = {
  QD: 1,
  BID: 2,
  TID: 3,
  QID: 4,
};

/** rateCode → 每日次数缓存（同次打印多组复用） */
const timesPerDayByRateCodeCache = new Map<string, number>();

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function buildUsageText(row: NurseStationTreatmentLineDto): string {
  return str(row.methodName) || str(row.methodCode);
}

/** 规范化拆分份数（无效时按 1，不拆总量） */
function normalizeQuantitySplitCount(raw: null | number | string | undefined): number {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return 1;
  }
  const n = typeof raw === 'number' ? raw : Number(String(raw).trim());
  if (!Number.isFinite(n) || n <= 1) {
    return 1;
  }
  return Math.trunc(n);
}

/** 数量展示：整数量去小数位，否则去掉尾随 0 */
function formatQuantityDisplay(qty: number): string {
  if (!Number.isFinite(qty)) {
    return '';
  }
  if (Number.isInteger(qty)) {
    return String(qty);
  }
  return String(qty).replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1');
}

/** 计量单位展示名收成瓶签短写（与开立展示「毫升→ml」同口径，并覆盖毫克/克） */
const ORDERED_DOSE_UNIT_REPLACEMENTS: ReadonlyArray<readonly [string, string]> = [
  ['毫升', 'ml'],
  ['毫克', 'mg'],
  ['微克', 'μg'],
  ['国际单位', 'IU'],
  ['单位', 'U'],
  ['克', 'g'],
];

/**
 * 瓶贴总量用 dosageDisplay 时：只保留最小（拆零）单位段，去掉括号内计量与「/次」。
 * 例：`1瓶(100.000ml)/次` → `1瓶`；`1片/次` → `1片`。
 */
function formatDosageDisplayAsMinUnit(dosageDisplay: null | string | undefined): string {
  const raw = str(dosageDisplay);
  if (!raw) {
    return '';
  }
  const withoutPerDose = raw.replace(/[/／]次$/u, '').trim();
  const parenIdx = withoutPerDose.search(/[(（]/u);
  if (parenIdx > 0) {
    return withoutPerDose.slice(0, parenIdx).trim();
  }
  return withoutPerDose;
}

/** 剂量数字去尾零：`100.000` → `100`，`2.500` → `2.5`，`35.625` 保持 */
function stripTrailingZerosInDoseQuantity(text: string): string {
  return text.replace(/(\d+)\.(\d*?)0+(?=[^\d]|$)/g, (_match, intPart: string, frac: string) => {
    if (!frac) {
      return intPart;
    }
    return `${intPart}.${frac}`;
  });
}

/**
 * 瓶签剂量列：医生开的计量单位，不印拆零单位。
 * `1瓶(100.000ml)/次` → `100ml`；`1支(1.000毫克)/次` → `1mg`；`10ml/次` → `10ml`。
 */
function formatInfusionLabelOrderedDose(dosageDisplay: null | string | undefined): string {
  const raw = str(dosageDisplay).replace(/[/／]次$/u, '').trim();
  if (!raw) {
    return '';
  }
  const wrapped = raw.match(/[(（]([^)）]+)[)）]/u);
  let dose = wrapped?.[1]?.trim() || raw;
  for (const [fromUnit, toUnit] of ORDERED_DOSE_UNIT_REPLACEMENTS) {
    dose = dose.replaceAll(fromUnit, toUnit);
  }
  dose = dose.replaceAll(/\s+/g, '');
  dose = dose.replaceAll(/mL|ML/g, 'ml');
  return stripTrailingZerosInDoseQuantity(dose);
}

function formatTotalQuantityText(
  row: NurseStationTreatmentLineDto,
  quantitySplitCount: number,
): string {
  const qtyRaw = row.quantity;
  // 住院执行页仅有 dosageDisplay（剂量列），无发药 quantity：瓶贴只印最小单位
  if (qtyRaw === null || qtyRaw === undefined || String(qtyRaw).trim() === '') {
    return formatDosageDisplayAsMinUnit(row.dosageDisplay);
  }
  const qty = typeof qtyRaw === 'number' ? qtyRaw : Number(String(qtyRaw).trim());
  if (!Number.isFinite(qty)) {
    const unit = str(row.unitName) || str(row.unitCode);
    const rawText = String(qtyRaw).trim();
    return unit ? `${rawText}${unit}` : rawText;
  }
  // 按天数×频次份数拆到本张（业务约定可整除）
  const splitCount = normalizeQuantitySplitCount(quantitySplitCount);
  const perLabelQty = splitCount > 1 ? decimalDiv(qty, splitCount) : qty;
  const qtyText = formatQuantityDisplay(perLabelQty);
  const unit = str(row.unitName) || str(row.unitCode);
  return unit ? `${qtyText}${unit}` : qtyText;
}

/**
 * 规格压成一行：去掉空格与括号备注，过长再截断。
 * `100 ml: 0.9g` → `100ml:0.9g`，避免规格列折成三行把第三支药挤出贴面。
 */
export function truncateInfusionLabelSpecText(
  raw: null | string | undefined,
  maxChars = 16,
): string {
  const text = str(raw)
    .replaceAll(/（[^）]*）/gu, '')
    .replaceAll(/\([^)]*\)/g, '')
    .replaceAll(/\s+/g, '');
  if (!text) {
    return '';
  }
  if (text.length <= maxChars) {
    return text;
  }
  return text.slice(0, maxChars);
}

function mapMedicationLine(
  row: NurseStationTreatmentLineDto,
  quantitySplitCount: number,
): OutpatientInfusionLabelMedicationLine {
  return {
    drugName: str(row.itemName),
    specText: truncateInfusionLabelSpecText(row.specText),
    doseText: formatInfusionLabelOrderedDose(row.dosageDisplay),
    totalQuantityText: formatTotalQuantityText(row, quantitySplitCount),
    usageText: buildUsageText(row),
  };
}

/** 由 dayTimes（如 08:00,18:00）解析每日次数 */
function countTimesFromDayTimes(dayTimes: null | string | undefined): number {
  const parts = str(dayTimes)
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
  return parts.length;
}

function infusionLineSortSeq(raw: NurseStationTreatmentLineDto['sortNumber']): number {
  if (raw === null || raw === undefined) {
    return Number.MAX_SAFE_INTEGER;
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER;
}

function formatDateTimeLikeNurseStation(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  const second = `${date.getSeconds()}`.padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

function parseExecuteTimeText(value: string): Date | null {
  const text = value.trim();
  if (!text) {
    return null;
  }
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4] ?? '0');
  const minute = Number(match[5] ?? '0');
  const second = Number(match[6] ?? '0');
  const parsed = new Date(year, month - 1, day, hour, minute, second);
  if (
    parsed.getFullYear() !== year ||
    parsed.getMonth() !== month - 1 ||
    parsed.getDate() !== day
  ) {
    return null;
  }
  return parsed;
}

/** 输液组用药天数（取组内最大值；无效时默认 1 天） */
export function resolveInfusionLabelPrintDayCount(
  medicationLines: NurseStationTreatmentLineDto[],
): number {
  let maxDays = 0;
  for (const row of medicationLines) {
    const raw = row.dispensePerDuration;
    if (raw === null || raw === undefined) {
      continue;
    }
    const days = typeof raw === 'number' ? raw : Number(String(raw).trim());
    if (Number.isFinite(days) && days > maxDays) {
      maxDays = Math.trunc(days);
    }
  }
  return maxDays > 0 ? maxDays : 1;
}

/** 取组内 rateCode（同组频次一致，取首条非空） */
function resolveGroupRateCode(medicationLines: NurseStationTreatmentLineDto[]): string {
  for (const row of medicationLines) {
    const rateCode = str(row.rateCode);
    if (rateCode) {
      return rateCode;
    }
  }
  return '';
}

/** 取组内频次展示（同组一致，取首条非空 rateName） */
function resolveGroupFreqText(medicationLines: NurseStationTreatmentLineDto[]): string {
  for (const row of medicationLines) {
    const rateName = str(row.rateName);
    if (rateName) {
      return rateName;
    }
  }
  return '';
}

/** 常见日频次回落；未知 code 默认 1 次/日 */
function resolveTimesPerDayFallback(rateCode: string): number {
  const key = rateCode.trim().toUpperCase();
  const mapped = COMMON_DAILY_TIMES_BY_RATE_CODE[key];
  return mapped !== undefined && mapped > 0 ? mapped : 1;
}

/**
 * 同步估算每日次数：优先 rateCode 常见表，否则当日槽位数，都无则 1。
 * 仅用于列表张数/预估页；真正打印仍走 resolveInfusionLabelPrintTimesPerDay。
 */
export function resolveInfusionTimesPerDaySync(
  rateCode?: null | string,
  daySlotCount?: null | number,
): number {
  const code = str(rateCode);
  if (code) {
    const mapped = resolveTimesPerDayFallback(code);
    const known = COMMON_DAILY_TIMES_BY_RATE_CODE[code.trim().toUpperCase()];
    // 常见 code 用表内次数；未知 code 不盲默认 1，改回落槽位
    if (known !== undefined && mapped > 0) {
      return mapped;
    }
  }
  const slots =
    typeof daySlotCount === 'number' && Number.isFinite(daySlotCount)
      ? Math.trunc(daySlotCount)
      : 0;
  return slots > 0 ? slots : 1;
}

/**
 * 输液组每日用药次数（由 rateCode → cus_frequency.dayTimes 段数）。
 * 同组频次一致；无 rateCode / 解析失败时默认 1。
 */
export async function resolveInfusionLabelPrintTimesPerDay(
  medicationLines: NurseStationTreatmentLineDto[],
): Promise<number> {
  const rateCode = resolveGroupRateCode(medicationLines);
  if (!rateCode) {
    return 1;
  }
  const cached = timesPerDayByRateCodeCache.get(rateCode);
  if (cached !== undefined) {
    return cached;
  }

  let timesFromApi = 0;
  try {
    const res = await pageFrequencyApi({ rateCode, pageNo: 1, pageSize: 20 });
    // 兼容 requestClient 解包 / 未解包两种形态
    const root = (res ?? {}) as {
      content?: Array<{ dayTimes?: string; rateCode?: string }>;
      data?: { content?: Array<{ dayTimes?: string; rateCode?: string }> };
    };
    let list: Array<{ dayTimes?: string; rateCode?: string }> = [];
    if (Array.isArray(root.data?.content)) {
      list = root.data.content;
    } else if (Array.isArray(root.content)) {
      list = root.content;
    }
    // 精确匹配 rateCode，避免模糊命中
    const rateCodeUpper = rateCode.toUpperCase();
    let matched = list.find((item) => str(item.rateCode).toUpperCase() === rateCodeUpper);
    if (!matched) {
      matched = list[0];
    }
    timesFromApi = countTimesFromDayTimes(matched?.dayTimes);
  } catch {
    timesFromApi = 0;
  }

  const times = timesFromApi > 0 ? timesFromApi : resolveTimesPerDayFallback(rateCode);
  timesPerDayByRateCodeCache.set(rateCode, times);
  return times;
}

/** 按用药天序（0 起）偏移执行时间；无法解析时原样返回 */
export function buildExecuteTimeTextForInfusionDay(
  baseExecuteTimeText: string,
  dayIndex: number,
): string {
  if (dayIndex <= 0) {
    return str(baseExecuteTimeText);
  }
  const parsed = parseExecuteTimeText(baseExecuteTimeText);
  if (!parsed) {
    return str(baseExecuteTimeText);
  }
  parsed.setDate(parsed.getDate() + dayIndex);
  return formatDateTimeLikeNurseStation(parsed);
}

/** 输液瓶签打印组：同 groupId 的药品打到同一张瓶贴 */
export interface InfusionLabelPrintGroup {
  groupId?: string;
  medicationLines: NurseStationTreatmentLineDto[];
}

/** 筛选输液类药品并按 groupId 分组（无组号则每条单独一组） */
export function groupInfusionMedicationLinesForPrint(
  lines: NurseStationTreatmentLineDto[],
): InfusionLabelPrintGroup[] {
  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of lines) {
    if (!isInfusionMedicationLine(row)) {
      continue;
    }
    const requestId = str(row.requestId);
    if (!requestId) {
      continue;
    }
    const groupId = str(row.groupId);
    const groupKey = groupId ? `g:${groupId}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: groupId || undefined,
      medicationLines: [],
    };
    if (!existing.medicationLines.some((line) => str(line.requestId) === requestId)) {
      existing.medicationLines.push(row);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()].map((group) => ({
    ...group,
    medicationLines: [...group.medicationLines].toSorted(
      (left, right) => infusionLineSortSeq(left.sortNumber) - infusionLineSortSeq(right.sortNumber),
    ),
  }));
}

/** 判断是否为输液类药品行（仅看后端 infusionFlag；目录 inject_flag 已在后端回落，不按用法限制） */
export function isInfusionMedicationLine(row: NurseStationTreatmentLineDto): boolean {
  return row.infusionFlag === true;
}

/** 护士站输液药品行 → PrintData */
export function buildOutpatientInfusionLabelPrintData(
  source: OutpatientInfusionLabelPrintSource,
): OutpatientInfusionLabelPrintData {
  const rows = (source.medicationLines ?? []).filter((line) => isInfusionMedicationLine(line));
  const quantitySplitCount = normalizeQuantitySplitCount(source.quantitySplitCount);
  return {
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    registerTimeText: str(source.registerTimeText),
    wardBedText: str(source.wardBedText),
    freqText: str(source.freqText) || resolveGroupFreqText(rows),
    medicationLines: rows.map((line) => mapMedicationLine(line, quantitySplitCount)),
    speedCode: str(source.speedCode),
    executorName: str(source.executorName),
    executeTimeText: str(source.executeTimeText),
  };
}
