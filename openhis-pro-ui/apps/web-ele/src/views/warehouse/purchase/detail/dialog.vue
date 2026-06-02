<script setup lang="ts">
import { reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { getPurchaseById } from '#/api';
import { useRequest } from '#/hooks/use-request';

import Edit from './edit.vue';
import ReturnEdit from './return-edit.vue';
import { calcTotalPrice } from './util';

const emit = defineEmits<{
  success: [];
}>();

const [Drawer, drawerApi] = useVbenDrawer();

const dialogState = reactive<{
  isReturnOrder: boolean;
  state: 'edit' | 'return' | 'view';
  supplyBusNo: string;
}>({
  supplyBusNo: '',
  state: 'view',
  isReturnOrder: false,
});

const purchaseDetail = ref<any>(null);

const editRef = ref<InstanceType<typeof Edit> | InstanceType<typeof ReturnEdit> | null>(null);

const { run: getDetail, loading: getPurchaseByIdLoading } = useRequest(getPurchaseById, {
  manual: true,
  onSuccess: (data) => {
    const detail = data || {};
    const list = Array.isArray(detail.supplyList) ? detail.supplyList : [];
    purchaseDetail.value = {
      ...detail,
      supplyList: list.map((item) => {
        const unitList = [{ value: item.unitCode, label: item.unitCode_dictText }];
        if (item.minUnitCode) {
          unitList.push({ value: item.minUnitCode, label: item.minUnitCode_dictText });
        }
        if (dialogState.state === 'return') {
          return {
            ...item,
            returnQuantity: item.itemQuantity,
            totalSalePrice: calcTotalPrice(item.itemQuantity, item.price),
            unitList,
            isUnit: item.defUnitCode === item.unitCode,
            productInfo: {
              productName: item.name,
              id: item.itemId,
              unitCode: item.defUnitCode,
            },
          };
        }
        return {
          ...item,
          totalSalePrice: calcTotalPrice(item.itemQuantity, item.price),
          unitList,
          isUnit: item.defUnitCode === item.unitCode,
          productInfo: {
            productName: item.name,
            id: item.itemId,
            unitCode: item.defUnitCode,
          },
        };
      }),
    };
  },
});

const open = (supplyBusNo: string, state: 'edit' | 'return' | 'view', isReturnOrder = false) => {
  drawerApi.open();
  dialogState.supplyBusNo = supplyBusNo;
  dialogState.state = state;
  dialogState.isReturnOrder = isReturnOrder;
  getDetail({ busNo: supplyBusNo });
};

const close = () => {
  drawerApi.close();
};

const handleReturnSuccess = () => {
  drawerApi.close();
  emit('success');
};

defineExpose({
  open,
  close,
});
</script>

<template>
  <Drawer
    class="w-[80%]"
    :title="dialogState.supplyBusNo"
    :loading="getPurchaseByIdLoading"
    :footer="false"
    footer-class="justify-center"
  >
    <Edit
      v-if="!dialogState.isReturnOrder && dialogState.state !== 'return'"
      :state="dialogState.state === 'view' ? 'view' : 'edit'"
      :purchase-detail="purchaseDetail"
      @success="handleReturnSuccess"
      ref="editRef"
      is-in-dialog
    />
    <ReturnEdit
      v-else
      :state="dialogState.state === 'view' ? 'view' : 'return'"
      :purchase-detail="purchaseDetail"
      :original-bus-no="dialogState.supplyBusNo"
      @success="handleReturnSuccess"
      ref="editRef"
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
        <template v-if="dialogState.state === 'return'">
          <ElButton type="primary" @click="editRef?.save">确认退库</ElButton>
          <ElButton @click="close">取消</ElButton>
        </template>
        <template v-else>
          <ElButton type="primary" @click="editRef?.save">保存</ElButton>
          <ElButton type="primary" @click="editRef?.saveAndSubmit">保存并提交审核</ElButton>
        </template>
      </template>
    </template>
  </Drawer>
</template>
