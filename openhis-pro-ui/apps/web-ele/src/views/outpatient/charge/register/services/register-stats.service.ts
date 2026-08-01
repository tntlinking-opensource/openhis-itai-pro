import type { GetTodayRegisterSummaryResult } from '#/api/outpatient-registration';

import { httpGetTodayRegisterSummary } from '#/api/outpatient-registration';

/** 拉取当日挂号统计（不含 PLANNED / ENTERED_IN_ERROR；已退仅 CANCELLED）。 */
export async function loadTodayRegisterSummary(
  date?: string,
): Promise<GetTodayRegisterSummaryResult> {
  return httpGetTodayRegisterSummary(date ? { date } : {});
}
