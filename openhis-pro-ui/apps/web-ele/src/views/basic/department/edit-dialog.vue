<script lang="ts" setup>
import type {
  Department,
  DepartmentClassOption,
  DepartmentOption,
  DepartmentParams,
  PractitionerOption,
  DepartmentTypeOption,
} from '#/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createDepartmentApi, editDepartmentApi, getDepartmentApi } from '#/api';

interface Props {
  visible?: boolean;
  formData?: Partial<Department>;
  departmentClassOptions?: DepartmentClassOption[];
  departmentTypeOptions?: DepartmentTypeOption[];
  departmentOptions?: DepartmentOption[];
  practitionerOptions?: PractitionerOption[];
}

interface FormApiWithValidateField {
  validateField?: (fields: string[]) => Promise<void>;
}

type DepartmentApiResponse = Department | { data?: Department };

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  departmentClassOptions: () => [],
  departmentTypeOptions: () => [],
  departmentOptions: () => [],
  practitionerOptions: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<Department>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

// 统一解析表单与详情中的上级组织 ID，兼容 parentDepartment/parentOrganization/parentId 三种来源。
function resolveParentId(value: Partial<Department> | undefined): string | undefined {
  if (!value) {
    return undefined;
  }
  if (value.parentDepartment?.id) {
    return String(value.parentDepartment.id);
  }
  if (value.parentOrganization?.id) {
    return String(value.parentOrganization.id);
  }
  if (value.parentId) {
    return String(value.parentId);
  }
  return undefined;
}

function hasParentRef(value: Partial<Department> | undefined): boolean {
  return !!resolveParentId(value);
}

function unwrapDepartmentResponse(response: DepartmentApiResponse): Department | undefined {
  if ('data' in response) {
    return response.data;
  }
  return response;
}

// 递归过滤树形数据，移除指定 ID 的节点及其子节点
function filterTreeOptions(
  items: DepartmentOption[],
  excludeId: number | string | undefined,
): DepartmentOption[] {
  if (!excludeId) {
    return items;
  }

  const excludeIdStr = String(excludeId);
  const result: DepartmentOption[] = [];

  for (const item of items) {
    // 如果当前节点是要排除的 ID，跳过
    if (item.value === excludeIdStr) {
      continue;
    }

    // 递归处理子节点
    const filteredChildren =
      item.children && item.children.length > 0
        ? filterTreeOptions(item.children, excludeId)
        : item.children === null
          ? null
          : undefined;

    result.push({
      ...item,
      children: filteredChildren,
    });
  }

  return result;
}

function updateFormSchema() {
  const typeField = formConfig.schema.find((item) => item.fieldName === 'typeEnum') as
    | undefined
    | { componentProps?: { options?: DepartmentTypeOption[] } };
  if (typeField?.componentProps) {
    typeField.componentProps.options = props.departmentTypeOptions;
  }

  const classField = formConfig.schema.find((item) => item.fieldName === 'classEnum') as
    | undefined
    | { componentProps?: { options?: DepartmentClassOption[] } };
  if (classField?.componentProps) {
    classField.componentProps.options = props.departmentClassOptions;
  }

  const parentIdField = formConfig.schema.find((item) => item.fieldName === 'parentId') as
    | undefined
    | { componentProps?: { data?: DepartmentOption[] } };
  if (parentIdField?.componentProps) {
    // 编辑模式下，过滤掉当前组织及其子节点
    const filteredOptions = isEditMode.value
      ? filterTreeOptions(props.departmentOptions, props.formData?.id || originalData.value?.id)
      : props.departmentOptions;
    parentIdField.componentProps.data = filteredOptions;
  }

  const doctorField = formConfig.schema.find((item) => item.fieldName === 'defDoctorId') as
    | undefined
    | { componentProps?: { options?: PractitionerOption[] } };
  if (doctorField?.componentProps) {
    doctorField.componentProps.options = props.practitionerOptions;
  }
}

function isHospitalType(typeEnum: unknown): boolean {
  if (typeEnum === null || typeEnum === undefined || typeEnum === '') {
    return false;
  }
  const normalizedType = String(typeEnum).trim().toUpperCase();
  return normalizedType === '1' || normalizedType === 'HOSPITAL';
}

function toComparableParentId(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return String(value).trim();
}

async function updateHospitalFieldVisibility(typeEnum: unknown) {
  const isHospital = isHospitalType(typeEnum);
  const medinsTypeField = formConfig.schema.find((item) => item.fieldName === 'medinsType') as
    | undefined
    | { hide?: boolean };
  if (medinsTypeField) {
    medinsTypeField.hide = !isHospital;
  }
  const medinsLvField = formConfig.schema.find((item) => item.fieldName === 'medinsLv') as
    | undefined
    | { hide?: boolean };
  if (medinsLvField) {
    medinsLvField.hide = !isHospital;
  }
  // 切换为非医院时主动清空医院专属字段，避免隐藏字段残留旧值被提交。
  if (!isHospital) {
    formApi.setFieldValue('medinsType', undefined);
    formApi.setFieldValue('medinsLv', undefined);
    return;
  }
  // 切回医院时立即触发一次校验，给出必填红框反馈。
  const formApiWithValidate = formApi as FormApiWithValidateField;
  if (formApiWithValidate.validateField) {
    await formApiWithValidate.validateField(['medinsType', 'medinsLv']);
  }
}

function validateHospitalFields(formValues: DepartmentParams): boolean {
  if (!isHospitalType(formValues.typeEnum)) {
    return true;
  }
  if (!String(formValues.ybNo ?? '').trim()) {
    ElMessage.warning('机构类型为医院时，医保机构登记不能为空');
    return false;
  }
  if (!String(formValues.medinsType ?? '').trim()) {
    ElMessage.warning('机构类型为医院时，医保机构类型不能为空');
    return false;
  }
  if (!String(formValues.medinsLv ?? '').trim()) {
    ElMessage.warning('机构类型为医院时，医保机构等级不能为空');
    return false;
  }
  return true;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = (await formApi.getValues()) as DepartmentParams;
    if (!validateHospitalFields(formValues)) {
      return;
    }
    const submitData: DepartmentParams = {
      ...formValues,
    };

    if (isEditMode.value) {
      submitData.id = props.formData?.id ?? originalData.value?.id;
      const originalParentId = toComparableParentId(
        originalData.value.parentDepartment?.id ??
          originalData.value.parentOrganization?.id ??
          originalData.value.parentId,
      );
      const currentParentId = toComparableParentId(formValues.parentId);
      const parentIdChanged = originalParentId !== currentParentId;
      submitData.parentIdChanged = parentIdChanged;
      if (parentIdChanged && originalParentId && !currentParentId) {
        await ElMessageBox.confirm(
          '你已清空上级组织，保存后该科室将被调整为根节点。是否继续？',
          '调整为根节点确认',
          {
            type: 'warning',
            confirmButtonText: '继续保存',
            cancelButtonText: '取消',
          },
        );
      }
      await editDepartmentApi(submitData as DepartmentParams);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createDepartmentApi(createData as DepartmentParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    const errorMessage =
      error?.response?.data?.message || error?.message || error?.msg || '操作失败，请稍后重试';
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
      const response = await getDepartmentApi(id);
      const data = unwrapDepartmentResponse(response as DepartmentApiResponse);
      if (data) {
        originalData.value = { ...data };
        const parentId = resolveParentId(data);
        const formData = {
          ...data,
          parentId,
        };
        formApi.setValues(formData);
        updateHospitalFieldVisibility(formData.typeEnum);
      } else {
        ElMessage.warning('未找到组织信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取组织详情失败:', error);
      ElMessage.error('获取组织详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = { ...props.formData };
      const parentId = resolveParentId(props.formData);
      const formData = {
        ...props.formData,
        parentId,
      };
      formApi.setValues(formData);
      updateHospitalFieldVisibility(formData.typeEnum);
    } else {
      originalData.value = {};
      await formApi.resetForm();
      updateHospitalFieldVisibility(undefined);
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    updateFormSchema();
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
      fieldName: 'busNo',
      label: '科室编号',
      componentProps: {
        placeholder: '可手填，不填自动生成',
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      rules: 'required',
    },
    {
      component: 'Select',
      fieldName: 'typeEnum',
      label: '类型',
      componentProps: {
        options: props.departmentTypeOptions,
        filterable: true,
        onChange: async (value: unknown) => {
          await updateHospitalFieldVisibility(value);
        },
      },
    },
    {
      component: 'Select',
      fieldName: 'classEnum',
      label: '类别',
      componentProps: {
        options: props.departmentClassOptions,
        filterable: true,
      },
    },
    {
      component: 'TreeSelect',
      fieldName: 'parentId',
      label: '上级组织',
      componentProps: {
        data: props.departmentOptions,
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
      component: 'Input',
      fieldName: 'ybNo',
      label: '医保编码',
    },
    {
      component: 'Input',
      fieldName: 'ybName',
      label: '医保名称',
    },
    {
      component: 'Input',
      fieldName: 'medinsId',
      label: '医保机构标识',
    },
    {
      component: 'Input',
      fieldName: 'medinsAdmdvs',
      label: '医保区划',
    },
    {
      component: 'Input',
      fieldName: 'medinsType',
      label: '医保机构类型',
    },
    {
      component: 'Input',
      fieldName: 'medinsLv',
      label: '医保机构等级',
    },
    {
      component: 'Input',
      fieldName: 'pyStr',
      label: '拼音',
    },
    {
      component: 'Input',
      fieldName: 'wbStr',
      label: '五笔',
    },
    {
      component: 'Select',
      fieldName: 'defDoctorId',
      label: '默认医师',
      componentProps: {
        options: props.practitionerOptions,
        filterable: true,
        clearable: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'caty',
      label: '科别',
    },
    {
      component: 'InputNumber',
      fieldName: 'displayOrder',
      label: '显示顺序',
      componentProps: {
        min: 0,
      },
    },
  ],
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

// 监听 departmentOptions 变化，确保在数据加载完成后正确回显 parentId
watch(
  () => props.departmentOptions,
  async (newOptions) => {
    if (newOptions && newOptions.length > 0 && hasParentRef(originalData.value)) {
      await nextTick();
      const parentId = resolveParentId(originalData.value);
      if (parentId) {
        formApi.setFieldValue('parentId', parentId);
      }
    }
  },
  { immediate: true },
);

watch(
  () => props.practitionerOptions,
  () => {
    updateFormSchema();
  },
  { immediate: true },
);

watch(
  () => props.formData?.typeEnum,
  (typeEnum) => {
    updateHospitalFieldVisibility(typeEnum);
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑科室' : '新增科室'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
