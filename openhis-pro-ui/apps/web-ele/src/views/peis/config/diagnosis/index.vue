<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { PeisCommonResultDto, PeisDiagnosisTemplateDto } from '#/api/peis/diagnosis-config';

import { onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';

import {
  saveCommonResultApi,
  saveDiagnosisTemplateApi,
  searchCommonResultApi,
  searchDiagnosisTemplateApi,
} from '#/api/peis/diagnosis-config';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'PeisDiagnosisConfig' });

type DiagnosisTab = 'template' | 'commonResult';

const activeTab = ref<DiagnosisTab>('template');
const list = ref<Array<PeisDiagnosisTemplateDto | PeisCommonResultDto>>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Record<string, any>>({});

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
];

const statusOptions = [
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
];

/** 体检诊断专用来源（与院内 hospital_dx 隔离，不进医生站） */
const PEIS_DX_SOURCE = 'peis_dx';

const sourceLabelMap: Record<string, string> = {
  peis_dx: '体检诊断',
  hospital_dx: '院内诊断',
};

const templateColumns: TableColumn[] = [
  { prop: 'code', label: '编码', minWidth: 120 },
  { prop: 'display', label: '名称', minWidth: 180 },
  { prop: 'categoryCode', label: '来源', width: 120, slot: 'categoryCode' },
  { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
  { prop: 'actions', label: '操作', width: 100, slot: 'actions' },
];

const commonResultColumns: TableColumn[] = [
  { prop: 'display', label: '名称', minWidth: 160 },
  { prop: 'valueString', label: '默认值', minWidth: 140 },
  { prop: 'interpretationCode', label: '解读', width: 90 },
  { prop: 'sortNum', label: '排序', width: 80 },
  { prop: 'statusEnum', label: '状态', width: 90 },
  { prop: 'actions', label: '操作', width: 100, slot: 'actions' },
];

const columns = ref<TableColumn[]>(templateColumns);

async function loadByTab() {
  const params = {
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  };
  if (activeTab.value === 'template') {
    const res = await searchDiagnosisTemplateApi(params);
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
    return;
  }
  const res = await searchCommonResultApi(params);
  list.value = res.content ?? [];
  total.value = Number(res.totalCount ?? 0);
}

const { run: runLoad, loading } = useRequest(loadByTab, { manual: true });

function handleSearch() {
  query.pageNo = 1;
  void runLoad();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void runLoad();
}

watch(activeTab, (tab) => {
  columns.value = tab === 'template' ? templateColumns : commonResultColumns;
  query.pageNo = 1;
  void runLoad();
});

function labelOfSource(code: unknown): string {
  const raw = String(code ?? '');
  return sourceLabelMap[raw] || raw || '—';
}

function labelOfStatus(code: unknown): string {
  const raw = String(code ?? '');
  const hit = statusOptions.find((o) => o.value === raw);
  return hit?.label || raw || '—';
}

function openCreate() {
  if (activeTab.value === 'template') {
    editForm.value = { statusEnum: 'active', categoryCode: PEIS_DX_SOURCE };
  } else {
    editForm.value = { statusEnum: 'active', sortNum: 1 };
  }
  editVisible.value = true;
}

function openEdit(row: Record<string, any>) {
  editForm.value = { ...row };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (activeTab.value === 'template') {
    if (!form.code?.trim() || !form.display?.trim()) {
      ElMessage.warning('请填写诊断编码与名称');
      return;
    }
    await saveDiagnosisTemplateApi({
      // 雪花 ID 以字符串提交，避免精度丢失
      definitionId: form.definitionId ? String(form.definitionId) : undefined,
      code: form.code.trim(),
      display: form.display.trim(),
      // 来源由后端固定 peis_dx；此处不传业务分类以免误写入 typeCode
      statusEnum: form.statusEnum || 'active',
    });
  } else {
    if (!form.display?.trim()) {
      ElMessage.warning('请填写常见结果名称');
      return;
    }
    await saveCommonResultApi({
      id: form.id ? Number(form.id) : undefined,
      observationDefinitionId: form.observationDefinitionId
        ? Number(form.observationDefinitionId)
        : undefined,
      deptOrgId: form.deptOrgId ? Number(form.deptOrgId) : undefined,
      display: form.display.trim(),
      valueString: form.valueString,
      interpretationCode: form.interpretationCode,
      sortNum: form.sortNum ?? 1,
      statusEnum: form.statusEnum || 'active',
    });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

onMounted(() => {
  void runLoad();
});
</script>

<template>
  <Page
    title="诊断与表达式"
    description="诊断建议库（来源：体检诊断 peis_dx，不进医生站）/ 常见结果"
  >
    <ElTabs v-model="activeTab" class="mb-3">
      <ElTabPane label="诊断建议库" name="template" />
      <ElTabPane label="常见结果" name="commonResult" />
    </ElTabs>

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
      <template #categoryCode="{ row }">
        {{ labelOfSource(row.categoryCode) }}
      </template>
      <template #statusEnum="{ row }">
        {{ labelOfStatus(row.statusEnum) }}
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
      </template>
    </TableLayout>

    <ElDialog v-model="editVisible" title="编辑" width="520px" destroy-on-close>
      <ElForm label-width="120px">
        <template v-if="activeTab === 'template'">
          <ElFormItem label="编码" required>
            <ElInput v-model="editForm.code" />
          </ElFormItem>
          <ElFormItem label="名称" required>
            <ElInput v-model="editForm.display" />
          </ElFormItem>
          <ElFormItem label="来源">
            <ElInput model-value="体检诊断" disabled />
          </ElFormItem>
        </template>
        <template v-else>
          <ElFormItem label="名称" required>
            <ElInput v-model="editForm.display" />
          </ElFormItem>
          <ElFormItem label="默认值">
            <ElInput v-model="editForm.valueString" />
          </ElFormItem>
          <ElFormItem label="解读码">
            <ElInput v-model="editForm.interpretationCode" placeholder="H/L/N" />
          </ElFormItem>
          <ElFormItem label="小项OD主键">
            <ElInput v-model="editForm.observationDefinitionId" />
          </ElFormItem>
          <ElFormItem label="科室主键">
            <ElInput v-model="editForm.deptOrgId" />
          </ElFormItem>
          <ElFormItem label="排序">
            <ElInputNumber v-model="editForm.sortNum" :min="0" class="w-full" />
          </ElFormItem>
        </template>
        <ElFormItem label="状态">
          <ElSelect v-model="editForm.statusEnum" class="w-full">
            <ElOption
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
