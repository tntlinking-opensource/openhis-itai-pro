import type { Editor } from '@tiptap/core';

import {
  focusNextEditableMetadataChip,
  focusPreviousEditableMetadataChip,
} from './focus-next-metadata-chip';
import {
  insertPlainTextAtCaret,
  isCaretAtContentEditableEnd,
  isCaretAtContentEditableStart,
  isContentEditableSelectionCollapsed,
} from './inline-contenteditable-caret';

export type InlineFieldNavMode =
  | 'checkbox'
  | 'contentEditable'
  | 'multiline'
  | 'number'
  | 'select'
  | 'text';

function textareaCaretOnFirstLine(ta: HTMLTextAreaElement): boolean {
  const v = ta.value;
  const i = ta.selectionStart ?? 0;
  return !v.slice(0, i).includes('\n');
}

function textareaCaretOnLastLine(ta: HTMLTextAreaElement): boolean {
  const v = ta.value;
  const i = ta.selectionStart ?? 0;
  return !v.slice(i).includes('\n');
}

/**
 * 行内数据元控件键盘串联。
 * - contentEditable：`Enter` 下一项；`Alt+Enter` 域内换行；`↑/↓` 在全文首/尾时上一项/下一项；`←/→` 仅域内移动光标
 * - text / number：`Enter` 下一项；`↑` 在域首上一项，`↓` 在域尾下一项
 * - multiline：`Ctrl+Enter` 下一项；`↑` 在第一行时上一项，`↓` 在最后一行时下一项
 * - checkbox：`Enter` 下一项；`↑/↓` 上一项/下一项
 * - select：仅 `Enter` 下一项（保留 `↑/↓` 改选项）
 */
export function handleInlineFieldKeydown(
  editor: Editor,
  pos: number,
  e: KeyboardEvent,
  mode: InlineFieldNavMode,
): void {
  if (e.isComposing) return;
  if (e.defaultPrevented) return;

  if (mode === 'contentEditable') {
    const el = e.target as HTMLElement;
    if (!el.isContentEditable) return;
    if (e.key === 'Enter') {
      if (e.altKey) {
        e.preventDefault();
        insertPlainTextAtCaret('\n');
        el.dispatchEvent(new Event('input', { bubbles: true }));
        return;
      }
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      return;
    }
    const collapsed = isContentEditableSelectionCollapsed(el);
    if (e.key === 'ArrowUp' && collapsed && isCaretAtContentEditableStart(el)) {
      e.preventDefault();
      focusPreviousEditableMetadataChip(editor, pos);
      return;
    }
    if (e.key === 'ArrowDown' && collapsed && isCaretAtContentEditableEnd(el)) {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
    }
    return;
  }

  if (mode === 'select') {
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
    }
    return;
  }

  if (mode === 'checkbox') {
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      focusPreviousEditableMetadataChip(editor, pos);
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
    }
    return;
  }

  if (mode === 'number') {
    const el = e.target as HTMLInputElement;
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      return;
    }
    const collapsed = el.selectionStart === el.selectionEnd;
    if (e.key === 'ArrowUp' && collapsed && el.selectionStart === 0) {
      e.preventDefault();
      focusPreviousEditableMetadataChip(editor, pos);
      return;
    }
    if (e.key === 'ArrowDown' && collapsed && el.selectionStart === el.value.length) {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
    }
    return;
  }

  if (mode === 'multiline') {
    const ta = e.target as HTMLTextAreaElement;
    if (e.key === 'Enter' && e.ctrlKey) {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      return;
    }
    const collapsed = ta.selectionStart === ta.selectionEnd;
    if (e.key === 'ArrowUp' && collapsed && textareaCaretOnFirstLine(ta)) {
      e.preventDefault();
      focusPreviousEditableMetadataChip(editor, pos);
      return;
    }
    if (e.key === 'ArrowDown' && collapsed && textareaCaretOnLastLine(ta)) {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
    }
    return;
  }

  const el = e.target as HTMLInputElement | HTMLTextAreaElement;
  if (e.key === 'Enter') {
    e.preventDefault();
    focusNextEditableMetadataChip(editor, pos);
    return;
  }
  const collapsed = el.selectionStart === el.selectionEnd;
  if (e.key === 'ArrowUp' && collapsed && el.selectionStart === 0) {
    e.preventDefault();
    focusPreviousEditableMetadataChip(editor, pos);
    return;
  }
  if (e.key === 'ArrowDown' && collapsed && el.selectionStart === el.value.length) {
    e.preventDefault();
    focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
  }
}
