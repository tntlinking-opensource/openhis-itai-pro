<script setup lang="ts">
import type {
  ElepCatalogItemDto,
  ElepCatalogPickerKind,
} from '#/api/doctor-station/elep-catalog.types';
import type {
  ElepEncounterPrescriptionItemDto,
  ElepHistoryDetailDto,
  ElepPatientHistoryItemDto,
} from '#/api/doctor-station/elep.types';

import { computed, onActivated, onMounted, ref, watch } from 'vue';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElScrollbar,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  httpElepCloneToEncounter,
  httpElepGeneratePdf,
  httpElepPreCheck,
  httpElepQueryPlatform,
  httpElepRevoke,
  httpElepSaveDraft,
  httpElepSign,
  httpElepUploadFile,
  httpGetElepHistoryDetail,
  httpListElepEncounterPrescriptions,
  httpListElepPatientHistory,
} from '#/api/doctor-station/elep.http';
import ElepCatalogPickerDialog from './components/elep-catalog-picker-dialog.vue';
import {
  appendDrugLine,
  applyDiagnosis,
  clonePlatformBody,
  createEmptyElepPlatformBody,
  removeDrugLine,
} from './services/elep-draft.service';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';

import {
  canElepEditDraft,
  canElepGeneratePdf,
  canElepPreCheck,
  canElepQueryPlatform,
  canElepRevoke,
  canElepSign,
  canElepUpload,
  formatElepTime,
  resolveElepStatusLabel,
  resolveElepStatusTagType,
} from './services/elep-status.service';

defineOptions({
  name: 'OutpatientDoctorStationElepAdvice',
});

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
  }>(),
  {
    encounterId: '',
    patientId: '',
  },
);

const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const contractBusNo = ref<string>();
const chargeTypeOptions = ref<Array<{ busNo?: string; categoryEnum?: string; name?: string }>>([]);

const encounterItems = ref<ElepEncounterPrescriptionItemDto[]>([]);
const historyItems = ref<ElepPatientHistoryItemDto[]>([]);
const activePrescriptionId = ref<string>('');
const detail = ref<ElepHistoryDetailDto | null>(null);
const editPlatformBody = ref<Record<string, unknown> | null>(null);
const pickerVisible = ref(false);
const pickerKind = ref<ElepCatalogPickerKind>('drug');

const listLoading = ref(false);
const detailLoading = ref(false);
const actionLoading = ref(false);

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

const contractReady = computed(() => Boolean(contractBusNo.value?.trim()));

const isDraftEditing = computed(
  () => Boolean(editPlatformBody.value) && canElepEditDraft(detail.value?.localStatus),
);

const editableDrugLines = computed(() => {
  const lines = editPlatformBody.value?.rxdrugdetail;
  if (!Array.isArray(lines)) return [];
  return lines.map((line, index) => ({
    lineNo: index + 1,
    medListCodg: (line as Record<string, unknown>).medListCodg as string | undefined,
    drugName: ((line as Record<string, unknown>).drugName ??
      (line as Record<string, unknown>).drugGenname) as string | undefined,
    drugSpec: (line as Record<string, unknown>).drugSpec as string | undefined,
  }));
});

const editableDiagnosisLabel = computed(() => {
  const diseinfo = editPlatformBody.value?.diseinfo as Record<string, unknown> | undefined;
  if (!diseinfo) return '—';
  const name = (diseinfo.diagName as string | undefined)?.trim();
  const code = (diseinfo.diagCode as string | undefined)?.trim();
  if (name && code) return `${name}（${code}）`;
  return name || code || '—';
});

async function loadChargeTypes() {
  try {
    const rows = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(rows ?? []);
    chargeTypeOptions.value = eligible;
    contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
  } catch {
    chargeTypeOptions.value = [];
    contractBusNo.value = undefined;
  }
}

async function loadEncounterPrescriptions() {
  if (!contextReady.value) {
    encounterItems.value = [];
    return;
  }
  listLoading.value = true;
  try {
    encounterItems.value = await httpListElepEncounterPrescriptions({
      encounterId: props.encounterId!.trim(),
    });
    if (
      activePrescriptionId.value &&
      !encounterItems.value.some((item) => item.prescriptionId === activePrescriptionId.value)
    ) {
      activePrescriptionId.value = '';
      detail.value = null;
    }
    if (!activePrescriptionId.value && encounterItems.value.length > 0) {
      await selectPrescription(encounterItems.value[0]?.prescriptionId ?? '');
    }
  } finally {
    listLoading.value = false;
  }
}

async function loadPatientHistory() {
  if (!props.patientId?.trim()) {
    historyItems.value = [];
    return;
  }
  try {
    const res = await httpListElepPatientHistory({
      patientId: props.patientId.trim(),
      currentEncounterId: props.encounterId?.trim() || undefined,
      contractBusNo: contractBusNo.value,
      page: 0,
      pageSize: 30,
    });
    historyItems.value = res.items ?? [];
  } catch {
    historyItems.value = [];
  }
}

async function reloadAll() {
  await Promise.all([loadEncounterPrescriptions(), loadPatientHistory()]);
}

async function selectPrescription(prescriptionId?: string | null) {
  const id = (prescriptionId ?? '').trim();
  activePrescriptionId.value = id;
  detail.value = null;
  editPlatformBody.value = null;
  if (!id) return;
  detailLoading.value = true;
  try {
    detail.value = await httpGetElepHistoryDetail(id);
    if (detail.value.uploadRequest && canElepEditDraft(detail.value.localStatus)) {
      editPlatformBody.value = clonePlatformBody(detail.value.uploadRequest);
    }
  } catch {
    detail.value = null;
  } finally {
    detailLoading.value = false;
  }
}

function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择医保契约');
  }
  return busNo;
}

async function withClinicalGuard(action: () => Promise<void>) {
  if (!contextReady.value) {
    ElMessage.warning('请先选择就诊患者');
    return;
  }
  const ok = await ensureReceptionBeforeClinicalWork();
  if (!ok) return;
  await action();
}

async function handlePreCheck() {
  const platformBody = isDraftEditing.value
    ? editPlatformBody.value
    : (detail.value?.uploadRequest as Record<string, unknown> | undefined);
  if (!detail.value?.prescriptionId || !platformBody) {
    ElMessage.warning('当前处方缺少预核验数据，请新建或编辑草稿后再签发');
    return;
  }
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      if (isDraftEditing.value) {
        await httpElepSaveDraft({
          contractBusNo: requireContractBusNo(),
          encounterId: props.encounterId!.trim(),
          patientId: props.patientId!.trim(),
          prescriptionId: detail.value!.prescriptionId,
          platformBody,
        });
      }
      const res = await httpElepPreCheck({
        contractBusNo: requireContractBusNo(),
        encounterId: props.encounterId!.trim(),
        patientId: props.patientId!.trim(),
        platformBody,
      });
      if (res.success === false) {
        ElMessage.error(res.message || '预核验失败');
        return;
      }
      ElMessage.success(res.message || '预核验成功');
      await reloadAll();
      await selectPrescription(detail.value!.prescriptionId);
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleSign() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const res = await httpElepSign({
        contractBusNo: requireContractBusNo(),
        prescriptionId: detail.value!.prescriptionId!,
      });
      if (res.success === false) {
        ElMessage.error(res.message || '签名失败');
        return;
      }
      ElMessage.success(res.message || '签名成功');
      await reloadAll();
      await selectPrescription(detail.value!.prescriptionId);
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleUpload() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const res = await httpElepUploadFile({
        contractBusNo: requireContractBusNo(),
        prescriptionId: detail.value!.prescriptionId!,
      });
      if (res.success === false) {
        ElMessage.error(res.message || '上传失败');
        return;
      }
      ElMessage.success(res.message || '上传成功');
      await reloadAll();
      await selectPrescription(detail.value!.prescriptionId);
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleRevoke() {
  if (!detail.value?.prescriptionId) return;
  await ElMessageBox.confirm('确认撤销该电子处方？', '撤销确认', { type: 'warning' });
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const res = await httpElepRevoke({
        contractBusNo: requireContractBusNo(),
        prescriptionId: detail.value!.prescriptionId!,
      });
      if (res.success === false) {
        ElMessage.error(res.message || '撤销失败');
        return;
      }
      ElMessage.success(res.message || '撤销成功');
      await reloadAll();
      await selectPrescription(detail.value!.prescriptionId);
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleQueryPlatform() {
  if (!detail.value?.prescriptionId) return;
  actionLoading.value = true;
  try {
    const res = await httpElepQueryPlatform({
      contractBusNo: requireContractBusNo(),
      prescriptionId: detail.value.prescriptionId,
      hiRxno: detail.value.hiRxno,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '平台查询失败');
      return;
    }
    ElMessage.success(res.message || '平台状态已刷新');
    await selectPrescription(detail.value.prescriptionId);
  } finally {
    actionLoading.value = false;
  }
}

async function handleGeneratePdf() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const res = await httpElepGeneratePdf({
        contractBusNo: requireContractBusNo(),
        prescriptionId: detail.value!.prescriptionId!,
        pdfInput: {
          prescriptionNo: detail.value!.hospRxno,
          rxTraceCode: detail.value!.hiRxno,
          diagName: detail.value!.mainDiagName || detail.value!.diagnosis?.diagName,
        },
      });
      if (res.success === false) {
        ElMessage.error(res.message || 'PDF 生成失败');
        return;
      }
      ElMessage.success(res.message || 'PDF 生成成功');
      await selectPrescription(detail.value!.prescriptionId);
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleNewDraft() {
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const draft = await httpElepSaveDraft({
        contractBusNo: requireContractBusNo(),
        encounterId: props.encounterId!.trim(),
        patientId: props.patientId!.trim(),
        platformBody: createEmptyElepPlatformBody(),
      });
      ElMessage.success('已创建空白草稿');
      await reloadAll();
      if (draft.prescriptionId) {
        await selectPrescription(draft.prescriptionId);
      }
    } finally {
      actionLoading.value = false;
    }
  });
}

async function handleSaveDraft() {
  if (!editPlatformBody.value) return;
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const draft = await httpElepSaveDraft({
        contractBusNo: requireContractBusNo(),
        encounterId: props.encounterId!.trim(),
        patientId: props.patientId!.trim(),
        prescriptionId: detail.value?.prescriptionId,
        platformBody: editPlatformBody.value!,
      });
      ElMessage.success('草稿已保存');
      await reloadAll();
      if (draft.prescriptionId) {
        await selectPrescription(draft.prescriptionId);
      }
    } finally {
      actionLoading.value = false;
    }
  });
}

function openPicker(kind: ElepCatalogPickerKind) {
  if (!contractReady.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  pickerKind.value = kind;
  pickerVisible.value = true;
}

function handlePickerConfirm(item: ElepCatalogItemDto) {
  if (!editPlatformBody.value) {
    editPlatformBody.value = createEmptyElepPlatformBody(detail.value?.hospRxno);
  }
  editPlatformBody.value =
    pickerKind.value === 'drug'
      ? appendDrugLine(editPlatformBody.value, item)
      : applyDiagnosis(editPlatformBody.value, item);
}

function handleRemoveDrugLine(lineNo: number) {
  if (!editPlatformBody.value) return;
  editPlatformBody.value = removeDrugLine(editPlatformBody.value, lineNo);
}

async function handleCloneFromHistory(item: ElepPatientHistoryItemDto, continuationRx: boolean) {
  if (!item.prescriptionId) return;
  if (continuationRx && !item.canContinuationRx) {
    ElMessage.warning('该历史方不满足续方条件，请改用套用');
    return;
  }
  await withClinicalGuard(async () => {
    actionLoading.value = true;
    try {
      const draft = await httpElepCloneToEncounter({
        sourcePrescriptionId: item.prescriptionId!,
        targetEncounterId: props.encounterId!.trim(),
        patientId: props.patientId!.trim(),
        contractBusNo: requireContractBusNo(),
        continuationRx,
        copyDiagnosis: true,
      });
      ElMessage.success(continuationRx ? '续方草稿已生成' : '套用草稿已生成');
      await reloadAll();
      if (draft.prescriptionId) {
        await selectPrescription(draft.prescriptionId);
      }
    } finally {
      actionLoading.value = false;
    }
  });
}

watch(
  () => [props.encounterId, props.patientId] as const,
  () => {
    void reloadAll();
  },
);

watch(contractBusNo, () => {
  void loadPatientHistory();
});

onMounted(() => {
  void loadChargeTypes().then(() => reloadAll());
});

onActivated(() => {
  void reloadAll();
});
</script>

<template>
  <div class="elep-advice flex h-full min-h-0 flex-col gap-3 p-3">
    <div class="flex flex-wrap items-center gap-3">
      <SectionTitle title="电子处方" />
      <ElSelect v-model="contractBusNo" class="w-52" clearable filterable placeholder="医保契约">
        <ElOption
          v-for="item in chargeTypeOptions"
          :key="item.busNo"
          :label="item.name || item.busNo"
          :value="item.busNo!"
        />
      </ElSelect>
      <ElButton :disabled="!contractReady" @click="handleNewDraft">新建草稿</ElButton>
      <ElButton :loading="listLoading" @click="reloadAll">刷新</ElButton>
    </div>

    <div v-if="!contextReady" class="flex flex-1 items-center justify-center">
      <ElEmpty description="请先选择就诊患者" />
    </div>

    <div v-else class="grid min-h-0 flex-1 grid-cols-[minmax(280px,34%)_1fr] gap-3">
      <section
        class="flex min-h-0 flex-col rounded border border-[var(--el-border-color)] bg-white"
      >
        <div class="border-b px-3 py-2 text-sm font-medium">历史电子处方</div>
        <ElScrollbar class="min-h-0 flex-1">
          <div v-if="historyItems.length === 0" class="p-4">
            <ElEmpty description="暂无历史上传方" :image-size="64" />
          </div>
          <div v-else class="flex flex-col gap-2 p-2">
            <div
              v-for="item in historyItems"
              :key="item.prescriptionId"
              class="rounded border border-[var(--el-border-color-lighter)] p-2"
            >
              <div class="text-xs text-[var(--el-text-color-secondary)]">
                {{ formatElepTime(item.prscTime) }}
              </div>
              <div class="mt-1 text-sm font-medium">{{ item.mainDiagName || '—' }}</div>
              <div class="mt-1 line-clamp-2 text-xs text-[var(--el-text-color-regular)]">
                {{ item.drugSummary || '—' }}
              </div>
              <div class="mt-2 flex gap-2">
                <ElButton
                  link
                  size="small"
                  type="primary"
                  @click="handleCloneFromHistory(item, false)"
                >
                  套用
                </ElButton>
                <ElButton
                  v-if="item.canContinuationRx"
                  link
                  size="small"
                  type="success"
                  @click="handleCloneFromHistory(item, true)"
                >
                  续方
                </ElButton>
              </div>
            </div>
          </div>
        </ElScrollbar>
      </section>

      <section class="flex min-h-0 flex-col gap-3">
        <div class="rounded border border-[var(--el-border-color)] bg-white p-2">
          <div class="mb-2 text-sm font-medium">当前就诊草稿</div>
          <ElTable
            v-loading="listLoading"
            :data="encounterItems"
            highlight-current-row
            size="small"
            @current-change="(row) => selectPrescription(row?.prescriptionId)"
          >
            <ElTableColumn label="院内处方号" min-width="120" prop="hospRxno" />
            <ElTableColumn label="诊断" min-width="120" prop="mainDiagName" show-overflow-tooltip />
            <ElTableColumn
              label="药品摘要"
              min-width="160"
              prop="drugSummary"
              show-overflow-tooltip
            />
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <ElTag :type="resolveElepStatusTagType(row.localStatus)" size="small">
                  {{ resolveElepStatusLabel(row.localStatus) }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>

        <div
          v-loading="detailLoading"
          class="min-h-0 flex-1 rounded border border-[var(--el-border-color)] bg-white p-3"
        >
          <template v-if="detail">
            <div class="mb-3 flex flex-wrap items-center gap-2">
              <ElTag :type="resolveElepStatusTagType(detail.localStatus)">
                {{ resolveElepStatusLabel(detail.localStatus) }}
              </ElTag>
              <span class="text-sm text-[var(--el-text-color-secondary)]">
                {{ detail.hospRxno }}
              </span>
              <div class="ml-auto flex flex-wrap gap-2">
                <template v-if="isDraftEditing">
                  <ElButton size="small" @click="openPicker('drug')">添加药品</ElButton>
                  <ElButton size="small" @click="openPicker('diagnosis')">选择诊断</ElButton>
                  <ElButton
                    :loading="actionLoading"
                    size="small"
                    type="primary"
                    @click="handleSaveDraft"
                  >
                    保存草稿
                  </ElButton>
                </template>
                <ElButton
                  v-if="canElepGeneratePdf(detail.localStatus, detail.pdfFilePath)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  @click="handleGeneratePdf"
                >
                  生成 PDF
                </ElButton>
                <ElButton
                  v-if="canElepPreCheck(detail.localStatus)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  type="primary"
                  @click="handlePreCheck"
                >
                  预核验
                </ElButton>
                <ElButton
                  v-if="canElepSign(detail.localStatus)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  type="primary"
                  @click="handleSign"
                >
                  签名
                </ElButton>
                <ElButton
                  v-if="canElepUpload(detail.localStatus)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  type="success"
                  @click="handleUpload"
                >
                  上传
                </ElButton>
                <ElButton
                  v-if="canElepQueryPlatform(detail.localStatus)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  @click="handleQueryPlatform"
                >
                  刷新平台状态
                </ElButton>
                <ElButton
                  v-if="canElepRevoke(detail.localStatus)"
                  :disabled="!contractReady"
                  :loading="actionLoading"
                  size="small"
                  type="danger"
                  @click="handleRevoke"
                >
                  撤销
                </ElButton>
              </div>
            </div>

            <ElDescriptions :column="2" border size="small">
              <ElDescriptionsItem label="医保处方号">
                {{ detail.hiRxno || '—' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="续方源号">
                {{ detail.initRxno || '—' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="开方医师">
                {{ detail.prscDrName || '—' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="开方时间">
                {{ formatElepTime(detail.prscTime) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="主诊断" :span="2">
                {{
                  isDraftEditing
                    ? editableDiagnosisLabel
                    : detail.mainDiagName || detail.diagnosis?.diagName || '—'
                }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="药品摘要" :span="2">
                {{ detail.drugSummary || '—' }}
              </ElDescriptionsItem>
            </ElDescriptions>

            <div
              v-if="(isDraftEditing ? editableDrugLines : detail.drugLines)?.length"
              class="mt-3"
            >
              <div class="mb-2 text-sm font-medium">药品明细</div>
              <ElTable :data="isDraftEditing ? editableDrugLines : detail.drugLines" size="small">
                <ElTableColumn label="#" prop="lineNo" width="48" />
                <ElTableColumn label="医保编码" min-width="120" prop="medListCodg" />
                <ElTableColumn label="药品名称" min-width="140" prop="drugName" />
                <ElTableColumn label="规格" min-width="100" prop="drugSpec" />
                <ElTableColumn v-if="isDraftEditing" label="操作" width="72">
                  <template #default="{ row }">
                    <ElButton
                      link
                      size="small"
                      type="danger"
                      @click="handleRemoveDrugLine(row.lineNo)"
                    >
                      删除
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </template>
          <ElEmpty v-else description="选择上方草稿、新建或从历史套用/续方" />
        </div>
      </section>
    </div>

    <ElepCatalogPickerDialog
      v-model="pickerVisible"
      :contract-bus-no="contractBusNo"
      :kind="pickerKind"
      @confirm="handlePickerConfirm"
    />
  </div>
</template>

<style scoped>
.elep-advice {
  background: var(--el-bg-color-page);
}
</style>
