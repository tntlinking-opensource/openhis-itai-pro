<!--
  库房明细表工具栏：添加行 / 删除行 / 保存 + 可选第四钮（订货=警戒订货，退货=进货单导入）。
-->
<script lang="ts" setup>
import { Check, Delete, Plus, Upload, Warning } from '@element-plus/icons-vue';
import { ElButton } from 'element-plus';

defineOptions({
  name: 'WarehouseOrderDetailToolbar',
});

defineProps<{
  buttonShow: {
    isAddShow?: boolean;
    isAlertShow?: boolean;
    isDeleteShow?: boolean;
    isOrderImport?: boolean;
    isSaveShow?: boolean;
  };
}>();

const emit = defineEmits<{
  addRow: [];
  alertOrder: [];
  deleteRow: [];
  orderImport: [];
  save: [];
}>();
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <ElButton v-if="buttonShow.isAddShow" type="primary" :icon="Plus" @click="emit('addRow')">
      添加行
    </ElButton>
    <ElButton
      v-if="buttonShow.isDeleteShow"
      type="danger"
      :icon="Delete"
      @click="emit('deleteRow')"
    >
      删除行
    </ElButton>
    <ElButton v-if="buttonShow.isSaveShow" type="warning" :icon="Check" @click="emit('save')">
      保存
    </ElButton>
    <ElButton v-if="buttonShow.isAlertShow" type="info" :icon="Warning" @click="emit('alertOrder')">
      警戒订货
    </ElButton>
    <ElButton
      v-if="buttonShow.isOrderImport"
      type="info"
      :icon="Upload"
      @click="emit('orderImport')"
    >
      进货单导入
    </ElButton>
  </div>
</template>
