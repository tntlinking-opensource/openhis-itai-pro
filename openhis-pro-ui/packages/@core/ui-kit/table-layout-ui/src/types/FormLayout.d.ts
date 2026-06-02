import type { FormItemConfig } from './FormItem.d';

/**
 * FormLayout 组件的 Props 类型
 */
export interface FormLayoutProps {
  /** 列数（0 表示不限制） */
  columns?: number;
  /** 表单项配置数组 */
  formItems: FormItemConfig[];
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

/**
 * FormLayout 组件暴露的方法
 */
export interface FormLayoutExpose {
  clearValidate: (props?: string | string[]) => void;
  formRef: InstanceType<typeof import('element-plus').ElForm> | null;
  resetFields: () => void;
  scrollToField: (prop: string) => void;
  validate: (callback?: (valid: boolean) => void) => Promise<void> | void;
  validateField: (
    props: string | string[],
    callback?: (valid: boolean) => void,
  ) => Promise<void> | void;
}
