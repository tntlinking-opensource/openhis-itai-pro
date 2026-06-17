<script setup lang="ts">
import { computed } from 'vue';

import { buildMmRulerLabels, buildMmRulerTicks } from '#/utils/report/overlay-mm-ruler';

defineOptions({ name: 'OverlayMmRuler' });

const props = withDefaults(
  defineProps<{
    axis: 'horizontal' | 'vertical';
    lengthMm: number;
    majorStepMm?: number;
    labelStepMm?: number;
    /** 选中落格在标尺上的区间（mm） */
    highlightStart?: number | null;
    highlightEnd?: number | null;
  }>(),
  {
    majorStepMm: 10,
    labelStepMm: 10,
    highlightStart: null,
    highlightEnd: null,
  },
);

const RULER_DEPTH = 8;

const ticks = computed(() =>
  buildMmRulerTicks({
    lengthMm: props.lengthMm,
    majorStepMm: props.majorStepMm,
  }),
);

const labels = computed(() => buildMmRulerLabels(props.lengthMm, props.labelStepMm));

const viewBox = computed(() => {
  if (props.axis === 'horizontal') {
    return `0 0 ${props.lengthMm} ${RULER_DEPTH}`;
  }
  return `0 0 ${RULER_DEPTH} ${props.lengthMm}`;
});

const highlightRange = computed(() => {
  const start = props.highlightStart;
  const end = props.highlightEnd;
  if (start == null || end == null || !Number.isFinite(start) || !Number.isFinite(end)) {
    return null;
  }
  const lo = Math.max(0, Math.min(start, end));
  const hi = Math.min(props.lengthMm, Math.max(start, end));
  if (hi <= lo) {
    return null;
  }
  return { lo, hi };
});
</script>

<template>
  <svg
    class="overlay-mm-ruler"
    :class="`is-${axis}`"
    :viewBox="viewBox"
    preserveAspectRatio="none"
    aria-hidden="true"
  >
    <rect
      v-if="axis === 'horizontal'"
      :width="lengthMm"
      :height="RULER_DEPTH"
      class="overlay-mm-ruler__bg"
    />
    <rect v-else :width="RULER_DEPTH" :height="lengthMm" class="overlay-mm-ruler__bg" />
    <rect
      v-if="highlightRange && axis === 'horizontal'"
      :x="highlightRange.lo"
      y="0"
      :width="highlightRange.hi - highlightRange.lo"
      :height="RULER_DEPTH"
      class="overlay-mm-ruler__highlight"
    />
    <rect
      v-if="highlightRange && axis === 'vertical'"
      x="0"
      :y="highlightRange.lo"
      :width="RULER_DEPTH"
      :height="highlightRange.hi - highlightRange.lo"
      class="overlay-mm-ruler__highlight"
    />
    <template v-if="axis === 'horizontal'">
      <line
        v-for="tick in ticks"
        :key="`h-${tick.mm}`"
        :x1="tick.mm"
        :y1="0"
        :x2="tick.mm"
        :y2="tick.length"
        class="overlay-mm-ruler__tick"
        :class="{ 'is-major': tick.major }"
      />
      <text
        v-for="mm in labels"
        :key="`hl-${mm}`"
        :x="mm"
        :y="RULER_DEPTH - 0.6"
        class="overlay-mm-ruler__label"
        text-anchor="middle"
      >
        {{ mm }}
      </text>
    </template>
    <template v-else>
      <line
        v-for="tick in ticks"
        :key="`v-${tick.mm}`"
        :x1="0"
        :y1="tick.mm"
        :x2="tick.length"
        :y2="tick.mm"
        class="overlay-mm-ruler__tick"
        :class="{ 'is-major': tick.major }"
      />
      <text
        v-for="mm in labels"
        :key="`vl-${mm}`"
        :x="RULER_DEPTH - 0.8"
        :y="mm"
        class="overlay-mm-ruler__label"
        text-anchor="end"
        dominant-baseline="middle"
      >
        {{ mm }}
      </text>
    </template>
  </svg>
</template>

<style scoped>
.overlay-mm-ruler {
  display: block;
  width: 100%;
  height: 100%;
  user-select: none;
  pointer-events: none;
}

.overlay-mm-ruler__bg {
  fill: var(--el-fill-color-light);
}

.overlay-mm-ruler__highlight {
  fill: rgba(245, 108, 108, 0.28);
}

.overlay-mm-ruler__tick {
  stroke: var(--el-border-color);
  stroke-width: 0.18;
}

.overlay-mm-ruler__tick.is-major {
  stroke: var(--el-text-color-secondary);
  stroke-width: 0.28;
}

.overlay-mm-ruler__label {
  fill: var(--el-text-color-secondary);
  font-size: 2.1px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
