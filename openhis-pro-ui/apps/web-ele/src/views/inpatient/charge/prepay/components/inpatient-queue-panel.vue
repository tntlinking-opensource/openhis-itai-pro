<script setup lang="ts">
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker } from 'element-plus';

import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';

defineOptions({
  name: 'InpatientQueuePanel',
});

const props = withDefaults(
  defineProps<{
    date: string;
    keyword: string;
    list: BillingPatientCardPatient[];
    loading?: boolean;
    selectedId: string;
  }>(),
  {},
);

const emit = defineEmits<{
  refresh: [];
  select: [patient: BillingPatientCardPatient];
  'update:date': [value: string];
  'update:keyword': [value: string];
}>();

/** 向父级同步左侧关键字。 */
function handleKeywordChange(value: string) {
  emit('update:keyword', value);
}

/** 向父级同步左侧日期。 */
function handleDateChange(value: string) {
  emit('update:date', value);
}

/** 点击卡片后通知父级切换当前患者。 */
function handleSelect(patient: BillingPatientCardPatient) {
  emit('select', patient);
}

/** 刷新按钮统一交由父级触发请求。 */
function handleRefresh() {
  emit('refresh');
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2">
    <div class="shrink-0 px-0.5 pt-0.5">
      <SectionTitle title="在院患者" />
      <div class="charge-filter-toolbar mt-2">
        <SelectFuzzyInput
          :model-value="props.keyword"
          placeholder="住院号 / 姓名 / 床号"
          full-width
          @update:model-value="handleKeywordChange"
        />
        <div class="charge-filter-toolbar__date-row flex min-w-0 items-center gap-1.5">
          <ElDatePicker
            :model-value="props.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="入院日期"
            clearable
            class="charge-filter-toolbar__date min-w-0 flex-1"
            @update:model-value="handleDateChange"
          />
          <ElButton
            type="default"
            class="!shrink-0 !px-2"
            title="刷新在院患者"
            @click="handleRefresh"
          >
            <Refresh class="h-4 w-4" />
          </ElButton>
        </div>
      </div>
    </div>
    <div class="charge-patient-scroll min-h-0 flex-1 space-y-2 overflow-y-auto px-0.5 pb-1">
      <template v-if="props.loading">
        <div class="py-8 text-center text-sm text-gray-400">患者列表加载中...</div>
      </template>
      <template v-else-if="props.list.length === 0">
        <div class="py-8 text-center text-sm text-gray-400">暂无在院患者</div>
      </template>
      <BillingPatientCard
        v-for="row in props.list"
        :key="row.id"
        :patient="row"
        :selected="row.id === props.selectedId"
        @select="handleSelect"
      />
    </div>
  </div>
</template>

<style scoped>
.charge-filter-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.charge-filter-toolbar__date :deep(.el-input__wrapper) {
  width: 100%;
}

.charge-patient-scroll {
  min-height: 200px;
}
</style>
