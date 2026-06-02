/**
 * Hiprint 打印 Composable
 * @description 提供便捷的打印功能封装（仅用于 JSON 模板打印）
 */

import type { Ref } from 'vue';

import type {
  PrintData,
  PrintOptions,
  PrintPanel,
  PrintPanelOptions,
  PrintTemplate,
} from '@vben/utils';

import { computed, ref } from 'vue';

import {
  print as doPrint,
  getTemplateJson,
  loadTemplateFromJson,
  previewPrint as preview,
} from '@vben/utils';

/**
 * Hiprint 打印 Composable 返回值
 */
export interface UseHiprintReturn {
  /** 当前打印模板 */
  template: Ref<null | PrintTemplate>;
  /** 当前打印面板 */
  panel: Ref<null | PrintPanel>;
  /** 是否已初始化 */
  isInitialized: Ref<boolean>;
  /** 创建空白模板 */
  createBlank: (options?: PrintPanelOptions) => null | PrintTemplate;
  /** 从 JSON 加载模板 */
  loadFromJson: (templateJson: Record<string, any> | string) => null | PrintTemplate;
  /** 添加打印面板 */
  addPanel: (options?: PrintPanelOptions) => null | PrintPanel;
  /** 添加 HTML 元素 */
  addHtml: (html: HTMLElement | string, options?: Record<string, any>) => void;
  /** 添加文本元素 */
  addText: (text: string, options?: Record<string, any>) => void;
  /** 添加表格元素 */
  addTable: (
    tableData: Record<string, any>[],
    columns: Array<{ field: string; title: string; width?: number }>,
    options?: Record<string, any>,
  ) => void;
  /** 执行打印 */
  print: (data?: PrintData, options?: PrintOptions) => void;
  /** 预览打印 */
  preview: (data?: PrintData) => void;
  /** 获取模板 JSON */
  getJson: () => null | string;
  /** 重置模板 */
  reset: () => void;
}

/**
 * 使用 Hiprint 打印（仅用于 JSON 模板打印）
 * @param initialTemplateJson 初始模板 JSON（可选）
 */
export function useHiprint(initialTemplateJson?: Record<string, any> | string): UseHiprintReturn {
  const template = ref<null | PrintTemplate>(null);
  const panel = ref<null | PrintPanel>(null);
  const isInitialized = ref(false);

  // 检查是否已初始化
  const checkInitialized = () => {
    const hiprint = window.hiprint || window.$hiprint?.hiprint;
    if (hiprint && !isInitialized.value) {
      isInitialized.value = true;
    }
    return isInitialized.value;
  };

  // 创建空白模板（已移除，仅支持从 JSON 加载）
  const createBlank = (): null | PrintTemplate => {
    console.warn('createBlank 已移除，请使用 loadFromJson 从 JSON 加载模板');
    return null;
  };

  // 从 JSON 加载模板
  const loadFromJson = (templateJson: Record<string, any> | string): null | PrintTemplate => {
    if (!checkInitialized()) {
      console.warn('Hiprint 未初始化');
      return null;
    }

    const newTemplate = loadTemplateFromJson(templateJson);
    if (newTemplate) {
      template.value = newTemplate;
      // 尝试获取第一个面板（如果模板有面板）
      try {
        const panels = newTemplate.getPanels?.() || newTemplate.panels || [];
        panel.value = panels[0] || null;
      } catch {
        panel.value = null;
      }
    }
    return newTemplate;
  };

  // 添加打印面板（已移除，模板应从 JSON 加载）
  const addPanel = (): null | PrintPanel => {
    console.warn('addPanel 已移除，模板应从 JSON 加载');
    return null;
  };

  // 添加 HTML 元素（已移除）
  const addHtml = (): void => {
    console.warn('addHtml 已移除，模板应从 JSON 加载');
  };

  // 添加文本元素（已移除）
  const addText = (): void => {
    console.warn('addText 已移除，模板应从 JSON 加载');
  };

  // 添加表格元素（已移除）
  const addTable = (): void => {
    console.warn('addTable 已移除，模板应从 JSON 加载');
  };

  // 执行打印
  const print = (data?: PrintData, options?: PrintOptions): void => {
    doPrint(template.value, data, options);
  };

  // 预览打印
  const previewPrint = (data?: PrintData): void => {
    preview(template.value, data);
  };

  // 获取模板 JSON
  const getJson = (): null | string => {
    return getTemplateJson(template.value);
  };

  // 重置模板
  const reset = (): void => {
    template.value = null;
    panel.value = null;
  };

  // 如果有初始模板，加载它
  if (initialTemplateJson) {
    loadFromJson(initialTemplateJson);
  }

  return {
    template,
    panel,
    isInitialized: computed(() => isInitialized.value),
    createBlank,
    loadFromJson,
    addPanel,
    addHtml,
    addText,
    addTable,
    print,
    preview: previewPrint,
    getJson,
    reset,
  };
}
