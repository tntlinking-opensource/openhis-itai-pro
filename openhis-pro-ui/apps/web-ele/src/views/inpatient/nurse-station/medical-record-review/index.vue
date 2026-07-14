<script setup lang="ts">
import type { CaseRecordDetailDto } from '#/api/mrms/types';
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus';

import { getCaseRecordByEncounterApi } from '#/api/mrms';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_TAG,
} from '#/views/mrms/case-record/case-record.constants';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationMedicalRecordReviewModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const router = useRouter();
const caseRecord = ref<CaseRecordDetailDto | null>(null);
const loadError = ref('');

const loadRequest = useRequest(getCaseRecordByEncounterApi, {
  manual: true,
  showErrorMessage: false,
});

const canOperate = computed(() => props.patients.length === 1);

const selectedPatient = computed(() => (canOperate.value ? props.patients[0] : undefined));

const selectedEncounterId = computed(() => preserveApiIdForRequest(selectedPatient.value?.id));

const caseRecordId = computed(() => preserveApiIdForRequest(caseRecord.value?.caseRecordId));

const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return null;
  }
  if (!canOperate.value) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后调阅病历）`,
      name: '请选择单一患者',
    });
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(patient);
});

const statusLabel = computed(() => {
  const code = caseRecord.value?.statusCode?.trim();
  if (!code) {
    return '—';
  }
  return CASE_RECORD_STATUS_LABEL[code] ?? code;
});

const statusTagType = computed(() => {
  const code = caseRecord.value?.statusCode?.trim() ?? '';
  return CASE_RECORD_STATUS_TAG[code] ?? 'info';
});

async function loadCaseRecord() {
  caseRecord.value = null;
  loadError.value = '';
  const encounterId = selectedEncounterId.value;
  if (!encounterId) {
    return;
  }
  try {
    caseRecord.value = await loadRequest.run(encounterId);
  } catch (error) {
    caseRecord.value = null;
    loadError.value =
      error instanceof Error ? error.message : '未找到该就诊对应的病案主档，可能尚未建档';
  }
}

function openHomepageEditor() {
  const id = caseRecordId.value;
  if (!id) {
    ElMessage.warning('暂无病案主档，无法打开首页');
    return;
  }
  void router.push({
    name: 'MrmsCaseRecordHomepage',
    params: { caseRecordId: id },
  });
}

watch(
  selectedEncounterId,
  (encounterId) => {
    if (!encounterId) {
      caseRecord.value = null;
      loadError.value = '';
      return;
    }
    void loadCaseRecord();
  },
  { immediate: true },
);
</script>

<template>
  <div class="medical-record-review-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />

    <section
      v-if="!canOperate"
      class="flex min-h-0 flex-1 items-center justify-center rounded-md bg-white"
    >
      <ElEmpty description="请在左侧患者列表单选一位患者" />
    </section>

    <section
      v-else
      v-loading="loadRequest.loading.value"
      class="review-panel flex min-h-0 flex-1 flex-col overflow-hidden rounded-md border border-solid border-[var(--el-border-color-lighter)] bg-white"
    >
      <header class="review-panel__toolbar">
        <div class="review-panel__title-wrap">
          <h3 class="review-panel__title">病案调阅</h3>
          <ElTag v-if="caseRecord" size="small" :type="statusTagType">{{ statusLabel }}</ElTag>
        </div>
        <div class="review-panel__actions">
          <ElButton @click="openHomepageEditor">打开病案首页</ElButton>
        </div>
      </header>

      <div v-if="loadError" class="review-panel__body">
        <ElEmpty :description="loadError" />
      </div>

      <div v-else-if="caseRecord" class="review-panel__body">
        <dl class="review-summary-grid">
          <div class="review-summary-item">
            <dt>病案号</dt>
            <dd>{{ caseRecord.medcasno || '—' }}</dd>
          </div>
          <div class="review-summary-item">
            <dt>住院号</dt>
            <dd>{{ selectedPatient?.encounterBusNo || '—' }}</dd>
          </div>
          <div class="review-summary-item">
            <dt>患者姓名</dt>
            <dd>{{ selectedPatient?.name || '—' }}</dd>
          </div>
          <div class="review-summary-item">
            <dt>科室/病区</dt>
            <dd>{{ selectedPatient?.wardName || selectedPatient?.regionName || '—' }}</dd>
          </div>
          <div class="review-summary-item">
            <dt>入院时间</dt>
            <dd>—</dd>
          </div>
          <div class="review-summary-item">
            <dt>出院时间</dt>
            <dd>—</dd>
          </div>
          <div class="review-summary-item review-summary-item--span2">
            <dt>主诊断</dt>
            <dd>{{ selectedPatient?.diagnosis || '—' }}</dd>
          </div>
        </dl>
        <p class="review-panel__hint">完整编辑请点「打开病案首页」进入病案首页页面。</p>
      </div>

      <div v-else class="review-panel__body">
        <ElEmpty description="正在加载病案信息…" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.medical-record-review-page {
  background: #f3f5f8;
  gap: 6px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 0 2px 6px;
}

.medical-record-review-page :deep(.inpatient-patient-info-header) {
  margin-bottom: 0;
}

.review-panel {
  min-height: 0;
}

.review-panel__toolbar {
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  padding: 12px 16px;
}

.review-panel__title-wrap {
  align-items: center;
  display: flex;
  gap: 8px;
}

.review-panel__title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.review-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-panel__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.review-summary-grid {
  display: grid;
  gap: 12px 24px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 0 16px;
}

.review-summary-item {
  min-width: 0;
}

.review-summary-item--span2 {
  grid-column: span 2;
}

.review-summary-item dt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}

.review-summary-item dd {
  color: var(--el-text-color-primary);
  font-size: 14px;
  margin: 0;
  word-break: break-word;
}

.review-panel__hint {
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  padding-top: 12px;
}

@media (max-width: 1280px) {
  .review-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .review-summary-item--span2 {
    grid-column: span 2;
  }
}
</style>
