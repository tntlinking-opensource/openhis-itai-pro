<script setup lang="ts">
import { ElButton, ElInput, ElOption, ElSelect } from 'element-plus';

import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';

import { REGISTRATION_LIST_STATUS_OPTIONS } from '../inpatient-register.constants';

defineOptions({
  name: 'RegisterListToolbar',
});

const props = withDefaults(
  defineProps<{
    idCard: string;
    keyword: string;
    registrationListStatus: string;
  }>(),
  {
    idCard: '',
    keyword: '',
    registrationListStatus: '',
  },
);

const emit = defineEmits<{
  noRecord: [];
  search: [];
  'update:idCard': [value: string];
  'update:keyword': [value: string];
  'update:registrationListStatus': [value: string];
}>();

/** 同步姓名关键字。 */
function handleKeywordChange(value: string) {
  emit('update:keyword', value);
}

/** 同步身份证检索值。 */
function handleIdCardChange(value: string) {
  emit('update:idCard', value);
}

/** 同步登记状态筛选并自动刷新列表。 */
function handleStatusChange(value: string) {
  emit('update:registrationListStatus', value);
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
  <div class="register-toolbar-actions flex w-full items-center justify-between gap-2">
    <div class="register-toolbar-actions__left flex items-center gap-2">
      <SelectFuzzyInput
        :model-value="props.keyword"
        placeholder="姓名"
        style="width: 220px; max-width: 220px; flex: 0 0 220px"
        @search="handleSearch"
        @update:model-value="handleKeywordChange"
      />
      <ElInput
        :model-value="props.idCard"
        clearable
        placeholder="请输入患者身份证号"
        style="width: 220px; max-width: 220px; flex: 0 0 220px"
        @update:model-value="handleIdCardChange"
      />
      <ElSelect
        :model-value="props.registrationListStatus"
        clearable
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
      <ElButton type="primary" @click="handleSearch">查询</ElButton>
    </div>
    <ElButton type="primary" @click="handleOpenNoRecord">住院登记</ElButton>
  </div>
</template>
