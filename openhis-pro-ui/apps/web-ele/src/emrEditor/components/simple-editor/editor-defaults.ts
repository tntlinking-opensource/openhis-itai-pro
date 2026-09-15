import type { JSONContent } from '@tiptap/core';

/** 空白文档默认左对齐（打开时光标在左侧，贴近 Word） */
export const defaultEmrEditorDoc: JSONContent = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      attrs: { textAlign: 'left' },
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

/**
 * 工具栏「n 倍行距」存 JSON 的倍数（与 Word 行距名称一致）。
 * 中文宋体单倍约 1.55×字号，故 CSS 不能直接写 1.5（12pt×1.5=18pt，看起来像单倍）。
 * 1.5 倍 → CSS 2.33（小四约 28pt），与 PrintConfig 渲染公式同步。
 */
export const EDITOR_LINE_HEIGHT_OPTIONS: { label: string; value: string }[] = [
  { label: '行距 1', value: '1' },
  { label: '行距 1.15', value: '1.15' },
  { label: '行距 1.5', value: '1.5' },
  { label: '行距 2', value: '2' },
  { label: '行距 2.5', value: '2.5' },
];

/** 中文宋体 Word「单倍行距」相对字号的系数（1.5 倍 × 1.55 ≈ 2.33） */
export const EMR_CJK_SINGLE_SPACING_FACTOR = 1.55;

/** 页眉下沿 / 页脚上沿线宽（Word 1 磅 = 1pt）；与 CSS --simple-editor-chrome-rule、PDF CHROME_RULE_PT 同步 */
export const EMR_CHROME_RULE_PT = 1;

/**
 * 工具栏倍数 → 实际 CSS line-height（无单位倍数）。
 *
 * @param toolbarMultiple 工具栏 value，如 `1.5`
 * @returns CSS line-height，如 `2.33`
 */
export function toCssLineHeight(toolbarMultiple: string): string {
  const key = String(toolbarMultiple ?? '').trim();
  const n = Number(key);
  if (!Number.isFinite(n) || n <= 0) {
    return key;
  }
  const css = n * EMR_CJK_SINGLE_SPACING_FACTOR;
  return String(Math.round(css * 100) / 100);
}

/**
 * 屏上/打印默认页边距（Word 常用 15mm）。
 * 国标 25.4/31.8 仅作工具栏「打印标准」预设，不再当默认——边距过大会多出空白页。
 */
export const DEFAULT_EDITOR_PAGE_MARGINS: {
  bottom: string;
  left: string;
  right: string;
  top: string;
} = {
  top: '15mm',
  right: '15mm',
  bottom: '15mm',
  left: '15mm',
};

/**
 * 工具栏「页边距」预设。
 * 「打印标准」= PrintConfig 国标（上/下 25.4mm，左/右 31.8mm）。
 * 文书/模板默认用 {@link DEFAULT_EDITOR_PAGE_MARGINS}，勿再硬编码 31.8mm。
 */
export const EDITOR_PAGE_MARGIN_PRESETS: {
  label: string;
  value: { bottom: string; left: string; right: string; top: string };
}[] = [
  {
    label: '紧凑',
    value: { top: '10mm', right: '10mm', bottom: '10mm', left: '10mm' },
  },
  {
    label: 'Word 常用',
    value: { ...DEFAULT_EDITOR_PAGE_MARGINS },
  },
  {
    label: '标准',
    value: { top: '25.4mm', right: '25.4mm', bottom: '25.4mm', left: '25.4mm' },
  },
  {
    label: '适中',
    value: { top: '20mm', right: '20mm', bottom: '20mm', left: '20mm' },
  },
  {
    label: '宽松',
    value: { top: '25mm', right: '25mm', bottom: '25mm', left: '25mm' },
  },
  {
    label: '打印标准',
    value: { top: '25.4mm', right: '31.8mm', bottom: '25.4mm', left: '31.8mm' },
  },
];

/** 工具栏「纸张大小」：屏上 CSS 宽高，打印仍以院内 PrintConfig 为准 */
export const EDITOR_PAPER_SIZE_PRESETS: {
  height: string;
  key: 'A4' | 'A5';
  label: string;
  width: string;
}[] = [
  { key: 'A4', label: 'A4（210×297mm）', width: '210mm', height: '297mm' },
  { key: 'A5', label: 'A5（148×210mm）', width: '148mm', height: '210mm' },
];

export type EditorPaperOrientation = 'landscape' | 'portrait';

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
