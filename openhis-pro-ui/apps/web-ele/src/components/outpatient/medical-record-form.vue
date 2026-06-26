<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto } from '#/api/emr/document';
import type { ApplyEmrTemplatePayload } from '#/emrEditor';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import {
  computed,
  inject,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

import { ChevronLeft, ChevronRight } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  createDocumentApi,
  getDocumentContentApi,
  getDocumentListApi,
  saveDocumentApi,
} from '#/api/emr/document';
import {
  createTemplateApi,
  getTemplateContentApi,
  serializeEmrTemplateContent,
} from '#/api/emr/template';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station';
import {
  buildMetadataValuesFromPatientInfo,
  isForceLogoutSession,
  normalizeEmrRootDoc,
  registerEmrUnsavedGuard,
  SimpleEditor,
} from '#/emrEditor';
import {
  defaultEmrEditorDoc,
  EDITOR_PAGE_MARGIN_PRESETS,
} from '#/emrEditor/components/simple-editor/editor-defaults';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import { EMR_DOCUMENT_TYPE_PROGRESS_NOTE } from '#/views/basic/emr-template/emr-progress-note.constants';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_STATION_KIND,
} from '#/views/basic/emr-template/emr-template-scope.constants';

import HistoryTemplatePanel from './history-template-panel.vue';

defineOptions({
  name: 'MedicalRecordForm',
});

const props = withDefaults(defineProps<Props>(), {
  stationKind: undefined,
  patientId: '',
  encounterId: '',
});

interface Props {
  /** 门诊 OUTPATIENT / 住院 INPATIENT，决定模板树可见范围 */
  stationKind?: EmrTemplateStationKind;
  patientInfo?: {
    age?: string;
    gender?: string;
    idCard?: string;
    medicalRecordNo?: string;
    name?: string;
    phone?: string;
  };
  patientId?: string;
  encounterId?: string;
}

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);
const userStore = useUserStore();

const editorRef = ref<InstanceType<typeof SimpleEditor> | null>(null);
const historyPanelRef = ref<InstanceType<typeof HistoryTemplatePanel> | null>(null);

/** 当前打开的服务端文书 ID（雪花 ID 字符串） */
const documentId = ref('');
/** 已选模板、尚未保存创建文书时的模板 ID */
const pendingTemplateId = ref('');
const documentReadOnly = ref(false);
const documentTypeCode = ref('');
const documentTitle = ref('');
const documentLoading = ref(false);

const printStandardMargins = EDITOR_PAGE_MARGIN_PRESETS.find((p) => p.label === '打印标准')
  ?.value ??
  EDITOR_PAGE_MARGIN_PRESETS[2]?.value ??
  EDITOR_PAGE_MARGIN_PRESETS[0]?.value ?? { top: 0, right: 0, bottom: 0, left: 0 };

const editorPageMargins = ref({ ...printStandardMargins });
const saveLoading = ref(false);
const saveAsTemplateLoading = ref(false);

/** 服务端下发的元数据（打开文书时合并） */
const serverMetadataValues = ref<Record<string, string>>({});

const editorMetaValues = computed(() => ({
  ...buildMetadataValuesFromPatientInfo(props.patientInfo),
  ...serverMetadataValues.value,
}));

const editorSurface = ref<'document' | 'template'>('document');
const templateLayoutLocked = ref(false);

const leftCollapsed = ref(false);
const SIDEBAR_WIDTH = 280;
const COLLAPSED_WIDTH = 48;

const recordDoc = ref<JSONContent | null>(null);

/** 最近一次从服务端载入或远程保存成功后的正文快照，用于「放弃修改」回退 */
const savedContentSnapshot = ref<JSONContent>(structuredClone(defaultEmrEditorDoc));

function captureContentSnapshot(doc: JSONContent) {
  savedContentSnapshot.value = structuredClone(doc);
}

/** IndexedDB 草稿主键：已创建文书用 documentId，仅选模板未保存时用就诊+模板 */
const editorDocId = computed(() => {
  if (documentId.value) return `emr-doc-${documentId.value}`;
  const tplId = pendingTemplateId.value;
  const patientId = (props.patientId ?? '').trim();
  const encounterId = (props.encounterId ?? '').trim();
  if (tplId && patientId) {
    return `emr-draft-${patientId}-${encounterId || 'no-enc'}-${tplId}`;
  }
  return '';
});

const remoteSaveHandler = computed(() =>
  documentId.value && !documentReadOnly.value ? remoteSaveDocument : undefined,
);

const historyTemplateTab = ref<'history' | 'template'>('template');

/** 住院：当前就诊下病程记录列表（按创建时间升序，用于上一张/下一张） */
const progressNoteNavList = ref<DocumentSummaryDto[]>([]);

const isInpatientStation = computed(
  () => props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT,
);

function sortDocumentsByCreatedAtAsc(rows: DocumentSummaryDto[]): DocumentSummaryDto[] {
  return [...rows].toSorted((a, b) => {
    const ta = Date.parse(a.createdAt ?? '') || 0;
    const tb = Date.parse(b.createdAt ?? '') || 0;
    return ta - tb;
  });
}

const progressNoteNavIndex = computed(() => {
  const id = documentId.value;
  if (!id) return -1;
  return progressNoteNavList.value.findIndex((row) => preserveApiIdForRequest(row.id) === id);
});

const showProgressNoteNav = computed(
  () =>
    isInpatientStation.value &&
    documentTypeCode.value === EMR_DOCUMENT_TYPE_PROGRESS_NOTE &&
    progressNoteNavList.value.length > 1,
);

const canProgressNotePrev = computed(
  () => showProgressNoteNav.value && progressNoteNavIndex.value > 0,
);

const canProgressNoteNext = computed(
  () =>
    showProgressNoteNav.value &&
    progressNoteNavIndex.value >= 0 &&
    progressNoteNavIndex.value < progressNoteNavList.value.length - 1,
);

/** 自动保存失败提示节流（避免连续 toast） */
let lastRemoteSaveErrorToastAt = 0;
const REMOTE_SAVE_ERROR_TOAST_MS = 8000;

function handleRemoteSaveError() {
  const now = Date.now();
  if (now - lastRemoteSaveErrorToastAt < REMOTE_SAVE_ERROR_TOAST_MS) return;
  lastRemoteSaveErrorToastAt = now;
  ElMessage.warning('自动保存失败，请检查网络后点击「保存」');
}

/** 远程自动/手动保存：写入 emr_document */
async function remoteSaveDocument(content: JSONContent) {
  if (!documentId.value || documentReadOnly.value) return;
  await saveDocumentApi({
    documentId: documentId.value,
    content,
  });
  captureContentSnapshot(content);
}

/** 编辑器是否存在未保存修改（依赖自动保存脏标，载入模板基线后为已保存） */
function isEditorDirty(): boolean {
  return editorRef.value?.isDirty?.() === true;
}

/** 将编辑器恢复为最近一次已保存快照（并清除本地草稿脏标） */
function revertEditorToSavedSnapshot() {
  recordDoc.value = structuredClone(savedContentSnapshot.value);
  void Promise.resolve().then(() => {
    editorRef.value?.markSaved?.();
    editorRef.value?.clearDraft?.();
  });
}

/** 放弃未保存：离开当前会话 / 切换打开其它模板或历史文书 */
type EmrDiscardContext = 'leave' | 'replace';

async function applyDiscardAfterConfirm(context: EmrDiscardContext) {
  if (context === 'replace') {
    // 调用方即将载入新正文：仅清掉「未落库的模板草稿」会话
    if (!documentId.value && pendingTemplateId.value) {
      resetDocumentSession();
    }
    return;
  }
  // 离开（换 Tab/患者/路由）：恢复服务端已保存或清空未落库会话
  if (documentId.value) {
    documentLoading.value = true;
    try {
      const dto = await getDocumentContentApi({ documentId: documentId.value });
      applyDocumentDto(dto);
    } catch {
      revertEditorToSavedSnapshot();
      ElMessage.warning('无法从服务端恢复，已回退到本地上次保存内容');
    } finally {
      documentLoading.value = false;
    }
    return;
  }
  resetDocumentSession();
}

/** 切换患者/模板/离开前确认未保存 */
async function confirmDiscardUnsavedIfNeeded(
  context: EmrDiscardContext = 'leave',
): Promise<boolean> {
  if (!isEditorDirty()) return true;
  const message =
    context === 'replace'
      ? '当前病历有未保存的修改，放弃后将打开所选内容，是否继续？'
      : '当前病历有未保存的修改，是否放弃修改并继续？';
  try {
    await ElMessageBox.confirm(message, '未保存提示', {
      type: 'warning',
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    });
    await applyDiscardAfterConfirm(context);
    return true;
  } catch {
    return false;
  }
}

/** 重置前确认：仅恢复基线，不清 pending（随后由 reload 刷新） */
async function confirmRevertBaselineForReset(): Promise<boolean> {
  if (!isEditorDirty()) return true;
  try {
    await ElMessageBox.confirm('是否放弃未保存的修改并恢复到上次保存的内容？', '重置确认', {
      type: 'warning',
      confirmButtonText: '恢复',
      cancelButtonText: '继续编辑',
    });
    return true;
  } catch {
    return false;
  }
}

/** 打开/新建病历前须已接诊（有 encounterId） */
function requireEncounterIdForDocument(options?: { silent?: boolean }): boolean {
  const encounterId = (props.encounterId ?? '').trim();
  if (!encounterId) {
    if (!options?.silent) {
      ElMessage.warning('请先接诊后再打开或新建病历');
    }
    return false;
  }
  return true;
}

/** 将接口文书 DTO 写入编辑器状态 */
function applyDocumentDto(dto: {
  content?: unknown;
  documentTypeCode?: string;
  id?: number | string;
  metadataValues?: Record<string, string>;
  readOnly?: boolean;
  title?: string;
}) {
  const id = preserveApiIdForRequest(dto.id);
  if (id) {
    documentId.value = id;
    pendingTemplateId.value = '';
  }
  if (dto.documentTypeCode) {
    documentTypeCode.value = dto.documentTypeCode;
  }
  if (dto.title !== undefined) {
    documentTitle.value = (dto.title ?? '').trim();
  }
  documentReadOnly.value = Boolean(dto.readOnly);
  if (dto.metadataValues) {
    serverMetadataValues.value = { ...dto.metadataValues };
  }
  const doc = normalizeEmrRootDoc(dto.content);
  recordDoc.value = doc;
  captureContentSnapshot(doc);
  editorSurface.value = 'document';
  templateLayoutLocked.value = false;
  void Promise.resolve().then(() => {
    editorRef.value?.markSaved?.();
    editorRef.value?.clearDraft?.();
  });
}

/** 清空当前文书会话（切换患者等） */
function resetDocumentSession() {
  documentId.value = '';
  pendingTemplateId.value = '';
  documentReadOnly.value = false;
  documentTypeCode.value = '';
  documentTitle.value = '';
  serverMetadataValues.value = {};
  const blank = structuredClone(defaultEmrEditorDoc);
  recordDoc.value = blank;
  captureContentSnapshot(blank);
  editorRef.value?.markSaved?.();
}

/** 本地载入模板正文，不创建服务端文书（保存时再 create） */
async function applyTemplateLocally(
  typeCode: string,
  templateId: string,
  options?: { silent?: boolean },
) {
  const tplId = preserveApiIdForRequest(templateId);
  if (!tplId) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  documentLoading.value = true;
  try {
    const tpl = await getTemplateContentApi({ templateId: tplId });
    documentId.value = '';
    documentReadOnly.value = false;
    documentTypeCode.value = typeCode;
    pendingTemplateId.value = tplId;
    serverMetadataValues.value = {};
    const doc = normalizeEmrRootDoc(tpl.content);
    recordDoc.value = doc;
    captureContentSnapshot(doc);
    editorSurface.value = 'document';
    templateLayoutLocked.value = false;
    void Promise.resolve().then(() => {
      editorRef.value?.markSaved?.();
      editorRef.value?.clearDraft?.();
    });
    if (!options?.silent) {
      ElMessage.success('已载入模板，编辑后请点击保存');
    }
  } catch {
    if (!options?.silent) {
      ElMessage.error('载入模板失败');
    }
    throw new Error('apply template failed');
  } finally {
    documentLoading.value = false;
  }
}

/** 保存前确保已有服务端文书：未创建则按待选模板 create */
async function ensureDocumentCreated(): Promise<null | string> {
  if (documentId.value) return documentId.value;
  const tplId = pendingTemplateId.value;
  const typeCode = documentTypeCode.value.trim();
  const patientId = (props.patientId ?? '').trim();
  const encounterId = (props.encounterId ?? '').trim();
  if (!tplId || !typeCode) return null;
  if (!patientId) {
    ElMessage.warning('请先选择患者');
    return null;
  }
  if (!requireEncounterIdForDocument()) return null;
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return null;
  }
  const created = await createDocumentApi({
    patientId,
    encounterId: encounterId || undefined,
    documentTypeCode: typeCode,
    templateId: tplId,
  });
  applyDocumentDto(created);
  if (isInpatientStation.value) {
    await refreshProgressNoteNavList();
  }
  return documentId.value;
}

/** 拉取当前就诊病程列表（仅 PROGRESS_NOTE，用于导航） */
async function refreshProgressNoteNavList() {
  const patientId = (props.patientId ?? '').trim();
  if (!patientId || !isInpatientStation.value) {
    progressNoteNavList.value = [];
    return;
  }
  const encounterId = (props.encounterId ?? '').trim();
  try {
    const data = await getDocumentListApi({
      patientId,
      encounterId: encounterId || undefined,
      documentTypeCode: EMR_DOCUMENT_TYPE_PROGRESS_NOTE,
      pageNo: 1,
      pageSize: 200,
    });
    const parsed = parsePagedRowsFromApiData<DocumentSummaryDto>(data);
    progressNoteNavList.value = sortDocumentsByCreatedAtAsc(parsed.content ?? []);
  } catch {
    progressNoteNavList.value = [];
  }
}

/** 从服务端重新加载当前文书 */
async function reloadCurrentDocument(options?: { silent?: boolean }) {
  if (pendingTemplateId.value && !documentId.value) {
    const typeCode = documentTypeCode.value.trim();
    if (!typeCode) {
      if (!options?.silent) ElMessage.info('请先在左侧选择模板');
      return;
    }
    try {
      await applyTemplateLocally(typeCode, pendingTemplateId.value, { silent: true });
      if (!options?.silent) ElMessage.success('已刷新模板');
    } catch {
      if (!options?.silent) ElMessage.error('刷新失败');
    }
    return;
  }
  if (!documentId.value) {
    if (!options?.silent) {
      ElMessage.info('请先在左侧选择模板或历史病历');
    }
    return;
  }
  if (documentLoading.value) return;
  documentLoading.value = true;
  try {
    const dto = await getDocumentContentApi({ documentId: documentId.value });
    applyDocumentDto(dto);
    if (!options?.silent) {
      ElMessage.success('已刷新');
    }
  } catch {
    if (!options?.silent) {
      ElMessage.error('刷新失败');
    }
    throw new Error('reload document failed');
  } finally {
    documentLoading.value = false;
  }
}

/** 是否可保存：已有文书，或已选模板待首次落库 */
const canSaveDocument = computed(() =>
  Boolean(documentId.value || (pendingTemplateId.value && documentTypeCode.value)),
);

/** 当前编辑器有正文且已知文书类型时可存为个人模板 */
const canSaveAsTemplate = computed(
  () =>
    Boolean(documentTypeCode.value.trim()) && !documentReadOnly.value && Boolean(recordDoc.value),
);

function resolveEncounterClassForStation(): string {
  if (props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.IMP;
  }
  return EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
}

function suggestDoctorStationTemplateCode(name: string): string {
  const base = (name.trim() || 'tpl')
    .replaceAll(/\s+/g, '_')
    .replaceAll(/[^\w-]/g, '')
    .slice(0, 32);
  return `${base}_ds_${String(Date.now()).slice(-8)}`.slice(0, 64);
}

/** 医生站「存为模版」：固定个人模板 + 当前就诊场景 */
async function handleSaveAsTemplate() {
  if (!canSaveAsTemplate.value) {
    if (!documentTypeCode.value.trim()) {
      ElMessage.warning('请先选择模板或打开病历以确定文书类型');
    } else if (documentReadOnly.value) {
      ElMessage.warning('当前病历为只读，无法存为模版');
    } else {
      ElMessage.warning('没有可保存为模板的内容');
    }
    return;
  }
  const practitionerId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as Record<string, unknown> | undefined,
  );
  if (!practitionerId) {
    ElMessage.warning('当前账号未关联医务人员，无法保存个人模板');
    return;
  }
  const content = editorRef.value?.getJSON?.() ?? recordDoc.value;
  if (!content) {
    ElMessage.warning('没有可保存为模板的内容');
    return;
  }
  try {
    const { value } = await ElMessageBox.prompt('将当前病历另存为您的个人模板', '存为模版', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPlaceholder: '模板名称',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入模板名称',
    });
    const name = (value ?? '').trim();
    if (!name) return;

    saveAsTemplateLoading.value = true;
    await createTemplateApi({
      name,
      code: suggestDoctorStationTemplateCode(name),
      documentTypeCode: documentTypeCode.value.trim(),
      scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL,
      encounterClassCode: resolveEncounterClassForStation(),
      ownerPractitionerId: practitionerId,
      fromDoctorStation: true,
      content: serializeEmrTemplateContent(content),
      enabled: true,
    });
    ElMessage.success('已存为个人模板');
    void historyPanelRef.value?.refreshTemplateTree?.();
  } catch {
    // 取消或错误由拦截器提示
  } finally {
    saveAsTemplateLoading.value = false;
  }
}

/** 手动保存：无文书时先 create，再 save */
async function handleSave() {
  if (!canSaveDocument.value) {
    ElMessage.warning('请先在左侧选择模板');
    return;
  }
  if (documentReadOnly.value) {
    ElMessage.warning('当前病历为只读，无法保存');
    return;
  }
  const content = editorRef.value?.getJSON?.() ?? recordDoc.value;
  if (!content) {
    ElMessage.warning('没有可保存的内容');
    return;
  }
  saveLoading.value = true;
  try {
    const id = await ensureDocumentCreated();
    if (!id) return;
    await saveDocumentApi({
      documentId: id,
      content,
    });
    captureContentSnapshot(content);
    recordDoc.value = content;
    editorRef.value?.markSaved?.();
    await editorRef.value?.clearDraft?.();
    ElMessage.success('保存成功');
    void historyPanelRef.value?.refreshHistory?.();
    if (isInpatientStation.value) {
      await refreshProgressNoteNavList();
    }
  } catch {
    // 业务/网络错误已由 request 拦截器提示
  } finally {
    saveLoading.value = false;
  }
}

/** 重置为服务端最近一次保存内容，或重新载入当前模板（不经过「放弃」清 pending） */
async function handleReset() {
  if (!documentId.value && !pendingTemplateId.value) return;
  const ok = await confirmRevertBaselineForReset();
  if (!ok) return;
  await reloadCurrentDocument();
}

/** 打开已有文书（历史列表 / 病程导航 / 住院默认加载） */
async function openDocumentFromSummary(
  row: DocumentSummaryDto,
  options?: { silent?: boolean; skipUnsavedConfirm?: boolean },
) {
  if (documentLoading.value) return;
  const id = preserveApiIdForRequest(row.id) ?? '';
  if (!id) {
    if (!options?.silent) ElMessage.warning('该病历缺少有效 ID');
    return;
  }
  if (!requireEncounterIdForDocument({ silent: options?.silent })) {
    return;
  }
  if (!options?.skipUnsavedConfirm) {
    const proceed = await confirmDiscardUnsavedIfNeeded('replace');
    if (!proceed) return;
  }
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  documentLoading.value = true;
  try {
    const dto = await getDocumentContentApi({ documentId: id });
    applyDocumentDto({
      ...dto,
      documentTypeCode: dto.documentTypeCode ?? row.documentTypeCode,
      title: dto.title ?? row.title,
    });
    if (!options?.silent) {
      ElMessage.success('已打开病历');
    }
    void historyPanelRef.value?.refreshHistory?.();
  } catch {
    if (!options?.silent) ElMessage.error('打开病历失败');
  } finally {
    documentLoading.value = false;
  }
}

/** 从历史列表按 documentId 打开已有文书 */
async function handleSelectDocument(row: DocumentSummaryDto) {
  await openDocumentFromSummary(row);
}

/** 住院：静默打开最新病程（侧栏保持模版 Tab） */
async function loadInpatientLatestProgressNote(options?: { silent?: boolean }) {
  if (!isInpatientStation.value) return;
  const patientId = (props.patientId ?? '').trim();
  if (!patientId) return;
  const encounterId = (props.encounterId ?? '').trim();
  if (!encounterId) return;
  await refreshProgressNoteNavList();
  const list = progressNoteNavList.value;
  if (list.length === 0) return;
  const latest = list[list.length - 1];
  if (!latest) return;
  await openDocumentFromSummary(latest, {
    silent: options?.silent ?? true,
    skipUnsavedConfirm: true,
  });
}

/** 病程上一张 / 下一张 */
async function navigateProgressNote(delta: -1 | 1) {
  const list = progressNoteNavList.value;
  const idx = progressNoteNavIndex.value;
  if (idx < 0) return;
  const nextIdx = idx + delta;
  if (nextIdx < 0 || nextIdx >= list.length) return;
  const row = list[nextIdx];
  if (!row) return;
  await openDocumentFromSummary(row, { silent: true });
}

/**
 * 套用模版：打开或创建对应文书类型记录（服务端持久化）。
 */
function applyEmrTemplate(payload?: ApplyEmrTemplatePayload) {
  if (payload?.content !== undefined && payload.content !== null) {
    recordDoc.value = payload.content;
  }
}

async function handleHistorySelect(item: EmrTemplateTreeNode) {
  if (item.isDocumentType) return;
  if (documentLoading.value) return;

  const typeCode = (item.documentTypeCode ?? item.template?.documentTypeCode ?? '').trim();
  if (!typeCode) {
    ElMessage.warning('该模板未关联文书类型');
    return;
  }

  const proceed = await confirmDiscardUnsavedIfNeeded('replace');
  if (!proceed) return;
  if (!requireEncounterIdForDocument()) {
    return;
  }

  const tplId = preserveApiIdForRequest(item.templateId);
  if (!tplId) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  await applyTemplateLocally(typeCode, tplId);
}

function handleTabChange(tab: 'history' | 'template') {
  historyTemplateTab.value = tab;
}

/** 切换患者/就诊时清空会话并（住院）加载最新病程 */
let skipPatientEncounterWatch = true;
const isTabActive = ref(false);
const pendingPatientEncounterReload = ref(false);

watch(
  () => [props.patientId, props.encounterId] as const,
  async ([nextPatientId, nextEncounterId], [prevPatientId, prevEncounterId]) => {
    if (!isTabActive.value) {
      pendingPatientEncounterReload.value = true;
      return;
    }
    if (skipPatientEncounterWatch) {
      skipPatientEncounterWatch = false;
      if (isInpatientStation.value && nextPatientId) {
        await loadInpatientLatestProgressNote({ silent: true });
      }
      return;
    }
    if (nextPatientId === prevPatientId && nextEncounterId === prevEncounterId) return;
    resetDocumentSession();
    progressNoteNavList.value = [];
    if (isInpatientStation.value && nextPatientId) {
      await loadInpatientLatestProgressNote({ silent: true });
    }
  },
);

onActivated(() => {
  isTabActive.value = true;
  if (!pendingPatientEncounterReload.value && !skipPatientEncounterWatch) return;
  pendingPatientEncounterReload.value = false;
  void (async () => {
    if (skipPatientEncounterWatch) {
      skipPatientEncounterWatch = false;
      if (isInpatientStation.value && props.patientId) {
        await loadInpatientLatestProgressNote({ silent: true });
      }
      return;
    }
    resetDocumentSession();
    progressNoteNavList.value = [];
    if (isInpatientStation.value && props.patientId) {
      await loadInpatientLatestProgressNote({ silent: true });
    }
  })();
});

onDeactivated(() => {
  isTabActive.value = false;
});

onMounted(() => {
  registerEmrUnsavedGuard(confirmDiscardUnsavedIfNeeded);
});

onUnmounted(() => {
  registerEmrUnsavedGuard(null);
});

onBeforeRouteLeave(async (_to, _from, next) => {
  if (isForceLogoutSession()) {
    next(true);
    return;
  }
  const ok = await confirmDiscardUnsavedIfNeeded();
  next(ok);
});

defineExpose({
  applyEmrTemplate,
  confirmDiscardUnsavedIfNeeded,
  reload: reloadCurrentDocument,
  save: handleSave,
});
</script>

<template>
  <div class="flex h-full overflow-hidden bg-white">
    <div
      class="flex-shrink-0 bg-white transition-all duration-300"
      :style="{ width: leftCollapsed ? `${COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px` }"
    >
      <div v-if="!leftCollapsed" class="h-full">
        <HistoryTemplatePanel
          ref="historyPanelRef"
          :station-kind="stationKind"
          :patient-id="patientId"
          :encounter-id="encounterId"
          :current-document-id="documentId"
          @select="handleHistorySelect"
          @select-document="handleSelectDocument"
          @tab-change="handleTabChange"
        />
      </div>
    </div>

    <div class="relative flex w-px flex-shrink-0 items-start justify-center bg-[#ebeef5]">
      <ElButton
        circle
        class="absolute left-1/2 top-[30px] z-10 h-6 w-6 -translate-x-1/2 border border-[#ebeef5] bg-white shadow-[0_2px_8px_rgba(0,0,0,0.12)] transition-colors hover:border-[#409eff] hover:bg-[#409eff] hover:text-white"
        @click="leftCollapsed = !leftCollapsed"
      >
        <ChevronRight v-if="leftCollapsed" class="h-3 w-3" />
        <ChevronLeft v-else class="h-3 w-3" />
      </ElButton>
    </div>

    <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
      <div class="flex h-[42px] flex-shrink-0 flex-wrap items-center gap-1 border-b px-3">
        <ElButton
          type="primary"
          :loading="saveAsTemplateLoading"
          :disabled="!canSaveAsTemplate"
          @click="handleSaveAsTemplate"
        >
          存为模版
        </ElButton>
        <ElButton :loading="documentLoading" @click="reloadCurrentDocument">刷新</ElButton>
        <template v-if="showProgressNoteNav">
          <ElButton
            :disabled="!canProgressNotePrev || documentLoading"
            @click="navigateProgressNote(-1)"
          >
            上一张
          </ElButton>
          <ElButton
            :disabled="!canProgressNoteNext || documentLoading"
            @click="navigateProgressNote(1)"
          >
            下一张
          </ElButton>
        </template>
        <ElButton :disabled="!canSaveDocument" @click="handleReset">重置</ElButton>
        <ElButton
          type="primary"
          :loading="saveLoading"
          :disabled="!canSaveDocument || documentReadOnly"
          @click="handleSave"
        >
          保存
        </ElButton>
        <span
          v-if="documentReadOnly"
          class="ml-2 text-xs"
          style="color: var(--el-text-color-secondary)"
        >
          只读
        </span>
      </div>
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div
          v-loading="documentLoading"
          class="medical-record-form__editor-wrap h-full border-t p-3"
        >
          <SimpleEditor
            ref="editorRef"
            v-model="recordDoc"
            v-model:layout-locked="templateLayoutLocked"
            v-model:page-margins="editorPageMargins"
            class="medical-record-form__editor"
            :surface="editorSurface"
            :doc-id="editorDocId"
            :read-only="documentReadOnly"
            :remote-save="remoteSaveHandler"
            :on-remote-save-error="handleRemoteSaveError"
            placeholder="请选择左侧模版或历史病历…"
            :metadata-values="editorMetaValues"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.medical-record-form__editor-wrap {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.medical-record-form__editor {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.medical-record-form__editor :deep(.simple-editor) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.medical-record-form__editor :deep(.simple-editor__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

:deep(.form-item-horizontal) {
  flex-direction: row !important;
  align-items: flex-start !important;

  > *:first-child {
    flex-shrink: 0;
    justify-content: flex-end;
    min-width: 80px;
    padding-top: 0.5rem;
    margin-right: 0.5rem !important;
    margin-bottom: 0 !important;
  }
}

:deep(.form-label-horizontal) {
  flex-shrink: 0;
  justify-content: flex-end;
  min-width: 80px;
  padding-top: 0.5rem;
  margin-right: 0.5rem !important;
  margin-bottom: 0 !important;
}
</style>
