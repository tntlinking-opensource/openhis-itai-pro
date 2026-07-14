<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';
import type { AdviceProductOption } from '../advice/services/advice.types';

import { computed, ref, watch } from 'vue';

import { ElAlert, ElButton, ElForm, ElFormItem, ElMessage, ElOption, ElSelect } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { httpInitInpatientDoctorStationAdvice } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

import { firstTerminologyCode } from '../advice/services/advice-format';
import {
  hasActiveNursingLevelOnPatient,
  loadNursingLevelCatalogOptions,
  saveAndSignNursingLevelAdvice,
} from '../advice/services/inpatient-advice-nursing';

defineOptions({
  name: 'InpatientNursingAdviceDialog',
});

interface Props {
  patient: InpatientDoctorStationPatient | null;
}

const props = withDefaults(defineProps<Props>(), {
  patient: null,
});

const visible = defineModel<boolean>({ default: false });

const emit = defineEmits<{
  success: [];
}>();

const formRef = ref<FormInstance>();
const nursingOptions = ref<AdviceProductOption[]>([]);
const defaultRateCode = ref('');

const form = ref({
  activityDefinitionId: '',
});

const rules: FormRules = {
  activityDefinitionId: [{ required: true, message: '请选择护理级别', trigger: 'change' }],
};

const catalogRequest = useRequest(
  async () => {
    const patient = props.patient;
    if (!patient) return [];
    const encounterId = (patient.encounterId ?? '').trim();
    const patientId = (patient.patientId ?? '').trim();
    if (!encounterId || !patientId) return [];
    return loadNursingLevelCatalogOptions({
      encounterId,
      patientId,
      organizationUnitId: preserveApiIdForRequest(patient.organizationId),
    });
  },
  { manual: true, showErrorMessage: false },
);

const initRequest = useRequest(
  async () => {
    const patient = props.patient;
    if (!patient) return '';
    const encounterId = (patient.encounterId ?? '').trim();
    const patientId = (patient.patientId ?? '').trim();
    if (!encounterId || !patientId) return '';
    const res = await httpInitInpatientDoctorStationAdvice({ encounterId, patientId });
    return firstTerminologyCode(res.medicationRateOptions ?? []);
  },
  { manual: true },
);

const submitRequest = useRequest(saveAndSignNursingLevelAdvice, {
  manual: true,
  showErrorMessage: false,
});

const dialogLoading = computed(
  () => catalogRequest.loading.value || initRequest.loading.value || submitRequest.loading.value,
);

/** 当前有效护理级别展示名（患者队列 enrich） */
const currentNursingLevelLabel = computed(() => {
  const name = String(props.patient?.nursingLevelName ?? '').trim();
  return name || '—';
});

/** 是否存在有效护理级别，换级须先停嘱 */
const hasActiveNursingLevel = computed(() =>
  hasActiveNursingLevelOnPatient(props.patient?.nursingLevelStatusEnum),
);

/** 选中目录行（提交 unit/rate/执行科室） */
const selectedNursingOption = computed(() =>
  nursingOptions.value.find(
    (opt) => String(opt.productDefinitionId ?? '').trim() === form.value.activityDefinitionId,
  ),
);

/** 弹窗打开：加载护理级别目录与默认频次 */
watch(visible, (open) => {
  if (!open) return;
  form.value.activityDefinitionId = '';
  formRef.value?.clearValidate();
  void Promise.all([catalogRequest.run(), initRequest.run()]).then(([options, rateCode]) => {
    nursingOptions.value = options ?? [];
    defaultRateCode.value = String(rateCode ?? '').trim();
    if (nursingOptions.value.length === 0) {
      ElMessage.warning('未找到护理级别目录，请确认主数据已维护 category=26 的诊疗活动');
    }
    const currentActId = preserveApiIdForRequest(props.patient?.nursingLevelActivityId);
    if (
      currentActId &&
      nursingOptions.value.some((opt) => opt.productDefinitionId === currentActId)
    ) {
      form.value.activityDefinitionId = currentActId;
    }
  });
});

/** 提交开立护理级别（长期 · 暂存+签发） */
async function handleSubmit() {
  const patient = props.patient;
  if (!patient) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (hasActiveNursingLevel.value) {
    ElMessage.warning('已存在有效护理级别医嘱，请先在医嘱列表停嘱后再换级');
    return;
  }
  const encounterId = (patient.encounterId ?? '').trim();
  const patientId = (patient.patientId ?? '').trim();
  if (!encounterId || !patientId) {
    ElMessage.warning('缺少就诊或患者信息');
    return;
  }
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  const selected = selectedNursingOption.value;
  const activityDefinitionId = String(form.value.activityDefinitionId ?? '').trim();
  const rateCode =
    String(selected?.rateCode ?? '').trim() || defaultRateCode.value || firstTerminologyCode([]);
  if (!rateCode) {
    ElMessage.warning('未解析到长期医嘱频次，请稍后重试');
    return;
  }
  const unitCode = String(selected?.defaultUnitCode ?? selected?.unitName ?? '').trim() || '次';

  try {
    const res = await submitRequest.run({
      encounterId,
      patientId,
      activityDefinitionId,
      rateCode,
      unitCode,
      performOrgId: preserveApiIdForRequest(selected?.defaultPerformOrgId),
    });
    if (res.success === false) {
      ElMessage.error(String(res.message ?? '').trim() || '开立护理级别失败');
      return;
    }
    ElMessage.success(String(res.message ?? '').trim() || '护理级别已开立');
    visible.value = false;
    emit('success');
  } catch (error: unknown) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '开立护理级别失败';
    ElMessage.error(msg);
  }
}

/** 关闭弹窗 */
function handleCancel() {
  visible.value = false;
}
</script>

<template>
  <AppDialog
    v-model="visible"
    title="开立护理级别"
    width="480px"
    append-to-body
    :close-on-click-modal="false"
    destroy-on-close
  >
    <ElAlert
      v-if="hasActiveNursingLevel"
      type="warning"
      :closable="false"
      show-icon
      class="ip-nursing-advice-form__alert"
      title="已存在有效护理级别"
      description="换级须先在医嘱列表对原护理级别执行「停嘱」，再开立新的护理级别。"
    />

    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      class="ip-nursing-advice-form"
    >
      <ElFormItem label="当前级别">
        <span class="ip-nursing-advice-form__readonly">{{ currentNursingLevelLabel }}</span>
      </ElFormItem>
      <ElFormItem label="护理级别" prop="activityDefinitionId">
        <ElSelect
          v-model="form.activityDefinitionId"
          placeholder="请选择护理级别"
          filterable
          class="w-full"
          :loading="catalogRequest.loading.value"
          :disabled="dialogLoading || hasActiveNursingLevel"
        >
          <ElOption
            v-for="item in nursingOptions"
            :key="item.id"
            :label="item.name"
            :value="String(item.productDefinitionId ?? '')"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="医嘱类型">
        <span class="ip-nursing-advice-form__readonly">长期医嘱</span>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton :disabled="dialogLoading" @click="handleCancel">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitRequest.loading.value"
        :disabled="hasActiveNursingLevel"
        @click="handleSubmit"
      >
        提交
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.ip-nursing-advice-form {
  padding-top: 4px;
}

.ip-nursing-advice-form__alert {
  margin-bottom: 12px;
}

.ip-nursing-advice-form__readonly {
  color: var(--el-text-color-regular);
  line-height: 32px;
}
</style>
