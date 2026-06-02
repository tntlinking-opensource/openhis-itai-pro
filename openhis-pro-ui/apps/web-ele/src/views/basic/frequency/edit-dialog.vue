<script lang="ts" setup>
import type {
  CreateFrequencyParams,
  FrequencyItem,
  UpdateFrequencyParams,
} from '#/api/basic/frequency';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createFrequencyApi, initFrequencyApi, updateFrequencyApi } from '#/api/basic/frequency';

interface Props {
  visible?: boolean;
  formData?: Partial<FrequencyItem> & { id?: string };
  /** 复制模式：以当前行预填，要求填写新频次编码 */
  isCopyMode?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  isCopyMode: false,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Record<string, unknown>>({});

const statusOptions = ref<Array<{ label: string; value: string }>>([]);
const cycleTypeOptions = ref<Array<{ label: string; value: string }>>([]);
const sceneOptions = ref<Array<{ label: string; value: string }>>([]);
const allowCrossDayOptions = ref<Array<{ label: string; value: string }>>([]);

const isEditMode = computed(() => {
  return !!props.formData?.id && !props.isCopyMode;
});

const dialogTitle = computed(() => {
  if (props.isCopyMode) return '复制频次';
  return isEditMode.value ? '编辑频次' : '新增频次';
});

/** 需求 2.2：频次编码在复制时必填（新编码），新增时建议填写 */
const rateCodeRequired = computed(() => props.isCopyMode);

async function loadInitOptions() {
  try {
    const res = await initFrequencyApi();
    const data = (
      res as {
        data?: {
          allowCrossDayOptions?: Array<{ label: string; value: string }>;
          cycleTypeOptions?: Array<{ label: string; value: string }>;
          sceneOptions?: Array<{ label: string; value: string }>;
          statusOptions?: Array<{ label: string; value: string }>;
        };
      }
    )?.data;
    statusOptions.value = data?.statusOptions ?? [];
    cycleTypeOptions.value = data?.cycleTypeOptions ?? [];
    sceneOptions.value = data?.sceneOptions ?? [];
    allowCrossDayOptions.value = data?.allowCrossDayOptions ?? [];
  } catch {
    statusOptions.value = [];
    cycleTypeOptions.value = [];
    sceneOptions.value = [];
    allowCrossDayOptions.value = [];
  }
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  modalApi.lock(true);
  try {
    const formValues = (await formApi.getValues()) as Record<string, unknown>;
    const merged = { ...originalData.value, ...formValues };

    // dayTimes：统一为字符串提交（后端为 string）
    let dayTimes: string | undefined;
    if (merged.dayTimes !== null && merged.dayTimes !== undefined && merged.dayTimes !== '') {
      dayTimes = Array.isArray(merged.dayTimes)
        ? (merged.dayTimes as string[]).join(',')
        : String(merged.dayTimes).trim();
    }

    // applicableScenes：多选为数组时转为逗号分隔字符串，后端为 String
    let applicableScenes: string | undefined;
    if (
      merged.applicableScenes !== null &&
      merged.applicableScenes !== undefined &&
      merged.applicableScenes !== ''
    ) {
      applicableScenes = Array.isArray(merged.applicableScenes)
        ? (merged.applicableScenes as string[]).join(',')
        : String(merged.applicableScenes).trim();
    }

    // activeFlag、allowCrossDay：前端可能为字符串 "0"/"1"，转为 number 供后端 Integer
    const activeFlag =
      merged.activeFlag !== undefined && merged.activeFlag !== null && merged.activeFlag !== ''
        ? Number(merged.activeFlag)
        : undefined;
    const allowCrossDay =
      merged.allowCrossDay !== undefined &&
      merged.allowCrossDay !== null &&
      merged.allowCrossDay !== ''
        ? Number(merged.allowCrossDay)
        : undefined;

    if (isEditMode.value) {
      const id = props.formData?.id ?? merged.id;
      if (!id) {
        ElMessage.warning('缺少频次ID');
        return;
      }
      // 编辑时频次编码只读，可能不在 formValues 中，用 originalData/formData 保留原值，避免更新时被置空
      const rateCode =
        (merged.rateCode as string) ??
        originalData.value.rateCode ??
        props.formData?.rateCode ??
        '';
      const payload: UpdateFrequencyParams = {
        id: String(id),
        rateCode: rateCode || undefined,
        name: String(merged.name ?? ''),
        dayInterval: merged.dayInterval as number | undefined,
        dayTimes,
        weekCycleFlag: merged.weekCycleFlag as number | undefined,
        weekInterval: merged.weekInterval as number | undefined,
        totalExecutionCount: merged.totalExecutionCount as number | undefined,
        executionPeriod: merged.executionPeriod as number | undefined,
        executionPeriodUnit: merged.executionPeriodUnit as string | undefined,
        thirdCode: merged.thirdCode as string | undefined,
        memo: merged.memo as string | undefined,
        activeFlag,
        sortNo: merged.sortNo as number | undefined,
        cycleType: merged.cycleType as number | undefined,
        applicableScenes,
        allowCrossDay,
      };
      await updateFrequencyApi(payload);
      ElMessage.success('更新成功');
    } else {
      const payload: CreateFrequencyParams = {
        rateCode: merged.rateCode as string | undefined,
        name: String(merged.name ?? ''),
        dayInterval: merged.dayInterval as number | undefined,
        dayTimes,
        weekCycleFlag: merged.weekCycleFlag as number | undefined,
        weekInterval: merged.weekInterval as number | undefined,
        totalExecutionCount: merged.totalExecutionCount as number | undefined,
        executionPeriod: merged.executionPeriod as number | undefined,
        executionPeriodUnit: merged.executionPeriodUnit as string | undefined,
        thirdCode: merged.thirdCode as string | undefined,
        memo: merged.memo as string | undefined,
        activeFlag,
        sortNo: merged.sortNo as number | undefined,
        cycleType: merged.cycleType as number | undefined,
        applicableScenes,
        allowCrossDay,
      };
      await createFrequencyApi(payload);
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

function normalizeDayTimes(v: FrequencyItem['dayTimes'] | unknown): string {
  if (v === null || v === undefined || v === '') return '';
  if (Array.isArray(v)) return (v as string[]).join(', ');
  return String(v).trim();
}

/** 适用场景：后端为逗号分隔字符串，多选组件需要数组 */
function normalizeApplicableScenes(v: unknown): string[] {
  if (v === null || v === undefined || v === '') return [];
  if (Array.isArray(v)) return (v as string[]).filter(Boolean);
  return String(v)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 状态下拉、周期类型、允许跨日：后端为 number，下拉 option.value 为 string，回显时转为字符串才能匹配并显示枚举文案 */
function toOptionValue(v: null | number | string | undefined): string {
  if (v === undefined || v === null || v === '') return '';
  return String(v);
}

async function loadFormData() {
  const { formData, isCopyMode } = props;
  if (formData && Object.keys(formData).length > 0) {
    originalData.value = { ...formData };
    await formApi.setValues({
      ...formData,
      dayTimes: normalizeDayTimes(formData.dayTimes),
      applicableScenes: normalizeApplicableScenes(formData.applicableScenes),
      activeFlag: toOptionValue(formData.activeFlag as number | string | undefined),
      cycleType: toOptionValue(formData.cycleType as number | string | undefined),
      allowCrossDay: toOptionValue(formData.allowCrossDay as number | string | undefined),
      // 复制模式下清空编码，由用户填写新编码
      rateCode: isCopyMode ? '' : formData.rateCode,
    });
  } else {
    originalData.value = {};
    await formApi.resetForm();
  }
}

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

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '220px' },
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'flex flex-wrap flex-row',
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'rateCode',
      label: '频次编码',
      rules: rateCodeRequired.value ? 'required' : undefined,
      componentProps: {
        placeholder: '如 QD、BID、TID、Q4H，复制时必填新编码',
        disabled: isEditMode.value,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '频次名称',
      rules: 'required',
      componentProps: {
        placeholder: '如 每日一次、每日两次',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'dayInterval',
      label: '日间隔(天)',
      componentProps: {
        placeholder: '如 QD=1，Q2D=2',
        min: 0,
      },
    },
    {
      component: 'Input',
      fieldName: 'dayTimes',
      label: '日内次数/时间点',
      componentProps: {
        placeholder: '如 1-0-0 或 08:00,14:00,20:00',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'weekCycleFlag',
      label: '按周周期(0否1是)',
      componentProps: {
        placeholder: '0 或 1',
        min: 0,
        max: 1,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'weekInterval',
      label: '周间隔',
      componentProps: {
        placeholder: '周间隔',
        min: 0,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'totalExecutionCount',
      label: '总执行次数',
      componentProps: {
        placeholder: '可选',
        min: 0,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'executionPeriod',
      label: '执行周期长度',
      componentProps: {
        placeholder: '可选',
        min: 0,
      },
    },
    {
      component: 'Input',
      fieldName: 'executionPeriodUnit',
      label: '执行周期单位',
      componentProps: {
        placeholder: '如 DAY、WEEK',
      },
    },
    {
      component: 'Input',
      fieldName: 'thirdCode',
      label: '第三方编码',
      componentProps: {
        placeholder: '医保、区域码等',
      },
    },
    {
      component: 'Select',
      fieldName: 'activeFlag',
      label: '状态',
      componentProps: {
        options: statusOptions.value,
        placeholder: '启用/停用',
        clearable: true,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortNo',
      label: '排序号',
      componentProps: {
        placeholder: '用于列表与下拉排序',
        min: 0,
      },
    },
    {
      component: 'Select',
      fieldName: 'cycleType',
      label: '周期类型',
      componentProps: {
        options: cycleTypeOptions.value,
        placeholder: '请选择周期类型',
        clearable: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'applicableScenes',
      label: '适用场景',
      componentProps: {
        options: sceneOptions.value,
        placeholder: '可多选，暂以逗号分隔保存',
        multiple: true,
        collapseTags: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'allowCrossDay',
      label: '允许跨日',
      componentProps: {
        options: allowCrossDayOptions.value,
        placeholder: '是否允许执行跨自然日',
        clearable: true,
      },
    },
    {
      component: 'InputArea',
      fieldName: 'memo',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
        rows: 3,
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
    class="w-[600px]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
