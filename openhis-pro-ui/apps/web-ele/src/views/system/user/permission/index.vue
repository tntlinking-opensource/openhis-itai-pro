<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { PermissionInfo } from '#/api/user/permission';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { deletePermissionApi, getPermissionListApi } from '#/api/user/permission';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'PermissionList',
});

const queryParams = ref<{
  keyword?: string;
}>({
  keyword: '',
});

const tableData = ref<PermissionInfo[]>([]);
const allPermissions = ref<PermissionInfo[]>([]); // 存储所有权限树，用于编辑对话框的父权限选择
const loading = ref(false);
const total = ref(0);

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
  },
  {
    prop: 'name',
    label: '权限名称',
    treeNode: true,
    width: 220,
  },
  {
    prop: 'displayName',
    label: '显示名称',
    width: 200,
  },
  {
    prop: 'description',
    label: '描述',
    minWidth: 200,
  },
  {
    prop: 'providerName',
    label: '提供者名称',
    width: 150,
  },
  {
    prop: 'providerKey',
    label: '提供者键',
    width: 150,
  },

  {
    prop: 'createdBy',
    label: '创建人',
    width: 120,
  },
  {
    prop: 'createdAt',
    label: '创建时间',
    width: 180,
  },
  {
    prop: 'updatedBy',
    label: '更新人',
    width: 120,
  },
  {
    prop: 'updatedAt',
    label: '更新时间',
    width: 180,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入权限名称或显示名称',
    clearable: true,
  },
]);

// 加载所有权限树（用于编辑对话框的父权限选择）
const loadAllPermissions = async () => {
  try {
    const response = await getPermissionListApi();
    const data =
      (response as unknown as { data?: PermissionInfo[] }).data || (response as PermissionInfo[]);
    allPermissions.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('加载权限树失败:', error);
    allPermissions.value = [];
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const response = await getPermissionListApi();
    const data =
      (response as unknown as { data?: PermissionInfo[] }).data || (response as PermissionInfo[]);
    let allData = Array.isArray(data) ? data : [];

    // 如果有搜索关键词，进行过滤
    if (queryParams.value.keyword) {
      const keyword = queryParams.value.keyword.toLowerCase();
      const filterTree = (nodes: PermissionInfo[]): PermissionInfo[] => {
        const result: PermissionInfo[] = [];
        for (const node of nodes) {
          const matchesKeyword =
            node.name?.toLowerCase().includes(keyword) ||
            node.displayName?.toLowerCase().includes(keyword);
          const filteredChildren = node.children ? filterTree(node.children) : [];

          if (matchesKeyword || filteredChildren.length > 0) {
            result.push({
              ...node,
              children: filteredChildren.length > 0 ? filteredChildren : node.children,
            });
          }
        }
        return result;
      };
      allData = filterTree(allData);
    }

    tableData.value = allData;
    // 计算总数（包括所有子节点）
    const countNodes = (nodes: PermissionInfo[]): number => {
      return nodes.reduce((count, node) => {
        return count + 1 + (node.children ? countNodes(node.children) : 0);
      }, 0);
    };
    total.value = countNodes(allData);
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: { keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    keyword: '',
  };
  loadData();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<PermissionInfo>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: PermissionInfo) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadAllPermissions();
  loadData();
};

const handleDelete = async (row: PermissionInfo) => {
  if (!row.id) {
    ElMessage.warning('缺少权限ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该权限吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deletePermissionApi({ id: row.id });
    ElMessage.success('删除成功');
    loadAllPermissions();
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

onMounted(() => {
  loadAllPermissions();
  loadData();
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
      :show-side-query="false"
      :show-top-query="true"
      :show-pagination="false"
      :tree="true"
      :tree-config="{
        children: 'children',
        indent: 20,
        line: false,
        expandAll: false,
        accordion: false,
        trigger: 'default',
      }"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)"> 编辑 </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :all-permissions="allPermissions"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
