/** 内联数据元控件类型（与目录 `DataElementDef.widget` 对齐） */
export type EmrDataElementWidget = 'checkbox' | 'multiline' | 'number' | 'select' | 'text';

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

export const EMR_DATA_ELEMENT_WIDGETS: readonly EmrDataElementWidget[] = [
  'text',
  'multiline',
  'select',
  'number',
  'checkbox',
] as const;

export function isEmrDataElementWidget(s: string): s is EmrDataElementWidget {
  return (EMR_DATA_ELEMENT_WIDGETS as readonly string[]).includes(s);
}
