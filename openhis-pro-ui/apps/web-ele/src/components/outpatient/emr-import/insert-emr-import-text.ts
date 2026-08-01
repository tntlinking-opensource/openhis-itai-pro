import type { Editor } from '@tiptap/core';

/** 打开带入弹窗前捕获的数据元岛（如辅助检查 textarea）插入目标 */
export interface EmrIslandInsertTarget {
  el: HTMLInputElement | HTMLTextAreaElement;
  start: number;
  end: number;
}

/** 捕获当前焦点是否在 EMR 数据元可编辑控件上（辅助检查等多行） */
export function captureEmrIslandInsertTarget(
  root?: HTMLElement | null,
): EmrIslandInsertTarget | null {
  const ae = document.activeElement;
  if (!(ae instanceof HTMLTextAreaElement) && !(ae instanceof HTMLInputElement)) {
    return null;
  }
  if (ae.disabled || ae.readOnly) return null;
  const island = ae.closest('.emr-de');
  if (!island) return null;
  if (root && !root.contains(island)) return null;
  const start = ae.selectionStart ?? ae.value.length;
  const end = ae.selectionEnd ?? start;
  return { el: ae, start, end };
}

/** 向 textarea/input 光标处插入纯文本，并触发 input 以便 NodeView 回写 attrs */
export function insertTextIntoIslandTarget(target: EmrIslandInsertTarget, text: string): boolean {
  const { el } = target;
  if (!el.isConnected) return false;
  const start = Math.max(0, Math.min(target.start, el.value.length));
  const end = Math.max(start, Math.min(target.end, el.value.length));
  const next = `${el.value.slice(0, start)}${text}${el.value.slice(end)}`;
  const proto =
    el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  const valueSetter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
  if (valueSetter) {
    valueSetter.call(el, next);
  } else {
    el.value = next;
  }
  const caret = start + text.length;
  try {
    el.setSelectionRange(caret, caret);
  } catch {
    /* number input 等可能不支持 */
  }
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.focus();
  return true;
}

/** TipTap：在光标处插入多行纯文本（换行 → hardBreak） */
export function insertPlainTextAtEditorCursor(editor: Editor, text: string): boolean {
  if (!text) return false;
  const parts = text.split('\n');
  const content: Array<{ type: string; text?: string }> = [];
  for (let i = 0; i < parts.length; i += 1) {
    const line = parts[i] ?? '';
    if (line) {
      content.push({ type: 'text', text: line });
    }
    if (i < parts.length - 1) {
      content.push({ type: 'hardBreak' });
    }
  }
  if (content.length === 0) return false;
  return editor.chain().focus().insertContent(content).run();
}

/**
 * 优先写入打开弹窗前聚焦的辅助检查等数据元岛；否则插入编辑器光标。
 */
export function insertEmrImportText(options: {
  editor: Editor | null;
  text: string;
  islandTarget?: EmrIslandInsertTarget | null;
}): boolean {
  const text = options.text;
  if (!text) return false;
  if (options.islandTarget) {
    const ok = insertTextIntoIslandTarget(options.islandTarget, text);
    if (ok) return true;
  }
  if (!options.editor || options.editor.isDestroyed) return false;
  return insertPlainTextAtEditorCursor(options.editor, text);
}
