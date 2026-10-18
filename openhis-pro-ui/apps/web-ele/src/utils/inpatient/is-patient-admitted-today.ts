import dayjs from 'dayjs';

/**
 * 队列行入院时刻：优先 admissionTime，缺省回落 startTime。
 */
export function resolvePatientAdmissionTime(item: {
  admissionTime?: string;
  startTime?: string;
}): string {
  return (item.admissionTime ?? item.startTime ?? '').trim();
}

/**
 * 是否今日入院（按入院日历日，与本地时区一致）。
 * 无有效入院时刻时不算今日入院。
 */
export function isPatientAdmittedToday(item: {
  admissionTime?: string;
  startTime?: string;
}): boolean {
  const raw = resolvePatientAdmissionTime(item);
  if (!raw) {
    return false;
  }
  const parsed = dayjs(raw);
  if (!parsed.isValid()) {
    return false;
  }
  return parsed.format('YYYY-MM-DD') === dayjs().format('YYYY-MM-DD');
}
