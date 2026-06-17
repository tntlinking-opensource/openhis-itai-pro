import type { LogEventRequest } from '#/api/report/report-print-log';

import { reportPrintLogEventApi } from '#/api/report/report-print-log';

/**
 * 写入打印日志（不阻塞主流程；失败仅 console.warn）。
 *
 * @returns 服务端 eventId，供 parentEventId 串联 preview → print
 */
export async function writeReportPrintLog(request: LogEventRequest): Promise<number | undefined> {
  try {
    const res = await reportPrintLogEventApi({
      ...request,
      source: request.source ?? 'WEB',
    });
    const id = res?.eventId;
    return id != null ? Number(id) : undefined;
  } catch (error) {
    console.warn('[reportPrintLog] logEvent failed', error);
    return undefined;
  }
}
