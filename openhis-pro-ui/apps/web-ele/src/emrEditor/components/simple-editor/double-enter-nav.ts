/**
 * 医生站：短时内连按两次回车 → 跳到下一可填项（等同 Tab）。
 * 仅可填域内生效；正文回车只换行/分段，不参与本计时。
 * 第一次回车照常（域内换行）；第二次吞掉并尽量回删刚插入的换行。
 */

/** 两次回车判定窗口（ms） */
export const DOUBLE_ENTER_NAV_MS = 400;

let lastEnterAtMs = 0;

/** 无修饰键的 Enter（Ctrl/Meta+Enter 仍走原「下一项」） */
export function isPlainEnterKey(e: KeyboardEvent): boolean {
  return e.key === 'Enter' && !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey;
}

/**
 * 消费一次回车：窗口内第二次返回 true（调用方应 preventDefault + 跳下一项）。
 * 第一次记时并返回 false（放行原有 Enter 行为）。
 */
export function consumeTimedDoubleEnter(e: KeyboardEvent): boolean {
  if (!isPlainEnterKey(e) || e.isComposing) return false;
  const now = Date.now();
  if (lastEnterAtMs > 0 && now - lastEnterAtMs <= DOUBLE_ENTER_NAV_MS) {
    lastEnterAtMs = 0;
    return true;
  }
  lastEnterAtMs = now;
  return false;
}

/** 重置双击回车计时（跳槽 / Tab 后调用，避免误触） */
export function resetDoubleEnterNav(): void {
  lastEnterAtMs = 0;
}
