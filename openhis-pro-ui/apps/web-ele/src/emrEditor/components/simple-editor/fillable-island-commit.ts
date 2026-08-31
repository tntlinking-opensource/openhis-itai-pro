import type { Editor } from '@tiptap/core';

/**
 * 岛屿内文本写入 PM（走撤销栈），禁止只改 DOM 再 updateAttributes。
 */
export function commitFillableIslandValue(editor: Editor, pos: number, value: string): boolean {
  const node = editor.state.doc.nodeAt(pos);
  if (!node || !('value' in node.attrs)) return false;
  const prev = String(node.attrs.value ?? '');
  if (prev === value) return false;
  editor.view.dispatch(editor.state.tr.setNodeAttribute(pos, 'value', value));
  const after = editor.state.doc.nodeAt(pos);
  return String(after?.attrs.value ?? '') === value;
}
