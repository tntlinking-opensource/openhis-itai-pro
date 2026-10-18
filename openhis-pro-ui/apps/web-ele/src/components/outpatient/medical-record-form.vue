<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';

import type { EmrTemplateTreeNode } from '#/components/outpatient/build-emr-template-tree';

import type { DocumentSummaryDto, EmrPrintPaperPayload } from '#/api/emr/document';
import type { TemplateDto } from '#/api/emr/template';
import type { ApplyEmrTemplatePayload, PatientInfoForEmrMetadata } from '#/emrEditor';
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

import { ChevronLeft, ChevronRight } from '@zxplat/icons';
import { useUserStore } from '@zxplat/stores';

import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import {
  createDocumentApi,
  deleteDocumentApi,
  generateDocumentPdfApi,
  getDocumentContentApi,
  getDocumentListApi,
  saveDocumentApi,
  submitRevisedArchiveApi,
  updateDocumentTitleApi,
} from '#/api/emr/document';
import { getDocumentTypeListApi } from '#/api/emr/document-type';
import { resolveEncounterMetadataApi } from '#/api/emr/encounter-metadata';
import {
  submitInpatientDocumentArchiveApi,
  submitOutpatientDocumentArchiveApi,
} from '#/api/mrms';
import {
  createTemplateApi,
  deleteTemplateApi,
  getTemplateContentApi,
  serializeEmrTemplateContent,
  updateTemplateApi,
} from '#/api/emr/template';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station';
import { useCompactViewport } from '#/composables/use-compact-viewport';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';
import { usePaperSheetFullscreen } from '#/views/inpatient/nurse-station/composables/use-paper-sheet-fullscreen';
import {
  applyDiagnosisMetadataToContentJson,
  buildMetadataValuesFromPatientInfo,
  EMR_DIAGNOSIS_METADATA_FIELD_KEYS,
  isForceLogoutSession,
  mergeEmrMetadataValues,
  registerEmrUnsavedGuard,
  stripDocumentStampMetadataFromContentJson,
  stripOutpatientSignoffMetadataFromContentJson,
} from '#/emrEditor';
import CanvasStationEditor from '#/emr-canvas/components/CanvasStationEditor.vue';
import {
  emptyStationRecordDoc,
  packCanvasTabContent,
  toStationRecordDoc,
  wrapCanvasPayload,
} from '#/emr-canvas/station-bridge';

/** 外挂布局：生产包 scoped 会丢，页签/编辑器高度链靠此外挂 css */
import './medical-record-form.css';
import {
  getCanvasStationPresetByTemplateId,
  isCanvasStationTemplateId,
} from '#/emr-canvas/templates/canvas-station-templates';
import {
  DEFAULT_EDITOR_PAGE_MARGINS,
} from '#/emrEditor/components/simple-editor/editor-defaults';
import {
  createDefaultEmrPageChrome,
  cloneEmrPageChrome,
  pinOutpatientChromeHeaderTitle,
  type EmrPageChrome,
} from '#/emrEditor/components/simple-editor/emr-page-chrome';
import { requestEmrPaginationRelayout } from '#/emrEditor/components/simple-editor/extensions/emr-pagination';
import {
  buildEmrPrintConfigPayload,
  collectScreenPrintMetricsFromDom,
} from '#/emrEditor/components/simple-editor/emr-print-config';
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

import { resolveEmrDirectoryCategory } from '#/components/outpatient/build-emr-template-tree';
import type { IRange } from '@hufe921/canvas-editor';

import EmrImportAdviceDialog from '#/components/outpatient/emr-import/emr-import-advice-dialog.vue';
import EmrImportDiagnosisDialog from '#/components/outpatient/emr-import/emr-import-diagnosis-dialog.vue';
import EmrPdfPreviewDialog from '#/components/outpatient/emr-pdf-preview-dialog.vue';
import HistoryTemplatePanel from '#/components/outpatient/history-template-panel.vue';
import SaveAsEmrTemplateDialog from '#/components/outpatient/save-as-emr-template-dialog.vue';

defineOptions({
  name: 'MedicalRecordForm',
});

const props = withDefaults(defineProps<Props>(), {
  stationKind: undefined,
  patientId: '',
  encounterId: '',
  forceReadOnly: false,
  restrictHistoryToInpatient: false,
  encounterScoped: false,
  initialDocumentId: '',
  archiveEnabled: true,
  hostOwnsUnsavedGuard: false,
});

interface Props {
  /** 门诊 OUTPATIENT / 住院 INPATIENT，决定模板树可见范围 */
  stationKind?: EmrTemplateStationKind;
  patientInfo?: PatientInfoForEmrMetadata;
  patientId?: string;
  encounterId?: string;
  /** 强制只读（如护士站病历调阅 / 病案只读）：不可新建/编辑/删除，仅查看与打印 */
  forceReadOnly?: boolean;
  /**
   * 历史列表仅允许打开住院目录文书（护士站病历调阅专用）。
   * 病案门诊/住院只读查阅勿开：可看不可改，不限制门诊文书。
   */
  restrictHistoryToInpatient?: boolean;
  /**
   * 按就诊收窄侧栏：仅本 encounter 文书，不拉患者跨就诊历史。
   * 病案「门诊/住院病例查询」打开时必开（列表行锚点为就诊）。
   */
  encounterScoped?: boolean;
  /** 打开时自动加载的文书主键（门诊列表行 documentId） */
  initialDocumentId?: string;
  /**
   * 是否展示文书归档（提交归档 / 已归档修改）。
   * 住院医生站关闭；病案首页「病历」、门诊医生站默认开启。
   */
  archiveEnabled?: boolean;
  /**
   * 宿主自己管未保存确认（多人病历工作台）。
   * 为 true 时不注册全局 guard、不拦路由离开，避免多实例互相覆盖。
   */
  hostOwnsUnsavedGuard?: boolean;
}

type PageMargins = { bottom: string; left: string; right: string; top: string };

/** 已打开的文书会话（一 Tab 一编辑器） */
interface EmrOpenTab {
  documentId: string;
  /** 文书状态：DRAFT / PENDING_ARCHIVE / ARCHIVED */
  documentStatus: string;
  /** 已归档：医生确认修改后本地解锁；不能保存，只能提交审核 */
  revisingArchived?: boolean;
  /** 文书就诊与当前就诊不一致，不可改 */
  linkedReadOnly?: boolean;
  documentReadOnly: boolean;
  documentTitle: string;
  documentTypeCode: string;
  /** 文书类型中文名，用于子 Tab 标题 */
  documentTypeName: string;
  editorSurface: 'document' | 'template';
  key: string;
  pageChrome: EmrPageChrome;
  pageMargins: PageMargins;
  pendingTemplateId: string;
  recordDoc: JSONContent | null;
  savedContentSnapshot: JSONContent;
  serverMetadataValues: Record<string, string>;
  /** 打开历史/已有文书时的来源模板，复用创建时带回 */
  sourceTemplateId: string;
  /** Tab 展示：病历头（文书类型名，无时间后缀） */
  tabHead: string;
  /** Tab 展示：患者姓名 */
  tabPatientName: string;
  /** Tab 展示：书写/保存医生 */
  tabAuthorName: string;
  /** 正文落地后递增，驱动编辑器按新稿重建，避免套模板后再打开已写病历仍停在空模板 */
  contentEpoch: number;
}

/** 文书子 Tab 不再限制同时打开数量 */

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);
const userStore = useUserStore();

const editorRefMap = new Map<string, InstanceType<typeof CanvasStationEditor>>();
const historyPanelRef = ref<InstanceType<typeof HistoryTemplatePanel> | null>(null);

/** 文书编辑默认页边距：贴近 Word/WPS，避免国标左右 31.8mm 屏上过空 */
const defaultEditorMargins: PageMargins = { ...DEFAULT_EDITOR_PAGE_MARGINS };

let tabKeySeq = 0;
function nextTabKey(): string {
  tabKeySeq += 1;
  return `emr-tab-${tabKeySeq}`;
}

function createBlankTab(): EmrOpenTab {
  const blank = emptyStationRecordDoc() as unknown as JSONContent;
  return {
    key: nextTabKey(),
    documentId: '',
    documentStatus: '',
    pendingTemplateId: '',
    documentReadOnly: false,
    documentTypeCode: '',
    documentTypeName: '',
    documentTitle: '',
    sourceTemplateId: '',
    serverMetadataValues: {},
    recordDoc: blank,
    savedContentSnapshot: structuredClone(blank),
    editorSurface: 'document',
    pageMargins: { ...defaultEditorMargins },
    pageChrome: createDefaultEmrPageChrome(),
    tabHead: '',
    tabPatientName: '',
    tabAuthorName: '',
    contentEpoch: 0,
  };
}

const openTabs = ref<EmrOpenTab[]>([createBlankTab()]);
const activeTabKey = ref(openTabs.value[0]!.key);
const documentLoading = ref(false);
const saveLoading = ref(false);
const submitArchiveLoading = ref(false);
const saveAsTemplateLoading = ref(false);
const saveAsTemplateDialogOpen = ref(false);
const saveAsTemplateDialogRef = ref<InstanceType<typeof SaveAsEmrTemplateDialog> | null>(null);
const pdfLoading = ref(false);
const pdfPreviewVisible = ref(false);
const pdfPreviewAutoPrint = ref(false);
const pdfPreviewBase64 = ref('');
const pdfPreviewPageCount = ref<number | undefined>();
const pdfPreviewTitle = ref('打印预览');
/** 当前预览是预览还是正式打印，换纸时沿用 */
const pdfPreviewMode = ref(true);

const importDiagnosisDialogOpen = ref(false);
const importAdviceDialogOpen = ref(false);
/** 最近一次 canvas 选区（工具栏/弹窗抢焦前快照） */
const lastCanvasInsertRange = ref<IRange | null>(null);
/** 打开带入弹窗时冻结的插入落点 */
const pendingCanvasInsertRange = ref<IRange | null>(null);

/** 记录 canvas 插入落点 */
function trackImportInsertTarget() {
  const range = getActiveEditor()?.captureInsertRange?.() ?? null;
  if (range) {
    lastCanvasInsertRange.value = range;
  }
}

function onEditorWrapFocusIn() {
  trackImportInsertTarget();
}

/** 挪光标/拖选后刷新快照（工具栏 mousedown 会抢焦，须提前记住） */
function onEditorWrapCaretTrack() {
  trackImportInsertTarget();
}

const activeTab = computed(
  () => openTabs.value.find((t) => t.key === activeTabKey.value) ?? openTabs.value[0]!,
);

const documentId = computed(() => activeTab.value.documentId);
const pendingTemplateId = computed(() => activeTab.value.pendingTemplateId);
const documentReadOnly = computed(() => isTabReadOnly(activeTab.value));
const documentTypeCode = computed(() => activeTab.value.documentTypeCode);

const openDocumentIds = computed(() =>
  openTabs.value.map((t) => t.documentId).filter((id) => Boolean(id)),
);

/** 子 Tab 脏标（模板可读的响应式映射） */
const tabDirtyMap = ref<Record<string, boolean>>({});
const hasUnsavedChangesFlag = computed(() => Object.values(tabDirtyMap.value).some(Boolean));

const emit = defineEmits<{
  dirtyChange: [dirty: boolean];
}>();

watch(hasUnsavedChangesFlag, (dirty) => {
  emit('dirtyChange', dirty);
});

/** 当前表单是否有未保存文书（供宿主页签圆点 / 关闭确认） */
function hasUnsavedChanges(): boolean {
  return hasUnsavedChangesFlag.value;
}

const leftCollapsed = ref(false);
const COLLAPSED_WIDTH = 48;
/** 进入全屏前侧栏是否折叠，退出时还原 */
const sidebarCollapsedBeforeFullscreen = ref(false);

const { isFullscreen, toggleFullscreen } = usePaperSheetFullscreen();

const { pickPreset } = useCompactViewport();

/** 全屏写病历：收起侧栏并通知 canvas 重算纸面 */
async function handleToggleEmrFullscreen() {
  const entering = !isFullscreen.value;
  if (entering) {
    sidebarCollapsedBeforeFullscreen.value = leftCollapsed.value;
    leftCollapsed.value = true;
  } else {
    leftCollapsed.value = sidebarCollapsedBeforeFullscreen.value;
  }
  toggleFullscreen();
  await nextTick();
  window.dispatchEvent(new Event('resize'));
}

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

function freezeImportInsertTargets() {
  // 工具栏 mousedown.prevent 时先冻结选区，再开弹窗
  const editorComp = getActiveEditor();
  pendingCanvasInsertRange.value =
    editorComp?.captureInsertRange?.() ?? lastCanvasInsertRange.value;
}

function openImportDiagnosisDialog() {
  freezeImportInsertTargets();
  importDiagnosisDialogOpen.value = true;
}

function openImportAdviceDialog() {
  freezeImportInsertTargets();
  importAdviceDialogOpen.value = true;
}

function handleImportInsert(text: string) {
  const editorComp = getActiveEditor();
  const ok = editorComp?.insertPlainText?.(text, pendingCanvasInsertRange.value) === true;
  pendingCanvasInsertRange.value = null;
  if (!ok) {
    ElMessage.warning('插入失败：请先将光标置于病历正文或填写控件内');
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

function setEditorRef(
  tabKey: string,
  el: Element | InstanceType<typeof CanvasStationEditor> | null,
) {
  if (el && typeof el === 'object' && 'isDirty' in el) {
    editorRefMap.set(tabKey, el as InstanceType<typeof CanvasStationEditor>);
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

/** 快照深拷贝：Vue reactive Proxy 不能 structuredClone，否则保存收尾抛 DataCloneError */
function cloneEmrJsonForSnapshot(doc: JSONContent): JSONContent {
  try {
    return JSON.parse(JSON.stringify(doc)) as JSONContent;
  } catch {
    return structuredClone(doc);
  }
}

/** 记下本次已保存正文，供脏检查与重置对照 */
function captureContentSnapshot(tab: EmrOpenTab, doc: JSONContent) {
  tab.savedContentSnapshot = cloneEmrJsonForSnapshot(doc);
}

/** 门诊病历页眉标题钉回 `{documentTitle}`，避免复用/存盘把上次病名写进新草稿 */
function pinOutpatientPageChrome(typeCode: string, chrome: EmrPageChrome): EmrPageChrome {
  if (typeCode.trim() !== 'OUTPATIENT_RECORD') return chrome;
  const next = cloneEmrPageChrome(chrome);
  next.header.template = pinOutpatientChromeHeaderTitle(next.header.template);
  return next;
}

/** 从接口 content 读出 Canvas 信封；旧 Tiptap 门诊模版落到门诊预设纸面 */
function loadEmrContentIntoTab(tab: EmrOpenTab, raw: unknown): JSONContent {
  return toStationRecordDoc(raw, tab.documentTypeCode) as unknown as JSONContent;
}

/** 存盘：Canvas 信封，不再写 Tiptap 页眉侧车 */
function packTabContentForSave(_tab: EmrOpenTab, doc: JSONContent): JSONContent {
  return packCanvasTabContent(doc);
}

/** 页眉/页边距写入后强制重算分页，避免第二页起正文与页眉错位 */
function relayoutEditorForTab(tabKey: string) {
  const run = () => {
    const ed = getEditor(tabKey)?.getEditor?.();
    if (ed && !ed.isDestroyed) {
      requestEmrPaginationRelayout(ed);
    }
  };
  run();
  // 查看页/弹层冷加载：NodeView、字体、图片未就绪时单次 nextTick 会量错；延迟多拍对齐编辑态
  void nextTick(() => {
    run();
    requestAnimationFrame(() => {
      run();
      window.setTimeout(run, 80);
      window.setTimeout(run, 250);
      window.setTimeout(run, 600);
    });
  });
  if (typeof document !== 'undefined' && document.fonts?.ready) {
    void document.fonts.ready.then(run);
  }
}

function activateTab(key: string) {
  if (openTabs.value.some((t) => t.key === key)) {
    activeTabKey.value = key;
    // v-show 从隐藏切出时几何为 0，须可见后再排
    void nextTick(() => relayoutEditorForTab(key));
  }
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

/** 只读/正文世代变化时重建编辑器，避免套模板后打开已写病历仍停在空模板 */
function editorInstanceKey(tab: EmrOpenTab): string {
  const mode = isTabReadOnly(tab) ? 'ro' : 'rw';
  return `${tab.key}:${mode}:${tab.contentEpoch}`;
}

function remoteSaveForTab(tabKey: string) {
  return async (content: JSONContent) => {
    const tab = openTabs.value.find((t) => t.key === tabKey);
    if (!tab?.documentId || tab.documentReadOnly || props.forceReadOnly) return;
    const packed = packTabContentForSave(tab, content);
    await saveDocumentApi({
      documentId: tab.documentId,
      content: packed,
    });
    captureContentSnapshot(tab, stripEmrDocSidecar(packed));
  };
}

function remoteSaveHandlerForTab(tab: EmrOpenTab) {
  return tab.documentId && !tab.documentReadOnly && !props.forceReadOnly
    ? remoteSaveForTab(tab.key)
    : undefined;
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

/** 是否像文书类型编码（全大写+下划线），避免 Tab 显示英文 code */
function looksLikeDocumentTypeCode(text: string): boolean {
  return /^[A-Z][A-Z0-9_]*$/.test(text);
}

function tabDisplayTitle(tab: EmrOpenTab): string {
  const head = (tab.tabHead ?? '').trim();
  if (head) return head;
  const title = (tab.documentTitle ?? '').trim();
  if (title && !looksLikeDocumentTypeCode(title)) return title;
  const typeName = (tab.documentTypeName ?? '').trim();
  if (typeName) return typeName;
  if (tab.pendingTemplateId) return '新建文书';
  return '未命名';
}

/** 去掉后端 title 自动拼接的时间后缀，以及模板变体「（标准）」 */
function stripDocumentTitleTimestamp(title: string): string {
  return title
    .replace(/-\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}(?::\d{2})?)?.*$/u, '')
    .replace(/[（(]\s*标准\s*[）)]/g, '')
    .replace(/标准$/u, '')
    .trim();
}

/**
 * Tab 名头：优先具体文书标题（模板名，如「沟通记录」「评估单」），
 * 不要用文书类型名（如「告知书」），否则同类型多份病历看不出区别。
 */
function resolveTabDocumentHead(tab: EmrOpenTab): string {
  const title = (tab.documentTitle ?? '').trim();
  if (title && !looksLikeDocumentTypeCode(title)) {
    const stripped = stripDocumentTitleTimestamp(title);
    if (stripped) return stripped;
  }
  const typeName = (tab.documentTypeName ?? '').trim();
  if (typeName) return stripDocumentTitleTimestamp(typeName) || typeName;
  if (tab.pendingTemplateId) return '新建文书';
  return '未命名';
}

/**
 * 纸面/打印页眉标题。门诊病历固定用文书类型名，不用模板名、主诉或病名。
 */
function resolvePaperDocumentTitle(tab: EmrOpenTab): string {
  if ((tab.documentTypeCode ?? '').trim() === 'OUTPATIENT_RECORD') {
    const typeName = (tab.documentTypeName ?? '').trim();
    if (typeName && !looksLikeDocumentTypeCode(typeName)) {
      return stripDocumentTitleTimestamp(typeName) || typeName;
    }
  }
  return resolveTabDocumentHead(tab);
}

function resolveLoginDoctorDisplayName(): string {
  const u = userStore.userInfo as Record<string, unknown> | null;
  if (!u) return '';
  const candidates = [u.realName, u.name, u.userName, u.username];
  for (const raw of candidates) {
    const text = String(raw ?? '').trim();
    if (text) return text;
  }
  return '';
}

/**
 * 门诊病历新开（套模板/复用）：右下角医生钉成当前登录人。
 * 签字日期等落款时间仍走元数据当天默认值。
 */
function pinOutpatientSignoffMetadata(
  typeCode: string,
  meta: Record<string, string>,
): Record<string, string> {
  if (typeCode.trim() !== 'OUTPATIENT_RECORD') return meta;
  const doctor = resolveLoginDoctorDisplayName();
  if (!doctor) return meta;
  return { ...meta, doctorName: doctor };
}

/** 门诊病历新开前清掉右下角落款，其它文书只清落款时间 */
function stripSignoffForNewDocument(typeCode: string, doc: JSONContent): JSONContent {
  if (typeCode.trim() === 'OUTPATIENT_RECORD') {
    return stripOutpatientSignoffMetadataFromContentJson(doc);
  }
  return stripDocumentStampMetadataFromContentJson(doc);
}

function resolvePatientDisplayName(metadata?: Record<string, string>): string {
  const fromMeta = (metadata?.patientName ?? '').trim();
  if (fromMeta) return fromMeta;
  return (props.patientInfo?.name ?? '').trim();
}

/**
 * 同步 Tab 页签展示文案（病历头 / 患者 / 医生）。
 *
 * @param tab 目标 Tab
 * @param options.authorName 列表 savedByName 或保存后当前医生
 * @param options.metadataValues 患者名优先从元数据取
 */
function syncTabHeader(
  tab: EmrOpenTab,
  options?: { authorName?: string; metadataValues?: Record<string, string> },
) {
  tab.tabHead = resolveTabDocumentHead(tab);
  const meta = options?.metadataValues ?? tab.serverMetadataValues;
  tab.tabPatientName = resolvePatientDisplayName(meta);
  const authorHint = (options?.authorName ?? '').trim();
  if (authorHint) {
    tab.tabAuthorName = authorHint;
    return;
  }
  if (!tab.documentId && tab.pendingTemplateId) {
    tab.tabAuthorName = resolveLoginDoctorDisplayName();
    return;
  }
  if (!tab.tabAuthorName) {
    tab.tabAuthorName = resolveLoginDoctorDisplayName();
  }
}

/** 悬停完整标题 */
function tabFullTooltip(tab: EmrOpenTab): string {
  const parts = [
    (tab.tabHead ?? '').trim() || tabDisplayTitle(tab),
    (tab.tabPatientName ?? '').trim(),
    (tab.tabAuthorName ?? '').trim(),
  ].filter(Boolean);
  return parts.join(' · ');
}

/** Tab 展示用短标题（兼容旧引用） */
function tabShortTitle(tab: EmrOpenTab): string {
  const head = (tab.tabHead ?? '').trim();
  if (head) return head;
  const full = tabDisplayTitle(tab);
  const stripped = stripDocumentTitleTimestamp(full);
  return stripped || full;
}

function isTabReadOnly(tab: EmrOpenTab): boolean {
  if (props.forceReadOnly) return true;
  if (tab.revisingArchived) return false;
  return tab.documentReadOnly;
}

/** 文书状态展示；空状态（未落库）不展示 */
function documentStatusLabel(status?: string, revising?: boolean): string {
  const code = (status ?? '').trim();
  if (code === 'ARCHIVED' && revising) return '已归档·修改中';
  if (code === 'ARCHIVED') return '已归档';
  if (code === 'PENDING_ARCHIVE') return '待归档';
  return '';
}

const activeStatusLabel = computed(() =>
  documentStatusLabel(activeTab.value.documentStatus, activeTab.value.revisingArchived),
);

/** 已归档且属于本次就诊：确认后可改，改完只能提交审核 */
const canReviseArchived = computed(() => {
  if (!props.archiveEnabled) return false;
  if (props.forceReadOnly) return false;
  if (activeTab.value.revisingArchived) return false;
  if (activeTab.value.linkedReadOnly) return false;
  return (activeTab.value.documentStatus ?? '').trim() === 'ARCHIVED';
});

async function handleStartArchiveRevision() {
  try {
    await ElMessageBox.confirm(
      '该病历已归档。修改不会直接保存，改完只能提交归档；审核通过后才会成为最新病历。是否继续？',
      '修改已归档病历',
      {
        type: 'warning',
        confirmButtonText: '继续修改',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  activeTab.value.revisingArchived = true;
}

/** 已归档修改：快照当前正式正文后进入同一归档审批队列。 */
async function handleSubmitArchivedRevision() {
  const tab = activeTab.value;
  if (!tab.revisingArchived) return;
  const documentId = preserveApiIdForRequest(tab.documentId);
  if (!documentId) {
    ElMessage.warning('文书尚未落库，无法提交归档');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '提交后进入归档审批，和首次归档同一队列。通过前正式病历保持不变；驳回会恢复到提交前的版本。是否提交？',
      '提交归档',
      {
        type: 'warning',
        confirmButtonText: '提交归档',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  getActiveEditor()?.exitPageChromeEdit?.();
  await nextTick();
  const rawContent = getActiveEditor()?.getJSON?.() ?? tab.recordDoc;
  if (!rawContent) {
    ElMessage.warning('没有可提交的内容');
    return;
  }
  const content = packTabContentForSave(tab, rawContent);
  submitArchiveLoading.value = true;
  try {
    await submitRevisedArchiveApi({ documentId, content });
    tab.revisingArchived = false;
    ElMessage.success('已提交归档，通过后将成为最新病历');
    await reloadCurrentDocument({ silent: true });
  } catch {
    // 拦截器已提示
  } finally {
    submitArchiveLoading.value = false;
  }
}

function resetTabContent(tab: EmrOpenTab) {
  tab.documentId = '';
  tab.documentStatus = '';
  tab.revisingArchived = false;
  tab.linkedReadOnly = false;
  tab.pendingTemplateId = '';
  tab.documentReadOnly = false;
  tab.documentTypeCode = '';
  tab.documentTypeName = '';
  tab.documentTitle = '';
  tab.sourceTemplateId = '';
  tab.serverMetadataValues = {};
  tab.tabHead = '';
  tab.tabPatientName = '';
  tab.tabAuthorName = '';
  const blank = emptyStationRecordDoc() as unknown as JSONContent;
  tab.recordDoc = blank;
  tab.contentEpoch += 1;
  captureContentSnapshot(tab, blank);
  tab.editorSurface = 'document';
  tab.pageMargins = { ...defaultEditorMargins };
  tab.pageChrome = createDefaultEmrPageChrome();
  void nextTick(() => {
    getEditor(tab.key)?.markSaved?.();
    markTabClean(tab.key);
  });
}

/** 换患者、打开其它文书、离开页面：不再弹出未保存确认 */
async function confirmDiscardUnsavedIfNeeded(): Promise<boolean> {
  return true;
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

/** 文书类型 code → 显示名（页眉标题回退） */
let documentTypeNameByCode: Map<string, string> | null = null;
/** 文书类型 code → 目录分类（住院调阅判断用；院内自定义编码不在写死名单里） */
let documentTypeCategoryByCode: Map<string, string> | null = null;

async function ensureDocumentTypeNameByCode(): Promise<Map<string, string>> {
  if (documentTypeNameByCode && documentTypeCategoryByCode) return documentTypeNameByCode;
  const map = new Map<string, string>();
  const categories = new Map<string, string>();
  try {
    const list = await getDocumentTypeListApi({ enabled: true });
    for (const dt of list ?? []) {
      const code = String(dt.code ?? '').trim();
      if (!code) continue;
      const name = String(dt.name ?? '').trim();
      if (name) map.set(code, name);
      const category = String(dt.categoryCode ?? '').trim();
      if (category) categories.set(code, category);
    }
  } catch {
    /* 列表失败时保持空表，由调用方用其它来源兜底 */
  }
  documentTypeNameByCode = map;
  documentTypeCategoryByCode = categories;
  return map;
}

/** 按文书类型目录归类；目录没有分类时再按类型编码兜底 */
async function resolveDocumentDirectoryCategory(typeCode: string): Promise<string> {
  await ensureDocumentTypeNameByCode();
  const code = typeCode.trim();
  const categoryCode = code ? documentTypeCategoryByCode?.get(code) : undefined;
  return resolveEmrDirectoryCategory(code, categoryCode);
}

async function resolveDocumentTypeDisplayName(
  typeCode: string,
  ...hints: Array<string | undefined>
): Promise<string> {
  for (const h of hints) {
    const t = (h ?? '').trim();
    if (t) return t;
  }
  const code = typeCode.trim();
  if (!code) return '';
  const map = await ensureDocumentTypeNameByCode();
  return map.get(code) ?? '';
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
    sourceTemplateId?: number | string;
    status?: string;
    title?: string;
    /** 列表 savedByName，用于 Tab 医生展示 */
    authorName?: string;
  },
  options?: { keepEditorContent?: boolean },
) {
  const previousDocumentId = tab.documentId;
  const id = preserveApiIdForRequest(dto.id);
  if (id) {
    tab.documentId = id;
    tab.pendingTemplateId = '';
  }
  if (dto.status !== undefined) {
    tab.documentStatus = (dto.status ?? '').trim();
  } else if (id && !tab.documentStatus) {
    tab.documentStatus = 'DRAFT';
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
  const sourceTpl = preserveApiIdForRequest(dto.sourceTemplateId);
  if (sourceTpl) {
    tab.sourceTemplateId = sourceTpl;
  }
  const currentEncounterId = preserveApiIdForRequest(props.encounterId) ?? '';
  const docEncounterId = preserveApiIdForRequest(dto.encounterId) ?? '';
  const linkedPeerReadOnly =
    Boolean(currentEncounterId) && Boolean(docEncounterId) && docEncounterId !== currentEncounterId;
  tab.linkedReadOnly = linkedPeerReadOnly;
  tab.revisingArchived = false;
  tab.documentReadOnly = props.forceReadOnly || Boolean(dto.readOnly) || linkedPeerReadOnly;
  if (dto.metadataValues) {
    tab.serverMetadataValues = { ...dto.metadataValues };
  }
  const keepEditorContent = options?.keepEditorContent === true;
  const switchingDocument =
    Boolean(previousDocumentId) && Boolean(id) && previousDocumentId !== id;
  const sameDocument = Boolean(previousDocumentId) && Boolean(id) && previousDocumentId === id;
  const editorScroll =
    keepEditorContent || sameDocument ? getEditor(tab.key)?.captureScrollPosition?.() : null;
  const doc = loadEmrContentIntoTab(tab, dto.content);
  if (!keepEditorContent) {
    if (switchingDocument) {
      getEditor(tab.key)?.resetViewport?.();
      tab.recordDoc = doc;
      tab.contentEpoch += 1;
    } else if (!sameDocument) {
      getEditor(tab.key)?.resetViewport?.();
      tab.recordDoc = doc;
    }
  }
  captureContentSnapshot(tab, doc);
  tab.editorSurface = 'document';
  syncTabHeader(tab, {
    authorName: dto.authorName,
    metadataValues: dto.metadataValues ?? tab.serverMetadataValues,
  });
  void nextTick(() => {
    if (keepEditorContent || sameDocument) {
      getEditor(tab.key)?.restoreScrollPosition?.(editorScroll);
    }
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

/** 当前 Tab 是否已占用（已有文书、待保存模板或已选类型） */
function activeTabHasDraftSession(tab: EmrOpenTab): boolean {
  if (tab.documentId) return true;
  if (tab.pendingTemplateId) return true;
  return Boolean(tab.documentTypeCode.trim());
}

/**
 * 当前标签已有会话时再选模板，自动新开子 Tab，避免覆盖上一份已写病历。
 *
 * @return 套用模板的 Tab
 */
function resolveTabForNewTemplateSession(): EmrOpenTab {
  const current = activeTab.value;
  if (!activeTabHasDraftSession(current)) {
    return current;
  }
  const blank = createBlankTab();
  openTabs.value = [...openTabs.value, blank];
  activeTabKey.value = blank.key;
  return blank;
}

function canOpenNewTab(): boolean {
  return true;
}

/** 页签后新开一份空白病历 */
function openNewRecordTab() {
  if (props.forceReadOnly) return;
  if (!canOpenNewTab()) return;
  const blank = createBlankTab();
  openTabs.value = [...openTabs.value, blank];
  activeTabKey.value = blank.key;
  void nextTick(() => relayoutEditorForTab(blank.key));
}

function isTabDirtyNow(tabKey: string): boolean {
  refreshTabDirty(tabKey);
  return isTabDirty(tabKey) || getEditor(tabKey)?.isDirty?.() === true;
}

function disposeClosedTab(tabKey: string) {
  editorRefMap.delete(tabKey);
  const nextDirty = { ...tabDirtyMap.value };
  delete nextDirty[tabKey];
  tabDirtyMap.value = nextDirty;
}

async function confirmCloseDirtyTabs(keys: string[]): Promise<boolean> {
  if (keys.length === 0) return true;
  const dirty = keys.some((key) => isTabDirtyNow(key));
  if (!dirty) return true;
  try {
    await ElMessageBox.confirm(
      keys.length > 1
        ? '选中的病历含未保存修改，关闭后将丢失，是否继续？'
        : '该病历有未保存的修改，关闭后将丢失，是否继续？',
      '关闭确认',
      {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '继续编辑',
      },
    );
    return true;
  } catch {
    return false;
  }
}

/** 批量移除页签；全部关掉时留一份空白会话 */
function closeTabsByKeys(keys: string[], keepActiveKey?: string) {
  if (keys.length === 0) return;
  const keySet = new Set(keys);
  const remaining = openTabs.value.filter((tab) => !keySet.has(tab.key));
  for (const key of keys) {
    disposeClosedTab(key);
  }
  if (remaining.length === 0) {
    const blank = createBlankTab();
    openTabs.value = [blank];
    activeTabKey.value = blank.key;
    return;
  }
  openTabs.value = remaining;
  if (keySet.has(activeTabKey.value)) {
    const next = remaining.find((tab) => tab.key === keepActiveKey) ?? remaining[0];
    activeTabKey.value = next!.key;
    void nextTick(() => relayoutEditorForTab(next!.key));
  }
}

/** 关闭文书子 Tab；最后一页则清空为空白会话 */
async function closeTab(tabKey: string) {
  const tab = openTabs.value.find((t) => t.key === tabKey);
  if (!tab) return;
  const ok = await confirmCloseDirtyTabs([tabKey]);
  if (!ok) return;
  if (openTabs.value.length <= 1) {
    resetTabContent(tab);
    return;
  }
  closeTabsByKeys([tabKey]);
}

function canCloseTabsBefore(tabKey: string): boolean {
  return openTabs.value.findIndex((tab) => tab.key === tabKey) > 0;
}

function canCloseTabsAfter(tabKey: string): boolean {
  const idx = openTabs.value.findIndex((tab) => tab.key === tabKey);
  return idx >= 0 && idx < openTabs.value.length - 1;
}

/** 页签右键：关闭当前 / 前 / 后 / 全部 */
async function handleDocTabCommand(command: string, tabKey: string) {
  const idx = openTabs.value.findIndex((tab) => tab.key === tabKey);
  if (idx < 0) return;
  if (command === 'close') {
    await closeTab(tabKey);
    return;
  }
  if (command === 'closeBefore') {
    const keys = openTabs.value.slice(0, idx).map((tab) => tab.key);
    const ok = await confirmCloseDirtyTabs(keys);
    if (!ok) return;
    closeTabsByKeys(keys, tabKey);
    return;
  }
  if (command === 'closeAfter') {
    const keys = openTabs.value.slice(idx + 1).map((tab) => tab.key);
    const ok = await confirmCloseDirtyTabs(keys);
    if (!ok) return;
    closeTabsByKeys(keys, tabKey);
    return;
  }
  if (command === 'closeAll') {
    const keys = openTabs.value.map((tab) => tab.key);
    const ok = await confirmCloseDirtyTabs(keys);
    if (!ok) return;
    if (openTabs.value.length <= 1) {
      resetTabContent(openTabs.value[0]!);
      return;
    }
    closeTabsByKeys(keys);
  }
}

/** 本地载入模板正文到指定 Tab，不创建服务端文书（保存时再 create） */
async function applyTemplateLocallyToTab(
  tab: EmrOpenTab,
  typeCode: string,
  templateId: string,
  options?: { silent?: boolean; documentTypeName?: string; documentTitle?: string },
) {
  const tplId = preserveApiIdForRequest(templateId);
  if (!tplId) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  documentLoading.value = true;
  try {
    const canvasPreset = isCanvasStationTemplateId(tplId)
      ? getCanvasStationPresetByTemplateId(tplId)
      : undefined;
    const tpl = canvasPreset
      ? {
          name: canvasPreset.name,
          documentTypeName: canvasPreset.name,
          content: wrapCanvasPayload(canvasPreset.data),
        }
      : await getTemplateContentApi({ templateId: tplId });
    // await 期间接诊/切患者可能重建 Tab：落到仍存活的会话上
    const liveTab = openTabs.value.find((t) => t.key === tab.key) ?? activeTab.value;
    liveTab.documentId = '';
    liveTab.documentStatus = '';
    // 强制只读场景不应落到可编辑新建态
    liveTab.documentReadOnly = props.forceReadOnly;
    liveTab.documentTypeCode = canvasPreset?.typeCode ?? typeCode;
    liveTab.documentTypeName = await resolveDocumentTypeDisplayName(
      liveTab.documentTypeCode,
      options?.documentTypeName,
      tpl.documentTypeName,
    );
    // 页签用文书/模板名，不用文书类型名（同类型多份才分得清）
    const titleHint = (options?.documentTitle ?? '').trim();
    const fromTpl = (tpl.name ?? '').trim();
    liveTab.documentTitle = titleHint || fromTpl || '';
    liveTab.pendingTemplateId = canvasPreset ? '' : tplId;
    liveTab.sourceTemplateId = tplId;
    // 本地患者条 + 服务端解析一并落库到 tab，并烘进正文，避免首屏 watch 竞态空白
    const serverMeta = await resolveMetadataForCurrentPatient();
    const mergedMeta = mergeEmrMetadataValues(
      buildMetadataValuesFromPatientInfo(props.patientInfo),
      serverMeta,
    );
    const signoffMeta = pinOutpatientSignoffMetadata(
      liveTab.documentTypeCode,
      mergedMeta,
    );
    liveTab.serverMetadataValues = signoffMeta;
    // 模版章节硬分页与软分页叠加会半页留白；新开文书只靠纸高软分页
    const doc = loadEmrContentIntoTab(liveTab, tpl.content);
    getEditor(liveTab.key)?.resetViewport?.();
    liveTab.recordDoc = doc;
    liveTab.contentEpoch += 1;
    captureContentSnapshot(liveTab, doc);
    liveTab.editorSurface = 'document';
    syncTabHeader(liveTab, {
      metadataValues: signoffMeta,
    });
    void nextTick(() => {
      relayoutEditorForTab(liveTab.key);
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

/** 从解析结果中取出中/西医诊断展示值 */
function pickDiagnosisMetadataValues(vals: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const key of EMR_DIAGNOSIS_METADATA_FIELD_KEYS) {
    const text = String(vals[key] ?? '').trim();
    if (text) {
      out[key] = text;
    }
  }
  return out;
}

/**
 * 将已开立诊断填入当前打开文书的中/西医诊断空槽。
 * 不覆盖医生已写内容，也不改患者/日期元数据。
 */
async function refreshDiagnosisMetadataIntoOpenTabs() {
  if (props.forceReadOnly) return;
  if (!(props.patientId ?? '').trim() || !(props.encounterId ?? '').trim()) return;
  const writableTabs = openTabs.value.filter((tab) => tab.recordDoc && !tab.documentReadOnly);
  if (writableTabs.length === 0) return;

  const serverMeta = await resolveMetadataForCurrentPatient();
  const mergedMeta = mergeEmrMetadataValues(
    buildMetadataValuesFromPatientInfo(props.patientInfo),
    serverMeta,
  );
  const diagnosisVals = pickDiagnosisMetadataValues(mergedMeta);
  if (Object.keys(diagnosisVals).length === 0) return;

  for (const tab of writableTabs) {
    const editorComp = getEditor(tab.key);
    if (editorComp?.fillEmptyDiagnosisMetadata) {
      editorComp.fillEmptyDiagnosisMetadata(diagnosisVals);
      continue;
    }
    tab.recordDoc = applyDiagnosisMetadataToContentJson(
      tab.recordDoc as JSONContent,
      diagnosisVals,
    );
  }
}

/** 保存前确保已有服务端文书：未创建则按当前正文 create，不因只读或未选模板中断 */
async function ensureDocumentCreated(): Promise<null | string> {
  const tab = activeTab.value;
  if (tab.documentId) return tab.documentId;
  const tplId = tab.pendingTemplateId;
  const typeCode = tab.documentTypeCode.trim();
  const patientId = (props.patientId ?? '').trim();
  const encounterId = (props.encounterId ?? '').trim();
  if (!patientId) {
    ElMessage.warning('请先选择患者');
    return null;
  }
  if (!requireEncounterIdForDocument()) return null;
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return null;
  }
  // 保留本地正文：create 可能返回默认模板内容，不能覆盖已复用/已编辑草稿
  const localRaw = getEditor(tab.key)?.getJSON?.() ?? tab.recordDoc;
  const localContent = localRaw ? packTabContentForSave(tab, localRaw) : null;
  const created = await createDocumentApi({
    patientId,
    encounterId: encounterId || undefined,
    documentTypeCode: typeCode,
    templateId: tplId || undefined,
  });
  applyDocumentDtoToTab(
    tab,
    {
      ...created,
      content: localContent ?? created.content,
      documentTypeName: tab.documentTypeName || undefined,
      sourceTemplateId: tplId || undefined,
    },
    { keepEditorContent: true },
  );
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

/** 是否可保存：已有文书，或已选模板/复用草稿待首次落库 */
const canSaveDocument = computed(() => {
  if (documentId.value) return true;
  if (!documentTypeCode.value) return false;
  if (pendingTemplateId.value) return true;
  // 历史复用：已有类型与正文、且可编辑
  return !documentReadOnly.value && Boolean(activeTab.value.recordDoc);
});

/**
 * 是否可提交归档：已落库草稿、可写、非调阅；且入口允许归档。
 * 未显式带回 status 时按草稿处理（create/save 后默认可提交）。
 */
const canSubmitArchive = computed(() => {
  if (!props.archiveEnabled) return false;
  if (props.forceReadOnly) return false;
  if (!documentId.value) return false;
  if (documentReadOnly.value) return false;
  const status = (activeTab.value.documentStatus || 'DRAFT').trim();
  return status === 'DRAFT';
});

/** 当前只读文书可复用到本次就诊（历史/关联打开态；调阅除外） */
const canReuseActiveDocument = computed(() => {
  if (props.forceReadOnly) return false;
  if (!documentReadOnly.value) return false;
  if (!documentId.value) return false;
  return Boolean((props.encounterId ?? '').trim());
});

/** 有正文即可生成 PDF（未保存也可，走 content 入参） */
const canPrintDocument = computed(() => Boolean(activeTab.value.recordDoc));

/** 当前编辑器有正文且已知文书类型时可存为模板 */
const canSaveAsTemplate = computed(
  () =>
    Boolean(documentTypeCode.value.trim()) &&
    !documentReadOnly.value &&
    Boolean(activeTab.value.recordDoc),
);

/** 侧栏「用当前病历更新模板」：与存为模版同前提 */
const canUpdateTemplateContent = computed(() => canSaveAsTemplate.value);

async function requestDocumentPdf(previewMode: boolean, paper?: EmrPrintPaperPayload) {
  if (!canPrintDocument.value) {
    ElMessage.warning('请先选择模板或打开病历');
    return null;
  }
  const tab = activeTab.value;
  const rawContent = getActiveEditor()?.getJSON?.() ?? tab.recordDoc;
  if (!rawContent) {
    ElMessage.warning('没有可打印的内容');
    return null;
  }
  const baked = rawContent;
  const content = packTabContentForSave(tab, baked);
  // 正式打印且可写：先落库，保证归档与纸质一致
  if (!previewMode && canSaveDocument.value && !documentReadOnly.value) {
    const dirty = isTabDirty(tab.key) || getActiveEditor()?.isDirty?.() === true;
    if (dirty || !tab.documentId) {
      const saved = await handleSave();
      if (!saved) {
        return null;
      }
      if (!tab.documentId && !documentId.value) {
        return null;
      }
    }
  }

  const docId = preserveApiIdForRequest(tab.documentId) ?? '';
  const pm = getActiveEditor()?.getEditor?.()?.view?.dom;
  const screenMetrics = pm instanceof HTMLElement ? collectScreenPrintMetricsFromDom(pm) : null;
  const result = await generateDocumentPdfApi({
    documentId: docId || undefined,
    content,
    previewMode,
    patientId: props.patientId || undefined,
    encounterId: props.encounterId || undefined,
    documentTitle: resolvePaperDocumentTitle(tab),
    documentTypeName: (tab.documentTypeName || '').trim() || undefined,
    metadata: mergedMeta,
    printConfig: buildEmrPrintConfigPayload(tab.pageMargins, tab.pageChrome, {
      isOutpatientRecord: documentTypeCode.value.trim() === 'OUTPATIENT_RECORD',
      screenMetrics,
      paper,
    }),
  });
  if (!result?.pdfBase64?.trim()) {
    ElMessage.warning('未生成 PDF 内容');
    return null;
  }
  return result;
}

/** 打印预览 / 打印：走 Canvas 编辑器纸面打印 */
async function printWithCanvasEditor() {
  if (!canPrintDocument.value) {
    ElMessage.warning('当前没有可打印的病历');
    return;
  }
  const editor = getActiveEditor();
  if (!editor?.printDocument) {
    ElMessage.warning('编辑器尚未就绪');
    return;
  }
  try {
    await editor.printDocument();
  } catch {
    ElMessage.warning('打印失败，请稍后重试');
  }
}

/** 打印预览：Canvas 纸面打印（系统打印框内预览） */
function handlePrintPreview() {
  void printWithCanvasEditor();
}

/** 打印：Canvas 纸面打印 */
function handlePrint() {
  void printWithCanvasEditor();
}

/** 预览弹窗改纸张：按当前正文重出 PDF，不改编辑器纸面 */
async function onPreviewPaperChange(paper: EmrPrintPaperPayload) {
  if (pdfLoading.value) return;
  pdfPreviewAutoPrint.value = false;
  pdfLoading.value = true;
  try {
    const result = await requestDocumentPdf(pdfPreviewMode.value, paper);
    if (!result) return;
    pdfPreviewBase64.value = result.pdfBase64!;
    pdfPreviewPageCount.value = result.pageCount;
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
  const rawContent = getActiveEditor()?.getJSON?.() ?? activeTab.value.recordDoc;
  if (!rawContent) {
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
  const rawContent = getActiveEditor()?.getJSON?.() ?? activeTab.value.recordDoc;
  if (!rawContent) {
    ElMessage.warning('没有可保存为模板的内容');
    return;
  }
  const content = packTabContentForSave(activeTab.value, rawContent);
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

/** 侧栏：重命名个人/科室模板 */
async function handleRenameTemplate(tpl: TemplateDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可修改模板');
    return;
  }
  const id = preserveApiIdForRequest(tpl.id) ?? '';
  if (!id) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  const currentName = (tpl.name ?? '').trim();
  try {
    const { value } = await ElMessageBox.prompt('请输入新的模板名称', '重命名模板', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: currentName,
      inputPattern: /\S+/,
      inputErrorMessage: '模板名称不能为空',
    });
    const name = String(value ?? '').trim();
    if (!name) {
      ElMessage.warning('模板名称不能为空');
      return;
    }
    if (name === currentName) return;
    await updateTemplateApi({ id, name });
    ElMessage.success('已重命名');
    void historyPanelRef.value?.refreshTemplateTree?.();
  } catch {
    // 取消或错误由拦截器提示
  }
}

/** 侧栏：用当前病历正文覆盖模板内容 */
async function handleUpdateTemplateContent(tpl: TemplateDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可修改模板');
    return;
  }
  if (!canUpdateTemplateContent.value) {
    ElMessage.warning('请先打开可编辑病历后再更新模板内容');
    return;
  }
  const id = preserveApiIdForRequest(tpl.id) ?? '';
  if (!id) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  const rawContent = getActiveEditor()?.getJSON?.() ?? activeTab.value.recordDoc;
  if (!rawContent) {
    ElMessage.warning('没有可写入模板的内容');
    return;
  }
  const content = packTabContentForSave(activeTab.value, rawContent);
  const title = (tpl.name ?? '').trim() || (tpl.code ?? '').trim() || '模板';
  try {
    await ElMessageBox.confirm(
      `确定用当前病历内容覆盖模板「${title}」吗？覆盖后不可恢复。`,
      '更新模板内容',
      {
        type: 'warning',
        confirmButtonText: '覆盖更新',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  try {
    await updateTemplateApi({ id, content });
    ElMessage.success('模板内容已更新');
    void historyPanelRef.value?.refreshTemplateTree?.();
  } catch {
    // 业务/网络错误已由 request 拦截器提示
  }
}

/** 侧栏：删除个人/科室模板 */
async function handleDeleteTemplate(tpl: TemplateDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可删除模板');
    return;
  }
  const id = preserveApiIdForRequest(tpl.id) ?? '';
  if (!id) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  try {
    await deleteTemplateApi({ id });
    ElMessage.success('模板已删除');
    void historyPanelRef.value?.refreshTemplateTree?.();
  } catch {
    // 业务/网络错误已由 request 拦截器提示
  }
}

/**
 * 手动保存：无文书时先 create，再 save。
 * 不因未选模板、只读或归档修订禁用；接口成功即提示「保存成功」并回读刷新。
 */
async function handleSave(): Promise<boolean> {
  const tab = activeTab.value;
  // 先退出页眉页脚编辑，保证侧车与正文一并落库
  getActiveEditor()?.exitPageChromeEdit?.();
  await nextTick();
  const activeEditor = getActiveEditor();
  const savedViewport = activeEditor?.captureScrollPosition?.();
  const rawContent = activeEditor?.getJSON?.() ?? tab.recordDoc;
  if (!rawContent) {
    ElMessage.warning('没有可保存的内容');
    return false;
  }
  const content = packTabContentForSave(tab, rawContent);
  saveLoading.value = true;
  // 接口一旦成功，后续刷新失败也视为保存成功
  let persisted = false;
  try {
    const id = await ensureDocumentCreated();
    if (!id) return false;
    await saveDocumentApi({
      documentId: id,
      content,
    });
    persisted = true;
    ElMessage.success('保存成功');
    void historyPanelRef.value?.refreshHistory?.();
    try {
      const body = stripEmrDocSidecar(content);
      captureContentSnapshot(tab, body);
      getActiveEditor()?.markSaved?.();
      await getActiveEditor()?.clearDraft?.();
      markTabClean(tab.key);
      tab.tabAuthorName = resolveLoginDoctorDisplayName() || tab.tabAuthorName;
      syncTabHeader(tab);
    } catch {
      // 本地快照/草稿清理失败不阻断回读刷新
    }
    if (isInpatientStation.value) {
      await refreshProgressNoteNavList();
    }
    getActiveEditor()?.restoreScrollPosition?.(savedViewport);
    return true;
  } catch {
    // 未落库：拦截器已提示；已落库：刷新失败不改成功结论
    return persisted;
  } finally {
    saveLoading.value = false;
  }
}

/**
 * 提交归档：DRAFT → PENDING_ARCHIVE。
 * 有未保存修改时先落库，再调门诊/住院提交归档接口。
 */
async function handleSubmitArchive(): Promise<boolean> {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可提交归档');
    return false;
  }
  if (!canSubmitArchive.value) {
    ElMessage.warning('仅草稿文书可提交归档');
    return false;
  }
  try {
    await ElMessageBox.confirm(
      '提交后文书将进入待归档审批且不可编辑，确认提交归档？',
      '提交归档',
      {
        type: 'warning',
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return false;
  }

  submitArchiveLoading.value = true;
  try {
    if (isTabDirty(activeTabKey.value) || getActiveEditor()?.isDirty?.()) {
      const saved = await handleSave();
      if (!saved) return false;
    }
    const id = preserveApiIdForRequest(activeTab.value.documentId);
    if (!id) {
      ElMessage.warning('文书尚未保存，请先保存再提交归档');
      return false;
    }
    if (isInpatientStation.value) {
      await submitInpatientDocumentArchiveApi({ documentId: id });
    } else {
      await submitOutpatientDocumentArchiveApi({ documentId: id });
    }
    const tab = activeTab.value;
    tab.documentStatus = 'PENDING_ARCHIVE';
    tab.documentReadOnly = true;
    ElMessage.success('已提交归档审批');
    void historyPanelRef.value?.refreshHistory?.();
    await reloadCurrentDocument({ silent: true });
    return true;
  } catch {
    return false;
  } finally {
    submitArchiveLoading.value = false;
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
  // 护士站调阅：仅允许打开住院目录文书；病案只读查阅不限制（可看不可改）
  // 院内类型码（如 DT_GJ_06）不在写死名单里，须用文书类型目录上的 categoryCode
  if (props.restrictHistoryToInpatient) {
    const category = await resolveDocumentDirectoryCategory(row.documentTypeCode ?? '');
    if (category !== 'INPATIENT') {
      if (!options?.silent) {
        ElMessage.warning('病历调阅仅支持查看住院病历');
      }
      return;
    }
  }
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
      activateTab(existing.key);
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
    const typeCode = (dto.documentTypeCode ?? row.documentTypeCode ?? '').trim();
    const typeName = await resolveDocumentTypeDisplayName(
      typeCode,
      row.documentTypeName,
      dto.documentTypeName,
    );
    applyDocumentDtoToTab(target, {
      ...dto,
      documentTypeCode: typeCode || dto.documentTypeCode || row.documentTypeCode,
      // 列表行可能无类型名：勿用 undefined 盖掉 content 接口返回值；再按 code 查字典
      documentTypeName: typeName || undefined,
      encounterId: dto.encounterId ?? row.encounterId,
      sourceTemplateId: row.sourceTemplateId,
      title: dto.title ?? row.title,
      authorName: (row.savedByName ?? '').trim() || undefined,
    });
    void refreshDiagnosisMetadataIntoOpenTabs();
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

/** 从历史列表打开已有文书：当前 Tab 已有其它会话时自动新标签打开，避免盖掉已写病历 */
async function handleSelectDocument(row: DocumentSummaryDto) {
  const rowId = preserveApiIdForRequest(row.id) ?? '';
  const activeId = preserveApiIdForRequest(activeTab.value.documentId) ?? '';
  const openInNewTab =
    activeTabHasDraftSession(activeTab.value) && rowId && activeId !== rowId;
  await openDocumentFromSummary(row, openInNewTab ? { openInNewTab: true } : undefined);
}

/** 历史列表 Ctrl/右键：新标签打开 */
async function handleOpenDocumentInNewTab(row: DocumentSummaryDto) {
  await openDocumentFromSummary(row, { openInNewTab: true });
}

/**
 * 历史/关联病历复用到本次就诊：复制正文 → 新建草稿并保存。
 * 不修改原就诊文书；患者元数据按当前就诊重烤。
 */
async function handleReuseDocument(row: DocumentSummaryDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可复用');
    return;
  }
  if (documentLoading.value) return;

  const sourceId = preserveApiIdForRequest(row.id) ?? '';
  if (!sourceId) {
    ElMessage.warning('该病历缺少有效 ID');
    return;
  }
  if (!requireEncounterIdForDocument()) return;

  const title =
    (row.documentTypeName ?? '').trim() ||
    (row.title ?? '').trim() ||
    (row.documentTypeCode ?? '').trim() ||
    '历史病历';
  try {
    await ElMessageBox.confirm(
      `将「${title}」内容复制到本次就诊并新建病历草稿，不会修改原病历。是否继续？`,
      '复用历史病历',
      {
        type: 'info',
        confirmButtonText: '复用',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  const proceed = await confirmDiscardUnsavedIfNeeded('replace');
  if (!proceed) return;

  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }

  const patientId = (props.patientId ?? '').trim();
  const encounterId = (props.encounterId ?? '').trim();
  if (!patientId) {
    ElMessage.warning('请先选择患者');
    return;
  }

  documentLoading.value = true;
  saveLoading.value = true;
  try {
    await nextTick();
    const dto = await getDocumentContentApi({ documentId: sourceId });
    const typeCode = (dto.documentTypeCode ?? row.documentTypeCode ?? '').trim();
    if (!typeCode) {
      ElMessage.warning('该病历缺少文书类型，无法复用');
      return;
    }

    const sourceTplId = preserveApiIdForRequest(row.sourceTemplateId) ?? '';

    // 按当前患者/就诊重烤只读元数据芯片；临床自由文本原样保留
    const serverMeta = await resolveMetadataForCurrentPatient();
    const mergedMeta = mergeEmrMetadataValues(
      buildMetadataValuesFromPatientInfo(props.patientInfo),
      serverMeta,
    );
    const signoffMeta = pinOutpatientSignoffMetadata(typeCode, mergedMeta);
    const packedDoc = packCanvasTabContent(toStationRecordDoc(dto.content, typeCode));

    const created = await createDocumentApi({
      patientId,
      encounterId: encounterId || undefined,
      documentTypeCode: typeCode,
      templateId: sourceTplId || undefined,
    });
    const newId = preserveApiIdForRequest(created.id);
    if (!newId) {
      ElMessage.error('复用失败：未返回新文书 ID');
      return;
    }
    await saveDocumentApi({
      documentId: newId,
      content: packedDoc,
    });

    const tab = activeTab.value;
    applyDocumentDtoToTab(tab, {
      ...created,
      content: packedDoc,
      documentTypeCode: typeCode,
      documentTypeName: (row.documentTypeName ?? '').trim() || tab.documentTypeName,
      encounterId,
      id: newId,
      metadataValues: signoffMeta,
      readOnly: false,
      sourceTemplateId: sourceTplId || undefined,
      title: created.title ?? '',
      authorName: resolveLoginDoctorDisplayName(),
    });
    ElMessage.success('已复用到本次就诊');
    void historyPanelRef.value?.refreshHistory?.();
    if (isInpatientStation.value) {
      await refreshProgressNoteNavList();
    }
  } catch {
    // 业务/网络错误已由 request 拦截器提示
  } finally {
    documentLoading.value = false;
    saveLoading.value = false;
  }
}

/** 工具栏：将当前打开的只读历史/关联病历复用到本次就诊 */
async function handleReuseActiveDocument() {
  const tab = activeTab.value;
  const id = preserveApiIdForRequest(tab.documentId) ?? '';
  if (!id) {
    ElMessage.warning('当前没有可复用的病历');
    return;
  }
  await handleReuseDocument({
    id,
    documentTypeCode: tab.documentTypeCode,
    documentTypeName: tab.documentTypeName,
    sourceTemplateId: tab.sourceTemplateId || undefined,
    title: tab.documentTitle,
  });
}

/** 关闭已打开的被删文书 Tab（不弹未保存确认） */
function closeDeletedDocumentTab(documentId: string) {
  const tab = findTabByDocumentId(documentId);
  if (!tab) return;
  if (openTabs.value.length <= 1) {
    resetTabContent(tab);
    return;
  }
  const idx = openTabs.value.findIndex((t) => t.key === tab.key);
  openTabs.value = openTabs.value.filter((t) => t.key !== tab.key);
  editorRefMap.delete(tab.key);
  const nextDirty = { ...tabDirtyMap.value };
  delete nextDirty[tab.key];
  tabDirtyMap.value = nextDirty;
  if (activeTabKey.value === tab.key) {
    const next = openTabs.value[Math.max(0, idx - 1)] ?? openTabs.value[0];
    activeTabKey.value = next!.key;
  }
}

/** 侧栏右键软删病历（仅创建人；后端二次校验） */
async function handleDeleteDocument(row: DocumentSummaryDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可删除');
    return;
  }
  const id = preserveApiIdForRequest(row.id) ?? '';
  if (!id) {
    ElMessage.warning('该病历缺少有效 ID');
    return;
  }
  try {
    await deleteDocumentApi({ documentId: id });
    ElMessage.success('删除成功');
    closeDeletedDocumentTab(id);
    void historyPanelRef.value?.refreshHistory?.();
    if (isInpatientStation.value) {
      await refreshProgressNoteNavList();
    }
  } catch {
    // 业务/网络错误已由 request 拦截器提示
  }
}

/** 侧栏右键：修改已写文书显示标题（非模板） */
async function handleRenameDocument(row: DocumentSummaryDto) {
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可重命名');
    return;
  }
  const id = preserveApiIdForRequest(row.id) ?? '';
  if (!id) {
    ElMessage.warning('该病历缺少有效 ID');
    return;
  }
  const currentTitle = stripDocumentTitleTimestamp((row.title ?? '').trim()) || '病历';
  try {
    const { value } = await ElMessageBox.prompt('请输入新的病历名称', '重命名病历', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: currentTitle,
      inputPattern: /\S+/,
      inputErrorMessage: '病历名称不能为空',
    });
    const title = String(value ?? '').trim();
    if (!title) {
      ElMessage.warning('病历名称不能为空');
      return;
    }
    if (title === currentTitle) return;
    const updated = await updateDocumentTitleApi({ documentId: id, title });
    const nextTitle = (updated?.title ?? title).trim();
    const openTab = findTabByDocumentId(id);
    // 已打开的 Tab 同步标题与页眉用名
    if (openTab) {
      openTab.documentTitle = nextTitle;
      openTab.tabHead = resolveTabDocumentHead(openTab);
    }
    ElMessage.success('已重命名');
    void historyPanelRef.value?.refreshHistory?.();
    if (isInpatientStation.value) {
      await refreshProgressNoteNavList();
    }
  } catch {
    // 取消或业务错误：取消不提示；接口错误由拦截器提示
  }
}

/** 住院：静默打开最新病程（侧栏默认「本诊」分页） */
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
  if (props.forceReadOnly) {
    ElMessage.warning('病历调阅仅支持查看，不可新建');
    return;
  }
  if (documentLoading.value) return;

  const typeCode = (item.documentTypeCode ?? item.template?.documentTypeCode ?? '').trim();
  if (!typeCode) {
    ElMessage.warning('该模板未关联文书类型');
    return;
  }

  const targetTab = resolveTabForNewTemplateSession();
  const needDiscardConfirm = targetTab.key === activeTabKey.value;
  if (needDiscardConfirm) {
    const proceed = await confirmDiscardUnsavedIfNeeded('replace');
    if (!proceed) return;
  }
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
  await applyTemplateLocallyToTab(targetTab, typeCode, tplId, {
    documentTypeName: (item.documentTypeName ?? '').trim(),
    documentTitle: (item.label ?? item.template?.name ?? '').trim(),
    silent: needDiscardConfirm ? undefined : true,
  });
  if (!needDiscardConfirm) {
    ElMessage.success('已在新标签载入模板，编辑后请点击保存');
  }
}

/** 住院已打开文书：一患者一实例，换人由宿主换 key，本实例不重置 */
function hasOpenedDocumentSession(): boolean {
  return openTabs.value.some((tab) => Boolean(tab.documentId) || Boolean(tab.pendingTemplateId));
}

function restoreOpenEditorViewports() {
  for (const tab of openTabs.value) {
    getEditor(tab.key)?.restoreScrollPosition?.();
  }
}

/** 切换患者/就诊时清空会话并（住院）加载最新病程 */
let skipPatientEncounterWatch = true;
const isTabActive = ref(false);
const pendingPatientEncounterReload = ref(false);

watch(
  () => [props.patientId, props.encounterId] as const,
  async ([nextPatientId, nextEncounterId], [prevPatientId, prevEncounterId]) => {
    if (
      props.hostOwnsUnsavedGuard &&
      isInpatientStation.value &&
      hasOpenedDocumentSession()
    ) {
      return;
    }
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

/** 仅病历页关浏览器/刷新时提示，其它业务页不拦 */
function handleEmrBrowserUnload(event: BeforeUnloadEvent) {
  event.preventDefault();
  event.returnValue = '';
}

function bindEmrBrowserUnload() {
  window.addEventListener('beforeunload', handleEmrBrowserUnload);
}

function unbindEmrBrowserUnload() {
  window.removeEventListener('beforeunload', handleEmrBrowserUnload);
}

onActivated(() => {
  isTabActive.value = true;
  if (!props.hostOwnsUnsavedGuard) {
    bindEmrBrowserUnload();
  }
  if (
    props.hostOwnsUnsavedGuard &&
    isInpatientStation.value &&
    hasOpenedDocumentSession()
  ) {
    pendingPatientEncounterReload.value = false;
    void refreshDiagnosisMetadataIntoOpenTabs();
    restoreOpenEditorViewports();
    return;
  }
  // 普通切回病历 Tab：按当前就诊重填空的中/西医诊断槽
  if (!pendingPatientEncounterReload.value && !skipPatientEncounterWatch) {
    void refreshDiagnosisMetadataIntoOpenTabs();
    restoreOpenEditorViewports();
    return;
  }
  pendingPatientEncounterReload.value = false;
  void (async () => {
    if (skipPatientEncounterWatch) {
      skipPatientEncounterWatch = false;
      if (isInpatientStation.value && props.patientId) {
        await loadInpatientLatestProgressNote({ silent: true });
      }
      void refreshDiagnosisMetadataIntoOpenTabs();
      return;
    }
    resetDocumentSession();
    progressNoteNavList.value = [];
    if (isInpatientStation.value && props.patientId) {
      await loadInpatientLatestProgressNote({ silent: true });
    }
    void refreshDiagnosisMetadataIntoOpenTabs();
  })();
});

onDeactivated(() => {
  isTabActive.value = false;
  if (!props.hostOwnsUnsavedGuard) {
    unbindEmrBrowserUnload();
  }
});

onMounted(() => {
  isTabActive.value = true;
  if (!props.hostOwnsUnsavedGuard) {
    registerEmrUnsavedGuard(confirmDiscardUnsavedIfNeeded);
    bindEmrBrowserUnload();
  }
  void tryOpenInitialDocument();
  if (
    props.hostOwnsUnsavedGuard &&
    skipPatientEncounterWatch &&
    isInpatientStation.value &&
    props.patientId
  ) {
    skipPatientEncounterWatch = false;
    void loadInpatientLatestProgressNote({ silent: true });
  }
});

/**
 * 病案列表带入 documentId 时自动打开该文书（仅本就诊收窄场景）。
 */
async function tryOpenInitialDocument() {
  if (!props.encounterScoped) {
    return;
  }
  const id = preserveApiIdForRequest(props.initialDocumentId);
  if (!id) {
    return;
  }
  // 已打开同文书则跳过
  if (findTabByDocumentId(id)) {
    return;
  }
  await openDocumentFromSummary(
    {
      id,
      encounterId: props.encounterId,
      patientId: props.patientId,
    } as DocumentSummaryDto,
    { silent: true, skipUnsavedConfirm: true },
  );
}

watch(
  () => [props.initialDocumentId, props.encounterId, props.encounterScoped] as const,
  () => {
    void tryOpenInitialDocument();
  },
);

onUnmounted(() => {
  if (!props.hostOwnsUnsavedGuard) {
    unbindEmrBrowserUnload();
    registerEmrUnsavedGuard(null);
  }
});

onBeforeRouteLeave(async (_to, _from, next) => {
  if (props.hostOwnsUnsavedGuard || isForceLogoutSession()) {
    next(true);
    return;
  }
  const ok = await confirmDiscardUnsavedIfNeeded();
  next(ok);
});

defineExpose({
  applyEmrTemplate,
  confirmDiscardUnsavedIfNeeded,
  hasUnsavedChanges,
  reload: reloadCurrentDocument,
  refreshTemplateTree: () => historyPanelRef.value?.refreshTemplateTree?.(),
  restoreViewport: restoreOpenEditorViewports,
  save: handleSave,
  submitArchive: handleSubmitArchive,
});
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
  <div
    class="medical-record-form flex h-full overflow-hidden bg-white"
    :class="{ 'medical-record-form--fullscreen': isFullscreen }"
  >
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
          :view-only="forceReadOnly"
          :encounter-scoped="encounterScoped"
          :restrict-history-to-inpatient="restrictHistoryToInpatient"
          :can-update-template-content="canUpdateTemplateContent"
          @select="handleHistorySelect"
          @select-document="handleSelectDocument"
          @open-document-in-new-tab="handleOpenDocumentInNewTab"
          @delete-document="handleDeleteDocument"
          @rename-document="handleRenameDocument"
          @reuse-document="handleReuseDocument"
          @rename-template="handleRenameTemplate"
          @update-template-content="handleUpdateTemplateContent"
          @delete-template="handleDeleteTemplate"
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
      <div class="medical-record-form__chrome">
        <div class="medical-record-form__doc-tabs">
          <ElDropdown
            v-for="tab in openTabs"
            :key="tab.key"
            trigger="contextmenu"
            class="medical-record-form__doc-tab-menu"
            @command="(cmd) => handleDocTabCommand(String(cmd), tab.key)"
          >
            <div
              class="medical-record-form__doc-tab"
              :class="{
                'medical-record-form__doc-tab--active': tab.key === activeTabKey,
                'medical-record-form__doc-tab--readonly': isTabReadOnly(tab),
              }"
              :title="tabFullTooltip(tab)"
              @click="activateTab(tab.key)"
            >
              <svg class="medical-record-form__doc-tab-ico" viewBox="0 0 16 16" aria-hidden="true">
                <path d="M4.2 2.2h4.8L12.2 5.4V13.8H4.2V2.2z" />
                <path d="M9 2.2V5.4h3.2" />
              </svg>
              <span class="medical-record-form__doc-tab-head">{{ tabShortTitle(tab) }}</span>
              <span
                v-if="(tab.tabAuthorName || '').trim()"
                class="medical-record-form__doc-tab-author"
              >
                {{ tab.tabAuthorName }}
              </span>
              <span
                v-if="isTabDirty(tab.key)"
                class="medical-record-form__doc-tab-dirty"
                title="未保存"
              />
              <span
                v-if="documentStatusLabel(tab.documentStatus, tab.revisingArchived)"
                class="medical-record-form__doc-tab-badge"
              >
                {{ documentStatusLabel(tab.documentStatus, tab.revisingArchived) }}
              </span>
              <span v-else-if="isTabReadOnly(tab)" class="medical-record-form__doc-tab-badge">
                只读
              </span>
              <button
                type="button"
                class="medical-record-form__doc-tab-close"
                title="关闭"
                @click.stop="closeTab(tab.key)"
              >
                ×
              </button>
            </div>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="close">关闭当前</ElDropdownItem>
                <ElDropdownItem command="closeBefore" :disabled="!canCloseTabsBefore(tab.key)">
                  关闭前
                </ElDropdownItem>
                <ElDropdownItem command="closeAfter" :disabled="!canCloseTabsAfter(tab.key)">
                  关闭后
                </ElDropdownItem>
                <ElDropdownItem command="closeAll" divided>关闭全部</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <button
            v-if="!forceReadOnly"
            type="button"
            class="medical-record-form__doc-tab-add"
            title="新开病历"
            @click="openNewRecordTab"
          >
            +
          </button>
        </div>
      </div>
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div
          v-loading="documentLoading"
          class="medical-record-form__editor-wrap h-full"
          @focusin="onEditorWrapFocusIn"
          @mouseup="onEditorWrapCaretTrack"
          @keyup="onEditorWrapCaretTrack"
        >
          <div
            v-for="tab in openTabs"
            :key="tab.key"
            class="medical-record-form__editor-pane"
            :class="{ 'medical-record-form__editor-pane--active': tab.key === activeTabKey }"
          >
            <CanvasStationEditor
              :key="editorInstanceKey(tab)"
              :ref="(el) => setEditorRef(tab.key, el as InstanceType<typeof CanvasStationEditor> | null)"
              v-model="tab.recordDoc"
              class="medical-record-form__editor"
              :read-only="isTabReadOnly(tab)"
              :metadata-values="
                mergeEmrMetadataValues(
                  buildMetadataValuesFromPatientInfo(patientInfo),
                  tab.serverMetadataValues,
                )
              "
              @change="onEditorChange(tab.key)"
            >
              <template #print>
                <div class="medical-record-form__host">
                  <div v-if="showProgressNoteNav" class="medical-record-form__host-group">
                    <button
                      type="button"
                      class="mrf-rib"
                      :disabled="!canProgressNotePrev || documentLoading"
                      @click="navigateProgressNote(-1)"
                    >
                      上一张
                    </button>
                    <button
                      type="button"
                      class="mrf-rib"
                      :disabled="!canProgressNoteNext || documentLoading"
                      @click="navigateProgressNote(1)"
                    >
                      下一张
                    </button>
                  </div>
                  <div class="medical-record-form__host-group">
                    <button
                      type="button"
                      class="mrf-rib"
                      :title="isFullscreen ? '退出全屏（Esc）' : '全屏写病历'"
                      @click="handleToggleEmrFullscreen"
                    >
                      <svg v-if="!isFullscreen" class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M3 6.2V3.2h3M10 3.2h3v3M13 9.8v3h-3M6 12.8H3v-3" />
                      </svg>
                      <svg v-else class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M6.2 3.2v3h-3M9.8 3.2v3h3M12.8 9.8h-3v3M3.2 9.8h3v3" />
                      </svg>
                      {{ isFullscreen ? '退出' : '全屏' }}
                    </button>
                    <button
                      type="button"
                      class="mrf-rib"
                      :disabled="documentLoading"
                      @click="reloadCurrentDocument"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M13.2 8A5.2 5.2 0 1 1 11.6 4" />
                        <path d="M13.2 2.8v3.2H10" />
                      </svg>
                      刷新
                    </button>
                    <button
                      v-if="!forceReadOnly"
                      type="button"
                      class="mrf-rib"
                      :disabled="!canSaveDocument"
                      @click="handleReset"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M3.2 7.2h6.2a3 3 0 1 1 0 6H7.4" />
                        <path d="M3.2 7.2 5.6 4.8M3.2 7.2 5.6 9.6" />
                      </svg>
                      重置
                    </button>
                    <button
                      v-if="canReuseActiveDocument"
                      type="button"
                      class="mrf-rib"
                      :disabled="documentLoading || saveLoading"
                      title="将当前历史/关联病历复制到本次就诊新建草稿"
                      @click="handleReuseActiveDocument"
                    >
                      复用到本次就诊
                    </button>
                  </div>
                  <div
                    v-if="
                      activeStatusLabel ||
                      documentReadOnly ||
                      forceReadOnly ||
                      canReviseArchived ||
                      (archiveEnabled && !forceReadOnly)
                    "
                    class="medical-record-form__host-group"
                  >
                    <span v-if="activeStatusLabel" class="medical-record-form__host-status">
                      {{ activeStatusLabel }}
                    </span>
                    <span
                      v-else-if="documentReadOnly || forceReadOnly"
                      class="medical-record-form__host-status"
                    >
                      只读
                    </span>
                    <button
                      v-if="!forceReadOnly && canReviseArchived"
                      type="button"
                      class="mrf-rib"
                      @click="handleStartArchiveRevision"
                    >
                      修改
                    </button>
                    <button
                      v-if="!forceReadOnly && archiveEnabled && activeTab.revisingArchived"
                      type="button"
                      class="mrf-rib"
                      :disabled="submitArchiveLoading"
                      @click="handleSubmitArchivedRevision"
                    >
                      提交归档
                    </button>
                    <button
                      v-else-if="!forceReadOnly && archiveEnabled"
                      type="button"
                      class="mrf-rib"
                      :disabled="!canSubmitArchive || saveLoading || submitArchiveLoading"
                      @click="handleSubmitArchive"
                    >
                      提交归档
                    </button>
                  </div>
                  <div class="medical-record-form__host-group medical-record-form__host-group--aside">
                    <button
                      type="button"
                      class="mrf-rib"
                      :disabled="!canPrintDocument || pdfLoading"
                      @click="handlePrintPreview"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M2 8s2.2-3.4 6-3.4S14 8 14 8s-2.2 3.4-6 3.4S2 8 2 8z" />
                        <circle cx="8" cy="8" r="1.5" />
                      </svg>
                      打印预览
                    </button>
                    <button
                      type="button"
                      class="mrf-rib"
                      :disabled="!canPrintDocument || pdfLoading"
                      @click="handlePrint"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M4.6 9.4V3.4h6.8v6" />
                        <rect x="3.2" y="8" width="9.6" height="4.4" rx="0.6" />
                        <path d="M5.4 10.8h5.2" />
                      </svg>
                      打印
                    </button>
                  </div>
                </div>
              </template>
              <template #host>
                <div class="medical-record-form__host">
                  <div v-if="!forceReadOnly" class="medical-record-form__host-group">
                    <button
                      type="button"
                      class="mrf-rib mrf-rib--accent"
                      :disabled="!canImportClinicalText"
                      title="勾选当前就诊诊断，插入到光标、自由文本或辅助检查填写区"
                      @mousedown.prevent="freezeImportInsertTargets"
                      @click="openImportDiagnosisDialog"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <rect x="3.4" y="2.4" width="9.2" height="11.2" rx="1" />
                        <path d="M6 2.4h4v2H6zM8 7.2v3.6M6.2 9h3.6" />
                      </svg>
                      插入诊断
                    </button>
                    <button
                      v-if="!forceReadOnly"
                      type="button"
                      class="mrf-rib mrf-rib--accent"
                      :disabled="!canImportClinicalText"
                      title="勾选当前就诊医嘱，插入到光标、自由文本或辅助检查填写区"
                      @mousedown.prevent="freezeImportInsertTargets"
                      @click="openImportAdviceDialog"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M3 4.2h6.2M3 7.2h6.2M3 10.2h4" />
                        <path d="M11.2 6.2v5.2M9.4 9.6l1.8 1.8 1.8-1.8" />
                      </svg>
                      带入医嘱
                    </button>
                    <button
                      v-if="!forceReadOnly"
                      type="button"
                      class="mrf-rib mrf-rib--accent"
                      :disabled="!canSaveAsTemplate || saveAsTemplateLoading"
                      @click="handleSaveAsTemplate"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M4 2.6h5.2L12.4 5.8v7.6H4z" />
                        <path d="M9.2 2.6v3.2h3.2M6.6 9.2h2.8M8 7.8v2.8" />
                      </svg>
                      存为模板
                    </button>
                    <button
                      v-if="!forceReadOnly"
                      type="button"
                      class="mrf-rib mrf-rib--primary"
                      :disabled="saveLoading"
                      @click="handleSave"
                    >
                      <svg class="mrf-ico" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M3.2 3.2h7.4l2.2 2.2v7.4H3.2z" />
                        <path d="M5.2 3.2v3.6h4.4M5.2 12.8v-2.6h5.6v2.6" />
                      </svg>
                      保存
                    </button>
                  </div>
                </div>
              </template>
            </CanvasStationEditor>
          </div>
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
      :loading="pdfLoading"
      @paper-change="onPreviewPaperChange"
    />
    <SaveAsEmrTemplateDialog
      ref="saveAsTemplateDialogRef"
      v-model:open="saveAsTemplateDialogOpen"
      @confirm="handleConfirmSaveAsTemplate"
    />
  </div>
  </Teleport>
</template>

