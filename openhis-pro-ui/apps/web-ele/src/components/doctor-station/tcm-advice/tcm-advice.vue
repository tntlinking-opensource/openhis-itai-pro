<script setup lang="ts">
import type {
  TcmHistoryTreeItem,
  TcmPrescriptionUi,
} from '#/components/doctor-station/tcm-advice/services/tcm-prescription.service';
import type { TcmSidebarTreeNode } from '#/components/doctor-station/tcm-advice/services/tcm-sidebar-tree.service';

import type { OrderTemplateBindingGroup } from '#/api/basic/order-template';
import type {
  DoctorStationAdviceCatalogRowDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { computed, nextTick, onActivated, onDeactivated, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElScrollbar,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTooltip,
  ElTree,
} from 'element-plus';

import {
  getOrderTemplateDetailApi,
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_WORKSTATION_INPATIENT,
  ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import * as outpatientTcmHttp from '#/api/doctor-station/tcm.http';
import * as outpatientAdviceHttp from '#/api/doctor-station/advice.http';
import * as inpatientTcmHttp from '#/api/inpatient/doctor-station/inpatient-doctor-station-tcm.http';
import * as inpatientAdviceHttp from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { DiagnosisPickerDialog } from '#/components/doctor-station/diagnosis';
import TcmHerbPanel from '#/components/doctor-station/tcm-advice/components/tcm-herb-panel.vue';
import TcmPrescriptionHeader from '#/components/doctor-station/tcm-advice/components/tcm-prescription-header.vue';
import TcmPrescriptionSummary from '#/components/doctor-station/tcm-advice/components/tcm-prescription-summary.vue';

import './tcm-advice-layout.css';
import { useAuthStore } from '#/store';
import { decimalAdd } from '#/utils/format/decimal-util';
import { ACTIVITY_DEFINITION_TABLE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import { mapCatalogRowToOption } from '#/views/outpatient/doctor-station/advice/services/advice-list';
import {
  buildTcmDosageInstructionSelectOptions,
  resolveTcmDosageInstructionLabel,
} from '#/components/doctor-station/tcm-advice/services/tcm-dosage-instruction';
import {
  applyTcmHistoryPrescriptionToDraft,
  TcmHistoryApplyError,
} from '#/components/doctor-station/tcm-advice/services/tcm-history-apply.service';
import {
  buildTcmMethodSelectOptions,
  buildTcmRateSelectOptions,
  resolveDefaultTcmMethodCode,
  resolveDefaultTcmRateCodeForTherapy,
} from '#/components/doctor-station/tcm-advice/services/tcm-method-code';
import {
  applyTcmOrderTemplateToPrescription,
  TcmOrderTemplateApplyError,
} from '#/components/doctor-station/tcm-advice/services/tcm-order-template-apply.service';
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
  findDuplicateHerbInPrescription,
  formatTcmSyndromeGroupOptionLabel,
  hasPersistableDraftChanges,
  INPATIENT_IMMEDIATE_RATE_CODE,
  isTcmLineRequestExceedCurrentLot,
  isTcmPrescriptionReadonly,
  loadHeaderFormFromPrescription,
  mapApiHeaderToUi,
  mapHistoryRowToTreeItem,
  resolvePrescriptionTabLabel,
  resolveTcmMethodLabel,
  resolveTcmRxStatusLabel,
  resolveTcmRxStatusTagType,
  syncHeaderFormToPrescription,
  TCM_DECOCTION_ACTIVITY_NAME,
  TCM_THERAPY_LONG_TERM,
  TCM_THERAPY_TEMPORARY,
  validatePrescriptionHeaderFields,
  validatePrescriptionNoDuplicateHerbs,
  validatePrescriptionStockExceed,
} from '#/components/doctor-station/tcm-advice/services/tcm-prescription.service';
import {
  buildTcmSidebarTree,
  resolveTcmSidebarLeafTooltip,
} from '#/components/doctor-station/tcm-advice/services/tcm-sidebar-tree.service';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { calcMoneyLineTotal } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildOrderTemplateProductNamesTooltip,
  fetchMergedDoctorStationOrderTemplateGroups,
  findOrderTemplateHeaderInGroups,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

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

const authStore = useAuthStore();

const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const sidebarKeyword = ref('');
const activePrescriptionKey = ref('');
const selectedSidebarNodeId = ref('');
const editingLineKey = ref<null | string>(null);
const selectedLineKeys = ref<Set<string>>(new Set());
const autofocusProductLineKey = ref<null | string>(null);
const templateApplyLoading = ref(false);
const diagnosisDialogVisible = ref(false);
/** 代煎中药目录单价（勾选代煎后计入合计） */
const decoctionUnitPrice = ref(0);
/** 朝阳：药材工作区（检索聚焦） */
const herbPanelRef = ref<{ bumpAddFocus?: () => void } | null>(null);

const diagnosisGroups = ref<TcmDiagnosisGroupRowDto[]>([]);
const tcmMethodCodeOptions = ref<TerminologyOptionDto[]>([]);
const medicationRateOptions = ref<TerminologyOptionDto[]>([]);
const dosageInstructionOptions = ref<TerminologyOptionDto[]>([]);
const historyItems = ref<TcmHistoryTreeItem[]>([]);
const templateGroups = ref<OrderTemplateBindingGroup>();
const prescriptions = ref<TcmPrescriptionUi[]>([]);
/** 饮片目录查询附带的发药库位缓存 */
const catalogDispenseLocationMap = ref<Record<string, string>>({});
/** 组套 hover Tooltip：药品名缓存（templateId → 文案） */
const templateProductTooltipCache = ref<Record<string, string>>({});
const templateProductTooltipPending = new Set<string>();

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

/** 单味特殊煎法下拉选项 */
const dosageInstructionSelectOptions = computed(() =>
  buildTcmDosageInstructionSelectOptions(dosageInstructionOptions.value),
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

/** 已签发且代煎中：可单独取消代煎费（仅门诊） */
const canVoidDecoctionCharge = computed(() => {
  if (isInpatientScope.value) {
    return false;
  }
  const rx = activePrescription.value;
  if (!rx || !isTcmPrescriptionReadonly(rx)) {
    return false;
  }
  if (!rx.sufferingFlag) {
    return false;
  }
  return Boolean(String(rx.prescriptionNo ?? '').trim());
});

const prescriptionStatusTagType = computed(() => {
  const status = String(
    activePrescription.value?.displayStatusEnum || activePrescription.value?.statusEnum || '',
  )
    .trim()
    .toUpperCase();
  // 草稿/待签发：用 warning 更醒目（系统默认 info+plain 过淡）
  if (status === 'DRAFT') {
    return 'warning';
  }
  return resolveTcmRxStatusTagType(
    activePrescription.value?.statusEnum,
    activePrescription.value?.displayStatusEnum,
  );
});

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

/** 载入方头表单时抑制 watch 同步 */
const headerFormLoadSuppressed = ref(false);

const prescriptionForm = reactive(loadHeaderFormFromPrescription(null));

/** 代煎费：勾选代煎时，目录单价 × 付数 */
const decoctionFeeAmount = computed(() => {
  if (!prescriptionForm.sufferingFlag) {
    return 0;
  }
  const unit = decoctionUnitPrice.value;
  if (!(unit > 0)) {
    return 0;
  }
  const doseQty = prescriptionForm.chineseHerbsDoseQuantity;
  if (doseQty === null || doseQty === undefined || doseQty <= 0) {
    return 0;
  }
  return calcMoneyLineTotal(unit, doseQty);
});

const totalPrescriptionAmount = computed(() => {
  const doseQty = prescriptionForm.chineseHerbsDoseQuantity;
  let herbTotal = singleDoseAmount.value;
  if (doseQty !== null && doseQty !== undefined && doseQty > 0) {
    herbTotal = calcMoneyLineTotal(singleDoseAmount.value, doseQty);
  }
  return decimalAdd(herbTotal, decoctionFeeAmount.value);
});

/** 住院新建处方默认长期；门诊无方头长临 */
function resolveDefaultTherapyEnumForNewPrescription(): string {
  return isInpatientScope.value ? TCM_THERAPY_LONG_TERM : '';
}

function createEmptyPrescriptionForScope(tcmSyndromeGroup = ''): TcmPrescriptionUi {
  const therapyEnum = resolveDefaultTherapyEnumForNewPrescription();
  // 用法默认水煎服(T01)，频次默认每日两次(BID)；住院临嘱仍优先 STAT
  const defaultMethodCode = resolveDefaultTcmMethodCode(tcmMethodCodeOptions.value);
  const defaultRateCode = resolveDefaultTcmRateCodeForTherapy(
    therapyEnum,
    medicationRateOptions.value,
  );
  return createEmptyPrescription(
    tcmSyndromeGroup,
    undefined,
    defaultMethodCode,
    defaultRateCode,
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
    { prop: 'code', label: '项目编码', width: 108 },
    { prop: 'name', label: '项目名称', width: 120 },
    { prop: 'manufacturer', label: '厂家', width: 200 },
    { prop: 'category', label: '类别', width: 64 },
    { prop: 'spec', label: '规格', width: 64 },
    { prop: 'chrgitmLvText', label: '医保等级', width: 72 },
    { prop: 'availableQuantity', label: '库存数量', width: 110 },
    { prop: 'price', label: '单价', width: 64 },
  ];
  if (isInpatientScope.value) {
    // 住院选品不展示库存列（与院点原口径一致）
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
    dosageInstructionOptions.value = initRes.dosageInstructionOptions ?? [];
    templateGroups.value = templateGroupRes;
    templateProductTooltipCache.value = {};
    templateProductTooltipPending.clear();
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
    void loadDecoctionUnitPrice(encounterId, patientId);
  },
  { manual: true },
);

/** 拉取「代煎中药」诊疗目录单价，供底栏费用展示 */
async function loadDecoctionUnitPrice(encounterId: string, patientId: string) {
  if (!encounterId || !patientId) {
    decoctionUnitPrice.value = 0;
    return;
  }
  const orgRaw = authStore.getSelectedOrganization();
  const organizationUnitId =
    orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
      ? String(orgRaw)
      : undefined;
  try {
    const pageFn = isInpatientScope.value
      ? inpatientAdviceHttp.httpPageInpatientDoctorStationAdviceCatalog
      : outpatientAdviceHttp.httpPageDoctorStationAdviceCatalog;
    const res = await pageFn({
      encounterId,
      patientId,
      organizationUnitId,
      keyword: TCM_DECOCTION_ACTIVITY_NAME,
      pageNo: 1,
      pageSize: 20,
      productTableNames: [ACTIVITY_DEFINITION_TABLE],
    });
    const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
    const matched = rawRows.find((row) => {
      const name = String(row.name ?? '').trim();
      return name === TCM_DECOCTION_ACTIVITY_NAME;
    });
    if (!matched) {
      decoctionUnitPrice.value = 0;
      return;
    }
    const option = mapCatalogRowToOption(matched);
    const price = Number(option.price ?? 0);
    decoctionUnitPrice.value = Number.isFinite(price) && price > 0 ? price : 0;
  } catch {
    decoctionUnitPrice.value = 0;
  }
}

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

/** 取消代煎费仅门诊 AppService 提供 */
const { loading: voidDecoctionLoading, run: runVoidTcmDecoctionCharge } = useRequest(
  (body) => outpatientTcmHttp.httpVoidTcmDecoctionCharge(body),
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
    voidDecoctionLoading.value ||
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
  // 服务端无方时保留一张空白页签，避免整排页签被清空
  if (mapped.length === 0) {
    const empty = createEmptyPrescriptionForScope(syndromeGroupOptions.value[0]?.value ?? '');
    prescriptions.value = [empty];
    activePrescriptionKey.value = empty.prescriptionGroupKey;
  } else {
    prescriptions.value = mapped;
    if (!mapped.some((rx) => rx.prescriptionGroupKey === activeKey)) {
      const firstRx = mapped[0];
      if (firstRx) {
        activePrescriptionKey.value = firstRx.prescriptionGroupKey;
      }
    }
  }
  loadActivePrescriptionForm();
}

/** 校验指定草稿方是否可暂存；非当前页失败时切到该页签并提示 */
function validatePrescriptionForSave(rx: TcmPrescriptionUi): boolean {
  const index = prescriptions.value.findIndex(
    (item) => item.prescriptionGroupKey === rx.prescriptionGroupKey,
  );
  const tabLabel = resolvePrescriptionTabLabel(rx, index >= 0 ? index : 0);
  const isActive = rx.prescriptionGroupKey === activePrescriptionKey.value;

  /** 失败时切到对应页签，便于用户就地改 */
  function focusFailedPrescription() {
    if (isActive) return;
    activePrescriptionKey.value = rx.prescriptionGroupKey;
    loadActivePrescriptionForm();
  }

  const headerError = validatePrescriptionHeaderFields(rx, {
    requireTherapy: isInpatientScope.value,
  });
  if (headerError) {
    focusFailedPrescription();
    ElMessage.warning(isActive ? headerError : `${tabLabel}：${headerError}`);
    return false;
  }
  const validLines = rx.lines.filter((line) => !line.pendingDelete && line.medicationDefinitionId);
  if (validLines.length === 0) {
    focusFailedPrescription();
    const msg = '请至少添加一味药材';
    ElMessage.warning(isActive ? msg : `${tabLabel}：${msg}`);
    return false;
  }
  const duplicateError = validatePrescriptionNoDuplicateHerbs(rx.lines);
  if (duplicateError) {
    focusFailedPrescription();
    ElMessage.warning(isActive ? duplicateError : `${tabLabel}：${duplicateError}`);
    return false;
  }
  const stockError = validatePrescriptionStockExceed(rx.lines, rx.chineseHerbsDoseQuantity ?? 1);
  if (stockError) {
    focusFailedPrescription();
    ElMessage.warning(isActive ? stockError : `${tabLabel}：${stockError}`);
    return false;
  }
  return true;
}

/** 暂存当前处方（签发前补存、组套套用后等显式场景） */
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
  applyInpatientTherapyRateCoupling();
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

/** 批量保存：一次提交所有有变更的草稿方 */
async function handleSaveAllDrafts() {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (autoSaveDraftInFlight.value) {
    return;
  }
  if (activePrescription.value && !isPrescriptionReadonly.value) {
    applyInpatientTherapyRateCoupling();
    syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
  }
  // 收集全部可编辑且有待提交变更的处方
  const targets = prescriptions.value.filter(
    (rx) => !isTcmPrescriptionReadonly(rx) && hasPersistableDraftChanges(rx),
  );
  if (targets.length === 0) {
    ElMessage.info('没有需要保存的变更');
    return;
  }
  for (const rx of targets) {
    if (!validatePrescriptionForSave(rx)) {
      return;
    }
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) {
    return;
  }
  const body = buildDraftSaveRequest(props.encounterId.trim(), props.patientId.trim(), targets);
  if (body.prescriptions.length === 0) {
    ElMessage.info('没有需要保存的变更');
    return;
  }
  autoSaveDraftInFlight.value = true;
  try {
    await runSaveDraft(body);
    await refreshPrescriptionsFromServer();
    const savedCount = body.prescriptions.length;
    ElMessage.success(savedCount > 1 ? `已保存 ${savedCount} 张处方` : '处方已保存');
  } finally {
    autoSaveDraftInFlight.value = false;
  }
}

/** 已成功拉数的就诊上下文键；用于判断 KeepAlive 切回是否仍为同一患者 */
const loadedTcmTabContextKey = ref('');

/** 当前就诊上下文键（encounterId|patientId） */
function resolveTcmTabContextKey(): string {
  return `${props.encounterId.trim()}|${props.patientId.trim()}`;
}

/** 是否存在未落库的本地方头/明细变更（切回页签时需保留，避免未暂存药材丢失） */
function hasUnsavedLocalPrescriptionDrafts(): boolean {
  return prescriptions.value.some((rx) => hasPersistableDraftChanges(rx));
}

/** 清空中药 Tab 内存态（无就诊上下文或换患者后失效） */
function clearTcmTabLocalState() {
  diagnosisGroups.value = [];
  tcmMethodCodeOptions.value = [];
  medicationRateOptions.value = [];
  dosageInstructionOptions.value = [];
  historyItems.value = [];
  templateGroups.value = undefined;
  templateProductTooltipCache.value = {};
  templateProductTooltipPending.clear();
  prescriptions.value = [];
  activePrescriptionKey.value = '';
  loadedTcmTabContextKey.value = '';
  loadActivePrescriptionForm();
}

/** 激活 / 工具栏刷新后拉数（接诊仅在写操作前触发，不在此自动写库） */
async function reload() {
  if (!contextReady.value) {
    clearTcmTabLocalState();
    return;
  }
  await runLoadTcmTab();
  loadedTcmTabContextKey.value = resolveTcmTabContextKey();
}

defineExpose({ reload });

/** KeepAlive 内仅激活页签拉数；换患者时由 watch 刷新当前可见 Tab */
const isTabActive = ref(false);

onActivated(() => {
  isTabActive.value = true;
  const contextKey = resolveTcmTabContextKey();
  // KeepAlive 切回同一就诊且仍有未保存草稿：保留内存态，勿用服务端列表冲掉未暂存药材
  if (
    contextKey &&
    contextKey === loadedTcmTabContextKey.value &&
    hasUnsavedLocalPrescriptionDrafts()
  ) {
    return;
  }
  void reload();
});

onDeactivated(() => {
  isTabActive.value = false;
});

watch(
  () => [props.encounterId, props.patientId],
  () => {
    if (!isTabActive.value) {
      // 非当前 Tab 换患者：使缓存上下文失效，下次激活强制重拉（避免脏草稿串患者）
      loadedTcmTabContextKey.value = '';
      return;
    }
    void reload();
  },
);

watch(
  prescriptionForm,
  () => {
    if (headerFormLoadSuppressed.value) return;
    if (!activePrescription.value || isPrescriptionReadonly.value) return;
    syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
  },
  { deep: true },
);

/** 付数变更后：已选药材超可用库存时即时提示 */
watch(
  () => prescriptionForm.chineseHerbsDoseQuantity,
  (doseQty) => {
    if (headerFormLoadSuppressed.value) return;
    if (!activePrescription.value || isPrescriptionReadonly.value) return;
    if (doseQty === null || doseQty === undefined || doseQty <= 0) return;
    const stockError = validatePrescriptionStockExceed(activePrescription.value.lines, doseQty);
    if (stockError) {
      ElMessage.warning(stockError);
    }
  },
);

/**
 * 载入当前处方头部表单。
 * @param options.preserveDirty 页签来回切换时保留未保存脏标记，避免切走再回来丢「待保存」状态
 */
function loadActivePrescriptionForm(options?: { preserveDirty?: boolean }) {
  headerFormLoadSuppressed.value = true;
  const form = loadHeaderFormFromPrescription(activePrescription.value);
  prescriptionForm.tcmSyndromeGroup = form.tcmSyndromeGroup;
  prescriptionForm.therapyEnum = form.therapyEnum ?? resolveDefaultTherapyEnumForNewPrescription();
  prescriptionForm.chineseHerbsDoseQuantity = form.chineseHerbsDoseQuantity;
  prescriptionForm.sufferingFlag = form.sufferingFlag;
  prescriptionForm.methodCode = form.methodCode;
  prescriptionForm.rateCode = form.rateCode;
  prescriptionForm.days = form.days;
  if (activePrescription.value && !options?.preserveDirty) {
    activePrescription.value.headerDirty = false;
  }
  void nextTick(() => {
    headerFormLoadSuppressed.value = false;
  });
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

/**
 * 切换到目标处方页签（v-model 可能已切走）。
 * 离开方的写回在 before-leave 完成，此处只加载目标方并保留其本地未保存状态。
 */
function handleSwitchPrescription(id: number | string) {
  activePrescriptionKey.value = String(id);
  editingLineKey.value = null;
  autofocusProductLineKey.value = null;
  selectedLineKeys.value = new Set();
  loadActivePrescriptionForm({ preserveDirty: true });
}

/**
 * 当前可编辑处方是否有未保存变更（先同步方头）。
 * 用于套用组套/历史等会覆盖当前方的操作提示，不再用于拦截页签切换。
 */
function hasUnsavedActiveDraftChanges(): boolean {
  if (!activePrescription.value || isPrescriptionReadonly.value) {
    return false;
  }
  syncActivePrescriptionHeaderFromForm();
  return hasPersistableDraftChanges(activePrescription.value);
}

/** 有未保存变更时提示并拦截（返回 false 表示不可继续） */
function ensureActiveDraftSavedOrWarn(actionLabel = '继续操作'): boolean {
  if (!hasUnsavedActiveDraftChanges()) {
    return true;
  }
  ElMessage.warning(`当前处方有未保存变更，请先点击「保存」后再${actionLabel}`);
  return false;
}

/**
 * 切换页签前：结束卡片编辑并把方头写回「离开的那一方」，始终允许切换。
 * ElTabs before-leave 时 v-model 尚未变更，activePrescription 仍是旧方。
 */
async function handleBeforeLeavePrescription(
  activeName: number | string,
  oldActiveName: number | string,
): Promise<boolean> {
  if (String(activeName) === String(oldActiveName)) {
    return true;
  }
  if (editingLineKey.value) {
    await handleHerbEditEnd(editingLineKey.value);
  }
  // 本地保留离开方的编辑，不强制先点「保存」
  syncActivePrescriptionHeaderFromForm();
  return true;
}

/** 新增处方页签并聚焦检索加药（当前方未保存也允许，先写回本地再开新方） */
async function handleAddPrescription() {
  if (editingLineKey.value) {
    await handleHerbEditEnd(editingLineKey.value);
  }
  syncActivePrescriptionHeaderFromForm();
  const defaultGroup = syndromeGroupOptions.value[0]?.value ?? '';
  const newRx = createEmptyPrescriptionForScope(defaultGroup);
  prescriptions.value.push(newRx);
  activePrescriptionKey.value = newRx.prescriptionGroupKey;
  loadActivePrescriptionForm();
  void nextTick(() => {
    herbPanelRef.value?.bumpAddFocus?.();
  });
}

/** 住院临嘱切换时联动频次（非默认值填充） */
function applyInpatientTherapyRateCoupling() {
  if (!isInpatientScope.value) return;
  const therapyCode = String(prescriptionForm.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (therapyCode === TCM_THERAPY_TEMPORARY) {
    prescriptionForm.rateCode = INPATIENT_IMMEDIATE_RATE_CODE;
    return;
  }
  if (therapyCode === TCM_THERAPY_LONG_TERM) {
    const current = String(prescriptionForm.rateCode ?? '')
      .trim()
      .toUpperCase();
    if (current === INPATIENT_IMMEDIATE_RATE_CODE) {
      prescriptionForm.rateCode = '';
    }
  }
}

/** 方头表单同步到当前处方（不自动保存，点保存/签发时再提交） */
function syncActivePrescriptionHeaderFromForm() {
  if (headerFormLoadSuppressed.value) return;
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  applyInpatientTherapyRateCoupling();
  syncHeaderFormToPrescription(activePrescription.value, prescriptionForm);
}

/** 打开添加诊断弹窗（默认中医病证） */
function handleOpenDiagnosisDialog() {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (isPrescriptionReadonly.value) {
    return;
  }
  diagnosisDialogVisible.value = true;
}

/** 诊断保存后刷新病证组下拉，并选中新开立组 */
async function handleDiagnosisPickerSaved() {
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  if (!encounterId || !patientId) {
    return;
  }
  const prevGroupKeys = new Set(
    diagnosisGroups.value
      .map((group) => String(group.tcmSyndromeGroup ?? '').trim())
      .filter(Boolean),
  );
  try {
    const res = await tcmHttp.value.httpListTcmDiagnosisGroups({ encounterId, patientId });
    diagnosisGroups.value = res.groups ?? [];
  } catch {
    ElMessage.error('刷新诊断列表失败，请稍后重试');
    return;
  }
  if (isPrescriptionReadonly.value) {
    return;
  }
  const newlyAdded = diagnosisGroups.value.find((group) => {
    const key = String(group.tcmSyndromeGroup ?? '').trim();
    return Boolean(key) && !prevGroupKeys.has(key);
  });
  const nextGroupKey = String(newlyAdded?.tcmSyndromeGroup ?? '').trim();
  if (nextGroupKey) {
    prescriptionForm.tcmSyndromeGroup = nextGroupKey;
    syncActivePrescriptionHeaderFromForm();
    return;
  }
  if (!String(prescriptionForm.tcmSyndromeGroup ?? '').trim()) {
    const first = syndromeGroupOptions.value[0]?.value;
    if (first) {
      prescriptionForm.tcmSyndromeGroup = first;
      syncActivePrescriptionHeaderFromForm();
    }
  }
}

/** 校验当前方可提交（含方头与至少一味药材） */
function validateActivePrescriptionForSave(): boolean {
  const rx = activePrescription.value;
  if (!rx) return false;
  return validatePrescriptionForSave(rx);
}

/** 写操作前确保已接诊 */
async function ensureReceptionBeforeWrite(): Promise<boolean> {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return ensureReceptionBeforeClinicalWork();
}

/** 卡片编辑结束：清理编辑态，空行本地移除（不自动保存） */
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
  }
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

/** 单独取消已签发方的代煎费（不撤回处方） */
async function handleVoidDecoctionCharge() {
  if (!canVoidDecoctionCharge.value || !activePrescription.value) {
    ElMessage.warning('当前处方不可取消代煎费');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认取消本方代煎中药费？将撤回未结算的代煎费用，并取消代煎勾选。',
      '取消代煎费',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  const ok = await ensureReceptionBeforeWrite();
  if (!ok) return;
  await runVoidTcmDecoctionCharge({
    encounterId: props.encounterId.trim(),
    patientId: props.patientId.trim(),
    prescriptionGroupKey: activePrescription.value.prescriptionGroupKey,
  });
  ElMessage.success('代煎费已取消');
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

/** 删除处方页签或整方草稿：仅一张时重置为空方并保留页签 */
async function handleDeletePrescription(targetKey?: string) {
  const removeKey = targetKey ?? activePrescriptionKey.value;
  if (!removeKey) return;
  const rx = prescriptions.value.find((item) => item.prescriptionGroupKey === removeKey);
  if (!rx) return;
  // 删除非当前页签前：先把当前方表单写回本地，避免丢编辑（不强制先保存）
  if (removeKey !== activePrescriptionKey.value) {
    if (editingLineKey.value) {
      await handleHerbEditEnd(editingLineKey.value);
    }
    syncActivePrescriptionHeaderFromForm();
  }
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
  const isLastPrescription = prescriptions.value.length <= 1;
  if (isLastPrescription) {
    // 唯一页签：本地重置为空方头+空明细，不摘掉页签
    const empty = createEmptyPrescriptionForScope(syndromeGroupOptions.value[0]?.value ?? '');
    prescriptions.value = [empty];
    activePrescriptionKey.value = empty.prescriptionGroupKey;
    editingLineKey.value = null;
    autofocusProductLineKey.value = null;
    selectedLineKeys.value = new Set();
    loadActivePrescriptionForm();
    if (hasPersistedLines) {
      ElMessage.success('处方已删除');
    }
    return;
  }
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

/** 列表版：检索选药后追加一行并写入目录信息（不立即暂存，便于聚焦改剂量） */
function handleAddHerbWithProduct(product: AdviceProductOption) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const sortNumber = activePrescription.value.lines.filter((l) => !l.pendingDelete).length + 1;
  const newLine = createEmptyLine(sortNumber);
  activePrescription.value.lines.push(newLine);
  handleUpdateHerbProduct(newLine.clientLineKey, product);
  // 未写入药品（如重复拦截）时回滚空行
  if (!newLine.medicationDefinitionId.trim()) {
    removeLineLocally(newLine.clientLineKey);
    return;
  }
  editingLineKey.value = null;
  autofocusProductLineKey.value = null;
}

function handleUpdateHerbProduct(lineKey: string, product: AdviceProductOption) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  const defId =
    preserveApiIdForRequest(product.productDefinitionId) ??
    preserveApiIdForRequest(product.id) ??
    '';
  // 同方已选过的药品不可再选（换药时排除当前行）
  if (defId) {
    const duplicate = findDuplicateHerbInPrescription(
      activePrescription.value.lines,
      defId,
      lineKey,
    );
    if (duplicate) {
      const name =
        String(duplicate.medicationName ?? '').trim() ||
        String(product.name ?? '').trim() ||
        '该药品';
      ElMessage.warning(`同一处方中不允许重复药品：${name}`);
      return;
    }
  }
  applyCatalogProductToLine(line, product);
  const locId = catalogDispenseLocationMap.value[defId];
  if (locId) {
    line.dispenseLocationId = locId;
  }
  // 选药后即时提示：无库存或请求量超可用库存
  const doseQty = activePrescription.value.chineseHerbsDoseQuantity ?? 1;
  const lotCap = line.catalogCurrentLotPackageQty;
  if (lotCap !== undefined && Number.isFinite(lotCap) && lotCap <= 0) {
    ElMessage.warning(`${String(product.name ?? '该药品')}无可用库存，请换选或待补货后再开`);
  } else if (isTcmLineRequestExceedCurrentLot(line, doseQty)) {
    ElMessage.warning(`${String(product.name ?? '该药品')}用量超过可用库存，请减少剂量/付数`);
  }
}

function handleHerbDoseChange(lineKey: string, dose: number) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  line.dose = dose;
  line.dirty = true;
  // 改剂量后即时提示是否超可用库存
  const doseQty = activePrescription.value.chineseHerbsDoseQuantity ?? 1;
  if (isTcmLineRequestExceedCurrentLot(line, doseQty)) {
    ElMessage.warning(
      `${String(line.medicationName ?? '该药品')}用量超过可用库存，请减少剂量/付数`,
    );
  }
}

/** 行内修改单味特殊煎法 */
function handleHerbDosageInstructionChange(lineKey: string, code: string) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  const line = activePrescription.value.lines.find((row) => row.clientLineKey === lineKey);
  if (!line) return;
  line.dosageInstructionCode = String(code ?? '').trim();
  line.dosageInstructionText =
    resolveTcmDosageInstructionLabel(line.dosageInstructionCode, dosageInstructionOptions.value) ||
    undefined;
  line.dirty = true;
}

function handleRemoveHerb(lineKey: string) {
  if (!activePrescription.value || isPrescriptionReadonly.value) return;
  removeLineLocally(lineKey);
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
  if (isInpatientScope.value && !String(rx.therapyEnum ?? '').trim()) {
    rx.therapyEnum = resolveDefaultTherapyEnumForNewPrescription();
    rx.headerDirty = true;
  }
}

/** 当前可编辑草稿方是否已有药材（有药再套用需二次确认） */
function hasActivePrescriptionHerbs(): boolean {
  const rx = activePrescription.value;
  if (!rx || isPrescriptionReadonly.value) {
    return false;
  }
  return rx.lines.some((line) => !line.pendingDelete && line.medicationDefinitionId);
}

/** 新建空白草稿方并清空行编辑态 */
function createBlankPrescriptionForApply(): null | TcmPrescriptionUi {
  handleAddPrescription();
  editingLineKey.value = null;
  autofocusProductLineKey.value = null;
  selectedLineKeys.value = new Set();
  return activePrescription.value;
}

/**
 * 组套/历史套用目标方：
 * - 当前方已有药材：询问是否新开；是→新方写方头；否→当前方仅增量明细；关弹窗→取消
 * - 空草稿：直接用当前方并写方头
 * - 无方/只读：新建并写方头
 */
async function resolvePrescriptionTargetForTemplateApply(): Promise<null | {
  applyHeader: boolean;
  createdNew: boolean;
  prescription: null | TcmPrescriptionUi;
}> {
  if (hasActivePrescriptionHerbs()) {
    try {
      await ElMessageBox.confirm(
        '当前处方已有药品，是否要新开一张处方？选择「否」将组套/历史内容增量追加到当前处方，不修改上方基础信息。',
        '提示',
        {
          confirmButtonText: '新开处方',
          cancelButtonText: '追加到当前',
          distinguishCancelAndClose: true,
          type: 'warning',
        },
      );
      return {
        prescription: createBlankPrescriptionForApply(),
        createdNew: true,
        applyHeader: true,
      };
    } catch (error) {
      // 否：追加到当前方，不改方头；点 X / Esc：取消
      if (error === 'cancel') {
        return {
          prescription: activePrescription.value,
          createdNew: false,
          applyHeader: false,
        };
      }
      return null;
    }
  }
  if (activePrescription.value && !isPrescriptionReadonly.value) {
    return {
      prescription: activePrescription.value,
      createdNew: false,
      applyHeader: true,
    };
  }
  return {
    prescription: createBlankPrescriptionForApply(),
    createdNew: true,
    applyHeader: true,
  };
}

/** 引用历史中药处方至当前/新建草稿方 */
async function handleApplyHistoryPrescription(data: TcmSidebarTreeNode) {
  if (data.nodeType !== 'history' || templateApplyLoading.value) {
    return;
  }
  if (!ensureActiveDraftSavedOrWarn('套用历史处方')) {
    return;
  }
  const item = historyItems.value.find((row) => row.id === data.id);
  if (!item) {
    ElMessage.warning('未找到历史处方');
    return;
  }
  const encounterId = preserveApiIdForRequest(props.encounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!encounterId || !patientId) {
    return;
  }
  const receptionOk = await ensureReceptionBeforeWrite();
  if (!receptionOk) {
    return;
  }
  const target = await resolvePrescriptionTargetForTemplateApply();
  if (!target) {
    return;
  }
  const { prescription: targetRx, createdNew: createdNewPrescription, applyHeader } = target;
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
    const result = await applyTcmHistoryPrescriptionToDraft({
      historyItem: item,
      encounterId,
      patientId,
      prescription: targetRx,
      applyHeader,
      catalogDispenseLocationMap: catalogDispenseLocationMap.value,
      currentSyndromeGroupValues: syndromeGroupOptions.value.map((opt) => opt.value),
      listTcmHerbalPrescriptions: tcmHttp.value.httpListTcmHerbalPrescriptions,
      pageTcmHerbalMedicationCatalog: tcmHttp.value.httpPageTcmHerbalMedicationCatalog,
    });
    // 写方头时补齐病证组等再 rx→表单；增量追加不改方头
    if (applyHeader) {
      ensurePrescriptionHeaderForTemplateApply(targetRx);
      loadActivePrescriptionForm();
    }
    const saved = await persistActivePrescriptionDraft({ silent: true });
    const label = item.treeLabel || item.summaryText || item.prescriptionNo;
    if (saved) {
      ElMessage.success(
        createdNewPrescription
          ? `已新建处方并引用「${label}」（${result.appliedLineCount} 味）`
          : applyHeader
            ? `已引用历史处方「${label}」（${result.appliedLineCount} 味）`
            : `已增量追加「${label}」（${result.appliedLineCount} 味），未修改方头`,
      );
    } else {
      ElMessage.warning(
        createdNewPrescription
          ? `已新建处方并写入历史方（${result.appliedLineCount} 味），请完善病证组等信息后保存`
          : applyHeader
            ? `历史方已写入当前处方（${result.appliedLineCount} 味），请完善病证组等信息后保存`
            : `历史方已增量追加（${result.appliedLineCount} 味），请完善后保存`,
      );
    }
  } catch (error) {
    selectedSidebarNodeId.value = '';
    if (error instanceof TcmHistoryApplyError) {
      ElMessage.error(error.message);
      return;
    }
    ElMessage.error('引用历史处方失败，请稍后重试');
  } finally {
    templateApplyLoading.value = false;
  }
}

/** 点击左侧树：组套套用 / 历史处方引用 */
async function handleSidebarTreeNodeClick(data: TcmSidebarTreeNode) {
  if (data.nodeType === 'history') {
    await handleApplyHistoryPrescription(data);
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
  if (!ensureActiveDraftSavedOrWarn('套用组套')) {
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
  const target = await resolvePrescriptionTargetForTemplateApply();
  if (!target) {
    return;
  }
  const { prescription: targetRx, createdNew: createdNewPrescription, applyHeader } = target;
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
      applyHeader,
      catalogDispenseLocationMap: catalogDispenseLocationMap.value,
      medicationRateOptions: medicationRateOptions.value,
      pageTcmHerbalMedicationCatalog: tcmHttp.value.httpPageTcmHerbalMedicationCatalog,
    });
    // 写方头时补齐病证组等再 rx→表单；增量追加不改方头
    if (applyHeader) {
      ensurePrescriptionHeaderForTemplateApply(targetRx);
      loadActivePrescriptionForm();
    }
    const saved = await persistActivePrescriptionDraft({ silent: true });
    const templateName = previewHeader.name ?? '';
    if (saved) {
      ElMessage.success(
        createdNewPrescription
          ? `已新建处方并套用组套「${templateName}」（${result.appliedLineCount} 味）`
          : applyHeader
            ? `已套用组套「${templateName}」（${result.appliedLineCount} 味）`
            : `已增量追加组套「${templateName}」（${result.appliedLineCount} 味），未修改方头`,
      );
    } else {
      ElMessage.warning(
        createdNewPrescription
          ? `已新建处方并写入组套（${result.appliedLineCount} 味），请完善病证组等信息后保存`
          : applyHeader
            ? `组套已写入当前处方（${result.appliedLineCount} 味），请完善病证组等信息后保存`
            : `组套已增量追加（${result.appliedLineCount} 味），请完善后保存`,
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
  templateProductTooltipCache.value = {};
  templateProductTooltipPending.clear();
}

/** 侧栏叶子 Tooltip 文案（组套优先用明细药品名缓存） */
function resolveSidebarLeafTooltip(data: TcmSidebarTreeNode): string {
  const templateId = preserveApiIdForRequest(data.templateId);
  return resolveTcmSidebarLeafTooltip(data, {
    templateProductSummary: templateId ? templateProductTooltipCache.value[templateId] : undefined,
  });
}

/** hover 组套行时拉取明细药品名并缓存 */
async function ensureTemplateProductTooltip(data: TcmSidebarTreeNode) {
  if (data.nodeType !== 'template') {
    return;
  }
  const templateId = preserveApiIdForRequest(data.templateId);
  if (
    !templateId ||
    templateProductTooltipCache.value[templateId] ||
    templateProductTooltipPending.has(templateId)
  ) {
    return;
  }
  templateProductTooltipPending.add(templateId);
  try {
    const detail = await getOrderTemplateDetailApi({ orderTemplateId: templateId });
    templateProductTooltipCache.value = {
      ...templateProductTooltipCache.value,
      [templateId]: buildOrderTemplateProductNamesTooltip(detail?.lines),
    };
  } catch {
    templateProductTooltipCache.value = {
      ...templateProductTooltipCache.value,
      [templateId]: '暂无药品',
    };
  } finally {
    templateProductTooltipPending.delete(templateId);
  }
}

/** 侧栏节点鼠标进入：组套补齐药品名 Tooltip */
function handleSidebarNodeMouseEnter(data: TcmSidebarTreeNode) {
  void ensureTemplateProductTooltip(data);
}

watch(sidebarKeyword, () => {
  scheduleSidebarSearchReload();
});
</script>

<template>
  <div class="tcm-advice-root grid h-full min-h-0">
    <aside
      class="tcm-advice-root__sidebar flex h-full min-h-0 flex-col border-r border-[var(--el-border-color-lighter)] bg-white p-2"
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
            <div class="tcm-tree-node-label" @mouseenter="handleSidebarNodeMouseEnter(data)">
              <ElTooltip
                v-if="data.nodeType === 'template' || data.nodeType === 'history'"
                :content="resolveSidebarLeafTooltip(data)"
                placement="right"
                effect="dark"
                :show-after="300"
                :hide-after="100"
              >
                <div class="w-full truncate text-sm text-[var(--el-text-color-regular)]">
                  {{ data.label }}
                </div>
              </ElTooltip>
              <div
                v-else
                class="w-full truncate text-sm font-medium text-[var(--el-text-color-primary)]"
              >
                {{ data.label }}
              </div>
            </div>
          </template>
        </ElTree>
      </ElScrollbar>
    </aside>

    <div class="tcm-advice-root__main flex h-full min-h-0 flex-col">
      <ElEmpty v-if="!contextReady" class="m-auto" description="请先选择患者" />

      <template v-else>
        <!-- 朝阳：页签与操作同一行，贴紧下方处方面板 -->
        <div class="tcm-advice-root__chrome flex min-w-0 flex-nowrap items-center gap-2">
          <ElTabs
            v-model="activePrescriptionKey"
            class="tcm-rx-tabs min-w-0 flex-1"
            closable
            :before-leave="handleBeforeLeavePrescription"
            @tab-change="handleSwitchPrescription"
            @tab-remove="handleTabRemove"
          >
            <ElTabPane
              v-for="(item, index) in prescriptions"
              :key="item.prescriptionGroupKey"
              :name="item.prescriptionGroupKey"
              :label="resolvePrescriptionTabLabel(item, index)"
            />
          </ElTabs>

          <ElTag
            class="tcm-rx-panel__status shrink-0"
            :type="prescriptionStatusTagType"
            size="small"
            effect="light"
            round
          >
            {{ prescriptionStatusLabel }}
          </ElTag>

          <div class="tcm-rx-panel__toolbar shrink-0">
            <ElButton type="primary" @click="handleAddPrescription">＋ 新增处方</ElButton>
            <ElButton type="success" :loading="mutationLoading" @click="handleSaveAllDrafts">
              保存
            </ElButton>
            <ElButton
              type="warning"
              :loading="mutationLoading"
              :disabled="isPrescriptionReadonly"
              @click="handleIssuePrescription"
            >
              签发
            </ElButton>
            <ElButton :loading="mutationLoading" @click="handleRevokePrescription"> 撤回 </ElButton>
            <ElButton
              v-if="isInpatientScope"
              type="warning"
              plain
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
              :loading="mutationLoading"
              @click="() => void handleDeletePrescription()"
            >
              删除
            </ElButton>
          </div>
        </div>

        <div v-loading="pageLoading" class="tcm-rx-panel">
          <div class="tcm-rx-panel__body">
            <div class="tcm-rx-panel__info">
              <TcmPrescriptionHeader
                v-model="prescriptionForm"
                :syndrome-group-options="syndromeGroupOptions"
                :method-code-options="methodCodeOptions"
                :rate-code-options="rateCodeOptions"
                :show-therapy-type="isInpatientScope"
                :disabled="isPrescriptionReadonly"
                @add-diagnosis="handleOpenDiagnosisDialog"
              />
            </div>

            <div class="tcm-rx-panel__search">
              <div class="tcm-rx-panel__search-host"></div>
            </div>

            <div class="tcm-rx-panel__table">
              <TcmHerbPanel
                ref="herbPanelRef"
                :lines="visibleLines"
                :chinese-herbs-dose-quantity="prescriptionForm.chineseHerbsDoseQuantity"
                :dosage-instruction-options="dosageInstructionSelectOptions"
                :product-columns="productColumns"
                :query-product-options="queryProductOptions"
                :readonly="isPrescriptionReadonly"
                @remove="handleRemoveHerb"
                @update-dose="handleHerbDoseChange"
                @update-dosage-instruction="handleHerbDosageInstructionChange"
                @add-product="handleAddHerbWithProduct"
              />
            </div>
          </div>

          <footer class="tcm-rx-panel__footer">
            <TcmPrescriptionSummary
              :herb-count="herbCount"
              :method-label="activeMethodLabel"
              :single-dose-amount="singleDoseAmount"
              :chinese-herbs-dose-quantity="prescriptionForm.chineseHerbsDoseQuantity"
              :decoction-fee-amount="decoctionFeeAmount"
              :decoction-fee-label="TCM_DECOCTION_ACTIVITY_NAME"
              :show-decoction-fee="prescriptionForm.sufferingFlag"
              :show-void-decoction-action="canVoidDecoctionCharge"
              :void-decoction-loading="voidDecoctionLoading"
              :total-amount="totalPrescriptionAmount"
              @void-decoction="handleVoidDecoctionCharge"
            />
          </footer>
        </div>
      </template>
    </div>
  </div>

  <DiagnosisPickerDialog
    v-model="diagnosisDialogVisible"
    default-medicine-type-code="2"
    :station-scope="stationScope"
    :encounter-id="encounterId"
    :patient-id="patientId"
    @saved="handleDiagnosisPickerSaved"
  />
</template>
