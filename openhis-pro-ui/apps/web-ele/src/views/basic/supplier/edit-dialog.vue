<script lang="ts" setup>
import type { Supplier } from '#/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createSupplierApi, getSupplierApi, updateSupplierApi } from '#/api';
import { normalizeSupplierTypeValue, SupplierTypeCode } from '#/constants/domain/supplier-type';
import { useAuthStore } from '#/store/auth';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'BasicSupplierEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<Supplier>;
  supplierTypeOptions?: Array<{ label: string; value: number }>;
  organizationOptions?: Array<{ label: string; value: string }>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  supplierTypeOptions: () => [],
  organizationOptions: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const authStore = useAuthStore();
const originalData = ref<Record<string, unknown>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

/**
 * 解析新增默认类型：优先取选项中与 SupplierType.SUPPLIER code 匹配的 value。
 */
function resolveDefaultSupplierType(): number | string {
  const matched = props.supplierTypeOptions.find(
    (item) => normalizeSupplierTypeValue(item.value) === SupplierTypeCode.SUPPLIER,
  );
  if (matched) {
    return matched.value;
  }
  return Number(SupplierTypeCode.SUPPLIER);
}

/**
 * 解析默认部门：仅取登录选院（医院），禁止回落科室 organizationId。
 */
function resolveDefaultOrgId(): string | undefined {
  const hospitalId = preserveApiIdForRequest(authStore.getSelectedHospital());
  if (!hospitalId || hospitalId === '0') {
    return undefined;
  }
  return hospitalId;
}

/**
 * 将详情/表单中的 orgId 规范为 TreeSelect 可绑定的 string；0/空视为无效。
 */
function normalizeOrgIdForForm(raw: unknown): string | undefined {
  const orgId = preserveApiIdForRequest(raw);
  if (!orgId || orgId === '0') {
    return undefined;
  }
  return orgId;
}

/** 提交新增/编辑供应商 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };
    // 仅提交有值字段，避免空串覆盖后端默认
    const submitData: Record<string, unknown> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') {
        submitData[key] = value;
      }
    });
    // 机构主键按 string 提交；未选或无效时回落登录选院（医院），禁止科室
    const resolvedOrgId = normalizeOrgIdForForm(submitData.orgId) ?? resolveDefaultOrgId();
    if (resolvedOrgId) {
      submitData.orgId = resolvedOrgId;
    } else {
      delete submitData.orgId;
    }

    if (isEditMode.value) {
      await updateSupplierApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createSupplierApi(createData);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

/** 打开弹窗时按新增/编辑装载表单；新增默认类型为供应商、部门为登录医院 */
async function loadFormData() {
  const id = props.formData?.id;
  if (id !== undefined && id !== null && String(id).trim() !== '') {
    modalApi.setState({ loading: true });
    try {
      const response = await getSupplierApi(String(id));
      const data = response;
      if (data) {
        originalData.value = { ...data };
        const formData = {
          ...data,
          orgId: normalizeOrgIdForForm(data.orgId) ?? resolveDefaultOrgId(),
        };
        await formApi.setValues(formData);
      } else {
        ElMessage.warning('未找到供应商信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取供应商详情失败:', error);
      ElMessage.error('获取供应商详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else if (props.formData && Object.keys(props.formData).length > 0) {
    originalData.value = { ...props.formData };
    const formData = {
      ...props.formData,
      orgId: normalizeOrgIdForForm(props.formData.orgId) ?? resolveDefaultOrgId(),
      typeEnum: props.formData.typeEnum ?? resolveDefaultSupplierType(),
    };
    formApi.setValues(formData);
  } else {
    originalData.value = {};
    await formApi.resetForm();
    // 新增：编号/拼音/五笔由后端生成；类型默认供应商；部门默认登录选院（医院）
    formApi.setValues({
      typeEnum: resolveDefaultSupplierType(),
      orgId: resolveDefaultOrgId(),
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    updateFormSchema();
    await nextTick();
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
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: '供应商名称',
      rules: 'required',
    },
    {
      component: 'TreeSelect',
      fieldName: 'orgId',
      label: '部门',
      componentProps: {
        data: props.organizationOptions,
        checkStrictly: true,
        filterable: true,
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
      fieldName: 'typeEnum',
      label: '类型',
      componentProps: {
        options: props.supplierTypeOptions,
      },
    },
    {
      component: 'Input',
      fieldName: 'address',
      label: '地址',
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: '电话',
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: '邮箱',
    },
  ],
  showDefaultActions: false,
});

interface SchemaItemWithProps {
  fieldName?: string;
  componentProps?: Record<string, unknown>;
}

const [Form, formApi] = useVbenForm(formConfig);

/** 同步类型/机构下拉到表单 schema（选项异步加载后刷新） */
function updateFormSchema() {
  const typeField = formConfig.schema.find((item) => item.fieldName === 'typeEnum') as
    | SchemaItemWithProps
    | undefined;
  if (typeField?.componentProps) {
    typeField.componentProps.options = props.supplierTypeOptions;
  }

  const orgField = formConfig.schema.find((item) => item.fieldName === 'orgId') as
    | SchemaItemWithProps
    | undefined;
  if (orgField?.componentProps) {
    orgField.componentProps.data = props.organizationOptions;
  }
}

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

watch(
  () => props.organizationOptions,
  async (newOptions) => {
    if (!newOptions || newOptions.length === 0) {
      return;
    }
    await nextTick();
    // 选项异步到位后回填：编辑用原值，新增用登录选院；忽略历史占位 0
    const formValues = await formApi.getValues();
    const orgId =
      normalizeOrgIdForForm(formValues.orgId) ??
      normalizeOrgIdForForm(originalData.value?.orgId) ??
      resolveDefaultOrgId();
    if (orgId) {
      formApi.setFieldValue('orgId', orgId);
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑供应商' : '新增供应商'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
