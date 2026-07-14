<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { Menu, PageMenuParams, UpdateMenuParams } from '#/api/core/system/menu.types';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';
import { IconifyIcon } from '@vben/icons';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSwitch, ElTag } from 'element-plus';

import {
  deleteMenuApi,
  disableMenuApi,
  enableMenuApi,
  getMenuTreeApi,
  pageMenuApi,
  updateMenuApi,
} from '#/api/core/system/menu';

import { resetAndRefreshAccessRoutes } from '#/router/access-init';
import { router } from '#/router';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'MenuList',
});

const accessStore = useAccessStore();

const queryParams = ref<PageMenuParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
});

const tableData = ref<Menu[]>([]);
const allMenus = ref<Menu[]>([]); // 存储所有菜单树，用于编辑对话框的父菜单选择
const loading = ref(false);
const total = ref(0);

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
  },
  {
    prop: 'name',
    label: '菜单名称',
    treeNode: true,
    width: 200,
  },
  {
    prop: 'displayName',
    label: '显示名称',
    width: 200,
  },
  {
    prop: 'icon',
    label: '图标',
    width: 80,
    slot: 'icon',
    align: 'center',
  },
  {
    prop: 'path',
    label: '路由路径',
    width: 200,
  },
  {
    prop: 'component',
    label: '组件路径',
  },

  {
    prop: 'sortOrder',
    label: '排序',
    width: 80,
    align: 'center',
  },
  {
    prop: 'isHidden',
    label: '是否隐藏',
    slot: 'isHidden',
    align: 'center',
    width: 100,
  },
  {
    prop: 'isEnabled',
    label: '状态',
    slot: 'isEnabled',
    align: 'center',
    width: 100,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 250,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入菜单名称或路径',
    clearable: true,
  },
]);

// 加载所有菜单树（用于编辑对话框的父菜单选择）
const loadAllMenus = async () => {
  try {
    const response = await getMenuTreeApi();
    const data = (response as unknown as { data?: Menu[] }).data || (response as Menu[]);
    allMenus.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('加载菜单树失败:', error);
    allMenus.value = [];
  }
};

// 递归排序树形数据
const sortTreeData = (data: Menu[]): Menu[] => {
  return data
    .map((item) => ({
      ...item,
      children: item.children && item.children.length > 0 ? sortTreeData(item.children) : [],
    }))
    .toSorted((a, b) => {
      const aOrder = a.sortOrder ?? 0;
      const bOrder = b.sortOrder ?? 0;
      return aOrder - bOrder;
    });
};

// 加载分页数据
const loadData = async () => {
  loading.value = true;
  try {
    const response = await pageMenuApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword,
    });
    const content = response.content || [];
    tableData.value = sortTreeData(content);
    const responseData = response;
    total.value = responseData.totalCount;
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

// 查询处理
const handleQuery = (params: { keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
  };
  loadData();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<Menu>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleAddChild = (row: Menu) => {
  dialogFormData.value = {
    parentId: row.id,
  };
  dialogVisible.value = true;
};

const handleEdit = (row: Menu) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleEnable = async (row: Menu) => {
  if (!row.id) {
    ElMessage.warning('缺少菜单ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要启用该菜单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await enableMenuApi({ id: row.id });
    ElMessage.success('启用成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '启用失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const hiddenUpdatingIds = ref<Set<string>>(new Set());

const refreshRoutesAfterMenuChange = async () => {
  if (!accessStore.accessToken) {
    return;
  }
  try {
    await resetAndRefreshAccessRoutes(router);
  } catch (error) {
    console.error('[MenuList] refresh routes after menu change failed', error);
  }
};

const handleToggleHidden = async (row: Menu, value: boolean) => {
  if (!row.id) {
    ElMessage.warning('缺少菜单ID');
    return;
  }
  if (hiddenUpdatingIds.value.has(row.id)) {
    return;
  }

  const previous = row.isHidden;
  row.isHidden = value;
  hiddenUpdatingIds.value.add(row.id);

  try {
    await updateMenuApi({
      id: row.id,
      request: {
        isHidden: String(value),
      } as UpdateMenuParams['request'],
    });
    ElMessage.success(value ? '已隐藏' : '已取消隐藏');
    await refreshRoutesAfterMenuChange();
  } catch (error: any) {
    row.isHidden = previous;
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      '更新隐藏状态失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    hiddenUpdatingIds.value.delete(row.id);
  }
};

const handleDisable = async (row: Menu) => {
  if (!row.id) {
    ElMessage.warning('缺少菜单ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要停用该菜单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await disableMenuApi({ id: row.id });
    ElMessage.success('停用成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '停用失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleDelete = async (row: Menu) => {
  if (!row.id) {
    ElMessage.warning('缺少菜单ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该菜单吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteMenuApi({ id: row.id });
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '删除失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleDialogSuccess = async () => {
  loadData();
  loadAllMenus();
  await refreshRoutesAfterMenuChange();
};

onMounted(() => {
  loadAllMenus();
  loadData();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams as any"
      :form-items="formItems"
      :table-columns="tableColumns"
      :expand-all="true"
      :show-side-query="false"
      :show-top-query="true"
      :show-pagination="true"
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
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #icon="{ row }">
        <IconifyIcon v-if="row.icon" :icon="row.icon" class="mx-auto size-5" />
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #isHidden="{ row }">
        <ElSwitch
          :model-value="row.isHidden"
          :disabled="hiddenUpdatingIds.has(row.id)"
          @click.stop
          @change="(value) => handleToggleHidden(row, value === true)"
        />
      </template>

      <template #isEnabled="{ row }">
        <ElTag :type="row.isEnabled ? 'success' : 'info'">
          {{ row.isEnabled ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)"> 编辑 </ElButton>
        <ElButton type="success" link @click.stop="handleAddChild(row)"> 新增 </ElButton>
        <ElButton v-if="!row.isEnabled" type="success" link @click.stop="handleEnable(row)">
          启用
        </ElButton>
        <ElButton v-if="row.isEnabled" type="warning" link @click.stop="handleDisable(row)">
          停用
        </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :all-menus="allMenus"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
