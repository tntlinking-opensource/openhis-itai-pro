/** 术语/字典下拉选项（与后端 OptionDto 一致） */
export interface TerminologyOptionItem {
  disabled?: boolean;
  label: string;
  value: string;
}

/** 将接口 OptionDto 列表规范化为下拉选项 */
export function normalizeTerminologyOptions(
  options: TerminologyOptionItem[] | null | undefined,
): TerminologyOptionItem[] {
  if (!options?.length) {
    return [];
  }
  return options
    .map((item) => ({
      label: String(item.label ?? '').trim(),
      value: String(item.value ?? '').trim(),
      disabled: item.disabled,
    }))
    .filter((item) => item.label && item.value);
}

/**
 * 术语码 → 展示文案（与医嘱模块 terminologyLabel 同构，抽到 utils 供住院等模块复用）。
 *
 * @param code 持久化术语码
 * @param options 字典下拉（adm_cond_code 等）
 * @param emptyFallback 空 code 时的占位；默认「—」
 */
export function resolveTerminologyOptionLabel(
  code: string | null | undefined,
  options: TerminologyOptionItem[] | null | undefined,
  emptyFallback = '—',
): string {
  const normalized = String(code ?? '').trim();
  if (!normalized) {
    return emptyFallback;
  }
  const hit = (options ?? []).find((item) => String(item.value ?? '').trim() === normalized);
  const label = String(hit?.label ?? '').trim();
  return label || normalized;
}
