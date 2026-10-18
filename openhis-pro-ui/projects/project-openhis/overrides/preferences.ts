import { defineOverridesPreferences } from '@zxplat/preferences/es/define-overrides';

/**
 * OpenHIS 产品演示院点：品牌名写死。
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    layout: 'header-nav',
    name: 'OpenHIS',
    defaultHomePath: '/analytics',
    enableRefreshToken: true,
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
