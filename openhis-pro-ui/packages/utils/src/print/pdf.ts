/**
 * PDF 打印工具
 * @description 用于打印后端返回的 PDF 文件
 */

/**
 * PDF 打印选项
 */
export interface PdfPrintOptions {
  /** 打印标题 */
  title?: string;
  /** 是否在新窗口打开 */
  newWindow?: boolean;
  /** 是否自动打印 */
  autoPrint?: boolean;
}

/**
 * 从 URL 打印 PDF
 * @param url PDF 文件 URL
 * @param options 打印选项
 */
export function printPdfFromUrl(url: string, options?: PdfPrintOptions): void {
  if (!url) {
    console.warn('PDF URL 不能为空');
    return;
  }

  try {
    const printWindow = window.open(url, options?.newWindow === false ? '_self' : '_blank');

    if (!printWindow) {
      console.error('无法打开打印窗口，请检查浏览器弹窗设置');
      return;
    }

    if (options?.autoPrint !== false) {
      printWindow.addEventListener('load', () => {
        setTimeout(() => {
          printWindow.print();
        }, 250);
      });
    }
  } catch (error) {
    console.error('打印 PDF 失败:', error);
  }
}

/**
 * 从 Blob 打印 PDF
 * @param blob PDF Blob 对象
 * @param options 打印选项
 */
export function printPdfFromBlob(blob: Blob, options?: PdfPrintOptions): void {
  if (!blob) {
    console.warn('PDF Blob 不能为空');
    return;
  }

  try {
    const url = URL.createObjectURL(blob);
    printPdfFromUrl(url, options);

    // 清理 URL 对象（延迟清理，确保打印完成）
    setTimeout(() => {
      URL.revokeObjectURL(url);
    }, 1000);
  } catch (error) {
    console.error('打印 PDF Blob 失败:', error);
  }
}

/**
 * 从 Base64 打印 PDF
 * @param base64 Base64 编码的 PDF 数据
 * @param options 打印选项
 */
export function printPdfFromBase64(base64: string, options?: PdfPrintOptions): void {
  if (!base64) {
    console.warn('PDF Base64 数据不能为空');
    return;
  }

  try {
    // 确保 base64 数据格式正确
    const base64Data = base64.startsWith('data:')
      ? base64
      : `data:application/pdf;base64,${base64}`;

    // 将 base64 转换为 blob
    const byteCharacters = atob(base64Data.split(',')[1] || base64);
    const byteNumbers: number[] = [];
    for (let i = 0; i < byteCharacters.length; i++) {
      byteNumbers.push(byteCharacters.codePointAt(i) ?? 0);
    }
    const byteArray = new Uint8Array(byteNumbers);
    const blob = new Blob([byteArray], { type: 'application/pdf' });

    printPdfFromBlob(blob, options);
  } catch (error) {
    console.error('打印 PDF Base64 失败:', error);
  }
}

/**
 * 从 ArrayBuffer 打印 PDF
 * @param arrayBuffer PDF ArrayBuffer
 * @param options 打印选项
 */
export function printPdfFromArrayBuffer(arrayBuffer: ArrayBuffer, options?: PdfPrintOptions): void {
  if (!arrayBuffer) {
    console.warn('PDF ArrayBuffer 不能为空');
    return;
  }

  try {
    const blob = new Blob([arrayBuffer], { type: 'application/pdf' });
    printPdfFromBlob(blob, options);
  } catch (error) {
    console.error('打印 PDF ArrayBuffer 失败:', error);
  }
}

/**
 * 从 Response 打印 PDF
 * @param response Fetch Response 对象
 * @param options 打印选项
 */
export async function printPdfFromResponse(
  response: Response,
  options?: PdfPrintOptions,
): Promise<void> {
  if (!response) {
    console.warn('Response 不能为空');
    return;
  }

  try {
    const blob = await response.blob();
    printPdfFromBlob(blob, options);
  } catch (error) {
    console.error('打印 PDF Response 失败:', error);
  }
}

/**
 * 下载 PDF 文件
 * @param url PDF 文件 URL
 * @param fileName 文件名
 */
export function downloadPdf(url: string, fileName?: string): void {
  if (!url) {
    console.warn('PDF URL 不能为空');
    return;
  }

  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName || 'download.pdf';
    link.target = '_blank';
    document.body.append(link);
    link.click();
    link.remove();
  } catch (error) {
    console.error('下载 PDF 失败:', error);
  }
}

/**
 * 下载 Blob PDF
 * @param blob PDF Blob 对象
 * @param fileName 文件名
 */
export function downloadPdfBlob(blob: Blob, fileName?: string): void {
  if (!blob) {
    console.warn('PDF Blob 不能为空');
    return;
  }

  try {
    const url = URL.createObjectURL(blob);
    downloadPdf(url, fileName);

    // 清理 URL 对象
    setTimeout(() => {
      URL.revokeObjectURL(url);
    }, 100);
  } catch (error) {
    console.error('下载 PDF Blob 失败:', error);
  }
}
