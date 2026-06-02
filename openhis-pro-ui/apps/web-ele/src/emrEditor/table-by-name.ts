import type { JSONContent } from '@tiptap/core';

function normName(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/** 从单元格节点提取纯文本 */
function extractTextFromCell(cell: JSONContent): string {
  const parts: string[] = [];
  const blocks = cell.content ?? [];
  for (const block of blocks) {
    const inlines = block.content ?? [];
    for (const inline of inlines) {
      if (inline.type === 'text' && typeof inline.text === 'string') {
        parts.push(inline.text);
      }
    }
  }
  return parts.join('').trim();
}

/**
 * 在文档 JSON 中按 data-name 查找 table 节点。
 */
export function findTableByName(doc: JSONContent, tableName: string): JSONContent | null {
  const want = normName(tableName);
  if (!want) {
    return null;
  }
  let hit: JSONContent | null = null;
  const walk = (node: JSONContent) => {
    if (hit) {
      return;
    }
    if (node.type === 'table' && normName(node.attrs?.name) === want) {
      hit = node;
      return;
    }
    for (const child of node.content ?? []) {
      walk(child);
    }
  };
  if (doc.type === 'doc') {
    for (const child of doc.content ?? []) {
      walk(child);
    }
  } else {
    walk(doc);
  }
  return hit;
}

/**
 * 按表名 + 单元格名读取单元格文本；未找到返回 null。
 */
export function getTableCellTextByName(
  doc: JSONContent,
  tableName: string,
  cellName: string,
): string | null {
  const table = findTableByName(doc, tableName);
  const wantCell = normName(cellName);
  if (!table?.content || !wantCell) {
    return null;
  }
  for (const row of table.content) {
    if (row.type !== 'tableRow' || !row.content) {
      continue;
    }
    for (const cell of row.content) {
      if (cell.type !== 'tableCell' && cell.type !== 'tableHeader') {
        continue;
      }
      if (normName(cell.attrs?.name) === wantCell) {
        return extractTextFromCell(cell);
      }
    }
  }
  return null;
}

/**
 * 列出表格内已命名单元格（name → 文本），无 name 的单元格跳过。
 */
export function listNamedTableCells(doc: JSONContent, tableName: string): Record<string, string> {
  const table = findTableByName(doc, tableName);
  const out: Record<string, string> = {};
  if (!table?.content) {
    return out;
  }
  for (const row of table.content) {
    if (row.type !== 'tableRow' || !row.content) {
      continue;
    }
    for (const cell of row.content) {
      if (cell.type !== 'tableCell' && cell.type !== 'tableHeader') {
        continue;
      }
      const name = normName(cell.attrs?.name);
      if (!name) {
        continue;
      }
      out[name] = extractTextFromCell(cell);
    }
  }
  return out;
}
