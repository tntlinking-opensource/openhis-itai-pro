<script setup lang="ts">
import {
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  shallowRef,
  watch,
} from 'vue';

import Editor, {
  EditorMode,
  EditorZone,
  ElementType,
  PageMode,
  PaperDirection,
  RenderMode,
  type Command,
  type ICatalog,
  type ICatalogItem,
  type IContextMenuContext,
  type IEditorData,
  type IElement,
  type IRangeStyle,
  type IRegisterContextMenu,
} from '@hufe921/canvas-editor';

import CanvasWordToolbar from '#/emr-canvas/components/canvas-word-toolbar.vue';
import MetadataSidebar from '#/emr-canvas/components/MetadataSidebar.vue';
import { DEMO_PATIENT_METADATA, type EmrMetadataField } from '#/emr-canvas/constants/emr-metadata';
import {
  EDITOR_MODE_OPTIONS,
  PAGE_MODE_OPTIONS,
  PAPER_DIRECTION_OPTIONS,
  PAPER_SIZE_OPTIONS,
} from '#/emr-canvas/constants/word-ribbon';
/** 外挂样式：院点 override 生产包 scoped 会丢，ribbon/纸面布局靠相对路径 css */
import '../styles/office-ribbon.css';
import './canvas-emr-editor.css';
import { applyMetadataValues, withHospitalMetadata } from '#/emr-canvas/utils/apply-metadata';
import { emptyCanvasData, toPlainEditorData } from '#/emr-canvas/utils/emr-content';
import {
  captureCanvasInsertRange,
  insertCanvasImportText,
} from '#/emr-canvas/utils/insert-canvas-import-text';
import {
  meta,
  replaceHeaderHospitalTitle,
} from '#/emr-canvas/templates/canvas-kit';

defineOptions({ name: 'CanvasEmrEditor' });

interface Props {
  modelValue?: IEditorData;
  /** 患者/就诊元数据；不传则用实验室演示患者 */
  metadataValues?: Record<string, string>;
  /** 打开纸面后是否自动带入元数据 */
  autoApplyMetadata?: boolean;
  /** 是否展示左侧元数据栏；医生站书写不需要 */
  showMetadataSidebar?: boolean;
  /** 元数据栏是否展示「带入当前患者」 */
  showApplyPatient?: boolean;
  /** 只读：查看模板时禁止改稿 */
  readOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => emptyCanvasData(),
  metadataValues: () => ({ ...DEMO_PATIENT_METADATA }),
  autoApplyMetadata: false,
  showMetadataSidebar: true,
  showApplyPatient: true,
  readOnly: false,
});

const emit = defineEmits<{
  change: [];
}>();

const hostRef = ref<HTMLDivElement | null>(null);
const editorRef = shallowRef<Editor | null>(null);
const command = shallowRef<Command | null>(null);
/** 光标所在表格 id；离开表格后清空，工具栏用它改边框 */
const activeTableId = ref('');
const showFind = ref(false);
const showCatalog = ref(false);
const catalog = ref<ICatalog>([]);
const pageMode = ref(PageMode.PAGING);
const paperDirection = ref(PaperDirection.VERTICAL);
const editorMode = ref(EditorMode.EDIT);
const paperLabel = ref('A4');
const wordCount = ref(0);
const pageCount = ref(1);
const visiblePages = ref('1');
const currentPage = ref(1);
const cursorRow = ref(0);
const cursorCol = ref(0);
const scalePercent = ref(100);
const zoneLabel = ref('正文');
const findKeyword = ref('');
const replaceText = ref('');
const findRegex = ref(false);
const findIgnoreCase = ref(true);
const findNav = ref('0/0');

const rangeStyle = reactive<IRangeStyle>({
  type: null,
  undo: false,
  redo: false,
  painter: false,
  font: 'SimSun',
  size: 16,
  bold: false,
  italic: false,
  underline: false,
  strikeout: false,
  color: '#000000',
  highlight: null,
  rowFlex: null,
  rowMargin: 1.5,
  dashArray: [],
  level: null,
  listType: null,
  listStyle: null,
  groupIds: null,
  textDecoration: null,
});

/** 空白纸不动；模板页眉占位「医院名称」在进编辑器前换成元数据院名 */
function dataForEditor(data: IEditorData | undefined): IEditorData {
  const plain = toPlainEditorData(data);
  if (!props.autoApplyMetadata) return plain;
  const hospital = String(withHospitalMetadata(props.metadataValues).hospitalName ?? '').trim();
  if (!hospital) return plain;
  return {
    ...plain,
    header: replaceHeaderHospitalTitle(plain.header ?? [], hospital),
  };
}

function getData(): IEditorData {
  const data = editorRef.value?.command.getValue()?.data;
  if (data && Array.isArray(data.main)) {
    return {
      header: data.header ?? [],
      main: data.main,
      footer: data.footer ?? [],
    };
  }
  return emptyCanvasData();
}

type CanvasViewport = {
  left: number;
  top: number;
  range: ReturnType<typeof captureCanvasInsertRange>;
};

let rememberedViewport: CanvasViewport = { left: 0, top: 0, range: null };
let lastUserScroll = { left: 0, top: 0 };
let userInteracting = false;
let restoringViewport = false;
let viewportHoldCount = 0;
let viewportRestoreTimers: number[] = [];
let restoreToken = 0;

function isHostLive(el: HTMLElement | null): el is HTMLElement {
  if (!el || !el.isConnected) return false;
  if (el.clientHeight < 8) return false;
  const pane = el.closest('.ip-station-panels__emr, .medical-record-form__editor-pane');
  if (pane instanceof HTMLElement) {
    if (pane.classList.contains('ip-station-panels__emr') && !pane.classList.contains('is-active')) {
      return false;
    }
    if (
      pane.classList.contains('medical-record-form__editor-pane') &&
      !pane.classList.contains('medical-record-form__editor-pane--active')
    ) {
      return false;
    }
  }
  const style = getComputedStyle(el);
  return style.display !== 'none' && style.visibility !== 'hidden';
}

function isMeaningfulViewport(pos?: CanvasViewport | null): pos is CanvasViewport {
  return Boolean(pos && (pos.top > 0 || pos.left > 0));
}

function cancelViewportRestore() {
  restoreToken += 1;
  for (const t of viewportRestoreTimers) window.clearTimeout(t);
  viewportRestoreTimers = [];
}

function markUserScrollIntent() {
  userInteracting = true;
  const el = hostRef.value;
  if (isHostLive(el)) {
    lastUserScroll = { left: el.scrollLeft, top: el.scrollTop };
  }
  window.requestAnimationFrame(() => {
    const live = hostRef.value;
    if (isHostLive(live)) {
      lastUserScroll = { left: live.scrollLeft, top: live.scrollTop };
    }
    userInteracting = false;
  });
}

function snapshotViewport() {
  if (restoringViewport || viewportHoldCount > 0) return;
  const el = hostRef.value;
  if (!isHostLive(el)) return;
  rememberedViewport = {
    left: el.scrollLeft,
    top: el.scrollTop,
    range: captureCanvasInsertRange(command.value),
  };
  if (userInteracting || el.scrollTop > 0 || el.scrollLeft > 0) {
    lastUserScroll = { left: el.scrollLeft, top: el.scrollTop };
  }
}

function onHostScroll() {
  if (restoringViewport || viewportHoldCount > 0) return;
  const el = hostRef.value;
  if (!isHostLive(el)) return;
  if (userInteracting || el.scrollTop > 0 || el.scrollLeft > 0) {
    cancelViewportRestore();
    snapshotViewport();
    return;
  }
  // 打开/保存后库会把滚动清 0；医生已经离开过首行则抢回来
  if (lastUserScroll.top > 0 || lastUserScroll.left > 0) {
    restoreScrollPosition({
      left: lastUserScroll.left,
      top: lastUserScroll.top,
      range: rememberedViewport.range,
    });
  }
}

function captureScrollPosition(): CanvasViewport {
  snapshotViewport();
  return {
    left: rememberedViewport.left,
    top: rememberedViewport.top,
    range: rememberedViewport.range,
  };
}

function restoreScrollPosition(pos?: CanvasViewport | null) {
  const target = pos ?? rememberedViewport;
  if (!isMeaningfulViewport(target)) {
    cancelViewportRestore();
    rememberedViewport = {
      left: 0,
      top: 0,
      range: target?.range ?? null,
    };
    return;
  }
  rememberedViewport = {
    left: target.left,
    top: target.top,
    range: target.range ?? null,
  };
  const token = ++restoreToken;
  const apply = () => {
    if (token !== restoreToken) return;
    const el = hostRef.value;
    if (!el) return;
    restoringViewport = true;
    el.scrollTop = target.top;
    el.scrollLeft = target.left;
    window.requestAnimationFrame(() => {
      restoringViewport = false;
    });
  };
  for (const t of viewportRestoreTimers) window.clearTimeout(t);
  viewportRestoreTimers = [];
  apply();
  void nextTick(apply);
  requestAnimationFrame(() => {
    apply();
    viewportRestoreTimers.push(window.setTimeout(apply, 80));
  });
}

function holdViewport(): CanvasViewport {
  const viewport = captureScrollPosition();
  viewportHoldCount += 1;
  return viewport;
}

function releaseViewport(pos?: CanvasViewport | null) {
  viewportHoldCount = Math.max(0, viewportHoldCount - 1);
  const target = isMeaningfulViewport(pos)
    ? pos
    : isMeaningfulViewport({
        left: lastUserScroll.left,
        top: lastUserScroll.top,
        range: rememberedViewport.range,
      })
      ? {
          left: lastUserScroll.left,
          top: lastUserScroll.top,
          range: rememberedViewport.range,
        }
      : null;
  if (target) restoreScrollPosition(target);
}

/** 新开另一份病历：从首行开始，不再沿用上一份的滚动 */
function resetViewport() {
  lastUserScroll = { left: 0, top: 0 };
  rememberedViewport = { left: 0, top: 0, range: null };
  cancelViewportRestore();
  const el = hostRef.value;
  if (el) {
    restoringViewport = true;
    el.scrollTop = 0;
    el.scrollLeft = 0;
    window.requestAnimationFrame(() => {
      restoringViewport = false;
    });
  }
}

/** 套模板/打开病历：整页替换，不重建实例 */
function setData(data: IEditorData) {
  const instance = editorRef.value;
  if (!instance) return;
  const viewport = captureScrollPosition();
  applyingExternal.value = true;
  instance.command.executeSetValue(dataForEditor(data), { isSetCursor: false });
  const finish = () => {
    applyingExternal.value = false;
    if (isMeaningfulViewport(viewport)) {
      restoreScrollPosition(viewport);
    }
  };
  if (props.autoApplyMetadata) {
    requestAnimationFrame(() => {
      applyDemoMetadata();
      requestAnimationFrame(finish);
    });
    return;
  }
  requestAnimationFrame(finish);
}

const applyingExternal = ref(false);

function zoneText(zone: EditorZone) {
  if (zone === EditorZone.HEADER) return '页眉';
  if (zone === EditorZone.FOOTER) return '页脚';
  return '正文';
}

function applyPaper(label: string) {
  const hit = PAPER_SIZE_OPTIONS.find((item) => item.label === label);
  if (!hit || !command.value) return;
  command.value.executePaperSize(hit.width, hit.height);
  paperLabel.value = hit.label;
}

function applyPaperDirection(dir: PaperDirection) {
  command.value?.executePaperDirection(dir);
  paperDirection.value = dir;
}

function applyPageMode(mode: PageMode) {
  command.value?.executePageMode(mode);
  pageMode.value = mode;
}

function applyEditorMode(mode: EditorMode) {
  command.value?.executeMode(mode);
  editorMode.value = mode;
}

function paperLabelOf(width: number, height: number) {
  const hit = PAPER_SIZE_OPTIONS.find(
    (item) =>
      (item.width === width && item.height === height) ||
      (item.width === height && item.height === width),
  );
  return hit?.label ?? `${width}×${height}`;
}

async function refreshWordCount() {
  const count = await editorRef.value?.command.getWordCount();
  wordCount.value = Number(count ?? 0);
}

let wordCountTimer = 0;

/** 字数统计会把整篇正文交给 worker，页数多时不能每个字都做。 */
function scheduleWordCount() {
  window.clearTimeout(wordCountTimer);
  wordCountTimer = window.setTimeout(() => {
    wordCountTimer = 0;
    void refreshWordCount();
  }, 500);
}

function refreshCursor() {
  const pos = editorRef.value?.command.getCursorPosition();
  cursorRow.value = (pos?.rowNo ?? 0) + 1;
  cursorCol.value = (pos?.index ?? 0) + 1;
}

function refreshFindNav() {
  const info = editorRef.value?.command.getSearchNavigateInfo();
  if (!info) {
    findNav.value = '0/0';
    return;
  }
  findNav.value = `${info.index + 1}/${info.count}`;
}

function applyFind() {
  editorRef.value?.command.executeSearch(findKeyword.value || null, {
    isRegEnable: findRegex.value,
    isIgnoreCase: findIgnoreCase.value,
  });
  refreshFindNav();
}

function replaceCurrent() {
  applyFind();
  editorRef.value?.command.executeReplace(replaceText.value);
  refreshFindNav();
}

async function refreshCatalog() {
  catalog.value = (await editorRef.value?.command.getCatalog()) ?? [];
}

function jumpCatalog(item: ICatalogItem) {
  editorRef.value?.command.executeLocationCatalog(item.id);
}

function isChoiceHoverTarget(el?: IElement | null) {
  if (!el) return false;
  if (el.controlComponent === 'checkbox' || el.controlComponent === 'radio') return true;
  return el.type === ElementType.CHECKBOX || el.type === ElementType.RADIO;
}

function applyCanvasCursor(evt: MouseEvent, cursor: string) {
  const canvas = evt.target;
  if (!(canvas instanceof HTMLCanvasElement)) return;
  if (canvas.style.cursor === 'copy' || canvas.style.cursor === 'col-resize' || canvas.style.cursor === 'row-resize') {
    return;
  }
  if (canvas.style.cursor !== cursor) canvas.style.cursor = cursor;
}

function onChoiceHoverMove(evt: MouseEvent) {
  if (rangeStyle.painter) return;
  const hit = command.value?.getPositionContextByEvent(evt)?.element;
  applyCanvasCursor(evt, isChoiceHoverTarget(hit) ? 'pointer' : 'text');
}

function onChoiceHoverLeave(evt: MouseEvent) {
  applyCanvasCursor(evt, 'text');
}

function onPositionContextChange(payload: { value?: { isTable?: boolean; tableId?: string } }) {
  const ctx = payload.value;
  activeTableId.value = ctx?.isTable && ctx.tableId ? ctx.tableId : '';
  snapshotViewport();
}

const BORDER_COLOR_PRESETS = [
  { name: '黑色', color: '#000000' },
  { name: '深灰', color: '#333333' },
  { name: '灰色', color: '#666666' },
  { name: '浅灰', color: '#999999' },
] as const;

const BORDER_PT_PRESETS = [0.25, 0.5, 0.75, 1, 1.5, 2.25, 3, 4.5, 6];

function borderPtToPx(pt: number): number {
  return Math.round(((pt * 96) / 72) * 100) / 100;
}

function applyContextTableBorder(
  command: Command,
  context: IContextMenuContext,
  patch: { borderColor?: string; borderWidth?: number },
) {
  const id = context.tableElement?.id;
  if (!id) return;
  command.executeUpdateElementById({ id, properties: patch });
}

/** 右键「表格边框」里补上颜色和磅数 */
function installTableBorderStyleMenu(instance: Editor) {
  const borderMenu = instance.register
    .getContextMenuList()
    .find((item) => item.i18nPath === 'contextmenu.table.border');
  if (!borderMenu) return;
  const colorMenus: IRegisterContextMenu[] = BORDER_COLOR_PRESETS.map((item) => {
    const menu: IRegisterContextMenu = {
      name: item.name,
      when: (ctx) => {
        const current = (ctx.tableElement?.borderColor || '#000000').toLowerCase();
        menu.name = current === item.color ? `${item.name} ✓` : item.name;
        return true;
      },
      callback: (command, ctx) => applyContextTableBorder(command, ctx, { borderColor: item.color }),
    };
    return menu;
  });
  const weightMenus: IRegisterContextMenu[] = BORDER_PT_PRESETS.map((pt) => {
    const px = borderPtToPx(pt);
    const menu: IRegisterContextMenu = {
      name: `${pt} 磅`,
      when: (ctx) => {
        const current = ctx.tableElement?.borderWidth ?? 1;
        menu.name = Math.abs(current - px) < 0.04 ? `${pt} 磅 ✓` : `${pt} 磅`;
        return true;
      },
      callback: (command, ctx) => applyContextTableBorder(command, ctx, { borderWidth: px }),
    };
    return menu;
  });
  borderMenu.childMenus = [
    ...(borderMenu.childMenus ?? []),
    { isDivider: true },
    { name: '边框颜色', when: () => true, childMenus: colorMenus },
    { name: '边框磅数', when: () => true, childMenus: weightMenus },
  ];
}

function bindListeners(instance: Editor) {
  instance.eventBus.on('mousemove', onChoiceHoverMove);
  instance.eventBus.on('mouseleave', onChoiceHoverLeave);
  instance.eventBus.on('positionContextChange', onPositionContextChange);
  instance.listener.rangeStyleChange = (payload) => {
    Object.assign(rangeStyle, payload);
    refreshCursor();
  };
  instance.listener.visiblePageNoListChange = (pages) => {
    visiblePages.value = pages.map((no) => no + 1).join('、') || '1';
  };
  instance.listener.intersectionPageNoChange = (pageNo) => {
    currentPage.value = pageNo + 1;
  };
  instance.listener.pageSizeChange = (size) => {
    pageCount.value = size;
  };
  instance.listener.pageScaleChange = (scale) => {
    scalePercent.value = Math.round(scale * 100);
  };
  instance.listener.pageModeChange = (mode) => {
    pageMode.value = mode;
  };
  instance.listener.zoneChange = (zone) => {
    zoneLabel.value = zoneText(zone);
  };
  instance.listener.contentChange = () => {
    scheduleWordCount();
    if (showCatalog.value) void refreshCatalog();
    if (!applyingExternal.value) {
      emit('change');
    }
  };
}

onMounted(() => {
  const host = hostRef.value;
  if (!host) return;
  const instance = new Editor(host, dataForEditor(props.modelValue), {
    mode: props.readOnly ? EditorMode.READONLY : EditorMode.EDIT,
    locale: 'zhCN',
    // Ctrl+滚轮交给浏览器缩放，不走编辑器纸面缩放
    shortcutDisableKeys: ['pageScale'],
    pageMode: PageMode.PAGING,
    // 官方配置：兼容模式逐字绘制，避免旧浏览器字体度量不一致时把多个字拼在一起画发虚
    renderMode: RenderMode.COMPATIBILITY,
    defaultFont: 'SimSun',
    defaultSize: 16,
    defaultRowMargin: 1.5,
    width: 794,
    height: 1123,
    // Word「窄」页边距 1.27 厘米（96dpi ≈ 48px），上右下左
    margins: [48, 48, 48, 48],
    paperDirection: PaperDirection.VERTICAL,
    pageGap: 16,
    background: { color: '#ffffff' },
    underlineColor: '#b0b8c2',
    zone: { tipDisabled: true },
    ruler: { disabled: true },
    header: { disabled: false, editable: true, top: 28, inactiveAlpha: 1 },
    footer: { disabled: false, editable: true, bottom: 36 },
    table: {
      // 宋体小四单倍一行约 23px。上下留白会把行再撑高，所以垂直内边距为 0，默认行高才能落在 24px。
      tdPadding: [0, 4, 0, 4],
      defaultTrMinHeight: 24,
    },
    pageNumber: {
      disabled: false,
      bottom: 28,
      size: 12,
      color: '#333333',
      format: '第{pageNo}页/共{pageCount}页',
    },
    placeholder: {
      data: '请在此处编辑内容',
    },
    control: {
      placeholderColor: '#9ca3af',
      bracketColor: '#93c5fd',
      // 全局不要括号/蓝底：否则单选多选也会被套上。填写类控件在自身 control 上设 prefix/highlight。
      prefix: '',
      postfix: '',
      borderWidth: 0,
      borderColor: '',
      activeBackgroundColor: '',
      disabledBackgroundColor: '',
      existValueBackgroundColor: '',
      noValueBackgroundColor: '',
    },
    checkbox: {
      width: 14,
      height: 14,
      gap: 6,
      lineWidth: 1,
      fillStyle: '#ffffff',
      strokeStyle: '#8a93a0',
    },
    radio: {
      width: 14,
      height: 14,
      gap: 6,
      lineWidth: 1,
      fillStyle: '#5b6b7c',
      strokeStyle: '#8a93a0',
    },
  });
  editorRef.value = instance;
  command.value = instance.command;
  editorMode.value = props.readOnly ? EditorMode.READONLY : EditorMode.EDIT;
  bindListeners(instance);
  installTableBorderStyleMenu(instance);
  const options = instance.command.getOptions();
  paperLabel.value = paperLabelOf(options.width, options.height);
  paperDirection.value = options.paperDirection;
  void refreshWordCount();
  host.addEventListener('scroll', onHostScroll, { passive: true });
  host.addEventListener('wheel', markUserScrollIntent, { passive: true });
  host.addEventListener('pointerdown', markUserScrollIntent, { passive: true });
  host.addEventListener('touchstart', markUserScrollIntent, { passive: true });
  if (props.autoApplyMetadata) {
    // 下一帧再写：等控件树就绪后再按 conceptId 带入
    requestAnimationFrame(() => applyDemoMetadata());
  }
});

onActivated(() => {
  restoreScrollPosition(rememberedViewport);
});

onDeactivated(() => {
  snapshotViewport();
});

/** 查看模板切只读，编辑模板切可写 */
watch(
  () => props.readOnly,
  (readonly) => {
    applyEditorMode(readonly ? EditorMode.READONLY : EditorMode.EDIT);
  },
);

onBeforeUnmount(() => {
  window.clearTimeout(wordCountTimer);
  for (const t of viewportRestoreTimers) window.clearTimeout(t);
  viewportRestoreTimers = [];
  hostRef.value?.removeEventListener('scroll', onHostScroll);
  hostRef.value?.removeEventListener('wheel', markUserScrollIntent);
  hostRef.value?.removeEventListener('pointerdown', markUserScrollIntent);
  hostRef.value?.removeEventListener('touchstart', markUserScrollIntent);
  editorRef.value?.eventBus.off('mousemove', onChoiceHoverMove);
  editorRef.value?.eventBus.off('mouseleave', onChoiceHoverLeave);
  editorRef.value?.eventBus.off('positionContextChange', onPositionContextChange);
  editorRef.value?.destroy();
  editorRef.value = null;
  command.value = null;
});

/** 按 conceptId 带入当前 metadataValues */
function applyDemoMetadata() {
  return applyHostMetadata(props.metadataValues, true);
}

/** 宿主写入指定字段；overwrite=false 只填空槽 */
function applyHostMetadata(values: Record<string, string>, overwrite = true) {
  const viewport = captureScrollPosition();
  applyingExternal.value = true;
  const n = applyMetadataValues(command.value, values, { overwrite });
  if (n > 0) void refreshWordCount();
  requestAnimationFrame(() => {
    applyingExternal.value = false;
    if (isMeaningfulViewport(viewport)) {
      restoreScrollPosition(viewport);
    }
  });
  return n;
}

/** 侧栏点选：插入元数据槽到光标处 */
function insertMetaField(field: EmrMetadataField) {
  command.value?.executeInsertControl(meta(field.fieldKey, field.label, field.editable === true));
}

/** 快照选区（插入诊断/医嘱弹窗前） */
function captureInsertRange() {
  return captureCanvasInsertRange(command.value);
}

/** 在光标/冻结选区插入诊断、医嘱纯文本 */
function insertPlainText(text: string, range?: ReturnType<typeof captureCanvasInsertRange>) {
  const ok = insertCanvasImportText({
    command: command.value,
    text,
    range,
  });
  if (ok) void refreshWordCount();
  return ok;
}

/** Canvas 编辑器打印：渲染纸面后打开浏览器打印 */
async function printDocument() {
  if (!command.value) return;
  await command.value.executePrint();
}

defineExpose({
  getData,
  setData,
  applyMetadata: applyDemoMetadata,
  applyHostMetadata,
  captureInsertRange,
  insertPlainText,
  printDocument,
  captureScrollPosition,
  restoreScrollPosition,
  holdViewport,
  releaseViewport,
  resetViewport,
});
</script>

<template>
  <div class="canvas-emr-shell">
    <CanvasWordToolbar
      :command="command"
      :range-style="rangeStyle"
      :table-id="activeTableId"
      @toggle-find="showFind = !showFind"
      @toggle-catalog="
        showCatalog = !showCatalog;
        if (showCatalog) void refreshCatalog();
      "
    >
      <template v-if="$slots.print" #print>
        <slot name="print" />
      </template>
      <template v-if="$slots.host" #host>
        <slot name="host" />
      </template>
    </CanvasWordToolbar>
    <div class="canvas-emr-shell__body">
      <MetadataSidebar
        v-if="showMetadataSidebar"
        :can-apply="Boolean(command)"
        :show-apply="showApplyPatient"
        @insert="insertMetaField"
        @apply="applyDemoMetadata"
      />
      <aside v-if="showCatalog" class="catalog-pane">
        <div class="catalog-pane__title">目录</div>
        <el-empty v-if="!catalog.length" description="插入标题后生成目录" :image-size="48" />
        <ul v-else class="catalog-tree">
          <li v-for="item in catalog" :key="item.id">
            <button type="button" class="catalog-tree__item" @click="jumpCatalog(item)">
              {{ item.name }}
            </button>
            <ul v-if="item.subCatalog?.length">
              <li v-for="child in item.subCatalog" :key="child.id">
                <button type="button" class="catalog-tree__item" @click="jumpCatalog(child)">
                  {{ child.name }}
                </button>
              </li>
            </ul>
          </li>
        </ul>
      </aside>
      <div ref="hostRef" class="canvas-emr-editor" />
      <aside v-if="showFind" class="find-pane">
        <div class="find-pane__title">查找 / 替换</div>
        <el-input v-model="findKeyword" size="small" placeholder="查找内容" @keyup.enter="applyFind" />
        <el-input v-model="replaceText" size="small" placeholder="替换为" />
        <el-checkbox v-model="findRegex">正则</el-checkbox>
        <el-checkbox v-model="findIgnoreCase">忽略大小写</el-checkbox>
        <div class="find-pane__nav">{{ findNav }}</div>
        <el-button size="small" @click="applyFind">查找</el-button>
        <el-button-group>
          <el-button size="small" @click="command?.executeSearchNavigatePre(); refreshFindNav()">上一个</el-button>
          <el-button size="small" @click="command?.executeSearchNavigateNext(); refreshFindNav()">下一个</el-button>
        </el-button-group>
        <el-button size="small" type="primary" @click="replaceCurrent">替换</el-button>
        <el-button size="small" @click="command?.executeSearch(null); showFind = false">关闭</el-button>
      </aside>
    </div>
    <div class="canvas-emr-status office-status">
      <span>可见页：{{ visiblePages }}</span>
      <span>页面：{{ currentPage }}/{{ pageCount }}</span>
      <span>字数：{{ wordCount }}</span>
      <span>行：{{ cursorRow }}</span>
      <span>列：{{ cursorCol }}</span>
      <span>区域：{{ zoneLabel }}</span>
      <el-select :model-value="paperLabel" size="small" style="width: 100px" @change="applyPaper">
        <el-option v-for="item in PAPER_SIZE_OPTIONS" :key="item.label" :label="item.label" :value="item.label" />
      </el-select>
      <el-select :model-value="paperDirection" size="small" style="width: 88px" @change="applyPaperDirection">
        <el-option
          v-for="item in PAPER_DIRECTION_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select :model-value="pageMode" size="small" style="width: 88px" @change="applyPageMode">
        <el-option v-for="item in PAGE_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select :model-value="editorMode" size="small" style="width: 108px" @change="applyEditorMode">
        <el-option v-for="item in EDITOR_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button-group>
        <el-button size="small" @click="command?.executePageScaleMinus()">-</el-button>
        <el-button size="small" @click="command?.executePageScaleRecovery()">{{ scalePercent }}%</el-button>
        <el-button size="small" @click="command?.executePageScaleAdd()">+</el-button>
      </el-button-group>
    </div>
  </div>
</template>

