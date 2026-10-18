<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { UserInfo } from '#/api/user/user';

import { computed, ref } from 'vue';

import { TableSelectDialog } from '@zxplat/common-ui';

import { getUserListApi } from '#/api/user/user';

interface Props {
  modelValue?: null | number | number[] | string | string[];
  placeholder?: string;
  multiple?: boolean;
  dialogTitle?: string;
  dialogWidth?: string;
  inputClass?: string;
  /** 是否显示输入框 */
  showInput?: boolean;
  /** 是否显示状态筛选 */
  showStatusFilter?: boolean;
  /** 是否显示锁定状态筛选 */
  showLockedFilter?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择用户',
  multiple: false,
  dialogTitle: '选择用户',
  dialogWidth: '60%',
  inputClass: 'w-[220px]',
  showInput: true,
  showStatusFilter: true,
  showLockedFilter: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | number | number[] | string | string[]): void;
  (e: 'change', item: null | UserInfo | UserInfo[]): void;
}>();

const columns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'userName',
    label: '用户名',
    width: 120,
  },
  {
    prop: 'name',
    label: '姓名',
    width: 120,
  },
  {
    prop: 'email',
    label: '邮箱',
    width: 180,
  },
  {
    prop: 'phoneNumber',
    label: '手机号',
    width: 180,
  },
  {
    prop: 'isActive',
    label: '状态',
    align: 'center',
    formatter: (row: Record<string, any>) => {
      return row.isActive ? '启用' : '禁用';
    },
  },
];

const formItems = computed<FormItemConfig[]>(() => {
  const items: FormItemConfig[] = [
    {
      type: 'input',
      prop: 'keyword',
      label: '关键词',
      placeholder: '请输入用户名、姓名或邮箱',
      clearable: true,
    },
  ];

  if (props.showStatusFilter) {
    items.push({
      type: 'select',
      prop: 'isActive',
      label: '状态',
      placeholder: '请选择状态',
      clearable: true,
      options: [
        { label: '启用', value: 'true' },
        { label: '禁用', value: 'false' },
      ],
    });
  }

  if (props.showLockedFilter) {
    items.push({
      type: 'select',
      prop: 'isLockedOut',
      label: '锁定状态',
      placeholder: '请选择锁定状态',
      clearable: true,
      options: [
        { label: '已锁定', value: 'true' },
        { label: '未锁定', value: 'false' },
      ],
    });
  }

  return items;
});

async function requestUserList(params: Record<string, any>) {
  const response = await getUserListApi({
    pageNo: params.pageNo || 1,
    pageSize: params.pageSize || 20,
    keyword: params.keyword || '',
    isActive: params.isActive,
    isLockedOut: params.isLockedOut,
  });
  return {
    content: response.content || [],
    total: Number(response.totalCount || 0),
    totalElements: Number(response.totalCount || 0),
  };
}

const tableSelectDialogRef = ref<InstanceType<typeof TableSelectDialog> | null>(null);

const openDialog = () => {
  if (!tableSelectDialogRef.value) {
    console.warn('tableSelectDialogRef 未找到');
    return;
  }

  const component = tableSelectDialogRef.value as any;
  if (typeof component.openDialog === 'function') {
    component.openDialog();
    return;
  }

  if (component.dialogVisible !== undefined) {
    component.dialogVisible = true;
    return;
  }

  console.error('无法打开对话框：找不到 openDialog 方法或 dialogVisible 属性', {
    component,
    keys: Object.keys(component || {}),
  });
};

defineExpose({
  openDialog,
});
</script>

<template>
  <TableSelectDialog
    ref="tableSelectDialogRef"
    :model-value="modelValue"
    :placeholder="placeholder"
    :multiple="multiple"
    :columns="columns"
    :form-items="formItems"
    :request="requestUserList"
    display-field="name"
    value-field="id"
    :dialog-title="dialogTitle"
    :dialog-width="dialogWidth"
    :input-class="inputClass"
    :show-input="showInput"
    @update:model-value="emit('update:modelValue', $event)"
    @change="emit('change', $event as UserInfo | UserInfo[] | null)"
  />
</template>
