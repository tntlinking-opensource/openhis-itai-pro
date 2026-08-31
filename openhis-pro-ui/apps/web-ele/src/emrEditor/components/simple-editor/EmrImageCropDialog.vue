<script setup lang="ts">
/**
 * 图片裁切对话框：拖拽选区，确认后输出相对原图的裁切矩形。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import type { EmrImageCropRect } from './emr-image-ops';

defineOptions({ name: 'EmrImageCropDialog' });

interface Props {
  modelValue?: boolean;
  src?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  src: '',
});

const emit = defineEmits<{
  'update:modelValue': [boolean];
  confirm: [EmrImageCropRect];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

const stageRef = ref<HTMLDivElement | null>(null);
const imgRef = ref<HTMLImageElement | null>(null);
const imgReady = ref(false);
const dragging = ref(false);
const dragMode = ref<'move' | 'new' | null>(null);
const startX = ref(0);
const startY = ref(0);
const originRect = ref({ x: 0.1, y: 0.1, width: 0.8, height: 0.8 });

/** 相对预览图的裁切框（0～1） */
const rect = ref<EmrImageCropRect>({ x: 0.1, y: 0.1, width: 0.8, height: 0.8 });

/** 预览图在舞台内的像素盒 */
const previewBox = ref({ left: 0, top: 0, width: 0, height: 0 });

/** 关闭对话框 */
function close() {
  visible.value = false;
}

/** 量测预览图在舞台中的位置 */
function measurePreview() {
  const stage = stageRef.value;
  const img = imgRef.value;
  if (!stage || !img) return;
  const sr = stage.getBoundingClientRect();
  const ir = img.getBoundingClientRect();
  previewBox.value = {
    left: ir.left - sr.left,
    top: ir.top - sr.top,
    width: ir.width,
    height: ir.height,
  };
}

/** 图片加载完成 */
function onImgLoad() {
  imgReady.value = true;
  nextTick(() => measurePreview());
}

watch(
  () => [props.modelValue, props.src] as const,
  async ([open]) => {
    if (!open) {
      imgReady.value = false;
      return;
    }
    rect.value = { x: 0.1, y: 0.1, width: 0.8, height: 0.8 };
    await nextTick();
    measurePreview();
  },
);

/** 舞台坐标 → 相对原图比例 */
function clientToNorm(clientX: number, clientY: number): { x: number; y: number } {
  const stage = stageRef.value;
  if (!stage) return { x: 0, y: 0 };
  const sr = stage.getBoundingClientRect();
  const box = previewBox.value;
  const x = (clientX - sr.left - box.left) / Math.max(1, box.width);
  const y = (clientY - sr.top - box.top) / Math.max(1, box.height);
  return {
    x: Math.max(0, Math.min(1, x)),
    y: Math.max(0, Math.min(1, y)),
  };
}

/** 按下：新建或移动选区 */
function onPointerDown(e: PointerEvent) {
  if (!imgReady.value) return;
  measurePreview();
  const p = clientToNorm(e.clientX, e.clientY);
  const r = rect.value;
  const inside = p.x >= r.x && p.x <= r.x + r.width && p.y >= r.y && p.y <= r.y + r.height;
  dragging.value = true;
  startX.value = p.x;
  startY.value = p.y;
  originRect.value = { ...r };
  dragMode.value = inside ? 'move' : 'new';
  if (!inside) {
    rect.value = { x: p.x, y: p.y, width: 0.01, height: 0.01 };
  }
  (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
}

/** 拖动选区 */
function onPointerMove(e: PointerEvent) {
  if (!dragging.value || !dragMode.value) return;
  const p = clientToNorm(e.clientX, e.clientY);
  if (dragMode.value === 'new') {
    const x1 = Math.min(startX.value, p.x);
    const y1 = Math.min(startY.value, p.y);
    const x2 = Math.max(startX.value, p.x);
    const y2 = Math.max(startY.value, p.y);
    rect.value = {
      x: x1,
      y: y1,
      width: Math.max(0.02, x2 - x1),
      height: Math.max(0.02, y2 - y1),
    };
    return;
  }
  const o = originRect.value;
  const dx = p.x - startX.value;
  const dy = p.y - startY.value;
  let nx = o.x + dx;
  let ny = o.y + dy;
  nx = Math.max(0, Math.min(1 - o.width, nx));
  ny = Math.max(0, Math.min(1 - o.height, ny));
  rect.value = { ...o, x: nx, y: ny };
}

/** 结束拖动 */
function onPointerUp() {
  dragging.value = false;
  dragMode.value = null;
}

/** 确认裁切 */
function onConfirm() {
  const r = rect.value;
  if (r.width < 0.02 || r.height < 0.02) {
    ElMessage.warning('请框选更大的区域');
    return;
  }
  emit('confirm', { ...r });
  close();
}

onBeforeUnmount(() => {
  dragging.value = false;
});
</script>

<template>
  <ElDialog
    v-model="visible"
    title="裁切图片"
    width="560px"
    append-to-body
    destroy-on-close
    @closed="imgReady = false"
  >
    <div
      ref="stageRef"
      class="emr-img-crop"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <img
        v-if="src"
        ref="imgRef"
        class="emr-img-crop__img"
        :src="src"
        alt=""
        draggable="false"
        @load="onImgLoad"
      />
      <div
        v-if="imgReady"
        class="emr-img-crop__box"
        :style="{
          left: `${previewBox.left + rect.x * previewBox.width}px`,
          top: `${previewBox.top + rect.y * previewBox.height}px`,
          width: `${rect.width * previewBox.width}px`,
          height: `${rect.height * previewBox.height}px`,
        }"
      />
    </div>
    <p class="emr-img-crop__hint">拖拽框选区域；在框内拖动可移动选区。</p>
    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" :disabled="!imgReady" @click="onConfirm">确定</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.emr-img-crop {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  max-height: 420px;
  overflow: hidden;
  background: #111827;
  user-select: none;
  touch-action: none;
  cursor: crosshair;
}

.emr-img-crop__img {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
  pointer-events: none;
}

.emr-img-crop__box {
  position: absolute;
  box-sizing: border-box;
  border: 2px solid #60a5fa;
  box-shadow: 0 0 0 9999px rgb(0 0 0 / 45%);
  pointer-events: none;
}

.emr-img-crop__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
</style>
