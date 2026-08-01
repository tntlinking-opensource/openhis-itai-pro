<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto, EmrPrintConfigPayload } from '#/api/emr/document';
import type { ApplyEmrTemplatePayload } from '#/emrEditor';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import {
  computed,
  inject,
  nextTick,
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

import { Close } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElMessage } from 'element-plus';

import {
  createDocumentApi,
  generateDocumentPdfApi,
  getDocumentContentApi,
  getDocumentListApi,
  saveDocumentApi,
} from '#/api/emr/document';
import { resolveEncounterMetadataApi } from '#/api/emr/encounter-metadata';
import {
  createTemplateApi,
  getTemplateContentApi,
  serializeEmrTemplateContent,
} from '#/api/emr/template';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station';
import { useCompactViewport } from '#/composables/use-compact-viewport';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';
import {
  applyMetadataValuesToContentJson,
  buildMetadataValuesFromPatientInfo,
  isForceLogoutSession,
  mergeEmrMetadataValues,
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
  type EmrTemplateScopeLevel,
} from '#/views/basic/emr-template/emr-template-scope.constants';

import EmrImportAdviceDialog from './emr-import/emr-import-advice-dialog.vue';
import EmrImportDiagnosisDialog from './emr-import/emr-import-diagnosis-dialog.vue';
import {
  captureEmrIslandInsertTarget,
  insertEmrImportText,
  type EmrIslandInsertTarget,
} from './emr-import/insert-emr-import-text';
import EmrPdfPreviewDialog from './emr-pdf-preview-dialog.vue';
import HistoryTemplatePanel from './history-template-panel.vue';
import SaveAsEmrTemplateDialog from './save-as-emr-template-dialog.vue';

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
    /** 医保/费用性质展示 */
    insurance?: string;
    medicalRecordNo?: string;
    name?: string;
    phone?: string;
  };
  patientId?: string;
  encounterId?: string;
}

type PageMargins = { bottom: string; left: string; right: string; top: string };

/** 已打开的文书会话（一 Tab 一编辑器） */
interface EmrOpenTab {
  documentId: string;
  documentReadOnly: boolean;
  documentTitle: string;
  documentTypeCode: string;
  /** 文书类型中文名，用于子 Tab 标题 */
  documentTypeName: string;
  editorSurface: 'document' | 'template';
  key: string;
  pageMargins: PageMargins;
  pendingTemplateId: string;
  recordDoc: JSONContent | null;
  savedContentSnapshot: JSONContent;
  serverMetadataValues: Record<string, string>;
  templateLayoutLocked: boolean;
}

const MAX_OPEN_TABS = 4;

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);
const userStore = useUserStore();

const editorRefMap = new Map<string, InstanceType<typeof SimpleEditor>>();
const historyPanelRef = ref<InstanceType<typeof HistoryTemplatePanel> | null>(null);

const printStandardMargins: PageMargins = EDITOR_PAGE_MARGIN_PRESETS.find(
  (p) => p.label === '打印标准',
)?.value ??
  EDITOR_PAGE_MARGIN_PRESETS[2]?.value ??
  EDITOR_PAGE_MARGIN_PRESETS[0]?.value ?? {
    top: '0',
    right: '0',
    bottom: '0',
    left: '0',
  };

let tabKeySeq = 0;
function nextTabKey(): string {
  tabKeySeq += 1;
  return `emr-tab-${tabKeySeq}`;
}

function createBlankTab(): EmrOpenTab {
  const blank = structuredClone(defaultEmrEditorDoc);
  return {
    key: nextTabKey(),
    documentId: '',
    pendingTemplateId: '',
    documentReadOnly: false,
    documentTypeCode: '',
    documentTypeName: '',
    documentTitle: '',
    serverMetadataValues: {},
    recordDoc: blank,
    savedContentSnapshot: structuredClone(blank),
    editorSurface: 'document',
    templateLayoutLocked: false,
    pageMargins: { ...printStandardMargins },
  };
}

const openTabs = ref<EmrOpenTab[]>([createBlankTab()]);
const activeTabKey = ref(openTabs.value[0]!.key);
const documentLoading = ref(false);
const saveLoading = ref(false);
const saveAsTemplateLoading = ref(false);
const saveAsTemplateDialogOpen = ref(false);
const saveAsTemplateDialogRef = ref<InstanceType<typeof SaveAsEmrTemplateDialog> | null>(null);
const pdfLoading = ref(false);
const pdfPreviewVisible = ref(false);
const pdfPreviewAutoPrint = ref(false);
const pdfPreviewBase64 = ref('');
const pdfPreviewPageCount = ref<number | undefined>();
const pdfPreviewTitle = ref('打印预览');

const importDiagnosisDialogOpen = ref(false);
const importAdviceDialogOpen = ref(false);
/** 最近一次聚焦的辅助检查等数据元岛（工具栏点击会抢焦点，需提前记住） */
const lastIslandInsertTarget = ref<EmrIslandInsertTarget | null>(null);
/** 打开带入弹窗时冻结的插入目标 */
const pendingIslandInsertTarget = ref<EmrIslandInsertTarget | null>(null);

function onEditorWrapFocusIn(ev: FocusEvent) {
  const target = ev.target;
  if (!(target instanceof HTMLElement)) return;
  const hit = captureEmrIslandInsertTarget();
  if (hit) {
    lastIslandInsertTarget.value = hit;
    return;
  }
  // 焦点回到正文时清除岛目标，避免误写入上次的辅助检查
  if (target.closest('.ProseMirror') || target.classList.contains('ProseMirror')) {
    lastIslandInsertTarget.value = null;
  }
}

const activeTab = computed(
  () => openTabs.value.find((t) => t.key === activeTabKey.value) ?? openTabs.value[0]!,
);

const documentId = computed(() => activeTab.value.documentId);
const pendingTemplateId = computed(() => activeTab.value.pendingTemplateId);
const documentReadOnly = computed(() => activeTab.value.documentReadOnly);
const documentTypeCode = computed(() => activeTab.value.documentTypeCode);

const openDocumentIds = computed(() =>
  openTabs.value.map((t) => t.documentId).filter((id) => Boolean(id)),
);

/** 子 Tab 脏标（模板可读的响应式映射） */
const tabDirtyMap = ref<Record<string, boolean>>({});

const leftCollapsed = ref(false);
const COLLAPSED_WIDTH = 48;

const { pickPreset } = useCompactViewport();

/** 侧栏展开宽度：<1680 压缩，≥1680（含 1920）保持 default */
const sidebarExpandedWidthPx = pickPreset(DOCTOR_STATION_SIDEBAR_WIDTH_PX.emrHistoryTemplate);

/** 住院：当前就诊下病程记录列表（按创建时间升序，用于上一张/下一张） */
const progressNoteNavList = ref<DocumentSummaryDto[]>([]);

const isInpatientStation = computed(
  () => props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT,
);

const importStationScope = computed(() =>
  isInpatientStation.value ? ('inpatient' as const) : ('outpatient' as const),
);

/** 插入诊断 / 带入医嘱：需就诊上下文且文书可编辑（编辑器就绪在点击时再校验） */
const canImportClinicalText = computed(() => {
  const encounterId = (props.encounterId ?? '').trim();
  const patientId = (props.patientId ?? '').trim();
  if (!encounterId || !patientId) return false;
  if (documentReadOnly.value) return false;
  return Boolean(
    activeTab.value.documentId || activeTab.value.pendingTemplateId || activeTab.value.recordDoc,
  );
});

function resolveIslandInsertTarget(): EmrIslandInsertTarget | null {
  const last = lastIslandInsertTarget.value;
  if (last?.el?.isConnected && !last.el.disabled && !last.el.readOnly) {
    const start = last.el.selectionStart ?? last.el.value.length;
    const end = last.el.selectionEnd ?? start;
    return { el: last.el, start, end };
  }
  return captureEmrIslandInsertTarget();
}

function openImportDiagnosisDialog() {
  // 工具栏按钮会抢走焦点；用最近聚焦过的数据元岛（含辅助检查）
  pendingIslandInsertTarget.value = resolveIslandInsertTarget();
  importDiagnosisDialogOpen.value = true;
}

function openImportAdviceDialog() {
  pendingIslandInsertTarget.value = resolveIslandInsertTarget();
  importAdviceDialogOpen.value = true;
}

function handleImportInsert(text: string) {
  const editorComp = getActiveEditor();
  const editor = editorComp?.getEditor?.() ?? null;
  const ok = insertEmrImportText({
    editor,
    text,
    islandTarget: pendingIslandInsertTarget.value,
  });
  pendingIslandInsertTarget.value = null;
  if (!ok) {
    ElMessage.warning('插入失败：请先将光标置于病历正文或辅助检查等可编辑处');
    return;
  }
  onEditorChange(activeTabKey.value);
  ElMessage.success('已插入');
}

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

function setEditorRef(tabKey: string, el: Element | InstanceType<typeof SimpleEditor> | null) {
  if (el && typeof el === 'object' && 'isDirty' in el) {
    editorRefMap.set(tabKey, el as InstanceType<typeof SimpleEditor>);
  } else {
    editorRefMap.delete(tabKey);
  }
}

function getEditor(tabKey: string) {
  return editorRefMap.get(tabKey);
}

function getActiveEditor() {
  return getEditor(activeTabKey.value);
}

function captureContentSnapshot(tab: EmrOpenTab, doc: JSONContent) {
  tab.savedContentSnapshot = structuredClone(doc);
}

function editorDocIdForTab(tab: EmrOpenTab): string {
  if (tab.documentId) return `emr-doc-${tab.documentId}`;
  const tplId = tab.pendingTemplateId;
  const patientId = (props.patientId ?? '').trim();
  const encounterId = (props.encounterId ?? '').trim();
  if (tplId && patientId) {
    return `emr-draft-${patientId}-${encounterId || 'no-enc'}-${tplId}`;
  }
  return '';
}

function remoteSaveForTab(tabKey: string) {
  return async (content: JSONContent) => {
    const tab = openTabs.value.find((t) => t.key === tabKey);
    if (!tab?.documentId || tab.documentReadOnly) return;
    await saveDocumentApi({
      documentId: tab.documentId,
      content,
    });
    captureContentSnapshot(tab, content);
  };
}

function remoteSaveHandlerForTab(tab: EmrOpenTab) {
  return tab.documentId && !tab.documentReadOnly ? remoteSaveForTab(tab.key) : undefined;
}

function isTabDirty(tabKey: string): boolean {
  return Boolean(tabDirtyMap.value[tabKey]);
}

function refreshTabDirty(tabKey: string) {
  const dirty = getEditor(tabKey)?.isDirty?.() === true;
  if (tabDirtyMap.value[tabKey] === dirty) return;
  tabDirtyMap.value = { ...tabDirtyMap.value, [tabKey]: dirty };
}

function onEditorChange(tabKey: string) {
  void nextTick(() => refreshTabDirty(tabKey));
}

function markTabClean(tabKey: string) {
  if (!tabDirtyMap.value[tabKey]) return;
  tabDirtyMap.value = { ...tabDirtyMap.value, [tabKey]: false };
}

function isActiveEditorDirty(): boolean {
  refreshTabDirty(activeTabKey.value);
  return isTabDirty(activeTabKey.value) || getEditor(activeTabKey.value)?.isDirty?.() === true;
}

function isAnyEditorDirty(): boolean {
  for (const tab of openTabs.value) {
    refreshTabDirty(tab.key);
    if (isTabDirty(tab.key) || getEditor(tab.key)?.isDirty?.() === true) return true;
  }
  return false;
}

/** 是否像文书类型编码（全大写+下划线），避免 Tab 显示英文 code */
function looksLikeDocumentTypeCode(text: string): boolean {
  return /^[A-Z][A-Z0-9_]*$/.test(text);
}

function tabDisplayTitle(tab: EmrOpenTab): string {
  const title = (tab.documentTitle ?? '').trim();
  if (title && !looksLikeDocumentTypeCode(title)) return title;
  const typeName = (tab.documentTypeName ?? '').trim();
  if (typeName) return typeName;
  if (tab.pendingTemplateId) return '新建文书';
  return '未命名';
}

function revertTabToSavedSnapshot(tab: EmrOpenTab) {
  tab.recordDoc = structuredClone(tab.savedContentSnapshot);
  void nextTick(() => {
    getEditor(tab.key)?.markSaved?.();
    getEditor(tab.key)?.clearDraft?.();
    markTabClean(tab.key);
  });
}

function resetTabContent(tab: EmrOpenTab) {
  tab.documentId = '';
  tab.pendingTemplateId = '';
  tab.documentReadOnly = false;
  tab.documentTypeCode = '';
  tab.documentTypeName = '';
  tab.documentTitle = '';
  tab.serverMetadataValues = {};
  const blank = structuredClone(defaultEmrEditorDoc);
  tab.recordDoc = blank;
  captureContentSnapshot(tab, blank);
  tab.editorSurface = 'document';
  tab.templateLayoutLocked = false;
  tab.pageMargins = { ...printStandardMargins };
  void nextTick(() => {
    getEditor(tab.key)?.markSaved?.();
    markTabClean(tab.key);
  });
}

/** 放弃未保存：离开当前会话 / 切换打开其它模板或历史文书 */
type EmrDiscardContext = 'leave' | 'replace';

async function applyDiscardAfterConfirm(context: EmrDiscardContext) {
  if (context === 'replace') {
    const tab = activeTab.value;
    if (!tab.documentId && tab.pendingTemplateId) {
      resetTabContent(tab);
    }
    return;
  }
  // 离开：逐个恢复已打开文书
  for (const tab of openTabs.value) {
    if (tab.documentId) {
      try {
        const dto = await getDocumentContentApi({ documentId: tab.documentId });
        applyDocumentDtoToTab(tab, dto);
      } catch {
        revertTabToSavedSnapshot(tab);
      }
    } else if (tab.pendingTemplateId) {
      resetTabContent(tab);
    } else {
      revertTabToSavedSnapshot(tab);
    }
  }
}

/** 切换患者/模板/离开前确认未保存 */
async function confirmDiscardUnsavedIfNeeded(
  context: EmrDiscardContext = 'leave',
): Promise<boolean> {
  const dirty = context === 'replace' ? isActiveEditorDirty() : isAnyEditorDirty();
  if (!dirty) return true;
  const message =
    context === 'replace'
      ? '当前病历有未保存的修改，放弃后将打开所选内容，是否继续？'
      : openTabs.value.length > 1
        ? '有未保存的病历修改，是否放弃修改并继续？'
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
  if (!isActiveEditorDirty()) return true;
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

/** 将接口文书 DTO 写入指定 Tab */
function applyDocumentDtoToTab(
  tab: EmrOpenTab,
  dto: {
    content?: unknown;
    documentTypeCode?: string;
    documentTypeName?: string;
    encounterId?: string | number;
    id?: number | string;
    metadataValues?: Record<string, string>;
    readOnly?: boolean;
    title?: string;
  },
) {
  const id = preserveApiIdForRequest(dto.id);
  if (id) {
    tab.documentId = id;
    tab.pendingTemplateId = '';
  }
  if (dto.documentTypeCode) {
    tab.documentTypeCode = dto.documentTypeCode;
  }
  if (dto.documentTypeName !== undefined) {
    const name = (dto.documentTypeName ?? '').trim();
    if (name) tab.documentTypeName = name;
  }
  if (dto.title !== undefined) {
    tab.documentTitle = (dto.title ?? '').trim();
  }
  const currentEncounterId = preserveApiIdForRequest(props.encounterId) ?? '';
  const docEncounterId = preserveApiIdForRequest(dto.encounterId) ?? '';
  const linkedPeerReadOnly =
    Boolean(currentEncounterId) && Boolean(docEncounterId) && docEncounterId !== currentEncounterId;
  tab.documentReadOnly = Boolean(dto.readOnly) || linkedPeerReadOnly;
  if (dto.metadataValues) {
    tab.serverMetadataValues = { ...dto.metadataValues };
  }
  const doc = normalizeEmrRootDoc(dto.content);
  tab.recordDoc = doc;
  captureContentSnapshot(tab, doc);
  tab.editorSurface = 'document';
  tab.templateLayoutLocked = false;
  void nextTick(() => {
    getEditor(tab.key)?.markSaved?.();
    getEditor(tab.key)?.clearDraft?.();
    markTabClean(tab.key);
  });
}

/** 清空全部文书会话（切换患者等） */
function resetDocumentSession() {
  for (const key of [...editorRefMap.keys()]) {
    editorRefMap.delete(key);
  }
  tabDirtyMap.value = {};
  const blank = createBlankTab();
  openTabs.value = [blank];
  activeTabKey.value = blank.key;
}

function findTabByDocumentId(id: string): EmrOpenTab | undefined {
  return openTabs.value.find((t) => t.documentId === id);
}

function canOpenNewTab(): boolean {
  if (openTabs.value.length < MAX_OPEN_TABS) return true;
  ElMessage.warning(`最多同时打开 ${MAX_OPEN_TABS} 份病历，请先关闭部分标签`);
  return false;
}

function activateTab(key: string) {
  if (openTabs.value.some((t) => t.key === key)) {
    activeTabKey.value = key;
  }
}

/** 关闭文书子 Tab；最后一页则清空为空白会话 */
async function closeTab(tabKey: string) {
  const tab = openTabs.value.find((t) => t.key === tabKey);
  if (!tab) return;
  refreshTabDirty(tabKey);
  if (isTabDirty(tabKey) || getEditor(tabKey)?.isDirty?.() === true) {
    try {
      await ElMessageBox.confirm('该病历有未保存的修改，关闭后将丢失，是否继续？', '关闭确认', {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '继续编辑',
      });
    } catch {
      return;
    }
  }
  if (openTabs.value.length <= 1) {
    resetTabContent(tab);
    return;
  }
  const idx = openTabs.value.findIndex((t) => t.key === tabKey);
  openTabs.value = openTabs.value.filter((t) => t.key !== tabKey);
  editorRefMap.delete(tabKey);
  const nextDirty = { ...tabDirtyMap.value };
  delete nextDirty[tabKey];
  tabDirtyMap.value = nextDirty;
  if (activeTabKey.value === tabKey) {
    const next = openTabs.value[Math.max(0, idx - 1)] ?? openTabs.value[0];
    activeTabKey.value = next!.key;
  }
}

/** 本地载入模板正文到指定 Tab，不创建服务端文书（保存时再 create） */
async function applyTemplateLocallyToTab(
  tab: EmrOpenTab,
  typeCode: string,
  templateId: string,
  options?: { silent?: boolean; documentTypeName?: string },
) {
  const tplId = preserveApiIdForRequest(templateId);
  if (!tplId) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  documentLoading.value = true;
  try {
    const tpl = await getTemplateContentApi({ templateId: tplId });
    // await 期间接诊/切患者可能重建 Tab：落到仍存活的会话上
    const liveTab = openTabs.value.find((t) => t.key === tab.key) ?? activeTab.value;
    liveTab.documentId = '';
    liveTab.documentReadOnly = false;
    liveTab.documentTypeCode = typeCode;
    liveTab.documentTypeName = (options?.documentTypeName ?? '').trim();
    liveTab.documentTitle = '';
    liveTab.pendingTemplateId = tplId;
    // 本地患者条 + 服务端解析一并落库到 tab，并烘进正文，避免首屏 watch 竞态空白
    const serverMeta = await resolveMetadataForCurrentPatient();
    const mergedMeta = mergeEmrMetadataValues(
      buildMetadataValuesFromPatientInfo(props.patientInfo),
      serverMeta,
    );
    liveTab.serverMetadataValues = mergedMeta;
    const doc = applyMetadataValuesToContentJson(
      normalizeEmrRootDoc(tpl.content),
      mergedMeta,
      true,
    );
    liveTab.recordDoc = doc;
    captureContentSnapshot(liveTab, doc);
    liveTab.editorSurface = 'document';
    liveTab.templateLayoutLocked = false;
    void nextTick(() => {
      getEditor(liveTab.key)?.markSaved?.();
      getEditor(liveTab.key)?.clearDraft?.();
      markTabClean(liveTab.key);
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

/** 按当前患者/就诊解析元数据；失败时返回空对象由患者条本地映射兜底 */
async function resolveMetadataForCurrentPatient(): Promise<Record<string, string>> {
  const patientId = (props.patientId ?? '').trim();
  if (!patientId) return {};
  try {
    const dto = await resolveEncounterMetadataApi({
      patientId,
      encounterId: (props.encounterId ?? '').trim() || undefined,
    });
    return dto?.values && Object.keys(dto.values).length > 0 ? { ...dto.values } : {};
  } catch {
    return {};
  }
}

/** 保存前确保已有服务端文书：未创建则按待选模板 create */
async function ensureDocumentCreated(): Promise<null | string> {
  const tab = activeTab.value;
  if (tab.documentId) return tab.documentId;
  const tplId = tab.pendingTemplateId;
  const typeCode = tab.documentTypeCode.trim();
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
  applyDocumentDtoToTab(tab, created);
  if (isInpatientStation.value) {
    await refreshProgressNoteNavList();
  }
  return tab.documentId;
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
  const tab = activeTab.value;
  if (tab.pendingTemplateId && !tab.documentId) {
    const typeCode = tab.documentTypeCode.trim();
    if (!typeCode) {
      if (!options?.silent) ElMessage.info('请先在左侧选择模板');
      return;
    }
    try {
      await applyTemplateLocallyToTab(tab, typeCode, tab.pendingTemplateId, { silent: true });
      if (!options?.silent) ElMessage.success('已刷新模板');
    } catch {
      if (!options?.silent) ElMessage.error('刷新失败');
    }
    return;
  }
  if (!tab.documentId) {
    if (!options?.silent) {
      ElMessage.info('请先在左侧选择模板或历史病历');
    }
    return;
  }
  if (documentLoading.value) return;
  documentLoading.value = true;
  try {
    const dto = await getDocumentContentApi({ documentId: tab.documentId });
    applyDocumentDtoToTab(tab, dto);
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

/** 有正文即可生成 PDF（未保存也可，走 content 入参） */
const canPrintDocument = computed(() => Boolean(activeTab.value.recordDoc));

/** 当前编辑器有正文且已知文书类型时可存为模板 */
const canSaveAsTemplate = computed(
  () =>
    Boolean(documentTypeCode.value.trim()) &&
    !documentReadOnly.value &&
    Boolean(activeTab.value.recordDoc),
);

/** 门诊病历纸质底边戒烟提示（与 emr 元数据 smokeFreeFooter / 模板 printFooter 一致） */
const OUTPATIENT_EMR_SMOKE_FREE_FOOTER =
  '为了您和家人的健康，请勿吸烟！如需戒烟帮助，请咨询戒烟医生。';

/** 编辑器页边距 → PrintConfig.margins（仅 mm；px 预设走服务端默认） */
function buildPrintConfigFromPageMargins(margins: PageMargins): EmrPrintConfigPayload | undefined {
  const parseMm = (raw: string): number | undefined => {
    const m = String(raw ?? '')
      .trim()
      .match(/^([\d.]+)\s*mm$/i);
    if (!m) return undefined;
    const n = Number(m[1]);
    return Number.isFinite(n) && n > 0 ? n : undefined;
  };
  const topMm = parseMm(margins.top);
  const rightMm = parseMm(margins.right);
  const bottomMm = parseMm(margins.bottom);
  const leftMm = parseMm(margins.left);

  const isOutpatientRecord = documentTypeCode.value.trim() === 'OUTPATIENT_RECORD';
  const footer = isOutpatientRecord
    ? {
        enabled: true,
        heightMm: 20,
        template: `${OUTPATIENT_EMR_SMOKE_FREE_FOOTER}　第 {page} 页`,
        showPageNumber: true,
      }
    : undefined;

  if (topMm == null && rightMm == null && bottomMm == null && leftMm == null && !footer) {
    return undefined;
  }

  let resolvedBottom = bottomMm;
  if (isOutpatientRecord && (resolvedBottom == null || resolvedBottom < 28)) {
    resolvedBottom = 28;
  }

  return {
    ...(topMm != null || rightMm != null || resolvedBottom != null || leftMm != null
      ? {
          margins: {
            ...(topMm != null ? { topMm } : {}),
            ...(rightMm != null ? { rightMm } : {}),
            ...(resolvedBottom != null ? { bottomMm: resolvedBottom } : {}),
            ...(leftMm != null ? { leftMm } : {}),
          },
        }
      : {}),
    ...(footer ? { footer } : {}),
  };
}

async function requestDocumentPdf(previewMode: boolean) {
  if (!canPrintDocument.value) {
    ElMessage.warning('请先选择模板或打开病历');
    return null;
  }
  const tab = activeTab.value;
  const content = getActiveEditor()?.getJSON?.() ?? tab.recordDoc;
  if (!content) {
    ElMessage.warning('没有可打印的内容');
    return null;
  }
  // 正式打印且可写：先落库，保证归档与纸质一致
  if (!previewMode && canSaveDocument.value && !documentReadOnly.value) {
    const dirty = isTabDirty(tab.key) || getActiveEditor()?.isDirty?.() === true;
    if (dirty || !tab.documentId) {
      await handleSave();
      if (!tab.documentId && !documentId.value) {
        return null;
      }
    }
  }

  const docId = preserveApiIdForRequest(tab.documentId) ?? '';
  const result = await generateDocumentPdfApi({
    documentId: docId || undefined,
    content,
    previewMode,
    patientId: props.patientId || undefined,
    encounterId: props.encounterId || undefined,
    documentTitle: (tab.documentTitle || tab.documentTypeName || '病历').trim(),
    documentTypeName: (tab.documentTypeName || '').trim() || undefined,
    metadata: {
      ...mergeEmrMetadataValues(
        buildMetadataValuesFromPatientInfo(props.patientInfo),
        tab.serverMetadataValues,
      ),
    },
    printConfig: buildPrintConfigFromPageMargins(tab.pageMargins),
  });
  if (!result?.pdfBase64?.trim()) {
    ElMessage.warning('未生成 PDF 内容');
    return null;
  }
  return result;
}

/** 打印预览：OpenPDF previewMode=true */
async function handlePrintPreview() {
  if (pdfLoading.value) return;
  pdfLoading.value = true;
  try {
    const result = await requestDocumentPdf(true);
    if (!result) return;
    pdfPreviewAutoPrint.value = false;
    pdfPreviewTitle.value = `${(activeTab.value.documentTypeName || activeTab.value.documentTitle || '病历').trim() || '病历'} · 预览`;
    pdfPreviewBase64.value = result.pdfBase64!;
    pdfPreviewPageCount.value = result.pageCount;
    pdfPreviewVisible.value = true;
  } catch {
    // 拦截器已提示
  } finally {
    pdfLoading.value = false;
  }
}

/** 正式打印：previewMode=false，打开预览并调起系统打印 */
async function handlePrint() {
  if (pdfLoading.value) return;
  pdfLoading.value = true;
  try {
    const result = await requestDocumentPdf(false);
    if (!result) return;
    pdfPreviewAutoPrint.value = true;
    pdfPreviewTitle.value = `${(activeTab.value.documentTypeName || activeTab.value.documentTitle || '病历').trim() || '病历'} · 打印`;
    pdfPreviewBase64.value = result.pdfBase64!;
    pdfPreviewPageCount.value = result.pageCount;
    pdfPreviewVisible.value = true;
  } catch {
    // 拦截器已提示
  } finally {
    pdfLoading.value = false;
  }
}

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

/** 医生站「存为模版」：打开弹窗选择个人/科室模板 */
function handleSaveAsTemplate() {
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
  const content = getActiveEditor()?.getJSON?.() ?? activeTab.value.recordDoc;
  if (!content) {
    ElMessage.warning('没有可保存为模板的内容');
    return;
  }
  saveAsTemplateDialogOpen.value = true;
}

async function handleConfirmSaveAsTemplate(payload: {
  name: string;
  organizationId?: string;
  scopeLevel: EmrTemplateScopeLevel;
}) {
  const content = getActiveEditor()?.getJSON?.() ?? activeTab.value.recordDoc;
  if (!content) {
    ElMessage.warning('没有可保存为模板的内容');
    return;
  }
  if (payload.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL) {
    const practitionerId = resolveLoginPractitionerIdForRequest(
      userStore.userInfo as Record<string, unknown> | undefined,
    );
    if (!practitionerId) {
      ElMessage.warning('当前账号未关联医务人员，无法保存个人模板');
      return;
    }
  }

  saveAsTemplateLoading.value = true;
  saveAsTemplateDialogRef.value?.setConfirmLoading(true);
  try {
    await createTemplateApi({
      name: payload.name,
      code: suggestDoctorStationTemplateCode(payload.name),
      documentTypeCode: documentTypeCode.value.trim(),
      scopeLevel: payload.scopeLevel,
      encounterClassCode: resolveEncounterClassForStation(),
      organizationId:
        payload.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT
          ? payload.organizationId
          : undefined,
      ownerPractitionerId:
        payload.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL
          ? resolveLoginPractitionerIdForRequest(
              userStore.userInfo as Record<string, unknown> | undefined,
            ) || undefined
          : undefined,
      fromDoctorStation: true,
      content: serializeEmrTemplateContent(content),
      enabled: true,
    });
    const scopeLabel =
      payload.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT ? '科室模板' : '个人模板';
    ElMessage.success(`已存为${scopeLabel}`);
    saveAsTemplateDialogRef.value?.close();
    void historyPanelRef.value?.refreshTemplateTree?.();
  } catch {
    // 取消或错误由拦截器提示
  } finally {
    saveAsTemplateLoading.value = false;
    saveAsTemplateDialogRef.value?.setConfirmLoading(false);
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
  const tab = activeTab.value;
  const content = getActiveEditor()?.getJSON?.() ?? tab.recordDoc;
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
    captureContentSnapshot(tab, content);
    tab.recordDoc = content;
    getActiveEditor()?.markSaved?.();
    await getActiveEditor()?.clearDraft?.();
    markTabClean(tab.key);
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
  options?: { silent?: boolean; skipUnsavedConfirm?: boolean; openInNewTab?: boolean },
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

  const existing = findTabByDocumentId(id);
  if (existing) {
    if (activeTabKey.value !== existing.key) {
      activeTabKey.value = existing.key;
      if (!options?.silent) {
        ElMessage.success('已切换到已打开的病历');
      }
    }
    return;
  }

  const openInNewTab = Boolean(options?.openInNewTab);
  if (openInNewTab) {
    if (!canOpenNewTab()) return;
  } else if (!options?.skipUnsavedConfirm) {
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
    let target = activeTab.value;
    if (openInNewTab) {
      const blank = createBlankTab();
      openTabs.value = [...openTabs.value, blank];
      activeTabKey.value = blank.key;
      target = blank;
    }
    applyDocumentDtoToTab(target, {
      ...dto,
      documentTypeCode: dto.documentTypeCode ?? row.documentTypeCode,
      documentTypeName: row.documentTypeName,
      encounterId: dto.encounterId ?? row.encounterId,
      title: dto.title ?? row.title,
    });
    if (!options?.silent) {
      ElMessage.success(openInNewTab ? '已在新标签打开病历' : '已打开病历');
    }
    // 打开不改变文书列表；树高亮由 currentDocumentId watch 同步，无需 refreshHistory
  } catch {
    if (!options?.silent) ElMessage.error('打开病历失败');
  } finally {
    documentLoading.value = false;
  }
}

/** 从历史列表按 documentId 打开已有文书（替换当前） */
async function handleSelectDocument(row: DocumentSummaryDto) {
  await openDocumentFromSummary(row);
}

/** 历史列表 Ctrl/右键：新标签打开 */
async function handleOpenDocumentInNewTab(row: DocumentSummaryDto) {
  await openDocumentFromSummary(row, { openInNewTab: true });
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
    activeTab.value.recordDoc = payload.content;
  }
}

async function handleHistorySelect(item: EmrTemplateTreeNode) {
  if (item.isDocumentType || item.isGroup || item.isDocument) return;
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
  // 接诊会刷新选中患者：等 props 落稳后再套模板，避免元数据解析用到旧上下文
  await nextTick();
  await applyTemplateLocallyToTab(activeTab.value, typeCode, tplId, {
    documentTypeName: (item.documentTypeName ?? '').trim(),
  });
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
  <div class="medical-record-form flex h-full overflow-hidden bg-white">
    <div
      class="medical-record-form__sidebar flex-shrink-0 bg-white transition-all duration-300"
      :style="{
        width: leftCollapsed ? `${COLLAPSED_WIDTH}px` : `${sidebarExpandedWidthPx}px`,
      }"
    >
      <div v-if="!leftCollapsed" class="h-full">
        <HistoryTemplatePanel
          ref="historyPanelRef"
          :station-kind="stationKind"
          :patient-id="patientId"
          :encounter-id="encounterId"
          :current-document-id="documentId"
          :open-document-ids="openDocumentIds"
          @select="handleHistorySelect"
          @select-document="handleSelectDocument"
          @open-document-in-new-tab="handleOpenDocumentInNewTab"
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
      <div
        class="medical-record-form__doc-tabs flex h-[36px] flex-shrink-0 items-stretch gap-0.5 overflow-x-auto border-b bg-[#f5f7fa] px-2"
      >
        <div
          v-for="tab in openTabs"
          :key="tab.key"
          class="medical-record-form__doc-tab group relative flex min-w-[88px] max-w-[180px] cursor-pointer items-center gap-1 px-2.5 text-xs"
          :class="{
            'medical-record-form__doc-tab--active': tab.key === activeTabKey,
          }"
          :title="tabDisplayTitle(tab)"
          @click="activateTab(tab.key)"
        >
          <span class="truncate">{{ tabDisplayTitle(tab) }}</span>
          <span
            v-if="isTabDirty(tab.key)"
            class="medical-record-form__doc-tab-dirty"
            title="未保存"
          >
            •
          </span>
          <button
            type="button"
            class="medical-record-form__doc-tab-close ml-auto flex h-4 w-4 flex-shrink-0 items-center justify-center rounded opacity-60 hover:bg-black/10 hover:opacity-100"
            title="关闭"
            @click.stop="closeTab(tab.key)"
          >
            <ElIcon :size="10"><Close /></ElIcon>
          </button>
        </div>
      </div>

      <div
        class="medical-record-form__toolbar flex h-[42px] flex-shrink-0 flex-wrap items-center gap-1 border-b px-3"
      >
        <ElButton
          type="primary"
          plain
          :disabled="!canImportClinicalText"
          title="勾选当前就诊诊断，插入到光标或辅助检查填写区"
          @click="openImportDiagnosisDialog"
        >
          插入诊断
        </ElButton>
        <ElButton
          type="primary"
          plain
          :disabled="!canImportClinicalText"
          title="勾选当前就诊医嘱，插入到光标或辅助检查填写区"
          @click="openImportAdviceDialog"
        >
          带入医嘱
        </ElButton>
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
        <ElButton :loading="pdfLoading" :disabled="!canPrintDocument" @click="handlePrintPreview">
          打印预览
        </ElButton>
        <ElButton
          type="primary"
          plain
          :loading="pdfLoading"
          :disabled="!canPrintDocument"
          @click="handlePrint"
        >
          打印
        </ElButton>
        <span
          v-if="documentReadOnly"
          class="ml-2 text-xs"
          style="color: var(--el-text-color-secondary)"
        >
          只读
        </span>
        <span
          class="ml-auto text-xs"
          style="color: var(--el-text-color-secondary)"
          title="树上病历：Ctrl+单击 或 右键「在新标签打开」"
        >
          Ctrl+单击病历可多开（最多 {{ MAX_OPEN_TABS }}）
        </span>
      </div>
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div
          v-loading="documentLoading"
          class="medical-record-form__editor-wrap h-full border-t p-3"
          @focusin="onEditorWrapFocusIn"
        >
          <SimpleEditor
            v-for="tab in openTabs"
            v-show="tab.key === activeTabKey"
            :key="tab.key"
            :ref="(el) => setEditorRef(tab.key, el as InstanceType<typeof SimpleEditor> | null)"
            v-model="tab.recordDoc"
            v-model:layout-locked="tab.templateLayoutLocked"
            v-model:page-margins="tab.pageMargins"
            class="medical-record-form__editor"
            :surface="tab.editorSurface"
            :doc-id="editorDocIdForTab(tab)"
            :read-only="tab.documentReadOnly"
            :remote-save="remoteSaveHandlerForTab(tab)"
            :on-remote-save-error="handleRemoteSaveError"
            :show-toolbar="false"
            placeholder="请选择左侧模板新建，或打开模板下的已有病历…"
            :metadata-values="
              mergeEmrMetadataValues(
                buildMetadataValuesFromPatientInfo(patientInfo),
                tab.serverMetadataValues,
              )
            "
            @change="onEditorChange(tab.key)"
          />
        </div>
      </div>
    </div>

    <EmrImportDiagnosisDialog
      v-model:open="importDiagnosisDialogOpen"
      :encounter-id="encounterId"
      :patient-id="patientId"
      :station-scope="importStationScope"
      @insert="handleImportInsert"
    />
    <EmrImportAdviceDialog
      v-model:open="importAdviceDialogOpen"
      :encounter-id="encounterId"
      :patient-id="patientId"
      :station-scope="importStationScope"
      @insert="handleImportInsert"
    />
    <EmrPdfPreviewDialog
      v-model="pdfPreviewVisible"
      :pdf-base64="pdfPreviewBase64"
      :page-count="pdfPreviewPageCount"
      :title="pdfPreviewTitle"
      :auto-print="pdfPreviewAutoPrint"
    />
    <SaveAsEmrTemplateDialog
      ref="saveAsTemplateDialogRef"
      v-model:open="saveAsTemplateDialogOpen"
      @confirm="handleConfirmSaveAsTemplate"
    />
  </div>
</template>

<style scoped lang="scss">
.medical-record-form__doc-tab {
  border-bottom: 2px solid transparent;
  color: var(--el-text-color-regular);
  transition:
    color 0.15s,
    background-color 0.15s,
    border-color 0.15s;

  &:hover {
    background-color: var(--el-fill-color);
  }

  &--active {
    border-bottom-color: var(--el-color-primary);
    background-color: #fff;
    color: var(--el-color-primary);
    font-weight: 500;
  }
}

.medical-record-form__doc-tab-dirty {
  flex-shrink: 0;
  color: var(--el-color-warning);
  font-size: 16px;
  line-height: 1;
}

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
