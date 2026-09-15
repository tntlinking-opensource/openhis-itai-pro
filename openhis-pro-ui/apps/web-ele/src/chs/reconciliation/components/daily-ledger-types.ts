/**
 * 日对账左侧费用行与明细差异行类型（对接 HIS 汇总 / 3201 / 3202）。
 */

import type { DetailMismatchKind } from '#/chs/constants/reconciliation';

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
  /** 对账开始日 yyyy-MM-dd（查询区间起点） */
  stmtBegndate: string;
  /** 对账结束日 yyyy-MM-dd（查询区间终点） */
  stmtEnddate: string;
  insutype: string;
  insutypeText: string;
  clrType: string;
  clrTypeText: string;
  medfeeSumamt: number;
  fundPaySumamt: number;
  cashPayamt: number;
  acctPay: number;
  /** 3201 笔数（可按平台明细改；本地缺收退测试单时往往小于平台） */
  setlCount: number;
  /** 查询时本地明细/汇总笔数快照，便于对照手工改笔数 */
  localSetlCount: number;
  status: ReconStatusCode;
  resultDesc?: string;
  localRecordId?: number | string;
}

/** 3202 明细对账差异行（平台回写） */
export interface DailyMismatchDetail {
  setlId: string;
  psnNo?: string;
  /** 人员姓名（本地补齐） */
  psnName?: string;
  mdtrtId?: string;
  /** 结算时间（本地补齐） */
  setlTime?: string;
  /** 本地状态 CONFIRMED / REVOKED */
  localStatus?: string;
  /** 本地是否命中结算回执 */
  localExists?: boolean;
  /** 本地医疗费 / 基金 / 个账 */
  localMedfeeSumamt?: number;
  localFundPaySumamt?: number;
  localAcctPay?: number;
  /** 平台退费标志 0/1 */
  refdSetlFlag?: string;
  stmtRslt?: string;
  /** 平台原始说明 */
  memo?: string;
  /** 归类后的短说明（弹窗主展示） */
  kindLabel?: string;
  /** 差异分类 */
  kind?: DetailMismatchKind;
  medfeeSumamt?: number;
  fundPaySumamt?: number;
  acctPay?: number;
}
