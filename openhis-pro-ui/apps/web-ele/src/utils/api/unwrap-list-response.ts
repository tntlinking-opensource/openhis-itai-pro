/**
 * 解析非分页列表响应（如退药明细 items、rows）。
 */
export function parseListItemsFromApiData<T = unknown>(data: unknown): T[] {
  if (!data || typeof data !== 'object') {
    return [];
  }
  const body = data as Record<string, unknown>;
  if (Array.isArray(body.items)) {
    return body.items as T[];
  }
  if (Array.isArray(body.rows)) {
    return body.rows as T[];
  }
  if (Array.isArray(body.content)) {
    return body.content as T[];
  }
  const nested = body.data;
  if (nested && typeof nested === 'object') {
    const inner = nested as Record<string, unknown>;
    if (Array.isArray(inner.items)) {
      return inner.items as T[];
    }
    if (Array.isArray(inner.rows)) {
      return inner.rows as T[];
    }
  }
  return [];
}
