import {
  ControlType,
  EditorMode,
  ListStyle,
  ListType,
  PageMode,
  PaperDirection,
  TitleLevel,
} from '@hufe921/canvas-editor';

export const FONT_OPTIONS = [
  { label: '宋体', value: 'SimSun' },
  { label: '微软雅黑', value: 'Microsoft YaHei' },
  { label: '黑体', value: 'SimHei' },
  { label: '楷体', value: 'KaiTi' },
  { label: '仿宋', value: 'FangSong' },
  { label: 'Arial', value: 'Arial' },
  { label: 'Times New Roman', value: 'Times New Roman' },
  { label: 'Calibri', value: 'Calibri' },
];

export const STYLE_GALLERY: { label: string; value: TitleLevel | 'body'; klass: string }[] = [
  { label: '正文', value: 'body', klass: 'is-body' },
  { label: '标题 1', value: TitleLevel.FIRST, klass: 'is-h1' },
  { label: '标题 2', value: TitleLevel.SECOND, klass: 'is-h2' },
  { label: '标题 3', value: TitleLevel.THIRD, klass: 'is-h3' },
  { label: '标题 4', value: TitleLevel.FOURTH, klass: 'is-h4' },
];

export const FONT_SIZE_OPTIONS = [
  { label: '初号', value: 56 },
  { label: '小初', value: 48 },
  { label: '一号', value: 34 },
  { label: '小一', value: 32 },
  { label: '二号', value: 29 },
  { label: '小二', value: 24 },
  { label: '三号', value: 21 },
  { label: '小三', value: 20 },
  { label: '四号', value: 18 },
  { label: '小四', value: 16 },
  { label: '五号', value: 14 },
  { label: '小五', value: 12 },
  { label: '六号', value: 10 },
  { label: '小六', value: 8 },
  { label: '七号', value: 7 },
  { label: '八号', value: 6 },
];

export const TITLE_OPTIONS: { label: string; value: TitleLevel | 'body' }[] = [
  { label: '正文', value: 'body' },
  { label: '标题1', value: TitleLevel.FIRST },
  { label: '标题2', value: TitleLevel.SECOND },
  { label: '标题3', value: TitleLevel.THIRD },
  { label: '标题4', value: TitleLevel.FOURTH },
  { label: '标题5', value: TitleLevel.FIFTH },
  { label: '标题6', value: TitleLevel.SIXTH },
];

export const ROW_MARGIN_OPTIONS = [1, 1.25, 1.5, 1.75, 2, 2.5, 3];

export const LIST_OPTIONS: { label: string; type: ListType | null; style?: ListStyle }[] = [
  { label: '取消列表', type: null },
  { label: '有序列表', type: ListType.OL, style: ListStyle.DECIMAL },
  { label: '复选框列表', type: ListType.UL, style: ListStyle.CHECKBOX },
  { label: '实心圆点', type: ListType.UL, style: ListStyle.DISC },
  { label: '空心圆点', type: ListType.UL, style: ListStyle.CIRCLE },
  { label: '空心方块', type: ListType.UL, style: ListStyle.SQUARE },
];

export const PAPER_SIZE_OPTIONS = [
  { label: 'A4', width: 794, height: 1123 },
  { label: 'A5', width: 559, height: 794 },
  { label: 'A3', width: 1123, height: 1587 },
  { label: 'A2', width: 1587, height: 2245 },
  { label: '5号信封', width: 831, height: 425 },
  { label: '6号信封', width: 850, height: 458 },
  { label: '7号信封', width: 866, height: 612 },
  { label: '9号信封', width: 866, height: 1224 },
  { label: '法律用纸', width: 816, height: 1344 },
  { label: '信纸', width: 816, height: 1056 },
];

export const PAGE_MODE_OPTIONS = [
  { label: '分页', value: PageMode.PAGING },
  { label: '连页', value: PageMode.CONTINUITY },
];

export const PAPER_DIRECTION_OPTIONS = [
  { label: '纵向', value: PaperDirection.VERTICAL },
  { label: '横向', value: PaperDirection.HORIZONTAL },
];

export const EDITOR_MODE_OPTIONS = [
  { label: '编辑模式', value: EditorMode.EDIT },
  { label: '清洁模式', value: EditorMode.CLEAN },
  { label: '只读模式', value: EditorMode.READONLY },
  { label: '表单模式', value: EditorMode.FORM },
  { label: '打印模式', value: EditorMode.PRINT },
  { label: '设计模式', value: EditorMode.DESIGN },
  { label: '涂鸦模式', value: EditorMode.GRAFFITI },
  { label: '留痕模式', value: EditorMode.TRACE },
];

export const CONTROL_TYPE_OPTIONS = [
  { label: '文本', value: ControlType.TEXT },
  { label: '数值', value: ControlType.NUMBER },
  { label: '列举', value: ControlType.SELECT },
  { label: '日期', value: ControlType.DATE },
  { label: '复选框', value: ControlType.CHECKBOX },
  { label: '单选框', value: ControlType.RADIO },
];

/** Word 常用页边距（96dpi 像素，顺序上右下左） */
export const MARGIN_PRESETS: { label: string; hint: string; value: [number, number, number, number] }[] = [
  { label: '普通', hint: '2.54 厘米', value: [96, 96, 96, 96] },
  { label: '窄', hint: '1.27 厘米', value: [48, 48, 48, 48] },
  { label: '适中', hint: '1.91 厘米', value: [72, 72, 72, 72] },
  { label: '宽', hint: '上/下 2.54，左/右 5.08', value: [96, 192, 96, 192] },
  { label: '办公', hint: '上/下 2.54，左/右 3.17', value: [96, 120, 96, 120] },
];

/** Word 常用行距 */
export const LINE_SPACING_PRESETS: { label: string; value: number }[] = [
  { label: '单倍行距', value: 1 },
  { label: '1.15 倍', value: 1.15 },
  { label: '1.5 倍', value: 1.5 },
  { label: '双倍行距', value: 2 },
  { label: '2.5 倍', value: 2.5 },
  { label: '3 倍', value: 3 },
];

export const CONTROL_QUICK_PRESETS: { label: string; type: ControlType; options?: string[] }[] = [
  { label: '文本', type: ControlType.TEXT },
  { label: '数字', type: ControlType.NUMBER },
  { label: '日期', type: ControlType.DATE },
  // RADIO 在插入时会转成方块 checkbox（max=1）
  { label: '是 / 否', type: ControlType.RADIO, options: ['是', '否'] },
  { label: '有 / 无', type: ControlType.RADIO, options: ['有', '无'] },
  { label: '男 / 女', type: ControlType.RADIO, options: ['男', '女'] },
];
