<script lang="ts" setup>
import type {
  ConsultationActivityOption,
  RegistrationType,
  SaveRegistrationTypeParams,
} from '#/api/core/system/registration';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import {
  addRegistrationTypeApi,
  joinRegistrationActivityIds,
  listConsultationActivityOptionsApi,
  parseRegistrationActivityIds,
  updateRegistrationTypeApi,
} from '#/api/core/system/registration';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';

defineOptions({
  name: 'RegistrationTypeEditDialog',
});

interface Props {
  visible?: boolean;
  formData?: null | Partial<RegistrationType>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: null,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

/** 诊查费下拉候选项 */
const consultationOptions = ref<ConsultationActivityOption[]>([]);
/** 选项加载中 */
const optionsLoading = ref(false);

const isEditMode = computed(() => !!props.formData?.id);

/** 下拉选项：label 带售价，value 用 string 主键 */
const selectOptions = computed(() => {
  // 组装 ElSelect options
  return consultationOptions.value.map((row) => {
    const priceText =
      row.salePrice === null || row.salePrice === undefined || row.salePrice === ''
        ? '无价'
        : `${formatMoneyDisplay(row.salePrice)}元`;
    const busNo = row.busNo ? ` / ${row.busNo}` : '';
    return {
      label: `${row.name || '未命名'}${busNo}（${priceText}）`,
      value: String(row.id),
    };
  });
});

/** 按已选 AD 汇总售价（只读展示） */
function sumSelectedSaleAmount(selectedIds: string[]): string {
  const prices: Array<number | string> = [];
  const priceById = new Map<string, number | string | null | undefined>();
  // 建立 id → 售价映射
  for (const row of consultationOptions.value) {
    priceById.set(String(row.id), row.salePrice);
  }
  for (const id of selectedIds) {
    const price = priceById.get(id);
    // 缺价跳过，不计入合计
    if (price === null || price === undefined || price === '') {
      continue;
    }
    prices.push(price);
  }
  return formatMoneyDisplay(sumMoneyThenSettle(prices));
}

/** 提交新增/编辑 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as {
      activityIds?: string[];
      name?: string;
    };
    const name = (values.name || '').trim();
    if (!name) {
      ElMessage.error('请输入名称');
      return;
    }
    // 选中的诊查费主键列表 → CSV
    const activityIds = joinRegistrationActivityIds(values.activityIds || []);
    const payload: SaveRegistrationTypeParams = {
      name,
      activityIds,
    };
    if (isEditMode.value) {
      if (!props.formData?.id) {
        ElMessage.error('缺少挂号类型ID');
        return;
      }
      await updateRegistrationTypeApi({
        ...payload,
        id: String(props.formData.id),
      });
      ElMessage.success('编辑挂号类型成功');
    } else {
      await addRegistrationTypeApi(payload);
      ElMessage.success('新增挂号类型成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    console.error('操作失败:', error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '操作失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

/** 拉取诊查费候选项 */
async function loadConsultationOptions() {
  optionsLoading.value = true;
  try {
    const rows = await listConsultationActivityOptionsApi();
    consultationOptions.value = rows || [];
  } catch (error: unknown) {
    console.error('加载诊查费候选项失败:', error);
    consultationOptions.value = [];
    ElMessage.error('加载诊查费候选项失败');
  } finally {
    optionsLoading.value = false;
  }
}

/** 打开时重置表单并回显 */
async function resetForm() {
  await formApi.resetForm();
  await loadConsultationOptions();

  if (props.formData && Object.keys(props.formData).length > 0) {
    const form = props.formData;
    const selectedIds = parseRegistrationActivityIds(form.activityIds);
    await formApi.setValues({
      name: form.name || '',
      activityIds: selectedIds,
      amountDisplay: sumSelectedSaleAmount(selectedIds),
    });
  } else {
    await formApi.setValues({
      name: '',
      activityIds: [],
      amountDisplay: formatMoneyDisplay(0),
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
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
      style: { width: '100%' },
    },
  },
  layout: 'horizontal' as const,
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      required: true,
      componentProps: {
        placeholder: '请输入挂号类型名称',
      },
      rules: z.string().min(1, { message: '请输入名称' }),
    },
    {
      component: 'Select',
      fieldName: 'activityIds',
      label: '诊查费',
      help: '可多选；零元号可不选。金额按所选诊查费当前售价合计，不可自定义。',
      componentProps: {
        placeholder: '请选择诊查费（可多选，零元可不选）',
        options: selectOptions.value,
        multiple: true,
        filterable: true,
        collapseTags: true,
        collapseTagsTooltip: true,
        clearable: true,
        loading: optionsLoading.value,
        // 选中变更时刷新只读金额
        onChange: (ids: string[]) => {
          const selected = Array.isArray(ids) ? ids.map(String) : [];
          formApi.setFieldValue('amountDisplay', sumSelectedSaleAmount(selected));
        },
      },
    },
    {
      component: 'Input',
      fieldName: 'amountDisplay',
      label: '金额',
      componentProps: {
        disabled: true,
        placeholder: '由诊查费售价合计',
      },
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
    class="w-[640px] max-w-[96vw]"
    :title="isEditMode ? '编辑挂号类型' : '新增挂号类型'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
