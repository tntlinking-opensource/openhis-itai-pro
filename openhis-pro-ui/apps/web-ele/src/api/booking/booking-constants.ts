/** 日程参与者类型（对齐 ScheduleActorKind.sortOrder） */
export const BOOKING_ACTOR_TYPE = {
  PRACTITIONER: 10,
  LOCATION: 20,
  HEALTHCARE_SERVICE: 30,
} as const;

const SCHEDULE_STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  active: '已发布',
  inactive: '已停用',
  retired: '已废弃',
  FROM_TEMPLATE: '占位预览',
};

export function formatScheduleStatus(code?: string) {
  if (!code) return '-';
  return SCHEDULE_STATUS_LABEL[code] ?? code;
}

export function scheduleStatusClass(code?: string) {
  if (code === 'active') return 'is-active';
  if (code === 'draft') return 'is-draft';
  if (code === 'FROM_TEMPLATE') return 'is-template';
  if (code === 'inactive' || code === 'retired') return 'is-inactive';
  return '';
}

/** 本地日历日 → yyyy-MM-dd（避免 toISOString 在 UTC+ 时区偏移一天） */
export function formatLocalDateIso(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 今日本地日期 yyyy-MM-dd */
export function todayLocalDateIso(): string {
  return formatLocalDateIso(new Date());
}

function parseLocalDate(dateStr: string): Date {
  return new Date(`${dateStr}T00:00:00`);
}

/** ISO 日期加减天数 */
export function addDaysIso(dateStr: string, days: number) {
  const date = parseLocalDate(dateStr);
  date.setDate(date.getDate() + days);
  return formatLocalDateIso(date);
}

/** 周一为一周起点 */
export function weekRangeAround(dateStr: string) {
  const date = parseLocalDate(dateStr);
  const day = date.getDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diffToMonday);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return {
    rangeStart: formatLocalDateIso(monday),
    rangeEnd: formatLocalDateIso(sunday),
  };
}

/** 锚点周 + 下一周（共 14 天） */
export function twoWeekRangeAround(dateStr: string) {
  const { rangeStart: anchorMonday, rangeEnd: anchorSunday } = weekRangeAround(dateStr);
  return {
    anchorMonday,
    rangeStart: anchorMonday,
    rangeEnd: addDaysIso(anchorSunday, 7),
  };
}

/** ISO 周次（周一为一周起点，与 weekRangeAround 一致） */
export function isoWeekInfo(dateStr: string) {
  const date = new Date(`${dateStr}T12:00:00`);
  const target = new Date(date);
  const dayNum = target.getDay() || 7;
  target.setDate(target.getDate() + 4 - dayNum);
  const weekYear = target.getFullYear();
  const jan1 = new Date(weekYear, 0, 1);
  const weekNumber = Math.ceil(((target.getTime() - jan1.getTime()) / 86400000 + 1) / 7);
  return { weekYear, weekNumber };
}

export function formatIsoWeekLabel(dateStr: string) {
  const { weekYear, weekNumber } = isoWeekInfo(dateStr);
  return `${weekYear}年第${weekNumber}周`;
}

export function weekRangeLabel(mondayStr: string) {
  const sunday = addDaysIso(mondayStr, 6);
  return `${mondayStr.slice(5)} ~ ${sunday.slice(5)}`;
}

/** 当前日所在自然月 yyyy-MM */
export function currentYearMonth(dateStr = todayLocalDateIso()) {
  return dateStr.slice(0, 7);
}

/** 自然月加减 */
export function shiftYearMonth(yearMonth: string, delta: number) {
  const [yearText, monthText] = yearMonth.split('-');
  const date = new Date(Number(yearText), Number(monthText) - 1 + delta, 1);
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  return `${year}-${month}`;
}

export function monthLabel(yearMonth: string) {
  const [year, month] = yearMonth.split('-');
  return `${year}年${Number(month)}月`;
}

/** 自然月起止 ISO 日期 */
export function monthRangeIso(yearMonth: string) {
  const [yearText, monthText] = yearMonth.split('-');
  const year = Number(yearText);
  const month = Number(monthText);
  const lastDay = new Date(year, month, 0).getDate();
  const dayText = `${lastDay}`.padStart(2, '0');
  return {
    rangeStart: `${yearMonth}-01`,
    rangeEnd: `${yearMonth}-${dayText}`,
  };
}

export function dayLabel(dateStr: string) {
  const labels = ['日', '一', '二', '三', '四', '五', '六'];
  const date = parseLocalDate(dateStr);
  return `${dateStr.slice(5)} 周${labels[date.getDay()]}`;
}

export function toDateTimeStart(dateStr: string) {
  return `${dateStr}T00:00:00`;
}

export function toDateTimeEnd(dateStr: string) {
  return `${dateStr}T23:59:59`;
}
