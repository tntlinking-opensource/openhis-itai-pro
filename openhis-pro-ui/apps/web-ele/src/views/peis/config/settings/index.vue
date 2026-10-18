<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { PeisConfigDto } from '#/api/peis/module-settings';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  PEIS_CONFIG_KEY_OPTIONS,
  saveConfigApi,
  searchConfigApi,
} from '#/api/peis/module-settings';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

defineOptions({ name: 'PeisModuleSettings' });

const list = ref<PeisConfigDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Partial<PeisConfigDto>>({});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '配置键' },
];

const columns: TableColumn[] = [
  { prop: 'configKey', label: '配置键', minWidth: 220 },
  { prop: 'configValue', label: '配置值', minWidth: 200 },
  { prop: 'actions', label: '操作', width: 100, slot: 'actions' },
];

const { run: runPage, loading } = useRequest(searchConfigApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

async function loadData() {
  await runPage({
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

function handleSearch() {
  query.pageNo = 1;
  void loadData();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void loadData();
}

function openCreate() {
  editForm.value = {
    configKey: PEIS_CONFIG_KEY_OPTIONS[0]?.value,
    configValue: '',
  };
  editVisible.value = true;
}

function openEdit(row: PeisConfigDto) {
  editForm.value = { ...row };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!form.configKey?.trim()) {
    ElMessage.warning('请选择配置键');
    return;
  }
  if (form.configValue === undefined || form.configValue === null) {
    ElMessage.warning('请填写配置值');
    return;
  }
  await saveConfigApi({
    id: form.id ? Number(form.id) : undefined,
    configKey: form.configKey.trim(),
    configValue: String(form.configValue),
    orgId: form.orgId ? Number(form.orgId) : undefined,
  });
  ElMessage.success('参数已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await loadData();
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <Page title="系统参数" description="PEIS 模块 KV 参数（AS-PEIS-CFG-003 · peis_config）">
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
        <ElButton type="primary" @click="openCreate">新建/覆盖</ElButton>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
      </template>
    </TableLayout>

    <ElDialog v-model="editVisible" title="编辑系统参数" width="520px" destroy-on-close>
      <ElForm label-width="100px">
        <ElFormItem label="配置键" required>
          <ElSelect v-model="editForm.configKey" class="w-full" filterable>
            <ElOption
              v-for="opt in PEIS_CONFIG_KEY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="配置值" required>
          <ElInput
            v-model="editForm.configValue"
            type="textarea"
            :rows="3"
            placeholder="如 true / false / 模板编码"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
