/**
 * 打印事件日志 — 对接 {@code POST /api/app/reportPrintLog/*}
 */
import { publicRequestClient } from '#/api/request';

export interface LogEventRequest {
  eventType: string;
  targetType: string;
  targetCode: string;
  templateVersion?: number;
  datasetCode?: string;
  bizRef?: Record<string, unknown>;
  copies?: number;
  status: 'SUCCESS' | 'FAILED';
  errorCode?: string;
  errorMessage?: string;
  contentHash?: string;
  contentType?: string;
  printerName?: string;
  clientJobId?: string;
  winDurationMs?: number;
  renderDurationMs?: number;
  parentEventId?: number;
  source?: string;
}

export interface LogEventResponse {
  eventId?: number;
}

export interface PrintEventLogItemDto {
  id?: number;
  eventType?: string;
  targetType?: string;
  targetCode?: string;
  templateVersion?: number;
  datasetCode?: string;
  bizRef?: Record<string, unknown>;
  operatorId?: number;
  operatorName?: string;
  organizationId?: number;
  copies?: number;
  status?: string;
  errorCode?: string;
  errorMessage?: string;
  contentHash?: string;
  contentType?: string;
  printerName?: string;
  clientJobId?: string;
  winDurationMs?: number;
  renderDurationMs?: number;
  source?: string;
  parentEventId?: number;
  createdAt?: string;
}

export interface PrintDailySummaryItemDto {
  statDate?: string;
  operatorId?: number;
  operatorName?: string;
  targetType?: string;
  targetCode?: string;
  eventType?: string;
  successCount?: number;
  failCount?: number;
  totalCopies?: number;
  lastEventAt?: string;
}

export interface PagedPrintLogResult<T> {
  content?: T[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface QueryPrintEventLogParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  eventType?: string;
  status?: string;
  targetCode?: string;
  targetType?: string;
  createdFrom?: string;
  createdTo?: string;
}

export interface QueryPrintDailySummaryParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  eventType?: string;
  targetCode?: string;
  statDateFrom?: string;
  statDateTo?: string;
}

export async function reportPrintLogEventApi(params: LogEventRequest) {
  return publicRequestClient.post<LogEventResponse>('/reportPrintLog/logEvent', params);
}

export async function queryPrintEventLogPageApi(params: QueryPrintEventLogParams) {
  return publicRequestClient.post<PagedPrintLogResult<PrintEventLogItemDto>>(
    '/reportPrintLog/queryPage',
    params,
  );
}

export async function queryPrintDailySummaryApi(params: QueryPrintDailySummaryParams) {
  return publicRequestClient.post<PagedPrintLogResult<PrintDailySummaryItemDto>>(
    '/reportPrintLog/queryDailySummary',
    params,
  );
}
