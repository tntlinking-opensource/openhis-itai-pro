<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { SettingDefinition } from '#/api/core/system/setting';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage } from 'element-plus';

import { initSettingDefinitionsApi } from '#/api/core/system/setting';

import ConfigDialog from './config-dialog.vue';

const allDefinitions = ref<SettingDefinition[]>([]);
const tableData = ref<SettingDefinition[]>([]);
const loading = ref(false);
const total = ref(0);
const initQueryParams = {
  pageNo: 1,
  pageSize: 10,
  keyword: '',
};
const queryParams = reactive({ ...initQueryParams });
const formItems = ref([
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
  },
]);
const tableColumns = ref<TableColumn[]>([
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    label: '标题',
    prop: 'title',
    minWidth: 240,
    showOverflowTooltip: true,
  },
  {
    label: '名称',
    prop: 'name',
    minWidth: 240,
    showOverflowTooltip: true,
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    width: 120,
  },
]);

const handleQuery = async () => {
  loading.value = true;
  try {
    const keyword = (queryParams.keyword || '').trim().toLowerCase();
    const filtered = keyword
      ? allDefinitions.value.filter((x) => {
          const name = (x.name || '').toLowerCase();
          const title = (x.title || '').toLowerCase();
          return name.includes(keyword) || title.includes(keyword);
        })
      : allDefinitions.value;

    total.value = filtered.length;
    const start = (queryParams.pageNo - 1) * queryParams.pageSize;
    tableData.value = filtered.slice(start, start + queryParams.pageSize);
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const loadInit = async () => {
  loading.value = true;
  try {
    allDefinitions.value = (await initSettingDefinitionsApi()) || [];
  } catch {
    allDefinitions.value = [];
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  Object.assign(queryParams, initQueryParams);
  handleQuery();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.pageSize = pagination.limit;
  queryParams.pageNo = pagination.page;
  handleQuery();
};

const dialogVisible = ref(false);
const currentDefinition = ref<null | SettingDefinition>(null);

const handleConfig = (row: SettingDefinition) => {
  currentDefinition.value = { ...row };
  dialogVisible.value = true;
};

const handleDialogClose = (v: boolean) => {
  dialogVisible.value = v;
};

onMounted(() => {
  loadInit().then(handleQuery);
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleConfig(row)"> 配置 </ElButton>
      </template>
    </TableLayout>

    <ConfigDialog
      v-model:visible="dialogVisible"
      :definition="currentDefinition"
      @update:visible="handleDialogClose"
    />
  </Page>
</template>
