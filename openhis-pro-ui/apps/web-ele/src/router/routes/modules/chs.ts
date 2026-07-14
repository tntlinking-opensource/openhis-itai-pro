import type { RouteRecordRaw } from 'vue-router';

/** 医保模块跳转子路由（Flyway V96 菜单未生效前，亦可从国家目录等页进入） */
const chsRoutes: RouteRecordRaw[] = [
  {
    name: 'ChsPlatformDict',
    path: '/chs/platform-dict',
    component: () => import('#/chs/platform-dict/index.vue'),
    meta: {
      hideInMenu: true,
      title: '平台字典(1901)',
    },
  },
  {
    name: 'ChsReconciliation',
    path: '/chs/reconciliation',
    component: () => import('#/chs/reconciliation/index.vue'),
    meta: {
      hideInMenu: true,
      title: '对账清算',
    },
  },
];

export default chsRoutes;
