import type { DispensePrescriptionItemRow } from './types';

/** 与后端约定一致的追溯码长度 */
export const TRACE_CODE_LENGTH = 20;

/** 模拟接口成功体：药品 id + 发药明细行 id + 规范后的追溯码 */
export type TraceResolveOk = {
  drugId: string;
  lineId: string;
  traceCode: string;
};

/** 模拟接口失败原因（对接 HTTP 后映射自响应码即可） */
export type TraceResolveFail = { reason: 'INVALID' | 'NOT_FOUND' };

export type TraceResolveResult = TraceResolveFail | TraceResolveOk;

/**
 * 模拟「追溯码 → 药品」解析接口（仅前端占位）。
 * 命中规则：20 位、非全 0；按码内容稳定哈希落到当前列表中一条明细（对接后删除本实现，改为请求后端）。
 */
export function resolveTraceCodeMock(
  code: string,
  lines: DispensePrescriptionItemRow[],
): TraceResolveResult {
  const t = code.trim();
  if (t.length !== TRACE_CODE_LENGTH) return { reason: 'INVALID' };
  if (lines.length === 0) return { reason: 'NOT_FOUND' };
  if (/^0+$/.test(t)) return { reason: 'NOT_FOUND' };

  let h = 0;
  for (let i = 0; i < t.length; i++) {
    h = Math.imul(31, h) + (t.codePointAt(i) ?? 0);
  }
  const idx = (h >>> 0) % lines.length;
  const row = lines[idx] ?? lines[0]!;
  // drugId 占位与 lineId 区分；对接后使用后端返回的药品主键
  return {
    drugId: `drug:${row.id}`,
    lineId: row.id,
    traceCode: t,
  };
}
