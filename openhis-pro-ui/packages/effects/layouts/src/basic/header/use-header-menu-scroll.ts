import { onUnmounted, ref } from 'vue';

/** 每帧向目标位置靠近的比例，越小越丝滑 */
const EASE_FACTOR = 0.16;
/** 滚轮位移倍率 */
const WHEEL_FACTOR = 1.2;

export function useHeaderMenuScroll() {
  const menuScrollRef = ref<HTMLElement | null>(null);

  let targetScrollLeft = 0;
  let animationId: number | null = null;

  function clampScrollLeft(el: HTMLElement, value: number) {
    const maxScroll = el.scrollWidth - el.clientWidth;
    return Math.max(0, Math.min(value, maxScroll));
  }

  function stopAnimation() {
    if (animationId !== null) {
      cancelAnimationFrame(animationId);
      animationId = null;
    }
  }

  function animateScroll() {
    const el = menuScrollRef.value;
    if (!el) {
      stopAnimation();
      return;
    }

    const current = el.scrollLeft;
    const distance = targetScrollLeft - current;

    if (Math.abs(distance) < 0.5) {
      el.scrollLeft = targetScrollLeft;
      stopAnimation();
      return;
    }

    el.scrollLeft = current + distance * EASE_FACTOR;
    animationId = requestAnimationFrame(animateScroll);
  }

  /** 头部横向菜单：滚轮纵向滑动转为左右平滑滚动 */
  function handleMenuWheel(event: WheelEvent) {
    const el = menuScrollRef.value;
    if (!el || el.scrollWidth <= el.clientWidth) {
      return;
    }

    if (animationId === null) {
      targetScrollLeft = el.scrollLeft;
    }

    targetScrollLeft = clampScrollLeft(el, targetScrollLeft + event.deltaY * WHEEL_FACTOR);

    if (animationId === null) {
      animationId = requestAnimationFrame(animateScroll);
    }

    event.preventDefault();
    event.stopPropagation();
  }

  onUnmounted(stopAnimation);

  return {
    handleMenuWheel,
    menuScrollRef,
  };
}
