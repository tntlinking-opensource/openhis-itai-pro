<script setup lang="ts">
import { computed } from 'vue';

import { ElProgress } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

export interface ChargeProgressStep {
  key: string;
  label: string;
}

defineOptions({
  name: 'ChargeProgressDialog',
});

const props = withDefaults(
  defineProps<{
    /** 当前进行中的步骤下标（从 0 起） */
    activeIndex?: number;
    detail?: string;
    steps?: ChargeProgressStep[];
    title?: string;
  }>(),
  {
    activeIndex: 0,
    detail: '',
    steps: () => [],
    title: '收费处理中',
  },
);

const visible = defineModel<boolean>({ required: true });

const percent = computed(() => {
  const total = props.steps.length;
  if (total <= 0) {
    return 0;
  }
  const done = Math.min(Math.max(props.activeIndex, 0), total);
  return Math.min(99, Math.round((done / total) * 100));
});

function stepState(index: number): 'active' | 'done' | 'pending' {
  if (index < props.activeIndex) {
    return 'done';
  }
  if (index === props.activeIndex) {
    return 'active';
  }
  return 'pending';
}
</script>

<template>
  <AppDialog
    v-model="visible"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    top="20vh"
  >
    <div class="charge-progress">
      <p class="charge-progress__hint">请勿关闭窗口或重复点击，医保通信可能需要数十秒。</p>
      <ElProgress :percentage="percent" :stroke-width="12" striped striped-flow />
      <ol class="charge-progress__steps">
        <li
          v-for="(step, index) in steps"
          :key="step.key"
          class="charge-progress__step"
          :class="`is-${stepState(index)}`"
        >
          <span class="charge-progress__marker">
            <template v-if="stepState(index) === 'done'">✓</template>
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="charge-progress__label">{{ step.label }}</span>
        </li>
      </ol>
      <p v-if="detail" class="charge-progress__detail">{{ detail }}</p>
    </div>
  </AppDialog>
</template>

<style scoped>
.charge-progress {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.charge-progress__hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.charge-progress__steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.charge-progress__step {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 14px;
}

.charge-progress__step.is-done {
  color: #16a34a;
}

.charge-progress__step.is-active {
  color: #0f172a;
  font-weight: 600;
}

.charge-progress__marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid currentColor;
  font-size: 12px;
  flex-shrink: 0;
}

.charge-progress__step.is-active .charge-progress__marker {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.charge-progress__step.is-done .charge-progress__marker {
  background: #dcfce7;
  border-color: #16a34a;
  color: #16a34a;
}

.charge-progress__detail {
  margin: 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
}
</style>
