import type { DefineComponent } from 'vue';

/**
 * Pagination 组件的 Props 类型
 */
export interface PaginationProps {
  /** 每页条数 */
  limit: number;
  /** 当前页码 */
  page: number;
  /** 总记录数 */
  total: number;
}

/**
 * 为 Pagination.vue 组件提供类型声明
 */
declare module '@/components/Pagination/index.vue' {
  const component: DefineComponent<PaginationProps>;
  export default component;
}
