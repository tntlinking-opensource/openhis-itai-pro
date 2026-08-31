/**
 * 页眉页脚 / 页边距与正文 JSON 同包存储（不进 Tiptap doc 节点树）。
 * 字段：`emrPageChrome`、`emrPageMargins`（与 template.json 约定一致）。
 */
import type { JSONContent } from '@tiptap/core';

import { DEFAULT_EDITOR_PAGE_MARGINS } from './editor-defaults';
import {
  cloneEmrPageChrome,
  createDefaultEmrPageChrome,
  normalizeEmrPageChrome,
  type EmrPageChrome,
} from './emr-page-chrome';

export type EmrDocPageMargins = {
  bottom: string;
  left: string;
  right: string;
  top: string;
};

export interface EmrDocSidecar {
  pageChrome: EmrPageChrome;
  pageMargins: EmrDocPageMargins;
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function readPageMargins(raw: unknown): EmrDocPageMargins {
  if (!isRecord(raw)) {
    return { ...DEFAULT_EDITOR_PAGE_MARGINS };
  }
  const pick = (key: keyof EmrDocPageMargins) => {
    const v = raw[key];
    return typeof v === 'string' && v.trim() ? v.trim() : DEFAULT_EDITOR_PAGE_MARGINS[key];
  };
  return {
    top: pick('top'),
    right: pick('right'),
    bottom: pick('bottom'),
    left: pick('left'),
  };
}

function readPageChrome(raw: unknown): EmrPageChrome {
  if (!isRecord(raw)) return createDefaultEmrPageChrome();
  const header = isRecord(raw.header) ? raw.header : {};
  const footer = isRecord(raw.footer) ? raw.footer : {};
  return normalizeEmrPageChrome({
    header: {
      align: (header.align as EmrPageChrome['header']['align']) ?? 'center',
      bold: Boolean(header.bold),
      enabled: header.enabled !== false,
      fontFamily: typeof header.fontFamily === 'string' ? header.fontFamily : undefined,
      fontSize: typeof header.fontSize === 'string' ? header.fontSize : undefined,
      heightMm: Number.isFinite(Number(header.heightMm)) ? Number(header.heightMm) : 0,
      italic: Boolean(header.italic),
      template: typeof header.template === 'string' ? header.template : '',
      underline: Boolean(header.underline),
    },
    footer: {
      align: (footer.align as EmrPageChrome['footer']['align']) ?? 'center',
      bold: Boolean(footer.bold),
      enabled: footer.enabled !== false,
      fontFamily: typeof footer.fontFamily === 'string' ? footer.fontFamily : undefined,
      fontSize: typeof footer.fontSize === 'string' ? footer.fontSize : undefined,
      heightMm: Number.isFinite(Number(footer.heightMm)) ? Number(footer.heightMm) : 0,
      italic: Boolean(footer.italic),
      template: typeof footer.template === 'string' ? footer.template : '',
      underline: Boolean(footer.underline),
    },
  });
}

/** 从接口 content（对象或 JSON 字符串）读出侧车数据 */
export function readEmrDocSidecar(raw: unknown): EmrDocSidecar {
  let root: unknown = raw;
  if (typeof raw === 'string') {
    const s = raw.trim();
    if (s) {
      try {
        root = JSON.parse(s) as unknown;
      } catch {
        root = null;
      }
    }
  }
  if (!isRecord(root)) {
    return {
      pageChrome: createDefaultEmrPageChrome(),
      pageMargins: { ...DEFAULT_EDITOR_PAGE_MARGINS },
    };
  }
  return {
    pageChrome: readPageChrome(root.emrPageChrome),
    pageMargins: readPageMargins(root.emrPageMargins),
  };
}

/** 写入侧车字段（保留正文 doc 结构） */
export function packEmrDocWithSidecar(doc: JSONContent, sidecar: EmrDocSidecar): JSONContent {
  return {
    ...doc,
    emrPageChrome: cloneEmrPageChrome(sidecar.pageChrome),
    emrPageMargins: { ...sidecar.pageMargins },
  };
}

/** 去掉侧车字段，得到纯 Tiptap 根节点 */
export function stripEmrDocSidecar(doc: JSONContent): JSONContent {
  const next = { ...doc };
  delete (next as Record<string, unknown>).emrPageChrome;
  delete (next as Record<string, unknown>).emrPageMargins;
  return next;
}
