<script lang="ts" setup>
import type { ImecWaveform } from '#/api';

import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

interface Props {
  waveform: ImecWaveform;
  color?: string;
  height?: number;
}

const props = withDefaults(defineProps<Props>(), {
  color: '#00ff00',
  height: 60,
});

const canvasRef = ref<HTMLCanvasElement | null>(null);
let animFrameId: null | number = null;

/** 波形滚动绘制器 — 模拟监护仪扫描线效果 */
class WaveformRenderer {
  private canvas: HTMLCanvasElement;
  private color: string;
  private ctx: CanvasRenderingContext2D;
  /** 每个像素对应的数据索引（浮点数，实现平滑滚动） */
  private cursor = 0;
  /** 显示高度 */
  private displayHeight = 0;
  /** 显示宽度 */
  private displayWidth = 0;
  /** 上一次绘制时 waveform.samples 的引用，检测数据更新 */
  private lastSamplesRef: ImecWaveform['samples'] | null = null;
  /** 上次数据更新时的 cursor 位置（用于检测新数据到来时是否重置） */
  private samplesLength = 0;

  constructor(canvas: HTMLCanvasElement, color: string) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d')!;
    this.color = color;
  }

  /** 绘制一帧 */
  draw(waveform: ImecWaveform) {
    const samples = waveform.samples;
    if (!samples || samples.length === 0) return;

    this.ensureSize();

    // 宽高为 0 时跳过（Canvas 尚未渲染到 DOM）
    if (this.displayWidth <= 0 || this.displayHeight <= 0) return;

    // 检测数据是否更新（引用变了或长度变了）
    if (samples !== this.lastSamplesRef || samples.length !== this.samplesLength) {
      // 新数据到来，如果长度变化较大则重置 cursor
      if (
        this.lastSamplesRef !== null &&
        Math.abs(samples.length - this.samplesLength) > samples.length * 0.5
      ) {
        this.cursor = 0;
      }
      this.lastSamplesRef = samples;
      this.samplesLength = samples.length;
    }

    const ctx = this.ctx;
    const w = this.displayWidth;
    const h = this.displayHeight;

    // 清屏
    ctx.clearRect(0, 0, w, h);

    // 网格
    this.drawGrid();

    // 标签
    this.drawLabel(waveform.name || waveform.code);

    // 计算数值范围
    let minVal = Infinity;
    let maxVal = -Infinity;
    for (const s of samples) {
      if (s.value < minVal) minVal = s.value;
      if (s.value > maxVal) maxVal = s.value;
    }
    const range = maxVal - minVal || 1;
    const padding = range * 0.1;
    minVal -= padding;
    maxVal += padding;

    // 波形绘制区域
    const yTop = 16;
    const yHeight = h - yTop - 4;

    // 每帧推进的采样点数（基于采样率和目标帧率60fps）
    const sampleRate = waveform.sampleRate || 200;
    const pixelsPerFrame = sampleRate / 60;

    // 绘制波形
    ctx.strokeStyle = this.color;
    ctx.lineWidth = 1.5;
    ctx.shadowColor = this.color;
    ctx.shadowBlur = 4;
    ctx.beginPath();

    for (let x = 0; x < w; x++) {
      // 每个像素对应的采样索引（从左到右线性映射）
      const sampleIdx = Math.floor((x / w) * samples.length);
      const normalized = (samples[sampleIdx]!.value - minVal) / (maxVal - minVal);
      const y = yTop + yHeight - normalized * yHeight;

      if (x === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.stroke();
    ctx.shadowBlur = 0;

    // 扫描线位置指示
    const scanX = w - 1;
    ctx.strokeStyle = this.color;
    ctx.lineWidth = 2;
    ctx.shadowColor = this.color;
    ctx.shadowBlur = 8;
    ctx.globalAlpha = 0.6;
    ctx.beginPath();
    ctx.moveTo(scanX, yTop);
    ctx.lineTo(scanX, yTop + yHeight);
    ctx.stroke();
    ctx.globalAlpha = 1;
    ctx.shadowBlur = 0;

    // 推进 cursor
    this.cursor = (this.cursor + pixelsPerFrame) % samples.length;
  }

  /** 重置 */
  reset() {
    this.cursor = 0;
    this.lastSamplesRef = null;
    this.samplesLength = 0;
  }

  /** 绘制网格线 */
  private drawGrid() {
    const ctx = this.ctx;
    const w = this.displayWidth;
    const h = this.displayHeight;

    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 0.5;

    // 水平线
    for (let y = 0; y <= h; y += h / 4) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }
    // 垂直线（较稀疏）
    for (let x = 0; x <= w; x += w / 8) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }
  }

  /** 绘制波形标签 */
  private drawLabel(name: string) {
    const ctx = this.ctx;
    ctx.fillStyle = this.color;
    ctx.font = '10px monospace';
    ctx.fillText(name, 4, 12);
  }

  /** 检查并更新画布尺寸（DPR 适配） */
  private ensureSize() {
    const dpr = window.devicePixelRatio || 1;
    const dw = this.canvas.clientWidth;
    const dh = this.canvas.clientHeight;

    if (this.canvas.width !== dw * dpr || this.canvas.height !== dh * dpr) {
      this.canvas.width = dw * dpr;
      this.canvas.height = dh * dpr;
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    this.displayWidth = dw;
    this.displayHeight = dh;
  }
}

let renderer: null | WaveformRenderer = null;

/** 绘制循环 */
function draw() {
  if (!renderer || !canvasRef.value) return;
  renderer.draw(props.waveform);
  animFrameId = requestAnimationFrame(draw);
}

onMounted(() => {
  // nextTick 确保 DOM 已渲染，canvas 能获取到正确尺寸
  nextTick(() => {
    if (canvasRef.value) {
      renderer = new WaveformRenderer(canvasRef.value, props.color);
    }
    animFrameId = requestAnimationFrame(draw);
  });
});

onBeforeUnmount(() => {
  if (animFrameId !== null) {
    cancelAnimationFrame(animFrameId);
  }
  renderer = null;
});

watch(
  () => props.waveform,
  () => {
    if (renderer) {
      renderer.reset();
    }
  },
);

watch(
  () => props.color,
  (newColor) => {
    // 颜色变化时需要重建 renderer
    if (canvasRef.value) {
      renderer = new WaveformRenderer(canvasRef.value, newColor);
    }
  },
);
</script>

<template>
  <div class="imec-waveform-item">
    <canvas ref="canvasRef" :style="{ height: `${height}px` }" class="waveform-canvas"></canvas>
  </div>
</template>

<style scoped>
.imec-waveform-item {
  width: 100%;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 4px;
  overflow: hidden;
}

.waveform-canvas {
  width: 100%;
  min-height: 60px;
  display: block;
}
</style>
