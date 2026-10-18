<script setup lang="ts">
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  PeisActivityDto,
  PeisObservationDefDto,
  PeisPackageDto,
} from '#/api/peis/catalog-config';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  deleteObservationDefApi,
  listActivityByIdsApi,
  listObservationDefByIdsApi,
  saveActivityApi,
  saveObservationDefApi,
  savePackageApi,
  searchActivityApi,
  searchObservationDefApi,
  searchPackageApi,
} from '#/api/peis/catalog-config';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { loadActivityTypeCodeOptions } from '#/views/basic/services/load-activity-type-code-options';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'PeisCatalogConfig' });

type CatalogTab = 'observation' | 'activity' | 'package';

const activeTab = ref<CatalogTab>('observation');
const list = ref<Array<PeisObservationDefDto | PeisActivityDto | PeisPackageDto>>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Record<string, any>>({});
/** 体检项目编辑：小项候选与内嵌穿梭筛选。 */
const observationOptions = ref<PeisObservationDefDto[]>([]);
const observationOptionsLoading = ref(false);
const odPickerKeyword = ref('');
/** 小项穿梭全量候选是否已拉过（避免长度判断误跳过）。 */
const observationOptionsReady = ref(false);
/** 体检套餐编辑：子项目候选与内嵌穿梭筛选。 */
const activityOptions = ref<PeisActivityDto[]>([]);
const activityOptionsLoading = ref(false);
const actPickerKeyword = ref('');
/** 项目穿梭全量候选是否已拉过。 */
const activityOptionsReady = ref(false);

function toIdStrings(ids: unknown): string[] {
  if (!Array.isArray(ids)) {
    return [];
  }
  return ids.map((id) => String(id ?? '').trim()).filter((id) => id.length > 0);
}

/** 可选主键：保留字符串，禁止 Number()（雪花 ID 会丢精度） */
function toOptionalId(value: unknown): string | undefined {
  if (value == null) {
    return undefined;
  }
  const s = String(value).trim();
  return s.length > 0 ? s : undefined;
}

function matchesPickerKeyword(keyword: string, display?: string, code?: string): boolean {
  if (!keyword) {
    return true;
  }
  return `${display || ''} ${code || ''}`.toLowerCase().includes(keyword);
}

const observationOptionMap = computed(() => {
  const map = new Map<string, PeisObservationDefDto>();
  for (const row of observationOptions.value) {
    if (row.definitionId == null) {
      continue;
    }
    map.set(String(row.definitionId), row);
  }
  return map;
});

const selectedObservationIds = computed(() => toIdStrings(editForm.value.observationDefinitionIds));

const availableObservationOptions = computed(() => {
  const selected = new Set(selectedObservationIds.value);
  const keyword = odPickerKeyword.value.trim().toLowerCase();
  return observationOptions.value.filter((row) => {
    if (row.definitionId == null) {
      return false;
    }
    if (selected.has(String(row.definitionId))) {
      return false;
    }
    return matchesPickerKeyword(keyword, row.display, row.code);
  });
});

const selectedObservationOptions = computed(() => {
  const rows: PeisObservationDefDto[] = [];
  for (const id of selectedObservationIds.value) {
    const hit = observationOptionMap.value.get(id);
    if (hit) {
      rows.push(hit);
    } else {
      rows.push({ definitionId: id, display: '未知小项', code: id });
    }
  }
  return rows;
});

const activityOptionMap = computed(() => {
  const map = new Map<string, PeisActivityDto>();
  for (const row of activityOptions.value) {
    if (row.activityId == null) {
      continue;
    }
    map.set(String(row.activityId), row);
  }
  return map;
});

const selectedChildActivityIds = computed(() => toIdStrings(editForm.value.childActivityIds));

const availableActivityOptions = computed(() => {
  const selected = new Set(selectedChildActivityIds.value);
  const keyword = actPickerKeyword.value.trim().toLowerCase();
  return activityOptions.value.filter((row) => {
    if (row.activityId == null) {
      return false;
    }
    if (selected.has(String(row.activityId))) {
      return false;
    }
    return matchesPickerKeyword(keyword, row.display, row.code);
  });
});

const selectedActivityOptions = computed(() => {
  const rows: PeisActivityDto[] = [];
  for (const id of selectedChildActivityIds.value) {
    const hit = activityOptionMap.value.get(id);
    if (hit) {
      rows.push(hit);
    } else {
      rows.push({ activityId: id, display: '未知项目', code: id });
    }
  }
  return rows;
});

const editDialogWidth = computed(() => {
  if (activeTab.value === 'activity' || activeTab.value === 'package') {
    return '920px';
  }
  return '560px';
});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const statusOptions = [
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
];

const valueTypeOptions = [
  { value: 'quantity', label: '数值' },
  { value: 'string', label: '文本' },
  { value: 'codeable', label: '编码' },
  { value: 'boolean', label: '布尔' },
];

const categoryFormOptions = [
  { value: 'exam_item', label: '体检小项' },
  { value: 'vitals', label: '生命体征' },
  { value: 'other', label: '其他' },
];

/** 项目结论落库分类码（历史值为 dept_conclusion） */
const PROJECT_CONCLUSION_CATEGORY = 'dept_conclusion';

/** 列表展示用（含系统自动创建的「项目结论」） */
const categoryLabelOptions = [
  ...categoryFormOptions,
  { value: PROJECT_CONCLUSION_CATEGORY, label: '项目结论' },
];

/** 项目类型下拉（术语 activity_type_code，含现场增码） */
const typeOptions = ref<Array<{ value: string; label: string }>>([]);

const genderOptions = [
  { value: 'BOTH', label: '不限' },
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
];

/**
 * 将选项 code 转为展示文案；未命中时回落原值。
 *
 * @param options 下拉选项
 * @param code 原始编码
 */
function labelOf(options: Array<{ value: string; label: string }>, code: unknown): string {
  const raw = String(code ?? '');
  const hit = options.find((o) => o.value === raw);
  if (hit) {
    return hit.label;
  }
  return raw || '—';
}

/**
 * 原价/套餐价展示：空则 —，否则结算 2 位（仅展示，不反推入账）。
 *
 * @param value SALE 单价
 */
function formatSalePrice(value: unknown): string {
  // 无定价行展示 —，与项目 tab 原价列一致
  if (value == null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return formatMoneyDisplay(num);
}

/**
 * 是否收费展示。
 *
 * @param flag pricingFlag
 */
function formatPricingFlag(flag: unknown): string {
  if (flag === true) {
    return '是';
  }
  if (flag === false) {
    return '否';
  }
  return '—';
}

/**
 * 按当前 Tab 生成表格列。
 *
 * @param tab 目录页签
 */
function columnsForTab(tab: CatalogTab): TableColumn[] {
  if (tab === 'observation') {
    return [
      { prop: 'display', label: '名称', minWidth: 160 },
      { prop: 'code', label: '编码', minWidth: 110 },
      { prop: 'unitCode', label: '单位', width: 80 },
      { prop: 'valueTypeEnum', label: '值类型', width: 90, slot: 'valueTypeEnum' },
      { prop: 'categoryCode', label: '分类', width: 110, slot: 'categoryCode' },
      { prop: 'orgName', label: '执行科室', minWidth: 120 },
      { prop: 'linkedActivityCount', label: '引用项目数', width: 100 },
      { prop: 'statusEnum', label: '状态', width: 80, slot: 'statusEnum' },
      { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
    ];
  }
  if (tab === 'activity') {
    return [
      { prop: 'sortOrder', label: '顺序', width: 70 },
      { prop: 'display', label: '名称', minWidth: 150 },
      { prop: 'code', label: '业务号', minWidth: 100 },
      { prop: 'pyStr', label: '拼音', width: 90 },
      { prop: 'typeEnum', label: '类型', width: 80, slot: 'typeEnum' },
      { prop: 'permittedGenderEnum', label: '适用性别', width: 90, slot: 'permittedGenderEnum' },
      { prop: 'orgName', label: '执行科室', minWidth: 110 },
      { prop: 'salePrice', label: '原价', width: 90, slot: 'salePrice' },
      { prop: 'pricingFlag', label: '收费', width: 70, slot: 'pricingFlag' },
      { prop: 'observationCount', label: '小项数', width: 80 },
      { prop: 'statusEnum', label: '状态', width: 80, slot: 'statusEnum' },
      { prop: 'actions', label: '操作', width: 90, slot: 'actions' },
    ];
  }
  return [
    { prop: 'display', label: '套餐名称', minWidth: 160 },
    { prop: 'code', label: '业务号', minWidth: 120 },
    { prop: 'typeEnum', label: '业务类型', width: 90, slot: 'typeEnum' },
    { prop: 'salePrice', label: '套餐价', width: 90, slot: 'salePrice' },
    { prop: 'childCount', label: '项目数', width: 90, slot: 'childCount' },
    { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
    { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
  ];
}

const columns = ref<TableColumn[]>(columnsForTab('observation'));

async function loadByTab() {
  const params = {
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  };
  if (activeTab.value === 'observation') {
    const res = await searchObservationDefApi(params);
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
    return;
  }
  if (activeTab.value === 'activity') {
    const res = await searchActivityApi(params);
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
    return;
  }
  const res = await searchPackageApi(params);
  list.value = res.content ?? [];
  total.value = Number(res.totalCount ?? 0);
}

const { run: runLoad, loading } = useRequest(loadByTab, { manual: true });

function handleSearch() {
  query.pageNo = 1;
  void runLoad();
}

function handleReset() {
  query.keyword = '';
  query.pageNo = 1;
  void runLoad();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void runLoad();
}

watch(activeTab, (tab) => {
  columns.value = columnsForTab(tab);
  query.pageNo = 1;
  void runLoad();
});

async function loadObservationOptions(ensureIds: unknown = []) {
  observationOptionsLoading.value = true;
  try {
    const ensure = toIdStrings(ensureIds);
    // 先补已选，对话框右侧可立即回显
    if (ensure.length > 0) {
      const haveNow = new Set(
        observationOptions.value
          .map((row) => String(row.definitionId ?? ''))
          .filter((id) => id.length > 0),
      );
      const missing = ensure.filter((id) => !haveNow.has(id));
      if (missing.length > 0) {
        const extra = await listObservationDefByIdsApi({ definitionIds: missing });
        if (extra?.length) {
          observationOptions.value = [...observationOptions.value, ...extra];
        }
      }
    }
    // 候选列表已有缓存则不再全量拉取
    if (observationOptionsReady.value) {
      return;
    }
    // 轻量候选：不装引用次数/科室
    const res = await searchObservationDefApi({
      pageNo: 1,
      pageSize: 500,
      pickerMode: true,
    });
    mergeObservationOptions(res.content ?? [], ensure);
    observationOptionsReady.value = true;
  } finally {
    observationOptionsLoading.value = false;
  }
}

/**
 * 合并小项候选，保留已选主键对应行。
 *
 * @param fetched 接口返回的候选
 * @param ensureIds 须保留的已选主键
 */
function mergeObservationOptions(fetched: PeisObservationDefDto[], ensureIds: string[]) {
  const byId = new Map<string, PeisObservationDefDto>();
  for (const row of [...observationOptions.value, ...fetched]) {
    if (row?.definitionId == null) {
      continue;
    }
    byId.set(String(row.definitionId), row);
  }
  observationOptions.value = [...byId.values()];
  void ensureIds;
}

async function loadActivityOptions(ensureIds: unknown = []) {
  activityOptionsLoading.value = true;
  try {
    const ensure = toIdStrings(ensureIds);
    // 先补已选项目，套餐右侧立即有名称
    if (ensure.length > 0) {
      const haveNow = new Set(
        activityOptions.value
          .map((row) => String(row.activityId ?? ''))
          .filter((id) => id.length > 0),
      );
      const missing = ensure.filter((id) => !haveNow.has(id));
      if (missing.length > 0) {
        const extra = await listActivityByIdsApi({ activityIds: missing });
        if (extra?.length) {
          activityOptions.value = [...activityOptions.value, ...extra];
        }
      }
    }
    // 候选已缓存则跳过重查询（打开编辑不再等 500 条重装）
    if (activityOptionsReady.value) {
      return;
    }
    const res = await searchActivityApi({
      pageNo: 1,
      pageSize: 500,
      pickerMode: true,
    });
    mergeActivityOptions(res.content ?? []);
    activityOptionsReady.value = true;
  } finally {
    activityOptionsLoading.value = false;
  }
}

/**
 * 合并体检项目候选（按 activityId 去重）。
 *
 * @param fetched 接口返回的候选
 */
function mergeActivityOptions(fetched: PeisActivityDto[]) {
  const byId = new Map<string, PeisActivityDto>();
  for (const row of [...activityOptions.value, ...fetched]) {
    if (row?.activityId == null) {
      continue;
    }
    byId.set(String(row.activityId), row);
  }
  activityOptions.value = [...byId.values()];
}

async function openCreate() {
  if (activeTab.value === 'observation') {
    editForm.value = {
      statusEnum: 'active',
      valueTypeEnum: 'string',
      categoryCode: 'exam_item',
    };
    editVisible.value = true;
    return;
  }
  if (activeTab.value === 'activity') {
    // 业务类型不预填硬编码，须从术语下拉必选
    editForm.value = {
      statusEnum: 'active',
      typeEnum: undefined,
      permittedGenderEnum: 'BOTH',
      sortOrder: undefined,
      observationDefinitionIds: [],
    };
    odPickerKeyword.value = '';
    // 先弹窗，候选后台加载，避免点击卡住
    editVisible.value = true;
    void loadObservationOptions();
    return;
  }
  // 套餐：业务类型必填，不写死默认码
  editForm.value = { statusEnum: 'active', typeEnum: undefined, childActivityIds: [] };
  actPickerKeyword.value = '';
  editVisible.value = true;
  void loadActivityOptions();
}

/**
 * 打开编辑：先出对话框，再异步装穿梭候选。
 *
 * @param row 当前列表行
 */
async function openEdit(row: Record<string, any>) {
  editForm.value = { ...row };
  if (activeTab.value === 'activity') {
    editForm.value.observationDefinitionIds = toIdStrings(row.observationDefinitionIds);
    odPickerKeyword.value = '';
    editVisible.value = true;
    void loadObservationOptions(editForm.value.observationDefinitionIds);
    return;
  }
  if (activeTab.value === 'package') {
    editForm.value.childActivityIds = toIdStrings(row.childActivityIds);
    actPickerKeyword.value = '';
    // 先弹窗：已选名称随后补齐，左侧候选后台刷新
    editVisible.value = true;
    void loadActivityOptions(editForm.value.childActivityIds);
    return;
  }
  editVisible.value = true;
}

/** 复制建议名称：已含「(副本)」则不再重复追加 */
function suggestCopyDisplay(name: unknown): string {
  const t = String(name ?? '').trim();
  if (!t) {
    return '未命名 (副本)';
  }
  if (t.includes('(副本)')) {
    return t;
  }
  return `${t} (副本)`;
}

/**
 * 复制体检套餐：清空主键后走新建保存；业务号由后端采番。
 *
 * @param row 当前列表行
 */
async function openCopyPackage(row: Record<string, any>) {
  editForm.value = {
    ...row,
    packageActivityId: undefined,
    code: undefined,
    display: suggestCopyDisplay(row.display),
    statusEnum: row.statusEnum || 'active',
    childActivityIds: toIdStrings(row.childActivityIds),
  };
  actPickerKeyword.value = '';
  editVisible.value = true;
  void loadActivityOptions(editForm.value.childActivityIds);
}

/**
 * 向表单数组字段追加主键（去重，保持字符串雪花）。
 *
 * @param field 表单字段名
 * @param id 主键
 */
function appendIdToFormField(field: string, id: string | number | undefined) {
  if (id == null) {
    return;
  }
  const key = String(id);
  const current = toIdStrings(editForm.value[field]);
  if (current.includes(key)) {
    return;
  }
  editForm.value[field] = [...current, key];
}

/**
 * 从表单数组字段移除主键。
 *
 * @param field 表单字段名
 * @param id 主键字符串
 */
function removeIdFromFormField(field: string, id: string) {
  editForm.value[field] = toIdStrings(editForm.value[field]).filter((x) => x !== id);
}

/**
 * 调整表单数组字段中主键的顺序。
 *
 * @param field 表单字段名
 * @param id 主键
 * @param delta -1 上移 / 1 下移
 */
function moveIdInFormField(field: string, id: string, delta: number) {
  const ids = toIdStrings(editForm.value[field]);
  const index = ids.indexOf(id);
  if (index < 0) {
    return;
  }
  const target = index + delta;
  if (target < 0 || target >= ids.length) {
    return;
  }
  const tmp = ids[index]!;
  ids[index] = ids[target]!;
  ids[target] = tmp;
  editForm.value[field] = ids;
}

function addOdToPicker(id: string | number | undefined) {
  appendIdToFormField('observationDefinitionIds', id);
}

function removeOdFromPicker(id: string) {
  removeIdFromFormField('observationDefinitionIds', id);
}

function moveOdInPicker(id: string, delta: number) {
  moveIdInFormField('observationDefinitionIds', id, delta);
}

function addActToPicker(id: string | number | undefined) {
  appendIdToFormField('childActivityIds', id);
}

function removeActFromPicker(id: string) {
  removeIdFromFormField('childActivityIds', id);
}

function moveActInPicker(id: string, delta: number) {
  moveIdInFormField('childActivityIds', id, delta);
}

async function handleDeleteObservation(row: PeisObservationDefDto) {
  if (!row.definitionId) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除小项「${row.display || row.code}」？删除后列表不再显示（软删除）。`,
      '删除体检小项',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteObservationDefApi({ definitionId: String(row.definitionId) });
  ElMessage.success('已删除');
  await runLoad();
}

async function handleSave() {
  const form = editForm.value;
  if (activeTab.value === 'observation') {
    if (!form.code?.trim() || !form.display?.trim()) {
      ElMessage.warning('请填写小项编码与名称');
      return;
    }
    await saveObservationDefApi({
      definitionId: toOptionalId(form.definitionId),
      code: form.code.trim(),
      display: form.display.trim(),
      valueTypeEnum: form.valueTypeEnum,
      categoryCode: form.categoryCode,
      unitCode: form.unitCode,
      statusEnum: form.statusEnum || 'active',
      orgId: toOptionalId(form.orgId),
    });
  } else if (activeTab.value === 'activity') {
    if (!form.display?.trim()) {
      ElMessage.warning('请填写项目名称');
      return;
    }
    if (!String(form.typeEnum ?? '').trim()) {
      ElMessage.warning('请选择业务类型');
      return;
    }
    await saveActivityApi({
      activityId: toOptionalId(form.activityId),
      display: form.display.trim(),
      typeEnum: String(form.typeEnum).trim(),
      statusEnum: form.statusEnum || 'active',
      // 必须传字符串雪花 ID；Number() 会精度丢失导致后端查不到 OD
      observationDefinitionIds: toIdStrings(form.observationDefinitionIds),
      orgId: toOptionalId(form.orgId),
      permittedGenderEnum: form.permittedGenderEnum || 'BOTH',
      sortOrder:
        form.sortOrder == null || form.sortOrder === '' ? undefined : Number(form.sortOrder),
    });
  } else if (activeTab.value === 'package') {
    if (!form.display?.trim()) {
      ElMessage.warning('请填写套餐名称');
      return;
    }
    if (!String(form.typeEnum ?? '').trim()) {
      ElMessage.warning('请选择业务类型');
      return;
    }
    await savePackageApi({
      packageActivityId: toOptionalId(form.packageActivityId),
      display: form.display.trim(),
      typeEnum: String(form.typeEnum).trim(),
      statusEnum: form.statusEnum || 'active',
      childActivityIds: toIdStrings(form.childActivityIds),
    });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  // 新建后回到第一页，避免新行落在当前页之外
  query.pageNo = 1;
  await runLoad();
}

onMounted(() => {
  void (async () => {
    try {
      typeOptions.value = await loadActivityTypeCodeOptions();
    } catch (error) {
      console.error('加载诊疗业务类型字典失败', error);
    }
    await runLoad();
  })();
});
</script>

<template>
  <Page
    title="体检目录"
    auto-content-height
    content-class="!overflow-hidden flex h-full min-h-0 flex-col"
  >
    <ElTabs v-model="activeTab" class="mb-2 shrink-0">
      <ElTabPane label="体检小项" name="observation" />
      <ElTabPane label="体检项目" name="activity" />
      <ElTabPane label="体检套餐" name="package" />
    </ElTabs>

    <div class="catalog-query shrink-0">
      <span class="catalog-query__label">关键词：</span>
      <ElInput
        v-model="query.keyword"
        clearable
        class="catalog-query__input"
        placeholder="编码/名称"
        @keyup.enter="handleSearch"
      />
      <ElButton type="primary" @click="handleSearch">搜索</ElButton>
      <ElButton @click="handleReset">重置</ElButton>
      <ElButton type="primary" @click="openCreate">新建</ElButton>
    </div>

    <TableLayout
      class="h-full min-h-0 flex-1"
      v-model:query-params="query"
      :table-columns="columns"
      :table-data="list"
      :show-top-query="false"
      :loading="loading"
      :total="total"
      :toolbar-config="{ enabled: false }"
      @pagination="handlePagination"
    >
      <template #valueTypeEnum="{ row }">
        {{ labelOf(valueTypeOptions, row.valueTypeEnum) }}
      </template>
      <template #categoryCode="{ row }">
        {{ labelOf(categoryLabelOptions, row.categoryCode) }}
      </template>
      <template #typeEnum="{ row }">
        {{ labelOf(typeOptions, row.typeEnum) }}
      </template>
      <template #permittedGenderEnum="{ row }">
        {{ labelOf(genderOptions, row.permittedGenderEnum || 'BOTH') }}
      </template>
      <template #salePrice="{ row }">
        {{ formatSalePrice(row.salePrice) }}
      </template>
      <template #pricingFlag="{ row }">
        {{ formatPricingFlag(row.pricingFlag) }}
      </template>
      <template #statusEnum="{ row }">
        {{ labelOf(statusOptions, row.statusEnum) }}
      </template>
      <template #childCount="{ row }">
        {{ (row.childActivityIds ?? []).length }}
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton v-if="activeTab === 'package'" link type="primary" @click="openCopyPackage(row)">
          复制
        </ElButton>
        <ElButton
          v-if="activeTab === 'observation'"
          link
          type="danger"
          @click="handleDeleteObservation(row)"
        >
          删除
        </ElButton>
      </template>
    </TableLayout>

    <ElDialog v-model="editVisible" title="编辑目录项" :width="editDialogWidth" destroy-on-close>
      <ElForm label-width="110px">
        <template v-if="activeTab === 'observation'">
          <ElFormItem label="名称" required>
            <ElInput v-model="editForm.display" />
          </ElFormItem>
          <ElFormItem label="编码" required>
            <ElInput v-model="editForm.code" />
          </ElFormItem>
          <ElFormItem label="值类型" required>
            <ElSelect v-model="editForm.valueTypeEnum" class="w-full">
              <ElOption
                v-for="opt in valueTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="分类" required>
            <ElInput
              v-if="editForm.categoryCode === PROJECT_CONCLUSION_CATEGORY"
              model-value="项目结论（系统自动，无需手工维护）"
              disabled
            />
            <ElSelect v-else v-model="editForm.categoryCode" class="w-full">
              <ElOption
                v-for="opt in categoryFormOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="单位">
            <ElInput v-model="editForm.unitCode" />
          </ElFormItem>
        </template>
        <template v-else-if="activeTab === 'activity'">
          <div class="activity-edit-grid">
            <ElFormItem label="名称" required>
              <ElInput v-model="editForm.display" />
            </ElFormItem>
            <ElFormItem label="业务类型" required>
              <ElSelect
                v-model="editForm.typeEnum"
                class="w-full"
                filterable
                clearable
                placeholder="请选择业务类型"
              >
                <ElOption
                  v-for="opt in typeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="适用性别">
              <ElSelect v-model="editForm.permittedGenderEnum" class="w-full">
                <ElOption
                  v-for="opt in genderOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="顺序">
              <ElInputNumber
                v-model="editForm.sortOrder"
                :min="0"
                :controls="false"
                class="w-full"
              />
            </ElFormItem>
            <ElFormItem label="状态">
              <ElSelect v-model="editForm.statusEnum" class="w-full">
                <ElOption
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <ElFormItem label="绑定小项">
            <div class="od-picker">
              <div class="od-picker__pane">
                <div class="od-picker__pane-title">
                  可选小项
                  <span class="od-picker__pane-count">
                    {{ availableObservationOptions.length }}
                  </span>
                </div>
                <ElInput
                  v-model="odPickerKeyword"
                  clearable
                  placeholder="搜索名称或编码"
                  class="od-picker__search"
                />
                <div v-loading="observationOptionsLoading" class="od-picker__list">
                  <button
                    v-for="opt in availableObservationOptions"
                    :key="String(opt.definitionId)"
                    type="button"
                    class="od-picker__row"
                    @click="addOdToPicker(opt.definitionId)"
                  >
                    <span class="od-picker__name">{{ opt.display }}</span>
                    <span class="od-picker__code">{{ opt.code }}</span>
                    <span class="od-picker__add">添加</span>
                  </button>
                  <ElEmpty
                    v-if="!observationOptionsLoading && availableObservationOptions.length === 0"
                    description="没有可添加的小项"
                    :image-size="64"
                  />
                </div>
              </div>
              <div class="od-picker__pane od-picker__pane--selected">
                <div class="od-picker__pane-title">
                  已选小项
                  <span class="od-picker__pane-count">
                    {{ selectedObservationOptions.length }}
                  </span>
                </div>
                <div class="od-picker__hint">顺序即诊台录入顺序，可用上移/下移调整</div>
                <div class="od-picker__list">
                  <div
                    v-for="(opt, index) in selectedObservationOptions"
                    :key="String(opt.definitionId)"
                    class="od-picker__row od-picker__row--selected"
                  >
                    <span class="od-picker__index">{{ index + 1 }}</span>
                    <div class="od-picker__meta">
                      <span class="od-picker__name" :title="opt.display">{{ opt.display }}</span>
                      <span class="od-picker__code" :title="opt.code">{{ opt.code }}</span>
                    </div>
                    <span class="od-picker__ops">
                      <ElButton
                        link
                        type="primary"
                        :disabled="index === 0"
                        @click="moveOdInPicker(String(opt.definitionId), -1)"
                      >
                        上移
                      </ElButton>
                      <ElButton
                        link
                        type="primary"
                        :disabled="index === selectedObservationOptions.length - 1"
                        @click="moveOdInPicker(String(opt.definitionId), 1)"
                      >
                        下移
                      </ElButton>
                      <ElButton
                        link
                        type="danger"
                        @click="removeOdFromPicker(String(opt.definitionId))"
                      >
                        移除
                      </ElButton>
                    </span>
                  </div>
                  <ElEmpty
                    v-if="selectedObservationOptions.length === 0"
                    description="从左侧添加小项"
                    :image-size="64"
                  />
                </div>
              </div>
            </div>
          </ElFormItem>
        </template>
        <template v-else-if="activeTab === 'package'">
          <div class="package-edit-grid">
            <ElFormItem label="套餐名称" required>
              <ElInput v-model="editForm.display" />
            </ElFormItem>
            <ElFormItem label="业务类型" required>
              <ElSelect
                v-model="editForm.typeEnum"
                class="w-full"
                filterable
                clearable
                placeholder="请选择业务类型"
              >
                <ElOption
                  v-for="opt in typeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="状态">
              <ElSelect v-model="editForm.statusEnum" class="w-full">
                <ElOption
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <ElFormItem label="包含项目">
            <div class="od-picker">
              <div class="od-picker__pane">
                <div class="od-picker__pane-title">
                  可选项目
                  <span class="od-picker__pane-count">
                    {{ availableActivityOptions.length }}
                  </span>
                </div>
                <ElInput
                  v-model="actPickerKeyword"
                  clearable
                  placeholder="搜索名称或业务号"
                  class="od-picker__search"
                />
                <div v-loading="activityOptionsLoading" class="od-picker__list">
                  <button
                    v-for="opt in availableActivityOptions"
                    :key="String(opt.activityId)"
                    type="button"
                    class="od-picker__row"
                    @click="addActToPicker(opt.activityId)"
                  >
                    <span class="od-picker__name">{{ opt.display }}</span>
                    <span class="od-picker__code">{{ opt.code }}</span>
                    <span class="od-picker__add">添加</span>
                  </button>
                  <ElEmpty
                    v-if="!activityOptionsLoading && availableActivityOptions.length === 0"
                    description="没有可添加的体检项目"
                    :image-size="64"
                  />
                </div>
              </div>
              <div class="od-picker__pane od-picker__pane--selected">
                <div class="od-picker__pane-title">
                  已选项目
                  <span class="od-picker__pane-count">
                    {{ selectedActivityOptions.length }}
                  </span>
                </div>
                <div class="od-picker__hint">顺序即套餐内项目顺序，可用上移/下移调整</div>
                <div class="od-picker__list">
                  <div
                    v-for="(opt, index) in selectedActivityOptions"
                    :key="String(opt.activityId)"
                    class="od-picker__row od-picker__row--selected"
                  >
                    <span class="od-picker__index">{{ index + 1 }}</span>
                    <div class="od-picker__meta">
                      <span class="od-picker__name" :title="opt.display">{{ opt.display }}</span>
                      <span class="od-picker__code" :title="opt.code">{{ opt.code }}</span>
                    </div>
                    <span class="od-picker__ops">
                      <ElButton
                        link
                        type="primary"
                        :disabled="index === 0"
                        @click="moveActInPicker(String(opt.activityId), -1)"
                      >
                        上移
                      </ElButton>
                      <ElButton
                        link
                        type="primary"
                        :disabled="index === selectedActivityOptions.length - 1"
                        @click="moveActInPicker(String(opt.activityId), 1)"
                      >
                        下移
                      </ElButton>
                      <ElButton
                        link
                        type="danger"
                        @click="removeActFromPicker(String(opt.activityId))"
                      >
                        移除
                      </ElButton>
                    </span>
                  </div>
                  <ElEmpty
                    v-if="selectedActivityOptions.length === 0"
                    description="从左侧添加体检项目"
                    :image-size="64"
                  />
                </div>
              </div>
            </div>
          </ElFormItem>
        </template>
        <ElFormItem v-if="activeTab === 'observation'" label="状态">
          <ElSelect v-model="editForm.statusEnum" class="w-full">
            <ElOption
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.catalog-query {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 0 2px;
}

.catalog-query__label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.catalog-query__input {
  width: 220px;
}

.activity-edit-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 0 12px;
}

.package-edit-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 0 12px;
}

.od-picker {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  min-height: 360px;
}

.od-picker__pane {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.od-picker__pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
}

.od-picker__pane-count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 500;
  text-align: center;
}

.od-picker__search {
  padding: 8px 10px 0;
}

.od-picker__hint {
  padding: 6px 12px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.od-picker__list {
  flex: 1;
  min-height: 320px;
  max-height: 360px;
  overflow: auto;
  padding: 8px;
}

.od-picker__row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-bottom: 6px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  text-align: left;
  cursor: pointer;
}

.od-picker__row:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.od-picker__row--selected {
  cursor: default;
  align-items: flex-start;
}

.od-picker__row--selected:hover {
  background: var(--el-fill-color-blank);
  border-color: var(--el-border-color-lighter);
}

.od-picker__index {
  width: 20px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  flex: none;
  line-height: 22px;
}

.od-picker__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.od-picker__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 20px;
}

.od-picker__row > .od-picker__name {
  flex: 1;
}

.od-picker__code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 18px;
}

.od-picker__row > .od-picker__code {
  flex: none;
  max-width: 42%;
}

.od-picker__add {
  color: var(--el-color-primary);
  font-size: 12px;
  flex: none;
}

.od-picker__ops {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  flex: none;
  max-width: 150px;
}
</style>
