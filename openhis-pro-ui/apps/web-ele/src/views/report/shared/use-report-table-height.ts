import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

/** 报表列表表格可视高度：随容器 ResizeObserver 自适应。 */
export function useReportTableHeight(initialHeight = 420) {
  const tableWrapRef = ref<HTMLElement | null>(null);
  const tableHeight = ref(initialHeight);

  let tableResizeObserver: null | ResizeObserver = null;

  /** 根据容器高度设置表格可视区域。 */
  function updateTableHeight() {
    const el = tableWrapRef.value;
    if (!el) {
      return;
    }
    const height = el.clientHeight;
    if (height > 0) {
      tableHeight.value = height;
    }
  }

  /** 绑定表格容器尺寸监听。 */
  function bindTableResizeObserver() {
    tableResizeObserver?.disconnect();
    const el = tableWrapRef.value;
    if (!el || typeof ResizeObserver === 'undefined') {
      return;
    }
    tableResizeObserver = new ResizeObserver(() => {
      updateTableHeight();
    });
    tableResizeObserver.observe(el);
    updateTableHeight();
  }

  onMounted(async () => {
    await nextTick();
    bindTableResizeObserver();
  });

  onBeforeUnmount(() => {
    tableResizeObserver?.disconnect();
    tableResizeObserver = null;
  });

  return {
    tableWrapRef,
    tableHeight,
    updateTableHeight,
    bindTableResizeObserver,
  };
}
