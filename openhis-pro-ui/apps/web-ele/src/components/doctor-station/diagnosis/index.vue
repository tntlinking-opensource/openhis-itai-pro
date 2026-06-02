<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

import { computed, inject, onActivated, onMounted, ref, watch } from 'vue';

import { ElEmpty, ElMessage } from 'element-plus';

import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station/clinical-reception-context';

import DiagnosisPickerDialog from './components/diagnosis-picker-dialog.vue';
import DiagnosisQuickTree from './components/diagnosis-quick-tree.vue';
import DiagnosisSavedTable from './components/diagnosis-saved-table.vue';
import {
  deleteEncounterDiagnosisRow,
  fetchEncounterDiagnosisTabData,
  saveEncounterDiagnosisRows,
  type DoctorStationDiagnosisScope,
} from './services/encounter-diagnosis.service';

defineOptions({ name: 'DoctorStationDiagnosisList' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** 门诊默认；住院医生站传 inpatient */
    stationScope?: DoctorStationDiagnosisScope;
  }>(),
  {
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
  },
);

const diagnosisRows = ref<EncounterDiagnosisRowDto[]>([]);
const businessClass = ref<CommonDiagnosesResponse | null>(null);
const listLoading = ref(false);
const quickSaveLoading = ref(false);
const pickerOpen = ref(false);

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

/** 激活 / 工具栏刷新 / 保存后：统一入口 */
async function reload() {
  if (!contextReady.value) {
    diagnosisRows.value = [];
    businessClass.value = null;
    return;
  }
  listLoading.value = true;
  try {
    const data = await fetchEncounterDiagnosisTabData(
      {
        encounterId: props.encounterId.trim(),
        patientId: props.patientId.trim(),
      },
      props.stationScope,
    );
    if (!data) {
      diagnosisRows.value = [];
      businessClass.value = null;
      return;
    }
    diagnosisRows.value = data.diagnosisList;
    businessClass.value = data.businessClass;
  } finally {
    listLoading.value = false;
  }
}

defineExpose({ reload });

onMounted(() => {
  void reload();
});

onActivated(() => {
  void reload();
});

watch(
  () => [props.encounterId, props.patientId],
  () => {
    void reload();
  },
);

/** 合并后的全量列表提交保存并刷新 */
async function persistMergedRows(rows: EncounterDiagnosisRowDto[]) {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (rows.length === 0) {
    ElMessage.warning('诊断列表不能为空');
    return;
  }
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  quickSaveLoading.value = true;
  try {
    await saveEncounterDiagnosisRows(encounterId, patientId, rows, props.stationScope);
    ElMessage.success('诊断已保存');
    await reload();
  } finally {
    quickSaveLoading.value = false;
  }
}

/** 点击「新增诊断」：先接诊/切在诊再打开检索弹窗 */
async function handleAddDiagnosisClick() {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  pickerOpen.value = true;
}

/** 侧栏常用树选中叶子：并入列表并保存 */
async function handleQuickAppend(append: EncounterDiagnosisRowDto) {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  await persistMergedRows([...diagnosisRows.value, append]);
}

/** 删除已持久化行 */
async function handleRemove(row: EncounterDiagnosisRowDto) {
  const encounterId = props.encounterId?.trim();
  const cid = row.conditionId;
  if (!cid || !encounterId) return;
  try {
    await deleteEncounterDiagnosisRow(encounterId, cid, props.stationScope);
    ElMessage.success('已删除');
    await reload();
  } catch {
    // 全局拦截器已提示
  }
}
</script>

<template>
  <div
    v-loading="listLoading || quickSaveLoading"
    class="diagnosis-list-panel flex h-full min-h-0 flex-col p-2"
  >
    <ElEmpty
      v-if="!contextReady"
      class="flex flex-1 flex-col items-center justify-center"
      description="请先选择患者以加载诊断数据。"
    />
    <template v-else>
      <div class="diagnosis-list-body">
        <DiagnosisQuickTree
          :business-class="businessClass"
          :rows="diagnosisRows"
          @append="handleQuickAppend"
        />
        <DiagnosisSavedTable
          :rows="diagnosisRows"
          @add-click="handleAddDiagnosisClick"
          @remove="handleRemove"
        />
      </div>
      <DiagnosisPickerDialog
        v-model="pickerOpen"
        :encounter-id="props.encounterId.trim()"
        :patient-id="props.patientId.trim()"
        :station-scope="props.stationScope"
        :existing-diagnosis-list="diagnosisRows"
        @saved="reload"
      />
    </template>
  </div>
</template>

<style scoped>
.diagnosis-list-panel {
  gap: 10px;
  min-height: 0;
}

.diagnosis-list-body {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 10px;
  flex: 1;
  min-height: 0;
  align-items: stretch;
}
</style>
