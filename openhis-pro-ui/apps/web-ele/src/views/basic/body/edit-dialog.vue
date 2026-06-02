<script lang="ts" setup>
import type { CreateBodyParams, UpdateBodyParams } from '#/api/basic/body';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createBodyApi, getBodyTreeApi, updateBodyApi } from '#/api/basic/body';

interface BodyListResponse {
  id?: string;
  name?: string;
  busNo?: string;
  parentId?: string;
  description?: string;
  [key: string]: unknown;
}

interface BodyTreeOption {
  value: string;
  label: string;
  children?: BodyTreeOption[];
}

interface Props {
  visible?: boolean;
  formData?: Partial<BodyListResponse>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<BodyListResponse>>({});
const bodyTreeOptions = ref<BodyTreeOption[]>([]);

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

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
    const parentId = mergedData.parentId || undefined;

    if (isEditMode.value) {
      // 编辑模式
      if (!originalData.value.id) {
        ElMessage.error('缺少身体部位ID');
        return;
      }
      const updateParams: UpdateBodyParams = {
        id: originalData.value.id,
        name: mergedData.name || '',
        parentId,
      };
      await updateBodyApi(updateParams);
      ElMessage.success('更新成功');
    } else {
      // 新增模式
      const createParams: CreateBodyParams = {
        name: mergedData.name || '',
        parentId,
      };
      await createBodyApi(createParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    const errorMessage =
      (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
        ?.msg ||
      (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
        ?.message ||
      (error as { message?: string })?.message ||
      (error as { msg?: string })?.msg ||
      (isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  if (props.formData && Object.keys(props.formData).length > 0) {
    originalData.value = { ...props.formData };
    formApi.setValues(props.formData);
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      name: '',
      parentId: undefined,
    });
  }
}

const filterTreeOptions = (items: BodyTreeOption[], excludeId?: string): BodyTreeOption[] => {
  if (!excludeId) {
    return items;
  }
  return items
    .filter((item) => item.value !== excludeId)
    .map((item) => ({
      ...item,
      children: item.children ? filterTreeOptions(item.children, excludeId) : undefined,
    }));
};

async function loadBodyTreeOptions() {
  const response = await getBodyTreeApi();
  const options = Array.isArray(response)
    ? response
    : Array.isArray((response as { data?: unknown[] })?.data)
      ? (response as { data: unknown[] }).data
      : [];
  const currentId = (props.formData?.id || originalData.value.id) as string | undefined;
  bodyTreeOptions.value = filterTreeOptions(options as BodyTreeOption[], currentId);
  const parentField = formConfig.schema.find((item) => item.fieldName === 'parentId') as
    | { componentProps?: { data?: BodyTreeOption[] } }
    | undefined;
  if (parentField?.componentProps) {
    parentField.componentProps.data = bodyTreeOptions.value;
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await loadBodyTreeOptions();
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
      label: '业务编号',
      componentProps: {
        placeholder: '系统自动生成',
        disabled: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      required: true,
      componentProps: {
        placeholder: '请输入名称',
      },
    },
    {
      component: 'TreeSelect',
      fieldName: 'parentId',
      label: '上级部位',
      componentProps: {
        data: bodyTreeOptions.value,
        filterable: true,
        clearable: true,
        checkStrictly: true,
        props: {
          value: 'value',
          label: 'label',
          children: 'children',
        },
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
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑身体部位' : '新增身体部位'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
