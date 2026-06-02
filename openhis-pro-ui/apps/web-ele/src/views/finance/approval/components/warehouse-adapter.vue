<script setup lang="ts">
import type { Component } from 'vue';

import { computed, nextTick, ref } from 'vue';

import type { PagePendingApprovalOrdersResponse } from '#/api/finance';

import {
  WarehouseDrawDrawer,
  WarehouseInventoryDrawer,
  WarehouseLossDrawer,
  WarehousePurchaseDrawer,
  WarehouseTransferDrawer,
} from '#/views/warehouse/_exports';

type WarehouseDrawerType = 'purchase' | 'draw' | 'inventory' | 'loss' | 'transfer';
type WarehouseDrawerTypeEnumValue = string | number;

type CommonDrawerExpose = {
  open: (busNo?: string, state?: 'edit' | 'view') => void;
  close: () => void;
};

type PurchaseDrawerExpose = {
  open: (busNo: string, state: 'edit' | 'return' | 'view', isReturnOrder?: boolean) => void;
  close: () => void;
};

export type FinanceWarehouseDrawerOpenOptions = {
  busNo: string;
  type: WarehouseDrawerType | WarehouseDrawerTypeEnumValue;
  row?: PagePendingApprovalOrdersResponse;
};

type AnyDrawerExpose = CommonDrawerExpose | PurchaseDrawerExpose;
const drawerRef = ref<AnyDrawerExpose | null>(null);

const current = ref<FinanceWarehouseDrawerOpenOptions | null>(null);

function normalizeWarehouseDrawerType(
  type: FinanceWarehouseDrawerOpenOptions['type'],
): WarehouseDrawerType | null {
  if (
    type === 'purchase' ||
    type === 'draw' ||
    type === 'inventory' ||
    type === 'loss' ||
    type === 'transfer'
  ) {
    return type;
  }

  const v = String(type);
  // 1: 采购入库 -> purchase
  // 2: 商品调拨 -> transfer
  // 5: 采购退库 -> purchase（退库单，走采购退库视图）
  // 6: 报损单   -> loss
  // 7: 领用出库 -> draw
  // 29: 物资进货单 -> purchase（同采购入库抽屉复用）
  // 31: 物资出库单 -> draw（同领用出库抽屉复用）
  const map: Record<string, WarehouseDrawerType> = {
    '1': 'purchase',
    '2': 'transfer',
    '5': 'purchase',
    '6': 'loss',
    '7': 'draw',
    '29': 'purchase',
    '34': 'inventory',
    '31': 'draw',
  };
  return map[v] ?? null;
}

const currentDrawerComponent = computed<Component | null>(() => {
  const t = current.value ? normalizeWarehouseDrawerType(current.value.type) : null;
  switch (t) {
    case 'purchase':
      return WarehousePurchaseDrawer;
    case 'draw':
      return WarehouseDrawDrawer;
    case 'inventory':
      return WarehouseInventoryDrawer;
    case 'loss':
      return WarehouseLossDrawer;
    case 'transfer':
      return WarehouseTransferDrawer;
    default:
      return null;
  }
});

const open = async (options: FinanceWarehouseDrawerOpenOptions) => {
  current.value = options;
  await nextTick();

  const t = normalizeWarehouseDrawerType(options.type);
  if (!t) {
    console.warn('[warehouse-adapter] 未支持的单据类型:', options.type, options);
    return;
  }

  const busNo = options.busNo;
  if (t === 'purchase') {
    const rawType = String(options.type);
    const isReturnOrder = rawType === '5';
    (drawerRef.value as PurchaseDrawerExpose | null)?.open(busNo, 'view', isReturnOrder);
  } else if (t === 'draw') {
    (drawerRef.value as CommonDrawerExpose | null)?.open(busNo, 'view');
  } else if (t === 'inventory') {
    (drawerRef.value as CommonDrawerExpose | null)?.open(busNo, 'view');
  } else if (t === 'loss') {
    (drawerRef.value as CommonDrawerExpose | null)?.open(busNo, 'view');
  } else if (t === 'transfer') {
    (drawerRef.value as CommonDrawerExpose | null)?.open(busNo, 'view');
  }
};

const close = () => {
  drawerRef.value?.close();
  current.value = null;
};

defineExpose({
  open,
  close,
});
</script>

<template>
  <component :is="currentDrawerComponent" v-if="currentDrawerComponent" ref="drawerRef">
    <template #footer="p">
      <slot
        name="footer"
        v-bind="{
          type: current?.type,
          busNo: p.busNo,
          state: p.state,
          close: p.close,
          row: current?.row,
        }"
      />
    </template>
  </component>
</template>
