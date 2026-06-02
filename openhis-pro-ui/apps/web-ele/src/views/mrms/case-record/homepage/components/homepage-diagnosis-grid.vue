<script setup lang="ts">
import type { DiagnosisLineDto } from '#/api/mrms/types';

import { computed } from 'vue';

import { ElButton, ElInput, ElTable, ElTableColumn } from 'element-plus';

import { HOMEPAGE_DIAG_TYPES } from '../../case-record.constants';
import MrmsDiagnosisPicker from './mrms-diagnosis-picker.vue';

defineOptions({ name: 'HomepageDiagnosisGrid' });

interface Props {
  rows?: DiagnosisLineDto[];
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  rows: () => [],
  readonly: false,
});

const emit = defineEmits<{
  'update:rows': [rows: DiagnosisLineDto[]];
  change: [];
}>();

const localRows = computed({
  get: () => props.rows ?? [],
  set: (v) => emit('update:rows', v),
});

/** 按诊断类型筛选当前块行 */
function rowsOfType(typeCode: string): DiagnosisLineDto[] {
  return localRows.value.filter((r) => r.diagTypeCode === typeCode);
}

/** 合并某类型行回全表 */
function replaceTypeRows(typeCode: string, typeRows: DiagnosisLineDto[]) {
  const others = localRows.value.filter((r) => r.diagTypeCode !== typeCode);
  localRows.value = [...others, ...typeRows];
  emit('change');
}

/** 为指定类型追加空行 */
function addRow(typeCode: string) {
  const typeRows = rowsOfType(typeCode);
  const nextSort = typeRows.length > 0 ? Math.max(...typeRows.map((r) => r.sortNo ?? 0)) + 1 : 1;
  replaceTypeRows(typeCode, [
    ...typeRows,
    { diagTypeCode: typeCode, sortNo: nextSort, diagCode: '', diagName: '', admCondCode: '' },
  ]);
}

/** 删除类型块内一行 */
function removeRow(typeCode: string, indexInType: number) {
  const typeRows = rowsOfType(typeCode);
  typeRows.splice(indexInType, 1);
  replaceTypeRows(typeCode, typeRows);
}

/** Picker 选中后写名称 */
function onPicked(typeCode: string, indexInType: number, item: { code?: string; name?: string }) {
  const typeRows = [...rowsOfType(typeCode)];
  const row = typeRows[indexInType];
  if (!row) {
    return;
  }
  row.diagCode = item.code ?? row.diagCode;
  row.diagName = item.name ?? row.diagName;
  replaceTypeRows(typeCode, typeRows);
}
</script>

<template>
  <div v-for="block in HOMEPAGE_DIAG_TYPES" :key="block.code" class="mb-16px">
    <div class="mb-8px flex items-center justify-between">
      <span class="text-14px font-500" style="color: var(--el-text-color-primary)">
        {{ block.label }}
      </span>
      <ElButton v-if="!readonly" link type="primary" @click="addRow(block.code)"> 新增行 </ElButton>
    </div>
    <ElTable :data="rowsOfType(block.code)" border size="small" empty-text="暂无诊断">
      <ElTableColumn type="index" label="序" width="50" align="center" />
      <ElTableColumn label="诊断编码" min-width="160">
        <template #default="{ row, $index }">
          <MrmsDiagnosisPicker
            v-if="!readonly"
            v-model="row.diagCode"
            :selected-label="row.diagName"
            @picked="(item) => onPicked(block.code, $index, item)"
          />
          <span v-else>{{ row.diagCode }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="诊断名称" min-width="200" prop="diagName" show-overflow-tooltip />
      <ElTableColumn label="入院病情" width="120">
        <template #default="{ row }">
          <ElInput
            v-if="!readonly"
            v-model="row.admCondCode"
            placeholder="病情"
            size="small"
            @input="emit('change')"
          />
          <span v-else>{{ row.admCondCode }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!readonly" label="操作" width="72" align="center">
        <template #default="{ $index }">
          <ElButton link type="danger" @click="removeRow(block.code, $index)"> 删 </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
