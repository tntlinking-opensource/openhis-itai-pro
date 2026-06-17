<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  OrderTemplateHeaderItem,
  OrderTemplateInitResponse,
  PageMaintainedOrderTemplateParams,
} from '#/api/basic/order-template';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTabPane, ElTabs, ElTag } from 'element-plus';

import {
  changeOrderTemplateLifecycleApi,
  initOrderTemplateMaintenanceApi,
  ORDER_TEMPLATE_KIND_GENERAL,
  ORDER_TEMPLATE_KIND_PRICING,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LIFECYCLE_DELETE,
  ORDER_TEMPLATE_LIFECYCLE_PUBLISH,
  ORDER_TEMPLATE_LIFECYCLE_RETIRE,
  pageMaintainedOrderTemplatesApi,
} from '#/api/basic/order-template';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicOrderTemplate' });

const activeKind = ref<string>(ORDER_TEMPLATE_KIND_GENERAL);
const queryParams = ref<PageMaintainedOrderTemplateParams>({
  pageNo: 1,
  pageSize: 50,
  templateKindEnum: ORDER_TEMPLATE_KIND_GENERAL,
  keyword: '',
});

const tableData = ref<OrderTemplateHeaderItem[]>([]);
const total = ref(0);
const initOptions = ref<OrderTemplateInitResponse>({});

/** 列宽用百分比铺满表格，避免固定像素总宽不足导致右侧大片空白 */
const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: '6%', align: 'center' },
  { prop: 'name', label: '名称', width: '28%', showOverflowTooltip: true },
  { prop: 'statusEnum', label: '状态', width: '11%', align: 'center', slot: 'statusEnum' },
  { prop: 'bindingEnum', label: '绑定', width: '11%', slot: 'bindingEnum' },
  { prop: 'sceneEnum', label: '场景', width: '11%', slot: 'sceneEnum' },
  { prop: 'action', label: '操作', width: '33%', slot: 'action', align: 'center' },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '名称',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'bindingEnum',
    label: '绑定类型',
    options: initOptions.value.bindingOptions ?? [],
    clearable: true,
  },
  {
    type: 'select',
    prop: 'sceneEnum',
    label: '适用场景',
    options: initOptions.value.sceneOptions ?? [],
    clearable: true,
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '状态',
    options: initOptions.value.statusOptions ?? [],
    clearable: true,
  },
]);

const kindTabOptions = computed(() => {
  if (initOptions.value.templateKindOptions?.length) {
    return initOptions.value.templateKindOptions;
  }
  return [
    { value: ORDER_TEMPLATE_KIND_GENERAL, label: '通用组套' },
    { value: ORDER_TEMPLATE_KIND_TCM, label: '中药组套' },
    { value: ORDER_TEMPLATE_KIND_PRICING, label: '划价组套' },
  ];
});

const { run: runInit, loading: initLoading } = useRequest(initOrderTemplateMaintenanceApi, {
  manual: true,
  onSuccess: (data) => {
    initOptions.value = data ?? {};
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageMaintainedOrderTemplatesApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data.content ?? [];
    total.value = data.totalCount ?? 0;
  },
});

const loading = computed(() => initLoading.value || pageLoading.value);

/** 从 init 下拉解析展示文案 */
function optionLabel(
  options: Array<{ label: string; value: string }> | undefined,
  value: unknown,
  fallback?: string,
): string {
  if (fallback) {
    return fallback;
  }
  if (value === null || value === undefined || String(value).trim() === '') {
    return '-';
  }
  const hit = (options ?? []).find((item) => item.value === String(value));
  return hit?.label ?? String(value);
}

/** 状态标签颜色 */
function statusTagType(statusEnum: string | undefined): 'info' | 'success' | 'warning' {
  if (statusEnum === 'ACTIVE') {
    return 'success';
  }
  if (statusEnum === 'DRAFT') {
    return 'warning';
  }
  return 'info';
}

/** 加载组套列表 */
function loadData() {
  queryParams.value.templateKindEnum = activeKind.value;
  runPage(queryParams.value);
}

/** 切换组套种类 Tab */
function handleKindChange(kind: number | string) {
  activeKind.value = String(kind);
  queryParams.value.pageNo = 1;
  loadData();
}

/** 查询 */
function handleQuery(formModel: Record<string, unknown>) {
  queryParams.value = {
    ...queryParams.value,
    pageNo: 1,
    keyword: typeof formModel.keyword === 'string' ? formModel.keyword : queryParams.value.keyword,
    bindingEnum:
      typeof formModel.bindingEnum === 'string'
        ? formModel.bindingEnum
        : queryParams.value.bindingEnum,
    sceneEnum:
      typeof formModel.sceneEnum === 'string' ? formModel.sceneEnum : queryParams.value.sceneEnum,
    statusEnum:
      typeof formModel.statusEnum === 'string'
        ? formModel.statusEnum
        : queryParams.value.statusEnum,
  };
  loadData();
}

/** 重置查询 */
function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    templateKindEnum: activeKind.value,
    keyword: '',
  };
  loadData();
}

/** 分页 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
}

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<OrderTemplateHeaderItem>>({});

/** 打开新增明细弹窗 */
function handleAdd() {
  dialogFormData.value = { templateKindEnum: activeKind.value };
  dialogVisible.value = true;
}

/** 打开维护明细弹窗（头 + 明细一次保存） */
function handleEdit(row: OrderTemplateHeaderItem) {
  dialogFormData.value = {
    ...row,
    orderTemplateId:
      row.orderTemplateId !== null && row.orderTemplateId !== undefined
        ? String(row.orderTemplateId)
        : undefined,
  };
  dialogVisible.value = true;
}

/** 弹窗保存成功后刷新列表 */
function handleDialogSuccess() {
  loadData();
}

/** 变更组套生命周期 */
async function handleLifecycle(
  row: OrderTemplateHeaderItem,
  lifecycleActionCode: string,
  confirmMessage?: string,
) {
  const orderTemplateId = preserveApiIdForRequest(row.orderTemplateId);
  if (!orderTemplateId) {
    ElMessage.warning('缺少组套ID');
    return;
  }
  if (confirmMessage) {
    try {
      await ElMessageBox.confirm(confirmMessage, '确认操作', { type: 'warning' });
    } catch {
      return;
    }
  }
  try {
    await changeOrderTemplateLifecycleApi({ orderTemplateId, lifecycleActionCode });
    const msgMap: Record<string, string> = {
      [ORDER_TEMPLATE_LIFECYCLE_PUBLISH]: '发布成功',
      [ORDER_TEMPLATE_LIFECYCLE_RETIRE]: '已停用',
      [ORDER_TEMPLATE_LIFECYCLE_DELETE]: '删除成功',
    };
    ElMessage.success(msgMap[lifecycleActionCode] ?? '操作成功');
    loadData();
  } catch {
    ElMessage.error('操作失败，请稍后重试');
  }
}

onMounted(async () => {
  await runInit();
  loadData();
});

onActivated(() => {
  loadData();
});
</script>

<template>
  <Page auto-content-height>
    <ElTabs :model-value="activeKind" class="mb-2" @tab-change="handleKindChange">
      <ElTabPane
        v-for="item in kindTabOptions"
        :key="item.value"
        :label="item.label"
        :name="item.value"
      />
    </ElTabs>

    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      row-key="orderTemplateId"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增明细</ElButton>
      </template>

      <template #bindingEnum="{ row }">
        {{ optionLabel(initOptions.bindingOptions, row.bindingEnum, row.bindingEnumText) }}
      </template>

      <template #sceneEnum="{ row }">
        {{ optionLabel(initOptions.sceneOptions, row.sceneEnum, row.sceneEnumText) }}
      </template>

      <template #statusEnum="{ row }">
        <ElTag :type="statusTagType(row.statusEnum)">
          {{ optionLabel(initOptions.statusOptions, row.statusEnum, row.statusEnumText) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElSpace>
          <ElButton link type="primary" @click="handleEdit(row)">维护明细</ElButton>
          <ElButton
            v-if="row.statusEnum === 'DRAFT'"
            link
            type="primary"
            @click="handleLifecycle(row, ORDER_TEMPLATE_LIFECYCLE_PUBLISH)"
          >
            发布
          </ElButton>
          <ElButton
            v-if="row.statusEnum === 'ACTIVE'"
            link
            type="warning"
            @click="
              handleLifecycle(
                row,
                ORDER_TEMPLATE_LIFECYCLE_RETIRE,
                '停用后不可再被调用，确定停用吗？',
              )
            "
          >
            停用
          </ElButton>
          <ElButton
            link
            type="danger"
            @click="handleLifecycle(row, ORDER_TEMPLATE_LIFECYCLE_DELETE, '确定删除该医嘱组套吗？')"
          >
            删除
          </ElButton>
        </ElSpace>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :template-kind-enum="activeKind"
      :init-options="initOptions"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
