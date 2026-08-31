import { isFillableSlotEmptyText, stripFillableZwsp } from '../emr-fillable-json';

export type FillableFieldKind = 'dataElement' | 'freeText' | 'metadata';

/** 方案 B：行内可填域为 atom 节点，值仅存 attrs.value */
export function resolveFillableFieldKind(node: { type: { name: string } }): FillableFieldKind {
  if (node.type.name === 'emrMetadataChip') return 'metadata';
  if (node.type.name === 'emrDataElement') return 'dataElement';
  return 'freeText';
}

/** 节点展示用 class（NodeView 外壳） */
export function fillableNodeExtraClass(
  node: { attrs: Record<string, unknown>; type: { name: string } },
  kind?: FillableFieldKind,
): string {
  const readonly =
    node.attrs.editable === false || node.attrs.editable === '0' ? ' emr-fillable--readonly' : '';
  const k = kind ?? resolveFillableFieldKind(node);
  const required =
    node.attrs.required === true || node.attrs.required === '1' || node.attrs.required === 'true'
      ? ' emr-fillable--required'
      : '';
  const isReadonly = node.attrs.editable === false || node.attrs.editable === '0';
  const metaChip =
    k === 'metadata' ? ` emr-metadata-chip${isReadonly ? ' emr-metadata-chip--readonly' : ''}` : '';
  return `emr-fillable emr-bracket-field emr-bracket-field--css emr-fillable--${k}${readonly}${required}${metaChip}`;
}

/** 空槽：仅看 attrs.value */
export function isFillableAtomValueEmpty(attrs: Record<string, unknown>): boolean {
  return isFillableSlotEmptyText(stripFillableZwsp(String(attrs.value ?? '')));
}

/** 事件是否发生在可填域 contenteditable 岛屿内（含 shell 括号区） */
export function isFillableIslandElement(el: Element | null | undefined): boolean {
  if (!el) return false;
  return Boolean(
    el.closest(
      '.emr-fillable-field__shell, .emr-fillable-field__input, .emr-inline-text__field, select.emr-de__control, input.emr-de__control, textarea.emr-de__control, input.emr-de__checkbox, input.emr-de__radio',
    ),
  );
}

/** 槽内占位文案（仅 contenteditable / 只读空槽 ::empty::before 使用，不重复段落粗体标签） */
export function resolveFillableVisualPlaceholder(
  attrs: Record<string, unknown>,
  surface: 'document' | 'template',
  kind: FillableFieldKind,
  fallback = '填写',
): string {
  const label = String(attrs.label ?? attrs.labelSnapshot ?? '').trim();
  const fieldKey = String(attrs.fieldKey ?? '').trim();
  const tip = String(attrs.placeholder ?? '').trim();

  if (kind === 'freeText') {
    // 自定义占位（与段落粗体标签不同）优先展示
    if (tip && tip !== label) return tip;
    // 占位与 label 同名时不重复字段名，改用通用提示
    return fallback;
  }

  if (kind === 'metadata') {
    return tip || label || fieldKey || fallback;
  }

  if (tip) return tip;
  if (surface === 'template') return '';
  return label || fallback;
}
