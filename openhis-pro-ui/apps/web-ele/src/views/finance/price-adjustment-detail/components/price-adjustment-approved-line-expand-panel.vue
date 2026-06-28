<script setup lang="ts">
import type {
  GetPriceAdjustmentApprovedLineResponse,
  PriceAdjustmentDetailDto,
} from '#/api/finance/price-adjustment-detail';

import { computed, onMounted, ref } from 'vue';

import { getPriceAdjustmentApprovedLineApi } from '#/api/finance/price-adjustment-detail';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { formatAdjustmentAmount } from '../utils/format-amount';
import PriceAdjustmentImpactAmount from './price-adjustment-impact-amount.vue';

defineOptions({ name: 'PriceAdjustmentApprovedLineExpandPanel' });

interface Props {
  lineId?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  lineId: '',
});

const detailCache = new Map<string, GetPriceAdjustmentApprovedLineResponse>();

const detailData = ref<GetPriceAdjustmentApprovedLineResponse | null>(null);

const { run: runGetDetail, loading } = useRequest(getPriceAdjustmentApprovedLineApi, {
  manual: true,
  onSuccess: (data) => {
    detailData.value = data ?? null;
    const id = preserveApiIdForRequest(props.lineId);
    if (id && data) {
      detailCache.set(id, data);
    }
  },
});

const line = computed(() => detailData.value?.line);
const isLogistics = computed(() => line.value?.adjustmentStyle === 'logistics');
const detailRows = computed<PriceAdjustmentDetailDto[]>(() => line.value?.details ?? []);
const hasBatchRows = computed(() => detailRows.value.length > 0);

// 展开行首次渲染时拉取详情，命中缓存则直接复用。
async function loadDetail() {
  const id = preserveApiIdForRequest(props.lineId);
  if (!id) {
    detailData.value = null;
    return;
  }
  const cached = detailCache.get(id);
  if (cached) {
    detailData.value = cached;
    return;
  }
  await runGetDetail({ id });
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <div v-loading="loading" class="expand-panel">
    <template v-if="line">
      <div v-if="hasBatchRows" class="expand-panel__content">
        <div class="expand-panel__summary">
          <span v-if="isLogistics" class="expand-panel__summary-item">
            <span class="expand-panel__summary-label">目标进价</span>
            <span class="expand-panel__summary-value tabular-nums">
              {{ formatAdjustmentAmount(line.newPurchaseAmount) }}
            </span>
          </span>
          <span class="expand-panel__summary-item">
            <span class="expand-panel__summary-label">目标售价</span>
            <span class="expand-panel__summary-value tabular-nums">
              {{ formatAdjustmentAmount(line.newSellingAmount) }}
            </span>
          </span>
          <span class="expand-panel__summary-item">
            <span class="expand-panel__summary-label">制单时间</span>
            <span class="expand-panel__summary-value">{{ line.adjustmentTime || '—' }}</span>
          </span>
        </div>

        <table class="expand-panel__table">
          <thead>
            <tr>
              <th class="col-lot">批号</th>
              <th class="col-qty">数量</th>
              <th class="col-amount">调前进价</th>
              <th class="col-amount">调前售价</th>
              <th class="col-impact">进价盈亏</th>
              <th class="col-impact">售价盈亏</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in detailRows" :key="`${row.lotNumber ?? ''}-${index}`">
              <td class="col-lot">{{ row.lotNumber || '—' }}</td>
              <td class="col-qty tabular-nums">
                {{ formatAdjustmentAmount(row.quantity, '') || row.quantity || '—' }}
              </td>
              <td class="col-amount tabular-nums">
                {{ formatAdjustmentAmount(row.oldPurchaseAmount) }}
              </td>
              <td class="col-amount tabular-nums">
                {{ formatAdjustmentAmount(row.oldSellingAmount) }}
              </td>
              <td class="col-impact">
                <PriceAdjustmentImpactAmount :value="row.purchaseImpact" align="right" />
              </td>
              <td class="col-impact">
                <PriceAdjustmentImpactAmount :value="row.sellingImpact" align="right" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="!loading" class="expand-panel__clinical">
        <span class="expand-panel__clinical-label">调价对比</span>
        <span class="expand-panel__clinical-item">
          <span class="expand-panel__summary-label">调前售价</span>
          <span class="expand-panel__summary-value tabular-nums">
            {{ formatAdjustmentAmount(line.details?.[0]?.oldSellingAmount) }}
          </span>
        </span>
        <span class="expand-panel__clinical-arrow">→</span>
        <span class="expand-panel__clinical-item">
          <span class="expand-panel__summary-label">目标售价</span>
          <span class="expand-panel__summary-value tabular-nums">
            {{ formatAdjustmentAmount(line.newSellingAmount) }}
          </span>
        </span>
        <span class="expand-panel__clinical-item">
          <span class="expand-panel__summary-label">盈亏</span>
          <PriceAdjustmentImpactAmount :value="detailData?.totalImpact" align="left" />
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.expand-panel {
  padding: 10px 16px 12px 108px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.expand-panel__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.expand-panel__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 13px;
  line-height: 20px;
}

.expand-panel__summary-item {
  display: inline-flex;
  gap: 8px;
  align-items: baseline;
}

.expand-panel__summary-label {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.expand-panel__summary-value {
  color: var(--el-text-color-primary);
}

.expand-panel__table {
  width: 100%;
  font-size: 13px;
  line-height: 20px;
  border-collapse: collapse;
  table-layout: fixed;

  th,
  td {
    padding: 8px 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  th {
    font-weight: 500;
    color: var(--el-text-color-regular);
    text-align: center;
    background: var(--el-fill-color-light);
  }

  tbody tr:last-child td {
    border-bottom: none;
  }

  tbody tr:nth-child(even) td {
    background: var(--el-fill-color-lighter);
  }
}

.col-lot {
  width: 18%;
  text-align: center;
}

.col-qty {
  width: 10%;
  text-align: center;
}

.col-amount {
  width: 16%;
  text-align: right;
}

.col-impact {
  width: 16%;
  text-align: right;
}

.expand-panel__clinical {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  font-size: 13px;
  line-height: 20px;
}

.expand-panel__clinical-label {
  margin-right: 4px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.expand-panel__clinical-item {
  display: inline-flex;
  gap: 8px;
  align-items: baseline;
}

.expand-panel__clinical-arrow {
  color: var(--el-text-color-secondary);
}
</style>
