<script setup lang="ts">
import type { KnowledgeBaseItem } from '#/api/emg/knowledge';
import type { InitResult, OrganizationOptionDto, PatientSearchRowDto } from '#/api/emg/triage';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { CreditCard, DocumentAdd, Postcard } from '@element-plus/icons-vue';
import { useDebounceFn } from '@vueuse/core';
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElSelect,
} from 'element-plus';

import { listKnowledgeBaseApi } from '#/api/emg/knowledge';
import {
  createEmergencyEncounterApi,
  createRecordApi,
  getInitApi,
  getOrganizationListApi,
  mockSearchPatients,
  pushMockQueueRow,
  searchPatientsApi,
} from '#/api/emg/triage';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectTable from '#/components/select-table.vue';

import KnowledgeTabs from './components/knowledge-tabs.vue';

defineOptions({
  name: 'Workbench',
});

const emit = defineEmits<{
  (e: 'saved'): void;
}>();

const initResult = ref<null | InitResult>(null);

/** 与患者搜索下拉绑定的主键，清空表示未从列表选择 */
const selectedSearchPatientId = ref<number | undefined>(undefined);
const selectedSearchPatientLabel = ref('');
const patientSearchQuery = ref({ searchType: 'all' });
const patientSelectColumns = [
  { prop: 'busNo', label: '病历号', width: 120 },
  { prop: 'name', label: '姓名', width: 100 },
  { prop: 'gender', label: '性别', width: 80 },
  { prop: 'phone', label: '联系电话', width: 140 },
  { prop: 'idCard', label: '身份证号', width: 190 },
];
const patientRowById = ref<Record<number, PatientSearchRowDto>>({});
const orgOptions = ref<OrganizationOptionDto[]>([]);
const locationOptions = ref<OrganizationOptionDto[]>([]);

const patient = reactive({
  id: null as null | number,
  busNo: '',
  name: '',
  gender: '',
  /** 自由描述：可填「2月」「3岁零5个月」「32」等 */
  age: '',
  birthDate: '',
  idType: 'SFZ',
  idCard: '',
  phone: '',
  nationality: '',
  address: '',
  contactName: '',
  contactTel: '',
});

const form = reactive({
  triageTime: '',
  chiefComplaint: '',
  symptom: '',
  conditions: [] as string[],
  /** 生命体征支持自由文字（如「测不出」），计算 MEWS 时从字符串中提取首个有效数字 */
  temperature: '',
  pulse: '',
  respiration: '',
  bpSystolic: '',
  bpDiastolic: '',
  spo2: '',
  /**
   * 意识(AVPU) 参与经典 MEWS（字符串值避免 ElSelect 与 0 的显示问题）
   * 0 清醒 · 1 对语言反应 · 2 对疼痛反应 · 3 无反应
   */
  mewsAvpu: '0' as '0' | '1' | '2' | '3',
  painNrs: null as null | number,
  /** 标准加权 RTS（0–约 7.84），需 GCS+收缩压+呼吸 */
  traumaRts: null as null | number,
  gcs: null as null | number,
  /** REMS 六变量分量（年龄、PR、MAP、RR、GCS、SpO₂），与文献急诊常用表一致 */
  remsScore: null as null | number,
  /** 由体征与 AVPU 自动计算（0–14 分，经典 MEWS） */
  mewsScore: 0,
  esiScore: null as null | number,
  aisScore: null as null | number,
  allergyTags: [] as string[],
  historyTags: [] as string[],
  specialTags: [] as string[],
  triageLevelEnum: null as null | number,
  levelAdjustReason: '',
  organizationId: null as null | number,
  locationId: null as null | number,
  diseaseTypes: [] as string[],
  patientSource: null as null | number,
  assignedDoctor: '',
  groupEventId: null as null | number,
  note: '',
  isGreenChannel: false,
  isFreeRegister: false,
});

const diseaseTypes = ref([
  { code: '0101', display: '内科' },
  { code: '0102', display: '外科' },
  { code: '0103', display: '创伤' },
  { code: '0104', display: '妇产科' },
  { code: '0105', display: '儿科' },
  { code: '0106', display: '骨科' },
  { code: '0107', display: '五官科' },
  { code: '0108', display: '皮肤科' },
]);

const patientSources = ref([
  { code: 1, display: '自行就诊' },
  { code: 2, display: '120送院' },
  { code: 3, display: '转诊' },
  { code: 4, display: '其他' },
]);

const groupEvents = ref([
  { id: 1, title: '交通事故', when: '2024-01-15 14:00', personCount: 5 },
  { id: 2, title: '工地事故', when: '2024-01-15 10:30', personCount: 3 },
]);

const practitioners = ref([
  { hisCode: 'D001', name: '张医生' },
  { hisCode: 'D002', name: '李医生' },
  { hisCode: 'D003', name: '王医生' },
]);

/** 从录入文本中解析用于计算的首个数字（无数字时返回 null，如「测不出」） */
function parseVitalNumeric(raw: null | string | undefined): null | number {
  const t = String(raw ?? '').trim();
  if (!t) return null;
  const m = t.match(/-?\d+(\.\d+)?/);
  if (!m) return null;
  return Number.parseFloat(m[0]!);
}

function hasVitalText(raw: null | string | undefined): boolean {
  return String(raw ?? '').trim().length > 0;
}

/** 经典 MEWS 分项：呼吸 */
function mewsRrPoints(rr: null | number): number {
  if (rr == null || Number.isNaN(rr)) return 0;
  if (rr <= 8) return 2;
  if (rr <= 11) return 1;
  if (rr <= 20) return 0;
  if (rr <= 24) return 2;
  return 3;
}

/** 经典 MEWS 分项：收缩压 */
function mewsSbpPoints(sbp: null | number): number {
  if (sbp == null || Number.isNaN(sbp)) return 0;
  if (sbp <= 70) return 3;
  if (sbp <= 80) return 2;
  if (sbp <= 100) return 1;
  if (sbp <= 199) return 0;
  return 2;
}

/** 经典 MEWS 分项：脉搏 */
function mewsHrPoints(hr: null | number): number {
  if (hr == null || Number.isNaN(hr)) return 0;
  if (hr <= 40) return 2;
  if (hr <= 50) return 1;
  if (hr <= 100) return 0;
  if (hr <= 110) return 1;
  if (hr <= 129) return 2;
  return 3;
}

/** 经典 MEWS 分项：体温 ℃ */
function mewsTempPoints(c: null | number): number {
  if (c == null || Number.isNaN(c)) return 0;
  if (c <= 35) return 2;
  if (c < 38.5) return 0;
  return 2;
}

function computeMewsFromVitals(): number {
  const rr = parseVitalNumeric(form.respiration);
  const sbp = parseVitalNumeric(form.bpSystolic);
  const hr = parseVitalNumeric(form.pulse);
  const temp = parseVitalNumeric(form.temperature);
  const avpu = Number.parseInt(form.mewsAvpu, 10);
  const av = Number.isNaN(avpu) ? 0 : Math.min(3, Math.max(0, avpu));
  return mewsRrPoints(rr) + mewsSbpPoints(sbp) + mewsHrPoints(hr) + mewsTempPoints(temp) + av;
}

/** 患者年龄：用于 REMS；支持「32岁」或纯数字，婴幼儿含「月」时按 0 岁计 */
function parseAgeYearsForRems(ageText: null | string | undefined): null | number {
  const s = String(ageText ?? '').trim();
  if (!s) return null;
  if (/\b月\b/.test(s) && !/\b岁\b/.test(s)) {
    return 0;
  }
  const yMatch = s.match(/(\d{1,3})\s*岁/);
  if (yMatch) {
    return Math.min(150, Number.parseInt(yMatch[1]!, 10));
  }
  if (/^\d{1,3}$/.test(s)) {
    return Math.min(150, Number.parseInt(s, 10));
  }
  const n = s.match(/(\d{1,3})/);
  return n ? Math.min(150, Number.parseInt(n[1]!, 10)) : null;
}

function rtsGcsCode(gcs: number): number {
  if (gcs >= 13) return 4;
  if (gcs >= 9) return 3;
  if (gcs >= 6) return 2;
  if (gcs >= 4) return 1;
  return 0;
}

function rtsSbpCode(sbp: number): number {
  if (sbp > 89) return 4;
  if (sbp >= 76) return 3;
  if (sbp >= 50) return 2;
  if (sbp >= 1) return 1;
  return 0;
}

function rtsRrCode(rr: number): number {
  if (rr >= 10 && rr <= 29) return 4;
  if (rr > 29) return 3;
  if (rr >= 6) return 2;
  if (rr >= 1) return 1;
  return 0;
}

/** 标准加权 Revision Trauma Score，Champion 等 */
function computeTraumaRts(): null | number {
  if (form.gcs == null) return null;
  const sbp = parseVitalNumeric(form.bpSystolic);
  const rr = parseVitalNumeric(form.respiration);
  if (sbp == null || rr == null) return null;
  const gc = rtsGcsCode(form.gcs);
  const sc = rtsSbpCode(sbp);
  const rc = rtsRrCode(rr);
  const raw = 0.9368 * gc + 0.7326 * sc + 0.2908 * rc;
  return Math.round(raw * 1000) / 1000;
}

function remsAgePoints(age: null | number): number {
  if (age == null) return 0;
  if (age < 45) return 0;
  if (age <= 54) return 1;
  if (age <= 64) return 2;
  if (age <= 74) return 3;
  return 4;
}

function remsPrPoints(pr: null | number): number {
  if (pr == null) return 0;
  if (pr >= 70 && pr <= 109) return 0;
  if ((pr >= 55 && pr <= 69) || (pr >= 110 && pr <= 139)) return 1;
  if ((pr >= 40 && pr <= 54) || (pr >= 140 && pr <= 179)) return 2;
  return 3;
}

function remsMapPoints(map: null | number): number {
  if (map == null) return 0;
  if (map >= 70 && map <= 109) return 0;
  if ((map >= 50 && map <= 69) || (map >= 110 && map <= 129)) return 1;
  if (map >= 130 && map <= 159) return 2;
  return 3;
}

function remsRrPointsForRems(rr: null | number): number {
  if (rr == null) return 0;
  if (rr >= 12 && rr <= 24) return 0;
  if ((rr >= 10 && rr <= 11) || (rr >= 25 && rr <= 34)) return 1;
  if (rr >= 6 && rr <= 9) return 2;
  if (rr >= 35 && rr <= 49) return 3;
  return 4;
}

function remsGcsComponentPoints(gcs: null | number): number {
  if (gcs == null) return 0;
  /* 14–15:0, 11–13:1, 8–10:2, 5–7:3, 3–4:4 */
  if (gcs >= 14) return 0;
  if (gcs >= 11) return 1;
  if (gcs >= 8) return 2;
  if (gcs >= 5) return 3;
  return 4;
}

function remsSpo2Points(spo2: null | number): number {
  if (spo2 == null) return 0;
  if (spo2 > 89) return 0;
  if (spo2 >= 86) return 1;
  if (spo2 >= 75) return 2;
  return 3;
}

/** REMS 分量求和；需已填 GCS 与左侧患者年龄，缺项项按 0 分计 */
function computeRemsScore(): null | number {
  if (form.gcs == null) return null;
  const ageY = parseAgeYearsForRems(patient.age);
  if (ageY == null) return null;
  const pr = parseVitalNumeric(form.pulse);
  const sbp = parseVitalNumeric(form.bpSystolic);
  const dbp = parseVitalNumeric(form.bpDiastolic);
  const rr = parseVitalNumeric(form.respiration);
  const spo2 = parseVitalNumeric(form.spo2);
  const map = sbp != null && dbp != null ? (sbp + 2 * dbp) / 3 : null;
  return (
    remsAgePoints(ageY) +
    remsPrPoints(pr) +
    remsMapPoints(map) +
    remsRrPointsForRems(rr) +
    remsGcsComponentPoints(form.gcs) +
    remsSpo2Points(spo2)
  );
}

function syncAutoScores() {
  form.mewsScore = computeMewsFromVitals();
  form.traumaRts = computeTraumaRts();
  form.remsScore = computeRemsScore();
}

function formatRtsDisplay(v: null | number): string {
  if (v == null) return '—';
  return String(Math.round(v * 1000) / 1000);
}

watch(
  () => [
    form.temperature,
    form.pulse,
    form.respiration,
    form.bpSystolic,
    form.bpDiastolic,
    form.spo2,
    form.mewsAvpu,
    form.gcs,
    patient.age,
  ],
  () => {
    syncAutoScores();
  },
  { deep: true, immediate: true },
);

const suggestedLevel = computed(() => {
  if (form.gcs != null && form.gcs <= 8) {
    return 1;
  }
  if (form.painNrs != null && form.painNrs >= 8) {
    return 2;
  }
  if (form.painNrs != null && form.painNrs >= 5) {
    return 3;
  }
  const spo2N = parseVitalNumeric(form.spo2);
  if (spo2N != null && spo2N < 92) {
    return 2;
  }
  const pulseN = parseVitalNumeric(form.pulse);
  if (pulseN != null && (pulseN > 120 || pulseN < 50)) {
    return 3;
  }
  if (form.remsScore != null && form.remsScore >= 7) {
    return 2;
  }
  if (form.mewsScore >= 5) {
    return 2;
  }
  return 4;
});

const levelManuallyOverridden = computed(() => {
  if (form.triageLevelEnum == null) {
    return false;
  }
  return form.triageLevelEnum !== suggestedLevel.value;
});

const levelOptions = [
  { value: 1, label: 'I 级-濒危', shortLabel: 'I级-濒危', color: '#f56c6c' },
  { value: 2, label: 'II 级-危重', shortLabel: 'II级-危重', color: '#e6a23c' },
  { value: 3, label: 'III 级-急症', shortLabel: 'III级-急症', color: '#fadb14' },
  { value: 4, label: 'IV 级-非急症', shortLabel: 'IV级-非急', color: '#67c23a' },
  { value: 5, label: 'V 级-亚急症', shortLabel: 'V级-亚急', color: '#909399' },
];

/** 默认只展示四级；展开后显示 V 级 */
const showTriageLevel5 = ref(false);

const levelOptionsForDisplay = computed(() =>
  showTriageLevel5.value ? levelOptions : levelOptions.filter((o) => o.value < 5),
);

watch(
  () => form.triageLevelEnum,
  (v) => {
    if (v === 5) {
      showTriageLevel5.value = true;
    }
  },
  { immediate: true },
);

function expandTriageLevel5() {
  showTriageLevel5.value = true;
}

function collapseTriageLevel5() {
  showTriageLevel5.value = false;
  if (form.triageLevelEnum === 5) {
    form.triageLevelEnum = 4;
  }
}

async function loadInit() {
  initResult.value = await getInitApi();
  orgOptions.value = await getOrganizationListApi();
  locationOptions.value = await getOrganizationListApi();
  if (form.triageLevelEnum == null) {
    form.triageLevelEnum = suggestedLevel.value;
  }
  form.triageTime = new Date().toISOString().slice(0, 16).replace('T', ' ');
}

const symptomTop30 = ref<KnowledgeBaseItem[]>([]);
const chiefComplaintTop30 = computed(() => symptomTop30.value.slice(0, 20));
const chiefComplaintTags = ref<string[]>([]);
const symptomSearchVisible = ref(false);
const symptomSearchQuery = ref('');
const symptomSearchResults = ref<KnowledgeBaseItem[]>([]);
const symptomSearchLoading = ref(false);
const rightTabActive = ref('knowledge');

/** 按 sortOrder 取知识库中排序前 30 的症状，供快速点选 */
async function loadSymptomTop30() {
  try {
    const res = await listKnowledgeBaseApi({ pageNo: 0, pageSize: 500, type: 'SYMPTOM' });
    symptomTop30.value = (res.content ?? [])
      .filter((i) => i.status === 1)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .slice(0, 30);
  } catch {
    symptomTop30.value = [];
  }
}

async function fetchSymptomSearch() {
  symptomSearchLoading.value = true;
  try {
    const q = symptomSearchQuery.value.trim();
    const res = await listKnowledgeBaseApi({
      pageNo: 0,
      pageSize: 200,
      type: 'SYMPTOM',
      ...(q ? { name: q } : {}),
    });
    symptomSearchResults.value = (res.content ?? [])
      .filter((i) => i.status === 1)
      .sort((a, b) => a.sortOrder - b.sortOrder);
  } catch {
    symptomSearchResults.value = [];
  } finally {
    symptomSearchLoading.value = false;
  }
}

const debouncedFetchSymptomSearch = useDebounceFn(() => {
  void fetchSymptomSearch();
}, 300);

function openSymptomSearch() {
  symptomSearchQuery.value = '';
  symptomSearchVisible.value = true;
  void fetchSymptomSearch();
}

function addConditionByName(name: string) {
  const n = name.trim();
  if (!n || form.conditions.includes(n)) {
    return;
  }
  form.conditions.push(n);
  form.symptom = form.conditions.join('；');
}

function splitDelimitedText(raw: string): string[] {
  return raw
    .split(/[；;、,\n]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function addChiefComplaintByName(name: string) {
  const n = name.trim();
  if (!n || chiefComplaintTags.value.includes(n)) {
    return;
  }
  chiefComplaintTags.value.push(n);
  const parts = splitDelimitedText(form.chiefComplaint);
  if (!parts.includes(n)) {
    form.chiefComplaint = [...parts, n].join('；');
  }
}

function removeChiefComplaintTag(name: string) {
  chiefComplaintTags.value = chiefComplaintTags.value.filter((t) => t !== name);
  const parts = splitDelimitedText(form.chiefComplaint).filter((t) => t !== name);
  form.chiefComplaint = parts.join('；');
}

function toggleChiefComplaintByName(name: string) {
  if (chiefComplaintTags.value.includes(name)) {
    removeChiefComplaintTag(name);
  } else {
    addChiefComplaintByName(name);
  }
}

function toggleConditionByName(name: string) {
  if (form.conditions.includes(name)) {
    removeCondition(name);
  } else {
    addConditionByName(name);
  }
}

function pickSymptomFromSearch(row: KnowledgeBaseItem) {
  addConditionByName(row.name);
}

onMounted(() => {
  loadInit().catch(() => {
    ElMessage.error('加载分诊选项失败');
  });
  void loadSymptomTop30();
});

function applySuggestedLevel() {
  form.triageLevelEnum = suggestedLevel.value;
  form.levelAdjustReason = '';
}

async function queryPatientOptions({
  pageNo = 1,
  pageSize = 20,
  keyword = '',
}: {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
}) {
  try {
    const res = await searchPatientsApi(keyword, pageNo, pageSize);
    const list = res.content?.length ? res.content : mockSearchPatients(keyword);
    const next = { ...patientRowById.value };
    for (const row of list) {
      next[row.id] = row;
    }
    patientRowById.value = next;
    return { content: list, totalElements: res.totalElements ?? list.length };
  } catch {
    const list = mockSearchPatients(keyword);
    const next = { ...patientRowById.value };
    for (const row of list) {
      next[row.id] = row;
    }
    patientRowById.value = next;
    return { content: list, totalElements: list.length };
  }
}

function onPatientSelectChange(value: number | number[] | string | string[]) {
  const picked = Array.isArray(value) ? value[0] : value;
  if (picked == null || picked === '') {
    selectedSearchPatientId.value = undefined;
    selectedSearchPatientLabel.value = '';
    clearSelectedPatient();
    return;
  }
  const patientId = Number(picked);
  if (Number.isNaN(patientId)) {
    return;
  }
  selectedSearchPatientId.value = patientId;
  const row = patientRowById.value[patientId];
  if (!row) return;
  selectedSearchPatientLabel.value = `${row.name || '—'}（${row.busNo || '—'}）`;
  selectPatient(row);
}

// 从检索结果项填充左侧患者信息
function clearSelectedPatient() {
  patient.id = null;
  patient.busNo = '';
  patient.name = '';
  patient.idCard = '';
  patient.phone = '';
  patient.gender = '';
  patient.age = '';
  patient.birthDate = '';
  patient.idType = 'SFZ';
  patient.nationality = '';
  patient.address = '';
  patient.contactName = '';
  patient.contactTel = '';
}

function selectPatient(row: PatientSearchRowDto) {
  patient.id = row.id;
  patient.busNo = row.busNo ?? '';
  patient.name = row.name ?? '';
  patient.idCard = row.idCard ?? '';
  patient.phone = row.phone ?? '';
  patient.gender = row.gender ?? '未知';
  patient.nationality = '汉族';
  patient.address = '';
  patient.contactName = '';
  patient.contactTel = '';

  if (row.birthDate) {
    patient.birthDate = row.birthDate;
  } else if (row.idCard && row.idCard.length === 18) {
    const y = row.idCard.slice(6, 10);
    const m = row.idCard.slice(10, 12);
    const d = row.idCard.slice(12, 14);
    patient.birthDate = `${y}-${m}-${d}`;
  } else {
    patient.birthDate = '';
  }

  if (row.age != null && row.age !== undefined) {
    patient.age = `${row.age}岁`;
  } else if (row.idCard && row.idCard.length === 18) {
    const birthYear = Number.parseInt(row.idCard.slice(6, 10), 10);
    patient.age = `${new Date().getFullYear() - birthYear}岁`;
  } else {
    patient.age = '';
  }
}

function stubReadCard(kind: string) {
  ElMessage.info(`读卡操作(${kind})需对接外设与接口，当前为占位操作`);
}

function hintNewPatient() {
  ElMessage.info('患者建档跳转待对接 FR-PAT-001');
}

// 从已选症状标签中移除并同步拼接到 symptom 文本
function removeCondition(item: string) {
  const index = form.conditions.indexOf(item);
  if (index !== -1) {
    form.conditions.splice(index, 1);
    form.symptom = form.conditions.join('；');
  }
}

function changeGreenChannel() {
  if (form.isGreenChannel) {
    form.isFreeRegister = true;
    form.triageLevelEnum = 1;
  }
}

function changeDiseaseType() {
  if (form.diseaseTypes.includes('0103')) {
    form.isGreenChannel = true;
    form.isFreeRegister = true;
    form.triageLevelEnum = 1;
  }
}

function resetForm() {
  selectedSearchPatientId.value = undefined;
  selectedSearchPatientLabel.value = '';
  patientRowById.value = {};
  patient.id = null;
  patient.busNo = '';
  patient.name = '';
  patient.gender = '';
  patient.age = '';
  patient.birthDate = '';
  patient.idType = 'SFZ';
  patient.idCard = '';
  patient.phone = '';
  patient.nationality = '';
  patient.address = '';
  patient.contactName = '';
  patient.contactTel = '';
  form.triageTime = new Date().toISOString().slice(0, 16).replace('T', ' ');
  form.chiefComplaint = '';
  chiefComplaintTags.value = [];
  form.symptom = '';
  form.conditions = [];
  form.temperature = '';
  form.pulse = '';
  form.respiration = '';
  form.bpSystolic = '';
  form.bpDiastolic = '';
  form.spo2 = '';
  form.mewsAvpu = '0';
  form.mewsScore = 0;
  form.painNrs = null;
  form.traumaRts = null;
  form.gcs = null;
  form.remsScore = null;
  form.esiScore = null;
  form.aisScore = null;
  form.allergyTags = [];
  form.historyTags = [];
  form.specialTags = [];
  form.triageLevelEnum = suggestedLevel.value;
  form.levelAdjustReason = '';
  form.organizationId = null;
  form.locationId = null;
  form.diseaseTypes = [];
  form.patientSource = null;
  form.assignedDoctor = '';
  form.groupEventId = null;
  form.note = '';
  form.isGreenChannel = false;
  form.isFreeRegister = false;
}

async function saveTriage() {
  if (!patient.id) {
    ElMessage.warning('请先检索并选择患者');
    return;
  }
  if (!form.chiefComplaint.trim()) {
    ElMessage.warning('请填写主诉');
    return;
  }
  if (
    !hasVitalText(form.temperature) &&
    !hasVitalText(form.pulse) &&
    !hasVitalText(form.respiration) &&
    !hasVitalText(form.bpSystolic) &&
    !hasVitalText(form.bpDiastolic) &&
    !hasVitalText(form.spo2)
  ) {
    ElMessage.warning('请至少录入一项生命体征');
    return;
  }
  if (!form.organizationId) {
    ElMessage.warning('请选择挂号科室');
    return;
  }
  if (!form.locationId) {
    ElMessage.warning('请选择分诊去向');
    return;
  }
  if (!form.triageLevelEnum) {
    ElMessage.warning('请选择分诊级别');
    return;
  }
  if (levelManuallyOverridden.value && !form.levelAdjustReason.trim()) {
    ElMessage.warning('调整分诊级别时请填写调整原因');
    return;
  }

  try {
    const encounterId = await createEmergencyEncounterApi({
      patientId: patient.id,
      organizationId: form.organizationId,
      serviceTypeId: 1,
      serviceBusinessDay: new Date().toISOString().slice(0, 10),
    });

    const assessments: any[] = [];
    if (form.painNrs != null) {
      assessments.push({
        assessmentType: 'PAIN',
        assessmentData: JSON.stringify({ nrs: form.painNrs }),
      });
    }
    if (form.gcs != null) {
      assessments.push({
        assessmentType: 'GCS',
        assessmentData: JSON.stringify({ gcs: form.gcs }),
      });
    }
    if (form.traumaRts != null) {
      assessments.push({
        assessmentType: 'TRAUMA_RTS',
        assessmentData: JSON.stringify({ rts: form.traumaRts }),
      });
    }
    if (form.remsScore != null) {
      assessments.push({
        assessmentType: 'REMS',
        assessmentData: JSON.stringify({ rems: form.remsScore }),
      });
    }
    assessments.push({
      assessmentType: 'MEWS',
      assessmentData: JSON.stringify({
        mews: form.mewsScore,
        avpu: Number.parseInt(form.mewsAvpu, 10),
        auto: true,
      }),
    });

    const bpA = String(form.bpSystolic).trim();
    const bpB = String(form.bpDiastolic).trim();
    const bpVal = bpA || bpB ? (bpA && bpB ? `${bpA}/${bpB}` : ((bpA || bpB) ?? '')) : '';
    const vitalSigns: { itemCode: string; itemName: string; value: string; unit: string }[] = [];
    if (hasVitalText(form.temperature)) {
      vitalSigns.push({
        itemCode: 'TEMP',
        itemName: '体温',
        value: String(form.temperature).trim(),
        unit: '℃',
      });
    }
    if (hasVitalText(form.pulse)) {
      vitalSigns.push({
        itemCode: 'PULSE',
        itemName: '脉搏',
        value: String(form.pulse).trim(),
        unit: '次/分',
      });
    }
    if (hasVitalText(form.respiration)) {
      vitalSigns.push({
        itemCode: 'RR',
        itemName: '呼吸',
        value: String(form.respiration).trim(),
        unit: '次/分',
      });
    }
    if (bpVal) {
      vitalSigns.push({ itemCode: 'BP', itemName: '血压', value: bpVal, unit: 'mmHg' });
    }
    if (hasVitalText(form.spo2)) {
      vitalSigns.push({
        itemCode: 'SPO2',
        itemName: '血氧',
        value: String(form.spo2).trim(),
        unit: '%',
      });
    }

    await createRecordApi({
      patientId: patient.id,
      encounterId,
      triageLevelEnum: form.triageLevelEnum,
      organizationId: form.organizationId,
      chiefComplaint: form.chiefComplaint,
      symptom: form.symptom,
      assessments,
      vitalSigns,
    });

    const orgName = orgOptions.value.find((o) => o.id === form.organizationId)?.name ?? '';
    const level = form.triageLevelEnum ?? 4;
    const levelName = levelOptions.find((o) => o.value === level)?.label ?? '';
    const locationName = locationOptions.value.find((o) => o.id === form.locationId)?.name ?? '';

    pushMockQueueRow({
      id: Date.now() % 100_000,
      patientName: patient.name,
      patientAge: patient.age,
      patientGender: patient.gender,
      triageLevelEnum: level,
      triageLevelName: levelName,
      triageTime: form.triageTime,
      chiefComplaint: form.chiefComplaint,
      organizationName: orgName,
      locationName,
      statusEnum: 1,
      statusName: '待就诊',
      waitMinutes: 0,
      encounterId,
    });

    ElMessage.success('保存分诊成功');
    resetForm();
    emit('saved');
  } catch {
    ElMessage.error('保存失败，请稍后重试');
  }
}
</script>

<template>
  <StationTriPaneLayout
    :show-right="false"
    :show-footer="false"
    class="min-h-0 flex-1"
    left-track="minmax(0, 55fr)"
    left-track-lg="minmax(0, 55fr)"
    center-track="minmax(0, 45fr)"
    center-track-lg="minmax(0, 45fr)"
  >
    <template #left>
      <div class="flex h-full min-h-0 w-full flex-col">
        <div class="patient-info-panel patient-info-card">
          <SectionTitle title="患者信息" />
          <div class="patient-search mt-3">
            <div class="patient-search__actions">
              <div class="patient-select-wrap">
                <SelectTable
                  v-model="selectedSearchPatientId"
                  class="patient-select w-full"
                  placeholder="姓名 / 拼音 / 五笔 / 身份证 / 病历号"
                  :columns="patientSelectColumns"
                  :query="patientSearchQuery"
                  :remote-method="queryPatientOptions"
                  value-key="id"
                  label-key="name"
                  :selected-label="selectedSearchPatientLabel"
                  @change="onPatientSelectChange"
                />
              </div>
              <ElButton type="primary" :icon="DocumentAdd" @click="hintNewPatient">建档</ElButton>
              <ElButton type="success" plain :icon="Postcard" @click="stubReadCard('id')">
                身份证
              </ElButton>
              <ElButton type="warning" plain :icon="CreditCard" @click="stubReadCard('yb')">
                医保卡
              </ElButton>
            </div>
          </div>
          <ElForm label-width="92px" class="patient-base-form triage-form-unified mt-3">
            <ElRow>
              <ElCol :span="8">
                <ElFormItem label="病历号">
                  <span class="patient-key-text">{{ patient.busNo || '—' }}</span>
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="患者姓名">
                  <span class="patient-key-text">{{ patient.name || '—' }}</span>
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="性别">
                  <ElRadioGroup v-model="patient.gender" size="default">
                    <ElRadioButton value="男">男</ElRadioButton>
                    <ElRadioButton value="女">女</ElRadioButton>
                  </ElRadioGroup>
                </ElFormItem>
              </ElCol>
            </ElRow>
            <ElRow>
              <ElCol :span="8">
                <ElFormItem label="出生日期">
                  <ElDatePicker
                    v-model="patient.birthDate"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    size="default"
                    class="w-full"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="年龄">
                  <ElInput
                    v-model="patient.age"
                    class="patient-age-input"
                    size="default"
                    clearable
                    placeholder="如 32岁、3岁5个月、2月"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="电话">
                  <ElInput v-model="patient.phone" size="default" placeholder="联系电话" />
                </ElFormItem>
              </ElCol>
            </ElRow>
            <ElRow>
              <ElCol :span="8">
                <ElFormItem label="证件类型">
                  <ElSelect
                    v-model="patient.idType"
                    placeholder="证件类型"
                    clearable
                    size="default"
                    class="w-full"
                  >
                    <ElOption value="SFZ" label="身份证" />
                    <ElOption value="HZ" label="护照" />
                    <ElOption value="TB" label="台胞证" />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="证件号码">
                  <ElInput v-model="patient.idCard" size="default" placeholder="证件号码" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="民族">
                  <ElSelect
                    v-model="patient.nationality"
                    placeholder="请选择民族"
                    clearable
                    size="default"
                    class="w-full"
                  >
                    <ElOption value="汉族" label="汉族" />
                    <ElOption value="蒙古族" label="蒙古族" />
                    <ElOption value="藏族" label="藏族" />
                    <ElOption value="维吾尔族" label="维吾尔族" />
                    <ElOption value="苗族" label="苗族" />
                    <ElOption value="其他" label="其他" />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
            </ElRow>
            <ElRow>
              <ElCol :span="8">
                <ElFormItem label="紧急联系人">
                  <ElInput v-model="patient.contactName" size="default" placeholder="联系人姓名" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="联系人电话">
                  <ElInput v-model="patient.contactTel" size="default" placeholder="联系人电话" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="住址">
                  <ElInput v-model="patient.address" size="default" placeholder="住址" />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </ElForm>
          <ElForm label-width="84px" class="triage-info-form triage-form-unified mt-3">
            <SectionTitle title="分诊信息" />
            <div class="workbench-assessment-form mt-2">
              <div class="vital--inline">
                <div class="vital-fields">
                  <div class="vital-field">
                    <ElFormItem label="体温(℃)">
                      <ElInput
                        v-model="form.temperature"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="数值或 测不出 等"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field">
                    <ElFormItem label="脉搏(次/分)">
                      <ElInput
                        v-model="form.pulse"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="数值或 测不出 等"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field">
                    <ElFormItem label="呼吸(次/分)">
                      <ElInput
                        v-model="form.respiration"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="数值或 测不出 等"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field">
                    <ElFormItem label="舒张压">
                      <ElInput
                        v-model="form.bpDiastolic"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="mmHg 或 测不出"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field">
                    <ElFormItem label="收缩压">
                      <ElInput
                        v-model="form.bpSystolic"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="mmHg 或 测不出"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field">
                    <ElFormItem label="血氧(%)">
                      <ElInput
                        v-model="form.spo2"
                        size="default"
                        class="vital-input w-full"
                        clearable
                        placeholder="数值或 测不出 等"
                      />
                    </ElFormItem>
                  </div>
                  <div class="vital-field vital-field--avpu">
                    <ElFormItem label="意识(AVPU)">
                      <ElSelect
                        v-model="form.mewsAvpu"
                        class="vital-input w-full"
                        size="default"
                        placeholder="参与 MEWS 计分"
                      >
                        <ElOption value="0" label="清醒 (A) — 0 分" />
                        <ElOption value="1" label="对语言有反应 (V) — 1 分" />
                        <ElOption value="2" label="对疼痛有反应 (P) — 2 分" />
                        <ElOption value="3" label="无反应 (U) — 3 分" />
                      </ElSelect>
                    </ElFormItem>
                  </div>
                </div>
              </div>

              <div class="triage-line-row">
                <ElFormItem label="主诉" required class="triage-line-row__item">
                  <ElInput
                    v-model="form.chiefComplaint"
                    type="textarea"
                    :rows="2"
                    maxlength="500"
                    show-word-limit
                    placeholder="可手动录入；也可在右侧“主诉/症状知识库”多选后自动回填"
                    class="triage-textarea--compact w-full"
                    size="default"
                  />
                  <div v-if="chiefComplaintTags.length" class="triage-line-tags">
                    <span class="triage-line-tags__label">已选主诉</span>
                    <div class="condition-tags">
                      <div v-for="item in chiefComplaintTags" :key="item" class="condition-tag">
                        <span>{{ item }}</span>
                        <i class="el-icon-close" @click="removeChiefComplaintTag(item)"></i>
                      </div>
                    </div>
                  </div>
                </ElFormItem>
              </div>
              <div class="triage-line-row">
                <ElFormItem label="症状" class="triage-line-row__item">
                  <ElInput
                    v-model="form.symptom"
                    type="textarea"
                    :rows="2"
                    readonly
                    placeholder="请在右侧“主诉/症状知识库”选择（支持多选）"
                    class="triage-textarea--compact w-full"
                    size="default"
                  />
                  <div v-if="form.conditions.length" class="triage-line-tags">
                    <span class="triage-line-tags__label">已选症状</span>
                    <div class="condition-tags">
                      <div v-for="item in form.conditions" :key="item" class="condition-tag">
                        <span>{{ item }}</span>
                        <i class="el-icon-close" @click="removeCondition(item)"></i>
                      </div>
                    </div>
                  </div>
                </ElFormItem>
              </div>
              <div class="triage-line-row">
                <ElFormItem label="备注" class="triage-line-row__item">
                  <ElInput
                    v-model="form.note"
                    type="textarea"
                    :rows="2"
                    placeholder="分诊沟通要点、陪送信息等"
                    size="default"
                    class="triage-textarea--compact w-full"
                  />
                </ElFormItem>
              </div>
            </div>

            <div class="triage-info-section triage-outcome-card workbench-block-dense">
              <ElRow :gutter="12" class="mt-2">
                <ElCol :span="8">
                  <ElFormItem label="分诊时间" required>
                    <ElDatePicker
                      v-model="form.triageTime"
                      type="datetime"
                      format="YYYY-MM-DD HH:mm"
                      value-format="YYYY-MM-DD HH:mm"
                      placeholder="选择日期时间"
                      class="w-full"
                      size="default"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="系统建议">
                    <span class="level-badge" :class="`level-${suggestedLevel}`">
                      {{ suggestedLevel }} 级
                    </span>
                    <ElButton link type="primary" size="small" @click="applySuggestedLevel">
                      采用
                    </ElButton>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8" class="triage-level-col--double">
                  <ElFormItem label="分诊级别" required>
                    <div class="triage-level-wrap">
                      <ElRadioGroup
                        v-model="form.triageLevelEnum"
                        size="default"
                        class="triage-level-group triage-level-group--flat"
                      >
                        <ElRadioButton
                          v-for="item in levelOptionsForDisplay"
                          :key="item.value"
                          :value="item.value"
                          :title="item.label"
                        >
                          <span :style="{ color: item.color }">{{ item.shortLabel }}</span>
                        </ElRadioButton>
                      </ElRadioGroup>
                      <ElButton
                        v-if="!showTriageLevel5"
                        class="triage-level-toggle"
                        type="primary"
                        link
                        @click="expandTriageLevel5"
                      >
                        五级
                      </ElButton>
                      <ElButton
                        v-else
                        class="triage-level-toggle"
                        type="info"
                        link
                        @click="collapseTriageLevel5"
                      >
                        仅四级
                      </ElButton>
                    </div>
                  </ElFormItem>
                </ElCol>
              </ElRow>
              <ElRow :gutter="12" class="mt-1">
                <ElCol :span="8">
                  <ElFormItem label="分诊去向" required>
                    <ElSelect
                      v-model="form.locationId"
                      placeholder="请选择分诊去向"
                      clearable
                      filterable
                      class="w-full"
                      size="default"
                    >
                      <ElOption
                        v-for="o in locationOptions"
                        :key="o.id"
                        :label="o.name ?? String(o.id)"
                        :value="o.id"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="挂号科室" required>
                    <ElSelect
                      v-model="form.organizationId"
                      placeholder="请选择挂号科室"
                      clearable
                      filterable
                      class="w-full"
                      size="default"
                    >
                      <ElOption
                        v-for="o in orgOptions"
                        :key="o.id"
                        :label="o.name ?? String(o.id)"
                        :value="o.id"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="挂号医生">
                    <ElSelect
                      v-model="form.assignedDoctor"
                      placeholder="请选择挂号医生"
                      clearable
                      filterable
                      class="w-full"
                      size="default"
                    >
                      <ElOption
                        v-for="p in practitioners"
                        :key="p.hisCode"
                        :label="`${p.name}-${p.hisCode}`"
                        :value="p.name"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="病种分类">
                    <ElSelect
                      v-model="form.diseaseTypes"
                      placeholder="请选择病种"
                      multiple
                      clearable
                      size="default"
                      class="w-full"
                      @change="changeDiseaseType"
                    >
                      <ElOption
                        v-for="item in diseaseTypes"
                        :key="item.code"
                        :label="item.display"
                        :value="item.code"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="患者来源">
                    <ElSelect
                      v-model="form.patientSource"
                      placeholder="请选择患者来源"
                      clearable
                      size="default"
                      class="w-full"
                    >
                      <ElOption
                        v-for="item in patientSources"
                        :key="item.code"
                        :label="item.display"
                        :value="item.code"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol v-if="levelManuallyOverridden" :span="8">
                  <ElFormItem label="调整原因" required>
                    <ElInput
                      v-model="form.levelAdjustReason"
                      placeholder="人工调整分级须填写原因"
                      maxlength="500"
                      show-word-limit
                      class="w-full"
                      size="default"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </div>
          </ElForm>
          <div class="patient-triage-actions">
            <div class="patient-triage-actions__ops">
              <ElCheckbox v-model="form.isGreenChannel" @change="changeGreenChannel">
                绿色通道
              </ElCheckbox>
              <ElCheckbox v-model="form.isFreeRegister">免费挂号</ElCheckbox>
              <ElButton type="primary" @click="saveTriage" size="default">保存分诊</ElButton>
            </div>
            <div class="patient-triage-actions__tip">
              <template v-if="patient.id">
                当前患者：{{ patient.name || '—' }}（{{ patient.gender || '—' }} ·
                {{ patient.age || '—' }} · 病历号 {{ patient.busNo || '—' }}）
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #center>
      <div class="flex h-full min-h-0 w-full flex-col">
        <KnowledgeTabs
          v-model:active-tab="rightTabActive"
          :chief-complaint-tags="chiefComplaintTags"
          :chief-complaint-top30="chiefComplaintTop30"
          :selected-conditions="form.conditions"
          :symptom-top30="symptomTop30"
          :on-toggle-chief-complaint="toggleChiefComplaintByName"
          :on-toggle-condition="toggleConditionByName"
          :on-open-symptom-search="openSymptomSearch"
        />
      </div>
    </template>
  </StationTriPaneLayout>

  <ElDialog
    v-model="symptomSearchVisible"
    title="搜索症状"
    width="520px"
    destroy-on-close
    class="symptom-search-dialog"
  >
    <div v-loading="symptomSearchLoading" class="symptom-search-body">
      <ElInput
        v-model="symptomSearchQuery"
        clearable
        placeholder="症状名称、拼音简码"
        size="default"
        class="w-full"
        @input="debouncedFetchSymptomSearch"
      />
      <p class="symptom-search-hint text-muted-foreground">点击行加入已选；可重复打开继续添加</p>
      <div
        v-if="!symptomSearchResults.length && !symptomSearchLoading"
        class="symptom-search-empty"
      >
        无匹配项，请调整关键词
      </div>
      <ul v-else class="symptom-search-list">
        <li
          v-for="row in symptomSearchResults"
          :key="row.id"
          :class="[
            'symptom-search-list__item',
            { 'is-selected': form.conditions.includes(row.name) },
          ]"
          @click="pickSymptomFromSearch(row)"
        >
          <span class="symptom-search-list__name">{{ row.name }}</span>
          <span v-if="row.pinyinCode" class="symptom-search-list__pinyin text-muted-foreground">{{
            row.pinyinCode
          }}</span>
        </li>
      </ul>
    </div>
  </ElDialog>
</template>

<style scoped>
.register-like-layout {
  display: flex;
  flex-direction: row;
  flex: 1;
  min-height: 0;
  height: 100%;
  gap: 4px;
  padding: 6px 6px 6px 2px;
  overflow: auto;
}

.register-like-layout__left {
  display: flex;
  flex: 0 0 52%;
  min-width: 760px;
  max-width: 980px;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.register-like-layout__right {
  display: flex;
  flex: 1;
  min-width: 420px;
  min-height: 0;
  overflow: auto;
}

.register-like-card {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 8px;
}

.register-like-card :deep(.el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 8px !important;
}

.register-like-card__body--left,
.register-like-card__body--right {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
}

.workbench {
  display: flex;
  flex-direction: column;
  font-size: 16px;
  line-height: 1.45;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
  /* 随内容增高，不强行撑满视口，避免与外层 tab 滚动区叠加以致底部留空+双滚动 */
  min-height: 0;
  padding: 8px 10px;
  box-sizing: border-box;
}

.workbench-content {
  display: grid;
  /* 上区按内容高度自适应，下区占用剩余空间，避免中间大块空白 */
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, auto) minmax(0, 1fr);
  gap: 4px;
  align-items: stretch;
  min-height: 0;
  flex: 1 1 0;
}

.content-left {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-x: hidden;
  overflow-y: auto;
}

.content-right {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-x: hidden;
  overflow-y: auto;
}

.triage-info-form {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
}

.patient-base-form :deep(.el-form-item),
.triage-info-form :deep(.el-form-item) {
  margin-bottom: 6px;
}

.patient-base-form :deep(.el-form-item__label) {
  justify-content: flex-end;
  padding-right: 8px;
  color: #020817;
  font-weight: 500;
  line-height: 28px;
}

.patient-base-form :deep(.el-radio-group) {
  min-height: 32px;
}

.patient-key-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.patient-info-panel,
.triage-info-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 10px;
}

.triage-info-section {
  margin-top: 0;
  padding-top: 4px;
  border-top: 0;
}

.triage-textarea--compact {
  max-width: none;
}

.chief-note-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.chief-note-row__item {
  min-width: 0;
}

.triage-line-row__item {
  width: 100%;
  margin-bottom: 8px;
}

.triage-line-row__item :deep(.el-form-item__label) {
  justify-content: flex-end;
  padding-right: 8px;
  color: #020817;
  font-weight: 500;
  line-height: 28px;
}

.triage-line-row__item :deep(.el-form-item__content) {
  align-items: flex-start;
  line-height: 28px;
}

.triage-focus-card :deep(.el-form-item__label),
.triage-outcome-card :deep(.el-form-item__label) {
  justify-content: flex-end;
}

.triage-focus-card :deep(.el-form-item),
.triage-outcome-card :deep(.el-form-item) {
  margin-bottom: 6px;
}

.triage-line-tags {
  margin-top: 6px;
}

.triage-line-tags__label {
  display: inline-block;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 根据可用高度自动紧凑：高度越低，间距/字号/控件高度越小 */
@media (max-height: 1080px) {
  .workbench {
    padding: 5px 6px;
    font-size: 14px;
    line-height: 1.3;
  }

  .workbench-content {
    gap: 3px;
  }

  .workbench-header {
    margin-bottom: 4px;
    padding: 5px 8px;
  }

  .workbench-toolbar {
    margin-bottom: 4px;
    padding: 3px 6px;
  }

  .content-left,
  .content-right {
    gap: 3px;
  }

  .patient-info-panel,
  .triage-info-panel {
    padding: 6px 8px;
  }

  .workbench-dense :deep(.el-form-item) {
    margin-bottom: 3px;
  }

  .workbench-right--comfort :deep(.el-input__wrapper),
  .workbench-right--comfort :deep(.el-select__wrapper),
  .patient-details--comfort :deep(.el-select__wrapper) {
    min-height: 30px;
  }

  .header-center-tip {
    font-size: 15px;
  }
}

@media (max-height: 900px) {
  .workbench {
    padding: 4px 6px;
  }

  .workbench-content {
    gap: 2px;
  }

  .content-left,
  .content-right {
    gap: 2px;
  }

  .workbench-header {
    margin-bottom: 3px;
    padding: 4px 6px;
  }

  .workbench-toolbar {
    margin-bottom: 3px;
    padding: 2px 5px;
  }

  .patient-info-panel,
  .triage-info-panel {
    padding: 5px 6px;
  }
}

/* 更紧凑的间距，略增大可辨读字号 */
.workbench-dense :deep(.el-form-item) {
  margin-bottom: 4px;
}

.workbench-dense :deep(.el-form-item__label) {
  font-size: 14px;
  line-height: 1.25;
  padding-bottom: 0;
}

.workbench-dense :deep(.el-input__inner),
.workbench-dense :deep(.el-textarea__inner) {
  font-size: 15px;
}

.workbench-dense :deep(.el-input-number) {
  width: 100%;
}

.workbench-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px 10px;
  margin-bottom: 6px;
  padding: 5px 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.workbench-toolbar__patient {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  min-width: 0;
  flex: 1;
}

.workbench-toolbar__empty {
  min-height: 1px;
}

.workbench-toolbar__label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.workbench-toolbar__name {
  font-size: 16px;
  color: var(--el-color-primary);
}

.workbench-toolbar__meta {
  font-size: 14px;
}

.triage-focus-hint {
  margin: 0 0 4px;
  line-height: 1.35;
}

.vital--inline {
  margin-top: 2px;
  padding-top: 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.vital--inline .section-sub-title {
  margin-top: 0;
  margin-bottom: 4px;
}

.vital-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 10px;
}

.vital-fields {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px 12px;
  align-items: start;
}

.vital-field {
  min-width: 0;
  max-width: none;
}

.vital-field--avpu {
  grid-column: span 1;
}

.vital-input {
  max-width: none;
}

.triage-level-wrap {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}

.triage-level-toggle {
  flex-shrink: 0;
  font-size: 14px;
  padding: 0 2px;
}

.triage-outcome-card :deep(.triage-level-group) {
  display: flex;
  gap: 4px;
  min-width: 0;
  flex: 1 1 0;
}

.triage-outcome-card :deep(.triage-level-group--flat) {
  flex-wrap: nowrap;
  width: 100%;
  flex: 1;
  min-width: 0;
  overflow: visible;
}

.triage-outcome-card :deep(.triage-level-group--flat .el-radio-button) {
  flex: 0 0 auto;
  min-width: auto;
}

.triage-outcome-card :deep(.triage-level-group--flat .el-radio-button__inner) {
  width: auto;
  box-sizing: border-box;
  padding: 6px 10px;
  font-size: 14px;
  line-height: 1.25;
  white-space: nowrap;
  text-align: center;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  padding: 6px 10px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 6px;
  margin-bottom: 6px;
  box-shadow: 0 2px 12px 0 rgba(64, 158, 255, 0.3);
  gap: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.header-icon {
  width: 24px;
  height: 24px;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
}

.header-center-tip {
  text-align: left;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  color: #111;
  margin-left: 8px;
}

.patient-triage-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.patient-triage-actions__ops {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.patient-triage-actions__tip {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.knowledge-panel {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-top: 2px;
}

/* 对齐门诊挂号：患者信息与分诊信息统一表单标签与控件视觉 */
.triage-form-unified :deep(.el-form-item__label) {
  padding-right: 8px;
  color: #020817;
  font-weight: 500;
  line-height: 28px;
}

.triage-form-unified :deep(.el-form-item__content) {
  min-height: 28px;
  line-height: 28px;
}

.triage-form-unified :deep(.el-input__inner),
.triage-form-unified :deep(.el-textarea__inner),
.triage-form-unified :deep(.el-date-editor .el-input__inner) {
  font-size: 14px;
}

.triage-form-unified :deep(.el-input__wrapper),
.triage-form-unified :deep(.el-select__wrapper) {
  min-height: 30px;
  padding-top: 2px;
  padding-bottom: 2px;
}

.triage-form-unified :deep(.el-radio) {
  margin-right: 14px;
  height: 28px;
}

/* 表单项统一改为固定 300px，并按可用宽度自动续排 */
.triage-form-unified :deep(.el-row) {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}

.triage-form-unified :deep(.el-row > .el-col) {
  flex: 0 0 300px !important;
  width: 300px !important;
  max-width: 300px !important;
}

.triage-info-form :deep(.el-row > .el-col) {
  flex: 0 0 240px !important;
  width: 240px !important;
  max-width: 240px !important;
}

.triage-info-form :deep(.el-row > .triage-level-col--double) {
  flex-basis: 492px !important;
  width: 492px !important;
  max-width: 492px !important;
}

.patient-base-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}

.patient-base-col {
  flex: 0 0 300px;
  width: 300px;
  max-width: 300px;
}

.patient-base-form {
  overflow-x: auto;
}

/* 患者基础信息固定三列：每项 300px（含 label），不足时横向滚动 */
.patient-base-form :deep(.el-row) {
  display: grid;
  grid-template-columns: repeat(3, 300px);
  column-gap: 12px;
}

.patient-base-form :deep(.el-row > .el-col) {
  width: 300px !important;
  max-width: 300px !important;
  min-width: 300px !important;
}

.patient-info-card {
  /* 不占据「等高列」多余高度，避免卡片区下方出现大块留白 */
  flex: 0 0 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.triage-focus-card,
.triage-outcome-card {
  flex: 0 0 auto;
  min-width: 0;
}

.assessment-card {
  min-height: 0;
  flex: 0 0 auto;
}

.other-info-card {
  flex: 0 0 auto;
}

.mt-2 {
  margin-top: 4px;
}

.mt-3 {
  margin-top: 6px;
}

.mt-4 {
  margin-top: 8px;
}

.w-full {
  width: 100%;
}

.w-20 {
  width: 80px;
}

.flex {
  display: flex;
}

.flex-1 {
  flex: 1;
}

.gap-2 {
  gap: 8px;
}

.patient-search {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.patient-search__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.patient-search__actions :deep(.el-button) {
  min-height: 34px;
  padding: 0 12px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.patient-select-wrap {
  width: 100%;
  max-width: 360px;
}

.patient-select-wrap :deep(.el-select .el-input__inner) {
  font-size: 15px;
}

.patient-hint {
  margin: 0.35rem 0 0;
  line-height: 1.35;
  font-size: 13px;
}

/* 患者信息区：Flex 自动响应式布局（自动换行 + 同行等分宽度） */
.patient-details--comfort {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px 8px;
  padding-top: 16px;
}

.patient-details--comfort :deep(.el-row) {
  display: contents;
}

.patient-details--comfort :deep(.el-col) {
  min-width: 0;
  flex: 1 1 220px !important;
  width: auto !important;
  max-width: 100% !important;
}

.patient-details--comfort :deep(.patient-doc-col--type) {
  flex: 0 1 220px !important;
}

.patient-details--comfort :deep(.patient-doc-col--nation) {
  flex: 0 1 220px !important;
}

.patient-details--comfort :deep(.patient-doc-col--idcard) {
  flex: 1 1 300px !important;
}

.patient-details--comfort .info-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.patient-details--comfort .info-item--key {
  gap: 8px;
}

.patient-details--comfort .info-label {
  font-size: 15px;
  line-height: 1.3;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  flex: 0 0 78px;
  text-align: right;
  white-space: nowrap;
}

.patient-details--comfort .info-label--key {
  font-size: 17px;
  font-weight: 700;
  flex-basis: 92px;
}

.patient-details--comfort .info-value {
  font-size: 16px;
  line-height: 1.3;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}

.patient-details--comfort .info-value--key {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.patient-details--comfort :deep(.el-input__inner),
.patient-details--comfort :deep(.el-input__wrapper) {
  font-size: 15px;
}

.patient-details--comfort :deep(.el-date-editor .el-input__inner) {
  font-size: 15px;
}

.patient-details--comfort :deep(.el-select .el-input__inner),
.patient-details--comfort :deep(.el-select__wrapper) {
  font-size: 15px;
  min-height: 34px;
}

.patient-details--comfort :deep(.el-radio-button__inner) {
  font-size: 14px;
  padding: 8px 14px;
}

.patient-option {
  padding: 6px 0 4px;
  line-height: 1.45;
  text-align: left;
}

.patient-option__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.patient-option__name {
  font-size: 19px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.patient-option__gender {
  flex-shrink: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.patient-option__idcard {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}

.patient-option__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  margin-top: 6px;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.patient-option__row b {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.patient-details {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 14px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.info-label {
  font-size: 15px;
  color: var(--el-text-color-secondary);
}

.info-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.vital-signs-section,
.scores-section,
.history-section {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.section-sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 4px;
  padding-left: 6px;
  border-left: 3px solid var(--el-color-primary);
}

.symptom-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.symptom-quick__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  margin-bottom: 4px;
}

.symptom-quick__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.symptom-quick__sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.symptom-selected {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}

.symptom-selected__label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.symptom-selected__tags {
  margin-top: 0;
}

.symptom-search-body {
  min-height: 200px;
}

.symptom-search-hint {
  margin: 8px 0 10px;
  font-size: 15px;
  line-height: 1.4;
}

.symptom-search-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.symptom-search-list {
  margin: 0;
  max-height: 320px;
  padding: 0;
  list-style: none;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.symptom-search-list__item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px 12px;
  padding: 10px 14px;
  font-size: 18px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.symptom-search-list__item:last-child {
  border-bottom: 0;
}

.symptom-search-list__item:hover {
  background: var(--el-fill-color-light);
}

.symptom-search-list__item.is-selected {
  background: #e6f4ff;
}

.symptom-search-list__name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.symptom-search-list__pinyin {
  font-size: 15px;
}

.condition-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.condition-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: #f0f9ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  font-size: 13px;
  color: #1890ff;
}

.condition-tag i {
  cursor: pointer;
  color: #909399;
}

.condition-tag i:hover {
  color: #f56c6c;
}

.score-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.score-btn {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #52c41a;
}

.score-btn:hover {
  background: #d9f7be;
  border-color: #95de64;
  color: #389e0d;
}

.scores-auto-hint {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.45;
}

.score-buttons :deep(.el-button) {
  font-size: 13px;
  padding: 4px 8px;
}

.level-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
  font-size: 13px;
}

.level-1 {
  color: #fff;
  background: #f56c6c;
}

.level-2 {
  color: #fff;
  background: #e6a23c;
}

.level-3 {
  color: #606266;
  background: #fadb14;
}

.level-4 {
  color: #fff;
  background: #67c23a;
}

.text-muted-foreground {
  color: var(--el-text-color-secondary);
}

:deep(.el-form-item) {
  margin-bottom: 4px;
}

/* 与左侧患者信息区对齐：右栏主诉/分诊/补充 标签与正文字号 */
.workbench-right--comfort :deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.workbench-right--comfort :deep(.el-input__inner),
.workbench-right--comfort :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.35;
}

.workbench-right--comfort :deep(.el-input__wrapper) {
  font-size: 14px;
  min-height: 34px;
}

.workbench-right--comfort :deep(.el-select__wrapper) {
  font-size: 14px;
  min-height: 34px;
}

.workbench-right--comfort :deep(.el-date-editor .el-input__inner) {
  font-size: 14px;
}

.workbench-right--comfort :deep(.el-input-number .el-input__inner) {
  font-size: 14px;
}

.workbench-right--comfort :deep(.el-radio-button__inner) {
  font-size: 13px;
  padding: 7px 10px;
}

/* 下半区表单列按宽度自动折行，提升窄宽度下可读性 */
.content-right :deep(.el-row) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 6px 8px;
}

.content-right :deep(.el-row > .el-col) {
  min-width: 0;
  width: auto !important;
  max-width: none !important;
  flex: 1 1 auto !important;
}

/* 对原先 span=24（100%）的列维持整行展示 */
.content-right :deep(.el-row > .el-col[style*='100%']) {
  grid-column: 1 / -1;
}

/* 分诊去向/科室/医生固定同一行显示，不参与自动换列 */
.content-right :deep(.triage-assign-row-fixed) {
  display: flex;
  flex-wrap: wrap;
  gap: 0 10px;
}

.content-right :deep(.triage-assign-row-fixed > .el-col) {
  min-width: 220px;
  flex: 1 1 220px !important;
  max-width: none !important;
  width: auto !important;
}

.patient-details--comfort :deep(.el-col[style*='100%']) {
  flex-basis: 100% !important;
}

.workbench-right--comfort .section-sub-title {
  font-size: 14px;
  margin-bottom: 4px;
}

.triage-focus-hint--size {
  font-size: 15px;
  line-height: 1.5;
}

.vital-mews-hint {
  margin: 2px 0 6px;
  font-size: 13px;
  line-height: 1.35;
}

.mews-total {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 2px 8px;
  background: #f0f9ff;
  border: 1px solid #d6e4ff;
  border-radius: 999px;
}

.mews-total__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.mews-total__value {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.mews-readonly {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 10px;
  font-size: 14px;
}

.mews-readonly__num {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.mews-readonly__unit {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mews-readonly__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.patient-info-card {
  overflow: visible;
}

@media (max-width: 1366px) {
  .register-like-layout {
    flex-direction: column;
    overflow: visible;
  }

  .register-like-layout__left,
  .register-like-layout__right {
    flex: 1 1 auto;
    min-width: 0;
    max-width: none;
    overflow: visible;
  }
}
</style>

<style>
/* 下拉 teleport 到 body，单独样式：宽下拉、大字号、选项自适应高度，避免被裁切 */
.patient-select-popper.el-select__popper,
.patient-select-popper {
  min-width: 400px;
  max-width: min(100vw - 32px, 560px);
  z-index: 5000;
}

.patient-select-popper .el-select-dropdown__item {
  height: auto !important;
  min-height: 5.5rem;
  line-height: 1.45;
  padding: 12px 16px;
  font-size: 16px;
  white-space: normal;
  align-items: flex-start;
}

.patient-select-popper .el-select-dropdown__item.is-selected {
  font-weight: 600;
}
</style>
