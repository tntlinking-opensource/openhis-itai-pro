/**
 * 对账/清算页「清算机构」下拉展示：去掉 busNo 中 H…- 前缀段，只保留「-」后编码。
 * 例：H22010402403-229900 → 229900
 */

/** 从契约 busNo 提取清算机构展示编码 */
export function formatClearOrgCode(busNo?: string | null): string {
  const raw = (busNo ?? '').trim();
  if (!raw) return '';
  // 去掉「H…-」整段，只保留横线后的编号
  const dashIdx = raw.indexOf('-');
  if (dashIdx >= 0) {
    return raw.slice(dashIdx + 1).trim();
  }
  return raw;
}

/** 清算机构下拉选项文案：名称 + 展示编码 */
export function formatClearOrgOptionLabel(label?: string | null, busNo?: string | null): string {
  const name = (label ?? '').trim();
  const code = formatClearOrgCode(busNo);
  if (name && code) return `${name}（${code}）`;
  return name || code || '—';
}
