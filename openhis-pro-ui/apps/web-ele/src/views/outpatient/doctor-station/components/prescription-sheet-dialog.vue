<script setup lang="ts">
import type { DoctorStationPatient } from '../doctorPatientStore';

import type {
  DoctorStationAdviceRowDto,
  PrescriptionScopeTabCode,
} from '#/api/doctor-station/advice.types';
import type { DispensePrescriptionBlock } from '#/views/outpatient/dispense/components/types';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElEmpty, ElMessage, ElRadioButton, ElRadioGroup } from 'element-plus';

import { fetchEncounterTcmDiagnosisDisplayText } from '#/components/doctor-station/diagnosis/services/fetch-encounter-tcm-diagnosis-text';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { printDoctorStationPrescriptions } from '#/utils/print/print-doctor-station-prescription';
import PrescriptionSheetView from '#/views/outpatient/dispense/components/prescription-sheet-view.vue';

import {
  buildDoctorStationPrescriptionSheetData,
  findHeadAdviceRowForBlock,
  mapAdviceRowsToPrescriptionBlocks,
  mapDoctorStationPatientToDispenseQueuePatient,
} from '../services/map-doctor-station-prescription-sheet';
import { fetchDoctorStationPrescriptionAdviceRows } from '../services/prescription-sheet.service';

defineOptions({ name: 'DoctorStationPrescriptionSheetDialog' });

interface Props {
  encounterId?: string;
  patientId?: string;
  patient?: DoctorStationPatient | null;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  patient: null,
});

const visible = defineModel<boolean>('visible', { default: false });

const scopeTab = ref<PrescriptionScopeTabCode>('CURRENT_ENCOUNTER');
const loading = ref(false);
const printSubmitting = ref(false);
/** 正在单张打印的笺面 key；空表示未在单张打印 */
const printingSheetKey = ref('');
const adviceRows = ref<DoctorStationAdviceRowDto[]>([]);
const sheetBlocks = ref<DispensePrescriptionBlock[]>([]);
/** 就诊 id → 中医主诊断展示文案（病证合并，供中药方头回显） */
const tcmDiagnosisTextByEncounterId = ref<Record<string, string>>({});

const dispensePatient = computed(() => {
  if (!props.patient) return null;
  return mapDoctorStationPatientToDispenseQueuePatient(props.patient);
});

/** 弹窗内多张处方单展示项（笺面组件与发药工作站同源） */
const prescriptionSheetViewItems = computed(() => {
  const patient = dispensePatient.value;
  if (!patient || sheetBlocks.value.length === 0) return [];
  const isHistory = scopeTab.value === 'HISTORY';
  const fallbackEncounterId = preserveApiIdForRequest(props.encounterId);
  return sheetBlocks.value.map((block) => {
    const headRow = findHeadAdviceRowForBlock(block, adviceRows.value);
    const blockEncounterId = preserveApiIdForRequest(headRow?.encounterId) || fallbackEncounterId;
    return {
      key: block.id,
      encounterId: blockEncounterId,
      data: buildDoctorStationPrescriptionSheetData(patient, block, headRow, {
        useHistoryEncounterNo: isHistory,
        tcmDiagnosisText: blockEncounterId
          ? tcmDiagnosisTextByEncounterId.value[blockEncounterId]
          : undefined,
      }),
    };
  });
});

/**
 * 为中药处方块涉及的就诊拉取主诊断文案（病证合并）。
 * 历史 Tab 可能跨多个就诊，按 encounterId 去重后并行请求。
 */
async function loadTcmDiagnosisTextsForBlocks(
  blocks: DispensePrescriptionBlock[],
  rows: DoctorStationAdviceRowDto[],
  patientId: string,
  fallbackEncounterId: string,
): Promise<Record<string, string>> {
  const encounterIds = new Set<string>();
  for (const block of blocks) {
    if (block.rxType !== 'herbal') continue;
    const headRow = findHeadAdviceRowForBlock(block, rows);
    const encId = preserveApiIdForRequest(headRow?.encounterId) || fallbackEncounterId;
    if (encId) encounterIds.add(encId);
  }
  if (encounterIds.size === 0) return {};
  const entries = await Promise.all(
    [...encounterIds].map(async (encId) => {
      try {
        const text = await fetchEncounterTcmDiagnosisDisplayText(encId, patientId);
        return [encId, text] as const;
      } catch {
        return [encId, ''] as const;
      }
    }),
  );
  const result: Record<string, string> = {};
  for (const [encId, text] of entries) {
    if (text) result[encId] = text;
  }
  return result;
}

/** 拉取当前 Tab 下本次就诊 / 历史已签发处方 */
async function loadPrescriptionSlips() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!encounterId || !patientId) {
    adviceRows.value = [];
    sheetBlocks.value = [];
    tcmDiagnosisTextByEncounterId.value = {};
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
    const blocks = mapAdviceRowsToPrescriptionBlocks(rows);
    sheetBlocks.value = blocks;
    tcmDiagnosisTextByEncounterId.value = await loadTcmDiagnosisTextsForBlocks(
      blocks,
      rows,
      patientId,
      encounterId,
    );
  } catch {
    adviceRows.value = [];
    sheetBlocks.value = [];
    tcmDiagnosisTextByEncounterId.value = {};
    ElMessage.error('加载处方单失败');
  } finally {
    loading.value = false;
  }
}

/** 切换本次就诊 / 历史处方 Tab 后重新拉数 */
function handleScopeTabChange() {
  void loadPrescriptionSlips();
}

/**
 * 打印处方笺：传入单张则只打该张，否则打当前列表全部。
 * @param sheetKey 笺面 key；空表示全部
 */
async function handlePrintPrescription(sheetKey?: string) {
  const fallbackEncounterId = preserveApiIdForRequest(props.encounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!patientId) {
    ElMessage.warning('缺少患者信息，无法打印');
    return;
  }
  const targetKey = String(sheetKey ?? '').trim();
  const sourceItems = targetKey
    ? prescriptionSheetViewItems.value.filter((item) => item.key === targetKey)
    : prescriptionSheetViewItems.value;
  const sheetItems = sourceItems.map((item) => ({
    data: item.data,
    encounterId: item.encounterId || fallbackEncounterId,
  }));
  if (sheetItems.length === 0) {
    ElMessage.warning('暂无已签发的药品可打印');
    return;
  }
  printSubmitting.value = true;
  printingSheetKey.value = targetKey;
  try {
    const result = await printDoctorStationPrescriptions({
      encounterId: fallbackEncounterId || sheetItems[0]!.encounterId || '',
      patientId,
      sheetItems,
      jobName: '门诊医生站处方',
    });
    if (!result.ok) {
      ElMessage.warning('处方打印失败，请稍后重试');
      return;
    }
    ElMessage.success(
      result.count === 1 ? '已发送处方单打印' : `已发送 ${result.count} 张处方单打印`,
    );
  } catch {
    ElMessage.error('处方打印失败');
  } finally {
    printSubmitting.value = false;
    printingSheetKey.value = '';
  }
}

/** 工具栏：打印当前列表全部处方 */
function handlePrintAllPrescriptions() {
  void handlePrintPrescription();
}

/** 单张笺面打印 */
function handlePrintOneSheet(sheetKey: string) {
  void handlePrintPrescription(sheetKey);
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
    width="min(1080px, 96vw)"
    append-to-body
    align-center
    class="doctor-station-rx-sheet-dialog"
    destroy-on-close
  >
    <div class="doctor-station-rx-sheet-toolbar mb-3 flex items-center justify-between gap-3">
      <div class="flex-1" />
      <ElRadioGroup
        v-model="scopeTab"
        size="small"
        class="doctor-station-rx-sheet-scope"
        @change="handleScopeTabChange"
      >
        <ElRadioButton value="CURRENT_ENCOUNTER">本次就诊</ElRadioButton>
        <ElRadioButton value="HISTORY">历史处方</ElRadioButton>
      </ElRadioGroup>
      <div class="flex flex-1 justify-end">
        <ElButton
          type="primary"
          :loading="printSubmitting && !printingSheetKey"
          :disabled="loading || prescriptionSheetViewItems.length === 0 || printSubmitting"
          @click="handlePrintAllPrescriptions"
        >
          全部打印
        </ElButton>
      </div>
    </div>
    <div
      v-loading="loading"
      class="doctor-station-rx-sheet-scroll max-h-[min(720px,78vh)] min-h-0 overflow-y-auto overflow-x-hidden"
    >
      <div
        v-if="prescriptionSheetViewItems.length > 0"
        class="doctor-station-rx-sheet-row flex min-h-0 flex-col items-center gap-4"
      >
        <PrescriptionSheetView
          v-for="item in prescriptionSheetViewItems"
          :key="item.key"
          class="doctor-station-rx-sheet-card"
          variant="doctorRx"
          :data="item.data"
          :print-disabled="printSubmitting"
          :print-loading="printingSheetKey === item.key"
          comfortable
          @print="handlePrintOneSheet(item.key)"
        />
      </div>
      <ElEmpty v-else-if="!loading" description="暂无已签发的处方单" :image-size="56" />
    </div>
  </ElDialog>
</template>

<style scoped>
.doctor-station-rx-sheet-scroll {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
}

.doctor-station-rx-sheet-row {
  width: 100%;
}

/** Tab 选中降权为 plain，与发药工作站一致 */
:deep(.doctor-station-rx-sheet-scope .el-radio-button__inner) {
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-blank);
  border-color: var(--el-border-color);
  box-shadow: none !important;
}

:deep(.doctor-station-rx-sheet-scope .el-radio-button.is-active .el-radio-button__inner) {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  box-shadow: none !important;
}
</style>

<!-- teleported Dialog 根节点样式 -->
<style>
.doctor-station-rx-sheet-dialog.el-dialog {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}

.doctor-station-rx-sheet-dialog .el-dialog__body {
  padding-top: 8px;
  background-color: transparent;
}
</style>
