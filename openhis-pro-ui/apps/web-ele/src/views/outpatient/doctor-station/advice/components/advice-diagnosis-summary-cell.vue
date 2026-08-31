<script setup lang="ts">
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { computed } from 'vue';

import {
  buildAdviceDiagnosisDisplaySegments,
  buildDiagnosisLookupById,
} from '../services/advice-diagnosis-display';

defineOptions({ name: 'AdviceDiagnosisSummaryCell' });

interface Props {
  diagnosisSummary?: string;
  encounterDiagnosisIds?: string;
  /** 当前就诊（或历史就诊）诊断候选项，用于解析慢病角标 */
  diagnosisItems?: DiagnosisItem[];
}

const props = withDefaults(defineProps<Props>(), {
  diagnosisSummary: '—',
  encounterDiagnosisIds: '',
  diagnosisItems: () => [],
});

/** 诊断名 + 慢/特角标分段 */
const segments = computed(() =>
  buildAdviceDiagnosisDisplaySegments({
    diagnosisSummary: props.diagnosisSummary,
    encounterDiagnosisIds: props.encounterDiagnosisIds,
    diagnosisById: buildDiagnosisLookupById(props.diagnosisItems ?? []),
  }),
);

const tooltipText = computed(() =>
  segments.value.map((seg) => (seg.tag ? `${seg.name}（${seg.tag}）` : seg.name)).join('、'),
);
</script>

<template>
  <span class="advice-dx-cell" :title="tooltipText">
    <template v-for="(seg, index) in segments" :key="`${seg.name}-${index}`">
      <span v-if="index > 0" class="advice-dx-cell__sep">、</span>
      <span class="advice-dx-cell__name">{{ seg.name }}</span>
      <span v-if="seg.tag" class="advice-dx-cell__chronic">{{ seg.tag }}</span>
    </template>
  </span>
</template>

<style scoped>
.advice-dx-cell {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 2px 0;
  max-width: 100%;
  color: var(--el-text-color-primary);
  font-size: inherit;
  line-height: 1.4;
  word-break: break-all;
}

.advice-dx-cell__sep {
  color: var(--el-text-color-regular);
}

.advice-dx-cell__name {
  max-width: 10em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.advice-dx-cell__chronic {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 2px;
  padding: 0 4px;
  border: 1.5px solid #0f766e;
  border-radius: 3px;
  background: transparent;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  vertical-align: middle;
}
</style>
