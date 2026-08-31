import type { App } from 'vue';
import type { InvFormPaperKind } from './form-print-preference';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import InvFormPaperKindDialogHost from './components/inv-form-paper-kind-dialog-host.vue';
import { INV_FORM_PAPER_KIND } from './form-print-preference';

export interface OpenInvFormPaperKindDialogOptions {
  initialPaperKind?: InvFormPaperKind;
  title?: string;
  confirmText?: string;
}

export interface InvFormPaperKindDialogResult {
  paperKind: InvFormPaperKind;
}

/** 函数式打开库房单据纸张选择（打印前） */
export function openInvFormPaperKindDialog(
  options: OpenInvFormPaperKindDialogOptions = {},
): Promise<InvFormPaperKindDialogResult | null> {
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

    app = createApp(InvFormPaperKindDialogHost, {
      title: options.title ?? '选择纸张',
      confirmText: options.confirmText ?? '确定',
      initialPaperKind: options.initialPaperKind ?? INV_FORM_PAPER_KIND.triple,
      onConfirm: (payload: InvFormPaperKindDialogResult) => {
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
