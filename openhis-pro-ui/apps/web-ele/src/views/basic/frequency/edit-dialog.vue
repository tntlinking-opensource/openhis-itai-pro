<script lang="ts" setup>
import type {
  CreateFrequencyParams,
  FrequencyCycleTypeCode,
  FrequencyItem,
  UpdateFrequencyParams,
} from '#/api/basic/frequency';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { createFrequencyApi, initFrequencyApi, updateFrequencyApi } from '#/api/basic/frequency';

defineOptions({ name: 'BasicFrequencyEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<FrequencyItem> & { id?: string };
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

const CYCLE_DAILY: FrequencyCycleTypeCode = 'daily';
const CYCLE_WEEKLY: FrequencyCycleTypeCode = 'weekly';
const CYCLE_INTERVAL: FrequencyCycleTypeCode = 'interval';
const CYCLE_AS_NEEDED: FrequencyCycleTypeCode = 'as-needed';
const CYCLE_FIXED_COUNT: FrequencyCycleTypeCode = 'fixed-count';

/** BR-FRQ-007：日执行时点 HH:mm 格式 */
const DAY_TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

const CYCLE_SPECIFIC_FIELDS = [
  'dayTimes',
  'dayCount',
  'dayInterval',
  'weekCycleFlag',
  'totalExecutionCount',
  'executionPeriod',
  'executionPeriodUnit',
  'allowCrossDay',
] as const;

const originalData = ref<Record<string, unknown>>({});
const selectedCycleType = ref<FrequencyCycleTypeCode | string>(CYCLE_DAILY);

const cycleTypeOptions = ref<Array<{ label: string; value: string }>>([]);
const sceneOptions = ref<Array<{ label: string; value: string }>>([]);
const allowCrossDayOptions = ref<Array<{ label: string; value: string }>>([]);
const periodUnitOptions = ref<Array<{ label: string; value: string }>>([]);

const weekCycleFlagOptions = [
  { label: '是', value: '1' },
  { label: '否', value: '0' },
];

const isEditMode = computed(() => !!props.formData?.id && !props.isCopyMode);

const dialogTitle = computed(() => {
  if (props.isCopyMode) return '复制频次';
  return isEditMode.value ? '编辑频次' : '新增频次';
});

const rateCodeRequired = computed(() => props.isCopyMode || !isEditMode.value);

const fullWidthStyle = { width: '100%' };

const sectionTitleClass = 'col-span-full !mb-0 !pb-0 frequency-form-dialog__section-title';

const showAllowCrossDay = computed(() => {
  const ct = selectedCycleType.value;
  return ct === CYCLE_DAILY || ct === CYCLE_WEEKLY || ct === CYCLE_INTERVAL;
});

async function loadInitOptions() {
  try {
    const res = await initFrequencyApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (
            res as {
              data?: {
                allowCrossDayOptions?: Array<{ label: string; value: string }>;
                cycleTypeOptions?: Array<{ label: string; value: string }>;
                periodUnitOptions?: Array<{ label: string; value: string }>;
                sceneOptions?: Array<{ label: string; value: string }>;
              };
            }
          ).data
        : (res as {
            allowCrossDayOptions?: Array<{ label: string; value: string }>;
            cycleTypeOptions?: Array<{ label: string; value: string }>;
            periodUnitOptions?: Array<{ label: string; value: string }>;
            sceneOptions?: Array<{ label: string; value: string }>;
          });
    cycleTypeOptions.value = data?.cycleTypeOptions ?? [];
    sceneOptions.value = data?.sceneOptions ?? [];
    allowCrossDayOptions.value = data?.allowCrossDayOptions ?? [];
    periodUnitOptions.value = data?.periodUnitOptions ?? [];
  } catch {
    cycleTypeOptions.value = [];
    sceneOptions.value = [];
    allowCrossDayOptions.value = [];
    periodUnitOptions.value = [];
  }
}

/** 周期类型切换时清空上一类型的专用字段，避免提交脏数据 */
async function handleCycleTypeChange(value: string) {
  selectedCycleType.value = value || CYCLE_DAILY;
  const resets: Record<string, undefined> = {};
  for (const field of CYCLE_SPECIFIC_FIELDS) {
    resets[field] = undefined;
  }
  await formApi.setValues(resets);
}

/** 校验日执行时点格式（BR-FRQ-007） */
function assertDayTimesFormat(dayTimes?: string): void {
  if (!dayTimes) return;
  const segments = dayTimes.split(',');
  for (const segment of segments) {
    const value = segment.trim();
    if (!value) continue;
    if (!DAY_TIME_PATTERN.test(value)) {
      throw new Error('日执行时点格式不合法，请使用 HH:mm 逗号分隔，如 08:00,12:00,18:00');
    }
  }
}

/** 按周期类型裁剪 payload，仅保留当前类型相关字段 */
function pickCycleFields(
  cycleType: string,
  merged: Record<string, unknown>,
): Partial<CreateFrequencyParams> {
  const dayTimes =
    merged.dayTimes !== null && merged.dayTimes !== undefined && merged.dayTimes !== ''
      ? Array.isArray(merged.dayTimes)
        ? (merged.dayTimes as string[]).join(',')
        : String(merged.dayTimes).trim()
      : undefined;

  const dayCount = merged.dayCount as number | undefined;
  const dayInterval = merged.dayInterval as number | undefined;
  const weekCycleFlag =
    merged.weekCycleFlag !== undefined &&
    merged.weekCycleFlag !== null &&
    merged.weekCycleFlag !== ''
      ? Number(merged.weekCycleFlag)
      : undefined;
  const totalExecutionCount = merged.totalExecutionCount as number | undefined;
  const executionPeriod = merged.executionPeriod as number | undefined;
  const executionPeriodUnit = merged.executionPeriodUnit as string | undefined;
  const allowCrossDay =
    merged.allowCrossDay !== undefined &&
    merged.allowCrossDay !== null &&
    merged.allowCrossDay !== ''
      ? String(merged.allowCrossDay)
      : undefined;

  if (cycleType === CYCLE_DAILY) {
    return { dayTimes, dayCount, dayInterval, allowCrossDay };
  }
  if (cycleType === CYCLE_WEEKLY) {
    return {
      dayTimes,
      dayInterval,
      weekCycleFlag: weekCycleFlag ?? 1,
      allowCrossDay,
    };
  }
  if (cycleType === CYCLE_INTERVAL) {
    return { executionPeriod, executionPeriodUnit, dayInterval, allowCrossDay };
  }
  if (cycleType === CYCLE_FIXED_COUNT) {
    return { totalExecutionCount, dayTimes };
  }
  if (cycleType === CYCLE_AS_NEEDED) {
    return { dayTimes };
  }
  return {};
}

function buildPayload(merged: Record<string, unknown>): CreateFrequencyParams {
  const cycleType = (merged.cycleType as string) || CYCLE_DAILY;
  const cycleFields = pickCycleFields(cycleType, merged);

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

  return {
    rateCode: merged.rateCode as string | undefined,
    name: String(merged.name ?? ''),
    cycleType,
    ...cycleFields,
    sortNo: merged.sortNo as number | undefined,
    applicableScenes,
  };
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  modalApi.lock(true);
  try {
    const formValues = (await formApi.getValues()) as Record<string, unknown>;
    const merged = { ...originalData.value, ...formValues };
    const payload = buildPayload(merged);

    try {
      assertDayTimesFormat(payload.dayTimes);
    } catch (error) {
      ElMessage.warning(error instanceof Error ? error.message : '日执行时点格式不合法');
      return;
    }

    if (isEditMode.value) {
      const id = props.formData?.id ?? merged.id;
      if (!id) {
        ElMessage.warning('缺少频次ID');
        return;
      }
      const rateCode =
        (merged.rateCode as string) ??
        (originalData.value.rateCode as string) ??
        props.formData?.rateCode ??
        '';
      const payload: UpdateFrequencyParams = {
        ...buildPayload(merged),
        id: String(id),
        rateCode: rateCode || undefined,
      };
      await updateFrequencyApi(payload);
      ElMessage.success('更新成功');
    } else {
      if (!payload.rateCode) {
        ElMessage.warning('请填写频次编码');
        return;
      }
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

function normalizeApplicableScenes(v: unknown): string[] {
  if (v === null || v === undefined || v === '') return [];
  if (Array.isArray(v)) return (v as string[]).filter(Boolean);
  return String(v)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function toOptionValue(v: null | number | string | undefined): string {
  if (v === undefined || v === null || v === '') return '';
  return String(v);
}

async function loadFormData() {
  const { formData, isCopyMode } = props;
  if (formData && Object.keys(formData).length > 0) {
    originalData.value = { ...formData };
    const cycleType = toOptionValue(formData.cycleType) || CYCLE_DAILY;
    selectedCycleType.value = cycleType;
    await formApi.setValues({
      ...formData,
      cycleType,
      dayTimes: normalizeDayTimes(formData.dayTimes),
      applicableScenes: normalizeApplicableScenes(formData.applicableScenes),
      weekCycleFlag: toOptionValue(formData.weekCycleFlag),
      allowCrossDay: toOptionValue(formData.allowCrossDay),
      rateCode: isCopyMode ? '' : formData.rateCode,
    });
  } else {
    originalData.value = {};
    selectedCycleType.value = CYCLE_DAILY;
    await formApi.resetForm();
    await formApi.setValues({ cycleType: CYCLE_DAILY });
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

function buildCycleFields() {
  const ct = selectedCycleType.value;
  const fields: Record<string, unknown>[] = [];

  if (ct === CYCLE_DAILY) {
    fields.push(
      {
        component: 'Input',
        fieldName: 'dayTimes',
        label: '日执行时点',
        componentProps: {
          placeholder: '逗号分隔，如 08:00,12:00,18:00',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'dayInterval',
        label: '日间隔(天)',
        componentProps: {
          placeholder: '隔日=2；每两天一次请单独建频次',
          min: 1,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'dayCount',
        label: '日计数',
        componentProps: {
          placeholder: '如「每 N 日」中的 N，可选',
          min: 1,
        },
      },
    );
  }

  if (ct === CYCLE_WEEKLY) {
    fields.push(
      {
        component: 'Select',
        fieldName: 'weekCycleFlag',
        label: '按周周期',
        componentProps: {
          options: weekCycleFlagOptions,
          placeholder: '是否为周周期频次',
        },
      },
      {
        component: 'Input',
        fieldName: 'dayTimes',
        label: '周内执行时点',
        componentProps: {
          placeholder: '逗号分隔，如 08:00,14:00',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'dayInterval',
        label: '周期间隔',
        componentProps: {
          placeholder: '每 N 周，可选',
          min: 1,
        },
      },
    );
  }

  if (ct === CYCLE_INTERVAL) {
    fields.push(
      {
        component: 'InputNumber',
        fieldName: 'executionPeriod',
        label: '执行周期',
        rules: 'required',
        componentProps: {
          placeholder: '如 Q8H 填 8',
          min: 1,
        },
      },
      {
        component: 'Select',
        fieldName: 'executionPeriodUnit',
        label: '周期单位',
        rules: 'required',
        componentProps: {
          options: periodUnitOptions.value,
          placeholder: '分钟/小时/天/周/月',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'dayInterval',
        label: '日间隔(天)',
        componentProps: {
          placeholder: '按天间隔时填写，如 QOD=2',
          min: 1,
        },
      },
    );
  }

  if (ct === CYCLE_FIXED_COUNT) {
    fields.push(
      {
        component: 'InputNumber',
        fieldName: 'totalExecutionCount',
        label: '总执行次数',
        rules: 'required',
        componentProps: {
          placeholder: '如 ST=1',
          min: 1,
        },
      },
      {
        component: 'Input',
        fieldName: 'dayTimes',
        label: '执行时点',
        componentProps: {
          placeholder: '可选，如 08:00',
        },
      },
    );
  }

  if (ct === CYCLE_AS_NEEDED) {
    fields.push({
      component: 'Input',
      fieldName: 'dayTimes',
      label: '参考时点',
      componentProps: {
        placeholder: 'PRN 可不填；若需参考时点可填写',
      },
    });
  }

  return fields;
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
  wrapperClass: 'frequency-form-dialog__grid grid grid-cols-1',
  schema: computed(() => {
    const baseFields: Record<string, unknown>[] = [
      {
        component: 'SectionTitle',
        fieldName: '__sectionBasic',
        label: '基本信息',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: { title: '基本信息' },
      },
      {
        component: 'Input',
        fieldName: 'rateCode',
        label: '频次编码',
        rules: rateCodeRequired.value
          ? z.string().min(1, { message: '请填写频次编码' })
          : undefined,
        componentProps: {
          placeholder: '如 QD、BID、Q8H、PRN',
          disabled: isEditMode.value,
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '频次名称',
        rules: z.string().min(1, { message: '请填写频次名称' }),
        componentProps: {
          placeholder: '如 每日一次、每8小时一次',
        },
      },
      {
        component: 'SectionTitle',
        fieldName: '__sectionExecution',
        label: '执行规则',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: { title: '执行规则' },
      },
      {
        component: 'Select',
        fieldName: 'cycleType',
        label: '周期类型',
        rules: z.string().min(1, { message: '请选择周期类型' }),
        componentProps: {
          options: cycleTypeOptions.value,
          placeholder: '请选择周期类型',
          onChange: handleCycleTypeChange,
        },
      },
      ...buildCycleFields(),
    ];

    if (showAllowCrossDay.value) {
      baseFields.push({
        component: 'Select',
        fieldName: 'allowCrossDay',
        label: '跨日策略',
        componentProps: {
          options: allowCrossDayOptions.value,
          placeholder: '是否允许执行跨自然日',
          clearable: true,
        },
      });
    }

    baseFields.push(
      {
        component: 'SectionTitle',
        fieldName: '__sectionScope',
        label: '适用范围',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: { title: '适用范围' },
      },
      {
        component: 'Select',
        fieldName: 'applicableScenes',
        label: '适用场景',
        componentProps: {
          options: sceneOptions.value,
          placeholder: '可多选',
          multiple: true,
          collapseTags: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'sortNo',
        label: '排序号',
        componentProps: {
          placeholder: '越小越靠前',
          min: 0,
        },
      },
    );

    return baseFields;
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
</script>

<template>
  <Modal
    class="frequency-form-dialog w-[600px] max-w-[96vw]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.frequency-form-dialog :deep(.frequency-form-dialog__grid) {
  align-items: start;
}

.frequency-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}

.frequency-form-dialog :deep(.frequency-form-dialog__section-title) {
  margin-top: 4px;
  padding-bottom: 0 !important;
}

.frequency-form-dialog :deep(.frequency-form-dialog__section-title .el-form-item__content) {
  min-height: 0;
}
</style>
