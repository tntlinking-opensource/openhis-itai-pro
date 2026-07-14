<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  CreateScheduledJobParams,
  JobHandlerOption,
  ScheduledJobInfo,
  ScheduledJobOption,
  ScheduledJobParameter,
  ScheduledJobTenantOption,
  UpdateScheduledJobParams,
} from '#/api/core/system/scheduled-job';

import { computed, reactive, ref, watch } from 'vue';

import { formatDateTime } from '@vben/utils';

import {
  ElButton,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  buildCronApi,
  buildTenantIdForRequest,
  createScheduledJobApi,
  getScheduledJobOptionsApi,
  listJobHandlersApi,
  listScheduledJobTenantOptionsApi,
  updateScheduledJobApi,
  validateCronApi,
} from '#/api/core/system/scheduled-job';

defineOptions({
  name: 'ScheduledJobEditDialog',
});

interface Props {
  visible?: boolean;
  formData?: Partial<ScheduledJobInfo>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const dialogVisible = ref(false);
const submitting = ref(false);
const formRef = ref<FormInstance>();

const originalJobName = ref('');
const originalJobGroup = ref('');

const isEditMode = computed(() => !!originalJobName.value && !!originalJobGroup.value);

const handlerOptions = ref<JobHandlerOption[]>([]);
const statusOptions = ref<ScheduledJobOption[]>([]);
const misfireOptions = ref<ScheduledJobOption[]>([]);
const concurrentOptions = ref<ScheduledJobOption[]>([]);
const presetOptions = ref<ScheduledJobOption[]>([]);
const tenantOptions = ref<ScheduledJobTenantOption[]>([]);

const cronPreview = ref('');
const cronDescription = ref('');
const nextFirePreview = ref<string[]>([]);

const weekDayOptions = [
  { label: '周日', value: 1 },
  { label: '周一', value: 2 },
  { label: '周二', value: 3 },
  { label: '周三', value: 4 },
  { label: '周四', value: 5 },
  { label: '周五', value: 6 },
  { label: '周六', value: 7 },
];

const formModel = reactive({
  jobName: '',
  jobGroup: 'DEFAULT',
  displayName: '',
  description: '',
  remark: '',
  jobBeanName: '',
  cronPresetType: 'DAILY',
  intervalMinutes: 5,
  minute: 0,
  hour: 2,
  dayOfWeek: 2,
  dayOfMonth: 1,
  cronExpression: '',
  misfirePolicy: 'DEFAULT',
  concurrentPolicy: 'DISALLOW',
  status: 'PAUSE',
  tenantId: '',
  parameters: [] as ScheduledJobParameter[],
});

const formRules: FormRules = {
  jobName: [{ required: true, message: '请输入任务标识', trigger: 'blur' }],
  jobGroup: [{ required: true, message: '请输入任务组', trigger: 'blur' }],
  jobBeanName: [{ required: true, message: '请选择任务处理器', trigger: 'change' }],
  cronPresetType: [{ required: true, message: '请选择执行计划类型', trigger: 'change' }],
};

const showIntervalMinutes = computed(() => formModel.cronPresetType === 'EVERY_N_MINUTES');
const showMinuteOnly = computed(() => formModel.cronPresetType === 'HOURLY');
const showHourMinute = computed(
  () =>
    formModel.cronPresetType === 'DAILY' ||
    formModel.cronPresetType === 'WEEKLY' ||
    formModel.cronPresetType === 'MONTHLY',
);
const showWeekDay = computed(() => formModel.cronPresetType === 'WEEKLY');
const showMonthDay = computed(() => formModel.cronPresetType === 'MONTHLY');
const showCustomCron = computed(() => formModel.cronPresetType === 'CUSTOM');
const nextFirePreviewShort = computed(() => {
  if (!nextFirePreview.value.length) {
    return '';
  }
  const head = nextFirePreview.value.slice(0, 2).join('、');
  if (nextFirePreview.value.length > 2) {
    return `${head} 等`;
  }
  return head;
});

/** 加载处理器与下拉选项 */
async function loadMetaOptions() {
  try {
    const [handlers, optionGroups, tenants] = await Promise.all([
      listJobHandlersApi(),
      getScheduledJobOptionsApi(),
      listScheduledJobTenantOptionsApi(),
    ]);
    handlerOptions.value = (handlers || []).filter((item) => item.beanName !== 'sampleJob');
    tenantOptions.value = tenants || [];
    for (const group of optionGroups || []) {
      if (group.groupCode === 'status') {
        statusOptions.value = group.options || [];
      }
      if (group.groupCode === 'misfirePolicy') {
        misfireOptions.value = group.options || [];
      }
      if (group.groupCode === 'concurrentPolicy') {
        concurrentOptions.value = group.options || [];
      }
      if (group.groupCode === 'cronPresetType') {
        presetOptions.value = group.options || [];
      }
    }
  } catch {
    ElMessage.error('加载任务配置选项失败');
  }
}

/** 从已有 Cron 反推可视化预设，便于编辑时回显小时/分钟 */
function inferCronPresetFromExpression(cron: string) {
  const trimmed = cron.trim();
  let matched = /^0 0\/(\d+) \* \* \* \?$/.exec(trimmed);
  if (matched) {
    return {
      presetType: 'EVERY_N_MINUTES',
      intervalMinutes: Number(matched[1]),
    };
  }
  matched = /^0 (\d+) \* \* \* \?$/.exec(trimmed);
  if (matched) {
    return { presetType: 'HOURLY', minute: Number(matched[1]) };
  }
  matched = /^0 (\d+) (\d+) \* \* \?$/.exec(trimmed);
  if (matched) {
    return {
      presetType: 'DAILY',
      minute: Number(matched[1]),
      hour: Number(matched[2]),
    };
  }
  matched = /^0 (\d+) (\d+) \? \* (\d+)$/.exec(trimmed);
  if (matched) {
    return {
      presetType: 'WEEKLY',
      minute: Number(matched[1]),
      hour: Number(matched[2]),
      dayOfWeek: Number(matched[3]),
    };
  }
  matched = /^0 (\d+) (\d+) (\d+) \* \?$/.exec(trimmed);
  if (matched) {
    return {
      presetType: 'MONTHLY',
      minute: Number(matched[1]),
      hour: Number(matched[2]),
      dayOfMonth: Number(matched[3]),
    };
  }
  return null;
}

/** 将父组件传入的数据灌入表单 */
function fillFormFromProps() {
  const data = props.formData || {};
  if (data.jobName && data.jobGroup) {
    originalJobName.value = data.jobName;
    originalJobGroup.value = data.jobGroup;
    formModel.jobName = data.jobName;
    formModel.jobGroup = data.jobGroup;
  } else {
    originalJobName.value = '';
    originalJobGroup.value = '';
    formModel.jobName = '';
    formModel.jobGroup = 'DEFAULT';
  }
  formModel.displayName = data.displayName || '';
  formModel.description = data.description || '';
  formModel.remark = data.remark || '';
  formModel.jobBeanName = data.jobBeanName || '';
  formModel.cronExpression = data.cronExpression || '';
  formModel.misfirePolicy = data.misfirePolicy || 'DEFAULT';
  formModel.concurrentPolicy = data.concurrentPolicy || 'DISALLOW';
  formModel.status = data.status || 'PAUSE';
  formModel.tenantId = data.tenantId ? String(data.tenantId) : '';
  formModel.parameters = data.parameters ? data.parameters.map((item) => ({ ...item })) : [];
  const cron = data.cronExpression || '';
  formModel.cronExpression = cron;
  const inferred = cron ? inferCronPresetFromExpression(cron) : null;
  if (inferred) {
    formModel.cronPresetType = inferred.presetType;
    if (inferred.intervalMinutes != null) {
      formModel.intervalMinutes = inferred.intervalMinutes;
    }
    if (inferred.minute != null) {
      formModel.minute = inferred.minute;
    }
    if (inferred.hour != null) {
      formModel.hour = inferred.hour;
    }
    if (inferred.dayOfWeek != null) {
      formModel.dayOfWeek = inferred.dayOfWeek;
    }
    if (inferred.dayOfMonth != null) {
      formModel.dayOfMonth = inferred.dayOfMonth;
    }
  } else {
    formModel.cronPresetType = 'CUSTOM';
  }
  cronPreview.value = cron;
  cronDescription.value = data.cronDescription || '';
  nextFirePreview.value = [];
}

/** 重置为新建默认值 */
function resetFormForCreate() {
  originalJobName.value = '';
  originalJobGroup.value = '';
  formModel.jobName = '';
  formModel.jobGroup = 'DEFAULT';
  formModel.displayName = '';
  formModel.description = '';
  formModel.remark = '';
  formModel.jobBeanName = '';
  formModel.cronPresetType = 'DAILY';
  formModel.intervalMinutes = 5;
  formModel.minute = 0;
  formModel.hour = 2;
  formModel.dayOfWeek = 2;
  formModel.dayOfMonth = 1;
  formModel.cronExpression = '';
  formModel.misfirePolicy = 'DEFAULT';
  formModel.concurrentPolicy = 'DISALLOW';
  formModel.status = 'PAUSE';
  formModel.tenantId = '';
  formModel.parameters = [];
  cronPreview.value = '';
  cronDescription.value = '';
  nextFirePreview.value = [];
}

/** 根据预设生成 Cron 并校验预览 */
async function refreshCronPreview() {
  if (formModel.cronPresetType === 'CUSTOM') {
    cronPreview.value = formModel.cronExpression.trim();
  } else {
    try {
      const built = await buildCronApi({
        cronPresetType: formModel.cronPresetType,
        intervalMinutes: formModel.intervalMinutes,
        minute: formModel.minute,
        hour: formModel.hour,
        dayOfWeek: formModel.dayOfWeek,
        dayOfMonth: formModel.dayOfMonth,
      });
      cronPreview.value = built.cronExpression;
      cronDescription.value = built.description || '';
    } catch {
      cronPreview.value = '';
      cronDescription.value = '';
      return;
    }
  }
  if (!cronPreview.value) {
    nextFirePreview.value = [];
    return;
  }
  try {
    const validated = await validateCronApi({ cronExpression: cronPreview.value });
    if (validated.valid) {
      cronDescription.value = validated.description || cronDescription.value;
      nextFirePreview.value = (validated.nextFireTimes || []).map((item) => formatDateTime(item));
    } else {
      nextFirePreview.value = [];
    }
  } catch {
    nextFirePreview.value = [];
  }
}

/** 过滤空参数行后提交 */
function buildParametersPayload(): ScheduledJobParameter[] {
  const result: ScheduledJobParameter[] = [];
  for (const item of formModel.parameters) {
    const key = (item.key || '').trim();
    if (!key) {
      continue;
    }
    result.push({ key, value: item.value || '' });
  }
  return result;
}

/** 提交新建或更新；同名任务已存在时提示覆盖更新 */
async function handleSubmit() {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await refreshCronPreview();
  if (!cronPreview.value) {
    ElMessage.warning('请完善执行计划');
    return;
  }
  submitting.value = true;
  try {
    const parameters = buildParametersPayload();
    const tenantId = buildTenantIdForRequest(formModel.tenantId);
    if (isEditMode.value) {
      const payload: UpdateScheduledJobParams = {
        jobName: originalJobName.value,
        jobGroup: originalJobGroup.value,
        newJobName: formModel.jobName.trim(),
        newJobGroup: formModel.jobGroup.trim(),
        displayName: formModel.displayName.trim() || undefined,
        description: formModel.description.trim() || undefined,
        remark: formModel.remark.trim() || undefined,
        jobBeanName: formModel.jobBeanName,
        cronExpression: cronPreview.value,
        misfirePolicy: formModel.misfirePolicy,
        concurrentPolicy: formModel.concurrentPolicy,
        status: formModel.status,
        tenantId,
        parameters,
      };
      await updateScheduledJobApi(payload);
      ElMessage.success('更新成功');
    } else {
      const payload: CreateScheduledJobParams = {
        jobName: formModel.jobName.trim(),
        jobGroup: formModel.jobGroup.trim() || 'DEFAULT',
        displayName: formModel.displayName.trim() || undefined,
        description: formModel.description.trim() || undefined,
        remark: formModel.remark.trim() || undefined,
        jobBeanName: formModel.jobBeanName,
        cronExpression: cronPreview.value,
        misfirePolicy: formModel.misfirePolicy,
        concurrentPolicy: formModel.concurrentPolicy,
        status: formModel.status,
        tenantId,
        parameters,
      };
      await createScheduledJobApi(payload);
      ElMessage.success('创建成功');
    }
    emit('success');
    dialogVisible.value = false;
    emit('update:visible', false);
  } catch (error: unknown) {
    const message =
      (error as { msg?: string; message?: string })?.msg ||
      (error as { message?: string })?.message ||
      '保存失败，请稍后重试';
    if (!isEditMode.value && message.includes('任务已存在')) {
      try {
        await ElMessageBox.confirm(
          `任务「${formModel.jobName}」已存在（可能未在列表中显示）。是否用当前表单配置覆盖更新？`,
          '任务已存在',
          { type: 'warning', confirmButtonText: '覆盖更新', cancelButtonText: '取消' },
        );
        const parameters = buildParametersPayload();
        const tenantId = buildTenantIdForRequest(formModel.tenantId);
        const updatePayload: UpdateScheduledJobParams = {
          jobName: formModel.jobName.trim(),
          jobGroup: formModel.jobGroup.trim() || 'DEFAULT',
          displayName: formModel.displayName.trim() || undefined,
          description: formModel.description.trim() || undefined,
          remark: formModel.remark.trim() || undefined,
          jobBeanName: formModel.jobBeanName,
          cronExpression: cronPreview.value,
          misfirePolicy: formModel.misfirePolicy,
          concurrentPolicy: formModel.concurrentPolicy,
          status: formModel.status,
          tenantId,
          parameters,
        };
        await updateScheduledJobApi(updatePayload);
        ElMessage.success('已覆盖更新现有任务');
        emit('success');
        dialogVisible.value = false;
        emit('update:visible', false);
        return;
      } catch (confirmError: unknown) {
        if (confirmError !== 'cancel') {
          const confirmMessage =
            (confirmError as { msg?: string; message?: string })?.msg ||
            (confirmError as { message?: string })?.message ||
            '覆盖更新失败，请稍后重试';
          ElMessage.error(confirmMessage);
        }
        return;
      }
    }
    ElMessage.error(message);
  } finally {
    submitting.value = false;
  }
}

function handleClose() {
  dialogVisible.value = false;
  emit('update:visible', false);
}

/** 新增一行业务参数 */
function handleAddParameter() {
  formModel.parameters.push({ key: '', value: '' });
}

/** 删除指定参数行 */
function handleRemoveParameter(index: number) {
  formModel.parameters.splice(index, 1);
}

watch(
  () => props.visible,
  async (visible) => {
    dialogVisible.value = visible;
    if (visible) {
      await loadMetaOptions();
      if (props.formData?.jobName && props.formData?.jobGroup) {
        fillFormFromProps();
      } else {
        resetFormForCreate();
      }
      await refreshCronPreview();
    }
  },
  { immediate: true },
);

watch(
  () => [
    formModel.cronPresetType,
    formModel.intervalMinutes,
    formModel.minute,
    formModel.hour,
    formModel.dayOfWeek,
    formModel.dayOfMonth,
    formModel.cronExpression,
  ],
  () => {
    if (dialogVisible.value) {
      refreshCronPreview();
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="isEditMode ? '编辑定时任务' : '新增定时任务'"
    width="880px"
    top="4vh"
    class="scheduled-job-edit-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="formModel"
      :rules="formRules"
      label-width="100px"
      class="scheduled-job-form"
    >
      <div class="section-title">基本信息</div>
      <ElRow :gutter="12">
        <ElCol :span="8">
          <ElFormItem label="任务标识" prop="jobName">
            <ElInput
              v-model="formModel.jobName"
              placeholder="如：住院滚费"
              :disabled="isEditMode"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="任务组" prop="jobGroup">
            <ElInput v-model="formModel.jobGroup" placeholder="DEFAULT" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="显示名称">
            <ElInput v-model="formModel.displayName" placeholder="运维可见名称" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="24">
          <ElFormItem label="任务处理器" prop="jobBeanName">
            <ElSelect
              v-model="formModel.jobBeanName"
              filterable
              placeholder="请选择处理器"
              class="w-full"
            >
              <ElOption
                v-for="item in handlerOptions"
                :key="item.beanName"
                :label="`${item.description || item.beanName}`"
                :value="item.beanName"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="24">
          <ElFormItem label="说明">
            <ElInput v-model="formModel.description" placeholder="任务用途说明（可选）" />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <div class="section-title">执行计划</div>
      <ElRow :gutter="12" class="items-end">
        <ElCol :span="8">
          <ElFormItem label="计划类型" prop="cronPresetType">
            <ElSelect v-model="formModel.cronPresetType" class="w-full">
              <ElOption
                v-for="item in presetOptions"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showIntervalMinutes" :span="8">
          <ElFormItem label="间隔(分)">
            <ElInputNumber v-model="formModel.intervalMinutes" :min="1" :max="59" class="w-full" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showHourMinute" :span="8">
          <ElFormItem label="小时">
            <ElInputNumber v-model="formModel.hour" :min="0" :max="23" class="w-full" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showMinuteOnly || showHourMinute" :span="8">
          <ElFormItem label="分钟">
            <ElInputNumber v-model="formModel.minute" :min="0" :max="59" class="w-full" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showWeekDay" :span="8">
          <ElFormItem label="星期">
            <ElSelect v-model="formModel.dayOfWeek" class="w-full">
              <ElOption
                v-for="item in weekDayOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showMonthDay" :span="8">
          <ElFormItem label="日期">
            <ElInputNumber v-model="formModel.dayOfMonth" :min="1" :max="31" class="w-full" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="showCustomCron" :span="16">
          <ElFormItem label="Cron">
            <ElInput v-model="formModel.cronExpression" placeholder="0 0 2 * * ?" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="24">
          <ElFormItem label="Cron 预览">
            <div class="preview-box">
              <span class="preview-cron">{{ cronPreview || '—' }}</span>
              <span v-if="cronDescription" class="preview-desc">{{ cronDescription }}</span>
              <span v-if="nextFirePreviewShort" class="preview-desc">
                下次执行：{{ nextFirePreviewShort }}
              </span>
            </div>
          </ElFormItem>
        </ElCol>
      </ElRow>

      <div class="section-title">高级选项</div>
      <ElRow :gutter="16">
        <ElCol :span="12">
          <ElFormItem label="错过策略">
            <ElSelect v-model="formModel.misfirePolicy" class="w-full">
              <ElOption
                v-for="item in misfireOptions"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="并发策略">
            <ElSelect v-model="formModel.concurrentPolicy" class="w-full">
              <ElOption
                v-for="item in concurrentOptions"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="初始状态">
            <ElSelect v-model="formModel.status" class="w-full">
              <ElOption
                v-for="item in statusOptions"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="所属租户">
            <ElSelect
              v-model="formModel.tenantId"
              filterable
              clearable
              placeholder="平台级任务可留空"
              class="w-full"
            >
              <ElOption
                v-for="item in tenantOptions"
                :key="item.id"
                :label="`${item.name} (${item.code})`"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </ElRow>

      <div class="section-title">
        任务参数
        <ElButton type="primary" link class="ml-2" @click="handleAddParameter">+ 添加参数</ElButton>
        <span v-if="!formModel.parameters.length" class="section-hint">无参数时可留空</span>
      </div>
      <ElTable
        v-if="formModel.parameters.length"
        :data="formModel.parameters"
        border
        class="params-table"
      >
        <ElTableColumn label="参数名" min-width="160">
          <template #default="{ row }">
            <ElInput v-model="row.key" placeholder="如 billDate" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="参数值" min-width="200">
          <template #default="{ row }">
            <ElInput v-model="row.value" placeholder="可选" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="72" align="center">
          <template #default="{ $index }">
            <ElButton type="danger" link @click="handleRemoveParameter($index)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElForm>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="handleSubmit">保存</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.section-title {
  margin: 6px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-hint {
  margin-left: 8px;
  font-size: 14px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.preview-box {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  align-items: center;
  width: 100%;
  min-height: 32px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.preview-cron {
  font-family: ui-monospace, monospace;
  font-size: 14px;
}

.preview-desc {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.params-table {
  max-height: 120px;
}

.scheduled-job-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
</style>

<style>
.scheduled-job-edit-dialog .el-dialog__body {
  padding-top: 12px;
  padding-bottom: 16px;
}
</style>
