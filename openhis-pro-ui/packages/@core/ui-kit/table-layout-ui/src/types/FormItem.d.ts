import type { CSSProperties } from 'vue';

/**
 * 表单项选项类型
 */
export interface FormItemOption {
  /** 是否禁用该选项 */
  disabled?: boolean;
  /** 选项标签 */
  label: string;
  /** 选项值 */
  value: boolean | null | number | string | undefined;
}

/**
 * onChange 回调函数类型
 * @param value - 新的值
 * @returns 返回 false 时阻止更新，返回其他值或 undefined 时允许更新
 */
export type OnChangeCallback = (
  value: Array<number | string> | Date | null | number | string | undefined,
) => boolean | Promise<boolean | undefined> | undefined;

/**
 * 表单项配置类型
 */
export interface FormItemConfig {
  /** 是否在变更前进行检查（仅 select 类型，为 true 时会执行 onChange 回调） */
  checkBeforeChange?: boolean;
  /** 是否可清空 */
  clearable?: boolean;
  /** 多选时是否折叠标签（仅 select 类型） */
  collapseTags?: boolean;
  /** 是否禁用 */
  disabled?: boolean;
  /** 结束日期占位符（仅 daterange 类型） */
  endPlaceholder?: string;
  /** 额外的属性（会通过 v-bind 传递给组件） */
  extraprops?: Record<string, any>;
  /** 是否可搜索（仅 select 类型） */
  filterable?: boolean;
  /** 格式化函数（text 类型，用于格式化显示的文本） */
  formatter?: (value: any) => string;
  /** 表单项标签 */
  label: string;
  /** 标签后缀 */
  labelSuffix?: string;
  /** 是否多选（仅 select 类型） */
  multiple?: boolean;
  /** 值变更时的回调函数 */
  onChange?: OnChangeCallback;
  /** 选项列表（select 和 radio 类型） */
  options?: FormItemOption[];
  /** 占位符文本 */
  placeholder?: string;
  /** 表单项字段名（对应 queryParams 的 key） */
  prop: string;
  /** 是否必填 */
  required?: boolean;
  /** 自定义插槽名称（custom 类型） */
  slot?: string;
  /** 开始日期占位符（仅 daterange 类型） */
  startPlaceholder?: string;
  /** 自定义样式对象或字符串 */
  style?: CSSProperties | string | { [key: string]: any; width?: string };
  /** 表单项类型 */
  type: 'custom' | 'date' | 'daterange' | 'input' | 'radio' | 'select' | 'text' | string;
  /** 日期格式（date 和 daterange 类型） */
  valueFormat?: string;
  /** 表单项宽度（字符串，如 '200px'） */
  width?: string;
}

/**
 * FormItem 组件的 Props 类型
 */
export interface FormItemProps {
  /** 表单项配置对象 */
  item: FormItemConfig;
  /** 表单项的值 */
  modelValue?: Array<number | string> | Date | null | number | string;
  /** 回车键按下时的回调函数 */
  onEnter?: () => void;
}
