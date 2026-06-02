import type { FormItemConfig } from './FormItem.d';

/**
 * Filter 组件的 Props 类型
 */
export interface FilterProps {
  /** 表单项配置数组 */
  formItems: FormItemConfig[];
  /** 标签宽度 */
  labelWidth?: string;
  /** 查询参数对象 */
  queryParams: Record<string, any>;
  /** 是否显示 */
  show?: boolean;
  /** 是否显示默认按钮 */
  showDefaultButtons?: boolean;
  /** 标签后是否添加冒号 */
  showLabelColon?: boolean;
}

/**
 * Filter 组件暴露的方法
 */
export interface FilterExpose {
  handleQuery: () => void;
  queryFormRef: InstanceType<typeof import('element-plus').ElForm> | null;
  resetQuery: () => void;
}
