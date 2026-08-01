/**
 * 日对账左侧费用行与明细差异行类型（对接 HIS 汇总 / 3201 / 3202）。
 */

export const RECON_STATUS = {
  PENDING: 'pending',
  BALANCED: 'balanced',
  UNBALANCED: 'unbalanced',
} as const;

export type ReconStatusCode = (typeof RECON_STATUS)[keyof typeof RECON_STATUS];

export const RECON_STATUS_LABEL: Record<ReconStatusCode, string> = {
  pending: '未对账',
  balanced: '平',
  unbalanced: '不平',
};

/** 日对账表格行：HIS 本地金额 + 对账状态 */
export interface DailyFeeRow {
  id: string;
  reconDate: string;
  insutype: string;
  insutypeText: string;
  clrType: string;
  clrTypeText: string;
  medfeeSumamt: number;
  fundPaySumamt: number;
  cashPayamt: number;
  acctPay: number;
  setlCount: number;
  status: ReconStatusCode;
  resultDesc?: string;
  localRecordId?: number | string;
}

/** 3202 明细对账差异行（平台回写） */
export interface DailyMismatchDetail {
  setlId: string;
  psnNo?: string;
  mdtrtId?: string;
  stmtRslt?: string;
  memo?: string;
  medfeeSumamt?: number;
  fundPaySumamt?: number;
  acctPay?: number;
}
