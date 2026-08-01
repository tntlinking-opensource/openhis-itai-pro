<script setup lang="ts">
import type { NursingRecordViewRow } from './nursing-record.types';

defineOptions({
  name: 'NursingRecordBodySection',
});

interface Props {
  row: NursingRecordViewRow;
}

const props = defineProps<Props>();

const oxygenModeOptions = ['无', '鼻导管', '面罩', '高流量', '呼吸机'].map((item) => ({
  label: item,
  value: item,
}));
const tubeCareOptions = ['无', '胃管', '导尿管', '静脉通路', '引流管', '气管插管'].map((item) => ({
  label: item,
  value: item,
}));
const skinStatusOptions = ['完整', '潮红', '压红', '破损', '渗液', '已换药'].map((item) => ({
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
    <div v-if="props.row.isEditing" class="section-grid section-grid--vitals">
      <label class="field-item field-item--numeric">
        <span class="field-item__label">体温</span>
        <ElInput v-model="props.row.temperature">
          <template #suffix>℃</template>
        </ElInput>
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">心率</span>
        <ElInput v-model="props.row.heartRate">
          <template #suffix>次/分</template>
        </ElInput>
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">脉率</span>
        <ElInput v-model="props.row.pulse">
          <template #suffix>次/分</template>
        </ElInput>
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">呼吸</span>
        <ElInput v-model="props.row.respiratory">
          <template #suffix>次/分</template>
        </ElInput>
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">血压</span>
        <ElInput v-model="props.row.bloodPressure">
          <template #suffix>mmHg</template>
        </ElInput>
      </label>
      <label class="field-item field-item--numeric">
        <span class="field-item__label">血氧饱和度</span>
        <ElInput v-model="props.row.oxygenSaturation">
          <template #suffix>%</template>
        </ElInput>
      </label>
    </div>
    <div v-else class="display-grid display-grid--vitals">
      <span class="display-item">体温：{{ toDisplayValue(props.row.temperature) }} ℃</span>
      <span class="display-item">心率：{{ toDisplayValue(props.row.heartRate) }} 次/分</span>
      <span class="display-item">脉率：{{ toDisplayValue(props.row.pulse) }} 次/分</span>
      <span class="display-item">呼吸：{{ toDisplayValue(props.row.respiratory) }} 次/分</span>
      <span class="display-item">血压：{{ toDisplayValue(props.row.bloodPressure) }} mmHg</span>
      <span class="display-item">血氧：{{ toDisplayValue(props.row.oxygenSaturation) }} %</span>
    </div>
  </div>

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

  <div class="timeline-card__section">
    <div v-if="props.row.isEditing" class="section-grid section-grid--care">
      <label class="field-item">
        <span class="field-item__label">皮肤情况</span>
        <ElSelect v-model="props.row.skinStatus" :options="skinStatusOptions" />
      </label>
      <label class="field-item">
        <span class="field-item__label">管路护理</span>
        <ElSelect v-model="props.row.tubeCare" :options="tubeCareOptions" />
      </label>
    </div>
    <template v-if="props.row.isEditing">
      <label class="field-item field-item--textarea">
        <span class="field-item__label">病情措施</span>
        <ElInput v-model="props.row.conditionMeasure" type="textarea" :rows="2" resize="none" />
      </label>
    </template>
    <div v-else class="display-grid display-grid--care">
      <span class="display-item">皮肤情况：{{ toDisplayValue(props.row.skinStatus) }}</span>
      <span class="display-item">管路护理：{{ toDisplayValue(props.row.tubeCare) }}</span>
      <span class="display-item display-item--wide">
        病情措施：{{ toDisplayValue(props.row.conditionMeasure) }}
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

.section-grid--vitals,
.section-grid--io {
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
}

.section-grid--care {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.display-grid {
  display: grid;
  gap: 3px 8px;
}

.display-grid--vitals,
.display-grid--io {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.display-grid--care {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.display-item {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 20px;
}

.display-item--wide {
  grid-column: 1 / -1;
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

.field-item--textarea {
  align-items: flex-start;
  margin-top: 2px;
}
</style>
