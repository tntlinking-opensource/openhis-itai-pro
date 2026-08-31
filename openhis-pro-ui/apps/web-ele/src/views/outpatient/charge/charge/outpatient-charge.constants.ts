import type { TagProps } from 'element-plus';

import type { ChargeSettlementSetlinfoDto } from '#/api/outpatient-charge/outpatient-charge.types';

import { FALLBACK_PAY_CHANNEL_OPTIONS } from '#/utils/payment/payment-channel-options';

/** 收费项状态（与域枚举 `ChargeItemStatus` code 一致）→ 文案 + 标签类型 */
export const CHARGE_ITEM_STATUS_LABEL_TAG: Record<
  string,
  { label: string; tagType: NonNullable<TagProps['type']> }
> = {
  DRAFT: { label: '草稿', tagType: 'info' },
  PLANNED: { label: '待收费', tagType: 'warning' },
  SETTLED: { label: '已结算', tagType: 'success' },
  REFUNDING: { label: '退款中', tagType: 'warning' },
  REFUNDED: { label: '已退款', tagType: 'info' },
};

/** 正式结算支付结果枚举编码（与支付流水域 `PaymentOutcome` 表 4.4 一致：`COMPLETED`=1） */
export const OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED = '1';

/** 正式结算失败：医嘱已变更须重新预结算（GAP-STALE-001 / EX-OPC-411） */
export const OUTPATIENT_CHARGE_SETTLE_ADVICE_CHANGED_CODE = 'EX-OPC-411';

/** IF-OPC-04/05 账户切换语义码 → 用户提示（与 AS-OP-FIN-002 §5 对齐） */
export const OUTPATIENT_CHARGE_SWITCH_ACCOUNT_MESSAGE: Record<string, string> = {
  'EX-OPC-201': '未找到目标记账账户，请核对患者费用性质与挂号开户',
  'EX-OPC-202': '账户切换失败，请稍后重试或联系管理员',
  'EX-OPC-210': '当前就诊为自费挂号，仅单一记账账户，不支持转医保/转自费',
  'EX-OPC-409': '操作成功',
};

/** 解析 IF-OPC-04/05 失败提示；未知码回落通用文案 */
export function resolveOutpatientChargeSwitchAccountMessage(messageCode?: null | string): string {
  if (messageCode && OUTPATIENT_CHARGE_SWITCH_ACCOUNT_MESSAGE[messageCode]) {
    return OUTPATIENT_CHARGE_SWITCH_ACCOUNT_MESSAGE[messageCode];
  }
  return OUTPATIENT_CHARGE_SWITCH_ACCOUNT_MESSAGE['EX-OPC-202'] ?? '账户切换失败';
}

/** 默认支付渠道（门诊收费结算弹窗）：银联扫码（可改选现金/微信等） */
export const DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE = 'SCAN_PAY';

/** 结算弹窗可选渠道（API 不可用时的 fallback，不含 SCAN_PAY） */
export const OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS = [...FALLBACK_PAY_CHANNEL_OPTIONS];

/** 确认收费弹窗「费用明细」固定字段（常显；金额由 `insuranceBreakdownItems` 按 key 覆盖，缺省为 0） */
export const OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS: readonly { key: string; label: string }[] = [
  { key: 'fundPay', label: '基金支付合计' },
  { key: 'poolingPay', label: '基本医保统筹' },
  { key: 'otherPay', label: '其他支出' },
  { key: 'accountPay', label: '医保个人账户' },
  { key: 'largeDiseasePay', label: '大病保险支付' },
  { key: 'deductibleLine', label: '起付线' },
  { key: 'cvlservPay', label: '公务员医疗补助' },
  { key: 'selfPayInScope', label: '范围内个人自付' },
  { key: 'selfPayOutScope', label: '范围外个人自费' },
  { key: 'policyDiscount', label: '政策性减免' },
  { key: 'cashPay', label: '个人现金（医保口径）' },
  { key: 'finalReceivable', label: '个人现金（待收）' },
];

/**
 * 支付记录「医保分摊」字段（国家局 2206/2207 setlinfo 常见项）。
 * `kind: money` 按金额展示；`ratio` 按比例；`text` 原文。
 */
export const OUTPATIENT_CHARGE_SETLINFO_FIELD_DEFS: readonly {
  key: keyof ChargeSettlementSetlinfoDto;
  kind: 'money' | 'ratio' | 'text';
  label: string;
}[] = [
  { key: 'medfeeSumamt', label: '医疗费总额', kind: 'money' },
  { key: 'fundPaySumamt', label: '基金支付总额', kind: 'money' },
  { key: 'psnPartAmt', label: '个人负担总金额', kind: 'money' },
  { key: 'hospPartAmt', label: '医院负担金额', kind: 'money' },
  { key: 'hifpPay', label: '基本医保统筹', kind: 'money' },
  { key: 'othPay', label: '其他支出', kind: 'money' },
  { key: 'poolPropSelfpay', label: '统筹基金支付比例', kind: 'ratio' },
  { key: 'cvlservFlag', label: '公务员标志', kind: 'text' },
  { key: 'cvlservPay', label: '公务员医疗补助', kind: 'money' },
  { key: 'hifesPay', label: '企业补充医保基金支出', kind: 'money' },
  { key: 'hifmiPay', label: '居民大病保险支出', kind: 'money' },
  { key: 'hifobPay', label: '职工大额医疗补助支出', kind: 'money' },
  { key: 'mafPay', label: '医疗救助基金支出', kind: 'money' },
  { key: 'hifdmPay', label: '伤残人员医疗保障支出', kind: 'money' },
  { key: 'inscpScpAmt', label: '符合政策范围金额', kind: 'money' },
  { key: 'actPayDedc', label: '实际支付起付线', kind: 'money' },
  { key: 'fulamtOwnpayAmt', label: '全自费金额', kind: 'money' },
  { key: 'overlmtSelfpay', label: '超限价自费费用', kind: 'money' },
  { key: 'preselfpayAmt', label: '先行自付金额', kind: 'money' },
  { key: 'acctPay', label: '医保个人账户支出', kind: 'money' },
  { key: 'acctMulaidPay', label: '医保个人账户共济', kind: 'money' },
  { key: 'psnCashPay', label: '个人现金（医保口径）', kind: 'money' },
  { key: 'balc', label: '医保个账余额', kind: 'money' },
  { key: 'setlId', label: '结算ID', kind: 'text' },
  { key: 'mdtrtId', label: '就诊ID', kind: 'text' },
  { key: 'chrgBchno', label: '收费批次号', kind: 'text' },
  { key: 'clrOptins', label: '清算经办机构', kind: 'text' },
  { key: 'clrWay', label: '清算方式', kind: 'text' },
  { key: 'clrType', label: '清算类别', kind: 'text' },
];
