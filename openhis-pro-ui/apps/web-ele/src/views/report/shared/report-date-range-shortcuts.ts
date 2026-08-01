import {
  buildDefaultDateRangeShortcuts,
  buildDefaultDateTimeRangeShortcuts,
} from '#/utils/format/date-picker-shortcuts';
import { getLocalDayRangeDateTimeStrings } from '#/utils/format/date-range-quick';

const REPORT_DATETIME_RE = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?/;

/** 将日期值规范为 YYYY-MM-DD（兼容接口回传的 datetime 字符串）。 */
export function normalizeReportDateRangeYmd(value?: string): string | undefined {
  const ymd = String(value ?? '')
    .trim()
    .slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(ymd) ? ymd : undefined;
}

/**
 * 将日期/时间值规范为 YYYY-MM-DD HH:mm:ss。
 * 仅日历日时：start→00:00:00、end→23:59:59；已含时分则保留（缺秒补 00）。
 */
export function normalizeReportDateTime(
  value?: string,
  bound: 'end' | 'start' = 'start',
): string | undefined {
  const text = String(value ?? '').trim();
  if (!text) {
    return undefined;
  }
  const ymd = normalizeReportDateRangeYmd(text);
  if (!ymd) {
    return undefined;
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    const day = getLocalDayRangeDateTimeStrings(ymd);
    return bound === 'start' ? day.startDate || undefined : day.endDate || undefined;
  }
  const match = REPORT_DATETIME_RE.exec(text);
  if (!match) {
    const day = getLocalDayRangeDateTimeStrings(ymd);
    return bound === 'start' ? day.startDate || undefined : day.endDate || undefined;
  }
  const raw = match[0].replace('T', ' ');
  return raw.length === 16 ? `${raw}:00` : raw;
}

/** 报表查询：将 YYYY-MM-DD 区间转为接口常用的本地日起止 datetime。 */
export function toReportApiDateTimeRange(
  start?: string,
  end?: string,
): { end?: string; start?: string } {
  const startYmd = normalizeReportDateRangeYmd(start);
  const endYmd = normalizeReportDateRangeYmd(end);
  if (!startYmd || !endYmd) {
    return {};
  }
  const startDay = getLocalDayRangeDateTimeStrings(startYmd);
  const endDay = getLocalDayRangeDateTimeStrings(endYmd);
  if (!startDay.startDate || !endDay.endDate) {
    return {};
  }
  return { start: startDay.startDate, end: endDay.endDate };
}

/**
 * 报表查询：起止已含时分秒则原样规范后回传；仅日历日时仍补全日边界。
 */
export function toReportApiDateTimeRangePrecise(
  start?: string,
  end?: string,
): { end?: string; start?: string } {
  const startDt = normalizeReportDateTime(start, 'start');
  const endDt = normalizeReportDateTime(end, 'end');
  if (!startDt || !endDt) {
    return {};
  }
  return { start: startDt, end: endDt };
}

/** 报表统计日期范围快捷项（与全局默认区间快捷同口径）。 */
export function buildReportDateRangeShortcuts() {
  return buildDefaultDateRangeShortcuts();
}

/** 报表统计日期时间范围快捷项（起止落在 00:00:00～23:59:59）。 */
export function buildReportDateTimeRangeShortcuts() {
  return buildDefaultDateTimeRangeShortcuts();
}
