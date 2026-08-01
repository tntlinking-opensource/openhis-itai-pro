import type { InjectionKey } from 'vue';

/** AppDialog / 业务弹层 provide：子表格据此关闭「填满父级」自适应 */
export const TABLE_LAYOUT_OVERLAY_HOST: InjectionKey<boolean> = Symbol('table-layout-overlay-host');

const OVERLAY_SELECTOR = [
  '.el-dialog',
  '.el-overlay-dialog',
  '.el-drawer',
  '.el-message-box',
  '.el-overlay',
  '[role="dialog"]',
].join(', ');

/**
 * 是否位于弹层容器内（Dialog / Drawer / MessageBox 等）。
 * 弹层父级高度常随内容变化；若再按父级 clientHeight 锁死表格 height，
 * 会出现「打开后列表被反复撑高」。
 */
export function isInsideOverlayContainer(el: Element | null | undefined): boolean {
  if (!el || typeof el.closest !== 'function') {
    return false;
  }
  return !!el.closest(OVERLAY_SELECTOR);
}

/** 祖先是否带有明确限高意图（滚动容器 / max-height / h-full|h-[…] 工具类） */
function hasExplicitHeightConstraint(el: HTMLElement): boolean {
  const style = getComputedStyle(el);
  const overflowY = style.overflowY;
  const overflow = style.overflow;
  const isScrollPort =
    overflowY === 'auto' ||
    overflowY === 'scroll' ||
    overflowY === 'hidden' ||
    overflow === 'auto' ||
    overflow === 'scroll' ||
    overflow === 'hidden';
  const maxHeightPx = parseFloat(style.maxHeight);
  const hasMaxHeight = Number.isFinite(maxHeightPx) && maxHeightPx > 0;
  const className = typeof el.className === 'string' ? el.className : '';
  const hasHeightUtility = /\bh-(full|screen|\[[^\]]+\])\b/.test(className);
  const hasInlineHeight = !!el.style.height && el.style.height !== 'auto';
  return isScrollPort || hasMaxHeight || hasHeightUtility || hasInlineHeight;
}

/**
 * 弹层内若存在「明确限高」的滚动容器（如明细弹窗 table-wrap、60vh 选人弹窗），
 * 返回其高度作 maxHeight；否则返回 null，表格按内容高度，避免被内容区一起撑高。
 */
export function findConstrainedOverlayMaxHeight(el: Element | null | undefined): null | number {
  if (!el || !(el instanceof HTMLElement)) {
    return null;
  }
  let current: HTMLElement | null = el.parentElement;
  while (current && current !== document.body) {
    if (
      current.classList.contains('el-dialog') ||
      current.classList.contains('el-drawer') ||
      current.classList.contains('el-message-box') ||
      current.classList.contains('el-overlay-dialog')
    ) {
      break;
    }
    if (hasExplicitHeightConstraint(current)) {
      const height = current.clientHeight;
      if (height >= 120) {
        return height;
      }
    }
    current = current.parentElement;
  }
  return null;
}
