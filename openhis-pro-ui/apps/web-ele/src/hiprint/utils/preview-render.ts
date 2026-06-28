import type { HiprintTemplateInstance } from '../core/types';
import type { BuildPreviewPrintDataOptions } from './print-data';

import { $, ensureHiprintReady, hiprint } from '../core/bootstrap';
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
  ensureHiprintReady();
  ensurePreviewPrintLockCss();
  return new hiprint.PrintTemplate({
    template: normalizePrintJson(json),
  }) as unknown as HiprintTemplateInstance;
}

/** 渲染预览 HTML 字符串 */
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
  const htmlResult = tpl.getHtml(printData);
  if (typeof htmlResult === 'string') {
    return htmlResult;
  }
  return (
    $('<div></div>')
      .append(htmlResult as never)
      .html() ?? ''
  );
}
