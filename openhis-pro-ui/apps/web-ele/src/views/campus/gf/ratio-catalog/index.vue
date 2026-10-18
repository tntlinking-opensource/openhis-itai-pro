<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { GfCatalogRatioDto } from '#/api/campus/campus-gf-ratio';
import type { GfChargeMode } from '#/api/campus/types';

import { onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  disableCatalogRatioApi,
  pageCatalogRatiosApi,
  saveCatalogRatioApi,
} from '#/api/campus/campus-gf-ratio';
import { gfChargeModeLabel, toIdString, unwrapCampusPage } from '#/api/campus/types';
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
  ElMessageBox,
} from 'element-plus';

defineOptions({ name: 'CampusGfRatioCatalog' });

const chargeMode = ref<GfChargeMode>('SELF_PAY_GF');
const list = ref<GfCatalogRatioDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Record<string, unknown>>({});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '目录编码' },
];

const columns: TableColumn[] = [
  { prop: 'catalogCode', label: '目录编码', minWidth: 140 },
  { prop: 'chargeModeLabel', label: '收费模式', width: 110 },
  { prop: 'ratio', label: '比例', width: 90 },
  { prop: 'effectiveFrom', label: '生效起', minWidth: 150 },
  { prop: 'effectiveTo', label: '生效止', minWidth: 150 },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

async function loadList() {
  const res = await pageCatalogRatiosApi({
    chargeModeEnum: chargeMode.value,
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
  editForm.value = { ratio: 1, chargeModeEnum: chargeMode.value };
  editVisible.value = true;
}

function openEdit(row: GfCatalogRatioDto) {
  editForm.value = { ...row, id: toIdString(row.id) };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.catalogCode || '').trim()) {
    ElMessage.warning('请填写目录编码');
    return;
  }
  await saveCatalogRatioApi({
    id: form.id ? toIdString(form.id) : null,
    catalogCode: String(form.catalogCode).trim(),
    chargeModeEnum: chargeMode.value,
    ratio: Number(form.ratio ?? 0),
    effectiveFrom: form.effectiveFrom as string | undefined,
    effectiveTo: form.effectiveTo as string | undefined,
  });
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function handleDisable(row: GfCatalogRatioDto) {
  try {
    await ElMessageBox.confirm(`确认停用目录比例「${row.catalogCode}」？`, '停用', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await disableCatalogRatioApi({ id: row.id! });
  ElMessage.success('已停用');
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
  <Page title="目录比例" description="按收费模式维护目录级公费报销比例（0～1）。">
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
        <ElButton type="primary" @click="openCreate">新建</ElButton>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="primary" @click="handleDisable(row)">停用</ElButton>
      </template>
    </TableLayout>

    <ElDialog v-model="editVisible" title="目录比例" width="480px" destroy-on-close>
      <ElForm label-width="90px">
        <ElFormItem label="收费模式">
          <span>{{ gfChargeModeLabel(chargeMode) }}</span>
        </ElFormItem>
        <ElFormItem label="目录编码" required>
          <ElInput v-model="editForm.catalogCode" :disabled="Boolean(editForm.id)" />
        </ElFormItem>
        <ElFormItem label="比例" required>
          <ElInputNumber v-model="editForm.ratio" :min="0" :max="1" :step="0.01" :precision="4" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
