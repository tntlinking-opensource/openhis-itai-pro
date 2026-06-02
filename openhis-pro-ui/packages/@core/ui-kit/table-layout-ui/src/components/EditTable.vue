<script lang="ts" setup>
import type { VxeGridInstance, VxeGridProps } from 'vxe-table';

import { reactive, useTemplateRef } from 'vue';

import { VxeGrid } from 'vxe-table';

interface RowVO {
  id: number;
  name: string;
  nickname: string;
}

const gridRef = useTemplateRef<VxeGridInstance>('gridRef');

const gridOptions = reactive<VxeGridProps<RowVO>>({
  border: true,
  showOverflow: true,
  keepSource: true,
  editConfig: {
    trigger: 'click',
    mode: 'row',
  },
  editRules: {
    nickname: [{ required: true, content: '请输入' }],
  },
  columns: [
    { type: 'checkbox', width: 60 },
    { type: 'seq', title: 'Number', width: 80 },
    { field: 'name', title: 'Name', minWidth: 140, editRender: { name: 'ElInput' } },
    { field: 'nickname', title: '输入框', width: 200, editRender: { name: 'ElInput' } },
  ],
  data: [
    { id: 10_001, name: 'Test1', nickname: 'Nickname11' },
    { id: 10_002, name: 'Test2', nickname: '' },
  ],
});

defineExpose({
  getGridRef: () => gridRef.value,
});
</script>

<template>
  <div>
    <VxeGrid ref="gridRef" v-bind="gridOptions" />
  </div>
</template>
