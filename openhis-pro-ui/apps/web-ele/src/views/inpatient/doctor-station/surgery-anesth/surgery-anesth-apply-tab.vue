<script setup lang="ts">
import type { InpatientDoctorStationAdviceCatalogRowDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';
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
import type {
  AdviceProductOption,
  ProductQueryParams,
} from '#/views/inpatient/doctor-station/advice/services/advice.types';

import { computed, nextTick, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { httpPageInpatientDoctorStationAdviceCatalog } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import {
  httpDeleteAnesthesiaApplyForm,
  httpDeleteSurgeryApplyForm,
  httpGetAnesthesiaApplyFormDetail,
  httpGetSurgeryApplyFormDetail,
  httpInitSurgeryAnesth,
  httpListAnesthesiaApplyForms,
  httpListSurgeryApplyForms,
  httpSaveAnesthesiaApplyForm,
  httpSaveSurgeryApplyForm,
  httpSearchClinicalProcV3,
  httpSignAnesthesiaApplyForm,
  httpSignSurgeryApplyForm,
  httpWithdrawAnesthesiaApplyForm,
  httpWithdrawSurgeryApplyForm,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-surgery-anesth.http';
import { httpLoadNurseStationSkinTestInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.http';
import { httpListPractitioners } from '#/api/outpatient-registration';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectTable from '#/components/select-table.vue';
import { useAuthStore } from '#/store';
import { excludePlatformAdminOptions } from '#/utils/auth/platform-admin';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preferredTotalUnitBandKindFromOrderCalcRule } from '#/utils/doctor-station/medication-order-total-calc';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { validatePlannedProcedureTime } from '#/utils/inpatient/surgery-anesth-time-bounds';
import { METHOD_CODE_IV_INJECTION } from '#/utils/print/constants/medication-method-codes';
import AdviceOrderSelect from '#/views/inpatient/doctor-station/advice/components/advice-order-select.vue';
import { mapCatalogRowToOption } from '#/views/inpatient/doctor-station/advice/services/advice-list';
import {
  ADVICE_PRODUCT_COLUMNS,
  MEDICATION_DEFINITION_TABLE,
  WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
} from '#/views/inpatient/doctor-station/advice/services/advice-tab.constants';
import { ANESTH_CATALOG_BUS_NO_SORT_PRIORITY } from '#/views/inpatient/doctor-station/surgery-anesth/anesth-catalog-sort.config';
import {
  defaultTotalAmountUnitCode,
  findBandByUnitCode,
  unitCodeByBandKind,
  unitNameByBandKind,
} from '#/views/inpatient/doctor-station/advice/services/medication-unit-bands';

defineOptions({ name: 'InpatientSurgeryAnesthApplyTab' });

interface Props {
  encounterId?: string;
  patientId?: string;
  /** SURGERY 手术申请；ANESTHESIA 麻醉申请 */
  formKind?: SurgeryAnesthFormKind;
  writeDisabled?: boolean;
  /** 入院时间（拟手术时间下界；与列表患者 startTime/admissionTime 同源） */
  admissionTime?: string;
  /** 出院时间（拟手术时间上界；未出院可空） */
  dischargeTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  formKind: 'SURGERY',
  writeDisabled: false,
  admissionTime: '',
  dischargeTime: '',
});

const STATUS_DRAFT = 'DRAFT';
const STATUS_ACTIVE = 'ACTIVE';
const ROLE_SURGEON = 'SURGEON';
const ROLE_FIRST = 'FIRST_ASSISTANT';
const ROLE_SECOND = 'SECOND_ASSISTANT';
const ROLE_NURSE = 'NURSE';
const ROLE_ANESTHETIST = 'ANESTHETIST';
/** 手术诊疗类目（与后端 ServiceRequestCategoryConstants 一致） */
const CATEGORY_SURGERY = '24';
/** 麻醉诊疗类目 */
const CATEGORY_ANESTHESIA = '25';
const ACTIVITY_DEFINITION_TABLE = 'wkf_activity_definition';

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '待签发',
  ACTIVE: '已发送',
  VERIFIED: '已校对',
  HOLD: '暂挂',
  CANCELLED: '已取消',
  STOPPED: '已停嘱',
  COMPLETED: '已完成',
  MIXED: '状态不一致',
};

const authStore = useAuthStore();
const loading = ref(false);
const saving = ref(false);
const levelOptions = ref<ApiOptionItem[]>([]);
const incisionOptions = ref<ApiOptionItem[]>([]);
/** 麻醉方法（安排区） */
const methodOptions = ref<ApiOptionItem[]>([]);
/** 用药频次（明细行） */
const rateOptions = ref<ApiOptionItem[]>([]);
/** 给药途径/用法（药品行） */
const medRouteOptions = ref<ApiOptionItem[]>([]);
/** 单次剂量单位（术语 dose_unit；麻醉不换算总量，可检索改选） */
const doseUnitOptions = ref<ApiOptionItem[]>([]);
/** 剂量单位下拉本地过滤结果（按 label/value 匹配 ml、ug 等） */
const filteredDoseUnitOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
/** 配合护士：ROLE_NURSE 激活岗 */
const nurseOptions = ref<ApiOptionItem[]>([]);
/** 麻醉医师：Init 返回的麻醉室医师岗；空则回落 practitionerOptions */
const anesthetistOptions = ref<ApiOptionItem[]>([]);
const surgeryHeaders = ref<SurgeryApplyFormHeaderDto[]>([]);
const anesthesiaHeaders = ref<AnesthesiaApplyFormHeaderDto[]>([]);
const activePrescriptionNo = ref('');
const headerStatus = ref('');
/** 麻醉页可选关联的手术申请号 */
const linkedSurgeryNo = ref('');
/** 手术页：勾选需要麻醉后展开完整麻醉申请并一并生成 */
const nestChecked = ref(false);
/** 已生成的嵌套麻醉申请号（回显） */
const nestedAnesthesiaNo = ref('');
const procedureTime = ref('');
const procedureLevelCode = ref('');
const incisionTypeCode = ref('');
/** 麻醉方法：独立麻醉页，或手术页勾选需要麻醉后填写 */
const anesthesiaMethodCode = ref('');
const plannedNote = ref('');
const surgeonId = ref('');
const firstAssistantId = ref('');
const secondAssistantId = ref('');
const nurseId = ref('');
/** 麻醉医师：独立麻醉页，或手术页勾选需要麻醉后填写 */
const anesthetistId = ref('');
const surgeryLines = ref<SurgeryApplyFormLineRequest[]>([]);
const anesthesiaLines = ref<AnesthesiaApplyFormLineRequest[]>([]);
/** main 麻醉项目；medication 麻醉药品（手术侧固定术式，不走本开关） */
const pickKind = ref<'main' | 'medication'>('main');
/** 选中后立刻入明细并清空，不回写长期值 */
const pickSelectValue = ref('');
/** 手术页嵌套麻醉区的选品种类（仅麻醉项目/药品，禁止附加） */
const nestPickKind = ref<'main' | 'medication'>('main');
const nestPickSelectValue = ref('');
/** 术前嘱托快捷短语（点选追加到编辑区） */
const ENTRUST_QUICK_TAGS = ['禁食水', '备皮', '皮试', '留置导尿', '禁食8h'] as const;
/** 正在编辑嘱托的术式 sortNumber；空表示收起 */
const editingEntrustParentSort = ref<number | null>(null);
/** 嘱托编辑草稿正文 */
const draftEntrustContent = ref('');
/** 嘱托编辑草稿挂靠术式 */
const draftEntrustBasedOn = ref<number | undefined>(undefined);
/** 术式下拉列（国临 PD，对齐临嘱多列信息密度） */
const procedurePickColumns = [
  { prop: 'procedureCode', label: '编码', width: 110 },
  { prop: 'name', label: '名称', minWidth: 200 },
  { prop: 'ybNo', label: '医保编码', width: 110 },
  { prop: 'pyStr', label: '拼音', width: 80 },
  { prop: 'wbStr', label: '五笔', width: 80 },
];
/** 诊疗/药品：复用临嘱目录列 */
const adviceProductColumns = ADVICE_PRODUCT_COLUMNS;

const isSurgery = computed(() => props.formKind === 'SURGERY');
/** 手术页勾选需要麻醉后展示完整麻醉填写区 */
const showNestAnesthesia = computed(() => isSurgery.value && nestChecked.value);
const encounterReady = computed(() => Boolean(props.encounterId?.trim()));
const editable = computed(
  () => !props.writeDisabled && (!headerStatus.value || headerStatus.value === STATUS_DRAFT),
);
const canSign = computed(() => editable.value && Boolean(activePrescriptionNo.value));
/** 草稿态且有单号时可删除整单 */
const canDelete = computed(() => editable.value && Boolean(activePrescriptionNo.value));
const canWithdraw = computed(
  () =>
    !props.writeDisabled &&
    headerStatus.value === STATUS_ACTIVE &&
    Boolean(activePrescriptionNo.value),
);
const activeLines = computed(() => (isSurgery.value ? surgeryLines.value : anesthesiaLines.value));

/** 手术明细表仅展示术式行（嘱托嵌套在术式下方） */
const surgeryProcedureRows = computed(() =>
  surgeryLines.value.filter((line) => line.lineKindCode === 'PROCEDURE'),
);

/** 手术明细项数（术式条数） */
const surgeryProcedureCount = computed(() => surgeryProcedureRows.value.length);

/** 术式行默认全部展开（挂嘱托区） */
const surgeryExpandRowKeys = computed(() =>
  surgeryProcedureRows.value
    .map((row) => row.sortNumber)
    .filter((n): n is number => n != null),
);

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

/** 临时医嘱默认频次「立即」码；未配置则空 */
function immediateRateCode() {
  const hit = rateOptions.value.find((item) => String(item.label ?? '').trim() === '立即');
  return hit ? String(hit.value) : '';
}

/**
 * 麻醉药品默认用法「静脉注射」码；术语未配时回落 404。
 * 不用药品目录 methodCode（注射类常为静脉滴注 405）。
 */
function defaultAnesthMethodCode() {
  const hit = medRouteOptions.value.find(
    (item) => String(item.label ?? '').trim() === '静脉注射',
  );
  if (hit) {
    const code = String(hit.value ?? '').trim();
    if (code) {
      return code;
    }
  }
  return METHOD_CODE_IV_INJECTION;
}

/** 麻醉医师下拉：优先 Init 麻醉室池，未配置时回落全量出诊医生；剔除平台 admin */
const anesthetistSelectOptions = computed(() => {
  let source = practitionerOptions.value;
  if (anesthetistOptions.value.length > 0) {
    source = anesthetistOptions.value;
  }
  return excludePlatformAdminOptions(source);
});

/** 配合护士下拉：护士岗；空则回落出诊医生（兼容历史）；剔除平台 admin */
const nurseSelectOptions = computed(() => {
  const source =
    nurseOptions.value.length > 0 ? nurseOptions.value : practitionerOptions.value;
  return excludePlatformAdminOptions(source);
});

/** 医务人员主键对姓名 */
function practitionerName(id?: string) {
  const key = String(id ?? '').trim();
  if (!key) {
    return '';
  }
  const fromAnesth = anesthetistOptions.value.find((item) => String(item.value) === key)?.label;
  if (fromAnesth) {
    return fromAnesth;
  }
  const fromNurse = nurseOptions.value.find((item) => String(item.value) === key)?.label;
  if (fromNurse) {
    return fromNurse;
  }
  return practitionerOptions.value.find((item) => String(item.value) === key)?.label ?? '';
}

/** 左侧队列状态徽章 class */
function queueStatusClass(code?: string) {
  if (code === STATUS_ACTIVE || code === 'VERIFIED' || code === 'COMPLETED') {
    return 'queue-status--ok';
  }
  if (code === STATUS_DRAFT) {
    return 'queue-status--draft';
  }
  return 'queue-status--other';
}

/**
 * 行种类码转中文。
 * SURCHARGE 仅历史/划价回显；ENTRUST 为术前嘱托（basedOn 术式）。
 *
 * @param code 行种类
 */
function lineKindText(code?: string) {
  if (code === 'PROCEDURE') {
    return '手术';
  }
  if (code === 'ENTRUST') {
    return '嘱托';
  }
  if (code === 'SURCHARGE') {
    return '附加';
  }
  if (code === 'ANESTHESIA') {
    return '麻醉';
  }
  if (code === 'MEDICATION') {
    return '药品';
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

/** 组装拟手术 / 拟麻醉横表（手术勾选需要麻醉时一并带麻醉方法与医师） */
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
    if (nestChecked.value) {
      push(ROLE_ANESTHETIST, anesthetistId.value);
    }
    return {
      procedureTime: procedureTime.value || undefined,
      procedureLevelCode: procedureLevelCode.value || undefined,
      incisionTypeCode: incisionTypeCode.value || undefined,
      anesthesiaMethodCode: nestChecked.value
        ? anesthesiaMethodCode.value || undefined
        : undefined,
      note: plannedNote.value || undefined,
      performers,
    };
  }
  // 麻醉独立页：方法 + 麻醉医师
  push(ROLE_ANESTHETIST, anesthetistId.value);
  return {
    anesthesiaMethodCode: anesthesiaMethodCode.value || undefined,
    note: plannedNote.value || undefined,
    performers,
  };
}

/** 清空右侧编辑区，准备新建 */
function resetEditor() {
  activePrescriptionNo.value = '';
  headerStatus.value = '';
  linkedSurgeryNo.value = '';
  nestChecked.value = false;
  nestedAnesthesiaNo.value = '';
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
  nestPickSelectValue.value = '';
  nestPickKind.value = 'main';
  editingEntrustParentSort.value = null;
  draftEntrustContent.value = '';
  draftEntrustBasedOn.value = undefined;
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
    const [initRes, listRes, surgeryLinkRes, practitionerRes, nurseInit] = await Promise.all([
      httpInitSurgeryAnesth({ encounterId }),
      isSurgery.value
        ? httpListSurgeryApplyForms({ encounterId })
        : httpListAnesthesiaApplyForms({ encounterId }),
      needSurgeryLinkList ? httpListSurgeryApplyForms({ encounterId }) : Promise.resolve(null),
      practitionerOptions.value.length > 0 ? Promise.resolve(null) : httpListPractitioners({}),
      nurseOptions.value.length > 0
        ? Promise.resolve(null)
        : httpLoadNurseStationSkinTestInit().catch(() => null),
    ]);
    levelOptions.value = initRes.procedureLevelOptions ?? [];
    incisionOptions.value = initRes.incisionTypeOptions ?? [];
    methodOptions.value = initRes.anesthesiaMethodOptions ?? [];
    rateOptions.value = initRes.rateOptions ?? [];
    medRouteOptions.value = initRes.methodOptions ?? [];
    doseUnitOptions.value = initRes.doseUnitOptions ?? [];
    filteredDoseUnitOptions.value = doseUnitOptions.value;
    anesthetistOptions.value = initRes.anesthetistOptions ?? [];
    if (practitionerRes) {
      practitionerOptions.value = practitionerRes.practitionerOptions ?? [];
    }
    if (nurseInit?.nurseOptions) {
      nurseOptions.value = (nurseInit.nurseOptions ?? [])
        .map((item) => ({
          label: String(item.label ?? '').trim(),
          value: String(item.value ?? '').trim(),
        }))
        .filter((item) => item.label && item.value);
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
      // 开立仅术式+术前嘱托；过滤历史附加行
      surgeryLines.value = (detail.lines ?? [])
        .filter((line) => line.lineKindCode !== 'SURCHARGE')
        .map((line, index) => {
          if (line.lineKindCode === 'ENTRUST') {
            return {
              lineKindCode: 'ENTRUST' as const,
              entrustContent: line.entrustContent || '',
              basedOnProcedureSortNumber: line.basedOnProcedureSortNumber,
              quantity: Number(line.quantity ?? 1) || 1,
              unitCode: line.unitCode || '次',
              sortNumber: line.sortNumber ?? index + 1,
              displayName: line.entrustContent || '术前嘱托',
            };
          }
          return {
            lineKindCode: 'PROCEDURE' as const,
            procedureDefinitionId: preserveApiIdForRequest(line.procedureDefinitionId),
            activityDefinitionId: preserveApiIdForRequest(line.activityDefinitionId) ?? '',
            quantity: Number(line.quantity ?? 1) || 1,
            unitCode: line.unitCode || '次',
            sortNumber: line.sortNumber ?? index + 1,
            mainProcedureFlag: line.mainProcedureFlag === true,
            displayName: line.procedureName || line.activityName || '',
          };
        });
      // 已嵌套：把麻醉申请单内容拉回本页一并编辑
      if (nestedAnesthesiaNo.value) {
        await loadNestedAnesthesiaIntoEditor(encounterId, nestedAnesthesiaNo.value);
      } else {
        anesthesiaLines.value = [];
      }
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
    // 开立仅麻醉项目/药品：过滤历史附加行
    anesthesiaLines.value = mapAnesthesiaDetailLines(detail.lines ?? []);
  } finally {
    loading.value = false;
  }
}

/**
 * 详情麻醉行 → 编辑区行；过滤 SURCHARGE；药品带名称/单位名/频次用法剂量。
 *
 * @param lines 详情行
 */
function mapAnesthesiaDetailLines(
  lines: Array<{
    lineKindCode?: string;
    activityDefinitionId?: string;
    medicationDefinitionId?: string;
    medicationName?: string;
    activityName?: string;
    quantity?: number | string;
    unitCode?: string;
    unitName?: string;
    sortNumber?: number;
    rateCode?: string;
    methodCode?: string;
    dose?: number;
    doseUnitCode?: string;
    doseUnitName?: string;
  }>,
) {
  return lines
    .filter((line) => line.lineKindCode !== 'SURCHARGE')
    // 纯药品申请的空壳锚点（activity_id=0）不进编辑区
    .filter((line) => {
      if (line.lineKindCode === 'MEDICATION') {
        return true;
      }
      const activityId = String(line.activityDefinitionId ?? '').trim();
      return activityId !== '' && activityId !== '0';
    })
    .map((line, index) => {
      // 回显空频次时补「立即」，与开立默认一致
      const rateCode = String(line.rateCode ?? '').trim() || immediateRateCode() || undefined;
      if (line.lineKindCode === 'MEDICATION') {
        return {
          lineKindCode: 'MEDICATION' as const,
          medicationDefinitionId: preserveApiIdForRequest(line.medicationDefinitionId) ?? '',
          quantity: Number(line.quantity ?? 1) || 1,
          unitCode: line.unitCode || 'U',
          unitName: line.unitName || line.unitCode || 'U',
          rateCode,
          methodCode: line.methodCode,
          dose: line.dose != null ? Number(line.dose) : undefined,
          doseUnitCode: line.doseUnitCode || line.unitCode || 'U',
          doseUnitName: line.doseUnitName || line.doseUnitCode || line.unitName || '',
          sortNumber: line.sortNumber ?? index + 1,
          displayName: line.medicationName || line.activityName || '',
        };
      }
      return {
        lineKindCode: 'ANESTHESIA' as const,
        activityDefinitionId: preserveApiIdForRequest(line.activityDefinitionId) ?? '',
        quantity: Number(line.quantity ?? 1) || 1,
        unitCode: line.unitCode || '次',
        unitName: line.unitName || undefined,
        rateCode,
        sortNumber: line.sortNumber ?? index + 1,
        displayName: line.activityName || '',
      };
    });
}

/** 回填横表与人员 */
function applyPlanned(planned?: PlannedSurgeryFieldsDto) {
  plannedNote.value = planned?.note ?? '';
  const performers = planned?.performers;
  if (isSurgery.value) {
    procedureTime.value = planned?.procedureTime ?? '';
    procedureLevelCode.value = planned?.procedureLevelCode ?? '';
    incisionTypeCode.value = planned?.incisionTypeCode ?? '';
    surgeonId.value = performerId(performers, ROLE_SURGEON);
    firstAssistantId.value = performerId(performers, ROLE_FIRST);
    secondAssistantId.value = performerId(performers, ROLE_SECOND);
    nurseId.value = performerId(performers, ROLE_NURSE);
    // 嵌套麻醉时横表也带方法/医师
    anesthesiaMethodCode.value = planned?.anesthesiaMethodCode ?? '';
    anesthetistId.value = performerId(performers, ROLE_ANESTHETIST);
    return;
  }
  anesthesiaMethodCode.value = planned?.anesthesiaMethodCode ?? '';
  anesthetistId.value = performerId(performers, ROLE_ANESTHETIST);
}

/** 打开手术单时把嵌套麻醉申请内容填进本页麻醉区 */
async function loadNestedAnesthesiaIntoEditor(encounterId: string, anesthesiaNo: string) {
  const detail = await httpGetAnesthesiaApplyFormDetail({
    encounterId,
    prescriptionNo: anesthesiaNo,
  });
  if (detail.planned?.anesthesiaMethodCode) {
    anesthesiaMethodCode.value = detail.planned.anesthesiaMethodCode;
  } else if (detail.header?.anesthesiaMethodCode) {
    anesthesiaMethodCode.value = detail.header.anesthesiaMethodCode;
  }
  const anesthFromPlanned = performerId(detail.planned?.performers, ROLE_ANESTHETIST);
  if (anesthFromPlanned) {
    anesthetistId.value = anesthFromPlanned;
  }
  anesthesiaLines.value = mapAnesthesiaDetailLines(detail.lines ?? []);
}

/** 勾选/取消需要麻醉 */
function onNestCheckedChange(checked: boolean | string | number) {
  const on = checked === true;
  nestChecked.value = on;
  if (!on) {
    // 取消勾选：清本页麻醉填写区（已生成单号仍保留，保存时后端删旧嵌套草稿）
    anesthesiaMethodCode.value = '';
    anesthetistId.value = '';
    anesthesiaLines.value = [];
    nestPickSelectValue.value = '';
    nestPickKind.value = 'main';
  }
}

const adviceLockCategory = computed<'MEDICATION' | 'SERVICE'>(() =>
  pickKind.value === 'medication' ? 'MEDICATION' : 'SERVICE',
);

const adviceCategoryEnum = computed(() => {
  if (pickKind.value === 'medication') {
    return '';
  }
  return isSurgery.value ? CATEGORY_SURGERY : CATEGORY_ANESTHESIA;
});

const nestAdviceLockCategory = computed<'MEDICATION' | 'SERVICE'>(() =>
  nestPickKind.value === 'medication' ? 'MEDICATION' : 'SERVICE',
);

const nestAdviceCategoryEnum = computed(() =>
  nestPickKind.value === 'medication' ? '' : CATEGORY_ANESTHESIA,
);

/** 搜索框提示（手术仅术式；麻醉仅项目/药品） */
const pickPlaceholder = computed(() => {
  if (pickKind.value === 'medication') {
    return '请选择药品';
  }
  return isSurgery.value ? '搜索术式（编码/名称/拼音）' : '请选择麻醉项目';
});

const nestPickPlaceholder = computed(() => {
  if (nestPickKind.value === 'medication') {
    return '请选择药品';
  }
  return '请选择麻醉项目';
});

/** 换了要添加的种类后清空选品，避免麻醉项目与药品串选 */
function setPickKind(kind: 'main' | 'medication') {
  if (pickKind.value === kind) {
    return;
  }
  pickKind.value = kind;
  pickSelectValue.value = '';
}

/** 嵌套麻醉区换选品种类后清空检索，避免麻醉项目与药品串选 */
function setNestPickKind(kind: 'main' | 'medication') {
  if (nestPickKind.value === kind) {
    return;
  }
  nestPickKind.value = kind;
  nestPickSelectValue.value = '';
}

type PickHit = {
  id: string;
  name: string;
  extra?: string;
  unitCode?: string;
  /** 展示用单位名（禁止把计量带 code 当展示） */
  unitName?: string;
  displaySpec?: string;
  procedureDefinitionId?: string;
  activityDefinitionId?: string;
  medicationDefinitionId?: string;
  rateCode?: string;
  methodCode?: string;
  dose?: number;
  doseUnitCode?: string;
  doseUnitName?: string;
  unitPrice?: number;
  unitOptions?: Array<{ value: string; label: string }>;
};

/** 术式 SelectTable：关键字可空，打开即可分页浏览 */
async function queryProcedurePickOptions(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  const text = String(params.keyword ?? '').trim();
  const res = await httpSearchClinicalProcV3({
    keyword: text || undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  });
  const content = (res.items ?? []).map((item) => {
    const id = preserveApiIdForRequest(item.id) ?? '';
    return {
      id,
      procedureCode: String(item.procedureCode ?? '').trim(),
      name: String(item.name ?? '').trim(),
      ybNo: String(item.ybNo ?? '').trim(),
      pyStr: String(item.pyStr ?? '').trim(),
      wbStr: String(item.wbStr ?? '').trim(),
      procedureDefinitionId: id,
    };
  });
  return { content, totalCount: res.totalCount ?? content.length };
}

/** 麻醉项目：按关键字缓存已排序结果，翻页只切本地切片（仅麻醉申请页） */
let anesthServiceCatalogCache: null | { key: string; options: AdviceProductOption[] } = null;
/** 同 key 并发合并，避免下拉连点打两次重请求 */
let anesthServiceCatalogInflight: null | {
  key: string;
  promise: Promise<AdviceProductOption[]>;
} = null;
/** 无关键字浏览上限（麻醉类目通常远小于此） */
const ANESTH_SERVICE_BROWSE_FETCH_SIZE = 100;
/** 有关键字时上限更小（检索命中少） */
const ANESTH_SERVICE_KEYWORD_FETCH_SIZE = 60;

/**
 * 拉取并缓存麻醉项目选品（前端排序）；命中缓存 / 进行中请求则复用。
 */
async function loadAnesthServiceCatalogSorted(args: {
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
  keyword: string;
  productTableNames: string[];
}): Promise<AdviceProductOption[]> {
  const cacheKey = `${args.encounterId}|${args.patientId}|${args.organizationUnitId ?? ''}|${args.keyword}`;
  if (anesthServiceCatalogCache && anesthServiceCatalogCache.key === cacheKey) {
    return anesthServiceCatalogCache.options;
  }
  if (anesthServiceCatalogInflight && anesthServiceCatalogInflight.key === cacheKey) {
    return anesthServiceCatalogInflight.promise;
  }
  const fetchSize = args.keyword
    ? ANESTH_SERVICE_KEYWORD_FETCH_SIZE
    : ANESTH_SERVICE_BROWSE_FETCH_SIZE;
  const promise = (async () => {
    const res = await httpPageInpatientDoctorStationAdviceCatalog({
      encounterId: args.encounterId,
      patientId: args.patientId,
      organizationUnitId: args.organizationUnitId,
      keyword: args.keyword || undefined,
      pageNo: 1,
      pageSize: fetchSize,
      productTableNames: args.productTableNames,
      categoryEnum: CATEGORY_ANESTHESIA,
      positiveQuantityOnly: false,
    });
    const { content: rawRows } =
      parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
    const options = rawRows
      .map((row, index) => ({ option: mapCatalogRowToOption(row), index }))
      .toSorted((a, b) => compareAnesthCatalogPickOrder(a.option, b.option, a.index, b.index))
      .map((entry) => entry.option);
    anesthServiceCatalogCache = { key: cacheKey, options };
    return options;
  })().finally(() => {
    if (anesthServiceCatalogInflight && anesthServiceCatalogInflight.key === cacheKey) {
      anesthServiceCatalogInflight = null;
    }
  });
  anesthServiceCatalogInflight = { key: cacheKey, promise };
  return promise;
}

/** 换患者/就诊清缓存，避免串数据、占内存 */
watch(
  () => [props.encounterId, props.patientId] as const,
  () => {
    anesthServiceCatalogCache = null;
    anesthServiceCatalogInflight = null;
  },
);

/** 诊疗/药品：复用住院医嘱目录（编码/名称/规格/医保等级/库存/单价） */
async function queryAdviceCatalogOptions(params: ProductQueryParams) {
  const encounterId = props.encounterId?.trim() || '';
  const patientId = props.patientId?.trim() || '';
  if (!encounterId || !patientId) {
    return { content: [] as AdviceProductOption[], totalElements: 0 };
  }
  const orgRaw = authStore.getSelectedOrganization();
  const organizationUnitId =
    orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
      ? String(orgRaw)
      : undefined;
  const tables =
    params.productTableNames && params.productTableNames.length > 0
      ? params.productTableNames
      : [ACTIVITY_DEFINITION_TABLE];
  const isMed = tables.includes(MEDICATION_DEFINITION_TABLE);
  const categoryEnum = params.categoryEnum?.trim() || undefined;
  const keyword = params.keyword?.trim() || '';
  // 仅麻醉项目：前端按排序号置顶；药品走原分页
  const anesthServiceSort = !isMed && categoryEnum === CATEGORY_ANESTHESIA;
  try {
    if (anesthServiceSort) {
      const all = await loadAnesthServiceCatalogSorted({
        encounterId,
        patientId,
        organizationUnitId,
        keyword,
        productTableNames: tables,
      });
      const pageNo = Math.max(1, Number(params.pageNo) || 1);
      const pageSize = Math.max(1, Number(params.pageSize) || 20);
      const start = (pageNo - 1) * pageSize;
      return {
        content: all.slice(start, start + pageSize),
        totalElements: all.length,
      };
    }
    const res = await httpPageInpatientDoctorStationAdviceCatalog({
      encounterId,
      patientId,
      organizationUnitId,
      keyword: keyword || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      productTableNames: tables,
      categoryEnum,
      positiveQuantityOnly: isMed ? params.positiveQuantityOnly : false,
      excludeMedicationCategoryCodes: isMed
        ? [...WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES]
        : undefined,
    });
    const { content: rawRows, totalCount } =
      parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
    return {
      content: rawRows.map((row) => mapCatalogRowToOption(row)),
      totalElements: totalCount,
    };
  } catch {
    return { content: [] as AdviceProductOption[], totalElements: 0 };
  }
}

/**
 * 麻醉选品排序键：接口 sortOrder 优先，否则院点编码置顶表，再回落无键。
 *
 * @param row 选品行
 */
function resolveAnesthCatalogSortKey(row: AdviceProductOption): number | null {
  if (row.sortOrder !== null && row.sortOrder !== undefined && Number.isFinite(Number(row.sortOrder))) {
    return Number(row.sortOrder);
  }
  const code = String(row.code ?? '').trim();
  if (!code) {
    return null;
  }
  const fromConfig = ANESTH_CATALOG_BUS_NO_SORT_PRIORITY[code];
  if (fromConfig !== null && fromConfig !== undefined && Number.isFinite(Number(fromConfig))) {
    return Number(fromConfig);
  }
  return null;
}

/**
 * 麻醉选品下拉排序：有排序键的在前（升序）；都无或同号时保持原序。
 *
 * @param a 左行
 * @param b 右行
 * @param aIndex 左行原下标
 * @param bIndex 右行原下标
 */
function compareAnesthCatalogPickOrder(
  a: AdviceProductOption,
  b: AdviceProductOption,
  aIndex: number,
  bIndex: number,
): number {
  const aKey = resolveAnesthCatalogSortKey(a);
  const bKey = resolveAnesthCatalogSortKey(b);
  if (aKey !== null && bKey !== null) {
    const byOrder = aKey - bKey;
    if (byOrder !== 0) {
      return byOrder;
    }
  } else if (aKey !== null) {
    return -1;
  } else if (bKey !== null) {
    return 1;
  }
  return aIndex - bIndex;
}

/**
 * 从计量带组装可选总量单位（对齐临嘱总量单位下拉）。
 *
 * @param bands 目录计量带
 */
function buildUnitOptionsFromBands(
  bands: NonNullable<AdviceProductOption['unitBands']>,
): Array<{ value: string; label: string }> {
  const options: Array<{ value: string; label: string }> = [];
  const seen = new Set<string>();
  for (const kind of ['PACKAGE', 'SALES', 'SPLIT'] as const) {
    const code = unitCodeByBandKind(bands, kind);
    if (!code || seen.has(code)) {
      continue;
    }
    seen.add(code);
    const name = unitNameByBandKind(bands, kind) || code;
    options.push({ value: code, label: name });
  }
  return options;
}

/**
 * 临嘱目录行 → 申请明细命中。
 * 药品：按计量带回填总量单位/剂量单位/规格/单价（禁止把 unitCode 数字码当展示名）。
 *
 * @param row 医嘱目录选项
 * @param asMedication true=药品定义，false=诊疗 AD
 */
function catalogRowToHit(row: AdviceProductOption, asMedication: boolean): PickHit {
  const defId = preserveApiIdForRequest(row.productDefinitionId) ?? '';
  const name = String(row.name ?? '').trim() || String(row.code ?? '').trim();
  const spec = String(row.spec ?? '').trim() || undefined;
  if (!asMedication) {
    const uCode = String(row.defaultUnitCode ?? '').trim() || '次';
    const uName = String(row.defaultUnitName ?? '').trim() || String(row.unitName ?? '').trim() || uCode;
    return {
      id: defId,
      name,
      extra: String(row.code ?? '').trim() || undefined,
      displaySpec: spec,
      unitCode: uCode,
      unitName: uName,
      activityDefinitionId: defId,
      rateCode: String(row.rateCode ?? '').trim() || undefined,
      unitPrice: row.price,
    };
  }
  const bands = row.unitBands ?? [];
  const partAttr = String(row.partAttributeEnum ?? '').trim();
  const defaultKind = preferredTotalUnitBandKindFromOrderCalcRule(partAttr);
  const totalUnitCode =
    defaultTotalAmountUnitCode(bands, defaultKind) ||
    unitCodeByBandKind(bands, 'SALES') ||
    unitCodeByBandKind(bands, 'PACKAGE') ||
    unitCodeByBandKind(bands, 'SPLIT') ||
    String(row.defaultUnitCode ?? '').trim() ||
    'U';
  const totalBand = findBandByUnitCode(bands, totalUnitCode);
  const totalUnitName =
    String(totalBand?.unitName ?? '').trim() ||
    String(row.defaultUnitName ?? '').trim() ||
    String(row.unitName ?? '').trim() ||
    totalUnitCode;
  const doseUnitCode =
    unitCodeByBandKind(bands, 'DOSE') ||
    unitCodeByBandKind(bands, 'SPLIT') ||
    totalUnitCode;
  const doseUnitName =
    unitNameByBandKind(bands, 'DOSE') ||
    unitNameByBandKind(bands, 'SPLIT') ||
    doseUnitCode;
  const unitOptions = buildUnitOptionsFromBands(bands);
  if (unitOptions.length === 0) {
    unitOptions.push({ value: totalUnitCode, label: totalUnitName });
  }
  const doseRaw = Number(row.dose);
  return {
    id: defId,
    name,
    extra: String(row.code ?? '').trim() || undefined,
    displaySpec: spec,
    unitCode: totalUnitCode,
    unitName: totalUnitName,
    medicationDefinitionId: defId,
    rateCode: String(row.rateCode ?? '').trim() || undefined,
    methodCode: String(row.methodCode ?? '').trim() || undefined,
    dose: Number.isFinite(doseRaw) && doseRaw > 0 ? doseRaw : undefined,
    doseUnitCode,
    doseUnitName,
    unitPrice: row.price,
    unitOptions,
  };
}

/** 点选术式行加入明细；返回 true 关闭下拉 */
async function onProcedurePickBeforeClose(item: Record<string, unknown>) {
  const id = String(item.procedureDefinitionId ?? item.id ?? '').trim();
  const name = String(item.name ?? '').trim();
  addProcedureHit({
    id,
    name: name || String(item.procedureCode ?? '').trim(),
    procedureDefinitionId: id,
  });
  return true;
}

/** 主明细区：临嘱选品点选后入行并清空，便于连续加项（麻醉侧专用） */
async function onAdviceProductSelect(row: AdviceProductOption) {
  if (pickKind.value === 'medication') {
    addAnesthHit(catalogRowToHit(row, true), true);
  } else {
    addAnesthHit(catalogRowToHit(row, false), false);
  }
  await nextTick();
  pickSelectValue.value = '';
}

/** 嵌套麻醉区：临嘱选品点选后入麻醉明细并清空 */
async function onNestAdviceProductSelect(row: AdviceProductOption) {
  addAnesthHit(
    catalogRowToHit(row, nestPickKind.value === 'medication'),
    nestPickKind.value === 'medication',
  );
  await nextTick();
  nestPickSelectValue.value = '';
}

/** SelectTable 会回写选中值，选完立刻清空以继续加项 */
async function onProcedurePickChange() {
  await nextTick();
  pickSelectValue.value = '';
}

/** 「+ 添加手术」：引导到搜索框点选术式（点选即入行） */
function focusProcedurePickHint() {
  if (!editable.value) {
    return;
  }
  ElMessage.info('请在左侧搜索框中点选术式加入明细');
}

/**
 * 追加术式行（医生站手术开立；不计费）。
 *
 * @param hit 术式选品命中
 */
function addProcedureHit(hit: PickHit) {
  if (!hit.procedureDefinitionId) {
    ElMessage.warning('请选择手术术式');
    return;
  }
  const hasMain = surgeryLines.value.some((line) => line.mainProcedureFlag);
  const nextSort = nextSurgerySortNumber();
  surgeryLines.value.push({
    lineKindCode: 'PROCEDURE',
    procedureDefinitionId: hit.procedureDefinitionId,
    quantity: 1,
    unitCode: '次',
    // 本质临嘱：默认「立即」（与临时医嘱同口径）
    rateCode: immediateRateCode() || undefined,
    sortNumber: nextSort,
    mainProcedureFlag: !hasMain,
    displayName: hit.name,
  });
}

/**
 * 下一行排序号（术式与嘱托共用递增）。
 */
function nextSurgerySortNumber() {
  let max = 0;
  for (const line of surgeryLines.value) {
    const n = Number(line.sortNumber ?? 0);
    if (n > max) {
      max = n;
    }
  }
  return max + 1;
}

/**
 * 术式行选项（供嘱托选择挂靠父术式）。
 */
const procedureParentOptions = computed(() =>
  surgeryLines.value
    .filter((line) => line.lineKindCode === 'PROCEDURE')
    .map((line) => ({
      value: line.sortNumber as number,
      label: line.displayName || `术式#${line.sortNumber}`,
    })),
);

/**
 * 追加麻醉明细行（独立麻醉页或手术嵌套区共用；禁止附加）。
 *
 * @param hit 选品命中
 * @param asMedication 麻醉药品；false 为麻醉项目
 */
function addAnesthHit(hit: PickHit, asMedication: boolean) {
  if (asMedication) {
    if (!hit.medicationDefinitionId) {
      ElMessage.warning('请选择麻醉药品');
      return;
    }
    anesthesiaLines.value.push({
      lineKindCode: 'MEDICATION',
      medicationDefinitionId: hit.medicationDefinitionId,
      quantity: 1,
      unitCode: hit.unitCode || 'U',
      unitName: hit.unitName || hit.unitCode || 'U',
      unitOptions: hit.unitOptions,
      // 频次固定默认「立即」（明细表不再展示频次列）
      rateCode: immediateRateCode() || undefined,
      // 麻醉药品默认静脉注射；不用目录 methodCode（常见静脉滴注）
      methodCode: defaultAnesthMethodCode(),
      dose: hit.dose,
      doseUnitCode: hit.doseUnitCode || hit.unitCode || 'U',
      doseUnitName: hit.doseUnitName || hit.doseUnitCode || hit.unitName,
      sortNumber: anesthesiaLines.value.length + 1,
      displayName: hit.name,
      displaySpec: hit.displaySpec,
      unitPrice: hit.unitPrice,
    });
    return;
  }
  if (!hit.activityDefinitionId) {
    ElMessage.warning('请选择麻醉项目');
    return;
  }
  anesthesiaLines.value.push({
    lineKindCode: 'ANESTHESIA',
    activityDefinitionId: hit.activityDefinitionId,
    quantity: 1,
    unitCode: hit.unitCode || '次',
    unitName: hit.unitName || hit.unitCode || '次',
    // 频次固定默认「立即」（明细表不再展示频次列）
    rateCode: immediateRateCode() || undefined,
    sortNumber: anesthesiaLines.value.length + 1,
    displayName: hit.name,
    displaySpec: hit.displaySpec,
    unitPrice: hit.unitPrice,
  });
}

/** 药品行切换总量单位时同步展示名 */
function onMedUnitChange(row: AnesthesiaApplyFormLineRequest, code: string) {
  row.unitCode = code;
  const hit = (row.unitOptions ?? []).find((opt) => opt.value === code);
  row.unitName = hit?.label || code;
}

/**
 * 剂量单位下拉：按展示名或码模糊过滤（如搜 ml / ug / 毫升）。
 *
 * @param query 用户输入
 */
function filterDoseUnitOptions(query: string) {
  const q = String(query ?? '')
    .trim()
    .toLowerCase();
  if (!q) {
    filteredDoseUnitOptions.value = doseUnitOptions.value;
    return;
  }
  filteredDoseUnitOptions.value = doseUnitOptions.value.filter((opt) => {
    const label = String(opt.label ?? '')
      .trim()
      .toLowerCase();
    const value = String(opt.value ?? '')
      .trim()
      .toLowerCase();
    return label.includes(q) || value.includes(q);
  });
}

/**
 * 药品行切换单次剂量单位；仅改 doseUnit*，不重算数量/总量。
 *
 * @param row 麻醉药品行
 * @param code 剂量单位码
 */
function onDoseUnitChange(row: AnesthesiaApplyFormLineRequest, code: string) {
  const next = String(code ?? '').trim();
  row.doseUnitCode = next;
  const hit = doseUnitOptions.value.find((opt) => String(opt.value) === next);
  if (hit?.label) {
    row.doseUnitName = String(hit.label);
    return;
  }
  row.doseUnitName = next;
}

/**
 * 行级剂量单位选项：过滤结果 + 当前已选（防过滤/历史码导致选中值空白）。
 *
 * @param row 麻醉药品行
 */
function doseUnitSelectOptionsForRow(row: AnesthesiaApplyFormLineRequest): ApiOptionItem[] {
  const base = filteredDoseUnitOptions.value;
  const code = String(row.doseUnitCode ?? '').trim();
  if (!code) {
    return base;
  }
  if (base.some((opt) => String(opt.value) === code)) {
    return base;
  }
  const fromAll = doseUnitOptions.value.find((opt) => String(opt.value) === code);
  if (fromAll) {
    return [fromAll, ...base];
  }
  return [
    {
      value: code,
      label: String(row.doseUnitName ?? '').trim() || code,
    },
    ...base,
  ];
}

/** 同一张手术单只保留一行主手术（按术式 sortNumber） */
function markMainProcedure(proc: SurgeryApplyFormLineRequest) {
  const sort = proc.sortNumber;
  surgeryLines.value = surgeryLines.value.map((line) => ({
    ...line,
    mainProcedureFlag:
      line.lineKindCode === 'PROCEDURE' && sort != null && line.sortNumber === sort,
  }));
}

/** 麻醉明细删行 */
function removeLine(index: number) {
  anesthesiaLines.value.splice(index, 1);
}

/** 删除术式及其挂靠的术前嘱托 */
function removeProcedure(proc: SurgeryApplyFormLineRequest) {
  const sort = proc.sortNumber;
  surgeryLines.value = surgeryLines.value.filter((line) => {
    if (line.lineKindCode === 'PROCEDURE' && line.sortNumber === sort) {
      return false;
    }
    if (line.lineKindCode === 'ENTRUST' && line.basedOnProcedureSortNumber === sort) {
      return false;
    }
    return true;
  });
  if (editingEntrustParentSort.value === sort) {
    editingEntrustParentSort.value = null;
  }
}

/** 取术式下挂靠的术前嘱托行（一术式一行） */
function findEntrustForProcedure(parentSort?: number) {
  if (parentSort == null) {
    return undefined;
  }
  return surgeryLines.value.find(
    (line) =>
      line.lineKindCode === 'ENTRUST' && line.basedOnProcedureSortNumber === parentSort,
  );
}

/** 术式下嘱托展示文案 */
function resolveEntrustDisplay(parentSort?: number) {
  const text = String(findEntrustForProcedure(parentSort)?.entrustContent ?? '').trim();
  return text;
}

/** 打开术式下的术前嘱托编辑面板 */
function openEntrustEditor(proc: SurgeryApplyFormLineRequest) {
  const sort = proc.sortNumber;
  if (sort == null) {
    return;
  }
  editingEntrustParentSort.value = sort;
  const existing = findEntrustForProcedure(sort);
  draftEntrustContent.value = String(existing?.entrustContent ?? '').trim();
  draftEntrustBasedOn.value =
    existing?.basedOnProcedureSortNumber != null
      ? existing.basedOnProcedureSortNumber
      : sort;
}

/** 点选快捷短语追加到嘱托草稿 */
function appendEntrustQuickTag(tag: string) {
  const phrase = String(tag ?? '').trim();
  if (!phrase) {
    return;
  }
  const cur = draftEntrustContent.value.trim();
  if (!cur) {
    draftEntrustContent.value = phrase;
    return;
  }
  if (cur.includes(phrase)) {
    return;
  }
  draftEntrustContent.value = `${cur}，${phrase}`;
}

/** 取消嘱托编辑 */
function cancelEntrustEditor() {
  editingEntrustParentSort.value = null;
  draftEntrustContent.value = '';
  draftEntrustBasedOn.value = undefined;
}

/** 保存术式下术前嘱托（仅写本地行，整单仍走底部保存） */
function saveEntrustEditor() {
  const parentSort = editingEntrustParentSort.value;
  if (parentSort == null) {
    return;
  }
  const content = draftEntrustContent.value.trim();
  if (!content) {
    ElMessage.warning('请填写术前嘱托内容');
    return;
  }
  const basedOn = draftEntrustBasedOn.value ?? parentSort;
  if (basedOn == null || !procedureParentOptions.value.some((opt) => opt.value === basedOn)) {
    ElMessage.warning('请选择挂靠术式');
    return;
  }
  const existing = findEntrustForProcedure(parentSort);
  if (existing) {
    existing.entrustContent = content;
    existing.basedOnProcedureSortNumber = basedOn;
    existing.displayName = content;
  } else {
    surgeryLines.value.push({
      lineKindCode: 'ENTRUST',
      entrustContent: content,
      basedOnProcedureSortNumber: basedOn,
      quantity: 1,
      unitCode: '次',
      rateCode: immediateRateCode() || undefined,
      sortNumber: nextSurgerySortNumber(),
      displayName: content,
    });
  }
  cancelEntrustEditor();
}

function removeNestAnesthLine(index: number) {
  anesthesiaLines.value.splice(index, 1);
}

/** 麻醉明细保存门禁文案；通过返回空串（频次已默认立即，不再校验手选） */
function validateAnesthesiaLinesForSave(rows: AnesthesiaApplyFormLineRequest[]) {
  for (const line of rows) {
    if (line.lineKindCode === 'MEDICATION') {
      if (!line.medicationDefinitionId) {
        continue;
      }
      if (!String(line.methodCode ?? '').trim()) {
        return '麻醉药品请选择用法';
      }
      if (line.dose == null || Number(line.dose) <= 0) {
        return '麻醉药品请填写剂量';
      }
      if (!String(line.doseUnitCode ?? '').trim()) {
        return '麻醉药品请选择剂量单位';
      }
    }
  }
  return '';
}

/**
 * 麻醉明细 → 保存载荷；仅 ANESTHESIA / MEDICATION（过滤历史附加）。
 *
 * @param rows 编辑区行
 */
function mapAnesthesiaLinesForSave(rows: AnesthesiaApplyFormLineRequest[]) {
  return rows
    .filter((line) => line.lineKindCode !== 'SURCHARGE')
    .filter((line) =>
      line.lineKindCode === 'MEDICATION'
        ? Boolean(line.medicationDefinitionId)
        : Boolean(line.activityDefinitionId),
    )
    .map((line, index) => ({
      lineKindCode: line.lineKindCode === 'MEDICATION' ? ('MEDICATION' as const) : ('ANESTHESIA' as const),
      activityDefinitionId: line.activityDefinitionId,
      medicationDefinitionId: line.medicationDefinitionId,
      quantity: line.quantity,
      unitCode: line.unitCode,
      // 提交时补默认「立即」，避免历史空频次
      rateCode: String(line.rateCode ?? '').trim() || immediateRateCode() || undefined,
      methodCode: line.methodCode,
      dose: line.dose,
      doseUnitCode: line.doseUnitCode || line.unitCode,
      sortNumber: index + 1,
    }));
}

function lineIsMedication(row: { lineKindCode?: string }) {
  return row.lineKindCode === 'MEDICATION';
}

/** 保存草稿。已有单号时带旧号，成功后换成返回的新号 */
async function saveForm() {
  if (!editable.value || !encounterReady.value) {
    return;
  }
  // 开立第一关：拟手术时间不得越出入院～出院窗
  const plannedTimeError = validatePlannedProcedureTime(procedureTime.value, {
    admissionTime: props.admissionTime,
    dischargeTime: props.dischargeTime,
  });
  if (plannedTimeError) {
    ElMessage.warning(plannedTimeError);
    return;
  }
  const encounterId = props.encounterId.trim();
  saving.value = true;
  try {
    if (isSurgery.value) {
      if (!String(surgeonId.value ?? '').trim()) {
        ElMessage.warning('请选择主刀');
        return;
      }
      if (nestChecked.value) {
        if (!String(anesthetistId.value ?? '').trim()) {
          ElMessage.warning('请选择麻醉医师');
          return;
        }
        const nestGate = validateAnesthesiaLinesForSave(anesthesiaLines.value);
        if (nestGate) {
          ElMessage.warning(nestGate);
          return;
        }
      }
      // 先重排术式 sortNumber，再映射嘱托 basedOn；附加加收由护士划价
      const procedureSource = surgeryLines.value.filter(
        (line) => line.lineKindCode === 'PROCEDURE' && Boolean(line.procedureDefinitionId),
      );
      if (procedureSource.length === 0) {
        ElMessage.warning('请先选择至少一项手术术式');
        return;
      }
      const sortRemap = new Map<number, number>();
      const procedurePayload = procedureSource.map((line, index) => {
        const newSort = index + 1;
        if (line.sortNumber != null) {
          sortRemap.set(line.sortNumber, newSort);
        }
        return {
          lineKindCode: 'PROCEDURE' as const,
          procedureDefinitionId: line.procedureDefinitionId,
          quantity: line.quantity,
          unitCode: line.unitCode,
          // 提交时补默认「立即」
          rateCode: String(line.rateCode ?? '').trim() || immediateRateCode() || undefined,
          sortNumber: newSort,
          mainProcedureFlag: line.mainProcedureFlag,
        };
      });
      const entrustPayload: Array<{
        lineKindCode: 'ENTRUST';
        entrustContent: string;
        basedOnProcedureSortNumber: number;
        quantity: number;
        unitCode: string;
        rateCode?: string;
        sortNumber: number;
      }> = [];
      let entrustSort = procedurePayload.length;
      for (const line of surgeryLines.value) {
        if (line.lineKindCode !== 'ENTRUST') {
          continue;
        }
        const content = String(line.entrustContent ?? '').trim();
        if (!content) {
          ElMessage.warning('请填写术前嘱托内容');
          return;
        }
        const oldParent = line.basedOnProcedureSortNumber;
        const parentSort =
          oldParent != null && sortRemap.has(oldParent)
            ? sortRemap.get(oldParent)!
            : procedurePayload[0]?.sortNumber;
        if (parentSort == null) {
          ElMessage.warning('术前嘱托须挂靠一条术式');
          return;
        }
        entrustSort = entrustSort + 1;
        entrustPayload.push({
          lineKindCode: 'ENTRUST',
          entrustContent: content,
          basedOnProcedureSortNumber: parentSort,
          quantity: line.quantity || 1,
          unitCode: line.unitCode || '次',
          // 提交时补默认「立即」
          rateCode: String(line.rateCode ?? '').trim() || immediateRateCode() || undefined,
          sortNumber: entrustSort,
        });
      }
      const lines = [...procedurePayload, ...entrustPayload];
      let nestLines: AnesthesiaApplyFormLineRequest[] | undefined;
      if (nestChecked.value) {
        nestLines = mapAnesthesiaLinesForSave(anesthesiaLines.value);
        if (!nestLines.length) {
          ElMessage.warning('已勾选需要麻醉，请添加至少一项麻醉项目或药品');
          return;
        }
      }
      const res = await httpSaveSurgeryApplyForm({
        encounterId,
        previousPrescriptionNo: activePrescriptionNo.value || undefined,
        lines,
        planned: buildPlanned(),
        nestAnesthesia: nestChecked.value || undefined,
        anesthesiaLines: nestLines,
      });
      const nextNo = res.prescriptionNo ?? '';
      nestedAnesthesiaNo.value = res.nestedAnesthesiaPrescriptionNo ?? '';
      ElMessage.success(
        nestedAnesthesiaNo.value ? '手术申请已保存，并已生成麻醉申请' : '手术申请已保存',
      );
      await reload();
      if (nextNo) {
        await openForm(nextNo);
      }
      return;
    }
    if (!String(anesthetistId.value ?? '').trim()) {
      ElMessage.warning('请选择麻醉医师');
      return;
    }
    const anesthGate = validateAnesthesiaLinesForSave(anesthesiaLines.value);
    if (anesthGate) {
      ElMessage.warning(anesthGate);
      return;
    }
    const lines = mapAnesthesiaLinesForSave(anesthesiaLines.value);
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

/** 删除草稿申请单（手术单会一并清理嵌套麻醉草稿） */
async function deleteForm() {
  if (!canDelete.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      isSurgery.value
        ? '删除后不可恢复；若已关联麻醉申请草稿将一并删除。'
        : '删除后不可恢复，确定删除该麻醉申请单？',
      '删除申请单',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  const encounterId = props.encounterId.trim();
  const prescriptionNo = activePrescriptionNo.value;
  saving.value = true;
  try {
    await (isSurgery.value
      ? httpDeleteSurgeryApplyForm({ encounterId, prescriptionNo })
      : httpDeleteAnesthesiaApplyForm({ encounterId, prescriptionNo }));
    ElMessage.success('已删除');
    resetEditor();
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
              <SectionTitle v-if="!isSurgery" title="麻醉安排" />
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
                  <el-form-item v-if="isSurgery" label="需要麻醉">
                    <div class="nest-toggle">
                      <el-checkbox
                        :model-value="nestChecked"
                        :disabled="!editable"
                        @change="onNestCheckedChange"
                      >
                        关联麻醉申请
                      </el-checkbox>
                      <div v-if="nestedAnesthesiaNo" class="nest-hint">
                        已关联 {{ nestedAnesthesiaNo }}
                      </div>
                    </div>
                  </el-form-item>
                  <el-form-item v-if="!isSurgery" label="麻醉方法">
                    <el-select
                      v-model="anesthesiaMethodCode"
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in methodOptions"
                        :key="String(opt.value)"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="!isSurgery" required label="麻醉医师">
                    <el-select
                      v-model="anesthetistId"
                      filterable
                      clearable
                      placeholder="请选择"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in anesthetistSelectOptions"
                        :key="'an-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="!isSurgery" label="关联手术" class="field-span-3">
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

            <!-- 手术团队；麻醉医师已并入上方麻醉安排 -->
            <section v-if="isSurgery" class="sheet-section">
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
                        v-for="opt in nurseSelectOptions"
                        :key="'n-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                  </el-form-item>
                </div>
              </el-form>
            </section>

            <!-- 明细：手术=术式卡片+嵌套嘱托；麻醉=扁平表 -->
            <section class="sheet-section">
              <div class="detail-head">
                <SectionTitle :title="isSurgery ? '手术申请' : '申请明细'" />
                <span v-if="isSurgery" class="detail-head__count">
                  共 {{ surgeryProcedureCount }} 项
                </span>
              </div>

              <template v-if="isSurgery">
                <div class="mb-2 mt-2 flex items-center gap-2">
                  <SelectTable
                    v-model="pickSelectValue"
                    class="min-w-0 flex-1"
                    :remote-method="queryProcedurePickOptions"
                    :columns="procedurePickColumns"
                    value-key="id"
                    label-key="name"
                    :placeholder="pickPlaceholder"
                    :disabled="!editable"
                    :clearable="true"
                    :page-size="20"
                    panel-width="960"
                    :before-close="onProcedurePickBeforeClose"
                    @change="onProcedurePickChange"
                  />
                  <el-button type="primary" :disabled="!editable" @click="focusProcedurePickHint">
                    + 添加手术
                  </el-button>
                </div>

                <el-table
                  :data="surgeryProcedureRows"
                  class="sheet-table proc-sheet-table"
                  table-layout="fixed"
                  row-key="sortNumber"
                  :expand-row-keys="surgeryExpandRowKeys"
                  empty-text="从上方搜索并点选加入手术明细"
                >
                  <el-table-column type="expand" width="1" class-name="proc-expand-col">
                    <template #default="{ row: proc }">
                      <div class="proc-sub">
                        <span class="proc-sub__rail" aria-hidden="true" />
                        <!-- 空态：无边框，仅一行文案+链接 -->
                        <div
                          v-if="
                            editingEntrustParentSort !== proc.sortNumber &&
                            !resolveEntrustDisplay(proc.sortNumber)
                          "
                          class="entrust-empty"
                        >
                          <span class="entrust-empty__label">嘱托信息：</span>
                          <el-button
                            link
                            type="primary"
                            :disabled="!editable"
                            @click="openEntrustEditor(proc)"
                          >
                            + 添加术前嘱托
                          </el-button>
                        </div>
                        <div
                          v-else-if="editingEntrustParentSort !== proc.sortNumber"
                          class="entrust-summary"
                        >
                          <span class="entrust-summary__label">嘱托信息：</span>
                          <span class="entrust-summary__rate">立即</span>
                          <span class="entrust-summary__sep">·</span>
                          <span
                            class="entrust-summary__text"
                            :title="resolveEntrustDisplay(proc.sortNumber)"
                          >
                            {{ resolveEntrustDisplay(proc.sortNumber) }}
                          </span>
                          <el-button
                            link
                            type="primary"
                            class="entrust-summary__edit"
                            :disabled="!editable"
                            @click="openEntrustEditor(proc)"
                          >
                            编辑
                          </el-button>
                        </div>
                        <div v-else class="entrust-card">
                          <div class="entrust-card__head">
                            <span class="entrust-card__title">嘱托信息</span>
                            <span
                              v-if="surgeryProcedureCount > 1"
                              class="entrust-card__hint"
                            >
                              挂靠：{{
                                procedureParentOptions.find(
                                  (o) => o.value === draftEntrustBasedOn,
                                )?.label ||
                                proc.displayName ||
                                '当前术式'
                              }}
                            </span>
                          </div>
                          <div
                            v-if="surgeryProcedureCount > 1"
                            class="entrust-card__row"
                          >
                            <span class="entrust-card__label">挂靠术式</span>
                            <el-select
                              v-model="draftEntrustBasedOn"
                              class="entrust-card__control"
                              :disabled="!editable"
                              placeholder="选择术式"
                            >
                              <el-option
                                v-for="opt in procedureParentOptions"
                                :key="'be-' + opt.value"
                                :label="opt.label"
                                :value="opt.value"
                              />
                            </el-select>
                          </div>
                          <div class="entrust-card__row">
                            <span class="entrust-card__label">频次</span>
                            <el-select
                              model-value="立即"
                              disabled
                              class="entrust-card__control entrust-card__control--rate"
                            >
                              <el-option label="立即" value="立即" />
                            </el-select>
                          </div>
                          <div class="entrust-card__row entrust-card__row--content">
                            <span class="entrust-card__label">嘱托内容</span>
                            <div class="entrust-card__content">
                              <el-input
                                v-model="draftEntrustContent"
                                type="textarea"
                                :rows="3"
                                maxlength="500"
                                show-word-limit
                                :disabled="!editable"
                                placeholder="请输入嘱托内容（必填）；可点选下方短语快速带入"
                              />
                              <div class="entrust-card__tags">
                                <button
                                  v-for="tag in ENTRUST_QUICK_TAGS"
                                  :key="tag"
                                  type="button"
                                  class="proc-tag"
                                  :disabled="!editable"
                                  @click="appendEntrustQuickTag(tag)"
                                >
                                  + {{ tag }}
                                </button>
                              </div>
                            </div>
                          </div>
                          <div class="entrust-card__actions">
                            <el-button @click="cancelEntrustEditor">取消</el-button>
                            <el-button
                              type="primary"
                              :disabled="!editable"
                              @click="saveEntrustEditor"
                            >
                              保存嘱托
                            </el-button>
                          </div>
                        </div>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型" width="72" align="center">
                    <template #default>
                      <span class="kind-badge kind-badge--main">手术</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="项目" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: proc }">
                      {{ proc.displayName || proc.procedureDefinitionId || '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="数量" width="140" align="center" class-name="qty-col">
                    <template #default="{ row: proc }">
                      <el-input-number
                        v-model="proc.quantity"
                        :min="1"
                        :disabled="!editable"
                        size="small"
                        controls-position="right"
                        class="qty-input"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="单位" width="72" align="center">
                    <template #default="{ row: proc }">
                      {{ proc.unitName || proc.unitCode || '次' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="主手术" width="72" align="center">
                    <template #default="{ row: proc }">
                      <el-checkbox
                        :model-value="proc.mainProcedureFlag === true"
                        :disabled="!editable"
                        @change="() => markMainProcedure(proc)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="72" align="center">
                    <template #default="{ row: proc }">
                      <el-button
                        link
                        type="danger"
                        :disabled="!editable"
                        @click="removeProcedure(proc)"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>

              <template v-else>
                <div class="mb-2 mt-2 flex items-center gap-2">
                  <AdviceOrderSelect
                    v-model="pickSelectValue"
                    class="min-w-0 flex-1"
                    :placeholder="pickPlaceholder"
                    :columns="adviceProductColumns"
                    :lock-category="adviceLockCategory"
                    :category-enum="adviceCategoryEnum"
                    :remote-method="queryAdviceCatalogOptions"
                    :disabled="!editable"
                    @select="onAdviceProductSelect"
                  >
                    <template #toolbar-leading>
                      <el-button
                        size="small"
                        :type="pickKind === 'main' ? 'primary' : 'default'"
                        :disabled="!editable"
                        @click="setPickKind('main')"
                      >
                        麻醉项目
                      </el-button>
                      <el-button
                        size="small"
                        :type="pickKind === 'medication' ? 'primary' : 'default'"
                        :disabled="!editable"
                        @click="setPickKind('medication')"
                      >
                        麻醉药品
                      </el-button>
                    </template>
                  </AdviceOrderSelect>
                </div>
                <el-table
                  :data="activeLines"
                  class="sheet-table"
                  table-layout="fixed"
                  empty-text="从上方搜索并点选加入明细"
                >
                  <el-table-column label="类型" width="72" align="center">
                    <template #default="{ row }">
                      <span
                        class="kind-badge"
                        :class="
                          row.lineKindCode === 'SURCHARGE'
                            ? 'kind-badge--extra'
                            : row.lineKindCode === 'MEDICATION'
                              ? 'kind-badge--med'
                              : 'kind-badge--main'
                        "
                      >
                        {{ lineKindText(row.lineKindCode) }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="项目" min-width="140" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{
                        row.displayName ||
                        row.activityDefinitionId ||
                        row.medicationDefinitionId
                      }}
                    </template>
                  </el-table-column>
                  <el-table-column label="规格" min-width="100" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ lineIsMedication(row) ? row.displaySpec || '—' : '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="数量" width="140" align="center" class-name="qty-col">
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
                  <el-table-column label="单位" width="100" align="center">
                    <template #default="{ row }">
                      <el-select
                        v-if="lineIsMedication(row) && row.unitOptions?.length"
                        :model-value="row.unitCode"
                        size="small"
                        class="w-full"
                        :disabled="!editable"
                        @change="(v: string) => onMedUnitChange(row, v)"
                      >
                        <el-option
                          v-for="opt in row.unitOptions"
                          :key="'u-' + opt.value"
                          :label="opt.label"
                          :value="opt.value"
                        />
                      </el-select>
                      <span v-else>{{ row.unitName || row.unitCode || '次' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="用法" min-width="100">
                    <template #default="{ row }">
                      <el-select
                        v-if="lineIsMedication(row)"
                        v-model="row.methodCode"
                        clearable
                        filterable
                        placeholder="必选"
                        size="small"
                        class="w-full"
                        :disabled="!editable"
                      >
                        <el-option
                          v-for="opt in medRouteOptions"
                          :key="'m-' + opt.value"
                          :label="opt.label"
                          :value="String(opt.value)"
                        />
                      </el-select>
                      <span v-else>—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="剂量" width="220" align="center" class-name="dose-col">
                    <template #default="{ row }">
                      <div v-if="lineIsMedication(row)" class="dose-cell">
                        <el-input-number
                          v-model="row.dose"
                          :min="0"
                          :step="0.1"
                          size="small"
                          controls-position="right"
                          class="dose-qty-input"
                          :disabled="!editable"
                        />
                        <el-select
                          :model-value="row.doseUnitCode"
                          filterable
                          clearable
                          placeholder="单位"
                          size="small"
                          class="dose-unit-select"
                          :disabled="!editable"
                          :filter-method="filterDoseUnitOptions"
                          @visible-change="
                            (open: boolean) => {
                              if (open) filterDoseUnitOptions('');
                            }
                          "
                          @change="(v: string) => onDoseUnitChange(row, v)"
                        >
                          <el-option
                            v-for="opt in doseUnitSelectOptionsForRow(row)"
                            :key="'du-' + opt.value"
                            :label="String(opt.label ?? opt.value)"
                            :value="String(opt.value)"
                          />
                        </el-select>
                      </div>
                      <span v-else>—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单价" width="72" align="center">
                    <template #default="{ row }">
                      {{
                        row.unitPrice != null && Number.isFinite(Number(row.unitPrice))
                          ? Number(row.unitPrice).toFixed(2)
                          : '—'
                      }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="72" align="center">
                    <template #default="{ $index }">
                      <el-button
                        link
                        type="danger"
                        :disabled="!editable"
                        @click="removeLine($index)"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
            </section>

            <!-- 手术页勾选需要麻醉：方法/医师/选品同一行，明细表精简列 -->
            <section v-if="showNestAnesthesia" class="sheet-section">
              <SectionTitle title="麻醉申请" />
              <el-form label-position="top" class="sheet-form anesth-bar mt-2">
                <el-form-item label="麻醉方法">
                  <el-select
                    v-model="anesthesiaMethodCode"
                    clearable
                    placeholder="请选择"
                    class="w-full"
                    :disabled="!editable"
                  >
                    <el-option
                      v-for="opt in methodOptions"
                      :key="'nest-m-' + String(opt.value)"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item required label="麻醉医师">
                  <el-select
                    v-model="anesthetistId"
                    filterable
                    clearable
                    placeholder="请选择"
                    class="w-full"
                    :disabled="!editable"
                  >
                    <el-option
                      v-for="opt in anesthetistSelectOptions"
                      :key="'nest-an-' + opt.value"
                      :label="opt.label"
                      :value="String(opt.value)"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="麻醉选品" class="anesth-bar__pick">
                  <AdviceOrderSelect
                    v-model="nestPickSelectValue"
                    class="anesth-bar__search"
                    :placeholder="nestPickPlaceholder"
                    :columns="adviceProductColumns"
                    :lock-category="nestAdviceLockCategory"
                    :category-enum="nestAdviceCategoryEnum"
                    :remote-method="queryAdviceCatalogOptions"
                    :disabled="!editable"
                    @select="onNestAdviceProductSelect"
                  >
                    <template #toolbar-leading>
                      <el-button
                        size="small"
                        :type="nestPickKind === 'main' ? 'primary' : 'default'"
                        :disabled="!editable"
                        @click="setNestPickKind('main')"
                      >
                        麻醉项目
                      </el-button>
                      <el-button
                        size="small"
                        :type="nestPickKind === 'medication' ? 'primary' : 'default'"
                        :disabled="!editable"
                        @click="setNestPickKind('medication')"
                      >
                        麻醉药品
                      </el-button>
                    </template>
                  </AdviceOrderSelect>
                </el-form-item>
              </el-form>
              <el-table
                :data="anesthesiaLines"
                class="sheet-table mt-2"
                table-layout="fixed"
                empty-text="从上方搜索并点选加入麻醉明细"
              >
                <el-table-column label="类型" width="72" align="center">
                  <template #default="{ row }">
                    <span
                      class="kind-badge"
                      :class="
                        row.lineKindCode === 'SURCHARGE'
                          ? 'kind-badge--extra'
                          : row.lineKindCode === 'MEDICATION'
                            ? 'kind-badge--med'
                            : 'kind-badge--main'
                      "
                    >
                      {{ lineKindText(row.lineKindCode) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="项目" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.displayName || row.activityDefinitionId || row.medicationDefinitionId }}
                  </template>
                </el-table-column>
                <el-table-column label="规格" min-width="100" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ lineIsMedication(row) ? row.displaySpec || '—' : '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="数量" width="140" align="center" class-name="qty-col">
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
                <el-table-column label="单位" width="100" align="center">
                  <template #default="{ row }">
                    <el-select
                      v-if="lineIsMedication(row) && row.unitOptions?.length"
                      :model-value="row.unitCode"
                      size="small"
                      class="w-full"
                      :disabled="!editable"
                      @change="(v: string) => onMedUnitChange(row, v)"
                    >
                      <el-option
                        v-for="opt in row.unitOptions"
                        :key="'nu-' + opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                    <span v-else>{{ row.unitName || row.unitCode || '次' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="用法" min-width="100">
                  <template #default="{ row }">
                    <el-select
                      v-if="lineIsMedication(row)"
                      v-model="row.methodCode"
                      clearable
                      filterable
                      placeholder="必选"
                      size="small"
                      class="w-full"
                      :disabled="!editable"
                    >
                      <el-option
                        v-for="opt in medRouteOptions"
                        :key="'nm-' + opt.value"
                        :label="opt.label"
                        :value="String(opt.value)"
                      />
                    </el-select>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="剂量" width="220" align="center" class-name="dose-col">
                  <template #default="{ row }">
                    <div v-if="lineIsMedication(row)" class="dose-cell">
                      <el-input-number
                        v-model="row.dose"
                        :min="0"
                        :step="0.1"
                        size="small"
                        controls-position="right"
                        class="dose-qty-input"
                        :disabled="!editable"
                      />
                      <el-select
                        :model-value="row.doseUnitCode"
                        filterable
                        clearable
                        placeholder="单位"
                        size="small"
                        class="dose-unit-select"
                        :disabled="!editable"
                        :filter-method="filterDoseUnitOptions"
                        @visible-change="
                          (open: boolean) => {
                            if (open) filterDoseUnitOptions('');
                          }
                        "
                        @change="(v: string) => onDoseUnitChange(row, v)"
                      >
                        <el-option
                          v-for="opt in doseUnitSelectOptionsForRow(row)"
                          :key="'ndu-' + opt.value"
                          :label="String(opt.label ?? opt.value)"
                          :value="String(opt.value)"
                        />
                      </el-select>
                    </div>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="单价" width="72" align="center">
                  <template #default="{ row }">
                    {{
                      row.unitPrice != null && Number.isFinite(Number(row.unitPrice))
                        ? Number(row.unitPrice).toFixed(2)
                        : '—'
                    }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="72" align="center">
                  <template #default="{ $index }">
                    <el-button
                      link
                      type="danger"
                      :disabled="!editable"
                      @click="removeNestAnesthLine($index)"
                    >
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
                  :placeholder="
                    isSurgery
                      ? '请输入备注（示例：术中需要超声。）'
                      : '请输入备注（示例：拟全麻。）'
                  "
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
          type="danger"
          plain
          :disabled="!canDelete"
          :loading="saving"
          @click="deleteForm"
        >
          删除
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

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-head__count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 隐藏展开列箭头，默认展开仅承载嘱托区 */
.proc-sheet-table :deep(.proc-expand-col) {
  width: 0 !important;
  padding: 0 !important;
  border: 0 !important;
}

.proc-sheet-table :deep(.proc-expand-col .cell) {
  display: none;
}

.proc-sheet-table :deep(.el-table__expanded-cell) {
  padding: 0 12px 8px 12px;
  background: var(--el-bg-color);
}

.proc-sub {
  display: flex;
  gap: 0;
  padding-top: 2px;
  align-items: stretch;
}

/* 图五：虚线加深，避免看不清 */
.proc-sub__rail {
  flex-shrink: 0;
  width: 64px;
  position: relative;
}

.proc-sub__rail::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 4px;
  left: 50%;
  width: 0;
  border-left: 1px dashed #94a3b8;
  transform: translateX(-50%);
}

.proc-sub__rail::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 12px;
  border-top: 1px dashed #94a3b8;
  transform: translateY(-50%);
}

/* 空嘱托 / 已填摘要：浅底区分主行 */
.entrust-empty,
.entrust-summary {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.entrust-empty {
  gap: 4px;
}

.entrust-empty__label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 700;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.entrust-summary {
  font-size: 13px;
  line-height: 22px;
}

.entrust-summary__label {
  flex-shrink: 0;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.entrust-summary__rate {
  flex-shrink: 0;
  color: var(--el-text-color-regular);
}

.entrust-summary__sep {
  flex-shrink: 0;
  color: var(--el-text-color-placeholder);
}

.entrust-summary__text {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
}

.entrust-summary__edit {
  flex-shrink: 0;
  margin-left: 0 !important;
}

/* 编辑：细边框卡 */
.entrust-card {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}

.entrust-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.entrust-card__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  line-height: 22px;
}

.entrust-card__title::after {
  content: '：';
}

.entrust-card__hint {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.entrust-card__row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  column-gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.entrust-card__row--content {
  align-items: start;
  margin-bottom: 0;
}

.entrust-card__label {
  width: 72px;
  padding-right: 8px;
  text-align: right;
  font-size: 14px;
  line-height: 32px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.entrust-card__label::after {
  content: '：';
  margin-left: 2px;
}

.entrust-card__row--content .entrust-card__label {
  line-height: 22px;
  padding-top: 6px;
}

.entrust-card__value {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.entrust-card__text {
  margin: 0;
  padding-top: 6px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.entrust-card__control {
  width: 100%;
  max-width: 240px;
}

.entrust-card__control--rate {
  max-width: 160px;
}

.entrust-card__content {
  min-width: 0;
  width: 100%;
}

.entrust-card__content :deep(.el-textarea) {
  width: 100%;
}

.entrust-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.proc-tag {
  padding: 2px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  background: var(--el-bg-color);
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.proc-tag:hover:not(:disabled) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.proc-tag:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.entrust-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
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

.field-span-3 {
  grid-column: span 3;
}

.nest-toggle {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 32px;
  justify-content: center;
}

.nest-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 嵌套麻醉：方法 / 医师 / 选品同一行 */
.anesth-bar {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) minmax(140px, 1fr) minmax(280px, 2.4fr);
  gap: 10px 12px;
  align-items: end;
}

.anesth-bar__pick :deep(.el-form-item__content) {
  min-width: 0;
}

.anesth-bar__search {
  min-width: 0;
  width: 100%;
}

.kind-badge {
  display: inline-block;
  min-width: 40px;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
}

.kind-badge--entrust {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.kind-badge--main {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.kind-badge--extra {
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.kind-badge--med {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.dose-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.dose-qty-input {
  width: 96px;
  flex-shrink: 0;
}

.dose-qty-input :deep(.el-input__wrapper) {
  padding-left: 6px;
  padding-right: 6px;
}

.dose-unit-select {
  width: 96px;
  flex-shrink: 0;
}

.sheet-table :deep(.dose-col .cell) {
  overflow: visible;
  padding-left: 6px;
  padding-right: 6px;
}

.sheet-table {
  width: 100%;
}

.sheet-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.sheet-table :deep(.qty-col .cell) {
  overflow: visible;
  padding-left: 8px;
  padding-right: 8px;
}

.qty-input {
  width: 112px;
}

.qty-input :deep(.el-input__wrapper) {
  padding-left: 8px;
  padding-right: 8px;
}

@media (max-width: 1100px) {
  .field-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .field-span-2 {
    grid-column: span 2;
  }

  .field-span-3 {
    grid-column: span 3;
  }

  .anesth-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .anesth-bar__pick {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .field-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field-span-2,
  .field-span-3 {
    grid-column: span 2;
  }

  .anesth-bar {
    grid-template-columns: 1fr;
  }
}
</style>
