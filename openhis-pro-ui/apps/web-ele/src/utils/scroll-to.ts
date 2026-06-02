declare global {
  interface Math {
    easeInOutQuad(t: number, b: number, c: number, d: number): number;
  }
}

Math.easeInOutQuad = function (t: number, b: number, c: number, d: number) {
  t /= d / 2;
  if (t < 1) {
    return (c / 2) * t * t + b;
  }
  t--;
  return (-c / 2) * (t * (t - 2) - 1) + b;
};

const requestAnimFrame = (function () {
  return (
    window.requestAnimationFrame ||
    (window as any).webkitRequestAnimationFrame ||
    (window as any).mozRequestAnimationFrame ||
    function (callback: FrameRequestCallback) {
      window.setTimeout(callback, 1000 / 60);
    }
  );
})();

function move(amount: number) {
  document.documentElement.scrollTop = amount;
  const parent = document.body.parentNode as HTMLElement | null;
  if (parent) parent.scrollTop = amount;
  document.body.scrollTop = amount;
}

function position() {
  const parent = document.body.parentNode as HTMLElement | null;
  return document.documentElement.scrollTop || (parent?.scrollTop ?? 0) || document.body.scrollTop;
}

/**
 * @param to - 目标滚动位置
 * @param duration - 动画时长（毫秒）
 * @param callback - 动画完成回调
 */
export function scrollTo(to: number, duration?: number, callback?: () => void): void {
  const start = position();
  const change = to - start;
  const increment = 20;
  let currentTime = 0;
  const animDuration = duration === undefined ? 500 : duration;

  const animateScroll = function () {
    currentTime += increment;
    const val = Math.easeInOutQuad(currentTime, start, change, animDuration);
    move(val);
    if (currentTime < animDuration) {
      requestAnimFrame(animateScroll);
    } else {
      callback?.();
    }
  };
  animateScroll();
}
