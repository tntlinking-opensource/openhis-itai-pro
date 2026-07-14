import type { SlotTemplateRequest } from '#/api/booking/outpatient-schedule';
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';

/** 规范化 HH:mm 或 HH:mm:ss */
export function normalizeSlotClockTime(time: string): string {
  const trimmed = time.trim();
  if (trimmed.length <= 5) {
    return `${trimmed}:00`;
  }
  return trimmed;
}

/** 放号时段行 → 指定日期的待放号请求 */
export function slotRowsToTemplateRequests(
  rows: SlotProvisionTemplateItemDto[],
  date: string,
): SlotTemplateRequest[] {
  const result: SlotTemplateRequest[] = [];
  for (const item of rows) {
    if (!item.startTime?.trim() || !item.endTime?.trim()) {
      continue;
    }
    const start = normalizeSlotClockTime(item.startTime);
    const end = normalizeSlotClockTime(item.endTime);
    result.push({
      startTime: `${date}T${start}`,
      endTime: `${date}T${end}`,
      capacity: item.capacity ?? 1,
    });
  }
  return result;
}

export function createDefaultSlotRow(): SlotProvisionTemplateItemDto {
  return { startTime: '08:00', endTime: '12:00', capacity: 10 };
}
