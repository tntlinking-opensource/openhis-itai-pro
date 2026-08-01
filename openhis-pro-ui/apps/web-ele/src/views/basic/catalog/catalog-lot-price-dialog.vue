<script lang="ts" setup>
import type { CatalogLotPriceRow } from '#/api';

import { ref, watch } from 'vue';

import { ElDialog, ElMessage, ElTable, ElTableColumn } from 'element-plus';

import { listDeviceLotPricesApi, listMedicationLotPricesApi } from '#/api';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({ name: 'CatalogLotPriceDialog' });

interface Props {
  /** 药品定义 / 耗材定义主键 */
  itemId?: string;
  /** 目录类型：药品 or 耗材 */
  catalogType?: 'device' | 'medication';
  /** 弹窗标题附加品名 */
  itemName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  itemId: '',
  catalogType: 'medication',
  itemName: '',
});

const open = defineModel<boolean>({ default: false });

const loading = ref(false);
const rows = ref<CatalogLotPriceRow[]>([]);

/** 打开弹窗时按主键拉批号价 */
async function loadLotPrices() {
  const id = props.itemId?.trim();
  if (!id) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const response =
      props.catalogType === 'device'
        ? await listDeviceLotPricesApi({ id })
        : await listMedicationLotPricesApi({ id });
    const data = response.data;
    rows.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('查询批号价格失败:', error);
    ElMessage.error('查询批号价格失败，请稍后重试');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

/** 金额列展示 */
function formatPrice(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  return Number.isFinite(num) ? formatMoneyDisplay(num) : String(value);
}

watch(
  () => open.value,
  (visible) => {
    if (visible) {
      void loadLotPrices();
    } else {
      rows.value = [];
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="open"
    :title="itemName ? `价格 — ${itemName}` : '价格'"
    width="560px"
    destroy-on-close
    append-to-body
  >
    <ElTable
      v-loading="loading"
      :data="rows"
      border
      stripe
      max-height="420"
      empty-text="暂无批号价格"
    >
      <ElTableColumn prop="lotNumber" label="批号" min-width="140" show-overflow-tooltip />
      <ElTableColumn label="进价" width="120" align="right" header-align="right">
        <template #default="{ row }">
          {{ formatPrice(row.costPrice) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="售价" width="120" align="right" header-align="right">
        <template #default="{ row }">
          {{ formatPrice(row.salePrice) }}
        </template>
      </ElTableColumn>
    </ElTable>
  </ElDialog>
</template>
