<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimePicker,
} from 'element-plus';

import {
  listActivityTypeOptionsByCategoryApi,
  pageActivityDefinitionForExecuteLocationApi,
} from '#/api/basic/execute-location';

defineOptions({ name: 'ExecuteLocationBatchAddDialog' });

interface Props {
  visible: boolean;
  /** 开立科室（配置页左侧选中） */
  organizationId: string;
  activityCategoryOptions: ApiOptionItem[];
  /** 执行科室下拉（init.executionOrgOptions 扁平或树转扁平由父组件传入） */
  executionOrgOptions: ApiOptionItem[];
  /** 当前开立科室已配置的项目 ID，用于去重 */
  boundActivityDefinitionIds: string[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  organizationId: '',
  activityCategoryOptions: () => [],
  executionOrgOptions: () => [],
  boundActivityDefinitionIds: () => [],
});

export interface BatchAddConfirmPayload {
  activityCategoryCode: string;
  activityTypeCode?: string;
  executionOrgId: string;
  items: ApiOptionItem[];
  startTime: string;
  endTime: string;
}

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [payload: BatchAddConfirmPayload];
}>();

const tableRef = ref<InstanceType<typeof ElTable> | null>(null);
const activityCategoryCode = ref<string>();
const activityTypeCode = ref<string>();
/** 随大类联动的业务类型选项 */
const activityTypeOptions = ref<ApiOptionItem[]>([]);
const activityTypeLoading = ref(false);
const executionOrgId = ref<string>();
const keyword = ref('');
const selectedRows = ref<ApiOptionItem[]>([]);
const itemOptions = ref<ApiOptionItem[]>([]);
const itemLoading = ref(false);
const startTime = ref('00:00:00');
const endTime = ref('23:59:59');

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 可选项目（排除当前科室已配置） */
const selectableItemOptions = computed(() =>
  itemOptions.value.filter(
    (item) => !props.boundActivityDefinitionIds.includes(String(item.value)),
  ),
);

/** 当前可选项目（后端已按关键字检索，前端只做已配置去重） */
const filteredItems = computed(() => selectableItemOptions.value);

/** 按诊疗大类 + 关键字拉取可选项目（关键字下推后端，避免只筛本地前 500 条） */
async function loadItemOptions() {
  if (!props.organizationId || !activityCategoryCode.value) {
    itemOptions.value = [];
    return;
  }
  itemLoading.value = true;
  try {
    const trimmedKeyword = keyword.value.trim();
    const resp = await pageActivityDefinitionForExecuteLocationApi({
      organizationId: props.organizationId,
      activityCategoryCode: activityCategoryCode.value,
      activityTypeCode: activityTypeCode.value,
      pageNo: 1,
      pageSize: 500,
      keyword: trimmedKeyword || undefined,
    });
    itemOptions.value = resp.content || [];
  } finally {
    itemLoading.value = false;
  }
}

/** 按当前大类加载业务类型（目录实际用过的类型） */
async function loadActivityTypeOptions() {
  if (!activityCategoryCode.value) {
    activityTypeOptions.value = [];
    return;
  }
  activityTypeLoading.value = true;
  try {
    const options = (await listActivityTypeOptionsByCategoryApi(activityCategoryCode.value)) || [];
    activityTypeOptions.value = options.map((item) => ({
      value: String(item.value ?? ''),
      label: String(item.label ?? item.value ?? ''),
    }));
  } finally {
    activityTypeLoading.value = false;
  }
}

/** 诊疗大类变更时重置类型/筛选与勾选，并重拉类型与项目 */
async function onCategoryChange() {
  keyword.value = '';
  activityTypeCode.value = undefined;
  selectedRows.value = [];
  itemOptions.value = [];
  await loadActivityTypeOptions();
  await loadItemOptions();
  await nextTick();
  tableRef.value?.clearSelection();
}

/** 业务类型变更时按类型重拉候选单品 */
async function onTypeChange() {
  // 未选大类时无法拉项目，仅记下类型等用户再选大类。
  if (!activityCategoryCode.value) {
    return;
  }
  selectedRows.value = [];
  await loadItemOptions();
  await nextTick();
  tableRef.value?.clearSelection();
}

/** 表格勾选变化 */
function onSelectionChange(rows: ApiOptionItem[]) {
  selectedRows.value = rows;
}

/** 判断行是否可勾选 */
function rowSelectable(row: ApiOptionItem) {
  return !props.boundActivityDefinitionIds.includes(String(row.value));
}

/** 全选当前列表（含关键字过滤结果） */
async function selectAllVisible() {
  await nextTick();
  const table = tableRef.value;
  if (!table) {
    return;
  }
  for (const row of filteredItems.value) {
    table.toggleRowSelection(row, true);
  }
}

/** 清空勾选 */
function clearSelection() {
  tableRef.value?.clearSelection();
  selectedRows.value = [];
}

/** 关闭并重置表单 */
function handleClose() {
  dialogVisible.value = false;
  activityCategoryCode.value = undefined;
  activityTypeCode.value = undefined;
  activityTypeOptions.value = [];
  executionOrgId.value = undefined;
  keyword.value = '';
  selectedRows.value = [];
  itemOptions.value = [];
  startTime.value = '00:00:00';
  endTime.value = '23:59:59';
}

/** 确认批量添加到表格 */
function handleConfirm() {
  if (!activityCategoryCode.value) {
    ElMessage.warning('请选择诊疗大类');
    return;
  }
  if (!executionOrgId.value) {
    ElMessage.warning('请选择执行科室');
    return;
  }
  if (!selectedRows.value.length) {
    ElMessage.warning('请至少勾选一个项目');
    return;
  }
  emit('confirm', {
    activityCategoryCode: activityCategoryCode.value,
    activityTypeCode: activityTypeCode.value,
    executionOrgId: executionOrgId.value,
    items: selectedRows.value,
    startTime: startTime.value,
    endTime: endTime.value,
  });
  handleClose();
}

let keywordSearchTimer: ReturnType<typeof setTimeout> | undefined;

watch(keyword, () => {
  if (!activityCategoryCode.value) {
    return;
  }
  if (keywordSearchTimer) {
    clearTimeout(keywordSearchTimer);
  }
  keywordSearchTimer = setTimeout(() => {
    void loadItemOptions();
  }, 300);
});

watch(
  () => props.visible,
  (open) => {
    if (open) {
      activityCategoryCode.value = undefined;
      activityTypeCode.value = undefined;
      activityTypeOptions.value = [];
      executionOrgId.value = undefined;
      keyword.value = '';
      selectedRows.value = [];
      itemOptions.value = [];
      startTime.value = '00:00:00';
      endTime.value = '23:59:59';
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    class="execute-batch-dialog"
    title="批量添加项目"
    width="720px"
    destroy-on-close
    @close="handleClose"
  >
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="execute-batch-dialog__alert"
      title="勾选项目后确认添加至主列表，核对生效时段后点击「批量保存」保存当前全部行。"
    />

    <section class="execute-batch-panel">
      <div class="execute-batch-panel__title">基础设置</div>
      <ElForm label-width="72px" class="execute-batch-form">
        <div class="execute-batch-form__grid">
          <ElFormItem label="诊疗大类" class="!mb-0">
            <ElSelect
              v-model="activityCategoryCode"
              placeholder="请选择诊疗大类"
              class="w-full"
              @change="onCategoryChange"
            >
              <ElOption
                v-for="item in activityCategoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="业务类型" class="!mb-0">
            <ElSelect
              v-model="activityTypeCode"
              placeholder="可选，随大类联动"
              clearable
              filterable
              class="w-full"
              :disabled="!activityCategoryCode"
              :loading="activityTypeLoading"
              @change="onTypeChange"
            >
              <ElOption
                v-for="item in activityTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="执行科室" class="!mb-0">
            <ElSelect
              v-model="executionOrgId"
              filterable
              clearable
              placeholder="护士站或医技科室"
              class="w-full"
            >
              <ElOption
                v-for="item in executionOrgOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="生效时段" class="execute-batch-form__span2 !mb-0">
            <div class="execute-batch-time">
              <ElTimePicker
                v-model="startTime"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                placeholder="开始"
              />
              <span class="execute-batch-time__sep">至</span>
              <ElTimePicker
                v-model="endTime"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                placeholder="结束"
              />
            </div>
          </ElFormItem>
        </div>
      </ElForm>
    </section>

    <section class="execute-batch-panel execute-batch-panel--table">
      <div class="execute-batch-panel__head">
        <div class="execute-batch-panel__head-left">
          <span class="execute-batch-panel__title">项目列表</span>
          <ElTag size="small" type="info">可选 {{ selectableItemOptions.length }}</ElTag>
          <ElTag size="small" type="success">已选 {{ selectedRows.length }}</ElTag>
        </div>
        <div class="execute-batch-panel__tools">
          <ElInput
            v-model="keyword"
            clearable
            placeholder="搜索项目名称"
            class="execute-batch-search"
            :prefix-icon="Search"
            :disabled="!activityCategoryCode"
          />
          <ElButton link type="primary" :disabled="!filteredItems.length" @click="selectAllVisible">
            全选
          </ElButton>
          <ElButton link :disabled="!selectedRows.length" @click="clearSelection">清空</ElButton>
        </div>
      </div>

      <ElTable
        ref="tableRef"
        v-loading="itemLoading"
        class="execute-batch-table"
        :data="filteredItems"
        row-key="value"
        size="small"
        border
        max-height="320"
        :empty-text="activityCategoryCode ? '暂无可用项目' : '请先选择诊疗大类'"
        @selection-change="onSelectionChange"
      >
        <ElTableColumn type="selection" width="44" align="center" :selectable="rowSelectable" />
        <ElTableColumn type="index" label="序号" width="52" align="center" />
        <ElTableColumn prop="label" label="项目名称" show-overflow-tooltip />
      </ElTable>

      <div v-if="activityCategoryCode && keyword" class="execute-batch-panel__foot">
        关键字筛选后显示 {{ filteredItems.length }} 项
      </div>
    </section>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :disabled="!selectedRows.length" @click="handleConfirm">
        确定添加{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.execute-batch-dialog__alert {
  margin-bottom: 12px;
}

.execute-batch-panel {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-blank);

  & + & {
    margin-top: 12px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px 12px;
    margin-bottom: 10px;
  }

  &__head-left {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  &__tools {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 8px;
  }

  &__foot {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.execute-batch-form__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  margin-top: 10px;
}

.execute-batch-form__span2 {
  grid-column: 1 / -1;
}

.execute-batch-time {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;

  :deep(.el-date-editor) {
    flex: 1;
    width: auto;
  }
}

.execute-batch-time__sep {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.execute-batch-search {
  width: 200px;
}

.execute-batch-table {
  width: 100%;
}
</style>
