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
  FROM_TEMPLATE: '模板',
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

/** ISO 日期加减天数 */
export function addDaysIso(dateStr: string, days: number) {
  const date = new Date(`${dateStr}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

/** 周一为一周起点 */
export function weekRangeAround(dateStr: string) {
  const date = new Date(`${dateStr}T00:00:00`);
  const day = date.getDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diffToMonday);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return {
    rangeStart: monday.toISOString().slice(0, 10),
    rangeEnd: sunday.toISOString().slice(0, 10),
  };
}

/** 当前日所在自然月 yyyy-MM */
export function currentYearMonth(dateStr = new Date().toISOString().slice(0, 10)) {
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
  const date = new Date(`${dateStr}T00:00:00`);
  return `${dateStr.slice(5)} 周${labels[date.getDay()]}`;
}

export function toDateTimeStart(dateStr: string) {
  return `${dateStr}T00:00:00`;
}

export function toDateTimeEnd(dateStr: string) {
  return `${dateStr}T23:59:59`;
}
