import type { EmrWidgetOptionItem } from './data-element-types';

/** 常用本地预设（不连系统字典，仅填入弹窗） */
export const EMR_WIDGET_OPTION_PRESETS: ReadonlyArray<{
  key: string;
  name: string;
  items: EmrWidgetOptionItem[];
}> = [
  {
    key: 'yesNo',
    name: '是否',
    items: [
      { value: 'Y', label: '是' },
      { value: 'N', label: '否' },
    ],
  },
  {
    key: 'haveNone',
    name: '有无',
    items: [
      { value: 'HAVE', label: '有' },
      { value: 'NONE', label: '无' },
    ],
  },
  {
    key: 'gender',
    name: '男女',
    items: [
      { value: 'M', label: '男' },
      { value: 'F', label: '女' },
    ],
  },
  {
    key: 'posNeg',
    name: '阴阳',
    items: [
      { value: 'POS', label: '阳性' },
      { value: 'NEG', label: '阴性' },
    ],
  },
];

/** 解析节点 attrs.optionsJson */
export function parseEmrWidgetOptions(json: string): EmrWidgetOptionItem[] {
  try {
    const raw = JSON.parse(json || '[]');
    if (!Array.isArray(raw)) return [];
    return raw
      .map((item: unknown) => {
        if (typeof item === 'string') {
          const t = item.trim();
          return t ? { value: t, label: t } : null;
        }
        if (item && typeof item === 'object') {
          const o = item as Record<string, unknown>;
          const value = String(o.value ?? o.code ?? '').trim();
          const label = String(o.label ?? o.value ?? o.code ?? '').trim();
          if (!value && !label) return null;
          return { value: value || label, label: label || value };
        }
        return null;
      })
      .filter((x): x is EmrWidgetOptionItem => !!x);
  } catch {
    return [];
  }
}

/** 序列化为 attrs.optionsJson */
export function serializeEmrWidgetOptions(items: EmrWidgetOptionItem[]): string {
  const cleaned = items
    .map((it) => ({
      value: String(it.value ?? '').trim(),
      label: String(it.label ?? '').trim(),
    }))
    .filter((it) => it.value || it.label)
    .map((it) => ({
      value: it.value || it.label,
      label: it.label || it.value,
    }));
  return JSON.stringify(cleaned);
}

/** 按存储值取展示文案 */
export function resolveEmrWidgetOptionLabel(options: EmrWidgetOptionItem[], value: string): string {
  const v = String(value ?? '');
  if (!v) return '';
  const hit = options.find((o) => o.value === v);
  return hit?.label ?? v;
}
