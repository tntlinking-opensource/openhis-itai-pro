<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { BodyItem } from '#/api/basic/body';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import { activeBodyApi, deleteBodyApi, getBodyListApi, inactiveBodyApi } from '#/api/basic/body';
import { PublicationStatusCode } from '#/constants/domain/publication-status';

import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicBodyIndex' });

const allData = ref<BodyItem[]>([]);
const tableData = ref<BodyItem[]>([]);
const loading = ref(false);

const initQueryParams = {
  keyword: '',
};
const queryParams = reactive({ ...initQueryParams });

const formItems = ref<FormItemConfig[]>([
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '名称 / 编码 / 拼音码',
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  {
    prop: 'busNo',
    label: '业务编号',
    width: 130,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '名称',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'parentName',
    label: '上级部位',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'pyStr',
    label: '拼音码',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 220,
    align: 'center',
  },
];

/** 由 parentId 映射上级部位名称，供列表展示 */
const parentNameMap = computed(() => {
  const map = new Map<string, string>();
  for (const item of allData.value) {
    if (item.id && item.name) {
      map.set(String(item.id), item.name);
    }
  }
  return map;
});

/** 判断部位是否为启用态 */
function isBodyActive(row: BodyItem): boolean {
  return row.statusEnum === PublicationStatusCode.ACTIVE;
}

/** 状态列 Tag 类型 */
function statusTagType(statusEnum?: string): 'danger' | 'info' | 'success' | 'warning' {
  switch (statusEnum) {
    case PublicationStatusCode.ACTIVE: {
      return 'success';
    }
    case PublicationStatusCode.DRAFT: {
      return 'warning';
    }
    case PublicationStatusCode.DISCARDED: {
      return 'danger';
    }
    default: {
      return 'info';
    }
  }
}

/** 附加 parentName 供表格展示 */
function enrichRows(items: BodyItem[]): BodyItem[] {
  return items.map((item) => {
    const parentId = item.parentId ? String(item.parentId) : '';
    return {
      ...item,
      parentName: parentId ? (parentNameMap.value.get(parentId) ?? '-') : '-',
    };
  });
}

/** 按关键词在前端过滤列表 */
function filterData() {
  const keyword = queryParams.keyword?.toLowerCase().trim() || '';
  const filtered = keyword
    ? allData.value.filter((item) => {
        const parentName = item.parentId
          ? (parentNameMap.value.get(String(item.parentId)) ?? '')
          : '';
        return (
          item.name?.toLowerCase().includes(keyword) ||
          item.busNo?.toLowerCase().includes(keyword) ||
          item.pyStr?.toLowerCase().includes(keyword) ||
          item.wbStr?.toLowerCase().includes(keyword) ||
          item.statusEnumText?.toLowerCase().includes(keyword) ||
          parentName.toLowerCase().includes(keyword)
        );
      })
    : allData.value;
  tableData.value = enrichRows(filtered);
}

/** 加载全量列表并刷新表格 */
async function loadAllData() {
  loading.value = true;
  try {
    allData.value = await getBodyListApi();
    filterData();
  } catch (error) {
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    allData.value = [];
    tableData.value = [];
  } finally {
    loading.value = false;
  }
}

/** 触发查询 */
function handleQuery() {
  filterData();
}

/** 重置查询条件 */
function handleReset() {
  Object.assign(queryParams, initQueryParams);
  filterData();
}

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<BodyItem>>({});

/** 打开新增弹窗 */
function handleAdd() {
  dialogFormData.value = {};
  dialogVisible.value = true;
}

/** 弹窗保存成功后刷新列表 */
function handleDialogSuccess() {
  loadAllData();
}

/** 打开编辑弹窗 */
function handleEdit(row: BodyItem) {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
}

/** 启用部位 */
async function handleEnable(row: BodyItem) {
  if (!row.id) {
    ElMessage.warning('缺少身体部位ID');
    return;
  }
  try {
    await activeBodyApi(row.id);
    ElMessage.success('启用成功');
    loadAllData();
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
}

/** 停用部位 */
async function handleDisable(row: BodyItem) {
  if (!row.id) {
    ElMessage.warning('缺少身体部位ID');
    return;
  }
  try {
    await inactiveBodyApi(row.id);
    ElMessage.success('停用成功');
    loadAllData();
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
}

/** 删除部位 */
async function handleDelete(row: BodyItem) {
  if (!row.id) {
    ElMessage.warning('缺少身体部位ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该身体部位吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteBodyApi(row.id);
    ElMessage.success('删除成功');
    loadAllData();
  } catch (error: unknown) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
}

onMounted(() => {
  loadAllData();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="false"
      row-key="id"
      stripe
      @query="handleQuery"
      @reset="handleReset"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #statusEnum="{ row }">
        <ElTag :type="statusTagType(row.statusEnum)" size="small" effect="plain">
          {{ row.statusEnumText || '-' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElSpace wrap :size="4">
          <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
          <ElButton v-if="!isBodyActive(row)" type="primary" link @click.stop="handleEnable(row)">
            启用
          </ElButton>
          <ElButton v-if="isBodyActive(row)" type="warning" link @click.stop="handleDisable(row)">
            停用
          </ElButton>
          <ElButton type="danger" link @click.stop="handleDelete(row)">删除</ElButton>
        </ElSpace>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
