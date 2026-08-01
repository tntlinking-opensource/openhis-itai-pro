<script setup lang="ts">
import { computed } from 'vue';

import './station-tri-pane-layout.css';

/**
 * 门诊/临床「工作站」三窗格布局骨架（左队列 / 中主区 / 右辅助 + 底栏）。
 *
 * 「TriPane」表示左中右三块主工作区；布局不持有业务状态，全部通过具名 slot 注入。
 * `showLeft` / `showRight` 控制是否渲染侧栏并参与 grid 列拼接；中间栏始终占位。
 * `showFooter` 控制是否渲染底栏；为 false 时不占高度，footer 插槽不会挂载。
 *
 * 列宽通过 `*Track` / `*TrackLg` 传入合法 CSS grid 轨；仅显示对应侧栏时才会加入该轨。
 * `centerTrackLg` 未传时沿用 `centerTrack`。
 *
 * @slot left    左栏内容（患者队列等）
 * @slot center  中栏内容（主操作/执行表格等）
 * @slot right   右栏内容（耗材、打印、执行记录等）
 * @slot footer  底部吸附工具栏内容（仅 `showFooter` 为 true 时渲染）
 * @slot drawer  抽屉/弹层内容（可选，置于布局最外层）
 */
defineOptions({ name: 'StationTriPaneLayout' });

const props = withDefaults(
  defineProps<{
    centerTrack?: string;
    centerTrackLg?: string;
    leftTrack?: string;
    leftTrackLg?: string;
    rightTrack?: string;
    rightTrackLg?: string;
    /** 是否渲染底栏（footer 插槽），默认 true */
    showFooter?: boolean;
    /** 是否渲染左栏并占用左轨，默认 true */
    showLeft?: boolean;
    /** 是否渲染右栏并占用右轨，默认 true */
    showRight?: boolean;
  }>(),
  {
    centerTrack: 'minmax(0, 22fr)',
    leftTrack: '260px',
    rightTrack: 'minmax(260px, 9fr)',
    leftTrackLg: '260px',
    rightTrackLg: 'minmax(280px, 9fr)',
    showFooter: true,
    showLeft: true,
    showRight: true,
  },
);

const centerTrackLgResolved = computed(() => props.centerTrackLg ?? props.centerTrack);

const gridCols = computed(() => {
  const parts: string[] = [];
  if (props.showLeft) {
    parts.push(props.leftTrack);
  }
  parts.push(props.centerTrack);
  if (props.showRight) {
    parts.push(props.rightTrack);
  }
  return parts.join(' ');
});

const gridColsLg = computed(() => {
  const parts: string[] = [];
  if (props.showLeft) {
    parts.push(props.leftTrackLg);
  }
  parts.push(centerTrackLgResolved.value);
  if (props.showRight) {
    parts.push(props.rightTrackLg);
  }
  return parts.join(' ');
});

const gridCssVars = computed(() => ({
  '--stp-grid-cols': gridCols.value,
  '--stp-grid-cols-lg': gridColsLg.value,
}));
</script>

<template>
  <div class="station-tri-pane-layout flex h-full min-h-0 flex-col">
    <div class="bg-background-deep flex min-h-0 flex-1 flex-col">
      <!-- 主区 grid：列数随 showLeft / showRight 变化 -->
      <div
        class="station-tri-pane-layout__grid grid min-h-0 flex-1 gap-1.5 overflow-hidden p-1.5"
        :class="{ 'pb-1': showFooter }"
        :style="gridCssVars"
      >
        <!-- 左栏禁止 min-w-0：否则栅格列 minmax(…px) 仍可能被压成「看不见」，患者队列等侧栏会「消失」 -->
        <section
          v-if="showLeft"
          class="bg-background flex h-full w-full min-w-[min(100%,260px)] shrink-0 flex-col overflow-hidden rounded-none transition-all duration-300"
        >
          <slot name="left"></slot>
        </section>

        <section
          class="bg-background mx-0.5 flex min-h-0 min-w-0 flex-col overflow-hidden rounded-none sm:mx-1"
        >
          <slot name="center"></slot>
        </section>

        <!-- 右栏与左栏同理，避免 min-w-0 把辅助侧栏压没 -->
        <section
          v-if="showRight"
          class="bg-background flex h-full min-w-[min(100%,240px)] shrink-0 flex-col overflow-hidden rounded-none"
        >
          <slot name="right"></slot>
        </section>
      </div>

      <footer
        v-if="showFooter"
        class="bg-background flex shrink-0 flex-wrap items-center justify-between gap-2 px-2 py-2 shadow-[0_-6px_18px_rgba(15,23,42,0.07)]"
      >
        <slot name="footer"></slot>
      </footer>
    </div>

    <slot name="drawer"></slot>
  </div>
</template>
