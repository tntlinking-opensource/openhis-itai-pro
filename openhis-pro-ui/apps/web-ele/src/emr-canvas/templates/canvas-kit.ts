import {
  ControlType,
  ElementType,
  FlexDirection,
  RowFlex,
  VerticalAlign,
  type IElement,
} from '@hufe921/canvas-editor';

import { CHOICE_CONTROL_BOUNDARY, FILL_CONTROL_CHROME } from '#/emr-canvas/utils/canvas-controls';

let seq = 0;

export function resetIds() {
  seq = 0;
}

function nextId(prefix: string) {
  seq += 1;
  return `${prefix}_${String(seq).padStart(3, '0')}`;
}

/** 96dpi：磅 → 像素 */
export function ptToPx(pt: number): number {
  return Math.round((pt * 96) / 72);
}

/**
 * 模板默认表格行高 = 编辑器 `table.defaultTrMinHeight`（24px）。
 * 历史名 TABLE_ROW_HEIGHT_40PT 保留作别名，避免旧 import 断裂。
 */
export const TABLE_DEFAULT_ROW_HEIGHT = 24;
export const TABLE_ROW_HEIGHT_40PT = TABLE_DEFAULT_ROW_HEIGHT;

export function t(value: string, extra?: Partial<IElement>): IElement {
  return { font: 'SimSun', size: 16, value, ...extra };
}

export function b(value: string, extra?: Partial<IElement>): IElement {
  return { font: 'SimSun', size: 16, value, bold: true, ...extra };
}

export function nl(): IElement {
  return { value: '\n' };
}

export function fill(label: string, conceptId?: string, minWidth?: number, placeholder?: string): IElement {
  return {
    type: ElementType.CONTROL,
    value: '',
    control: {
      type: ControlType.TEXT,
      value: null,
      placeholder: placeholder ?? label,
      conceptId: conceptId ?? nextId('fill'),
      ...FILL_CONTROL_CHROME,
      underline: false,
      border: false,
      // 勿设过大 minWidth：画布会用空位把控件撑开
      ...(minWidth ? { minWidth } : {}),
    },
  };
}

export function meta(fieldKey: string, label: string, editable = false): IElement {
  return {
    type: ElementType.CONTROL,
    value: '',
    control: {
      type: ControlType.TEXT,
      value: null,
      placeholder: label,
      conceptId: fieldKey,
      ...FILL_CONTROL_CHROME,
      underline: false,
      border: false,
      disabled: !editable,
    },
  };
}

export function dateFill(conceptId: string, label: string): IElement {
  return {
    type: ElementType.CONTROL,
    value: '',
    control: {
      type: ControlType.DATE,
      value: null,
      placeholder: label,
      conceptId,
      dateFormat: 'yyyy-MM-dd HH:mm',
      ...FILL_CONTROL_CHROME,
      underline: false,
      border: false,
    },
  };
}

/** 是/否：方块勾选，且最多选 1 项（视觉上等同单选） */
export function yesNo(conceptId: string): IElement {
  return {
    type: ElementType.CONTROL,
    value: '',
    control: {
      type: ControlType.CHECKBOX,
      value: null,
      code: null,
      conceptId,
      max: 1,
      flexDirection: FlexDirection.ROW,
      prefix: CHOICE_CONTROL_BOUNDARY,
      postfix: CHOICE_CONTROL_BOUNDARY,
      valueSets: [
        { value: '是', code: 'Y' },
        { value: '否', code: 'N' },
      ],
    },
  };
}

export function box(code: string): IElement {
  return {
    type: ElementType.CHECKBOX,
    value: '',
    checkbox: { value: false, code },
  };
}

export function labeledBox(code: string, label: string): IElement[] {
  return [
    {
      type: ElementType.CONTROL,
      value: '',
      control: {
        type: ControlType.CHECKBOX,
        value: null,
        code: null,
        conceptId: code,
        flexDirection: FlexDirection.ROW,
        prefix: CHOICE_CONTROL_BOUNDARY,
        postfix: CHOICE_CONTROL_BOUNDARY,
        valueSets: [{ value: label, code }],
      },
    },
  ];
}

export function ynLine(label: string, conceptId: string): IElement[] {
  return [t(label), yesNo(conceptId)];
}

export interface CellSpec {
  value: IElement[];
  colspan?: number;
  rowspan?: number;
  verticalAlign?: VerticalAlign;
  backgroundColor?: string;
}

export function td(value: IElement[], extra?: Omit<CellSpec, 'value'>) {
  return {
    colspan: extra?.colspan ?? 1,
    rowspan: extra?.rowspan ?? 1,
    value: value.length ? value : [{ value: '' }],
    verticalAlign: extra?.verticalAlign ?? VerticalAlign.MIDDLE,
    ...(extra?.backgroundColor ? { backgroundColor: extra.backgroundColor } : {}),
  };
}

export function table(
  colWidths: number[],
  rows: CellSpec[][],
  rowHeight: number | number[] = TABLE_DEFAULT_ROW_HEIGHT,
): IElement {
  return {
    type: ElementType.TABLE,
    value: '',
    id: nextId('tbl'),
    width: colWidths.reduce((sum, item) => sum + item, 0),
    colgroup: colWidths.map((width) => ({ width })),
    trList: rows.map((cells, index) => {
      const height = Array.isArray(rowHeight)
        ? (rowHeight[index] ?? TABLE_DEFAULT_ROW_HEIGHT)
        : rowHeight;
      return {
        height,
        minHeight: height,
        tdList: cells.map((cell) => td(cell.value, cell)),
      };
    }),
  };
}

export function patientBar(): IElement {
  // 加宽列，避免占位文字在蓝底/边框下被挤成换行
  const col = 130;
  return table(
    [col, col, col, col, col],
    [
      [
        { value: [b('姓名：'), meta('patientName', '患者姓名')] },
        { value: [b('性别：'), meta('gender', '性别')] },
        { value: [b('年龄：'), meta('age', '年龄'), t(' 岁')] },
        { value: [b('科室：'), meta('deptName', '科室')] },
        { value: [b('门诊号：'), meta('medicalRecordNo', '病历号')] },
      ],
    ],
    TABLE_DEFAULT_ROW_HEIGHT,
  );
}

/** 页眉院名元素 id：带入元数据时只改文字 */
export const HOSPITAL_TITLE_ID = 'emrHospitalTitle';
export const EMR_LETTERHEAD_ID = 'emrLetterhead';
export const DOCUMENT_TITLE_ID = 'emrDocumentTitle';

function walkElements(elements: IElement[] | undefined, visit: (el: IElement) => boolean): boolean {
  if (!elements?.length) return false;
  for (const el of elements) {
    if (visit(el)) return true;
    const rows = el.trList;
    if (!rows?.length) continue;
    for (const row of rows) {
      for (const cell of row.tdList ?? []) {
        if (walkElements(cell.value, visit)) return true;
      }
    }
  }
  return false;
}

export function hospitalTitleEl(name: string): IElement {
  return {
    id: HOSPITAL_TITLE_ID,
    value: name,
    font: 'SimHei',
    size: 29,
    bold: true,
    rowFlex: RowFlex.CENTER,
    rowMargin: 1.05,
  };
}

function documentTitleEl(title: string): IElement {
  return {
    id: DOCUMENT_TITLE_ID,
    value: title,
    font: 'SimSun',
    size: 21,
    rowFlex: RowFlex.CENTER,
    rowMargin: 1.05,
  };
}

/** 模板页眉是否带院名槽（空白纸 header=[] 为否） */
export function headerHasHospitalTitleSlot(header: IElement[] | undefined): boolean {
  if (!header?.length) return false;
  return walkElements(header, (el) => {
    if (el.id === HOSPITAL_TITLE_ID) return true;
    return el.font === 'SimHei' && el.size === 29 && el.value === '医院名称';
  });
}

/** 只改院名文字；空白纸不插入页眉 */
export function replaceHeaderHospitalTitle(header: IElement[], hospital: string): IElement[] {
  const text = hospital.trim();
  if (!text || !headerHasHospitalTitleSlot(header)) return header;
  const next = JSON.parse(JSON.stringify(header)) as IElement[];
  let replaced = false;
  walkElements(next, (el) => {
    if (el.id !== HOSPITAL_TITLE_ID && !(el.font === 'SimHei' && el.size === 29 && el.value === '医院名称')) {
      return false;
    }
    el.value = text;
    el.id = HOSPITAL_TITLE_ID;
    el.font = 'SimHei';
    el.size = 29;
    el.bold = true;
    el.rowFlex = RowFlex.CENTER;
    replaced = true;
    return true;
  });
  return replaced ? next : header;
}

/** 页眉：医院名称（黑体二号居中）+ 文书标题（宋体三号居中）+ 分隔线 */
export function pageHeader(title: string): IElement[] {
  const docTitle = title.trim() || '病历';
  return [
    hospitalTitleEl('医院名称'),
    { value: '\n', rowFlex: RowFlex.CENTER, rowMargin: 1 },
    documentTitleEl(docTitle),
    {
      type: ElementType.SEPARATOR,
      value: '',
      lineWidth: 1.15,
    },
  ];
}
