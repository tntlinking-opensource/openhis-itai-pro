/**
 * CRB 对照 CSV 导入解析（首行表头，逗号分隔）。
 */

/** 解析 CSV 文本为对象行（首行为表头）。 */
export function parseCrbCsvRows(text: string): Record<string, string>[] {
  const normalized = text.replace(/^\uFEFF/, '').trim();
  if (!normalized) {
    return [];
  }
  const lines = normalized.split(/\r?\n/).filter((line) => line.trim().length > 0);
  if (lines.length < 2) {
    return [];
  }
  const headers = splitCsvLine(lines[0]).map((h) => h.trim());
  const rows: Record<string, string>[] = [];
  // 从第二行起按表头装配字段
  for (let i = 1; i < lines.length; i += 1) {
    const cells = splitCsvLine(lines[i]);
    const row: Record<string, string> = {};
    for (let c = 0; c < headers.length; c += 1) {
      const key = headers[c];
      if (!key) {
        continue;
      }
      row[key] = (cells[c] ?? '').trim();
    }
    rows.push(row);
  }
  return rows;
}

/** 按逗号拆分一行（简易实现，不支持引号转义嵌套）。 */
function splitCsvLine(line: string): string[] {
  return line.split(',');
}
