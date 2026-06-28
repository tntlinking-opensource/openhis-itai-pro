import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

/** 纸质单据全屏切换（Esc 退出、锁定 body 滚动）。 */
export function usePaperSheetFullscreen() {
  const isFullscreen = ref(false);

  function toggleFullscreen() {
    isFullscreen.value = !isFullscreen.value;
  }

  function exitFullscreen() {
    isFullscreen.value = false;
  }

  function handleKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape' && isFullscreen.value) {
      exitFullscreen();
    }
  }

  watch(isFullscreen, (active) => {
    document.body.style.overflow = active ? 'hidden' : '';
  });

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleKeydown);
    document.body.style.overflow = '';
  });

  return {
    exitFullscreen,
    isFullscreen,
    toggleFullscreen,
  };
}
