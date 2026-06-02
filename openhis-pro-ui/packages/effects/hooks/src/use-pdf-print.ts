/**
 * PDF 打印 Composable
 * @description 用于打印后端返回的 PDF 文件
 */

import type { Ref } from 'vue';

import type { PdfPrintOptions } from '@vben/utils';

import { ref } from 'vue';

import {
  downloadPdf,
  downloadPdfBlob,
  printPdfFromArrayBuffer,
  printPdfFromBase64,
  printPdfFromBlob,
  printPdfFromResponse,
  printPdfFromUrl,
} from '@vben/utils';

/**
 * PDF 打印 Composable 返回值
 */
export interface UsePdfPrintReturn {
  /** 是否正在打印 */
  isPrinting: Ref<boolean>;
  /** 从 URL 打印 */
  printFromUrl: (url: string, options?: PdfPrintOptions) => void;
  /** 从 Blob 打印 */
  printFromBlob: (blob: Blob, options?: PdfPrintOptions) => void;
  /** 从 Base64 打印 */
  printFromBase64: (base64: string, options?: PdfPrintOptions) => void;
  /** 从 ArrayBuffer 打印 */
  printFromArrayBuffer: (arrayBuffer: ArrayBuffer, options?: PdfPrintOptions) => void;
  /** 从 Response 打印 */
  printFromResponse: (response: Response, options?: PdfPrintOptions) => Promise<void>;
  /** 下载 PDF */
  download: (url: string, fileName?: string) => void;
  /** 下载 Blob PDF */
  downloadBlob: (blob: Blob, fileName?: string) => void;
}

/**
 * 使用 PDF 打印
 */
export function usePdfPrint(): UsePdfPrintReturn {
  const isPrinting = ref(false);

  // 从 URL 打印
  const printFromUrl = (url: string, options?: PdfPrintOptions): void => {
    isPrinting.value = true;
    try {
      printPdfFromUrl(url, options);
    } finally {
      // 延迟重置状态，确保打印窗口已打开
      setTimeout(() => {
        isPrinting.value = false;
      }, 500);
    }
  };

  // 从 Blob 打印
  const printFromBlob = (blob: Blob, options?: PdfPrintOptions): void => {
    isPrinting.value = true;
    try {
      printPdfFromBlob(blob, options);
    } finally {
      setTimeout(() => {
        isPrinting.value = false;
      }, 500);
    }
  };

  // 从 Base64 打印
  const printFromBase64 = (base64: string, options?: PdfPrintOptions): void => {
    isPrinting.value = true;
    try {
      printPdfFromBase64(base64, options);
    } finally {
      setTimeout(() => {
        isPrinting.value = false;
      }, 500);
    }
  };

  // 从 ArrayBuffer 打印
  const printFromArrayBuffer = (arrayBuffer: ArrayBuffer, options?: PdfPrintOptions): void => {
    isPrinting.value = true;
    try {
      printPdfFromArrayBuffer(arrayBuffer, options);
    } finally {
      setTimeout(() => {
        isPrinting.value = false;
      }, 500);
    }
  };

  // 从 Response 打印
  const printFromResponse = async (
    response: Response,
    options?: PdfPrintOptions,
  ): Promise<void> => {
    isPrinting.value = true;
    try {
      await printPdfFromResponse(response, options);
    } finally {
      setTimeout(() => {
        isPrinting.value = false;
      }, 500);
    }
  };

  // 下载 PDF
  const download = (url: string, fileName?: string): void => {
    downloadPdf(url, fileName);
  };

  // 下载 Blob PDF
  const downloadBlob = (blob: Blob, fileName?: string): void => {
    downloadPdfBlob(blob, fileName);
  };

  return {
    isPrinting,
    printFromUrl,
    printFromBlob,
    printFromBase64,
    printFromArrayBuffer,
    printFromResponse,
    download,
    downloadBlob,
  };
}
