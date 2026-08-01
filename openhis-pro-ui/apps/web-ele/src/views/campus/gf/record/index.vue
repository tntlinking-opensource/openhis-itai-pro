<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { GfRatioRequestDto } from '#/api/campus/campus-gf-record';
import type { GfChargeMode } from '#/api/campus/types';

import { onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';

import { createAdjustRequestApi, pageRequestsApi } from '#/api/campus/campus-gf-record';
import { gfChargeModeLabel, gfRequestStatusLabel, unwrapCampusPage } from '#/api/campus/types';
import { useRequest } from '#/hooks/use-request';
import ChargeModeSwitch from '#/views/campus/components/ChargeModeSwitch.vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'CampusGfRecord' });

const chargeMode = ref<GfChargeMode>('SELF_PAY_GF');
const list = ref<GfRatioRequestDto[]>([]);
const total = ref(0);
const createVisible = ref(false);
const createForm = ref<Record<string, unknown>>({});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  statusEnum: undefined as string | undefined,
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '单号/项目编码' },
  {
    prop: 'statusEnum',
    label: '状态',
    type: 'select',
    clearable: true,
    options: [
      { label: '待审批', value: 'PENDING' },
      { label: '已通过', value: 'APPROVED' },
      { label: '已驳回', value: 'REJECTED' },
    ],
  },
];

const columns: TableColumn[] = [
  { prop: 'requestNo', label: '申请单号', minWidth: 140 },
  { prop: 'itemCode', label: '项目编码', minWidth: 120 },
  { prop: 'chargeModeLabel', label: '收费模式', width: 110 },
  { prop: 'oldRatio', label: '原比例', width: 90 },
  { prop: 'newRatio', label: '新比例', width: 90 },
  { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
  { prop: 'applyTime', label: '申请时间', minWidth: 150 },
  { prop: 'remark', label: '备注', minWidth: 120, showOverflowTooltip: true },
];

async function loadList() {
  const res = await pageRequestsApi({
    chargeModeEnum: chargeMode.value,
    statusEnum: query.statusEnum || null,
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
  const page = unwrapCampusPage(res);
  list.value = page.list;
  total.value = page.total;
}

const { run: runLoad, loading } = useRequest(loadList, { manual: true });

function handleSearch() {
  query.pageNo = 1;
  void runLoad();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void runLoad();
}

function openCreate() {
  createForm.value = { chargeModeEnum: chargeMode.value };
  createVisible.value = true;
}

async function handleCreate() {
  const form = createForm.value;
  if (!String(form.itemCode || '').trim()) {
    ElMessage.warning('请填写项目编码');
    return;
  }
  if (form.newRatio == null) {
    ElMessage.warning('请填写新比例');
    return;
  }
  await createAdjustRequestApi({
    chargeModeEnum: chargeMode.value,
    itemCode: String(form.itemCode).trim(),
    newRatio: Number(form.newRatio),
    oldRatio: form.oldRatio != null ? Number(form.oldRatio) : undefined,
    reason: String(form.reason || '').trim() || undefined,
  });
  ElMessage.success('申请已提交');
  createVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

watch(chargeMode, () => {
  query.pageNo = 1;
  void runLoad();
});

onMounted(() => {
  void runLoad();
});
</script>

<template>
  <Page title="申请记录" description="项目比例调整申请列表；可按收费模式与状态筛选。">
    <div class="mb-3">
      <ChargeModeSwitch v-model="chargeMode" />
    </div>

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
        <ElButton type="primary" @click="openCreate">发起申请</ElButton>
      </template>
      <template #statusEnum="{ row }">
        <ElTag
          :type="
            row.statusEnum === 'APPROVED'
              ? 'success'
              : row.statusEnum === 'REJECTED'
                ? 'danger'
                : 'warning'
          "
        >
          {{ gfRequestStatusLabel(row.statusEnum) }}
        </ElTag>
      </template>
    </TableLayout>

    <ElDialog v-model="createVisible" title="发起比例调整申请" width="480px" destroy-on-close>
      <ElForm label-width="90px">
        <ElFormItem label="收费模式">
          <span>{{ gfChargeModeLabel(chargeMode) }}</span>
        </ElFormItem>
        <ElFormItem label="项目编码" required>
          <ElInput v-model="createForm.itemCode" />
        </ElFormItem>
        <ElFormItem label="原比例">
          <ElInputNumber
            v-model="createForm.oldRatio"
            :min="0"
            :max="1"
            :step="0.01"
            :precision="4"
          />
        </ElFormItem>
        <ElFormItem label="新比例" required>
          <ElInputNumber
            v-model="createForm.newRatio"
            :min="0"
            :max="1"
            :step="0.01"
            :precision="4"
          />
        </ElFormItem>
        <ElFormItem label="原因">
          <ElInput v-model="createForm.reason" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleCreate">提交</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
