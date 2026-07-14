import { isTraceSlotEmpty } from './trace-code-parse';

/** 逗号串拆分为追溯码列表（去空白） */
export function splitTraceNoCommaString(raw: string | undefined): string[] {
  const t = (raw ?? '').trim();
  if (isTraceSlotEmpty(t)) return [];
  return t
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '');
}

/** 判断逗号串中是否已包含指定码 */
export function traceNoCommaContains(raw: string | undefined, code: string): boolean {
  const target = code.trim();
  if (!target) return false;
  return splitTraceNoCommaString(raw).includes(target);
}

/** 单码绑定：直接覆盖 */
export function bindTraceNoSingle(_current: string | undefined, next: string): string {
  return next.trim();
}

/** 逗号串绑定：去重追加 */
export function bindTraceNoCommaAppend(current: string | undefined, next: string): string {
  const code = next.trim();
  if (!code) return (current ?? '').trim();
  const parts = splitTraceNoCommaString(current);
  if (parts.includes(code)) return parts.join(',');
  parts.push(code);
  return parts.join(',');
}

/** 按绑定模式写入追溯码 */
export function applyTraceBind(
  current: string | undefined,
  next: string,
  mode: 'comma' | 'single',
): string {
  return mode === 'single'
    ? bindTraceNoSingle(current, next)
    : bindTraceNoCommaAppend(current, next);
}
