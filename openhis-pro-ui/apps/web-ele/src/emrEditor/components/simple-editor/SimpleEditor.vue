<script setup lang="ts">
import type { Editor, JSONContent } from '@tiptap/core';

import { type EmrEditorCaps, syncFillableIslandContentEditable } from './use-emr-editor-caps';

import {
  EMR_DIAGNOSIS_METADATA_FIELD_KEYS,
  isDocumentStampMetadataKey,
  resolveFillableMetadataKey,
  shouldForceOverwriteMetadataKey,
  type ApplyMetadataValuesOptions,
} from '#/emrEditor/emr-patient-metadata';
import type { EmrMetadataField } from '#/emrEditor/types';

import { probeEmrImageSrc } from '#/api/emr/editor-image';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

import {
  computed,
  getCurrentInstance,
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  provide,
  ref,
  watch,
} from 'vue';

import {
  ArrowDown,
  ArrowUp,
  Brush,
  Calendar,
  Checked,
  CircleCheck,
  Crop,
  Document,
  Expand,
  Fold,
  Grid,
  Link,
  List,
  Lock,
  Memo,
  Minus,
  Notebook,
  Operation,
  Picture,
  Printer,
  Refresh,
  RefreshLeft,
  RefreshRight,
  ScaleToOriginal,
  Search,
  Select,
  Switch,
  Ticket,
  Unlock,
} from '@element-plus/icons-vue';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';
import { EditorContent, useEditor } from '@tiptap/vue-3';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import EmrInsertWidgetDialog from './EmrInsertWidgetDialog.vue';
import type { EmrInsertWidgetResult } from './EmrInsertWidgetDialog.vue';
import EmrImageCropDialog from './EmrImageCropDialog.vue';
import {
  computeEmrImageSize,
  cropEmrImageSrc,
  resolveEmrImageAtPos,
  resolveEmrImagePosFromDom,
  rotateEmrImageSrcClockwise,
  type EmrImageCropRect,
  type EmrImageSelection,
} from './emr-image-ops';
import { type EmrDataElementWidget } from './data-element-types';
import {
  defaultEmrEditorDoc,
  DEFAULT_EDITOR_PAGE_MARGINS,
  describeEditorPageMargins,
  EDITOR_FONT_FAMILY_OPTIONS,
  EDITOR_FONT_SIZE_OPTIONS,
  EDITOR_LINE_HEIGHT_OPTIONS,
  EDITOR_PAGE_MARGIN_PRESETS,
  EDITOR_PAPER_SIZE_PRESETS,
  EDITOR_PARAGRAPH_SPACING_OPTIONS,
  EDITOR_TEXT_INDENT_OPTIONS,
  formatEditorMarginCm,
  matchEditorPageMarginPreset,
  type EditorPageMargins,
  type EditorPaperOrientation,
} from './editor-defaults';
import {
  clampCellMinHeightMm,
  EMR_MIN_CELL_HEIGHT_MM,
  pageContentHeightMm,
} from './extensions/emr-table-layout';
import {
  applyEmrPageChromeBandDomStyle,
  applyChromeSelectionTextStyle,
  buildChromeEditHtml,
  buildEmrPageChromeContext,
  cloneEmrPageChrome,
  createChromeFieldChip,
  createChromeImageEl,
  applyChromeImageWidth,
  applyChromeImagePosition,
  ensureChromeLogoRowLayout,
  readChromeImagePosition,
  resolveChromeImagePositionHost,
  syncChromeLineAfterLogoPadding,
  createDefaultEmrPageChrome,
  EMR_PAGE_CHROME_FIELDS,
  normalizeEmrPageChrome,
  placeCaretAfterChromeChip,
  resolveScreenChromeBandPaddingMm,
  serializeChromeEditHost,
  toggleChromeSelectionMark,
  type EmrPageChrome,
  type EmrPageChromeAlign,
  type EmrPageChromeBand,
} from './emr-page-chrome';
import { EMR_CONTENT_LOCK_BYPASS_META } from './extensions/emr-content-lock';
import { EMR_FIXED_TEXT_ALLOW_MUTATE_META } from './extensions/emr-fixed-text';
import {
  EMR_PAGE_CHROME_META,
  EMR_MEASURED_CHROME_META,
  assignChromeBandEditHtml,
  queryEmrChromeEditHost,
  requestEmrPaginationRelayout,
} from './extensions/emr-pagination';
import { EMR_SKIP_FILLABLE_RESTORE_META } from './extensions/emr-fillable-mark';
import EmrFindReplaceBar from './EmrFindReplaceBar.vue';
import { getSimpleEditorExtensions } from './get-simple-editor-extensions';
import { validateRequiredFillables } from './emr-required-validate';
import { readContentEditablePlainText } from './inline-contenteditable-caret';
import { useEmrAutoSave } from './use-emr-auto-save';
import EmrFillableShortcutRail from './node-views/EmrFillableShortcutRail.vue';
import { normalizeEmrRootDoc } from '../../document-content';
import {
  collectAllFillableHits,
  fillableCaretAfterDropCommand,
  liftPosOutOfFillableField,
  replaceFillableHitPlainText,
  resolveFillableAt,
  resolveInsertEmrMetadataChip,
  serializeFillableMarksToNodes,
  stripFillableZwsp,
} from '../../fillable-field';

import './simple-editor.scss';

defineOptions({
  name: 'SimpleEditor',
});

const props = withDefaults(
  defineProps<{
    /** 是否在挂载时自动恢复草稿，默认 true；弹窗场景建议 false 并手动调 checkDraft */
    autoRestoreDraft?: boolean;
    /** 自动保存防抖间隔（毫秒），0 则关闭自动保存，默认 3000 */
    autoSaveDebounceMs?: number;
    /** 文档唯一 ID，用于 IndexedDB 草稿主键；不传则关闭本地草稿 */
    docId?: string;
    /**
     * @deprecated 文书锁已下线；保留 prop 以免外部绑定报错，始终按未锁定处理。
     */
    layoutLocked?: boolean;
    /** 可拖拽字段定义（后续可由元数据配置接口下发） */
    metadataFields?: EmrMetadataField[];
    /** 字段当前值（后续对接患者信息等） */
    metadataValues?: Record<string, string>;
    /** v-model: 文档 JSON（Tiptap / ProseMirror） */
    modelValue?: JSONContent | null;
    /** A4 纸面页边距（CSS 长度值），默认 Word 标准页边距 */
    pageMargins?: {
      bottom?: string;
      left?: string;
      right?: string;
      top?: string;
    };
    /**
     * 页眉页脚（与 PrintConfig 同源）。不进正文 JSON；v-model:pageChrome。
     */
    pageChrome?: EmrPageChrome;
    /** 文书类型名，供页眉 `{documentTypeName}` */
    documentTypeName?: string;
    /** 文书标题，供页眉 `{documentTitle}` */
    documentTitle?: string;
    placeholder?: string;
    readOnly?: boolean;
    /** 防抖后写入服务端；与 docId 同时配置时先本地草稿再远程保存 */
    remoteSave?: (content: JSONContent) => Promise<void>;
    /** 自动/手动保存时远程写入失败 */
    onRemoteSaveError?: () => void;
    /**
     * template：制作模板（固定版式字灰底便于辨认）；
     * document：填写文书（固定字无背景，与正文一致）
     */
    surface?: 'document' | 'template';
    /**
     * 是否展示左侧元数据拖拽栏。
     * 默认：surface=template 时展示；医生站个人/科室模板维护可显式关闭。
     */
    showMetadataSidebar?: boolean;
    /** 是否显示排版工具栏；医生站书写病历时可关，模板制作保持默认 true */
    showToolbar?: boolean;
  }>(),
  {
    autoRestoreDraft: true,
    autoSaveDebounceMs: 3000,
    docId: '',
    metadataFields: () => [],
    metadataValues: () => ({}),
    modelValue: null,
    pageMargins: undefined,
    pageChrome: undefined,
    documentTypeName: '',
    documentTitle: '',
    placeholder: undefined,
    readOnly: false,
    surface: 'document',
    showMetadataSidebar: undefined,
    layoutLocked: false,
    showToolbar: true,
  },
);

const emit = defineEmits<{
  change: [];
  /** 工具栏「打印」：由宿主对接 OpenPDF 正式打印；未监听时回退 window.print */
  print: [];
  'update:layoutLocked': [boolean];
  'update:modelValue': [JSONContent | null];
  'update:pageMargins': [{ bottom: string; left: string; right: string; top: string }];
  'update:pageChrome': [EmrPageChrome];
}>();

function normalizeEditorPageMargins(raw?: {
  bottom?: string;
  left?: string;
  right?: string;
  top?: string;
}): EditorPageMargins {
  return {
    top: raw?.top?.trim() || DEFAULT_EDITOR_PAGE_MARGINS.top,
    right: raw?.right?.trim() || DEFAULT_EDITOR_PAGE_MARGINS.right,
    bottom: raw?.bottom?.trim() || DEFAULT_EDITOR_PAGE_MARGINS.bottom,
    left: raw?.left?.trim() || DEFAULT_EDITOR_PAGE_MARGINS.left,
  };
}

/** 屏上页边距真源：先写本地立刻改纸面，再同步宿主 v-model */
const localPageMargins = ref<EditorPageMargins>(normalizeEditorPageMargins(props.pageMargins));
const marginCustomVisible = ref(false);
const customMarginCm = ref({ bottom: 1.5, left: 1.5, right: 1.5, top: 1.5 });

const currentMarginPresetKey = computed(() => matchEditorPageMarginPreset(localPageMargins.value));

function pageMarginThumbStyle(value: EditorPageMargins): Record<string, string> {
  const parse = (raw: string, fallback: number) => {
    const m = raw.trim().match(/^([\d.]+)\s*mm$/i);
    const n = m ? Number(m[1]) : fallback;
    return Number.isFinite(n) ? n : fallback;
  };
  const top = parse(value.top, 15);
  const right = parse(value.right, 15);
  const bottom = parse(value.bottom, 15);
  const left = parse(value.left, 15);
  return {
    paddingTop: `${Math.max(8, (top / 297) * 100).toFixed(1)}%`,
    paddingRight: `${Math.max(8, (right / 210) * 100).toFixed(1)}%`,
    paddingBottom: `${Math.max(8, (bottom / 297) * 100).toFixed(1)}%`,
    paddingLeft: `${Math.max(8, (left / 210) * 100).toFixed(1)}%`,
  };
}

function commitPageMargins(next: EditorPageMargins) {
  if (!canChangeLayout.value) return;
  localPageMargins.value = { ...next };
  emit('update:pageMargins', { ...next });
  emit('change');
  autoSave?.markDirty();
  void nextTick(() => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    requestEmrPaginationRelayout(ed);
  });
}

function applyPageMarginPreset(preset: (typeof EDITOR_PAGE_MARGIN_PRESETS)[number]) {
  commitPageMargins({ ...preset.value });
}

function onPageMarginCommand(command: string | number | object) {
  if (command === 'custom') {
    const cur = localPageMargins.value;
    customMarginCm.value = {
      top: Number(formatEditorMarginCm(parseCssLengthMm(cur.top, 15))),
      right: Number(formatEditorMarginCm(parseCssLengthMm(cur.right, 15))),
      bottom: Number(formatEditorMarginCm(parseCssLengthMm(cur.bottom, 15))),
      left: Number(formatEditorMarginCm(parseCssLengthMm(cur.left, 15))),
    };
    marginCustomVisible.value = true;
    return;
  }
  const key = String(command);
  const preset = EDITOR_PAGE_MARGIN_PRESETS.find((p) => p.key === key);
  if (preset) applyPageMarginPreset(preset);
}

function applyCustomPageMargins() {
  const clampCm = (n: number) => Math.min(8, Math.max(0.5, Math.round(n * 100) / 100));
  const toMm = (cm: number) => `${Math.round(clampCm(cm) * 10 * 10) / 10}mm`;
  commitPageMargins({
    top: toMm(customMarginCm.value.top),
    right: toMm(customMarginCm.value.right),
    bottom: toMm(customMarginCm.value.bottom),
    left: toMm(customMarginCm.value.left),
  });
  marginCustomVisible.value = false;
}

/** 当前纸张规格（屏上预览；正式 PDF 仍走 PrintConfig） */
const paperSizeKey = ref<(typeof EDITOR_PAPER_SIZE_PRESETS)[number]['key']>('A4');
const paperOrientation = ref<EditorPaperOrientation>('portrait');
/** 标尺 / 网格线：仅编辑辅助，不进文档 JSON */
const showPageRuler = ref(false);
const showPageGridlines = ref(false);

const paperSizeLabel = computed(() => {
  const hit = EDITOR_PAPER_SIZE_PRESETS.find((p) => p.key === paperSizeKey.value);
  return hit?.key ?? 'A4';
});

const paperOrientationLabel = computed(() =>
  paperOrientation.value === 'landscape' ? '横向' : '纵向',
);

/** 解析当前纸宽/纸高（横向时对调） */
function resolvePaperCssSize(): { height: string; width: string } {
  const preset =
    EDITOR_PAPER_SIZE_PRESETS.find((p) => p.key === paperSizeKey.value) ??
    EDITOR_PAPER_SIZE_PRESETS[0]!;
  if (paperOrientation.value === 'landscape') {
    return { width: preset.height, height: preset.width };
  }
  return { width: preset.width, height: preset.height };
}

/** 切换纸张大小 */
function applyPaperSize(key: (typeof EDITOR_PAPER_SIZE_PRESETS)[number]['key']) {
  if (!canChangeLayout.value) return;
  paperSizeKey.value = key;
}

/** 切换纸张方向 */
function applyPaperOrientation(next: EditorPaperOrientation) {
  if (!canChangeLayout.value) return;
  paperOrientation.value = next;
}

/** 切换标尺显示 */
function togglePageRuler() {
  showPageRuler.value = !showPageRuler.value;
}

/** 切换网格线显示 */
function togglePageGridlines() {
  showPageGridlines.value = !showPageGridlines.value;
}

/** 插入分页符 */
function insertPageBreakFromRibbon() {
  if (!canChangeLayout.value) return;
  chain()?.insertEmrPageBreak().run();
}

/** 插入大纲（目录节点） */
function insertOutlineFromRibbon() {
  if (!canChangeLayout.value) return;
  chain()?.insertEmrToc().run();
}

/** 从「页面」页签插入页码：进入页脚编辑后写入常用页码文案 */
async function insertPageNumberFromRibbon(mode: 'field' | 'preset' = 'preset') {
  if (!canChangeLayout.value) return;
  const next = cloneEmrPageChrome(localPageChrome.value);
  if (!next.footer.enabled) {
    next.footer.enabled = true;
    commitPageChrome(next);
  }
  if (chromeEditingBand.value !== 'footer') {
    enterChromeEdit('footer');
    // enterChromeEdit 在 nextTick + rAF 里挂载可编辑区，需多等一帧
    await nextTick();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
  }
  if (mode === 'field') {
    insertChromeField('page');
    return;
  }
  insertChromePageNumberPreset();
}

/** 把页眉页脚配置写入分页插件，触发纸面重画 */
function syncPageChromeToEditor(ed?: Editor | null) {
  const target = ed ?? editor.value;
  if (!target || target.isDestroyed) return;
  const store = target.storage.emrPagination as
    | {
        chrome: EmrPageChrome;
        context: ReturnType<typeof buildEmrPageChromeContext>;
        editingBand: 'footer' | 'header' | null;
      }
    | undefined;
  if (!store) return;
  const nextChrome = cloneEmrPageChrome(localPageChrome.value);
  const typeName = (props.documentTypeName || '').trim();
  const title = (props.documentTitle || typeName || '').trim();
  const nextContext = buildEmrPageChromeContext({
    documentTitle: title,
    documentTypeName: typeName,
    metadataValues: {
      ...mergedMetaValues.value,
      ...(title ? { documentTitle: title } : {}),
      ...(typeName ? { documentTypeName: typeName } : {}),
    },
    orgName: resolvePrintHospitalName(),
  });
  const key = JSON.stringify({ c: nextChrome, x: nextContext });
  const prevKey = JSON.stringify({ c: store.chrome, x: store.context });
  if (key !== prevKey) {
    store.chrome = nextChrome;
    store.context = nextContext;
  }
  requestEmrPaginationRelayout(target);
}

const editorContentRef = ref<HTMLElement | null>(null);

type EditorScrollPos = { left: number; top: number };

function resolveEditorScrollEl(): HTMLElement | null {
  const fromRef = editorContentRef.value;
  if (fromRef instanceof HTMLElement) return fromRef;
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return null;
  const fromDom = ed.view.dom.closest('.simple-editor__content');
  return fromDom instanceof HTMLElement ? fromDom : null;
}

/** KeepAlive 会把节点挪到游离容器，此时 scrollTop 已被清 0，不能当有效视口 */
function isEditorScrollElLive(el: HTMLElement): boolean {
  if (!el.isConnected) return false;
  if (el.clientHeight < 8) return false;
  const pane = el.closest('.medical-record-form__editor-pane');
  if (pane && !pane.classList.contains('medical-record-form__editor-pane--active')) {
    return false;
  }
  const style = getComputedStyle(el);
  return style.display !== 'none' && style.visibility !== 'hidden';
}

let rememberedEditorScroll: EditorScrollPos = { left: 0, top: 0 };
let restoringEditorScroll = false;
let editorScrollTarget: HTMLElement | null = null;
let editorScrollPollTimer: number | undefined;
let editorScrollRestoreTimers: number[] = [];

function snapshotEditorScroll() {
  if (restoringEditorScroll) return;
  const el = resolveEditorScrollEl();
  if (!el || !isEditorScrollElLive(el)) return;
  rememberedEditorScroll = { left: el.scrollLeft, top: el.scrollTop };
}

function onEditorScrollElScroll() {
  snapshotEditorScroll();
}

function unbindEditorScrollMemory() {
  if (editorScrollTarget) {
    editorScrollTarget.removeEventListener('scroll', onEditorScrollElScroll);
    editorScrollTarget = null;
  }
  if (editorScrollPollTimer != null) {
    window.clearInterval(editorScrollPollTimer);
    editorScrollPollTimer = undefined;
  }
}

function bindEditorScrollMemory() {
  const el = resolveEditorScrollEl();
  if (!el) return;
  if (el === editorScrollTarget) return;
  unbindEditorScrollMemory();
  editorScrollTarget = el;
  el.addEventListener('scroll', onEditorScrollElScroll, { passive: true });
  editorScrollPollTimer = window.setInterval(snapshotEditorScroll, 250);
}

function captureEditorScroll(): EditorScrollPos {
  snapshotEditorScroll();
  return { ...rememberedEditorScroll };
}

function restoreEditorScroll(pos?: EditorScrollPos | null) {
  const target = pos ?? rememberedEditorScroll;
  rememberedEditorScroll = { left: target.left, top: target.top };
  const apply = () => {
    bindEditorScrollMemory();
    const el = resolveEditorScrollEl();
    if (!el) return;
    restoringEditorScroll = true;
    el.scrollTop = target.top;
    el.scrollLeft = target.left;
    window.requestAnimationFrame(() => {
      restoringEditorScroll = false;
    });
  };
  for (const t of editorScrollRestoreTimers) window.clearTimeout(t);
  editorScrollRestoreTimers = [];
  apply();
  void nextTick(apply);
  requestAnimationFrame(() => {
    apply();
    editorScrollRestoreTimers.push(
      window.setTimeout(apply, 80),
      window.setTimeout(apply, 260),
      window.setTimeout(apply, 620),
      window.setTimeout(apply, 1200),
    );
  });
}

/** 冷加载/只读查看：内容与 NodeView 落稳后再排，避免查看页页眉重叠 */
let settledRelayoutTimers: number[] = [];
function scheduleSettledPaginationRelayout() {
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return;
  for (const t of settledRelayoutTimers) window.clearTimeout(t);
  settledRelayoutTimers = [];
  const run = () => {
    const cur = editor.value;
    if (!cur || cur.isDestroyed) return;
    requestEmrPaginationRelayout(cur);
    restoreEditorScroll(rememberedEditorScroll);
  };
  run();
  void nextTick(() => {
    run();
    requestAnimationFrame(() => {
      run();
      settledRelayoutTimers.push(
        window.setTimeout(run, 80),
        window.setTimeout(run, 250),
        window.setTimeout(run, 600),
      );
    });
  });
  if (typeof document !== 'undefined' && document.fonts?.ready) {
    void document.fonts.ready.then(run);
  }
}

/** 提交页眉页脚变更（模板可改；文书态只读） */
function commitPageChrome(next: EmrPageChrome) {
  localPageChrome.value = cloneEmrPageChrome(next);
  emit('update:pageChrome', cloneEmrPageChrome(next));
  syncPageChromeToEditor();
}

function closeCtxMenu(event?: Event) {
  if (event?.target instanceof Element && event.target.closest('.simple-editor__ctx-menu')) {
    return;
  }
  ctxMenu.value.visible = false;
}

/** 右键：弹出精简菜单，不覆盖顶栏全部能力 */
function onEditorContextMenu(event: MouseEvent) {
  if (props.readOnly) return;
  event.preventDefault();
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return;
  const coords = ed.view.posAtCoords({ left: event.clientX, top: event.clientY });
  const pos = coords?.pos ?? ed.state.selection.head;
  const $pos = ed.state.doc.resolve(Math.min(pos, ed.state.doc.content.size));
  let inTable = false;
  for (let d = $pos.depth; d > 0; d--) {
    if ($pos.node(d).type.name === 'table') {
      inTable = true;
      break;
    }
  }
  // 点在图片上：记下位置供右键改大小/旋转等（不依赖浮层工具条）
  let inImage = false;
  let imgPos: null | number = null;
  const fromDom = resolveEmrImagePosFromDom(ed, event.target, event.clientX, event.clientY);
  if (fromDom >= 0) {
    imgPos = fromDom;
  } else {
    const nodeAfter = $pos.nodeAfter;
    const nodeBefore = $pos.nodeBefore;
    if (nodeAfter?.type.name === 'image') {
      imgPos = $pos.pos;
    } else if (nodeBefore?.type.name === 'image') {
      imgPos = $pos.pos - nodeBefore.nodeSize;
    }
  }
  if (imgPos != null && canChangeLayout.value) {
    inImage = true;
    try {
      ed.chain().setNodeSelection(imgPos).run();
    } catch {
      /* 选区失败仍可用 imagePos 做右键操作 */
    }
  } else if (inTable) {
    // 点在表内时把选区落到点击格，后续插行/行高才对得上右键那一行
    try {
      ed.chain().setTextSelection(pos).run();
    } catch {
      /* 选区失败仍按点击位置出表菜单 */
    }
  }
  ctxMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    inTable,
    inFillable: Boolean(resolveFillableAt(ed.state.doc, pos)),
    inImage,
    imagePos: imgPos,
    inChromeImage: false,
  };
}

/** 插入纯文本（文书锁已下线，直接写入光标处） */
function insertPlainText(text: string) {
  const ed = editor.value;
  if (!ed || ed.isDestroyed || props.readOnly || !text) return;
  ed.chain().focus().insertContent(text).run();
}

/** 插入当前日期 / 时间 / 日期时间 */
function insertDateTime(kind: 'date' | 'datetime' | 'time') {
  const now = dayjs();
  const text =
    kind === 'date'
      ? now.format('YYYY-MM-DD')
      : kind === 'time'
        ? now.format('HH:mm:ss')
        : now.format('YYYY-MM-DD HH:mm:ss');
  insertPlainText(text);
  closeCtxMenu();
}

function getPaginationStore() {
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return null;
  return ed.storage.emrPagination as
    | {
        chrome: EmrPageChrome;
        context: ReturnType<typeof buildEmrPageChromeContext>;
        editingBand: 'footer' | 'header' | null;
      }
    | undefined;
}

/** 取当前正在编辑的首页页眉/页脚内容区（inner，非外壳） */
function getChromeEditHost(): HTMLElement | null {
  const ed = editor.value;
  const band = chromeEditingBand.value;
  if (!ed || ed.isDestroyed || !band) return null;
  return queryEmrChromeEditHost(ed.view.dom, band);
}

/**
 * 选系统文件框时，关闭对话框后的点击常落到正文，会误触发 exitChromeEdit。
 * 选图期间屏蔽 dismiss。
 */
let chromeFilePickGuardUntil = 0;

function armChromeFilePickGuard() {
  chromeFilePickGuardUntil = Date.now() + 2500;
}

function isChromeFilePickGuarded(): boolean {
  return Date.now() < chromeFilePickGuardUntil;
}

/** 正在主动退出页眉编辑：允许 blur，勿抢回焦点 */
let chromeExiting = false;

/** 页眉编辑区最近一次有效选区，用于单击外部后恢复光标 */
let chromeSavedRange: Range | null = null;

const CHROME_FOCUS_SAFE_SEL =
  '[data-emr-chrome], .simple-editor__chrome-bar, .simple-editor__toolbar, .simple-editor__sidebar, .simple-editor__file, .simple-editor__ctx-menu, .emr-find-replace-bar, .el-dropdown-menu, .el-popper, .el-message-box, .el-overlay, .el-select-dropdown';

function isChromeFocusSafeTarget(t: EventTarget | null): boolean {
  return t instanceof Element && Boolean(t.closest(CHROME_FOCUS_SAFE_SEL));
}

function saveChromeCaret() {
  if (!chromeEditingBand.value || chromeExiting) return;
  const host = chromeHostInputEl;
  const sel = window.getSelection();
  if (!host || !sel || sel.rangeCount === 0) return;
  const range = sel.getRangeAt(0);
  if (!host.contains(range.commonAncestorContainer)) return;
  // 点工具栏字号/字体时下拉抢焦会折叠选区，勿用折叠选区覆盖有效文字选区
  if (range.collapsed && chromeSavedRange && !chromeSavedRange.collapsed) {
    try {
      if (host.contains(chromeSavedRange.commonAncestorContainer)) return;
    } catch {
      chromeSavedRange = null;
    }
  }
  try {
    chromeSavedRange = range.cloneRange();
  } catch {
    chromeSavedRange = null;
  }
}

/** 应用页眉局部格式前：恢复工具栏操作前保存的文字选区 */
function prepareChromeFormatSelection(host: HTMLElement): boolean {
  host.focus({ preventScroll: true });
  const sel = window.getSelection();
  if (!sel) return false;
  if (sel.rangeCount > 0) {
    const live = sel.getRangeAt(0);
    if (!live.collapsed && host.contains(live.commonAncestorContainer)) {
      chromeSavedRange = live.cloneRange();
      return true;
    }
  }
  if (chromeSavedRange && !chromeSavedRange.collapsed) {
    try {
      if (host.contains(chromeSavedRange.commonAncestorContainer)) {
        sel.removeAllRanges();
        sel.addRange(chromeSavedRange);
        return true;
      }
    } catch {
      chromeSavedRange = null;
    }
  }
  return false;
}

function restoreChromeCaret() {
  const host = getChromeEditHost() ?? chromeHostInputEl;
  if (!host) return;
  // 分页重绘可能换了 DOM 节点，重新绑监听并保持可编辑
  if (chromeHostInputEl !== host) {
    host.contentEditable = 'true';
    bindChromeHostListeners(host);
  }
  host.focus({ preventScroll: true });
  const sel = window.getSelection();
  if (!sel) return;
  if (chromeSavedRange) {
    try {
      if (host.contains(chromeSavedRange.commonAncestorContainer)) {
        sel.removeAllRanges();
        sel.addRange(chromeSavedRange);
        return;
      }
    } catch {
      // DOM 重建后 Range 失效
    }
  }
  const range = document.createRange();
  range.selectNodeContents(host);
  range.collapse(false);
  sel.removeAllRanges();
  sel.addRange(range);
  chromeSavedRange = range.cloneRange();
}

/**
 * 单击页眉外部纸面/滚动区：不退出、不失焦（对齐 Word P0-4）。
 * 工具栏/侧栏等安全区允许聚焦，但仍保持页眉编辑态。
 */
function onChromeEditOutsideMouseDown(event: MouseEvent) {
  if (!chromeEditingBand.value || chromeExiting) return;
  if (isChromeFilePickGuarded()) return;
  if (!(event.target instanceof Element)) return;
  if (isChromeFocusSafeTarget(event.target)) {
    saveChromeCaret();
    return;
  }
  if (event.target.closest('[data-emr-chrome]')) return;
  // 正文 ProseMirror 单击不抢事件（分页后须能点进第一页；双击仍走退出页眉）
  if (event.target.closest('.ProseMirror')) return;
  if (
    !event.target.closest(
      '.simple-editor__content, .simple-editor__papers, .simple-editor__body, .simple-editor',
    )
  ) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  restoreChromeCaret();
}

/** 双击正文/纸面外部：退出页眉页脚编辑（对齐 Word） */
function onChromeEditOutsideDblClick(event: MouseEvent) {
  if (!chromeEditingBand.value || chromeExiting) return;
  if (isChromeFilePickGuarded()) return;
  if (!(event.target instanceof Element)) return;
  if (isChromeFocusSafeTarget(event.target)) return;
  if (event.target.closest('[data-emr-chrome]')) return;
  if (
    !event.target.closest(
      '.simple-editor__content, .simple-editor__papers, .simple-editor__body, .simple-editor',
    )
  ) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  exitChromeEdit();
}

function onChromeHostBlur() {
  if (!chromeEditingBand.value || chromeExiting) return;
  saveChromeCaret();
  requestAnimationFrame(() => {
    if (!chromeEditingBand.value || chromeExiting) return;
    const ae = document.activeElement;
    if (isChromeFocusSafeTarget(ae)) return;
    if (ae instanceof Element && !ae.closest('.simple-editor')) return;
    restoreChromeCaret();
  });
}

function attachChromeFocusGuards(host: HTMLElement) {
  document.addEventListener('mousedown', onChromeEditOutsideMouseDown, true);
  document.addEventListener('dblclick', onChromeEditOutsideDblClick, true);
  document.addEventListener('selectionchange', saveChromeCaret);
  host.addEventListener('blur', onChromeHostBlur);
}

function detachChromeFocusGuards(host: HTMLElement | null) {
  document.removeEventListener('mousedown', onChromeEditOutsideMouseDown, true);
  document.removeEventListener('dblclick', onChromeEditOutsideDblClick, true);
  document.removeEventListener('selectionchange', saveChromeCaret);
  host?.removeEventListener('blur', onChromeHostBlur);
}

/** 把纸面编辑区立刻写回 pageChrome 模板（避免选图后 DOM 被重建丢图） */
function persistChromeHostToTemplate() {
  const band = chromeEditingBand.value;
  const host = getChromeEditHost() ?? chromeHostInputEl;
  if (!band || !host) return;
  const template = serializeChromeEditHost(host);
  const next = cloneEmrPageChrome(localPageChrome.value);
  if (next[band].template === template) return;
  next[band].template = template;
  commitPageChrome(next);
}

function detachChromeHostListeners() {
  detachChromeFocusGuards(chromeHostInputEl);
  cancelChromeImgPointerDrag();
  if (!chromeHostInputEl) return;
  chromeHostInputEl.removeEventListener('input', onChromeHostInput);
  chromeHostInputEl.removeEventListener('pointerdown', onChromeHostPointerDown);
  chromeHostInputEl.removeEventListener('contextmenu', onChromeHostContextMenu);
  chromeHostInputEl.removeEventListener('dragover', onChromeHostDragOver);
  chromeHostInputEl.removeEventListener('drop', onChromeHostDrop);
  clearChromeImgSelection();
  chromeHostInputEl = null;
}

/** 绑定页眉可编辑区 DOM 监听（含防失焦） */
function bindChromeHostListeners(host: HTMLElement) {
  detachChromeHostListeners();
  chromeHostInputEl = host;
  host.addEventListener('input', onChromeHostInput);
  host.addEventListener('pointerdown', onChromeHostPointerDown);
  host.addEventListener('contextmenu', onChromeHostContextMenu);
  host.addEventListener('dragover', onChromeHostDragOver);
  host.addEventListener('drop', onChromeHostDrop);
  attachChromeFocusGuards(host);
}

/** 清除页眉图片选中态 */
function clearChromeImgSelection() {
  chromeHostInputEl
    ?.querySelectorAll('img.simple-editor__chrome-img--selected')
    .forEach((el) => el.classList.remove('simple-editor__chrome-img--selected'));
  chromeSelectedImgEl = null;
  chromeImgWidth.value = undefined;
  chromeImgPosX.value = undefined;
  chromeImgPosY.value = undefined;
}

/** 量内容真实高度：先去掉 min-height，避免被边距撑高后反馈膨胀 */
function measureChromeHostContentHeightPx(host: HTMLElement): number {
  const prevMin = host.style.minHeight;
  const prevMax = host.style.maxHeight;
  const prevHeight = host.style.height;
  host.style.minHeight = '0px';
  host.style.maxHeight = 'none';
  host.style.height = 'auto';
  const h = Math.max(host.scrollHeight, 28);
  host.style.minHeight = prevMin;
  host.style.maxHeight = prevMax;
  host.style.height = prevHeight;
  return h;
}

/** 含页眉/页脚外壳 padding 的总高度（退出编辑记 heightMm 用） */
function measureChromeBandShellHeightPx(host: HTMLElement): number {
  const innerH = measureChromeHostContentHeightPx(host);
  const shell = host.parentElement;
  if (!(shell instanceof HTMLElement)) return innerH;
  const cs = getComputedStyle(shell);
  const pad = (Number.parseFloat(cs.paddingTop) || 0) + (Number.parseFloat(cs.paddingBottom) || 0);
  return innerH + pad;
}

/** 按编辑区实际高度撑开纸张上/下边距；仅超出页边距时才撑开，短页眉不把正文往上拽 */
function syncChromeEditBandLayout() {
  const host = getChromeEditHost();
  const band = chromeEditingBand.value;
  if (!host || !band) return;
  const hPx = measureChromeBandShellHeightPx(host);
  const m = localPageMargins.value;
  const baseMm = band === 'header' ? parseCssLengthMm(m.top, 15) : parseCssLengthMm(m.bottom, 15);
  const basePx = (baseMm * 96) / 25.4;
  // 不低于工具栏页边距，避免编辑时正文上移、退出后又拉开
  const usePx = Math.max(Math.ceil(hPx), Math.ceil(basePx));
  const cssPx = `${usePx}px`;
  const prev = chromeMarginOverride.value;
  if (band === 'header') {
    if (prev?.top === cssPx) return;
    chromeMarginOverride.value = {
      top: cssPx,
      bottom: prev?.bottom,
    };
  } else if (prev?.bottom === cssPx) {
    return;
  } else {
    chromeMarginOverride.value = {
      top: prev?.top,
      bottom: cssPx,
    };
  }
  // 勿 dispatch ProseMirror：每次输入重画纸面会抢焦点，导致插字段后无法继续打字
}

function onChromeHostInput() {
  requestAnimationFrame(() => syncChromeEditBandLayout());
}

/** 选中页眉/页脚图片，供顶栏改宽、改位置与右键操作 */
function selectChromeImage(img: HTMLImageElement) {
  chromeHostInputEl
    ?.querySelectorAll('img.simple-editor__chrome-img--selected')
    .forEach((el) => el.classList.remove('simple-editor__chrome-img--selected'));
  img.classList.add('simple-editor__chrome-img--selected');
  chromeSelectedImgEl = img;
  const w = img.getAttribute('width') || img.style.width;
  const n = Number.parseFloat(String(w));
  chromeImgWidth.value =
    Number.isFinite(n) && n > 0 ? Math.round(n) : Math.round(img.clientWidth || 80);
  const bandHost = chromeHostInputEl;
  if (bandHost) {
    const posHost = resolveChromeImagePositionHost(img, bandHost);
    ensureChromeLogoRowLayout(posHost, img);
    const pos = readChromeImagePosition(img, posHost);
    chromeImgPosX.value = pos.x;
    chromeImgPosY.value = pos.y;
    syncChromeLineAfterLogoPadding(posHost, img);
  }
}

/** 点字段片时把光标放到片后；点图片时选中并可组内拖拽 */
function onChromeHostPointerDown(event: PointerEvent) {
  const host = chromeHostInputEl;
  if (!host) return;
  const target = event.target;
  if (!(target instanceof Element)) return;
  const img = target.closest('img.simple-editor__chrome-img');
  if (img instanceof HTMLImageElement && host.contains(img)) {
    event.preventDefault();
    event.stopPropagation();
    selectChromeImage(img);
    beginChromeImgPointerDrag(img, resolveChromeImagePositionHost(img, host), event);
    return;
  }
  clearChromeImgSelection();
  const chip = target.closest('.simple-editor__chrome-field');
  if (!chip || !host.contains(chip)) return;
  event.preventDefault();
  host.focus();
  placeCaretAfterChromeChip(chip, host);
}

/** 页眉图片右键：调整大小 / 删除 */
function onChromeHostContextMenu(event: MouseEvent) {
  if (!canChangeLayout.value) return;
  const host = chromeHostInputEl;
  if (!host) return;
  const target = event.target;
  if (!(target instanceof Element)) return;
  const img = target.closest('img.simple-editor__chrome-img');
  if (!(img instanceof HTMLImageElement) || !host.contains(img)) return;
  event.preventDefault();
  event.stopPropagation();
  selectChromeImage(img);
  ctxMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    inTable: false,
    inFillable: false,
    inImage: false,
    imagePos: null,
    inChromeImage: true,
  };
}

/** 页眉选中图片改宽度 */
function onChromeImgWidthChange(width: number | undefined) {
  if (!chromeSelectedImgEl || width == null || !Number.isFinite(width)) return;
  applyChromeImageWidth(chromeSelectedImgEl, width);
  const bandHost = chromeHostInputEl;
  if (bandHost) {
    syncChromeLineAfterLogoPadding(
      resolveChromeImagePositionHost(chromeSelectedImgEl, bandHost),
      chromeSelectedImgEl,
    );
  }
  persistChromeHostToTemplate();
  requestAnimationFrame(() => syncChromeEditBandLayout());
}

function applySelectedChromeImgPosition(x: number, y: number) {
  if (!chromeSelectedImgEl || !chromeHostInputEl) return;
  const posHost = resolveChromeImagePositionHost(chromeSelectedImgEl, chromeHostInputEl);
  applyChromeImagePosition(chromeSelectedImgEl, posHost, x, y);
}

/** 页眉选中图片改水平位置（组内 left px） */
function onChromeImgPosXChange(x: number | undefined) {
  if (!chromeSelectedImgEl || x == null || !Number.isFinite(x)) return;
  applySelectedChromeImgPosition(x, chromeImgPosY.value ?? 0);
  persistChromeHostToTemplate();
}

/** 页眉选中图片改垂直位置（组内 top px） */
function onChromeImgPosYChange(y: number | undefined) {
  if (!chromeSelectedImgEl || y == null || !Number.isFinite(y)) return;
  applySelectedChromeImgPosition(chromeImgPosX.value ?? 0, y);
  persistChromeHostToTemplate();
}

/** 右键：调整页眉图片宽度 */
async function ctxResizeChromeImage() {
  const img = chromeSelectedImgEl;
  closeCtxMenu();
  if (!img || !chromeHostInputEl?.contains(img)) return;
  const parsedW = Number.parseFloat(img.getAttribute('width') || img.style.width);
  const curW =
    chromeImgWidth.value ??
    (Number.isFinite(parsedW) && parsedW > 0 ? parsedW : Math.round(img.clientWidth || 80));
  try {
    const { value } = await ElMessageBox.prompt('图片宽度（像素）', '调整页眉图片大小', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: String(Math.round(curW)),
      inputPattern: /^[1-9]\d{0,3}$/,
      inputErrorMessage: '请输入 1～9999 的整数',
    });
    const width = Number(value);
    if (!Number.isFinite(width) || width < 16) {
      ElMessage.warning('宽度无效');
      return;
    }
    applyChromeImageWidth(img, width);
    chromeImgWidth.value = Math.round(width);
    selectChromeImage(img);
    persistChromeHostToTemplate();
    requestAnimationFrame(() => syncChromeEditBandLayout());
  } catch {
    /* 取消 */
  }
}

/** 右键：删除页眉图片 */
function ctxDeleteChromeImage() {
  const img = chromeSelectedImgEl;
  closeCtxMenu();
  if (!img || !chromeHostInputEl?.contains(img)) return;
  img.remove();
  clearChromeImgSelection();
  persistChromeHostToTemplate();
  onChromeHostInput();
}

/** 页眉字段片显示名：侧栏元数据优先 */
function chromeLabelOf(key: string): string | undefined {
  const hit = (props.metadataFields ?? []).find((f) => f.fieldKey === key);
  return hit?.label?.trim() || undefined;
}

/** 把选区落到页眉编辑区某点（拖放落点） */
function placeChromeCaretAtPoint(host: HTMLElement, clientX: number, clientY: number) {
  const sel = window.getSelection();
  if (!sel) return;
  const doc = document as Document & {
    caretPositionFromPoint?: (x: number, y: number) => { offset: number; offsetNode: Node } | null;
    caretRangeFromPoint?: (x: number, y: number) => Range | null;
  };
  let range: Range | null = null;
  if (typeof doc.caretRangeFromPoint === 'function') {
    range = doc.caretRangeFromPoint(clientX, clientY);
  } else if (typeof doc.caretPositionFromPoint === 'function') {
    const pos = doc.caretPositionFromPoint(clientX, clientY);
    if (pos) {
      range = document.createRange();
      range.setStart(pos.offsetNode, pos.offset);
      range.collapse(true);
    }
  }
  if (!range || !host.contains(range.startContainer)) {
    range = document.createRange();
    range.selectNodeContents(host);
    range.collapse(false);
  }
  // 点到字段片上时抬到片后，避免光标困在 contenteditable=false 里
  const startEl =
    range.startContainer instanceof Element
      ? range.startContainer
      : range.startContainer.parentElement;
  const chip = startEl?.closest?.('.simple-editor__chrome-field');
  if (chip && host.contains(chip)) {
    placeCaretAfterChromeChip(chip, host);
    return;
  }
  sel.removeAllRanges();
  sel.addRange(range);
}

function onChromeHostDragOver(event: DragEvent) {
  const types = event.dataTransfer ? Array.from(event.dataTransfer.types) : [];
  if (!types.includes('application/emr-metadata')) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
}

function onChromeHostDrop(event: DragEvent) {
  if (!chromeEditingBand.value || !canChangeLayout.value) return;
  const raw = event.dataTransfer?.getData('application/emr-metadata');
  if (!raw) return;
  event.preventDefault();
  event.stopPropagation();
  try {
    const payload = JSON.parse(raw) as { fieldKey?: string; label?: string };
    if (!payload?.fieldKey) return;
    const host = getChromeEditHost();
    if (host) placeChromeCaretAtPoint(host, event.clientX, event.clientY);
    insertChromeField(payload.fieldKey, payload.label);
  } catch {
    /* ignore bad payload */
  }
}

function pxToMm(px: number): number {
  return Math.max(8, Math.round((px * 25.4) / 96));
}

/** 进入 Word 式页眉/页脚编辑：纸面原地改，不再弹占位符输入框 */
function enterChromeEdit(band: 'footer' | 'header') {
  if (!canChangeLayout.value) return;
  if (chromeEditingBand.value && chromeEditingBand.value !== band) {
    exitChromeEdit();
  }
  const next = cloneEmrPageChrome(localPageChrome.value);
  next[band].enabled = true;
  commitPageChrome(next);
  chromeEditingBand.value = band;
  ribbonTab.value = 'home';
  ribbonExpanded.value = true;
  const store = getPaginationStore();
  if (store) store.editingBand = band;
  const ed = editor.value;
  if (ed && !ed.isDestroyed) {
    const tr = ed.state.tr.setMeta(EMR_PAGE_CHROME_META, true);
    tr.setMeta('addToHistory', false);
    ed.view.dispatch(tr);
  }
  void nextTick(() => {
    requestAnimationFrame(() => {
      const edNow = editor.value;
      if (!edNow || edNow.isDestroyed || chromeEditingBand.value !== band) return;
      const host = queryEmrChromeEditHost(edNow.view.dom, band);
      if (!host) return;
      assignChromeBandEditHtml(
        host,
        buildChromeEditHtml(localPageChrome.value[band].template, chromeLabelOf),
      );
      chromeExiting = false;
      bindChromeHostListeners(host);
      host.focus();
      // 防止 ProseMirror 抢 tabindex 焦点
      edNow.view.dom.setAttribute('tabindex', '-1');
      syncChromeEditBandLayout();
      const last = host.lastChild;
      if (last instanceof HTMLElement && last.dataset.chromeField) {
        placeCaretAfterChromeChip(last, host);
        saveChromeCaret();
        return;
      }
      // 末行是包着字段片的 div 时，也落到可输入处
      if (last instanceof HTMLElement && (last.tagName === 'DIV' || last.tagName === 'P')) {
        const chip = last.querySelector('.simple-editor__chrome-field:last-of-type');
        if (chip) {
          placeCaretAfterChromeChip(chip, host);
          saveChromeCaret();
          return;
        }
      }
      const sel = window.getSelection();
      if (!sel) return;
      const range = document.createRange();
      range.selectNodeContents(host);
      range.collapse(false);
      sel.removeAllRanges();
      sel.addRange(range);
      saveChromeCaret();
    });
  });
}

/** 把纸面编辑区写回模板并退出 */
function exitChromeEdit() {
  const band = chromeEditingBand.value;
  if (!band) return;
  chromeExiting = true;
  const host = getChromeEditHost();
  const template = host ? serializeChromeEditHost(host) : localPageChrome.value[band].template;
  let heightMm = localPageChrome.value[band].heightMm;
  if (host) {
    // 按真实内容（含外壳 padding）记高度，避免退出后 overflow 裁切
    const contentMm = pxToMm(measureChromeBandShellHeightPx(host));
    const baseMm =
      band === 'header'
        ? parseCssLengthMm(localPageMargins.value.top, 15)
        : parseCssLengthMm(localPageMargins.value.bottom, 15);
    heightMm = Math.max(contentMm, baseMm);
    // 立刻退出 contentEditable，避免等重绘前抢焦点
    host.contentEditable = 'false';
    host.parentElement?.classList.remove('simple-editor__page-band--editing');
  }
  detachChromeHostListeners();
  chromeSavedRange = null;
  const store = getPaginationStore();
  if (store) store.editingBand = null;
  chromeEditingBand.value = null;
  chromeMarginOverride.value = null;
  const next = cloneEmrPageChrome(localPageChrome.value);
  next[band].template = template;
  next[band].heightMm = heightMm;
  commitPageChrome(next);
  // 配置未变时 sync 会短路；仍强制重画并收回正文焦点
  const ed = editor.value;
  if (ed && !ed.isDestroyed) {
    ed.view.dom.setAttribute('tabindex', '0');
    const tr = ed.state.tr.setMeta(EMR_PAGE_CHROME_META, true);
    tr.setMeta('addToHistory', false);
    ed.view.dispatch(tr);
    void nextTick(() => {
      chromeExiting = false;
      if (!editor.value || editor.value.isDestroyed) return;
      editor.value.view.focus();
    });
  } else {
    chromeExiting = false;
  }
}

/** 在页眉/页脚光标处插入字段片（侧栏元数据或内置页码等） */
function insertChromeField(key: string, label?: string) {
  const host = getChromeEditHost();
  if (!host) return;
  host.focus();
  const chip = createChromeFieldChip(key, label ?? chromeLabelOf(key));
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || !host.contains(sel.anchorNode)) {
    host.appendChild(chip);
    placeCaretAfterChromeChip(chip, host);
    onChromeHostInput();
    return;
  }
  const range = sel.getRangeAt(0);
  // 选区若落在字段片内部，抬到片后，避免嵌进不可编辑节点
  const anchorEl =
    sel.anchorNode instanceof Element ? sel.anchorNode : sel.anchorNode?.parentElement;
  const inChip = anchorEl?.closest?.('.simple-editor__chrome-field');
  if (inChip && host.contains(inChip)) {
    range.setStartAfter(inChip);
    range.collapse(true);
  }
  range.deleteContents();
  range.insertNode(chip);
  placeCaretAfterChromeChip(chip, host);
  onChromeHostInput();
}

/** 在页眉/页脚光标处插入图片（写入模板 `{{emrImg:...}}`，不进正文 JSON） */
function insertChromeImage(src: string) {
  const host = getChromeEditHost();
  if (!host) {
    ElMessage.warning('页眉编辑区未就绪，请双击页眉后再试');
    return;
  }
  const url = src.trim();
  if (!url || url.includes('}')) {
    ElMessage.warning('图片地址无效，无法写入页眉');
    return;
  }
  // 纸面可能重建过：监听器绑到旧 host 时，改绑到当前 host
  if (chromeHostInputEl !== host) {
    host.contentEditable = 'true';
    bindChromeHostListeners(host);
  }
  host.focus();
  // 默认 80px，避免被 CSS 未设宽上限卡住；之后可右键/顶栏改宽
  const img = createChromeImageEl(url, 80);
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || !host.contains(sel.anchorNode)) {
    host.appendChild(img);
    placeCaretAfterChromeChip(img, host);
    selectChromeImage(img);
    onChromeHostInput();
    persistChromeHostToTemplate();
    return;
  }
  const range = sel.getRangeAt(0);
  const anchorEl =
    sel.anchorNode instanceof Element ? sel.anchorNode : sel.anchorNode?.parentElement;
  const inChip = anchorEl?.closest?.('.simple-editor__chrome-field');
  if (inChip && host.contains(inChip)) {
    range.setStartAfter(inChip);
    range.collapse(true);
  }
  range.deleteContents();
  range.insertNode(img);
  placeCaretAfterChromeChip(img, host);
  selectChromeImage(img);
  onChromeHostInput();
  persistChromeHostToTemplate();
}

/** 插入图片：编辑页眉/页脚时写入纸面边距带；否则写入正文 */
function insertEditorOrChromeImage(src: string) {
  if (chromeEditingBand.value) {
    insertChromeImage(src);
    return;
  }
  editor.value?.chain().focus().setImage({ src }).run();
}

/**
 * 拖到页眉/页脚边距，或侧栏点击（编辑页眉中）：写入页眉字段片。
 * 返回 true 表示已处理，勿再插正文。
 */
function tryInsertMetadataIntoChrome(
  fieldKey: string,
  label: string,
  clientX?: number,
  clientY?: number,
): boolean {
  if (!canChangeLayout.value) return false;
  let band = chromeEditingBand.value;
  if (!band && clientX != null && clientY != null) {
    band = hitTestChromeBandAtPoint(clientX, clientY);
  }
  if (!band) return false;
  if (chromeEditingBand.value !== band) {
    enterChromeEdit(band);
    void nextTick(() => {
      requestAnimationFrame(() => {
        if (chromeEditingBand.value !== band) return;
        insertChromeField(fieldKey, label);
      });
    });
    return true;
  }
  if (clientX != null && clientY != null) {
    const host = getChromeEditHost();
    if (host) placeChromeCaretAtPoint(host, clientX, clientY);
  }
  insertChromeField(fieldKey, label);
  return true;
}

/** 插入 Word 常用页码文案：第 {page} 页 (共 {total} 页) */
function insertChromePageNumberPreset() {
  const host = getChromeEditHost();
  if (!host) return;
  host.focus();
  const frag = document.createDocumentFragment();
  frag.append('第 ');
  const pageChip = createChromeFieldChip('page');
  frag.append(pageChip);
  frag.append(' 页 (共 ');
  const totalChip = createChromeFieldChip('total');
  frag.append(totalChip);
  frag.append(' 页)');
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || !host.contains(sel.anchorNode)) {
    host.appendChild(frag);
    placeCaretAfterChromeChip(totalChip, host);
    onChromeHostInput();
    return;
  }
  const range = sel.getRangeAt(0);
  range.deleteContents();
  range.insertNode(frag);
  placeCaretAfterChromeChip(totalChip, host);
  onChromeHostInput();
}

/** 页眉/页脚左中右对齐（仅编辑器纸面；PDF 仍居中直至 PrintConfig 同步） */
function setChromeAlign(align: EmrPageChromeAlign) {
  patchChromeBand({ align });
}

/** 合并写回当前编辑中的页眉/页脚整段样式，并立刻刷到 DOM */
function patchChromeBand(partial: Partial<EmrPageChromeBand>) {
  const band = chromeEditingBand.value;
  if (!band || !canChangeLayout.value) return;
  const next = cloneEmrPageChrome(localPageChrome.value);
  next[band] = { ...next[band], ...partial };
  commitPageChrome(next);
  toolbarRevision.value += 1;
  const host = getChromeEditHost();
  const shell = host?.parentElement;
  if (shell instanceof HTMLElement) {
    applyEmrPageChromeBandDomStyle(shell, next[band]);
  }
}

function applyFontFamily(value: string | null) {
  if (chromeEditingBand.value) {
    const host = getChromeEditHost();
    if (!host) return;
    if (prepareChromeFormatSelection(host)) {
      if (applyChromeSelectionTextStyle(host, { fontFamily: value?.trim() || '' })) {
        chromeSavedRange = null;
        persistChromeHostToTemplate();
        if (localPageChrome.value[chromeEditingBand.value].fontFamily) {
          patchChromeBand({ fontFamily: undefined });
        }
      }
      return;
    }
    patchChromeBand({ fontFamily: value?.trim() || undefined });
    return;
  }
  if (value) chain()?.setFontFamily(value).run();
  else chain()?.unsetFontFamily().run();
}

function applyFontSize(value: string | null) {
  if (chromeEditingBand.value) {
    const host = getChromeEditHost();
    if (!host) return;
    if (prepareChromeFormatSelection(host)) {
      if (applyChromeSelectionTextStyle(host, { fontSize: value?.trim() || '' })) {
        chromeSavedRange = null;
        persistChromeHostToTemplate();
        if (localPageChrome.value[chromeEditingBand.value].fontSize) {
          patchChromeBand({ fontSize: undefined });
        }
      }
      return;
    }
    patchChromeBand({ fontSize: value?.trim() || undefined });
    return;
  }
  if (value) chain()?.setFontSize(value).run();
  else chain()?.unsetFontSize().run();
}

/** 按字号列表阶梯增大/减小（A+ / A-） */
function stepFontSize(delta: -1 | 1) {
  if (!canChangeLayout.value) return;
  const opts = EDITOR_FONT_SIZE_OPTIONS;
  if (!opts.length) return;
  let cur = '12pt';
  if (chromeEditingBand.value) {
    cur = localPageChrome.value[chromeEditingBand.value].fontSize?.trim() || '12pt';
  } else {
    const ed = editor.value;
    const attrs = (ed?.getAttributes('textStyle') ?? {}) as Record<string, string | undefined>;
    cur = attrs.fontSize?.trim() || '12pt';
  }
  let idx = opts.findIndex((o) => o.value === cur);
  if (idx < 0) idx = opts.findIndex((o) => o.value === '12pt');
  if (idx < 0) idx = Math.floor(opts.length / 2);
  // 列表从大到小：A+ 往更小下标，A- 往更大下标
  const next = Math.min(opts.length - 1, Math.max(0, idx + delta));
  applyFontSize(opts[next]!.value);
}

/** 清除字符格式（加粗/颜色/字号等 marks） */
function clearCharacterFormat() {
  if (!canEdit.value || chromeEditingBand.value) return;
  chain()?.unsetAllMarks().unsetFontFamily().unsetFontSize().run();
}

/** 首行缩进阶梯：减小 / 增大 */
function stepTextIndent(delta: -1 | 1) {
  if (!canChangeLayout.value || !canSetTextIndent.value) return;
  const opts = EDITOR_TEXT_INDENT_OPTIONS;
  const ed = editor.value;
  if (!ed || !opts.length) return;
  const $from = ed.state.selection.$from;
  let cur: null | string = null;
  for (let d = $from.depth; d > 0; d--) {
    const n = $from.node(d);
    if (n.type.name === 'paragraph') {
      const v = n.attrs.textIndent;
      cur = typeof v === 'string' && v.trim() ? v.trim() : null;
      break;
    }
  }
  if (delta < 0) {
    if (!cur) return;
    const idx = opts.findIndex((o) => o.value === cur);
    if (idx <= 0) {
      chain()?.unsetTextIndent().run();
      return;
    }
    chain()
      ?.setTextIndent(opts[idx - 1]!.value)
      .run();
    return;
  }
  if (!cur) {
    chain()?.setTextIndent(opts[0]!.value).run();
    return;
  }
  const idx = opts.findIndex((o) => o.value === cur);
  const next = Math.min(opts.length - 1, Math.max(0, idx + 1));
  chain()?.setTextIndent(opts[next]!.value).run();
}

/**
 * 工具栏打印：优先交宿主走 OpenPDF（与页外「打印」同源）；
 * 无 @print 监听时回退浏览器打印当前纸面。
 */
function printEditorPaper() {
  const propsOnPrint = getCurrentInstance()?.vnode.props?.onPrint;
  if (typeof propsOnPrint === 'function') {
    exitChromeEdit();
    emit('print');
    return;
  }
  const body = document.body;
  body.classList.add('is-simple-editor-printing');
  const cleanup = () => {
    body.classList.remove('is-simple-editor-printing');
    window.removeEventListener('afterprint', cleanup);
  };
  window.addEventListener('afterprint', cleanup);
  window.print();
  window.setTimeout(cleanup, 1500);
}

/** 刷新工具栏状态与纸面装饰 */
function refreshEditorChrome() {
  toolbarRevision.value += 1;
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return;
  const tr = ed.state.tr.setMeta(EMR_PAGE_CHROME_META, true);
  tr.setMeta('addToHistory', false);
  ed.view.dispatch(tr);
}

function toggleChromeOrBodyMark(mark: 'bold' | 'italic' | 'underline' | 'strike') {
  if (chromeEditingBand.value) {
    if (mark === 'strike') return;
    const host = getChromeEditHost();
    if (host && prepareChromeFormatSelection(host) && toggleChromeSelectionMark(host, mark)) {
      chromeSavedRange = null;
      persistChromeHostToTemplate();
      return;
    }
    const band = chromeEditingBand.value;
    const cur = localPageChrome.value[band];
    if (mark === 'bold') patchChromeBand({ bold: !cur.bold });
    else if (mark === 'italic') patchChromeBand({ italic: !cur.italic });
    else patchChromeBand({ underline: !cur.underline });
    return;
  }
  if (mark === 'bold') chain()?.toggleBold().run();
  else if (mark === 'italic') chain()?.toggleItalic().run();
  else if (mark === 'underline') chain()?.toggleUnderline().run();
  else chain()?.toggleStrike().run();
}

function applyTextAlign(align: 'center' | 'justify' | 'left' | 'right') {
  if (chromeEditingBand.value) {
    if (align === 'justify') return;
    setChromeAlign(align);
    return;
  }
  chain()?.setTextAlign(align).run();
}

function toggleChromeBand(band: 'footer' | 'header') {
  if (!canChangeLayout.value) return;
  if (chromeEditingBand.value === band) {
    exitChromeEdit();
  }
  const next = cloneEmrPageChrome(localPageChrome.value);
  next[band].enabled = !next[band].enabled;
  commitPageChrome(next);
}

/** 双击落在某页上/下边距带时，判定为页眉或页脚（正文层盖住纸时的兜底） */
function hitTestChromeBandAtPoint(clientX: number, clientY: number): 'footer' | 'header' | null {
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return null;
  const pm = ed.view.dom;
  const layer = pm.parentElement?.querySelector(':scope > .simple-editor__papers');
  if (!(layer instanceof HTMLElement)) return null;
  const papers = layer.querySelectorAll(':scope > .simple-editor__paper');
  for (const page of papers) {
    if (!(page instanceof HTMLElement)) continue;
    const rect = page.getBoundingClientRect();
    if (clientX < rect.left || clientX > rect.right) continue;
    if (clientY < rect.top || clientY > rect.bottom) continue;
    const cs = getComputedStyle(pm);
    const marginTop = Number.parseFloat(cs.paddingTop) || 0;
    const marginBottom = Number.parseFloat(cs.paddingBottom) || 0;
    if (clientY <= rect.top + marginTop) return 'header';
    if (clientY >= rect.bottom - marginBottom) return 'footer';
    return null;
  }
  return null;
}

/** 双击纸面页眉/页脚进入编辑；编辑中双击外部退出（对齐 Word） */
function onChromeBandDblClick(event: MouseEvent) {
  if (!canChangeLayout.value) return;
  const target = event.target;
  let band: 'footer' | 'header' | null = null;
  if (target instanceof Element) {
    const bandEl = target.closest('[data-emr-chrome]');
    if (bandEl instanceof HTMLElement) {
      const v = bandEl.dataset.emrChrome;
      if (v === 'header' || v === 'footer') band = v;
    }
  }
  if (!band) {
    band = hitTestChromeBandAtPoint(event.clientX, event.clientY);
  }

  if (chromeEditingBand.value) {
    if (!band) {
      event.preventDefault();
      event.stopPropagation();
      exitChromeEdit();
      return;
    }
    if (chromeEditingBand.value === band) return;
    event.preventDefault();
    event.stopPropagation();
    enterChromeEdit(band);
    return;
  }

  if (!band) return;
  event.preventDefault();
  event.stopPropagation();
  enterChromeEdit(band);
}

/** Esc 退出页眉页脚编辑 */
function onChromeEditDismiss(event: Event) {
  if (!chromeEditingBand.value) return;
  if (isChromeFilePickGuarded()) return;
  if (!(event instanceof KeyboardEvent) || event.key !== 'Escape') return;
  exitChromeEdit();
}

async function ctxCopy() {
  closeCtxMenu();
  document.execCommand('copy');
}

async function ctxCut() {
  closeCtxMenu();
  document.execCommand('cut');
}

async function ctxPaste() {
  closeCtxMenu();
  try {
    const text = await navigator.clipboard.readText();
    insertPlainText(text);
  } catch {
    ElMessage.warning('无法读取剪贴板，请使用 Ctrl+V');
  }
}

function ctxDeleteFillable() {
  const ed = editor.value;
  closeCtxMenu();
  if (!ed) return;
  const hit = resolveFillableAt(ed.state.doc, ed.state.selection.head);
  if (!hit) return;
  ed.view.dispatch(
    ed.state.tr.delete(hit.from, hit.to).setMeta(EMR_SKIP_FILLABLE_RESTORE_META, true),
  );
}

function ctxInsertPageBreak() {
  closeCtxMenu();
  chain()?.insertEmrPageBreak().run();
}

/** 右键菜单对应的正文图片（优先用右键时记下的 pos） */
function resolveCtxImage(): EmrImageSelection | null {
  const ed = editor.value;
  if (!ed || !canChangeLayout.value) return null;
  const pos = ctxMenu.value.imagePos;
  if (pos != null) return resolveEmrImageAtPos(ed, pos);
  return null;
}

/** 写回图片属性并尽量保持选中 */
function applyCtxImageAttrs(hit: EmrImageSelection, attrs: Record<string, unknown>) {
  const ed = editor.value;
  if (!ed) return;
  const node = ed.state.doc.nodeAt(hit.pos);
  if (!node || node.type.name !== 'image') return;
  const tr = ed.state.tr.setNodeMarkup(hit.pos, undefined, {
    ...node.attrs,
    ...attrs,
    wrap: 'inline',
  });
  try {
    tr.setSelection(NodeSelection.create(tr.doc, hit.pos));
  } catch {
    /* ignore */
  }
  ed.view.dispatch(tr);
  ed.view.focus();
}

/** 右键：调整图片宽度（等比） */
async function ctxResizeImage() {
  const hit = resolveCtxImage();
  closeCtxMenu();
  if (!hit) return;
  const curW = hit.attrs.width ?? 200;
  try {
    const { value } = await ElMessageBox.prompt('图片宽度（像素）', '调整图片大小', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: String(curW),
      inputPattern: /^[1-9]\d{0,3}$/,
      inputErrorMessage: '请输入 1～9999 的整数',
    });
    const width = Number(value);
    if (!Number.isFinite(width) || width < 24) {
      ElMessage.warning('宽度无效');
      return;
    }
    const size = await computeEmrImageSize(hit.attrs.src, width, {
      width: hit.attrs.width,
      height: hit.attrs.height,
    });
    applyCtxImageAttrs(hit, { width: size.width, height: size.height });
  } catch {
    /* 取消 */
  }
}

/** 右键：顺时针旋转 90° */
async function ctxRotateImage() {
  const hit = resolveCtxImage();
  closeCtxMenu();
  if (!hit) return;
  try {
    const next = await rotateEmrImageSrcClockwise(hit.attrs.src);
    const displayW = hit.attrs.width ?? next.width;
    const displayH = hit.attrs.height ?? next.height;
    applyCtxImageAttrs(hit, {
      src: next.src,
      width: displayH,
      height: displayW,
      rotate: 0,
    });
  } catch {
    ElMessage.warning('旋转失败');
  }
}

const imageCropOpen = ref(false);
const imageCropSrc = ref('');
const imageCropPos = ref<null | number>(null);

/** 右键：打开裁切 */
function ctxCropImage() {
  const hit = resolveCtxImage();
  closeCtxMenu();
  if (!hit) return;
  imageCropPos.value = hit.pos;
  imageCropSrc.value = hit.attrs.src;
  imageCropOpen.value = true;
}

/** 裁切确认 */
async function onImageCropConfirm(rect: EmrImageCropRect) {
  const ed = editor.value;
  const pos = imageCropPos.value;
  if (!ed || pos == null) return;
  const hit = resolveEmrImageAtPos(ed, pos);
  if (!hit) return;
  try {
    const next = await cropEmrImageSrc(hit.attrs.src, rect);
    const maxW = hit.attrs.width ?? next.width;
    const scale = next.width > 0 ? Math.min(1, maxW / next.width) : 1;
    applyCtxImageAttrs(hit, {
      src: next.src,
      width: Math.max(16, Math.round(next.width * scale)),
      height: Math.max(16, Math.round(next.height * scale)),
    });
  } catch {
    ElMessage.warning('裁切失败');
  } finally {
    imageCropPos.value = null;
    imageCropSrc.value = '';
  }
}

/** 右键删除图片 */
function ctxDeleteImage() {
  const hit = resolveCtxImage();
  closeCtxMenu();
  const ed = editor.value;
  if (!ed || !hit) return;
  const node = ed.state.doc.nodeAt(hit.pos);
  if (!node || node.type.name !== 'image') return;
  ed.view.dispatch(ed.state.tr.delete(hit.pos, hit.pos + node.nodeSize));
  ed.view.focus();
}

provide(
  'emrEditorSurface',
  computed(() => props.surface),
);

provide(
  'emrEditorLayoutLocked',
  computed(() => false),
);

/** 元数据侧栏：显式 prop 优先，否则仅 template 表面展示 */
const showMetadataSidebar = computed(() => {
  if (props.showMetadataSidebar !== undefined) {
    return props.showMetadataSidebar;
  }
  return props.surface === 'template';
});

const emrEditorCaps = computed<EmrEditorCaps>(() => ({
  layoutLocked: false,
  readOnly: props.readOnly,
}));
provide('emrEditorCaps', emrEditorCaps);

const autoSave = props.docId
  ? useEmrAutoSave({
      debounceMs: props.autoSaveDebounceMs,
      docId: props.docId,
      getContent: () => {
        const json = editor.value?.getJSON();
        return json ? serializeFillableMarksToNodes(json) : null;
      },
      remoteSave: props.remoteSave
        ? async (_docId, content) => {
            await props.remoteSave!(content);
          }
        : undefined,
      onRemoteSaveError: props.onRemoteSaveError
        ? () => {
            props.onRemoteSaveError?.();
          }
        : undefined,
      onRestoreDraft: (content) => {
        const ed = editor.value;
        if (!ed || ed.isDestroyed) return;
        ed.chain()
          .setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true)
          .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true)
          .setContent(normalizeEmrRootDoc(content), { emitUpdate: false })
          .run();
        const payload = serializeFillableMarksToNodes(ed.getJSON());
        lastEmittedModelJson = fingerprintEmrModelValue(payload);
        emit('update:modelValue', payload);
      },
    })
  : null;

const saveStatusLabel = computed(() => {
  if (!autoSave) return '';
  switch (autoSave.status.value) {
    case 'dirty': {
      return '未保存';
    }
    case 'saved': {
      return '已保存';
    }
    case 'saving': {
      return '保存中…';
    }
    default: {
      return '';
    }
  }
});

const fileInputRef = ref<HTMLInputElement | null>(null);
const findReplaceVisible = ref(false);
/** Word 式功能区当前页签：开始 / 页面 / 插入（仅重排已有能力） */
const ribbonTab = ref<'home' | 'insert' | 'page'>('home');
/** 完整功能区：文书默认收起；模板制作默认展开 */
const ribbonExpanded = ref(props.surface === 'template');
const toolbarRef = ref<HTMLElement | null>(null);

const showFullRibbon = computed(() => ribbonExpanded.value);

function toggleRibbonExpanded() {
  ribbonExpanded.value = !ribbonExpanded.value;
}

function closeRibbonExpanded() {
  ribbonExpanded.value = false;
}

function onRibbonKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !ribbonExpanded.value) return;
  if (chromeEditingBand.value) return;
  if (props.surface === 'template') return;
  closeRibbonExpanded();
}
const localPageChrome = ref<EmrPageChrome>(
  normalizeEmrPageChrome(props.pageChrome ?? createDefaultEmrPageChrome()),
);
/** 当前正在纸面编辑的页眉/页脚；null 表示写正文 */
const chromeEditingBand = ref<'footer' | 'header' | null>(null);
/** 编辑页眉/页脚时临时撑开上/下边距（回车增高） */
const chromeMarginOverride = ref<{ bottom?: string; top?: string } | null>(null);
let chromeHostInputEl: HTMLElement | null = null;
type ChromeImgPointerDrag = {
  img: HTMLImageElement;
  moved: boolean;
  offsetX: number;
  offsetY: number;
  pointerId: number;
  posHost: HTMLElement;
};

/** 页眉图片组内指针拖拽 */
let chromeImgPointerDrag: ChromeImgPointerDrag | null = null;

function onChromeImgPointerMove(event: PointerEvent) {
  const drag = chromeImgPointerDrag;
  if (!drag || event.pointerId !== drag.pointerId) return;
  if (!drag.moved) {
    drag.moved = true;
    drag.img.classList.add('simple-editor__chrome-img--dragging');
  }
  const hostRect = drag.posHost.getBoundingClientRect();
  const left = Math.max(0, Math.round(event.clientX - hostRect.left - drag.offsetX));
  const top = Math.max(0, Math.round(event.clientY - hostRect.top - drag.offsetY));
  applyChromeImagePosition(drag.img, drag.posHost, left, top);
  chromeImgPosX.value = left;
  chromeImgPosY.value = top;
  event.preventDefault();
}

function detachChromeImgPointerDragListeners() {
  document.removeEventListener('pointermove', onChromeImgPointerMove, true);
  document.removeEventListener('pointerup', onChromeImgPointerUp, true);
  document.removeEventListener('pointercancel', onChromeImgPointerUp, true);
}

function onChromeImgPointerUp(event: PointerEvent) {
  const drag = chromeImgPointerDrag;
  chromeImgPointerDrag = null;
  detachChromeImgPointerDragListeners();
  if (!drag || event.pointerId !== drag.pointerId) return;
  drag.img.classList.remove('simple-editor__chrome-img--dragging');
  if (!drag.moved) return;
  selectChromeImage(drag.img);
  persistChromeHostToTemplate();
  onChromeHostInput();
}

function beginChromeImgPointerDrag(
  img: HTMLImageElement,
  bandHost: HTMLElement,
  event: PointerEvent,
) {
  const posHost = resolveChromeImagePositionHost(img, bandHost);
  ensureChromeLogoRowLayout(posHost, img);
  const imgRect = img.getBoundingClientRect();
  chromeImgPointerDrag = {
    posHost,
    img,
    moved: false,
    offsetX: event.clientX - imgRect.left,
    offsetY: event.clientY - imgRect.top,
    pointerId: event.pointerId,
  };
  document.addEventListener('pointermove', onChromeImgPointerMove, true);
  document.addEventListener('pointerup', onChromeImgPointerUp, true);
  document.addEventListener('pointercancel', onChromeImgPointerUp, true);
}

function cancelChromeImgPointerDrag() {
  const drag = chromeImgPointerDrag;
  chromeImgPointerDrag = null;
  detachChromeImgPointerDragListeners();
  if (!drag) return;
  drag.img.classList.remove('simple-editor__chrome-img--dragging');
}

/** 页眉编辑态当前点中的图片（改宽度/组内位置） */
let chromeSelectedImgEl: HTMLImageElement | null = null;
const chromeImgWidth = ref<number | undefined>(undefined);
const chromeImgPosX = ref<number | undefined>(undefined);
const chromeImgPosY = ref<number | undefined>(undefined);

function parseCssLengthMm(raw: string | undefined, fallback: number): number {
  if (!raw) return fallback;
  const mm = raw.trim().match(/^([\d.]+)\s*mm$/i);
  if (mm) {
    const n = Number(mm[1]);
    return Number.isFinite(n) ? n : fallback;
  }
  const px = raw.trim().match(/^([\d.]+)\s*px$/i);
  if (px) {
    const n = Number(px[1]);
    return Number.isFinite(n) ? (n * 25.4) / 96 : fallback;
  }
  return fallback;
}

const pageMarginStyle = computed(() => {
  const m = localPageMargins.value;
  const paper = resolvePaperCssSize();
  const vars: Record<string, string> = {
    '--simple-editor-a4-w': paper.width,
    '--simple-editor-a4-min-h': paper.height,
    '--simple-editor-corner-top': m.top,
    '--simple-editor-margin-top': m.top,
    '--simple-editor-corner-right': m.right,
    '--simple-editor-margin-right': m.right,
    '--simple-editor-corner-bottom': m.bottom,
    '--simple-editor-margin-bottom': m.bottom,
    '--simple-editor-corner-left': m.left,
    '--simple-editor-margin-left': m.left,
  };

  const topBase = parseCssLengthMm(m.top, 15);
  const bottomBase = parseCssLengthMm(m.bottom, 15);
  if (localPageChrome.value.header.enabled) {
    const headerMm = resolveScreenChromeBandPaddingMm(localPageChrome.value.header, topBase);
    vars['--simple-editor-margin-top'] = `${headerMm}mm`;
  }
  if (localPageChrome.value.footer.enabled) {
    const footerMm = resolveScreenChromeBandPaddingMm(localPageChrome.value.footer, bottomBase);
    vars['--simple-editor-margin-bottom'] = `${footerMm}mm`;
  }
  if (chromeMarginOverride.value?.top) {
    vars['--simple-editor-margin-top'] = chromeMarginOverride.value.top;
  }
  if (chromeMarginOverride.value?.bottom) {
    vars['--simple-editor-margin-bottom'] = chromeMarginOverride.value.bottom;
  }
  return vars;
});

const ctxMenu = ref({
  inFillable: false,
  inImage: false,
  /** 右键命中的正文图片文档位置 */
  imagePos: null as null | number,
  /** 右键命中页眉/页脚图片 */
  inChromeImage: false,
  inTable: false,
  visible: false,
  x: 0,
  y: 0,
});

/** 强制工具栏随选区/事务刷新（editor 为 markRaw） */
const toolbarRevision = ref(0);

const mergedMetaValues = computed(() => ({
  ...(props.metadataValues ?? {}),
}));

/** 从可填命中位置向上找命名块 attrs.name（如 westernDiagnosis） */
function readNamedBlockNameAtPos(doc: Editor['state']['doc'], pos: number): string {
  const size = doc.content.size;
  const safe = Math.min(Math.max(0, pos), size);
  const $pos = doc.resolve(safe);
  for (let depth = $pos.depth; depth > 0; depth -= 1) {
    const name = String($pos.node(depth).attrs.name ?? '').trim();
    if (name) return name;
  }
  return '';
}

/**
 * 将 metadata 写入正文中的元数据节点。
 * @param ed 编辑器实例
 * @param vals fieldKey / elementId → 展示值
 * @param readonlyOnly 为 true 时：只读域始终同步；可改域仅在当前值为空时预填（保留医生已录入）
 * 患者绑定域与就诊时间默认按 vals 强制覆盖；落款时间已有值不覆盖。
 */
function applyMetadataValuesToDocNodes(
  ed: Editor,
  vals: Record<string, string>,
  readonlyOnly: boolean,
  options?: ApplyMetadataValuesOptions,
) {
  const { schema } = ed.state;
  const onlyKeys = options?.onlyKeys;
  const allowForceOverwrite = options?.forceOverwriteBoundKeys !== false;
  const updates: { hit: ReturnType<typeof collectAllFillableHits>[number]; nextVal: string }[] = [];
  for (const hit of collectAllFillableHits(ed.state.doc)) {
    const attrs = (hit.mark?.attrs ?? hit.node?.attrs ?? {}) as Record<string, unknown>;
    const nodeType = hit.node?.type.name ?? (hit.mark ? 'emrMetadataChip' : '');
    const key = resolveFillableMetadataKey(
      nodeType,
      attrs,
      readNamedBlockNameAtPos(ed.state.doc, hit.from),
    );
    if (!key) continue;
    if (onlyKeys && !onlyKeys.has(key)) continue;
    const nextVal = vals[key] ?? '';
    if (onlyKeys && !String(nextVal).trim()) continue;
    const forceOverwrite = allowForceOverwrite && shouldForceOverwriteMetadataKey(key);
    const editable = hit.editable;
    const cur = hit.mark
      ? stripFillableZwsp(ed.state.doc.textBetween(hit.from, hit.to, '\n', '\n')).trim()
      : String(hit.node?.attrs.value ?? '').trim();
    // 落款时间已写入正文则保留（查看已有病历不得换成当天）
    if (isDocumentStampMetadataKey(key) && cur) continue;
    // 可改域：文书态仅预填空值；患者绑定/就诊时间始终强制覆盖（与 ContentJson 路径同构）
    if (!forceOverwrite && readonlyOnly && editable && cur) continue;
    if (cur === String(nextVal).trim()) continue;
    updates.push({ hit, nextVal });
  }
  if (!updates.length) return;
  updates.sort((a, b) => b.hit.from - a.hit.from);
  let tr = ed.state.tr;
  for (const u of updates) {
    tr = replaceFillableHitPlainText(tr, schema, u.hit, u.nextVal);
  }
  if (tr.docChanged) {
    tr.setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true);
    tr.setMeta(EMR_CONTENT_LOCK_BYPASS_META, true);
    ed.view.dispatch(tr);
  }
}

/** 供 handleDrop 使用，避免 useEditor 初始化时闭包拿不到实例 */
let editorRefForDrop: Editor | undefined;
/**
 * 最近一次 onUpdate 回写经 normalize+serialize 后的指纹。
 * modelValue watch 用同一管道比对，避免回声整篇 setContent 把光标甩到文末。
 */
let lastEmittedModelJson = '';

function fingerprintEmrModelValue(val: unknown): string {
  return JSON.stringify(
    serializeFillableMarksToNodes(normalizeEmrRootDoc(val ?? defaultEmrEditorDoc)),
  );
}

const editor = useEditor({
  extensions: getSimpleEditorExtensions({ placeholder: props.placeholder }),
  content: normalizeEmrRootDoc(props.modelValue ?? defaultEmrEditorDoc),
  /**
   * 只读关根编辑器；书写态全文可改（文书锁已下线）。
   */
  editable: !props.readOnly,
  onCreate: ({ editor: ed }) => {
    editorRefForDrop = ed;
    syncPageChromeToEditor(ed);
    ed.view.dom.addEventListener('contextmenu', onEditorContextMenu);
    void nextTick(() => bindEditorScrollMemory());
  },
  onDestroy: () => {
    editorRefForDrop?.view.dom.removeEventListener('contextmenu', onEditorContextMenu);
    editorRefForDrop = undefined;
  },
  editorProps: {
    handleKeyDown(_view, event) {
      const key = event.key.toLowerCase();
      if ((event.ctrlKey || event.metaKey) && key === 'f') {
        event.preventDefault();
        findReplaceVisible.value = true;
        return true;
      }
      if (!findReplaceVisible.value) return false;
      if (key === 'f3') {
        event.preventDefault();
        const ed = editorRefForDrop;
        if (event.shiftKey) {
          ed?.commands.emrFindPrevious();
        } else {
          ed?.commands.emrFindNext();
        }
        return true;
      }
      if (key === 'escape') {
        findReplaceVisible.value = false;
        editorRefForDrop?.commands.emrClearFind();
        return true;
      }
      return false;
    },
    handleDrop(view, event, _slice, moved) {
      if (moved) return false;
      const ed = editorRefForDrop;
      if (!ed || !ed.isEditable) return false;
      const raw = event.dataTransfer?.getData('application/emr-metadata');
      if (!raw) return false;
      try {
        const payload = JSON.parse(raw) as {
          dataType?: string;
          editable?: boolean;
          fieldKey: string;
          label: string;
        };
        if (!payload?.fieldKey) return false;
        // 拖到页眉/页脚边距 → 插入页眉字段，不进正文
        if (
          tryInsertMetadataIntoChrome(payload.fieldKey, payload.label, event.clientX, event.clientY)
        ) {
          event.preventDefault();
          return true;
        }
        const coords = view.posAtCoords({
          left: event.clientX,
          top: event.clientY,
        });
        if (!coords) return false;
        const insertPos = liftPosOutOfFillableField(view.state.doc, coords.pos);
        // 制作模板：value 留空，靠下划线槽位辨认；套用/书写文书时用患者元数据
        const value =
          props.surface === 'template' ? '' : (mergedMetaValues.value[payload.fieldKey] ?? '');
        const editable = payload.editable !== false;
        event.preventDefault();
        const resolved = resolveInsertEmrMetadataChip({
          dataType: payload.dataType,
          editable,
          fieldKey: payload.fieldKey,
          label: payload.label,
          value,
        });
        if (resolved.kind === 'dataElement') {
          ed.chain().focus().insertContentAt(insertPos, resolved.content).run();
          return true;
        }
        ed.chain()
          .focus()
          .insertContentAt(insertPos, resolved.insertContent)
          .command(fillableCaretAfterDropCommand())
          .run();
        return true;
      } catch {
        return false;
      }
    },
  },
  onUpdate: ({ editor: ed }) => {
    const payload = serializeFillableMarksToNodes(ed.getJSON());
    lastEmittedModelJson = fingerprintEmrModelValue(payload);
    emit('update:modelValue', payload);
    emit('change');
    autoSave?.markDirty();
  },
  onTransaction: ({ transaction }) => {
    toolbarRevision.value += 1;
    const measured = transaction.getMeta(EMR_MEASURED_CHROME_META) as
      | { footerMm?: number; headerMm?: number }
      | undefined;
    if (!measured) return;
    const next = cloneEmrPageChrome(localPageChrome.value);
    let changed = false;
    if (measured.headerMm != null && Math.abs(measured.headerMm - next.header.heightMm) > 0.3) {
      next.header.heightMm = measured.headerMm;
      changed = true;
    }
    if (measured.footerMm != null && Math.abs(measured.footerMm - next.footer.heightMm) > 0.3) {
      next.footer.heightMm = measured.footerMm;
      changed = true;
    }
    if (!changed) return;
    localPageChrome.value = next;
    // 实测后立刻用 px 撑开正文边距，避免 heightMm 回写前一帧正文顶边压过页眉 float 行
    if (measured.headerMm != null) {
      const topPx = Math.ceil((measured.headerMm * 96) / 25.4);
      chromeMarginOverride.value = {
        top: `${topPx}px`,
        bottom: chromeMarginOverride.value?.bottom,
      };
    }
    emit('update:pageChrome', cloneEmrPageChrome(next));
    const ed = editor.value;
    if (ed && !ed.isDestroyed) {
      const store = ed.storage.emrPagination as { chrome?: EmrPageChrome } | undefined;
      if (store) store.chrome = cloneEmrPageChrome(next);
      // 页眉实测回写后补一次重排（测量已与 margin-top 解耦，不会正反馈）
      requestEmrPaginationRelayout(ed);
    }
  },
});

watch(
  () => props.pageChrome,
  (v) => {
    if (!v) return;
    localPageChrome.value = cloneEmrPageChrome(v);
    syncPageChromeToEditor();
  },
  { deep: true },
);

watch(
  () => props.pageMargins,
  (v) => {
    localPageMargins.value = normalizeEditorPageMargins(v);
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    requestEmrPaginationRelayout(ed);
  },
  { deep: true },
);

/** 根编辑器可写后，把填写岛 contenteditable 从 v-once 冻结值写回 */
function syncEditorIslandEditable(ed: Editor | undefined = editor.value) {
  if (!ed || ed.isDestroyed) return;
  const writable = !props.readOnly;
  syncFillableIslandContentEditable(ed.view.dom, writable);
  void nextTick(() => {
    if (ed.isDestroyed) return;
    syncFillableIslandContentEditable(ed.view.dom, !props.readOnly);
  });
}

watch(
  () => props.modelValue,
  (val) => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    const nextDoc = normalizeEmrRootDoc(val ?? defaultEmrEditorDoc);
    const next = fingerprintEmrModelValue(nextDoc);
    // 自身 onUpdate 回声：勿整篇 setContent（会丢选区飞文末）
    if (next === lastEmittedModelJson) return;
    const cur = fingerprintEmrModelValue(serializeFillableMarksToNodes(ed.getJSON()));
    if (cur === next) {
      lastEmittedModelJson = next;
      return;
    }
    const sel = ed.state.selection;
    const prevFrom = sel.from;
    const prevTo = sel.to;
    const scrollToKeep = captureEditorScroll();
    ed.chain()
      .setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true)
      .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true)
      .setContent(nextDoc, { emitUpdate: false })
      .run();
    lastEmittedModelJson = next;
    // 外部换稿时尽量保住选区，避免默认落文末
    const size = ed.state.doc.content.size;
    const from = Math.max(0, Math.min(prevFrom, size));
    const to = Math.max(0, Math.min(prevTo, size));
    try {
      ed.commands.setTextSelection(from === to ? from : { from, to });
    } catch {
      /* 位置越界则保持 setContent 默认选区 */
    }
    // 同步写入：勿 await nextTick，否则元数据 watch 与套模板竞态时会出现首屏空白、再点才填充
    if (props.surface === 'document') {
      applyMetadataValuesToDocNodes(ed, mergedMetaValues.value, true);
    }
    // 换稿常复用 NodeView；v-once 岛可能仍停在打开历史时的 contenteditable=false
    syncEditorIslandEditable(ed);
    restoreEditorScroll(scrollToKeep);
    scheduleSettledPaginationRelayout();
  },
);

watch(
  () => props.readOnly,
  (ro) => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    ed.setEditable(!ro);
    toolbarRevision.value += 1;
    syncEditorIslandEditable(ed);
    // 只读切换会换 NodeView 壳，高度变化后须重排（查看页常见）
    scheduleSettledPaginationRelayout();
  },
);

/** 患者信息条变更：仅同步只读绑定域；可改域保留医生录入（不与套用模版后的全量预填混用） */
watch(
  [() => props.surface, () => mergedMetaValues.value],
  () => {
    if (props.surface === 'template') return;
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    applyMetadataValuesToDocNodes(ed, mergedMetaValues.value, true);
  },
  { deep: true },
);

watch(
  () =>
    [
      props.documentTypeName,
      props.documentTitle,
      mergedMetaValues.value,
      localPageChrome.value,
    ] as const,
  () => {
    syncPageChromeToEditor();
  },
  { deep: true },
);

function toggleFindReplaceBar() {
  findReplaceVisible.value = !findReplaceVisible.value;
  if (!findReplaceVisible.value) {
    editor.value?.commands.emrClearFind();
  }
}

onMounted(() => {
  if (autoSave && props.docId && props.autoRestoreDraft && !props.modelValue) {
    autoSave.tryRestoreDraft();
  }
  document.addEventListener('click', closeCtxMenu);
  document.addEventListener('keydown', onChromeEditDismiss);
  document.addEventListener('keydown', onRibbonKeydown);
  void nextTick(() => bindEditorScrollMemory());
  scheduleSettledPaginationRelayout();
});

onActivated(() => {
  bindEditorScrollMemory();
  restoreEditorScroll(rememberedEditorScroll);
});

onDeactivated(() => {
  snapshotEditorScroll();
  unbindEditorScrollMemory();
});

onBeforeUnmount(() => {
  for (const t of settledRelayoutTimers) window.clearTimeout(t);
  settledRelayoutTimers = [];
  for (const t of editorScrollRestoreTimers) window.clearTimeout(t);
  editorScrollRestoreTimers = [];
  unbindEditorScrollMemory();
  if (chromeEditingBand.value) {
    exitChromeEdit();
  }
  document.removeEventListener('click', closeCtxMenu);
  document.removeEventListener('keydown', onChromeEditDismiss);
  document.removeEventListener('keydown', onRibbonKeydown);
  editor.value?.destroy();
});

function chain() {
  return editor.value?.chain().focus();
}

type ParagraphSpacingPreset = (typeof EDITOR_PARAGRAPH_SPACING_OPTIONS)[number];

function applyParagraphSpacingPreset(o: ParagraphSpacingPreset) {
  const c = chain();
  if (!c) return;
  const patch: { marginBottom?: null | string; marginTop?: null | string } = {};
  if (Object.prototype.hasOwnProperty.call(o, 'marginTop')) {
    patch.marginTop = o.marginTop ?? null;
  }
  if (Object.prototype.hasOwnProperty.call(o, 'marginBottom')) {
    patch.marginBottom = o.marginBottom ?? null;
  }
  c.setParagraphSpacing(patch).run();
}

/** 与 editor.isEditable 一致：只读时工具栏变灰 */
const canEdit = computed(() => !!editor.value && !props.readOnly && editor.value.isEditable);

/** 可改排版：插入、块样式、对齐、列表、元数据拖拽等 */
const canChangeLayout = computed(() => canEdit.value);

watch(canChangeLayout, (ok) => {
  if (!ok && chromeEditingBand.value) {
    exitChromeEdit();
  }
});

/** 样式卡片高亮：正文 / H1~H3 */
function isRibbonStyleActive(kind: 'h1' | 'h2' | 'h3' | 'paragraph'): boolean {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed) return kind === 'paragraph';
  if (kind === 'paragraph') {
    return !ed.isActive('heading');
  }
  if (kind === 'h1') return ed.isActive('heading', { level: 1 });
  if (kind === 'h2') return ed.isActive('heading', { level: 2 });
  return ed.isActive('heading', { level: 3 });
}

/** 应用正文或标题样式 */
function applyRibbonStyle(kind: 'h1' | 'h2' | 'h3' | 'paragraph') {
  if (kind === 'paragraph') {
    chain()?.setParagraph().run();
    return;
  }
  if (kind === 'h1') {
    chain()?.toggleHeading({ level: 1 }).run();
    return;
  }
  if (kind === 'h2') {
    chain()?.toggleHeading({ level: 2 }).run();
    return;
  }
  chain()?.toggleHeading({ level: 3 }).run();
}

const fontFamilyLabel = computed(() => {
  void toolbarRevision.value;
  const fallback = EDITOR_FONT_FAMILY_OPTIONS[0]?.label ?? '宋体';
  const chromeBand = chromeEditingBand.value;
  if (chromeBand) {
    const ff = localPageChrome.value[chromeBand].fontFamily?.trim();
    if (!ff) return fallback;
    return EDITOR_FONT_FAMILY_OPTIONS.find((o) => o.value === ff)?.label ?? fallback;
  }
  const ed = editor.value;
  if (!ed) return fallback;
  const attrs = ed.getAttributes('textStyle') as Record<string, string | undefined>;
  const ff = attrs.fontFamily?.trim();
  if (!ff) return fallback;
  const hit = EDITOR_FONT_FAMILY_OPTIONS.find((o) => o.value === ff);
  return hit?.label ?? fallback;
});

const fontSizeLabel = computed(() => {
  void toolbarRevision.value;
  const chromeBand = chromeEditingBand.value;
  if (chromeBand) {
    const fs = localPageChrome.value[chromeBand].fontSize?.trim() || '9pt';
    return EDITOR_FONT_SIZE_OPTIONS.find((o) => o.value === fs)?.label ?? fs;
  }
  const ed = editor.value;
  const fallback = '小四';
  if (!ed) return fallback;
  const attrs = ed.getAttributes('textStyle') as Record<string, string | undefined>;
  const fs = attrs.fontSize?.trim();
  if (!fs) return fallback;
  const hit = EDITOR_FONT_SIZE_OPTIONS.find((o) => o.value === fs);
  return hit?.label ?? fs;
});

const lineHeightLabel = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  const fallback = '行距 1.5';
  if (!ed) return fallback;
  const $from = ed.state.selection.$from;
  let lh: null | string = null;
  for (let d = $from.depth; d > 0; d--) {
    const n = $from.node(d);
    if (n.type.name === 'paragraph' || n.type.name === 'heading') {
      const v = n.attrs.lineHeight;
      lh = typeof v === 'string' && v.trim() ? v.trim() : null;
      break;
    }
  }
  if (!lh) return fallback;
  const hit = EDITOR_LINE_HEIGHT_OPTIONS.find((o) => o.value === lh);
  return hit?.label ?? `行距 ${lh}`;
});

const canSetTextIndent = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed || !canChangeLayout.value) return false;
  const $from = ed.state.selection.$from;
  for (let d = $from.depth; d > 0; d--) {
    if ($from.node(d).type.name === 'paragraph') return true;
  }
  return false;
});

const canSetParagraphSpacing = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed || !canChangeLayout.value) return false;
  const $from = ed.state.selection.$from;
  for (let d = $from.depth; d > 0; d--) {
    if ($from.node(d).type.name === 'paragraph') {
      return true;
    }
  }
  return false;
});

const tbar = computed(() => {
  void toolbarRevision.value;
  const chromeBand = chromeEditingBand.value;
  if (chromeBand) {
    const c = localPageChrome.value[chromeBand];
    const align = c.align ?? 'center';
    return {
      alignCenter: align === 'center',
      alignJustify: false,
      alignLeft: align === 'left',
      alignRight: align === 'right',
      bold: Boolean(c.bold),
      italic: Boolean(c.italic),
      strike: false,
      subscript: false,
      superscript: false,
      underline: Boolean(c.underline),
    };
  }
  const ed = editor.value;
  if (!ed) {
    return {
      alignCenter: false,
      alignJustify: false,
      alignLeft: false,
      alignRight: false,
      bold: false,
      italic: false,
      strike: false,
      subscript: false,
      superscript: false,
      underline: false,
    };
  }
  return {
    alignCenter: ed.isActive({ textAlign: 'center' }),
    alignJustify: ed.isActive({ textAlign: 'justify' }),
    alignLeft: ed.isActive({ textAlign: 'left' }),
    alignRight: ed.isActive({ textAlign: 'right' }),
    bold: ed.isActive('bold'),
    italic: ed.isActive('italic'),
    strike: ed.isActive('strike'),
    subscript: ed.isActive('subscript'),
    superscript: ed.isActive('superscript'),
    underline: ed.isActive('underline'),
  };
});

const isInTable = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed) return false;
  const $from = ed.state.selection.$from;
  for (let d = $from.depth; d > 0; d--) {
    if ($from.node(d).type.name === 'table') return true;
  }
  return false;
});

/** 当前选区所在 tableRow，以及行内第一个格子的文档位置 */
function currentTableRow() {
  const ed = editor.value;
  if (!ed) return null;
  const $from = ed.state.selection.$from;
  for (let d = $from.depth; d > 0; d--) {
    const node = $from.node(d);
    if (node.type.name === 'tableRow') {
      return { node, start: $from.start(d) };
    }
  }
  return null;
}

/** 一行里已写 minHeightMm 的最大值（视觉行高跟最高格走） */
function readRowMinHeightMm(row: {
  child: (i: number) => { attrs: Record<string, unknown> };
  childCount: number;
}) {
  let maxMm: null | number = null;
  for (let i = 0; i < row.childCount; i++) {
    const mm = Number(row.child(i).attrs.minHeightMm);
    if (!Number.isFinite(mm) || mm <= 0) continue;
    maxMm = maxMm == null ? mm : Math.max(maxMm, mm);
  }
  return maxMm;
}

/** 当前行 minHeightMm，供行高数字框 */
const currentRowMinHeightMm = computed(() => {
  void toolbarRevision.value;
  const row = currentTableRow();
  return row ? readRowMinHeightMm(row.node) : null;
});

/** 本页内容区高度，行高上限用它，避免空书写区把落款挤出页脚 */
const cellMinHeightMaxMm = computed(() => {
  const paper = resolvePaperCssSize();
  const paperMm = parseCssLengthMm(paper.height, 297);
  const topMm = parseCssLengthMm(localPageMargins.value.top, 15);
  const bottomMm = parseCssLengthMm(localPageMargins.value.bottom, 15);
  return Math.round(pageContentHeightMm(paperMm, topMm, bottomMm));
});

/** 把当前行所有格写成同一 minHeightMm；空/0 表示跟内容走 */
function onRowMinHeightMmChange(raw: null | number | undefined) {
  if (!canEdit.value || props.readOnly) return;
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return;
  const row = currentTableRow();
  if (!row) return;
  const result =
    raw == null
      ? { clamped: false, value: null as null | number }
      : clampCellMinHeightMm(raw, cellMinHeightMaxMm.value);
  if (result.value === readRowMinHeightMm(row.node)) return;
  if (result.clamped) {
    ElMessage.warning('已到本页可用高度');
  }
  const { tr } = ed.state;
  let offset = 0;
  // 整行每格都写同一下限，避免只改当前格时行高看起来没动
  for (let i = 0; i < row.node.childCount; i++) {
    const cell = row.node.child(i);
    const current = Number(cell.attrs.minHeightMm);
    const currentVal = Number.isFinite(current) && current > 0 ? current : null;
    if (currentVal !== result.value) {
      tr.setNodeMarkup(row.start + offset, undefined, {
        ...cell.attrs,
        minHeightMm: result.value,
      });
    }
    offset += cell.nodeSize;
  }
  if (tr.docChanged) {
    ed.view.dispatch(tr);
  }
}

/** 选区内是否包含非纯文本行内节点（元数据、固定字、图片等），有则不可整体转为固定文字 */
function selectionHasNonTextInline(doc: Editor['state']['doc'], from: number, to: number) {
  let found = false;
  doc.nodesBetween(from, to, (node) => {
    if (node.isBlock) return true;
    if (node.isText) return true;
    found = true;
    return false;
  });
  return found;
}

/** 可将当前选区转为 emrFixedText：同一段内、仅文字、非空 */
const canFixSelection = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed || !canEdit.value) return false;
  const { empty, from, to } = ed.state.selection;
  if (empty) return false;
  const $from = ed.state.doc.resolve(from);
  const $to = ed.state.doc.resolve(to);
  if ($from.parent !== $to.parent) return false;
  if (selectionHasNonTextInline(ed.state.doc, from, to)) return false;
  const raw = ed.state.doc.textBetween(from, to, '', '');
  return raw.trim().length > 0;
});

/** 当前为选中整块固定文字节点时可取消固定 */
const canUnfixFixedText = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed || !canEdit.value) return false;
  const sel = ed.state.selection;
  return sel instanceof NodeSelection && sel.node.type.name === 'emrFixedText';
});

function fixSelectedTextAsFixed() {
  editor.value?.chain().focus().fixSelectionAsEmrFixedText().run();
}

function unfixSelectedFixedText() {
  editor.value?.chain().focus().unfixEmrFixedText().run();
}

function clampDividerLineWidth(n: number): number {
  if (!Number.isFinite(n)) return 1;
  return Math.min(24, Math.max(1, Math.round(n)));
}

/** 整块选中实线/虚线横线时可改颜色与粗细 */
const selectedDividerLine = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed || props.readOnly) return null;
  const sel = ed.state.selection;
  if (!(sel instanceof NodeSelection)) return null;
  const n = sel.node;
  if (n.type.name === 'horizontalRule') {
    return {
      kind: 'solid' as const,
      pos: sel.from,
      lineColor: String(n.attrs.lineColor ?? '#000000'),
      lineWidth: clampDividerLineWidth(Number(n.attrs.lineWidth)),
    };
  }
  if (n.type.name === 'emrHorizontalDashed') {
    return {
      kind: 'dashed' as const,
      pos: sel.from,
      lineColor: String(n.attrs.lineColor ?? '#000000'),
      lineWidth: clampDividerLineWidth(Number(n.attrs.lineWidth)),
    };
  }
  return null;
});

function applyDividerLineAttrs(patch: { lineColor?: string; lineWidth?: number }) {
  const ed = editor.value;
  const line = selectedDividerLine.value;
  if (!ed || !line || props.readOnly) return;
  const node = ed.state.doc.nodeAt(line.pos);
  if (!node) return;
  const next = { ...node.attrs };
  if (patch.lineColor !== undefined) Object.assign(next, { lineColor: patch.lineColor });
  if (patch.lineWidth !== undefined) {
    Object.assign(next, { lineWidth: clampDividerLineWidth(patch.lineWidth) });
  }
  ed.view.dispatch(ed.state.tr.setNodeMarkup(line.pos, undefined, next));
}

function onDividerLineColorInput(e: Event) {
  applyDividerLineAttrs({ lineColor: (e.target as HTMLInputElement).value });
}

function onDividerLineWidthChange(v: number | undefined) {
  if (v === undefined) return;
  applyDividerLineAttrs({ lineWidth: v });
}

/** 侧栏元数据分组顺序（未列出的来源排在 MANUAL 前） */
const META_SOURCE_GROUP_ORDER = [
  'PATIENT',
  'ENCOUNTER',
  'ADMISSION',
  'DEPT',
  'WARD',
  'DIAGNOSIS',
  'ALLERGY',
  'VITAL_SIGN',
  'LAB_SUMMARY',
  'EXAM_SUMMARY',
  'USER',
  'USER_DEPT',
  'PRACTITIONER',
  'ORG',
  'INSURANCE',
  'SYSTEM',
  'SETTING',
  'DOCUMENT',
  'TEMPLATE',
  'VERSION',
  'CONST',
  'DICT',
  'READ_MODEL',
  'EXPR',
  'FIELD_REF',
  'HTTP_API',
  'REQUEST',
  'MANUAL',
] as const;

const META_SOURCE_LABELS: Record<string, string> = {
  PATIENT: '患者主档',
  ENCOUNTER: '就诊',
  ADMISSION: '入院',
  DEPT: '科室',
  WARD: '病区',
  DIAGNOSIS: '诊断',
  ALLERGY: '过敏',
  VITAL_SIGN: '体征',
  LAB_SUMMARY: '检验',
  EXAM_SUMMARY: '检查',
  USER: '当前用户',
  USER_DEPT: '用户科室',
  PRACTITIONER: '医务人员',
  ORG: '机构',
  INSURANCE: '医保',
  SYSTEM: '系统',
  SETTING: '系统配置',
  DOCUMENT: '文书',
  TEMPLATE: '模板',
  VERSION: '版本',
  CONST: '常量',
  DICT: '字典',
  READ_MODEL: '读模型',
  EXPR: '表达式',
  FIELD_REF: '引用字段',
  HTTP_API: '外部接口',
  REQUEST: '请求覆盖',
  MANUAL: '手工录入',
  OTHER: '其他',
};

/** 默认展开：患者 + 就诊相关 */
const META_SOURCE_DEFAULT_OPEN = new Set(['PATIENT', 'ENCOUNTER', 'ADMISSION', 'DEPT', 'WARD']);

const metaSearchKeyword = ref('');
/** true = 折叠；未写入则按默认展开规则 */
const metaGroupCollapsed = ref<Record<string, boolean>>({});

function metaSourceKey(field: EmrMetadataField) {
  const s = field.valueSource?.trim();
  return s || 'OTHER';
}

function metaSourceLabel(source: string) {
  return META_SOURCE_LABELS[source] ?? source;
}

function isMetaGroupOpen(source: string) {
  if (metaSearchKeyword.value.trim()) return true;
  if (Object.prototype.hasOwnProperty.call(metaGroupCollapsed.value, source)) {
    return !metaGroupCollapsed.value[source];
  }
  return META_SOURCE_DEFAULT_OPEN.has(source);
}

function toggleMetaGroup(source: string) {
  if (metaSearchKeyword.value.trim()) return;
  const nextCollapsed = isMetaGroupOpen(source);
  metaGroupCollapsed.value = { ...metaGroupCollapsed.value, [source]: nextCollapsed };
}

function metaChipTitle(field: EmrMetadataField) {
  const val = mergedMetaValues.value[field.fieldKey];
  const parts = [field.label, field.fieldKey];
  if (val) parts.push(val);
  return parts.join(' · ');
}

const filteredMetadataFields = computed(() => {
  const kw = metaSearchKeyword.value.trim().toLowerCase();
  const list = props.metadataFields ?? [];
  if (!kw) return list;
  return list.filter(
    (f) => f.label.toLowerCase().includes(kw) || f.fieldKey.toLowerCase().includes(kw),
  );
});

const metadataFieldGroups = computed(() => {
  const buckets = new Map<string, EmrMetadataField[]>();
  for (const f of filteredMetadataFields.value) {
    const key = metaSourceKey(f);
    const arr = buckets.get(key);
    if (arr) arr.push(f);
    else buckets.set(key, [f]);
  }
  const orderIndex = new Map(META_SOURCE_GROUP_ORDER.map((k, i) => [k as string, i] as const));
  const keys = [...buckets.keys()].sort((a, b) => {
    const rank = (k: string) =>
      k === 'OTHER'
        ? Number.MAX_SAFE_INTEGER
        : (orderIndex.get(k) ?? META_SOURCE_GROUP_ORDER.length);
    const ia = rank(a);
    const ib = rank(b);
    if (ia !== ib) return ia - ib;
    return a.localeCompare(b);
  });
  return keys.map((source) => ({
    source,
    label: metaSourceLabel(source),
    fields: buckets.get(source)!,
    open: isMetaGroupOpen(source),
  }));
});

function onMetaDragStart(e: DragEvent, field: EmrMetadataField) {
  if (props.readOnly) {
    e.preventDefault();
    return;
  }
  const dt = e.dataTransfer;
  if (!dt) return;
  dt.setData(
    'application/emr-metadata',
    JSON.stringify({
      dataType: field.dataType,
      editable: field.editable !== false,
      fieldKey: field.fieldKey,
      label: field.label,
    }),
  );
  dt.effectAllowed = 'copy';
}

/** 编辑页眉/页脚时：点击侧栏元数据即可插入字段片 */
function onMetaChipClick(field: EmrMetadataField) {
  if (!canChangeLayout.value || props.readOnly) return;
  if (!chromeEditingBand.value) return;
  tryInsertMetadataIntoChrome(field.fieldKey, field.label);
}

/** 正文：双击侧栏元数据，插入到当前光标处（进槽内，可立刻填写） */
function insertMetadataAtCursor(field: EmrMetadataField): boolean {
  const ed = editor.value;
  if (!ed || ed.isDestroyed || !ed.isEditable) return false;
  if (!canChangeLayout.value || props.readOnly) return false;

  const insertPos = liftPosOutOfFillableField(ed.state.doc, ed.state.selection.head);
  const value = props.surface === 'template' ? '' : (mergedMetaValues.value[field.fieldKey] ?? '');
  const resolved = resolveInsertEmrMetadataChip({
    dataType: field.dataType,
    editable: field.editable !== false,
    fieldKey: field.fieldKey,
    label: field.label,
    value,
  });
  if (resolved.kind === 'dataElement') {
    ed.chain().focus().insertContentAt(insertPos, resolved.content).run();
    return true;
  }
  ed.chain()
    .focus()
    .insertContentAt(insertPos, resolved.insertContent)
    .command(fillableCaretAfterDropCommand())
    .run();
  return true;
}

function onMetaChipDblClick(event: MouseEvent, field: EmrMetadataField) {
  event.preventDefault();
  if (!canChangeLayout.value || props.readOnly) return;
  // 页眉/页脚编辑中由单击处理，避免双击连插两次
  if (chromeEditingBand.value) return;
  insertMetadataAtCursor(field);
}

async function promptLink() {
  if (!editor.value || props.readOnly) return;
  const previous = editor.value.getAttributes('link').href as string | undefined;
  try {
    const { value } = await ElMessageBox.prompt('链接地址', '插入链接', {
      inputValue: previous ?? 'https://',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
    const href = value?.trim();
    if (!href) {
      chain()?.extendMarkRange('link').unsetLink().run();
      return;
    }
    chain()?.extendMarkRange('link').setLink({ href }).run();
  } catch {
    /* cancel */
  }
}

async function promptImageUrl() {
  if (!editor.value || props.readOnly) return;
  if (chromeEditingBand.value) {
    armChromeFilePickGuard();
  }
  try {
    const { value } = await ElMessageBox.prompt('图片 URL', '插入图片', {
      inputPlaceholder: 'https://',
      confirmButtonText: '插入',
      cancelButtonText: '取消',
    });
    const src = value?.trim();
    if (!src) return;
    if (!(await probeEmrImageSrc(src))) {
      ElMessage.warning('该图片地址无法加载，请检查 URL 或网络');
      return;
    }
    if (chromeEditingBand.value) {
      armChromeFilePickGuard();
    }
    insertEditorOrChromeImage(src);
  } catch {
    /* cancel */
  }
}

/** 模板版式标签：行内固定字，不可逐字编辑，可整块删除 */
async function promptInsertFixedText() {
  if (!editor.value || props.readOnly) return;
  try {
    const { value } = await ElMessageBox.prompt(
      '插入后正文中不可逐字修改，仅可整块选中删除。适用于「主诉：」等版式文字。',
      '插入固定文字',
      {
        inputPlaceholder: '例如：主诉：',
        confirmButtonText: '插入',
        cancelButtonText: '取消',
      },
    );
    const text = value?.trim() ?? '';
    if (!text) {
      ElMessage.warning('请输入要插入的文字');
      return;
    }
    chain()?.insertEmrFixedText({ text }).run();
  } catch {
    /* cancel */
  }
}

/** 行内自由文本：与下拉等共用插入弹窗（含必填） */
function promptInsertFreeText() {
  openInsertWidgetDialog('text');
}

const insertWidgetDialogVisible = ref(false);
const insertWidgetKind = ref<EmrDataElementWidget>('select');

/** 打开插入控件弹窗（自由文本 / 下拉 / 单选 / 日期 / 复选） */
function openInsertWidgetDialog(widget: EmrDataElementWidget) {
  if (!editor.value || props.readOnly) return;
  insertWidgetKind.value = widget;
  insertWidgetDialogVisible.value = true;
}

/** 弹窗确认后写入行内可填域（选项与必填仅存 JSON） */
function onInsertWidgetConfirm(result: EmrInsertWidgetResult) {
  if (!editor.value || props.readOnly) return;
  if (result.widget === 'text') {
    chain()
      ?.insertEmrFreeText({
        label: result.label,
        placeholder: result.placeholder || result.label,
        value: '',
        required: result.required,
        requiredMessage: result.requiredMessage,
      })
      .run();
    return;
  }
  chain()
    ?.insertEmrDataElement({
      editable: true,
      elementId: '',
      labelSnapshot: result.label,
      optionsJson: result.optionsJson,
      placeholder: result.placeholder,
      required: result.required,
      requiredMessage: result.requiredMessage,
      value: '',
      widgetOverride: result.widget,
    })
    .run();
}

/** 工具栏「图片」：本地选文件 → data URL；页眉编辑态写入页眉，否则写入正文 */
async function onImageFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !editor.value || props.readOnly) return;
  input.value = '';
  if (chromeEditingBand.value) {
    armChromeFilePickGuard();
  }
  try {
    const src = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.addEventListener('load', () => resolve(String(reader.result ?? '')));
      reader.addEventListener('error', () =>
        reject(reader.error ?? new Error('read image failed')),
      );
      reader.readAsDataURL(file);
    });
    if (!src) {
      ElMessage.warning('未能读取图片文件');
      return;
    }
    insertEditorOrChromeImage(src);
  } catch {
    ElMessage.warning('读取图片失败');
  }
}

/** 为当前表格 / 单元格设置 data-name（按名称取值） */
async function promptTableIdentifiers() {
  if (!editor.value || !isInTable.value || props.readOnly) return;
  const ed = editor.value;
  const { $from } = ed.state.selection;
  let tableAttrs: Record<string, unknown> = {};
  let cellType: 'tableCell' | 'tableHeader' | null = null;
  let cellAttrs: Record<string, unknown> = {};
  for (let d = $from.depth; d > 0; d--) {
    const n = $from.node(d);
    if (n.type.name === 'table') {
      tableAttrs = { ...(n.attrs as Record<string, unknown>) };
    }
    if (n.type.name === 'tableCell' || n.type.name === 'tableHeader') {
      cellType = n.type.name;
      cellAttrs = { ...(n.attrs as Record<string, unknown>) };
    }
  }
  try {
    const { value: tableName } = await ElMessageBox.prompt('表格名称（data-name）', '表格标识', {
      inputValue: String(tableAttrs.name ?? ''),
      inputPlaceholder: '如：vitalsTable',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
    const name = tableName?.trim();
    if (name) {
      chain()?.updateAttributes('table', { name }).run();
    } else {
      chain()?.updateAttributes('table', { name: null }).run();
    }
    if (!cellType) return;
    const { value: cellName } = await ElMessageBox.prompt(
      '当前单元格名称（data-name，可留空）',
      '单元格标识',
      {
        inputValue: String(cellAttrs.name ?? ''),
        confirmButtonText: '确定',
        cancelButtonText: '跳过',
      },
    );
    const named = cellName?.trim();
    if (named) {
      chain()?.updateAttributes(cellType, { name: named }).run();
    } else if (cellName !== undefined) {
      chain()?.updateAttributes(cellType, { name: null }).run();
    }
  } catch {
    /* cancel */
  }
}

function setColor(hex: null | string) {
  if (props.readOnly) return;
  if (!hex) {
    chain()?.unsetColor().run();
    return;
  }
  chain()?.setColor(hex).run();
}

function setHighlight(hex: null | string) {
  if (props.readOnly) return;
  if (!hex) {
    chain()?.unsetHighlight().run();
    return;
  }
  chain()?.setHighlight({ color: hex }).run();
}

/** Shift+点击颜色区：清除字体颜色（不打开取色器） */
function onColorInputMouseDown(e: MouseEvent) {
  if (!canEdit.value || !e.shiftKey) return;
  e.preventDefault();
  setColor(null);
}

/** Shift+点击高亮区：清除背景色 */
function onHighlightInputMouseDown(e: MouseEvent) {
  if (!canEdit.value || !e.shiftKey) return;
  e.preventDefault();
  setHighlight(null);
}

function onForeColorInput(e: Event) {
  setColor((e.target as HTMLInputElement).value);
}

function onHighlightColorInput(e: Event) {
  setHighlight((e.target as HTMLInputElement).value);
}

function toggleSuperscript() {
  chain()?.unsetSubscript().toggleSuperscript().run();
}

function toggleSubscript() {
  chain()?.unsetSuperscript().toggleSubscript().run();
}

function openImageFilePicker() {
  if (props.readOnly) return;
  if (chromeEditingBand.value) {
    armChromeFilePickGuard();
  }
  fileInputRef.value?.click();
}

const FILLABLE_ISLAND_SELECTOR =
  '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field';

/**
 * 把纸面填写岛屿的 DOM 字回写到 attrs.value。
 * 点「保存」时焦点已在按钮上，只刷 activeElement 会漏掉刚写的主诉等。
 */
function flushFillableIslandsToDoc(ed: Editor) {
  if (ed.isDestroyed) return;
  const { view } = ed;
  const jobs: { hit: NonNullable<ReturnType<typeof resolveFillableAt>>; text: string }[] = [];
  const islands = view.dom.querySelectorAll(FILLABLE_ISLAND_SELECTOR);
  for (const node of islands) {
    if (!(node instanceof HTMLElement)) continue;
    const text = stripFillableZwsp(readContentEditablePlainText(node));
    const wrapper = node.closest('[data-node-view-wrapper]');
    const posTarget = wrapper instanceof HTMLElement ? wrapper : node;
    let pos: number;
    try {
      // atom NodeView 内部 contenteditable 不在 PM 映射里，须从 wrapper 取 pos
      pos = view.posAtDOM(posTarget, 0);
    } catch {
      continue;
    }
    const hit = resolveFillableAt(view.state.doc, pos);
    if (!hit) continue;
    const cur =
      hit.kind === 'widget'
        ? stripFillableZwsp(String(hit.node?.attrs.value ?? ''))
        : stripFillableZwsp(view.state.doc.textBetween(hit.from, hit.to, '', ''));
    if (cur === text) continue;
    jobs.push({ hit, text });
  }
  if (jobs.length === 0) return;
  // 从后往前改，避免 mark 替换改文档长度后前面 pos 失效
  jobs.sort((a, b) => b.hit.from - a.hit.from);
  let tr = view.state.tr;
  for (const job of jobs) {
    tr = replaceFillableHitPlainText(tr, view.state.schema, job.hit, job.text);
  }
  if (tr.docChanged) {
    tr.setMeta(EMR_SKIP_FILLABLE_RESTORE_META, true);
    view.dispatch(tr);
  }
}

function getJSON(): JSONContent | null {
  const ed = editor.value;
  if (ed && !ed.isDestroyed) {
    flushFillableIslandsToDoc(ed);
  }
  const json = ed?.getJSON() ?? null;
  return json ? serializeFillableMarksToNodes(json) : null;
}

function getHTML(): string {
  return editor.value?.getHTML() ?? '';
}

/**
 * 文书保存前必填校验；失败时聚焦首个空槽。
 * @returns true 表示通过
 */
function validateRequired(): boolean {
  const ed = editor.value;
  if (!ed || ed.isDestroyed) return true;
  flushFillableIslandsToDoc(ed);
  const { ok, violations } = validateRequiredFillables(ed);
  if (ok) return true;
  const first = violations[0];
  const tip = first?.message || '请完善必填项';
  const extra = violations.length > 1 ? `（另有 ${violations.length - 1} 项未填）` : '';
  ElMessage.warning(`${tip}${extra}`);
  return false;
}

function setContent(content: JSONContent | null | string | undefined) {
  const ed = editor.value;
  if (!ed) return;
  const run = (payload: JSONContent | string) => {
    ed.chain()
      .setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true)
      .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true)
      .setContent(payload, { emitUpdate: false })
      .run();
  };
  if (content === null || content === undefined) {
    run(defaultEmrEditorDoc);
    return;
  }
  if (typeof content === 'string') {
    try {
      run(normalizeEmrRootDoc(JSON.parse(content) as JSONContent));
    } catch {
      run(`<p>${content}</p>`);
    }
    return;
  }
  run(normalizeEmrRootDoc(content));
}

defineExpose({
  getEditor: (): Editor | null => editor.value ?? null,
  getJSON,
  getHTML,
  setContent,
  validateRequired,
  /** 退出页眉/页脚编辑并写回模板（保存前调用） */
  exitPageChromeEdit: () => exitChromeEdit(),
  /** 查询是否有本地草稿，返回 { content, updatedAt } 或 null */
  checkDraft: () => autoSave?.checkDraft() ?? Promise.resolve(null),
  /** 主动恢复草稿到编辑器 */
  restoreDraft: () => autoSave?.tryRestoreDraft(),
  /** 立即写入当前内容到草稿（含 remoteSave）；手动保存可传 rethrowRemoteError */
  flushAutoSave: (options?: { rethrowRemoteError?: boolean }) => autoSave?.flush(options),
  /** 清除本地草稿 */
  clearDraft: () => autoSave?.clearDraft(),
  /** 载入服务端正文后同步为已保存 */
  markSaved: () => autoSave?.markSaved(),
  /** 标记存在未远程保存的修改（如仅本地载入模板） */
  markDirty: () => autoSave?.markDirty(),
  isDirty: () => autoSave?.status.value === 'dirty',
  /** 将已开立中/西医诊断写入对应空槽（不覆盖已填、不改日期/患者域） */
  fillEmptyDiagnosisMetadata: (vals: Record<string, string>) => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    if (props.surface === 'template' || props.readOnly) return;
    applyMetadataValuesToDocNodes(ed, vals, true, {
      forceOverwriteBoundKeys: false,
      onlyKeys: new Set(EMR_DIAGNOSIS_METADATA_FIELD_KEYS),
    });
  },
  captureScrollPosition: captureEditorScroll,
  restoreScrollPosition: restoreEditorScroll,
});
</script>

<template>
  <div
    class="simple-editor"
    :class="{
      'simple-editor--layout-locked': false,
      'simple-editor--surface-template': surface === 'template',
      'simple-editor--surface-document': surface === 'document',
      'simple-editor--chrome-editable': canChangeLayout,
      'simple-editor--chrome-editing': Boolean(chromeEditingBand),
      'simple-editor--show-ruler': showPageRuler,
      'simple-editor--show-gridlines': showPageGridlines,
    }"
    :style="pageMarginStyle"
  >
    <div class="simple-editor__body">
      <aside v-if="showMetadataSidebar" class="simple-editor__sidebar" aria-label="元数据字段">
        <div class="simple-editor__sidebar-title">元数据</div>
        <p v-if="true" class="simple-editor__sidebar-hint">
          {{
            chromeEditingBand
              ? `正在编辑${chromeEditingBand === 'header' ? '页眉' : '页脚'}：点击或拖到纸面即可插入字段`
              : '拖到正文插入字段，或双击插入到当前光标处；拖到页眉/页脚边距可插入页眉字段。正文内仅展示取值；「只读」随患者信息更新，「可改」可手工修正。'
          }}
        </p>
        <p v-else class="simple-editor__sidebar-hint">版式已锁定，不可从侧栏拖入新字段。</p>
        <ElInput
          v-model="metaSearchKeyword"
          class="simple-editor__sidebar-search"
          clearable
          placeholder="搜索名称 / 编码"
          size="small"
        >
          <template #prefix>
            <ElIcon><Search /></ElIcon>
          </template>
        </ElInput>
        <div v-if="metadataFieldGroups.length === 0" class="simple-editor__sidebar-empty">
          无匹配字段
        </div>
        <div v-for="g in metadataFieldGroups" :key="g.source" class="simple-editor__meta-group">
          <button
            type="button"
            class="simple-editor__meta-group-head"
            :aria-expanded="g.open"
            @click="toggleMetaGroup(g.source)"
          >
            <ElIcon
              class="simple-editor__meta-group-caret"
              :class="{ 'simple-editor__meta-group-caret--open': g.open }"
            >
              <ArrowDown />
            </ElIcon>
            <span class="simple-editor__meta-group-label">{{ g.label }}</span>
            <span class="simple-editor__meta-group-count">{{ g.fields.length }}</span>
          </button>
          <div v-show="g.open" class="simple-editor__meta-group-body">
            <div
              v-for="f in g.fields"
              :key="f.fieldKey"
              class="simple-editor__meta-chip"
              :class="{
                'simple-editor__meta-chip--layout-locked': false,
                'simple-editor__meta-chip--chrome-target': Boolean(chromeEditingBand),
              }"
              :draggable="canChangeLayout"
              :title="metaChipTitle(f)"
              @dragstart="onMetaDragStart($event, f)"
              @click="onMetaChipClick(f)"
              @dblclick="onMetaChipDblClick($event, f)"
            >
              <span class="simple-editor__meta-chip-label">{{ f.label }}</span>
              <span
                class="simple-editor__meta-chip-badge"
                :class="
                  f.editable === false
                    ? 'simple-editor__meta-chip-badge--locked'
                    : 'simple-editor__meta-chip-badge--edit'
                "
              >
                {{ f.editable === false ? '只读' : '可改' }}
              </span>
            </div>
          </div>
        </div>
      </aside>

      <div class="simple-editor__main">
        <div
          v-if="showToolbar"
          ref="toolbarRef"
          class="simple-editor__toolbar"
          :class="{ 'is-ribbon-open': showFullRibbon }"
        >
          <!-- 文书收起态：Word 快速访问工具栏 -->
          <div v-if="!showFullRibbon" class="simple-editor__qat">
            <div class="simple-editor__toolbar-compact-group">
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                title="撤销"
                :disabled="!canEdit"
                @click="chain()?.undo().run()"
              >
                <ElIcon :size="16"><RefreshLeft /></ElIcon>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                title="重做"
                :disabled="!canEdit"
                @click="chain()?.redo().run()"
              >
                <ElIcon :size="16"><RefreshRight /></ElIcon>
              </button>
            </div>

            <span class="simple-editor__toolbar-compact-sep" aria-hidden="true"></span>

            <div class="simple-editor__toolbar-compact-group">
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.bold }"
                title="加粗"
                :disabled="!canEdit"
                @click="toggleChromeOrBodyMark('bold')"
              >
                <span class="simple-editor__mark-b">B</span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.italic }"
                title="斜体"
                :disabled="!canEdit"
                @click="toggleChromeOrBodyMark('italic')"
              >
                <span class="simple-editor__mark-i">I</span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.underline }"
                title="下划线"
                :disabled="!canEdit"
                @click="toggleChromeOrBodyMark('underline')"
              >
                <span class="simple-editor__mark-u">U</span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.strike }"
                title="删除线"
                :disabled="!canEdit || Boolean(chromeEditingBand)"
                @click="toggleChromeOrBodyMark('strike')"
              >
                <span class="simple-editor__mark-s">S</span>
              </button>
            </div>

            <span class="simple-editor__toolbar-compact-sep" aria-hidden="true"></span>

            <ElDropdown trigger="click" :disabled="!canChangeLayout">
              <button
                type="button"
                class="simple-editor__ribbon-select simple-editor__ribbon-select--md simple-editor__toolbar-compact-select"
                :disabled="!canChangeLayout"
              >
                {{ fontFamilyLabel }}
                <ElIcon><ArrowDown /></ElIcon>
              </button>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem @click="applyFontFamily(null)">默认字体</ElDropdownItem>
                  <ElDropdownItem
                    v-for="(o, idx) in EDITOR_FONT_FAMILY_OPTIONS"
                    :key="o.value"
                    :divided="idx === 0"
                    @click="applyFontFamily(o.value)"
                  >
                    {{ o.label }}
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>

            <ElDropdown trigger="click" :disabled="!canChangeLayout">
              <button
                type="button"
                class="simple-editor__ribbon-select simple-editor__ribbon-select--sm simple-editor__toolbar-compact-select"
                :disabled="!canChangeLayout"
              >
                {{ fontSizeLabel }}
                <ElIcon><ArrowDown /></ElIcon>
              </button>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem @click="applyFontSize(null)">默认字号</ElDropdownItem>
                  <ElDropdownItem
                    v-for="(o, idx) in EDITOR_FONT_SIZE_OPTIONS"
                    :key="o.value"
                    :divided="idx === 0"
                    @click="applyFontSize(o.value)"
                  >
                    {{ o.label }}
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>

            <span class="simple-editor__toolbar-compact-sep" aria-hidden="true"></span>

            <div class="simple-editor__toolbar-compact-group">
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.alignLeft }"
                title="左对齐"
                :disabled="!canChangeLayout"
                @click="applyTextAlign('left')"
              >
                <span
                  class="simple-editor__align-bars simple-editor__align-bars--left"
                  aria-hidden="true"
                >
                  <span></span><span></span><span></span><span></span>
                </span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.alignCenter }"
                title="居中"
                :disabled="!canChangeLayout"
                @click="applyTextAlign('center')"
              >
                <span
                  class="simple-editor__align-bars simple-editor__align-bars--center"
                  aria-hidden="true"
                >
                  <span></span><span></span><span></span><span></span>
                </span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                :class="{ 'is-active': tbar.alignRight }"
                title="右对齐"
                :disabled="!canChangeLayout"
                @click="applyTextAlign('right')"
              >
                <span
                  class="simple-editor__align-bars simple-editor__align-bars--right"
                  aria-hidden="true"
                >
                  <span></span><span></span><span></span><span></span>
                </span>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                title="清除格式"
                :disabled="!canEdit || Boolean(chromeEditingBand)"
                @click="clearCharacterFormat"
              >
                <ElIcon :size="14"><Brush /></ElIcon>
              </button>
            </div>

            <span class="simple-editor__toolbar-compact-sep" aria-hidden="true"></span>

            <label
              v-if="isInTable"
              class="simple-editor__cell-min-height"
              title="当前行最小高度（mm）。空书写区可调矮；有字时仍跟内容走，不会裁切。"
            >
              <span class="simple-editor__cell-min-height-label">行高</span>
              <ElInputNumber
                :model-value="currentRowMinHeightMm ?? undefined"
                size="small"
                :min="EMR_MIN_CELL_HEIGHT_MM"
                :max="cellMinHeightMaxMm"
                :step="1"
                :disabled="!canEdit"
                controls-position="right"
                class="simple-editor__cell-min-height-input"
                @change="onRowMinHeightMmChange"
              />
              <span class="simple-editor__cell-min-height-unit">mm</span>
            </label>

            <span
              v-if="isInTable"
              class="simple-editor__toolbar-compact-sep"
              aria-hidden="true"
            ></span>

            <div class="simple-editor__toolbar-compact-group">
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                title="查找替换 (Ctrl+F)"
                @click="toggleFindReplaceBar"
              >
                <ElIcon :size="16"><Search /></ElIcon>
              </button>
              <button
                type="button"
                class="simple-editor__ribbon-icon"
                title="打印（OpenPDF）"
                @click="printEditorPaper"
              >
                <ElIcon :size="16"><Printer /></ElIcon>
              </button>
            </div>

            <span class="simple-editor__toolbar-compact-sep" aria-hidden="true"></span>

            <button
              type="button"
              class="simple-editor__qat-expand"
              title="展开功能区"
              @click="toggleRibbonExpanded"
            >
              <ElIcon :size="14"><Operation /></ElIcon>
              <span>展开功能区</span>
              <ElIcon :size="12"><ArrowDown /></ElIcon>
            </button>

            <span
              v-if="autoSave"
              class="simple-editor__save-status simple-editor__save-status--compact"
              :class="{
                'simple-editor__save-status--dirty': autoSave.status.value === 'dirty',
                'simple-editor__save-status--saving': autoSave.status.value === 'saving',
                'simple-editor__save-status--saved': autoSave.status.value === 'saved',
              }"
            >
              {{ saveStatusLabel }}
            </span>
          </div>

          <!-- 完整 Ribbon：内嵌展开，与 Word 一致 -->
          <div v-else class="simple-editor__ribbon-shell">
            <div class="simple-editor__ribbon-top">
              <div class="simple-editor__ribbon-tabs" role="tablist">
                <button
                  type="button"
                  class="simple-editor__ribbon-tab"
                  role="tab"
                  :class="{ 'is-active': ribbonTab === 'home' }"
                  :aria-selected="ribbonTab === 'home'"
                  @click="ribbonTab = 'home'"
                >
                  开始
                </button>
                <button
                  type="button"
                  class="simple-editor__ribbon-tab"
                  role="tab"
                  :class="{ 'is-active': ribbonTab === 'page' }"
                  :aria-selected="ribbonTab === 'page'"
                  @click="ribbonTab = 'page'"
                >
                  页面
                </button>
                <button
                  type="button"
                  class="simple-editor__ribbon-tab"
                  role="tab"
                  :class="{ 'is-active': ribbonTab === 'insert' }"
                  :aria-selected="ribbonTab === 'insert'"
                  @click="ribbonTab = 'insert'"
                >
                  插入
                </button>
              </div>
              <div class="simple-editor__ribbon-top-right">
                <span
                  v-if="autoSave"
                  class="simple-editor__save-status simple-editor__save-status--compact"
                  :class="{
                    'simple-editor__save-status--dirty': autoSave.status.value === 'dirty',
                    'simple-editor__save-status--saving': autoSave.status.value === 'saving',
                    'simple-editor__save-status--saved': autoSave.status.value === 'saved',
                  }"
                >
                  {{ saveStatusLabel }}
                </span>
                <button
                  type="button"
                  class="simple-editor__ribbon-collapse"
                  title="收起功能区"
                  @click="closeRibbonExpanded"
                >
                  <ElIcon :size="14"><ArrowUp /></ElIcon>
                  <span>收起</span>
                </button>
              </div>
            </div>

            <div class="simple-editor__ribbon" role="tabpanel">
              <!-- 开始 -->
              <template v-if="ribbonTab === 'home'">
                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <div class="simple-editor__ribbon-stack">
                      <div class="simple-editor__ribbon-row">
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="撤销"
                          :disabled="!canEdit"
                          @click="chain()?.undo().run()"
                        >
                          <ElIcon :size="16"><RefreshLeft /></ElIcon>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="重做"
                          :disabled="!canEdit"
                          @click="chain()?.redo().run()"
                        >
                          <ElIcon :size="16"><RefreshRight /></ElIcon>
                        </button>
                      </div>
                      <div class="simple-editor__ribbon-row">
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="所选固定"
                          :disabled="!canChangeLayout || !canFixSelection"
                          @click="fixSelectedTextAsFixed"
                        >
                          <ElIcon :size="15"><Lock /></ElIcon>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="清除格式"
                          :disabled="!canEdit || Boolean(chromeEditingBand)"
                          @click="clearCharacterFormat"
                        >
                          <ElIcon :size="15"><Brush /></ElIcon>
                        </button>
                      </div>
                    </div>
                  </div>
                  <span class="simple-editor__ribbon-group-label">剪贴板</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <div class="simple-editor__ribbon-stack">
                      <div class="simple-editor__ribbon-row">
                        <ElDropdown trigger="click" :disabled="!canChangeLayout">
                          <button
                            type="button"
                            class="simple-editor__ribbon-select simple-editor__ribbon-select--md"
                            :disabled="!canChangeLayout"
                          >
                            {{ fontFamilyLabel }}
                            <ElIcon><ArrowDown /></ElIcon>
                          </button>
                          <template #dropdown>
                            <ElDropdownMenu>
                              <ElDropdownItem @click="applyFontFamily(null)"
                                >默认字体</ElDropdownItem
                              >
                              <ElDropdownItem
                                v-for="(o, idx) in EDITOR_FONT_FAMILY_OPTIONS"
                                :key="o.value"
                                :divided="idx === 0"
                                @click="applyFontFamily(o.value)"
                              >
                                {{ o.label }}
                              </ElDropdownItem>
                            </ElDropdownMenu>
                          </template>
                        </ElDropdown>
                        <ElDropdown trigger="click" :disabled="!canChangeLayout">
                          <button
                            type="button"
                            class="simple-editor__ribbon-select simple-editor__ribbon-select--sm"
                            :disabled="!canChangeLayout"
                          >
                            {{ fontSizeLabel }}
                            <ElIcon><ArrowDown /></ElIcon>
                          </button>
                          <template #dropdown>
                            <ElDropdownMenu>
                              <ElDropdownItem @click="applyFontSize(null)">默认字号</ElDropdownItem>
                              <ElDropdownItem
                                v-for="(o, idx) in EDITOR_FONT_SIZE_OPTIONS"
                                :key="o.value"
                                :divided="idx === 0"
                                @click="applyFontSize(o.value)"
                              >
                                {{ o.label }}
                              </ElDropdownItem>
                            </ElDropdownMenu>
                          </template>
                        </ElDropdown>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="增大字号"
                          :disabled="!canChangeLayout"
                          @click="stepFontSize(-1)"
                        >
                          A+
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="减小字号"
                          :disabled="!canChangeLayout"
                          @click="stepFontSize(1)"
                        >
                          A-
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="清除字符格式"
                          :disabled="!canEdit || Boolean(chromeEditingBand)"
                          @click="clearCharacterFormat"
                        >
                          <ElIcon :size="14"><Brush /></ElIcon>
                        </button>
                      </div>
                      <div class="simple-editor__ribbon-row">
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.bold }"
                          title="加粗"
                          :disabled="!canEdit"
                          @click="toggleChromeOrBodyMark('bold')"
                        >
                          <span class="simple-editor__mark-b">B</span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.italic }"
                          title="斜体"
                          :disabled="!canEdit"
                          @click="toggleChromeOrBodyMark('italic')"
                        >
                          <span class="simple-editor__mark-i">I</span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.underline }"
                          title="下划线"
                          :disabled="!canEdit"
                          @click="toggleChromeOrBodyMark('underline')"
                        >
                          <span class="simple-editor__mark-u">U</span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.strike }"
                          title="删除线"
                          :disabled="!canEdit || Boolean(chromeEditingBand)"
                          @click="toggleChromeOrBodyMark('strike')"
                        >
                          <span class="simple-editor__mark-s">S</span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.superscript }"
                          title="上标"
                          :disabled="!canEdit"
                          @click="toggleSuperscript"
                        >
                          <span class="simple-editor__mark-sup">x<sup>2</sup></span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.subscript }"
                          title="下标"
                          :disabled="!canEdit"
                          @click="toggleSubscript"
                        >
                          <span class="simple-editor__mark-sub">x<sub>2</sub></span>
                        </button>
                        <div
                          class="simple-editor__color-group"
                          title="文本突出显示（点击选色；Shift+点击清除）"
                        >
                          <span
                            class="simple-editor__color-label simple-editor__color-label--hilite"
                          >
                            ab
                          </span>
                          <input
                            type="color"
                            class="simple-editor__color-input"
                            value="#fff59d"
                            :disabled="!canChangeLayout"
                            @mousedown="onHighlightInputMouseDown"
                            @input="onHighlightColorInput"
                          />
                        </div>
                        <div
                          class="simple-editor__color-group"
                          title="字体颜色（点击选色；Shift+点击清除）"
                        >
                          <span class="simple-editor__color-label simple-editor__color-label--fore">
                            A
                          </span>
                          <input
                            type="color"
                            class="simple-editor__color-input"
                            value="#000000"
                            :disabled="!canChangeLayout"
                            @mousedown="onColorInputMouseDown"
                            @input="onForeColorInput"
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                  <span class="simple-editor__ribbon-group-label">字体</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <div class="simple-editor__ribbon-stack">
                      <div class="simple-editor__ribbon-row">
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="项目符号"
                          :disabled="!canChangeLayout"
                          @click="chain()?.toggleBulletList().run()"
                        >
                          •
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="编号"
                          :disabled="!canChangeLayout"
                          @click="chain()?.toggleOrderedList().run()"
                        >
                          1.
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="任务列表"
                          :disabled="!canChangeLayout"
                          @click="chain()?.toggleTaskList().run()"
                        >
                          <ElIcon :size="15"><Checked /></ElIcon>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="减小缩进"
                          :disabled="!canChangeLayout || !canSetTextIndent"
                          @click="stepTextIndent(-1)"
                        >
                          <ElIcon :size="15"><Fold /></ElIcon>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          title="增大缩进"
                          :disabled="!canChangeLayout || !canSetTextIndent"
                          @click="stepTextIndent(1)"
                        >
                          <ElIcon :size="15"><Expand /></ElIcon>
                        </button>
                      </div>
                      <div class="simple-editor__ribbon-row">
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.alignLeft }"
                          title="左对齐"
                          :disabled="!canChangeLayout"
                          @click="applyTextAlign('left')"
                        >
                          <span
                            class="simple-editor__align-bars simple-editor__align-bars--left"
                            aria-hidden="true"
                          >
                            <span></span><span></span><span></span><span></span>
                          </span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.alignCenter }"
                          title="居中"
                          :disabled="!canChangeLayout"
                          @click="applyTextAlign('center')"
                        >
                          <span
                            class="simple-editor__align-bars simple-editor__align-bars--center"
                            aria-hidden="true"
                          >
                            <span></span><span></span><span></span><span></span>
                          </span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.alignRight }"
                          title="右对齐"
                          :disabled="!canChangeLayout"
                          @click="applyTextAlign('right')"
                        >
                          <span
                            class="simple-editor__align-bars simple-editor__align-bars--right"
                            aria-hidden="true"
                          >
                            <span></span><span></span><span></span><span></span>
                          </span>
                        </button>
                        <button
                          type="button"
                          class="simple-editor__ribbon-icon"
                          :class="{ 'is-active': tbar.alignJustify }"
                          title="两端对齐"
                          :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                          @click="applyTextAlign('justify')"
                        >
                          <span
                            class="simple-editor__align-bars simple-editor__align-bars--justify"
                            aria-hidden="true"
                          >
                            <span></span><span></span><span></span><span></span>
                          </span>
                        </button>
                        <ElDropdown
                          trigger="click"
                          :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                        >
                          <button
                            type="button"
                            class="simple-editor__ribbon-select simple-editor__ribbon-select--md"
                            :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                          >
                            {{ lineHeightLabel }}
                            <ElIcon><ArrowDown /></ElIcon>
                          </button>
                          <template #dropdown>
                            <ElDropdownMenu>
                              <ElDropdownItem @click="chain()?.unsetBlockLineHeight().run()">
                                默认行距
                              </ElDropdownItem>
                              <ElDropdownItem
                                v-for="(o, idx) in EDITOR_LINE_HEIGHT_OPTIONS"
                                :key="o.value"
                                :divided="idx === 0"
                                @click="chain()?.setBlockLineHeight(o.value).run()"
                              >
                                {{ o.label }}
                              </ElDropdownItem>
                            </ElDropdownMenu>
                          </template>
                        </ElDropdown>
                        <ElDropdown
                          trigger="click"
                          :disabled="!canChangeLayout || !canSetParagraphSpacing"
                        >
                          <button
                            type="button"
                            class="simple-editor__ribbon-select"
                            title="段前/段后间距（仅段落）"
                            :disabled="!canChangeLayout || !canSetParagraphSpacing"
                          >
                            段间距
                            <ElIcon><ArrowDown /></ElIcon>
                          </button>
                          <template #dropdown>
                            <ElDropdownMenu>
                              <ElDropdownItem @click="chain()?.unsetParagraphSpacing().run()">
                                默认段间距
                              </ElDropdownItem>
                              <ElDropdownItem
                                v-for="(o, idx) in EDITOR_PARAGRAPH_SPACING_OPTIONS"
                                :key="o.label"
                                :divided="idx === 0"
                                @click="applyParagraphSpacingPreset(o)"
                              >
                                {{ o.label }}
                              </ElDropdownItem>
                            </ElDropdownMenu>
                          </template>
                        </ElDropdown>
                      </div>
                    </div>
                  </div>
                  <span class="simple-editor__ribbon-group-label">段落</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group simple-editor__ribbon-group--fill">
                  <div class="simple-editor__ribbon-group-body">
                    <div class="simple-editor__style-gallery">
                      <button
                        type="button"
                        class="simple-editor__style-card"
                        :class="{ 'is-active': isRibbonStyleActive('paragraph') }"
                        :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                        @click="applyRibbonStyle('paragraph')"
                      >
                        <span class="simple-editor__style-card-title">正文</span>
                        <span class="simple-editor__style-card-tag">Text</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__style-card simple-editor__style-card--h1"
                        :class="{ 'is-active': isRibbonStyleActive('h1') }"
                        :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                        @click="applyRibbonStyle('h1')"
                      >
                        <span class="simple-editor__style-card-title">标题1</span>
                        <span class="simple-editor__style-card-tag">H1</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__style-card simple-editor__style-card--h2"
                        :class="{ 'is-active': isRibbonStyleActive('h2') }"
                        :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                        @click="applyRibbonStyle('h2')"
                      >
                        <span class="simple-editor__style-card-title">标题2</span>
                        <span class="simple-editor__style-card-tag">H2</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__style-card"
                        :class="{ 'is-active': isRibbonStyleActive('h3') }"
                        :disabled="!canChangeLayout || Boolean(chromeEditingBand)"
                        @click="applyRibbonStyle('h3')"
                      >
                        <span class="simple-editor__style-card-title">标题3</span>
                        <span class="simple-editor__style-card-tag">H3</span>
                      </button>
                    </div>
                    <button
                      type="button"
                      class="simple-editor__ribbon-icon"
                      title="取消固定"
                      :disabled="!canChangeLayout || !canUnfixFixedText"
                      @click="unfixSelectedFixedText"
                    >
                      <ElIcon :size="15"><Unlock /></ElIcon>
                    </button>
                  </div>
                  <span class="simple-editor__ribbon-group-label">样式</span>
                </div>
              </template>

              <!-- 页面：纸张 / 页边距 / 页眉页脚 / 显示（与插入页签分开） -->
              <template v-else-if="ribbonTab === 'page'">
                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <ElDropdown trigger="click" :disabled="!canChangeLayout">
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd"
                        :disabled="!canChangeLayout"
                      >
                        <ElIcon><Ticket /></ElIcon>
                        <span>{{ paperSizeLabel }}</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem
                            v-for="p in EDITOR_PAPER_SIZE_PRESETS"
                            :key="p.key"
                            @click="applyPaperSize(p.key)"
                          >
                            {{ p.label }}
                          </ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                    <ElDropdown trigger="click" :disabled="!canChangeLayout">
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd"
                        :disabled="!canChangeLayout"
                      >
                        <ElIcon><ScaleToOriginal /></ElIcon>
                        <span>{{ paperOrientationLabel }}</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem @click="applyPaperOrientation('portrait')">
                            纵向
                          </ElDropdownItem>
                          <ElDropdownItem @click="applyPaperOrientation('landscape')">
                            横向
                          </ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                  </div>
                  <span class="simple-editor__ribbon-group-label">纸张</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <ElDropdown
                      trigger="click"
                      :disabled="!canChangeLayout"
                      popper-class="simple-editor__margin-popper"
                      @command="onPageMarginCommand"
                    >
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd"
                        :disabled="!canChangeLayout"
                      >
                        <ElIcon><Crop /></ElIcon>
                        <span>页边距</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem
                            v-for="p in EDITOR_PAGE_MARGIN_PRESETS"
                            :key="p.key"
                            :command="p.key"
                            :class="{ 'is-checked': currentMarginPresetKey === p.key }"
                          >
                            <div class="simple-editor__margin-preset">
                              <div
                                class="simple-editor__margin-thumb"
                                :style="pageMarginThumbStyle(p.value)"
                              >
                                <span class="simple-editor__margin-thumb-body"></span>
                              </div>
                              <div class="simple-editor__margin-preset-text">
                                <div class="simple-editor__margin-preset-name">{{ p.label }}</div>
                                <div class="simple-editor__margin-preset-line">
                                  {{ describeEditorPageMargins(p.value).line1 }}
                                </div>
                                <div class="simple-editor__margin-preset-line">
                                  {{ describeEditorPageMargins(p.value).line2 }}
                                </div>
                              </div>
                            </div>
                          </ElDropdownItem>
                          <ElDropdownItem
                            divided
                            command="custom"
                            :class="{ 'is-checked': currentMarginPresetKey == null }"
                          >
                            <span class="simple-editor__margin-custom-item">自定义页边距...</span>
                          </ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                  </div>
                  <span class="simple-editor__ribbon-group-label">页边距</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :class="{ 'is-active': chromeEditingBand === 'header' }"
                      :disabled="!canChangeLayout"
                      @click="enterChromeEdit('header')"
                    >
                      <ElIcon><Notebook /></ElIcon>
                      <span>页眉</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :class="{ 'is-active': chromeEditingBand === 'footer' }"
                      :disabled="!canChangeLayout"
                      @click="enterChromeEdit('footer')"
                    >
                      <ElIcon><Memo /></ElIcon>
                      <span>页脚</span>
                    </button>
                    <ElDropdown trigger="click" :disabled="!canChangeLayout">
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd"
                        :disabled="!canChangeLayout"
                      >
                        <ElIcon><Document /></ElIcon>
                        <span>页码</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem @click="insertPageNumberFromRibbon('preset')">
                            第 X 页 (共 Y 页)
                          </ElDropdownItem>
                          <ElDropdownItem @click="insertPageNumberFromRibbon('field')">
                            仅页码
                          </ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                  </div>
                  <span class="simple-editor__ribbon-group-label">页眉页脚</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="insertPageBreakFromRibbon"
                    >
                      <ElIcon><Switch /></ElIcon>
                      <span>分页符</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="insertOutlineFromRibbon"
                    >
                      <ElIcon><List /></ElIcon>
                      <span>目录</span>
                    </button>
                  </div>
                  <span class="simple-editor__ribbon-group-label">页面</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :class="{ 'is-active': showPageRuler }"
                      @click="togglePageRuler"
                    >
                      <ElIcon><Operation /></ElIcon>
                      <span>标尺</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :class="{ 'is-active': showPageGridlines }"
                      @click="togglePageGridlines"
                    >
                      <ElIcon><Grid /></ElIcon>
                      <span>网格线</span>
                    </button>
                  </div>
                  <span class="simple-editor__ribbon-group-label">显示</span>
                </div>
              </template>

              <!-- 插入：图标磁贴 + 控件网格 -->
              <template v-else-if="ribbonTab === 'insert'">
                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <div class="simple-editor__ribbon-cmd-grid">
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canChangeLayout"
                        @click="openInsertWidgetDialog('select')"
                      >
                        <ElIcon><Select /></ElIcon>
                        <span>下拉框</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canChangeLayout"
                        @click="openInsertWidgetDialog('radio')"
                      >
                        <ElIcon><CircleCheck /></ElIcon>
                        <span>单选</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canChangeLayout"
                        @click="openInsertWidgetDialog('date')"
                      >
                        <ElIcon><Calendar /></ElIcon>
                        <span>日期</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canEdit"
                        @click="insertDateTime('datetime')"
                      >
                        <ElIcon><Calendar /></ElIcon>
                        <span>当前时间</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canChangeLayout"
                        @click="promptInsertFreeText"
                      >
                        <ElIcon><Document /></ElIcon>
                        <span>自由文本</span>
                      </button>
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd-flat"
                        :disabled="!canChangeLayout"
                        @click="openInsertWidgetDialog('checkbox')"
                      >
                        <ElIcon><Checked /></ElIcon>
                        <span>复选框</span>
                      </button>
                    </div>
                  </div>
                  <span class="simple-editor__ribbon-group-label">控件</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <ElDropdown trigger="click" :disabled="!canChangeLayout">
                      <button
                        type="button"
                        class="simple-editor__ribbon-cmd"
                        :disabled="!canChangeLayout"
                      >
                        <ElIcon><Grid /></ElIcon>
                        <span>表格</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem
                            @click="
                              chain()?.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
                            "
                          >
                            表格 3×3
                          </ElDropdownItem>
                          <ElDropdownItem
                            @click="
                              chain()?.insertTable({ rows: 4, cols: 4, withHeaderRow: true }).run()
                            "
                          >
                            表格 4×4
                          </ElDropdownItem>
                          <ElDropdownItem
                            @click="
                              chain()?.insertTable({ rows: 5, cols: 5, withHeaderRow: true }).run()
                            "
                          >
                            表格 5×5
                          </ElDropdownItem>
                          <template v-if="isInTable && canEdit">
                            <ElDropdownItem divided @click="chain()?.addColumnBefore().run()">
                              左侧插入列
                            </ElDropdownItem>
                            <ElDropdownItem @click="chain()?.addColumnAfter().run()">
                              右侧插入列
                            </ElDropdownItem>
                            <ElDropdownItem @click="chain()?.addRowBefore().run()">
                              上方插入行
                            </ElDropdownItem>
                            <ElDropdownItem @click="chain()?.addRowAfter().run()">
                              下方插入行
                            </ElDropdownItem>
                            <ElDropdownItem divided @click="chain()?.deleteColumn().run()">
                              删除当前列
                            </ElDropdownItem>
                            <ElDropdownItem @click="chain()?.deleteRow().run()"
                              >删除行</ElDropdownItem
                            >
                            <ElDropdownItem
                              divided
                              :disabled="!canChangeLayout"
                              @click="chain()?.mergeCells().run()"
                            >
                              合并单元格
                            </ElDropdownItem>
                            <ElDropdownItem
                              :disabled="!canChangeLayout"
                              @click="chain()?.splitCell().run()"
                            >
                              拆分
                            </ElDropdownItem>
                            <ElDropdownItem
                              divided
                              :disabled="!canChangeLayout"
                              @click="chain()?.toggleHeaderRow().run()"
                            >
                              切换表头行
                            </ElDropdownItem>
                            <ElDropdownItem
                              :disabled="!canChangeLayout"
                              @click="chain()?.toggleHeaderColumn().run()"
                            >
                              切换表头列
                            </ElDropdownItem>
                            <ElDropdownItem
                              divided
                              :disabled="!canChangeLayout"
                              @click="promptTableIdentifiers"
                            >
                              表格/单元格命名…
                            </ElDropdownItem>
                            <ElDropdownItem
                              divided
                              :disabled="!canChangeLayout"
                              @click="chain()?.deleteTable().run()"
                            >
                              删除表格
                            </ElDropdownItem>
                          </template>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                    <label
                      v-if="isInTable"
                      class="simple-editor__cell-min-height simple-editor__cell-min-height--ribbon"
                      title="当前行最小高度（mm）。空书写区可调矮；有字时仍跟内容走，不会裁切。"
                    >
                      <span class="simple-editor__cell-min-height-label">行高</span>
                      <span class="simple-editor__cell-min-height-row">
                        <ElInputNumber
                          :model-value="currentRowMinHeightMm ?? undefined"
                          size="small"
                          :min="EMR_MIN_CELL_HEIGHT_MM"
                          :max="cellMinHeightMaxMm"
                          :step="1"
                          :disabled="!canEdit"
                          controls-position="right"
                          class="simple-editor__cell-min-height-input"
                          @change="onRowMinHeightMmChange"
                        />
                        <span class="simple-editor__cell-min-height-unit">mm</span>
                      </span>
                    </label>
                  </div>
                  <span class="simple-editor__ribbon-group-label">表格</span>
                </div>

                <span class="simple-editor__ribbon-sep"></span>

                <div class="simple-editor__ribbon-group">
                  <div class="simple-editor__ribbon-group-body">
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="promptLink"
                    >
                      <ElIcon><Link /></ElIcon>
                      <span>链接</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="openImageFilePicker"
                    >
                      <ElIcon><Picture /></ElIcon>
                      <span>图片</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="promptImageUrl"
                    >
                      <ElIcon><Picture /></ElIcon>
                      <span>图片URL</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="chain()?.setHorizontalRule().run()"
                    >
                      <ElIcon><Minus /></ElIcon>
                      <span>横线</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="chain()?.insertEmrHorizontalDashed().run()"
                    >
                      <ElIcon><Minus /></ElIcon>
                      <span>虚线</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="chain()?.insertEmrToc().run()"
                    >
                      <ElIcon><List /></ElIcon>
                      <span>目录</span>
                    </button>
                    <button
                      type="button"
                      class="simple-editor__ribbon-cmd"
                      :disabled="!canChangeLayout"
                      @click="chain()?.insertEmrPageBreak().run()"
                    >
                      <ElIcon><Switch /></ElIcon>
                      <span>分页符</span>
                    </button>
                    <ElDropdown trigger="click" :disabled="!canEdit">
                      <button type="button" class="simple-editor__ribbon-cmd" :disabled="!canEdit">
                        <ElIcon><Calendar /></ElIcon>
                        <span>日期</span>
                      </button>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem @click="insertDateTime('date')">当前日期</ElDropdownItem>
                          <ElDropdownItem @click="insertDateTime('time')">当前时间</ElDropdownItem>
                          <ElDropdownItem @click="insertDateTime('datetime')">
                            当前日期时间
                          </ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                  </div>
                  <span class="simple-editor__ribbon-group-label">插入</span>
                </div>

                <template v-if="selectedDividerLine">
                  <span class="simple-editor__ribbon-sep"></span>
                  <div class="simple-editor__ribbon-group">
                    <div class="simple-editor__ribbon-group-body">
                      <div
                        class="simple-editor__divider-line-tools"
                        :title="selectedDividerLine.kind === 'solid' ? '实线横线' : '虚线'"
                      >
                        <span class="simple-editor__divider-line-tools-label">{{
                          selectedDividerLine.kind === 'solid' ? '横线' : '虚线'
                        }}</span>
                        <input
                          type="color"
                          class="simple-editor__divider-line-color-input"
                          :value="selectedDividerLine.lineColor"
                          :disabled="readOnly"
                          aria-label="分割线颜色"
                          @input="onDividerLineColorInput"
                        />
                        <ElInputNumber
                          :model-value="selectedDividerLine.lineWidth"
                          :min="1"
                          :max="24"
                          :step="1"
                          size="small"
                          controls-position="right"
                          class="simple-editor__divider-line-width"
                          :disabled="readOnly"
                          aria-label="分割线粗细"
                          @change="onDividerLineWidthChange"
                        />
                      </div>
                    </div>
                    <span class="simple-editor__ribbon-group-label">线条</span>
                  </div>
                </template>
              </template>
            </div>
          </div>

          <input
            ref="fileInputRef"
            class="simple-editor__file"
            type="file"
            accept="image/*"
            :disabled="readOnly"
            @change="onImageFile"
          />
        </div>

        <div v-if="chromeEditingBand" class="simple-editor__chrome-bar">
          <span class="simple-editor__chrome-bar-title">
            正在编辑{{ chromeEditingBand === 'header' ? '页眉' : '页脚' }}
          </span>
          <span class="simple-editor__chrome-bar-hint"
            >可用「开始」页签改字体、字号、加粗、对齐</span
          >
          <ElButton size="small" @click="openImageFilePicker">
            <ElIcon class="el-icon--left"><Picture /></ElIcon>
            插入图片
          </ElButton>
          <template v-if="chromeImgWidth != null">
            <span class="simple-editor__chrome-bar-hint">图片宽</span>
            <ElInputNumber
              v-model="chromeImgWidth"
              size="small"
              :min="16"
              :max="800"
              :step="4"
              controls-position="right"
              style="width: 110px"
              @change="onChromeImgWidthChange"
            />
            <span class="simple-editor__chrome-bar-hint">水平</span>
            <ElInputNumber
              v-model="chromeImgPosX"
              size="small"
              :min="0"
              :max="200"
              :step="1"
              controls-position="right"
              style="width: 100px"
              @update:model-value="onChromeImgPosXChange"
              @change="onChromeImgPosXChange"
            />
            <span class="simple-editor__chrome-bar-hint">垂直</span>
            <ElInputNumber
              v-model="chromeImgPosY"
              size="small"
              :min="0"
              :max="120"
              :step="1"
              controls-position="right"
              style="width: 100px"
              @update:model-value="onChromeImgPosYChange"
              @change="onChromeImgPosYChange"
            />
          </template>
          <ElDropdown trigger="click">
            <ElButton class="simple-editor__label-dropdown" text size="small">
              对齐
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="setChromeAlign('left')">左对齐</ElDropdownItem>
                <ElDropdownItem @click="setChromeAlign('center')">居中</ElDropdownItem>
                <ElDropdownItem @click="setChromeAlign('right')">右对齐</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <ElDropdown trigger="click">
            <ElButton class="simple-editor__label-dropdown" text size="small">
              插入页码
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="insertChromePageNumberPreset">
                  第 X 页 (共 Y 页)
                </ElDropdownItem>
                <ElDropdownItem @click="insertChromeField('page')">仅页码</ElDropdownItem>
                <ElDropdownItem divided disabled>插入字段</ElDropdownItem>
                <ElDropdownItem
                  v-for="f in EMR_PAGE_CHROME_FIELDS"
                  :key="f.key"
                  @click="insertChromeField(f.key)"
                >
                  {{ f.label }}
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <span class="simple-editor__toolbar-spacer"></span>
          <ElButton type="primary" size="small" @click="exitChromeEdit">关闭页眉和页脚</ElButton>
        </div>

        <EmrFindReplaceBar
          v-model:visible="findReplaceVisible"
          :editor="editor"
          :read-only="readOnly"
          :layout-locked="false"
        />

        <div
          ref="editorContentRef"
          class="simple-editor__content"
          @dblclick="onChromeBandDblClick"
        >
          <div
            v-if="surface === 'document' && !readOnly"
            class="simple-editor__fillable-rail-anchor"
            aria-hidden="true"
          >
            <EmrFillableShortcutRail />
          </div>
          <div v-if="showPageRuler" class="simple-editor__ruler" aria-hidden="true"></div>
          <EditorContent v-if="editor" :editor="editor" />
        </div>
      </div>
    </div>
  </div>
  <Teleport to="body">
    <ul
      v-if="ctxMenu.visible"
      class="simple-editor__ctx-menu"
      :style="{ left: `${ctxMenu.x}px`, top: `${ctxMenu.y}px` }"
      @click.stop
      @contextmenu.prevent
    >
      <li @click="ctxCut">剪切</li>
      <li @click="ctxCopy">复制</li>
      <li @click="ctxPaste">粘贴</li>
      <li class="simple-editor__ctx-menu-sep"></li>
      <li @click="insertDateTime('date')">插入日期</li>
      <li @click="insertDateTime('time')">插入时间</li>
      <li v-if="canChangeLayout" @click="ctxInsertPageBreak">插入分页符</li>
      <li v-if="ctxMenu.inFillable" @click="ctxDeleteFillable">删除填空</li>
      <template v-if="ctxMenu.inImage && canChangeLayout">
        <li class="simple-editor__ctx-menu-sep"></li>
        <li @click="ctxResizeImage">调整图片大小</li>
        <li @click="ctxRotateImage">旋转 90°</li>
        <li @click="ctxCropImage">裁切</li>
        <li @click="ctxDeleteImage">删除图片</li>
      </template>
      <template v-if="ctxMenu.inChromeImage && canChangeLayout">
        <li class="simple-editor__ctx-menu-sep"></li>
        <li @click="ctxResizeChromeImage">调整图片大小</li>
        <li @click="ctxDeleteChromeImage">删除图片</li>
      </template>
      <template v-if="ctxMenu.inTable">
        <li class="simple-editor__ctx-menu-sep"></li>
        <li class="simple-editor__ctx-menu-row-height" @click.stop @mousedown.stop>
          <label
            class="simple-editor__cell-min-height"
            title="当前行最小高度（mm）。空书写区可调矮；有字时仍跟内容走，不会裁切。"
          >
            <span class="simple-editor__cell-min-height-label">行高</span>
            <ElInputNumber
              :model-value="currentRowMinHeightMm ?? undefined"
              size="small"
              :min="EMR_MIN_CELL_HEIGHT_MM"
              :max="cellMinHeightMaxMm"
              :step="1"
              :disabled="!canEdit"
              controls-position="right"
              class="simple-editor__cell-min-height-input"
              @change="onRowMinHeightMmChange"
            />
            <span class="simple-editor__cell-min-height-unit">mm</span>
          </label>
        </li>
        <li
          @click="
            chain()?.addRowAfter().run();
            closeCtxMenu();
          "
        >
          下方插入行
        </li>
        <li
          @click="
            chain()?.addRowBefore().run();
            closeCtxMenu();
          "
        >
          上方插入行
        </li>
        <li
          @click="
            chain()?.deleteRow().run();
            closeCtxMenu();
          "
        >
          删除行
        </li>
        <li
          @click="
            chain()?.addColumnAfter().run();
            closeCtxMenu();
          "
        >
          右侧插入列
        </li>
        <li
          @click="
            chain()?.deleteColumn().run();
            closeCtxMenu();
          "
        >
          删除列
        </li>
      </template>
    </ul>
  </Teleport>
  <EmrInsertWidgetDialog
    v-model="insertWidgetDialogVisible"
    :widget="insertWidgetKind"
    @confirm="onInsertWidgetConfirm"
  />
  <EmrImageCropDialog v-model="imageCropOpen" :src="imageCropSrc" @confirm="onImageCropConfirm" />
  <ElDialog
    v-model="marginCustomVisible"
    title="页边距"
    width="420px"
    append-to-body
    destroy-on-close
    class="simple-editor__margin-dialog"
  >
    <div class="simple-editor__margin-custom-grid">
      <label class="simple-editor__margin-custom-field">
        <span>上</span>
        <ElInputNumber
          v-model="customMarginCm.top"
          :min="0.5"
          :max="8"
          :step="0.1"
          :precision="2"
          controls-position="right"
        />
        <span>厘米</span>
      </label>
      <label class="simple-editor__margin-custom-field">
        <span>下</span>
        <ElInputNumber
          v-model="customMarginCm.bottom"
          :min="0.5"
          :max="8"
          :step="0.1"
          :precision="2"
          controls-position="right"
        />
        <span>厘米</span>
      </label>
      <label class="simple-editor__margin-custom-field">
        <span>左</span>
        <ElInputNumber
          v-model="customMarginCm.left"
          :min="0.5"
          :max="8"
          :step="0.1"
          :precision="2"
          controls-position="right"
        />
        <span>厘米</span>
      </label>
      <label class="simple-editor__margin-custom-field">
        <span>右</span>
        <ElInputNumber
          v-model="customMarginCm.right"
          :min="0.5"
          :max="8"
          :step="0.1"
          :precision="2"
          controls-position="right"
        />
        <span>厘米</span>
      </label>
    </div>
    <template #footer>
      <ElButton @click="marginCustomVisible = false">取消</ElButton>
      <ElButton type="primary" @click="applyCustomPageMargins">确定</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.simple-editor__body {
  display: flex;
  min-height: 0;
  flex: 1;
}

.simple-editor__cell-min-height {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: nowrap;
}

.simple-editor__cell-min-height-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.simple-editor__cell-min-height--ribbon {
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  box-sizing: border-box;
  height: 68px;
  max-height: 68px;
  padding: 0 4px;
  gap: 2px;
}

.simple-editor__cell-min-height-input {
  width: 92px;
}

.simple-editor__cell-min-height :deep(.el-input-number) {
  width: 92px;
  flex-shrink: 0;
}

.simple-editor__cell-min-height-unit {
  color: var(--el-text-color-regular);
}

.simple-editor__sidebar {
  width: 220px;
  flex-shrink: 0;
  padding: 10px 10px 12px;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
  overflow: auto;
}

.simple-editor__sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}

.simple-editor__sidebar-hint {
  font-size: 11px;
  color: #6b7280;
  line-height: 1.4;
  margin: 0 0 8px;
}

.simple-editor__sidebar-search {
  margin-bottom: 8px;
}

.simple-editor__sidebar-empty {
  font-size: 12px;
  color: #9ca3af;
  padding: 8px 2px;
}

.simple-editor__meta-group {
  margin-bottom: 4px;
}

.simple-editor__meta-group-head {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
}

.simple-editor__meta-group-head:hover {
  color: #1f2937;
}

.simple-editor__meta-group-caret {
  flex-shrink: 0;
  font-size: 12px;
  transition: transform 0.15s ease;
  transform: rotate(-90deg);
}

.simple-editor__meta-group-caret--open {
  transform: rotate(0deg);
}

.simple-editor__meta-group-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.simple-editor__meta-group-count {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
  color: #9ca3af;
}

.simple-editor__meta-group-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 0 6px;
}

.simple-editor__meta-chip {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 6px;
  border: 1px dashed #c7d2fe;
  background: #eef2ff;
  cursor: grab;
  user-select: none;
}

.simple-editor__meta-chip:active {
  cursor: grabbing;
}

.simple-editor__meta-chip--chrome-target {
  cursor: pointer;
  border-color: #93c5fd;
  background: #eff6ff;
}

.simple-editor__meta-chip-label {
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  color: #312e81;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.simple-editor__meta-chip-badge {
  flex-shrink: 0;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.5;
}

.simple-editor__meta-chip-badge--locked {
  background: #e0e7ff;
  color: #3730a3;
}

.simple-editor__meta-chip-badge--edit {
  background: #dcfce7;
  color: #166534;
}

.simple-editor__main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.simple-editor__toolbar-text-btn {
  flex-shrink: 0;
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  color: #4b5563;
  border-radius: 6px;
}

.simple-editor__toolbar-text-btn:hover:not(:disabled) {
  background: #eef2f7;
}

.simple-editor__icon-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  color: #4b5563;
  border-radius: 6px;
  font-weight: 600;
}

.simple-editor__icon-btn:hover {
  background: #eef2f7;
}

.simple-editor__icon-btn.is-active {
  background: #e0e7ff;
  color: #3730a3;
}

.simple-editor__label-dropdown {
  min-width: 72px;
  height: 28px;
  padding: 0 8px;
  color: #374151;
  border-radius: 6px;
  font-size: 13px;
}

.simple-editor__label-dropdown:hover {
  background: #eef2f7;
}

.simple-editor__divider-line-tools {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}

.simple-editor__divider-line-tools-label {
  font-size: 12px;
  color: #6b7280;
  user-select: none;
}

.simple-editor__divider-line-width {
  width: 92px;
}

.simple-editor__divider-line-width :deep(.el-input__wrapper) {
  padding-right: 6px;
  padding-left: 8px;
}

/* 勿用 .simple-editor__color-input：该类为「叠在 A 字上」设计，含 absolute + inset:0，无定位父级时会相对视口铺满整页 */
.simple-editor__divider-line-color-input {
  flex-shrink: 0;
  box-sizing: border-box;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
  background: transparent;
}

.simple-editor__divider-line-color-input:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.simple-editor__mark-b {
  font-weight: 700;
}

.simple-editor__mark-i {
  font-style: italic;
}

.simple-editor__mark-u {
  text-decoration: underline;
}

.simple-editor__mark-s {
  text-decoration: line-through;
}

.simple-editor__mark-sup sup,
.simple-editor__mark-sub sub {
  font-size: 0.65em;
  font-weight: 600;
}

.simple-editor__color-group {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 26px;
  padding: 0 4px;
  border-radius: 3px;
}

.simple-editor__color-group:hover {
  background: #e8eaed;
}

.simple-editor__color-label {
  position: relative;
  z-index: 0;
  display: inline-block;
  min-width: 18px;
  padding: 0 2px 1px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  color: #374151;
  user-select: none;
  pointer-events: none;
}

/* 原生取色器叠在 A / ab 上，去掉 Element Plus 色块触发器 */
.simple-editor__color-input {
  position: absolute;
  z-index: 1;
  inset: 0;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  cursor: pointer;
  opacity: 0;
}

.simple-editor__color-label--fore {
  border-bottom: 3px solid #dc2626;
}

.simple-editor__color-label--hilite {
  font-style: italic;
  font-weight: 500;
  border-bottom: 3px solid #facc15;
}

.simple-editor__align-bars {
  display: inline-flex;
  flex-direction: column;
  flex-shrink: 0;
  justify-content: center;
  gap: 2px;
  width: 14px;
  height: 14px;
  vertical-align: middle;
}

.simple-editor__align-bars span {
  display: block;
  height: 2px;
  border-radius: 1px;
  background: currentColor;
}

.simple-editor__align-bars--left span {
  align-self: flex-start;
}

.simple-editor__align-bars--left span:nth-child(1) {
  width: 100%;
}

.simple-editor__align-bars--left span:nth-child(2) {
  width: 78%;
}

.simple-editor__align-bars--left span:nth-child(3) {
  width: 56%;
}

.simple-editor__align-bars--left span:nth-child(4) {
  width: 100%;
}

.simple-editor__align-bars--center span {
  align-self: center;
}

.simple-editor__align-bars--center span:nth-child(1) {
  width: 100%;
}

.simple-editor__align-bars--center span:nth-child(2) {
  width: 78%;
}

.simple-editor__align-bars--center span:nth-child(3) {
  width: 56%;
}

.simple-editor__align-bars--center span:nth-child(4) {
  width: 100%;
}

.simple-editor__align-bars--right span {
  align-self: flex-end;
}

.simple-editor__align-bars--right span:nth-child(1) {
  width: 100%;
}

.simple-editor__align-bars--right span:nth-child(2) {
  width: 78%;
}

.simple-editor__align-bars--right span:nth-child(3) {
  width: 56%;
}

.simple-editor__align-bars--right span:nth-child(4) {
  width: 100%;
}

.simple-editor__align-bars--justify span {
  width: 100%;
  align-self: stretch;
}

.simple-editor__divider {
  width: 1px;
  height: 22px;
  background: #e5e7eb;
}

.simple-editor__file {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.simple-editor__save-status {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  user-select: none;
  line-height: 1.4;
}

.simple-editor__save-status--saved {
  color: #16a34a;
}

.simple-editor__save-status--saving {
  color: #d97706;
}

.simple-editor__save-status--dirty {
  color: #dc2626;
}

.simple-editor__ctx-menu {
  position: fixed;
  z-index: 4000;
  margin: 0;
  padding: 4px 0;
  min-width: 160px;
  list-style: none;
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color, #e5e7eb);
  border-radius: 4px;
}

.simple-editor__ctx-menu li {
  padding: 6px 12px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  cursor: pointer;
}

.simple-editor__ctx-menu li:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.simple-editor__ctx-menu-row-height {
  cursor: default;
}

.simple-editor__ctx-menu li.simple-editor__ctx-menu-row-height:hover {
  background: transparent;
}

.simple-editor__ctx-menu-sep {
  height: 1px;
  margin: 4px 0;
  padding: 0;
  background: var(--el-border-color-lighter, #ebeef5);
  pointer-events: none;
}
</style>
