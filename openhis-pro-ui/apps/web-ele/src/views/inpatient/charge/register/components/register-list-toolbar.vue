<script setup lang="ts">
import type { OptionDto, RegistrationListStatus } from '#/api/inpatient-registration';

import { computed, nextTick } from 'vue';

import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus';

import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';

import { REGISTRATION_LIST_STATUS_OPTIONS } from '../inpatient-register.constants';

defineOptions({
  name: 'RegisterListToolbar',
});

const props = withDefaults(
  defineProps<{
    admissionDateFrom: string;
    admissionDateTo: string;
    idCard: string;
    keyword: string;
    organizationId: string;
    organizationOptions: OptionDto[];
    registrationListStatuses: RegistrationListStatus[];
    wardId: string;
    wardOptions: OptionDto[];
  }>(),
  {
    admissionDateFrom: '',
    admissionDateTo: '',
    idCard: '',
    keyword: '',
    organizationId: '',
    organizationOptions: () => [],
    registrationListStatuses: () => [],
    wardId: '',
    wardOptions: () => [],
  },
);

const emit = defineEmits<{
  noRecord: [];
  'organization-change': [value: string];
  search: [];
  'update:admissionDateFrom': [value: string];
  'update:admissionDateTo': [value: string];
  'update:idCard': [value: string];
  'update:keyword': [value: string];
  'update:organizationId': [value: string];
  'update:registrationListStatuses': [value: RegistrationListStatus[]];
  'update:wardId': [value: string];
}>();

/** 入院日期区间与 DatePicker 双向绑定。 */
const admissionDateRange = computed({
  get(): [string, string] | null {
    const from = props.admissionDateFrom.trim();
    const to = props.admissionDateTo.trim();
    if (from && to) {
      return [from, to];
    }
    return null;
  },
  set(value: [string, string] | null) {
    emit('update:admissionDateFrom', value?.[0] ?? '');
    emit('update:admissionDateTo', value?.[1] ?? '');
  },
});

/** 同步姓名关键字。 */
function handleKeywordChange(value: string) {
  emit('update:keyword', value);
}

/** 同步身份证检索值。 */
function handleIdCardChange(value: string) {
  emit('update:idCard', value);
}

/** 同步登记状态多选并在父组件写入后再刷新列表。 */
async function handleStatusChange(value: RegistrationListStatus[] | null | undefined) {
  emit('update:registrationListStatuses', value ?? []);
  await nextTick();
  emit('search');
}

/** 同步科室筛选；清空时顺带由父组件重载病区选项。 */
async function handleOrganizationChange(value: string | null | undefined) {
  const next = value ?? '';
  emit('update:organizationId', next);
  emit('organization-change', next);
  await nextTick();
  emit('search');
}

/** 同步病区筛选并刷新列表。 */
async function handleWardChange(value: string | null | undefined) {
  emit('update:wardId', value ?? '');
  await nextTick();
  emit('search');
}

/** 同步入院日期区间并刷新列表。 */
async function handleAdmissionDateChange(value: [string, string] | null) {
  admissionDateRange.value = value;
  await nextTick();
  emit('search');
}

/** 触发列表查询。 */
function handleSearch() {
  emit('search');
}

/** 打开无档登记弹窗。 */
function handleOpenNoRecord() {
  emit('noRecord');
}
</script>

<template>
  <div class="register-toolbar-actions flex w-full flex-wrap items-center justify-between gap-2">
    <div class="register-toolbar-actions__left flex min-w-0 flex-wrap items-center gap-2">
      <SelectFuzzyInput
        class="shrink-0"
        :model-value="props.keyword"
        placeholder="姓名/住院号"
        style="width: 180px; max-width: 180px; flex: 0 0 180px"
        @search="handleSearch"
        @update:model-value="handleKeywordChange"
      />
      <ElInput
        class="shrink-0"
        :model-value="props.idCard"
        clearable
        placeholder="身份证号"
        style="width: 180px; max-width: 180px; flex: 0 0 180px"
        @update:model-value="handleIdCardChange"
      />
      <ElSelect
        class="shrink-0"
        :model-value="props.organizationId || undefined"
        clearable
        filterable
        placeholder="住院科室"
        style="width: 160px; max-width: 160px; flex: 0 0 160px"
        @update:model-value="handleOrganizationChange"
      >
        <ElOption
          v-for="item in props.organizationOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElSelect
        class="shrink-0"
        :disabled="!props.organizationId"
        :model-value="props.wardId || undefined"
        clearable
        filterable
        placeholder="入院病区"
        style="width: 160px; max-width: 160px; flex: 0 0 160px"
        @update:model-value="handleWardChange"
      >
        <ElOption
          v-for="item in props.wardOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElDatePicker
        class="shrink-0"
        :model-value="admissionDateRange"
        clearable
        end-placeholder="入院止"
        range-separator="至"
        start-placeholder="入院起"
        style="width: 260px; max-width: 260px; flex: 0 0 260px"
        type="daterange"
        value-format="YYYY-MM-DD"
        @update:model-value="handleAdmissionDateChange"
      />
      <ElSelect
        class="shrink-0"
        :model-value="props.registrationListStatuses"
        clearable
        collapse-tags
        collapse-tags-tooltip
        multiple
        placeholder="登记状态"
        style="width: 220px; max-width: 220px; flex: 0 0 220px"
        @update:model-value="handleStatusChange"
      >
        <ElOption
          v-for="item in REGISTRATION_LIST_STATUS_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElButton class="shrink-0" type="primary" @click="handleSearch">查询</ElButton>
    </div>
    <ElButton class="shrink-0" type="primary" @click="handleOpenNoRecord">住院登记</ElButton>
  </div>
</template>
