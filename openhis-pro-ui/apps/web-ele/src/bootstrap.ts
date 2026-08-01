import { createApp, watchEffect } from 'vue';

import { unmountGlobalLoading } from '@vben/utils/es/helpers/unmount-global-loading';

import { useTitle } from '@vueuse/core';
import ElementPlus from 'element-plus';

import App from './app.vue';

// 全局主题样式必须在 mount 前同步加载；异步 import 会导致生产环境 CSS chunk 晚于首屏，样式爆炸
import '@vben/styles';
import 'element-plus/dist/index.css';
import '@vben/styles/ele';
import './styles/layout-viewport-compact.css';
import './styles/doctor-station-viewport-compact.css';
import './styles/inventory-viewport-compact.css';
import './styles/persistent-error-messagebox.css';
import './styles/date-picker-shortcuts-bottom.css';

async function bootstrap(namespace: string) {
  // 添加全局错误处理，捕获 Element Plus useKeydown.ts 中的 querySelectorAll 错误
  if (typeof window !== 'undefined') {
    // 处理同步错误
    window.addEventListener('error', (event) => {
      // 忽略 Element Plus useKeydown.ts 中的 querySelectorAll 错误
      const error = event.error;
      const message = event.message || error?.message || '';
      const source = event.filename || '';

      if (
        (typeof message === 'string' &&
          message.includes('querySelectorAll') &&
          message.includes('useKeydown')) ||
        (source.includes('querySelectorAll') && source.includes('useKeydown'))
      ) {
        event.preventDefault(); // 阻止错误传播
      }
    });

    // 处理 Promise 的未捕获错误
    window.addEventListener('unhandledrejection', (event) => {
      const error = event.reason;
      if (
        error &&
        typeof error === 'object' &&
        'message' in error &&
        typeof error.message === 'string' &&
        error.message.includes('querySelectorAll') &&
        error.message.includes('useKeydown')
      ) {
        event.preventDefault(); // 阻止错误传播
      }
    });
  }

  const app = createApp(App);

  // 配置 Vue 错误处理，捕获 Element Plus useKeydown.ts 中的错误
  app.config.errorHandler = (err, instance, info) => {
    // 完全忽略 Element Plus useKeydown.ts 中的 querySelectorAll 错误
    if (err) {
      const errorMessage = (err as Error)?.message || (typeof err === 'string' ? err : String(err));
      const errorStack = (err as Error)?.stack || (typeof err === 'string' ? err : String(err));

      if (
        (errorMessage.includes('querySelectorAll') && errorMessage.includes('useKeydown')) ||
        (errorStack.includes('querySelectorAll') && errorStack.includes('useKeydown'))
      ) {
        return; // 完全忽略，不打印
      }
    }
    // 其他错误正常处理
    console.error('Vue error:', err, instance, info);
  };

  // ElementPlus 已内置注册 v-loading，勿再 app.directive('loading', ...)
  app.use(ElementPlus);

  // 配置 pinia-tore
  const [{ initStores }, { router }] = await Promise.all([
    import('@vben/stores'),
    import('./router'),
  ]);
  await initStores(app, { namespace });

  // 配置路由及路由守卫
  app.use(router);

  // i18n 须在 mount 前就绪，避免生产冷启动首屏 $t 回显 key（如 common.login）
  const { setupI18n, $t } = await import('#/locales');
  await setupI18n(app);

  app.mount('#app');
  unmountGlobalLoading();

  // mount 后分批初始化：P0 适配器有依赖顺序；P1 插件可并行；P2 菜单预拉独立
  void (async () => {
    try {
      const { initComponentAdapter } = await import('./adapter/component');
      initComponentAdapter();
      const { initSetupVbenForm } = await import('./adapter/form');
      await initSetupVbenForm();
      await import('./adapter/vxe-table');
    } catch (error) {
      console.error('[bootstrap] form/vxe adapter init failed', error);
    }

    try {
      await Promise.all([
        import('@vben/common-ui/es/tippy').then(({ initTippy }) => {
          initTippy(app);
        }),
        import('@vben/access/es/directive').then(({ registerAccessDirective }) => {
          registerAccessDirective(app);
        }),
        import('@vben/common-ui/es/loading').then(({ registerLoadingDirective }) => {
          registerLoadingDirective(app, { loading: false, spinning: 'spinning' });
        }),
        import('@vben/plugins/motion').then(({ MotionPlugin }) => {
          app.use(MotionPlugin);
        }),
      ]);
      const { preferences } = await import('@vben/preferences/es/preferences-manager');
      watchEffect(() => {
        if (preferences.app.dynamicTitle) {
          const routeTitle = router.currentRoute.value.meta?.title;
          const pageTitle = (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
          useTitle(pageTitle);
        }
      });
    } catch (error) {
      console.error('[bootstrap] app plugin init failed', error);
    }
  })();

  void import('./router/access-init').then(({ prefetchAccessRoutes }) =>
    prefetchAccessRoutes(router).catch((error) => {
      console.warn('[bootstrap] background prefetch failed', error);
    }),
  );
}

export { bootstrap };
