import type { App } from 'vue';

import type { HiprintPrinterItem } from './fetch-printer-list';
import type { InvFormPaperKind, InvFormTripleSpec } from './form-print-preference';
import type { ServiceApplyPaperSize } from './service-apply-paper-preference';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import PrinterSelectDialogHost from './components/printer-select-dialog-host.vue';
import { INV_FORM_PAPER_KIND, INV_FORM_TRIPLE_SPEC } from './form-print-preference';
import { DEFAULT_SERVICE_APPLY_PAPER_SIZE } from './service-apply-paper-preference';

export interface OpenPrinterSelectDialogOptions {
  bindingLabel?: string;
  title?: string;
  confirmText?: string;
  initialPrinterName?: string;
  initialSkipPrompt?: boolean;
  unavailableHint?: string;
  loadPrinters: () => Promise<HiprintPrinterItem[]>;
  /** 库房单据：同窗选择三联单 / A4 */
  showPaperKind?: boolean;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  /** 门诊申请单：同窗选择 A4 / A5 */
  showServiceApplyPaperSize?: boolean;
  initialServiceApplyPaperSize?: ServiceApplyPaperSize;
  showSkipPrompt?: boolean;
}

export interface PrinterSelectDialogResult {
  printerName: string;
  skipPrompt: boolean;
  paperKind?: InvFormPaperKind;
  tripleSpec?: InvFormTripleSpec;
  serviceApplyPaperSize?: ServiceApplyPaperSize;
}

/** 函数式打开打印机选择弹窗（供 print 运行时调用） */
export function openPrinterSelectDialog(
  options: OpenPrinterSelectDialogOptions,
): Promise<null | PrinterSelectDialogResult> {
  return new Promise((resolve) => {
    const mountEl = document.createElement('div');
    document.body.append(mountEl);

    let app: App | null = null;
    let settled = false;

    /** 卸载临时 Vue 实例 */
    function cleanup(): void {
      app?.unmount();
      app = null;
      mountEl.remove();
    }

    /** 只结算一次，避免确认后 @close 再触发 cancel */
    function settle(result: null | PrinterSelectDialogResult): void {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      resolve(result);
    }

    app = createApp(PrinterSelectDialogHost, {
      title: options.title ?? '选择打印机',
      bindingLabel: options.bindingLabel,
      confirmText: options.confirmText ?? '打印',
      initialPrinterName: options.initialPrinterName,
      initialSkipPrompt: options.initialSkipPrompt,
      unavailableHint: options.unavailableHint,
      loadPrinters: options.loadPrinters,
      showPaperKind: options.showPaperKind === true,
      initialPaperKind: options.initialPaperKind ?? INV_FORM_PAPER_KIND.triple,
      initialTripleSpec: options.initialTripleSpec ?? INV_FORM_TRIPLE_SPEC.third,
      showServiceApplyPaperSize: options.showServiceApplyPaperSize === true,
      initialServiceApplyPaperSize:
        options.initialServiceApplyPaperSize ?? DEFAULT_SERVICE_APPLY_PAPER_SIZE,
      showSkipPrompt: options.showSkipPrompt !== false,
      onConfirm: (payload: PrinterSelectDialogResult) => {
        settle(payload);
      },
      onCancel: () => {
        settle(null);
      },
    });
    app.use(ElementPlus);
    app.mount(mountEl);
  });
}

/** 设置页复用：直接传入已加载的打印机列表 */
export function openPrinterSelectDialogWithList(
  options: Omit<OpenPrinterSelectDialogOptions, 'loadPrinters'> & {
    printers: HiprintPrinterItem[];
  },
): Promise<null | PrinterSelectDialogResult> {
  const { printers, ...rest } = options;
  return openPrinterSelectDialog({
    ...rest,
    loadPrinters: async () => printers,
  });
}
