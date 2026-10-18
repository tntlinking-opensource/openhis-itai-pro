/**
 * 组套套用产物标记（会话级）。
 *
 * <p>通用组套 / 申请单组套套用后，签发前不得再解析用法/诊疗/合管等绑定配置带出耗材
 * （BR-ORD-038 / BR-OTL-APY-005）。本缓存按就诊记住套用落库的 requestId，
 * 刷新同 Tab 仍有效；关闭浏览器后丢失（与「不写 source_template_id」一致）。</p>
 */
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const STORAGE_KEY = 'zxplat.orderTemplateOriginRequestIds.v1';

/** 规范化医嘱 requestId（空串视为无效） */
export function normalizeOrderTemplateOriginRequestId(value: unknown): string {
  return preserveApiIdForRequest(value) ?? String(value ?? '').trim();
}

/** 读取 sessionStorage 中的就诊→requestId 映射 */
function readCache(): Record<string, string[]> {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    const result: Record<string, string[]> = {};
    for (const [encounterId, ids] of Object.entries(parsed as Record<string, unknown>)) {
      const key = String(encounterId ?? '').trim();
      if (!key || !Array.isArray(ids)) {
        continue;
      }
      const normalized = ids
        .map((id) => normalizeOrderTemplateOriginRequestId(id))
        .filter(Boolean);
      if (normalized.length > 0) {
        result[key] = [...new Set(normalized)];
      }
    }
    return result;
  } catch {
    return {};
  }
}

/** 写回 sessionStorage */
function writeCache(cache: Record<string, string[]>): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
  } catch {
    // 隐私模式/配额满时忽略，本会话内存外无法持久
  }
}

/**
 * 记住本就诊下由组套套用落库的医嘱 requestId（可多次追加）。
 *
 * @param encounterId 就诊主键
 * @param requestIds 新建或套用对应的 requestId 列表
 */
export function rememberOrderTemplateOriginRequestIds(
  encounterId: string,
  requestIds: readonly unknown[],
): void {
  const encounterKey = String(encounterId ?? '').trim();
  if (!encounterKey) {
    return;
  }
  const incoming = requestIds
    .map((id) => normalizeOrderTemplateOriginRequestId(id))
    .filter(Boolean);
  if (incoming.length === 0) {
    return;
  }
  const cache = readCache();
  const merged = new Set(cache[encounterKey] ?? []);
  for (const id of incoming) {
    merged.add(id);
  }
  cache[encounterKey] = [...merged];
  writeCache(cache);
}

/**
 * 是否为组套套用产物（签发时跳过绑定带出）。
 *
 * @param encounterId 就诊主键
 * @param requestId 医嘱请求主键
 * @returns 命中缓存则为 true
 */
export function isOrderTemplateOriginRequestId(
  encounterId: string,
  requestId: unknown,
): boolean {
  const encounterKey = String(encounterId ?? '').trim();
  const id = normalizeOrderTemplateOriginRequestId(requestId);
  if (!encounterKey || !id) {
    return false;
  }
  const ids = readCache()[encounterKey] ?? [];
  return ids.includes(id);
}

/**
 * 从医嘱行列表收集有效 requestId（去重保序）。
 *
 * @param rows 含 requestId 的行
 * @returns requestId 列表
 */
export function collectAdviceRequestIdsFromRows(
  rows: ReadonlyArray<{ requestId?: unknown }>,
): string[] {
  const result: string[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    const id = normalizeOrderTemplateOriginRequestId(row.requestId);
    if (!id || seen.has(id)) {
      continue;
    }
    seen.add(id);
    result.push(id);
  }
  return result;
}

/**
 * 套用前后差分：仅保留本次新出现的 requestId。
 *
 * @param beforeRequestIds 套用前已有 requestId
 * @param afterRows 套用并刷新后的医嘱行
 * @returns 新 requestId 列表
 */
export function diffNewAdviceRequestIdsAfterTemplateApply(
  beforeRequestIds: ReadonlySet<string> | readonly string[],
  afterRows: ReadonlyArray<{ requestId?: unknown }>,
): string[] {
  const before =
    beforeRequestIds instanceof Set
      ? beforeRequestIds
      : new Set(
          [...beforeRequestIds]
            .map((id) => normalizeOrderTemplateOriginRequestId(id))
            .filter(Boolean),
        );
  const created: string[] = [];
  for (const id of collectAdviceRequestIdsFromRows(afterRows)) {
    if (before.has(id)) {
      continue;
    }
    created.push(id);
  }
  return created;
}
