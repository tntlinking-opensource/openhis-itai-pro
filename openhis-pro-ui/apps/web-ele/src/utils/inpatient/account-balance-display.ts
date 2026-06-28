/**
 * 住院账户余额展示（与后端 InpatientAccountBalanceDisplayAssembler 对齐：卡片/队列仅展示单一余额）。
 */

/** 账户余额展示字段（队列/摘要 DTO 共有）。 */
export interface InpatientAccountBalanceDisplayFields {
  accountBalance?: null | number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: null | number | string;
  lowBalanceWarning?: boolean;
  pendingSettlementTotal?: null | number | string;
}

function formatAmount(value?: null | number | string): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isFinite(num)) {
    return `${num.toFixed(2)} 元`;
  }
  return String(value);
}

/** 主展示文案：优先后端 summaryText，否则回退金额格式化。 */
export function formatInpatientAccountBalancePrimaryText(
  row?: InpatientAccountBalanceDisplayFields | null,
): string {
  if (!row) {
    return '—';
  }
  const summary = (row.accountBalanceSummaryText ?? '').trim();
  if (summary) {
    return summary;
  }
  return formatAmount(row.accountBalance ?? row.availableBalance);
}

/** 副展示文案（当前产品不展示，恒为 undefined）。 */
export function formatInpatientAccountBalanceSecondaryText(
  _row?: InpatientAccountBalanceDisplayFields | null,
): string | undefined {
  return undefined;
}

/** 余额区域标签：统一为「余额」。 */
export function resolveInpatientAccountBalanceLabel(
  _row?: InpatientAccountBalanceDisplayFields | null,
): string {
  return '余额';
}
