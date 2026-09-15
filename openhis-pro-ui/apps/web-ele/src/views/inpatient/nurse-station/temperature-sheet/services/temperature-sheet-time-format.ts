import type { TemperatureSheetTimeSlot } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

const CHINESE_DIGITS = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九'] as const;

/** 三测时次有效测量区间（下一时次整点前 1 分钟截止） */
const TEMPERATURE_SHEET_TIME_SLOT_RANGE_END_HOUR: Record<TemperatureSheetTimeSlot, number> = {
  '02': 5,
  '06': 9,
  '10': 13,
  '14': 17,
  '18': 21,
  '22': 1,
};

/** 解析日期时间字符串为 YYYY-MM-DD + HH:mm */
export function parseDateTimeYmdHm(value?: string): { dateYmd: string; hm: string } | null {
  const text = (value ?? '').trim();
  if (!text) {
    return null;
  }
  const match = /^(\d{4}-\d{2}-\d{2})[T\s](\d{1,2}):(\d{2})/.exec(text);
  if (match) {
    const hour = Number(match[2]);
    const minute = Number(match[3]);
    if (Number.isFinite(hour) && Number.isFinite(minute)) {
      return {
        dateYmd: match[1],
        hm: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`,
      };
    }
  }
  const dateOnly = /^(\d{4}-\d{2}-\d{2})/.exec(text);
  if (dateOnly) {
    return { dateYmd: dateOnly[1], hm: '00:00' };
  }
  return null;
}

/** 比较两个 HH:mm；左小返回负，相等返回 0，左大返回正 */
export function compareHm(leftHm: string, rightHm: string): number {
  const left = (leftHm ?? '').trim();
  const right = (rightHm ?? '').trim();
  if (left === right) {
    return 0;
  }
  return left < right ? -1 : 1;
}

/** 解析入院日期 YYYY-MM-DD */
export function resolveAdmissionDateYmd(admissionAt?: string): null | string {
  return parseDateTimeYmdHm(admissionAt)?.dateYmd ?? null;
}

/**
 * 录入日当天的测量时间下限 HH:mm。
 * 仅入院日当天有下限（入院时分）；其他日期无限制时返回 null。
 */
export function resolveMinRecordHmForDate(recordDate: string, admissionAt?: string): null | string {
  const admission = parseDateTimeYmdHm(admissionAt);
  const dateYmd = (recordDate ?? '').trim();
  if (!admission || !dateYmd) {
    return null;
  }
  if (dateYmd !== admission.dateYmd) {
    return null;
  }
  return admission.hm;
}

/** 录入日+时分是否早于入院时间 */
export function isRecordDateTimeBeforeAdmission(
  recordDate: string,
  recordTimeHm: string,
  admissionAt?: string,
): boolean {
  const admission = parseDateTimeYmdHm(admissionAt);
  const dateYmd = (recordDate ?? '').trim();
  const hm = (recordTimeHm ?? '').trim();
  if (!admission || !dateYmd || !hm) {
    return false;
  }
  if (dateYmd < admission.dateYmd) {
    return true;
  }
  if (dateYmd > admission.dateYmd) {
    return false;
  }
  return compareHm(hm, admission.hm) < 0;
}

/** 小时映射到三测时次（与测量区间一致） */
export function resolveTemperatureSheetTimeSlotFromHour(hour: number): TemperatureSheetTimeSlot {
  if (hour >= 2 && hour <= 5) {
    return '02';
  }
  if (hour >= 6 && hour <= 9) {
    return '06';
  }
  if (hour >= 10 && hour <= 13) {
    return '10';
  }
  if (hour >= 14 && hour <= 17) {
    return '14';
  }
  if (hour >= 18 && hour <= 21) {
    return '18';
  }
  return '22';
}

/** HH:mm 映射到三测时次 */
export function resolveTemperatureSheetTimeSlotFromHm(hm: string): TemperatureSheetTimeSlot {
  const match = /^(\d{1,2}):(\d{2})$/.exec(hm.trim());
  const hour = match ? Number(match[1]) : 0;
  return resolveTemperatureSheetTimeSlotFromHour(Number.isFinite(hour) ? hour : 0);
}

/** 图表落格就近整点（分小于 30 取当时，分大于等于 30 进位下一时） */
export interface TemperatureSheetNearestHour {
  hour: number;
  hm: string;
}

/**
 * 按 30 分界就近取整点，供体温/脉搏/入院等图表落格。
 * 会跨三测时次的半点仅六个：01:30、05:30、09:30、13:30、17:30、21:30。
 */
export function roundTemperatureSheetHmToNearestHour(
  hm: string,
): TemperatureSheetNearestHour | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(hm.trim());
  if (!match) {
    return null;
  }
  let hour = Number(match[1]);
  const minute = Number(match[2]);
  if (
    !Number.isFinite(hour) ||
    !Number.isFinite(minute) ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59
  ) {
    return null;
  }
  // 半点起进位；23:30 进到 00:00 仍落当日 22 时次
  if (minute >= 30) {
    hour = (hour + 1) % 24;
  }
  return {
    hour,
    hm: `${String(hour).padStart(2, '0')}:00`,
  };
}

/** 就近整点后再映射三测时次（体温/脉搏点、入院标记共用） */
export function resolveTemperatureSheetChartTimeSlotFromHm(hm: string): TemperatureSheetTimeSlot {
  const rounded = roundTemperatureSheetHmToNearestHour(hm);
  if (!rounded) {
    return resolveTemperatureSheetTimeSlotFromHm(hm);
  }
  return resolveTemperatureSheetTimeSlotFromHour(rounded.hour);
}

/** 时次转 HH:mm（用于事件时间默认值） */
export function resolveDefaultEventTimeHm(timeSlot: TemperatureSheetTimeSlot): string {
  const hour = Number(timeSlot);
  if (!Number.isFinite(hour)) {
    return '00:00';
  }
  return `${String(hour).padStart(2, '0')}:00`;
}

/** 时次对应测量时间范围（如 02 → 2:00~5:59） */
export function formatTemperatureSheetTimeSlotRange(timeSlot: TemperatureSheetTimeSlot): string {
  const startHour = Number(timeSlot);
  if (!Number.isFinite(startHour)) {
    return '';
  }
  const endHour = TEMPERATURE_SHEET_TIME_SLOT_RANGE_END_HOUR[timeSlot];
  if (endHour === undefined) {
    return '';
  }
  return `${startHour}:00~${endHour}:59`;
}

/** 将 HH:mm 转为纸质体温单中文时间（如 14:30 → 十四时三十分） */
export function formatHmToChineseTime(hm: string): string {
  const match = /^(\d{1,2}):(\d{2})$/.exec(hm.trim());
  if (!match) {
    return hm.trim();
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return hm.trim();
  }
  const hourText = formatHourChinese(hour);
  if (minute === 0) {
    return `${hourText}时`;
  }
  return `${hourText}时${formatMinuteChinese(minute)}分`;
}

/** 将中文时间或 HH:mm 解析为 HH:mm（解析失败则回退到时次默认） */
export function parseChineseTimeToHm(
  text: string,
  fallbackTimeSlot?: TemperatureSheetTimeSlot,
): string {
  const trimmed = (text ?? '').trim();
  const hmMatch = /^(\d{1,2}):(\d{2})$/.exec(trimmed);
  if (hmMatch) {
    const hour = Number(hmMatch[1]);
    const minute = Number(hmMatch[2]);
    if (
      Number.isFinite(hour) &&
      Number.isFinite(minute) &&
      hour >= 0 &&
      hour <= 23 &&
      minute >= 0 &&
      minute <= 59
    ) {
      return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    }
  }
  const chineseMatch = /^(.+?)时(?:(.+?)分)?$/.exec(trimmed);
  if (chineseMatch) {
    const hour = parseChineseNumber(chineseMatch[1] ?? '');
    const minute = chineseMatch[2] ? parseChineseNumber(chineseMatch[2]) : 0;
    if (
      hour !== null &&
      minute !== null &&
      hour >= 0 &&
      hour <= 23 &&
      minute >= 0 &&
      minute <= 59
    ) {
      return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    }
  }
  return fallbackTimeSlot ? resolveDefaultEventTimeHm(fallbackTimeSlot) : '00:00';
}

function formatHourChinese(hour: number): string {
  if (hour === 10) {
    return '十';
  }
  if (hour === 20) {
    return '二十';
  }
  if (hour < 10) {
    return CHINESE_DIGITS[hour] ?? String(hour);
  }
  const tens = Math.floor(hour / 10);
  const ones = hour % 10;
  const tensText = tens === 1 ? '十' : `${CHINESE_DIGITS[tens] ?? String(tens)}十`;
  if (ones === 0) {
    return tensText;
  }
  return `${tensText}${CHINESE_DIGITS[ones] ?? String(ones)}`;
}

function formatMinuteChinese(minute: number): string {
  if (minute === 0) {
    return '零';
  }
  if (minute < 10) {
    return CHINESE_DIGITS[minute] ?? String(minute);
  }
  const tens = Math.floor(minute / 10);
  const ones = minute % 10;
  if (tens === 1) {
    return ones === 0 ? '十' : `十${CHINESE_DIGITS[ones] ?? String(ones)}`;
  }
  const tensText = `${CHINESE_DIGITS[tens] ?? String(tens)}十`;
  if (ones === 0) {
    return tensText;
  }
  return `${tensText}${CHINESE_DIGITS[ones] ?? String(ones)}`;
}

function parseChineseNumber(text: string): number | null {
  const trimmed = text.trim();
  if (!trimmed) {
    return 0;
  }
  if (/^\d+$/.test(trimmed)) {
    const num = Number(trimmed);
    return Number.isFinite(num) ? num : null;
  }
  if (trimmed === '十') {
    return 10;
  }
  if (trimmed.startsWith('十')) {
    const ones = trimmed.slice(1);
    return (
      10 +
      (CHINESE_DIGITS.indexOf(ones as (typeof CHINESE_DIGITS)[number]) >= 0
        ? CHINESE_DIGITS.indexOf(ones as (typeof CHINESE_DIGITS)[number])
        : 0)
    );
  }
  if (trimmed.endsWith('十')) {
    const tens = trimmed.slice(0, -1);
    const tensIndex = CHINESE_DIGITS.indexOf(tens as (typeof CHINESE_DIGITS)[number]);
    return tensIndex >= 0 ? tensIndex * 10 : null;
  }
  const tenIndex = trimmed.indexOf('十');
  if (tenIndex >= 0) {
    const tensPart = trimmed.slice(0, tenIndex);
    const onesPart = trimmed.slice(tenIndex + 1);
    const tens = tensPart ? CHINESE_DIGITS.indexOf(tensPart as (typeof CHINESE_DIGITS)[number]) : 1;
    const ones = onesPart ? CHINESE_DIGITS.indexOf(onesPart as (typeof CHINESE_DIGITS)[number]) : 0;
    if (tens >= 0 && ones >= 0) {
      return tens * 10 + ones;
    }
  }
  const digitIndex = CHINESE_DIGITS.indexOf(trimmed as (typeof CHINESE_DIGITS)[number]);
  return digitIndex >= 0 ? digitIndex : null;
}

/** 时次转中文（纸质体温单「十时」口径） */
export function formatTemperatureSheetTimeSlotChinese(timeSlot: TemperatureSheetTimeSlot): string {
  const hour = Number(timeSlot);
  if (!Number.isFinite(hour)) {
    return timeSlot;
  }
  const chineseDigits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
  if (hour === 10) {
    return '十时';
  }
  if (hour === 20) {
    return '二十时';
  }
  if (hour === 22) {
    return '二十二时';
  }
  if (hour < 10) {
    return `${chineseDigits[hour] ?? String(hour)}时`;
  }
  const tens = Math.floor(hour / 10);
  const ones = hour % 10;
  const tensText = tens === 1 ? '十' : `${chineseDigits[tens] ?? String(tens)}十`;
  if (ones === 0) {
    return `${tensText}时`;
  }
  return `${tensText}${chineseDigits[ones] ?? String(ones)}时`;
}
