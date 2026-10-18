import type { FormItemConfig } from './FormItem.d';

/**
 * 表格列配置类型
 */
export interface TableColumn {
  /** 对齐方式 */
  align?: 'center' | 'left' | 'right';
  /** 表头对齐方式 */
  headerAlign?: 'center' | 'left' | 'right';
  /** 是否固定列 */
  fixed?: 'left' | 'right' | boolean;
  /** 格式化函数 */
  formatter?: (row: Record<string, any>, column: any, cellValue: any, index?: number) => string;
  /** 列标签 */
  label?: string;
  /** 最小宽度 */
  minWidth?: number | string;
  /** 列字段名 */
  prop?: string;
  /** 是否可选（selection 类型） */
  selectable?: (row: Record<string, any>, index: number) => boolean;
  /** 是否显示溢出提示 */
  showOverflowTooltip?: boolean;
  /** 自定义插槽名称 */
  slot?: string;
  /** 是否可排序 */
  sortable?: 'custom' | boolean;
  /** 是否显示树形节点展开按钮（树形表格使用） */
  treeNode?: boolean;
  /** 列类型 */
  type?: 'expand' | 'index' | 'selection';
  /** 列宽度 */
  width?: number | string;
}

/**
 * 树节点数据类型
 */
export interface TreeNodeData {
  [key: string]: any;
  children?: TreeNodeData[];
}

/**
 * TableLayout 组件的 Props 类型
 */
export interface TableLayoutProps {
  /** 是否显示边框 */
  border?: boolean;
  /** 展开行配置 */
  expandConfig?: {
    visibleMethod?: ({ row }: { row: Record<string, any> }) => boolean;
  };
  /** 表单项配置数组 */
  formItems?: FormItemConfig[];
  /** 是否高亮当前行 */
  highlightCurrentRow?: boolean;
  /** 是否所有数据都在客户端，如果是则在组件内部进行分页 */
  isAllData?: boolean;
  /** 加载状态 */
  loading?: boolean;
  /** 最大高度 */
  maxHeight?: number | string;
  /** 查询参数对象 */
  queryParams: {
    [key: string]: any;
    pageNo?: number;
    pageSize?: number;
  };
  /** 行的类名函数 */
  rowClassName?: (params: { row: Record<string, any>; rowIndex: number }) => string;
  /** 单元格合并（VxeGrid spanMethod） */
  spanMethod?: (params: {
    column: Record<string, any>;
    columnIndex: number;
    row: Record<string, any>;
    rowIndex: number;
  }) => { colspan?: number; rowspan?: number } | undefined | void;
  /** 行键 */
  rowKey?: ((row: Record<string, any>) => string) | string;
  /** 是否显示"全部"节点（仅在 showSideQuery 为 true 时有效） */
  showAllNode?: boolean;
  /** 是否显示默认按钮 */
  showDefaultButtons?: boolean;
  /** 是否显示分页 */
  showPagination?: boolean;
  showRefreshButton?: boolean;
  /** 是否显示侧边查询 */
  showSideQuery?: boolean;
  /** 是否显示顶部查询 */
  showTopQuery?: boolean;
  /** 侧边查询加载状态 */
  sideQueryLoading?: boolean;
  /** 侧边查询参数 */
  sideQueryParams?: Record<string, any>;
  /** 侧边栏数据 */
  siderData?: TreeNodeData[];
  /** 侧边栏宽度 */
  sideWidth?: number;
  /** 表格尺寸 */
  size?: 'default' | 'large' | 'small';
  /** 是否显示斑马纹 */
  stripe?: boolean;
  /** 表格列配置 */
  tableColumns?: TableColumn[];
  /**
   * 表格数据变化时是否自动清空勾选。
   * 默认 true；加载后默认全选的页面应设为 false。
   */
  clearSelectionOnDataChange?: boolean;
  /** 表格数据 */
  tableData: Record<string, any>[];
  /** 表格高度 */
  tableHeight?: number | string;
  /** 工具栏配置 */
  toolbarConfig?: {
    [key: string]: any;
    custom?: boolean;
    export?: boolean;
    refresh?: boolean;
    zoom?: boolean;
  };
  /** 总记录数 */
  total?: number;
  /** 是否启用树形表格 */
  tree?: boolean;
  /** 树形表格配置 */
  treeConfig?: {
    accordion?: boolean;
    children?: string;
    expandAll?: boolean;
    indent?: number;
    line?: boolean;
    trigger?: string;
  };
  /** 树节点键名 */
  treeNodeKey?: string;
}
