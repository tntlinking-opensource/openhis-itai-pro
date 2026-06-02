<script setup lang="ts">
import type { TransferDetailResponse } from '#/api/warehouse/transfer';

import { computed, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { findTransferByBusNo } from '#/api/warehouse/transfer';
import { useRequest } from '#/hooks/use-request';

import Edit from './edit.vue';

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
  title: '新增调拨',
});

const transferDetail = ref<null | TransferDetailResponse>(null);
const editRef = ref<InstanceType<typeof Edit> | null>(null);

const { run: getDetail, loading: getDetailLoading } = useRequest(findTransferByBusNo, {
  manual: true,
  onSuccess: (res) => {
    transferDetail.value = res || null;
  },
});

const open = (supplyBusNo?: string, state: 'edit' | 'view' = 'edit') => {
  drawerApi.open();
  dialogState.supplyBusNo = supplyBusNo ?? '';
  dialogState.state = state;
  dialogState.title = state === 'view' ? '查看调拨' : '编辑调拨';
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
    <Edit
      :state="dialogState.state === 'view' ? 'view' : 'edit'"
      :transfer-detail="transferDetail"
      @success="handleSaveSuccess"
      ref="editRef"
      :is-in-dialog="true"
    />
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
