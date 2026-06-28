import type { GetTodayRegisterSummaryResult } from '#/api/outpatient-registration';

import { httpGetTodayRegisterSummary } from '#/api/outpatient-registration';

/** 拉取当日挂号统计（不含待收费 PLANNED）。 */
export async function loadTodayRegisterSummary(
  date?: string,
): Promise<GetTodayRegisterSummaryResult> {
  return httpGetTodayRegisterSummary(date ? { date } : {});
}
