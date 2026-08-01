import type { TraceScanScenePreset } from './trace-types';

import { TRACE_SCAN_SCENE_PRESETS } from './trace-types';
import { batchResolveTraceIdentityByPreset } from './trace-identity-api';
import { isRemainingCoverageReached, sumBoundRemainingSplit } from './trace-line-scan-count';
import { splitTraceNoCommaString } from './trace-no-string';

/**
 * 按码批量反查主档剩余拆零（供发药半扫确认等消耗侧盖住判定）。
 *
 * @param traceNos 待查追溯码
 * @param preset 反查场景；默认发药 DISPENSE
 * @returns 码 → 剩余拆零；反查失败或缺字段时该码不入表
 */
export async function loadRemainingSplitByTraceNos(
  traceNos: string[],
  preset: TraceScanScenePreset = TRACE_SCAN_SCENE_PRESETS.dispense,
): Promise<Record<string, number>> {
  const unique: string[] = [];
  const seen = new Set<string>();
  // 去空白、去重，避免无效反查
  for (const raw of traceNos) {
    const code = String(raw ?? '').trim();
    if (!code || seen.has(code)) continue;
    seen.add(code);
    unique.push(code);
  }
  const out: Record<string, number> = {};
  if (unique.length === 0) return out;
  try {
    const res = await batchResolveTraceIdentityByPreset(unique, preset);
    for (const item of res.items ?? []) {
      const code = String(item.traceNo ?? '').trim();
      const rem = Number(item.remainingSplitQuantity);
      // 仅缓存有效非负剩余
      if (!code || !Number.isFinite(rem) || rem < 0) continue;
      out[code] = rem;
    }
  } catch {
    // 反查失败：返回空表，调用方回落个数口径
  }
  return out;
}

/**
 * 发药行已扫码是否「不足」（须半扫确认）：优先剩余盖住；缺数据回落个数。
 *
 * @param args.traceRaw 行上逗号码串
 * @param args.operationSplitQty 本行操作拆零
 * @param args.needCount 建议绑码个数（回落）
 * @param args.remainingByTraceNo 主档剩余缓存
 * @returns true=不足须确认；false=未扫或已盖住/个数够
 */
export function isDispenseScannedTraceInsufficient(args: {
  traceRaw: string;
  operationSplitQty: number | undefined;
  needCount: number;
  remainingByTraceNo: Record<string, number>;
}): boolean {
  const raw = String(args.traceRaw ?? '').trim();
  // 完全未扫：放行
  if (!raw || raw === '—') return false;
  const codes = splitTraceNoCommaString(raw);
  if (codes.length === 0) return false;

  const boundSum = sumBoundRemainingSplit(raw, args.remainingByTraceNo);
  const covered = isRemainingCoverageReached(args.operationSplitQty, boundSum);
  // 剩余合计已盖住：合法
  if (covered === true) return false;
  // 有剩余口径且未盖住：不足
  if (covered === false) return true;

  // 缺剩余/拆零数据：回落「建议个数」
  return codes.length < args.needCount;
}
