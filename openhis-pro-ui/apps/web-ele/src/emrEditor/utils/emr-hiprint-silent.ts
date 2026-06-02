import { ElMessage } from 'element-plus';

import { isHiprintAutoConnectDisabled } from '#/utils/hiprint-connect-pref';

export interface EmrSilentPrintOptions {
  /** 任务 ID，用于 electron-hiprint 回调与日志 */
  templateId: string;
  /** 打印机名称，空则使用客户端默认打印机 */
  printer?: string;
  /** 打印份数 */
  copies?: number;
}

export interface EmrSilentPdfPrintOptions extends EmrSilentPrintOptions {
  /** 纸张，如 A4（blob_pdf） */
  paperName?: string;
}

type HiprintHiwebSocket = {
  host?: string;
  opened?: boolean;
  send?: (payload: Record<string, unknown>) => void;
  socket?: {
    connected?: boolean;
    emit?: (event: string, payload: Record<string, unknown>) => void;
  };
};

function getHiwebSocket(): HiprintHiwebSocket | null {
  if (typeof window === 'undefined') return null;
  const hiprint = window.hiprint ?? window.$hiprint?.hiprint;
  const ws = (hiprint as { hiwebSocket?: HiprintHiwebSocket } | undefined)?.hiwebSocket;
  return ws ?? null;
}

function isHiwebSocketConnected(ws: HiprintHiwebSocket): boolean {
  if (ws.opened === true) return true;
  if (ws.socket?.connected === true) return true;
  return false;
}

function ensureHiwebSocketReady(): HiprintHiwebSocket | null {
  if (isHiprintAutoConnectDisabled()) {
    ElMessage.warning('请先在「个人中心 → 客户端」关闭「屏蔽 Hiprint 本地打印连接」');
    return null;
  }

  const ws = getHiwebSocket();
  if (!ws?.send && !ws?.socket?.emit) {
    ElMessage.error('Hiprint 未加载，请刷新页面后重试');
    return null;
  }

  if (!isHiwebSocketConnected(ws)) {
    const host = ws.host || 'http://localhost:17521';
    ElMessage.error(`未连接打印客户端（${host}），请安装并运行 electron-hiprint`);
    window.open('hiprint://', '_blank', 'noopener,noreferrer');
    return null;
  }

  return ws;
}

function dispatchToPrintClient(ws: HiprintHiwebSocket, payload: Record<string, unknown>): void {
  if (ws.send) {
    ws.send(payload);
    return;
  }
  ws.socket?.emit?.('news', payload);
}

/**
 * 通过 electron-hiprint 静默打印 PDF（type: blob_pdf）
 */
export function sendEmrPdfSilentPrint(pdfBlob: Blob, options: EmrSilentPdfPrintOptions): boolean {
  const ws = ensureHiwebSocketReady();
  if (!ws) return false;

  if (!pdfBlob || pdfBlob.size === 0) {
    ElMessage.warning('PDF 数据为空，无法打印');
    return false;
  }

  const templateId = options.templateId.trim();
  if (!templateId) {
    ElMessage.warning('打印任务 ID 无效');
    return false;
  }

  try {
    dispatchToPrintClient(ws, {
      type: 'blob_pdf',
      pdf_blob: pdfBlob,
      templateId,
      printer: options.printer ?? '',
      paperName: options.paperName ?? 'A4',
      silent: true,
      printDialog: false,
      copies: options.copies ?? 1,
    });
    ElMessage.success('已发送静默打印任务，请在打印客户端查看进度');
    return true;
  } catch (e) {
    ElMessage.error((e as Error)?.message || '静默打印发送失败');
    return false;
  }
}

/**
 * 通过 electron-hiprint 静默打印 HTML（编辑器所见内容）
 */
export function sendEmrHtmlSilentPrint(html: string, options: EmrSilentPrintOptions): boolean {
  const ws = ensureHiwebSocketReady();
  if (!ws) return false;

  const content = html.trim();
  if (!content) {
    ElMessage.warning('打印内容为空');
    return false;
  }

  const templateId = options.templateId.trim();
  if (!templateId) {
    ElMessage.warning('打印任务 ID 无效');
    return false;
  }

  try {
    dispatchToPrintClient(ws, {
      html: content,
      templateId,
      printer: options.printer ?? '',
      silent: true,
      printBackground: true,
      printDialog: false,
      copies: options.copies ?? 1,
      pageSize: 'A4',
    });
    ElMessage.success('已发送编辑器静默打印任务，请在打印客户端查看进度');
    return true;
  } catch (e) {
    ElMessage.error((e as Error)?.message || '编辑器静默打印发送失败');
    return false;
  }
}

export function buildEmrSilentPrintTemplateId(prefix: string, id?: string): string {
  const docPart = (id ?? 'unknown').trim() || 'unknown';
  return `emr-${prefix}-${docPart}-${Date.now()}`;
}
