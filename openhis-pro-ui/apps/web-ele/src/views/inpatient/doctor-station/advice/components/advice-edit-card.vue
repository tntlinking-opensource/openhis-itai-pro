<script setup lang="ts">
import type {
  AdviceDrugItem,
  AdviceDrugPatchPayload,
  AdviceEditForm,
  AdviceProductOption,
  InpatientAdviceTherapyType,
  ProductColumn,
  ProductQueryParams,
} from '../services/advice.types';
import type { WesternDrugFormHandlers } from './advice-western-drug-form-panel.vue';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { ElButton, ElMessage, ElRadio, ElRadioGroup } from 'element-plus';

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
  buildTcmProductSelectPatch,
  buildWesternProductSelectPatch,
  createAdviceDrugItem,
  inferDoseBandConvertK,
  parseOptionalNumberInput,
  pickCatalogChrgitmLvPatch,
} from '../services/advice-edit';
import {
  defaultMedicationRateCode,
  formatMoneyStrYuan,
  formatUnitPriceYuan,
  resolveContractForChrgitmLv,
} from '../services/advice-format';
import { isAdviceCatalogZeroStock } from '../services/advice-list';
import {
  inferAdviceLineEditorKind,
  isEntrustAdviceDrugItem,
  isInpatientDischargeMedicationTherapy,
  isInpatientLongTermMedicationTherapy,
  validateWesternEditLineItem,
} from '../services/advice-row';
import {
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATEGORY_CODE,
} from '../services/advice-tab.constants';
import {
  convertTotalAmountWhenUnitChanges,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
  roundMedicationSplitQty,
} from '../services/medication-unit-bands';
import AdviceDeviceServiceFormPanel from './advice-device-service-form-panel.vue';
import AdviceOrderSelect from './advice-order-select.vue';
import AdviceWesternDrugFormPanel from './advice-western-drug-form-panel.vue';

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    feeNatureOptions?: TerminologyOptionDto[];
    initialGroup?: AdviceDrugItem[];
    medicationRateOptions?: TerminologyOptionDto[];
    medicationRouteOptions?: TerminologyOptionDto[];
    /** IF-IP-DOR-18 输液滴速选项 */
    medicationSpeedOptions?: TerminologyOptionDto[];
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
    feeNatureOptions: () => [],
    encounterId: '',
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
    medicationSpeedOptions: () => [],
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
  editorDirtyChange: [value: boolean];
  saveGroup: [value: AdviceDrugItem[]];
}>();

const isTcm = computed(() => props.variant === 'tcm');

/** 住院西药编辑卡：长期 / 临时 / 出院带药（默认长期） */
const therapyType = ref<InpatientAdviceTherapyType>('LONG_TERM');

const THERAPY_TYPE_CODES = new Set<InpatientAdviceTherapyType>([
  'DISCHARGE_MEDICATION',
  'LONG_TERM',
  'TEMPORARY',
]);

watch(
  () => props.initialGroup,
  (group) => {
    if (!group?.length || isTcm.value) return;
    const med = group.find((item) => inferAdviceLineEditorKind(item) === 'MEDICATION');
    const svc = group.find((item) => inferAdviceLineEditorKind(item) === 'SERVICE');
    const code = String((med ?? svc)?.therapyEnum ?? '').trim();
    if (THERAPY_TYPE_CODES.has(code as InpatientAdviceTherapyType)) {
      therapyType.value = code as InpatientAdviceTherapyType;
    }
  },
  { immediate: true },
);

const { productColumns, queryProductOptions } = props;

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

/** 嘱托编辑卡：隐藏出院带药（仅长期/临时） */
const isEntrustEditor = computed(() => {
  if (isTcm.value) return false;
  const items = drugItems.value;
  if (items.length === 0) return false;
  return items.every((item) => {
    if (!String(item.selectedId ?? '').trim()) return false;
    return isEntrustAdviceDrugItem(item);
  });
});

watch(isEntrustEditor, (entrust) => {
  if (entrust && isInpatientDischargeMedicationTherapy(therapyType.value)) {
    therapyType.value = 'LONG_TERM';
  }
});

const setField = <K extends keyof AdviceDrugItem>(
  index: number,
  key: K,
  value: AdviceDrugItem[K],
) => {
  const next = [...drugItems.value];
  const current = next[index];
  if (!current) return;
  next[index] = { ...current, [key]: value };
  // 同组滴速一致：改一条同步到同 groupId 全部药品行
  if (key === 'speedCode') {
    const gid = String(next[index]?.groupId ?? '').trim();
    const speed = String(value ?? '').trim() || undefined;
    if (gid) {
      for (let i = 0; i < next.length; i += 1) {
        if (i === index) {
          continue;
        }
        const row = next[i];
        if (!row || inferAdviceLineEditorKind(row) !== 'MEDICATION') {
          continue;
        }
        if (String(row.groupId ?? '').trim() !== gid) {
          continue;
        }
        next[i] = { ...row, speedCode: speed };
      }
    }
  }
  drugItems.value = next;
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
  refreshRowFrequencyExecutionCount,
  recalcRowTotalAmountForTargetUnit,
} = useAdviceFrequencyTotal({
  drugItems,
  isTcm,
  setField,
  therapyType,
});

/** 出院带药目录仅药品 */
const catalogScope = computed(() =>
  isInpatientDischargeMedicationTherapy(therapyType.value) ? 'MEDICATION_ONLY' : 'ALL',
);

watch(therapyType, (code) => {
  if (isInpatientLongTermMedicationTherapy(code)) {
    autoCalcTotalAmount.value = false;
  }
  const longTerm = isInpatientLongTermMedicationTherapy(code);
  const isTemporary =
    String(code ?? '')
      .trim()
      .toUpperCase() === 'TEMPORARY';
  const temporaryRate = isTemporary
    ? defaultMedicationRateCode(props.medicationRateOptions ?? [], 'TEMPORARY')
    : '';
  // 已从列表回填为临时的行：保留已有频次，避免打开编辑时被「立即」覆盖
  const syncingFromLoadedTemporary =
    isTemporary &&
    drugItems.value.some(
      (item) =>
        String(item.therapyEnum ?? '')
          .trim()
          .toUpperCase() === 'TEMPORARY',
    );
  // 出院带药：移除已选诊疗/嘱托行（BR-IP-ORD-029）
  if (isInpatientDischargeMedicationTherapy(code)) {
    const before = drugItems.value.length;
    drugItems.value = drugItems.value.filter(
      (item) => inferAdviceLineEditorKind(item) !== 'SERVICE',
    );
    if (drugItems.value.length < before) {
      ElMessage.warning('出院带药仅允许开立药品，已移除诊疗行');
    }
  }
  drugItems.value = drugItems.value.map((item) => {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'MEDICATION' || kind === 'SERVICE') {
      const fillTemporaryRate = isTemporary && temporaryRate && !syncingFromLoadedTemporary;
      return {
        ...item,
        therapyEnum: code,
        ...(longTerm && kind === 'MEDICATION' ? { medicationDays: undefined } : {}),
        ...(fillTemporaryRate ? { frequency: temporaryRate } : {}),
      };
    }
    return item;
  });
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

function markTotalAmountManualOverride(index: number) {
  setField(index, 'totalAmountManualOverride', true);
}

function handleTotalAmountManualInput(index: number, value: unknown) {
  markTotalAmountManualOverride(index);
  setField(index, 'totalAmount', parseOptionalNumberInput(value) as AdviceDrugItem['totalAmount']);
}

function handleTotalAmountUnitManualSelect(index: number, value: unknown) {
  const nu = String(value ?? '').trim();
  const item = drugItems.value[index];
  if (!item || nu === String(item.totalAmountUnit ?? '').trim()) return;

  if (autoCalcTotalAmount.value && !item.totalAmountManualOverride) {
    recalcRowTotalAmountForTargetUnit(index, nu);
    return;
  }

  markTotalAmountManualOverride(index);
  handleTotalAmountUnitSelect(index, value);
}

const clearMedicationOnlyFields = (): Partial<AdviceDrugItem> => ({
  singleDose: undefined,
  doseConvertRatio: undefined,
  doseBandConvertK: undefined,
  frequency: '',
  usage: '',
  speedCode: undefined,
  injectFlag: undefined,
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
    ElMessage.warning('库存不足');
    return;
  }
  const prev = drugItems.value[index];
  if (!prev) return;
  const base: Partial<AdviceDrugItem> = {
    /** 与目录下拉 `mapCatalogRowToOption` 的复合 id 一致，保存时组包取定义主键 */
    selectedId: String(row.id ?? '').trim(),
    selectedDrugName: row.name || '',
    selectedDrugSpec: row.spec || '',
    productTableName: String(row.productTableName ?? '').trim() || undefined,
  };
  const tbl = String(row.productTableName ?? '')
    .trim()
    .toLowerCase();
  if (isTcm.value) {
    mergeDrugRow(index, {
      ...base,
      selectedDrugPrice: row.price ?? '',
      ...buildTcmProductSelectPatch(row),
      ...pickCatalogChrgitmLvPatch(row),
    });
  } else if (tbl === 'wkf_activity_definition') {
    // 出院带药禁止选诊疗（含嘱托）
    if (isInpatientDischargeMedicationTherapy(therapyType.value)) {
      ElMessage.warning('出院带药仅允许开立药品医嘱');
      return;
    }
    const orgId = String(row.defaultPerformOrgId ?? '').trim();
    const orgName = String(row.defaultPerformOrgName ?? '').trim();
    const catalogRate = String(row.rateCode ?? '').trim();
    const defaultRate = defaultMedicationRateCode(
      props.medicationRateOptions ?? [],
      therapyType.value,
    );
    const uCode = String(row.defaultUnitCode ?? '').trim() || '次';
    const uName = String(row.defaultUnitName ?? '').trim() || uCode;
    const categoryCode = String(row.categoryCode ?? '').trim();
    const isEntrust =
      categoryCode === INPATIENT_ENTRUST_CATEGORY_CODE ||
      String(row.name ?? '').trim() === INPATIENT_ENTRUST_ACTIVITY_NAME;
    mergeDrugRow(index, {
      ...base,
      selectedDrugPrice: row.price ?? '',
      ...clearMedicationOnlyFields(),
      ...pickCatalogChrgitmLvPatch(row),
      frequency: catalogRate || defaultRate,
      therapyEnum: therapyType.value,
      performOrgId: orgId || undefined,
      performOrgName: orgName || undefined,
      catalogCategoryCode: categoryCode || undefined,
      // 嘱托：数量/单位默认 1 次，正文待医生录入
      serviceQuantity: 1,
      totalAmount: 1,
      totalAmountUnit: uCode,
      totalUnitSalesCode: uCode,
      totalUnitSalesName: uName,
      entrustContent: isEntrust ? String(prev.entrustContent ?? '').trim() || undefined : undefined,
    });
  } else if (tbl === 'adm_device_definition') {
    ElMessage.warning('住院医生站不支持开立耗材医嘱');
    return;
  } else {
    const catalogPatch = buildWesternProductSelectPatch(prev, row, {
      totalUnitDefaultKind: therapyType.value === 'TEMPORARY' ? undefined : 'SALES',
    });
    const nextFreq = String(catalogPatch.frequency ?? prev.frequency ?? '').trim();
    mergeDrugRow(index, {
      ...base,
      totalAmountManualOverride: false,
      ...catalogPatch,
      // 临时且目录未带频次：默认「立即」
      ...(!nextFreq
        ? {
            frequency: defaultMedicationRateCode(
              props.medicationRateOptions ?? [],
              therapyType.value,
            ),
          }
        : {}),
    });
    if (!isTcm.value && autoCalcTotalAmount.value && therapyType.value === 'TEMPORARY') {
      void nextTick(() => refreshRowFrequencyExecutionCount(index));
    }
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
  void focusWesternPrimarySingleDose();
};

function validateEditorLineItem(item: AdviceDrugItem) {
  if (isTcm.value) {
    return props.validateRow ? props.validateRow(item) : false;
  }
  return validateWesternEditLineItem(item, therapyType.value);
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
    return formatNonMedicationCollapsedSummary(item, kind, collapsedTextOpts.value);
  }
  return formatWesternCollapsedSummary(item, collapsedTextOpts.value);
};

const getCollapsedTooltip = (item: AdviceDrugItem) => {
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE' || kind === 'SERVICE') {
    const mid = formatNonMedicationCollapsedSummary(item, kind);
    const full = `${item.feeNature} | ${mid} | 总金额：${formatMoneyStrYuan(calcAdviceTotalFee(item))}`;
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
  void focusWesternPrimarySingleDose();
};

const handleDeleteDrug = (index: number) => {
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

const handleAddDrug = () => {
  const lastItem = drugItems.value[drugItems.value.length - 1];
  if (!lastItem) return;
  const lastKind = inferAdviceLineEditorKind(lastItem);
  if (!assertRowValid(lastItem)) {
    ElMessage.warning('请先完善上一条医嘱的必填项后再添加');
    return;
  }
  const base: Partial<AdviceEditForm> = {
    orderType: lastItem.orderType,
    feeNature: lastItem.feeNature,
    skinTest: lastItem.skinTest,
    pharmacy: lastItem.pharmacy,
    usage: lastItem.usage,
    frequency: lastItem.frequency,
    speedCode: lastItem.speedCode,
    injectFlag: lastItem.injectFlag,
    medicationDays: isInpatientLongTermMedicationTherapy(therapyType.value)
      ? undefined
      : lastItem.medicationDays,
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

/** 顶栏保存：西药行带上当前长期/临时选择 */
const handleSaveGroup = () => {
  const invalidIndex = drugItems.value.findIndex((item) => !assertRowValid(item));
  if (invalidIndex !== -1) {
    ElMessage.warning(`第${invalidIndex + 1}条医嘱必填项未完成`);
    return;
  }
  const rows = drugItems.value.map((item) => {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'SERVICE' || kind === 'MEDICATION') {
      return { ...item, therapyEnum: therapyType.value };
    }
    return item;
  });
  emit('saveGroup', rows);
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
    debouncedRecalcFrequencyTotal: () => debouncedRefreshFrequencyExecutionCount(index),
    debouncedEnsureFrequencyCountAndRecalcTotal: () =>
      debouncedEnsureFrequencyCountAndRecalcTotal(index),
    handleTotalAmountManualInput: (v) => handleTotalAmountManualInput(index, v),
    handleTotalAmountUnitManualSelect: (v) => handleTotalAmountUnitManualSelect(index, v),
  };
}

watch(
  () => props.initialGroup,
  () => {
    drugItems.value = getInitialDrugItems();
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
        <ElRadioGroup v-if="!isTcm" v-model="therapyType">
          <ElRadio label="LONG_TERM">长期</ElRadio>
          <ElRadio label="TEMPORARY">临时</ElRadio>
          <ElRadio v-if="!isEntrustEditor" label="DISCHARGE_MEDICATION">出院带药</ElRadio>
        </ElRadioGroup>
        <span v-if="isTcm && tcmGroupNo">组号 {{ tcmGroupNo }}</span>
      </div>
      <div class="flex items-center gap-2">
        <ElButton
          v-if="!isEntrustEditor"
          size="small"
          link
          class="card-header-btn"
          @click="handleAddDrug"
        >
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
      </div>
    </div>

    <div ref="adviceCardBodyRef" class="advice-card-body bg-white px-3 py-2">
      <div
        v-for="(item, index) in drugItems"
        :key="`${item.selectedId}-${index}`"
        class="drug-entry"
      >
        <div
          class="drug-entry-top-row mb-2 min-w-0 items-center gap-2"
          :class="
            item.selectedId && !item.expanded && !isTcm
              ? 'drug-entry-top-row--western-collapsed'
              : 'drug-entry-top-row--flex-only flex flex-wrap'
          "
        >
          <AdviceOrderSelect
            v-if="!isEntrustEditor"
            class="advice-order-select-cell min-w-0 shrink-0"
            :expand-nonce="item.expanded && !isTcm ? (props.orderSelectExpandNonce ?? 0) : 0"
            :focus-trigger-nonce="item.expanded && !isTcm ? (props.orderSelectFocusNonce ?? 0) : 0"
            :model-value="item.selectedId"
            :display-label="item.selectedDrugName"
            keep-selected-label-on-open
            :placeholder="
              isInpatientDischargeMedicationTherapy(therapyType)
                ? '请选择药品（出院带药）'
                : '请选择药品/诊疗项目'
            "
            :columns="productColumns"
            :catalog-scope="catalogScope"
            :remote-method="queryProductOptions"
            @select="(row) => handleProductSelect(index, row)"
            @update:model-value="
              (value: unknown) => {
                const v = String(value ?? '').trim();
                setField(index, 'selectedId', v);
                if (!v) {
                  setField(index, 'productTableName', undefined);
                  setField(index, 'catalogCategoryCode', undefined);
                  setField(index, 'entrustContent', undefined);
                }
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
                | {{ item.feeNature }} | {{ getCollapsedSummary(item) }} | 总金额：{{
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
              v-if="drugItems.length > 1"
              size="small"
              link
              class="entry-action-btn danger"
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
            ></slot>
          </template>
          <template v-else>
            <AdviceWesternDrugFormPanel
              v-if="inferAdviceLineEditorKind(item) === 'MEDICATION'"
              v-model:auto-calc-total-amount="autoCalcTotalAmount"
              :hide-total-amount="isInpatientLongTermMedicationTherapy(therapyType)"
              :form-handlers="westernFormHandlers(index)"
              :item="item"
              :fee-nature-options="feeNatureOptions"
              :medication-rate-options="medicationRateOptions"
              :medication-route-options="medicationRouteOptions"
              :medication-speed-options="medicationSpeedOptions"
            />
            <AdviceDeviceServiceFormPanel
              v-else
              :editor-kind="inferAdviceLineEditorKind(item) === 'DEVICE' ? 'DEVICE' : 'SERVICE'"
              :encounter-id="props.encounterId"
              :patient-id="props.patientId"
              :medication-rate-options="medicationRateOptions"
              :therapy-type="therapyType"
              :item="item"
              :set-field="(k, v) => setField(index, k, v)"
            />
          </template>
        </div>
        <div v-else-if="item.selectedId && isTcm">
          <slot name="collapsed" :item="item" :index="index" :is-tcm="true"></slot>
        </div>
      </div>
    </div>
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

.advice-card-header :deep(.el-radio__label) {
  color: #fff;
  font-size: 14px;
}

.card-header-btn {
  color: rgb(255 255 255 / 95%) !important;
  font-size: 14px !important;
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
