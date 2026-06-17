import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@vben/access';
import { registerLoadingDirective } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { initStores, useAccessStore } from '@vben/stores';
import '@vben/styles';
import '@vben/styles/ele';

import { useTitle } from '@vueuse/core';
import ElementPlus, { ElLoading } from 'element-plus';

import { $t, setupI18n } from '#/locales';

import { initComponentAdapter } from './adapter/component';
import { initSetupVbenForm } from './adapter/form';
import App from './app.vue';
import ProductSelectTable from './components/select-product.vue';
import { router } from './router';
import {
  applyHiprintAutoConnectPolicy,
  isHiprintAutoConnectDisabled,
} from './utils/hiprint-connect-pref';

// 初始化 vxe-table，确保 useTableForm 被正确初始化
import './adapter/vxe-table';

// 导入 Element Plus 基础样式
import 'element-plus/dist/index.css';

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

  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupVbenForm();

  // // 设置弹窗的默认配置
  // setDefaultModalProps({
  //   fullscreenButton: false,
  // });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 2000,
  // });
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

  app.use(ElementPlus);

  // 注册Element Plus提供的v-loading指令
  app.directive('loading', ElLoading.directive);

  // 注册Vben提供的v-loading和v-spinning指令
  registerLoadingDirective(app, {
    loading: false, // Vben提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册Vben提供的v-loading指令
    spinning: 'spinning',
  });

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-tore
  await initStores(app, { namespace });

  // 切回标签页时静默恢复服务端租户/科室会话（应对后端重启而前端未刷新）
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      const accessStore = useAccessStore();
      if (!accessStore.accessToken) {
        return;
      }
      void import('#/utils/restore-login-scope').then(({ restoreLoginScopeOnce }) =>
        restoreLoginScopeOnce().catch(() => {}),
      );
    });
  }

  // 安装权限指令
  registerAccessDirective(app);

  // 全局注册 ProductSelectTable 组件
  app.component('ProductSelectTable', ProductSelectTable);

  // 初始化 tippy
  const { initTippy } = await import('@vben/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫（须在 prefetchAccessRoutes 之前安装，保证 store 内可安全使用 router）
  app.use(router);

  // 已登录时预拉菜单并注册动态路由，避免首次点击需后端菜单的页面时才阻塞导航
  const { prefetchAccessRoutes } = await import('./router/access-init');
  await prefetchAccessRoutes(router);

  // 配置Motion插件
  const { MotionPlugin } = await import('@vben/plugins/motion');
  app.use(MotionPlugin);

  // 注册 Hiprint 插件（可在个人中心 → 客户端 中屏蔽 17521 自动连接）
  try {
    const disableHiprintConnect = isHiprintAutoConnectDisabled();
    const { hiPrintPlugin, defaultElementTypeProvider } = await import('vue-plugin-hiprint');
    app.use(hiPrintPlugin, '$hiprint', !disableHiprintConnect);
    app.use(defaultElementTypeProvider, '$defaultElementTypeProvider');
    if (disableHiprintConnect) {
      await applyHiprintAutoConnectPolicy();
    }
  } catch (error) {
    console.warn('Hiprint 插件注册失败:', error);
  }

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle = (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  app.mount('#app');
}

export { bootstrap };
