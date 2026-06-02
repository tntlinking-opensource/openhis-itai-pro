/** localStorage：是否屏蔽 Hiprint 本地打印客户端（17521）自动连接 */
export const HIPRINT_DISABLE_AUTO_CONNECT_KEY = 'openhis.hiprint.disableAutoConnect';

/** 屏蔽自动连接变更后通知（可选监听，用于运行时断开） */
export const HIPRINT_CONNECT_PREF_CHANGED_EVENT = 'openhis:hiprint-connect-pref-changed';

function readRaw(): string | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  return localStorage.getItem(HIPRINT_DISABLE_AUTO_CONNECT_KEY);
}

/** 是否已开启「屏蔽 Hiprint 本地打印连接」 */
export function isHiprintAutoConnectDisabled(): boolean {
  const raw = readRaw();
  if (raw === null) {
    return false;
  }
  return raw === 'true' || raw === '1';
}

/** 写入屏蔽开关 */
export function setHiprintAutoConnectDisabled(disabled: boolean): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.setItem(HIPRINT_DISABLE_AUTO_CONNECT_KEY, disabled ? 'true' : 'false');
  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent(HIPRINT_CONNECT_PREF_CHANGED_EVENT, { detail: { disabled } }),
    );
  }
}

type HiprintConnectExports = {
  autoConnect?: (callback?: (connected: boolean, msg?: string) => void) => void;
  disAutoConnect?: () => void;
  hiPrintPlugin?: { disAutoConnect?: () => void };
};

/** 按当前偏好应用连接策略（bootstrap 与设置页切换时调用） */
export async function applyHiprintAutoConnectPolicy(): Promise<void> {
  const mod = (await import('vue-plugin-hiprint')) as HiprintConnectExports;

  if (isHiprintAutoConnectDisabled()) {
    mod.hiPrintPlugin?.disAutoConnect?.();
    mod.disAutoConnect?.();
    return;
  }

  mod.autoConnect?.();
}
