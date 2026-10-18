<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';
import type { AdviceProductOption } from '../advice/services/advice.types';
import type { ActiveNursingLevelAdviceSnapshot } from '../advice/services/inpatient-advice-nursing';

import { computed, ref, watch } from 'vue';

import { ElAlert, ElButton, ElForm, ElFormItem, ElMessage, ElOption, ElSelect } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  INPATIENT_NURSING_LEVEL_RATE_CODE,
  INPATIENT_NURSING_LEVEL_RATE_LABEL,
} from '../advice/services/advice-tab.constants';
import {
  loadActiveNursingLevelFromAdvice,
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
/** 打开弹窗时从医嘱列表解析的有效护理级别（停嘱后立刻退出） */
const activeNursingLevel = ref<ActiveNursingLevelAdviceSnapshot | null>(null);

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

const activeNursingRequest = useRequest(
  async () => {
    const patient = props.patient;
    if (!patient) return null;
    const encounterId = (patient.encounterId ?? '').trim();
    const patientId = (patient.patientId ?? '').trim();
    if (!encounterId || !patientId) return null;
    return loadActiveNursingLevelFromAdvice({ encounterId, patientId });
  },
  { manual: true, showErrorMessage: false },
);

const submitRequest = useRequest(saveAndSignNursingLevelAdvice, {
  manual: true,
  showErrorMessage: false,
});

const dialogLoading = computed(
  () =>
    catalogRequest.loading.value ||
    activeNursingRequest.loading.value ||
    submitRequest.loading.value,
);

/** 当前有效护理级别展示名（医嘱列表） */
const currentNursingLevelLabel = computed(() => {
  const name = String(activeNursingLevel.value?.displayName ?? '').trim();
  return name || '—';
});

/** 是否存在有效护理级别，换级须先停嘱（读医嘱列表态，不读患者队列 enrich） */
const hasActiveNursingLevel = computed(() => activeNursingLevel.value != null);

/** 选中目录行（提交 unit/执行科室；频次固定 QD） */
const selectedNursingOption = computed(() =>
  nursingOptions.value.find(
    (opt) => String(opt.productDefinitionId ?? '').trim() === form.value.activityDefinitionId,
  ),
);

/** 弹窗打开：并行加载目录 + 当前医嘱中的有效护理级别 */
watch(visible, (open) => {
  if (!open) return;
  form.value.activityDefinitionId = '';
  activeNursingLevel.value = null;
  formRef.value?.clearValidate();
  void Promise.all([catalogRequest.run(), activeNursingRequest.run()]).then(
    ([options, active]) => {
      nursingOptions.value = options ?? [];
      activeNursingLevel.value = active ?? null;
      if (nursingOptions.value.length === 0) {
        ElMessage.warning('未找到护理级别目录，请确认主数据已维护 category=26 的诊疗活动');
      }
      const currentActId = preserveApiIdForRequest(active?.activityDefinitionId);
      if (
        currentActId &&
        nursingOptions.value.some((opt) => opt.productDefinitionId === currentActId)
      ) {
        form.value.activityDefinitionId = currentActId;
      }
    },
  );
});

/** 提交开立护理级别（长期 · 频次固定 QD · 暂存+签发） */
async function handleSubmit() {
  const patient = props.patient;
  if (!patient) {
    ElMessage.warning('请先选择患者');
    return;
  }
  // 提交前再读一次医嘱列表，避免打开后刚停嘱仍被旧快照拦住或漏拦
  try {
    const encounterId = (patient.encounterId ?? '').trim();
    const patientId = (patient.patientId ?? '').trim();
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const latest = await activeNursingRequest.run();
    activeNursingLevel.value = latest ?? null;
    if (latest) {
      ElMessage.warning('已存在有效护理级别医嘱，请先在医嘱列表停嘱后再换级');
      return;
    }
  } catch {
    ElMessage.error('校验当前护理级别失败，请稍后重试');
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
  // 快捷开立固定每日一次；后端透传 rateCode，不在服务端改写
  const rateCode = INPATIENT_NURSING_LEVEL_RATE_CODE;
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
          :loading="catalogRequest.loading.value || activeNursingRequest.loading.value"
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
      <ElFormItem label="频次">
        <span class="ip-nursing-advice-form__readonly">{{
          INPATIENT_NURSING_LEVEL_RATE_LABEL
        }}</span>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton :disabled="dialogLoading" @click="handleCancel">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitRequest.loading.value"
        :disabled="hasActiveNursingLevel || activeNursingRequest.loading.value"
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
}
</style>
