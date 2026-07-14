import type { ComputeFrequencyExecutionTimePointsResponse } from '#/api/basic/frequency';

import { computeFrequencyExecutionTimePointsApi } from '#/api/basic/frequency';

export interface ResolveFrequencyExecutionCountParams {
  rateCode: string;
  medicationDays: number;
}

/** 用药计划起点：本地「次日 00:00:00」。 */
function startAtNextLocalDayMidnight(): Date {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** 本地 Date → 后端 LocalDateTime 可解析串（无时区，形如 2026-07-05T00:00:00）。 */
function formatLocalDateTimeForApi(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 由用药天数推导展开窗口 [start, end)（end 为起点 + 天数）。 */
export function buildMedicationFrequencyPlanTimeRange(medicationDays: number): {
  startTime: string;
  endTime: string;
} {
  const start = startAtNextLocalDayMidnight();
  const end = new Date(start);
  end.setDate(end.getDate() + Math.trunc(medicationDays));
  return {
    startTime: formatLocalDateTimeForApi(start),
    endTime: formatLocalDateTimeForApi(end),
  };
}

/** 从频次计算接口响应解析执行次数（兼容未解包 data 与 executionAtList 回退）。 */
export function parseFrequencyExecutionCountFromResponse(res: unknown): number {
  const root = (res ?? {}) as Record<string, unknown>;
  const payload =
    root.executionCount !== undefined || Array.isArray(root.executionAtList)
      ? root
      : ((root.data as Record<string, unknown> | undefined) ?? root);
  const direct = Math.trunc(Number(payload.executionCount ?? 0));
  if (direct >= 1) return direct;
  const list = payload.executionAtList;
  if (Array.isArray(list) && list.length > 0) return list.length;
  return 0;
}

/** 调用频次模块计算执行次数（仅频次/用药天数变更时调用）。 */
export async function resolveFrequencyExecutionCount(
  params: ResolveFrequencyExecutionCountParams,
): Promise<number> {
  const rateCode = String(params.rateCode ?? '').trim();
  const days = Math.trunc(Number(params.medicationDays));
  if (!rateCode || days < 1) return 0;

  const { startTime, endTime } = buildMedicationFrequencyPlanTimeRange(days);
  try {
    const res = await computeFrequencyExecutionTimePointsApi({ rateCode, startTime, endTime });
    return parseFrequencyExecutionCountFromResponse(
      res as ComputeFrequencyExecutionTimePointsResponse,
    );
  } catch {
    return 0;
  }
}
