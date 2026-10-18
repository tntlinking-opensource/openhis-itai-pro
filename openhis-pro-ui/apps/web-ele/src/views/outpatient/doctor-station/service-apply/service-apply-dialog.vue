<script setup lang="ts">
import type { ServiceApplyDocumentGroup } from './service-apply-doc-groups';
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
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';
import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import {
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_WORKSTATION_INPATIENT,
  ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import { useEnsureClinicalReception } from '#/components/doctor-station';
import {
  AdviceDiagnosisBindPanel,
  DiagnosisPickerDialog,
} from '#/components/doctor-station/diagnosis';
import {
  joinEncounterDiagnosisIds,
  mapEncounterRowToDiagnosisItem,
  parseEncounterDiagnosisIds,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  clearEncounterDiagnosisSession,
  readEncounterDiagnosisSession,
  rememberEncounterDiagnosisSession,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-session';
import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import { resolveServicePreviewBillQuantity } from '../advice/services/service-quantity-calc';
import { rememberOrderTemplateOriginRequestIds } from '../advice/services/order-template-origin-cache.service';
import { printServiceApplyForms } from '#/utils/print/print-service-apply';
import { SERVICE_REQUEST_PRIORITY_EM } from '#/utils/print/constants/service-request-priority';
import { syncLineRemarkWithRehabTreatmentCourse } from '#/utils/service-apply-display';
import { decodeServiceApplyDetailIds } from '#/views/basic/ordertemplate/order-template-lines';
import * as inpatientServiceApplyApi from '#/views/inpatient/doctor-station/service-apply/inpatient-service-apply.service';
import { SERVICE_APPLY_EXAM_DEFAULT_FIRST_BODY_PART } from '#/views/outpatient/doctor-station/service-apply/service-apply-default-body-part.config';

import { inferServiceApplyKindFromAdviceRow } from './service-apply-doc-groups';
import ServiceApplyShellWrap from './service-apply-shell-wrap.vue';
import {
  isServiceApplyDetailRequired,
  SERVICE_APPLY_CATEGORY_BY_KIND,
} from './service-apply.constants';
import ServiceApplyOrderTemplatePanel from './service-apply-order-template-panel.vue';
import {
  buildDetailItems,
  buildSaveServiceApplyFormRequest,
  buildServiceApplyBindTriggerLines,
  buildServiceApplyDiagnosisOptions,
  buildServiceApplySelectedItemsFromAdviceRows,
  catalogRowActivityTypeCode,
  catalogRowActivityTypeLabel,
  catalogRowCategoryLabel,
  catalogRowChrgitmLvText,
  catalogRowKey,
  catalogRowUnitPrice,
  detailTargetIds,
  filterSpecimenDefinitionOptions,
  firstTreeOptionValue,
  matchBodyStructureTreeFilterNode,
  mergeCatalogDefaultPerformOrgOptions,
  pickCatalogDefaultPerformOrgId,
  pickDefaultServiceApplyEncounterDiagnosisIds,
  resolveServiceApplyDiagnosisIdsFromAdviceRow,
  resolveServiceApplyEncounterDiagnosisIds,
  resolveServiceApplyDraftBindsBeforeSign,
  resolveServiceApplyKindLabel,
  resolveServiceApplyLineExecutionCount,
  validateServiceApplySelectedItems,
} from './service-apply.service';
import * as outpatientServiceApplyApi from './service-apply.service';

defineOptions({ name: 'ServiceApplyDialog' });

const props = withDefaults(
  defineProps<{
    applyKind?: ServiceApplyKind;
    /** 编辑模式：传入已聚组的申请单行，打开时回显 */
    editDocumentGroup?: null | ServiceApplyDocumentGroup;
    encounterId?: string;
    modelValue: boolean;
    patient?: ServiceApplyPatientSummary;
    patientId?: string;
    /** 门诊打印抬头：机构名、科室、开单医师 */
    printMeta?: ServiceApplyPrintMeta;
    /** 外层容器：弹窗 / 抽屉（新建与修改默认弹窗） */
    shell?: 'dialog' | 'drawer';
    /** 门诊 / 住院：决定申请单保存与签发所调 HTTP */
    stationScope?: 'inpatient' | 'outpatient';
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
const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

/** 按 stationScope 选择门诊/住院申请单 API 实现 */
const serviceApplyApi = computed(() =>
  props.stationScope === 'inpatient' ? inpatientServiceApplyApi : outpatientServiceApplyApi,
);

const isInpatientScope = computed(() => props.stationScope === 'inpatient');

const keyword = ref('');
const catalogTab = ref<CatalogTab>('frequent');
/** 申请单分类 chips 当前选中的业务类型码；空=不限 */
const activeTypeCode = ref('');
/** 分类 chips：来自未按业务类型收窄的目录页去重 */
const typeChipOptions = ref<Array<{ code: string; label: string }>>([]);
const catalogItems = ref<DoctorStationAdviceCatalogRowDto[]>([]);
const catalogLoading = ref(false);
const selectedItems = ref<ServiceApplySelectedItem[]>([]);
const actionLoading = ref(false);
const orgId = ref('');
const orgOptions = ref<Array<{ label: string; value: string }>>([]);
const encounterDiagnosisIds = ref<string[]>([]);
const encounterDiagnosisRows = ref<EncounterDiagnosisRowDto[]>([]);
const diagnosisDialogVisible = ref(false);
const bodyStructureOptions = ref<TerminologyOptionDto[]>([]);
const specimenDefinitionOptions = ref<TerminologyOptionDto[]>([]);
/** 样本下拉自定义过滤关键字（空则展示全量） */
const specimenFilterKeyword = ref('');
/** 本窗最近一次保存生成的全部 AR（拆多张再保存须整批传给后端删旧） */
const savedPrescriptionNos = ref<string[]>([]);
/** 首张 AR（兼容展示/单号编辑回显） */
const savedPrescriptionNo = ref('');

let catalogSearchTimer: ReturnType<typeof setTimeout> | undefined;
let catalogFetchToken = 0;

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

/** 弹窗/抽屉标题 */
const isEditMode = computed(() => (props.editDocumentGroup?.lines.length ?? 0) > 0);
const dialogTitle = computed(() => {
  const kindLabel = resolveServiceApplyKindLabel(effectiveApplyKind.value);
  if (isEditMode.value) {
    return `修改${kindLabel}申请单`;
  }
  return `${kindLabel}申请`;
});

/** 明细下拉：检验样本（按关键字过滤）/ 检查·治疗部位树 */
const detailOptions = computed(() => {
  if (effectiveApplyKind.value === 'lab') {
    return filterSpecimenDefinitionOptions(
      specimenDefinitionOptions.value,
      specimenFilterKeyword.value,
    );
  }
  return bodyStructureOptions.value;
});

/** 明细字段标签 */
const detailFieldLabel = computed(() => (effectiveApplyKind.value === 'lab' ? '样本' : '部位'));

/** 部位/样本均选填 */
const detailRequired = computed(() => isServiceApplyDetailRequired(effectiveApplyKind.value));

const isLabApply = computed(() => effectiveApplyKind.value === 'lab');

/** 右侧空清单提示 */
const emptyCartHint = computed(
  () => `从左侧选择${resolveServiceApplyKindLabel(effectiveApplyKind.value)}项目`,
);

/** 分类 chips（业务类型码+展示名） */
const categoryList = computed(() => typeChipOptions.value);

/** 左侧展示的项目列表（服务端已按业务类型收窄时不再二次过滤） */
const filteredCatalog = computed(() => catalogItems.value);

/** 已选目录行键集合，用于高亮左侧 */
const selectedCatalogRowKeys = computed(
  () => new Set(selectedItems.value.map((row) => row.catalogRowKey)),
);

/** 已选合计金额（单次量×本行次数×单价；入账以后端为准） */
const selectedTotalAmount = computed(() =>
  sumMoneyThenSettle(
    selectedItems.value.map((row) =>
      calcMoneyLineTotal(
        row.unitPrice,
        resolveServicePreviewBillQuantity(
          row.quantity,
          resolveServiceApplyLineExecutionCount(row.executionCount),
        ),
      ),
    ),
  ),
);

/** 就诊/患者门控是否就绪 */
const contextReady = computed(() => {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  return Boolean(encounterId && patientId);
});

/** 整单诊断绑定候选项（当前就诊已保存诊断） */
const diagnosisBindOptions = computed((): DiagnosisItem[] => {
  const items: DiagnosisItem[] = [];
  for (const row of encounterDiagnosisRows.value) {
    const item = mapEncounterRowToDiagnosisItem(row);
    if (item) items.push(item);
  }
  return items;
});

/** 金额格式化（纯展示） */
function formatMoney(value: number): string {
  return formatMoneyDisplay(value);
}

/** 样本 Select：自定义过滤（名称/编码/拼音/五笔） */
function handleSpecimenFilter(query: string) {
  specimenFilterKeyword.value = query;
}

/** 样本下拉关闭时清空过滤关键字，避免再次打开仍收窄 */
function handleSpecimenDropdownVisible(open: boolean) {
  if (!open) {
    specimenFilterKeyword.value = '';
  }
}

/** 部位 TreeSelect：节点是否命中过滤关键字 */
function handleBodyStructureFilterNode(keyword: string, data: TerminologyOptionDto): boolean {
  return matchBodyStructureTreeFilterNode(keyword, data);
}

/** 生成已选行唯一 key */
function nextRowKey(catalogKey: string): string {
  return `${catalogKey}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** 当前执行科室展示名（与下拉 label 一致） */
function resolveCurrentPerformOrgName(): string {
  return orgOptions.value.find((item) => item.value === orgId.value)?.label ?? '';
}

/** 治疗申请 + 康复科：同步清单各行备注中的固定疗程说明 */
function syncSelectedItemsRehabCourseRemark() {
  if (effectiveApplyKind.value !== 'treatment') {
    return;
  }
  const performOrgName = resolveCurrentPerformOrgName();
  selectedItems.value = selectedItems.value.map((row) => {
    const nextRemark = syncLineRemarkWithRehabTreatmentCourse(
      row.remark,
      'treatment',
      performOrgName,
    );
    if ((row.remark ?? '') === (nextRemark ?? '')) {
      return row;
    }
    return { ...row, remark: nextRemark };
  });
}

/** 将目录行转为已选行（字段对齐 ServiceApplyFormLineRequest） */
function buildSelectedRow(row: DoctorStationAdviceCatalogRowDto): ServiceApplySelectedItem {
  const kind = effectiveApplyKind.value;
  // 检查默认带部位树首项；检验样本与治疗部位不预填。国基等院点可关预填。
  let defaultTargetId = '';
  if (kind === 'exam' && SERVICE_APPLY_EXAM_DEFAULT_FIRST_BODY_PART) {
    defaultTargetId = firstTreeOptionValue(bodyStructureOptions.value);
  }
  const key = catalogRowKey(row);
  return {
    rowKey: nextRowKey(key),
    catalogRowKey: key,
    activityDefinitionId: String(row.productDefinitionId ?? '').trim(),
    activityName: String(row.name ?? '').trim(),
    quantity: 1,
    // 新建行默认执行次数 1，与原先整单默认一致
    executionCount: 1,
    unitCode: String(row.unitCode ?? '').trim(),
    unitPrice: catalogRowUnitPrice(row),
    chrgitmLvText: catalogRowChrgitmLvText(row) || undefined,
    detailItems: defaultTargetId ? buildDetailItems(kind, [defaultTargetId]) : [],
    // 已选康复科时，新加入行直接带上固定疗程备注
    remark: syncLineRemarkWithRehabTreatmentCourse(undefined, kind, resolveCurrentPerformOrgName()),
    priorityEnum: '',
    packageChildren: row.packageChildren,
  };
}

/** 刷新整单诊断候选项；空白时按「上次选择 / 唯一诊断」默认。 */
async function refreshEncounterDiagnosisOptions() {
  // 住院申请单不展示诊断，不拉候选项
  if (isInpatientScope.value) {
    encounterDiagnosisRows.value = [];
    encounterDiagnosisIds.value = [];
    return;
  }
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  if (!encounterId || !patientId) {
    encounterDiagnosisRows.value = [];
    encounterDiagnosisIds.value = [];
    return;
  }
  const rows = await serviceApplyApi.value.loadServiceApplyEncounterDiagnosisRows(
    encounterId,
    patientId,
  );
  encounterDiagnosisRows.value = rows;
  const options = buildServiceApplyDiagnosisOptions(rows);
  const current = encounterDiagnosisIds.value.filter((id) =>
    options.some((item) => item.value === id),
  );
  if (current.length > 0) {
    encounterDiagnosisIds.value = current;
    return;
  }
  // 编辑回显未命中 / 新建空白：会话上次 → 唯一诊断
  const defaults = pickDefaultServiceApplyEncounterDiagnosisIds(
    rows,
    options,
    readEncounterDiagnosisSession(encounterId),
  );
  encounterDiagnosisIds.value = defaults;
  if (defaults.length > 0) {
    rememberEncounterDiagnosisSession(encounterId, defaults);
  }
}

/** 打开诊断录入弹窗 */
async function openDiagnosisDialog() {
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  diagnosisDialogVisible.value = true;
}

/** 诊断绑定变更：主诊优先排序，并写入会话记忆。 */
function onEncounterDiagnosisIdsChange(value: unknown) {
  const raw = Array.isArray(value) ? value.map((id) => String(id ?? '').trim()) : [];
  const idsText = joinEncounterDiagnosisIds(raw, encounterDiagnosisRows.value);
  const nextIds = parseEncounterDiagnosisIds(idsText);
  encounterDiagnosisIds.value = nextIds;
  rememberEncounterDiagnosisSession(props.encounterId, nextIds);
}

/** 诊断保存后刷新候选项 */
async function handleDiagnosisPickerSaved() {
  await refreshEncounterDiagnosisOptions();
}

/** 「选择诊断」打开时面板已拉取最新列表：同步申请单候选项与已选 id */
function onBindDiagnosisOptionsRefreshed(payload: {
  displayRows: EncounterDiagnosisRowDto[];
  items: DiagnosisItem[];
}) {
  encounterDiagnosisRows.value = payload.displayRows;
  const valid = new Set(
    payload.items.map((item) => String(item.encounterDiagnosisId ?? item.id ?? '').trim()),
  );
  encounterDiagnosisIds.value = encounterDiagnosisIds.value.filter((id) => valid.has(id));
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
  // 有规则用规则并集；没配时接口回落开立科室，不再拉全院
  let merged = await serviceApplyApi.value.mergeServiceApplyOrgOptions({
    encounterId,
    patientId,
    activityDefinitionIds,
  });
  const selectedKeys = selectedItems.value.map((row) => row.catalogRowKey);
  orgOptions.value = mergeCatalogDefaultPerformOrgOptions(merged, catalogItems.value, selectedKeys);
  const allowedIds = new Set(orgOptions.value.map((item) => item.value));
  // 已选若是医院根等非科室，清空后回落目录默认科室或首个科室
  if (orgId.value && !allowedIds.has(orgId.value)) {
    orgId.value = '';
  }
  if (!orgId.value && orgOptions.value.length > 0) {
    orgId.value =
      pickCatalogDefaultPerformOrgId(orgOptions.value, catalogItems.value, selectedKeys) ||
      orgOptions.value[0]?.value ||
      '';
  }
  // 默认/回落后的执行科室可能已是康复科，同步行备注
  syncSelectedItemsRehabCourseRemark();
}

/**
 * 从当前目录页收集业务类型 chips（按 code 去重，保留首次展示名）。
 * 排除与申请单大类同名的类型（如检查申请单下不再出「检查」chip，只留 CT/DR）。
 *
 * @param rows 目录行
 * @param excludeLabel 与当前申请单大类同名的展示文案
 */
function collectTypeChipOptions(
  rows: DoctorStationAdviceCatalogRowDto[],
  excludeLabel?: string,
): Array<{
  code: string;
  label: string;
}> {
  const seen = new Set<string>();
  const options: Array<{ code: string; label: string }> = [];
  const excluded = String(excludeLabel ?? '').trim();
  for (const item of rows) {
    const code = catalogRowActivityTypeCode(item);
    if (!code || seen.has(code)) {
      continue;
    }
    const label = catalogRowActivityTypeLabel(item);
    // 与申请单通道大类同名则跳过，避免 CT/DR 与「检查」平级混排
    if (excluded && label === excluded) {
      continue;
    }
    seen.add(code);
    options.push({ code, label });
  }
  return options;
}

/** 加载初始化下拉与目录 */
async function loadPanelData() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  if (!encounterId || !patientId) return;

  const token = ++catalogFetchToken;
  catalogLoading.value = true;
  try {
    const typeFilter =
      catalogTab.value === 'category' ? String(activeTypeCode.value ?? '').trim() : '';
    const [initOpts, catalogRes] = await Promise.all([
      serviceApplyApi.value.loadServiceApplyInitOptions(encounterId, patientId),
      serviceApplyApi.value.loadServiceApplyCatalog({
        encounterId,
        patientId,
        applyKind: effectiveApplyKind.value,
        keyword: keyword.value,
        organizationUnitId: resolveOrganizationUnitId(),
        typeEnum: typeFilter || undefined,
      }),
      refreshEncounterDiagnosisOptions(),
    ]);
    if (token !== catalogFetchToken) return;
    bodyStructureOptions.value = initOpts.bodyStructureOptions;
    specimenDefinitionOptions.value = initOpts.specimenDefinitionOptions;
    specimenFilterKeyword.value = '';
    catalogItems.value = catalogRes.rows;
    // 申请单已按目录分类圈定（检验/检查/治疗）；chips = 目录页业务类型去重，去掉与大类同名项
    if (!typeFilter) {
      typeChipOptions.value = collectTypeChipOptions(
        catalogRes.rows,
        resolveServiceApplyKindLabel(effectiveApplyKind.value),
      );
    }
    // 编辑回显：列表若未带医保等级，用当前目录页补齐展示文案
    enrichSelectedChrgitmLvFromCatalog();
  } catch {
    if (token !== catalogFetchToken) return;
    bodyStructureOptions.value = [];
    specimenDefinitionOptions.value = [];
    specimenFilterKeyword.value = '';
    catalogItems.value = [];
    encounterDiagnosisRows.value = [];
    encounterDiagnosisIds.value = [];
    ElMessage.error('加载申请项目失败');
  } finally {
    if (token === catalogFetchToken) {
      catalogLoading.value = false;
    }
  }
}

/** 用左侧目录行补齐右侧清单医保等级展示（仅填空，不覆盖已有文案） */
function enrichSelectedChrgitmLvFromCatalog() {
  if (selectedItems.value.length === 0 || catalogItems.value.length === 0) {
    return;
  }
  // 目录键 → 医保等级文案
  const textByKey = new Map<string, string>();
  for (const row of catalogItems.value) {
    const text = catalogRowChrgitmLvText(row);
    if (!text) continue;
    textByKey.set(catalogRowKey(row), text);
  }
  if (textByKey.size === 0) return;
  selectedItems.value = selectedItems.value.map((item) => {
    if (String(item.chrgitmLvText ?? '').trim()) {
      return item;
    }
    const fromCatalog = textByKey.get(item.catalogRowKey);
    if (!fromCatalog) {
      return item;
    }
    return { ...item, chrgitmLvText: fromCatalog };
  });
}

/** 取当前机构上下文 */
function resolveOrganizationUnitId(): string | undefined {
  const orgRaw = authStore.getSelectedOrganization();
  if (orgRaw === null || orgRaw === undefined || String(orgRaw).trim() === '') {
    return undefined;
  }
  return String(orgRaw).trim();
}

/** 套用申请单组套：只加入与当前弹窗类型一致的检查/检验/治疗行 */
async function handleApplyOrderTemplate(header: OrderTemplateHeaderItem) {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  const orderTemplateId = String(header.orderTemplateId ?? '').trim();
  if (!encounterId || !patientId || !orderTemplateId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const categoryCode = SERVICE_APPLY_CATEGORY_BY_KIND[effectiveApplyKind.value];
  actionLoading.value = true;
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
    const matched = (preview?.lines ?? []).filter(
      (line) => String(line.groupKey ?? '').trim() === categoryCode,
    );
    if (matched.length === 0) {
      ElMessage.warning(`该组套没有${resolveServiceApplyKindLabel(effectiveApplyKind.value)}项目`);
      return;
    }
    const addedKeys: string[] = [];
    for (const line of matched) {
      const activityId = String(line.activityDefinitionId ?? '').trim();
      const name = String(line.productName ?? '').trim();
      if (!activityId || !name) {
        continue;
      }
      const catalog = await serviceApplyApi.value.loadServiceApplyCatalog({
        applyKind: effectiveApplyKind.value,
        encounterId,
        patientId,
        keyword: name,
      });
      const hit = catalog.rows.find(
        (row) => String(row.productDefinitionId ?? '').trim() === activityId,
      );
      if (!hit) {
        continue;
      }
      const key = catalogRowKey(hit);
      if (selectedCatalogRowKeys.value.has(key)) {
        continue;
      }
      const selectedRow = buildSelectedRow(hit);
      // 申请单组套套用：签发时不走绑定/合管自动带出
      selectedRow.fromOrderTemplate = true;
      const qty = Number(line.defaultQuantity);
      if (Number.isFinite(qty) && qty > 0) {
        selectedRow.quantity = qty;
      }
      const detailIds = decodeServiceApplyDetailIds(line.dosageText);
      if (detailIds.length > 0) {
        selectedRow.detailItems = buildDetailItems(effectiveApplyKind.value, detailIds);
      }
      selectedItems.value = [...selectedItems.value, selectedRow];
      addedKeys.push(selectedRow.rowKey);
    }
    if (addedKeys.length === 0) {
      ElMessage.warning('组套项目已在清单中，或当前目录未找到对应项目');
      return;
    }
    await refreshOrgOptions();
    ElMessage.success(`已加入 ${addedKeys.length} 项`);
  } catch {
    ElMessage.error('组套套用失败，请稍后重试');
  } finally {
    actionLoading.value = false;
  }
}

/** 点击左侧目录行：加入右侧清单 */
async function handleAddCatalogItem(row: DoctorStationAdviceCatalogRowDto) {
  const key = catalogRowKey(row);
  if (selectedCatalogRowKeys.value.has(key)) {
    ElMessage.warning('该项目已在申请清单中');
    return;
  }
  const selectedRow = buildSelectedRow(row);
  selectedItems.value = [...selectedItems.value, selectedRow];
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
  savedPrescriptionNos.value = [];
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
    encounterDiagnosisIds.value,
    effectiveApplyKind.value,
    {
      requireDiagnosis: !isInpatientScope.value,
    },
  );
  if (validationMsg) {
    throw new Error(validationMsg);
  }
  const request = buildSaveServiceApplyFormRequest({
    encounterId,
    patientId,
    applyKind: effectiveApplyKind.value,
    orgId: orgId.value,
    encounterDiagnosisIds: encounterDiagnosisIds.value,
    encounterDiagnosisRows: encounterDiagnosisRows.value,
    previousPrescriptionNos:
      savedPrescriptionNos.value.length > 0
        ? [...savedPrescriptionNos.value]
        : savedPrescriptionNo.value
          ? [savedPrescriptionNo.value]
          : undefined,
    previousPrescriptionNo: savedPrescriptionNo.value || undefined,
    items: selectedItems.value,
  });
  return { encounterId, patientId, request };
}

/** 保存申请单（DRAFT；可按执行科室拆多张 AR） */
async function persistApplyForm(): Promise<{
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
  prescriptionNos: string[];
  request: ReturnType<typeof buildSaveServiceApplyFormRequest>;
  requestIds: string[];
  requestPrescriptionNos: string[];
}> {
  const { encounterId, patientId, request } = buildPersistApplyFormRequest();
  const result = await serviceApplyApi.value.saveServiceApplyForm(request);
  // 记住本窗全部 AR，同窗再保存时删齐拆单兄弟，避免孤儿 DRAFT
  savedPrescriptionNos.value =
    result.prescriptionNos.length > 0
      ? [...result.prescriptionNos]
      : result.prescriptionNo
        ? [result.prescriptionNo]
        : [];
  savedPrescriptionNo.value =
    result.prescriptionNo || savedPrescriptionNos.value[0] || '';
  rememberEncounterDiagnosisSession(encounterId, encounterDiagnosisIds.value);
  // 组套套用行落库后写入会话标记，供列表签发跳过绑定带出
  const templateOriginRequestIds: string[] = [];
  for (let i = 0; i < selectedItems.value.length; i += 1) {
    const item = selectedItems.value[i];
    const requestId = preserveApiIdForRequest(result.requestIds?.[i]);
    if (!item?.fromOrderTemplate || !requestId) {
      continue;
    }
    templateOriginRequestIds.push(requestId);
  }
  rememberOrderTemplateOriginRequestIds(encounterId, templateOriginRequestIds);
  return { encounterId, patientId, request, ...result };
}

/** 签发申请单（保存 → 绑定确认 → 签发生效；与开立医嘱一致，先弹窗可删无库存耗材） */
async function executeSignRequests(): Promise<{
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
  prescriptionNos: string[];
  requestPrescriptionNos: string[];
  requestIds: string[];
}> {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  const { requestIds, prescriptionNos, requestPrescriptionNos, prescriptionNo } =
    await persistApplyForm();
  const stationScope = isInpatientScope.value ? 'inpatient' : 'outpatient';
  // 耗材绑定按入参行下标对齐 requestIds；组套套用行已排除（BR-ORD-038）
  const bindLines = buildServiceApplyBindTriggerLines(selectedItems.value, requestIds, {
    encounterId,
    encounterDiagnosisIds: resolveServiceApplyEncounterDiagnosisIds(
      encounterDiagnosisIds.value,
      encounterDiagnosisRows.value,
    ),
  });
  // 门诊：签发前弹窗确认绑定（可改量/删行）；住院：适配器恒空
  if (bindLines.length > 0) {
    await resolveServiceApplyDraftBindsBeforeSign({
      encounterId,
      patientId,
      lines: bindLines,
      stationScope,
    });
  }
  await serviceApplyApi.value.signServiceApplyRequests({
    encounterId,
    patientId,
    requestIds,
    stationScope,
  });
  return {
    encounterId,
    patientId,
    prescriptionNo,
    prescriptionNos,
    requestPrescriptionNos,
    requestIds,
  };
}

/** 签发成功后按拆单结果逐张打印（同执行科/共看组一张） */
async function printSignedApplyForm(signResult: {
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
  prescriptionNos: string[];
  requestPrescriptionNos: string[];
}) {
  const performOrgName = orgOptions.value.find((item) => item.value === orgId.value)?.label ?? '';
  const formNos =
    signResult.prescriptionNos.length > 0
      ? signResult.prescriptionNos
      : [signResult.prescriptionNo].filter(Boolean);
  const allResults: Array<{ ok?: boolean; errorCode?: string }> = [];
  for (const formNo of formNos) {
    const itemsForForm = selectedItems.value.filter((_, index) => {
      const lineNo = signResult.requestPrescriptionNos[index];
      return !lineNo || lineNo === formNo;
    });
    if (itemsForForm.length === 0) {
      continue;
    }
    const results = await printServiceApplyForms({
      applyKind: effectiveApplyKind.value,
      encounterId: signResult.encounterId,
      patientId: signResult.patientId,
      prescriptionNo: formNo,
      patient: props.patient,
      items: [...itemsForForm],
      performOrgId: orgId.value,
      performOrgName,
      encounterDiagnosisIds: encounterDiagnosisIds.value,
      encounterDiagnosisRows: encounterDiagnosisRows.value,
      bodyStructureOptions: bodyStructureOptions.value,
      specimenDefinitionOptions: specimenDefinitionOptions.value,
      printMeta: props.printMeta,
    });
    allResults.push(...results);
    if (results.some((item) => item.errorCode === 'USER_CANCELLED')) {
      return;
    }
  }
  const successCount = allResults.filter((item) => item.ok).length;
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
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  actionLoading.value = true;
  try {
    const saved = await persistApplyForm();
    const formCount = saved.prescriptionNos.length;
    ElMessage.success(formCount > 1 ? `已按执行科室暂存 ${formCount} 张申请单` : '申请单已暂存');
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
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  actionLoading.value = true;
  try {
    const signed = await executeSignRequests();
    const formCount = signed.prescriptionNos.length;
    ElMessage.success(formCount > 1 ? `签发成功（${formCount} 张申请单）` : '签发成功');
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
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  actionLoading.value = true;
  try {
    const signResult = await executeSignRequests();
    const formCount = signResult.prescriptionNos.length;
    ElMessage.success(formCount > 1 ? `签发成功（${formCount} 张申请单）` : '签发成功');
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

/**
 * 切换 Tab：离开「分类」时清空业务类型筛选并重载全量（仍受申请单目录分类约束）。
 *
 * @param tab 目录 Tab
 */
function handleCatalogTabChange(tab: CatalogTab) {
  catalogTab.value = tab;
  if (tab !== 'category') {
    activeTypeCode.value = '';
    void loadPanelData();
  }
}

/**
 * 点选申请单分类 chip（空码=全部业务类型）。
 *
 * @param code 业务类型码
 */
function handleTypeChipClick(code: string) {
  activeTypeCode.value = code;
  void loadPanelData();
}

/** 更新当前行 detailItems（清空时 ids 可能为空） */
function writeDetailTargetIds(row: ServiceApplySelectedItem, ids: null | string[] | undefined) {
  row.detailItems = buildDetailItems(effectiveApplyKind.value, Array.isArray(ids) ? ids : []);
}

/** 编辑模式：将列表行回显到右侧清单 */
async function hydrateFromEditDocumentGroup(group: ServiceApplyDocumentGroup) {
  const applyFormNo = String(group.applyFormNo ?? '').trim();
  // 列表编辑入口按单张 AR 回显；同窗拆多张后的再保存靠 savedPrescriptionNos 记住全集
  savedPrescriptionNos.value = applyFormNo ? [applyFormNo] : [];
  savedPrescriptionNo.value = applyFormNo;
  // 按行回填单次量与执行次数（各行可不同）；绑定耗材统一签发时确认，卡内不预览
  selectedItems.value = buildServiceApplySelectedItemsFromAdviceRows(
    group.lines,
    effectiveApplyKind.value,
  );
  const head = group.lines[0];
  const previousOrgId = String(head?.performOrgId ?? '').trim();
  orgId.value = previousOrgId;
  // 住院申请单不回显诊断
  if (!isInpatientScope.value) {
    const options = buildServiceApplyDiagnosisOptions(encounterDiagnosisRows.value);
    const matchedDiagnosisIds = resolveServiceApplyDiagnosisIdsFromAdviceRow(
      String(head?.diagnosisSummary ?? ''),
      head?.encounterDiagnosisIds,
      options,
    );
    if (matchedDiagnosisIds.length > 0) {
      encounterDiagnosisIds.value = matchedDiagnosisIds;
      rememberEncounterDiagnosisSession(props.encounterId, matchedDiagnosisIds);
    } else {
      const defaults = pickDefaultServiceApplyEncounterDiagnosisIds(
        encounterDiagnosisRows.value,
        options,
        readEncounterDiagnosisSession(props.encounterId),
      );
      encounterDiagnosisIds.value = defaults;
      if (defaults.length > 0) {
        rememberEncounterDiagnosisSession(props.encounterId, defaults);
      }
    }
  }
  await refreshOrgOptions();
  // 仅当回显执行科室仍是科室候选时修正 label；医院根禁止塞回下拉
  const performOrgName = String(head?.performOrgName ?? '').trim();
  if (orgId.value && orgId.value === previousOrgId && performOrgName) {
    const existing = orgOptions.value.find((item) => item.value === orgId.value);
    if (existing) {
      existing.label = performOrgName;
    }
  }
  // 回显科室名后再按康复科规则补备注（编辑单可能尚未写入该文案）
  syncSelectedItemsRehabCourseRemark();
}

/** 弹窗打开时重置为初始态并加载数据 */
let suppressKeywordReload = false;

function resetPanelState() {
  suppressKeywordReload = true;
  keyword.value = '';
  catalogTab.value = 'frequent';
  activeTypeCode.value = '';
  typeChipOptions.value = [];
  selectedItems.value = [];
  actionLoading.value = false;
  orgId.value = '';
  orgOptions.value = [];
  encounterDiagnosisIds.value = [];
  encounterDiagnosisRows.value = [];
  savedPrescriptionNos.value = [];
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
    // 换患者/就诊时清空共用诊断会话记忆
    if (prev && (prev[2] !== encounterId || prev[3] !== patientId)) {
      clearEncounterDiagnosisSession();
    }
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

/** 切换执行科室：康复科写入疗程备注，切走则去掉 */
watch(orgId, () => {
  syncSelectedItemsRehabCourseRemark();
});
</script>

<template>
  <div class="service-apply-dialog-host">
    <ServiceApplyShellWrap
      :model-value="modelValue"
      :shell="shell"
      :title="dialogTitle"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <div
        class="service-apply-root"
        :class="shell === 'drawer' ? 'service-apply-root--drawer' : ''"
      >
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
          <ElTag v-if="isInpatientScope" size="small" effect="plain" type="warning">
            临时医嘱
          </ElTag>
        </div>

        <div v-if="!contextReady" class="context-hint">请先选择患者后再开立申请单</div>

        <div v-else class="workspace-panels">
          <section class="template-panel">
            <div class="template-panel__title">组套</div>
            <ServiceApplyOrderTemplatePanel
              :active="modelValue"
              :apply-kind="effectiveApplyKind"
              :disabled="actionLoading"
              :encounter-id="encounterId"
              :patient-id="patientId"
              @select="handleApplyOrderTemplate"
            />
          </section>

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
                :class="{ 'category-chip--active': !activeTypeCode }"
                @click="handleTypeChipClick('')"
              >
                全部
              </button>
              <button
                v-for="chip in categoryList"
                :key="chip.code"
                type="button"
                class="category-chip"
                :class="{ 'category-chip--active': activeTypeCode === chip.code }"
                @click="handleTypeChipClick(chip.code)"
              >
                {{ chip.label }}
              </button>
            </div>

            <div v-loading="catalogLoading" class="catalog-panel__list">
              <button
                v-for="item in filteredCatalog"
                :key="catalogRowKey(item)"
                type="button"
                class="catalog-item"
                :class="{
                  'catalog-item--selected': selectedCatalogRowKeys.has(catalogRowKey(item)),
                }"
                @click="handleAddCatalogItem(item)"
              >
                <div class="catalog-item__head">
                  <span class="catalog-item__name">
                    {{ item.name }}
                    <span v-if="catalogRowChrgitmLvText(item)" class="catalog-item__chrgitm-lv">
                      {{ catalogRowChrgitmLvText(item) }}
                    </span>
                  </span>
                  <span class="catalog-item__price-wrap">
                    <span class="catalog-item__price"
                      >¥{{ formatMoney(catalogRowUnitPrice(item)) }}</span
                    >
                  </span>
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
              <div class="cart-panel__field-row">
                <span class="cart-field__label">执行科室</span>
                <ElSelect v-model="orgId" class="cart-panel__field-select" placeholder="请选择">
                  <ElOption
                    v-for="org in orgOptions"
                    :key="org.value"
                    :label="org.label"
                    :value="org.value"
                  />
                </ElSelect>
              </div>
              <div v-if="!isInpatientScope" class="cart-panel__field-row">
                <span class="cart-field__label">诊断</span>
                <div class="cart-panel__diagnosis">
                  <AdviceDiagnosisBindPanel
                    :selected-ids="encounterDiagnosisIds"
                    :options="diagnosisBindOptions"
                    :encounter-id="props.encounterId"
                    :patient-id="props.patientId"
                    @update:selected-ids="onEncounterDiagnosisIdsChange"
                    @add-diagnosis="openDiagnosisDialog"
                    @deleted="() => void refreshEncounterDiagnosisOptions()"
                    @main-switched="() => void refreshEncounterDiagnosisOptions()"
                    @options-refreshed="onBindDiagnosisOptionsRefreshed"
                  />
                </div>
              </div>
            </div>

            <div class="cart-panel__list">
              <div v-for="row in selectedItems" :key="row.rowKey" class="cart-item">
                <div class="cart-item__top">
                  <div class="cart-item__info">
                    <div class="cart-item__title-row">
                      <span class="cart-item__name">
                        {{ row.activityName }}
                        <span
                          v-if="String(row.chrgitmLvText ?? '').trim()"
                          class="cart-item__chrgitm-lv"
                        >
                          {{ String(row.chrgitmLvText).trim() }}
                        </span>
                      </span>
                      <span class="cart-item__price">
                        ¥{{
                          formatMoney(
                            calcMoneyLineTotal(
                              row.unitPrice,
                              resolveServicePreviewBillQuantity(
                                row.quantity,
                                resolveServiceApplyLineExecutionCount(row.executionCount),
                              ),
                            ),
                          )
                        }}
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
                  <span class="cart-field__label">执行次数</span>
                  <ElInputNumber
                    v-model="row.executionCount"
                    :min="1"
                    :max="99"
                    :controls="false"
                    class="cart-field__qty"
                  />
                  <span class="cart-field__label">单次执行数量</span>
                  <ElInputNumber
                    v-model="row.quantity"
                    :min="1"
                    :max="99"
                    :controls="false"
                    class="cart-field__qty"
                  />
                </div>
                <div class="cart-item__detail-row">
                  <span class="cart-field__label">
                    {{ detailFieldLabel }}
                    <span v-if="!detailRequired" class="cart-field__optional">选填</span>
                  </span>
                  <ElSelect
                    v-if="isLabApply"
                    :model-value="detailTargetIds(row.detailItems)"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    filterable
                    clearable
                    :filter-method="handleSpecimenFilter"
                    class="cart-field__detail"
                    :placeholder="
                      detailRequired
                        ? `请选择${detailFieldLabel}（支持拼音）`
                        : `选填${detailFieldLabel}（支持拼音）`
                    "
                    @visible-change="handleSpecimenDropdownVisible"
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
                    clearable
                    class="cart-field__detail"
                    :data="bodyStructureOptions"
                    :props="{ value: 'value', label: 'label', children: 'children' }"
                    :filter-node-method="handleBodyStructureFilterNode"
                    node-key="value"
                    :placeholder="
                      detailRequired
                        ? `请选择${detailFieldLabel}（支持拼音）`
                        : `选填${detailFieldLabel}（支持拼音）`
                    "
                    @update:model-value="(v: string[]) => writeDetailTargetIds(row, v)"
                  />
                </div>
                <div class="cart-item__remark">
                  <span class="cart-field__label">备注</span>
                  <ElInput v-model="row.remark" placeholder="选填" maxlength="200" />
                </div>
                <div
                  v-if="effectiveApplyKind === 'lab' || effectiveApplyKind === 'exam'"
                  class="cart-item__urgent"
                >
                  <ElCheckbox
                    :model-value="row.priorityEnum === SERVICE_REQUEST_PRIORITY_EM"
                    @update:model-value="
                      (checked: boolean | string | number) => {
                        row.priorityEnum = checked ? SERVICE_REQUEST_PRIORITY_EM : '';
                      }
                    "
                  >
                    急
                  </ElCheckbox>
                </div>
              </div>

              <ElEmpty
                v-if="selectedItems.length === 0"
                class="cart-panel__empty"
                :description="emptyCartHint"
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
            <ElButton
              v-if="selectedItems.length > 0"
              link
              type="danger"
              @click="handleClearSelected"
            >
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
      v-if="!isInpatientScope"
      v-model="diagnosisDialogVisible"
      :station-scope="props.stationScope"
      :encounter-id="encounterId"
      :patient-id="patientId"
      :existing-diagnosis-list="encounterDiagnosisRows"
      @saved="handleDiagnosisPickerSaved"
    />
  </div>
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
  grid-template-columns: 300px minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
  height: 0;
}

.template-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.template-panel__title {
  flex: 0 0 auto;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
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

.catalog-item--selected,
.catalog-item--selected:hover {
  border-color: var(--el-color-primary-light-3);
  background: var(--el-color-primary-light-7);
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
  min-width: 0;
}

.catalog-item__price-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* 医保等级：仅展示甲/乙/丙等文案，紧跟项目名称 */
.catalog-item__chrgitm-lv {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-warning);
  white-space: nowrap;
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
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #dbeafe;
  background: #f8fbff;
}

.cart-panel__field-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 8px 12px;
  min-width: 0;
}

.cart-panel__field-select {
  width: 100%;
}

.cart-panel__diagnosis {
  display: flex;
  align-items: center;
  min-width: 0;
}

.cart-panel__diagnosis :deep(.dx-bind) {
  flex: 1;
  min-width: 0;
}

.cart-panel__diagnosis :deep(.dx-bind__panel) {
  min-width: 0;
}

.cart-panel__diagnosis :deep(.el-button) {
  flex-shrink: 0;
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
  line-height: 1.35;
  word-break: break-word;
  white-space: normal;
}

.cart-item__chrgitm-lv {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-warning);
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
  grid-template-columns: 72px 76px 84px 76px;
  align-items: center;
  gap: 0 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e8f0fe;
}

.cart-item__detail-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.cart-item__remark {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.cart-item__urgent {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding-left: 80px;
}

.cart-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: right;
  white-space: nowrap;
}

.cart-field__optional {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
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
