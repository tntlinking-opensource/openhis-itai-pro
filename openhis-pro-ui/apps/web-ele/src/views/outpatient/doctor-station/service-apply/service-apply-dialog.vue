<script setup lang="ts">
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
  ServiceApplySelectedItem,
} from './service-apply.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';

import { computed, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import { DiagnosisPickerDialog } from '#/components/doctor-station/diagnosis';
import { useAuthStore } from '#/store';
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import { printServiceApplyForms } from '#/utils/print/print-service-apply';
import * as inpatientServiceApplyApi from '#/views/inpatient/doctor-station/service-apply/inpatient-service-apply.service';

import {
  buildDetailItems,
  buildSaveServiceApplyFormRequest,
  buildServiceApplyDiagnosisOptions,
  buildServiceApplySelectedItemsFromAdviceRows,
  catalogRowCategoryLabel,
  catalogRowKey,
  catalogRowUnitPrice,
  detailTargetIds,
  firstTreeOptionValue,
  mergeCatalogDefaultPerformOrgOptions,
  pickDefaultServiceApplyEncounterDiagnosisId,
  resolveServiceApplyDiagnosisIdFromAdviceRow,
  validateServiceApplySelectedItems,
} from './service-apply.service';
import * as outpatientServiceApplyApi from './service-apply.service';
import type { ServiceApplyDocumentGroup } from './service-apply-doc-groups';
import { inferServiceApplyKindFromAdviceRow } from './service-apply-doc-groups';
import ServiceApplyShellWrap from './service-apply-shell-wrap.vue';

defineOptions({ name: 'ServiceApplyDialog' });

const props = withDefaults(
  defineProps<{
    applyKind?: ServiceApplyKind;
    encounterId?: string;
    modelValue: boolean;
    patient?: ServiceApplyPatientSummary;
    patientId?: string;
    /** 门诊打印抬头：机构名、科室、开单医师 */
    printMeta?: ServiceApplyPrintMeta;
    /** 门诊 / 住院：决定申请单保存与签发所调 HTTP */
    stationScope?: 'inpatient' | 'outpatient';
    /** 外层容器：弹窗 / 抽屉（编辑申请单用抽屉） */
    shell?: 'dialog' | 'drawer';
    /** 编辑模式：传入已聚组的申请单行，打开时回显 */
    editDocumentGroup?: ServiceApplyDocumentGroup | null;
  }>(),
  {
    applyKind: 'lab',
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
    shell: 'dialog',
    editDocumentGroup: null,
    patient: () => ({
      patientName: '—',
      genderText: '—',
      ageText: '—',
      encounterNo: '—',
      feeNatureText: '—',
    }),
    printMeta: () => ({}),
  },
);

const emit = defineEmits<{
  saved: [];
  /** 申请单签发成功 */
  signed: [];
  'update:modelValue': [value: boolean];
}>();

type CatalogTab = 'category' | 'frequent';

const authStore = useAuthStore();

/** 按 stationScope 选择门诊/住院申请单 API 实现 */
const serviceApplyApi = computed(() =>
  props.stationScope === 'inpatient' ? inpatientServiceApplyApi : outpatientServiceApplyApi,
);

const isInpatientScope = computed(() => props.stationScope === 'inpatient');

const keyword = ref('');
const catalogTab = ref<CatalogTab>('frequent');
const activeCategory = ref('');
const catalogItems = ref<DoctorStationAdviceCatalogRowDto[]>([]);
const catalogLoading = ref(false);
const selectedItems = ref<ServiceApplySelectedItem[]>([]);
const actionLoading = ref(false);
const orgId = ref('');
const orgOptions = ref<Array<{ label: string; value: string }>>([]);
const encounterDiagnosisId = ref('');
const encounterDiagnosisRows = ref<EncounterDiagnosisRowDto[]>([]);
const diagnosisDialogVisible = ref(false);
const bodyStructureOptions = ref<TerminologyOptionDto[]>([]);
const specimenDefinitionOptions = ref<TerminologyOptionDto[]>([]);
const savedPrescriptionNo = ref('');

let catalogSearchTimer: ReturnType<typeof setTimeout> | undefined;
let catalogFetchToken = 0;

/** 弹窗/抽屉标题 */
const isEditMode = computed(() => (props.editDocumentGroup?.lines.length ?? 0) > 0);
const dialogTitle = computed(() => {
  const kindLabel = effectiveApplyKind.value === 'lab' ? '检验' : '检查';
  if (isEditMode.value) {
    return `修改${kindLabel}申请单`;
  }
  return `${kindLabel}申请`;
});

/** 编辑回显时的申请单类型 */
const effectiveApplyKind = computed((): ServiceApplyKind => {
  const head = props.editDocumentGroup?.lines[0];
  if (head) {
    const inferred = inferServiceApplyKindFromAdviceRow(head);
    if (inferred) {
      return inferred;
    }
  }
  return props.applyKind;
});

/** 明细下拉：检验样本 / 检查部位 */
const detailOptions = computed(() =>
  effectiveApplyKind.value === 'lab' ? specimenDefinitionOptions.value : bodyStructureOptions.value,
);

/** 明细字段标签 */
const detailFieldLabel = computed(() => (effectiveApplyKind.value === 'lab' ? '样本' : '部位'));

const isLabApply = computed(() => effectiveApplyKind.value === 'lab');

/** 分类列表（由已加载目录去重） */
const categoryList = computed(() => {
  const set = new Set<string>();
  for (const item of catalogItems.value) {
    const label = catalogRowCategoryLabel(item);
    if (label !== '—') set.add(label);
  }
  return [...set];
});

/** 左侧展示的项目列表 */
const filteredCatalog = computed(() => {
  let list = catalogItems.value;
  if (catalogTab.value === 'category' && activeCategory.value) {
    list = list.filter((item) => catalogRowCategoryLabel(item) === activeCategory.value);
  }
  return list;
});

/** 已选目录行键集合，用于高亮左侧 */
const selectedCatalogRowKeys = computed(
  () => new Set(selectedItems.value.map((row) => row.catalogRowKey)),
);

/** 已选合计金额（6 位加总后 2 位展示；入账以后端为准） */
const selectedTotalAmount = computed(() =>
  sumMoneyThenSettle(
    selectedItems.value.map((row) => calcMoneyLineTotal(row.unitPrice, row.quantity)),
  ),
);

/** 就诊/患者门控是否就绪 */
const contextReady = computed(() => {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  return Boolean(encounterId && patientId);
});

/** 整单诊断下拉（当前就诊已保存诊断） */
const diagnosisOptions = computed(() =>
  buildServiceApplyDiagnosisOptions(encounterDiagnosisRows.value),
);

/** 金额格式化（纯展示） */
function formatMoney(value: number): string {
  return formatMoneyDisplay(value);
}

/** 生成已选行唯一 key */
function nextRowKey(catalogKey: string): string {
  return `${catalogKey}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** 将目录行转为已选行（字段对齐 ServiceApplyFormLineRequest） */
function buildSelectedRow(row: DoctorStationAdviceCatalogRowDto): ServiceApplySelectedItem {
  const defaultTargetId =
    props.applyKind === 'lab'
      ? String(specimenDefinitionOptions.value[0]?.value ?? '').trim()
      : firstTreeOptionValue(bodyStructureOptions.value);
  const key = catalogRowKey(row);
  return {
    rowKey: nextRowKey(key),
    catalogRowKey: key,
    activityDefinitionId: String(row.productDefinitionId ?? '').trim(),
    activityName: String(row.name ?? '').trim(),
    quantity: 1,
    unitCode: String(row.unitCode ?? '').trim(),
    unitPrice: catalogRowUnitPrice(row),
    detailItems: defaultTargetId
      ? buildDetailItems(effectiveApplyKind.value, [defaultTargetId])
      : [],
  };
}

/** 刷新整单诊断下拉并默认主诊断 */
async function refreshEncounterDiagnosisOptions() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  if (!encounterId || !patientId) {
    encounterDiagnosisRows.value = [];
    encounterDiagnosisId.value = '';
    return;
  }
  const rows = await serviceApplyApi.value.loadServiceApplyEncounterDiagnosisRows(
    encounterId,
    patientId,
  );
  encounterDiagnosisRows.value = rows;
  const options = buildServiceApplyDiagnosisOptions(rows);
  const current = String(encounterDiagnosisId.value ?? '').trim();
  if (current && options.some((item) => item.value === current)) {
    return;
  }
  encounterDiagnosisId.value = pickDefaultServiceApplyEncounterDiagnosisId(rows, options);
}

/** 打开诊断录入弹窗 */
function openDiagnosisDialog() {
  diagnosisDialogVisible.value = true;
}

/** 诊断保存后刷新下拉并选中最新主诊断 */
async function handleDiagnosisPickerSaved() {
  await refreshEncounterDiagnosisOptions();
}

/** 刷新整单执行科室下拉（全局字段，选中后作用于清单全部项目） */
async function refreshOrgOptions() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  const activityDefinitionIds = selectedItems.value.map((row) => row.activityDefinitionId);
  if (!encounterId || !patientId || activityDefinitionIds.length === 0) {
    orgOptions.value = [];
    return;
  }
  const merged = await serviceApplyApi.value.mergeServiceApplyOrgOptions({
    encounterId,
    patientId,
    activityDefinitionIds,
  });
  orgOptions.value = mergeCatalogDefaultPerformOrgOptions(
    merged,
    catalogItems.value,
    selectedItems.value.map((row) => row.catalogRowKey),
  );
  if (!orgId.value && orgOptions.value.length > 0) {
    orgId.value = orgOptions.value[0]?.value ?? '';
  }
}

/** 加载初始化下拉与目录 */
async function loadPanelData() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  if (!encounterId || !patientId) return;

  const token = ++catalogFetchToken;
  catalogLoading.value = true;
  try {
    const [initOpts, catalogRes] = await Promise.all([
      serviceApplyApi.value.loadServiceApplyInitOptions(encounterId, patientId),
      serviceApplyApi.value.loadServiceApplyCatalog({
        encounterId,
        patientId,
        applyKind: effectiveApplyKind.value,
        keyword: keyword.value,
        organizationUnitId: resolveOrganizationUnitId(),
      }),
      refreshEncounterDiagnosisOptions(),
    ]);
    if (token !== catalogFetchToken) return;
    bodyStructureOptions.value = initOpts.bodyStructureOptions;
    specimenDefinitionOptions.value = initOpts.specimenDefinitionOptions;
    catalogItems.value = catalogRes.rows;
  } catch {
    if (token !== catalogFetchToken) return;
    bodyStructureOptions.value = [];
    specimenDefinitionOptions.value = [];
    catalogItems.value = [];
    encounterDiagnosisRows.value = [];
    encounterDiagnosisId.value = '';
    ElMessage.error('加载申请项目失败');
  } finally {
    if (token === catalogFetchToken) {
      catalogLoading.value = false;
    }
  }
}

/** 取当前机构上下文 */
function resolveOrganizationUnitId(): string | undefined {
  const orgRaw = authStore.getSelectedOrganization();
  if (orgRaw === null || orgRaw === undefined || String(orgRaw).trim() === '') {
    return undefined;
  }
  return String(orgRaw).trim();
}

/** 点击左侧目录行：加入右侧清单 */
async function handleAddCatalogItem(row: DoctorStationAdviceCatalogRowDto) {
  const key = catalogRowKey(row);
  if (selectedCatalogRowKeys.value.has(key)) {
    ElMessage.warning('该项目已在申请清单中');
    return;
  }
  selectedItems.value = [...selectedItems.value, buildSelectedRow(row)];
  const defaultOrgId = String(row.defaultPerformOrgId ?? '').trim();
  if (defaultOrgId && !orgId.value) {
    orgId.value = defaultOrgId;
  }
  await refreshOrgOptions();
}

/** 从右侧清单移除一行 */
async function handleRemoveSelected(rowKey: string) {
  selectedItems.value = selectedItems.value.filter((row) => row.rowKey !== rowKey);
  await refreshOrgOptions();
}

/** 清空已选清单 */
async function handleClearSelected() {
  selectedItems.value = [];
  orgId.value = '';
  savedPrescriptionNo.value = '';
  await refreshOrgOptions();
}

/** 关闭弹窗 */
function handleClose() {
  emit('update:modelValue', false);
}

/** 校验并组装申请单保存请求 */
function buildPersistApplyFormRequest() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  const validationMsg = validateServiceApplySelectedItems(
    selectedItems.value,
    orgId.value,
    encounterDiagnosisId.value,
  );
  if (validationMsg) {
    throw new Error(validationMsg);
  }
  const request = buildSaveServiceApplyFormRequest({
    encounterId,
    patientId,
    applyKind: effectiveApplyKind.value,
    orgId: orgId.value,
    encounterDiagnosisId: encounterDiagnosisId.value,
    encounterDiagnosisRows: encounterDiagnosisRows.value,
    previousPrescriptionNo: savedPrescriptionNo.value || undefined,
    items: selectedItems.value,
  });
  return { encounterId, patientId, request };
}

/** 保存申请单（DRAFT） */
async function persistApplyForm(): Promise<{
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
  request: ReturnType<typeof buildSaveServiceApplyFormRequest>;
  requestIds: string[];
}> {
  const { encounterId, patientId, request } = buildPersistApplyFormRequest();
  const result = await serviceApplyApi.value.saveServiceApplyForm(request);
  savedPrescriptionNo.value = result.prescriptionNo;
  return { encounterId, patientId, request, ...result };
}

/** 签发申请单（保存 → 签发生效） */
async function executeSignRequests(): Promise<{
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
}> {
  const { encounterId, patientId, requestIds } = await persistApplyForm();
  await serviceApplyApi.value.signServiceApplyRequests({ encounterId, patientId, requestIds });
  return {
    encounterId,
    patientId,
    prescriptionNo: savedPrescriptionNo.value,
  };
}

/** 签发成功后按弹窗内已选项目打印（非静默，可弹打印机选择） */
async function printSignedApplyForm(signResult: {
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
}) {
  const performOrgName = orgOptions.value.find((item) => item.value === orgId.value)?.label ?? '';
  const results = await printServiceApplyForms({
    applyKind: effectiveApplyKind.value,
    encounterId: signResult.encounterId,
    patientId: signResult.patientId,
    prescriptionNo: signResult.prescriptionNo,
    patient: props.patient,
    items: [...selectedItems.value],
    performOrgId: orgId.value,
    performOrgName,
    encounterDiagnosisId: encounterDiagnosisId.value,
    encounterDiagnosisRows: encounterDiagnosisRows.value,
    bodyStructureOptions: bodyStructureOptions.value,
    specimenDefinitionOptions: specimenDefinitionOptions.value,
    printMeta: props.printMeta,
  });
  const successCount = results.filter((item) => item.ok).length;
  if (successCount > 0) {
    ElMessage.success(`已提交 ${successCount} 张申请单打印`);
  }
}

/** 暂存申请单 */
async function handleSaveDraft() {
  if (!contextReady.value) {
    ElMessage.warning('缺少就诊或患者信息');
    return;
  }
  actionLoading.value = true;
  try {
    await persistApplyForm();
    ElMessage.success('申请单已暂存');
    emit('saved');
  } catch (error: unknown) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '暂存失败';
    ElMessage.error(msg);
  } finally {
    actionLoading.value = false;
  }
}

/** 签发申请单 */
async function handleSign() {
  if (!contextReady.value) {
    ElMessage.warning('缺少就诊或患者信息');
    return;
  }
  actionLoading.value = true;
  try {
    await executeSignRequests();
    ElMessage.success('签发成功');
    emit('saved');
    emit('signed');
    handleClose();
  } catch (error: unknown) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '签发失败';
    ElMessage.error(msg);
  } finally {
    actionLoading.value = false;
  }
}

/** 签发并打印（门诊：签发成功后立即打印；住院仅签发） */
async function handleSignAndPrint() {
  if (!contextReady.value) {
    ElMessage.warning('缺少就诊或患者信息');
    return;
  }
  actionLoading.value = true;
  try {
    const signResult = await executeSignRequests();
    ElMessage.success('签发成功');
    if (!isInpatientScope.value) {
      await printSignedApplyForm(signResult);
    }
    emit('saved');
    emit('signed');
    handleClose();
  } catch (error: unknown) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '签发失败';
    ElMessage.error(msg);
  } finally {
    actionLoading.value = false;
  }
}

/** 切换 Tab 时重置分类筛选 */
function handleCatalogTabChange(tab: CatalogTab) {
  catalogTab.value = tab;
  if (tab !== 'category') {
    activeCategory.value = '';
  }
}

/** 更新当前行 detailItems */
function writeDetailTargetIds(row: ServiceApplySelectedItem, ids: string[]) {
  row.detailItems = buildDetailItems(effectiveApplyKind.value, ids);
}

/** 编辑模式：将列表行回显到右侧清单 */
async function hydrateFromEditDocumentGroup(group: ServiceApplyDocumentGroup) {
  savedPrescriptionNo.value = group.applyFormNo;
  selectedItems.value = buildServiceApplySelectedItemsFromAdviceRows(
    group.lines,
    effectiveApplyKind.value,
  );
  const head = group.lines[0];
  orgId.value = String(head?.performOrgId ?? '').trim();
  const options = buildServiceApplyDiagnosisOptions(encounterDiagnosisRows.value);
  const matchedDiagnosisId = resolveServiceApplyDiagnosisIdFromAdviceRow(
    String(head?.diagnosisSummary ?? ''),
    options,
  );
  if (matchedDiagnosisId) {
    encounterDiagnosisId.value = matchedDiagnosisId;
  } else {
    encounterDiagnosisId.value = pickDefaultServiceApplyEncounterDiagnosisId(
      encounterDiagnosisRows.value,
      options,
    );
  }
  await refreshOrgOptions();
  const performOrgName = String(head?.performOrgName ?? '').trim();
  if (orgId.value && performOrgName) {
    const existing = orgOptions.value.find((item) => item.value === orgId.value);
    if (existing) {
      existing.label = performOrgName;
    } else {
      orgOptions.value.unshift({ value: orgId.value, label: performOrgName });
    }
  }
}

/** 弹窗打开时重置为初始态并加载数据 */
let suppressKeywordReload = false;

function resetPanelState() {
  suppressKeywordReload = true;
  keyword.value = '';
  catalogTab.value = 'frequent';
  activeCategory.value = '';
  selectedItems.value = [];
  actionLoading.value = false;
  orgId.value = '';
  orgOptions.value = [];
  encounterDiagnosisId.value = '';
  encounterDiagnosisRows.value = [];
  savedPrescriptionNo.value = '';
  catalogItems.value = [];
  suppressKeywordReload = false;
}

watch(
  () =>
    [
      props.modelValue,
      props.applyKind,
      props.encounterId,
      props.patientId,
      props.editDocumentGroup?.applyFormNo,
    ] as const,
  ([open, kind, encounterId, patientId], prev) => {
    if (!open || !contextReady.value) return;
    const wasOpen = prev?.[0] ?? false;
    const contextChanged =
      !prev || prev[1] !== kind || prev[2] !== encounterId || prev[3] !== patientId;
    const editFormNo = props.editDocumentGroup?.applyFormNo ?? '';
    const editChanged = prev?.[4] !== editFormNo;
    if ((!wasOpen && open) || (wasOpen && (contextChanged || editChanged))) {
      resetPanelState();
      void (async () => {
        await loadPanelData();
        const group = props.editDocumentGroup;
        if (group && group.lines.length > 0) {
          await hydrateFromEditDocumentGroup(group);
        }
      })();
    }
  },
);

watch(keyword, () => {
  if (suppressKeywordReload || !props.modelValue || !contextReady.value) return;
  if (catalogSearchTimer) clearTimeout(catalogSearchTimer);
  catalogSearchTimer = setTimeout(() => {
    void loadPanelData();
  }, 300);
});
</script>

<template>
  <ServiceApplyShellWrap
    :model-value="modelValue"
    :shell="shell"
    :title="dialogTitle"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="service-apply-root" :class="shell === 'drawer' ? 'service-apply-root--drawer' : ''">
      <div class="patient-strip">
        <div class="patient-strip__main">
          <span class="patient-strip__name">{{ patient.patientName }}</span>
          <span class="patient-strip__meta">{{ patient.genderText }}</span>
          <span class="patient-strip__meta">{{ patient.ageText }}</span>
          <span class="patient-strip__divider">|</span>
          <span class="patient-strip__label">就诊号</span>
          <span class="patient-strip__value">{{ patient.encounterNo }}</span>
        </div>
        <ElTag size="small" effect="light" type="info">{{ patient.feeNatureText }}</ElTag>
        <ElTag v-if="isInpatientScope" size="small" effect="plain" type="warning"> 临时医嘱 </ElTag>
      </div>

      <div v-if="!contextReady" class="context-hint">请先选择患者后再开立申请单</div>

      <div v-else class="workspace-panels">
        <section class="catalog-panel">
          <div class="catalog-panel__toolbar">
            <ElInput
              v-model="keyword"
              clearable
              placeholder="拼音 / 名称检索"
              :prefix-icon="Search"
              class="catalog-panel__search"
            />
          </div>

          <div class="catalog-panel__tabs">
            <button
              type="button"
              class="catalog-tab"
              :class="{ 'catalog-tab--active': catalogTab === 'frequent' }"
              @click="handleCatalogTabChange('frequent')"
            >
              全部
            </button>
            <button
              type="button"
              class="catalog-tab"
              :class="{ 'catalog-tab--active': catalogTab === 'category' }"
              @click="handleCatalogTabChange('category')"
            >
              分类
            </button>
          </div>

          <div v-if="catalogTab === 'category'" class="catalog-panel__categories">
            <button
              type="button"
              class="category-chip"
              :class="{ 'category-chip--active': !activeCategory }"
              @click="activeCategory = ''"
            >
              全部
            </button>
            <button
              v-for="cat in categoryList"
              :key="cat"
              type="button"
              class="category-chip"
              :class="{ 'category-chip--active': activeCategory === cat }"
              @click="activeCategory = cat"
            >
              {{ cat }}
            </button>
          </div>

          <div v-loading="catalogLoading" class="catalog-panel__list">
            <button
              v-for="item in filteredCatalog"
              :key="catalogRowKey(item)"
              type="button"
              class="catalog-item"
              :class="{ 'catalog-item--selected': selectedCatalogRowKeys.has(catalogRowKey(item)) }"
              @click="handleAddCatalogItem(item)"
            >
              <div class="catalog-item__head">
                <span class="catalog-item__name">{{ item.name }}</span>
                <span class="catalog-item__price"
                  >¥{{ formatMoney(catalogRowUnitPrice(item)) }}</span
                >
              </div>
              <div class="catalog-item__sub">
                <span>{{ catalogRowCategoryLabel(item) }}</span>
                <span v-if="item.unitName">{{ item.unitName }}</span>
              </div>
            </button>

            <ElEmpty
              v-if="!catalogLoading && filteredCatalog.length === 0"
              description="未找到匹配项目"
              :image-size="72"
            />
          </div>
        </section>

        <section class="cart-panel">
          <div class="cart-panel__header">
            <div class="cart-panel__title">
              申请清单
              <span class="cart-panel__count">{{ selectedItems.length }} 项</span>
            </div>
            <div class="cart-panel__total">
              合计
              <span class="cart-panel__amount">¥{{ formatMoney(selectedTotalAmount) }}</span>
            </div>
          </div>

          <div class="cart-panel__form-fields">
            <span class="cart-field__label">执行科室</span>
            <ElSelect v-model="orgId" class="cart-panel__field-select" placeholder="请选择">
              <ElOption
                v-for="org in orgOptions"
                :key="org.value"
                :label="org.label"
                :value="org.value"
              />
            </ElSelect>
            <span class="cart-field__label">诊断</span>
            <div class="cart-panel__diagnosis">
              <ElSelect
                v-model="encounterDiagnosisId"
                filterable
                clearable
                class="cart-panel__field-select"
                placeholder="请选择诊断"
              >
                <ElOption
                  v-for="item in diagnosisOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
              <ElButton link type="primary" @click="openDiagnosisDialog">添加诊断</ElButton>
            </div>
          </div>

          <div class="cart-panel__list">
            <div v-for="row in selectedItems" :key="row.rowKey" class="cart-item">
              <div class="cart-item__top">
                <div class="cart-item__info">
                  <div class="cart-item__title-row">
                    <span class="cart-item__name">{{ row.activityName }}</span>
                    <span class="cart-item__price">
                      ¥{{ formatMoney(calcMoneyLineTotal(row.unitPrice, row.quantity)) }}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  class="cart-item__remove"
                  @click="handleRemoveSelected(row.rowKey)"
                >
                  ×
                </button>
              </div>

              <div class="cart-item__fields">
                <span class="cart-field__label">数量</span>
                <ElInputNumber
                  v-model="row.quantity"
                  :min="1"
                  :max="99"
                  :controls="false"
                  class="cart-field__qty"
                />
                <span class="cart-field__label">{{ detailFieldLabel }}</span>
                <ElSelect
                  v-if="isLabApply"
                  :model-value="detailTargetIds(row.detailItems)"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  class="cart-field__detail"
                  :placeholder="`请选择${detailFieldLabel}`"
                  @update:model-value="(v: string[]) => writeDetailTargetIds(row, v)"
                >
                  <ElOption
                    v-for="opt in detailOptions"
                    :key="String(opt.value ?? '')"
                    :label="String(opt.label ?? opt.value ?? '')"
                    :value="String(opt.value ?? '')"
                  />
                </ElSelect>
                <ElTreeSelect
                  v-else
                  :model-value="detailTargetIds(row.detailItems)"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  filterable
                  check-strictly
                  class="cart-field__detail"
                  :data="bodyStructureOptions"
                  :props="{ value: 'value', label: 'label', children: 'children' }"
                  node-key="value"
                  :placeholder="`请选择${detailFieldLabel}`"
                  @update:model-value="(v: string[]) => writeDetailTargetIds(row, v)"
                />
              </div>
              <div class="cart-item__remark">
                <span class="cart-field__label">备注</span>
                <ElInput v-model="row.remark" placeholder="选填" maxlength="200" />
              </div>
            </div>

            <ElEmpty
              v-if="selectedItems.length === 0"
              class="cart-panel__empty"
              description="从左侧选择检验/检查项目"
              :image-size="80"
            >
              <template #description>
                <p class="cart-empty-tip">点击左侧项目即可加入清单</p>
              </template>
            </ElEmpty>
          </div>
        </section>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <div class="dialog-footer__left">
          <ElButton v-if="selectedItems.length > 0" link type="danger" @click="handleClearSelected">
            清空清单
          </ElButton>
        </div>
        <div class="dialog-footer__right">
          <ElButton @click="handleClose">取消</ElButton>
          <ElButton :loading="actionLoading" :disabled="!contextReady" @click="handleSaveDraft">
            暂存
          </ElButton>
          <ElButton
            type="primary"
            :loading="actionLoading"
            :disabled="!contextReady"
            @click="handleSign"
          >
            签发
          </ElButton>
          <ElButton
            v-if="!isInpatientScope"
            type="primary"
            plain
            :loading="actionLoading"
            :disabled="!contextReady"
            @click="handleSignAndPrint"
          >
            签发并打印
          </ElButton>
        </div>
      </div>
    </template>
  </ServiceApplyShellWrap>

  <DiagnosisPickerDialog
    v-model="diagnosisDialogVisible"
    :station-scope="props.stationScope"
    :encounter-id="encounterId"
    :patient-id="patientId"
    :existing-diagnosis-list="encounterDiagnosisRows"
    @saved="handleDiagnosisPickerSaved"
  />
</template>

<style scoped>
.service-apply-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 700px;
  overflow: hidden;
}

.service-apply-root--drawer {
  height: calc(100vh - 148px);
  min-height: 560px;
}

.context-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.patient-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  flex-shrink: 0;
  border-radius: 8px;
  background: linear-gradient(90deg, #f0f9ff 0%, #fff 100%);
  border: 1px solid #dbeafe;
}

.patient-strip__main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.patient-strip__name {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.patient-strip__meta,
.patient-strip__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.patient-strip__value {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.patient-strip__divider {
  color: var(--el-border-color);
}

.workspace-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  flex: 1;
  min-height: 0;
  height: 0;
}

.catalog-panel,
.cart-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.catalog-panel__toolbar {
  padding: 10px 12px 0;
}

.catalog-panel__search {
  width: 100%;
}

.catalog-panel__tabs {
  display: flex;
  gap: 4px;
  padding: 10px 12px 0;
}

.catalog-tab {
  padding: 6px 14px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.15s ease;
}

.catalog-tab:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.catalog-tab--active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
  font-weight: 600;
}

.catalog-panel__categories {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px 0;
}

.category-chip {
  padding: 4px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.category-chip--active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.catalog-panel__list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.catalog-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition: all 0.15s ease;
}

.catalog-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: #f8fbff;
  box-shadow: 0 2px 8px rgb(64 158 255 / 8%);
}

.catalog-item--selected {
  border-color: var(--el-color-primary-light-5);
  background: #f0f9ff;
}

.catalog-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.catalog-item__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.catalog-item__price {
  font-size: 13px;
  font-weight: 700;
  color: #0f766e;
  flex-shrink: 0;
}

.catalog-item__sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cart-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  flex-shrink: 0;
  background: #eaf4ff;
  border-bottom: 1px solid #dbeafe;
}

.cart-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.cart-panel__count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.cart-panel__total {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cart-panel__amount {
  margin-left: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #0f766e;
}

.cart-panel__form-fields {
  display: grid;
  grid-template-columns: 72px 1fr 48px 1fr;
  align-items: center;
  gap: 8px 12px;
  padding: 8px 12px;
  border-bottom: 1px solid #dbeafe;
  background: #f8fbff;
}

.cart-panel__field-select {
  width: 100%;
}

.cart-panel__diagnosis {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.cart-panel__diagnosis .cart-panel__field-select {
  flex: 1;
  min-width: 0;
}

.cart-panel__list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cart-item {
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fafcff;
}

.cart-item__top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.cart-item__info {
  flex: 1;
  min-width: 0;
}

.cart-item__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cart-item__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cart-item__price {
  font-size: 13px;
  font-weight: 700;
  color: #0f766e;
  flex-shrink: 0;
}

.cart-item__remove {
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
}

.cart-item__remove:hover {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.cart-item__fields {
  display: grid;
  grid-template-columns: 56px 76px 56px minmax(0, 1fr);
  align-items: center;
  gap: 0 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e8f0fe;
}

.cart-item__remark {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.cart-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: right;
  white-space: nowrap;
}

.cart-field__qty {
  width: 76px;
}

.cart-field__qty :deep(.el-input__wrapper) {
  width: 76px;
}

.cart-field__detail {
  width: 100%;
  min-width: 0;
}

.cart-panel__empty {
  flex: 1;
  justify-content: center;
}

.cart-empty-tip {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-footer__right {
  display: flex;
  gap: 8px;
}
</style>
