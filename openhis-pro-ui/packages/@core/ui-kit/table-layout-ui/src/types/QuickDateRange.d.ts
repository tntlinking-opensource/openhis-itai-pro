/**
 * QuickDateRange 组件的 Props 类型
 */
export interface QuickDateRangeProps {
  /** 透传给日期选择器的其他属性 */
  attrs?: Record<string, any>;
  /** 是否可清空 */
  clearable?: boolean;
  /** 日期选择器的样式 */
  datePickerStyle?: Record<string, any>;
  /** 结束日期占位符 */
  endPlaceholder?: string;
  /** 日期范围值（字符串数组，如 ['2024-01-01', '2024-01-31']） */
  modelValue?: string[];
  /** 开始日期占位符 */
  startPlaceholder?: string;
  /** 日期格式 */
  valueFormat?: string;
}
