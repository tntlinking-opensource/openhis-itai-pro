<script setup lang="ts">
import type { DocumentTypeDto } from '#/api/emr/document-type';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { deleteDocumentTypeApi, getDocumentTypeListApi } from '#/api/emr/document-type';

import DocumentTypeEditDialog from './document-type-edit-dialog.vue';

defineOptions({
  name: 'EmrDocumentTypePanel',
});

const emit = defineEmits<{
  change: [];
  select: [code: string | undefined];
}>();

const editDialogRef = ref<InstanceType<typeof DocumentTypeEditDialog> | null>(null);
const loading = ref(false);
const keyword = ref('');
const selectedCode = ref<string | undefined>(undefined);
const allRows = ref<DocumentTypeDto[]>([]);

/** 前端关键词过滤后的展示列表 */
const displayRows = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  if (!key) return allRows.value;
  return allRows.value.filter((row) => {
    const code = row.code?.toLowerCase() ?? '';
    const name = row.name?.toLowerCase() ?? '';
    return code.includes(key) || name.includes(key);
  });
});

/** 拉取全部文书类型（管理侧不过滤启用） */
async function loadList() {
  loading.value = true;
  try {
    const list = await getDocumentTypeListApi({});
    allRows.value = Array.isArray(list) ? list : [];
  } catch (error) {
    console.error(error);
    allRows.value = [];
    ElMessage.error('加载文书类型失败');
  } finally {
    loading.value = false;
    emit('change');
  }
}

/** 新建文书类型 */
function handleCreate() {
  editDialogRef.value?.openCreate();
}

/** 编辑当前行 */
function handleEdit(row: DocumentTypeDto) {
  editDialogRef.value?.openEdit(row);
}

/** 二次确认后删除 */
async function handleDelete(row: DocumentTypeDto) {
  const id = row.id;
  if (id === undefined || id === null) return;
  try {
    await ElMessageBox.confirm(`确定删除文书类型「${row.name ?? row.code}」吗？`, '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确定',
      type: 'warning',
    });
    await deleteDocumentTypeApi({ id: String(id) });
    ElMessage.success('删除成功');
    if (selectedCode.value === row.code) {
      selectedCode.value = undefined;
      emit('select', undefined);
    }
    await loadList();
  } catch (error: unknown) {
    if (error === 'cancel') return;
    console.error(error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '删除失败');
  }
}

/** 点击行：筛选右侧模板列表 */
function handleRowClick(row: DocumentTypeDto) {
  const code = row.code?.trim() || undefined;
  if (selectedCode.value === code) {
    selectedCode.value = undefined;
    emit('select', undefined);
    return;
  }
  selectedCode.value = code;
  emit('select', code);
}

/** 增删改成功后刷新列表 */
async function handleEditSuccess() {
  await loadList();
}

loadList();

defineExpose({
  loadList,
  getEnabledOptions: () =>
    allRows.value
      .filter((d) => d.isEnabled !== false && d.code)
      .map((d) => ({
        label: d.name ?? String(d.code),
        value: String(d.code),
      })),
});
</script>

<template>
  <section class="emr-doc-type-panel">
    <header class="emr-doc-type-panel__header">
      <span class="emr-doc-type-panel__title">文书类型</span>
      <ElButton type="primary" size="small" @click="handleCreate">新建</ElButton>
    </header>

    <div class="emr-doc-type-panel__query">
      <ElInput v-model="keyword" placeholder="编码 / 名称" clearable size="small" />
    </div>

    <ElTable
      v-loading="loading"
      :data="displayRows"
      size="small"
      height="100%"
      highlight-current-row
      row-key="id"
      class="emr-doc-type-panel__table"
      @row-click="handleRowClick"
    >
      <ElTableColumn prop="name" label="名称" min-width="88" show-overflow-tooltip />
      <ElTableColumn prop="code" label="编码" min-width="100" show-overflow-tooltip />
      <ElTableColumn label="分类" width="88" align="center">
        <template #default="{ row }">
          {{
            row.categoryCode === 'OUTPATIENT'
              ? '门诊病历'
              : row.categoryCode === 'INPATIENT'
                ? '住院病历'
                : row.categoryCode === 'NOTICE'
                  ? '告知书'
                  : row.categoryCode === 'SURGERY'
                    ? '手术病历'
                    : row.categoryCode === 'OTHER'
                      ? '其他'
                      : (row.categoryCode ?? '-')
          }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="56" align="center">
        <template #default="{ row }">
          <ElTag :type="row.isEnabled !== false ? 'success' : 'info'" size="small">
            {{ row.isEnabled !== false ? '启' : '停' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="88" fixed="right" align="center">
        <template #default="{ row }">
          <ElButton link type="primary" size="small" @click.stop="handleEdit(row)">编辑</ElButton>
          <ElButton link type="danger" size="small" @click.stop="handleDelete(row)">删</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <p class="emr-doc-type-panel__hint">点击行可筛选右侧模板；再次点击取消筛选</p>

    <DocumentTypeEditDialog ref="editDialogRef" @success="handleEditSuccess" />
  </section>
</template>

<style scoped>
.emr-doc-type-panel {
  display: flex;
  flex-direction: column;
  width: 380px;
  min-width: 320px;
  height: 100%;
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.emr-doc-type-panel__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.emr-doc-type-panel__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.emr-doc-type-panel__query {
  flex-shrink: 0;
  margin-bottom: 8px;
}

.emr-doc-type-panel__table {
  flex: 1;
  min-height: 0;
}

.emr-doc-type-panel__table :deep(.el-table__row) {
  cursor: pointer;
}

.emr-doc-type-panel__hint {
  flex-shrink: 0;
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
