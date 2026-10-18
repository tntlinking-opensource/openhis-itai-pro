/** canvas-editor 存盘信封，与旧 Tiptap `{ type: 'doc' }` 区分 */

import type { IEditorData } from '@hufe921/canvas-editor';

import { EMR_LETTERHEAD_ID, pageHeader } from '#/emr-canvas/templates/canvas-kit';
import { applyControlChromeInElements } from '#/emr-canvas/utils/canvas-controls';

export const CANVAS_ENGINE = 'canvas-editor';

export interface CanvasEditorPayload {
  engine: typeof CANVAS_ENGINE;
  schema: 'v1';
  data: IEditorData;
}

/**
 * 空白正文必须是 []。
 * canvas-editor 打开时会强制在正文前再补一个换行；若这里再放一个 \n，纸面上就是两行空行。
 */
function emptyMain(): IEditorData['main'] {
  return [];
}

/** 只有换行、没有字、表、控件的正文，按空白处理，避免旧默认 \n 打开后又多出一行 */
function isBlankMain(main: IEditorData['main']): boolean {
  if (!main.length) return true;
  for (const el of main) {
    if (el.type && el.type !== 'text') return false;
    if (el.trList || el.control || el.valueList) return false;
    const value = el.value ?? '';
    if (value !== '' && value !== '\n' && value !== '\r\n' && value !== '\u200B') return false;
  }
  return true;
}

/** 空白纸面，供新建或覆盖旧 Tiptap 模板；页眉不写业务占位 */
export function emptyCanvasData(): IEditorData {
  return {
    header: [],
    main: emptyMain(),
    footer: [],
  };
}

/** 模板管理新建：页眉「医院名称」+ 文书标题，正文留空 */
export function defaultCanvasTemplateData(documentTitle: string): IEditorData {
  const title = documentTitle.trim() || '病历';
  return {
    header: pageHeader(title),
    main: emptyMain(),
    footer: [],
  };
}

/** 去掉 Vue Proxy，canvas-editor 内部 structuredClone 才能过 */
export function toPlainEditorData(data: IEditorData | undefined): IEditorData {
  const source = data ?? emptyCanvasData();
  const cloned = JSON.parse(JSON.stringify(source)) as IEditorData;
  if (!Array.isArray(cloned.main) || isBlankMain(cloned.main)) {
    cloned.main = emptyMain();
  }
  cloned.header = Array.isArray(cloned.header) ? cloned.header : [];
  cloned.footer = Array.isArray(cloned.footer) ? cloned.footer : [];
  applyControlChromeInElements(cloned.header);
  applyControlChromeInElements(cloned.main);
  applyControlChromeInElements(cloned.footer);
  enableTableBorderDrag(cloned.header);
  enableTableBorderDrag(cloned.main);
  enableTableBorderDrag(cloned.footer);
  return cloned;
}

/** 旧模板 tableToolDisabled 会把边框拖拽一并关掉，打开时去掉以便拖线框；页眉信头除外 */
function enableTableBorderDrag(elements: IEditorData['main'] | undefined) {
  if (!elements?.length) return;
  for (const el of elements) {
    if (el.id === EMR_LETTERHEAD_ID) {
      continue;
    }
    if (el.tableToolDisabled) {
      delete el.tableToolDisabled;
    }
    const rows = el.trList;
    if (!rows?.length) continue;
    for (const row of rows) {
      for (const cell of row.tdList ?? []) {
        enableTableBorderDrag(cell.value);
      }
    }
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 判断是否为本工程 Canvas 信封 */
export function isCanvasPayload(value: unknown): value is CanvasEditorPayload {
  if (!isRecord(value)) return false;
  if (value.engine !== CANVAS_ENGINE) return false;
  return isRecord(value.data) && Array.isArray(value.data.main);
}

/** 判断是否为历史 Tiptap 文书 JSON */
export function isTiptapDoc(value: unknown): boolean {
  return isRecord(value) && value.type === 'doc';
}

/** 接口 content（对象或 JSON 字符串）→ 可解析对象 */
export function parseTemplateContent(raw: unknown): unknown {
  if (raw === undefined || raw === null) return null;
  if (typeof raw === 'string') {
    const text = raw.trim();
    if (!text) return null;
    try {
      return JSON.parse(text) as unknown;
    } catch {
      return null;
    }
  }
  return raw;
}

/** 打开模板：Canvas 信封取出 data；旧稿或空稿给空白纸 */
export function toCanvasEditorData(raw: unknown): {
  data: IEditorData;
  legacyTiptap: boolean;
} {
  const parsed = parseTemplateContent(raw);
  if (isCanvasPayload(parsed)) {
    return { data: parsed.data, legacyTiptap: false };
  }
  if (isTiptapDoc(parsed)) {
    return { data: emptyCanvasData(), legacyTiptap: true };
  }
  if (isRecord(parsed) && Array.isArray(parsed.main)) {
    return {
      data: {
        header: Array.isArray(parsed.header) ? (parsed.header as IEditorData['header']) : [],
        main: parsed.main as IEditorData['main'],
        footer: Array.isArray(parsed.footer) ? (parsed.footer as IEditorData['footer']) : [],
      },
      legacyTiptap: false,
    };
  }
  return { data: emptyCanvasData(), legacyTiptap: false };
}

/** 把画布 data 打成存盘信封。模板页只依赖本函数，避免拉入国基文书预设包 */
export function wrapCanvasPayload(data: IEditorData): CanvasEditorPayload {
  return {
    engine: CANVAS_ENGINE,
    schema: 'v1',
    data,
  };
}

/** 编辑器值打成后端 content 字符串 */
export function serializeCanvasContent(data: IEditorData): string {
  return JSON.stringify(wrapCanvasPayload(data));
}
