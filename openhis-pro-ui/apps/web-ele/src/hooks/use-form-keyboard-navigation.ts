import type { Ref } from 'vue';

/** 与 Element Plus 表单控件常见可聚焦节点匹配 */
export const DEFAULT_FORM_KEYBOARD_SELECTORS =
  'input:not([disabled]):not([type="hidden"]),textarea:not([disabled]),.el-select .el-select__wrapper,.el-input-number input:not([disabled])';

/** 含 TreeSelect、Checkbox、tabindex 控件的扩展选择器 */
export const EXTENDED_FORM_KEYBOARD_SELECTORS = [
  'input:not([disabled]):not([type="hidden"])',
  'textarea:not([disabled])',
  '.el-select .el-select__wrapper:not(.is-disabled)',
  '.el-tree-select .el-select__wrapper:not(.is-disabled)',
  '.el-input-number input:not([disabled])',
  '.el-checkbox__original:not([disabled])',
  '[tabindex]:not([tabindex="-1"]):not([disabled])',
].join(',');

export const DEFAULT_FORM_KEYBOARD_NAV_KEYS = ['ArrowLeft', 'ArrowRight', 'Enter'] as const;

export const FORM_KEYBOARD_GRID_NAV_KEYS = [
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'ArrowUp',
  'Enter',
] as const;

/** Select / TreeSelect 键盘策略 */
export interface FormSelectKeyboardPolicy {
  /** Select 上 Enter 不用于焦点跳转，保留 EP 默认（打开/选中），默认 true */
  preserveEnter?: boolean;
  /** 下拉展开时 ↑↓ 交给 EP 选项移动，默认 true */
  preserveVerticalArrowsWhenExpanded?: boolean;
  /** 未展开时方向键用于焦点跳转，默认 true */
  navigateArrowsWhenCollapsed?: boolean;
}

export interface FormKeyboardGridTolerance {
  row?: number;
  col?: number;
}

export interface FormKeyboardNavigationContext {
  readonly focusable: readonly HTMLElement[];
  readonly currentIndex: number;
  readonly event: KeyboardEvent;
  /** ArrowLeft / ArrowUp 为 -1，其余导航键为 +1 */
  readonly direction: -1 | 1;
}

export interface UseFormKeyboardNavigationOptions {
  selectors?: string;
  /** 触发导航的按键，默认左/右方向键与 Enter */
  keys?: readonly string[];
  /**
   * 返回要聚焦项在 `focusable` 中的下标（会被夹紧到合法范围）。
   * 不返回或返回 `undefined` 时，使用 navigationMode 默认规则。
   */
  resolveFocusIndex?: (ctx: FormKeyboardNavigationContext) => number | undefined;
  /** 返回 true 时不拦截、不导航（默认忽略 textarea Enter） */
  shouldIgnoreEvent?: (event: KeyboardEvent, target: HTMLElement) => boolean;
  /** Select 键盘策略；`true` 启用推荐默认项 */
  selectPolicy?: boolean | FormSelectKeyboardPolicy;
  /** linear：DOM 顺序；grid：同行左右 / 同列上下 */
  navigationMode?: 'grid' | 'linear';
  gridTolerance?: FormKeyboardGridTolerance;
  /** 过滤 Select/TreeSelect 内层 input，避免与 wrapper 重复；selectPolicy 开启时默认 true */
  filterSelectInnerInputs?: boolean;
}

const DEFAULT_SELECT_POLICY: Required<FormSelectKeyboardPolicy> = {
  preserveEnter: true,
  preserveVerticalArrowsWhenExpanded: true,
  navigateArrowsWhenCollapsed: true,
};

const DEFAULT_GRID_TOLERANCE: Required<FormKeyboardGridTolerance> = {
  row: 24,
  col: 56,
};

const ARROW_KEYS = new Set(['ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowUp']);

function normalizeSelectPolicy(
  policy: boolean | FormSelectKeyboardPolicy | undefined,
): Required<FormSelectKeyboardPolicy> | null {
  if (policy === undefined || policy === false) {
    return null;
  }
  if (policy === true) {
    return { ...DEFAULT_SELECT_POLICY };
  }
  return {
    preserveEnter: policy.preserveEnter ?? DEFAULT_SELECT_POLICY.preserveEnter,
    preserveVerticalArrowsWhenExpanded:
      policy.preserveVerticalArrowsWhenExpanded ??
      DEFAULT_SELECT_POLICY.preserveVerticalArrowsWhenExpanded,
    navigateArrowsWhenCollapsed:
      policy.navigateArrowsWhenCollapsed ?? DEFAULT_SELECT_POLICY.navigateArrowsWhenCollapsed,
  };
}

/** 焦点是否在 Select / TreeSelect 内 */
export function isFormSelectTarget(target: HTMLElement): boolean {
  return !!target.closest('.el-select, .el-tree-select');
}

/** 焦点是否在下拉面板内（popper 通常 teleport 到 body） */
export function isFormSelectDropdownTarget(target: HTMLElement): boolean {
  const dropdown = target.closest('.el-select-dropdown, .el-tree-select__popper');
  if (!dropdown) {
    return false;
  }
  return dropdown.getAttribute('aria-hidden') !== 'true';
}

/** 下拉面板是否已展开（EP 将 aria-expanded 设在 combobox input，而非 wrapper） */
export function isFormSelectExpanded(target: HTMLElement): boolean {
  if (isFormSelectDropdownTarget(target)) {
    return true;
  }

  const selectRoot = target.closest('.el-select, .el-tree-select');
  if (!selectRoot) {
    return false;
  }

  const expandedCombobox = selectRoot.querySelector<HTMLElement>(
    '[role="combobox"][aria-expanded="true"]',
  );
  if (expandedCombobox) {
    return true;
  }

  const keyboardEl = resolveFormSelectKeyboardEl(target);
  if (keyboardEl?.getAttribute('aria-expanded') === 'true') {
    return true;
  }

  const controlsId =
    keyboardEl?.getAttribute('aria-controls') ??
    selectRoot.querySelector('[aria-controls]')?.getAttribute('aria-controls');
  if (controlsId) {
    const panel = document.getElementById(controlsId);
    if (panel && panel.getAttribute('aria-hidden') !== 'true' && panel.offsetParent !== null) {
      return true;
    }
  }

  return false;
}

/** EP Select 实际响应键盘的节点（filterable 为 input，否则为 wrapper） */
export function resolveFormSelectKeyboardEl(target: HTMLElement): HTMLElement | null {
  const root = target.closest('.el-select, .el-tree-select');
  if (!root) {
    return null;
  }
  const input = root.querySelector(
    'input.el-select__input:not([disabled]), input:not([type="hidden"]):not([disabled])',
  ) as HTMLInputElement | null;
  if (input && root.contains(input)) {
    return input;
  }
  return root.querySelector('.el-select__wrapper:not(.is-disabled)') as HTMLElement | null;
}

/** 未展开时：保留 Enter 展开/确认，wrapper 焦点时转发到 EP 监听节点 */
export function preserveFormSelectEnter(event: KeyboardEvent, target: HTMLElement): void {
  if (event.key !== 'Enter' || event.isComposing) {
    return;
  }
  if (!isFormSelectTarget(target) || isFormSelectExpanded(target)) {
    return;
  }

  const keyboardEl = resolveFormSelectKeyboardEl(target);
  if (!keyboardEl) {
    return;
  }

  if (keyboardEl === target || keyboardEl.contains(target)) {
    return;
  }

  event.preventDefault();
  event.stopPropagation();
  keyboardEl.focus({ preventScroll: true });
  keyboardEl.dispatchEvent(
    new KeyboardEvent('keydown', {
      key: 'Enter',
      code: 'Enter',
      keyCode: 13,
      which: 13,
      bubbles: true,
      cancelable: true,
    }),
  );
}

/** 聚焦 Select 键盘节点或普通控件 */
export function focusFormControl(el: HTMLElement | undefined) {
  if (!el) {
    return;
  }
  const selectKeyboardEl = resolveFormSelectKeyboardEl(el);
  if (selectKeyboardEl) {
    selectKeyboardEl.focus({ preventScroll: true });
    return;
  }
  el.focus({ preventScroll: true });
}

const defaultShouldIgnoreEvent: NonNullable<
  UseFormKeyboardNavigationOptions['shouldIgnoreEvent']
> = (event, target) => target.tagName === 'TEXTAREA' && event.key === 'Enter';

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
  if (target.closest('.el-select, .el-tree-select')) {
    return false;
  }

  const { selectionEnd, selectionStart, value } = target;
  if (selectionStart === null || selectionEnd === null) {
    return false;
  }
  if (selectionStart !== selectionEnd) {
    return true;
  }
  if (event.key === 'ArrowLeft') {
    return selectionStart > 0;
  }
  return selectionEnd < value.length;
}

function isSelectInnerInput(el: HTMLElement): boolean {
  return el.tagName === 'INPUT' && !!el.closest('.el-select, .el-tree-select');
}

export function collectFormFocusableElements(
  scope: HTMLElement,
  selectors: string = DEFAULT_FORM_KEYBOARD_SELECTORS,
  filterSelectInnerInputs = false,
): HTMLElement[] {
  return [...scope.querySelectorAll<HTMLElement>(selectors)].filter((el) => {
    if (el.offsetParent === null || el.hasAttribute('readonly')) {
      return false;
    }
    if (filterSelectInnerInputs && isSelectInnerInput(el)) {
      return false;
    }
    if (el.closest('.is-disabled') || el.getAttribute('aria-hidden') === 'true') {
      return false;
    }
    return true;
  });
}

function clampIndex(index: number, length: number): number {
  if (length <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(length - 1, index));
}

function getFormItemRect(el: HTMLElement): DOMRect {
  const formItem = el.closest('.el-form-item') as HTMLElement | null;
  return (formItem ?? el).getBoundingClientRect();
}

/** 按视觉位置在同列/同行查找下一焦点 */
export function resolveGridFocusIndex(
  focusable: readonly HTMLElement[],
  currentIndex: number,
  axis: 'horizontal' | 'vertical',
  direction: -1 | 1,
  tolerance: Required<FormKeyboardGridTolerance>,
): number | undefined {
  const current = focusable[currentIndex];
  if (!current) {
    return undefined;
  }
  const currentRect = getFormItemRect(current);

  let bestIndex: number | undefined;
  let bestDistance = Infinity;

  focusable.forEach((candidate, index) => {
    if (index === currentIndex) {
      return;
    }
    const rect = getFormItemRect(candidate);

    if (axis === 'vertical') {
      if (Math.abs(rect.left - currentRect.left) > tolerance.col) {
        return;
      }
      const delta = (rect.top - currentRect.top) * direction;
      if (delta <= 4) {
        return;
      }
      if (delta < bestDistance) {
        bestDistance = delta;
        bestIndex = index;
      }
      return;
    }

    if (Math.abs(rect.top - currentRect.top) > tolerance.row) {
      return;
    }
    const delta = (rect.left - currentRect.left) * direction;
    if (delta <= 4) {
      return;
    }
    if (delta < bestDistance) {
      bestDistance = delta;
      bestIndex = index;
    }
  });

  return bestIndex;
}

/** 是否应由表单导航接管该按键（false 则保留 EP / 原生行为） */
export function shouldHandleFormKeyboardNavigation(
  event: KeyboardEvent,
  target: HTMLElement,
  options: {
    keys: readonly string[];
    selectPolicy: Required<FormSelectKeyboardPolicy> | null;
    shouldIgnoreEvent: NonNullable<UseFormKeyboardNavigationOptions['shouldIgnoreEvent']>;
  },
): boolean {
  if (event.isComposing) {
    return false;
  }
  if (options.shouldIgnoreEvent(event, target)) {
    return false;
  }
  if (shouldKeepNativeCaretNavigation(event, target)) {
    return false;
  }
  if (!options.keys.includes(event.key)) {
    return false;
  }

  const inSelect = isFormSelectTarget(target);
  const inSelectDropdown = isFormSelectDropdownTarget(target);
  const expanded = isFormSelectExpanded(target);
  const policy = options.selectPolicy;

  if (inSelectDropdown && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
    return false;
  }

  if (event.key === 'Enter') {
    if (target.tagName === 'TEXTAREA') {
      return false;
    }
    if (inSelect && policy?.preserveEnter) {
      return false;
    }
    return true;
  }

  if (!ARROW_KEYS.has(event.key)) {
    return false;
  }

  if ((inSelect || expanded) && policy) {
    if (expanded) {
      if (
        (event.key === 'ArrowUp' || event.key === 'ArrowDown') &&
        policy.preserveVerticalArrowsWhenExpanded
      ) {
        return false;
      }
    } else if (inSelect && policy.navigateArrowsWhenCollapsed) {
      return true;
    }
  }

  return true;
}

function resolveDefaultFocusIndex(
  ctx: FormKeyboardNavigationContext,
  navigationMode: 'grid' | 'linear',
  gridTolerance: Required<FormKeyboardGridTolerance>,
): number {
  const { currentIndex, event, focusable } = ctx;
  const key = event.key;

  if (navigationMode === 'grid') {
    let nextIndex: number | undefined;
    if (key === 'ArrowRight' || key === 'Enter') {
      nextIndex = resolveGridFocusIndex(focusable, currentIndex, 'horizontal', 1, gridTolerance);
      if (nextIndex === undefined) {
        nextIndex = currentIndex + 1;
      }
    } else if (key === 'ArrowLeft') {
      nextIndex = resolveGridFocusIndex(focusable, currentIndex, 'horizontal', -1, gridTolerance);
      if (nextIndex === undefined) {
        nextIndex = currentIndex - 1;
      }
    } else if (key === 'ArrowDown') {
      nextIndex = resolveGridFocusIndex(focusable, currentIndex, 'vertical', 1, gridTolerance);
      if (nextIndex === undefined) {
        return currentIndex;
      }
    } else if (key === 'ArrowUp') {
      nextIndex = resolveGridFocusIndex(focusable, currentIndex, 'vertical', -1, gridTolerance);
      if (nextIndex === undefined) {
        return currentIndex;
      }
    }
    if (nextIndex === undefined) {
      return currentIndex;
    }
    return clampIndex(nextIndex, focusable.length);
  }

  if (key === 'ArrowLeft' || key === 'ArrowUp') {
    return clampIndex(currentIndex - 1, focusable.length);
  }
  return clampIndex(currentIndex + 1, focusable.length);
}

export const useFormKeyboardNavigation = (
  formAreaRef: Ref<HTMLElement | null>,
  options: UseFormKeyboardNavigationOptions = {},
) => {
  const selectPolicy = normalizeSelectPolicy(options.selectPolicy);
  const filterSelectInnerInputs = options.filterSelectInnerInputs ?? selectPolicy !== null;
  const selectors =
    options.selectors ??
    (selectPolicy !== null ? EXTENDED_FORM_KEYBOARD_SELECTORS : DEFAULT_FORM_KEYBOARD_SELECTORS);
  const keys = options.keys ?? DEFAULT_FORM_KEYBOARD_NAV_KEYS;
  const navigationMode = options.navigationMode ?? 'linear';
  const gridTolerance: Required<FormKeyboardGridTolerance> = {
    row: options.gridTolerance?.row ?? DEFAULT_GRID_TOLERANCE.row,
    col: options.gridTolerance?.col ?? DEFAULT_GRID_TOLERANCE.col,
  };
  const shouldIgnoreEvent = options.shouldIgnoreEvent ?? defaultShouldIgnoreEvent;
  const { resolveFocusIndex } = options;

  const navigateFromTarget = (target: HTMLElement, direction: -1 | 1, event: KeyboardEvent) => {
    const scope = formAreaRef.value;
    if (!scope) {
      return;
    }
    const focusable = collectFormFocusableElements(scope, selectors, filterSelectInnerInputs);
    const currentIndex = focusable.findIndex((el) => el === target || el.contains(target));
    if (currentIndex === -1) {
      return;
    }

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
        : resolveDefaultFocusIndex(ctx, navigationMode, gridTolerance);

    if (nextIndex === currentIndex) {
      return;
    }
    focusFormControl(focusable[nextIndex]);
  };

  const handleFormKeydown = (event: KeyboardEvent) => {
    const target = event.target as HTMLElement | null;
    if (!target || !formAreaRef.value?.contains(target)) {
      return;
    }

    if (
      event.key === 'Enter' &&
      selectPolicy?.preserveEnter &&
      isFormSelectTarget(target) &&
      !shouldIgnoreEvent(event, target)
    ) {
      if (!isFormSelectExpanded(target)) {
        preserveFormSelectEnter(event, target);
      }
      return;
    }

    if (
      !shouldHandleFormKeyboardNavigation(event, target, {
        keys,
        selectPolicy,
        shouldIgnoreEvent,
      })
    ) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    const direction: -1 | 1 = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
    navigateFromTarget(target, direction, event);
  };

  return {
    handleFormKeydown,
    navigateFromTarget,
    focusFormControl,
    collectFocusable: () => {
      const scope = formAreaRef.value;
      if (!scope) {
        return [];
      }
      return collectFormFocusableElements(scope, selectors, filterSelectInnerInputs);
    },
  };
};
