<script setup lang="ts">
import type { DoctorStationPatient } from '../doctorPatientStore';

import type {
  DoctorStationAdviceRowDto,
  PrescriptionScopeTabCode,
} from '#/api/doctor-station/advice.types';
import type { DispensePrescriptionBlock } from '#/views/outpatient/dispense/components/types';

import { computed, ref, watch } from 'vue';

import { ElDialog, ElEmpty, ElMessage, ElRadioButton, ElRadioGroup } from 'element-plus';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import PrescriptionSheetView from '#/views/outpatient/dispense/components/prescription-sheet-view.vue';

import {
  buildDoctorStationPrescriptionSheetData,
  findHeadAdviceRowForBlock,
  mapAdviceRowsToPrescriptionBlocks,
  mapDoctorStationPatientToDispenseQueuePatient,
} from '../services/map-doctor-station-prescription-sheet';
import { fetchDoctorStationPrescriptionAdviceRows } from '../services/prescription-sheet.service';

defineOptions({ name: 'DoctorStationPrescriptionSheetDialog' });

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  patient: null,
});

interface Props {
  encounterId?: string;
  patientId?: string;
  patient?: DoctorStationPatient | null;
}

const visible = defineModel<boolean>('visible', { default: false });

const scopeTab = ref<PrescriptionScopeTabCode>('CURRENT_ENCOUNTER');
const loading = ref(false);
const adviceRows = ref<DoctorStationAdviceRowDto[]>([]);
const sheetBlocks = ref<DispensePrescriptionBlock[]>([]);

const dispensePatient = computed(() => {
  if (!props.patient) return null;
  return mapDoctorStationPatientToDispenseQueuePatient(props.patient);
});

/** 弹窗内多张处方单展示项 */
const prescriptionSheetViewItems = computed(() => {
  const patient = dispensePatient.value;
  if (!patient || sheetBlocks.value.length === 0) return [];
  const isHistory = scopeTab.value === 'HISTORY';
  return sheetBlocks.value.map((block) => {
    const headRow = findHeadAdviceRowForBlock(block, adviceRows.value);
    return {
      key: block.id,
      data: buildDoctorStationPrescriptionSheetData(patient, block, headRow, {
        useHistoryEncounterNo: isHistory,
      }),
    };
  });
});

/** 拉取当前 Tab 下本次就诊 / 历史处方（pageDoctorStationAdvice） */
async function loadPrescriptionSlips() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!encounterId || !patientId) {
    adviceRows.value = [];
    sheetBlocks.value = [];
    return;
  }
  loading.value = true;
  try {
    const rows = await fetchDoctorStationPrescriptionAdviceRows({
      encounterId,
      patientId,
      prescriptionScopeTabCode: scopeTab.value,
    });
    adviceRows.value = rows;
    sheetBlocks.value = mapAdviceRowsToPrescriptionBlocks(rows);
  } catch {
    adviceRows.value = [];
    sheetBlocks.value = [];
    ElMessage.error('加载处方单失败');
  } finally {
    loading.value = false;
  }
}

/** 切换本次就诊 / 历史处方 Tab 后重新拉数 */
function handleScopeTabChange() {
  void loadPrescriptionSlips();
}

/** 弹窗打开时默认本次就诊并加载 */
watch(visible, (open) => {
  if (!open) return;
  scopeTab.value = 'CURRENT_ENCOUNTER';
  void loadPrescriptionSlips();
});

/** 切换患者后若弹窗仍开，刷新列表 */
watch(
  () => [props.encounterId, props.patientId] as const,
  () => {
    if (!visible.value) return;
    void loadPrescriptionSlips();
  },
);
</script>

<template>
  <ElDialog
    v-model="visible"
    title="处方单查看"
    width="min(920px, 94vw)"
    append-to-body
    align-center
    class="dispense-rx-sheet-dialog doctor-station-rx-sheet-dialog"
    destroy-on-close
  >
    <div class="mb-3 flex items-center justify-center">
      <ElRadioGroup v-model="scopeTab" size="small" @change="handleScopeTabChange">
        <ElRadioButton value="CURRENT_ENCOUNTER">本次就诊</ElRadioButton>
        <ElRadioButton value="HISTORY">历史处方</ElRadioButton>
      </ElRadioGroup>
    </div>
    <div
      v-loading="loading"
      class="dispense-rx-sheet-scroll max-h-[min(720px,78vh)] min-h-0 overflow-auto pb-1"
    >
      <template v-if="prescriptionSheetViewItems.length > 0">
        <div
          v-for="item in prescriptionSheetViewItems"
          :key="item.key"
          class="dispense-rx-sheet-stack-item mb-6 last:mb-0"
        >
          <PrescriptionSheetView :data="item.data" comfortable />
        </div>
      </template>
      <ElEmpty v-else-if="!loading" description="暂无处方单数据" :image-size="56" />
    </div>
  </ElDialog>
</template>

<style scoped>
:deep(.doctor-station-rx-sheet-dialog.el-dialog) {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}

:deep(.doctor-station-rx-sheet-dialog .el-dialog__body) {
  padding-top: 8px;
  background-color: transparent;
}

.dispense-rx-sheet-scroll {
  background-color: transparent;
}
</style>
