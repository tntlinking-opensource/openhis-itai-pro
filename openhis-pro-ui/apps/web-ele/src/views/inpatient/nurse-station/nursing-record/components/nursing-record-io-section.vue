<script setup lang="ts">
import type { NursingRecordViewRow } from './nursing-record.types';

interface Props {
  row: NursingRecordViewRow;
}

const props = defineProps<Props>();

const oxygenModeOptions = ['无', '鼻导管', '面罩', '高流量', '呼吸机'].map((item) => ({
  label: item,
  value: item,
}));

/** 将记录字段转成展示文案，空值统一显示占位。 */
function toDisplayValue(value?: string) {
  return value && value.length > 0 ? value : '-';
}
</script>

<template>
  <div class="timeline-card__section">
    <div v-if="props.row.isEditing" class="section-grid section-grid--io">
      <label class="field-item">
        <span class="field-item__label">吸氧方式</span>
        <ElSelect v-model="props.row.oxygenMode" :options="oxygenModeOptions" />
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">吸氧</span>
        <ElInput v-model="props.row.oxygenRate">
          <template #suffix>L/分</template>
        </ElInput>
      </label>
      <label class="field-item">
        <span class="field-item__label">流量</span>
        <ElInput v-model="props.row.oxygenFlow" />
      </label>
      <label class="field-item">
        <span class="field-item__label">入量名称</span>
        <ElInput v-model="props.row.intakeName" />
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">入量</span>
        <ElInput v-model="props.row.intakeAmount">
          <template #suffix>ml</template>
        </ElInput>
      </label>
      <label class="field-item">
        <span class="field-item__label">途径</span>
        <ElInput v-model="props.row.intakeRoute" />
      </label>
      <label class="field-item">
        <span class="field-item__label">出量名称</span>
        <ElInput v-model="props.row.outputName" />
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">出量</span>
        <ElInput v-model="props.row.outputAmount">
          <template #suffix>ml</template>
        </ElInput>
      </label>
    </div>
    <div v-else class="display-grid display-grid--io">
      <span class="display-item">吸氧方式：{{ toDisplayValue(props.row.oxygenMode) }}</span>
      <span class="display-item">吸氧：{{ toDisplayValue(props.row.oxygenRate) }} L/分</span>
      <span class="display-item">流量：{{ toDisplayValue(props.row.oxygenFlow) }}</span>
      <span class="display-item">
        入量：{{ toDisplayValue(props.row.intakeName) }}
        {{ toDisplayValue(props.row.intakeAmount) }} ml
      </span>
      <span class="display-item">途径：{{ toDisplayValue(props.row.intakeRoute) }}</span>
      <span class="display-item">
        出量：{{ toDisplayValue(props.row.outputName) }}
        {{ toDisplayValue(props.row.outputAmount) }} ml
      </span>
    </div>
  </div>
</template>

<style scoped>
.timeline-card__section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-grid {
  display: grid;
  gap: 8px 10px;
}

.section-grid--io {
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
}

.display-grid {
  display: grid;
  gap: 3px 8px;
}

.display-grid--io {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.display-item {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 20px;
}

.field-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.field-item--numeric {
  min-width: 0;
}

.field-item--numeric :deep(.el-input) {
  width: 72px;
}

.field-item__label {
  color: var(--el-text-color-regular);
  flex: 0 0 62px;
  font-size: 14px;
  line-height: 1.2;
}

.field-item :deep(.el-input),
.field-item :deep(.el-select) {
  flex: 1;
  min-width: 0;
}
</style>
