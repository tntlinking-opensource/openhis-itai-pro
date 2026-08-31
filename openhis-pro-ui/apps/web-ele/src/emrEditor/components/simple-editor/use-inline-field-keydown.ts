import type { Editor } from '@tiptap/core';

import { consumeTimedDoubleEnter, resetDoubleEnterNav } from './double-enter-nav';
import { commitFillableIslandValue } from './fillable-island-commit';
import {
  focusNextEditableMetadataChip,
  focusPreviousEditableMetadataChip,
} from './focus-next-metadata-chip';
import {
  clearIslandDeleteSelectionSnapshot,
  contentEditableCaretOffset,
  deleteCharBeforeCaretIfNewlineInInput,
  insertPlainTextAtCaret,
  isCaretAtContentEditableEnd,
  isCaretAtContentEditableStart,
  isContentEditableSelectionCollapsed,
  peekPinnedIslandCaret,
  pinIslandCaret,
  clearPinnedIslandCaret,
  islandHasVisibleTextSelection,
  readContentEditableNonCollapsedRange,
  readContentEditablePlainText,
  readIslandDeleteSelection,
  scheduleIslandCaretRestore,
  snapshotIslandDeleteSelection,
  syncContentEditableFromValue,
} from './inline-contenteditable-caret';

/** keydown 已处理删键时，跳过同一次按键的 beforeinput，避免连删两字 */
let islandDeleteHandledByKeydown = false;

export type InlineFieldNavMode =
  | 'checkbox'
  | 'contentEditable'
  | 'date'
  | 'multiline'
  | 'number'
  | 'radio'
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
 * - contentEditable：`Enter` 域内换行（走 PM 撤销栈）；短时双击 `Enter` / `Tab` / `Ctrl+Enter` 下一项
 * - text / number / select / date / checkbox / radio：`Enter` 下一项
 * - multiline：`Ctrl+Enter` / 短时双击 `Enter` 下一项
 */
function handleTabFieldNavigation(editor: Editor, pos: number, e: KeyboardEvent): boolean {
  if (e.key !== 'Tab') return false;
  e.preventDefault();
  if (e.shiftKey) {
    focusPreviousEditableMetadataChip(editor, pos);
  } else {
    focusNextEditableMetadataChip(editor, pos);
  }
  resetDoubleEnterNav();
  return true;
}

/** 岛屿内 Ctrl+Z / Ctrl+Y：走编辑器撤销栈并回刷 DOM */
function handleIslandUndoRedo(
  editor: Editor,
  pos: number,
  e: KeyboardEvent,
  el: HTMLElement | HTMLInputElement | HTMLTextAreaElement,
): boolean {
  const mod = e.ctrlKey || e.metaKey;
  if (!mod) return false;
  const key = e.key.toLowerCase();
  const redo = key === 'y' || (key === 'z' && e.shiftKey);
  const undo = key === 'z' && !e.shiftKey;
  if (!undo && !redo) return false;
  e.preventDefault();
  e.stopPropagation();
  if (undo) {
    editor.commands.undo();
  } else {
    editor.commands.redo();
  }
  const node = editor.state.doc.nodeAt(pos);
  if (node && el instanceof HTMLElement && el.isContentEditable) {
    const next = String(node.attrs.value ?? '');
    const caretRaw = contentEditableCaretOffset(el);
    const caret = caretRaw >= 0 ? Math.min(caretRaw, next.length) : next.length;
    requestAnimationFrame(() => {
      syncContentEditableFromValue(el, next, caret);
    });
  } else if (node && (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) {
    const next = String(node.attrs.value ?? '');
    el.value = next;
  }
  resetDoubleEnterNav();
  return true;
}

/** capture 阶段调用：在删键折叠选区前快照拖选范围 */
export function captureIslandDeleteSelection(el: HTMLElement | null | undefined): void {
  snapshotIslandDeleteSelection(el);
}

/** 从事件/选区解析当前自由文本岛屿（勿仅信 e.target，垫片上可能不是 contenteditable） */
function resolveIslandContentEditable(e?: Event): HTMLElement | null {
  const pick = (node: EventTarget | Node | null | undefined): HTMLElement | null => {
    if (!(node instanceof Node)) return null;
    const el = node instanceof HTMLElement ? node : node.parentElement;
    if (!el) return null;
    if (
      el.isContentEditable &&
      el.matches('.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field')
    ) {
      return el;
    }
    const closest = el.closest(
      '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field',
    );
    if (closest instanceof HTMLElement && closest.isContentEditable) return closest;
    return null;
  };

  const fromEvent = pick(e?.target);
  if (fromEvent) return fromEvent;
  const fromActive = pick(document.activeElement);
  if (fromActive) return fromActive;
  const sel = window.getSelection();
  if (sel?.rangeCount) {
    const r = sel.getRangeAt(0);
    return pick(r.commonAncestorContainer) ?? pick(r.startContainer);
  }
  return null;
}

/**
 * 岛屿内批量删除。
 * 换页垫片为 contenteditable=false，原生 Delete/Backspace 跨垫片时常删不动。
 * `range` 须在 preventDefault 前快照，避免删键处理中途选区被清掉。
 */
export function deleteIslandContentEditableSelection(
  editor: Editor,
  pos: number,
  el: HTMLElement,
  range?: { from: number; to: number } | null,
): boolean {
  const resolved = range ?? readIslandDeleteSelection(el);
  if (!resolved || resolved.to <= resolved.from) return false;
  const node = editor.state.doc.nodeAt(pos);
  const current = readContentEditablePlainText(el);
  const next = current.slice(0, resolved.from) + current.slice(resolved.to);
  if (next === current) return false;
  pinIslandCaret(el, resolved.from, 600);
  if (node && 'value' in node.attrs) {
    if (!commitFillableIslandValue(editor, pos, next)) {
      syncContentEditableFromValue(el, next, resolved.from, { pinCaret: true });
      return true;
    }
  }
  scheduleIslandCaretRestore(el, next, resolved.from);
  return true;
}

/**
 * 岛屿内折叠光标删一字（含 `\n`）。
 * 换页垫片为 contenteditable=false，原生 Backspace 常删不掉换行，只能删当前行正文。
 */
export function deleteIslandContentEditableChar(
  editor: Editor,
  pos: number,
  el: HTMLElement,
  direction: 'backward' | 'forward',
): boolean {
  let caretRaw = contentEditableCaretOffset(el);
  // 选区偶发丢失（垫片重挂后）：用钉住的偏移续删，避免 return false 后无操作
  if (caretRaw < 0) {
    const pinned = peekPinnedIslandCaret(el);
    if (pinned != null) caretRaw = pinned;
  }
  if (caretRaw < 0) return false;
  // 仍有蓝选区时禁止按折叠光标删一字（否则会删掉选区前一个字符）
  if (islandHasVisibleTextSelection(el)) return false;
  const node = editor.state.doc.nodeAt(pos);
  const domText = readContentEditablePlainText(el);
  const current =
    node && 'value' in node.attrs
      ? (() => {
          const attr = String(node.attrs.value ?? '');
          return document.activeElement === el && domText !== attr ? domText : attr || domText;
        })()
      : domText;
  let from: number;
  let to: number;
  if (direction === 'backward') {
    if (caretRaw <= 0) return false;
    from = caretRaw - 1;
    to = caretRaw;
  } else {
    if (caretRaw >= current.length) return false;
    from = caretRaw;
    to = caretRaw + 1;
  }
  const next = current.slice(0, from) + current.slice(to);
  if (next === current) return false;
  // 钉住删后光标：commit → Vue sync → 分页重挂垫片 会异步改 DOM，重测易落到文末
  pinIslandCaret(el, from, 600);
  if (node && 'value' in node.attrs) {
    if (!commitFillableIslandValue(editor, pos, next)) {
      syncContentEditableFromValue(el, next, from, { pinCaret: true });
      return true;
    }
  }
  scheduleIslandCaretRestore(el, next, from);
  return true;
}

/** beforeinput 删键：与 keydown 去重，避免一次按键删两次 */
export function handleIslandContentEditableBeforeInputDelete(
  editor: Editor,
  pos: number,
  el: HTMLElement,
  e: InputEvent,
): boolean {
  if (islandDeleteHandledByKeydown) return true;
  const t = e.inputType;
  if (
    t !== 'deleteContentBackward' &&
    t !== 'deleteContentForward' &&
    t !== 'deleteByCut' &&
    t !== 'deleteContent'
  ) {
    return false;
  }
  const selRange = readIslandDeleteSelection(el);
  if (selRange) {
    if (!deleteIslandContentEditableSelection(editor, pos, el, selRange)) return false;
    clearIslandDeleteSelectionSnapshot(el);
    e.preventDefault();
    return true;
  }
  if (islandHasVisibleTextSelection(el)) return false;
  const direction = t === 'deleteContentForward' ? 'forward' : 'backward';
  if (!deleteIslandContentEditableChar(editor, pos, el, direction)) return false;
  clearIslandDeleteSelectionSnapshot(el);
  e.preventDefault();
  return true;
}

/** 在槽内插入换行（PM 事务，可撤销；commit 失败也刷 DOM，避免 Enter 被吞成空操作） */
export function insertNewlineInContentEditableIsland(
  editor: Editor,
  pos: number,
  el: HTMLElement,
): boolean {
  const node = editor.state.doc.nodeAt(pos);
  const domText = readContentEditablePlainText(el);
  // 以 DOM 为准：attrs 偶发落后于已输入未落库的字符，否则换行落点错 / 看起来像没换行
  const current =
    node && 'value' in node.attrs
      ? (() => {
          const attr = String(node.attrs.value ?? '');
          return document.activeElement === el && domText !== attr ? domText : attr || domText;
        })()
      : domText;

  const selRange = readContentEditableNonCollapsedRange(el);
  let next: string;
  let newCaret: number;
  if (selRange) {
    next = `${current.slice(0, selRange.from)}\n${current.slice(selRange.to)}`;
    newCaret = selRange.from + 1;
  } else {
    const caretRaw = contentEditableCaretOffset(el);
    let caret = caretRaw >= 0 ? Math.min(caretRaw, current.length) : current.length;
    // 末尾连续换行区间内的光标按「文末」处理，避免 \\n 插在换行前、续写粘同行
    const beforeTrail = current.replace(/\n+$/, '');
    if (current.endsWith('\n') && caret > beforeTrail.length) {
      caret = current.length;
    }
    next = `${current.slice(0, caret)}\n${current.slice(caret)}`;
    newCaret = caret + 1;
  }
  if (next === current) return false;

  pinIslandCaret(el, newCaret);
  if (node && 'value' in node.attrs) {
    if (!commitFillableIslandValue(editor, pos, next)) {
      scheduleIslandCaretRestore(el, next, newCaret);
      return true;
    }
  }
  // 立刻 + 多帧回焦：commit 触发分页后 PM 偶发抢焦，文书锁下表现为光标丢失
  scheduleIslandCaretRestore(el, next, newCaret);
  return true;
}

/** 短时双击回车：回删换行后跳下一可填项 */
function handleDoubleEnterNextField(
  editor: Editor,
  pos: number,
  e: KeyboardEvent,
  mode: InlineFieldNavMode,
): boolean {
  if (!consumeTimedDoubleEnter(e)) return false;
  e.preventDefault();
  e.stopPropagation();
  if (mode === 'contentEditable') {
    const el = resolveIslandContentEditable(e) ?? (e.target as HTMLElement);
    if (el instanceof HTMLElement && el.isContentEditable) {
      const node = editor.state.doc.nodeAt(pos);
      const current = String(node?.attrs.value ?? readContentEditablePlainText(el));
      const caretRaw = contentEditableCaretOffset(el);
      const caret = caretRaw >= 0 ? caretRaw : current.length;
      if (caret > 0 && current[caret - 1] === '\n') {
        const next = current.slice(0, caret - 1) + current.slice(caret);
        pinIslandCaret(el, caret - 1);
        commitFillableIslandValue(editor, pos, next);
        syncContentEditableFromValue(el, next, caret - 1);
      }
    }
  } else if (mode === 'multiline' || mode === 'text') {
    const el = e.target as HTMLInputElement | HTMLTextAreaElement;
    if (el && 'value' in el) {
      deleteCharBeforeCaretIfNewlineInInput(el);
    }
  }
  focusNextEditableMetadataChip(editor, pos, { caret: 'start' });
  resetDoubleEnterNav();
  return true;
}

export function handleInlineFieldKeydown(
  editor: Editor,
  pos: number,
  e: KeyboardEvent,
  mode: InlineFieldNavMode,
): void {
  if (e.isComposing) return;
  if (e.defaultPrevented) return;

  if (mode === 'contentEditable') {
    const el =
      resolveIslandContentEditable(e) ?? (e.target instanceof HTMLElement ? e.target : null);
    if (!el?.isContentEditable) return;
    if (e.key !== 'Backspace' && e.key !== 'Delete') {
      clearIslandDeleteSelectionSnapshot(el);
    }
    if (handleIslandUndoRedo(editor, pos, e, el)) return;

    // 岛屿内 Ctrl+A：只选本岛，禁止冒泡到 ProseMirror 根导致整篇选中后一键冲毁模版
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      const range = document.createRange();
      range.selectNodeContents(el);
      const sel = window.getSelection();
      if (sel) {
        sel.removeAllRanges();
        sel.addRange(range);
      }
      return;
    }

    // 岛屿内删：垫片 contenteditable=false 时原生常删不掉换行，统一按纯文本偏移手删
    if ((e.key === 'Backspace' || e.key === 'Delete') && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const direction = e.key === 'Backspace' ? 'backward' : 'forward';
      const selRange = readIslandDeleteSelection(el);
      let handled = false;
      if (selRange) {
        handled = deleteIslandContentEditableSelection(editor, pos, el, selRange);
      } else if (!islandHasVisibleTextSelection(el)) {
        handled = deleteIslandContentEditableChar(editor, pos, el, direction);
      }
      if (handled) {
        e.preventDefault();
        e.stopPropagation();
        clearIslandDeleteSelectionSnapshot(el);
        islandDeleteHandledByKeydown = true;
        requestAnimationFrame(() => {
          islandDeleteHandledByKeydown = false;
        });
      }
      return;
    }

    if (
      e.key === 'ArrowLeft' ||
      e.key === 'ArrowRight' ||
      e.key === 'ArrowUp' ||
      e.key === 'ArrowDown' ||
      e.key === 'Home' ||
      e.key === 'End'
    ) {
      clearPinnedIslandCaret(el);
    }

    if (handleDoubleEnterNextField(editor, pos, e, mode)) return;

    if (e.key === 'Enter') {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        focusNextEditableMetadataChip(editor, pos);
        resetDoubleEnterNav();
        return;
      }
      // 必须 preventDefault：否则原生会插 <br>/<div>，textContent 读不到 \n，回写后换行消失
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      insertNewlineInContentEditableIsland(editor, pos, el);
      return;
    }
    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        focusPreviousEditableMetadataChip(editor, pos);
      } else {
        focusNextEditableMetadataChip(editor, pos);
      }
      resetDoubleEnterNav();
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

  if (handleDoubleEnterNextField(editor, pos, e, mode)) return;

  if (mode === 'select' || mode === 'date') {
    if (handleTabFieldNavigation(editor, pos, e)) return;
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      resetDoubleEnterNav();
    }
    return;
  }

  if (mode === 'checkbox' || mode === 'radio') {
    if (handleTabFieldNavigation(editor, pos, e)) return;
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      resetDoubleEnterNav();
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
    if (handleTabFieldNavigation(editor, pos, e)) return;
    if (e.key === 'Enter') {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      resetDoubleEnterNav();
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
    if (handleIslandUndoRedo(editor, pos, e, ta)) return;
    if (e.key === 'Enter' && e.ctrlKey) {
      e.preventDefault();
      focusNextEditableMetadataChip(editor, pos);
      resetDoubleEnterNav();
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
  if (handleIslandUndoRedo(editor, pos, e, el)) return;
  if (e.key === 'Enter') {
    e.preventDefault();
    focusNextEditableMetadataChip(editor, pos);
    resetDoubleEnterNav();
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
