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

defineOptions({ name: 'DepartmentEditDialog' });

const fullWidthStyle = { width: '100%' };
const sectionTitleClass = 'col-span-2 !mb-0 !pb-0 dept-form-dialog__section-title';

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

// 下拉绑定值统一转 string，与 ElSelectV2 option.value 严格匹配。
function toSelectString(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  return String(value).trim();
}

function toDepartmentFormValues(
  value: Partial<Department>,
): Partial<Department> & { parentId?: string } {
  return {
    ...value,
    parentId: resolveParentId(value),
    typeEnum: toSelectString(value.typeEnum),
    classEnum: toSelectString(value.classEnum),
    defDoctorId: toSelectString(value.defDoctorId),
  };
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

// 医院类型下不展示的科室专属字段。
const HOSPITAL_HIDDEN_FIELDS = ['parentId', 'classEnum', 'defDoctorId', 'caty'] as const;
// 仅医院类型展示的医保字段。
const HOSPITAL_ONLY_FIELDS = ['medinsType', 'medinsLv'] as const;

function setSchemaFieldHide(fieldName: string, hide: boolean) {
  const field = formConfig.schema.find((item) => item.fieldName === fieldName) as
    | undefined
    | { hide?: boolean };
  if (field) {
    field.hide = hide;
  }
}

async function updateHospitalFieldVisibility(typeEnum: unknown) {
  const isHospital = isHospitalType(typeEnum);

  for (const fieldName of HOSPITAL_HIDDEN_FIELDS) {
    setSchemaFieldHide(fieldName, isHospital);
  }
  for (const fieldName of HOSPITAL_ONLY_FIELDS) {
    setSchemaFieldHide(fieldName, !isHospital);
  }

  if (isHospital) {
    // 医院为根节点且无科室属性，切换时清空不适用字段。
    for (const fieldName of HOSPITAL_HIDDEN_FIELDS) {
      formApi.setFieldValue(fieldName, undefined);
    }
    return;
  }

  // 切换为非医院时清空医院专属字段，避免隐藏字段残留旧值被提交。
  for (const fieldName of HOSPITAL_ONLY_FIELDS) {
    formApi.setFieldValue(fieldName, undefined);
  }
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = (await formApi.getValues()) as DepartmentParams;
    const submitData: DepartmentParams = {
      ...formValues,
    };
    // 医院须为根节点且无科室属性，提交前剔除不适用字段。
    if (isHospitalType(submitData.typeEnum)) {
      submitData.parentId = undefined;
      submitData.classEnum = undefined;
      submitData.defDoctorId = undefined;
      submitData.caty = undefined;
    }

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
        const formData = toDepartmentFormValues(data);
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
      const formData = toDepartmentFormValues(props.formData);
      formApi.setValues(formData);
      updateHospitalFieldVisibility(formData.typeEnum);
    } else {
      originalData.value = {};
      await formApi.resetForm();
      formApi.setFieldValue('displayOrder', 0);
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
    labelWidth: 96,
    labelClass: 'justify-start text-left',
    componentProps: {
      style: fullWidthStyle,
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'dept-form-dialog__grid grid grid-cols-2 gap-x-6',
  schema: [
    {
      component: 'SectionTitle',
      fieldName: '__sectionBasic',
      label: '基本信息',
      hideLabel: true,
      formItemClass: sectionTitleClass,
      componentProps: {
        title: '基本信息',
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Select',
      fieldName: 'typeEnum',
      label: '类型',
      componentProps: {
        options: props.departmentTypeOptions,
        filterable: true,
        placeholder: '请选择',
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
        placeholder: '请选择',
      },
    },
    {
      component: 'TreeSelect',
      fieldName: 'parentId',
      label: '上级组织',
      formItemClass: 'col-span-2',
      componentProps: {
        data: props.departmentOptions,
        checkStrictly: true,
        filterable: true,
        clearable: true,
        placeholder: '请选择',
        props: {
          value: 'value',
          label: 'label',
          children: 'children',
        },
        nodeKey: 'value',
        style: fullWidthStyle,
      },
    },
    {
      component: 'SectionTitle',
      fieldName: '__sectionYb',
      label: '医保信息',
      hideLabel: true,
      formItemClass: sectionTitleClass,
      componentProps: {
        title: '医保信息',
      },
    },
    {
      component: 'Input',
      fieldName: 'ybNo',
      label: '医保编码',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'ybName',
      label: '医保名称',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'medinsId',
      label: '机构标识',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'medinsAdmdvs',
      label: '医保区划',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'medinsType',
      label: '机构类型',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'medinsLv',
      label: '机构等级',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'SectionTitle',
      fieldName: '__sectionOther',
      label: '其他',
      hideLabel: true,
      formItemClass: sectionTitleClass,
      componentProps: {
        title: '其他',
      },
    },
    {
      component: 'Input',
      fieldName: 'pyStr',
      label: '拼音',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Input',
      fieldName: 'wbStr',
      label: '五笔',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'Select',
      fieldName: 'defDoctorId',
      label: '默认医师',
      componentProps: {
        options: props.practitionerOptions,
        filterable: true,
        clearable: true,
        placeholder: '请选择',
      },
    },
    {
      component: 'Input',
      fieldName: 'caty',
      label: '科别',
      componentProps: {
        placeholder: '请输入',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'displayOrder',
      label: '显示顺序',
      defaultValue: 0,
      componentProps: {
        min: 0,
        controlsPosition: 'right',
        style: fullWidthStyle,
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

// init 下拉就绪后重新写入枚举/医师字段，避免 options 为空时 setValues 无法匹配 label。
watch(
  () =>
    [
      props.departmentTypeOptions.length,
      props.departmentClassOptions.length,
      props.practitionerOptions.length,
    ] as const,
  async () => {
    if (!originalData.value || Object.keys(originalData.value).length === 0) {
      return;
    }
    if (
      props.departmentTypeOptions.length === 0 &&
      props.departmentClassOptions.length === 0 &&
      props.practitionerOptions.length === 0
    ) {
      return;
    }
    await nextTick();
    const { typeEnum, classEnum, defDoctorId } = toDepartmentFormValues(originalData.value);
    if (typeEnum !== undefined) {
      formApi.setFieldValue('typeEnum', typeEnum);
    }
    if (classEnum !== undefined) {
      formApi.setFieldValue('classEnum', classEnum);
    }
    if (defDoctorId !== undefined) {
      formApi.setFieldValue('defDoctorId', defDoctorId);
    }
  },
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
    class="dept-form-dialog w-[820px] max-w-[96vw]"
    :title="isEditMode ? '编辑科室' : '新增科室'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.dept-form-dialog :deep(.dept-form-dialog__grid) {
  align-items: start;
}

.dept-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}

.dept-form-dialog :deep(.dept-form-dialog__section-title) {
  margin-top: 2px;
  padding-bottom: 0 !important;
}

.dept-form-dialog :deep(.dept-form-dialog__section-title .el-form-item__content) {
  min-height: 0;
}
</style>
