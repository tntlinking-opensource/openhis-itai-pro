import type { HiprintTemplateInstance } from '../core/types';
import type { BuildPreviewPrintDataOptions } from './print-data';

import { $, ensureHiprintPrintEngine, hiprint } from '../core/bootstrap';
import { buildPreviewPrintData } from './print-data';
import { normalizePrintJson } from './print-json';

let previewPrintLockLoaded = false;

/** 屏幕预览需加载 print-lock.css（index.html 仅 media=print，对话框内不生效） */
export function ensurePreviewPrintLockCss(): void {
  if (previewPrintLockLoaded || document.querySelector('#hiprint-print-lock-screen')) {
    previewPrintLockLoaded = true;
    return;
  }
  const link = document.createElement('link');
  link.id = 'hiprint-print-lock-screen';
  link.rel = 'stylesheet';
  link.href = '/print-lock.css';
  document.head.append(link);
  previewPrintLockLoaded = true;
}

/** 用当前 PrintJson 创建非 design 态模板实例（预览/打印与画布 design 分离） */
export function createPreviewTemplate(json: Record<string, unknown>): HiprintTemplateInstance {
  ensureHiprintPrintEngine();
  ensurePreviewPrintLockCss();
  const normalized = normalizePrintJson(json);
  const panels = normalized.panels;
  const template: Record<string, unknown> = {
    panels: Array.isArray(panels) ? panels : [],
  };
  if (normalized.engine !== undefined) {
    template.engine = normalized.engine;
  }
  if (normalized.schemaVersion !== undefined) {
    template.schemaVersion = normalized.schemaVersion;
  }
  return new hiprint.PrintTemplate({
    template,
  }) as unknown as HiprintTemplateInstance;
}

/** 把 hiprint getHtml 结果转成 HTML 字符串 */
function htmlFromTemplateResult(htmlResult: unknown): string {
  if (typeof htmlResult === 'string') {
    return htmlResult;
  }
  return (
    $('<div></div>')
      .append(htmlResult as never)
      .html() ?? ''
  );
}

/** 渲染预览 HTML 字符串（设计器：合并 testData / sample） */
export function renderPreviewHtml(
  templateJson: Record<string, unknown>,
  data: Record<string, unknown>,
  options?: BuildPreviewPrintDataOptions,
): string {
  const tpl = createPreviewTemplate(templateJson);
  const printData = buildPreviewPrintData(templateJson, {
    ...options,
    samplePrintData: data,
  });
  return htmlFromTemplateResult(tpl.getHtml(printData));
}

/** 业务预览：只用传入 PrintData，不合并模板 testData */
export function renderPrintDataHtml(
  templateJson: Record<string, unknown>,
  data: Record<string, unknown>,
): string {
  const tpl = createPreviewTemplate(templateJson);
  return htmlFromTemplateResult(tpl.getHtml(data));
}
