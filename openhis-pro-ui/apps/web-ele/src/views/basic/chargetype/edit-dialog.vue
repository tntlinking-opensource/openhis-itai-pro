<script lang="ts" setup>
import type { ChargeType, CreateChargeTypeParams } from '#/api/core/system/charge-type';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import {
  createChargeTypeApi,
  initChargeTypeApi,
  updateChargeTypeApi,
} from '#/api/core/system/charge-type';

interface Props {
  visible?: boolean;
  formData?: null | Partial<ChargeType>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: null,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const isEditMode = computed(() => !!props.formData?.id);
const busNoValue = ref<string>('');
const categoryEnumValue = ref<null | string>(null);

// 费用性质分类下拉选项（来自后端 /chargeType/init，数据源：ContractCategory 枚举）
const categoryOptions = ref<ApiOptionItem[]>([]);
const loadingInit = ref(false);

async function loadInitOptions() {
  if (loadingInit.value) return;
  loadingInit.value = true;
  try {
    const res = await initChargeTypeApi();
    const data = ('data' in (res as object) ? (res as { data?: unknown }).data : res) as
      | undefined
      | { categoryOptions?: ApiOptionItem[] };
    categoryOptions.value = data?.categoryOptions ?? [];
  } catch (error) {
    console.error('加载费用性质初始化数据失败:', error);
    categoryOptions.value = [];
  } finally {
    loadingInit.value = false;
  }
}

function handleCategoryEnumUpdate(value: null | string | string[]) {
  const raw = Array.isArray(value) ? (value[0] ?? null) : value;
  const v = raw === null ? null : String(raw);
  categoryEnumValue.value = v;
  formApi.setFieldValue('categoryEnum', v ?? '');
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as Record<string, unknown>;
    if (isEditMode.value) {
      if (!props.formData?.id) {
        ElMessage.error('缺少收费类型ID');
        return;
      }
      const params: CreateChargeTypeParams & { id: string } = {
        id: props.formData.id,
        busNo: (values.busNo as string) || '',
        name: values.name || '',
        displayOrder: Number(values.displayOrder) || 0,
        categoryEnum: values.categoryEnum || '',
      };
      await updateChargeTypeApi(params);
      ElMessage.success('编辑费用性质成功');
    } else {
      const params: CreateChargeTypeParams = {
        busNo: (values.busNo as string) || '',
        name: values.name || '',
        displayOrder: Number(values.displayOrder) || 0,
        categoryEnum: values.categoryEnum || '',
      };
      await createChargeTypeApi(params);
      ElMessage.success('新增收费类型成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    // 约束错误对象类型，避免使用 any
    const err = error as { message?: string; msg?: string };
    console.error('操作失败:', error);
    const errorMessage = err.msg || err.message || '操作失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function resetForm() {
  await formApi.resetForm();

  if (props.formData && Object.keys(props.formData).length > 0) {
    const form = props.formData as Partial<
      ChargeType & { categoryEnum?: string; displayOrder?: number }
    >;
    // 分类回显直接使用后端的枚举值（1、2、3...），统一转成字符串
    const cat = form.categoryEnum;
    categoryEnumValue.value = cat === null ? null : String(cat);
    busNoValue.value = form.busNo ?? '';
    await formApi.setValues({
      busNo: form.busNo ?? '',
      name: form.name || '',
      displayOrder: form.displayOrder ?? 0,
      categoryEnum: cat === null ? '' : String(cat),
    });
  } else {
    categoryEnumValue.value = null;
    busNoValue.value = '';
    await formApi.setValues({
      busNo: '',
      name: '',
      displayOrder: 0,
      categoryEnum: '',
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await loadInitOptions();
    await resetForm();
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
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'busNo',
      label: '编码',
      required: true,
      componentProps: {
        placeholder: '请输入费用性质编码',
        maxlength: 64,
      },
      rules: z.string().min(1, { message: '请输入编码' }),
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      required: true,
      componentProps: {
        placeholder: '请输入名称',
      },
      rules: z.string().min(1, { message: '请输入名称' }),
    },
    {
      component: 'InputNumber',
      fieldName: 'displayOrder',
      label: '显示顺序',
      componentProps: {
        placeholder: '请输入显示顺序',
        min: 0,
        style: { width: '220px' },
      },
      rules: z.number().min(0, { message: '显示顺序必须大于等于0' }),
    },
    {
      component: 'Input',
      fieldName: 'categoryEnum',
      label: '分类',
      slot: 'categoryEnum',
      required: true,
      rules: z.string().min(1, { message: '请选择分类' }),
    },
  ]),
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
    class="w-[600px]"
    :title="isEditMode ? '编辑费用性质' : '新增费用性质'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form>
      <template #categoryEnum>
        <!-- 费用性质分类：自费/公费/医保等，值为 ContractCategory.value（1、2、3...） -->
        <ElSelect
          v-model="categoryEnumValue"
          placeholder="请选择分类"
          style="width: 220px"
          @change="handleCategoryEnumUpdate"
        >
          <ElOption
            v-for="item in categoryOptions"
            :key="String(item.value)"
            :label="item.label"
            :value="String(item.value ?? '')"
          />
        </ElSelect>
      </template>
    </Form>
  </Modal>
</template>
