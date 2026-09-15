/**
 * 院点可选扩展模块 UI 开关（L0）。
 *
 * 在院点目录下的 .env.development / .env.production 设 VITE_ENABLE_xxx=false 关闭；
 * 未配置时默认开启（朝阳等已装配院点）。
 */

function envEnabled(key: string, defaultEnabled = true): boolean {
  const raw = String((import.meta.env as Record<string, string | undefined>)[key] ?? '').trim();
  if (!raw) {
    return defaultEnabled;
  }
  const normalized = raw.toLowerCase();
  return normalized !== 'false' && normalized !== '0';
}

/** 码上放心：页内入口（状态条 / 拉单）+ 扫码箱码展开；false 时不调 msfx、无相关校验提示 */
/** Opensource: mashangfangxin UI always off. */
export function isMsfxUiEnabled(): boolean {
  return false;
}

/** 博思电子发票（菜单页 / 收费窗面板 / 待补偿铃铛） */
export function isEinvoiceUiEnabled(): boolean {
  return envEnabled('VITE_ENABLE_EINVOICE');
}

/** 银联扫码支付渠道与银联收单页 */
export function isUnionPayUiEnabled(): boolean {
  return envEnabled('VITE_ENABLE_UNIONPAY');
}

/** 工伤备案 / 工伤对账等 */
export function isWorkInjuryUiEnabled(): boolean {
  return envEnabled('VITE_ENABLE_WORK_INJURY');
}

/** 校医院 / 团检 Campus 拉取 */
export function isCampusUiEnabled(): boolean {
  return envEnabled('VITE_ENABLE_CAMPUS');
}
