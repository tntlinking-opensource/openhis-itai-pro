<script lang="ts" setup>
import type {
  CreateSpecimenDefinitionParams,
  SpecimenDefinitionItem,
  UpdateSpecimenDefinitionParams,
} from '#/api/basic/specimen-definition';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import {
  createSpecimenDefinitionApi,
  initSpecimenDefinitionApi,
  updateSpecimenDefinitionApi,
} from '#/api/basic/specimen-definition';

defineOptions({ name: 'BasicSpecimenDefinitionEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<SpecimenDefinitionItem>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<SpecimenDefinitionItem>>({});
const typeEnumOptions = ref<ApiOptionItem[]>([]);

const isEditMode = computed(() => !!props.formData?.id);

const dialogTitle = computed(() => (isEditMode.value ? '编辑样本' : '新增样本'));

const fullWidthStyle = { width: '100%' };

/** 加载标本种类下拉选项 */
async function loadInitOptions() {
  try {
    const res = await initSpecimenDefinitionApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (res as { data?: { typeEnumOptions?: ApiOptionItem[] } }).data
        : (res as { typeEnumOptions?: ApiOptionItem[] });
    typeEnumOptions.value = data?.typeEnumOptions ?? [];
  } catch {
    typeEnumOptions.value = [];
  }
}

/** 提交新增或维护样本定义 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as Record<string, unknown>;
    const merged = {
      ...originalData.value,
      ...values,
    };

    const payload: CreateSpecimenDefinitionParams = {
      name: String(merged.name ?? '').trim(),
      typeEnum: String(merged.typeEnum ?? '').trim(),
    };

    if (isEditMode.value) {
      const id = props.formData?.id;
      if (!id) {
        ElMessage.warning('缺少样本定义ID');
        return;
      }
      const updatePayload: UpdateSpecimenDefinitionParams = {
        ...payload,
        id: String(id),
      };
      await updateSpecimenDefinitionApi(updatePayload);
      ElMessage.success('更新成功');
    } else {
      await createSpecimenDefinitionApi(payload);
      ElMessage.success('创建成功');
    }

    emit('success');
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败' : '创建失败', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

/** 打开弹窗时回填表单 */
async function loadFormData() {
  const { formData } = props;
  if (formData && Object.keys(formData).length > 0) {
    originalData.value = { ...formData };
    await formApi.setValues({
      name: formData.name ?? '',
      typeEnum: formData.typeEnum ?? '',
    });
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      name: '',
      typeEnum: '',
    });
  }
}

const formConfig = reactive({
  commonConfig: {
    labelWidth: 96,
    labelClass: 'justify-start text-left',
    componentProps: {
      style: fullWidthStyle,
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'specimen-form-dialog__grid grid grid-cols-1',
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'name',
      label: '样本名称',
      rules: z.string().min(1, { message: '请填写样本名称' }),
      componentProps: {
        placeholder: '如 全血、尿液',
      },
    },
    {
      component: 'Select',
      fieldName: 'typeEnum',
      label: '标本种类',
      rules: z.string().min(1, { message: '请选择标本种类' }),
      componentProps: {
        options: typeEnumOptions.value,
        placeholder: '请选择标本种类',
      },
    },
  ]),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await nextTick();
    await loadInitOptions();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

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
    class="specimen-form-dialog w-[520px] max-w-[96vw]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.specimen-form-dialog :deep(.specimen-form-dialog__grid) {
  align-items: start;
}

.specimen-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
