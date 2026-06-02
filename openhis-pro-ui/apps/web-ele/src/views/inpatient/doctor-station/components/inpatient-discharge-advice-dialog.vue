<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { computed, reactive, ref, watch } from 'vue';

import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import { createInpatientDischargeAdvice } from '../advice/services/inpatient-advice-discharge';
import {
  loadOutWayCodeOptions,
  type OutWayCodeOption,
} from '../services/load-out-way-code-options';

defineOptions({
  name: 'InpatientDischargeAdviceDialog',
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
const outWayOptions = ref<OutWayCodeOption[]>([]);

const form = reactive({
  plannedDischargeTime: '',
  outWayCode: '',
  reasonText: '',
});

const rules: FormRules = {
  outWayCode: [{ required: true, message: '请选择出院方式', trigger: 'change' }],
};

const optionsRequest = useRequest(loadOutWayCodeOptions, { manual: true });
const submitRequest = useRequest(createInpatientDischargeAdvice, {
  manual: true,
  showErrorMessage: false,
});

const dialogLoading = computed(() => optionsRequest.loading.value || submitRequest.loading.value);

/** 原科室展示文案 */
const sourceOrgDisplay = computed(() => (props.patient?.organizationName ?? '').trim() || '—');

/** 原病区/床位展示文案 */
const sourceLocationDisplay = computed(() => {
  const ward = (props.patient?.wardName ?? '').trim();
  const bed = (props.patient?.bedName ?? '').trim();
  const merged = [ward, bed].filter(Boolean).join(' ');
  return merged || '—';
});

/** 弹窗打开时重置表单并加载出院方式字典 */
watch(visible, (open) => {
  if (!open) {
    return;
  }
  form.plannedDischargeTime = dayjs().format('YYYY-MM-DD HH:mm:ss');
  form.outWayCode = '';
  form.reasonText = '';
  formRef.value?.clearValidate();
  void optionsRequest.run().then((rows) => {
    outWayOptions.value = rows ?? [];
  });
});

/** 解析原位置 id：优先床位，否则病区 */
function resolveSourceLocationId(): string | undefined {
  const bedId = preserveApiIdForRequest(props.patient?.bedId);
  if (bedId) {
    return bedId;
  }
  return preserveApiIdForRequest(props.patient?.wardId);
}

/** 提交开立出院医嘱 */
async function handleSubmit() {
  const patient = props.patient;
  if (!patient) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const encounterId = (patient.encounterId ?? '').trim();
  const patientId = (patient.patientId ?? '').trim();
  if (!encounterId || !patientId) {
    ElMessage.warning('缺少就诊或患者信息');
    return;
  }
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  try {
    const res = await submitRequest.run({
      encounterId,
      patientId,
      outWayCode: form.outWayCode.trim(),
      plannedDischargeTime: form.plannedDischargeTime.trim() || undefined,
      reasonText: form.reasonText.trim() || undefined,
      sourceOrgId: preserveApiIdForRequest(patient.organizationId),
      sourceLocationId: resolveSourceLocationId(),
    });
    if (res.success === false) {
      ElMessage.error(String(res.message || '').trim() || '开立出院医嘱失败');
      return;
    }
    ElMessage.success(String(res.message || '').trim() || '出院医嘱已开立');
    visible.value = false;
    emit('success');
  } catch (error: unknown) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '开立出院医嘱失败';
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
    title="开立出院医嘱"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    destroy-on-close
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="108px"
      class="ip-discharge-advice-form"
    >
      <ElFormItem label="计划出院时间">
        <ElDatePicker
          v-model="form.plannedDischargeTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="默认当前时间"
          class="w-full"
          :disabled="dialogLoading"
        />
      </ElFormItem>
      <ElFormItem label="出院方式" prop="outWayCode">
        <ElSelect
          v-model="form.outWayCode"
          placeholder="请选择出院方式"
          filterable
          class="w-full"
          :loading="optionsRequest.loading.value"
          :disabled="dialogLoading"
        >
          <ElOption
            v-for="item in outWayOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="原科室">
        <span class="ip-discharge-advice-form__readonly">{{ sourceOrgDisplay }}</span>
      </ElFormItem>
      <ElFormItem label="原病区/床位">
        <span class="ip-discharge-advice-form__readonly">{{ sourceLocationDisplay }}</span>
      </ElFormItem>
      <ElFormItem label="出院原因">
        <ElInput
          v-model="form.reasonText"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="选填"
          :disabled="dialogLoading"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton :disabled="dialogLoading" @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :loading="submitRequest.loading.value" @click="handleSubmit">
        提交
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.ip-discharge-advice-form {
  padding-top: 4px;
}

.ip-discharge-advice-form__readonly {
  color: var(--el-text-color-regular);
  line-height: 32px;
}
</style>
