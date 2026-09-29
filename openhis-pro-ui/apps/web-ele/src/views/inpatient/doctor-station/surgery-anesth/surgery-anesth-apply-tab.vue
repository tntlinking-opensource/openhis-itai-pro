<script setup lang="ts">
import type {
  AnesthesiaApplyFormHeaderDto,
  AnesthesiaApplyFormLineRequest,
  PlannedPerformerDto,
  PlannedSurgeryFieldsDto,
  SurgeryAnesthFormKind,
  SurgeryApplyFormHeaderDto,
  SurgeryApplyFormLineRequest,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-surgery-anesth.types';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectTable from '#/components/select-table.vue';

import {
  httpGetAnesthesiaApplyFormDetail,
  httpGetSurgeryApplyFormDetail,
  httpInitSurgeryAnesth,
  httpListAnesthesiaApplyForms,
  httpListSurgeryApplyForms,
  httpSaveAnesthesiaApplyForm,
  httpSaveSurgeryApplyForm,
  httpSearchClinicalProcV3,
  httpSearchSurchargeActivityDefinition,
  httpSignAnesthesiaApplyForm,
  httpSignSurgeryApplyForm,
  httpToggleAnesthesiaNest,
  httpWithdrawAnesthesiaApplyForm,
  httpWithdrawSurgeryApplyForm,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-surgery-anesth.http';
import { httpListPractitioners } from '#/api/outpatient-registration';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'InpatientSurgeryAnesthApplyTab' });

interface Props {
  encounterId?: string;
  patientId?: string;
  /** SURGERY 手术申请；ANESTHESIA 麻醉申请 */
  formKind?: SurgeryAnesthFormKind;
  writeDisabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  formKind: 'SURGERY',
  writeDisabled: false,
});

const STATUS_DRAFT = 'DRAFT';
const STATUS_ACTIVE = 'ACTIVE';
const ROLE_SURGEON = 'SURGEON';
const ROLE_FIRST = 'FIRST_ASSISTANT';
const ROLE_SECOND = 'SECOND_ASSISTANT';
const ROLE_NURSE = 'NURSE';
const ROLE_ANESTHETIST = 'ANESTHETIST';

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '待签发',
  ACTIVE: '已发送',
  MIXED: '状态不一致',
};

const loading = ref(false);
const saving = ref(false);
const levelOptions = ref<ApiOptionItem[]>([]);
const incisionOptions = ref<ApiOptionItem[]>([]);
const methodOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const surgeryHeaders = ref<SurgeryApplyFormHeaderDto[]>([]);
const anesthesiaHeaders = ref<AnesthesiaApplyFormHeaderDto[]>([]);
const activePrescriptionNo = ref('');
const headerStatus = ref('');
const nestedAnesthesiaNo = ref('');
const linkedSurgeryNo = ref('');
const nestChecked = ref(false);
const procedureTime = ref('');
const procedureLevelCode = ref('');
const incisionTypeCode = ref('');
const anesthesiaMethodCode = ref('');
const plannedNote = ref('');
const surgeonId = ref('');
const firstAssistantId = ref('');
const secondAssistantId = ref('');
const nurseId = ref('');
const anesthetistId = ref('');
const surgeryLines = ref<SurgeryApplyFormLineRequest[]>([]);
const anesthesiaLines = ref<AnesthesiaApplyFormLineRequest[]>([]);
/** main 选手术/麻醉项目；extra 选另外收费的项目 */
const pickKind = ref<'extra' | 'main'>('main');
/** SelectTable 选中后立刻入明细并清空，不回写长期值 */
const pickSelectValue = ref('');
const pickColumns = [
  { prop: 'name', label: '名称', minWidth: 220 },
  { prop: 'extra', label: '说明', minWidth: 140 },
];

const isSurgery = computed(() => props.formKind === 'SURGERY');
const encounterReady = computed(() => Boolean(props.encounterId?.trim()));
const editable = computed(
  () => !props.writeDisabled && (!headerStatus.value || headerStatus.value === STATUS_DRAFT),
);
const canSign = computed(() => editable.value && Boolean(activePrescriptionNo.value));
const canWithdraw = computed(
  () =>
    !props.writeDisabled &&
    headerStatus.value === STATUS_ACTIVE &&
    Boolean(activePrescriptionNo.value),
);
const activeLines = computed(() => (isSurgery.value ? surgeryLines.value : anesthesiaLines.value));

/** 左侧队列卡片：主术式或麻醉方法作标题，单号和时间作辅文 */
const queueCards = computed(() => {
  if (isSurgery.value) {
    return surgeryHeaders.value.map((item) => ({
      prescriptionNo: item.prescriptionNo ?? '',
      statusEnum: item.statusEnum,
      title: item.mainProcedureName || '未填术式',
      meta: item.prescriptionNo || '—',
      extra: formatQueueTime(item.plannedProcedureTime),
      count: item.lineCount,
    }));
  }
  return anesthesiaHeaders.value.map((item) => ({
    prescriptionNo: item.prescriptionNo ?? '',
    statusEnum: item.statusEnum,
    title: optionLabel(methodOptions.value, item.anesthesiaMethodCode) || '麻醉申请',
    meta: item.prescriptionNo || '—',
    extra: formatLinkedSurgeryExtra(item.linkedSurgeryPrescriptionNo),
    count: item.lineCount,
  }));
});

/** 麻醉侧下拉：关联手术展示「术式名 + 申请号」；已关联但不在列表里时补一条 */
const linkedSurgeryOptions = computed(() => {
  const options = surgeryHeaders.value
    .filter((item) => String(item.prescriptionNo ?? '').trim())
    .map((item) => ({
      value: String(item.prescriptionNo).trim(),
      label: formatLinkedSurgeryLabel(item),
    }));
  const current = linkedSurgeryNo.value.trim();
  if (current && !options.some((opt) => opt.value === current)) {
    options.unshift({ value: current, label: current });
  }
  return options;
});

/** 关联手术展示文案：术式名 + 申请号 */
function formatLinkedSurgeryLabel(item: SurgeryApplyFormHeaderDto) {
  const name = String(item.mainProcedureName ?? '').trim() || '未填术式';
  const no = String(item.prescriptionNo ?? '').trim();
  return no ? `${name} ${no}` : name;
}

/** 队列辅文：用术式名+单号回显关联手术 */
function formatLinkedSurgeryExtra(linkedNo?: string) {
  const no = String(linkedNo ?? '').trim();
  if (!no) {
    return '';
  }
  const hit = surgeryHeaders.value.find((item) => String(item.prescriptionNo ?? '').trim() === no);
  if (hit) {
    return `关联 ${formatLinkedSurgeryLabel(hit)}`;
  }
  return `关联 ${no}`;
}

/** 队列时间只留月日时分 */
function formatQueueTime(raw?: string) {
  const text = String(raw ?? '').trim();
  if (text.length >= 16) {
    return text.slice(5, 16);
  }
  return text;
}

/** 下拉选项 code 对展示名 */
function optionLabel(options: ApiOptionItem[], code?: string) {
  const key = String(code ?? '').trim();
  if (!key) {
    return '';
  }
  return options.find((item) => String(item.value) === key)?.label ?? '';
}

/** 医务人员主键对姓名 */
function practitionerName(id?: string) {
  const key = String(id ?? '').trim();
  if (!key) {
    return '';
  }
  return practitionerOptions.value.find((item) => String(item.value) === key)?.label ?? '';
}

/** 左侧队列状态徽章 class */
function queueStatusClass(code?: string) {
  if (code === STATUS_ACTIVE) {
    return 'queue-status--ok';
  }
  if (code === STATUS_DRAFT) {
    return 'queue-status--draft';
  }
  return 'queue-status--other';
}

/** 行种类码转中文 */
function lineKindText(code?: string) {
  if (code === 'PROCEDURE') {
    return '手术';
  }
  if (code === 'SURCHARGE') {
    return '附加';
  }
  if (code === 'ANESTHESIA') {
    return '麻醉';
  }
  return code || '—';
}

/** 状态码转展示文案 */
function statusText(code?: string) {
  const key = String(code ?? '').trim();
  return STATUS_LABEL[key] ?? (key || '—');
}

/** 签发接口只校验非空，本地生成占位码 */
function buildSignCode() {
  return `SA-${Date.now()}`;
}

/** 从横表人员列表取出某角色的医务人员主键 */
function performerId(list: PlannedPerformerDto[] | undefined, role: string) {
  const hit = (list ?? []).find((item) => item.roleCode === role);
  return preserveApiIdForRequest(hit?.practitionerId) ?? '';
}

/** 组装拟手术/拟麻醉横表 */
function buildPlanned(): PlannedSurgeryFieldsDto {
  const performers: PlannedPerformerDto[] = [];
  const push = (roleCode: string, raw?: string) => {
    const practitionerId = preserveApiIdForRequest(raw);
    if (practitionerId) {
      performers.push({ roleCode, practitionerId });
    }
  };
  if (isSurgery.value) {
    push(ROLE_SURGEON, surgeonId.value);
    push(ROLE_FIRST, firstAssistantId.value);
    push(ROLE_SECOND, secondAssistantId.value);
    push(ROLE_NURSE, nurseId.value);
  }
  push(ROLE_ANESTHETIST, anesthetistId.value);
  return {
    procedureTime: procedureTime.value || undefined,
    procedureLevelCode: procedureLevelCode.value || undefined,
    incisionTypeCode: incisionTypeCode.value || undefined,
    anesthesiaMethodCode: anesthesiaMethodCode.value || undefined,
    note: plannedNote.value || undefined,
    performers,
  };
}

/** 清空右侧编辑区，准备新建 */
function resetEditor() {
  activePrescriptionNo.value = '';
  headerStatus.value = '';
  nestedAnesthesiaNo.value = '';
  linkedSurgeryNo.value = '';
  nestChecked.value = false;
  procedureTime.value = '';
  procedureLevelCode.value = '';
  incisionTypeCode.value = '';
  anesthesiaMethodCode.value = '';
  plannedNote.value = '';
  surgeonId.value = '';
  firstAssistantId.value = '';
  secondAssistantId.value = '';
  nurseId.value = '';
  anesthetistId.value = '';
  surgeryLines.value = [];
  anesthesiaLines.value = [];
  pickSelectValue.value = '';
  pickKind.value = 'main';
}

/** 加载下拉与当前页签申请列表 */
async function reload() {
  if (!encounterReady.value) {
    surgeryHeaders.value = [];
    anesthesiaHeaders.value = [];
    resetEditor();
    return;
  }
  loading.value = true;
  try {
    const encounterId = props.encounterId.trim();
    const needSurgeryLinkList = !isSurgery.value;
    const [initRes, listRes, surgeryLinkRes, practitionerRes] = await Promise.all([
      httpInitSurgeryAnesth({ encounterId }),
      isSurgery.value
        ? httpListSurgeryApplyForms({ encounterId })
        : httpListAnesthesiaApplyForms({ encounterId }),
      needSurgeryLinkList ? httpListSurgeryApplyForms({ encounterId }) : Promise.resolve(null),
      practitionerOptions.value.length > 0 ? Promise.resolve(null) : httpListPractitioners({}),
    ]);
    levelOptions.value = initRes.procedureLevelOptions ?? [];
    incisionOptions.value = initRes.incisionTypeOptions ?? [];
    methodOptions.value = initRes.anesthesiaMethodOptions ?? [];
    if (practitionerRes) {
      practitionerOptions.value = practitionerRes.practitionerOptions ?? [];
    }
    if (isSurgery.value && listRes && 'forms' in listRes) {
      surgeryHeaders.value = (listRes.forms ?? []) as SurgeryApplyFormHeaderDto[];
    }
    if (!isSurgery.value && listRes && 'forms' in listRes) {
      anesthesiaHeaders.value = (listRes.forms ?? []) as AnesthesiaApplyFormHeaderDto[];
    }
    if (surgeryLinkRes && 'forms' in surgeryLinkRes) {
      surgeryHeaders.value = (surgeryLinkRes.forms ?? []) as SurgeryApplyFormHeaderDto[];
    }
  } finally {
    loading.value = false;
  }
}

/** 打开一张已有申请单 */
async function openForm(prescriptionNo: string) {
  const no = prescriptionNo.trim();
  const encounterId = props.encounterId.trim();
  if (!encounterId || !no) {
    return;
  }
  loading.value = true;
  try {
    if (isSurgery.value) {
      const detail = await httpGetSurgeryApplyFormDetail({ encounterId, prescriptionNo: no });
      activePrescriptionNo.value = detail.header?.prescriptionNo ?? no;
      headerStatus.value = String(detail.header?.statusEnum ?? '');
      nestedAnesthesiaNo.value = detail.header?.nestedAnesthesiaPrescriptionNo ?? '';
      nestChecked.value = Boolean(nestedAnesthesiaNo.value);
      applyPlanned(detail.planned);
      surgeryLines.value = (detail.lines ?? []).map((line, index) => ({
        lineKindCode: line.lineKindCode === 'SURCHARGE' ? 'SURCHARGE' : 'PROCEDURE',
        procedureDefinitionId: preserveApiIdForRequest(line.procedureDefinitionId),
        activityDefinitionId: preserveApiIdForRequest(line.activityDefinitionId) ?? '',
        quantity: Number(line.quantity ?? 1) || 1,
        unitCode: line.unitCode || '次',
        sortNumber: line.sortNumber ?? index + 1,
        mainProcedureFlag: line.mainProcedureFlag === true,
        displayName: line.procedureName || line.activityName || '',
      }));
      return;
    }
    const detail = await httpGetAnesthesiaApplyFormDetail({ encounterId, prescriptionNo: no });
    activePrescriptionNo.value = detail.header?.prescriptionNo ?? no;
    headerStatus.value = String(detail.header?.statusEnum ?? '');
    linkedSurgeryNo.value = detail.header?.linkedSurgeryPrescriptionNo ?? '';
    applyPlanned(detail.planned);
    if (!anesthesiaMethodCode.value && detail.header?.anesthesiaMethodCode) {
      anesthesiaMethodCode.value = detail.header.anesthesiaMethodCode;
    }
    anesthesiaLines.value = (detail.lines ?? []).map((line, index) => ({
      lineKindCode: line.lineKindCode === 'SURCHARGE' ? 'SURCHARGE' : 'ANESTHESIA',
      activityDefinitionId: preserveApiIdForRequest(line.activityDefinitionId) ?? '',
      quantity: Number(line.quantity ?? 1) || 1,
      unitCode: line.unitCode || '次',
      sortNumber: line.sortNumber ?? index + 1,
      displayName: line.activityName || '',
    }));
  } finally {
    loading.value = false;
  }
}

/** 回填横表与人员 */
function applyPlanned(planned?: PlannedSurgeryFieldsDto) {
  procedureTime.value = planned?.procedureTime ?? '';
  procedureLevelCode.value = planned?.procedureLevelCode ?? '';
  incisionTypeCode.value = planned?.incisionTypeCode ?? '';
  anesthesiaMethodCode.value = planned?.anesthesiaMethodCode ?? '';
  plannedNote.value = planned?.note ?? '';
  const performers = planned?.performers;
  surgeonId.value = performerId(performers, ROLE_SURGEON);
  firstAssistantId.value = performerId(performers, ROLE_FIRST);
  secondAssistantId.value = performerId(performers, ROLE_SECOND);
  nurseId.value = performerId(performers, ROLE_NURSE);
  anesthetistId.value = performerId(performers, ROLE_ANESTHETIST);
}

/** 搜索框提示：手术名称、麻醉项目，或另外收费的项目 */
const pickPlaceholder = computed(() => {
  if (pickKind.value === 'extra') {
    return '输入要另外收费的项目名称';
  }
  return isSurgery.value ? '输入手术名称、编码或拼音' : '输入麻醉项目名称';
});

/** 换了要添加的种类后清空选品，避免手术和附加项目串在一起 */
function onPickKindChange() {
  pickSelectValue.value = '';
}

type PickHit = {
  id: string;
  name: string;
  extra?: string;
  unitCode?: string;
  procedureDefinitionId?: string;
  activityDefinitionId?: string;
};

/** SelectTable 远程检索：手术主项 / 附加收费项 */
async function queryPickOptions(params: { keyword?: string; pageNo: number; pageSize: number }) {
  const text = String(params.keyword ?? '').trim();
  if (!text) {
    return { content: [] as PickHit[], totalCount: 0 };
  }
  if (isSurgery.value && pickKind.value === 'main') {
    const res = await httpSearchClinicalProcV3({ keyword: text });
    const content: PickHit[] = (res.items ?? []).map((item) => ({
      id: preserveApiIdForRequest(item.id) ?? '',
      name: `${item.procedureCode ?? ''} ${item.name ?? ''}`.trim(),
      extra: item.activityName,
      procedureDefinitionId: preserveApiIdForRequest(item.id),
      activityDefinitionId: preserveApiIdForRequest(item.activityDefinitionId),
    }));
    return { content, totalCount: content.length };
  }
  const res = await httpSearchSurchargeActivityDefinition({
    keyword: text,
    sideCode: isSurgery.value ? 'SURGERY' : 'ANESTHESIA',
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  });
  const content: PickHit[] = (res.items ?? []).map((item) => ({
    id: preserveApiIdForRequest(item.activityDefinitionId) ?? '',
    name: item.name ?? '',
    extra: item.busNo,
    unitCode: item.unitCode,
    activityDefinitionId: preserveApiIdForRequest(item.activityDefinitionId),
  }));
  return { content, totalCount: res.totalCount ?? content.length };
}

/** 点选品行加入明细；返回 true 关闭下拉 */
async function onPickBeforeClose(item: Record<string, unknown>) {
  addHit(
    {
      id: String(item.id ?? ''),
      name: String(item.name ?? ''),
      extra: item.extra != null ? String(item.extra) : undefined,
      unitCode: item.unitCode != null ? String(item.unitCode) : undefined,
      procedureDefinitionId:
        item.procedureDefinitionId != null ? String(item.procedureDefinitionId) : undefined,
      activityDefinitionId:
        item.activityDefinitionId != null ? String(item.activityDefinitionId) : undefined,
    },
    pickKind.value === 'extra',
  );
  return true;
}

/** SelectTable 会回写选中值，选完立刻清空以继续加项 */
async function onPickChange() {
  await nextTick();
  pickSelectValue.value = '';
}

/** 把检索命中追加为申请行 */
function addHit(hit: PickHit, asSurcharge: boolean) {
  if (isSurgery.value && !asSurcharge) {
    if (!hit.activityDefinitionId) {
      ElMessage.warning('这项手术还没对应收费项目，不能开');
      return;
    }
    const hasMain = surgeryLines.value.some((line) => line.mainProcedureFlag);
    surgeryLines.value.push({
      lineKindCode: 'PROCEDURE',
      procedureDefinitionId: hit.procedureDefinitionId,
      activityDefinitionId: hit.activityDefinitionId,
      quantity: 1,
      unitCode: '次',
      sortNumber: surgeryLines.value.length + 1,
      mainProcedureFlag: !hasMain,
      displayName: hit.name,
    });
    return;
  }
  if (!hit.activityDefinitionId) {
    return;
  }
  if (isSurgery.value) {
    surgeryLines.value.push({
      lineKindCode: 'SURCHARGE',
      activityDefinitionId: hit.activityDefinitionId,
      quantity: 1,
      unitCode: hit.unitCode || '次',
      sortNumber: surgeryLines.value.length + 1,
      displayName: hit.name,
    });
    return;
  }
  anesthesiaLines.value.push({
    lineKindCode: asSurcharge ? 'SURCHARGE' : 'ANESTHESIA',
    activityDefinitionId: hit.activityDefinitionId,
    quantity: 1,
    unitCode: hit.unitCode || '次',
    sortNumber: anesthesiaLines.value.length + 1,
    displayName: hit.name,
  });
}

/** 同一张手术单只保留一行主手术 */
function markMain(index: number) {
  surgeryLines.value = surgeryLines.value.map((line, i) => ({
    ...line,
    mainProcedureFlag: i === index && line.lineKindCode === 'PROCEDURE',
  }));
}

/** 删掉编辑中的一行 */
function removeLine(index: number) {
  if (isSurgery.value) {
    surgeryLines.value.splice(index, 1);
    return;
  }
  anesthesiaLines.value.splice(index, 1);
}

/** 保存草稿。已有单号时带旧号，成功后换成返回的新号 */
async function saveForm() {
  if (!editable.value || !encounterReady.value) {
    return;
  }
  const encounterId = props.encounterId.trim();
  saving.value = true;
  try {
    if (isSurgery.value) {
      const lines = surgeryLines.value
        .filter((line) => line.activityDefinitionId)
        .map((line, index) => ({
          lineKindCode: line.lineKindCode,
          procedureDefinitionId: line.procedureDefinitionId,
          activityDefinitionId: line.activityDefinitionId,
          quantity: line.quantity,
          unitCode: line.unitCode,
          sortNumber: index + 1,
          mainProcedureFlag: line.mainProcedureFlag,
        }));
      if (!lines.some((line) => line.lineKindCode === 'PROCEDURE')) {
        ElMessage.warning('请先选择至少一项手术');
        return;
      }
      const res = await httpSaveSurgeryApplyForm({
        encounterId,
        previousPrescriptionNo: activePrescriptionNo.value || undefined,
        lines,
        planned: buildPlanned(),
        nestAnesthesia: nestChecked.value && !nestedAnesthesiaNo.value ? true : undefined,
      });
      const nextNo = res.prescriptionNo ?? '';
      if (res.nestedAnesthesiaPrescriptionNo) {
        nestedAnesthesiaNo.value = res.nestedAnesthesiaPrescriptionNo;
      }
      ElMessage.success('手术申请已保存');
      await reload();
      if (nextNo) {
        await openForm(nextNo);
      }
      return;
    }
    const lines = anesthesiaLines.value
      .filter((line) => line.activityDefinitionId)
      .map((line, index) => ({
        lineKindCode: line.lineKindCode,
        activityDefinitionId: line.activityDefinitionId,
        quantity: line.quantity,
        unitCode: line.unitCode,
        sortNumber: index + 1,
      }));
    if (lines.length === 0) {
      ElMessage.warning('麻醉申请至少需要一行');
      return;
    }
    const res = await httpSaveAnesthesiaApplyForm({
      encounterId,
      previousPrescriptionNo: activePrescriptionNo.value || undefined,
      linkedSurgeryPrescriptionNo: linkedSurgeryNo.value.trim() || undefined,
      lines,
      planned: buildPlanned(),
    });
    ElMessage.success('麻醉申请已保存');
    await reload();
    if (res.prescriptionNo) {
      await openForm(res.prescriptionNo);
    }
  } finally {
    saving.value = false;
  }
}

/** 整单签发 */
async function signForm() {
  if (!canSign.value) {
    return;
  }
  const encounterId = props.encounterId.trim();
  const body = {
    encounterId,
    prescriptionNo: activePrescriptionNo.value,
    signCode: buildSignCode(),
  };
  saving.value = true;
  try {
    await (isSurgery.value ? httpSignSurgeryApplyForm(body) : httpSignAnesthesiaApplyForm(body));
    ElMessage.success('已签发');
    await reload();
    await openForm(body.prescriptionNo);
  } finally {
    saving.value = false;
  }
}

/** 撤回已发送且未执行的申请 */
async function withdrawForm() {
  if (!canWithdraw.value) {
    return;
  }
  try {
    await ElMessageBox.confirm('撤回后申请回到待签发，已执行的不能撤回。', '撤回申请', {
      type: 'warning',
      confirmButtonText: '撤回',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  const encounterId = props.encounterId.trim();
  const prescriptionNo = activePrescriptionNo.value;
  saving.value = true;
  try {
    await (isSurgery.value
      ? httpWithdrawSurgeryApplyForm({ encounterId, prescriptionNo })
      : httpWithdrawAnesthesiaApplyForm({ encounterId, prescriptionNo }));
    ElMessage.success('已撤回');
    await reload();
    await openForm(prescriptionNo);
  } finally {
    saving.value = false;
  }
}

/** 已保存手术单补建或取消嵌套麻醉 */
async function toggleNest() {
  if (!isSurgery.value || !activePrescriptionNo.value || props.writeDisabled) {
    return;
  }
  const encounterId = props.encounterId.trim();
  saving.value = true;
  try {
    const res = await httpToggleAnesthesiaNest({
      encounterId,
      surgeryPrescriptionNo: activePrescriptionNo.value,
      enabled: !nestedAnesthesiaNo.value,
    });
    nestedAnesthesiaNo.value = res.anesthesiaPrescriptionNo ?? '';
    nestChecked.value = Boolean(nestedAnesthesiaNo.value);
    ElMessage.success(nestedAnesthesiaNo.value ? '已生成麻醉申请' : '已取消嵌套麻醉');
    await reload();
  } finally {
    saving.value = false;
  }
}

watch(
  () => [props.encounterId, props.formKind] as const,
  () => {
    resetEditor();
    void reload();
  },
  { immediate: true },
);

defineExpose({ reload });
</script>

<template>
  <StationTriPaneLayout
    class="h-full min-h-0"
    :show-right="false"
    left-track="280px"
    left-track-lg="280px"
    center-track="minmax(0, 1fr)"
  >
    <template #left>
      <div class="queue flex h-full min-h-0 flex-col">
        <div class="queue__head">
          <span class="queue__head-title">申请记录</span>
          <el-button
            link
            type="primary"
            :disabled="writeDisabled || !encounterReady"
            @click="resetEditor"
          >
            新建
          </el-button>
        </div>
        <div class="queue__list min-h-0 flex-1 overflow-auto">
          <button
            v-for="item in queueCards"
            :key="item.prescriptionNo"
            type="button"
            class="record"
            :class="{ 'is-active': item.prescriptionNo === activePrescriptionNo }"
            @click="openForm(item.prescriptionNo)"
          >
            <div class="record__top">
              <span class="record__title" :title="item.title">{{ item.title }}</span>
              <span class="queue-status" :class="queueStatusClass(item.statusEnum)">
                {{ statusText(item.statusEnum) }}
              </span>
            </div>
            <div class="record__mid">
              <span>{{ item.extra || '未排时间' }}</span>
              <span v-if="item.count" class="record__count">{{ item.count }} 项</span>
            </div>
            <div class="record__foot">
              <span class="record__no">{{ item.meta }}</span>
            </div>
          </button>
          <div v-if="queueCards.length === 0" class="queue__empty">
            {{ encounterReady ? '暂无申请' : '请先选择住院患者' }}
          </div>
        </div>
      </div>
    </template>

    <template #center>
      <div v-loading="loading" class="flex h-full min-h-0 flex-col bg-[var(--el-bg-color)]">
        <div class="min-h-0 flex-1 overflow-auto px-3 py-2">
          <article class="sheet">
            <header class="sheet-head">
              <h2 class="sheet-title">{{ isSurgery ? '手术申请单' : '麻醉申请单' }}</h2>
            </header>

            <!-- 安排 -->
            <section class="sheet-section">
              <SectionTitle :title="isSurgery ? '手术安排' : '麻醉安排'" />
              <el-form label-position="top" class="sheet-form mt-2">
                <div class="field-grid">
                  <el-form-item v-if="isSurgery" label="拟手术时间">
                    <el-date-picker
                      v-model="procedureTime"
                      type="datetime"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      format="YYYY-MM-DD HH:mm"
                      placeholder="选择时间"
                      :disabled="!editable"
                      class="!w-full"
                    />
                  </el-form-item>
                  <el-form-item v-if="isSurgery" label="手术级别">
                    <el-select
                      v-model="procedureLevelCode"
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in levelOptions"
                        :key="String(opt.value)"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="isSurgery" label="切口类别">
                    <el-select
                      v-model="incisionTypeCode"
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in incisionOptions"
                        :key="String(opt.value)"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="麻醉方法" class="field-span-2">
                    <div class="method-with-nest">
                      <el-select
                        v-model="anesthesiaMethodCode"
                        clearable
                        placeholder="请选择"
                        class="method-with-nest__select"
                        :disabled="!editable"
                      >
                        <el-option
                          v-for="opt in methodOptions"
                          :key="String(opt.value)"
                          :label="opt.label"
                          :value="opt.value"
                        />
                      </el-select>
                      <el-checkbox
                        v-if="isSurgery"
                        v-model="nestChecked"
                        class="method-with-nest__check shrink-0"
                        :disabled="!editable || Boolean(nestedAnesthesiaNo)"
                      >
                        保存时一并生成麻醉申请
                      </el-checkbox>
                    </div>
                    <div v-if="isSurgery && nestedAnesthesiaNo" class="nest-hint">
                      已关联 {{ nestedAnesthesiaNo }}
                    </div>
                  </el-form-item>
                  <el-form-item v-if="!isSurgery" label="关联手术" class="field-span-2">
                    <el-select
                      v-model="linkedSurgeryNo"
                      filterable
                      clearable
                      class="w-full"
                      :disabled="!editable"
                      placeholder="可选；展示术式名 + 申请号"
                    >
                      <el-option
                        v-for="opt in linkedSurgeryOptions"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                </div>
              </el-form>
            </section>

            <!-- 团队 -->
            <section class="sheet-section">
              <SectionTitle :title="isSurgery ? '手术团队' : '麻醉医师'" />
              <el-form label-position="top" class="sheet-form mt-2">
                <div class="field-grid">
                  <el-form-item v-if="isSurgery" required label="主刀">
                    <el-select
                      v-model="surgeonId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in practitionerOptions"
                        :key="String(opt.value)"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="isSurgery" label="一助">
                    <el-select
                      v-model="firstAssistantId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in practitionerOptions"
                        :key="'a1-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="isSurgery" label="二助">
                    <el-select
                      v-model="secondAssistantId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in practitionerOptions"
                        :key="'a2-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="isSurgery" label="配合护士">
                    <el-select
                      v-model="nurseId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in practitionerOptions"
                        :key="'n-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="麻醉医师">
                    <el-select
                      v-model="anesthetistId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in practitionerOptions"
                        :key="'an-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                </div>
              </el-form>
            </section>

            <!-- 明细 -->
            <section class="sheet-section">
              <SectionTitle title="申请明细" />
              <div class="mb-2 mt-2 flex items-center gap-2">
                <el-select
                  v-model="pickKind"
                  class="!w-28 shrink-0"
                  :disabled="!editable"
                  @change="onPickKindChange"
                >
                  <el-option :label="isSurgery ? '手术' : '麻醉项目'" value="main" />
                  <el-option label="附加项目" value="extra" />
                </el-select>
                <SelectTable
                  v-model="pickSelectValue"
                  class="min-w-0 flex-1"
                  :remote-method="queryPickOptions"
                  :columns="pickColumns"
                  value-key="id"
                  label-key="name"
                  :placeholder="pickPlaceholder"
                  :disabled="!editable"
                  :clearable="true"
                  :page-size="20"
                  panel-width="640"
                  :before-close="onPickBeforeClose"
                  @change="onPickChange"
                />
              </div>
              <el-table
                :data="activeLines"
                class="sheet-table"
                table-layout="fixed"
                empty-text="从上方搜索并点选加入明细"
              >
                <el-table-column label="种类" width="72" align="center">
                  <template #default="{ row }">
                    <span
                      class="kind-badge"
                      :class="
                        row.lineKindCode === 'SURCHARGE' ? 'kind-badge--extra' : 'kind-badge--main'
                      "
                    >
                      {{ lineKindText(row.lineKindCode) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="项目" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.displayName || row.activityDefinitionId }}
                  </template>
                </el-table-column>
                <el-table-column label="数量" width="148" align="center">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.quantity"
                      :min="1"
                      :disabled="!editable"
                      size="small"
                      controls-position="right"
                      class="qty-input"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="单位" width="64" align="center">
                  <template #default="{ row }">
                    {{ row.unitCode || '次' }}
                  </template>
                </el-table-column>
                <el-table-column v-if="isSurgery" label="主手术" width="72" align="center">
                  <template #default="{ row, $index }">
                    <el-checkbox
                      :model-value="row.mainProcedureFlag === true"
                      :disabled="!editable || row.lineKindCode !== 'PROCEDURE'"
                      @change="markMain($index)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="72" align="center">
                  <template #default="{ $index }">
                    <el-button link type="danger" :disabled="!editable" @click="removeLine($index)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <!-- 备注 -->
            <section class="sheet-section sheet-section--last">
              <SectionTitle title="备注" />
              <div class="mt-2">
                <el-input
                  v-model="plannedNote"
                  type="textarea"
                  :rows="3"
                  :disabled="!editable"
                  placeholder="请输入备注信息（示例：拟全麻，术中需要超声。）"
                />
              </div>
            </section>
          </article>
        </div>
      </div>
    </template>

    <template #footer>
      <span class="text-xs text-[var(--el-text-color-regular)]">
        {{
          headerStatus === STATUS_ACTIVE ? '已发送，未执行可撤回' : '待签发可修改，签发后发送护士站'
        }}
      </span>
      <div class="flex flex-wrap items-center gap-2">
        <el-button
          v-if="isSurgery && activePrescriptionNo"
          :disabled="writeDisabled"
          :loading="saving"
          @click="toggleNest"
        >
          {{ nestedAnesthesiaNo ? '取消嵌套麻醉' : '生成麻醉申请' }}
        </el-button>
        <el-button :disabled="!canWithdraw" :loading="saving" @click="withdrawForm">
          撤回
        </el-button>
        <el-button type="primary" :disabled="!canSign" :loading="saving" @click="signForm">
          签发
        </el-button>
        <el-button type="primary" :disabled="!editable" :loading="saving" @click="saveForm">
          保存
        </el-button>
      </div>
    </template>
  </StationTriPaneLayout>
</template>

<style scoped>
/* —— 左侧申请记录 —— */
.queue__head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.queue__head-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.queue__list {
  padding: 8px;
}

.queue__empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.record {
  display: block;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 10px 8px 12px;
  text-align: left;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  box-shadow: inset 3px 0 0 0 var(--el-border-color-lighter);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    box-shadow 0.15s ease;
}

.record:hover {
  border-color: var(--el-border-color-lighter);
  box-shadow: inset 3px 0 0 0 var(--el-color-primary-light-5);
}

.record.is-active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  box-shadow: inset 3px 0 0 0 var(--el-color-primary);
}

.record.is-active .record__title {
  color: var(--el-color-primary);
}

.record__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.record__title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.queue-status {
  flex-shrink: 0;
  padding: 0 6px;
  line-height: 18px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 2px;
}

.queue-status--draft {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
}

.queue-status--ok {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-7);
}

.queue-status--other {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.record__mid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.record__count {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.record__foot {
  margin-top: 6px;
  padding-top: 5px;
  border-top: 1px dashed var(--el-border-color-extra-light);
}

.record__no {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  color: var(--el-text-color-secondary);
}

/* —— 右侧单据主区 —— */
.sheet {
  width: 100%;
  padding: 4px 4px 12px;
}

.sheet-head {
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.sheet-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.sheet-section {
  margin-top: 2px;
}

.sheet-section + .sheet-section {
  margin-top: 14px;
}

.sheet-section--last {
  margin-bottom: 0;
}

/* 系统表单 label（Element Plus 默认字号） */
.sheet-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.sheet-form :deep(.el-form-item__label) {
  margin-bottom: 4px;
  padding: 0;
  line-height: 1.4;
  font-size: var(--el-form-label-font-size, var(--el-font-size-base));
  color: var(--el-text-color-regular);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px 12px;
}

.field-span-2 {
  grid-column: span 2;
}

.method-with-nest {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.method-with-nest__select {
  flex: 1;
  min-width: 0;
}

.nest-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kind-badge {
  display: inline-block;
  min-width: 40px;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
}

.kind-badge--main {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.kind-badge--extra {
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.sheet-table {
  width: 100%;
}

.sheet-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.qty-input {
  width: 120px;
}

@media (max-width: 1100px) {
  .field-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .field-span-2 {
    grid-column: span 2;
  }
}

@media (max-width: 720px) {
  .field-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field-span-2 {
    grid-column: span 2;
  }

  .method-with-nest {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
