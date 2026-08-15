/** 3201 / 3203A 常用字典（吉林省基线） */

export const YB_INSUTYPE_OPTIONS = [
  { label: '职工基本医疗保险', value: '310' },
  { label: '城乡居民基本医疗保险', value: '390' },
  { label: '离休人员医疗保障', value: '340' },
] as const;

export const YB_CLR_TYPE_OPTIONS = [
  { label: '门诊', value: '11' },
  { label: '住院', value: '21' },
  { label: '药店购药', value: '41' },
] as const;

/** 3205A 清算申请状态 */
export const YB_CLR_STAS_OPTIONS = [
  { label: '已申请', value: '10' },
  { label: '已受理', value: '20' },
  { label: '已清算', value: '50' },
] as const;

const INSUTYPE_LABEL = Object.fromEntries(
  YB_INSUTYPE_OPTIONS.map((item) => [item.value, item.label]),
);
const CLR_TYPE_LABEL = Object.fromEntries(
  YB_CLR_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);
const CLR_STAS_LABEL = Object.fromEntries(
  YB_CLR_STAS_OPTIONS.map((item) => [item.value, item.label]),
);

export function formatInsutype(code?: string | null): string {
  if (!code) return '—';
  return INSUTYPE_LABEL[code] ?? code;
}

/** 医生站卡片短标签：310→职工，390→居民；未知码不展示 */
export function formatInsutypeShort(code?: string | null): string {
  const c = (code ?? '').trim();
  if (c === '310') return '职工';
  if (c === '390') return '居民';
  return '';
}

export function formatClrType(code?: string | null): string {
  if (!code) return '—';
  return CLR_TYPE_LABEL[code] ?? code;
}

export function formatClrStas(code?: string | null): string {
  if (!code) return '—';
  return CLR_STAS_LABEL[code] ?? code;
}

/** 3201 对账结果 stmt_rslt */
export function formatStmtRslt(code?: string | null): string {
  if (!code) return '—';
  if (code === '0') return '平';
  if (code === '1') return '不平';
  return code;
}

export function isStmtBalanced(code?: string | null): boolean {
  return code === '0';
}

/** 3202 明细差异分类（按平台 memo 归类，便于弹窗筛选） */
export const DETAIL_MISMATCH_KIND = {
  REFUND_STATUS: 'refund_status',
  MISSING: 'missing',
  ALREADY_BALANCED: 'already_balanced',
  OTHER: 'other',
} as const;

export type DetailMismatchKind = (typeof DETAIL_MISMATCH_KIND)[keyof typeof DETAIL_MISMATCH_KIND];

export const DETAIL_MISMATCH_KIND_LABEL: Record<DetailMismatchKind, string> = {
  refund_status: '退费状态不一致',
  missing: '中心无此结算',
  already_balanced: '已平或重报（可忽略）',
  other: '其他差异',
};

export function formatDetailKindLabel(kind?: DetailMismatchKind | null): string {
  if (!kind) return '其他差异';
  return DETAIL_MISMATCH_KIND_LABEL[kind] ?? '其他差异';
}

/** 需人工关注（非已平重报噪声） */
export function isDetailAttention(kind?: DetailMismatchKind | null): boolean {
  return kind != null && kind !== DETAIL_MISMATCH_KIND.ALREADY_BALANCED;
}

function isAllZeroAmounts(amounts?: {
  medfeeSumamt?: number | null;
  fundPaySumamt?: number | null;
  acctPay?: number | null;
}): boolean {
  if (!amounts) return true;
  const med = Number(amounts.medfeeSumamt ?? 0);
  const fund = Number(amounts.fundPaySumamt ?? 0);
  const acct = Number(amounts.acctPay ?? 0);
  return med === 0 && fund === 0 && acct === 0;
}

/**
 * 按 3202 结果 memo + 金额 + 本院/中心退费语义归类明细差异。
 * <p>「不存在…或对账结果已平」且金额全 0：多为已平后重对噪声。</p>
 * <p>「退费状态不一致」但本院已撤销且中心退费标志=1（或有效+正向）：语义一致，可忽略。</p>
 */
export function classifyDetailMemo(
  memo?: string | null,
  amounts?: {
    medfeeSumamt?: number | null;
    fundPaySumamt?: number | null;
    acctPay?: number | null;
  },
  statusCtx?: {
    localStatus?: string | null;
    refdSetlFlag?: string | null;
    /** 本院与中心金额是否一致；缺省视为一致（仅按状态语义判断） */
    amountsMatch?: boolean;
  },
): DetailMismatchKind {
  const text = (memo ?? '').trim();
  if (text.includes('退费状态')) {
    const status = statusCtx?.localStatus?.trim();
    const flag = statusCtx?.refdSetlFlag?.trim();
    const amountsMatch = statusCtx?.amountsMatch !== false;
    // 本院已撤销 + 中心退费 = 同义；本院有效 + 中心正向 = 同义
    if (amountsMatch && status === 'REVOKED' && flag === '1') {
      return DETAIL_MISMATCH_KIND.ALREADY_BALANCED;
    }
    if (amountsMatch && status === 'CONFIRMED' && flag === '0') {
      return DETAIL_MISMATCH_KIND.ALREADY_BALANCED;
    }
    return DETAIL_MISMATCH_KIND.REFUND_STATUS;
  }
  const zero = isAllZeroAmounts(amounts);
  const hasAlreadyFlat =
    text.includes('对账结果已平') || text.includes('对账平') || text.includes('对照平');
  const hasMissing = text.includes('不存在');
  // 金额全 0 的「不存在/已平」：可忽略重报
  if (zero && (hasAlreadyFlat || hasMissing)) {
    return DETAIL_MISMATCH_KIND.ALREADY_BALANCED;
  }
  // 有金额且中心无此结算
  if (hasMissing && !zero) {
    return DETAIL_MISMATCH_KIND.MISSING;
  }
  if (hasAlreadyFlat) {
    return DETAIL_MISMATCH_KIND.ALREADY_BALANCED;
  }
  return DETAIL_MISMATCH_KIND.OTHER;
}
