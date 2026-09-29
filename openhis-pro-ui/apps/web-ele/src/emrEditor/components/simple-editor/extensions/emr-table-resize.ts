import type { Node as ProseMirrorNode } from '@tiptap/pm/model';
import { Plugin, PluginKey, type EditorState, type Transaction } from '@tiptap/pm/state';
import { TableMap, cellAround, colCount } from '@tiptap/pm/tables';
import type { EditorView } from '@tiptap/pm/view';

import { normalizeColShares, parseColWidths, rebalanceColShares } from './emr-table-layout';

const EMR_COL_WIDTHS_META = 'emrColWidths';

/** 两列份额是否已足够接近，避免拖一下反复写 JSON */
function sameShares(left: number[], right: number[]): boolean {
  if (left.length !== right.length) {
    return false;
  }
  for (let i = 0; i < left.length; i++) {
    if (Math.abs(left[i]! - right[i]!) > 0.05) {
      return false;
    }
  }
  return true;
}

/** 第一行按列展开的 colwidth px；没有则 null */
function firstRowColwidthPx(table: ProseMirrorNode, columnCount: number): Array<null | number> {
  const widths: Array<null | number> = Array.from({ length: columnCount }, () => null);
  const row = table.firstChild;
  if (!row) {
    return widths;
  }
  let col = 0;
  for (let i = 0; i < row.childCount && col < columnCount; i++) {
    const cell = row.child(i);
    const span = Math.max(1, Number(cell.attrs.colspan) || 1);
    const colwidth = cell.attrs.colwidth;
    for (let j = 0; j < span && col < columnCount; j++, col++) {
      const px = Array.isArray(colwidth) ? Number(colwidth[j]) : Number.NaN;
      widths[col] = Number.isFinite(px) && px > 0 ? px : null;
    }
  }
  return widths;
}

/** 任一格还留着 Tiptap 拖列写下的 px colwidth */
function tableHasPxColwidth(table: ProseMirrorNode): boolean {
  for (let r = 0; r < table.childCount; r++) {
    const row = table.child(r);
    for (let c = 0; c < row.childCount; c++) {
      const colwidth = row.child(c).attrs.colwidth;
      if (!Array.isArray(colwidth)) {
        continue;
      }
      if (colwidth.some((item) => typeof item === 'number' && item > 0)) {
        return true;
      }
    }
  }
  return false;
}

function sharesEqualEnough(existing: number[] | null, next: number[]): boolean {
  return existing != null && sameShares(existing, next);
}

/** 从 colgroup 量出当前视觉列宽，作为协议份额 */
function measureColSharesFromDom(
  view: EditorView,
  tablePos: number,
  columnCount: number,
): number[] | null {
  const dom = view.nodeDOM(tablePos);
  if (!(dom instanceof HTMLElement)) {
    return null;
  }
  const table = dom.nodeName === 'TABLE' ? (dom as HTMLTableElement) : dom.querySelector('table');
  if (!table) {
    return null;
  }
  const cols = table.querySelectorAll('colgroup > col');
  if (cols.length !== columnCount) {
    return null;
  }
  const widths: number[] = [];
  for (let i = 0; i < cols.length; i++) {
    const width = (cols[i] as HTMLElement).getBoundingClientRect().width;
    if (!Number.isFinite(width) || width <= 0) {
      return null;
    }
    widths.push(width);
  }
  return normalizeColShares(widths);
}

/** 把单元格上的 px colwidth 清掉，避免 TableView 再按像素撑表宽 */
function clearCellColwidth(tr: Transaction, tablePos: number, table: ProseMirrorNode) {
  const from = tablePos;
  const to = tablePos + table.nodeSize;
  tr.doc.nodesBetween(from, to, (node, absPos) => {
    if (node.type.name !== 'tableCell' && node.type.name !== 'tableHeader') {
      return true;
    }
    if (!node.attrs.colwidth) {
      return false;
    }
    tr.setNodeMarkup(absPos, undefined, { ...node.attrs, colwidth: null });
    return false;
  });
}

function selectedTableColIndex(state: EditorState): number | null {
  const $cell = cellAround(state.selection.$from);
  if (!$cell) {
    return null;
  }
  return colCount($cell);
}

/**
 * 拖列结束把 px colwidth 收成 table.attrs.colWidths；增删列时重算等长份额。
 * 量 DOM 用拖的时候 colgroup 上已经写好的宽度，避免把缺 px 的列当成 1。
 */
export function createEmrTableColWidthPlugin(getView: () => EditorView | null) {
  return new Plugin({
    key: new PluginKey(EMR_COL_WIDTHS_META),
    appendTransaction(transactions, oldState, newState) {
      if (transactions.some((item) => item.getMeta(EMR_COL_WIDTHS_META))) {
        return null;
      }
      if (!transactions.some((item) => item.docChanged)) {
        return null;
      }
      const view = getView();
      const colIndexBefore = selectedTableColIndex(oldState);
      const jobs: Array<{ hasPx: boolean; nextShares: number[]; pos: number }> = [];

      newState.doc.descendants((node, pos) => {
        if (node.type.name !== 'table') {
          return true;
        }
        const columnCount = TableMap.get(node).width;
        if (columnCount <= 0) {
          return false;
        }
        const existing = parseColWidths(node.attrs.colWidths);
        const hasPx = tableHasPxColwidth(node);
        const lengthMismatch = existing != null && existing.length !== columnCount;
        if (!hasPx && !lengthMismatch) {
          return false;
        }

        let nextShares: number[] | null = null;
        if (hasPx && view) {
          nextShares = measureColSharesFromDom(view, pos, columnCount);
        }
        if (!nextShares && hasPx) {
          const px = firstRowColwidthPx(node, columnCount);
          if (px.every((item): item is number => item != null)) {
            nextShares = normalizeColShares(px);
          } else if (existing && existing.length === columnCount) {
            nextShares = existing.slice();
          }
        }
        if (!nextShares && lengthMismatch && existing) {
          const shrinking = columnCount < existing.length;
          nextShares = rebalanceColShares(
            existing,
            columnCount,
            shrinking ? colIndexBefore : null,
            shrinking ? null : colIndexBefore,
          );
        }
        if (!nextShares) {
          return false;
        }
        if (sharesEqualEnough(existing, nextShares) && !hasPx) {
          return false;
        }
        jobs.push({ hasPx, nextShares, pos });
        return false;
      });

      if (jobs.length === 0) {
        return null;
      }
      const tr = newState.tr;
      for (let i = jobs.length - 1; i >= 0; i--) {
        const job = jobs[i]!;
        const current = tr.doc.nodeAt(job.pos);
        if (!current || current.type.name !== 'table') {
          continue;
        }
        const existing = parseColWidths(current.attrs.colWidths);
        if (!sharesEqualEnough(existing, job.nextShares)) {
          tr.setNodeMarkup(job.pos, undefined, {
            ...current.attrs,
            colWidths: job.nextShares,
          });
        }
        if (job.hasPx) {
          const tableAfter = tr.doc.nodeAt(job.pos);
          if (tableAfter) {
            clearCellColwidth(tr, job.pos, tableAfter);
          }
        }
      }
      if (!tr.docChanged) {
        return null;
      }
      return tr.setMeta(EMR_COL_WIDTHS_META, true);
    },
  });
}

/** 给 TableView 用：表永远锁在内容区宽度，不认 Tiptap 累加的 px */
export function lockEmrTableToContentWidth(table: HTMLTableElement) {
  table.style.width = '100%';
  table.style.maxWidth = '100%';
  table.style.minWidth = '';
  table.style.tableLayout = 'fixed';
}
