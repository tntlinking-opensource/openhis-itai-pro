/**
 * 事前提醒试调 3101/3103 — 对接 {@code POST /api/app/insuranceDetailAuditTry/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceDetailAuditTry';

/** 3101 违规明细行 */
export interface AuditViolationDetail {
  jrdId?: string;
  patnId?: string;
  mdtrtId?: string;
  rxId?: string;
  volaItemType?: string;
  volaAmt?: number;
}

/** 3101 违规头 */
export interface AuditViolation {
  jrId?: string;
  ruleId?: string;
  ruleName?: string;
  volaCont?: string;
  patnId?: string;
  mdtrtId?: string;
  volaAmt?: number;
  volaAmtStas?: string;
  sevDeg?: string;
  volaEvid?: string;
  volaBhvrType?: string;
  details?: AuditViolationDetail[];
}

/** 3101 分析结果 */
export interface AuditAnalyzeResult {
  taskId?: string;
  trigScen?: string;
  contractBusNo?: string;
  violations?: AuditViolation[];
}

/** 从门诊就诊组装 3101 */
export interface DetailAuditAnalyzeFromEncounterParams {
  contractBusNo?: string;
  contractId?: number | string;
  encounterId: number | string;
  chargeItemIds?: Array<number | string>;
  mdtrtId: string;
  trigScen: string;
  syscode?: string;
  medinsType: string;
  medinsLv: string;
  brdy?: string;
}

/** 3103 单条反馈 */
export interface AuditFeedbackWarnParams {
  /** 填明细 jrdId（吉林 R7） */
  warnRsltId: string;
  dspoWay: string;
  dspoWayRea?: string;
  jrId?: string;
}

/** 3103 反馈命令 */
export interface AuditFeedback3103Params {
  contractBusNo?: string;
  contractId?: number | string;
  warnType: string;
  warns: AuditFeedbackWarnParams[];
}

async function postDetailAudit<T>(method: string, body: unknown): Promise<T> {
  const res = await requestClient.post<ApiResponse<T>>(
    `${BASE}/${method}`,
    body,
    chsLongRequestConfig(),
  );
  return res.data as T;
}

/** 【3101】手工/粘贴 Command 试调 */
export function analyze3101Api(command: Record<string, unknown>) {
  return postDetailAudit<AuditAnalyzeResult>('analyze3101', command);
}

/** 【3101】从门诊就诊自动组装后试调 */
export function analyze3101FromEncounterApi(params: DetailAuditAnalyzeFromEncounterParams) {
  return postDetailAudit<AuditAnalyzeResult>('analyze3101FromEncounter', params);
}

/** 【3103】反馈试调 */
export function feedback3103Api(params: AuditFeedback3103Params) {
  return postDetailAudit<void>('feedback3103', params);
}
