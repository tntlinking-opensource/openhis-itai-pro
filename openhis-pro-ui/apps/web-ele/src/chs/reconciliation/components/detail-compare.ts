/**
 * 3202 明细对账 Beyond Compare 式字段对照（本院本地回执 | 中心结果回写）。
 */

import type { DailyMismatchDetail } from './daily-ledger-types';

import { formatStmtRslt } from '#/chs/constants/reconciliation';

export type DetailCompareViewMode = 'list' | 'side';

/** 单行字段对照项 */
export interface DetailCompareField {
  key: string;
  label: string;
  localValue: string;
  platformValue: string;
  /** 左右值视为不一致，需高亮 */
  diff: boolean;
}

function dash(value?: string | null): string {
  const text = value?.trim();
  return text ? text : '—';
}

export function formatLocalStatus(status?: string): string {
  if (!status) return '—';
  if (status === 'CONFIRMED') return '有效';
  if (status === 'REVOKED') return '已撤销';
  return status;
}

export function formatRefdFlag(flag?: string): string {
  if (flag === '1') return '退费';
  if (flag === '0') return '正向';
  return '—';
}

function formatMoneyText(value?: number | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return '—';
  return value.toFixed(2);
}

/** 金额不等（两侧均有值且差值超容差） */
export function isAmountDiff(local?: number | null, platform?: number | null): boolean {
  const localMissing = local === undefined || local === null || Number.isNaN(local);
  const platformMissing = platform === undefined || platform === null || Number.isNaN(platform);
  // 旧缓存缺本地金额时不标 diff，避免误报
  if (localMissing || platformMissing) return false;
  return Math.abs(local - platform) > 0.005;
}

/** 本院状态 vs 中心退费标志是否冲突 */
export function isStatusKindDiff(row: DailyMismatchDetail): boolean {
  // 已归为可忽略的不标红
  if (row.kind === 'already_balanced') return false;
  if (row.localExists === false) return true;
  const status = row.localStatus?.trim();
  const flag = row.refdSetlFlag?.trim();
  // 无本地/中心状态时：仅「退费状态不一致」类保留为差异
  if (!status || !flag) {
    return row.kind === 'refund_status';
  }
  // 真正冲突：本院已撤销但中心仍正向，或本院有效但中心已退费
  if (status === 'REVOKED' && flag === '0') return true;
  if (status === 'CONFIRMED' && flag === '1') return true;
  return false;
}

/**
 * 按固定顺序生成本院|中心对照行。
 */
export function buildCompareFields(row: DailyMismatchDetail): DetailCompareField[] {
  const localMissing = row.localExists === false;

  const setlId = dash(row.setlId === '—' ? undefined : row.setlId);
  const mdtrtId = dash(row.mdtrtId);
  const psnNo = dash(row.psnNo);
  const psnNameLocal = localMissing ? '—' : dash(row.psnName);
  const setlTimeLocal = localMissing ? '—' : dash(row.setlTime);
  const statusLocal = localMissing ? '本院无此结算' : formatLocalStatus(row.localStatus);
  const statusPlatform = formatRefdFlag(row.refdSetlFlag);

  const localMedfee = formatMoneyText(row.localMedfeeSumamt);
  const localFund = formatMoneyText(row.localFundPaySumamt);
  const localAcct = formatMoneyText(row.localAcctPay);
  const platMedfee = formatMoneyText(row.medfeeSumamt);
  const platFund = formatMoneyText(row.fundPaySumamt);
  const platAcct = formatMoneyText(row.acctPay);

  return [
    {
      key: 'setlId',
      label: '结算号',
      localValue: localMissing ? '—' : setlId,
      platformValue: setlId,
      diff: localMissing,
    },
    {
      key: 'mdtrtId',
      label: '就诊号',
      localValue: localMissing ? '—' : mdtrtId,
      platformValue: mdtrtId,
      diff: localMissing,
    },
    {
      key: 'psnNo',
      label: '人员编号',
      localValue: localMissing ? '—' : psnNo,
      platformValue: psnNo,
      diff: localMissing,
    },
    {
      key: 'psnName',
      label: '人员姓名',
      localValue: psnNameLocal,
      platformValue: '—',
      diff: false,
    },
    {
      key: 'setlTime',
      label: '结算时间',
      localValue: setlTimeLocal,
      platformValue: '—',
      diff: false,
    },
    {
      key: 'status',
      label: '状态 / 退费标志',
      localValue: statusLocal,
      platformValue: statusPlatform,
      diff: isStatusKindDiff(row),
    },
    {
      key: 'medfee',
      label: '医疗费',
      localValue: localMissing ? '—' : localMedfee,
      platformValue: platMedfee,
      diff:
        !localMissing &&
        row.localMedfeeSumamt != null &&
        isAmountDiff(row.localMedfeeSumamt, row.medfeeSumamt),
    },
    {
      key: 'fund',
      label: '基金',
      localValue: localMissing ? '—' : localFund,
      platformValue: platFund,
      diff:
        !localMissing &&
        row.localFundPaySumamt != null &&
        isAmountDiff(row.localFundPaySumamt, row.fundPaySumamt),
    },
    {
      key: 'acct',
      label: '个账',
      localValue: localMissing ? '—' : localAcct,
      platformValue: platAcct,
      diff:
        !localMissing && row.localAcctPay != null && isAmountDiff(row.localAcctPay, row.acctPay),
    },
    {
      key: 'stmtRslt',
      label: '对账结果',
      localValue: '—',
      platformValue: formatStmtRslt(row.stmtRslt) || '—',
      diff: false,
    },
    {
      key: 'memo',
      label: '说明',
      localValue: '—',
      platformValue: dash(row.memo || row.kindLabel),
      // 可忽略类不再标红说明
      diff: row.kind !== 'already_balanced' && Boolean(row.memo || row.kindLabel),
    },
  ];
}
