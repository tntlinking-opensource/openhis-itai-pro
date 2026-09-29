/** 后端 Jackson 序列化 byte[] 为 base64 字符串时的导出响应形态。 */
export interface BinaryExportResult {
  fileName?: string;
  contentType?: string;
  /** 部分 AppService 使用 body 字段承载 xlsx 字节。 */
  body?: string;
  /** 部分 AppService 使用 fileBytes 字段承载 xlsx 字节。 */
  fileBytes?: string;
  /** 部分报表（结余留用/处方抽查/医保费用明细等）使用 content 字段。 */
  content?: string;
  exportedRowCount?: number;
  rowCount?: number;
  truncated?: boolean;
}

/** 触发浏览器下载 xlsx 等二进制导出文件。 */
export function downloadBinaryExportFile(
  result: BinaryExportResult,
  defaultFileName = 'export.xlsx',
): void {
  const base64 = (result.body ?? result.fileBytes ?? result.content)?.trim();
  if (!base64) {
    return;
  }
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    // atob 得到的是「每字符一字节」的 binary string，须用 charCodeAt，勿用 codePointAt
    bytes[i] = binary.charCodeAt(i) & 0xff;
  }
  const blob = new Blob([bytes], {
    type: result.contentType ?? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = result.fileName?.trim() || defaultFileName;
  link.click();
  URL.revokeObjectURL(url);
}
