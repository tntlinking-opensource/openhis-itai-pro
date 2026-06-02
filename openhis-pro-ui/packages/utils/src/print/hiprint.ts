/**
 * Hiprint 打印工具封装
 * @description 提供统一的打印接口和方法
 */

import type { PrintOptions, PrintPanel, PrintTemplate } from './types';

/**
 * Hiprint 实例类型
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

// 扩展 Window 接口以支持 hiprint
declare global {
  interface Window {
    hiprint?: HiprintInstance;
    $hiprint?: {
      [key: string]: any;
      hiprint: HiprintInstance;
    };
  }
}

/**
 * 获取 hiprint 实例
 * @description 从 vue-plugin-hiprint 获取 hiprint 实例
 */
export function getHiprint(): HiprintInstance | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const hiprint = window.hiprint || window.$hiprint?.hiprint;
    return hiprint || null;
  } catch (error) {
    console.warn('获取 Hiprint 实例失败:', error);
    return null;
  }
}

/**
 * 初始化 Hiprint
 * @description 初始化 hiprint（如果存在 init 方法）
 */
export function initHiprint(): boolean {
  const hiprint = getHiprint();
  if (!hiprint) {
    console.warn('Hiprint 未加载，请确保已正确注册 vue-plugin-hiprint 插件');
    return false;
  }

  try {
    if (typeof hiprint.init === 'function') {
      hiprint.init();
    }
    return true;
  } catch (error) {
    console.error('Hiprint 初始化失败:', error);
    return false;
  }
}

/**
 * 创建打印模板
 * @param templateJson 模板 JSON 数据
 * @param options 模板选项
 */
export function createPrintTemplate(
  templateJson?: Record<string, any>,
  options?: Record<string, any>,
): null | PrintTemplate {
  const hiprint = getHiprint();
  if (!hiprint) {
    console.warn('Hiprint 未加载');
    return null;
  }

  try {
    const templateOptions = templateJson ? { template: templateJson, ...options } : options;
    return new hiprint.PrintTemplate(templateOptions);
  } catch (error) {
    console.error('创建打印模板失败:', error);
    return null;
  }
}

/**
 * 创建空白打印模板
 * @param options 模板选项
 * @param options.height 高度
 * @param options.paperFooter 页脚
 * @param options.paperHeader 页眉
 * @param options.width 宽度
 */
export function createBlankTemplate(options?: {
  height?: number;
  paperFooter?: number;
  paperHeader?: number;
  width?: number;
}): null | PrintTemplate {
  const hiprint = getHiprint();
  if (!hiprint) {
    console.warn('Hiprint 未加载');
    return null;
  }

  try {
    const template = new hiprint.PrintTemplate();
    const defaultOptions = {
      width: 210,
      height: 297,
      paperFooter: 340,
      paperHeader: 10,
      ...options,
    };
    template.addPrintPanel(defaultOptions);
    return template;
  } catch (error) {
    console.error('创建空白模板失败:', error);
    return null;
  }
}

/**
 * 执行打印
 * @param template 打印模板
 * @param data 打印数据
 * @param options 打印选项
 */
export function print(
  template: null | PrintTemplate,
  data?: Record<string, any>,
  options?: PrintOptions,
): void {
  if (!template) {
    console.warn('打印模板不存在');
    return;
  }

  try {
    template.print(data || {}, {
      printer: options?.printer || '',
      title: options?.title || '打印',
      ...options,
    });
  } catch (error) {
    console.error('打印失败:', error);
  }
}

/**
 * 预览打印
 * @param template 打印模板
 * @param data 打印数据
 */
export function previewPrint(template: null | PrintTemplate, data?: Record<string, any>): void {
  if (!template) {
    console.warn('打印模板不存在');
    return;
  }

  try {
    template.preview(data || {});
  } catch (error) {
    console.error('预览失败:', error);
  }
}

/**
 * 获取打印模板 JSON
 * @param template 打印模板
 */
export function getTemplateJson(template: null | PrintTemplate): null | string {
  if (!template) {
    return null;
  }

  try {
    return JSON.stringify(template.getTemplate());
  } catch (error) {
    console.error('获取模板 JSON 失败:', error);
    return null;
  }
}

/**
 * 从 JSON 加载模板
 * @param templateJson 模板 JSON 字符串或对象
 */
export function loadTemplateFromJson(
  templateJson: Record<string, any> | string,
): null | PrintTemplate {
  const hiprint = getHiprint();
  if (!hiprint) {
    console.warn('Hiprint 未加载');
    return null;
  }

  try {
    const json = typeof templateJson === 'string' ? JSON.parse(templateJson) : templateJson;
    return new hiprint.PrintTemplate({ template: json });
  } catch (error) {
    console.error('加载模板失败:', error);
    return null;
  }
}

/**
 * 添加打印面板
 * @param template 打印模板
 * @param options 面板选项
 * @param options.height 高度
 * @param options.paperFooter 页脚
 * @param options.paperHeader 页眉
 * @param options.width 宽度
 */
export function addPrintPanel(
  template: null | PrintTemplate,
  options?: {
    height?: number;
    paperFooter?: number;
    paperHeader?: number;
    width?: number;
  },
): null | PrintPanel {
  if (!template) {
    console.warn('打印模板不存在');
    return null;
  }

  try {
    const defaultOptions = {
      width: 210,
      height: 297,
      paperFooter: 340,
      paperHeader: 10,
      ...options,
    };
    return template.addPrintPanel(defaultOptions);
  } catch (error) {
    console.error('添加打印面板失败:', error);
    return null;
  }
}

/**
 * 添加 HTML 元素到打印面板
 * @param panel 打印面板
 * @param html HTML 内容或元素
 * @param options 元素选项
 * @param options.height 高度
 * @param options.left 左边距
 * @param options.top 上边距
 * @param options.width 宽度
 */
export function addPrintHtmlElement(
  panel: null | PrintPanel,
  html: HTMLElement | string,
  options?: {
    [key: string]: any;
    height?: number;
    left?: number;
    top?: number;
    width?: number;
  },
): void {
  if (!panel) {
    console.warn('打印面板不存在');
    return;
  }

  try {
    const htmlContent = typeof html === 'string' ? html : html.innerHTML;
    panel.addPrintHtmlElement({
      html: htmlContent,
      options: {
        width: 190,
        height: 277,
        top: 10,
        left: 10,
        ...options,
      },
    });
  } catch (error) {
    console.error('添加 HTML 元素失败:', error);
  }
}

/**
 * 添加文本元素到打印面板
 * @param panel 打印面板
 * @param text 文本内容
 * @param options 元素选项
 * @param options.fontSize 字号
 * @param options.fontWeight 字重
 * @param options.height 高度
 * @param options.left 左边距
 * @param options.textAlign 对齐
 * @param options.top 上边距
 * @param options.width 宽度
 */
export function addPrintTextElement(
  panel: null | PrintPanel,
  text: string,
  options?: {
    [key: string]: any;
    fontSize?: number;
    fontWeight?: string;
    height?: number;
    left?: number;
    textAlign?: 'center' | 'left' | 'right';
    top?: number;
    width?: number;
  },
): void {
  if (!panel) {
    console.warn('打印面板不存在');
    return;
  }

  try {
    panel.addPrintTextElement({
      text,
      options: {
        width: 100,
        height: 20,
        top: 10,
        left: 10,
        fontSize: 14,
        fontWeight: 'normal',
        textAlign: 'left',
        ...options,
      },
    });
  } catch (error) {
    console.error('添加文本元素失败:', error);
  }
}

/**
 * 添加表格元素到打印面板
 * @param panel 打印面板
 * @param tableData 表格数据
 * @param columns 列配置
 * @param options 元素选项
 * @param options.height 高度
 * @param options.left 左边距
 * @param options.top 上边距
 * @param options.width 宽度
 */
export function addPrintTableElement(
  panel: null | PrintPanel,
  tableData: Record<string, any>[],
  columns: Array<{ field: string; title: string; width?: number }>,
  options?: {
    [key: string]: any;
    height?: number;
    left?: number;
    top?: number;
    width?: number;
  },
): void {
  if (!panel) {
    console.warn('打印面板不存在');
    return;
  }

  try {
    panel.addPrintTableElement({
      tableData,
      columns,
      options: {
        width: 190,
        height: 200,
        top: 10,
        left: 10,
        ...options,
      },
    });
  } catch (error) {
    console.error('添加表格元素失败:', error);
  }
}
