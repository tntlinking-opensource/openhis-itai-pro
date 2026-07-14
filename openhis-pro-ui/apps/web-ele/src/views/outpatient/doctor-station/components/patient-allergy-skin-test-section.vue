<script setup lang="ts">
import type { PatientSkinTestHistorySummary } from '../services/patient-skin-test-history.types';

import { ref, watch } from 'vue';

import { WarningFilled } from '@element-plus/icons-vue';
import { ElDialog, ElIcon } from 'element-plus';

import { fetchPatientSkinTestHistorySummary } from '../services/patient-skin-test-history.service';

defineOptions({ name: 'PatientAllergySkinTestSection' });

interface Props {
  patientId?: string;
  encounterId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  patientId: '',
  encounterId: '',
});

const loading = ref(false);
const dialogVisible = ref(false);
const summary = ref<PatientSkinTestHistorySummary>({
  records: [],
  positiveRecords: [],
  positiveMedicationNames: [],
  hasPositive: false,
});

/** 拉取患者历史皮试汇总 */
async function loadSkinTestHistory() {
  const patientId = String(props.patientId ?? '').trim();
  const encounterId = String(props.encounterId ?? '').trim();
  if (!patientId || !encounterId) {
    summary.value = {
      records: [],
      positiveRecords: [],
      positiveMedicationNames: [],
      hasPositive: false,
    };
    dialogVisible.value = false;
    return;
  }
  loading.value = true;
  try {
    summary.value = await fetchPatientSkinTestHistorySummary(patientId, encounterId);
    if (!summary.value.hasPositive) {
      dialogVisible.value = false;
    }
  } finally {
    loading.value = false;
  }
}

/** 点击感叹号：弹窗展示阳性过敏药品 */
function handleOpenDialog() {
  if (loading.value || !summary.value.hasPositive) {
    return;
  }
  dialogVisible.value = true;
}

watch(
  () => [props.patientId, props.encounterId] as const,
  () => {
    dialogVisible.value = false;
    void loadSkinTestHistory();
  },
  { immediate: true },
);
</script>

<template>
  <div class="patient-allergy-skin-test">
    <div class="patient-item__row patient-item__row--last">
      <span class="patient-item__label">过敏史</span>
      <span class="patient-item__value patient-allergy-skin-test__value">
        <template v-if="loading">加载中…</template>
        <button
          v-else-if="summary.hasPositive"
          type="button"
          class="patient-allergy-skin-test__warn-btn"
          aria-label="查看皮试阳性过敏药品"
          @click.stop="handleOpenDialog"
        >
          <ElIcon class="patient-allergy-skin-test__warn-icon">
            <WarningFilled />
          </ElIcon>
        </button>
        <template v-else>-</template>
      </span>
    </div>

    <ElDialog
      v-model="dialogVisible"
      title="过敏药品"
      width="420px"
      append-to-body
      destroy-on-close
    >
      <div class="patient-allergy-skin-test__dialog-list">
        <div
          v-for="record in summary.positiveRecords"
          :key="record.key"
          class="patient-allergy-skin-test__dialog-item"
        >
          <div class="patient-allergy-skin-test__dialog-main">
            <span class="patient-allergy-skin-test__drug">{{ record.medicationName }}</span>
            <span class="patient-allergy-skin-test__result">{{ record.clinicalStatusText }}</span>
          </div>
          <div v-if="record.recordedAt" class="patient-allergy-skin-test__record-meta">
            {{ record.recordedAt }}
          </div>
        </div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.patient-item__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: none;
}

.patient-item__row--last {
  padding-bottom: 4px;
}

.patient-item__label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.patient-item__value {
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: 12px;
  line-height: 18px;
  text-align: right;
}

.patient-allergy-skin-test__value {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
}

.patient-allergy-skin-test__warn-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  line-height: 1;
}

.patient-allergy-skin-test__warn-btn:hover .patient-allergy-skin-test__warn-icon {
  opacity: 0.85;
}

.patient-allergy-skin-test__warn-icon {
  color: var(--el-color-danger);
  font-size: 16px;
}

.patient-allergy-skin-test__dialog-list {
  max-height: 360px;
  overflow-y: auto;
}

.patient-allergy-skin-test__dialog-item + .patient-allergy-skin-test__dialog-item {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-extra-light);
}

.patient-allergy-skin-test__dialog-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.patient-allergy-skin-test__drug {
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 20px;
  word-break: break-all;
}

.patient-allergy-skin-test__result {
  flex-shrink: 0;
  color: var(--el-color-danger);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.patient-allergy-skin-test__record-meta {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
