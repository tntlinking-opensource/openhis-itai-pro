import type { App } from 'vue';
import type { InvFormPaperKind } from './form-print-preference';
import type { PrintResult } from './runtime/types';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import PrintPreviewDialogHost from './components/print-preview-dialog-host.vue';

export interface OpenPrintPreviewDialogOptions {
  /** 已用 hiprint getHtml 渲染好的预览 HTML */
  previewHtml: string;
  /** 预览内打印：走 hiprint print2，禁止浏览器 window.print */
  onPrint: (copies: number, formPaperKind?: InvFormPaperKind) => Promise<PrintResult>;
  /** 当前这份单据的折数（hiprint 分页） */
  foldCount?: number;
  /** 整单连打份数（预览内不再改，仅传给打印） */
  copies?: number;
  /** 针式联单：预览按实物折高贴紧，齿孔用虚线 */
  continuousForm?: boolean;
  /** 库房单据当前纸张 */
  formPaperKind?: InvFormPaperKind;
  /** 库房单据：标题栏展示纸张切换 */
  showFormPaperKind?: boolean;
  /** 切换纸张后重渲染预览 HTML */
  onFormPaperKindChange?: (kind: InvFormPaperKind) => {
    foldCount: number;
    previewHtml: string;
  };
}

/** 函数式打开打印预览弹窗（供 print 运行时调用） */
export function openPrintPreviewDialog(options: OpenPrintPreviewDialogOptions): Promise<void> {
  return new Promise((resolve) => {
    const mountEl = document.createElement('div');
    document.body.append(mountEl);

    let app: App | null = null;

    /** 卸载临时 Vue 实例 */
    function cleanup(): void {
      app?.unmount();
      app = null;
      mountEl.remove();
      resolve();
    }

    app = createApp(PrintPreviewDialogHost, {
      previewHtml: options.previewHtml,
      foldCount: options.foldCount,
      copies: options.copies,
      continuousForm: options.continuousForm,
      formPaperKind: options.formPaperKind,
      showFormPaperKind: options.showFormPaperKind,
      onFormPaperKindChange: options.onFormPaperKindChange,
      onPrint: options.onPrint,
      onClosed: cleanup,
    });
    app.use(ElementPlus);
    app.mount(mountEl);
  });
}
