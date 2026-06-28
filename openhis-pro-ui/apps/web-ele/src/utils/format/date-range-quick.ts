const ONE_DAY_MS = 24 * 60 * 60 * 1000;

const DEFAULT_DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** `YYYY-MM-DD` 严格匹配（用于解析日历日，避免 `Date` 字符串解析的时区歧义）。 */
const LOCAL_YMD_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function formatLocalYmd(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * 本地日历日的 `YYYY-MM-DD`（不按 UTC 偏移）。
 *
 * @param date 默认取当前时刻所在本地日
 */
export function formatLocalDateYmd(date: Date = new Date()): string {
  return formatLocalYmd(date);
}

/**
 * 将本地 `Date` 按占位符格式化为字符串，占位符约定与 `formatDateStr` 一致：`YYYY` `MM` `DD` `HH` `mm` `ss`。
 *
 * @param date 有效日期；非法则返回空串
 * @param format 默认 `YYYY-MM-DD HH:mm:ss`
 */
export function formatLocalDateTime(date: Date, format = DEFAULT_DATETIME_FORMAT): string {
  if (Number.isNaN(date.getTime())) return '';
  const year = String(date.getFullYear());
  const month = pad2(date.getMonth() + 1);
  const day = pad2(date.getDate());
  const hours = pad2(date.getHours());
  const minutes = pad2(date.getMinutes());
  const seconds = pad2(date.getSeconds());
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}

/**
 * 将日历日 `YYYY-MM-DD` 转为接口常用的「本地当天起止」：`... 00:00:00` ～ `... 23:59:59`。
 * 非法或非日历日字符串时返回空起止，由调用方决定是否拦截。
 *
 * @param ymd 如 `2026-05-03`
 */
export function getLocalDayRangeDateTimeStrings(ymd: string): {
  endDate: string;
  startDate: string;
} {
  const trimmed = ymd.trim();
  const match = LOCAL_YMD_RE.exec(trimmed);
  if (!match) return { endDate: '', startDate: '' };

  const y = Number(match[1]);
  const monthIndex = Number(match[2]) - 1;
  const d = Number(match[3]);
  const start = new Date(y, monthIndex, d, 0, 0, 0, 0);
  if (start.getFullYear() !== y || start.getMonth() !== monthIndex || start.getDate() !== d) {
    return { endDate: '', startDate: '' };
  }

  const end = new Date(y, monthIndex, d, 23, 59, 59, 0);
  return {
    endDate: formatLocalDateTime(end),
    startDate: formatLocalDateTime(start),
  };
}

/** 本地日历日的 0 点（忽略时分秒） */
function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/**
 * 算出一个日期区间 `[开始日, 结束日]`（`YYYY-MM-DD`），给日期范围筛选、接口 `dateFrom/dateTo` 用。
 * 不写 `refDate` 时，按「今天」这台电脑的本地日历理解。
 *
 * 四种写法（看名字就知道选哪种）：
 *
 * 1) `strategy: 'calendar-day'` —— 从某一天起，往后数连续几天（都算在区间里）。
 *    只要「今天一天」：`{ strategy: 'calendar-day' }`（和写 `consecutiveDays: 1` 一样）
 *    从 ref 那天起连续 3 天：`{ strategy: 'calendar-day', consecutiveDays: 3 }`
 *    指定从哪天开始数：`{ strategy: 'calendar-day', refDate: new Date('2026-05-01'), consecutiveDays: 5 }`
 *
 * 2) `strategy: 'calendar-week'` —— 先找到 ref 所在那周的「周一」，从那个周一开始，接几个完整的「周一～周日」。
 *    只要「本周」：`{ strategy: 'calendar-week' }`
 *    从本周周一开始连续两个整周：`{ strategy: 'calendar-week', consecutiveWeeks: 2 }`
 *
 * 3) `strategy: 'calendar-month'` —— 以 ref 所在那个自然月为「最后一个月」，往前再拼上几个整月（每月 1 号到最后一天）。
 *    只要「本月」：`{ strategy: 'calendar-month' }`
 *    以本月为结尾、一共连续 3 个自然月：`{ strategy: 'calendar-month', consecutiveMonths: 3 }`
 *
 * 4) `strategy: 'rolling-days'` —— 把 ref 那一天当成区间的「最后一天」，再往前倒 `spanDays` 天（含最后一天），适合「最近 N 天」。
 *    最近 30 天（含今天）：`{ strategy: 'rolling-days', spanDays: 30 }`
 *    以某一天为「区间最后一天」再往前 7 天：`{ strategy: 'rolling-days', refDate: 该日的 Date, spanDays: 7 }`
 */
export type DateRangeQuickSpec =
  | {
      consecutiveDays?: number;
      refDate?: Date;
      strategy: 'calendar-day';
    }
  | {
      consecutiveMonths?: number;
      refDate?: Date;
      strategy: 'calendar-month';
    }
  | {
      consecutiveWeeks?: number;
      refDate?: Date;
      strategy: 'calendar-week';
    }
  | {
      refDate?: Date;
      spanDays: number;
      strategy: 'rolling-days';
    };

/** 见上方 `DateRangeQuickSpec` 注释里的四种「怎么写」示例。 */
export function getDateRangeQuick(spec: DateRangeQuickSpec): [string, string] {
  const ref = spec.refDate ?? new Date();

  if (spec.strategy === 'calendar-day') {
    const n = Math.max(1, spec.consecutiveDays ?? 1);
    const start = startOfLocalDay(ref);
    const end = new Date(start);
    end.setDate(end.getDate() + n - 1);
    return [formatLocalYmd(start), formatLocalYmd(end)];
  }

  if (spec.strategy === 'calendar-month') {
    const n = Math.max(1, spec.consecutiveMonths ?? 1);
    const y = ref.getFullYear();
    const m = ref.getMonth();
    const start = new Date(y, m - (n - 1), 1);
    const end = new Date(y, m + 1, 0);
    return [formatLocalYmd(start), formatLocalYmd(end)];
  }

  if (spec.strategy === 'calendar-week') {
    const n = Math.max(1, spec.consecutiveWeeks ?? 1);
    const weekDay = ref.getDay() === 0 ? 7 : ref.getDay();
    const monday = new Date(ref.getTime() - (weekDay - 1) * ONE_DAY_MS);
    const end = new Date(monday.getTime() + (7 * n - 1) * ONE_DAY_MS);
    return [formatLocalYmd(monday), formatLocalYmd(end)];
  }

  const span = Math.max(1, spec.spanDays);
  const end = startOfLocalDay(ref);
  const start = new Date(end);
  start.setDate(start.getDate() - (span - 1));
  return [formatLocalYmd(start), formatLocalYmd(end)];
}

/**
 * 将可解析为时间的值格式化为本地时间字符串（占位符：`YYYY` `MM` `DD` `HH` `mm` `ss`）。
 * 空值或无法解析的日期返回空串。
 */
export function formatDateStr(
  cellValue: Date | null | number | string | undefined,
  format = DEFAULT_DATETIME_FORMAT,
): string {
  if (cellValue === null || cellValue === undefined || cellValue === '') return '';

  const date = cellValue instanceof Date ? cellValue : new Date(cellValue);
  if (Number.isNaN(date.getTime())) return '';

  return formatLocalDateTime(date, format);
}
