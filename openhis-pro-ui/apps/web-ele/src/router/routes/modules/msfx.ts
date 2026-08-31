import type { RouteRecordRaw } from 'vue-router';

/**
 * 码上放心路由兜底（Flyway V3 菜单未生效前亦可直达）。
 * 正式入口以后端菜单为准：一级「码上放心」→ 供应商对照 / 上传运维。
 * name 与 whale_menus.name 对齐，避免与后端动态路由重复注册。
 */
const msfxRoutes: RouteRecordRaw[] = [
  {
    name: 'MsfxSupplierMap',
    path: '/msfx/supplier-map',
    component: () => import('#/views/basic/msfx-supplier-map/index.vue'),
    meta: {
      hideInMenu: true,
      title: '供应商对照',
      permission: 'Navigation.MsfxSupplierMap',
    },
  },
  {
    name: 'MsfxUploadOps',
    path: '/msfx/ops',
    component: () => import('#/msfx/ops/index.vue'),
    meta: {
      hideInMenu: true,
      title: '上传运维',
      permission: 'Navigation.MsfxUploadOps',
    },
  },
  {
    name: 'MsfxSupplierMapLegacyRedirect',
    path: '/basic/msfx-supplier-map',
    redirect: '/msfx/supplier-map',
    meta: {
      hideInMenu: true,
      title: '供应商对照',
    },
  },
];

export default msfxRoutes;
