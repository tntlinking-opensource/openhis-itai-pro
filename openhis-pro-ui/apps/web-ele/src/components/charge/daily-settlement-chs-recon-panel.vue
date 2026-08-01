<script setup lang="ts">
import type { ChargeType } from '#/api/core/common';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton, ElCard } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import DailyLedgerList from '#/chs/reconciliation/components/daily-ledger-list.vue';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'DailySettlementChsReconPanel' });

const props = withDefaults(
  defineProps<{
    /** 对账日 yyyy-MM-dd，跟随日结查询窗起点 */
    reconDate?: string;
    /** 清算业务类型：门诊 11 / 住院 21 */
    clrType?: string;
  }>(),
  {
    reconDate: undefined,
    clrType: '11',
  },
);

const router = useRouter();
const contractBusNo = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
});

const resolvedReconDate = computed(() => props.reconDate?.trim() || undefined);

function openFullReconciliation() {
  void router.push({ name: 'ChsReconciliation' });
}

onMounted(() => {
  void loadChargeTypes();
});
</script>

<template>
  <ElCard shadow="never" class="ds-chs-recon-card h-full min-h-0 min-w-0 overflow-hidden">
    <div class="flex h-full min-h-0 min-w-0 flex-col gap-2 overflow-hidden">
      <div class="ds-chs-recon-card__header">
        <div class="ds-chs-recon-card__title">医保对账</div>
        <ElButton link type="primary" @click="openFullReconciliation">完整对账页</ElButton>
      </div>
      <div class="ds-chs-recon-card__body min-h-0 min-w-0 flex-1">
        <DailyLedgerList
          v-model:contract-bus-no="contractBusNo"
          compact
          :charge-type-options="chargeTypeOptions"
          :loading-charge-types="loadingChargeTypes"
          :initial-recon-date="resolvedReconDate"
          :default-clr-type="clrType"
        />
      </div>
    </div>
  </ElCard>
</template>

<style scoped>
.ds-chs-recon-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
}

.ds-chs-recon-card__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ds-chs-recon-card__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ds-chs-recon-card__body {
  min-width: 0;
  min-height: 0;
  padding-top: 2px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
