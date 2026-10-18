import type { App } from 'vue';
import type { ServiceApplyPaperSize } from './service-apply-paper-preference';

import { createApp } from 'vue';

import ElementPlus from 'element-plus';

import ServiceApplyPaperDialogHost from './components/service-apply-paper-dialog-host.vue';
import {
  getServiceApplyPaperSizePreference,
  saveServiceApplyPaperSizePreference,
} from './service-apply-paper-preference';

export interface OpenServiceApplyPaperDialogOptions {
  initialPaperSize?: ServiceApplyPaperSize;
  title?: string;
  confirmText?: string;
  /** 确认后写入本地偏好（默认 true） */
  remember?: boolean;
}

export interface ServiceApplyPaperDialogResult {
  paperSize: ServiceApplyPaperSize;
}

/** 函数式打开门诊申请单纸张选择（打印前） */
export function openServiceApplyPaperDialog(
  options: OpenServiceApplyPaperDialogOptions = {},
): Promise<ServiceApplyPaperDialogResult | null> {
  return new Promise((resolve) => {
    const mountEl = document.createElement('div');
    document.body.append(mountEl);

    let app: App | null = null;

    function cleanup(): void {
      app?.unmount();
      app = null;
      mountEl.remove();
    }

    app = createApp(ServiceApplyPaperDialogHost, {
      title: options.title ?? '选择申请单纸张',
      confirmText: options.confirmText ?? '确定',
      initialPaperSize: options.initialPaperSize ?? getServiceApplyPaperSizePreference(),
      onConfirm: (payload: ServiceApplyPaperDialogResult) => {
        if (options.remember !== false) {
          saveServiceApplyPaperSizePreference(payload.paperSize);
        }
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
