<script setup lang="ts">
import { computed } from 'vue';

import { ElProgress } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

export interface InsuranceProgressStep {
  key: string;
  label: string;
}

defineOptions({
  name: 'InsuranceProgressDialog',
});

const props = withDefaults(
  defineProps<{
    /** 当前进行中的步骤下标（从 0 起） */
    activeIndex?: number;
    /** 条数进度当前值（费用上传等）；与 countTotal 同时有值时显示第二进度条 */
    countCurrent?: null | number;
    /** 条数进度文案；不传时默认「已上传 current/total」 */
    countLabelText?: null | string;
    /** 条数进度总数 */
    countTotal?: null | number;
    detail?: string;
    steps?: InsuranceProgressStep[];
    title?: string;
  }>(),
  {
    activeIndex: 0,
    countCurrent: null,
    countLabelText: null,
    countTotal: null,
    detail: '',
    steps: () => [],
    title: '处理中',
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

const countPercent = computed(() => {
  const total = props.countTotal;
  const current = props.countCurrent;
  if (total == null || total <= 0 || current == null) {
    return 0;
  }
  return Math.min(100, Math.round((Math.max(0, current) / total) * 100));
});

const showCountBar = computed(
  () => props.countTotal != null && props.countTotal > 0 && props.countCurrent != null,
);

const countLabel = computed(() => {
  if (!showCountBar.value) {
    return '';
  }
  if (props.countLabelText) {
    return props.countLabelText;
  }
  return `已上传 ${props.countCurrent}/${props.countTotal}`;
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
    <div class="insurance-progress">
      <p class="insurance-progress__hint">请勿关闭窗口或重复点击，医保通信可能需要数十秒。</p>
      <ElProgress :percentage="percent" :stroke-width="12" striped striped-flow />
      <ol class="insurance-progress__steps">
        <li
          v-for="(step, index) in steps"
          :key="step.key"
          class="insurance-progress__step"
          :class="`is-${stepState(index)}`"
        >
          <span class="insurance-progress__marker">
            <template v-if="stepState(index) === 'done'">✓</template>
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="insurance-progress__label">{{ step.label }}</span>
        </li>
      </ol>
      <div v-if="showCountBar" class="insurance-progress__count">
        <p class="insurance-progress__count-label">{{ countLabel }}</p>
        <ElProgress
          :percentage="countPercent"
          :stroke-width="10"
          striped
          striped-flow
          status="success"
        />
      </div>
      <p v-if="detail" class="insurance-progress__detail">{{ detail }}</p>
    </div>
  </AppDialog>
</template>

<style scoped>
.insurance-progress {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.insurance-progress__hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.insurance-progress__steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.insurance-progress__step {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 14px;
}

.insurance-progress__step.is-done {
  color: #16a34a;
}

.insurance-progress__step.is-active {
  color: #0f172a;
  font-weight: 600;
}

.insurance-progress__marker {
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

.insurance-progress__step.is-active .insurance-progress__marker {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.insurance-progress__step.is-done .insurance-progress__marker {
  background: #dcfce7;
  border-color: #16a34a;
  color: #16a34a;
}

.insurance-progress__count {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.insurance-progress__count-label {
  margin: 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.insurance-progress__detail {
  margin: 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
}
</style>
