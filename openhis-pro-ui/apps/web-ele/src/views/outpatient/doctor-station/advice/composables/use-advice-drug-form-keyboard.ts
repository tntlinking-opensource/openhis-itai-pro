import type { Ref } from 'vue';

import type { FormKeyboardNavigationContext } from '#/hooks/use-form-keyboard-navigation';

import { ElButton } from 'element-plus';

import {
  collectFormFocusableElements,
  DEFAULT_FORM_KEYBOARD_SELECTORS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';

/** Enter 跳下一项时跳过 `el-select`，避免焦点停在展开控件上。 */
function resolveEnterFocusSkipSelects(ctx: FormKeyboardNavigationContext): number | undefined {
  if (ctx.event.key !== 'Enter') {
    return undefined;
  }
  const { focusable, currentIndex } = ctx;
  for (let i = currentIndex + 1; i < focusable.length; i++) {
    const el = focusable[i];
    if (!el) continue;
    if (!el.closest('.el-select')) {
      return i;
    }
  }
  return undefined;
}

function clampFocusIndex(index: number, length: number): number {
  if (length <= 0) return 0;
  return Math.max(0, Math.min(length - 1, index));
}

/**
 * 药品表单块内 Enter 顺序导航；最后一项 Enter 聚焦头部「保存」。
 */
export function useAdviceDrugFormKeyboard(
  formAreaRef: Ref<HTMLElement | null>,
  headerSaveButtonRef: Ref<InstanceType<typeof ElButton> | null>,
) {
  const { handleFormKeydown: baseDrugFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
    resolveFocusIndex: resolveEnterFocusSkipSelects,
  });

  function focusHeaderSaveButton() {
    const el = headerSaveButtonRef.value?.$el;
    if (el instanceof HTMLElement) {
      el.focus({ preventScroll: true });
    }
  }

  function handleDrugFormKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;
    if (!target || !formAreaRef.value?.contains(target)) {
      return;
    }
    if (event.key === 'Enter' && !event.isComposing) {
      const focusable = collectFormFocusableElements(
        formAreaRef.value,
        DEFAULT_FORM_KEYBOARD_SELECTORS,
      );
      const currentIndex = focusable.findIndex((el) => el === target || el.contains(target));
      if (currentIndex !== -1) {
        const ctx: FormKeyboardNavigationContext = {
          focusable,
          currentIndex,
          event,
          direction: 1,
        };
        const resolved = resolveEnterFocusSkipSelects(ctx);
        const nextIndex =
          typeof resolved === 'number' && !Number.isNaN(resolved)
            ? clampFocusIndex(resolved, focusable.length)
            : clampFocusIndex(currentIndex + 1, focusable.length);
        if (nextIndex === currentIndex) {
          event.preventDefault();
          focusHeaderSaveButton();
          return;
        }
      }
    }
    baseDrugFormKeydown(event);
  }

  function setFormScope(event: FocusEvent) {
    const t = event.currentTarget;
    if (t instanceof HTMLElement) {
      formAreaRef.value = t;
    }
  }

  return {
    handleDrugFormKeydown,
    setFormScope,
  };
}
