import type { JSONContent } from '@tiptap/core';

/** 首行为居中段落，占位符与光标视觉居中 */
export const defaultEmrEditorDoc: JSONContent = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      attrs: { textAlign: 'center' },
      content: [],
    },
  ],
};

/** 工具栏「字体」下拉（value 为 CSS font-family） */
export const EDITOR_FONT_FAMILY_OPTIONS: { label: string; value: string }[] = [
  { label: '宋体', value: 'SimSun, "Songti SC", "Noto Serif CJK SC", serif' },
  { label: '黑体', value: 'SimHei, "Heiti SC", "Noto Sans CJK SC", sans-serif' },
  { label: '微软雅黑', value: '"Microsoft YaHei", "PingFang SC", sans-serif' },
  { label: '楷体', value: 'KaiTi, "Kaiti SC", serif' },
  { label: '仿宋', value: 'FangSong, "FangSong SC", serif' },
  { label: 'Arial', value: 'Arial, Helvetica, sans-serif' },
];

/** 工具栏「字号」下拉（value 为 font-size，与 Word 中文字号大致对应） */
export const EDITOR_FONT_SIZE_OPTIONS: { label: string; value: string }[] = [
  { label: '初号', value: '56px' },
  { label: '小初', value: '48px' },
  { label: '一号', value: '34px' },
  { label: '小一', value: '32px' },
  { label: '二号', value: '29px' },
  { label: '小二', value: '24px' },
  { label: '三号', value: '21px' },
  { label: '小三', value: '20px' },
  { label: '四号', value: '19px' },
  { label: '小四', value: '16px' },
  { label: '五号', value: '14px' },
  { label: '小五', value: '12px' },
];

/** 工具栏「行距」下拉（value 为块级 CSS line-height） */
export const EDITOR_LINE_HEIGHT_OPTIONS: { label: string; value: string }[] = [
  { label: '行距 1', value: '1' },
  { label: '行距 1.15', value: '1.15' },
  { label: '行距 1.5', value: '1.5' },
  { label: '行距 2', value: '2' },
  { label: '行距 2.5', value: '2.5' },
];

/** 工具栏「页边距」预设（屏幕编辑用 px） */
export const EDITOR_PAGE_MARGIN_PRESETS: {
  label: string;
  value: { bottom: string; left: string; right: string; top: string };
}[] = [
  {
    label: '紧凑',
    value: { top: '12px', right: '14px', bottom: '12px', left: '14px' },
  },
  {
    label: '标准',
    value: { top: '20px', right: '24px', bottom: '20px', left: '24px' },
  },
  {
    label: '适中',
    value: { top: '24px', right: '36px', bottom: '24px', left: '36px' },
  },
  {
    label: '宽松',
    value: { top: '32px', right: '48px', bottom: '32px', left: '48px' },
  },
  {
    label: '打印标准',
    value: { top: '25.4mm', right: '31.8mm', bottom: '25.4mm', left: '31.8mm' },
  },
];

/** 工具栏「首行缩进」下拉 */
export const EDITOR_TEXT_INDENT_OPTIONS: { label: string; value: string }[] = [
  { label: '缩进 2 字符', value: '2em' },
  { label: '缩进 4 字符', value: '4em' },
  { label: '缩进 1cm', value: '1cm' },
  { label: '缩进 2cm', value: '2cm' },
];

/** 工具栏「段间距」：段前/段后 margin（仅段落）；缺省字段表示不修改该侧 */
export const EDITOR_PARAGRAPH_SPACING_OPTIONS: {
  label: string;
  marginBottom?: null | string;
  marginTop?: null | string;
}[] = [
  { label: '紧凑', marginBottom: '0.35em', marginTop: '0' },
  { label: '标准', marginBottom: '0.75em', marginTop: '0' },
  { label: '宽松', marginBottom: '1em', marginTop: '0.35em' },
  { label: '段前加大', marginTop: '0.75em' },
  { label: '段后加大', marginBottom: '1.25em' },
];
