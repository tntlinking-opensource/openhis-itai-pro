import type { FormItemConfig } from './FormItem.d';

/**
 * Form 组件的 Props 类型
 */
export interface FormProps {
  /** 表单项配置数组 */
  formItems: FormItemConfig[];
  /** 是否内联表单 */
  inline?: boolean;
  /** 标签位置 */
  labelPosition?: 'left' | 'right' | 'top';
  /** 标签宽度 */
  labelWidth?: string;
  /** 表单数据对象 */
  model: Record<string, any>;
  /** 表单验证规则 */
  rules?: Record<string, any>;
  /** 标签后是否添加冒号 */
  showLabelColon?: boolean;
}
