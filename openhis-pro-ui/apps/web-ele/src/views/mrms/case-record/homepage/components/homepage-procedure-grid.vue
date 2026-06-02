<script setup lang="ts">
import type { ProcedureLineDto } from '#/api/mrms/types';

import { computed } from 'vue';

import { ElButton, ElDatePicker, ElInput, ElTable, ElTableColumn } from 'element-plus';

defineOptions({ name: 'HomepageProcedureGrid' });

interface Props {
  rows?: ProcedureLineDto[];
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  rows: () => [],
  readonly: false,
});

const emit = defineEmits<{
  'update:rows': [rows: ProcedureLineDto[]];
  change: [];
}>();

const localRows = computed({
  get: () => props.rows ?? [],
  set: (v) => {
    emit('update:rows', v);
    emit('change');
  },
});

/** 追加手术操作行 */
function addRow() {
  const nextSort =
    localRows.value.length > 0 ? Math.max(...localRows.value.map((r) => r.sortNo ?? 0)) + 1 : 1;
  localRows.value = [...localRows.value, { sortNo: nextSort, oprnOprtCode: '', oprnOprtName: '' }];
}

/** 删除指定行 */
function removeRow(index: number) {
  const copy = [...localRows.value];
  copy.splice(index, 1);
  localRows.value = copy;
}
</script>

<template>
  <div class="mb-8px flex items-center justify-between">
    <span class="text-14px font-500" style="color: var(--el-text-color-primary)"> 手术及操作 </span>
    <ElButton v-if="!readonly" link type="primary" @click="addRow"> 新增行 </ElButton>
  </div>
  <ElTable :data="localRows" border size="small" empty-text="暂无手术操作">
    <ElTableColumn type="index" label="序" width="50" align="center" />
    <ElTableColumn label="手术编码" width="140">
      <template #default="{ row }">
        <ElInput
          v-if="!readonly"
          v-model="row.oprnOprtCode"
          size="small"
          placeholder="ICD-9-CM-3"
          @input="emit('change')"
        />
        <span v-else>{{ row.oprnOprtCode }}</span>
      </template>
    </ElTableColumn>
    <ElTableColumn label="手术名称" min-width="180">
      <template #default="{ row }">
        <ElInput v-if="!readonly" v-model="row.oprnOprtName" size="small" @input="emit('change')" />
        <span v-else>{{ row.oprnOprtName }}</span>
      </template>
    </ElTableColumn>
    <ElTableColumn label="手术日期" width="160">
      <template #default="{ row }">
        <ElDatePicker
          v-if="!readonly"
          v-model="row.oprnDate"
          type="date"
          value-format="YYYY-MM-DD"
          size="small"
          class="w-full"
          @change="emit('change')"
        />
        <span v-else>{{ row.oprnDate }}</span>
      </template>
    </ElTableColumn>
    <ElTableColumn v-if="!readonly" label="操作" width="72" align="center">
      <template #default="{ $index }">
        <ElButton link type="danger" @click="removeRow($index)"> 删 </ElButton>
      </template>
    </ElTableColumn>
  </ElTable>
</template>
