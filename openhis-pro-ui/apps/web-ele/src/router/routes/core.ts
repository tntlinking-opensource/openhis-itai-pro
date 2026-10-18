import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@zxplat/constants';

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
      /** 病案首页列表由菜单注册 /mrms/homepage；详情多开走独立隐藏路由，避免与菜单同名冲突 */
      {
        name: 'MrmsCaseRecordHomepageDetail',
        path: '/mrms/homepage/detail/:caseRecordId?',
        component: () => import('#/views/mrms/homepage/homepage-detail.vue'),
        meta: {
          hideInMenu: true,
          /** 与列表分缓存；详情身份变化时仍按 fullPath 开页签 */
          keepAlive: true,
          title: '病案详情',
        },
      },
      /** 结算清单只读预览（列表「结算清单」入口；不嵌病案详情） */
      {
        name: 'MrmsSettleListPreview',
        path: '/mrms/settle-list/:caseRecordId?',
        component: () => import('#/views/mrms/settle-list/index.vue'),
        meta: {
          hideInMenu: true,
          title: '结算清单',
        },
      },
      /** 质控工作台（菜单 component 由后台配置） */
      {
        name: 'MrmsQcWorkbench',
        path: '/mrms/qc-workbench',
        component: () => import('#/views/mrms/qc-workbench/index.vue'),
        meta: {
          hideInMenu: true,
          title: '质控工作台',
        },
      },
      /** 卷宗预览 */
      {
        name: 'MrmsCaseRecordVolume',
        path: '/mrms/volume/:caseRecordId',
        component: () => import('#/views/mrms/volume/index.vue'),
        meta: {
          hideInMenu: true,
          title: '卷宗预览',
        },
      },
      /** 借阅复印 */
      {
        name: 'MrmsBorrowWorkbench',
        path: '/mrms/borrow',
        component: () => import('#/views/mrms/borrow/index.vue'),
        meta: {
          hideInMenu: true,
          title: '借阅复印',
        },
      },
      /** 归档审批（菜单 path=/mrms/archive-approval；侧栏靠 V10 种子 Navigation.MrmsArchiveApproval） */
      {
        name: 'MrmsArchiveApproval',
        path: '/mrms/archive-approval',
        component: () => import('#/views/mrms/archive-approval/index.vue'),
        meta: {
          hideInMenu: true,
          title: '归档审批',
        },
      },
      /** 门诊病历查询（菜单 path=/mrms/outpatient-emr） */
      {
        name: 'MrmsOutpatientEmrQuery',
        path: '/mrms/outpatient-emr',
        component: () => import('#/views/mrms/outpatient-emr/index.vue'),
        meta: {
          hideInMenu: true,
          title: '门诊病历查询',
        },
      },
      /** 住院病例查询（菜单 path=/mrms/inpatient-emr；体温单/护理记录无独立菜单） */
      {
        name: 'MrmsInpatientEmrQuery',
        path: '/mrms/inpatient-emr',
        component: () => import('#/views/mrms/inpatient-emr/index.vue'),
        meta: {
          hideInMenu: true,
          title: '住院病例查询',
        },
      },
    ],
  },
  /** 新版病历编辑器（仅前端 Tiptap Simple Editor，与 /emr 旧页隔离） */
  // {
  // EMR（Tiptap + zxplat-emr）— P0 工作台与编辑
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
