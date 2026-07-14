import type { SlotTemplateRequest } from '#/api/booking/outpatient-schedule';

function toMs(date: string, time: string) {
  return new Date(`${date}T${time}`).getTime();
}

function formatDateTime(date: string, ms: number) {
  const value = new Date(ms);
  const pad = (n: number) => `${n}`.padStart(2, '0');
  return `${date}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}

/** 预估按起止与间隔可切分的号源段数；slotMinutes 为 0 时整段 1 个 */
export function countSlotsInRange(
  date: string,
  startTime: string,
  endTime: string,
  slotMinutes: number,
): number {
  const startMs = toMs(date, startTime);
  const endMs = toMs(date, endTime);
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
    return 0;
  }
  if (slotMinutes <= 0) {
    return 1;
  }
  const stepMs = slotMinutes * 60_000;
  let count = 0;
  let cursor = startMs;
  while (cursor + stepMs <= endMs) {
    count += 1;
    cursor += stepMs;
  }
  return count;
}

/** 按起止时间与间隔切分时段模板；slotMinutes 为 0 时整段 1 个号源 */
export function buildSlotTemplatesFromRange(
  date: string,
  startTime: string,
  endTime: string,
  slotMinutes: number,
  capacity: number,
): SlotTemplateRequest[] {
  const startMs = toMs(date, startTime);
  const endMs = toMs(date, endTime);
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
    return [];
  }
  if (slotMinutes <= 0) {
    return [buildSingleSlotTemplate(date, startTime, endTime, capacity)];
  }

  const templates: SlotTemplateRequest[] = [];
  const stepMs = slotMinutes * 60_000;
  let cursor = startMs;
  while (cursor + stepMs <= endMs) {
    const slotEnd = cursor + stepMs;
    templates.push({
      startTime: formatDateTime(date, cursor),
      endTime: formatDateTime(date, slotEnd),
      capacity,
    });
    cursor = slotEnd;
  }
  return templates;
}

/** 自定义起止时间 → 单条模板 */
export function buildSingleSlotTemplate(
  date: string,
  startTime: string,
  endTime: string,
  capacity: number,
): SlotTemplateRequest {
  return {
    startTime: `${date}T${startTime}`,
    endTime: `${date}T${endTime}`,
    capacity,
  };
}

export function slotTemplateKey(template: SlotTemplateRequest) {
  return template.startTime;
}

/** 机构级唯一键：organization_id + start_time + end_time */
export function slotWindowKey(template: SlotTemplateRequest): string {
  return `${normalizeDateTimeKey(template.startTime)}|${normalizeDateTimeKey(template.endTime)}`;
}

export function slotWindowKeyFromListItem(slot: {
  startTime?: string;
  endTime?: string;
}): string | undefined {
  if (!slot.startTime || !slot.endTime) {
    return undefined;
  }
  return `${normalizeDateTimeKey(slot.startTime)}|${normalizeDateTimeKey(slot.endTime)}`;
}

/** 统一为 YYYY-MM-DDTHH:mm:ss，便于与库内 timestamp 比较 */
export function normalizeDateTimeKey(value: string): string {
  const trimmed = value.trim();
  const withoutFraction = trimmed.replace(/\.\d+/, '');
  const withoutTz = withoutFraction.replace(/([+-]\d{2}(?::\d{2})?|Z)$/i, '');
  return withoutTz.replace(' ', 'T').slice(0, 19);
}

/** 合并待放号列表（按开始时间去重） */
export function mergePendingTemplates(
  current: SlotTemplateRequest[],
  incoming: SlotTemplateRequest[],
): SlotTemplateRequest[] {
  const keys = new Set(current.map(slotTemplateKey));
  const merged = [...current];
  for (const template of incoming) {
    if (keys.has(slotTemplateKey(template))) {
      continue;
    }
    keys.add(slotTemplateKey(template));
    merged.push(template);
  }
  merged.sort((a, b) => a.startTime.localeCompare(b.startTime));
  return merged;
}

/** 过滤与已有号源重复的时段（按开始时刻，兼容旧逻辑） */
export function filterAgainstExistingStarts(
  templates: SlotTemplateRequest[],
  existingStarts: Set<string>,
): { accepted: SlotTemplateRequest[]; skippedExisting: number } {
  const accepted: SlotTemplateRequest[] = [];
  let skippedExisting = 0;
  for (const template of templates) {
    if (existingStarts.has(template.startTime)) {
      skippedExisting += 1;
      continue;
    }
    accepted.push(template);
  }
  return { accepted, skippedExisting };
}

/** 过滤与同科室已占用时间窗冲突的时段（uk_wkf_slot_org_time_window） */
export function filterAgainstExistingWindows(
  templates: SlotTemplateRequest[],
  occupiedWindows: Set<string>,
): { accepted: SlotTemplateRequest[]; skippedExisting: number } {
  const accepted: SlotTemplateRequest[] = [];
  let skippedExisting = 0;
  for (const template of templates) {
    if (occupiedWindows.has(slotWindowKey(template))) {
      skippedExisting += 1;
      continue;
    }
    accepted.push(template);
  }
  return { accepted, skippedExisting };
}

export function formatTemplateTimeRange(template: SlotTemplateRequest) {
  const start = template.startTime.slice(11, 16);
  const end = template.endTime.slice(11, 16);
  return `${start} - ${end}`;
}
