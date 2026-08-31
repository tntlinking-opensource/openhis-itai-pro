export interface FillableShortcutHintItem {
  key: string;
  text: string;
}

/** 文书右侧固定快捷键说明 */
export const FILLABLE_SHORTCUT_HINTS: FillableShortcutHintItem[] = [
  { key: 'Tab', text: '下一项' },
  { key: 'Shift+Tab', text: '上一项' },
  { key: '回车×2', text: '下一项' },
  { key: 'Ctrl+回车', text: '下一项' },
  { key: '回车', text: '换行（文本槽）' },
  { key: '回车', text: '下一项（单行框）' },
];
