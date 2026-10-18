import { initPreferences, updatePreferences } from '@zxplat/preferences/es/preferences-manager';
import { unmountGlobalLoading } from '@zxplat/utils/es/helpers/unmount-global-loading';

import { overridesPreferences } from '#/preferences';

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // 偏好设置须在 bootstrap 前完成：路由守卫/布局会读取 preferences（主题、进度条等）
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });
  // app.name / logo 以代码配置为准，避免 localStorage 旧缓存盖住院点标题
  updatePreferences({
    app: {
      defaultHomePath: overridesPreferences.app?.defaultHomePath ?? '/analytics',
      name: overridesPreferences.app?.name,
    },
    logo: overridesPreferences.logo,
    theme: overridesPreferences.theme,
  });

  // 预编译路由 chunk，缩短冷启动首次进登录页时间
  void import('./router');

  const bootstrapTimeoutMs = import.meta.env.DEV ? 60_000 : 10_000;
  const loadingFallbackTimer = window.setTimeout(() => {
    unmountGlobalLoading();
    console.warn('[init] bootstrap 超时，已强制关闭 Loading');
  }, bootstrapTimeoutMs);

  try {
    const { bootstrap } = await import('./bootstrap');
    await bootstrap(namespace);
  } catch (error) {
    unmountGlobalLoading();
    throw error;
  } finally {
    window.clearTimeout(loadingFallbackTimer);
  }
}

initApplication();
