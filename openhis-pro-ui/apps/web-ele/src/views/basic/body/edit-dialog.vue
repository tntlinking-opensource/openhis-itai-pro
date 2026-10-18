<script lang="ts" setup>
import type { BodyItem, BodyTreeOption } from '#/api/basic/body';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { createBodyApi, getBodyApi, getBodyTreeApi, updateBodyApi } from '#/api/basic/body';

defineOptions({ name: 'BasicBodyEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<BodyItem>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<BodyItem>>({});
const bodyTreeOptions = ref<BodyTreeOption[]>([]);

const isEditMode = computed(() => !!props.formData?.id);

const dialogTitle = computed(() => (isEditMode.value ? '编辑身体部位' : '新增身体部位'));

const fullWidthStyle = { width: '100%' };

/** 统一将 parentId 转为 TreeSelect 所需的 string */
function normalizeParentId(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  return String(value);
}

/** 编辑时排除自身及子孙，避免选自己为上级 */
function filterTreeOptions(items: BodyTreeOption[], excludeId?: string): BodyTreeOption[] {
  if (!excludeId) {
    return items;
  }
  return items
    .filter((item) => item.value !== excludeId)
    .map((item) => ({
      ...item,
      children: item.children ? filterTreeOptions(item.children, excludeId) : undefined,
    }));
}

/** 加载上级部位树 */
async function loadBodyTreeOptions() {
  try {
    const options = await getBodyTreeApi();
    const excludeId = normalizeParentId(props.formData?.id ?? originalData.value.id);
    bodyTreeOptions.value = filterTreeOptions(options, excludeId);
  } catch (error) {
    console.error('加载上级部位树失败:', error);
    bodyTreeOptions.value = [];
  }
}

/** 提交新增或编辑 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as Record<string, unknown>;
    const name = String(values.name ?? '').trim();
    const parentId = normalizeParentId(values.parentId);

    if (isEditMode.value) {
      const id = normalizeParentId(originalData.value.id ?? props.formData?.id);
      if (!id) {
        ElMessage.warning('缺少身体部位ID');
        return;
      }
      await updateBodyApi({ id, name, parentId });
      ElMessage.success('更新成功');
    } else {
      await createBodyApi({ name, parentId });
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

/** 打开弹窗时拉详情并回填表单 */
async function loadFormData() {
  const editId = normalizeParentId(props.formData?.id);
  if (editId) {
    modalApi.setState({ loading: true });
    try {
      const detail = await getBodyApi(editId);
      if (!detail) {
        ElMessage.warning('未找到身体部位信息');
        modalApi.close();
        return;
      }
      originalData.value = { ...detail };
      await formApi.setValues({
        busNo: detail.busNo ?? '',
        name: detail.name ?? '',
        parentId: normalizeParentId(detail.parentId),
      });
    } catch (error) {
      console.error('获取身体部位详情失败:', error);
      ElMessage.error('获取身体部位详情失败，请稍后重试');
      modalApi.close();
    } finally {
      modalApi.setState({ loading: false });
    }
    return;
  }

  originalData.value = {};
  await formApi.resetForm();
  await formApi.setValues({
    name: '',
    parentId: undefined,
  });
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
  wrapperClass: 'body-form-dialog__grid grid grid-cols-1',
  schema: computed(() => [
    ...(isEditMode.value
      ? [
          {
            component: 'Input' as const,
            fieldName: 'busNo',
            label: '业务编号',
            componentProps: {
              disabled: true,
            },
          },
        ]
      : []),
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      rules: z.string().min(1, { message: '请填写部位名称' }),
      componentProps: {
        placeholder: '请输入部位名称',
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
        placeholder: '不选则为顶级部位',
        props: {
          value: 'value',
          label: 'label',
          children: 'children',
        },
        nodeKey: 'value',
        style: fullWidthStyle,
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
    await loadBodyTreeOptions();
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
    class="body-form-dialog w-[520px] max-w-[96vw]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.body-form-dialog :deep(.body-form-dialog__grid) {
  align-items: start;
}

.body-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
