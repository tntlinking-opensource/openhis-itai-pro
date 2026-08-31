/** 内联数据元控件类型（与目录 `DataElementDef.widget` 对齐） */
export type EmrDataElementWidget =
  | 'checkbox'
  | 'date'
  | 'multiline'
  | 'number'
  | 'radio'
  | 'select'
  | 'text';

/** 数据元目录项（文档外注册表）；文档节点只存 elementId + value 等 */
export interface DataElementDef {
  id: string;
  name: string;
  widget: EmrDataElementWidget;
  required?: boolean;
  /** 下拉等选项：code 为存储值，label 为展示 */
  valueSet?: { code: string; label: string }[];
  defaultValue?: string;
}

/** LOCAL JSON 选项项（存 attrs.optionsJson） */
export interface EmrWidgetOptionItem {
  /** 存储值（code） */
  value: string;
  /** 展示文案 */
  label: string;
}

export const EMR_DATA_ELEMENT_WIDGETS: readonly EmrDataElementWidget[] = [
  'text',
  'multiline',
  'select',
  'radio',
  'number',
  'date',
  'checkbox',
] as const;

export function isEmrDataElementWidget(s: string): s is EmrDataElementWidget {
  return (EMR_DATA_ELEMENT_WIDGETS as readonly string[]).includes(s);
}

/** 可作为连续正文进光标的数据元（下拉/单选/日期/勾选仍是控件岛） */
export function isTextLikeEmrDataElementWidget(widget: string): boolean {
  return widget === 'text' || widget === 'multiline' || widget === 'number';
}

/** 插入时需配置选项列表的控件 */
export function isOptionListWidget(widget: string): boolean {
  return widget === 'select' || widget === 'radio';
}
