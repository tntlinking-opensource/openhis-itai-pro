<script setup lang="ts">
import type { DispenseQueuePatient } from './types';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';

import { ElButton, ElDatePicker, ElInput, ElRadio, ElRadioGroup } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';

import { toDispensePatientCardModel } from './dispense-patient-card-map';

defineOptions({
  name: 'DispenseQueuePanel',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  queueDateRangeChange: [range: [Date | string, Date | string] | null];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

interface Props {
  filteredPatients: DispenseQueuePatient[];
  loadingQueue?: boolean;
  selectedPatientId: null | string;
}

const keyword = defineModel<string>('keyword', { required: true });
const queueDateRange = defineModel<[string, string] | null>('queueDateRange', { required: true });
const queueScope = defineModel<'completed' | 'pending'>('queueScope', { required: true });

/** 区间查询：展开面板默认快捷时间范围 */
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();

const isQueueListRefreshing = ref(false);

/** 触发展示旋转动画并通知父级重新拉队列 */
function handleRefresh() {
  isQueueListRefreshing.value = true;
  emit('refresh');
  window.setTimeout(() => {
    isQueueListRefreshing.value = false;
  }, 400);
}

function onPatientCardSelect(card: PatientCardPatient) {
  if ('patientType' in card && card.patientType === 'inpatient') {
    emit('selectPatient', { id: card.id });
    return;
  }
  emit('selectPatient', { id: (card as OutpatientPatientCardPayload).encounterId });
}
</script>

<template>
  <div class="dispense-queue-panel flex h-full min-h-0 flex-col">
    <div class="dispense-queue-panel__filters mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="待发药患者" />
      <ElRadioGroup
        v-model="queueScope"
        class="dispense-queue-scope flex flex-wrap items-center gap-x-4 gap-y-1"
      >
        <ElRadio value="pending">待发药</ElRadio>
        <ElRadio value="completed">已发药</ElRadio>
      </ElRadioGroup>
      <div class="dispense-daterange-row w-full min-w-0">
        <ElDatePicker
          v-model="queueDateRange"
          class="dispense-daterange w-full"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :shortcuts="dateRangeShortcuts"
          @change="emit('queueDateRangeChange', $event as [Date | string, Date | string] | null)"
        />
      </div>
      <div class="flex min-w-0 items-center gap-1.5">
        <ElInput v-model="keyword" clearable placeholder="姓名 / 排队号" class="min-w-0 flex-1">
          <template #prefix>
            <Search class="h-4 w-4" />
          </template>
        </ElInput>
        <ElButton
          type="default"
          class="shrink-0 !px-2"
          :loading="props.loadingQueue"
          @click="handleRefresh"
        >
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isQueueListRefreshing }" />
        </ElButton>
      </div>
    </div>
    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="filteredPatients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="p in filteredPatients"
          :key="p.id"
          variant="dispense"
          :patient="toDispensePatientCardModel(p)"
          :selected="selectedPatientId === p.id"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dispense-daterange-row :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  --el-date-editor-daterange-width: 100%;
}
</style>
