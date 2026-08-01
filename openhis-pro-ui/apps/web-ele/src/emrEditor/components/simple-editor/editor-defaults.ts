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

/** 工具栏「字号」下拉（value 用 pt，与 PrintConfig / Word 中文字号一致） */
export const EDITOR_FONT_SIZE_OPTIONS: { label: string; value: string }[] = [
  { label: '初号', value: '42pt' },
  { label: '小初', value: '36pt' },
  { label: '一号', value: '26pt' },
  { label: '小一', value: '24pt' },
  { label: '二号', value: '22pt' },
  { label: '小二', value: '18pt' },
  { label: '三号', value: '16pt' },
  { label: '小三', value: '15pt' },
  { label: '四号', value: '14pt' },
  { label: '小四', value: '12pt' },
  { label: '五号', value: '10.5pt' },
  { label: '小五', value: '9pt' },
];

/** 工具栏「行距」下拉（value 为块级 CSS line-height；病历默认 1.5） */
export const EDITOR_LINE_HEIGHT_OPTIONS: { label: string; value: string }[] = [
  { label: '行距 1', value: '1' },
  { label: '行距 1.15', value: '1.15' },
  { label: '行距 1.5', value: '1.5' },
  { label: '行距 2', value: '2' },
  { label: '行距 2.5', value: '2.5' },
];

/**
 * 工具栏「页边距」预设。
 * 「打印标准」= PrintConfig 国标页边距（上/下 25.4mm，左/右 31.8mm）。
 */
export const EDITOR_PAGE_MARGIN_PRESETS: {
  label: string;
  value: { bottom: string; left: string; right: string; top: string };
}[] = [
  {
    label: '紧凑',
    value: { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' },
  },
  {
    label: '标准',
    value: { top: '20mm', right: '20mm', bottom: '20mm', left: '20mm' },
  },
  {
    label: '适中',
    value: { top: '25mm', right: '25mm', bottom: '25mm', left: '25mm' },
  },
  {
    label: '宽松',
    value: { top: '30mm', right: '30mm', bottom: '30mm', left: '30mm' },
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
  { label: '紧凑', marginBottom: '0.15em', marginTop: '0' },
  { label: '标准', marginBottom: '0.25em', marginTop: '0' },
  { label: '宽松', marginBottom: '0.5em', marginTop: '0.15em' },
  { label: '段前加大', marginTop: '0.5em' },
  { label: '段后加大', marginBottom: '0.75em' },
];
