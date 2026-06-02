import { defineOverridesPreferences } from '@vben/preferences';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    layout: 'header-nav',
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '/analytics',
    enableCheckUpdates: false,
    preferencesButtonPosition: undefined,
    accessMode: 'mixed',
  },
  theme: {
    builtinType: 'sky-blue',
    colorPrimary: 'hsl(212 100% 45%)',
    radius: '0.25',
  },
  logo: {
    source: import.meta.env.VITE_APP_LOGO,
  },
  widget: {
    themeToggle: false,
    languageToggle: false,
    timezone: false,
    fullscreen: false,
    lockScreen: false,
    notification: true,
    sidebarToggle: false,
  },
  breadcrumb: {
    enable: false,
  },
});
