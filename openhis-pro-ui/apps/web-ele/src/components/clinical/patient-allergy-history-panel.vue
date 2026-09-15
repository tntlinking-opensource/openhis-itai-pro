<script setup lang="ts">
import type { PatientAllergyHistoryRowDto } from '#/api/clinical/patient-allergy-history.types';

import { computed, ref, watch } from 'vue';

import { ElInput, ElMessage } from 'element-plus';

import {
  httpDeletePatientAllergyHistory,
  httpListPatientAllergyHistory,
  httpSavePatientAllergyHistory,
} from '#/api/clinical/patient-allergy-history.http';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'PatientAllergyHistoryPanel' });

interface Props {
  encounterId?: string;
  patientId?: string;
  /** 医生默认已确诊，住院护士默认未确诊 */
  defaultVerificationStatus?: string;
  readOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  defaultVerificationStatus: 'UNCONFIRMED',
  readOnly: false,
});

const ALLERGY_TYPE_DECLARED = 'declared';
const ALLERGY_TYPE_NKDA = 'nkda';
const ALLERGY_CATEGORY_OTHER = 'other';

const allergyText = ref('');
const loadedText = ref('');
const rows = ref<PatientAllergyHistoryRowDto[]>([]);

const canOperate = computed(() => {
  const patientId = String(props.patientId ?? '').trim();
  const encounterId = String(props.encounterId ?? '').trim();
  return Boolean(patientId && encounterId);
});

const canWrite = computed(() => canOperate.value && !props.readOnly);

const listRequest = useRequest(httpListPatientAllergyHistory, { manual: true });
const saveRequest = useRequest(httpSavePatientAllergyHistory, { manual: true });
const deleteRequest = useRequest(httpDeletePatientAllergyHistory, { manual: true });

/** 主诉与无已知过敏可改；皮试阳性不进本输入框 */
function isEditableAllergyRow(row: PatientAllergyHistoryRowDto): boolean {
  const typeCode = String(row.typeCode ?? '').trim();
  return typeCode === ALLERGY_TYPE_DECLARED || typeCode === ALLERGY_TYPE_NKDA;
}

/** 把可编辑行拼成一个文本；仅 NKDA 时回显「无」 */
function resolveEditableText(list: PatientAllergyHistoryRowDto[], nkdaFlag: boolean): string {
  const names: string[] = [];
  for (const row of list) {
    // 只拼主诉过敏原名，NKDA 没有名称
    if (!isEditableAllergyRow(row)) {
      continue;
    }
    const typeCode = String(row.typeCode ?? '').trim();
    if (typeCode === ALLERGY_TYPE_NKDA) {
      continue;
    }
    const name = String(row.allergenName ?? '').trim();
    if (name) {
      names.push(name);
    }
  }
  if (names.length > 0) {
    return names.join('、');
  }
  if (nkdaFlag) {
    return '无';
  }
  return '';
}

/** 取第一条主诉记录主键，供覆盖保存 */
function pickDeclaredRecordId(list: PatientAllergyHistoryRowDto[]): string {
  for (const row of list) {
    // 覆盖保存时复用已有主诉主键
    const typeCode = String(row.typeCode ?? '').trim();
    if (typeCode !== ALLERGY_TYPE_DECLARED) {
      continue;
    }
    return preserveApiIdForRequest(row.id) ?? '';
  }
  return '';
}

/** 按患者刷新输入框 */
async function loadHistory() {
  const patientId = preserveApiIdForRequest(props.patientId);
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!patientId || !encounterId) {
    rows.value = [];
    allergyText.value = '';
    loadedText.value = '';
    return;
  }
  const data = await listRequest.run({ patientId, encounterId });
  const list = data?.rows ?? [];
  rows.value = list;
  const text = resolveEditableText(list, data?.nkdaFlag === true);
  allergyText.value = text;
  loadedText.value = text;
}

/** 删除一条可编辑过敏史 */
async function deleteEditableRow(
  row: PatientAllergyHistoryRowDto,
  patientId: string,
  encounterId: string,
) {
  const allergyRecordId = preserveApiIdForRequest(row.id);
  if (!allergyRecordId) {
    return;
  }
  await deleteRequest.run({ allergyRecordId, patientId, encounterId });
}

/**
 * 把输入框写成一条主诉过敏史。
 * 空文本则删掉主诉/NKDA；皮试行不动。
 */
async function persist(silent = false) {
  if (!canWrite.value) {
    return;
  }
  const patientId = preserveApiIdForRequest(props.patientId);
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!patientId || !encounterId) {
    return;
  }
  const text = allergyText.value.trim();
  if (text === loadedText.value.trim()) {
    return;
  }
  // 只清主诉/NKDA，皮试阳性行仍由皮试页维护
  const editableRows = rows.value.filter((row) => isEditableAllergyRow(row));
  if (!text) {
    for (const row of editableRows) {
      await deleteEditableRow(row, patientId, encounterId);
    }
    await loadHistory();
    if (!silent) {
      ElMessage.success('过敏史已保存');
    }
    return;
  }
  const declaredId = pickDeclaredRecordId(rows.value);
  await saveRequest.run({
    id: declaredId || undefined,
    patientId,
    encounterId,
    categoryCode: ALLERGY_CATEGORY_OTHER,
    description: text,
    verificationStatusCode: props.defaultVerificationStatus,
  });
  for (const row of editableRows) {
    const rowId = preserveApiIdForRequest(row.id) ?? '';
    if (declaredId && rowId === declaredId) {
      continue;
    }
    await deleteEditableRow(row, patientId, encounterId);
  }
  await loadHistory();
  if (!silent) {
    ElMessage.success('过敏史已保存');
  }
}

watch(
  () => [props.patientId, props.encounterId] as const,
  () => {
    void loadHistory();
  },
  { immediate: true },
);

defineExpose({
  persist,
});
</script>

<template>
  <ElInput
    v-model="allergyText"
    :disabled="!canWrite"
    maxlength="255"
    clearable
    placeholder="请填写过敏史"
  />
</template>
