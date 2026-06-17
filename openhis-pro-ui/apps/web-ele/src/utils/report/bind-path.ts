/** 从样例 root 按 bindPath 取值，供设计器 overlay 落格框预览文案。 */
export function resolveBindPath(
  root: Record<string, unknown> | undefined,
  bindPath?: string | null,
): unknown {
  if (!root || !bindPath?.trim()) {
    return undefined;
  }
  let current: unknown = root;
  for (const segment of bindPath.split('.')) {
    if (current == null || typeof current !== 'object' || Array.isArray(current)) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[segment];
  }
  return current;
}

/** 与后端 PrintTextFormat.formatLabelValue 一致 */
export function formatLabelValue(label?: string | null, valueText?: string | null): string {
  const labelText = label?.trim() ?? '';
  const value = valueText?.trim() ?? '';
  if (!value) {
    return labelText;
  }
  if (!labelText) {
    return value;
  }
  if (labelText.endsWith('：') || labelText.endsWith(':')) {
    return `${labelText}${value}`;
  }
  if (labelText.includes('：') || labelText.includes(':')) {
    return `${labelText}${value}`;
  }
  return `${labelText}：${value}`;
}

export function formatSamplePreviewText(value: unknown, renderType?: string, maxLen = 48): string {
  if (value == null || value === '') {
    return '';
  }
  if (Array.isArray(value)) {
    const label = renderType === 'table' ? `明细 ${value.length} 行` : `[${value.length}]`;
    return truncate(label, maxLen);
  }
  if (typeof value === 'object') {
    return '';
  }
  return truncate(String(value), maxLen);
}

export function formatOverlayFieldPreviewText(options: {
  label?: string | null;
  labelOverride?: string | null;
  defaultLabel?: string | null;
  bindPath?: string | null;
  renderType?: string | null;
  sampleRoot?: Record<string, unknown> | null;
}): string {
  const renderType = options.renderType ?? '';
  if (renderType === 'qrcode' || renderType === 'table') {
    return formatSamplePreviewText(
      resolveBindPath(options.sampleRoot ?? undefined, options.bindPath),
      renderType,
    );
  }
  if (renderType === 'staticTitle') {
    const staticText =
      options.labelOverride?.trim() || options.defaultLabel?.trim() || options.label?.trim() || '';
    return truncate(staticText, 48);
  }
  const label =
    options.labelOverride?.trim() || options.label?.trim() || options.defaultLabel?.trim() || '';
  const raw = resolveBindPath(options.sampleRoot ?? undefined, options.bindPath);
  const valueText = raw == null || raw === '' ? '' : String(raw);
  return truncate(formatLabelValue(label, valueText), 48);
}

function truncate(text: string, maxLen: number): string {
  if (text.length <= maxLen) {
    return text;
  }
  return `${text.slice(0, maxLen - 1)}…`;
}
