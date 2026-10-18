<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { NurseStationOrderExecuteTableRow } from '../services/map-nurse-station-order-execute';
import type { SurgeryAnesthExecuteDialogDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-surgery-anesth-execute.types';

import { computed, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  httpLoadSurgeryAnesthExecuteDialog,
  httpSubmitSurgeryAnesthExecute,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-surgery-anesth-execute.http';
import AppDialog from '#/components/app-dialog.vue';
import { useUserStore } from '@zxplat/stores';

import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  SERVICE_CATEGORY_ANESTHESIA,
  SERVICE_CATEGORY_PROCEDURE,
} from '#/utils/print/constants/service-request-category-codes';

defineOptions({ name: 'SurgeryAnesthExecuteDialog' });

interface Props {
  modelValue?: boolean;
  patient?: NurseStationModulePatient | null;
  row?: NurseStationOrderExecuteTableRow | null;
  /** 计划槽位时刻 YYYY-MM-DD HH:mm:ss */
  planTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  patient: null,
  row: null,
  planTime: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  success: [];
}>();

const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const dialog = ref<SurgeryAnesthExecuteDialogDto | null>(null);

const procedureTime = ref('');
const specimen = ref('');
const note = ref('');
const nurseId = ref('');
const anesthesiaStartTime = ref('');
const anesthesiaEndTime = ref('');

const isSurgery = computed(() => {
  const cat = String(dialog.value?.categoryEnum ?? props.row?.categoryEnum ?? '').trim();
  return cat === SERVICE_CATEGORY_PROCEDURE;
});

const isAnesthesia = computed(() => {
  const cat = String(dialog.value?.categoryEnum ?? props.row?.categoryEnum ?? '').trim();
  return cat === SERVICE_CATEGORY_ANESTHESIA;
});

const dialogTitle = computed(() => (isAnesthesia.value ? '麻醉执行' : '手术执行'));

const activityName = computed(() => {
  return (
    String(dialog.value?.activityName ?? '').trim() ||
    String(props.row?.content ?? '').trim() ||
    '—'
  );
});

/** 配合护士下拉：申请带回 + 当前登录人 */
const nurseOptions = computed(() => {
  const options: Array<{ label: string; value: string }> = [];
  const fromDialogId = preserveApiIdForRequest(dialog.value?.nurseId) ?? '';
  const fromDialogName = String(dialog.value?.nurseName ?? '').trim();
  if (fromDialogId) {
    options.push({ value: fromDialogId, label: fromDialogName || fromDialogId });
  }
  const loginId =
    resolveLoginPractitionerIdForRequest(
      userStore.userInfo as Record<string, unknown> | null,
    ) ?? '';
  const loginName = String(
    (userStore.userInfo as { realName?: string; nickname?: string } | null)?.realName ??
      (userStore.userInfo as { nickname?: string } | null)?.nickname ??
      '',
  ).trim();
  if (loginId && !options.some((item) => item.value === loginId)) {
    options.push({ value: loginId, label: loginName || '当前登录人' });
  }
  return options;
});

/** 关闭弹窗 */
function closeDialog() {
  emit('update:modelValue', false);
}

/** 装载弹窗横表 */
async function loadDialog() {
  const patient = props.patient;
  const row = props.row;
  const planTime = String(props.planTime ?? '').trim();
  if (!patient || !row || !planTime) {
    dialog.value = null;
    return;
  }
  loading.value = true;
  try {
    const res = await httpLoadSurgeryAnesthExecuteDialog({
      encounterId: preserveApiIdForRequest(patient.id) ?? '',
      serviceRequestId: preserveApiIdForRequest(row.orderId) ?? '',
      planTime,
    });
    dialog.value = res;
    procedureTime.value = String(res.procedureTime ?? '').trim();
    specimen.value = String(res.specimen ?? '').trim();
    note.value = String(res.note ?? '').trim();
    nurseId.value =
      preserveApiIdForRequest(res.nurseId) ||
      resolveLoginPractitionerIdForRequest(
        userStore.userInfo as Record<string, unknown> | null,
      ) ||
      '';
    anesthesiaStartTime.value = String(res.anesthesiaStartTime ?? '').trim();
    anesthesiaEndTime.value = String(res.anesthesiaEndTime ?? '').trim();
  } finally {
    loading.value = false;
  }
}

/** 提交执行 */
async function submitExecute() {
  const patient = props.patient;
  const row = props.row;
  const planTime = String(props.planTime ?? '').trim();
  if (!patient || !row || !planTime) {
    return;
  }
  const accountId = preserveApiIdForRequest(patient.accountId) ?? '';
  if (!accountId) {
    ElMessage.warning('患者账户信息缺失，无法执行');
    return;
  }
  if (isAnesthesia.value) {
    const start = String(anesthesiaStartTime.value ?? '').trim();
    const end = String(anesthesiaEndTime.value ?? '').trim();
    if (start && end && end < start) {
      ElMessage.warning('麻醉结束时间不得早于开始时间');
      return;
    }
  }
  saving.value = true;
  try {
    const res = await httpSubmitSurgeryAnesthExecute({
      encounterId: preserveApiIdForRequest(patient.id) ?? '',
      serviceRequestId: preserveApiIdForRequest(row.orderId) ?? '',
      planTime,
      actualTime: planTime,
      accountId,
      procedureTime: isSurgery.value ? procedureTime.value || undefined : undefined,
      specimen: isSurgery.value ? specimen.value || undefined : undefined,
      note: isSurgery.value ? note.value || undefined : undefined,
      nurseId: nurseId.value || undefined,
      anesthesiaStartTime: isAnesthesia.value
        ? anesthesiaStartTime.value || undefined
        : undefined,
      anesthesiaEndTime: isAnesthesia.value ? anesthesiaEndTime.value || undefined : undefined,
      bindItems: row.bindItems?.length
        ? row.bindItems.map((bind) => ({
            ...bind,
            bindingId: preserveApiIdForRequest(bind.bindingId),
            activityId: preserveApiIdForRequest(bind.activityId),
            deviceId: preserveApiIdForRequest(bind.deviceId),
            chargeItemDefinitionId: preserveApiIdForRequest(bind.chargeItemDefinitionId),
          }))
        : undefined,
    });
    const warnings = res.warnings ?? [];
    if (warnings.length) {
      ElMessage.warning(warnings.join('；'));
    } else {
      ElMessage.success(isAnesthesia.value ? '麻醉已执行' : '手术已执行');
    }
    emit('success');
    closeDialog();
  } finally {
    saving.value = false;
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      dialog.value = null;
      return;
    }
    void loadDialog();
  },
);
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="sae-exec">
      <div class="sae-exec__title">{{ activityName }}</div>
      <ElForm label-position="top" class="sae-exec__form">
        <div class="sae-exec__grid">
          <ElFormItem label="计划执行时间">
            <ElInput :model-value="planTime" disabled />
          </ElFormItem>
          <ElFormItem v-if="isSurgery" label="手术时间">
            <ElDatePicker
              v-model="procedureTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              class="!w-full"
              placeholder="选择手术时间"
            />
          </ElFormItem>
          <ElFormItem v-if="isAnesthesia" label="麻醉开始">
            <ElDatePicker
              v-model="anesthesiaStartTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              class="!w-full"
              placeholder="选择开始时间"
            />
          </ElFormItem>
          <ElFormItem v-if="isAnesthesia" label="麻醉结束">
            <ElDatePicker
              v-model="anesthesiaEndTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              class="!w-full"
              placeholder="选择结束时间"
            />
          </ElFormItem>
          <ElFormItem label="配合护士">
            <ElSelect v-model="nurseId" clearable filterable class="w-full" placeholder="请选择">
              <ElOption
                v-for="opt in nurseOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="isSurgery" label="样本">
            <ElInput v-model="specimen" clearable placeholder="可选" />
          </ElFormItem>
        </div>

        <div class="sae-exec__readonly">
          <div v-if="isSurgery" class="sae-exec__readonly-row">
            <span>主刀：{{ dialog?.surgeonName || '—' }}</span>
            <span>一助：{{ dialog?.firstAssistantName || '—' }}</span>
            <span>二助：{{ dialog?.secondAssistantName || '—' }}</span>
          </div>
          <div v-if="isAnesthesia" class="sae-exec__readonly-row">
            <span>麻醉医师：{{ dialog?.anesthetistName || '—' }}</span>
            <span>麻醉方法：{{ dialog?.anesthesiaMethodCode || '—' }}</span>
          </div>
        </div>

        <ElFormItem v-if="isSurgery" label="备注">
          <ElInput v-model="note" type="textarea" :rows="2" placeholder="可选" />
        </ElFormItem>
      </ElForm>
    </div>
    <template #footer>
      <ElButton @click="closeDialog">取消</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="loading" @click="submitExecute">
        确认执行
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.sae-exec {
  min-height: 160px;
}

.sae-exec__title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sae-exec__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.sae-exec__form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.sae-exec__readonly {
  margin: 8px 0 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.sae-exec__readonly-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.sae-exec__readonly-row + .sae-exec__readonly-row {
  margin-top: 6px;
}
</style>
