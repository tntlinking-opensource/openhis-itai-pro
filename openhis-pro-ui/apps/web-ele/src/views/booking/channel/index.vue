<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { ChannelConfigDto } from '#/api/booking/channel-booking';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  deleteChannelConfigApi,
  loadChannelConfigApi,
  pageChannelConfigsApi,
  saveChannelConfigApi,
} from '#/api/booking/channel-booking';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElSwitch,
} from 'element-plus';

defineOptions({ name: 'BookingChannel' });

const list = ref<ChannelConfigDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Partial<ChannelConfigDto>>({ enabledFlag: true });

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '渠道编码/名称' },
];

const columns: TableColumn[] = [
  { prop: 'channelCode', label: '渠道编码', minWidth: 120 },
  { prop: 'channelName', label: '渠道名称', minWidth: 140 },
  { prop: 'enabledFlag', label: '启用', width: 80, slot: 'enabledFlag' },
  { prop: 'quotaPercent', label: '配额(%)', width: 90 },
  { prop: 'remarkText', label: '备注', minWidth: 160 },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const { run: runPage, loading } = useRequest(pageChannelConfigsApi, {
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
  editForm.value = { enabledFlag: true, quotaPercent: 100 };
  editVisible.value = true;
}

async function openEdit(row: ChannelConfigDto) {
  if (!row.id) return;
  const detail = await loadChannelConfigApi({ id: row.id });
  editForm.value = { ...detail };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!form.channelCode?.trim() || !form.channelName?.trim()) {
    ElMessage.warning('请填写渠道编码与名称');
    return;
  }
  await saveChannelConfigApi({
    id: form.id,
    channelCode: form.channelCode.trim(),
    channelName: form.channelName.trim(),
    enabledFlag: form.enabledFlag,
    quotaPercent: form.quotaPercent,
    remarkText: form.remarkText,
  });
  ElMessage.success('渠道配置已保存');
  editVisible.value = false;
  await loadData();
}

async function handleDelete(row: ChannelConfigDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除渠道「${row.channelName}」？`, '删除渠道');
    await deleteChannelConfigApi({ id: row.id });
    ElMessage.success('已删除');
    await loadData();
  } catch {
    // cancel
  }
}

onMounted(loadData);
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:query-params="query"
      :form-items="formItems"
      :table-columns="columns"
      :table-data="list"
      :total="total"
      :loading="loading"
      @search="handleSearch"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建渠道</ElButton>
      </template>
      <template #enabledFlag="{ row }">
        {{ row.enabledFlag ? '是' : '否' }}
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>

    <ElDialog v-model="editVisible" :title="editForm.id ? '编辑渠道' : '新建渠道'" width="480px">
      <ElForm label-width="96px">
        <ElFormItem label="渠道编码" required>
          <ElInput v-model="editForm.channelCode" placeholder="WINDOW / MINIAPP" />
        </ElFormItem>
        <ElFormItem label="渠道名称" required>
          <ElInput v-model="editForm.channelName" placeholder="窗口 / 小程序" />
        </ElFormItem>
        <ElFormItem label="启用">
          <ElSwitch v-model="editForm.enabledFlag" />
        </ElFormItem>
        <ElFormItem label="配额比例">
          <ElInputNumber v-model="editForm.quotaPercent" :min="0" :max="100" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="editForm.remarkText" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
