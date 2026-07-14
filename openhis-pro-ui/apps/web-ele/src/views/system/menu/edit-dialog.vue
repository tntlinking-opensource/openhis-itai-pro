<script lang="ts" setup>
import type { CreateMenuParams, Menu, UpdateMenuParams } from '#/api/core/system/menu.types';
import type { PermissionInfo } from '#/api/user/permission';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createMenuApi, getMenuApi, updateMenuApi } from '#/api/core/system/menu';
import { getPermissionListApi } from '#/api/user/permission';

interface Props {
  visible?: boolean;
  formData?: Partial<Menu>;
  allMenus?: Menu[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  allMenus: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<Menu>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

// 权限树数据
const allPermissions = ref<PermissionInfo[]>([]);

// 将权限数据转换为树形选择器选项格式
function convertPermissionsToOptions(permissions: PermissionInfo[]): Array<{
  children?: Array<{ label: string; value: string }>;
  label: string;
  value: string;
}> {
  return permissions.map((permission) => ({
    label: permission.displayName || permission.name,
    value: permission.name,
    children:
      permission.children && permission.children.length > 0
        ? convertPermissionsToOptions(permission.children)
        : undefined,
  }));
}

// 权限选项
const permissionOptions = computed(() => {
  return convertPermissionsToOptions(allPermissions.value);
});

// 加载权限树
const loadPermissions = async () => {
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

// 递归过滤树形数据，移除指定 ID 的节点及其子节点
function filterTreeOptions(items: Menu[], excludeId: number | string | undefined): Menu[] {
  if (!excludeId) {
    return items;
  }

  const excludeIdStr = String(excludeId);
  const result: Menu[] = [];

  for (const item of items) {
    // 如果当前节点是要排除的 ID，跳过
    if (String(item.id) === excludeIdStr) {
      continue;
    }

    // 递归处理子节点
    const filteredChildren =
      item.children && item.children.length > 0 ? filterTreeOptions(item.children, excludeId) : [];

    result.push({
      ...item,
      children: filteredChildren,
    });
  }

  return result;
}

// 将菜单数据转换为选项格式
function convertMenusToOptions(menus: Menu[]): Array<{
  children?: Array<{ label: string; value: number | string }>;
  label: string;
  value: number | string;
}> {
  return menus.map((menu) => ({
    label: menu.name,
    value: menu.id,
    children:
      menu.children && menu.children.length > 0 ? convertMenusToOptions(menu.children) : undefined,
  }));
}

// 获取父菜单选项（编辑模式下过滤掉当前菜单及其子菜单）
const parentMenuOptions = computed(() => {
  const menus = isEditMode.value
    ? filterTreeOptions(props.allMenus, props.formData?.id || originalData.value?.id)
    : props.allMenus;
  return convertMenusToOptions(menus);
});

// schema 是 computed，会自动响应 parentMenuOptions 的变化，不需要手动更新

function parseBoolString(value: unknown, defaultValue: boolean): boolean {
  if (value === true || value === 'true') {
    return true;
  }
  if (value === false || value === 'false') {
    return false;
  }
  return defaultValue;
}

/** 编辑模式局部更新：空字符串不传，避免误把 directory/path 等关键字段清空。 */
function buildEditUpdateRequest(
  mergedData: Partial<Menu>,
  originalData: Partial<Menu>,
  rawParentId: unknown,
): UpdateMenuParams['request'] {
  const hadParent =
    originalData.parentId !== undefined &&
    originalData.parentId !== null &&
    String(originalData.parentId) !== '';
  const clearParentInput = rawParentId === undefined || rawParentId === null || rawParentId === '';

  const request: UpdateMenuParams['request'] = {
    displayName: mergedData.displayName || mergedData.name || originalData.displayName || '',
    isEnabled: String(parseBoolString(mergedData.isEnabled, originalData.isEnabled !== false)),
    isHidden: String(parseBoolString(mergedData.isHidden, originalData.isHidden === true)),
    isExternal: String(parseBoolString(mergedData.isExternal, originalData.isExternal === true)),
    sortOrder: mergedData.sortOrder ?? originalData.sortOrder ?? 0,
  };

  if (mergedData.name?.trim()) {
    request.name = mergedData.name.trim();
  }
  if (mergedData.path?.trim()) {
    request.path = mergedData.path.trim();
  }
  if (mergedData.menuType?.trim()) {
    request.menuType = mergedData.menuType.trim();
  }
  if (mergedData.component !== undefined && mergedData.component !== null) {
    request.component = mergedData.component;
  }
  if (mergedData.icon !== undefined) {
    request.icon = mergedData.icon || '';
  }
  if (mergedData.remark !== undefined) {
    request.remark = mergedData.remark || '';
  }
  if (mergedData.externalUrl !== undefined) {
    request.externalUrl = mergedData.externalUrl || '';
  }
  if (mergedData.keepAlive !== undefined) {
    request.keepAlive = parseBoolString(mergedData.keepAlive, originalData.keepAlive === true);
  }
  if (clearParentInput && hadParent) {
    request.clearParent = true;
  } else if (!clearParentInput && rawParentId != null && rawParentId !== '') {
    request.parentId = String(rawParentId);
  }

  return request;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const rawParentId = formValues.parentId;
    const clearParent = rawParentId === undefined || rawParentId === null || rawParentId === '';
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };

    if (isEditMode.value && originalData.value.id) {
      const updateParams: UpdateMenuParams = {
        id: originalData.value.id,
        request: buildEditUpdateRequest(mergedData, originalData.value, rawParentId),
      };
      await updateMenuApi(updateParams);
      ElMessage.success('更新成功');
    } else {
      const createParams: CreateMenuParams = {
        externalUrl: mergedData.externalUrl || '',
        code: mergedData.code || '',
        isExternal: String(mergedData.isExternal ?? false),
        displayName: mergedData.displayName || mergedData.name || '',
        icon: mergedData.icon || '',
        remark: mergedData.remark || '',
        parentId: clearParent ? undefined : String(rawParentId),
        isHidden: String(mergedData.isHidden ?? false),
        keepAlive: mergedData.keepAlive,
        path: mergedData.path || '',
        component: mergedData.component || '',
        sortOrder: mergedData.sortOrder ?? 0,
        isEnabled: String(mergedData.isEnabled ?? true),
        name: mergedData.name || '',
        menuType: mergedData.menuType || '',
        permissionName: mergedData.permissionName || '',
      };
      await createMenuApi(createParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      (isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    modalApi.setState({ loading: true });
    try {
      const response = await getMenuApi(id);
      const data = (response as unknown as { data?: Menu }).data || (response as Menu);
      if (data) {
        originalData.value = { ...data };
        const formData = {
          ...data,
          isEnabled: String(data.isEnabled ?? true),
          isHidden: String(data.isHidden ?? false),
          isExternal: String(data.isExternal ?? false),
          keepAlive: String(data.keepAlive ?? false),
          parentId: data.parentId || undefined,
        };
        formApi.setValues(formData);
      } else {
        ElMessage.warning('未找到菜单信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取菜单详情失败:', error);
      ElMessage.error('获取菜单详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = { ...props.formData };
      const formData = {
        ...props.formData,
        isEnabled: String(props.formData.isEnabled ?? true),
        isHidden: String(props.formData.isHidden ?? false),
        isExternal: String(props.formData.isExternal ?? false),
        parentId: props.formData.parentId || undefined,
        keepAlive: String(props.formData.keepAlive ?? false),
      };
      formApi.setValues(formData);
    } else {
      originalData.value = {};
      await formApi.resetForm();
      await formApi.setValues({
        isEnabled: 'true',
        isHidden: 'false',
        isExternal: 'false',
        parentId: undefined,
        sortOrder: 0,
      });
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await loadPermissions();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '220px' },
    },
  },
  layout: 'horizontal' as const,
  schema: computed(() => {
    const schema: any[] = [];

    if (!isEditMode.value) {
      schema.push({
        component: 'Input',
        fieldName: 'code',
        label: '菜单编码',
        required: true,
      });
    }

    schema.push(
      {
        component: 'Input',
        fieldName: 'name',
        label: '菜单名称',
        required: true,
      },
      {
        component: 'Input',
        fieldName: 'displayName',
        label: '显示名称',
        required: true,
      },
      {
        component: 'Input',
        fieldName: 'path',
        label: '路由路径',
        required: true,
      },
      {
        component: 'Input',
        fieldName: 'component',
        label: '组件路径',
      },
      {
        component: 'Input',
        fieldName: 'icon',
        label: '图标',
      },
      {
        component: 'TreeSelect',
        fieldName: 'permissionName',
        label: '权限',
        componentProps: {
          data: permissionOptions.value,
          checkStrictly: true,
          filterable: true,
          clearable: true,
          props: {
            value: 'value',
            label: 'label',
            children: 'children',
          },
          nodeKey: 'value',
        },
      },
      {
        component: 'Select',
        fieldName: 'menuType',
        label: '菜单类型',
        componentProps: {
          options: [
            { label: '页面菜单 (menu)', value: 'menu' },
            { label: '目录 (directory)', value: 'directory' },
          ],
        },
      },
      {
        component: 'TreeSelect',
        fieldName: 'parentId',
        label: '父菜单',
        componentProps: {
          data: parentMenuOptions.value,
          checkStrictly: true,
          filterable: true,
          clearable: true,
          props: {
            value: 'value',
            label: 'label',
            children: 'children',
          },
          nodeKey: 'value',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'sortOrder',
        label: '排序',
        componentProps: {
          min: 0,
        },
      },
      {
        component: 'Select',
        fieldName: 'isEnabled',
        label: '是否启用',
        componentProps: {
          options: [
            { label: '启用', value: 'true' },
            { label: '停用', value: 'false' },
          ],
        },
      },
      {
        component: 'Select',
        fieldName: 'isHidden',
        label: '是否隐藏',
        componentProps: {
          options: [
            { label: '是', value: 'true' },
            { label: '否', value: 'false' },
          ],
        },
      },
      {
        component: 'Select',
        fieldName: 'keepAlive',
        label: '是否缓存',
        componentProps: {
          options: [
            { label: '是', value: 'true' },
            { label: '否', value: 'false' },
          ],
        },
      },
      {
        component: 'Select',
        fieldName: 'isExternal',
        label: '是否外部链接',
        componentProps: {
          options: [
            { label: '是', value: 'true' },
            { label: '否', value: 'false' },
          ],
        },
      },
      {
        component: 'Input',
        fieldName: 'externalUrl',
        label: '外部链接',
      },
      {
        component: 'InputArea',
        fieldName: 'remark',
        label: '备注',
        componentProps: {
          rows: 3,
        },
      },
    );

    return schema;
  }),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);

// 监听父菜单选项变化，确保在数据加载完成后正确回显
watch(
  () => parentMenuOptions.value,
  async (newOptions) => {
    if (newOptions && newOptions.length > 0 && originalData.value && originalData.value.parentId) {
      await nextTick();
      const parentId = String(originalData.value.parentId);
      formApi.setFieldValue('parentId', parentId);
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑菜单' : '新增菜单'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
