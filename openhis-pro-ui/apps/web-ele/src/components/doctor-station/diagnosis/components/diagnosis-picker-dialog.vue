<script setup lang="ts">
import type {
  DiagnosisConfirmPayload,
  DiagnosisItem,
  DiagnosisMedicineType,
  DiagnosisScopeType,
} from '#/components/doctor-station/diagnosis/services/diagnosis.types';
import type { DoctorStationDiagnosisScope } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import type {
  TcmCatalogKind,
  TcmDiagnosisPick,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';

import type {
  ConditionDefinitionRowResponse,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

import { computed, nextTick, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { saveEncounterDiagnosisRows } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import {
  formatDiagnosisYbNoDisplay,
  hasDiagnosisYbNo,
  formatTcmDiagnosisDisplayName,
  mapTcmCatalogRowToConditionDefinitionRow,
  resolveTcmCatalogKindFromRow,
  resolveTcmCatalogRole,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import {
  appendTcmDiagnosisGroupBatch,
  resolveTcmDiagnosisHttp,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-groups.service';

/** 外挂布局样式（院点包 scoped 可能丢失，查询行靠此外挂 + 原子类） */
import './diagnosis-picker-dialog.css';

defineOptions({ name: 'DiagnosisPickerDialog' });

/** 中医目录行：带目录角色，点选写入病或证 */
interface TcmMergedCatalogRow extends ConditionDefinitionRowResponse {
  tcmCatalogKind: TcmCatalogKind;
}

const props = withDefaults(
  defineProps<{
    /** 打开时默认西医(1)/中医(2)；中药处方页传 2 */
    defaultMedicineTypeCode?: '1' | '2';
    encounterId?: string;
    existingDiagnosisList?: EncounterDiagnosisRowDto[];
    modelValue: boolean;
    patientDiagnoses?: DiagnosisItem[];
    patientId?: string;
    /**
     * 仅选目录回传（病案首页等）：与住院开诊断同一检索弹窗，确定时不写就诊诊断。
     */
    pickOnly?: boolean;
    stationScope?: DoctorStationDiagnosisScope;
  }>(),
  {
    defaultMedicineTypeCode: '1',
    encounterId: '',
    existingDiagnosisList: () => [],
    patientDiagnoses: () => [],
    patientId: '',
    pickOnly: false,
    stationScope: 'outpatient',
  },
);

const emit = defineEmits<{
  confirm: [value: DiagnosisConfirmPayload];
  saved: [];
  'update:modelValue': [value: boolean];
}>();

const diagnosisHttp = computed(() =>
  createDoctorStationDiagnosisHttpClient(
    props.stationScope === 'inpatient'
      ? '/inpatientDoctorStationDiagnosis'
      : '/doctorStationDiagnosis',
  ),
);

const tcmHttp = computed(() => resolveTcmDiagnosisHttp(props.stationScope));

/** 有就诊上下文或 pickOnly：走目录检索 UI；否则走医嘱侧本地候选 */
const encounterMode = computed(
  () =>
    props.pickOnly === true ||
    (Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim())),
);

/** 底部主按钮文案 */
const encounterConfirmLabel = computed(() => (props.pickOnly === true ? '加入列表' : '确定并保存'));

// —— 就诊检索模式（接口分页） ——
const medicineTypeCode = ref<'1' | '2'>('1');
const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const records = ref<ConditionDefinitionRowResponse[]>([]);
const tableLoading = ref(false);
const saveLoading = ref(false);
const selectedRow = ref<ConditionDefinitionRowResponse | null>(null);
const tcmDiseasePick = ref<null | TcmDiagnosisPick>(null);
const tcmSyndromePick = ref<null | TcmDiagnosisPick>(null);
/** 中医目录角色：疾病 / 证候分开展示，避免合并列表把证候挤到后面 */
const tcmCatalogKind = ref<TcmCatalogKind>('disease');
const catalogTableRef = ref<{ setCurrentRow: (row?: unknown) => void } | null>(null);

const isTcmMode = computed(() => medicineTypeCode.value === '2');

const tcmSearchPlaceholder = computed(() =>
  tcmCatalogKind.value === 'syndrome' ? '证候名称或编码' : '疾病名称或编码',
);

/**
 * 西医国临选码：住院医生站 + 病案首页 pickOnly（后端 inpatient 接口已收敛 clinical_v2）。
 * 门诊西医仍走院内/医保目录。
 */
const isClinicalV2WesternPick = computed(
  () => !isTcmMode.value && (props.pickOnly === true || props.stationScope === 'inpatient'),
);

/** 编码列标题：国临选码用「国临码」，门诊目录仍用「诊断编码」 */
const catalogCodeColumnLabel = computed(() => {
  if (isClinicalV2WesternPick.value) {
    return '国临码';
  }
  return '诊断编码';
});

/** 表格名称列标题：中医「名称」；国临「国临诊断名称」；门诊「诊断名称」 */
const catalogNameColumnLabel = computed(() => {
  if (isTcmMode.value) {
    return '名称';
  }
  if (isClinicalV2WesternPick.value) {
    return '国临诊断名称';
  }
  return '诊断名称';
});

/** 表格行主键：中医带上目录角色，避免病/证同 id 时高亮串行 */
function catalogRowKey(row: ConditionDefinitionRowResponse) {
  const kind = (row as TcmMergedCatalogRow).tcmCatalogKind ?? 'western';
  return `${kind}-${row.conditionDefinitionId ?? ''}`;
}

/** 拉取一页中医病名或证候（IF-DOR-31：按 catalogRole 切换，不合并） */
async function loadTcmCatalogPage() {
  const catalogKind = tcmCatalogKind.value;
  const res = await tcmHttp.value.httpPageTcmConditionDefinitionCatalog({
    catalogRole: resolveTcmCatalogRole(catalogKind),
    keyword: keyword.value.trim() || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  });
  records.value = (res.rows ?? []).map((row) => ({
    ...mapTcmCatalogRowToConditionDefinitionRow(row, catalogKind),
    tcmCatalogKind: catalogKind,
  }));
  total.value = Number(res.total ?? 0);
  selectedRow.value = null;
}

/** 拉取当前页诊断定义目录（住院/病案首页国临；门诊西医院内医保；中医按病/证切换） */
async function loadDefinitionPage() {
  tableLoading.value = true;
  try {
    if (isTcmMode.value) {
      await loadTcmCatalogPage();
      return;
    }
    // 住院站 / pickOnly 走 inpatient 前缀 → 后端仅 clinical_v2；门诊走门诊前缀
    const res = await diagnosisHttp.value.httpGetConditionDefinitionMetadata({
      diagnosisKindCode: '1',
      keyword: keyword.value.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    records.value = res.content ?? [];
    total.value = Number(res.totalCount ?? res.total ?? 0);
    // 翻页/检索后不自动选中首行，避免误入「选择结果」
    selectedRow.value = null;
  } finally {
    tableLoading.value = false;
    await nextTick();
    catalogTableRef.value?.setCurrentRow();
  }
}

watch([medicineTypeCode, tcmCatalogKind, pageSize], () => {
  if (!props.modelValue || !encounterMode.value) return;
  pageNo.value = 1;
  void loadDefinitionPage();
});

watch(medicineTypeCode, (next) => {
  if (next === '1') {
    tcmDiseasePick.value = null;
    tcmSyndromePick.value = null;
  }
});

// —— 医嘱本地候选模式 ——
const medicineType = ref<DiagnosisMedicineType>('western');
const scopeType = ref<DiagnosisScopeType>('all');
const selectedId = ref('');
const saveAsMainDiagnosis = ref(false);
/** 存个人常用（绑定当前登录用户 PERSONAL 模板） */
const saveAsPersonal = ref(false);
/** 存科室常用（绑定当前登录科室 DEPARTMENT 模板） */
const saveAsDepartment = ref(false);
const legacyTcmDiseasePick = ref<null | TcmDiagnosisPick>(null);
const legacyTcmSyndromePick = ref<null | TcmDiagnosisPick>(null);
const legacyTcmCatalogKind = ref<TcmCatalogKind>('disease');

const sourceDiagnoses = computed(() => props.patientDiagnoses);

const displayDiagnoses = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return sourceDiagnoses.value.filter((item) => {
    if (item.medicineType !== medicineType.value) return false;
    if (scopeType.value === 'common' && !item.isCommon) return false;
    if (scopeType.value === 'history' && !item.isHistory) return false;
    if (!text) return true;
    return item.name.toLowerCase().includes(text) || item.code.toLowerCase().includes(text);
  });
});

const selectedDiagnosis = computed(() => {
  return displayDiagnoses.value.find((item) => item.id === selectedId.value) || null;
});

const legacyIsTcmMode = computed(() => medicineType.value === 'tcm');

/** 将检索行转为中医疾病/证候候选 */
function toTcmPick(row: ConditionDefinitionRowResponse): TcmDiagnosisPick {
  return {
    conditionDefinitionId: row.conditionDefinitionId ?? '',
    conditionCode: row.conditionCode ?? '',
    conditionName: row.conditionName ?? '',
  };
}

/** 切到另一侧目录：清空关键字并加载，已选病/证保留 */
function switchTcmCatalogIfNeeded(next: TcmCatalogKind) {
  if (tcmCatalogKind.value === next) return;
  keyword.value = '';
  pageNo.value = 1;
  tcmCatalogKind.value = next;
}

/** 就诊模式：按当前目录角色写入疾病或证候（切换目录不覆盖另一侧） */
function applyEncounterRowPick(row: ConditionDefinitionRowResponse | null) {
  if (!row) return;
  if (!isTcmMode.value) return;
  const pick = toTcmPick(row);
  const kind =
    (row as TcmMergedCatalogRow).tcmCatalogKind ??
    resolveTcmCatalogKindFromRow(row as TcmMergedCatalogRow) ??
    tcmCatalogKind.value;
  if (kind === 'syndrome') {
    tcmSyndromePick.value = pick;
    if (!tcmDiseasePick.value?.conditionDefinitionId) {
      switchTcmCatalogIfNeeded('disease');
    }
    return;
  }
  tcmDiseasePick.value = pick;
  if (!tcmSyndromePick.value?.conditionDefinitionId) {
    switchTcmCatalogIfNeeded('syndrome');
  }
}

/** 重置中医表单候选 */
function resetTcmFormPicks() {
  tcmDiseasePick.value = null;
  tcmSyndromePick.value = null;
  legacyTcmDiseasePick.value = null;
  legacyTcmSyndromePick.value = null;
}

function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 弹窗打开：按模式初始化（不预选任何诊断） */
function onDialogOpen() {
  resetTcmFormPicks();
  keyword.value = '';
  pageNo.value = 1;
  selectedRow.value = null;
  selectedId.value = '';
  tcmCatalogKind.value = 'disease';
  legacyTcmCatalogKind.value = 'disease';
  saveAsMainDiagnosis.value = false;
  saveAsPersonal.value = false;
  saveAsDepartment.value = false;
  const defaultCode = props.defaultMedicineTypeCode === '2' ? '2' : '1';
  medicineTypeCode.value = defaultCode;
  medicineType.value = defaultCode === '2' ? 'tcm' : 'western';
  if (encounterMode.value) {
    void loadDefinitionPage();
  }
}

/**
 * 按勾选将诊断定义写入个人/科室常用模板。
 * 科室 objectId 由后端取当前登录科室；未勾选则跳过。
 * 绑定失败不阻断就诊诊断已保存结果，仅提示。
 */
async function bindCommonIfNeeded(params: {
  conditionDefinitionId?: null | string;
  syndromeDefinitionId?: null | string;
}): Promise<boolean> {
  if (!saveAsPersonal.value && !saveAsDepartment.value) return true;
  const defId = preserveApiIdForRequest(params.conditionDefinitionId);
  if (!defId) {
    ElMessage.warning('无法保存常用：缺少诊断定义标识');
    return false;
  }
  const syndromeId = preserveApiIdForRequest(params.syndromeDefinitionId);
  try {
    await diagnosisHttp.value.httpBindCommonDiagnosis({
      conditionDefinitionId: defId,
      syndromeDefinitionId: syndromeId || undefined,
      saveAsPersonal: saveAsPersonal.value,
      saveAsDepartment: saveAsDepartment.value,
    });
    return true;
  } catch {
    ElMessage.error('常用诊断保存失败，请稍后重试');
    return false;
  }
}

function handleEncounterSearch() {
  pageNo.value = 1;
  void loadDefinitionPage();
}

function handleEncounterPageChange(p: number) {
  pageNo.value = p;
  void loadDefinitionPage();
}

function handleEncounterCurrentChange(row: ConditionDefinitionRowResponse | undefined) {
  selectedRow.value = row ?? null;
}

/** 中医用行点击写入病/证：不依赖 current-change，避免高亮行二次点选失效 */
function handleEncounterRowClick(row: ConditionDefinitionRowResponse) {
  if (!row) return;
  selectedRow.value = row;
  applyEncounterRowPick(row);
}

function handleEncounterRowDblclick(row: ConditionDefinitionRowResponse) {
  if (!row) return;
  selectedRow.value = row;
  applyEncounterRowPick(row);
  // 中医须病+证齐备才保存；未齐备时双击仅写入当前侧候选
  if (isTcmMode.value) {
    if (
      !tcmDiseasePick.value?.conditionDefinitionId ||
      !tcmSyndromePick.value?.conditionDefinitionId
    ) {
      return;
    }
    void handleEncounterConfirmSave();
    return;
  }
  void handleEncounterConfirmSave(row);
}

/** 组装待保存的就诊诊断行（西医单条；中医走 IF-DOR-32 病证组接口） */
function buildEncounterAppendRow(): EncounterDiagnosisRowDto | null {
  if (isTcmMode.value) return null;
  const resolved = selectedRow.value;
  if (!resolved) return null;
  return {
    conditionDefinitionId: resolved.conditionDefinitionId,
    conditionCode: resolved.conditionCode ?? '',
    conditionDefinitionName: resolved.conditionName ?? '',
    medicineType: resolved.medicineType,
    ybNo: resolved.ybNo ?? undefined,
  };
}

/** pickOnly：组装 confirm 载荷并关闭（不落就诊诊断） */
async function handlePickOnlyConfirm() {
  if (isTcmMode.value) {
    if (!tcmDiseasePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医疾病');
      return;
    }
    if (!tcmSyndromePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医证候');
      return;
    }
    const displayName = formatTcmDiagnosisDisplayName(
      tcmDiseasePick.value.conditionName,
      tcmSyndromePick.value.conditionName,
    );
    emit('confirm', {
      selected: {
        id: tcmDiseasePick.value.conditionDefinitionId,
        code: tcmDiseasePick.value.conditionCode,
        name: displayName,
        medicineType: 'tcm',
        conditionDefinitionId: tcmDiseasePick.value.conditionDefinitionId,
      },
      saveAsMainDiagnosis: saveAsMainDiagnosis.value,
      saveAsCommon: false,
      saveAsPersonal: false,
      saveAsDepartment: false,
    });
    closeDialog();
    return;
  }
  const resolved = selectedRow.value;
  if (!resolved?.conditionDefinitionId) {
    ElMessage.warning('请先在上方列表中选择诊断');
    return;
  }
  emit('confirm', {
    selected: {
      id: resolved.conditionDefinitionId,
      code: resolved.conditionCode ?? '',
      name: resolved.conditionName ?? '',
      medicineType: 'western',
      conditionDefinitionId: resolved.conditionDefinitionId,
    },
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: false,
    saveAsPersonal: false,
    saveAsDepartment: false,
  });
  closeDialog();
}

/** 选中定义并入列表并提交保存；中医走病证组批次，西医走就诊诊断批次 */
async function handleEncounterConfirmSave(pick?: ConditionDefinitionRowResponse | null) {
  if (pick) {
    selectedRow.value = pick;
    applyEncounterRowPick(pick);
  }

  // 病案首页等：只回传选中结果，绝不调就诊诊断保存（避免「当前患者不可编辑诊断」）
  if (props.pickOnly === true) {
    await handlePickOnlyConfirm();
    return;
  }

  const eid = props.encounterId.trim();
  const pid = props.patientId.trim();
  if (!eid || !pid) {
    ElMessage.warning('请先选择患者');
    return;
  }

  if (isTcmMode.value) {
    if (!tcmDiseasePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医疾病');
      return;
    }
    if (!tcmSyndromePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医证候');
      return;
    }
    saveLoading.value = true;
    try {
      const result = await appendTcmDiagnosisGroupBatch(
        eid,
        pid,
        tcmDiseasePick.value,
        tcmSyndromePick.value,
        saveAsMainDiagnosis.value,
        props.stationScope,
      );
      if (result === 'duplicate') {
        ElMessage.info('该病证组已在当前列表中');
        return;
      }
      await bindCommonIfNeeded({
        conditionDefinitionId: tcmDiseasePick.value.conditionDefinitionId,
        syndromeDefinitionId: tcmSyndromePick.value.conditionDefinitionId,
      });
      ElMessage.success('中医病证已保存');
      emit('saved');
      closeDialog();
    } finally {
      saveLoading.value = false;
    }
    return;
  }

  const append = buildEncounterAppendRow();
  if (!append) {
    ElMessage.warning('请先在上方列表中选择诊断');
    return;
  }
  // 西医未对医保码：允许落库（自费），但明确提示结算上传会失败
  if (!isTcmMode.value && !hasDiagnosisYbNo(append.ybNo)) {
    ElMessage.warning('该诊断未对照医保码，医保结算将无法上传；请优先选择已对码诊断');
  }
  if (
    props.existingDiagnosisList.some(
      (r) => r.conditionDefinitionId === append.conditionDefinitionId,
    )
  ) {
    ElMessage.info('该诊断已在当前列表中');
    return;
  }
  let merged: EncounterDiagnosisRowDto[];
  if (saveAsMainDiagnosis.value) {
    const cleared = props.existingDiagnosisList.map((r) => ({ ...r, mainDiagnosisFlag: 0 }));
    append.mainDiagnosisFlag = 1;
    merged = [...cleared, append];
  } else {
    merged = [...props.existingDiagnosisList, append];
  }
  saveLoading.value = true;
  try {
    await saveEncounterDiagnosisRows(eid, pid, merged, props.stationScope);
    await bindCommonIfNeeded({
      conditionDefinitionId: append.conditionDefinitionId,
    });
    ElMessage.success('诊断已保存');
    emit('saved');
    closeDialog();
  } finally {
    saveLoading.value = false;
  }
}

/** 本地候选：按当前目录角色写入疾病或证候 */
function applyLegacyRowPick(row: DiagnosisItem | undefined) {
  if (!row || !legacyIsTcmMode.value) return;
  const pick: TcmDiagnosisPick = {
    conditionDefinitionId: row.conditionDefinitionId ?? row.id,
    conditionCode: row.code,
    conditionName: row.name,
  };
  if (legacyTcmCatalogKind.value === 'syndrome') {
    legacyTcmSyndromePick.value = pick;
    if (!legacyTcmDiseasePick.value?.conditionDefinitionId) {
      legacyTcmCatalogKind.value = 'disease';
    }
    return;
  }
  legacyTcmDiseasePick.value = pick;
  if (!legacyTcmSyndromePick.value?.conditionDefinitionId) {
    legacyTcmCatalogKind.value = 'syndrome';
  }
}

/** 本地候选确认：先落常用模板，再回传选中结果 */
async function handleLegacyConfirm() {
  if (legacyIsTcmMode.value) {
    if (!legacyTcmDiseasePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医疾病');
      return;
    }
    if (!legacyTcmSyndromePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医证候');
      return;
    }
    const commonOk = await bindCommonIfNeeded({
      conditionDefinitionId: legacyTcmDiseasePick.value.conditionDefinitionId,
      syndromeDefinitionId: legacyTcmSyndromePick.value.conditionDefinitionId,
    });
    if (!commonOk) return;
    const displayName = formatTcmDiagnosisDisplayName(
      legacyTcmDiseasePick.value.conditionName,
      legacyTcmSyndromePick.value.conditionName,
    );
    emit('confirm', {
      selected: {
        id: legacyTcmDiseasePick.value.conditionDefinitionId,
        code: legacyTcmDiseasePick.value.conditionCode,
        name: displayName,
        medicineType: 'tcm',
        conditionDefinitionId: legacyTcmDiseasePick.value.conditionDefinitionId,
      },
      saveAsMainDiagnosis: saveAsMainDiagnosis.value,
      saveAsCommon: saveAsPersonal.value || saveAsDepartment.value,
      saveAsPersonal: saveAsPersonal.value,
      saveAsDepartment: saveAsDepartment.value,
    });
    closeDialog();
    return;
  }
  if (!selectedDiagnosis.value) return;
  const westernCommonOk = await bindCommonIfNeeded({
    conditionDefinitionId:
      selectedDiagnosis.value.conditionDefinitionId ?? selectedDiagnosis.value.id,
  });
  if (!westernCommonOk) return;
  emit('confirm', {
    selected: selectedDiagnosis.value as DiagnosisItem,
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: saveAsPersonal.value || saveAsDepartment.value,
    saveAsPersonal: saveAsPersonal.value,
    saveAsDepartment: saveAsDepartment.value,
  });
  closeDialog();
}

function setLegacySelectedIdFromRow(row: DiagnosisItem | undefined) {
  selectedId.value = row?.id || '';
  applyLegacyRowPick(row);
}

function onLegacyTableRow(row: DiagnosisItem | undefined) {
  setLegacySelectedIdFromRow(row);
}

/** 双击行：按当前主诊/常用勾选直接确认（与底部「确认」一致） */
async function handleLegacyRowDblclick(row: DiagnosisItem | undefined) {
  if (!row) return;
  applyLegacyRowPick(row);
  if (legacyIsTcmMode.value) {
    // 中医须病+证齐备；未齐备时仅写入，不强制弹警告
    if (
      !legacyTcmDiseasePick.value?.conditionDefinitionId ||
      !legacyTcmSyndromePick.value?.conditionDefinitionId
    ) {
      return;
    }
    await handleLegacyConfirm();
    return;
  }
  const commonOk = await bindCommonIfNeeded({
    conditionDefinitionId: row.conditionDefinitionId ?? row.id,
  });
  if (!commonOk) return;
  emit('confirm', {
    selected: row,
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: saveAsPersonal.value || saveAsDepartment.value,
    saveAsPersonal: saveAsPersonal.value,
    saveAsDepartment: saveAsDepartment.value,
  });
  closeDialog();
}

watch(medicineType, (next) => {
  if (next === 'western') {
    legacyTcmDiseasePick.value = null;
    legacyTcmSyndromePick.value = null;
  }
});

const encounterConfirmDisabled = computed(() => {
  if (isTcmMode.value) {
    // 主线：一病一症，疾病与证候均须选定
    return (
      !tcmDiseasePick.value?.conditionDefinitionId || !tcmSyndromePick.value?.conditionDefinitionId
    );
  }
  return !selectedRow.value;
});

const legacyConfirmDisabled = computed(() => {
  if (legacyIsTcmMode.value) {
    return (
      !legacyTcmDiseasePick.value?.conditionDefinitionId ||
      !legacyTcmSyndromePick.value?.conditionDefinitionId
    );
  }
  return !selectedDiagnosis.value;
});
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :width="encounterMode ? '920px' : '880px'"
    :title="encounterMode ? '新增诊断' : '新增诊断'"
    append-to-body
    @update:model-value="onDialogModelValue"
    @open="onDialogOpen"
  >
    <!-- 就诊站：分页检索已发布定义 -->
    <div v-if="encounterMode" class="dx-picker-dialog diagnosis-dialog flex flex-col gap-3">
      <div class="toolbar-row flex items-center justify-between gap-3">
        <ElRadioGroup v-model="medicineTypeCode" class="dx-picker-dialog__kind kind-radio-group">
          <ElRadioButton label="1">西医诊断</ElRadioButton>
          <ElRadioButton label="2">中医诊断</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-if="isTcmMode"
          v-model="tcmCatalogKind"
          class="dx-picker-dialog__tcm-catalog"
        >
          <ElRadioButton label="disease">疾病</ElRadioButton>
          <ElRadioButton label="syndrome">证候</ElRadioButton>
        </ElRadioGroup>
      </div>
      <div class="dx-picker-dialog__search search-row flex flex-nowrap items-center gap-2">
        <div class="dx-picker-dialog__search-input search-input-wrap w-0 min-w-0 flex-1">
          <ElInput
            v-model="keyword"
            class="w-full"
            :placeholder="isTcmMode ? tcmSearchPlaceholder : '请输入'"
            clearable
            @keyup.enter="handleEncounterSearch"
          />
        </div>
        <ElButton
          class="dx-picker-dialog__search-btn search-btn shrink-0"
          type="primary"
          :loading="tableLoading"
          @click="handleEncounterSearch"
        >
          查询
        </ElButton>
      </div>
      <div class="dx-picker-dialog__table-wrap table-wrap">
        <ElTable
          ref="catalogTableRef"
          v-loading="tableLoading"
          :data="records"
          :row-key="catalogRowKey"
          border
          height="320"
          highlight-current-row
          @current-change="handleEncounterCurrentChange"
          @row-click="handleEncounterRowClick"
          @row-dblclick="handleEncounterRowDblclick"
        >
          <ElTableColumn v-if="isTcmMode" label="类型" width="80" align="center">
            <template #default="{ row }">
              {{ row.sourceEnumText || (row.tcmCatalogKind === 'syndrome' ? '证候' : '疾病') }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="conditionCode" :label="catalogCodeColumnLabel" width="160" />
          <ElTableColumn label="医保码" width="160">
            <template #default="{ row }">
              <span :class="{ 'text-red-500': !hasDiagnosisYbNo(row.ybNo) }">
                {{ formatDiagnosisYbNoDisplay(row.ybNo) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="conditionName" :label="catalogNameColumnLabel" min-width="220" />
        </ElTable>
      </div>
      <template v-if="isTcmMode">
        <div class="dx-picker-dialog__form-row form-row flex items-center gap-2">
          <span
            class="dx-picker-dialog__label label is-required required w-[70px] shrink-0 text-right"
            >疾病</span
          >
          <ElInput
            class="min-w-0 flex-1"
            readonly
            :model-value="tcmDiseasePick?.conditionName ?? ''"
            placeholder="切换到「疾病」后点选"
          />
        </div>
        <div class="dx-picker-dialog__form-row form-row flex items-center gap-2">
          <span
            class="dx-picker-dialog__label label is-required required w-[70px] shrink-0 text-right"
            >证候</span
          >
          <ElInput
            class="min-w-0 flex-1"
            readonly
            :model-value="tcmSyndromePick?.conditionName ?? ''"
            placeholder="切换到「证候」后点选"
          />
        </div>
      </template>
      <div v-else class="dx-picker-dialog__form-row form-row flex items-center gap-2">
        <span class="dx-picker-dialog__label label w-[70px] shrink-0 text-right">选择结果</span>
        <ElInput
          class="min-w-0 flex-1"
          readonly
          :model-value="selectedRow?.conditionName || ''"
          placeholder="请先在上方列表中选择诊断"
        />
      </div>
      <div class="form-row checkbox-row flex flex-wrap items-center gap-5">
        <ElCheckbox v-model="saveAsMainDiagnosis">主诊断</ElCheckbox>
        <template v-if="pickOnly !== true">
          <ElCheckbox v-model="saveAsPersonal">存个人常用</ElCheckbox>
          <ElCheckbox v-model="saveAsDepartment">存科室常用</ElCheckbox>
        </template>
      </div>
      <div class="flex justify-end">
        <ElPagination
          layout="prev, pager, next, total"
          :page-size="pageSize"
          :current-page="pageNo"
          :total="Number(total) || 0"
          @current-change="handleEncounterPageChange"
        />
      </div>
    </div>

    <!-- 医嘱：本地候选 -->
    <div v-else class="dx-picker-dialog diagnosis-dialog flex flex-col gap-3">
      <div class="toolbar-row flex items-center justify-between gap-3">
        <ElRadioGroup v-model="medicineType" class="dx-picker-dialog__kind kind-radio-group">
          <ElRadioButton label="western">西医诊断</ElRadioButton>
          <ElRadioButton label="tcm">中医诊断</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-if="legacyIsTcmMode"
          v-model="legacyTcmCatalogKind"
          class="dx-picker-dialog__tcm-catalog"
        >
          <ElRadioButton label="disease">疾病</ElRadioButton>
          <ElRadioButton label="syndrome">证候</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup v-model="scopeType" size="small">
          <ElRadio label="all">全部诊断</ElRadio>
          <ElRadio label="common">常用诊断</ElRadio>
          <ElRadio label="history">历史诊断</ElRadio>
        </ElRadioGroup>
      </div>
      <div class="dx-picker-dialog__search search-row flex flex-nowrap items-center gap-2">
        <div class="dx-picker-dialog__search-input search-input-wrap w-0 min-w-0 flex-1">
          <ElInput
            v-model="keyword"
            class="w-full"
            :placeholder="
              legacyIsTcmMode
                ? legacyTcmCatalogKind === 'syndrome'
                  ? '证候名称或编码'
                  : '疾病名称或编码'
                : '请输入'
            "
            clearable
          />
        </div>
      </div>
      <div class="dx-picker-dialog__table-wrap table-wrap">
        <ElTable
          :data="displayDiagnoses"
          border
          height="320"
          highlight-current-row
          @current-change="onLegacyTableRow"
          @row-click="onLegacyTableRow"
          @row-dblclick="handleLegacyRowDblclick"
        >
          <ElTableColumn prop="code" label="诊断编码" width="220" />
          <ElTableColumn
            prop="name"
            :label="legacyIsTcmMode ? '名称' : '诊断名称'"
            min-width="380"
          />
        </ElTable>
      </div>
      <template v-if="legacyIsTcmMode">
        <div class="dx-picker-dialog__form-row form-row flex items-center gap-2">
          <span
            class="dx-picker-dialog__label label is-required required w-[70px] shrink-0 text-right"
            >疾病</span
          >
          <ElInput
            class="min-w-0 flex-1"
            readonly
            :model-value="legacyTcmDiseasePick?.conditionName ?? ''"
            placeholder="切换到「疾病」后点选"
          />
        </div>
        <div class="dx-picker-dialog__form-row form-row flex items-center gap-2">
          <span
            class="dx-picker-dialog__label label is-required required w-[70px] shrink-0 text-right"
            >证候</span
          >
          <ElInput
            class="min-w-0 flex-1"
            readonly
            :model-value="legacyTcmSyndromePick?.conditionName ?? ''"
            placeholder="切换到「证候」后点选"
          />
        </div>
      </template>
      <div v-else class="dx-picker-dialog__form-row form-row flex items-center gap-2">
        <span class="dx-picker-dialog__label label w-[70px] shrink-0 text-right">选择结果</span>
        <ElInput
          class="min-w-0 flex-1"
          readonly
          :model-value="selectedDiagnosis?.name || ''"
          placeholder="请先在上方列表中选择诊断"
        />
      </div>
      <div class="form-row checkbox-row flex flex-wrap items-center gap-5">
        <ElCheckbox v-model="saveAsMainDiagnosis">主诊断</ElCheckbox>
        <template v-if="pickOnly !== true">
          <ElCheckbox v-model="saveAsPersonal">存个人常用</ElCheckbox>
          <ElCheckbox v-model="saveAsDepartment">存科室常用</ElCheckbox>
        </template>
      </div>
    </div>

    <template #footer>
      <div v-if="encounterMode" class="dialog-footer flex justify-end gap-2">
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton
          type="primary"
          :disabled="encounterConfirmDisabled"
          :loading="saveLoading"
          @click="() => handleEncounterConfirmSave()"
        >
          {{ encounterConfirmLabel }}
        </ElButton>
      </div>
      <div v-else class="dialog-footer flex justify-end gap-2">
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton
          type="primary"
          :disabled="legacyConfirmDisabled"
          @click="() => handleLegacyConfirm()"
        >
          确认
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>
