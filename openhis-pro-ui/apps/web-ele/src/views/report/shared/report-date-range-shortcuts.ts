import {
  formatLocalDateYmd,
  getDateRangeQuick,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';

/** 将 YYYY-MM-DD 区间转为 Date 对（供 ElDatePicker shortcuts 使用）。 */
function toDatePair(range: [string, string]): [Date, Date] {
  const parseYmd = (ymd: string) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd.trim());
    if (!match) {
      return new Date(Number.NaN);
    }
    return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  };
  return [parseYmd(range[0]), parseYmd(range[1])];
}

/** 取昨天所在本地日历日（供快捷项使用）。 */
function getYesterdayRefDate(): Date {
  const today = new Date();
  return new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
}

/** 近 N 个自然月：从 N 个月前的 1 号至今天。 */
function getRecentMonthsToToday(monthCount: number): [string, string] {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth() - (monthCount - 1), 1);
  return [formatLocalDateYmd(start), formatLocalDateYmd(today)];
}

/** 上一个自然年：1 月 1 日至 12 月 31 日。 */
function getLastCalendarYearRange(): [string, string] {
  const year = new Date().getFullYear() - 1;
  return [`${year}-01-01`, `${year}-12-31`];
}

/** 将日期值规范为 YYYY-MM-DD（兼容接口回传的 datetime 字符串）。 */
export function normalizeReportDateRangeYmd(value?: string): string | undefined {
  const ymd = String(value ?? '')
    .trim()
    .slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(ymd) ? ymd : undefined;
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

/** 报表统计日期范围快捷项（今天 / 昨天 / 本周 / 本月 …）。 */
export function buildReportDateRangeShortcuts() {
  return [
    {
      text: '今天',
      value: () => toDatePair(getDateRangeQuick({ strategy: 'calendar-day' })),
    },
    {
      text: '昨天',
      value: () =>
        toDatePair(
          getDateRangeQuick({
            strategy: 'calendar-day',
            refDate: getYesterdayRefDate(),
            consecutiveDays: 1,
          }),
        ),
    },
    {
      text: '本周',
      value: () => toDatePair(getDateRangeQuick({ strategy: 'calendar-week' })),
    },
    {
      text: '本月',
      value: () => toDatePair(getDateRangeQuick({ strategy: 'calendar-month' })),
    },
    {
      text: '近三月',
      value: () => toDatePair(getRecentMonthsToToday(3)),
    },
    {
      text: '近半年',
      value: () => toDatePair(getRecentMonthsToToday(6)),
    },
    {
      text: '近一年',
      value: () => toDatePair(getRecentMonthsToToday(12)),
    },
    {
      text: '去年',
      value: () => toDatePair(getLastCalendarYearRange()),
    },
  ];
}
