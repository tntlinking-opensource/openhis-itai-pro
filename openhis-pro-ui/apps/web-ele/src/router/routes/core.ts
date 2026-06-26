import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
/** 全局404页面 */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: BasicLayout,
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
  children: [
    {
      component: () => import('#/views/_core/fallback/not-found.vue'),
      meta: {
        hideInBreadcrumb: true,
        hideInMenu: true,
        hideInTab: true,
        title: '404',
      },
      name: 'NotFound',
      path: '',
    },
  ],
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  /**
   * 根路由
   * 使用基础布局，作为所有页面的父级容器，子级就不必配置BasicLayout。
   * 此路由必须存在，且不应修改
   */
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: LOGIN_PATH,
    children: [
      {
        name: 'Profile',
        path: '/profile',
        component: () => import('#/views/_core/profile/index.vue'),
        meta: {
          hideInMenu: true,
          title: '个人中心',
        },
      },
      /** 病案首页全屏编辑（菜单入口为病案列表，component 由后台配置） */
      {
        name: 'MrmsCaseRecordHomepage',
        path: '/mrms/case-record/homepage/:caseRecordId',
        component: () => import('#/views/mrms/case-record/homepage/index.vue'),
        meta: {
          hideInMenu: true,
          title: '病案首页',
        },
      },
      /** C 类报表设计器（无侧栏入口，从报表模板「设计」跳转） */
      {
        name: 'WhaleReportDesigner',
        path: '/reports/designer',
        component: () => import('#/views/report/designer/index.vue'),
        meta: {
          hideInMenu: true,
          title: '报表设计器',
        },
      },
    ],
  },
  /** 新版病历编辑器（仅前端 Tiptap Simple Editor，与 /emr 旧页隔离） */
  // {
  // EMR（Tiptap + whale-emr）— P0 工作台与编辑
  // {
  //   component: BasicLayout,
  //   meta: {
  //     ignoreAccess: true,
  //     title: '电子病历',
  //   },
  //   name: 'Emr',
  //   path: '/emr',
  //   redirect: '/emr/workbench',
  //   children: [
  //     {
  //       component: () => import('#/views/emr/workbench/index.vue'),
  //       meta: {
  //         ignoreAccess: true,
  //         title: '病历工作台',
  //       },
  //       name: 'EmrWorkbench',
  //       path: 'workbench',
  //     },
  //     {
  //       component: () => import('#/views/emr/editor/index.vue'),
  //       meta: {
  //         hideInMenu: true,
  //         hideInTab: false,
  //         ignoreAccess: true,
  //         title: '文书编辑',
  //       },
  //       name: 'EmrEditor',
  //       path: 'editor',
  //     },
  //     {
  //       component: () => import('#/views/emr/template/index.vue'),
  //       meta: {
  //         ignoreAccess: true,
  //         title: '模板列表',
  //       },
  //       name: 'EmrTemplate',
  //       path: 'template',
  //     },
  //   ],
  // },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'Authentication',
    },
    name: 'Authentication',
    path: '/auth',
    redirect: LOGIN_PATH,
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: {
          title: '登录',
        },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
