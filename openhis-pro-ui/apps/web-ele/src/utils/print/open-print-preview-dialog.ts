import type { App } from 'vue';
import type {
  InvFormPaperChangePayload,
  InvFormPaperKind,
  InvFormTripleSpec,
} from './form-print-preference';
import type { PrintPageSelection } from './runtime/print-page-selection';
import type { PrintResult } from './runtime/types';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import PrintPreviewDialogHost from './components/print-preview-dialog-host.vue';

export interface OpenPrintPreviewDialogOptions {
  /** 已用 hiprint getHtml 渲染好的预览 HTML */
  previewHtml: string;
  /** 预览内打印：走 hiprint print2，禁止浏览器 window.print */
  onPrint: (
    copies: number,
    formPaperKind?: InvFormPaperKind,
    tripleSpec?: InvFormTripleSpec,
    printerName?: string,
    pageSelection?: PrintPageSelection,
  ) => Promise<PrintResult>;
  /** 当前这份单据的折数/页数（hiprint 分页） */
  foldCount?: number;
  /** 整单连打份数（预览内不再改，仅传给打印） */
  copies?: number;
  /** 针式联单：预览按实物折高贴紧，齿孔用虚线 */
  continuousForm?: boolean;
  /** 库房单据当前纸张 */
  formPaperKind?: InvFormPaperKind;
  /** 库房单据当前三联单规格 */
  formTripleSpec?: InvFormTripleSpec;
  /** 库房单据：预览右侧展示选纸 */
  showFormPaperKind?: boolean;
  /** 已保存的场景打印机，加载列表后优先选中 */
  initialPrinterName?: string;
  /** 切换纸张或规格后重渲染预览 HTML */
  onFormPaperChange?: (payload: InvFormPaperChangePayload) => {
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
      formTripleSpec: options.formTripleSpec,
      showFormPaperKind: options.showFormPaperKind,
      initialPrinterName: options.initialPrinterName,
      onFormPaperChange: options.onFormPaperChange,
      onPrint: options.onPrint,
      onClosed: cleanup,
    });
    app.use(ElementPlus);
    app.mount(mountEl);
  });
}
