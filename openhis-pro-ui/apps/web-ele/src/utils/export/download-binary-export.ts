/** 后端 Jackson 序列化 byte[] 为 base64 字符串时的导出响应形态。 */
export interface BinaryExportResult {
  fileName?: string;
  contentType?: string;
  body?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

/** 触发浏览器下载 xlsx 等二进制导出文件。 */
export function downloadBinaryExportFile(
  result: BinaryExportResult,
  defaultFileName = 'export.xlsx',
): void {
  const base64 = result.body?.trim();
  if (!base64) {
    return;
  }
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.codePointAt(i) ?? 0;
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
