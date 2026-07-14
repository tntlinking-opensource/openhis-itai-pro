/** 日排班 / 门诊草稿共用的号别类型选项与工具 */

export const SCHEDULE_MODE_OPTIONS = [
  { value: 'MODE-A', label: '医师号' },
  { value: 'MODE-B', label: '科室号' },
] as const;

export type ScheduleModeFormValue = (typeof SCHEDULE_MODE_OPTIONS)[number]['value'];

/** 列表/表单展示用，不暴露 MODE-A 等内部码 */
export function scheduleModeLabel(mode?: string): string {
  if (isDepartmentScheduleMode(mode)) {
    return '科室号';
  }
  return '医师号';
}

/** 科室号日排班（MODE-B / MODE-C） */
export function isDepartmentScheduleMode(mode?: string): boolean {
  return mode === 'MODE-B' || mode === 'MODE-C';
}

export function normalizeScheduleModeForForm(mode?: string): ScheduleModeFormValue {
  if (mode === 'MODE-B' || mode === 'MODE-C') {
    return 'MODE-B';
  }
  return 'MODE-A';
}

/** 从草稿/日程 actors 推断号别类型 */
export function inferScheduleModeFromPractitioner(practitionerId?: unknown): ScheduleModeFormValue {
  return practitionerId ? 'MODE-A' : 'MODE-B';
}
