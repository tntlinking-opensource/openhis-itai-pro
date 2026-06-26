<script setup lang="ts">
import type { TcmHistoryTreeItem, TcmPrescriptionUi } from './services/tcm-prescription.service';
import type { TcmSidebarTreeNode } from './services/tcm-sidebar-tree.service';

import type { OrderTemplateBindingGroup } from '#/api/basic/order-template';
import type {
  DoctorStationAdviceCatalogRowDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { computed, onActivated, onDeactivated, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRadioButton,
  ElRadioGroup,
  ElScrollbar,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTooltip,
  ElTree,
} from 'element-plus';

import {
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_WORKSTATION_INPATIENT,
  ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import * as outpatientTcmHttp from '#/api/doctor-station/tcm.http';
import * as inpatientTcmHttp from '#/api/inpatient/doctor-station/inpatient-doctor-station-tcm.http';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalMul } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { mapCatalogRowToOption } from '#/views/outpatient/doctor-station/advice/services/advice-list';
import {
  fetchMergedDoctorStationOrderTemplateGroups,
  findOrderTemplateHeaderInGroups,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import TcmHerbAddCard from './components/tcm-herb-add-card.vue';
import TcmHerbCard from './components/tcm-herb-card.vue';
import TcmPrescriptionHeader from './components/tcm-prescription-header.vue';
import TcmPrescriptionSummary from './components/tcm-prescription-summary.vue';
import {
  buildTcmMethodSelectOptions,
  buildTcmRateSelectOptions,
  resolveDefaultTcmRateCodeForTherapy,
} from './services/tcm-method-code';
import {
  applyTcmOrderTemplateToPrescription,
  TcmOrderTemplateApplyError,
} from './services/tcm-order-template-apply.service';
import {
  applyCatalogProductToLine,
  buildCancelStopLongTermTcmRequest,
  buildDeleteDraftRequest,
  buildDraftSaveRequest,
  buildSignRequest,
  buildStopLongTermTcmRequest,
  buildWithdrawRequest,
  calcSingleDoseAmount,
  canCancelStopLongTermTcmPrescription,
  canStopLongTermTcmPrescription,
  createEmptyLine,
  createEmptyPrescription,
  formatTcmSyndromeGroupOptionLabel,
  hasPersistableDraftChanges,
  INPATIENT_IMMEDIATE_RATE_CODE,
  isTcmPrescriptionReadonly,
  loadHeaderFormFromPrescription,
  mapApiHeaderToUi,
  mapHistoryRowToTreeItem,
  resolveDefaultTcmMethodCode,
  resolveDefaultTcmRateCode,
  resolvePrescriptionTabLabel,
  resolveTcmMethodLabel,
  resolveTcmRxStatusLabel,
  resolveTcmRxStatusTagType,
  syncHeaderFormToPrescription,
  TCM_THERAPY_LONG_TERM,
  TCM_THERAPY_TEMPORARY,
} from './services/tcm-prescription.service';
import {
  buildTcmSidebarTree,
  formatTcmHistoryNodeTooltip,
  resolveTcmTemplateNodeTooltip,
} from './services/tcm-sidebar-tree.service';

defineOptions({
  name: 'DoctorStationTcmAdvice',
});

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** 门诊 / 住院医生站 */
    stationScope?: 'inpatient' | 'outpatient';
  }>(),
  {
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
  },
);

const isInpatientScope = computed(() => props.stationScope === 'inpatient');

const tcmHttp = computed(() => (isInpatientScope.value ? inpatientTcmHttp : outpatientTcmHttp));

const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const sidebarKeyword = ref('');
const activePrescriptionKey = ref('');
const selectedSidebarNodeId = ref('');
const editingLineKey = ref<null | string>(null);
const selectedLineKeys = ref<Set<string>>(new Set());
const autofocusProductLineKey = ref<null | string>(null);
const templateApplyLoading = ref(false);

const diagnosisGroups = ref<TcmDiagnosisGroupRowDto[]>([]);
const tcmMethodCodeOptions = ref<TerminologyOptionDto[]>([]);
const medicationRateOptions = ref<TerminologyOptionDto[]>([]);
const historyItems = ref<TcmHistoryTreeItem[]>([]);
const templateGroups = ref<OrderTemplateBindingGroup>();
const prescriptions = ref<TcmPrescriptionUi[]>([]);
/** 饮片目录查询附带的发药库位缓存 */
const catalogDispenseLocationMap = ref<Record<string, string>>({});

/** 左侧组套 + 历史处方树 */
const sidebarTreeData = computed<TcmSidebarTreeNode[]>(() =>
  buildTcmSidebarTree({
    templateGroups: templateGroups.value,
    historyItems: historyItems.value,
    searchKeyword: sidebarKeyword.value,
  }),
);

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

/** 病证组下拉选项 */
const syndromeGroupOptions = computed(() =>
  diagnosisGroups.value
    .filter((g) => (g.tcmSyndromeGroup ?? '').trim())
    .map((g) => ({
      value: String(g.tcmSyndromeGroup),
      label: formatTcmSyndromeGroupOptionLabel(g),
    })),
);

/** 中药用法下拉选项（仅 tcm_method_code 术语） */
const methodCodeOptions = computed(() =>
  buildTcmMethodSelectOptions(tcmMethodCodeOptions.value, prescriptionForm.methodCode),
);

/** 用药频次下拉选项 */
const rateCodeOptions = computed(() =>
  buildTcmRateSelectOptions(medicationRateOptions.value, prescriptionForm.rateCode),
);

/** 当前方用法展示名 */
const activeMethodLabel = computed(() =>
  resolveTcmMethodLabel(
    prescriptionForm.methodCode,
    tcmMethodCodeOptions.value,
    activePrescription.value?.methodName,
  ),
);

/** 当前激活处方 */
const activePrescription = computed(() => {
  return (
    prescriptions.value.find((item) => item.prescriptionGroupKey === activePrescriptionKey.value) ??
    null
  );
});

/** 当前处方是否只读 */
const isPrescriptionReadonly = computed(() => isTcmPrescriptionReadonly(activePrescription.value));

const prescriptionStatusTagType = computed(() =>
  resolveTcmRxStatusTagType(
    activePrescription.value?.statusEnum,
    activePrescription.value?.displayStatusEnum,
  ),
);

const prescriptionStatusLabel = computed(() =>
  resolveTcmRxStatusLabel(
    activePrescription.value?.statusEnum,
    activePrescription.value?.displayStatusEnumText,
    activePrescription.value?.displayStatusEnum,
  ),
);

/** 当前处方可见药材行 */
const visibleLines = computed(() => {
  return (activePrescription.value?.lines ?? []).filter((line) => !line.pendingDelete);
});

const herbCount = computed(() => visibleLines.value.length);

const singleDoseAmount = computed(() => calcSingleDoseAmount(visibleLines.value));

const totalPrescriptionAmount = computed(() => {
  const doseQty = prescriptionForm.chineseHerbsDoseQuantity || 1;
  return decimalMul(singleDoseAmount.value, doseQty);
});

const prescriptionForm = reactive(loadHeaderFormFromPrescription(null));

/** 住院新建处方默认长期；门诊无方头长临 */
function resolveDefaultTherapyEnumForNewPrescription(): string {
  return isInpatientScope.value ? TCM_THERAPY_LONG_TERM : '';
}

function createEmptyPrescriptionForScope(tcmSyndromeGroup = ''): TcmPrescriptionUi {
  const therapyEnum = resolveDefaultTherapyEnumForNewPrescription();
  return createEmptyPrescription(
    tcmSyndromeGroup,
    1,
    resolveDefaultTcmMethodCode(tcmMethodCodeOptions.value),
    resolveDefaultTcmRateCodeForTherapy(therapyEnum, medicationRateOptions.value),
    therapyEnum || undefined,
  );
}

const canStopLongTermActivePrescription = computed(() =>
  canStopLongTermTcmPrescription(activePrescription.value),
);

const canCancelStopLongTermActivePrescription = computed(() =>
  canCancelStopLongTermTcmPrescription(activePrescription.value),
);

const productColumns = computed(() => {
  const base = [
    { prop: 'code', label: '项目编码', width: 120 },
    { prop: 'name', label: '项目名称', width: 200 },
    { prop: 'category', label: '类别', width: 72 },
    { prop: 'spec', label: '单位', width: 80 },
    { prop: 'availableQuantity', label: '库存数量', width: 88 },
    { prop: 'price', label: '单价', width: 72 },
  ];
  if (isInpatientScope.value) {
    return base.filter((col) => col.prop !== 'availableQuantity');
  }
  return base;
});

const { loading: pageLoading, run: runLoadTcmTab } = useRequest(
  async () => {
    const encounterId = props.encounterId.trim();
    const patientId = props.patientId.trim();
    const keyword = sidebarKeyword.value.trim() || undefined;
    const api = tcmHttp.value;
    const [groupRes, rxRes, historyRes, initRes, templateGroupRes] = await Promise.all([
      api.httpListTcmDiagnosisGroups({ encounterId, patientId }),
      api.httpListTcmHerbalPrescriptions({
        encounterId,
        patientId,
      }),
      api.httpListPatientTcmHerbalPrescriptionHistory({
        patientId,
        currentEncounterId: encounterId,
        keyword,
      }),
      api.httpInitDoctorStationTcm({ encounterId, patientId }),
      fetchMergedDoctorStationOrderTemplateGroups({
        encounterId,
        patientId,
        templateKindEnum: ORDER_TEMPLATE_KIND_TCM,
        searchKey: keyword,
      }),
    ]);
    diagnosisGroups.value = groupRes.groups ?? [];
    tcmMethodCodeOptions.value = initRes.tcmMethodCodeOptions ?? [];
    medicationRateOptions.value = initRes.medicationRateOptions ?? [];
    templateGroups.value = templateGroupRes;
    const mapped = (rxRes.prescriptions ?? []).map((dto) => mapApiHeaderToUi(dto));
    prescriptions.value = mapped;
    historyItems.value = (historyRes.historyRows ?? []).map((row, index) =>
      mapHistoryRowToTreeItem(row, index),
    );
    if (mapped.length === 0) {
      const empty = createEmptyPrescriptionForScope(syndromeGroupOptions.value[0]?.value ?? '');
      prescriptions.value = [empty];
      activePrescriptionKey.value = empty.prescriptionGroupKey;
    } else if (!mapped.some((rx) => rx.prescriptionGroupKey === activePrescriptionKey.value)) {
      const firstRx = mapped[0];
      if (firstRx) {
        activePrescriptionKey.value = firstRx.prescriptionGroupKey;
      }
    }
    loadActivePrescriptionForm();
  },
  { manual: true },
);

const sidebarLoading = computed(() => pageLoading.value || templateApplyLoading.value);

const { loading: saveLoading, run: runSaveDraft } = useRequest(
  (body) => tcmHttp.value.httpSaveTcmHerbalPrescriptionDraftBatch(body),
  { manual: true, showErrorMessage: true },
);

const { loading: signLoading, run: runSignBatch } = useRequest(
  (body) => tcmHttp.value.httpSignTcmHerbalPrescriptionBatch(body),
  { manual: true, showErrorMessage: true },
);

const { loading: withdrawLoading, run: runWithdrawOrDelete } = useRequest(
  (body) => tcmHttp.value.httpWithdrawOrDeleteTcmHerbalPrescription(body),
  { manual: true, showErrorMessage: true },
);

const { loading: stopLongTermLoading, run: runStopLongTerm } = useRequest(
  (body) => inpatientTcmHttp.httpStopLongTermTcmHerbalPrescription(body),
  { manual: true, showErrorMessage: true },
);

const { loading: cancelStopLongTermLoading, run: runCancelStopLongTerm } = useRequest(
  (body) => inpatientTcmHttp.httpCancelStopLongTermTcmHerbalPrescription(body),
  { manual: true, showErrorMessage: true },
);

const mutationLoading = computed(
  () =>
    saveLoading.value ||
    signLoading.value ||
    withdrawLoading.value ||
    stopLongTermLoading.value ||
    cancelStopLongTermLoading.value,
);
const autoSaveDraftInFlight = ref(false);

/** 暂存成功后刷新处方列表（写操作后复用，避免整 Tab 重载） */
async function refreshPrescriptionsFromServer() {
  if (!contextReady.value) {
    return;
  }
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  const rxRes = await tcmHttp.value.httpListTcmHerbalPrescriptions({
    encounterId,
    patientId,
  });
  const mapped = (rxRes.prescriptions ?? []).map((dto) => mapApiHeaderToUi(dto));
  const activeKey = activePrescriptionKey.value;
  prescriptions.value = mapped;
  if (mapped.length > 0 && !mapped.some((rx) => rx.prescriptionGroupKey === activeKey)) {
    const firstRx = mapped[0];
    if (firstRx) {
      activePrescriptionKey.value = firstRx.prescriptionGroupKey;
    }
  }
  loadActivePrescriptionForm();
}

/** 暂存当前处方（卡片失焦等场景可静默调用） */
async function persistActivePrescriptionDraft(options?: {
  silent?: boolean;
  /** 签发前仅方头变更且行 id 已齐时可跳过，由签发后统一 list */
  skipRefresh?: boolean;
}) {
  if (!activePrescription.value || isPrescriptionReadonly.value) {
    return false;
  }
  if (autoSaveDraftInFlight.value) {
    return false;
  }
  syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
  if (!hasPersistableDraftChanges(activePrescription.value)) {
    return false;
  }
  if (!validateActivePrescriptionForSave()) {
    return false;
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) {
    return false;
  }
  const body = buildDraftSaveRequest(props.encounterId.trim(), props.patientId.trim(), [
    activePrescription.value,
  ]);
  if (body.prescriptions.length === 0) {
    return false;
  }
  autoSaveDraftInFlight.value = true;
  try {
    await runSaveDraft(body);
    if (!options?.skipRefresh) {
      await refreshPrescriptionsFromServer();
    } else if (activePrescription.value) {
      activePrescription.value.headerDirty = false;
      activePrescription.value.lines.forEach((line) => {
        line.dirty = false;
      });
    }
    if (!options?.silent) {
      ElMessage.success('处方已暂存');
    }
    return true;
  } finally {
    autoSaveDraftInFlight.value = false;
  }
}

/** 激活 / 工具栏刷新 / 写操作后统一拉数 */
async function reload() {
  if (!contextReady.value) {
    diagnosisGroups.value = [];
    tcmMethodCodeOptions.value = [];
    historyItems.value = [];
    templateGroups.value = undefined;
    prescriptions.value = [];
    activePrescriptionKey.value = '';
    loadActivePrescriptionForm();
    return;
  }
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) {
    return;
  }
  await runLoadTcmTab();
}

defineExpose({ reload });

/** KeepAlive 内仅激活页签拉数；换患者时由 watch 刷新当前可见 Tab */
const isTabActive = ref(false);

onActivated(() => {
  isTabActive.value = true;
  void reload();
});

onDeactivated(() => {
  isTabActive.value = false;
});

watch(
  () => [props.encounterId, props.patientId],
  () => {
    if (!isTabActive.value) return;
    void reload();
  },
);

watch(
  prescriptionForm,
  () => {
    if (!activePrescription.value || isPrescriptionReadonly.value) return;
    syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
  },
  { deep: true },
);

/** 载入当前处方头部表单 */
function loadActivePrescriptionForm() {
  const form = loadHeaderFormFromPrescription(activePrescription.value);
  prescriptionForm.tcmSyndromeGroup = form.tcmSyndromeGroup;
  prescriptionForm.therapyEnum = form.therapyEnum ?? resolveDefaultTherapyEnumForNewPrescription();
  prescriptionForm.chineseHerbsDoseQuantity = form.chineseHerbsDoseQuantity;
  prescriptionForm.sufferingFlag = form.sufferingFlag;
  prescriptionForm.methodCode = form.methodCode;
  prescriptionForm.rateCode = form.rateCode;
  prescriptionForm.days = form.days;
  prescriptionForm.remark = form.remark;
}

/** 分页检索饮片目录 */
async function queryProductOptions(params: { keyword?: string; pageNo: number; pageSize: number }) {
  if (!contextReady.value) {
    return { content: [] as AdviceProductOption[], totalElements: 0 };
  }
  const res = await tcmHttp.value.httpPageTcmHerbalMedicationCatalog({
    encounterId: props.encounterId.trim(),
    patientId: props.patientId.trim(),
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  });
  const { content: rawRows, totalCount } =
    parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  const content = rawRows.map((row) => {
    const option = mapCatalogRowToOption(row);
    const defId = preserveApiIdForRequest(option.productDefinitionId);
    const locId = preserveApiIdForRequest(row.defaultDispenseLocationId);
    if (defId && locId) {
      catalogDispenseLocationMap.value[defId] = locId;
    }
    return option;
  });
  return {
    content,
    totalElements: totalCount,
  };
}

/** 切换处方页签（先结束当前卡片编辑并自动暂存） */
async function handleSwitchPrescription(id: number | string) {
  if (editingLineKey.value) {
    await handleHerbEditEnd(editingLineKey.value);
  }
  activePrescriptionKey.value = String(id);
  editingLineKey.value = null;
  autofocusProductLineKey.value = null;
  selectedLineKeys.value = new Set();
  loadActivePrescriptionForm();
}

/** 新增处方页签 */
function handleAddPrescription() {
  const defaultGroup = syndromeGroupOptions.value[0]?.value ?? '';
  const newRx = createEmptyPrescriptionForScope(defaultGroup);
  prescriptions.value.push(newRx);
  activePrescriptionKey.value = newRx.prescriptionGroupKey;
  loadActivePrescriptionForm();
}

/** 校验当前方可提交 */
function validateActivePrescriptionForSave(): boolean {
  const rx = activePrescription.value;
  if (!rx) return false;
  if (!rx.tcmSyndromeGroup.trim()) {
    ElMessage.warning('请先选择绑定的病证组');
    return false;
  }
  if (isInpatientScope.value) {
    const therapy = String(rx.therapyEnum ?? prescriptionForm.therapyEnum ?? '').trim();
    if (!therapy) {
      ElMessage.warning('请选择医嘱类型（长期/临时）');
      return false;
    }
  }
  if (!String(rx.methodCode ?? '').trim()) {
    ElMessage.warning('请选择用法');
    return false;
  }
  if (!String(rx.rateCode ?? '').trim()) {
    ElMessage.warning('请选择用药频次');
    return false;
  }
  const validLines = rx.lines.filter((line) => !line.pendingDelete && line.medicationDefinitionId);
  if (validLines.length === 0) {
    ElMessage.warning('请至少添加一味药材');
    return false;
  }
  return true;
}

/** 写操作前确保已接诊 */
async function ensureReceptionBeforeWrite(): Promise<boolean> {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return ensureReceptionBeforeClinicalWork();
}

/** 卡片编辑结束：移除空行后自动暂存 */
async function handleHerbEditEnd(lineKey: string) {
  if (editingLineKey.value !== lineKey) return;
  editingLineKey.value = null;
  if (autofocusProductLineKey.value === lineKey) {
    autofocusProductLineKey.value = null;
  }
  if (!activePrescription.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (line && !line.medicationDefinitionId.trim()) {
    removeLineLocally(lineKey);
    return;
  }
  await persistActivePrescriptionDraft({ silent: true });
}

/** 签发当前处方：有未暂存变更时先保存，再签发 */
async function handleIssuePrescription() {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  if (!validateActivePrescriptionForSave()) return;
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) return;
  syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
  const rx = prescriptions.value.find(
    (item) => item.prescriptionGroupKey === activePrescriptionKey.value,
  );
  if (!rx) return;
  if (hasPersistableDraftChanges(activePrescription.value)) {
    const signableLines = rx.lines.filter(
      (line) => !line.pendingDelete && line.medicationDefinitionId,
    );
    const allLinesHaveRequestIds = signableLines.every((line) => Boolean(line.medicationRequestId));
    const saved = await persistActivePrescriptionDraft({
      silent: true,
      skipRefresh: allLinesHaveRequestIds,
    });
    if (!saved) return;
  }
  const signBody = buildSignRequest(props.encounterId.trim(), props.patientId.trim(), rx);
  if (signBody.signTargets.length === 0) {
    ElMessage.warning('没有可签发的药材行');
    return;
  }
  await runSignBatch(signBody);
  ElMessage.success('处方已签发');
  await refreshPrescriptionsFromServer();
}

/** 撤回当前处方 */
async function handleRevokePrescription() {
  if (!activePrescription.value) return;
  if (isTcmPrescriptionReadonly(activePrescription.value) === false) {
    ElMessage.warning('草稿处方请使用删除');
    return;
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) return;
  const body = buildWithdrawRequest(
    props.encounterId.trim(),
    props.patientId.trim(),
    activePrescription.value,
  );
  await runWithdrawOrDelete(body);
  ElMessage.success('处方已撤回');
  await refreshPrescriptionsFromServer();
}

/** 停嘱：住院长期已签发方 */
async function handleStopLongTermPrescription() {
  if (!activePrescription.value || !canStopLongTermActivePrescription.value) {
    ElMessage.warning('当前处方不可停嘱');
    return;
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) return;
  const body = buildStopLongTermTcmRequest(
    props.encounterId.trim(),
    props.patientId.trim(),
    activePrescription.value,
  );
  if (!body) {
    ElMessage.warning('缺少处方号，无法停嘱');
    return;
  }
  const res = await runStopLongTerm(body);
  if (res?.success === false) {
    ElMessage.error(String(res.message || '').trim() || '停嘱失败');
    return;
  }
  ElMessage.success(String(res?.message || '').trim() || '停嘱成功');
  await refreshPrescriptionsFromServer();
}

/** 取消停嘱：住院长期已停嘱方 */
async function handleCancelStopLongTermPrescription() {
  if (!activePrescription.value || !canCancelStopLongTermActivePrescription.value) {
    ElMessage.warning('当前处方不可取消停嘱');
    return;
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) return;
  const body = buildCancelStopLongTermTcmRequest(
    props.encounterId.trim(),
    props.patientId.trim(),
    activePrescription.value,
  );
  if (!body) {
    ElMessage.warning('缺少处方号，无法取消停嘱');
    return;
  }
  const res = await runCancelStopLongTerm(body);
  if (res?.success === false) {
    ElMessage.error(String(res.message || '').trim() || '取消停嘱失败');
    return;
  }
  ElMessage.success(String(res?.message || '').trim() || '取消停嘱成功');
  await refreshPrescriptionsFromServer();
}

/** 删除处方页签或整方草稿 */
async function handleDeletePrescription(targetKey?: string) {
  const removeKey = targetKey ?? activePrescriptionKey.value;
  if (!removeKey) return;
  const rx = prescriptions.value.find((item) => item.prescriptionGroupKey === removeKey);
  if (!rx) return;
  await ElMessageBox.confirm('确认删除当前处方？', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  });
  const hasPersistedLines = rx.lines.some((line) => line.medicationRequestId);
  if (hasPersistedLines) {
    const ok = await ensureReceptionBeforeWrite();
    if (!ok) return;
    const body = buildDeleteDraftRequest(props.encounterId.trim(), props.patientId.trim(), rx);
    await runWithdrawOrDelete(body);
  }
  if (prescriptions.value.length <= 1) {
    const empty = createEmptyPrescriptionForScope(syndromeGroupOptions.value[0]?.value ?? '');
    prescriptions.value = [empty];
    activePrescriptionKey.value = empty.prescriptionGroupKey;
  } else {
    const index = prescriptions.value.findIndex((item) => item.prescriptionGroupKey === removeKey);
    if (index !== -1) {
      prescriptions.value.splice(index, 1);
    }
    if (activePrescriptionKey.value === removeKey) {
      const fallback = prescriptions.value[0];
      if (fallback) {
        activePrescriptionKey.value = fallback.prescriptionGroupKey;
      }
    }
  }
  loadActivePrescriptionForm();
  if (hasPersistedLines) {
    ElMessage.success('处方已删除');
    await refreshPrescriptionsFromServer();
  }
}

function handleTabRemove(name: number | string) {
  void handleDeletePrescription(String(name));
}

function handleHerbAutofocusProductConsumed(lineKey: string) {
  if (autofocusProductLineKey.value === lineKey) {
    autofocusProductLineKey.value = null;
  }
}

function handleHerbSelect(lineKey: string) {
  if (isPrescriptionReadonly.value) return;
  const next = new Set(selectedLineKeys.value);
  if (next.has(lineKey)) {
    next.delete(lineKey);
  } else {
    next.add(lineKey);
  }
  selectedLineKeys.value = next;
}

function isHerbSelected(lineKey: string) {
  return selectedLineKeys.value.has(lineKey);
}

function handleClearHerbSelection() {
  selectedLineKeys.value = new Set();
}

function handleHerbEditStart(lineKey: string) {
  if (isPrescriptionReadonly.value) return;
  editingLineKey.value = lineKey;
}

function removeLineLocally(lineKey: string) {
  if (!activePrescription.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  if (line.medicationRequestId) {
    line.pendingDelete = true;
    line.dirty = true;
  } else {
    const index = activePrescription.value.lines.findIndex((row) => row.clientLineKey === lineKey);
    if (index !== -1) {
      activePrescription.value.lines.splice(index, 1);
    }
  }
  if (editingLineKey.value === lineKey) {
    editingLineKey.value = null;
  }
  const next = new Set(selectedLineKeys.value);
  next.delete(lineKey);
  selectedLineKeys.value = next;
}

function handleAddHerbCard() {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const sortNumber = activePrescription.value.lines.filter((l) => !l.pendingDelete).length + 1;
  const newLine = createEmptyLine(sortNumber);
  activePrescription.value.lines.push(newLine);
  editingLineKey.value = newLine.clientLineKey;
  autofocusProductLineKey.value = newLine.clientLineKey;
}

function handleUpdateHerbProduct(lineKey: string, product: AdviceProductOption) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  applyCatalogProductToLine(line, product);
  const defId = preserveApiIdForRequest(product.productDefinitionId) ?? '';
  const locId = catalogDispenseLocationMap.value[defId];
  if (locId) {
    line.dispenseLocationId = locId;
  }
}

function handleHerbDoseChange(lineKey: string, dose: number) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  line.dose = dose;
  line.dirty = true;
}

function handleRemoveHerb(lineKey: string) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  const hadPersisted = Boolean(line?.medicationRequestId);
  removeLineLocally(lineKey);
  if (hadPersisted) {
    void persistActivePrescriptionDraft({ silent: true });
  }
}

/** 套用组套前补齐方头必填项（病证组/用法/频次），便于自动暂存 */
function ensurePrescriptionHeaderForTemplateApply(rx: TcmPrescriptionUi) {
  if (!rx.tcmSyndromeGroup.trim()) {
    const firstGroup = syndromeGroupOptions.value[0]?.value;
    if (firstGroup) {
      rx.tcmSyndromeGroup = firstGroup;
      rx.headerDirty = true;
    }
  }
  if (!String(rx.methodCode ?? '').trim()) {
    rx.methodCode = resolveDefaultTcmMethodCode(tcmMethodCodeOptions.value);
    rx.headerDirty = true;
  }
  if (!String(rx.rateCode ?? '').trim()) {
    rx.rateCode = resolveDefaultTcmRateCode(medicationRateOptions.value);
    rx.headerDirty = true;
  }
  if (!rx.chineseHerbsDoseQuantity || rx.chineseHerbsDoseQuantity <= 0) {
    rx.chineseHerbsDoseQuantity = 1;
    rx.headerDirty = true;
  }
  if (!rx.days || rx.days <= 0) {
    rx.days = 1;
    rx.headerDirty = true;
  }
  if (isInpatientScope.value && !String(rx.therapyEnum ?? '').trim()) {
    rx.therapyEnum = resolveDefaultTherapyEnumForNewPrescription();
    rx.rateCode = resolveDefaultTcmRateCodeForTherapy(rx.therapyEnum, medicationRateOptions.value);
    rx.headerDirty = true;
  }
}

/** 组套套用目标方：当前方已签发或无方时新建草稿方 */
function preparePrescriptionForTemplateApply(): {
  createdNew: boolean;
  prescription: TcmPrescriptionUi | null;
} {
  if (activePrescription.value && !isPrescriptionReadonly.value) {
    return { prescription: activePrescription.value, createdNew: false };
  }
  handleAddPrescription();
  editingLineKey.value = null;
  autofocusProductLineKey.value = null;
  selectedLineKeys.value = new Set();
  return { prescription: activePrescription.value, createdNew: true };
}

/** 点击左侧树：组套套用 / 历史处方摘要 */
async function handleSidebarTreeNodeClick(data: TcmSidebarTreeNode) {
  if (data.nodeType === 'history') {
    const item = historyItems.value.find((row) => row.id === data.id);
    selectedSidebarNodeId.value = data.id;
    ElMessage.info(formatTcmHistoryNodeTooltip(item));
    return;
  }
  if (data.nodeType !== 'template') {
    return;
  }
  const header =
    data.header ??
    (data.templateId
      ? findOrderTemplateHeaderInGroups(templateGroups.value, data.templateId)
      : undefined);
  if (!header) {
    return;
  }
  if (templateApplyLoading.value) {
    return;
  }
  const templateKind = String(header.templateKindEnum ?? '')
    .trim()
    .toUpperCase();
  if (templateKind && templateKind !== ORDER_TEMPLATE_KIND_TCM) {
    ElMessage.warning('请选用中药组套');
    return;
  }
  const orderTemplateId = preserveApiIdForRequest(header.orderTemplateId);
  const encounterId = preserveApiIdForRequest(props.encounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!orderTemplateId || !encounterId || !patientId) {
    return;
  }
  const receptionOk = await ensureReceptionBeforeWrite();
  if (!receptionOk) {
    return;
  }
  const { prescription: targetRx, createdNew: createdNewPrescription } =
    preparePrescriptionForTemplateApply();
  if (!targetRx) {
    ElMessage.warning('无法创建新处方');
    return;
  }
  if (editingLineKey.value) {
    await handleHerbEditEnd(editingLineKey.value);
  }
  templateApplyLoading.value = true;
  selectedSidebarNodeId.value = data.id;
  try {
    const preview = await previewOrderTemplateApplyApi({
      orderTemplateId,
      encounterId,
      patientId,
      applyContextCode: ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
      workstationKindCode: isInpatientScope.value
        ? ORDER_TEMPLATE_WORKSTATION_INPATIENT
        : ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
    });
    const lines = preview?.lines ?? [];
    const previewHeader = preview?.header ?? header;
    const result = await applyTcmOrderTemplateToPrescription({
      header: previewHeader,
      lines,
      encounterId,
      patientId,
      prescription: targetRx,
      catalogDispenseLocationMap: catalogDispenseLocationMap.value,
      medicationRateOptions: medicationRateOptions.value,
      pageTcmHerbalMedicationCatalog: tcmHttp.value.httpPageTcmHerbalMedicationCatalog,
    });
    ensurePrescriptionHeaderForTemplateApply(targetRx);
    syncHeaderFormToPrescription(targetRx, prescriptionForm);
    loadActivePrescriptionForm();
    const saved = await persistActivePrescriptionDraft({ silent: true });
    const templateName = previewHeader.name ?? '';
    if (saved) {
      ElMessage.success(
        createdNewPrescription
          ? `已新建处方并套用组套「${templateName}」（${result.appliedLineCount} 味）`
          : `已套用组套「${templateName}」（${result.appliedLineCount} 味）`,
      );
    } else {
      ElMessage.warning(
        createdNewPrescription
          ? `已新建处方并写入组套（${result.appliedLineCount} 味），请完善病证组等信息后保存`
          : `组套已写入当前处方（${result.appliedLineCount} 味），请完善病证组等信息后保存`,
      );
    }
  } catch (error) {
    selectedSidebarNodeId.value = '';
    if (error instanceof TcmOrderTemplateApplyError) {
      ElMessage.error(error.message);
      return;
    }
    ElMessage.error('组套套用失败，请稍后重试');
  } finally {
    templateApplyLoading.value = false;
  }
}

let sidebarSearchTimer: ReturnType<typeof setTimeout> | undefined;

/** 侧栏搜索：防抖刷新组套列表与历史处方 */
function scheduleSidebarSearchReload() {
  clearTimeout(sidebarSearchTimer);
  sidebarSearchTimer = setTimeout(() => {
    if (!contextReady.value) {
      return;
    }
    void reloadSidebarSources();
  }, 300);
}

/** 刷新侧栏组套与历史（不重载整张处方工作区） */
async function reloadSidebarSources() {
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  const keyword = sidebarKeyword.value.trim() || undefined;
  const [historyRes, templateGroupRes] = await Promise.all([
    tcmHttp.value.httpListPatientTcmHerbalPrescriptionHistory({
      patientId,
      currentEncounterId: encounterId,
      keyword,
    }),
    fetchMergedDoctorStationOrderTemplateGroups({
      encounterId,
      patientId,
      templateKindEnum: ORDER_TEMPLATE_KIND_TCM,
      searchKey: keyword,
    }),
  ]);
  historyItems.value = (historyRes.historyRows ?? []).map((row, index) =>
    mapHistoryRowToTreeItem(row, index),
  );
  templateGroups.value = templateGroupRes;
}

watch(
  () => prescriptionForm.therapyEnum,
  (therapy) => {
    if (!isInpatientScope.value || !activePrescription.value || isPrescriptionReadonly.value) {
      return;
    }
    const therapyCode = String(therapy ?? '')
      .trim()
      .toUpperCase();
    if (therapyCode === TCM_THERAPY_TEMPORARY) {
      prescriptionForm.rateCode = INPATIENT_IMMEDIATE_RATE_CODE;
    } else if (therapyCode === TCM_THERAPY_LONG_TERM) {
      const current = String(prescriptionForm.rateCode ?? '')
        .trim()
        .toUpperCase();
      if (current === INPATIENT_IMMEDIATE_RATE_CODE) {
        prescriptionForm.rateCode = resolveDefaultTcmRateCode(medicationRateOptions.value);
      }
    }
  },
);

watch(sidebarKeyword, () => {
  scheduleSidebarSearchReload();
});
</script>

<template>
  <div class="tcm-advice-root grid h-full min-h-0 grid-cols-[240px_minmax(0,1fr)]">
    <aside
      class="tcm-advice-root__sidebar flex h-full min-h-0 flex-col border-r border-[var(--el-border-color-light)] p-1.5"
    >
      <ElInput v-model="sidebarKeyword" placeholder="搜索组套/历史处方" clearable class="mb-1.5" />

      <ElScrollbar v-loading="sidebarLoading" class="min-h-0 flex-1">
        <ElTree
          :data="sidebarTreeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :current-node-key="selectedSidebarNodeId"
          class="tcm-template-tree"
          @node-click="handleSidebarTreeNodeClick"
        >
          <template #default="{ data }">
            <div class="flex w-full items-center justify-between py-0.5">
              <ElTooltip
                v-if="data.nodeType === 'template' || data.nodeType === 'history'"
                :content="
                  data.nodeType === 'template'
                    ? resolveTcmTemplateNodeTooltip(data.header)
                    : data.tooltip || '暂无药品'
                "
                placement="right"
                effect="dark"
                :show-after="400"
                :hide-after="100"
              >
                <span class="truncate text-sm text-[var(--el-text-color-regular)]">
                  {{ data.label }}
                </span>
              </ElTooltip>
              <span v-else class="truncate text-sm font-medium text-[var(--el-text-color-primary)]">
                {{ data.label }}
              </span>
            </div>
          </template>
        </ElTree>
      </ElScrollbar>
    </aside>

    <div class="tcm-advice-root__main flex h-full min-h-0 flex-col p-2">
      <ElEmpty v-if="!contextReady" class="m-auto" description="请先选择患者" />

      <template v-else>
        <div class="mb-2">
          <ElTabs
            v-model="activePrescriptionKey"
            class="tcm-rx-tabs"
            closable
            addable
            @tab-change="handleSwitchPrescription"
            @tab-add="handleAddPrescription"
            @tab-remove="handleTabRemove"
          >
            <ElTabPane
              v-for="(item, index) in prescriptions"
              :key="item.prescriptionGroupKey"
              :name="item.prescriptionGroupKey"
              :label="resolvePrescriptionTabLabel(item, index)"
            />
            <template #add-icon>
              <ElButton type="primary" class="tcm-tab-add-btn">新增处方</ElButton>
            </template>
          </ElTabs>
        </div>

        <div class="tcm-advice-root__toolbar mb-2 flex flex-wrap items-center gap-2">
          <ElButton
            type="primary"
            :loading="mutationLoading"
            :disabled="isPrescriptionReadonly"
            @click="handleIssuePrescription"
          >
            签发
          </ElButton>
          <ElButton
            type="warning"
            plain
            :loading="mutationLoading"
            @click="handleRevokePrescription"
          >
            撤回
          </ElButton>
          <ElButton
            v-if="isInpatientScope"
            type="warning"
            :loading="mutationLoading"
            :disabled="!canStopLongTermActivePrescription"
            @click="handleStopLongTermPrescription"
          >
            停嘱
          </ElButton>
          <ElButton
            v-if="isInpatientScope"
            type="warning"
            plain
            :loading="mutationLoading"
            :disabled="!canCancelStopLongTermActivePrescription"
            @click="handleCancelStopLongTermPrescription"
          >
            取消停嘱
          </ElButton>
          <ElButton
            type="danger"
            plain
            :loading="mutationLoading"
            @click="() => void handleDeletePrescription()"
          >
            删除
          </ElButton>
        </div>

        <div
          v-loading="pageLoading"
          class="tcm-prescription-content border-border-base bg-background-card flex min-h-0 flex-1 flex-col overflow-hidden rounded border"
        >
          <div class="tcm-prescription-content__form px-4 py-3">
            <div class="mb-2 flex items-center gap-2">
              <SectionTitle class="!mb-0 shrink-0" title="处方信息" />
              <ElTag :type="prescriptionStatusTagType" size="small" effect="light">
                {{ prescriptionStatusLabel }}
              </ElTag>
            </div>
            <TcmPrescriptionHeader
              v-model="prescriptionForm"
              :syndrome-group-options="syndromeGroupOptions"
              :method-code-options="methodCodeOptions"
              :rate-code-options="rateCodeOptions"
              :show-therapy-type="isInpatientScope"
              :disabled="isPrescriptionReadonly"
            />
          </div>

          <div class="tcm-prescription-content__divider"></div>

          <ElScrollbar class="tcm-herb-panel__scroll min-h-0 flex-1">
            <div class="tcm-herb-grid p-3" @click.self="handleClearHerbSelection">
              <TcmHerbCard
                v-for="line in visibleLines"
                :key="line.clientLineKey"
                :item="line"
                :chinese-herbs-dose-quantity="prescriptionForm.chineseHerbsDoseQuantity || 1"
                :selected="isHerbSelected(line.clientLineKey)"
                :editing="editingLineKey === line.clientLineKey"
                :autofocus-product="autofocusProductLineKey === line.clientLineKey"
                :product-columns="productColumns"
                :query-product-options="queryProductOptions"
                :readonly="isPrescriptionReadonly"
                @select="handleHerbSelect(line.clientLineKey)"
                @edit-start="handleHerbEditStart(line.clientLineKey)"
                @edit-end="handleHerbEditEnd(line.clientLineKey)"
                @autofocus-product-consumed="handleHerbAutofocusProductConsumed(line.clientLineKey)"
                @remove="handleRemoveHerb(line.clientLineKey)"
                @product-select="handleUpdateHerbProduct(line.clientLineKey, $event)"
                @update-dose="handleHerbDoseChange(line.clientLineKey, $event)"
              />
              <TcmHerbAddCard :disabled="isPrescriptionReadonly" @add="handleAddHerbCard" />
            </div>
          </ElScrollbar>

          <TcmPrescriptionSummary
            :herb-count="herbCount"
            :method-label="activeMethodLabel"
            :single-dose-amount="singleDoseAmount"
            :chinese-herbs-dose-quantity="prescriptionForm.chineseHerbsDoseQuantity || 1"
            :total-amount="totalPrescriptionAmount"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tcm-template-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 4px;
}

.tcm-template-tree :deep(.el-tree-node__content:hover) {
  background-color: var(--el-fill-color-light);
}

.tcm-template-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--el-color-primary-light-9);
}

.tcm-rx-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.tcm-rx-tabs :deep(.el-tabs__new-tab) {
  width: auto;
  min-width: auto;
  border: 0;
  padding: 0;
}

.tcm-tab-add-btn {
  margin: 0;
  padding: 0 8px;
}

.tcm-prescription-content {
  display: flex;
  flex-direction: column;
}

.tcm-prescription-content__form {
  background: var(--el-fill-color-lighter);
}

.tcm-prescription-content__divider {
  flex-shrink: 0;
  border-top: 1px solid var(--el-border-color-light);
}

.tcm-herb-panel__scroll {
  flex: 1;
  min-height: 0;
  background: var(--el-fill-color-lighter);
}

.tcm-herb-panel__scroll :deep(.el-scrollbar__view) {
  background: var(--el-fill-color-lighter);
}

.tcm-herb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  align-content: start;
}
</style>
