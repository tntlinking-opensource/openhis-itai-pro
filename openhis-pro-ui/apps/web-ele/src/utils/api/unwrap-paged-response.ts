/**
 * 解析后端统一分页块（与 AbstractPageQuery / Page 返回对齐）。
 *
 * 支持：
 * - 标准：`{ content: T[], totalCount, pageNo?, pageSize? }`（`totalCount` 可为数字字符串）
 * - 旧版：`{ records: T[], total | totalElements }`
 * - 部分应用：`{ rows: T[], total | totalCount | totalElements }`（门诊发药/医嘱目录等）
 * - 外层多包一层：`{ content: { content: T[], totalCount, ... } }`（业务 `data` 下再挂 `content`）
 */
export interface ParsedPagedList<T = unknown> {
  content: T[];
  totalCount: number;
  pageNo?: number;
  pageSize?: number;
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** 若 `body.content` 是「分页对象」而非行数组，则剥至内层分页对象 */
function peelOuterContentWrapper(body: Record<string, unknown>): Record<string, unknown> {
  const wrapped = body.content;
  if (!isRecord(wrapped)) return body;
  if (
    Array.isArray(wrapped.content) ||
    Array.isArray(wrapped.records) ||
    Array.isArray(wrapped.rows)
  ) {
    return wrapped;
  }
  return body;
}

export function parsePagedRowsFromApiData<T = unknown>(data: unknown): ParsedPagedList<T> {
  if (!isRecord(data)) {
    return { content: [], totalCount: 0 };
  }
  const page = peelOuterContentWrapper(data);
  const content =
    (Array.isArray(page.content) ? page.content : undefined) ??
    (Array.isArray(page.records) ? page.records : undefined) ??
    (Array.isArray(page.rows) ? page.rows : undefined) ??
    [];
  const rawTotal = page.totalCount ?? page.totalElements ?? page.total ?? 0;
  return {
    content: content as T[],
    totalCount: Number(rawTotal) || 0,
    pageNo: typeof page.pageNo === 'number' ? page.pageNo : undefined,
    pageSize: typeof page.pageSize === 'number' ? page.pageSize : undefined,
  };
}
