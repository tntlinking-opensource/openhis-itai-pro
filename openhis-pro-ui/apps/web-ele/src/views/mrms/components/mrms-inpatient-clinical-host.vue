<script setup lang="ts">
import type { HomepageListRow } from '../homepage/homepage-list.types';
import type { InpatientAdviceQuickAction } from '#/views/inpatient/doctor-station/advice/components/inpatient-advice-quick-action-bar.vue';
import type { InpatientDoctorStationPatient } from '#/views/inpatient/doctor-station/doctorPatientStore';
import type { NurseStationModulePatient } from '#/views/inpatient/nurse-station/components/nurse-station-module-patient.types';
import type { PrintDocType } from '#/views/inpatient/nurse-station/print-center/print-center.types';
import type { ServiceApplyDocumentGroup } from '#/views/outpatient/doctor-station/service-apply/service-apply-doc-groups';
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import { computed, defineAsyncComponent, nextTick, provide, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import PatientAllergyHistoryDialog from '#/components/clinical/patient-allergy-history-dialog.vue';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { resolveAdministrativeGenderLabel } from '#/utils/gender/resolve-administrative-gender-label';
import { EMR_TEMPLATE_STATION_KIND } from '#/views/basic/emr-template/emr-template-scope.constants';
import { InpatientAdviceList } from '#/views/inpatient/doctor-station/advice';
import { useInpatientDoctorPatientStore } from '#/views/inpatient/doctor-station/doctorPatientStore';
import { parseAgeTextToNumber } from '#/views/inpatient/nurse-station/services/map-inpatient-nurse-station-api';
import { printInpatientPrintCenter } from '#/views/inpatient/nurse-station/print-center/print-center-print';
import { inferServiceApplyKindFromAdviceRow } from '#/views/outpatient/doctor-station/service-apply/service-apply-doc-groups';
import ServiceApplyDialog from '#/views/outpatient/doctor-station/service-apply/service-apply-dialog.vue';

defineOptions({ name: 'MrmsInpatientClinicalHost' });

export type MrmsInpatientClinicalKind = 'emr' | 'nursing' | 'temperature' | 'order' | 'bill';

interface Props {
  /**
   * 组件默认是否只读；具体打开时由 open(..., { forceReadOnly }) 覆盖。
   * 未传 options 时默认只读，避免病案首页等入口误放开写权限。
   */
  defaultReadOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  defaultReadOnly: true,
});

const visible = ref(false);
const kind = ref<MrmsInpatientClinicalKind>('emr');
const row = ref<HomepageListRow | null>(null);
/** 本次打开会话的只读态（按行门禁；默认 true） */
const sessionReadonly = ref(true);
const adviceListRef = ref<InstanceType<typeof InpatientAdviceList> | null>(null);
/** 病案侧栏医嘱打印中（长期/临时） */
const orderPrinting = ref(false);

/** 生效只读：以本次 open 写入的 sessionReadonly 为准（未传 options 时已回落 props 默认 true） */
const effectiveReadonly = computed(() => sessionReadonly.value);

const doctorPatientStore = useInpatientDoctorPatientStore();
/** 打开医嘱前暂存医生站选中，关闭后还原，避免污染医生站 */
const previousDoctorPatient = ref<InpatientDoctorStationPatient | null>(null);

const TemperatureSheet = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/temperature-sheet/index.vue'),
);
const NursingRecord = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/nursing-record/index.vue'),
);
const CostDetails = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/cost-details/index.vue'),
);

const title = computed(() => {
  const name = row.value?.patientName?.trim() || '患者';
  let base = `住院病历 · ${name}`;
  if (kind.value === 'temperature') {
    base = `体温单 · ${name}`;
  } else if (kind.value === 'nursing') {
    base = `护理记录 · ${name}`;
  } else if (kind.value === 'order') {
    base = `住院医嘱 · ${name}`;
  } else if (kind.value === 'bill') {
    base = `费用清单 · ${name}`;
  }
  // 门禁只读时标题明示，避免误以为可改
  if (effectiveReadonly.value) {
    return `${base}（只读）`;
  }
  return base;
});

const encounterId = computed(() => preserveApiIdForRequest(row.value?.encounterId) || '');
const patientId = computed(() => preserveApiIdForRequest(row.value?.patientId) || '');

/** 病历页眉本地回退：床号/病房/科室，服务端非空仍优先 */
const emrPatientInfo = computed(() => {
  const current = row.value;
  if (!current) {
    return undefined;
  }
  const loc = resolveEmrLocationFromListBed(current.bedName);
  const ageRaw = (current.ageText ?? '').trim();
  return {
    name: current.patientName || '',
    gender: resolveListGenderLabel(current.genderEnum),
    age: ageRaw.replace(/岁\s*$/u, ''),
    phone: '',
    insurance: (current.insuranceTypeText ?? '').trim(),
    medicalRecordNo: (current.iptNo ?? '').trim(),
    idCard: (current.certno ?? '').trim(),
    deptName: (current.admDeptName ?? '').trim(),
    wardName: loc.wardName,
    bedNo: loc.bedNo,
  };
});

/** 医嘱门禁用的医生站患者占位 */
const stubDoctorPatient = computed<InpatientDoctorStationPatient | null>(() => {
  const current = row.value;
  if (!current?.encounterId) {
    return null;
  }
  const encId = preserveApiIdForRequest(current.encounterId);
  const pid = preserveApiIdForRequest(current.patientId);
  if (!encId || !pid) {
    return null;
  }
  let statusEnum: string | undefined;
  // 已结算（含已离院结过）→ 医嘱写门禁按 COMPLETED
  if (current.settledCode === '1') {
    statusEnum = 'COMPLETED';
  } else if (current.dischargedCode === '1') {
    // 已出院未走 settled 标记时，仍按 DISCHARGED 收紧写操作
    statusEnum = 'DISCHARGED';
  }
  return {
    encounterId: encId,
    patientId: pid,
    patientName: current.patientName || '',
    encounterBusNo: current.iptNo,
    admissionTime: current.admTime,
    startTime: current.admTime,
    endTime: current.dscgTime,
    organizationName: current.admDeptName,
    diagnosisSummary: current.dischargeDiagnosisText,
    genderEnum: current.genderEnum,
    genderEnumText: resolveListGenderLabel(current.genderEnum),
    ageText: current.ageText,
    statusEnum,
  };
});

/**
 * 去掉护士站「原{房}-{床}（未占床）」包装，体温单/打印抬头只保留房-床中间段。
 *
 * @param bedName 列表回填的床位展示名
 * @return 纯房-床名（如 `201-3`）；无法解析时原样返回
 */
function stripUnoccupiedBedDisplay(bedName?: string): string {
  const raw = (bedName ?? '').trim();
  // 空床位：体温单床号列留空
  if (!raw) {
    return '';
  }
  const matched = /^原(.+)（未占床）$/.exec(raw);
  // 召回/清床未占床展示包装：只留中间「房-床」供纸面抬头
  if (matched?.[1]) {
    return matched[1].trim();
  }
  return raw;
}

/**
 * 列表床位展示拆成病房/床号：支持「房-床」或未占床包装后的中间段。
 *
 * @param bedName 列表回填的床位展示名
 * @return 病房名与床号；无横杠时病房留空由服务端 HOUSE 补
 */
function resolveEmrLocationFromListBed(bedName?: string): { bedNo: string; wardName: string } {
  const middle = stripUnoccupiedBedDisplay(bedName);
  const dash = middle.lastIndexOf('-');
  // 「201-3」：前段病房、后段床号
  if (dash > 0) {
    return {
      wardName: middle.slice(0, dash).trim(),
      bedNo: middle.slice(dash + 1).trim(),
    };
  }
  return { wardName: '', bedNo: middle };
}

/**
 * 列表性别码 → 体温单抬头文案（兼容 AdministrativeGender 与首页 1/2）。
 *
 * @param gendCode 列表 gendCode / genderEnum
 * @return 男性/女性等；无法识别时空串
 */
function resolveListGenderLabel(gendCode?: string): string {
  const raw = (gendCode ?? '').trim();
  // 首页表单码：1 男 2 女（主档分页路径可能回填）
  if (raw === '1') {
    return resolveAdministrativeGenderLabel(undefined, 'MALE', '');
  }
  if (raw === '2') {
    return resolveAdministrativeGenderLabel(undefined, 'FEMALE', '');
  }
  return resolveAdministrativeGenderLabel(undefined, raw, '');
}

/** 护士站模块患者入参（id=encounterId；出院取末次已完成位置） */
const nursePatients = computed<NurseStationModulePatient[]>(() => {
  const current = row.value;
  if (!current?.encounterId) {
    return [];
  }
  const encId = preserveApiIdForRequest(current.encounterId);
  // 就诊主键精度保护失败则不组装患者，避免体温单/打印绑错人
  if (!encId) {
    return [];
  }
  const wardDisplay = (current.wardName ?? '').trim() || '-';
  const bedDisplay = stripUnoccupiedBedDisplay(current.bedName);
  return [
    {
      id: encId,
      patientId: preserveApiIdForRequest(current.patientId) || undefined,
      encounterBusNo: current.iptNo,
      admissionAt: current.admTime,
      dischargeAt: current.dscgTime,
      bedNo: bedDisplay,
      name: current.patientName || '',
      gender: resolveListGenderLabel(current.genderEnum),
      age: parseAgeTextToNumber(current.ageText),
      nurseLevel: '',
      diagnosis: current.dischargeDiagnosisText,
      organizationName: current.admDeptName,
      regionName: wardDisplay,
      // 体温单「病室」列复用病区名（列表无独立病房字段）
      wardName: wardDisplay,
      pendingAudit: 0,
      pendingExecute: 0,
      riskTags: [],
    },
  ];
});

/** 费用清单首选日期起（入院日） */
const billPreferredDateFrom = computed(() => {
  const raw = row.value?.admTime?.trim();
  if (!raw) {
    return undefined;
  }
  return raw.slice(0, 10);
});

/** 费用清单首选日期止（出院日或今日） */
const billPreferredDateTo = computed(() => {
  const raw = row.value?.dscgTime?.trim();
  if (raw) {
    return raw.slice(0, 10);
  }
  const today = new Date();
  const y = today.getFullYear();
  const m = String(today.getMonth() + 1).padStart(2, '0');
  const d = String(today.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
});

/** 护理记录首选日期：与费用清单同口径（入院～出院/今日） */
const nursingPreferredDateFrom = computed(() => billPreferredDateFrom.value);
const nursingPreferredDateTo = computed(() => billPreferredDateTo.value);

/** 已结算默认查 SETTLED，否则待结算 */
const billPreferredStatus = computed(() =>
  row.value?.settledCode === '1' ? 'SETTLED' : 'PENDING_SETTLEMENT',
);

const serviceApplyDialogVisible = ref(false);
const allergyDialogVisible = ref(false);
const serviceApplyKind = ref<ServiceApplyKind>('lab');
const serviceApplyEditGroup = ref<null | ServiceApplyDocumentGroup>(null);

const serviceApplyPatientSummary = computed<ServiceApplyPatientSummary>(() => {
  const p = stubDoctorPatient.value;
  return {
    patientName: (p?.patientName ?? '').trim() || '—',
    genderText: (p?.genderEnumText ?? '').trim() || '—',
    ageText: (p?.ageText ?? '').trim() || '—',
    encounterNo: (p?.encounterBusNo ?? '').trim() || '—',
    feeNatureText: (p?.organizationName ?? '').trim() || '住院',
  };
});

/** 病案弹层内：已绑定就诊即可继续临床操作 */
provide(ENSURE_CLINICAL_RECEPTION_KEY, async () => {
  if (!encounterId.value || !patientId.value) {
    ElMessage.warning('缺少就诊或患者主键');
    return false;
  }
  return true;
});

/** 打开医嘱：写入医生站选中占位 */
function bindDoctorPatientContext() {
  previousDoctorPatient.value = doctorPatientStore.selectedPatient;
  const stub = stubDoctorPatient.value;
  if (stub) {
    doctorPatientStore.setSelectedPatient(stub);
  }
}

/** 关闭医嘱：还原医生站选中 */
function restoreDoctorPatientContext() {
  doctorPatientStore.setSelectedPatient(previousDoctorPatient.value);
  previousDoctorPatient.value = null;
}

/** 打开指定临床模块；forceReadOnly 按行门禁传入 */
function open(
  nextKind: MrmsInpatientClinicalKind,
  nextRow: HomepageListRow,
  options?: { forceReadOnly?: boolean },
) {
  const enc = preserveApiIdForRequest(nextRow.encounterId);
  if (!enc) {
    ElMessage.warning('缺少就诊主键，无法打开');
    return;
  }
  if (
    (nextKind === 'emr' ||
      nextKind === 'order' ||
      nextKind === 'bill' ||
      nextKind === 'temperature' ||
      nextKind === 'nursing') &&
    !preserveApiIdForRequest(nextRow.patientId)
  ) {
    let tip = '缺少患者主键，无法打开病历';
    if (nextKind === 'order') {
      tip = '缺少患者主键，无法打开医嘱';
    } else if (nextKind === 'bill') {
      tip = '缺少患者主键，无法打开费用清单';
    } else if (nextKind === 'temperature') {
      tip = '缺少患者主键，无法打开体温单';
    } else if (nextKind === 'nursing') {
      tip = '缺少患者主键，无法打开护理记录';
    }
    ElMessage.warning(tip);
    return;
  }
  // 按行门禁覆盖会话只读；未传 options.forceReadOnly 时回落组件默认（只读）
  if (options && typeof options.forceReadOnly === 'boolean') {
    sessionReadonly.value = options.forceReadOnly;
  } else {
    sessionReadonly.value = props.defaultReadOnly;
  }
  kind.value = nextKind;
  row.value = nextRow;
  if (nextKind === 'order') {
    bindDoctorPatientContext();
  }
  visible.value = true;
}

/** 关闭弹层 */
function close() {
  visible.value = false;
}

/** 打开检验/检查/治疗申请弹窗（新建）；只读会话禁止 */
function openServiceApplyDialog(applyKind: ServiceApplyKind) {
  if (effectiveReadonly.value) {
    ElMessage.info('当前为只读，不可开立申请');
    return;
  }
  serviceApplyEditGroup.value = null;
  serviceApplyKind.value = applyKind;
  serviceApplyDialogVisible.value = true;
}

/** 医嘱底部快捷入口（对齐医生站主壳） */
function handleQuickAction(action: InpatientAdviceQuickAction) {
  if (action === 'lab' || action === 'exam' || action === 'treatment') {
    openServiceApplyDialog(action);
    return;
  }
  if (action === 'entrust') {
    return;
  }
  if (action === 'allergy') {
    allergyDialogVisible.value = true;
    return;
  }
  const pendingLabel: Partial<Record<InpatientAdviceQuickAction, string>> = {
    transfusion: '输血申请',
    pathology: '病理申请',
    surgery: '手术申请',
  };
  const label = pendingLabel[action];
  if (label) {
    ElMessage.info(`${label}功能待实现`);
  }
}

/** 双击草稿申请单：弹窗编辑；只读会话禁止 */
function handleEditServiceApply(group: ServiceApplyDocumentGroup) {
  if (effectiveReadonly.value) {
    ElMessage.info('当前为只读，不可编辑申请单');
    return;
  }
  const head = group.lines[0];
  const inferred = head ? inferServiceApplyKindFromAdviceRow(head) : null;
  serviceApplyKind.value = inferred ?? 'lab';
  serviceApplyEditGroup.value = group;
  serviceApplyDialogVisible.value = true;
}

/** 申请单保存后刷新医嘱列表 */
async function handleServiceApplyPersisted() {
  await nextTick();
  await adviceListRef.value?.reload?.();
}

/**
 * 病案侧栏打印长期/临时医嘱单（医生站已开立全量 + 已校对同源字段；开立时间正序）。
 *
 * @param docType long_order / temp_order
 */
async function handlePrintOrderDoc(docType: Extract<PrintDocType, 'long_order' | 'temp_order'>) {
  // 防重复点击：上一单未完成不二次入队
  if (orderPrinting.value) {
    return;
  }
  const patients = nursePatients.value;
  // 侧栏仅单就诊上下文；人数异常说明行未就绪
  if (patients.length !== 1) {
    ElMessage.warning('缺少患者上下文，无法打印医嘱');
    return;
  }
  const dateFrom = billPreferredDateFrom.value;
  const dateTo = billPreferredDateTo.value;
  // 开嘱日过滤依赖入院～出院/今日区间
  if (!dateFrom || !dateTo) {
    ElMessage.warning('缺少入院/出院日期，无法打印医嘱');
    return;
  }
  const label = docType === 'long_order' ? '长期医嘱' : '临时医嘱';
  orderPrinting.value = true;
  try {
    const result = await printInpatientPrintCenter({
      docType,
      patients,
      // 病案归档：全量开立 + 已校对字段合并；不勾待打行
      selectedRows: [],
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
      orderOpenedTimeAsc: true,
      orderPrintDataSource: 'doctor_advice',
    });
    // 仅成功时提示份数；失败由打印中心内部已告警
    if (result.ok) {
      ElMessage.success(`已发送打印：${label}，共 ${result.count} 份`);
    }
  } finally {
    orderPrinting.value = false;
  }
}

watch(visible, (v) => {
  if (!v) {
    if (kind.value === 'order') {
      restoreDoctorPatientContext();
    }
    row.value = null;
    serviceApplyDialogVisible.value = false;
    serviceApplyEditGroup.value = null;
  }
});

watch(serviceApplyDialogVisible, (open) => {
  if (!open) {
    serviceApplyEditGroup.value = null;
  }
});

defineExpose({ open, close });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title"
    width="96%"
    top="2vh"
    destroy-on-close
    class="mrms-clinical-host-dialog"
    append-to-body
  >
    <div v-if="visible && row" class="mrms-clinical-host__body">
      <MedicalRecordForm
        v-if="kind === 'emr'"
        :station-kind="EMR_TEMPLATE_STATION_KIND.INPATIENT"
        :encounter-id="encounterId"
        :patient-id="patientId"
        :patient-info="emrPatientInfo"
        encounter-scoped
        :force-read-only="effectiveReadonly"
      />
      <div v-else-if="kind === 'order'" class="mrms-clinical-host__order-wrap">
        <div class="mrms-clinical-host__order-toolbar">
          <ElButton
            size="small"
            type="primary"
            plain
            :loading="orderPrinting"
            @click="handlePrintOrderDoc('long_order')"
          >
            打印长期医嘱
          </ElButton>
          <ElButton
            size="small"
            type="primary"
            plain
            :loading="orderPrinting"
            @click="handlePrintOrderDoc('temp_order')"
          >
            打印临时医嘱
          </ElButton>
        </div>
        <InpatientAdviceList
          ref="adviceListRef"
          class="mrms-clinical-host__advice"
          :encounter-id="encounterId"
          :patient-id="patientId"
          :force-write-disabled="effectiveReadonly"
          default-time-sort-order="asc"
          include-tcm-prescriptions
          show-execution-record
          @quick-action="handleQuickAction"
          @edit-service-apply="handleEditServiceApply"
        />
      </div>
      <TemperatureSheet
        v-else-if="kind === 'temperature'"
        :patients="nursePatients"
        :readonly="effectiveReadonly"
      />
      <NursingRecord
        v-else-if="kind === 'nursing'"
        :patients="nursePatients"
        :readonly="effectiveReadonly"
        :preferred-date-from="nursingPreferredDateFrom"
        :preferred-date-to="nursingPreferredDateTo"
      />
      <CostDetails
        v-else-if="kind === 'bill'"
        :patients="nursePatients"
        hide-patient-header
        hide-clinical-pre-settle
        :preferred-date-from="billPreferredDateFrom"
        :preferred-date-to="billPreferredDateTo"
        :preferred-status-enum="billPreferredStatus"
      />
    </div>
  </ElDialog>

  <PatientAllergyHistoryDialog
    v-model="allergyDialogVisible"
    :patient-id="patientId"
    :encounter-id="encounterId"
    default-verification-status="CONFIRMED"
    :read-only="effectiveReadonly"
  />

  <ServiceApplyDialog
    v-model="serviceApplyDialogVisible"
    station-scope="inpatient"
    shell="dialog"
    :apply-kind="serviceApplyKind"
    :edit-document-group="serviceApplyEditGroup"
    :encounter-id="encounterId"
    :patient-id="patientId"
    :patient="serviceApplyPatientSummary"
    @saved="handleServiceApplyPersisted"
  />
</template>

<style scoped>
.mrms-clinical-host__body {
  height: min(86vh, 920px);
  min-height: 480px;
  overflow: auto;
}

.mrms-clinical-host__order-wrap {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
}

.mrms-clinical-host__order-toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.mrms-clinical-host__advice {
  height: 100%;
  min-height: 0;
  flex: 1;
}
</style>

<style>
/* 弹层内容区拉高，便于病历/医嘱/体温单操作 */
.mrms-clinical-host-dialog.el-dialog {
  margin-bottom: 2vh;
}

.mrms-clinical-host-dialog .el-dialog__body {
  padding-top: 8px;
  padding-bottom: 12px;
}
</style>
