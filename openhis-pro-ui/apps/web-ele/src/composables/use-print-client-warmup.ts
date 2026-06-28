import { onMounted } from 'vue';

import { schedulePrintClientWarmup } from '#/utils/print/runtime/hiprint-client';

/**
 * 打印业务页挂载后后台预热本地打印客户端（5000 health → Hiprint Socket），不阻塞页面主流程。
 */
export function usePrintClientWarmup(): void {
  onMounted(() => {
    schedulePrintClientWarmup();
  });
}
