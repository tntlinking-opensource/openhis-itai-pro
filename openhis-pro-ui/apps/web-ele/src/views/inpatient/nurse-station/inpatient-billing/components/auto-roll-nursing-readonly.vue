<script setup lang="ts">
import type {
  AutoRollNursingTableRow,
  AutoRollPostedChargeTableRow,
} from '../services/map-nurse-station-auto-roll';

import { computed } from 'vue';

import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'InpatientBillingAutoRollNursingReadonly',
});

const props = withDefaults(defineProps<Props>(), {
  billDate: '',
  loading: false,
  canOperateBilling: false,
  nursingRows: () => [],
  postedChargesByServiceRequestId: () => ({}),
  postedLoadingServiceRequestIds: () => ({}),
  expandedServiceRequestIds: () => [],
  cancellingChargeItemId: null,
});

const emit = defineEmits<{
  onQuery: [];
  'update:billDate': [value: string];
  'update:expandedServiceRequestIds': [value: string[]];
  expandChange: [row: AutoRollNursingTableRow, expandedRows: AutoRollNursingTableRow[]];
  cancelPostedCharge: [nursingRow: AutoRollNursingTableRow, chargeRow: AutoRollPostedChargeTableRow];
}>();

interface Props {
  billDate?: string;
  loading?: boolean;
  canOperateBilling?: boolean;
  nursingRows?: AutoRollNursingTableRow[];
  postedChargesByServiceRequestId?: Record<string, AutoRollPostedChargeTableRow[]>;
  postedLoadingServiceRequestIds?: Record<string, boolean>;
  expandedServiceRequestIds?: string[];
  cancellingChargeItemId?: string | null;
}

const billDateModel = computed({
  get: () => props.billDate,
  set: (value: string | null) => emit('update:billDate', value ?? ''),
});

const expandedKeysModel = computed({
  get: () => props.expandedServiceRequestIds,
  set: (value: string[]) => emit('update:expandedServiceRequestIds', value),
});

/** 空表提示：无护理医嘱时引导去校对（业务日只影响展开费用，不隐藏本表行）。 */
const nursingEmptyText = computed(
  () => '暂无护理级别记录，请先在「医嘱校对」维护护理级别医嘱',
);

/** 格式化护理费单价/金额展示。 */
function formatNursingMoney(value: number | string | undefined): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return `￥${formatMoneyDisplay(num)}`;
}

/** 解析护理行已滚费用。 */
function resolvePostedCharges(serviceRequestId: string): AutoRollPostedChargeTableRow[] {
  return props.postedChargesByServiceRequestId[serviceRequestId] ?? [];
}

/** 护理行展开 loading。 */
function isPostedChargesLoading(serviceRequestId: string): boolean {
  return props.postedLoadingServiceRequestIds[serviceRequestId] === true;
}

/** 展开变化透传给父级懒加载。 */
function handleExpandChange(row: AutoRollNursingTableRow, expandedRows: AutoRollNursingTableRow[]) {
  expandedKeysModel.value = expandedRows.map((item) => item.id);
  emit('expandChange', row, expandedRows);
}
</script>

<template>
  <section class="auto-roll-nursing">
    <header class="auto-roll-nursing__header">
      <span class="auto-roll-nursing__title">护理级别（只读）</span>
      <div class="auto-roll-nursing__query">
        <span class="auto-roll-nursing__label">业务日</span>
        <ElDatePicker
          v-model="billDateModel"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          placeholder="全部住院期间"
          clearable
          style="width: 148px"
        />
        <ElButton type="primary" :loading="loading" @click="emit('onQuery')">查询</ElButton>
      </div>
    </header>

    <div class="auto-roll-nursing__body">
      <ElTable
        v-loading="loading"
        :data="nursingRows"
        border
        stripe
        size="small"
        row-key="id"
        :expand-row-keys="expandedKeysModel"
        :empty-text="nursingEmptyText"
        @expand-change="handleExpandChange"
      >
        <ElTableColumn type="expand" width="48" align="center">
          <template #default="{ row }">
            <div class="auto-roll-nursing__posted">
              <ElTable
                v-loading="isPostedChargesLoading(row.serviceRequestId)"
                :data="resolvePostedCharges(row.serviceRequestId)"
                border
                size="small"
                empty-text="暂无已滚费用记录"
              >
                <ElTableColumn
                  prop="occurrenceTime"
                  label="业务时间"
                  min-width="160"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="quantityText" label="数量" width="88" align="center" />
                <ElTableColumn label="单价" width="100" align="right">
                  <template #default="{ row: chargeRow }">
                    {{ formatNursingMoney(chargeRow.unitPrice) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="金额" width="100" align="right">
                  <template #default="{ row: chargeRow }">
                    {{ formatNursingMoney(chargeRow.totalPrice) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="statusText"
                  label="状态"
                  width="96"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row: chargeRow }">
                    <ElButton
                      link
                      type="danger"
                      :disabled="!canOperateBilling || !chargeRow.cancelable"
                      :loading="cancellingChargeItemId === chargeRow.chargeItemId"
                      @click="emit('cancelPostedCharge', row, chargeRow)"
                    >
                      取消
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="row.isActive ? 'success' : 'info'" effect="light" size="small">
              {{ row.statusText }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="activityName" label="护理级别" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="quantityText" label="数量" width="88" align="center" />
        <ElTableColumn label="价格" width="100" align="right">
          <template #default="{ row }">
            {{ formatNursingMoney(row.unitPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="checkTime"
          label="校对时间"
          min-width="160"
          align="center"
          show-overflow-tooltip
        />
      </ElTable>
      <p class="auto-roll-nursing__hint">
        护理费由医嘱校对驱动，每日自动滚费；不可在此配置。展开可查看已滚费用，待结算可取消。业务日只过滤展开后的费用，不隐藏护理级别行。
      </p>
    </div>
  </section>
</template>

<style scoped>
.auto-roll-nursing {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
}

.auto-roll-nursing__header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
}

.auto-roll-nursing__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.auto-roll-nursing__query {
  align-items: center;
  display: flex;
  gap: 8px;
}

.auto-roll-nursing__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.auto-roll-nursing__body {
  min-height: 72px;
}

.auto-roll-nursing__posted {
  padding: 8px 12px 12px 36px;
}

.auto-roll-nursing__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 6px 0 0;
}
</style>
