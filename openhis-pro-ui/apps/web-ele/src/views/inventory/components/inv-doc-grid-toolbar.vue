<!--
  库房单据明细表工具栏：添加行 / 可选批量添加 / 删除勾选行。
-->
<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue';
import { ElButton } from 'element-plus';

defineOptions({ name: 'InvDocGridToolbar' });

withDefaults(
  defineProps<{
    disabled?: boolean;
    showAdd?: boolean;
    showBatchAdd?: boolean;
    showTraceScan?: boolean;
  }>(),
  {
    disabled: false,
    showAdd: true,
    showBatchAdd: false,
    showTraceScan: false,
  },
);

const emit = defineEmits<{
  add: [];
  batchAdd: [];
  deleteSelected: [];
  traceScan: [];
}>();
</script>

<template>
  <ElButton
    v-if="showAdd"
    class="mr-2"
    type="primary"
    :icon="Plus"
    :disabled="disabled"
    @click="emit('add')"
  >
    添加行
  </ElButton>
  <ElButton v-if="showBatchAdd" class="mr-2" :disabled="disabled" @click="emit('batchAdd')">
    批量添加
  </ElButton>
  <ElButton v-if="showTraceScan" class="mr-2" :disabled="disabled" @click="emit('traceScan')">
    扫追溯码
  </ElButton>
  <ElButton type="danger" :icon="Delete" :disabled="disabled" @click="emit('deleteSelected')">
    删除行
  </ElButton>
</template>
