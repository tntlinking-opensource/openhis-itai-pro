/** 事前提醒试调页常量（trig_scen / 处置方式） */

export const DETAIL_AUDIT_TRIG_SCEN_OPTIONS = [
  { value: '1', label: '1 门诊挂号' },
  { value: '2', label: '2 门诊收费登记' },
  { value: '3', label: '3 住院登记' },
  { value: '4', label: '4 住院收费登记' },
  { value: '5', label: '5 住院执行医嘱' },
  { value: '6', label: '6 门诊结算' },
  { value: '7', label: '7 门诊预结算' },
  { value: '8', label: '8 住院结算' },
  { value: '9', label: '9 住院预结算' },
  { value: '10', label: '10 购药划卡' },
] as const;

export const DETAIL_AUDIT_WARN_TYPE_OPTIONS = [
  { value: '1', label: '1 事前' },
  { value: '2', label: '2 事中' },
] as const;

export const DETAIL_AUDIT_DSPO_WAY_OPTIONS = [
  { value: '1', label: '1 继续执行' },
  { value: '2', label: '2 返回修改' },
] as const;

/** 严重程度展示 */
export function formatSevDeg(code?: string | null): string {
  if (code === '1') {
    return '明确违规';
  }
  if (code === '2') {
    return '高度可疑';
  }
  if (code === '3') {
    return '轻度可疑';
  }
  if (!code) {
    return '—';
  }
  return code;
}
