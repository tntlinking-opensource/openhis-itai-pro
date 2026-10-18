<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { BodyItem } from '#/api/basic/body';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import { activeBodyApi, deleteBodyApi, getBodyListApi, inactiveBodyApi } from '#/api/basic/body';
import { PublicationStatusCode } from '#/constants/domain/publication-status';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicBodyIndex' });

const allData = ref<BodyItem[]>([]);
const tableData = ref<BodyItem[]>([]);
const loading = ref(false);
const total = ref(0);

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
  {
    prop: 'busNo',
    label: '业务编号',
    width: 130,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '名称',
    minWidth: 180,
    treeNode: true,
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

/** 统计树节点总数，供列表展示 */
function countTreeNodes(items: BodyItem[] | undefined): number {
  if (!items || items.length === 0) {
    return 0;
  }
  let count = 0;
  for (const item of items) {
    count++;
    count += countTreeNodes(item.children);
  }
  return count;
}

/** 将平面列表按 parentId 组装为树 */
function buildTreeFromFlat(items: BodyItem[]): BodyItem[] {
  const nodeMap = new Map<string, BodyItem>();
  for (const item of items) {
    if (item.id !== undefined && item.id !== null) {
      nodeMap.set(String(item.id), { ...item, children: [] });
    }
  }

  const roots: BodyItem[] = [];
  for (const item of items) {
    const node =
      item.id !== undefined && item.id !== null ? nodeMap.get(String(item.id)) : undefined;
    if (!node) {
      continue;
    }
    const parentKey =
      item.parentId === undefined || item.parentId === null ? '' : String(item.parentId);
    const parentNode = parentKey ? nodeMap.get(parentKey) : undefined;
    if (parentNode) {
      if (!parentNode.children) {
        parentNode.children = [];
      }
      parentNode.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

/** 按业务编号稳定排序整棵树 */
function sortTreeByBusNo(items: BodyItem[]): BodyItem[] {
  const sorted = [...items].toSorted((a, b) =>
    (a.busNo ?? '').localeCompare(b.busNo ?? '', 'zh-CN'),
  );
  for (const item of sorted) {
    if (item.children && item.children.length > 0) {
      item.children = sortTreeByBusNo(item.children);
    }
  }
  return sorted;
}

/** 判断单条部位是否命中关键词 */
function matchesKeyword(item: BodyItem, keyword: string): boolean {
  return (
    item.name?.toLowerCase().includes(keyword) ||
    item.busNo?.toLowerCase().includes(keyword) ||
    item.pyStr?.toLowerCase().includes(keyword) ||
    item.wbStr?.toLowerCase().includes(keyword) ||
    item.statusEnumText?.toLowerCase().includes(keyword)
  );
}

/** 收集匹配节点及其全部祖先，保证树检索时父路径可见 */
function collectVisibleIds(items: BodyItem[], keyword: string): Set<string> {
  const idToParent = new Map<string, string>();
  for (const item of items) {
    if (item.id && item.parentId) {
      idToParent.set(String(item.id), String(item.parentId));
    }
  }

  const matchedIds = new Set<string>();
  for (const item of items) {
    if (item.id && matchesKeyword(item, keyword)) {
      matchedIds.add(String(item.id));
    }
  }

  const visibleIds = new Set(matchedIds);
  for (const id of matchedIds) {
    let parentId = idToParent.get(id);
    while (parentId) {
      visibleIds.add(parentId);
      parentId = idToParent.get(parentId);
    }
  }
  return visibleIds;
}

/** 按关键词过滤后组树并刷新表格 */
function filterData() {
  const keyword = queryParams.keyword?.toLowerCase().trim() || '';
  const source = keyword
    ? allData.value.filter(
        (item) => item.id && collectVisibleIds(allData.value, keyword).has(String(item.id)),
      )
    : allData.value;
  const roots = sortTreeByBusNo(buildTreeFromFlat(source));
  tableData.value = roots;
  total.value = countTreeNodes(roots);
}

/** 加载全量列表并刷新树形表格 */
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
    total.value = 0;
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
    case PublicationStatusCode.INACTIVE: {
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
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-pagination="false"
      :tree="true"
      :tree-config="{
        children: 'children',
        indent: 20,
        line: false,
        expandAll: true,
        accordion: false,
        trigger: 'default',
      }"
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
