<script setup lang="ts">
defineOptions({ name: 'SidePaneCollapseHandle' });

interface Props {
  /** 当前是否已收起 */
  collapsed?: boolean;
}

withDefaults(defineProps<Props>(), {
  collapsed: false,
});

const emit = defineEmits<{
  toggle: [];
}>();

/**
 * 展开态（贴侧栏右缘）：右侧贴边，左侧圆润鼓出（非直线梯形）
 * viewBox 0 0 28 108
 */
const PATH_EXPANDED =
  'M28 0 C28 0 28 14 22 22 C12 34 4 40 4 54 C4 68 12 74 22 86 C28 94 28 108 28 108 Z';

/**
 * 收起态（贴左缘）：左侧贴边，右侧圆润鼓出
 */
const PATH_COLLAPSED =
  'M0 0 C0 0 0 14 6 22 C16 34 24 40 24 54 C24 68 16 74 6 86 C0 94 0 108 0 108 Z';

/** 切换收起/展开 */
function handleToggle() {
  emit('toggle');
}
</script>

<template>
  <button
    type="button"
    class="side-pane-collapse-handle"
    :class="{ 'is-collapsed': collapsed }"
    :title="collapsed ? '展开' : '收起'"
    :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
    @click.stop="handleToggle"
  >
    <!-- SVG 圆润侧耳（曲线轮廓，避免直线梯形感） -->
    <svg
      class="side-pane-collapse-handle__shape"
      viewBox="0 0 28 108"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path
        class="side-pane-collapse-handle__path"
        :d="collapsed ? PATH_COLLAPSED : PATH_EXPANDED"
      />
    </svg>
    <!-- 指向：展开→右，收起→左 -->
    <span class="side-pane-collapse-handle__tri" aria-hidden="true"></span>
  </button>
</template>

<style scoped>
/*
 * 竖向把手：浅灰半透明圆润侧耳 + 细边框 + 方向三角。
 * 默认隐藏，父级侧栏 :hover / 已收起时显示；垂直居中由父级定位。
 */
.side-pane-collapse-handle {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 108px;
  padding: 0;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: rgb(144 147 153 / 80%);
  cursor: pointer;
  outline: none;
  box-shadow: none;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.15s ease,
    color 0.15s ease;
}

.side-pane-collapse-handle__shape {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.side-pane-collapse-handle__path {
  fill: rgb(220 223 230 / 22%);
  stroke: rgb(190 194 202 / 60%);
  stroke-width: 1.25;
  vector-effect: non-scaling-stroke;
  transition:
    fill 0.15s ease,
    stroke 0.15s ease;
}

.side-pane-collapse-handle__tri {
  position: relative;
  z-index: 1;
  width: 0;
  height: 0;
  border-style: solid;
  /* 默认收起：三角朝左 */
  border-width: 6px 7px 6px 0;
  border-color: transparent currentcolor transparent transparent;
}

.side-pane-collapse-handle.is-collapsed .side-pane-collapse-handle__tri {
  /* 展开：三角朝右 */
  border-width: 6px 0 6px 7px;
  border-color: transparent transparent transparent currentcolor;
}

.side-pane-collapse-handle:hover,
.side-pane-collapse-handle:focus-visible {
  color: rgb(96 98 102 / 90%);
}

.side-pane-collapse-handle:hover .side-pane-collapse-handle__path,
.side-pane-collapse-handle:focus-visible .side-pane-collapse-handle__path {
  fill: rgb(220 223 230 / 38%);
  stroke: rgb(170 175 185 / 75%);
}
</style>
