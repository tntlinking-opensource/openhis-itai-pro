import { Table, TableCell, TableHeader, TableView } from '@tiptap/extension-table';
import type { Node as ProseMirrorNode } from '@tiptap/pm/model';

import {
  buildCellLayoutStyle,
  colWidthsToPercents,
  parseColWidths,
  parseOptionalBoolAttr,
} from './emr-table-layout';
import { createEmrTableColWidthPlugin, lockEmrTableToContentWidth } from './emr-table-resize';

/** 表格/单元格 data-name、data-id，供按名称取值与 PDF 定位 */
const emrNameAttr = {
  default: null as null | string,
  parseHTML: (element: HTMLElement) => element.getAttribute('data-name'),
  renderHTML: (attributes: { name?: null | string }) => {
    if (!attributes.name) {
      return {};
    }
    return { 'data-name': attributes.name };
  },
};

const emrIdAttr = {
  default: null as null | string,
  parseHTML: (element: HTMLElement) => element.getAttribute('data-id'),
  renderHTML: (attributes: { id?: null | string }) => {
    if (!attributes.id) {
      return {};
    }
    return { 'data-id': attributes.id };
  },
};

/**
 * 把 §5.1 格 attrs 写成 data-* + 内联 style，供 L1 回放。
 */
function cellLayoutAttrs() {
  return {
    minHeightMm: {
      default: null as null | number,
      parseHTML: (element: HTMLElement) => {
        const raw = element.getAttribute('data-min-height-mm');
        if (!raw) {
          return null;
        }
        const value = Number(raw);
        if (!Number.isFinite(value) || value <= 0) {
          return null;
        }
        return value;
      },
      renderHTML: (attributes: { minHeightMm?: null | number }) => {
        if (attributes.minHeightMm == null) {
          return {};
        }
        return { 'data-min-height-mm': String(attributes.minHeightMm) };
      },
    },
    paddingMm: {
      default: null as unknown,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-padding-mm'),
      renderHTML: (attributes: { paddingMm?: unknown }) => {
        if (attributes.paddingMm == null || attributes.paddingMm === '') {
          return {};
        }
        if (typeof attributes.paddingMm === 'string') {
          return { 'data-padding-mm': attributes.paddingMm };
        }
        return { 'data-padding-mm': JSON.stringify(attributes.paddingMm) };
      },
    },
    align: {
      default: null as null | string,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-align'),
      renderHTML: (attributes: { align?: null | string }) => {
        if (!attributes.align) {
          return {};
        }
        return { 'data-align': attributes.align };
      },
    },
    valign: {
      default: null as null | string,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-valign'),
      renderHTML: (attributes: { valign?: null | string }) => {
        if (!attributes.valign) {
          return {};
        }
        return { 'data-valign': attributes.valign };
      },
    },
    wrap: {
      default: null as null | boolean,
      parseHTML: (element: HTMLElement) => parseOptionalBoolAttr(element, 'data-wrap'),
      renderHTML: (attributes: { wrap?: null | boolean }) => {
        if (attributes.wrap == null) {
          return {};
        }
        return { 'data-wrap': attributes.wrap ? 'true' : 'false' };
      },
    },
    nowrap: {
      default: null as null | boolean,
      parseHTML: (element: HTMLElement) => parseOptionalBoolAttr(element, 'data-nowrap'),
      renderHTML: (attributes: { nowrap?: null | boolean }) => {
        if (attributes.nowrap == null) {
          return {};
        }
        return { 'data-nowrap': attributes.nowrap ? 'true' : 'false' };
      },
    },
    /**
     * 值格只显示填写结果，不画芯片标签括号和空槽占位。
     * 会诊值格、落款、入院表头用它，不再按表名藏括号。
     */
    bareValue: {
      default: null as null | boolean,
      parseHTML: (element: HTMLElement) => parseOptionalBoolAttr(element, 'data-bare-value'),
      renderHTML: (attributes: { bareValue?: null | boolean }) => {
        if (attributes.bareValue == null) {
          return {};
        }
        return { 'data-bare-value': attributes.bareValue ? 'true' : 'false' };
      },
    },
    fillRemainShare: {
      default: null as null | number,
      parseHTML: (element: HTMLElement) => {
        const raw = element.getAttribute('data-fill-remain-share');
        if (!raw) {
          return null;
        }
        const value = Number(raw);
        if (!Number.isFinite(value) || value <= 0) {
          return null;
        }
        return value;
      },
      renderHTML: (attributes: { fillRemainShare?: null | number }) => {
        if (attributes.fillRemainShare == null) {
          return {};
        }
        return { 'data-fill-remain-share': String(attributes.fillRemainShare) };
      },
    },
    /**
     * 合成内联 style。Tiptap mergeAttributes 会拼接各 attr 的 style。
     */
    emrLayoutStyle: {
      default: null,
      parseHTML: () => null,
      renderHTML: (attributes: Record<string, unknown>) => {
        const style = buildCellLayoutStyle(attributes);
        if (!style) {
          return {};
        }
        return { style };
      },
    },
  };
}

/**
 * 协议 colWidths 写到 colgroup，盖过 Tiptap 按 px colwidth 推的列宽。
 */
function applyProtocolColWidths(table: HTMLTableElement, node: ProseMirrorNode) {
  const shares = parseColWidths(node.attrs.colWidths);
  if (!shares || shares.length === 0) {
    table.removeAttribute('data-col-widths');
    table.removeAttribute('data-emr-col-widths');
    return;
  }
  table.setAttribute('data-col-widths', shares.join(','));
  table.setAttribute('data-emr-col-widths', '1');
  lockEmrTableToContentWidth(table);
  const percents = colWidthsToPercents(shares);
  const colgroup = table.querySelector('colgroup');
  if (!colgroup) {
    return;
  }
  const cols = colgroup.children;
  for (let i = 0; i < cols.length && i < percents.length; i++) {
    (cols[i] as HTMLElement).style.width = percents[i];
  }
}

/**
 * 无框表：data-emr-border=false，CSS 去线。
 */
function applyProtocolBorder(table: HTMLTableElement, node: ProseMirrorNode) {
  if (node.attrs.border === false) {
    table.setAttribute('data-emr-border', 'false');
    return;
  }
  if (node.attrs.border === true) {
    table.setAttribute('data-emr-border', 'true');
    return;
  }
  table.removeAttribute('data-emr-border');
}

/** TableView 自己建 table 节点，renderHTML 的 data-name 不会落到 DOM，须在 View 里回写 */
function syncEmrTableDomAttrs(table: HTMLTableElement, node: ProseMirrorNode) {
  const name = typeof node.attrs.name === 'string' ? node.attrs.name : '';
  const id = typeof node.attrs.id === 'string' ? node.attrs.id : '';
  if (name) {
    table.setAttribute('data-name', name);
  } else {
    table.removeAttribute('data-name');
  }
  if (id) {
    table.setAttribute('data-id', id);
  } else {
    table.removeAttribute('data-id');
  }
  applyProtocolColWidths(table, node);
  applyProtocolBorder(table, node);
  lockEmrTableToContentWidth(table);
  if (node.attrs.splitLate === true) {
    table.setAttribute('data-split-late', 'true');
  } else {
    table.removeAttribute('data-split-late');
  }
}

class EmrTableView extends TableView {
  constructor(node: ProseMirrorNode, cellMinWidth: number) {
    super(node, cellMinWidth);
    syncEmrTableDomAttrs(this.table, node);
  }

  update(node: ProseMirrorNode) {
    const ok = super.update(node);
    if (ok) {
      syncEmrTableDomAttrs(this.table, node);
    }
    return ok;
  }
}

/** 扩展 Table：name/id + §5.1 列宽/边框/整行切页 */
export const EmrTable = Table.extend({
  addOptions() {
    return {
      ...this.parent?.(),
      View: EmrTableView,
    };
  },
  addAttributes() {
    return {
      ...this.parent?.(),
      name: emrNameAttr,
      id: emrIdAttr,
      colWidths: {
        default: null as unknown,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-col-widths'),
        renderHTML: () => {
          // TableView 负责写 colgroup，避免 renderHTML 丢在 wrapper 上
          return {};
        },
      },
      border: {
        default: null as null | boolean,
        parseHTML: (element: HTMLElement) => parseOptionalBoolAttr(element, 'data-emr-border'),
        renderHTML: () => ({}),
      },
      splitLate: {
        default: null as null | boolean,
        parseHTML: (element: HTMLElement) => parseOptionalBoolAttr(element, 'data-split-late'),
        renderHTML: () => ({}),
      },
    };
  },
  addProseMirrorPlugins() {
    return [
      ...(this.parent?.() ?? []),
      createEmrTableColWidthPlugin(() => this.editor?.view ?? null),
    ];
  },
});

/** 扩展 TableCell：name + §5.1 格布局 */
export const EmrTableCell = TableCell.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      name: emrNameAttr,
      ...cellLayoutAttrs(),
    };
  },
});

/** 表头格与数据格同一套布局 attrs */
export const EmrTableHeader = TableHeader.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      name: emrNameAttr,
      ...cellLayoutAttrs(),
    };
  },
});
