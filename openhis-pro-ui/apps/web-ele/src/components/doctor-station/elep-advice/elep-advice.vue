<script setup lang="ts">
import type { ElepCatalogItemDto } from '#/api/doctor-station/elep-catalog.types';
import type {
  ElepEncounterContextDto,
  ElepEncounterPrescriptionItemDto,
  ElepHistoryDetailDto,
} from '#/api/doctor-station/elep.types';

import { computed, onActivated, onMounted, ref, watch } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElScrollbar,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  httpElepGeneratePdf,
  httpElepLoadEncounterContext,
  httpElepPreCheck,
  httpElepQueryPlatform,
  httpElepReview,
  httpElepRevoke,
  httpElepSaveDraft,
  httpElepSign,
  httpElepUploadFile,
  httpGetElepHistoryDetail,
  httpListElepEncounterPrescriptions,
} from '#/api/doctor-station/elep.http';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import './elep-advice-layout.css';

import ElepCatalogSelect from './components/elep-catalog-select.vue';
import {
  appendDrugLine,
  applyDiagnosis,
  applyDisease,
  applyDrugLinePatch,
  createEmptyElepOpenForm,
  formFromElepDetail,
  removeDrugLine,
  resolveElepDrugLineError,
  type ElepDrugLinePatchField,
  type ElepOpenForm,
} from './services/elep-draft.service';
import {
  canElepEditDraft,
  canElepGeneratePdf,
  canElepPreCheck,
  canElepQueryPlatform,
  canElepReview,
  canElepRevoke,
  canElepSign,
  canElepUpload,
  ELEP_FLOW_STEPS,
  formatElepTimeShort,
  hasElepReviewed,
  resolveElepFlowStepIndex,
  resolveElepQueueTitle,
  resolveElepStatusLabel,
  resolveElepStatusTagType,
} from './services/elep-status.service';

defineOptions({
  name: 'OutpatientDoctorStationElepAdvice',
});

interface Props {
  encounterId?: string;
  patientId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
});

const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const contractBusNo = ref<string>();
const contractId = ref<string>();

const encounterContext = ref<ElepEncounterContextDto | null>(null);
const encounterItems = ref<ElepEncounterPrescriptionItemDto[]>([]);
const activePrescriptionId = ref<string>('');
const detail = ref<ElepHistoryDetailDto | null>(null);
const form = ref<ElepOpenForm>(createEmptyElepOpenForm());
const editorAreaRef = ref<HTMLElement | null>(null);

const { handleFormKeydown } = useFormKeyboardNavigation(editorAreaRef, {
  selectPolicy: true,
});

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

const isDraftEditing = computed(() => canElepEditDraft(detail.value?.localStatus));

const isUnsavedDraft = computed(() => !activePrescriptionId.value);

const diagnosisName = computed(() => form.value.diagnosis.diagName?.trim() || '');

const diagnosisCode = computed({
  get: () => form.value.diagnosis.diagCode ?? '',
  set: (value: string) => {
    if (value.trim()) return;
    form.value = { ...form.value, diagnosis: {} };
  },
});

const diseaseName = computed(() => form.value.diseName?.trim() || '');

const diseaseCode = computed({
  get: () => form.value.diseCodg ?? '',
  set: (value: string) => {
    if (value.trim()) return;
    form.value = applyDisease(form.value, null);
  },
});

const flowStepIndex = computed(() =>
  resolveElepFlowStepIndex(detail.value?.localStatus, detail.value?.pharCode),
);

const reviewLabel = computed(() => {
  if (hasElepReviewed(detail.value?.pharCode)) {
    return detail.value?.pharName?.trim() || '已审方';
  }
  return '未审方';
});

const queueItems = computed(() => {
  const saved = encounterItems.value;
  if (!isUnsavedDraft.value) return saved;
  const draftItem: ElepEncounterPrescriptionItemDto = {
    prescriptionId: '',
    mainDiagName: diagnosisName.value,
    localStatus: 'DRAFT',
    drugSummary: form.value.drugLines
      .map((line) => line.drugName?.trim())
      .filter(Boolean)
      .join('、'),
  };
  return [draftItem, ...saved];
});

const isDraftDirty = computed(() => {
  if (!isUnsavedDraft.value || !isDraftEditing.value) return false;
  return Boolean(form.value.diagnosis.diagCode?.trim()) || form.value.drugLines.length > 0;
});

const newDraftDisabledReason = computed(() => {
  if (!contextReady.value) return '请先选择就诊患者';
  return '';
});

const footerSummary = computed(() => {
  const count = form.value.drugLines.length;
  const days = form.value.valiDays.trim() || '3';
  const diag = diagnosisName.value || '未选诊断';
  return `共 ${count} 种药品 · 有效 ${days} 天 · ${diag}`;
});

type ElepNextFlowKey = 'precheck' | 'pdf' | 'review' | 'sign' | 'upload';

const nextFlowAction = computed(() => {
  const status = detail.value?.localStatus;
  const phar = detail.value?.pharCode;
  if (canElepReview(status, phar)) {
    return { key: 'review' as ElepNextFlowKey, label: '审方', type: 'primary' as const };
  }
  if (canElepPreCheck(status, phar)) {
    return { key: 'precheck' as ElepNextFlowKey, label: '预核验', type: 'primary' as const };
  }
  if (canElepGeneratePdf(status, detail.value?.pdfFilePath)) {
    return { key: 'pdf' as ElepNextFlowKey, label: '生成处方单', type: 'primary' as const };
  }
  if (canElepSign(status)) {
    return { key: 'sign' as ElepNextFlowKey, label: '签名', type: 'primary' as const };
  }
  if (canElepUpload(status)) {
    return { key: 'upload' as ElepNextFlowKey, label: '上传医保', type: 'success' as const };
  }
  return null;
});

const { run: runLoadContext, loading: contextLoading } = useRequest(httpElepLoadEncounterContext, {
  showErrorMessage: true,
});

const { run: runListEncounter, loading: listLoading } = useRequest(
  httpListElepEncounterPrescriptions,
  { showErrorMessage: true },
);

const { run: runGetDetail, loading: detailLoading } = useRequest(httpGetElepHistoryDetail, {
  showErrorMessage: true,
});

const { run: runSaveDraft, loading: saveLoading } = useRequest(httpElepSaveDraft, {
  showErrorMessage: true,
});

const { run: runReview, loading: reviewLoading } = useRequest(httpElepReview, {
  showErrorMessage: true,
});

const { run: runPreCheck, loading: preCheckLoading } = useRequest(httpElepPreCheck, {
  showErrorMessage: true,
});

const { run: runSign, loading: signLoading } = useRequest(httpElepSign, {
  showErrorMessage: true,
});

const { run: runUpload, loading: uploadLoading } = useRequest(httpElepUploadFile, {
  showErrorMessage: true,
});

const { run: runGeneratePdf, loading: pdfLoading } = useRequest(httpElepGeneratePdf, {
  showErrorMessage: true,
});

const { run: runQueryPlatform, loading: queryLoading } = useRequest(httpElepQueryPlatform, {
  showErrorMessage: true,
});

const { run: runRevoke, loading: revokeLoading } = useRequest(httpElepRevoke, {
  showErrorMessage: true,
});

const nextFlowLoading = computed(() => {
  const key = nextFlowAction.value?.key;
  if (key === 'review') return reviewLoading.value;
  if (key === 'precheck') return preCheckLoading.value;
  if (key === 'pdf') return pdfLoading.value;
  if (key === 'sign') return signLoading.value;
  if (key === 'upload') return uploadLoading.value;
  return false;
});

/**
 * 挂号契约回填到本页目录检索，医生不选手选省市工伤。
 */
function applyContractFromContext() {
  const ctx = encounterContext.value;
  const busNo = ctx?.contractBusNo?.trim();
  contractBusNo.value = busNo || undefined;
  if (ctx?.contractId == null || String(ctx.contractId).trim() === '') {
    contractId.value = undefined;
    return;
  }
  contractId.value = String(ctx.contractId);
}

/**
 * 挂号没带契约时，用费用性质里第一条医保契约兜底目录检索。
 */
async function applyFallbackContractFromChargeTypes() {
  if (contractBusNo.value?.trim() && contractId.value?.trim()) {
    return;
  }
  try {
    const list = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(list ?? []);
    // 优先保住挂号已有 busNo，否则取第一条医保费用性质
    const busNo = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
    if (busNo) {
      contractBusNo.value = busNo;
    }
    if (!contractId.value?.trim()) {
      const matched = eligible.find((item) => item.busNo === (contractBusNo.value ?? ''));
      contractId.value = matched?.value || eligible[0]?.value;
    }
  } catch {
    // 开发阶段：费用性质拉失败不挡选诊断/加药
  }
}

/**
 * 装载挂号 map(11) 与医师/科室医保码。
 */
async function loadEncounterContext() {
  if (!contextReady.value) {
    encounterContext.value = null;
    contractBusNo.value = undefined;
    contractId.value = undefined;
    return;
  }
  encounterContext.value = await runLoadContext({
    encounterId: props.encounterId!.trim(),
    patientId: props.patientId!.trim(),
  });
  applyContractFromContext();
  await applyFallbackContractFromChargeTypes();
}

/**
 * 刷新当前就诊电子处方列表。
 */
async function loadEncounterPrescriptions() {
  if (!contextReady.value) {
    encounterItems.value = [];
    return;
  }
  encounterItems.value = await runListEncounter({
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
}

/**
 * 刷新上下文与列表。
 */
async function reloadAll() {
  await loadEncounterContext();
  await loadEncounterPrescriptions();
}

/**
 * 选中一张处方并回填本页。
 *
 * @param prescriptionId 处方主键
 */
async function selectPrescription(prescriptionId?: string | null) {
  const id = (prescriptionId ?? '').trim();
  activePrescriptionId.value = id;
  detail.value = null;
  if (!id) {
    form.value = createEmptyElepOpenForm();
    return;
  }
  detail.value = await runGetDetail(id);
  form.value = formFromElepDetail(detail.value);
}

/**
 * 当前医保契约 bus_no；开发阶段允许空串，不在前端抛错。
 */
function requireContractBusNo(): string {
  return contractBusNo.value?.trim() || '';
}

/**
 * 接诊门禁后再执行写操作。
 *
 * @param action 业务动作
 */
async function withClinicalGuard(action: () => Promise<void>) {
  if (!contextReady.value) {
    ElMessage.warning('请先选择就诊患者');
    return;
  }
  const ok = await ensureReceptionBeforeClinicalWork();
  if (!ok) return;
  await action();
}

/**
 * 组装保存草稿请求体。
 */
function buildSaveDraftBody() {
  return {
    contractBusNo: requireContractBusNo(),
    contractId: contractId.value ? preserveApiIdForRequest(contractId.value) : undefined,
    encounterId: props.encounterId!.trim(),
    patientId: props.patientId!.trim(),
    prescriptionId: form.value.prescriptionId || detail.value?.prescriptionId,
    diseCodg: form.value.diseCodg.trim() || undefined,
    diseName: form.value.diseName.trim() || undefined,
    valiDays: form.value.valiDays.trim() || '3',
    diagnosis: form.value.diagnosis,
    drugLines: form.value.drugLines,
  };
}

/**
 * 本页新建空白方，不立即落库。
 */
function handleNewDraft() {
  if (newDraftDisabledReason.value) {
    ElMessage.warning(newDraftDisabledReason.value);
    return;
  }
  activePrescriptionId.value = '';
  detail.value = null;
  form.value = createEmptyElepOpenForm();
}

/**
 * 侧栏是否当前选中项。
 *
 * @param item 队列行
 */
function isQueueItemActive(item: ElepEncounterPrescriptionItemDto): boolean {
  const id = (item.prescriptionId ?? '').trim();
  if (!id) return isUnsavedDraft.value;
  return id === activePrescriptionId.value;
}

/**
 * 侧栏处方标题。
 *
 * @param item 队列行
 */
function resolveQueueItemTitle(item: ElepEncounterPrescriptionItemDto): string {
  return resolveElepQueueTitle(item.mainDiagName, !(item.prescriptionId ?? '').trim());
}

/**
 * 选中侧栏处方；未保存新开方有内容时先确认。
 *
 * @param prescriptionId 处方主键，空串表示新开草稿
 */
async function handleSelectQueueItem(prescriptionId?: string | null) {
  const id = (prescriptionId ?? '').trim();
  if (id === activePrescriptionId.value) return;
  if (!id && isUnsavedDraft.value) return;
  if (isUnsavedDraft.value && isDraftDirty.value && id) {
    try {
      await ElMessageBox.confirm('当前新开处方尚未保存，切换后将丢失，是否继续？', '提示', {
        type: 'warning',
      });
    } catch {
      return;
    }
  }
  await selectPrescription(id);
}

/**
 * 执行当前一步上传动作。
 */
async function handleNextFlow() {
  const key = nextFlowAction.value?.key;
  if (key === 'review') {
    await handleReview();
    return;
  }
  if (key === 'precheck') {
    await handlePreCheck();
    return;
  }
  if (key === 'pdf') {
    await handleGeneratePdf();
    return;
  }
  if (key === 'sign') {
    await handleSign();
    return;
  }
  if (key === 'upload') {
    await handleUpload();
  }
}

/**
 * 保存草稿（只写电子处方表）。
 */
async function handleSaveDraft() {
  await withClinicalGuard(async () => {
    // 3-03 西药行必填：用法、频次、单次用量、服药起止时间
    for (const line of form.value.drugLines) {
      const err = resolveElepDrugLineError(line, line.lineNo ?? 0);
      if (err) {
        ElMessage.warning(err);
        return;
      }
    }
    const draft = await runSaveDraft(buildSaveDraftBody());
    ElMessage.success('草稿已保存，改内容后需重新审方');
    await loadEncounterPrescriptions();
    if (draft.prescriptionId) {
      await selectPrescription(draft.prescriptionId);
    }
  });
}

/**
 * 院内医保药师审方。
 */
async function handleReview() {
  const prescriptionId = detail.value?.prescriptionId;
  if (!prescriptionId) {
    ElMessage.warning('请先保存草稿再审方');
    return;
  }
  await withClinicalGuard(async () => {
    const res = await runReview({
      contractBusNo: requireContractBusNo(),
      prescriptionId,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '审方失败');
      return;
    }
    ElMessage.success(res.message || '审方通过');
    await selectPrescription(prescriptionId);
  });
}

/**
 * A0001 预核验（服务端组装报文）。
 */
async function handlePreCheck() {
  const prescriptionId = detail.value?.prescriptionId;
  if (!prescriptionId) {
    ElMessage.warning('请先保存草稿并审方');
    return;
  }
  await withClinicalGuard(async () => {
    const res = await runPreCheck({
      contractBusNo: requireContractBusNo(),
      contractId: contractId.value,
      encounterId: props.encounterId!.trim(),
      patientId: props.patientId!.trim(),
      prescriptionId,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '预核验失败');
      return;
    }
    ElMessage.success(res.message || '预核验成功');
    await loadEncounterPrescriptions();
    await selectPrescription(prescriptionId);
  });
}

/**
 * A0002 签名。
 */
async function handleSign() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    const res = await runSign({
      contractBusNo: requireContractBusNo(),
      prescriptionId: detail.value!.prescriptionId!,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '签名失败');
      return;
    }
    ElMessage.success(res.message || '签名成功');
    await loadEncounterPrescriptions();
    await selectPrescription(detail.value!.prescriptionId);
  });
}

/**
 * A0003 上传。
 */
async function handleUpload() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    const res = await runUpload({
      contractBusNo: requireContractBusNo(),
      prescriptionId: detail.value!.prescriptionId!,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '上传失败');
      return;
    }
    ElMessage.success(res.message || '上传成功');
    await loadEncounterPrescriptions();
    await selectPrescription(detail.value!.prescriptionId);
  });
}

/**
 * A0004 撤销。
 */
async function handleRevoke() {
  if (!detail.value?.prescriptionId) return;
  await ElMessageBox.confirm('确认撤销该电子处方？', '撤销确认', { type: 'warning' });
  await withClinicalGuard(async () => {
    const res = await runRevoke({
      contractBusNo: requireContractBusNo(),
      prescriptionId: detail.value!.prescriptionId!,
    });
    if (res.success === false) {
      ElMessage.error(res.message || '撤销失败');
      return;
    }
    ElMessage.success(res.message || '撤销成功');
    await loadEncounterPrescriptions();
    await selectPrescription(detail.value!.prescriptionId);
  });
}

/**
 * A0005 刷新平台状态。
 */
async function handleQueryPlatform() {
  if (!detail.value?.prescriptionId) return;
  const res = await runQueryPlatform({
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
}

/**
 * 生成本地 PDF。
 */
async function handleGeneratePdf() {
  if (!detail.value?.prescriptionId) return;
  await withClinicalGuard(async () => {
    const res = await runGeneratePdf({
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
  });
}

/**
 * 方头点选医保诊断。
 *
 * @param item 1307 目录行
 */
function handleDiagnosisSelect(item: ElepCatalogItemDto) {
  form.value = applyDiagnosis(form.value, item);
}

/**
 * 方头点选慢病病种；不选则按普通门诊上传。
 *
 * @param item 1307 目录行
 */
function handleDiseaseSelect(item: ElepCatalogItemDto) {
  form.value = applyDisease(form.value, item);
}

/**
 * 检索点选药品：立即加入明细并清空检索，方便连续加药。
 *
 * @param item 1301 目录行
 */
function handleDrugSelect(item: ElepCatalogItemDto) {
  form.value = appendDrugLine(form.value, item);
}

/**
 * 删除本页药品行。
 *
 * @param lineNo 1-based 行号
 */
function handleRemoveDrugLine(lineNo: number) {
  form.value = removeDrugLine(form.value, lineNo);
}

/**
 * 改药品用法字段。
 *
 * @param lineNo 行号
 * @param field 字段名
 * @param value 输入值
 */
function patchDrugLine(lineNo: number, field: ElepDrugLinePatchField, value: string) {
  form.value = {
    ...form.value,
    // 只改当前行；用法/频次/天数会连带码和结束时间
    drugLines: form.value.drugLines.map((line) => {
      if (line.lineNo !== lineNo) return line;
      return applyDrugLinePatch(line, field, value);
    }),
  };
}

watch(
  () => [props.encounterId, props.patientId] as const,
  () => {
    void reloadAll();
  },
);

onMounted(() => {
  void reloadAll();
});

onActivated(() => {
  void reloadAll();
});
</script>

<template>
  <div class="elep-advice-root grid h-full min-h-0">
    <aside class="elep-advice-root__sidebar flex h-full min-h-0 flex-col">
      <div class="elep-advice-root__sidebar-title">本次处方</div>
      <ElScrollbar v-loading="listLoading" class="min-h-0 flex-1">
        <button
          v-for="(item, index) in queueItems"
          :key="item.prescriptionId || `draft-${index}`"
          class="elep-queue-item"
          :class="{ 'is-active': isQueueItemActive(item) }"
          type="button"
          @click="handleSelectQueueItem(item.prescriptionId)"
        >
          <div class="elep-queue-item__head">
            <span class="elep-queue-item__title">{{ resolveQueueItemTitle(item) }}</span>
            <ElTag :type="resolveElepStatusTagType(item.localStatus)" effect="light" size="small">
              {{
                (item.prescriptionId ?? '').trim()
                  ? resolveElepStatusLabel(item.localStatus)
                  : '未保存'
              }}
            </ElTag>
          </div>
          <div class="elep-queue-item__sub">
            {{ item.drugSummary?.trim() || '尚未添加药品' }}
          </div>
          <div v-if="formatElepTimeShort(item.prscTime)" class="elep-queue-item__meta">
            {{ formatElepTimeShort(item.prscTime) }}
          </div>
        </button>
        <ElEmpty v-if="queueItems.length === 0" class="mt-8" description="本次还没有电子处方" />
      </ElScrollbar>
    </aside>

    <div class="elep-advice-root__main flex h-full min-h-0 flex-col">
      <ElEmpty v-if="!contextReady" class="m-auto" description="请先选择就诊患者" />

      <template v-else>
        <div class="elep-advice-root__chrome flex min-w-0 flex-nowrap items-center gap-2">
          <div class="elep-flow min-w-0">
            <template v-for="(step, index) in ELEP_FLOW_STEPS" :key="step">
              <span
                class="elep-flow__step"
                :class="{
                  'is-current': index === flowStepIndex,
                  'is-done': index < flowStepIndex,
                }"
              >
                {{ step }}
              </span>
              <span v-if="index < ELEP_FLOW_STEPS.length - 1" class="elep-flow__sep">/</span>
            </template>
          </div>

          <div class="elep-rx-panel__status-group shrink-0">
            <span class="elep-rx-panel__status-label">状态</span>
            <ElTag
              class="elep-rx-panel__status"
              :type="resolveElepStatusTagType(detail?.localStatus || 'DRAFT')"
              effect="light"
              size="small"
              round
            >
              {{ isUnsavedDraft ? '未保存' : resolveElepStatusLabel(detail?.localStatus) }}
            </ElTag>
            <ElTag
              class="elep-rx-panel__status"
              :type="hasElepReviewed(detail?.pharCode) ? 'success' : 'info'"
              effect="light"
              size="small"
              round
            >
              {{ reviewLabel }}
            </ElTag>
          </div>

          <div class="elep-rx-panel__toolbar shrink-0">
            <ElTooltip
              :content="newDraftDisabledReason"
              :disabled="!newDraftDisabledReason"
              placement="bottom"
            >
              <ElButton type="primary" @click="handleNewDraft"> 新增处方 </ElButton>
            </ElTooltip>
            <ElButton
              v-if="isDraftEditing"
              type="success"
              :loading="saveLoading"
              @click="handleSaveDraft"
            >
              保存
            </ElButton>
            <ElButton
              v-if="nextFlowAction"
              :loading="nextFlowLoading"
              :type="nextFlowAction.type"
              @click="handleNextFlow"
            >
              {{ nextFlowAction.label }}
            </ElButton>
            <ElButton
              v-if="canElepQueryPlatform(detail?.localStatus)"
              :loading="queryLoading"
              plain
              @click="handleQueryPlatform"
            >
              查询状态
            </ElButton>
            <ElButton
              v-if="canElepRevoke(detail?.localStatus)"
              :loading="revokeLoading"
              type="danger"
              @click="handleRevoke"
            >
              撤销
            </ElButton>
            <ElButton :loading="listLoading || contextLoading" @click="reloadAll">刷新</ElButton>
          </div>
        </div>

        <div v-loading="detailLoading" class="elep-rx-panel">
          <div ref="editorAreaRef" class="elep-rx-panel__body" @keydown.capture="handleFormKeydown">
            <div class="elep-rx-panel__info">
              <ElForm class="elep-rx-header" :model="form" label-width="48px">
                <div class="elep-rx-header__row flex items-center gap-x-2.5 gap-y-1">
                  <ElFormItem
                    label="诊断"
                    class="elep-rx-header__item elep-rx-header__item--diagnosis min-w-0"
                  >
                    <div class="elep-rx-header__diagnosis">
                      <ElepCatalogSelect
                        v-if="isDraftEditing"
                        v-model="diagnosisCode"
                        kind="diagnosis"
                        class="elep-rx-header__diag-select"
                        :selected-label="diagnosisName"
                        placeholder="诊断名称 / 拼音"
                        @select="handleDiagnosisSelect"
                      />
                      <span
                        v-else
                        class="elep-rx-header__diag-name"
                        :class="{ 'is-empty': !diagnosisName }"
                      >
                        {{ diagnosisName || '—' }}
                      </span>
                    </div>
                  </ElFormItem>

                  <ElFormItem
                    label="病种"
                    class="elep-rx-header__item elep-rx-header__item--disease min-w-0"
                  >
                    <ElepCatalogSelect
                      v-if="isDraftEditing"
                      v-model="diseaseCode"
                      kind="diagnosis"
                      class="elep-rx-header__disease-select"
                      :selected-label="diseaseName"
                      placeholder="慢病时选，可不填"
                      @select="handleDiseaseSelect"
                    />
                    <span
                      v-else
                      class="elep-rx-header__diag-name"
                      :class="{ 'is-empty': !diseaseName }"
                    >
                      {{ diseaseName || '—' }}
                    </span>
                  </ElFormItem>

                  <ElFormItem
                    label="天数"
                    class="elep-rx-header__item elep-rx-header__item--num shrink-0"
                  >
                    <ElInput v-if="isDraftEditing" v-model="form.valiDays" />
                    <span v-else>{{ form.valiDays || '—' }}</span>
                  </ElFormItem>
                </div>
              </ElForm>
            </div>

            <div v-if="isDraftEditing" class="elep-rx-panel__search">
              <div class="elep-rx-panel__search-input">
                <ElepCatalogSelect
                  kind="drug"
                  placeholder="输入药名 / 厂家 / 首字母后搜索"
                  @select="handleDrugSelect"
                />
              </div>
            </div>

            <div class="elep-rx-panel__table">
              <ElTable :data="form.drugLines" height="100%" size="small">
                <ElTableColumn align="center" label="#" prop="lineNo" width="48" />
                <ElTableColumn label="药品" min-width="180">
                  <template #default="{ row }">
                    <div class="elep-drug-name" :title="row.medListCodg">
                      <span class="elep-drug-name__main">{{ row.drugName || '—' }}</span>
                    </div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="规格" min-width="90" prop="drugSpec" show-overflow-tooltip />
                <ElTableColumn align="center" label="总量" width="88">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.drugCnt"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'drugCnt', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.drugCnt || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="单位" width="72">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.drugDosunt"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'drugDosunt', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.drugDosunt || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="用法" min-width="88">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.medcWayDscr"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'medcWayDscr', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.medcWayDscr || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="频次" min-width="88">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.usedFrquName"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'usedFrquName', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.usedFrquName || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="单次" width="72">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.sinDoscnt"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'sinDoscnt', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.sinDoscnt || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="次单位" width="72">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.sinDosunt"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'sinDosunt', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.sinDosunt || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="天数" width="64">
                  <template #default="{ row }">
                    <ElInput
                      v-if="isDraftEditing"
                      :model-value="row.medcDays"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'medcDays', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.medcDays || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="开始时间" width="176">
                  <template #default="{ row }">
                    <ElDatePicker
                      v-if="isDraftEditing"
                      :model-value="row.medcBegntime"
                      type="datetime"
                      format="YYYY-MM-DD HH:mm"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      placeholder="开始"
                      class="elep-drug-time"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'medcBegntime', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.medcBegntime || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn align="center" label="结束时间" width="176">
                  <template #default="{ row }">
                    <ElDatePicker
                      v-if="isDraftEditing"
                      :model-value="row.medcEndtime"
                      type="datetime"
                      format="YYYY-MM-DD HH:mm"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      placeholder="结束"
                      class="elep-drug-time"
                      @update:model-value="
                        (v) => patchDrugLine(row.lineNo, 'medcEndtime', String(v ?? ''))
                      "
                    />
                    <span v-else>{{ row.medcEndtime || '—' }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn v-if="isDraftEditing" align="center" label="操作" width="72">
                  <template #default="{ row }">
                    <ElButton link type="danger" @click="handleRemoveDrugLine(row.lineNo)">
                      删除
                    </ElButton>
                  </template>
                </ElTableColumn>
                <template #empty>
                  <ElEmpty :description="isDraftEditing ? '输入药名或拼音添加药品' : '暂无药品'" />
                </template>
              </ElTable>
            </div>
          </div>

          <footer class="elep-rx-panel__footer">
            <div class="elep-rx-panel__summary">{{ footerSummary }}</div>
          </footer>
        </div>
      </template>
    </div>
  </div>
</template>
