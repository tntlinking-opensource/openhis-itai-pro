<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ReportDefinitionItemDto, ReportSqlPresetItemDto } from '#/api/report/report';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElOptionGroup,
  ElSelect,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  copyReportDefinitionApi,
  createReportDefinitionApi,
  deleteReportDefinitionApi,
  listReportDefinitionsApi,
  listSqlPresetsApi,
  parseReportFilters,
  parseReportHeaderConfig,
  resolveDefinitionFilters,
  resolveDefinitionHeader,
  publishReportDefinitionApi,
  unpublishReportDefinitionApi,
} from '#/api/report/report';
import { useRequest } from '#/hooks/use-request';
import { groupPresetsByPriority } from '#/utils/report/report-builder';

defineOptions({
  name: 'WhaleReportTemplate',
});

const router = useRouter();
const tableData = ref<ReportDefinitionItemDto[]>([]);
const sqlPresets = ref<ReportSqlPresetItemDto[]>([]);
const createVisible = ref(false);
const copyVisible = ref(false);
const copySource = ref<ReportDefinitionItemDto | null>(null);
const createForm = reactive({
  reportCode: '',
  name: '',
  remark: '',
  presetReportCode: '',
});
const copyForm = reactive({
  reportCode: '',
  name: '',
  remark: '',
});

const groupedPresets = computed(() => groupPresetsByPriority(sqlPresets.value));

const queryParams = reactive<{ keyword?: string; status?: string }>({});

const { run: runList, loading: listLoading } = useRequest(listReportDefinitionsApi, {
  manual: true,
  showErrorMessage: false,
});

const { run: runCreate, loading: createLoading } = useRequest(createReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});

const { run: runCopy, loading: copyLoading } = useRequest(copyReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});

const { run: runPublish, loading: publishLoading } = useRequest(publishReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});

const { run: runUnpublish, loading: unpublishLoading } = useRequest(unpublishReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});

const formItems = computed<FormItemConfig[]>(() => [
  { label: '关键词', prop: 'keyword', type: 'input', placeholder: '名称 / 编码' },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    clearable: true,
    options: [
      { label: '草稿', value: 'draft' },
      { label: '已发布', value: 'published' },
    ],
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, fixed: 'left' },
  { prop: 'name', label: '报表名称', minWidth: 200, fixed: 'left', showOverflowTooltip: true },
  { prop: 'reportCode', label: '报表编码', minWidth: 200, showOverflowTooltip: true },
  { prop: 'sourceType', label: '来源', width: 88, slot: 'sourceType' },
  { prop: 'status', label: '状态', width: 96, slot: 'status' },
  { prop: 'remark', label: '备注', minWidth: 160, showOverflowTooltip: true },
  { prop: 'action', label: '操作', fixed: 'right', width: 360, align: 'center', slot: 'action' },
];

async function getList() {
  const rows = await runList({
    keyword: queryParams.keyword?.trim() || undefined,
    status: queryParams.status?.trim() || undefined,
  });
  tableData.value = Array.isArray(rows) ? rows : [];
}

async function loadPresets() {
  const rows = await listSqlPresetsApi();
  sqlPresets.value = Array.isArray(rows) ? rows : [];
}

function openCreate() {
  createForm.reportCode = '';
  createForm.name = '';
  createForm.remark = '';
  createForm.presetReportCode = '';
  createVisible.value = true;
}

function openCopy(row: ReportDefinitionItemDto) {
  copySource.value = row;
  copyForm.reportCode = row.reportCode ? `${row.reportCode}_copy` : '';
  copyForm.name = row.name ? `${row.name}（副本）` : '';
  copyForm.remark = row.remark ?? '';
  copyVisible.value = true;
}

async function handleCreate() {
  const reportCode = createForm.reportCode.trim();
  const name = createForm.name.trim();
  if (!reportCode || !name) {
    ElMessage.warning('请填写报表编码和名称');
    return;
  }
  const created = await runCreate({
    reportCode,
    name,
    remark: createForm.remark?.trim() || undefined,
    presetReportCode: createForm.presetReportCode?.trim() || undefined,
  });
  ElMessage.success('已创建');
  createVisible.value = false;
  await getList();
  if (created?.id) {
    goDesign(created);
  }
}

async function handleCopy() {
  if (!copySource.value?.id) {
    return;
  }
  const reportCode = copyForm.reportCode.trim();
  const name = copyForm.name.trim();
  if (!reportCode || !name) {
    ElMessage.warning('请填写新报表编码和名称');
    return;
  }
  const created = await runCopy({
    sourceId: copySource.value.id,
    reportCode,
    name,
    remark: copyForm.remark?.trim() || undefined,
  });
  ElMessage.success('已复制');
  copyVisible.value = false;
  await getList();
  if (created?.id) {
    goDesign(created);
  }
}

function goDesign(row: ReportDefinitionItemDto) {
  if (!row.id) {
    return;
  }
  void router.push({ path: '/reports/designer', query: { id: String(row.id) } });
}

function validateBeforePublish(row: ReportDefinitionItemDto): string | null {
  if (!row.name?.trim()) {
    return '报表名称未填写';
  }
  if (row.sourceType === 'sql') {
    if (!row.querySql?.trim()) {
      return 'SQL 报表未配置查询语句';
    }
    const header = resolveDefinitionHeader(row);
    if (!header.title?.trim()) {
      return '表头标题未填写';
    }
    const filters = resolveDefinitionFilters(row);
    const hasDateFilter = filters.some(
      (f) => f.field === 'dateFrom' || f.field === 'dateTo' || f.type === 'daterange',
    );
    if (!hasDateFilter) {
      return '未配置日期筛选（dateFrom/dateTo 或日期区间）';
    }
  }
  return null;
}

async function handlePublish(row: ReportDefinitionItemDto) {
  if (!row.id) {
    return;
  }
  const validationError = validateBeforePublish(row);
  if (validationError) {
    try {
      await ElMessageBox.confirm(
        `${validationError}。建议在报表设计器中完成配置并试查后再发布，是否前往设计器？`,
        '无法发布',
        { type: 'warning', confirmButtonText: '去设计器', cancelButtonText: '取消' },
      );
      goDesign(row);
    } catch {
      // user cancelled
    }
    return;
  }
  await runPublish(row.id);
  ElMessage.success('已发布');
  await getList();
}

async function handleUnpublish(row: ReportDefinitionItemDto) {
  if (!row.id) {
    return;
  }
  await runUnpublish(row.id);
  ElMessage.success('已撤回');
  await getList();
}

async function handleDelete(row: ReportDefinitionItemDto) {
  if (!row.id) {
    return;
  }
  if (row.sourceType !== 'sql') {
    ElMessage.warning('出厂 Loader 报表不可删除');
    return;
  }
  await ElMessageBox.confirm(`确定删除报表「${row.name ?? row.reportCode}」？`, '删除确认', {
    type: 'warning',
  });
  await deleteReportDefinitionApi(row.id);
  ElMessage.success('已删除');
  await getList();
}

onMounted(() => {
  void loadPresets();
  void getList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :form-items="formItems"
      :loading="listLoading"
      :show-pagination="false"
      @query="getList"
      @reset="getList"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建报表</ElButton>
      </template>

      <template #sourceType="{ row }">
        <ElTag size="small" :type="row.sourceType === 'sql' ? 'success' : 'info'">
          {{ row.sourceType === 'sql' ? 'SQL' : '出厂' }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag size="small" :type="row.status === 'published' ? 'success' : 'info'">
          {{ row.status === 'published' ? '已发布' : '草稿' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton link type="primary" @click="goDesign(row)">设计</ElButton>
        <ElTooltip
          v-if="row.sourceType !== 'sql'"
          content="出厂 Loader 报表不可复制为 SQL 报表"
          placement="top"
        >
          <ElButton link type="primary" disabled>复制</ElButton>
        </ElTooltip>
        <ElButton v-else link type="primary" @click="openCopy(row)">复制</ElButton>
        <ElButton
          v-if="row.status !== 'published'"
          link
          type="success"
          :loading="publishLoading"
          @click="handlePublish(row)"
        >
          发布
        </ElButton>
        <ElButton
          v-else
          link
          type="warning"
          :loading="unpublishLoading"
          @click="handleUnpublish(row)"
        >
          撤回
        </ElButton>
        <ElButton v-if="row.sourceType === 'sql'" link type="danger" @click="handleDelete(row)">
          删除
        </ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="createVisible"
      title="新建 SQL 报表"
      width="520px"
      destroy-on-close
      align-center
    >
      <ElForm label-width="96px">
        <ElFormItem label="报表编码" required>
          <ElInput v-model="createForm.reportCode" placeholder="如 dept_drug_usage" clearable />
        </ElFormItem>
        <ElFormItem label="报表名称" required>
          <ElInput v-model="createForm.name" placeholder="显示名称" clearable />
        </ElFormItem>
        <ElFormItem label="预制模板">
          <ElSelect
            v-model="createForm.presetReportCode"
            filterable
            clearable
            placeholder="可选：从 V20 预制 SQL 初始化"
            class="w-full"
          >
            <ElOptionGroup
              v-for="group in groupedPresets"
              :key="group.priority"
              :label="group.label"
            >
              <ElOption
                v-for="preset in group.items"
                :key="preset.reportCode"
                :label="`${preset.name} (${preset.reportCode})`"
                :value="preset.reportCode ?? ''"
              />
            </ElOptionGroup>
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="createForm.remark" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="createLoading" @click="handleCreate"
          >创建并设计</ElButton
        >
      </template>
    </ElDialog>

    <ElDialog v-model="copyVisible" title="复制报表" width="520px" destroy-on-close align-center>
      <ElForm label-width="96px">
        <ElFormItem label="源报表">
          <span>{{ copySource?.name }} ({{ copySource?.reportCode }})</span>
        </ElFormItem>
        <ElFormItem label="新编码" required>
          <ElInput v-model="copyForm.reportCode" clearable />
        </ElFormItem>
        <ElFormItem label="新名称" required>
          <ElInput v-model="copyForm.name" clearable />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="copyForm.remark" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="copyVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="copyLoading" @click="handleCopy">复制并设计</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
