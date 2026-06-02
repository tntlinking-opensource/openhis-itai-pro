<script setup lang="ts">
import type {
  AdviceDrugItem,
  AdviceDrugPatchPayload,
} from '#/views/outpatient/doctor-station/advice';

import { ElInputNumber, ElOption, ElSelect } from 'element-plus';

defineOptions({ name: 'TcmAdviceDetailForm' });

defineProps<{
  item: AdviceDrugItem;
}>();

const emit = defineEmits<{
  patch: [payload: AdviceDrugPatchPayload];
}>();

/** 回写一行字段（仅中药编辑区使用）。 */
function patch<K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) {
  emit('patch', { key, value });
}

const pharmacyOptions = ['门诊药房', '住院西药房', '草药房'];

const specialDecoctionOptions = [
  { label: '无', value: '' },
  { label: '先煎', value: '先煎' },
  { label: '后下', value: '后下' },
  { label: '包煎', value: '包煎' },
  { label: '另煎', value: '另煎' },
  { label: '烊化', value: '烊化' },
  { label: '冲服', value: '冲服' },
];
</script>

<template>
  <div class="tcm-detail-form">
    <div class="tcm-detail-grid">
      <div class="field-item">
        <span class="meta-label">药房</span>
        <ElSelect
          :model-value="item.pharmacy"
          class="field-control"
          placeholder="请选择药房"
          @update:model-value="
            (value: string | undefined) => patch('pharmacy', String(value ?? ''))
          "
        >
          <ElOption v-for="opt in pharmacyOptions" :key="opt" :label="opt" :value="opt" />
        </ElSelect>
      </div>
      <div class="field-item">
        <span class="meta-label">单次剂量</span>
        <div class="dose-wrap">
          <ElInputNumber
            :model-value="item.singleDose"
            class="dose-input"
            :min="0"
            :controls="false"
            @update:model-value="
              (value: number | undefined) => patch('singleDose', Number(value ?? 0))
            "
          />
          <span class="dose-unit">g</span>
        </div>
      </div>
      <div class="field-item">
        <span class="meta-label">特殊煎法</span>
        <ElSelect
          :model-value="item.tcmSpecialDecoction ?? ''"
          class="field-control"
          placeholder="请选择"
          clearable
          @update:model-value="
            (value: string | undefined) => patch('tcmSpecialDecoction', String(value ?? ''))
          "
        >
          <ElOption
            v-for="opt in specialDecoctionOptions"
            :key="opt.value || 'none'"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tcm-detail-form {
  width: fit-content;
  max-width: 100%;
}

.tcm-detail-grid {
  display: grid;
  grid-template-columns: 220px 180px 220px;
  gap: 10px 16px;
  align-items: end;
  width: fit-content;
  max-width: 100%;
}

.field-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  min-width: 0;
}

@media (max-width: 900px) {
  .tcm-detail-grid {
    grid-template-columns: 1fr;
  }
}

.meta-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 32px;
  text-align: right;
  padding-right: 8px;
  white-space: nowrap;
}

.meta-label::after {
  content: ':';
  margin-left: 2px;
}

.field-control {
  width: 100%;
  min-width: 0;
}

.dose-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.dose-input {
  width: 104px;
}

.dose-unit {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}
</style>
