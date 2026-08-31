<script setup lang="ts">
import { computed } from 'vue';

import { ElTooltip } from 'element-plus';

import { splitTraceNoCommaString } from '#/utils/trace';

defineOptions({ name: 'TraceCodeTooltip' });

interface Props {
  /** 追溯码逗号串或列表 */
  value?: string | string[];
  emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  value: '',
  emptyText: '—',
});

/** 解析为追溯码列表 */
const codes = computed(() => {
  if (Array.isArray(props.value)) {
    return props.value
      .map((code) => String(code ?? '').trim())
      .filter((code) => code !== '' && code !== '—');
  }
  return splitTraceNoCommaString(String(props.value ?? ''));
});

/** 单元格主文案：多码时个数用徽标常显，文案只留首码预览 */
const displayText = computed(() => {
  const n = codeCount.value;
  if (n <= 0) return '';
  if (n === 1) return codes.value[0] ?? '';
  const first = codes.value[0] ?? '';
  return first.length > 14 ? `${first.slice(0, 14)}…` : first;
});

const codeCount = computed(() => codes.value.length);

const hasCodes = computed(() => codeCount.value > 0);
</script>

<template>
  <ElTooltip
    v-if="hasCodes"
    placement="top"
    effect="light"
    :show-after="200"
    popper-class="trace-code-tooltip-popper"
  >
    <span class="trace-code-tooltip__text font-mono" :title="`共 ${codeCount} 个追溯码`">
      <span v-if="codeCount > 1" class="trace-code-tooltip__badge">{{ codeCount }}</span>
      {{ displayText }}
    </span>
    <template #content>
      <div class="trace-code-tooltip-panel">
        <div class="trace-code-tooltip-panel__head">
          <span class="trace-code-tooltip-panel__label">追溯码</span>
          <span class="trace-code-tooltip-panel__count">共 {{ codeCount }} 个</span>
        </div>
        <ul class="trace-code-tooltip-panel__list">
          <li v-for="code in codes" :key="code" class="trace-code-tooltip-panel__item font-mono">
            {{ code }}
          </li>
        </ul>
      </div>
    </template>
  </ElTooltip>
  <span v-else class="trace-code-tooltip__empty">{{ props.emptyText }}</span>
</template>

<style scoped lang="scss">
.trace-code-tooltip__text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-code-tooltip__badge {
  display: inline-block;
  min-width: 1.25rem;
  padding: 0 5px;
  margin-right: 4px;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
  color: var(--el-color-primary);
  text-align: center;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}

.trace-code-tooltip__empty {
  color: var(--el-text-color-placeholder);
}
</style>

<style lang="scss">
.trace-code-tooltip-popper {
  max-width: min(90vw, 480px);
  padding: 0 !important;
  background: var(--el-bg-color) !important;
  border: 1px solid var(--el-border-color-light) !important;
  box-shadow: var(--el-box-shadow-light) !important;
}

.trace-code-tooltip-popper .el-popper__arrow::before {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
}

.trace-code-tooltip-panel {
  min-width: 200px;
  max-height: 280px;
  padding: 10px 12px;
  overflow-y: auto;
}

.trace-code-tooltip-panel__head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.trace-code-tooltip-panel__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.trace-code-tooltip-panel__count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.trace-code-tooltip-panel__list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.trace-code-tooltip-panel__item {
  padding: 4px 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  letter-spacing: 0.03em;
  word-break: break-all;
}

.trace-code-tooltip-panel__item + .trace-code-tooltip-panel__item {
  border-top: 1px solid var(--el-border-color-extra-light);
}
</style>
