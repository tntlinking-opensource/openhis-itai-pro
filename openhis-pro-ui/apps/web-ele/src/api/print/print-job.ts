/**
 * 打印任务运行时 — 对接 {@code POST /api/app/printJob/<method>}（IF-BAS-PRT-31～32）
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/printJob';

export interface PreparePrintParams {
  /** 业务场景码（与 busNo 二选一） */
  scenarioCode?: string;
  /** 模板 busNo（管理/debug 用，与 scenarioCode 二选一） */
  busNo?: string;
}

export interface PreparePrintResult {
  traceId?: string;
  busNo?: string;
  scenarioCode?: string;
  templateVersion?: number;
  PrintJson?: Record<string, unknown>;
  samplePrintData?: Record<string, unknown>;
}

export interface ConfirmPrintParams {
  traceId: string;
  busNo: string;
  scenarioCode?: string;
  templateVersion?: number;
  status: 'FAILED' | 'SUCCESS';
  printer?: string;
  copies?: number;
  printData?: Record<string, unknown>;
  bizRef?: Record<string, unknown>;
  clientType?: string;
  errorCode?: string;
  errorMessage?: string;
}

export interface ConfirmPrintResult {
  ok?: boolean;
}

/** 打印准备：按 scenarioCode 或 busNo 拉取已发布模板 */
export async function preparePrintApi(params: PreparePrintParams) {
  return publicRequestClient.post<PreparePrintResult>(`${BASE}/preparePrint`, params);
}

/** 打印确认：落库审计日志 */
export async function confirmPrintApi(params: ConfirmPrintParams) {
  return publicRequestClient.post<ConfirmPrintResult>(`${BASE}/confirmPrint`, params);
}
