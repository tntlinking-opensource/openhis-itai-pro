import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';

/** 纸质单据（体温单/护理记录）设计宽度，与 A4 屏幕稿一致 */
export const PAPER_SHEET_DESIGN_WIDTH = 794;

export interface PaperSheetViewportScaleOptions {
  designWidth?: number;
  /** 固定设计高度（如 A4）；与 contentHeightRef 二选一 */
  designHeight?: number;
  /** 随内容变化的设计高度（如护理记录表格行数） */
  contentHeightRef?: Ref<number>;
  /** 测量内容高度的元素；作为 contentHeightRef 的补充 */
  contentRef?: Ref<HTMLElement | null | undefined>;
  /** 视口内为侧边操作区等预留宽度 */
  reservedWidth?: number;
  /** 视口内为顶栏等预留高度 */
  reservedHeight?: number;
  /** 视口内边距（若 CSS 已设 padding，此处传 0） */
  padding?: number;
  /** 关闭时使用的固定缩放（非全屏常态展示） */
  fallbackScale?: number;
  /** 是否启用视口自适应缩放（全屏时为 true） */
  enabled?: Ref<boolean>;
  /** contain：整页落入视口；width：按宽度放大，超出高度可滚动 */
  fitMode?: 'contain' | 'width';
}

/** 全屏视口内等比 contain 放大，整页完整落入可视区域。 */
export function usePaperSheetViewportScale(
  viewportRef: Ref<HTMLElement | null | undefined>,
  options: PaperSheetViewportScaleOptions = {},
) {
  const designWidth = options.designWidth ?? PAPER_SHEET_DESIGN_WIDTH;
  const scale = ref(1);
  const measuredContentHeight = ref(0);

  const resolvedDesignHeight = computed(() => {
    const explicitHeight = options.contentHeightRef?.value ?? 0;
    if (explicitHeight > 0) {
      return explicitHeight;
    }
    if (measuredContentHeight.value > 0) {
      return measuredContentHeight.value;
    }
    return options.designHeight ?? 0;
  });

  const shellWidth = computed(() => Math.ceil(designWidth * scale.value));

  const shellHeight = computed(() => {
    const designHeight = resolvedDesignHeight.value;
    if (designHeight <= 0) {
      return 0;
    }
    return Math.ceil(designHeight * scale.value);
  });

  function resolveViewportSize() {
    const viewport = viewportRef.value;
    if (!viewport) {
      return null;
    }
    const width = viewport.clientWidth;
    const height = viewport.clientHeight;
    if (width <= 0 || height <= 0) {
      return null;
    }
    return { height, width };
  }

  function recomputeScale() {
    if (options.enabled && !options.enabled.value) {
      scale.value = options.fallbackScale ?? 1;
      return;
    }

    const designHeight = resolvedDesignHeight.value;
    if (designHeight <= 0) {
      scale.value = options.fallbackScale ?? 1;
      return;
    }

    const viewportSize = resolveViewportSize();
    if (!viewportSize) {
      scale.value = options.fallbackScale ?? 1;
      return;
    }

    const padding = options.padding ?? 0;
    const reservedWidth = options.reservedWidth ?? 0;
    const reservedHeight = options.reservedHeight ?? 0;
    const availableWidth = Math.max(0, viewportSize.width - padding * 2 - reservedWidth);
    const availableHeight = Math.max(0, viewportSize.height - padding * 2 - reservedHeight);

    if (availableWidth <= 0 || availableHeight <= 0) {
      scale.value = options.fallbackScale ?? 1;
      return;
    }

    scale.value =
      options.fitMode === 'width'
        ? availableWidth / designWidth
        : Math.min(availableWidth / designWidth, availableHeight / designHeight);
  }

  let viewportObserver: ResizeObserver | null = null;
  let contentObserver: ResizeObserver | null = null;

  function bindViewportObserver() {
    viewportObserver?.disconnect();
    viewportObserver = null;
    if (!viewportRef.value || typeof ResizeObserver === 'undefined') {
      recomputeScale();
      return;
    }
    viewportObserver = new ResizeObserver(() => recomputeScale());
    viewportObserver.observe(viewportRef.value);
    recomputeScale();
  }

  function remeasureContent() {
    const content = options.contentRef?.value;
    if (!content) {
      recomputeScale();
      return;
    }
    const nextHeight = Math.ceil(
      content.getBoundingClientRect().height || content.scrollHeight || content.offsetHeight,
    );
    if (nextHeight <= 0) {
      recomputeScale();
      return;
    }
    measuredContentHeight.value = nextHeight;
    recomputeScale();
  }

  function bindContentObserver() {
    contentObserver?.disconnect();
    contentObserver = null;
    if (!options.contentRef?.value || typeof ResizeObserver === 'undefined') {
      remeasureContent();
      return;
    }
    contentObserver = new ResizeObserver(() => remeasureContent());
    contentObserver.observe(options.contentRef.value);
    remeasureContent();
  }

  function handleWindowResize() {
    if (options.enabled?.value) {
      recomputeScale();
    }
  }

  watch(viewportRef, () => bindViewportObserver(), { flush: 'post' });
  watch(
    () => options.enabled?.value,
    (active) => {
      if (!active) {
        measuredContentHeight.value = 0;
      }
      recomputeScale();
      if (active) {
        remeasureContent();
      }
    },
  );
  watch(
    () => options.contentRef?.value,
    () => bindContentObserver(),
    { flush: 'post' },
  );
  watch(resolvedDesignHeight, () => recomputeScale());

  onBeforeUnmount(() => {
    viewportObserver?.disconnect();
    contentObserver?.disconnect();
    window.removeEventListener('resize', handleWindowResize);
  });

  window.addEventListener('resize', handleWindowResize);

  return {
    scale,
    shellWidth,
    shellHeight,
    recomputeScale,
    remeasureContent,
    bindViewportObserver,
    bindContentObserver,
  };
}
