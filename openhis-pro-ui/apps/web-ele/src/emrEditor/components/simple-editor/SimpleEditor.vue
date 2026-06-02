<script setup lang="ts">
import type { Editor, JSONContent } from '@tiptap/core';

import type { EmrEditorCaps } from './use-emr-editor-caps';

import type { EmrMetadataField } from '#/emrEditor/types';

import { uploadEditorImageApi } from '#/api/emr/editor-image';

import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';

import { ArrowDown, Picture, RefreshLeft, RefreshRight, Search } from '@element-plus/icons-vue';
import { NodeSelection } from '@tiptap/pm/state';
import { EditorContent, useEditor } from '@tiptap/vue-3';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import { getDataElementDef, serializeOptionsFromDef } from './data-element-registry';
import {
  defaultEmrEditorDoc,
  EDITOR_FONT_FAMILY_OPTIONS,
  EDITOR_FONT_SIZE_OPTIONS,
  EDITOR_LINE_HEIGHT_OPTIONS,
  EDITOR_PAGE_MARGIN_PRESETS,
  EDITOR_PARAGRAPH_SPACING_OPTIONS,
  EDITOR_TEXT_INDENT_OPTIONS,
} from './editor-defaults';
import { EMR_CONTENT_LOCK_BYPASS_META } from './extensions/emr-content-lock';
import { EMR_FIXED_TEXT_ALLOW_MUTATE_META } from './extensions/emr-fixed-text';
import EmrFindReplaceBar from './EmrFindReplaceBar.vue';
import { getSimpleEditorExtensions } from './get-simple-editor-extensions';
import { useEmrAutoSave } from './use-emr-auto-save';

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
     * 固定模板：除「可编辑元数据域 + 自由文本」外，正文 ProseMirror 层不可改（防误删版式/乱打字）。
     * 工具栏排版项会一并禁用；与工具栏「固定模板 / 修改模板」配合请使用 v-model:layout-locked。
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
  }>(),
  {
    autoRestoreDraft: true,
    autoSaveDebounceMs: 3000,
    docId: '',
    metadataFields: () => [],
    metadataValues: () => ({}),
    modelValue: null,
    pageMargins: undefined,
    placeholder: undefined,
    readOnly: false,
    surface: 'document',
    layoutLocked: false,
  },
);

const emit = defineEmits<{
  change: [];
  'update:layoutLocked': [boolean];
  'update:modelValue': [JSONContent | null];
  'update:pageMargins': [{ bottom: string; left: string; right: string; top: string }];
}>();

function applyPageMarginPreset(preset: (typeof EDITOR_PAGE_MARGIN_PRESETS)[number]) {
  emit('update:pageMargins', { ...preset.value });
}

function lockTemplateLayout() {
  if (props.readOnly) return;
  emit('update:layoutLocked', true);
  ElMessage.success('模板已固定：仅可编辑可改元数据域与自由文本');
}

function unlockTemplateLayout() {
  if (props.readOnly) return;
  emit('update:layoutLocked', false);
  ElMessage.info('已允许修改全文版式与正文');
}

provide(
  'emrEditorSurface',
  computed(() => props.surface),
);

provide(
  'emrEditorLayoutLocked',
  computed(() => props.layoutLocked),
);

const emrEditorCaps = computed<EmrEditorCaps>(() => ({
  layoutLocked: props.layoutLocked,
  readOnly: props.readOnly,
}));
provide('emrEditorCaps', emrEditorCaps);

const autoSave = props.docId
  ? useEmrAutoSave({
      debounceMs: props.autoSaveDebounceMs,
      docId: props.docId,
      getContent: () => editor.value?.getJSON() ?? null,
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
          .setContent(content, { emitUpdate: false })
          .run();
        emit('update:modelValue', content);
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

const pageMarginStyle = computed(() => {
  const m = props.pageMargins;
  if (!m) return undefined;
  const vars: Record<string, string> = {};
  if (m.top) vars['--simple-editor-margin-top'] = m.top;
  if (m.right) vars['--simple-editor-margin-right'] = m.right;
  if (m.bottom) vars['--simple-editor-margin-bottom'] = m.bottom;
  if (m.left) vars['--simple-editor-margin-left'] = m.left;
  return Object.keys(vars).length > 0 ? vars : undefined;
});

const fileInputRef = ref<HTMLInputElement | null>(null);
const findReplaceVisible = ref(false);

/** 强制工具栏随选区/事务刷新（editor 为 markRaw） */
const toolbarRevision = ref(0);

const mergedMetaValues = computed(() => ({
  ...(props.metadataValues ?? {}),
}));

/**
 * 将 metadata 写入正文中的元数据节点。
 * @param ed 编辑器实例
 * @param vals fieldKey / elementId → 展示值
 * @param readonlyOnly 为 true 时仅更新只读绑定域；为 false 时在套用模版后预填只读+可改域
 */
function applyMetadataValuesToDocNodes(
  ed: Editor,
  vals: Record<string, string>,
  readonlyOnly: boolean,
) {
  const { tr, doc } = ed.state;
  doc.descendants((node, pos) => {
    if (node.type.name === 'emrMetadataChip') {
      if (readonlyOnly && node.attrs.editable !== false) return;
      const fk = node.attrs.fieldKey as string;
      if (!fk) return;
      const nextVal = vals[fk] ?? '';
      if (String(node.attrs.value ?? '') === String(nextVal)) return;
      tr.setNodeMarkup(pos, undefined, {
        ...node.attrs,
        value: nextVal,
      });
      return;
    }
    if (node.type.name === 'emrDataElement') {
      if (readonlyOnly && node.attrs.editable !== false) return;
      const id = String(node.attrs.elementId ?? '');
      if (!id) return;
      const nextVal = vals[id] ?? '';
      if (String(node.attrs.value ?? '') === String(nextVal)) return;
      tr.setNodeMarkup(pos, undefined, {
        ...node.attrs,
        value: nextVal,
      });
    }
  });
  if (tr.docChanged) {
    tr.setMeta(EMR_CONTENT_LOCK_BYPASS_META, true);
    ed.view.dispatch(tr);
  }
}

/** 供 handleDrop 使用，避免 useEditor 初始化时闭包拿不到实例 */
let editorRefForDrop: Editor | undefined;

const editor = useEditor({
  extensions: getSimpleEditorExtensions({ placeholder: props.placeholder }),
  content: props.modelValue ?? defaultEmrEditorDoc,
  /** 固定模板时关 ProseMirror 根编辑；可录入区由 NodeView 内 contenteditable/disabled 例外放行 */
  editable: !props.readOnly && !props.layoutLocked,
  onCreate: ({ editor: ed }) => {
    editorRefForDrop = ed;
    if (ed.storage.emrContentLock) {
      ed.storage.emrContentLock.enabled = props.layoutLocked;
    }
  },
  onDestroy: () => {
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
      if (props.layoutLocked) return false;
      const raw = event.dataTransfer?.getData('application/emr-metadata');
      if (!raw) return false;
      try {
        const payload = JSON.parse(raw) as {
          editable?: boolean;
          fieldKey: string;
          label: string;
        };
        if (!payload?.fieldKey) return false;
        const coords = view.posAtCoords({
          left: event.clientX,
          top: event.clientY,
        });
        if (!coords) return false;
        // 制作模板：正文占位默认展示 label；套用/书写文书时用患者元数据
        const value =
          props.surface === 'template'
            ? String(payload.label ?? '')
            : (mergedMetaValues.value[payload.fieldKey] ?? '');
        const editable = payload.editable !== false;
        event.preventDefault();
        const def = getDataElementDef(payload.fieldKey);
        const optionsJson = def ? serializeOptionsFromDef(def) : '[]';
        ed.chain()
          .focus()
          .insertContentAt(coords.pos, {
            type: 'emrDataElement',
            attrs: {
              editable,
              elementId: payload.fieldKey,
              labelSnapshot: payload.label,
              optionsJson,
              placeholder: payload.label ?? '',
              value,
              widgetOverride: '',
            },
          })
          .run();
        return true;
      } catch {
        return false;
      }
    },
  },
  onUpdate: ({ editor: ed }) => {
    emit('update:modelValue', ed.getJSON());
    emit('change');
    autoSave?.markDirty();
  },
  onTransaction: () => {
    toolbarRevision.value += 1;
  },
});

watch(
  () => props.modelValue,
  async (val) => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    const cur = JSON.stringify(ed.getJSON());
    const next = JSON.stringify(val ?? defaultEmrEditorDoc);
    if (cur === next) return;
    ed.chain()
      .setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true)
      .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true)
      .setContent(val ?? defaultEmrEditorDoc, { emitUpdate: false })
      .run();
    await nextTick();
    if (props.surface === 'document') {
      applyMetadataValuesToDocNodes(ed, mergedMetaValues.value, false);
    }
  },
  { deep: true },
);

watch(
  () => [props.readOnly, props.layoutLocked] as const,
  ([ro, locked]) => {
    const ed = editor.value;
    if (!ed || ed.isDestroyed) return;
    ed.setEditable(!ro && !locked);
    if (ed.storage.emrContentLock) {
      ed.storage.emrContentLock.enabled = locked;
    }
    toolbarRevision.value += 1;
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
});

onBeforeUnmount(() => {
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

/** 与 editor.isEditable 一致：固定模板时根文档不可键入，工具栏同步变灰 */
const canEdit = computed(() => !!editor.value && !props.readOnly && editor.value.isEditable);

/** 可改排版：插入、块样式、对齐、列表、元数据拖拽等 */
const canChangeLayout = computed(() => canEdit.value && !props.layoutLocked);

const blockTypeLabel = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  if (!ed) return '正文';
  if (ed.isActive('heading', { level: 1 })) return '标题 1';
  if (ed.isActive('heading', { level: 2 })) return '标题 2';
  if (ed.isActive('heading', { level: 3 })) return '标题 3';
  return '正文';
});

const fontFamilyLabel = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  const fallback = EDITOR_FONT_FAMILY_OPTIONS[0]?.label ?? '宋体';
  if (!ed) return fallback;
  const attrs = ed.getAttributes('textStyle') as Record<string, string | undefined>;
  const ff = attrs.fontFamily?.trim();
  if (!ff) return fallback;
  const hit = EDITOR_FONT_FAMILY_OPTIONS.find((o) => o.value === ff);
  return hit?.label ?? fallback;
});

const fontSizeLabel = computed(() => {
  void toolbarRevision.value;
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

const textIndentLabel = computed(() => {
  void toolbarRevision.value;
  const ed = editor.value;
  const fallback = '缩进';
  if (!ed) return fallback;
  const $from = ed.state.selection.$from;
  for (let d = $from.depth; d > 0; d--) {
    const n = $from.node(d);
    if (n.type.name === 'paragraph') {
      const v = n.attrs.textIndent;
      if (typeof v === 'string' && v.trim()) {
        const hit = EDITOR_TEXT_INDENT_OPTIONS.find((o) => o.value === v);
        return hit?.label ?? `缩进 ${v}`;
      }
      break;
    }
  }
  return fallback;
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

function onMetaDragStart(e: DragEvent, field: EmrMetadataField) {
  if (props.layoutLocked || props.readOnly) {
    e.preventDefault();
    return;
  }
  const dt = e.dataTransfer;
  if (!dt) return;
  dt.setData(
    'application/emr-metadata',
    JSON.stringify({
      editable: field.editable !== false,
      fieldKey: field.fieldKey,
      label: field.label,
    }),
  );
  dt.effectAllowed = 'copy';
}

async function promptLink() {
  if (!editor.value || props.readOnly || props.layoutLocked) return;
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
  if (!editor.value || props.readOnly || props.layoutLocked) return;
  try {
    const { value } = await ElMessageBox.prompt('图片 URL', '插入图片', {
      inputPlaceholder: 'https://',
      confirmButtonText: '插入',
      cancelButtonText: '取消',
    });
    const src = value?.trim();
    if (!src) return;
    chain()?.setImage({ src }).run();
  } catch {
    /* cancel */
  }
}

/** 模板版式标签：行内固定字，不可逐字编辑，可整块删除 */
async function promptInsertFixedText() {
  if (!editor.value || props.readOnly || props.layoutLocked) return;
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

/** 行内自由文本：可自定义括号内灰色提示标签 */
async function promptInsertFreeText() {
  if (!editor.value || props.readOnly || props.layoutLocked) return;
  try {
    const { value } = await ElMessageBox.prompt(
      '未填写内容时在方括号内显示的灰色提示文字。',
      '插入自由文本',
      {
        inputPlaceholder: '例如：姓名',
        inputValue: '填写',
        confirmButtonText: '插入',
        cancelButtonText: '取消',
      },
    );
    const label = value?.trim() || '填写';
    chain()?.insertEmrFreeText({ label, placeholder: label }).run();
  } catch {
    /* cancel */
  }
}

async function onImageFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !editor.value || props.readOnly || props.layoutLocked) return;
  input.value = '';
  try {
    const src = await uploadEditorImageApi(file);
    editor.value.chain().focus().setImage({ src }).run();
    return;
  } catch {
    ElMessage.warning('图片上传失败，已使用本地嵌入（体积较大，建议稍后重试上传）');
  }
  const reader = new FileReader();
  reader.addEventListener('load', () => {
    const src = reader.result as string;
    editor.value?.chain().focus().setImage({ src }).run();
  });
  reader.readAsDataURL(file);
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
  if (props.readOnly || props.layoutLocked) return;
  if (!hex) {
    chain()?.unsetColor().run();
    return;
  }
  chain()?.setColor(hex).run();
}

function setHighlight(hex: null | string) {
  if (props.readOnly || props.layoutLocked) return;
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
  if (props.layoutLocked || props.readOnly) return;
  fileInputRef.value?.click();
}

function getJSON(): JSONContent | null {
  return editor.value?.getJSON() ?? null;
}

function getHTML(): string {
  return editor.value?.getHTML() ?? '';
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
      run(JSON.parse(content) as JSONContent);
    } catch {
      run(`<p>${content}</p>`);
    }
    return;
  }
  run(content);
}

defineExpose({
  getEditor: (): Editor | null => editor.value ?? null,
  getJSON,
  getHTML,
  setContent,
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
});
</script>

<template>
  <div
    class="simple-editor"
    :class="{
      'simple-editor--layout-locked': layoutLocked,
      'simple-editor--surface-template': surface === 'template',
    }"
    :style="pageMarginStyle"
  >
    <div class="simple-editor__body">
      <aside v-if="surface === 'template'" class="simple-editor__sidebar" aria-label="元数据字段">
        <div class="simple-editor__sidebar-title">元数据</div>
        <p v-if="!layoutLocked" class="simple-editor__sidebar-hint">
          拖到正文插入字段：正文内仅展示取值；「只读」随患者信息更新，「可改」可手工修正。
        </p>
        <p v-else class="simple-editor__sidebar-hint">版式已锁定，不可从侧栏拖入新字段。</p>
        <div
          v-for="f in metadataFields"
          :key="f.fieldKey"
          class="simple-editor__meta-chip"
          :class="{ 'simple-editor__meta-chip--layout-locked': layoutLocked }"
          :draggable="canChangeLayout"
          @dragstart="onMetaDragStart($event, f)"
        >
          <div class="simple-editor__meta-chip-head">
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
          <span class="simple-editor__meta-chip-key">{{ f.fieldKey }}</span>
          <span class="simple-editor__meta-chip-val">
            {{ mergedMetaValues[f.fieldKey] ?? '—' }}
          </span>
        </div>
      </aside>

      <div class="simple-editor__main">
        <div class="simple-editor__toolbar">
          <ElButton
            class="simple-editor__icon-btn"
            text
            :disabled="!canEdit"
            @click="chain()?.undo().run()"
          >
            <ElIcon><RefreshLeft /></ElIcon>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :disabled="!canEdit"
            @click="chain()?.redo().run()"
          >
            <ElIcon><RefreshRight /></ElIcon>
          </ElButton>

          <span class="simple-editor__divider"></span>
          <ElButton
            class="simple-editor__toolbar-text-btn"
            text
            size="small"
            type="primary"
            :disabled="readOnly || layoutLocked"
            @click="lockTemplateLayout"
          >
            固定模板
          </ElButton>
          <ElButton
            class="simple-editor__toolbar-text-btn"
            text
            size="small"
            :disabled="readOnly || !layoutLocked"
            @click="unlockTemplateLayout"
          >
            修改模板
          </ElButton>

          <span class="simple-editor__divider"></span>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              页边距
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem
                  v-for="p in EDITOR_PAGE_MARGIN_PRESETS"
                  :key="p.label"
                  @click="applyPageMarginPreset(p)"
                >
                  {{ p.label }}
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <span class="simple-editor__divider"></span>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              插入
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="chain()?.setHorizontalRule().run()">横线</ElDropdownItem>
                <ElDropdownItem @click="chain()?.insertEmrHorizontalDashed().run()">
                  虚线
                </ElDropdownItem>
                <ElDropdownItem @click="chain()?.insertEmrToc().run()">目录</ElDropdownItem>
                <ElDropdownItem @click="chain()?.insertEmrPageBreak().run()">分页符</ElDropdownItem>
                <ElDropdownItem @click="promptInsertFixedText">固定文字</ElDropdownItem>
                <ElDropdownItem @click="promptInsertFreeText">自由文本</ElDropdownItem>
                <ElDropdownItem divided @click="chain()?.insertEmrTextField().run()">
                  文本框
                </ElDropdownItem>
                <ElDropdownItem @click="chain()?.insertEmrCheckboxField().run()">
                  复选框
                </ElDropdownItem>
                <ElDropdownItem @click="chain()?.insertEmrSelectField().run()">
                  下拉框
                </ElDropdownItem>
                <ElDropdownItem
                  divided
                  @click="chain()?.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()"
                >
                  表格 3×3
                </ElDropdownItem>
                <ElDropdownItem
                  @click="chain()?.insertTable({ rows: 4, cols: 4, withHeaderRow: true }).run()"
                >
                  表格 4×4
                </ElDropdownItem>
                <ElDropdownItem
                  @click="chain()?.insertTable({ rows: 5, cols: 5, withHeaderRow: true }).run()"
                >
                  表格 5×5
                </ElDropdownItem>
                <ElDropdownItem divided @click="promptLink">插入链接</ElDropdownItem>
                <ElDropdownItem @click="promptImageUrl">
                  <ElIcon><Picture /></ElIcon>
                  <span style="margin-left: 6px">图片 URL</span>
                </ElDropdownItem>
                <ElDropdownItem @click="openImageFilePicker">上传图片</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <template v-if="selectedDividerLine">
            <span class="simple-editor__divider"></span>
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
          </template>

          <template v-if="isInTable && canChangeLayout">
            <span class="simple-editor__divider"></span>
            <ElDropdown trigger="click">
              <ElButton class="simple-editor__label-dropdown" text>
                表格
                <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
              </ElButton>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem @click="chain()?.addColumnBefore().run()">
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
                  <ElDropdownItem @click="chain()?.deleteRow().run()">删除行</ElDropdownItem>
                  <ElDropdownItem divided @click="chain()?.mergeCells().run()">
                    合并单元格
                  </ElDropdownItem>
                  <ElDropdownItem @click="chain()?.splitCell().run()">拆分</ElDropdownItem>
                  <ElDropdownItem divided @click="chain()?.toggleHeaderRow().run()">
                    切换表头行
                  </ElDropdownItem>
                  <ElDropdownItem @click="chain()?.toggleHeaderColumn().run()">
                    切换表头列
                  </ElDropdownItem>
                  <ElDropdownItem divided @click="promptTableIdentifiers">
                    表格/单元格命名…
                  </ElDropdownItem>
                  <ElDropdownItem divided @click="chain()?.deleteTable().run()">
                    删除表格
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>
          </template>

          <span class="simple-editor__divider"></span>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              {{ blockTypeLabel }}
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="chain()?.setParagraph().run()">正文</ElDropdownItem>
                <ElDropdownItem @click="chain()?.toggleHeading({ level: 1 }).run()">
                  标题 1
                </ElDropdownItem>
                <ElDropdownItem @click="chain()?.toggleHeading({ level: 2 }).run()">
                  标题 2
                </ElDropdownItem>
                <ElDropdownItem @click="chain()?.toggleHeading({ level: 3 }).run()">
                  标题 3
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              {{ fontFamilyLabel }}
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="chain()?.unsetFontFamily().run()">默认字体</ElDropdownItem>
                <ElDropdownItem
                  v-for="(o, idx) in EDITOR_FONT_FAMILY_OPTIONS"
                  :key="o.value"
                  :divided="idx === 0"
                  @click="chain()?.setFontFamily(o.value).run()"
                >
                  {{ o.label }}
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              {{ fontSizeLabel }}
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="chain()?.unsetFontSize().run()">默认字号</ElDropdownItem>
                <ElDropdownItem
                  v-for="(o, idx) in EDITOR_FONT_SIZE_OPTIONS"
                  :key="o.value"
                  :divided="idx === 0"
                  @click="chain()?.setFontSize(o.value).run()"
                >
                  {{ o.label }}
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <ElDropdown trigger="click" :disabled="!canChangeLayout">
            <ElButton class="simple-editor__label-dropdown" text :disabled="!canChangeLayout">
              {{ lineHeightLabel }}
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
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

          <ElDropdown trigger="click" :disabled="!canChangeLayout || !canSetParagraphSpacing">
            <ElButton
              class="simple-editor__label-dropdown"
              text
              :disabled="!canChangeLayout || !canSetParagraphSpacing"
              title="段前/段后间距（仅段落）"
            >
              段间距
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
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

          <ElDropdown trigger="click" :disabled="!canChangeLayout || !canSetTextIndent">
            <ElButton
              class="simple-editor__label-dropdown"
              text
              :disabled="!canChangeLayout || !canSetTextIndent"
              title="首行缩进（仅段落）"
            >
              {{ textIndentLabel }}
              <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="chain()?.unsetTextIndent().run()">无缩进</ElDropdownItem>
                <ElDropdownItem
                  v-for="(o, idx) in EDITOR_TEXT_INDENT_OPTIONS"
                  :key="o.value"
                  :divided="idx === 0"
                  @click="chain()?.setTextIndent(o.value).run()"
                >
                  {{ o.label }}
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <span class="simple-editor__divider"></span>

          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.bold }"
            :disabled="!canEdit"
            @click="chain()?.toggleBold().run()"
          >
            <span class="simple-editor__mark-b">B</span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.italic }"
            :disabled="!canEdit"
            @click="chain()?.toggleItalic().run()"
          >
            <span class="simple-editor__mark-i">I</span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.underline }"
            :disabled="!canEdit"
            @click="chain()?.toggleUnderline().run()"
          >
            <span class="simple-editor__mark-u">U</span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.strike }"
            :disabled="!canEdit"
            @click="chain()?.toggleStrike().run()"
          >
            <span class="simple-editor__mark-s">S</span>
          </ElButton>

          <span class="simple-editor__divider"></span>

          <ElButton
            class="simple-editor__toolbar-text-btn"
            text
            size="small"
            :disabled="!canChangeLayout || !canFixSelection"
            title="将选中的普通文字变为版式固定字（同一段内、勿含元数据等）"
            @click="fixSelectedTextAsFixed"
          >
            所选固定
          </ElButton>
          <ElButton
            class="simple-editor__toolbar-text-btn"
            text
            size="small"
            :disabled="!canChangeLayout || !canUnfixFixedText"
            title="将固定字还原为普通文字后才可删除或改写（须整块选中固定字）"
            @click="unfixSelectedFixedText"
          >
            取消固定
          </ElButton>

          <span class="simple-editor__divider"></span>

          <div class="simple-editor__color-group" title="字体颜色（点击选色；Shift+点击清除）">
            <span class="simple-editor__color-label simple-editor__color-label--fore">A</span>
            <input
              type="color"
              class="simple-editor__color-input"
              value="#000000"
              :disabled="!canChangeLayout"
              @mousedown="onColorInputMouseDown"
              @input="onForeColorInput"
            />
          </div>
          <div class="simple-editor__color-group" title="文本突出显示（点击选色；Shift+点击清除）">
            <span class="simple-editor__color-label simple-editor__color-label--hilite">ab</span>
            <input
              type="color"
              class="simple-editor__color-input"
              value="#fff59d"
              :disabled="!canChangeLayout"
              @mousedown="onHighlightInputMouseDown"
              @input="onHighlightColorInput"
            />
          </div>

          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.superscript }"
            :disabled="!canEdit"
            title="上标"
            @click="toggleSuperscript"
          >
            <span class="simple-editor__mark-sup">x<sup>2</sup></span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.subscript }"
            :disabled="!canEdit"
            title="下标"
            @click="toggleSubscript"
          >
            <span class="simple-editor__mark-sub">x<sub>2</sub></span>
          </ElButton>

          <span class="simple-editor__divider"></span>

          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.alignLeft }"
            :disabled="!canChangeLayout"
            title="左对齐"
            @click="chain()?.setTextAlign('left').run()"
          >
            <span
              class="simple-editor__align-bars simple-editor__align-bars--left"
              aria-hidden="true"
            >
              <span></span><span></span><span></span><span></span>
            </span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.alignCenter }"
            :disabled="!canChangeLayout"
            title="居中"
            @click="chain()?.setTextAlign('center').run()"
          >
            <span
              class="simple-editor__align-bars simple-editor__align-bars--center"
              aria-hidden="true"
            >
              <span></span><span></span><span></span><span></span>
            </span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.alignRight }"
            :disabled="!canChangeLayout"
            title="右对齐"
            @click="chain()?.setTextAlign('right').run()"
          >
            <span
              class="simple-editor__align-bars simple-editor__align-bars--right"
              aria-hidden="true"
            >
              <span></span><span></span><span></span><span></span>
            </span>
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :class="{ 'is-active': tbar.alignJustify }"
            :disabled="!canChangeLayout"
            title="两端对齐"
            @click="chain()?.setTextAlign('justify').run()"
          >
            <span
              class="simple-editor__align-bars simple-editor__align-bars--justify"
              aria-hidden="true"
            >
              <span></span><span></span><span></span><span></span>
            </span>
          </ElButton>

          <span class="simple-editor__divider"></span>

          <ElButton
            class="simple-editor__icon-btn"
            text
            :disabled="!canChangeLayout"
            title="项目符号"
            @click="chain()?.toggleBulletList().run()"
          >
            •
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :disabled="!canChangeLayout"
            title="编号"
            @click="chain()?.toggleOrderedList().run()"
          >
            1.
          </ElButton>
          <ElButton
            class="simple-editor__icon-btn"
            text
            :disabled="!canChangeLayout"
            title="任务列表"
            @click="chain()?.toggleTaskList().run()"
          >
            ☑
          </ElButton>

          <ElButton
            class="simple-editor__toolbar-text-btn"
            text
            size="small"
            title="查找替换 (Ctrl+F)"
            @click="toggleFindReplaceBar"
          >
            <ElIcon><Search /></ElIcon>
            查找
          </ElButton>

          <span class="simple-editor__toolbar-spacer"></span>

          <span
            v-if="autoSave"
            class="simple-editor__save-status"
            :class="{
              'simple-editor__save-status--dirty': autoSave.status.value === 'dirty',
              'simple-editor__save-status--saving': autoSave.status.value === 'saving',
              'simple-editor__save-status--saved': autoSave.status.value === 'saved',
            }"
          >
            {{ saveStatusLabel }}
          </span>

          <input
            ref="fileInputRef"
            class="simple-editor__file"
            type="file"
            accept="image/*"
            :disabled="readOnly || layoutLocked"
            @change="onImageFile"
          />
        </div>

        <EmrFindReplaceBar
          v-model:visible="findReplaceVisible"
          :editor="editor"
          :read-only="readOnly"
          :replace-disabled="layoutLocked"
        />

        <div class="simple-editor__content">
          <EditorContent v-if="editor" :editor="editor" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.simple-editor__body {
  display: flex;
  min-height: 0;
  flex: 1;
}

.simple-editor__sidebar {
  width: 200px;
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
  margin: 0 0 10px;
}

.simple-editor__meta-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 8px;
  margin-bottom: 8px;
  border-radius: 8px;
  border: 1px dashed #c7d2fe;
  background: #eef2ff;
  cursor: grab;
  user-select: none;
}

.simple-editor__meta-chip:active {
  cursor: grabbing;
}

.simple-editor__meta-chip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.simple-editor__meta-chip-label {
  font-size: 13px;
  font-weight: 600;
  color: #312e81;
}

.simple-editor__meta-chip-badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.4;
}

.simple-editor__meta-chip-badge--locked {
  background: #e0e7ff;
  color: #3730a3;
}

.simple-editor__meta-chip-badge--edit {
  background: #dcfce7;
  color: #166534;
}

.simple-editor__meta-chip-key {
  font-size: 11px;
  color: #6366f1;
  font-family: ui-monospace, monospace;
}

.simple-editor__meta-chip-val {
  font-size: 12px;
  color: #4b5563;
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
  min-width: 26px;
  height: 28px;
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
</style>
