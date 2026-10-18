import type { RouteRecordRaw } from 'vue-router';

/** 医保模块跳转子路由（Flyway 菜单未生效前亦可兜底进入） */
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
    name: 'ChsInvokeLog',
    path: '/chs/invoke-log',
    component: () => import('#/chs/invoke-log/index.vue'),
    meta: {
      hideInMenu: true,
      title: '医保调用日志',
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
  {
    name: 'ChsCatalogNotice',
    path: '/chs/catalog-notice',
    component: () => import('#/chs/catalog-notice/index.vue'),
    meta: {
      hideInMenu: true,
      title: '医保文件管理',
    },
  },
  {
    name: 'ChsDetailAuditTry',
    path: '/chs/detail-audit-try',
    component: () => import('#/chs/detail-audit-try/index.vue'),
    meta: {
      hideInMenu: true,
      title: '事前提醒试调',
    },
  },
  {
    name: 'ChsDetailAuditStats',
    path: '/chs/detail-audit-stats',
    component: () => import('#/chs/detail-audit-stats/index.vue'),
    meta: {
      hideInMenu: true,
      title: '事前提醒统计',
    },
  },
  {
    name: 'ChsInpatientMedcasOps',
    path: '/chs/medcas-ops',
    component: () => import('#/chs/medcas-ops/index.vue'),
    meta: {
      hideInMenu: true,
      title: '住院业务上报',
    },
  },
  {
    name: 'ChsHongqiResettle',
    path: '/chs/hongqi-resettle',
    component: () => import('#/chs/hongqi-resettle/index.vue'),
    meta: {
      hideInMenu: true,
      title: '红旗合同重结',
    },
  },
];

export default chsRoutes;
