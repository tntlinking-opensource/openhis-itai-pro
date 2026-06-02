/**
 * Element Plus `ElTag` 的 `type`，用于状态列等配色。
 */
export type ElTagVisualType = 'danger' | 'info' | 'success' | 'warning';

/**
 * 按业务状态码从映射表取 `ElTag` 的 `type`；未命中时返回 `fallback`。
 *
 * @param code 状态码（会与 `String(code).trim()` 后查表）
 * @param map 码 → tag type，由各业务页面或常量模块维护
 * @param fallback 缺省或未配置时的展示类型，默认 `info`
 */
export function tagTypeByCode(
  code: null | string | undefined,
  map: Readonly<Record<string, ElTagVisualType>>,
  fallback: ElTagVisualType = 'info',
): ElTagVisualType {
  const key = String(code ?? '').trim();
  return map[key] ?? fallback;
}
