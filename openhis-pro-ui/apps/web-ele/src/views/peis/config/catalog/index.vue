<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  PeisActivityDto,
  PeisObservationDefDto,
  PeisPackageDto,
} from '#/api/peis/catalog-config';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';

import {
  deleteObservationDefApi,
  saveActivityApi,
  saveObservationDefApi,
  savePackageApi,
  searchActivityApi,
  searchObservationDefApi,
  searchPackageApi,
} from '#/api/peis/catalog-config';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
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
/** 体检套餐编辑：子项目候选与内嵌穿梭筛选。 */
const activityOptions = ref<PeisActivityDto[]>([]);
const activityOptionsLoading = ref(false);
const actPickerKeyword = ref('');

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
    return '860px';
  }
  return '560px';
});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
];

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

const categoryOptions = [
  { value: 'exam_item', label: '体检小项' },
  { value: 'vitals', label: '生命体征' },
  { value: 'dept_conclusion', label: '分科结论' },
  { value: 'other', label: '其他' },
];

const typeOptions = [
  { value: '1', label: '检验' },
  { value: '2', label: '检查' },
  { value: '5', label: '其他' },
];

function labelOf(options: Array<{ value: string; label: string }>, code: unknown): string {
  const raw = String(code ?? '');
  const hit = options.find((o) => o.value === raw);
  if (hit) {
    return hit.label;
  }
  return raw || '—';
}

function columnsForTab(tab: CatalogTab): TableColumn[] {
  if (tab === 'observation') {
    return [
      { prop: 'display', label: '名称', minWidth: 160 },
      { prop: 'code', label: '编码', minWidth: 120 },
      { prop: 'valueTypeEnum', label: '值类型', width: 100, slot: 'valueTypeEnum' },
      { prop: 'categoryCode', label: '分类', width: 120, slot: 'categoryCode' },
      { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
      { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
    ];
  }
  if (tab === 'activity') {
    return [
      { prop: 'display', label: '名称', minWidth: 160 },
      { prop: 'code', label: '业务号', minWidth: 120 },
      { prop: 'typeEnum', label: '类型', width: 90, slot: 'typeEnum' },
      { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
      { prop: 'actions', label: '操作', width: 100, slot: 'actions' },
    ];
  }
  return [
    { prop: 'display', label: '套餐名称', minWidth: 160 },
    { prop: 'code', label: '业务号', minWidth: 120 },
    { prop: 'childCount', label: '项目数', width: 90, slot: 'childCount' },
    { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
    { prop: 'actions', label: '操作', width: 100, slot: 'actions' },
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

async function loadObservationOptions() {
  observationOptionsLoading.value = true;
  try {
    // 不按状态过滤：已绑定但停用的小项仍可回显；软删项后端默认不返回
    const res = await searchObservationDefApi({
      pageNo: 1,
      pageSize: 500,
    });
    observationOptions.value = res.content ?? [];
  } finally {
    observationOptionsLoading.value = false;
  }
}

async function loadActivityOptions() {
  activityOptionsLoading.value = true;
  try {
    // 套餐子项仅体检项目（childrenFlag=false）；停用项仍可回显
    const res = await searchActivityApi({
      pageNo: 1,
      pageSize: 500,
    });
    activityOptions.value = res.content ?? [];
  } finally {
    activityOptionsLoading.value = false;
  }
}

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

function removeIdFromFormField(field: string, id: string) {
  editForm.value[field] = toIdStrings(editForm.value[field]).filter((x) => x !== id);
}

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

async function openCreate() {
  if (activeTab.value === 'observation') {
    editForm.value = {
      statusEnum: 'active',
      valueTypeEnum: 'string',
      categoryCode: 'exam_item',
    };
  } else if (activeTab.value === 'activity') {
    editForm.value = {
      statusEnum: 'active',
      typeEnum: '1',
      observationDefinitionIds: [],
    };
    odPickerKeyword.value = '';
    await loadObservationOptions();
  } else {
    editForm.value = { statusEnum: 'active', childActivityIds: [] };
    actPickerKeyword.value = '';
    await loadActivityOptions();
  }
  editVisible.value = true;
}

async function openEdit(row: Record<string, any>) {
  editForm.value = { ...row };
  if (activeTab.value === 'activity') {
    editForm.value.observationDefinitionIds = toIdStrings(row.observationDefinitionIds);
    odPickerKeyword.value = '';
    await loadObservationOptions();
  }
  if (activeTab.value === 'package') {
    editForm.value.childActivityIds = toIdStrings(row.childActivityIds);
    actPickerKeyword.value = '';
    await loadActivityOptions();
  }
  editVisible.value = true;
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
    await saveActivityApi({
      activityId: toOptionalId(form.activityId),
      display: form.display.trim(),
      typeEnum: form.typeEnum,
      statusEnum: form.statusEnum || 'active',
      // 必须传字符串雪花 ID；Number() 会精度丢失导致后端查不到 OD
      observationDefinitionIds: toIdStrings(form.observationDefinitionIds),
      orgId: toOptionalId(form.orgId),
    });
  } else if (activeTab.value === 'package') {
    if (!form.display?.trim()) {
      ElMessage.warning('请填写套餐名称');
      return;
    }
    await savePackageApi({
      packageActivityId: toOptionalId(form.packageActivityId),
      display: form.display.trim(),
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
  void runLoad();
});
</script>

<template>
  <Page
    title="体检目录"
    description="小项 / 项目 / 套餐（AS-PEIS-CFG-001；项目类型在体检项目编辑时选择）"
  >
    <ElTabs v-model="activeTab" class="mb-3">
      <ElTabPane label="体检小项" name="observation" />
      <ElTabPane label="体检项目" name="activity" />
      <ElTabPane label="体检套餐" name="package" />
    </ElTabs>

    <TableLayout
      v-model:query-params="query"
      :table-columns="columns"
      :table-data="list"
      :form-items="formItems"
      :loading="loading"
      :total="total"
      @pagination="handlePagination"
      @query="handleSearch"
      @reset="handleSearch"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建</ElButton>
      </template>
      <template #valueTypeEnum="{ row }">
        {{ labelOf(valueTypeOptions, row.valueTypeEnum) }}
      </template>
      <template #categoryCode="{ row }">
        {{ labelOf(categoryOptions, row.categoryCode) }}
      </template>
      <template #typeEnum="{ row }">
        {{ labelOf(typeOptions, row.typeEnum) }}
      </template>
      <template #statusEnum="{ row }">
        {{ labelOf(statusOptions, row.statusEnum) }}
      </template>
      <template #childCount="{ row }">
        {{ (row.childActivityIds ?? []).length }}
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
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
            <ElSelect v-model="editForm.categoryCode" class="w-full">
              <ElOption
                v-for="opt in categoryOptions"
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
            <ElFormItem label="类型" required>
              <ElSelect v-model="editForm.typeEnum" class="w-full">
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
                    <span class="od-picker__name">{{ opt.display }}</span>
                    <span class="od-picker__code">{{ opt.code }}</span>
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
                    <span class="od-picker__name">{{ opt.display }}</span>
                    <span class="od-picker__code">{{ opt.code }}</span>
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
}

.od-picker__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.od-picker__code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  flex: none;
}

.od-picker__add {
  color: var(--el-color-primary);
  font-size: 12px;
  flex: none;
}

.od-picker__ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}
</style>
