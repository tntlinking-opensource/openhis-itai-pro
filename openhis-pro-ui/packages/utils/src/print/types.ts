/**
 * Hiprint 类型定义
 */

/**
 * Hiprint 实例接口
 */
export interface HiprintInstance {
  /**
   * 打印模板构造函数
   */
  PrintTemplate: new (options?: {
    [key: string]: any;
    template?: Record<string, any>;
  }) => PrintTemplate;
  /**
   * 初始化方法（可选）
   */
  init?: () => void;
  [key: string]: any;
}

/**
 * 打印模板接口
 */
export interface PrintTemplate {
  /**
   * 添加打印面板
   */
  addPrintPanel: (options: PrintPanelOptions) => PrintPanel;
  /**
   * 打印
   */
  print: (data?: Record<string, any>, options?: PrintOptions) => void;
  /**
   * 预览
   */
  preview: (data?: Record<string, any>) => void;
  /**
   * 获取模板 JSON
   */
  getTemplate: () => Record<string, any>;
  /**
   * 更新模板
   */
  update?: (templateJson: Record<string, any>) => void;
  /**
   * 获取面板列表（可选）
   */
  getPanels?: () => PrintPanel[];
  /**
   * 面板列表（可选）
   */
  panels?: PrintPanel[];
  [key: string]: any;
}

/**
 * 打印面板接口
 */
export interface PrintPanel {
  /**
   * 添加 HTML 元素
   */
  addPrintHtmlElement: (config: { html: string; options: Record<string, any> }) => void;
  /**
   * 添加文本元素
   */
  addPrintTextElement: (config: { options: Record<string, any>; text: string }) => void;
  /**
   * 添加表格元素
   */
  addPrintTableElement: (config: {
    columns: Array<{ field: string; title: string; width?: number }>;
    options: Record<string, any>;
    tableData: Record<string, any>[];
  }) => void;
  [key: string]: any;
}

/**
 * 打印面板选项
 */
export interface PrintPanelOptions {
  /** 宽度（mm） */
  width?: number;
  /** 高度（mm） */
  height?: number;
  /** 页脚高度 */
  paperFooter?: number;
  /** 页眉高度 */
  paperHeader?: number;
  [key: string]: any;
}

/**
 * 打印选项
 */
export interface PrintOptions {
  /** 打印机名称 */
  printer?: string;
  /** 打印标题 */
  title?: string;
  /** 是否预览 */
  preview?: boolean;
  /** 打印份数 */
  copies?: number;
  [key: string]: any;
}

/**
 * 打印数据接口
 */
export interface PrintData {
  [key: string]: any;
}

/**
 * PDF 打印选项
 */
export interface PdfPrintOptions {
  /** 打印标题 */
  title?: string;
  /** 是否在新窗口打开 */
  newWindow?: boolean;
  /** 是否自动打印 */
  autoPrint?: boolean;
}
