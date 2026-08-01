import { formatLocalDateYmd, getDateRangeQuick } from '#/utils/format/date-range-quick';

/** ElDatePicker popper-class：快捷项改到底部横排（见 styles/date-picker-shortcuts-bottom.css）。 */
export const DATE_PICKER_SHORTCUTS_BOTTOM_POPPER_CLASS = 'oh-date-picker-shortcuts-bottom';

/**
 * 单日选择器 popper-class：底部两端布局（上一天 | 下一天）+ link 主色。
 * 含通用底部布局类，可直接赋给 :popper-class。
 */
export const DATE_PICKER_SINGLE_DAY_SHORTCUTS_POPPER_CLASS =
  'oh-date-picker-shortcuts-bottom oh-date-picker-shortcuts-bottom--single-day';

/** ElDatePicker shortcuts 项（单日或区间）。 */
export interface DatePickerShortcutItem {
  text: string;
  /** 有 value 时 EP 会 pick 并关面板；单日上一天/下一天只用 onClick 保持面板打开 */
  value?: Date | (() => Date) | (() => [Date, Date]) | [Date, Date];
  onClick?: () => void;
}

/** 将 YYYY-MM-DD 解析为本地日历日 0 点。 */
function parseYmdToLocalDate(ymd: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd.trim());
  if (!match) {
    return new Date(Number.NaN);
  }
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

/** 将 YYYY-MM-DD 区间转为 Date 对（供 daterange shortcuts）。 */
function toDatePair(range: [string, string]): [Date, Date] {
  return [parseYmdToLocalDate(range[0]), parseYmdToLocalDate(range[1])];
}

/** 将 YYYY-MM-DD 区间转为当日 00:00:00～23:59:59（供 datetimerange shortcuts）。 */
function toDateTimePair(range: [string, string]): [Date, Date] {
  const start = parseYmdToLocalDate(range[0]);
  const end = parseYmdToLocalDate(range[1]);
  return [
    new Date(start.getFullYear(), start.getMonth(), start.getDate(), 0, 0, 0),
    new Date(end.getFullYear(), end.getMonth(), end.getDate(), 23, 59, 59),
  ];
}

/** 取昨天所在本地日历日。 */
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

/** 解析当前选中日：空值回退今天。 */
function resolveCurrentLocalDay(getCurrent?: () => Date | null | string | undefined): Date {
  const today = new Date();
  const fallback = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  if (!getCurrent) {
    return fallback;
  }
  const raw = getCurrent();
  if (raw == null || raw === '') {
    return fallback;
  }
  if (raw instanceof Date) {
    if (Number.isNaN(raw.getTime())) {
      return fallback;
    }
    return new Date(raw.getFullYear(), raw.getMonth(), raw.getDate());
  }
  const ymd = String(raw).trim().slice(0, 10);
  const parsed = parseYmdToLocalDate(ymd);
  if (Number.isNaN(parsed.getTime())) {
    return fallback;
  }
  return parsed;
}

/**
 * 单日选择器展开面板快捷项：相对当前选中日的上一天 / 下一天。
 * 仅用 onClick 写回日期（不设 value），避免 Element Plus pick 后关面板。
 */
export function buildSingleDayPickerShortcuts(
  getCurrent: () => Date | null | string | undefined,
  onSelectDay: (ymd: string) => void,
): DatePickerShortcutItem[] {
  /** 相对当前日偏移 N 天并写回 YYYY-MM-DD */
  function shiftAndSelect(dayOffset: number) {
    const base = resolveCurrentLocalDay(getCurrent);
    const next = new Date(base.getFullYear(), base.getMonth(), base.getDate() + dayOffset);
    onSelectDay(formatLocalDateYmd(next));
  }

  return [
    {
      text: '上一天',
      onClick: () => shiftAndSelect(-1),
    },
    {
      text: '下一天',
      onClick: () => shiftAndSelect(1),
    },
  ];
}

/** 默认日期范围快捷项（今天 / 昨天 / 本周 / 本月 / 近三月 …）。 */
export function buildDefaultDateRangeShortcuts(): DatePickerShortcutItem[] {
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

/** 默认日期时间范围快捷项（与日期范围同口径，落在起止日的 00:00:00～23:59:59）。 */
export function buildDefaultDateTimeRangeShortcuts(): DatePickerShortcutItem[] {
  return [
    {
      text: '今天',
      value: () => toDateTimePair(getDateRangeQuick({ strategy: 'calendar-day' })),
    },
    {
      text: '昨天',
      value: () =>
        toDateTimePair(
          getDateRangeQuick({
            strategy: 'calendar-day',
            refDate: getYesterdayRefDate(),
            consecutiveDays: 1,
          }),
        ),
    },
    {
      text: '本周',
      value: () => toDateTimePair(getDateRangeQuick({ strategy: 'calendar-week' })),
    },
    {
      text: '本月',
      value: () => toDateTimePair(getDateRangeQuick({ strategy: 'calendar-month' })),
    },
    {
      text: '近三月',
      value: () => toDateTimePair(getRecentMonthsToToday(3)),
    },
    {
      text: '近半年',
      value: () => toDateTimePair(getRecentMonthsToToday(6)),
    },
    {
      text: '近一年',
      value: () => toDateTimePair(getRecentMonthsToToday(12)),
    },
    {
      text: '去年',
      value: () => toDateTimePair(getLastCalendarYearRange()),
    },
  ];
}
