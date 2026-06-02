import type { TableColumn } from './TableLayout.d';

export interface RowVO {
  address: string;
  age: number;
  id: number;
  name: string;
  role: string;
  sex: string;
}
export interface ValidRules {
  [key: string]: { message: string; required: boolean }[];
}
/**
 * EditableTable 组件的 Props 类型
 */
export interface EditableTableProps {
  /** 添加按钮文本 */
  addButtonText?: string;
  /** 是否自动聚焦到新添加的行 */
  autoFocus?: boolean;
  /** 是否显示边框 */
  border?: boolean;
  /** 表格列配置 */
  columns: EditableTableColumn[];
  /** 新增行的默认数据 */
  defaultRowData?: () => Record<string, any>;
  /** 删除按钮文本 */
  deleteButtonText?: string;
  /** 是否禁用 */
  disabled?: boolean;
  /** 表格高度 */
  height?: number | string;
  /** 表格最大高度 */
  maxHeight?: number | string;
  /** 最大行数 */
  maxRows?: number;
  /** 最小行数 */
  minRows?: number;
  /** 表格数据 */
  modelValue: Record<string, any>[];
  /** 行键字段名 */
  rowKey?: string;
  /** 是否显示添加按钮 */
  showAddButton?: boolean;
  /** 是否显示删除按钮 */
  showDeleteButton?: boolean;
  /** 表格尺寸 */
  size?: 'default' | 'large' | 'small';
  /** 是否显示斑马纹 */
  stripe?: boolean;
  /** 验证错误信息，格式：{ [rowKey]: { [field]: boolean } } */
  validationErrors?: Record<string, Record<string, boolean>>;
  /** 验证规则 */
  validRules?: ValidRules;
}

/**
 * EditableTable 列配置
 */
export interface EditableTableColumn extends TableColumn {
  /** 是否可编辑 */
  editable?: boolean;
  /** 编辑时的选项（用于 select 类型），可以是数组或函数（函数接收 row 和 column 参数，返回选项数组） */
  editOptions?:
    | ((
        row: Record<string, any>,
        column: EditableTableColumn,
      ) => Array<{ label: string; value: any }>)
    | Array<{ label: string; value: any }>;
  /** 编辑时的占位符 */
  editPlaceholder?: string;
  /** 编辑时的额外属性 */
  editProps?: Record<string, any>;
  /** 编辑时的渲染函数 */
  editRender?: (row: Record<string, any>, column: EditableTableColumn) => Component | string;
  /** 编辑时的验证规则 */
  editRules?: any[];
  /** 编辑时的样式 */
  editStyle?: Record<string, any>;
  /** 编辑类型：input, select, date, number, textarea, unitQuantity, productSelect 等 */
  editType?: 'custom' | 'date' | 'input' | 'number' | 'select' | 'textarea' | 'unitQuantity';
  /** 格式化时的渲染函数 */
  formatterRender?: (row: Record<string, any>, column: EditableTableColumn) => Component | string;
  /** 单元格值变更时的回调函数 */
  onChange?: (row: Record<string, any>, column: EditableTableColumn, value: any) => void;
  /** 单位数量输入配置（当 editType 为 unitQuantity 时使用） */
  unitQuantityConfig?: {
    /** 是否使用大单位 */
    isUnit: boolean;
    /** 小单位标签 */
    minUnitLabel: string;
    /** 拆零比 */
    partPercent: number;
    /** 大单位标签 */
    unitLabel: string;
  };
}

/**
 * EditableTable 组件暴露的方法
 */
export interface EditableTableExpose {
  /** 添加新行 */
  addRow: (rowData?: Record<string, any>) => void;
  /** 清空表格 */
  clear: () => void;
  /** 获取表格数据 */
  getData: () => Record<string, any>[];
  /** 删除行 */
  removeRow: (row: RowVO) => void;
  /** 设置表格数据 */
  setData: (data: Record<string, any>[]) => void;
  /** 验证表格数据 */
  validate: () => Promise<boolean>;
}
