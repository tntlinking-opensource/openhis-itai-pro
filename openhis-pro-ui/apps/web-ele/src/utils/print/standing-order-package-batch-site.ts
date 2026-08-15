import { getEffectiveSettingValueApi } from '#/api/core/system/setting';

/** 设置名：住院长嘱是否可拆（1=可拆，0=不可拆一盒一点） */
const SETTING_INPATIENT_STANDING_ORDER_SPLITTABLE = 'inpatientStandingOrderSplittable';

/** 会话内缓存，避免摆药单待打/打印各打一次 */
let cachedPackageBatchSiteEnabled: null | Promise<boolean> = null;

/**
 * 当前院点是否启用长嘱不可拆「一盒一点」（setting=0）。
 * 读失败时按可拆（false）回落，避免误把剂量×次数改成盒数。
 */
export async function resolveStandingOrderPackageBatchSiteEnabled(): Promise<boolean> {
  if (!cachedPackageBatchSiteEnabled) {
    cachedPackageBatchSiteEnabled = getEffectiveSettingValueApi({
      name: SETTING_INPATIENT_STANDING_ORDER_SPLITTABLE,
    })
      .then((value) => String(value ?? '').trim() === '0')
      .catch(() => false);
  }
  return cachedPackageBatchSiteEnabled;
}
