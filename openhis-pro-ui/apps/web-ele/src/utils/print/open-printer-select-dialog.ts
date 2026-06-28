import type { App } from 'vue';

import type { HiprintPrinterItem } from './fetch-printer-list';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import PrinterSelectDialogHost from './components/printer-select-dialog-host.vue';

export interface OpenPrinterSelectDialogOptions {
  bindingLabel?: string;
  title?: string;
  confirmText?: string;
  initialPrinterName?: string;
  initialSkipPrompt?: boolean;
  unavailableHint?: string;
  loadPrinters: () => Promise<HiprintPrinterItem[]>;
}

export interface PrinterSelectDialogResult {
  printerName: string;
  skipPrompt: boolean;
}

/** 函数式打开打印机选择弹窗（供 print 运行时调用） */
export function openPrinterSelectDialog(
  options: OpenPrinterSelectDialogOptions,
): Promise<null | PrinterSelectDialogResult> {
  return new Promise((resolve) => {
    const mountEl = document.createElement('div');
    document.body.append(mountEl);

    let app: App | null = null;

    /** 卸载临时 Vue 实例 */
    function cleanup(): void {
      app?.unmount();
      app = null;
      mountEl.remove();
    }

    app = createApp(PrinterSelectDialogHost, {
      title: options.title ?? '选择打印机',
      bindingLabel: options.bindingLabel,
      confirmText: options.confirmText ?? '打印',
      initialPrinterName: options.initialPrinterName,
      initialSkipPrompt: options.initialSkipPrompt,
      unavailableHint: options.unavailableHint,
      loadPrinters: options.loadPrinters,
      onConfirm: (payload: PrinterSelectDialogResult) => {
        cleanup();
        resolve(payload);
      },
      onCancel: () => {
        cleanup();
        resolve(null);
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
