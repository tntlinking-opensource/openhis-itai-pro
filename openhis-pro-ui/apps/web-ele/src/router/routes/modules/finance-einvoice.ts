import type { RouteRecordRaw } from 'vue-router';

/** 电子发票隐藏子路由（金额调整页，无菜单入口） */
const financeEinvoiceRoutes: RouteRecordRaw[] = [
  {
    name: 'EinvoiceDocumentAmount',
    path: '/finance/einvoice/document/:documentId/amount',
    component: () => import('#/views/finance/einvoice/document/amount.vue'),
    meta: {
      hideInMenu: true,
      title: '电子发票金额调整',
      permission: 'einvoice-boss.document.adjust',
    },
  },
];

export default financeEinvoiceRoutes;
