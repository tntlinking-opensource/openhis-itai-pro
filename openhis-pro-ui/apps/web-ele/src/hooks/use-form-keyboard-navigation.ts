import type { Ref } from 'vue';

/** 与 Element Plus 表单控件常见可聚焦节点匹配 */
export const DEFAULT_FORM_KEYBOARD_SELECTORS =
  'input:not([disabled]):not([type="hidden"]),textarea:not([disabled]),.el-select .el-select__wrapper,.el-input-number input:not([disabled])';

export const DEFAULT_FORM_KEYBOARD_NAV_KEYS = ['ArrowLeft', 'ArrowRight', 'Enter'] as const;

export interface FormKeyboardNavigationContext {
  readonly focusable: readonly HTMLElement[];
  readonly currentIndex: number;
  readonly event: KeyboardEvent;
  /** ArrowLeft 为 -1，ArrowRight / Enter 为 +1 */
  readonly direction: -1 | 1;
}

export interface UseFormKeyboardNavigationOptions {
  selectors?: string;
  /** 触发导航的按键，默认左/右方向键与 Enter */
  keys?: readonly string[];
  /**
   * 返回要聚焦项在 `focusable` 中的下标（会被夹紧到合法范围）。
   * 不返回或返回 `undefined` 时，使用默认：`currentIndex + direction`（下一项/上一项）。
   */
  resolveFocusIndex?: (ctx: FormKeyboardNavigationContext) => number | undefined;
  /** 返回 true 时不拦截、不导航（默认忽略 textarea，避免与多行编辑冲突） */
  shouldIgnoreEvent?: (event: KeyboardEvent, target: HTMLElement) => boolean;
}

const defaultShouldIgnoreEvent: NonNullable<
  UseFormKeyboardNavigationOptions['shouldIgnoreEvent']
> = (_event, target) => target.tagName === 'TEXTAREA';

function shouldKeepNativeCaretNavigation(event: KeyboardEvent, target: HTMLElement): boolean {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
    return false;
  }
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) {
    return false;
  }
  if (target.readOnly || target.disabled) {
    return false;
  }

  const { selectionEnd, selectionStart, value } = target;
  if (selectionStart === null || selectionEnd === null) {
    return false;
  }

  // 输入框内有选中范围时，优先保留原生选区移动行为。
  if (selectionStart !== selectionEnd) {
    return true;
  }

  if (event.key === 'ArrowLeft') {
    return selectionStart > 0;
  }
  return selectionEnd < value.length;
}

export function collectFormFocusableElements(
  scope: HTMLElement,
  selectors: string = DEFAULT_FORM_KEYBOARD_SELECTORS,
): HTMLElement[] {
  return [...scope.querySelectorAll<HTMLElement>(selectors)].filter((el) => {
    return el.offsetParent !== null && !el.hasAttribute('readonly');
  });
}

function clampIndex(index: number, length: number): number {
  if (length <= 0) return 0;
  return Math.max(0, Math.min(length - 1, index));
}

export const useFormKeyboardNavigation = (
  formAreaRef: Ref<HTMLElement | null>,
  options: UseFormKeyboardNavigationOptions = {},
) => {
  const selectors = options.selectors ?? DEFAULT_FORM_KEYBOARD_SELECTORS;
  const keys = options.keys ?? DEFAULT_FORM_KEYBOARD_NAV_KEYS;
  const shouldIgnoreEvent = options.shouldIgnoreEvent ?? defaultShouldIgnoreEvent;
  const { resolveFocusIndex } = options;

  const navigateFromTarget = (target: HTMLElement, direction: -1 | 1, event: KeyboardEvent) => {
    const scope = formAreaRef.value;
    if (!scope) return;
    const focusable = collectFormFocusableElements(scope, selectors);
    const currentIndex = focusable.findIndex((el) => el === target || el.contains(target));
    if (currentIndex === -1) return;

    const ctx: FormKeyboardNavigationContext = {
      focusable,
      currentIndex,
      event,
      direction,
    };

    const resolved = resolveFocusIndex?.(ctx);
    const nextIndex =
      typeof resolved === 'number' && !Number.isNaN(resolved)
        ? clampIndex(resolved, focusable.length)
        : clampIndex(currentIndex + direction, focusable.length);

    focusable[nextIndex]?.focus();
  };

  const handleFormKeydown = (event: KeyboardEvent) => {
    const target = event.target as HTMLElement | null;
    if (!target) return;
    if (!formAreaRef.value?.contains(target)) return;
    if (shouldIgnoreEvent(event, target)) return;
    if (shouldKeepNativeCaretNavigation(event, target)) return;
    if (!keys.includes(event.key)) return;

    event.preventDefault();
    const direction: -1 | 1 = event.key === 'ArrowLeft' ? -1 : 1;
    navigateFromTarget(target, direction, event);
  };

  return {
    handleFormKeydown,
    /** 可在自定义 key 处理里复用同一套焦点列表与 resolve 逻辑 */
    navigateFromTarget,
  };
};
