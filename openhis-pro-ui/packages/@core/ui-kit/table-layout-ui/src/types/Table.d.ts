import type { TableColumn } from './TableLayout.d';

/**
 * Table 组件的 Props 类型
 */
export interface TableProps {
  /** 是否显示边框 */
  border?: boolean;
  /** 展开行配置 */
  expandConfig?: {
    visibleMethod?: ({ row }: { row: Record<string, any> }) => boolean;
  };
  /** 是否高亮当前行 */
  highlightCurrentRow?: boolean;
  /** 是否所有数据都在客户端，如果是则在组件内部进行分页 */
  isAllData?: boolean;
  /** 加载状态 */
  loading?: boolean;
  /** 最大高度 */
  maxHeight?: number | string;
  /** 当前页码 */
  pageNo?: number;
  /** 每页条数 */
  pageSize?: number;
  /** 分页左侧自定义文案 */
  paginationLeftText?: string;
  /** 分页组件自定义属性 */
  paginationProps?: {
    autoScroll?: boolean;
    background?: boolean;
    hidden?: boolean;
    layout?: string;
    pagerCount?: number;
    pageSizes?: number[];
  };
  /** 行的类名函数 */
  rowClassName?: (params: { row: Record<string, any>; rowIndex: number }) => string;
  /** 行键 */
  rowKey?: ((row: Record<string, any>) => string) | string;
  /** 是否显示分页 */
  showPagination?: boolean;
  /** 表格尺寸 */
  size?: 'default' | 'large' | 'small';
  /** 是否显示斑马纹 */
  stripe?: boolean;
  /** 表格列配置 */
  tableColumns?: TableColumn[];
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
}
