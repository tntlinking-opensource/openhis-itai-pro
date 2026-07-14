<script setup lang="ts">
import type { NurseStationAutoRollNursingReadonlyDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed } from 'vue';

import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { hasAutoRollNursingReadonly } from '../services/map-nurse-station-auto-roll';

defineOptions({
  name: 'InpatientBillingAutoRollNursingReadonly',
});

const props = withDefaults(defineProps<Props>(), {
  billDate: '',
  loading: false,
  nursing: null,
});

const emit = defineEmits<{
  onQuery: [];
  'update:billDate': [value: string];
}>();

interface Props {
  billDate?: string;
  loading?: boolean;
  nursing?: NurseStationAutoRollNursingReadonlyDto | null;
}

const billDateModel = computed({
  get: () => props.billDate,
  set: (value: string) => emit('update:billDate', value),
});

/** 护理只读区是否有有效医嘱解析结果。 */
const hasNursing = computed(() => hasAutoRollNursingReadonly(props.nursing));

/** 格式化护理费单价展示。 */
function formatNursingUnitPrice(value: number | string | undefined): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return `￥${formatMoneyDisplay(num)}`;
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
          placeholder="选择业务日"
          clearable
          style="width: 148px"
        />
        <ElButton type="primary" :loading="loading" @click="emit('onQuery')">查询</ElButton>
      </div>
    </header>

    <div v-loading="loading" class="auto-roll-nursing__body">
      <template v-if="hasNursing && nursing">
        <div class="auto-roll-nursing__grid">
          <div class="auto-roll-nursing__item">
            <span class="auto-roll-nursing__item-label">护理级别</span>
            <span class="auto-roll-nursing__item-value">{{ nursing.activityName ?? '—' }}</span>
          </div>
          <div class="auto-roll-nursing__item">
            <span class="auto-roll-nursing__item-label">价格</span>
            <span class="auto-roll-nursing__item-value">
              {{ formatNursingUnitPrice(nursing.unitPrice) }}
            </span>
          </div>
          <div class="auto-roll-nursing__item">
            <span class="auto-roll-nursing__item-label">校对时间</span>
            <span class="auto-roll-nursing__item-value">{{ nursing.checkTime ?? '—' }}</span>
          </div>
          <div class="auto-roll-nursing__item">
            <span class="auto-roll-nursing__item-label">医嘱状态</span>
            <span class="auto-roll-nursing__item-value">
              <ElTag effect="light" size="small">{{ nursing.statusDictText ?? '—' }}</ElTag>
            </span>
          </div>
        </div>
        <p class="auto-roll-nursing__hint">
          护理费由医嘱校对驱动，每日 00:01 自动滚费；不可在此 Tab 配置。
        </p>
      </template>
      <ElEmpty
        v-else
        description="暂无有效护理级别，请先在「医嘱校对」维护护理级别医嘱"
        :image-size="56"
      />
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

.auto-roll-nursing__grid {
  display: grid;
  gap: 8px 16px;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
}

.auto-roll-nursing__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.auto-roll-nursing__item--wide {
  grid-column: span 2;
}

.auto-roll-nursing__item-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.auto-roll-nursing__item-value {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.auto-roll-nursing__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 6px 0 0;
}
</style>
