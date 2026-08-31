/**
 * 打印审计日志只读 — 对接 {@code POST /api/app/printLog/<method>}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/printLog';

/** 按业务主键回查已打印 */
export interface QueryPrintedKeysParams {
  scenarioCode: string;
  /** bizRef 主键字段名，默认 listRowKeys（支持数组或标量） */
  keyField?: string;
  keys: string[];
  /** 业务查询日 YYYY-MM-DD（过滤 bizRef.queryDate） */
  queryDate?: string;
  /** 业务区间起（与 bizDateTo 同时传，过滤 bizRef.dateFrom/dateTo） */
  bizDateFrom?: string;
  /** 业务区间止 */
  bizDateTo?: string;
}

export interface QueryPrintedKeysResult {
  printedKeys?: string[];
  lastPrintedAtByKey?: Record<string, string>;
}

/** 批量回查业务主键是否已有 SUCCESS 打印审计 */
export async function httpQueryPrintedKeys(
  body: QueryPrintedKeysParams,
): Promise<QueryPrintedKeysResult> {
  return publicRequestClient.post<QueryPrintedKeysResult>(`${BASE}/queryPrintedKeys`, body);
}
