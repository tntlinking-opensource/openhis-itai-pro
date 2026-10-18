<script setup lang="ts">
import type { InventoryDetailResponse } from '#/api/warehouse/inventory';

import { computed, reactive, ref } from 'vue';

import { useVbenDrawer } from '@zxplat/common-ui';

import { findInventoryByBusNoApi } from '#/api/warehouse/inventory';
import { useRequest } from '#/hooks/use-request';

import Edit from './edit.vue';
import BatchEdit from './batch-edit.vue';

const emit = defineEmits<{
  success: [];
}>();

const [Drawer, drawerApi] = useVbenDrawer();

const dialogState = reactive<{
  state: 'edit' | 'view';
  supplyBusNo: string;
  title: string;
}>({
  state: 'edit',
  supplyBusNo: '',
  title: '新增盘点',
});

const inventoryDetail = ref<InventoryDetailResponse | null>(null);
const editRef = ref<any | null>(null);

const isBatchInventory = computed(() => inventoryDetail.value?.isBatches === true);

const { run: getDetail, loading: getDetailLoading } = useRequest(findInventoryByBusNoApi, {
  manual: true,
  onSuccess: (res) => {
    inventoryDetail.value = res || null;
  },
});

const open = (supplyBusNo?: string, state: 'edit' | 'view' = 'edit') => {
  drawerApi.open();
  dialogState.supplyBusNo = supplyBusNo ?? '';
  dialogState.state = state;
  dialogState.title = state === 'view' ? '查看盘点' : '编辑盘点';
  getDetail({ busNo: supplyBusNo });
};

const close = () => {
  drawerApi.close();
};

const handleSaveSuccess = () => {
  drawerApi.close();
  emit('success');
};

const saveLoading = computed(
  () => (editRef.value as null | { saveLoading?: { value: boolean } })?.saveLoading?.value ?? false,
);

defineExpose({
  open,
  close,
});
</script>

<template>
  <Drawer
    class="w-[80%]"
    :title="dialogState.supplyBusNo || dialogState.title"
    :loading="getDetailLoading"
    :footer="false"
    footer-class="justify-center"
  >
    <template v-if="inventoryDetail">
      <BatchEdit
        v-if="isBatchInventory"
        :state="dialogState.state === 'view' ? 'view' : 'edit'"
        :inventory-detail="inventoryDetail"
        :is-in-dialog="true"
        ref="editRef"
        @success="handleSaveSuccess"
      />
      <Edit
        v-else
        :state="dialogState.state === 'view' ? 'view' : 'edit'"
        :inventory-detail="inventoryDetail"
        :is-in-dialog="true"
        ref="editRef"
        @success="handleSaveSuccess"
      />
    </template>
    <template
      #footer
      v-if="(dialogState.state === 'view' && $slots.footer) || dialogState.state !== 'view'"
    >
      <slot
        name="footer"
        :bus-no="dialogState.supplyBusNo"
        :state="dialogState.state"
        :close="close"
      />
      <template v-if="!$slots.footer && dialogState.state !== 'view'">
        <ElButton type="primary" :loading="saveLoading" @click="editRef?.save">保存</ElButton>
        <ElButton type="primary" :loading="saveLoading" @click="editRef?.saveAndSubmit">
          保存并提交审核
        </ElButton>
        <ElButton @click="close">取消</ElButton>
      </template>
    </template>
  </Drawer>
</template>
