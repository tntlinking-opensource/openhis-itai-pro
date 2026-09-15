<script setup lang="ts">
import type {
  ConditionDefinitionRowResponse,
  MaintainableCommonDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';
import type { DoctorStationDiagnosisScope } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import AppDialog from '#/components/app-dialog.vue';
import { formatDiagnosisYbNoDisplay } from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 查询区布局与开立诊断弹窗同构（外挂 css，避免院点 scoped 丢失） */
import './diagnosis-picker-dialog.css';

defineOptions({ name: 'CommonDiagnosisMaintainDialog' });

interface Props {
  modelValue: boolean;
  /** 门诊默认；住院传 inpatient */
  stationScope?: DoctorStationDiagnosisScope;
}

const props = withDefaults(defineProps<Props>(), {
  stationScope: 'outpatient',
});

const emit = defineEmits<{
  changed: [];
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

/** 当前维护维度：个人 / 科室 */
type MaintainScope = 'department' | 'personal';

const activeScope = ref<MaintainScope>('personal');
const personalList = ref<MaintainableCommonDiagnosisRowDto[]>([]);
const departmentList = ref<MaintainableCommonDiagnosisRowDto[]>([]);
/** 维护列表是否发生过增删，关闭时通知父级刷新快捷树 */
const dirty = ref(false);

const diagnosisHttp = computed(() =>
  createDoctorStationDiagnosisHttpClient(
    props.stationScope === 'inpatient'
      ? '/inpatientDoctorStationDiagnosis'
      : '/doctorStationDiagnosis',
  ),
);

/** 住院西医落国临：检索/已绑列表编码列用「国临码」 */
const isInpatient = computed(() => props.stationScope === 'inpatient');
const catalogCodeColumnLabel = computed(() => (isInpatient.value ? '国临码' : '诊断编码'));

const currentList = computed(() =>
  activeScope.value === 'personal' ? personalList.value : departmentList.value,
);

/** 已绑定常用的定义主键集合，用于表格行禁用重复添加 */
const boundDefinitionIds = computed(() => {
  const ids = new Set<string>();
  for (const row of currentList.value) {
    const id = String(row.conditionDefinitionId ?? '').trim();
    if (id) ids.add(id);
  }
  return ids;
});

// —— 诊断目录检索（与开立诊断同口径：空关键字默认全量分页） ——
const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const records = ref<ConditionDefinitionRowResponse[]>([]);
const selectedRow = ref<ConditionDefinitionRowResponse | null>(null);

const { run: runList, loading: listLoading } = useRequest(
  () => diagnosisHttp.value.httpListMaintainableCommonDiagnoses(),
  {
    manual: true,
    onSuccess: (data) => {
      personalList.value = data.personalList ?? [];
      departmentList.value = data.departmentList ?? [];
    },
  },
);

const { run: runUnbind, loading: unbindLoading } = useRequest(
  (templateId: string) =>
    diagnosisHttp.value.httpUnbindCommonDiagnosis({
      templateIds: [preserveApiIdForRequest(templateId) ?? templateId],
    }),
  {
    manual: true,
    onSuccess: async () => {
      ElMessage.success('已移除');
      dirty.value = true;
      await runList();
    },
  },
);

const { run: runBind, loading: bindLoading } = useRequest(
  (definitionId: string) =>
    diagnosisHttp.value.httpBindCommonDiagnosis({
      conditionDefinitionId: preserveApiIdForRequest(definitionId) ?? definitionId,
      saveAsPersonal: activeScope.value === 'personal',
      saveAsDepartment: activeScope.value === 'department',
    }),
  {
    manual: true,
    onSuccess: async () => {
      ElMessage.success(activeScope.value === 'personal' ? '已存入个人常用' : '已存入科室常用');
      dirty.value = true;
      selectedRow.value = null;
      await runList();
    },
  },
);

const { run: runSearch, loading: searchLoading } = useRequest(
  async () => {
    // 与开立诊断一致：空关键字不传，后端返回全部已发布定义
    const page = await diagnosisHttp.value.httpGetConditionDefinitionMetadata({
      diagnosisKindCode: '1',
      keyword: keyword.value.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    return page;
  },
  {
    manual: true,
    onSuccess: (page) => {
      records.value = page.content ?? [];
      const totalRaw = page.totalCount ?? page.total ?? 0;
      total.value = Number(totalRaw) || 0;
      selectedRow.value = null;
    },
  },
);

/** 弹窗打开：加载常用列表，并默认查出全部诊断 */
function onDialogOpen() {
  dirty.value = false;
  activeScope.value = 'personal';
  keyword.value = '';
  pageNo.value = 1;
  selectedRow.value = null;
  void runList();
  void runSearch();
}

/** 弹窗关闭：有变更则通知父级刷新快捷树 */
function onDialogClosed() {
  if (dirty.value) {
    emit('changed');
  }
}

/** 关键字检索（回车/查询/清空） */
function handleSearch() {
  pageNo.value = 1;
  selectedRow.value = null;
  void runSearch();
}

/** 诊断目录翻页 */
function handlePageChange(page: number) {
  pageNo.value = page;
  selectedRow.value = null;
  void runSearch();
}

/** 选中待绑定行 */
function handleCurrentChange(row: ConditionDefinitionRowResponse | null) {
  selectedRow.value = row;
}

/** 双击行直接存为当前维度常用 */
async function handleRowDblclick(row: ConditionDefinitionRowResponse) {
  if (!row) return;
  selectedRow.value = row;
  await handleBindSelected();
}

/** 将选中行绑定到当前 Tab 对应的常用 */
async function handleBindSelected() {
  const defId = selectedRow.value?.conditionDefinitionId?.trim();
  if (!defId) {
    ElMessage.warning('请先在列表中选择一条诊断');
    return;
  }
  if (boundDefinitionIds.value.has(defId)) {
    ElMessage.warning('该诊断已在常用列表中');
    return;
  }
  await runBind(defId);
}

/** 移除一条常用绑定 */
async function handleRemove(row: MaintainableCommonDiagnosisRowDto) {
  const templateId = row.templateId?.trim();
  if (!templateId) {
    ElMessage.warning('缺少模板标识，无法移除');
    return;
  }
  const name = row.conditionDefinitionName?.trim() || row.conditionCode || '该诊断';
  try {
    await ElMessageBox.confirm(`确认从常用中移除「${name}」？`, '移除诊断', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await runUnbind(templateId);
}

/** 切换个人/科室维度时清空选中 */
watch(activeScope, () => {
  selectedRow.value = null;
});

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      onDialogOpen();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="visible"
    title="诊断维护"
    width="920px"
    destroy-on-close
    append-to-body
    @closed="onDialogClosed"
  >
    <div class="dx-picker-dialog dx-maintain flex flex-col gap-3">
      <div class="flex items-center justify-between gap-3">
        <ElRadioGroup v-model="activeScope" class="dx-picker-dialog__kind">
          <ElRadioButton label="personal">个人常用</ElRadioButton>
          <ElRadioButton label="department">科室常用</ElRadioButton>
        </ElRadioGroup>
      </div>

      <div class="dx-picker-dialog__search flex flex-nowrap items-center gap-2">
        <div class="dx-picker-dialog__search-input w-0 min-w-0 flex-1">
          <ElInput
            v-model="keyword"
            class="w-full"
            clearable
            placeholder="请输入诊断名称或编码"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </div>
        <ElButton
          class="dx-picker-dialog__search-btn shrink-0"
          type="primary"
          :loading="searchLoading"
          @click="handleSearch"
        >
          查询
        </ElButton>
      </div>

      <div class="dx-picker-dialog__table-wrap">
        <ElTable
          v-loading="searchLoading"
          :data="records"
          border
          height="320"
          highlight-current-row
          empty-text="暂无诊断"
          @current-change="handleCurrentChange"
          @row-dblclick="handleRowDblclick"
        >
          <ElTableColumn
            prop="conditionCode"
            :label="catalogCodeColumnLabel"
            width="160"
            align="center"
          />
          <ElTableColumn label="医保码" width="160" align="center">
            <template #default="{ row }">
              {{ formatDiagnosisYbNoDisplay(row.ybNo) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="conditionName"
            label="诊断名称"
            min-width="220"
            align="center"
            show-overflow-tooltip
          />
        </ElTable>
      </div>

      <div class="dx-picker-dialog__form-row flex items-center gap-2">
        <span class="dx-picker-dialog__label">选择结果</span>
        <ElInput
          class="min-w-0 flex-1"
          readonly
          :model-value="selectedRow?.conditionName || ''"
          placeholder="请先在上方列表中选择诊断"
        />
        <ElButton
          type="primary"
          class="shrink-0"
          :loading="bindLoading"
          :disabled="!selectedRow"
          @click="handleBindSelected"
        >
          存为{{ activeScope === 'personal' ? '个人' : '科室' }}常用
        </ElButton>
      </div>

      <div class="flex justify-end">
        <ElPagination
          layout="prev, pager, next, total"
          :page-size="pageSize"
          :current-page="pageNo"
          :total="Number(total) || 0"
          @current-change="handlePageChange"
        />
      </div>

      <div class="dx-maintain__bound">
        <div class="dx-maintain__bound-title">
          已存{{ activeScope === 'personal' ? '个人' : '科室' }}常用
          <span class="dx-maintain__bound-count">{{ currentList.length }}</span>
        </div>
        <div class="dx-picker-dialog__table-wrap">
          <ElTable
            v-loading="listLoading || unbindLoading"
            :data="currentList"
            border
            height="180"
            empty-text="暂无常用诊断"
          >
            <ElTableColumn
              prop="conditionCode"
              :label="catalogCodeColumnLabel"
              width="140"
              align="center"
            />
            <ElTableColumn
              prop="conditionDefinitionName"
              label="诊断名称"
              min-width="200"
              align="center"
              show-overflow-tooltip
            />
            <ElTableColumn label="医保码" width="140" align="center">
              <template #default="{ row }">
                {{ formatDiagnosisYbNoDisplay(row.ybNo) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="88" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton type="danger" link :loading="unbindLoading" @click="handleRemove(row)">
                  移除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="visible = false">关闭</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.dx-maintain__bound {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.dx-maintain__bound-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.dx-maintain__bound-count {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}
</style>
