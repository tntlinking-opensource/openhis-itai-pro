<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import { computed } from 'vue';

import { Table } from '@vben-core/table-layout-ui';

defineOptions({ name: 'DiagnosisSavedTable' });

const props = defineProps<{
  rows: EncounterDiagnosisRowDto[];
}>();

const emit = defineEmits<{
  'add-click': [];
  remove: [row: EncounterDiagnosisRowDto];
}>();

const tableColumns = computed<TableColumn[]>(() => [
  { type: 'index', label: '#', width: 52, align: 'center', headerAlign: 'center' },
  { prop: 'conditionCode', label: '诊断编码', width: 180 },
  { prop: 'conditionDefinitionName', label: '诊断名称', minWidth: 220 },
  {
    prop: 'diagnosisKindText',
    label: '类别',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'mainDiagnosisFlag',
    label: '主诊',
    width: 72,
    align: 'center',
    headerAlign: 'center',
    slot: 'mainFlag',
  },
  {
    label: '操作',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'actions',
  },
]);
</script>

<template>
  <section class="diagnosis-table-wrap">
    <div class="diagnosis-list-header">
      <ElButton type="primary" class="mb-2" @click="emit('add-click')">新增诊断</ElButton>
    </div>
    <Table
      class="min-h-0 flex-1"
      row-key="conditionId"
      :table-data="props.rows"
      :table-columns="tableColumns"
      :show-pagination="false"
      :toolbar-config="{
        custom: false,
        export: false,
        refresh: false,
        zoom: false,
      }"
      border
    >
      <template #mainFlag="{ row }">
        <span>{{ row.mainDiagnosisFlag === 1 ? '是' : '否' }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="danger" @click="emit('remove', row)">删除</ElButton>
      </template>
    </Table>
  </section>
</template>

<style scoped>
.diagnosis-list-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.diagnosis-table-wrap {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
</style>
