import type { RouteRecordRaw } from 'vue-router';

/** 统一预约静态路由（大屏等需脱离 Layout 的页面） */
const bookingRoutes: RouteRecordRaw[] = [
  {
    name: 'BookingQueueDisplay',
    path: '/booking/queue-display',
    component: () => import('#/views/booking/queue-display/index.vue'),
    meta: {
      hideInMenu: true,
      hideInTab: true,
      ignoreAccess: true,
      noBasicLayout: true,
      title: '叫号大屏',
    },
  },
];

export default bookingRoutes;
