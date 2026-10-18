<script setup lang="ts">
import type { DrawDetailResponse } from '#/api/warehouse/draw';

import { computed, reactive, ref } from 'vue';

import { useVbenDrawer } from '@zxplat/common-ui';

import { findRequisitionByBusNoApi } from '#/api/warehouse/draw';
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
  title: '新增领用',
});

const drawDetail = ref<null | DrawDetailResponse>(null);
const editRef = ref<InstanceType<typeof Edit> | null>(null);

const { run: getDetail, loading: getDetailLoading } = useRequest(findRequisitionByBusNoApi, {
  manual: true,
  onSuccess: (res) => {
    drawDetail.value = res || null;
  },
});

const open = (supplyBusNo?: string, state: 'edit' | 'view' = 'edit') => {
  drawerApi.open();
  dialogState.supplyBusNo = supplyBusNo ?? '';
  dialogState.state = state;
  dialogState.title = supplyBusNo ? (state === 'view' ? '查看领用' : '编辑领用') : '新增领用';
  drawDetail.value = null;
  if (supplyBusNo) {
    getDetail({ busNo: supplyBusNo });
  }
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
      :draw-detail="drawDetail"
      :is-in-dialog="true"
      ref="editRef"
      @success="handleSaveSuccess"
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
