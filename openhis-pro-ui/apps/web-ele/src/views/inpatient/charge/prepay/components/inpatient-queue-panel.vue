<script setup lang="ts">
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';
import type { PrepayStayScope } from '../inpatient-prepay.constants';

import { computed } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker, ElRadio, ElRadioGroup } from 'element-plus';

import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';

import { PREPAY_STAY_SCOPE, PREPAY_STAY_SCOPE_OPTIONS } from '../inpatient-prepay.constants';

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
    stayScope: PrepayStayScope;
  }>(),
  {
    loading: false,
  },
);

const emit = defineEmits<{
  refresh: [];
  select: [patient: BillingPatientCardPatient];
  'update:date': [value: string];
  'update:keyword': [value: string];
  'update:stayScope': [value: PrepayStayScope];
}>();

/** 侧栏标题随在院范围变化 */
const sectionTitle = computed(() => {
  if (props.stayScope === PREPAY_STAY_SCOPE.NOT_IN_HOSPITAL) {
    return '不在院患者';
  }
  if (props.stayScope === PREPAY_STAY_SCOPE.ALL) {
    return '患者队列';
  }
  return '在院患者';
});

/** 空列表提示文案 */
const emptyText = computed(() => {
  if (props.stayScope === PREPAY_STAY_SCOPE.NOT_IN_HOSPITAL) {
    return '暂无不在院患者';
  }
  if (props.stayScope === PREPAY_STAY_SCOPE.ALL) {
    return '暂无患者';
  }
  return '暂无在院患者';
});

/** 向父级同步左侧关键字。 */
function handleKeywordChange(value: string) {
  emit('update:keyword', value);
}

/** 向父级同步左侧日期。 */
function handleDateChange(value: string) {
  emit('update:date', value);
}

/** 向父级同步在院范围。 */
function handleStayScopeChange(value: string | number | boolean | undefined) {
  emit('update:stayScope', String(value ?? '') as PrepayStayScope);
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
      <SectionTitle :title="sectionTitle" />
      <div class="charge-filter-toolbar mt-2">
        <ElRadioGroup
          :model-value="props.stayScope"
          class="charge-filter-toolbar__stay-scope w-full"
          size="small"
          @update:model-value="handleStayScopeChange"
        >
          <ElRadio
            v-for="opt in PREPAY_STAY_SCOPE_OPTIONS"
            :key="opt.value"
            :value="opt.value"
            border
          >
            {{ opt.label }}
          </ElRadio>
        </ElRadioGroup>
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
            title="刷新患者队列"
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
        <div class="py-8 text-center text-sm text-gray-400">{{ emptyText }}</div>
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

.charge-filter-toolbar__stay-scope {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.charge-filter-toolbar__stay-scope :deep(.el-radio) {
  margin-right: 0;
}

.charge-filter-toolbar__date :deep(.el-input__wrapper) {
  width: 100%;
}

.charge-patient-scroll {
  min-height: 200px;
}
</style>
