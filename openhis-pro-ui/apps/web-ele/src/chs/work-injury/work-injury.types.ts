/** 工伤 invoke prepare 载荷（中性结构，对齐 health-integration-contracts）。 */
export interface PrepareWorkInjuryInvokeResult {
  sessionId?: string;
  stepToken?: string;
  infno?: string;
  fixmedinsCode?: string;
  operatorNo?: string;
  input?: Record<string, unknown>;
  nextAction?: string;
  errMsg?: string;
}

export interface PrepareWorkInjuryInvokeRequest {
  phase: string;
  sessionId?: string;
  encounterId?: string | number;
  contractId?: string | number;
  paymentDraftId?: string | number;
  operatorId?: string | number;
  context?: Record<string, unknown>;
}

export interface SubmitWorkInjuryInvokeResultRequest {
  sessionId: string;
  stepToken: string;
  success: boolean;
  infcode?: string;
  errMsg?: string;
  output?: Record<string, unknown>;
  winMsgid?: string;
  context?: Record<string, unknown>;
  /** Win 实际发送的 inputdata JSON。 */
  winRawInput?: string;
}

export interface SubmitWorkInjuryInvokeResultResponse {
  sessionId?: string;
  nextAction?: string;
  errMsg?: string;
  summary?: string;
  settlementRecordId?: string | number;
  setlId?: string;
  chrgBchno?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  psnCashPay?: number | string;
}

/** OpenHIS.Win /api/work-injury/invoke 响应。 */
export interface WorkInjuryWinInvokeResponse {
  success?: boolean;
  infcode?: string;
  errMsg?: string;
  output?: Record<string, unknown> | unknown;
  signNo?: string;
  msgid?: string;
  /** Win 组装的完整 inputdata 字符串。 */
  rawInput?: string;
}

export interface WorkInjurySettlementRecordResult {
  settlementRecordId?: string | number;
  paymentId?: string | number;
  preSettleId?: string | number;
  contractId?: string | number;
  chargeBatchNo?: string;
  settlementId?: string;
  encounterPlatformId?: string;
  status?: string;
  msgid2207?: string;
}

export interface WorkInjuryReverseCommand {
  settlementRecordId: string | number;
  paymentId?: string | number;
  encounterId?: string | number;
  contractId?: string | number;
  operatorId?: string | number;
}

export type WorkInjuryPhaseCode =
  | 'REG_QUERY'
  | 'REG_APPROVAL'
  | 'OP_SETTLE'
  | 'OP_REVERSE'
  | 'RECON';

/** 【2301】审批上传 input 预览请求（不发 Win）。 */
export interface BuildWorkInjury2301PayloadRequest {
  encounterId: string | number;
  contractId?: string | number;
  approveType?: string;
  startDate?: string;
  endDate?: string;
  terminReason?: string;
  injureAffirmId?: string;
  orgCode?: string;
  orgName?: string;
}

export interface BuildWorkInjury2301PayloadResponse {
  input?: Record<string, unknown>;
}

/** 就诊工伤备案/审批上下文（loadEncounterContext）。 */
export interface WorkInjuryEncounterContextResult {
  workInjuryEncounter?: boolean;
  qualificationValid?: boolean;
  approvalStatusLabel?: string;
  contractId?: string | number;
  needs2301Upload?: boolean;
  psnNo?: string;
  injureAffirmId?: string;
  qualificationId?: string;
  qualificationStatus?: string;
  medType?: string;
}
