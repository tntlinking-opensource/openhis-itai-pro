<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ReportDefinitionItemDto } from '#/api/report/report-query';

import { computed, reactive, ref } from 'vue';
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
  ElTag,
} from 'element-plus';

import {
  createReportDefinitionApi,
  deleteReportDefinitionApi,
  listReportDefinitionsApi,
  publishReportDefinitionApi,
  unpublishReportDefinitionApi,
} from '#/api/report/report-query';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'WhaleReportTemplate',
});

const router = useRouter();
const tableData = ref<ReportDefinitionItemDto[]>([]);
const createVisible = ref(false);
const createForm = reactive({
  reportCode: '',
  name: '',
  remark: '',
});

const queryParams = reactive<{ keyword?: string; status?: string }>({});

const { run: runList, loading: listLoading } = useRequest(listReportDefinitionsApi, {
  manual: true,
});

const { run: runCreate, loading: createLoading } = useRequest(createReportDefinitionApi, {
  manual: true,
});

const { run: runPublish, loading: publishLoading } = useRequest(publishReportDefinitionApi, {
  manual: true,
});

const { run: runUnpublish, loading: unpublishLoading } = useRequest(unpublishReportDefinitionApi, {
  manual: true,
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
  { prop: 'action', label: '操作', fixed: 'right', width: 300, align: 'center', slot: 'action' },
];

async function getList() {
  const rows = await runList({
    keyword: queryParams.keyword?.trim() || undefined,
    status: queryParams.status?.trim() || undefined,
  });
  tableData.value = Array.isArray(rows) ? rows : [];
}

function openCreate() {
  createForm.reportCode = '';
  createForm.name = '';
  createForm.remark = '';
  createVisible.value = true;
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
  });
  ElMessage.success('已创建');
  createVisible.value = false;
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

async function handlePublish(row: ReportDefinitionItemDto) {
  if (!row.id) {
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

void getList();
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:query-params="queryParams"
      :columns="tableColumns"
      :data="tableData"
      :form-items="formItems"
      :loading="listLoading"
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
      width="480px"
      destroy-on-close
      align-center
    >
      <ElForm label-width="88px">
        <ElFormItem label="报表编码" required>
          <ElInput v-model="createForm.reportCode" placeholder="如 dept_drug_usage" clearable />
        </ElFormItem>
        <ElFormItem label="报表名称" required>
          <ElInput v-model="createForm.name" placeholder="显示名称" clearable />
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
  </Page>
</template>
