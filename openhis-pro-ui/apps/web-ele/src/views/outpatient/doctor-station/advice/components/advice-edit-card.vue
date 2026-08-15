<script setup lang="ts">
import type {
  AdviceDrugItem,
  AdviceDrugPatchPayload,
  AdviceEditForm,
  AdviceProductOption,
  ProductColumn,
  ProductQueryParams,
} from '../services/advice.types';
import type { WesternDrugFormHandlers } from './advice-western-drug-form-panel.vue';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisConfirmPayload, DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { DiagnosisPickerDialog } from '#/components/doctor-station/diagnosis';
import {
  buildEncounterDiagnosisBindingPatch,
  mapEncounterRowToDiagnosisItem,
  parseEncounterDiagnosisIds,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  readEncounterDiagnosisSession,
  rememberEncounterDiagnosisSession,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-session';
import { fetchEncounterDiagnosisDisplayList } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';

import { useAdviceDrugFormKeyboard } from '../composables/use-advice-drug-form-keyboard';
import { calcAdviceTotalFee } from '../composables/use-advice-drug-list';
import { useAdviceFrequencyTotal } from '../composables/use-advice-frequency-total';
import {
  formatNonMedicationCollapsedSummary,
  formatNonMedicationCollapsedTooltip,
  formatWesternCollapsedSummary,
  formatWesternCollapsedTooltip,
} from '../services/advice-collapsed-text';
import {
  buildDeviceProductSelectPatch,
  buildTcmProductSelectPatch,
  buildWesternProductSelectPatch,
  createAdviceDrugItem,
  inferDoseBandConvertK,
  parseOptionalNumberInput,
  pickCatalogChrgitmLvPatch,
  resolveDefaultDrugRowDiagnosisBinding,
} from '../services/advice-edit';
import {
  formatMoneyStrYuan,
  formatUnitPriceYuan,
  resolveContractForChrgitmLv,
} from '../services/advice-format';
import { isAdviceCatalogZeroStock } from '../services/advice-list';
import { validateMedicationGroupSharedFields } from '../services/advice-medication-draft';
import { inferAdviceLineEditorKind, validateWesternEditLineItem } from '../services/advice-row';
import {
  convertTotalAmountWhenUnitChanges,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
  roundMedicationSplitQty,
} from '../services/medication-unit-bands';
import AdviceDeviceServiceFormPanel from './advice-device-service-form-panel.vue';
import AdviceOrderSelect from './advice-order-select.vue';
import AdviceWesternDrugFormPanel from './advice-western-drug-form-panel.vue';

defineOptions({ name: 'AdviceEditCard' });

const props = withDefaults(
  defineProps<{
    defaultFeeNature?: string;
    encounterId?: string;
    feeNatureOptions?: TerminologyOptionDto[];
    initialGroup?: AdviceDrugItem[];
    /** 当前就诊开立会话内上次选中的诊断 ids（不跨刷新） */
    lastSelectedEncounterDiagnosisIds?: string[];
    medicationRateOptions?: TerminologyOptionDto[];
    medicationRouteOptions?: TerminologyOptionDto[];
    modelValue: AdviceEditForm;
    orderSelectExpandNonce?: number;
    orderSelectFocusNonce?: number;
    patientId?: string;
    productColumns: ProductColumn[];
    queryProductOptions: (params: ProductQueryParams) => Promise<{
      content: AdviceProductOption[];
      totalElements: number;
    }>;
    /** 顶栏「保存」提交中（由父级写接口期间传入） */
    saveLoading?: boolean;
    tcmGroupNo?: string;
    validateRow?: (item: AdviceDrugItem) => boolean;
    variant?: 'tcm' | 'western';
  }>(),
  {
    defaultFeeNature: '',
    feeNatureOptions: () => [],
    encounterId: '',
    lastSelectedEncounterDiagnosisIds: () => [],
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
    orderSelectExpandNonce: 0,
    orderSelectFocusNonce: 0,
    patientId: '',
    saveLoading: false,
    tcmGroupNo: '',
    variant: 'western',
  },
);

const emit = defineEmits<{
  close: [];
  deleteDrug: [index: number, item: AdviceDrugItem];
  editorDirtyChange: [value: boolean];
  /** 医生点选诊断后回写会话记忆（供下一条医嘱默认） */
  'update:lastSelectedEncounterDiagnosisIds': [value: string[]];
  saveGroup: [value: AdviceDrugItem[], encounterDiagnosisRows: EncounterDiagnosisRowDto[]];
}>();

const patientDiagnoses = defineModel<DiagnosisItem[]>('patientDiagnoses', { default: () => [] });

const isTcm = computed(() => props.variant === 'tcm');
const { productColumns, queryProductOptions } = props;

const encounterDiagnosisSnapshot = ref<EncounterDiagnosisRowDto[]>([]);

async function loadEncounterDiagnosisOptions() {
  const encounterId = props.encounterId?.trim() || '';
  const patientId = props.patientId?.trim() || '';
  if (!encounterId || !patientId) {
    encounterDiagnosisSnapshot.value = [];
    patientDiagnoses.value = [];
    return;
  }
  try {
    const displayList = await fetchEncounterDiagnosisDisplayList(
      { encounterId, patientId },
      'outpatient',
    );
    encounterDiagnosisSnapshot.value = displayList;
    patientDiagnoses.value = displayList
      .map((row) => mapEncounterRowToDiagnosisItem(row))
      .filter((item): item is DiagnosisItem => item !== null);
    applyMainDiagnosisDefaultToDrugRowsAfterLoad();
  } catch {
    encounterDiagnosisSnapshot.value = [];
    patientDiagnoses.value = [];
  }
}

async function onDiagnosisPickerSaved() {
  await loadEncounterDiagnosisOptions();
}

/** 选择诊断弹窗内删除已开立诊断后：刷新候选项并清理各行绑定 */
async function onEncounterDiagnosisDeleted(deletedId: string) {
  const id = String(deletedId ?? '').trim();
  await loadEncounterDiagnosisOptions();
  if (!id) return;
  drugItems.value = drugItems.value.map((item) => {
    const ids = (item.encounterDiagnosisIds ?? []).filter((x) => String(x ?? '').trim() !== id);
    if (ids.length === (item.encounterDiagnosisIds ?? []).length) {
      return item;
    }
    const names = ids
      .map((eid) => {
        const hit = patientDiagnoses.value.find(
          (d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim() === eid,
        );
        return String(hit?.name ?? '').trim();
      })
      .filter(Boolean);
    return {
      ...item,
      encounterDiagnosisIds: ids,
      diagnosis: names.join('、'),
    };
  });
}

/** 选择诊断弹窗内切换主诊后：仅刷新候选项（不改行绑定） */
async function onEncounterDiagnosisMainSwitched() {
  await loadEncounterDiagnosisOptions();
}

/** 「选择诊断」打开时面板已拉取最新列表：同步候选项与保存用快照 */
function onEncounterDiagnosisOptionsRefreshed(payload: {
  displayRows: EncounterDiagnosisRowDto[];
  items: DiagnosisItem[];
}) {
  encounterDiagnosisSnapshot.value = payload.displayRows;
  patientDiagnoses.value = payload.items;
}

watch(
  () => [props.encounterId, props.patientId],
  () => {
    void loadEncounterDiagnosisOptions();
  },
  { immediate: true },
);

const diagnosisDialogVisible = ref(false);
const diagnosisDialogTargetIndex = ref<null | number>(null);
const diagnosisDialogOpenByAuto = ref(false);
const diagnosisDialogConfirmed = ref(false);
const diagnosisAutoPromptMuted = ref(false);

const createDrugItem = (seed?: Partial<AdviceDrugItem>): AdviceDrugItem =>
  createAdviceDrugItem(seed, {
    feeNatureOptions: props.feeNatureOptions ?? [],
    medicationRateOptions: props.medicationRateOptions ?? [],
    medicationRouteOptions: props.medicationRouteOptions ?? [],
  });

const toDrugItem = (item: AdviceDrugItem): AdviceDrugItem => ({
  ...inferDoseBandConvertK(item),
  expanded: Boolean(item.expanded),
});

const getInitialDrugItems = () => {
  if (props.initialGroup && props.initialGroup.length > 0) {
    return props.initialGroup.map((item, index) => toDrugItem({ ...item, expanded: index === 0 }));
  }
  /** 西药空白卡首张：生成会话组号，后续「添加药品」沿用同一 groupId（暂存写入 medicationPayload） */
  if (props.variant !== 'tcm') {
    return [createDrugItem({ ...props.modelValue, groupId: String(Date.now()) })];
  }
  return [createDrugItem(props.modelValue)];
};

const drugItems = ref<AdviceDrugItem[]>(getInitialDrugItems());

/** 输液会话：同卡多组，仅「结束并保存」落库 */
const infusionMode = ref(false);
/** 当前操作组（组内加药写入此 groupId） */
const activeGroupId = ref(String(drugItems.value[0]?.groupId ?? '').trim() || String(Date.now()));

/** 输液会话内组数（用于顶栏展示） */
const infusionGroupCount = computed(() => {
  const ids = new Set<string>();
  for (const item of drugItems.value) {
    const gid = String(item.groupId ?? '').trim();
    if (gid) {
      ids.add(gid);
    }
  }
  return ids.size;
});

/** 将会话内上次诊断选择回写给父级，并写入医嘱/申请单共用记忆。 */
function rememberEncounterDiagnosisSelection(ids: string[] | undefined) {
  const parsed = parseEncounterDiagnosisIds(ids);
  if (parsed.length === 0) return;
  rememberEncounterDiagnosisSession(props.encounterId, parsed);
  emit('update:lastSelectedEncounterDiagnosisIds', parsed);
}

/** 按「已选 > 上次 > 唯一诊断」解析行诊断绑定。 */
function resolveRowDiagnosisBinding(
  item: Pick<AdviceDrugItem, 'diagnosis' | 'encounterDiagnosisIds'>,
) {
  const propLast = parseEncounterDiagnosisIds(props.lastSelectedEncounterDiagnosisIds);
  const lastSelectedIds =
    propLast.length > 0 ? propLast : readEncounterDiagnosisSession(props.encounterId);
  return resolveDefaultDrugRowDiagnosisBinding(
    item.encounterDiagnosisIds,
    item.diagnosis,
    encounterDiagnosisSnapshot.value,
    lastSelectedIds,
  );
}

/** 就诊诊断加载后：空白行按上次选择 / 唯一诊断默认（耗材行跳过）。 */
function applyMainDiagnosisDefaultToDrugRowsAfterLoad() {
  const snapshot = encounterDiagnosisSnapshot.value;
  if (snapshot.length === 0) return;
  drugItems.value = drugItems.value.map((item) => {
    // 耗材不绑定就诊诊断
    if (inferAdviceLineEditorKind(item) === 'DEVICE') {
      return item;
    }
    const binding = resolveRowDiagnosisBinding(item);
    rememberEncounterDiagnosisSelection(binding.encounterDiagnosisIds);
    return {
      ...item,
      ...binding,
    };
  });
}

/** 保存前规范化各行诊断绑定（诊疗/药品；耗材跳过）。 */
function ensureAdviceRowsDiagnosisBeforeSave() {
  const snapshot = encounterDiagnosisSnapshot.value;
  if (snapshot.length === 0) return;
  drugItems.value = drugItems.value.map((item) => {
    if (inferAdviceLineEditorKind(item) === 'DEVICE') {
      return item;
    }
    return {
      ...item,
      ...resolveRowDiagnosisBinding(item),
    };
  });
}

const setField = <K extends keyof AdviceDrugItem>(
  index: number,
  key: K,
  value: AdviceDrugItem[K],
) => {
  const next = [...drugItems.value];
  const current = next[index];
  if (!current) return;
  next[index] = { ...current, [key]: value };
  drugItems.value = next;
  if (key === 'encounterDiagnosisIds') {
    rememberEncounterDiagnosisSelection(value as AdviceDrugItem['encounterDiagnosisIds']);
  }
};

function mergeDrugRow(index: number, patch: Partial<AdviceDrugItem>) {
  const next = [...drugItems.value];
  const cur = next[index];
  if (!cur) return;
  next[index] = { ...cur, ...patch };
  drugItems.value = next;
}

const {
  autoCalcTotalAmount,
  debouncedRefreshFrequencyExecutionCount,
  debouncedEnsureFrequencyCountAndRecalcTotal,
  invalidateRowFrequencyRefresh,
  markRowTotalAmountManualOverride,
  refreshRowFrequencyExecutionCount,
  recalcRowTotalAmountForTargetUnit,
} = useAdviceFrequencyTotal({
  drugItems,
  isTcm,
  setField,
  patchRow: mergeDrugRow,
});

const adviceCardBodyRef = ref<HTMLElement | null>(null);
const formAreaRef = ref<HTMLElement | null>(null);
const headerSaveButtonRef = ref<InstanceType<typeof ElButton> | null>(null);

const { handleDrugFormKeydown, setFormScope } = useAdviceDrugFormKeyboard(
  formAreaRef,
  headerSaveButtonRef,
);

async function focusWesternPrimarySingleDose() {
  if (isTcm.value) return;
  await nextTick();
  const root = adviceCardBodyRef.value;
  const input = root?.querySelector?.(
    '.western-single-dose-primary input, .simple-line-qty-primary input',
  ) as HTMLInputElement | null;
  input?.focus?.();
}

function applySplitDoseToDoseSide(index: number, singleDose: number) {
  const item = drugItems.value[index];
  const k = item?.doseBandConvertK;
  if (k === undefined || !Number.isFinite(k) || k <= 0) return;
  const currentDose = Number(item.doseConvertRatio);
  // 剂量侧改完会同步左侧片数；若片数只是剂量反算结果，禁止再用两位片数反推剂量（否则 35.625→0.75→35.63）
  if (Number.isFinite(currentDose) && currentDose > 0) {
    const splitFromDose = roundMedicationSplitQty(currentDose / k);
    if (roundMedicationSplitQty(singleDose) === splitFromDose) {
      return;
    }
  }
  // 剂量侧落库口径保留 6 位；界面另按 3 位展示
  setField(index, 'doseConvertRatio', roundMedicationQty(singleDose * k));
}

function applyDoseQtyToSplitSide(index: number, doseQty: number) {
  const item = drugItems.value[index];
  const k = item?.doseBandConvertK;
  if (k === undefined || !Number.isFinite(k) || k <= 0) return;
  setField(index, 'singleDose', roundMedicationSplitQty(doseQty / k));
}

function handleTotalAmountUnitSelect(index: number, value: unknown) {
  const nu = String(value ?? '').trim();
  const item = drugItems.value[index];
  if (!item) return;
  const converted = convertTotalAmountWhenUnitChanges({
    oldUnit: String(item.totalAmountUnit ?? ''),
    newUnit: nu,
    bands: item.unitBands,
    splitCode: String(item.totalUnitSplitCode ?? ''),
    salesCode: String(item.totalUnitSalesCode ?? ''),
    splitToSalesK: item.totalAmountSplitToSalesK,
    currentAmount: Number(item.totalAmount ?? Number.NaN),
  });
  if (converted !== undefined) {
    setField(index, 'totalAmount', converted);
  }
  setField(index, 'totalAmountUnit', nu);
  const linePrice = resolveAdviceLineUnitPrice({
    bands: item.unitBands,
    catalogUnitPrice: item.catalogUnitPrice,
    totalAmountUnit: nu,
  });
  if (linePrice !== undefined) {
    setField(index, 'selectedDrugPrice', linePrice);
  }
}

function handleTotalAmountFocus(index: number) {
  markRowTotalAmountManualOverride(index);
}

function handleTotalAmountManualInput(index: number, value: unknown) {
  invalidateRowFrequencyRefresh(index);
  mergeDrugRow(index, {
    totalAmountManualOverride: true,
    totalAmount: parseOptionalNumberInput(value) as AdviceDrugItem['totalAmount'],
  });
}

function handleTotalAmountUnitManualSelect(index: number, value: unknown) {
  const nu = String(value ?? '').trim();
  const item = drugItems.value[index];
  if (!item || nu === String(item.totalAmountUnit ?? '').trim()) return;

  if (autoCalcTotalAmount.value && !item.totalAmountManualOverride) {
    recalcRowTotalAmountForTargetUnit(index, nu);
    return;
  }

  markRowTotalAmountManualOverride(index);
  handleTotalAmountUnitSelect(index, value);
}

const clearMedicationOnlyFields = (): Partial<AdviceDrugItem> => ({
  singleDose: undefined,
  doseConvertRatio: undefined,
  doseBandConvertK: undefined,
  catalogDose: undefined,
  catalogMinUnit: undefined,
  catalogMaxUnit: undefined,
  dosePresetKind: undefined,
  frequency: '',
  usage: '',
  medicationDays: undefined,
  skinTest: false,
  pharmacy: '',
  doseMainUnit: '',
  doseSubUnit: '',
  unitBands: undefined,
  catalogUnitPrice: undefined,
  chrgitmLv: undefined,
  chrgitmLvText: undefined,
  totalUnitPackageCode: undefined,
  totalUnitPackageName: undefined,
  totalUnitSplitCode: undefined,
  totalUnitSplitName: undefined,
  totalUnitSalesCode: undefined,
  totalUnitSalesName: undefined,
  totalAmountSplitToSalesK: undefined,
  partAttributeEnum: undefined,
  frequencyExecutionCount: undefined,
  totalAmountManualOverride: false,
  tcmSpecialDecoction: '',
  serviceQuantity: undefined,
});

const handleProductSelect = (index: number, row: AdviceProductOption) => {
  if (isAdviceCatalogZeroStock(row)) {
    ElMessage.warning(`${String(row.name ?? '该药品')}库存不足，无法开立`);
    return;
  }
  const prev = drugItems.value[index];
  if (!prev) return;
  const tbl = String(row.productTableName ?? '')
    .trim()
    .toLowerCase();

  // 输液会话：仅开药品（诊疗/耗材走绑定带出）
  if (infusionMode.value && !isTcm.value) {
    if (tbl === 'wkf_activity_definition' || tbl === 'adm_device_definition') {
      ElMessage.warning('输液模式下不可开立诊疗或耗材');
      return;
    }
  }

  const base: Partial<AdviceDrugItem> = {
    /** 与目录下拉 `mapCatalogRowToOption` 的复合 id 一致，保存时组包取定义主键 */
    selectedId: String(row.id ?? '').trim(),
    selectedDrugName: row.name || '',
    selectedDrugSpec: row.spec || '',
    productTableName: String(row.productTableName ?? '').trim() || undefined,
  };
  if (isTcm.value) {
    mergeDrugRow(index, {
      ...base,
      selectedDrugPrice: row.price ?? '',
      ...buildTcmProductSelectPatch(row),
      ...pickCatalogChrgitmLvPatch(row),
    });
  } else if (tbl === 'wkf_activity_definition') {
    const orgId = String(row.defaultPerformOrgId ?? '').trim();
    const orgName = String(row.defaultPerformOrgName ?? '').trim();
    const uCode = String(row.defaultUnitCode ?? '').trim();
    const uName = String(row.defaultUnitName ?? '').trim() || uCode;
    if (!uCode) {
      ElMessage.warning('该诊疗项目未维护销售/计价单位，请先在「基础数据 > 诊疗目录」维护后再开立');
    }
    const diagnosisBinding = resolveRowDiagnosisBinding(prev);
    mergeDrugRow(index, {
      ...base,
      selectedDrugPrice: row.price ?? '',
      ...clearMedicationOnlyFields(),
      ...pickCatalogChrgitmLvPatch(row),
      ...diagnosisBinding,
      performOrgId: orgId || undefined,
      performOrgName: orgName || undefined,
      serviceQuantity: 1,
      totalAmount: 1,
      totalAmountUnit: uCode,
      totalUnitSalesCode: uCode,
      totalUnitSalesName: uName,
    });
    rememberEncounterDiagnosisSelection(diagnosisBinding.encounterDiagnosisIds);
  } else if (tbl === 'adm_device_definition') {
    // 耗材不绑定诊断；数量单位默认拆零，不取目录包装单位
    mergeDrugRow(index, {
      ...base,
      ...clearMedicationOnlyFields(),
      ...buildDeviceProductSelectPatch(row),
      diagnosis: '',
      encounterDiagnosisIds: [],
      performOrgId: undefined,
      performOrgName: undefined,
      totalAmount: 1,
    });
  } else {
    const diagnosisBinding = resolveRowDiagnosisBinding(prev);
    mergeDrugRow(index, {
      ...base,
      ...diagnosisBinding,
      totalAmountManualOverride: false,
      ...buildWesternProductSelectPatch(prev, row),
      // 输液会话内保持当前组号
      ...(infusionMode.value
        ? { groupId: String(prev.groupId ?? '').trim() || activeGroupId.value }
        : {}),
    });
    rememberEncounterDiagnosisSelection(diagnosisBinding.encounterDiagnosisIds);
  }
  drugItems.value = drugItems.value.map((item, itemIndex) => ({
    ...item,
    expanded: itemIndex === index,
  }));
  let mergedRow = drugItems.value[index];
  // 医保等级=自费：强制费用性质为自费合同，避免仍挂医保
  if (mergedRow && !isTcm.value) {
    const contractSel = resolveContractForChrgitmLv(
      props.feeNatureOptions ?? [],
      mergedRow.chrgitmLv,
      mergedRow.contractId,
    );
    if (
      contractSel.contractId &&
      (contractSel.contractId !== String(mergedRow.contractId ?? '').trim() ||
        contractSel.feeNature !== String(mergedRow.feeNature ?? '').trim())
    ) {
      mergedRow = {
        ...mergedRow,
        contractId: contractSel.contractId || undefined,
        feeNature: contractSel.feeNature,
      };
      drugItems.value[index] = mergedRow;
    }
  }
  if (infusionMode.value && mergedRow) {
    const gid = String(mergedRow.groupId ?? '').trim();
    if (gid) {
      activeGroupId.value = gid;
    }
  }
  if (
    !isTcm.value &&
    autoCalcTotalAmount.value &&
    mergedRow &&
    inferAdviceLineEditorKind(mergedRow) === 'MEDICATION'
  ) {
    void nextTick(() => refreshRowFrequencyExecutionCount(index));
  }
  void focusWesternPrimarySingleDose();
};

function validateEditorLineItem(item: AdviceDrugItem) {
  if (isTcm.value) {
    return props.validateRow ? props.validateRow(item) : false;
  }
  return validateWesternEditLineItem(item);
}

function assertRowValid(item: AdviceDrugItem) {
  return validateEditorLineItem(item);
}

const collapsedTextOpts = computed(() => ({
  medicationRateOptions: props.medicationRateOptions ?? [],
  medicationRouteOptions: props.medicationRouteOptions ?? [],
}));

const getCollapsedSummary = (item: AdviceDrugItem) => {
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE' || kind === 'SERVICE') {
    return formatNonMedicationCollapsedSummary(item, kind);
  }
  return formatWesternCollapsedSummary(item, collapsedTextOpts.value);
};

const getCollapsedTooltip = (item: AdviceDrugItem) => {
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE' || kind === 'SERVICE') {
    const mid = formatNonMedicationCollapsedSummary(item, kind);
    const full = `${item.diagnosis || '-'} | ${item.feeNature} | ${mid} | 总金额：${formatMoneyStrYuan(calcAdviceTotalFee(item))}`;
    return formatNonMedicationCollapsedTooltip(item, kind, full);
  }
  return formatWesternCollapsedTooltip(item, collapsedTextOpts.value);
};

const handleEditDrug = (index: number) => {
  if (!drugItems.value[index]) return;
  const currentEditingIndex = drugItems.value.findIndex((item) => item.expanded);
  if (currentEditingIndex !== -1 && currentEditingIndex !== index) {
    const editingItem = drugItems.value[currentEditingIndex];
    if (editingItem && !assertRowValid(editingItem)) {
      ElMessage.warning(`请先完善第${currentEditingIndex + 1}条医嘱的必填项`);
      return;
    }
  }
  drugItems.value = drugItems.value.map((item, itemIndex) => ({
    ...item,
    expanded: itemIndex === index,
  }));
  if (infusionMode.value) {
    const gid = String(drugItems.value[index]?.groupId ?? '').trim();
    if (gid) {
      activeGroupId.value = gid;
    }
  }
  void focusWesternPrimarySingleDose();
};

const handleDeleteDrug = (index: number) => {
  const item = drugItems.value[index];
  if (!item) {
    return;
  }
  const requestId = String(item.medicationRequestId ?? '').trim();
  if (requestId) {
    emit('deleteDrug', index, item);
    return;
  }
  if (drugItems.value.length <= 1) {
    ElMessage.warning('至少保留一条药品记录');
    return;
  }
  const deletedExpanded = drugItems.value[index]?.expanded ?? false;
  let next = drugItems.value.filter((_, itemIndex) => itemIndex !== index);
  if (deletedExpanded && next.length > 0) {
    next = next.map((it, i) => ({ ...it, expanded: i === 0 }));
  }
  drugItems.value = next;
};

function openDiagnosisDialog(index: number, byAuto = false) {
  diagnosisDialogTargetIndex.value = index;
  diagnosisDialogOpenByAuto.value = byAuto;
  diagnosisDialogConfirmed.value = false;
  if (!byAuto) {
    diagnosisAutoPromptMuted.value = false;
  }
  diagnosisDialogVisible.value = true;
}

function handleDiagnosisFocus(index: number) {
  const current = drugItems.value[index];
  if (!current) return;
  const hasBound =
    (current.encounterDiagnosisIds?.length ?? 0) > 0 ||
    Boolean(String(current.diagnosis ?? '').trim());
  if (patientDiagnoses.value.length === 0 && !hasBound && !diagnosisAutoPromptMuted.value) {
    openDiagnosisDialog(index, true);
  }
}

function handleDiagnosisConfirm(payload: DiagnosisConfirmPayload) {
  const targetIndex = diagnosisDialogTargetIndex.value;
  if (targetIndex === null) return;
  diagnosisDialogConfirmed.value = true;
  const exists = patientDiagnoses.value.some((item) => item.id === payload.selected.id);
  if (!exists) {
    patientDiagnoses.value = [...patientDiagnoses.value, payload.selected];
  }
  const current = drugItems.value[targetIndex];
  const eid = String(payload.selected.encounterDiagnosisId ?? '').trim();
  if (eid) {
    const nextIds = [...(current?.encounterDiagnosisIds ?? []), eid];
    const patch = buildEncounterDiagnosisBindingPatch(nextIds, encounterDiagnosisSnapshot.value);
    mergeDrugRow(targetIndex, patch);
    rememberEncounterDiagnosisSelection(patch.encounterDiagnosisIds);
  } else if (payload.selected.name) {
    setField(targetIndex, 'diagnosis', payload.selected.name);
  }
  diagnosisAutoPromptMuted.value = false;
  if (payload.saveAsPersonal || payload.saveAsDepartment || payload.saveAsCommon) {
    ElMessage.success('诊断已保存为常用');
  }
}

function handleDiagnosisDialogVisibleChange(visible: boolean) {
  diagnosisDialogVisible.value = visible;
  if (visible) return;
  if (diagnosisDialogOpenByAuto.value && !diagnosisDialogConfirmed.value) {
    diagnosisAutoPromptMuted.value = true;
    ElMessage.info('当前患者尚未开立诊断，可点击诊断字段右侧“新增”直接开诊断');
  }
  diagnosisDialogOpenByAuto.value = false;
  diagnosisDialogConfirmed.value = false;
  diagnosisDialogTargetIndex.value = null;
}

/** 生成新的就诊内组号（毫秒 + 序号，避免同毫秒碰撞） */
function createNewGroupId(seq = 0): string {
  return String(Date.now() + seq);
}

/** 卡内是否有已选品行 */
function hasSelectedDrugContent(): boolean {
  return drugItems.value.some((item) => Boolean(String(item.selectedId ?? '').trim()));
}

/** 输液模式：第 index 行是否为新组首行（用于组头） */
function isInfusionGroupStart(index: number): boolean {
  if (!infusionMode.value || isTcm.value) {
    return false;
  }
  const curr = String(drugItems.value[index]?.groupId ?? '').trim();
  if (!curr) {
    return false;
  }
  if (index <= 0) {
    return true;
  }
  return String(drugItems.value[index - 1]?.groupId ?? '').trim() !== curr;
}

/** 输液组头文案：第 N 组 */
function resolveInfusionGroupHeaderLabel(index: number): string {
  const seen = new Set<string>();
  let ordinal = 0;
  for (let i = 0; i <= index; i += 1) {
    const gid = String(drugItems.value[i]?.groupId ?? '').trim();
    if (!gid || seen.has(gid)) {
      continue;
    }
    seen.add(gid);
    ordinal += 1;
  }
  return `第 ${ordinal} 组`;
}

/** 进入输液模式：支持同卡多组，结束时一次保存 */
async function handleEnterInfusionMode() {
  if (isTcm.value) {
    return;
  }
  if (infusionMode.value) {
    return;
  }
  if (hasSelectedDrugContent()) {
    try {
      await ElMessageBox.confirm('进入输液将清空当前编辑卡内容，是否继续？', '进入输液', {
        type: 'warning',
        confirmButtonText: '进入',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }
  }
  const gid = createNewGroupId();
  infusionMode.value = true;
  activeGroupId.value = gid;
  drugItems.value = [
    createDrugItem({
      ...props.modelValue,
      groupId: gid,
      expanded: true,
    }),
  ];
  ElMessage.success('已进入输液，请开立后点击「结束并保存」');
}

/** 退出输液模式（可丢弃未保存内容） */
async function handleExitInfusionMode() {
  if (!infusionMode.value) {
    return;
  }
  if (hasSelectedDrugContent()) {
    try {
      await ElMessageBox.confirm('退出将丢弃本卡未保存的输液内容，是否继续？', '退出输液', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }
  }
  const gid = createNewGroupId();
  infusionMode.value = false;
  activeGroupId.value = gid;
  drugItems.value = [
    createDrugItem({
      ...props.modelValue,
      groupId: gid,
      expanded: true,
    }),
  ];
}

/** 向当前组追加一行（输液模式「组内加药」） */
async function handleAddWithinInfusionGroup() {
  await commitDrugFormPendingInputs();
  if (patientDiagnoses.value.length === 0) {
    ElMessage.warning('请先开立诊断后再新增医嘱，可在诊断字段右侧点击“新增”直接开诊断');
    openDiagnosisDialog(drugItems.value.length - 1);
    return;
  }

  // 当前组最后一行须已选品且校验通过
  const groupId = activeGroupId.value || createNewGroupId();
  activeGroupId.value = groupId;
  let lastInGroupIndex = -1;
  for (let i = drugItems.value.length - 1; i >= 0; i -= 1) {
    if (String(drugItems.value[i]?.groupId ?? '').trim() === groupId) {
      lastInGroupIndex = i;
      break;
    }
  }
  const lastItem =
    lastInGroupIndex >= 0 ? drugItems.value[lastInGroupIndex] : drugItems.value.at(-1);
  if (!lastItem || !String(lastItem.selectedId ?? '').trim()) {
    ElMessage.warning('请先完善当前组药品后再追加');
    return;
  }
  if (!assertRowValid(lastItem)) {
    ElMessage.warning('请先完善上一条医嘱的必填项后再添加');
    return;
  }

  rememberEncounterDiagnosisSelection(lastItem.encounterDiagnosisIds);
  const diagnosisBinding = resolveRowDiagnosisBinding({
    diagnosis: '',
    encounterDiagnosisIds: [],
  });
  const nextItems = drugItems.value.map((item, index) => ({
    ...item,
    expanded: false,
    ...(index === lastInGroupIndex ? { groupId } : {}),
  }));
  const insertAt = lastInGroupIndex >= 0 ? lastInGroupIndex + 1 : nextItems.length;
  nextItems.splice(insertAt, 0, {
    ...createDrugItem({
      orderType: lastItem.orderType,
      diagnosis: diagnosisBinding.diagnosis,
      encounterDiagnosisIds: diagnosisBinding.encounterDiagnosisIds,
      contractId: lastItem.contractId,
      feeNature: lastItem.feeNature,
      skinTest: lastItem.skinTest,
      pharmacy: lastItem.pharmacy,
      usage: lastItem.usage,
      frequency: lastItem.frequency,
      medicationDays: lastItem.medicationDays,
      groupId,
    }),
    expanded: true,
  });
  drugItems.value = nextItems;
}

/** 新开一组：新 groupId + 空行 */
async function handleStartNewInfusionGroup() {
  await commitDrugFormPendingInputs();
  const filled = drugItems.value.filter((item) => Boolean(String(item.selectedId ?? '').trim()));
  if (filled.length === 0) {
    ElMessage.warning('请先开立当前组药品后再新开一组');
    return;
  }
  const invalidIndex = filled.findIndex((item) => !assertRowValid(item));
  if (invalidIndex !== -1) {
    ElMessage.warning(`请先完善第${invalidIndex + 1}条医嘱的必填项`);
    return;
  }
  const groupValidationError = validateMedicationGroupSharedFields(drugItems.value);
  if (groupValidationError) {
    ElMessage.warning(groupValidationError);
    return;
  }

  const lastFilled = filled[filled.length - 1];
  rememberEncounterDiagnosisSelection(lastFilled?.encounterDiagnosisIds);
  const diagnosisBinding = resolveRowDiagnosisBinding({
    diagnosis: '',
    encounterDiagnosisIds: [],
  });
  const gid = createNewGroupId(1);
  activeGroupId.value = gid;
  drugItems.value = [
    ...drugItems.value.map((item) => ({ ...item, expanded: false })),
    {
      ...createDrugItem({
        orderType: lastFilled?.orderType,
        diagnosis: diagnosisBinding.diagnosis,
        encounterDiagnosisIds: diagnosisBinding.encounterDiagnosisIds,
        contractId: lastFilled?.contractId,
        feeNature: lastFilled?.feeNature,
        skinTest: lastFilled?.skinTest,
        pharmacy: lastFilled?.pharmacy,
        usage: lastFilled?.usage,
        frequency: lastFilled?.frequency,
        medicationDays: lastFilled?.medicationDays,
        groupId: gid,
      }),
      expanded: true,
    },
  ];
}

const handleAddDrug = async () => {
  if (infusionMode.value) {
    await handleAddWithinInfusionGroup();
    return;
  }
  await commitDrugFormPendingInputs();
  const lastItem = drugItems.value[drugItems.value.length - 1];
  if (!lastItem) return;
  const lastKind = inferAdviceLineEditorKind(lastItem);
  if (!isTcm.value && lastKind === 'MEDICATION' && patientDiagnoses.value.length === 0) {
    ElMessage.warning('请先开立诊断后再新增医嘱，可在诊断字段右侧点击“新增”直接开诊断');
    openDiagnosisDialog(drugItems.value.length - 1);
    return;
  }
  if (!assertRowValid(lastItem)) {
    ElMessage.warning('请先完善上一条医嘱的必填项后再添加');
    return;
  }
  rememberEncounterDiagnosisSelection(lastItem.encounterDiagnosisIds);
  // 新行优先继承会话上次诊断；无上次且唯一诊断时由 resolve 填入
  const diagnosisBinding = resolveRowDiagnosisBinding({
    diagnosis: '',
    encounterDiagnosisIds: [],
  });
  const base: Partial<AdviceEditForm> = {
    orderType: lastItem.orderType,
    diagnosis: diagnosisBinding.diagnosis,
    encounterDiagnosisIds: diagnosisBinding.encounterDiagnosisIds,
    contractId: lastItem.contractId,
    feeNature: lastItem.feeNature,
    skinTest: lastItem.skinTest,
    pharmacy: lastItem.pharmacy,
    usage: lastItem.usage,
    frequency: lastItem.frequency,
    medicationDays: lastItem.medicationDays,
  };
  /** 西药：本条与上新行共用组号（首添时本条可能尚无 groupId，则此时生成并由两行共享） */
  let sessionGroupId: string | undefined;
  if (!isTcm.value && lastKind === 'MEDICATION') {
    sessionGroupId = String(lastItem.groupId ?? '').trim() || String(Date.now());
  }
  const nextItems = [...drugItems.value];
  nextItems[nextItems.length - 1] = {
    ...lastItem,
    expanded: false,
    ...(sessionGroupId ? { groupId: sessionGroupId } : {}),
  };
  nextItems.push({
    ...createDrugItem({
      ...base,
      ...(sessionGroupId ? { groupId: sessionGroupId } : {}),
    }),
    expanded: false,
  });
  drugItems.value = nextItems;
};

/** 保存前从 DOM 读取总量输入框当前值，避免 ElInputNumber 未失焦时 state 仍为自动推算值。 */
function syncExpandedWesternTotalAmountFromDom(root: HTMLElement) {
  const entries = root.querySelectorAll(':scope > .drug-entry');
  entries.forEach((entryEl, index) => {
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim() || !item.expanded || isTcm.value) return;
    if (inferAdviceLineEditorKind(item) !== 'MEDICATION') return;
    const input = entryEl.querySelector(
      '.total-amount-item .dose-qty-input input',
    ) as HTMLInputElement | null;
    if (!input) return;
    const parsed = parseOptionalNumberInput(input.value);
    if (parsed === undefined || !(parsed > 0)) return;
    if (Number(item.totalAmount) === parsed) return;
    invalidateRowFrequencyRefresh(index);
    mergeDrugRow(index, {
      totalAmountManualOverride: true,
      totalAmount: parsed as AdviceDrugItem['totalAmount'],
    });
  });
}

/** 保存/新增前提交表单内未失焦的 ElInputNumber 等控件，避免手工总量尚未写入 state。 */
async function commitDrugFormPendingInputs() {
  const root = adviceCardBodyRef.value;
  if (!root) return;
  syncExpandedWesternTotalAmountFromDom(root);
  const active = document.activeElement;
  if (active instanceof HTMLElement && root.contains(active)) {
    active.blur();
    await nextTick();
  }
  syncExpandedWesternTotalAmountFromDom(root);
  drugItems.value.forEach((item, index) => {
    if (item.totalAmountManualOverride) {
      invalidateRowFrequencyRefresh(index);
    }
  });
  await nextTick();
}

const handleSaveGroup = async () => {
  await commitDrugFormPendingInputs();
  ensureAdviceRowsDiagnosisBeforeSave();
  // 丢掉未选品空行（新开一组后未填的尾行）
  const toSave = drugItems.value.filter((item) => Boolean(String(item.selectedId ?? '').trim()));
  if (toSave.length === 0) {
    ElMessage.warning(infusionMode.value ? '请先开立输液药品' : '请先选择医嘱项目');
    return;
  }
  const invalidIndex = toSave.findIndex((item) => !assertRowValid(item));
  if (invalidIndex !== -1) {
    ElMessage.warning(`第${invalidIndex + 1}条医嘱必填项未完成`);
    return;
  }
  const groupValidationError = validateMedicationGroupSharedFields(toSave);
  if (groupValidationError) {
    ElMessage.warning(groupValidationError);
    return;
  }
  // 保存前把本组最后一次有效诊断写入会话记忆，供下一条空白行默认
  for (let i = toSave.length - 1; i >= 0; i -= 1) {
    const ids = toSave[i]?.encounterDiagnosisIds;
    if ((ids?.length ?? 0) > 0) {
      rememberEncounterDiagnosisSelection(ids);
      break;
    }
  }
  emit('saveGroup', toSave, encounterDiagnosisSnapshot.value);
};

function patchRowFromSlot(index: number, payload: AdviceDrugPatchPayload) {
  setField(index, payload.key, payload.value);
}

function rowPatchWrapper(index: number) {
  return (payload: AdviceDrugPatchPayload) => {
    patchRowFromSlot(index, payload);
  };
}

function westernFormHandlers(index: number): WesternDrugFormHandlers {
  return {
    setField: (key, value) => setField(index, key, value),
    applySplitDoseToDoseSide: (v) => applySplitDoseToDoseSide(index, v),
    applyDoseQtyToSplitSide: (v) => applyDoseQtyToSplitSide(index, v),
    debouncedRefreshFrequencyExecutionCount: () => debouncedRefreshFrequencyExecutionCount(index),
    debouncedEnsureFrequencyCountAndRecalcTotal: () =>
      debouncedEnsureFrequencyCountAndRecalcTotal(index),
    handleTotalAmountFocus: () => handleTotalAmountFocus(index),
    handleTotalAmountManualInput: (v) => handleTotalAmountManualInput(index, v),
    handleTotalAmountUnitManualSelect: (v) => handleTotalAmountUnitManualSelect(index, v),
    handleDiagnosisFocus: () => handleDiagnosisFocus(index),
    openDiagnosisDialog: () => openDiagnosisDialog(index),
    onEncounterDiagnosisDeleted: (deletedId: string) => {
      void onEncounterDiagnosisDeleted(deletedId);
    },
    onEncounterDiagnosisMainSwitched: () => {
      void onEncounterDiagnosisMainSwitched();
    },
    onEncounterDiagnosisOptionsRefreshed,
  };
}

watch(
  () => props.initialGroup,
  () => {
    drugItems.value = getInitialDrugItems();
    infusionMode.value = false;
    activeGroupId.value = String(drugItems.value[0]?.groupId ?? '').trim() || createNewGroupId();
    void nextTick(() => {
      applyMainDiagnosisDefaultToDrugRowsAfterLoad();
    });
  },
  { deep: true },
);

watch(
  () => drugItems.value,
  (value: AdviceDrugItem[]) => {
    emit(
      'editorDirtyChange',
      value.some((item) => Boolean(item.selectedId)),
    );
  },
  { deep: true, immediate: true },
);

watch(
  () =>
    drugItems.value
      .map((d) => `${String(d.selectedId ?? '').trim()}:${d.expanded ? '1' : '0'}`)
      .join('|'),
  () => {
    void focusWesternPrimarySingleDose();
  },
);

onMounted(() => {
  void focusWesternPrimarySingleDose();
});
</script>

<template>
  <div class="advice-card rounded-xl border">
    <div class="advice-card-header flex items-center justify-between px-3 py-2 text-white">
      <div class="flex items-center gap-2 text-sm">
        <span v-if="isTcm && tcmGroupNo">组号 {{ tcmGroupNo }}</span>
        <span v-else-if="infusionMode">输液中 · 共 {{ infusionGroupCount }} 组</span>
      </div>
      <div class="flex items-center gap-2">
        <template v-if="infusionMode">
          <ElButton size="small" link class="card-header-btn" @click="handleAddWithinInfusionGroup">
            组内加药
          </ElButton>
          <ElButton size="small" link class="card-header-btn" @click="handleStartNewInfusionGroup">
            新开一组
          </ElButton>
          <ElButton
            ref="headerSaveButtonRef"
            size="small"
            link
            class="card-header-btn"
            :loading="saveLoading"
            @click="handleSaveGroup"
          >
            结束并保存
          </ElButton>
          <ElButton size="small" link class="card-header-btn" @click="handleExitInfusionMode">
            退出
          </ElButton>
        </template>
        <template v-else>
          <ElButton
            v-if="!isTcm"
            size="small"
            link
            class="card-header-btn"
            @click="handleEnterInfusionMode"
          >
            输液
          </ElButton>
          <ElButton size="small" link class="card-header-btn" @click="handleAddDrug">
            追加药品
          </ElButton>
          <ElButton
            ref="headerSaveButtonRef"
            size="small"
            link
            class="card-header-btn"
            :loading="saveLoading"
            @click="handleSaveGroup"
          >
            保存
          </ElButton>
          <ElButton size="small" link class="card-header-btn" @click="emit('close')">关闭</ElButton>
        </template>
      </div>
    </div>

    <div ref="adviceCardBodyRef" class="advice-card-body bg-white px-3 py-2">
      <div
        v-for="(item, index) in drugItems"
        :key="`${item.groupId ?? ''}-${item.selectedId}-${index}`"
        class="drug-entry"
      >
        <div v-if="isInfusionGroupStart(index)" class="infusion-group-header mb-2 text-xs">
          {{ resolveInfusionGroupHeaderLabel(index) }}
        </div>
        <div
          class="drug-entry-top-row mb-2 min-w-0 items-center gap-2"
          :class="
            item.selectedId && !item.expanded && !isTcm
              ? 'drug-entry-top-row--western-collapsed'
              : 'drug-entry-top-row--flex-only flex flex-wrap'
          "
        >
          <AdviceOrderSelect
            class="advice-order-select-cell min-w-0 shrink-0"
            :expand-nonce="item.expanded && !isTcm ? (props.orderSelectExpandNonce ?? 0) : 0"
            :focus-trigger-nonce="item.expanded && !isTcm ? (props.orderSelectFocusNonce ?? 0) : 0"
            :model-value="item.selectedId"
            :display-label="item.selectedDrugName"
            keep-selected-label-on-open
            :placeholder="infusionMode ? '请选择药品' : '请选择药品/诊疗项目/耗材'"
            :columns="productColumns"
            :remote-method="queryProductOptions"
            @select="(row) => handleProductSelect(index, row)"
            @update:model-value="
              (value: unknown) => {
                const v = String(value ?? '').trim();
                setField(index, 'selectedId', v);
                if (!v) setField(index, 'productTableName', undefined);
              }
            "
          />
          <div
            v-if="item.selectedId && !item.expanded && !isTcm"
            class="collapsed-summary collapsed-summary--inline collapsed-summary--one-line w-full min-w-0"
          >
            <div class="collapsed-single-line" :title="getCollapsedTooltip(item)">
              <span class="collapsed-name"
                >{{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】单价：{{
                  formatUnitPriceYuan(item.selectedDrugPrice)
                }}</span
              >
              <span class="collapsed-rest">
                | {{ item.diagnosis || '-' }} | {{ item.feeNature }} |
                {{ getCollapsedSummary(item) }} | 总金额：{{
                  formatMoneyStrYuan(calcAdviceTotalFee(item))
                }}
              </span>
            </div>
          </div>
          <div class="drug-entry-actions shrink-0">
            <ElButton
              v-if="item.selectedId && !item.expanded"
              size="small"
              link
              class="entry-action-btn"
              @click="handleEditDrug(index)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="drugItems.length > 1 || item.medicationRequestId"
              size="small"
              link
              class="entry-action-btn danger"
              :loading="saveLoading"
              @click="handleDeleteDrug(index)"
            >
              删除
            </ElButton>
          </div>
        </div>

        <div
          v-if="item.selectedId && item.expanded"
          class="drug-form-panel"
          @focusin="setFormScope"
          @keydown="handleDrugFormKeydown"
        >
          <template v-if="isTcm">
            <slot
              name="detail"
              :item="item"
              :index="index"
              :drug-items="drugItems"
              :is-tcm="true"
              :calc-total-fee="calcAdviceTotalFee"
              :patch="rowPatchWrapper(index)"
              :patient-diagnoses="[]"
              :update-patient-diagnoses="() => {}"
            ></slot>
          </template>
          <template v-else>
            <AdviceWesternDrugFormPanel
              v-if="inferAdviceLineEditorKind(item) === 'MEDICATION'"
              v-model:auto-calc-total-amount="autoCalcTotalAmount"
              :form-handlers="westernFormHandlers(index)"
              :item="item"
              :encounter-id="props.encounterId"
              :patient-id="props.patientId"
              :fee-nature-options="feeNatureOptions"
              :medication-rate-options="medicationRateOptions"
              :medication-route-options="medicationRouteOptions"
              :patient-diagnoses="patientDiagnoses"
            />
            <AdviceDeviceServiceFormPanel
              v-else
              :editor-kind="inferAdviceLineEditorKind(item) === 'DEVICE' ? 'DEVICE' : 'SERVICE'"
              :encounter-id="props.encounterId"
              :patient-id="props.patientId"
              :fee-nature-options="feeNatureOptions"
              :item="item"
              :patient-diagnoses="patientDiagnoses"
              :set-field="(k, v) => setField(index, k, v)"
              :open-diagnosis-dialog="() => openDiagnosisDialog(index)"
              :on-encounter-diagnosis-deleted="onEncounterDiagnosisDeleted"
              :on-encounter-diagnosis-main-switched="onEncounterDiagnosisMainSwitched"
              :on-encounter-diagnosis-options-refreshed="onEncounterDiagnosisOptionsRefreshed"
            />
          </template>
        </div>
        <div v-else-if="item.selectedId && isTcm">
          <slot name="collapsed" :item="item" :index="index" :is-tcm="true"></slot>
        </div>
      </div>
    </div>
    <DiagnosisPickerDialog
      :model-value="diagnosisDialogVisible"
      :encounter-id="props.encounterId"
      :patient-id="props.patientId"
      :existing-diagnosis-list="encounterDiagnosisSnapshot"
      :patient-diagnoses="patientDiagnoses"
      @update:model-value="handleDiagnosisDialogVisibleChange"
      @confirm="handleDiagnosisConfirm"
      @saved="onDiagnosisPickerSaved"
    />
  </div>
</template>

<style scoped>
.advice-card {
  border-color: #cfe3f5;
  border-radius: 14px;
  box-shadow: 0 6px 14px rgb(15 23 42 / 10%);
  overflow: visible;
  background: #fff;
}

.advice-card-header {
  background: linear-gradient(90deg, #5aa9cf 0%, #3a8fbe 55%, #2f74b5 100%);
  border-top-left-radius: 14px;
  border-top-right-radius: 14px;
}

.advice-card-body {
  border-bottom-left-radius: 14px;
  border-bottom-right-radius: 14px;
}

.infusion-group-header {
  color: var(--el-text-color-regular);
  border-bottom: 1px dashed var(--el-border-color);
  padding-bottom: 4px;
}

.card-header-btn {
  color: rgb(255 255 255 / 95%) !important;
}

.drug-entry-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.entry-action-btn {
  color: #2563eb !important;
}

.entry-action-btn.danger {
  color: #dc2626 !important;
}

.advice-card :deep(.vxe-pulldown--panel) {
  z-index: 5000 !important;
}

.drug-entry + .drug-entry {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #d1e4ff;
}

.drug-form-panel {
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.collapsed-summary {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  padding: 8px 10px;
}

.collapsed-summary--inline {
  min-width: 0;
}

.collapsed-summary--one-line .collapsed-single-line {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.35;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.collapsed-summary--one-line .collapsed-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.collapsed-summary--one-line .collapsed-rest {
  font-weight: 400;
}

.drug-entry-top-row--western-collapsed {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr) auto;
  align-items: center;
}

.drug-entry-top-row--flex-only {
  min-width: 0;
}

.drug-entry-top-row {
  min-width: 0;
}
</style>
