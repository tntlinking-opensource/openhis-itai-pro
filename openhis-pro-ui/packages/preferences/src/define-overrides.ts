import type { Preferences } from '@zxplat-core/preferences';
import type { DeepPartial } from '@zxplat-core/typings';

/**
 * 项目级偏好覆盖定义，独立子路径导出避免拉起完整 preferences 桶。
 */
function defineOverridesPreferences(preferences: DeepPartial<Preferences>) {
  return preferences;
}

export { defineOverridesPreferences };
