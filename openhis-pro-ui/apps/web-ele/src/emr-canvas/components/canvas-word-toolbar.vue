<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';

import {
  ControlType,
  EDITOR_COMPONENT,
  EditorComponent,
  EditorZone,
  ElementType,
  ListStyle,
  ListType,
  RowFlex,
  SurroundPosition,
  TitleLevel,
  type Command,
  type IElement,
  type IRangeStyle,
} from '@hufe921/canvas-editor';

import InsertWidgetDialog, {
  type InsertWidgetKind,
  type InsertWidgetResult,
} from '#/emr-canvas/components/InsertWidgetDialog.vue';
import RibbonBtn from '#/emr-canvas/components/word-ribbon/RibbonBtn.vue';
import RibSvg from '#/emr-canvas/components/word-ribbon/RibSvg.vue';
import {
  FONT_OPTIONS,
  FONT_SIZE_OPTIONS,
  LINE_SPACING_PRESETS,
  LIST_OPTIONS,
  MARGIN_PRESETS,
  STYLE_GALLERY,
} from '#/emr-canvas/constants/word-ribbon';
/** 外挂样式：院点 override 生产包 scoped 会丢，ribbon 用相对路径 css */
import '../styles/office-ribbon.css';
import './canvas-word-toolbar.css';
import { TABLE_ROW_HEIGHT_40PT } from '#/emr-canvas/templates/canvas-kit';
import { buildControlElement } from '#/emr-canvas/utils/canvas-controls';

defineOptions({ name: 'CanvasWordToolbar' });

const props = defineProps<{
  command: Command | null;
  rangeStyle: IRangeStyle;
  /** 光标所在表格；空表示不在表格里 */
  tableId?: string;
}>();

const emit = defineEmits<{
  toggleCatalog: [];
  toggleFind: [];
}>();

const tableHover = reactive({ row: 3, col: 3 });
/** Word 常用边框磅值。库默认 1px，约 0.75 磅 */
const BORDER_PT_PRESETS = [0.25, 0.5, 0.75, 1, 1.5, 2.25, 3, 4.5, 6];
const borderColor = ref('#000000');
const borderPt = ref(0.75);
/** 点开表格菜单时记住，避免点颜色框后光标离开表格 */
const latchedTableId = ref('');
const imageInputRef = ref<HTMLInputElement | null>(null);
const widgetOpen = ref(false);
const widgetKind = ref<InsertWidgetKind>('text');

const titleValue = computed({
  get: () => props.rangeStyle.level ?? 'body',
  set: (value: TitleLevel | 'body') => {
    props.command?.executeTitle(value === 'body' ? null : value);
  },
});

const align = computed(() => props.rangeStyle.rowFlex);

const lineSpacingLabel = computed(() => {
  const hit = LINE_SPACING_PRESETS.find((item) => item.value === props.rangeStyle.rowMargin);
  return hit?.label ?? '行距';
});

const borderPtOptions = computed(() => {
  const values = new Set(BORDER_PT_PRESETS);
  values.add(borderPt.value);
  return [...values].sort((a, b) => a - b);
});

/** 库里的 borderWidth 是像素；界面按磅 */
function borderPxToPt(px: number): number {
  const pt = (px * 72) / 96;
  const hit = BORDER_PT_PRESETS.find((item) => Math.abs(item - pt) < 0.04);
  return hit ?? Math.round(pt * 100) / 100;
}

function borderPtToPx(pt: number): number {
  return Math.round(((pt * 96) / 72) * 100) / 100;
}

function rememberTable() {
  if (props.tableId) latchedTableId.value = props.tableId;
}

function syncTableBorder() {
  rememberTable();
  const id = latchedTableId.value;
  if (!props.command || !id) return;
  const table = props.command.getElementById({ id }).find((el) => el.type === ElementType.TABLE);
  if (!table) return;
  borderColor.value = table.borderColor || '#000000';
  borderPt.value = borderPxToPt(table.borderWidth ?? 1);
}

function applyTableBorder(patch: { borderColor?: string; borderWidth?: number }) {
  const id = latchedTableId.value;
  if (!id) return;
  run((cmd) => {
    cmd.executeUpdateElementById({ id, properties: patch });
  });
}

function onBorderColor(evt: Event) {
  const color = (evt.target as HTMLInputElement).value;
  if (!color) return;
  borderColor.value = color;
  applyTableBorder({ borderColor: color });
}

function onBorderPt(evt: Event) {
  const pt = Number((evt.target as HTMLSelectElement).value);
  if (!Number.isFinite(pt) || pt <= 0) return;
  borderPt.value = pt;
  applyTableBorder({ borderWidth: borderPtToPx(pt) });
}

watch(
  () => props.tableId,
  (id) => {
    if (id) {
      latchedTableId.value = id;
      syncTableBorder();
    }
  },
);

/** 执行编辑器命令 */
function run(fn: (cmd: Command) => void) {
  if (!props.command) return;
  fn(props.command);
}

/**
 * 点字号下拉时，document mousedown 会先走库的 recoveryRangeStyle，
 * 把指示器重置成 defaultSize（小四），正文其实还是四号。
 * 在点下拉之前按选区文字冻住字号，打开菜单期间不要用被重置的指示器回显。
 */
const frozenStyle = ref<{ font?: string; size?: number } | null>(null);
const holdSizeEcho = ref(false);

type FillControlNode = {
  type?: string;
  conceptId?: string;
  size?: number;
  font?: string;
  value?: IElement[] | null;
};

function visitTextStyle(
  nodes: IElement[] | undefined,
  sizes: Set<number>,
  fonts: Set<string>,
  defaultSize: number,
  defaultFont: string,
) {
  if (!nodes?.length) return;
  for (const node of nodes) {
    if (!node || node.type === ElementType.TABLE) continue;
    const raw = node.value;
    if (raw !== '\u200b' && (raw || node.size || node.font)) {
      sizes.add(node.size || defaultSize);
      fonts.add(node.font || defaultFont);
    }
    const inner = (node.control as FillControlNode | undefined)?.value;
    if (Array.isArray(inner)) visitTextStyle(inner, sizes, fonts, defaultSize, defaultFont);
  }
}

function styleFromNodes(nodes: IElement[] | undefined): { font?: string; size?: number } | null {
  const cmd = props.command;
  if (!cmd || !nodes?.length) return null;
  const defaultSize = cmd.getOptions()?.defaultSize ?? 16;
  const defaultFont = cmd.getOptions()?.defaultFont ?? 'SimSun';
  const sizes = new Set<number>();
  const fonts = new Set<string>();
  visitTextStyle(nodes, sizes, fonts, defaultSize, defaultFont);
  if (!sizes.size && !fonts.size) return null;
  return {
    font: fonts.size === 1 ? [...fonts][0] : undefined,
    size: sizes.size === 1 ? [...sizes][0] : undefined,
  };
}

/** 整表、选区、光标前一个字，都读文字上的字号，不用被重置成小四的指示器。 */
function readLiveTextStyle(): { font?: string; size?: number } {
  const cmd = props.command;
  const indicated = { font: props.rangeStyle.font, size: props.rangeStyle.size };
  if (!cmd) return indicated;
  const range = cmd.getRange();
  if (range?.isCrossRowCol && range.tableId) {
    const table = cmd.getElementById({ id: range.tableId }).find((el) => el.type === ElementType.TABLE);
    if (table?.trList?.length) {
      const startTr = Math.min(range.startTrIndex ?? 0, range.endTrIndex ?? 0);
      const endTr = Math.max(range.startTrIndex ?? 0, range.endTrIndex ?? 0);
      const startTd = Math.min(range.startTdIndex ?? 0, range.endTdIndex ?? 0);
      const endTd = Math.max(range.startTdIndex ?? 0, range.endTdIndex ?? 0);
      const nodes: IElement[] = [];
      for (let row = startTr; row <= endTr; row++) {
        for (let col = startTd; col <= endTd; col++) {
          const cell = table.trList[row]?.tdList?.[col]?.value;
          if (cell?.length) nodes.push(...cell);
        }
      }
      const fromTable = styleFromNodes(nodes);
      if (fromTable?.size || fromTable?.font) return fromTable;
    }
  }
  const ctx = cmd.getRangeContext();
  if (ctx && !ctx.isCollapsed && ctx.selectionElementList?.length) {
    const fromSelection = styleFromNodes(ctx.selectionElementList);
    if (fromSelection?.size || fromSelection?.font) return fromSelection;
  }
  const before = cmd.getSurroundElementList({ direction: SurroundPosition.BEFORE, length: 1 })?.[0];
  const caretNode = before?.size ? before : ctx?.startElement;
  const fromCaret = styleFromNodes(caretNode ? [caretNode] : undefined);
  if (fromCaret?.size || fromCaret?.font) return fromCaret;
  const controlSize = readActiveControlSize();
  if (controlSize) return { size: controlSize, font: indicated.font };
  return indicated;
}

function onRibbonPointerDown(event: PointerEvent) {
  const target = event.target;
  if (!(target instanceof Element) || !target.closest('.rib-select--size')) return;
  frozenStyle.value = readLiveTextStyle();
  holdSizeEcho.value = true;
}

const sizeMenuOpen = ref(false);

function onSizeVisible(open: boolean) {
  sizeMenuOpen.value = open;
  if (open && !holdSizeEcho.value) {
    frozenStyle.value = readLiveTextStyle();
    holdSizeEcho.value = true;
  }
  if (!open) {
    holdSizeEcho.value = false;
    frozenStyle.value = null;
  }
}

const displayFont = computed(() => {
  if (holdSizeEcho.value && frozenStyle.value?.font) return frozenStyle.value.font;
  return readLiveTextStyle().font || props.rangeStyle.font;
});
const displaySize = computed(() => {
  if (holdSizeEcho.value && frozenStyle.value) return frozenStyle.value.size || undefined;
  return readLiveTextStyle().size || props.rangeStyle.size || undefined;
});

watch(
  () => props.rangeStyle,
  () => {
    if (holdSizeEcho.value || sizeMenuOpen.value) return;
    frozenStyle.value = null;
  },
);

/** 插入指定行列的表格（新表行高用模板默认 40 磅） */
function insertTable(row: number, col: number) {
  run((cmd) => {
    cmd.executeInsertTable(row, col);
    const { endIndex } = cmd.getRange();
    const main = cmd.getValue()?.data?.main;
    const el = main?.[endIndex];
    if (el?.type !== ElementType.TABLE || !el.id) return;
    const px = TABLE_ROW_HEIGHT_40PT;
    cmd.executeUpdateElementById({
      id: el.id,
      properties: {
        trList: (el.trList ?? []).map((tr) => ({
          ...tr,
          height: px,
          minHeight: px,
        })),
      },
    });
  });
}

/** 设置项目符号或编号 */
function setList(type: ListType | null, style?: ListStyle) {
  run((cmd) => cmd.executeList(type, style));
}

/** 设置段落对齐 */
function setAlign(flex: RowFlex) {
  run((cmd) => cmd.executeRowFlex(flex));
}

/** 设置行距 */
function setRowMargin(margin: number | string) {
  run((cmd) => cmd.executeRowMargin(Number(margin)));
}

function isFillControlType(type: string | undefined) {
  return type === ControlType.TEXT || type === ControlType.NUMBER || type === ControlType.DATE;
}

/** 光标所在的文本/数字/日期控件。普通正文返回空。 */
function activeFillControl(): { conceptId: string; zone?: EditorZone } | null {
  const ctx = props.command?.getRangeContext();
  const control = (ctx?.startElement as { control?: FillControlNode } | undefined)?.control;
  if (!control?.conceptId || !isFillControlType(control.type)) return null;
  return { conceptId: control.conceptId, zone: ctx?.zone };
}

function zoneElements(
  data: { header?: IElement[]; main?: IElement[]; footer?: IElement[] },
  zone?: EditorZone,
): IElement[] | undefined {
  if (zone === EditorZone.HEADER) return data.header;
  if (zone === EditorZone.FOOTER) return data.footer;
  if (zone === EditorZone.MAIN) return data.main;
  return undefined;
}

/**
 * 库的 executeSize 只改选区元素。文本控件重排时用 control.value 上的 size，
 * 光标停在控件里时选区是折叠的，字号改不到已有内容。
 */
function paintFillControlStyle(patch: { size?: number; font?: string }): boolean {
  const cmd = props.command;
  const active = activeFillControl();
  if (!cmd || !active) return false;
  const data = cmd.getValue()?.data;
  if (!data) return false;
  const roots = active.zone
    ? [zoneElements(data, active.zone)]
    : [data.header, data.main, data.footer];
  let hit = false;
  const visit = (elements: IElement[] | undefined) => {
    if (!elements?.length) return;
    for (const el of elements) {
      const control = el.control as FillControlNode | undefined;
      if (
        control &&
        control.conceptId === active.conceptId &&
        isFillControlType(control.type)
      ) {
        if (patch.size != null) control.size = patch.size;
        if (patch.font) control.font = patch.font;
        if (Array.isArray(control.value)) {
          for (const node of control.value) {
            if (!node || typeof node !== 'object') continue;
            if (patch.size != null) node.size = patch.size;
            if (patch.font) node.font = patch.font;
          }
        }
        hit = true;
      }
      const rows = el.trList;
      if (!rows?.length) continue;
      for (const row of rows) {
        for (const cell of row.tdList ?? []) visit(cell.value);
      }
    }
  };
  for (const root of roots) visit(root);
  if (!hit) return false;
  const range = cmd.getRange();
  cmd.executeSetValue(
    {
      header: data.header ?? [],
      main: data.main,
      footer: data.footer ?? [],
    },
    { isSetCursor: false },
  );
  if (range && range.startIndex >= 0 && range.endIndex >= 0) {
    cmd.executeSetRange(
      range.startIndex,
      range.endIndex,
      range.tableId,
      range.startTdIndex,
      range.endTdIndex,
      range.startTrIndex,
      range.endTrIndex,
    );
  }
  return true;
}

function readActiveControlSize(): number | null {
  const cmd = props.command;
  const active = activeFillControl();
  if (!cmd || !active) return null;
  const data = cmd.getValue()?.data;
  if (!data) return null;
  const roots = active.zone
    ? [zoneElements(data, active.zone)]
    : [data.header, data.main, data.footer];
  let found: number | null = null;
  const visit = (elements: IElement[] | undefined) => {
    if (!elements?.length || found != null) return;
    for (const el of elements) {
      const control = el.control as FillControlNode | undefined;
      if (
        control &&
        control.conceptId === active.conceptId &&
        isFillControlType(control.type)
      ) {
        const fromValue = control.value?.find((node) => node?.size)?.size;
        found = control.size || fromValue || null;
        return;
      }
      for (const row of el.trList ?? []) {
        for (const cell of row.tdList ?? []) visit(cell.value);
      }
    }
  };
  for (const root of roots) visit(root);
  return found;
}

function isTableSelection(): boolean {
  return Boolean(props.command?.getRange()?.isCrossRowCol);
}

function paintNodeStyle(node: IElement, patch: { size?: number; font?: string }) {
  if (patch.size != null) node.size = patch.size;
  if (patch.font) node.font = patch.font;
  const control = node.control as FillControlNode | undefined;
  if (!control) return;
  if (patch.size != null) control.size = patch.size;
  if (patch.font) control.font = patch.font;
  if (Array.isArray(control.value)) {
    for (const inner of control.value) {
      if (inner && typeof inner === 'object') paintNodeStyle(inner, patch);
    }
  }
}

/**
 * 整表/跨格选中时库的 rangeStyle 锚在表格节点上，executeSize 有时拿不到单元格。
 * 直接改选中格子里的文字和填写控件。
 */
function paintTableSelectionStyle(patch: { size?: number; font?: string }): boolean {
  const cmd = props.command;
  const range = cmd?.getRange();
  if (!cmd || !range?.isCrossRowCol) return false;
  const data = cmd.getValue()?.data;
  if (!data) return false;
  const startTr = Math.min(range.startTrIndex ?? 0, range.endTrIndex ?? 0);
  const endTr = Math.max(range.startTrIndex ?? 0, range.endTrIndex ?? 0);
  const startTd = Math.min(range.startTdIndex ?? 0, range.endTdIndex ?? 0);
  const endTd = Math.max(range.startTdIndex ?? 0, range.endTdIndex ?? 0);
  let hit = false;
  const walk = (elements: IElement[] | undefined) => {
    if (!elements?.length) return;
    for (const el of elements) {
      if (el.type !== ElementType.TABLE || !el.trList?.length) continue;
      if (range.tableId && el.id && el.id !== range.tableId) continue;
      for (let row = startTr; row <= endTr; row++) {
        const cells = el.trList[row]?.tdList ?? [];
        const from = range.tableId ? startTd : 0;
        const to = range.tableId ? endTd : cells.length - 1;
        for (let col = from; col <= to; col++) {
          const nodes = cells[col]?.value;
          if (!nodes?.length) continue;
          for (const node of nodes) paintNodeStyle(node, patch);
          hit = true;
        }
      }
    }
  };
  walk(data.header);
  walk(data.main);
  walk(data.footer);
  if (!hit) return false;
  cmd.executeSetValue(
    {
      header: data.header ?? [],
      main: data.main,
      footer: data.footer ?? [],
    },
    { isSetCursor: false },
  );
  if (range.startIndex >= 0 && range.endIndex >= 0) {
    cmd.executeSetRange(
      range.startIndex,
      range.endIndex,
      range.tableId,
      range.startTdIndex,
      range.endTdIndex,
      range.startTrIndex,
      range.endTrIndex,
    );
  }
  return true;
}

/** 设置字体 */
function setFont(font: string) {
  if (!font) return;
  if (isTableSelection()) {
    if (!paintTableSelectionStyle({ font })) {
      run((cmd) => cmd.executeFont(font));
    }
  } else if (!paintFillControlStyle({ font })) {
    run((cmd) => cmd.executeFont(font));
  }
  if (frozenStyle.value) frozenStyle.value = { ...frozenStyle.value, font };
}

/** 设置字号。整表选中时改单元格文字，不能只改当前填写控件。 */
function setSize(size: number | string) {
  const next = Number(size);
  if (!Number.isFinite(next) || next <= 0) return;
  if (isTableSelection()) {
    if (!paintTableSelectionStyle({ size: next })) {
      run((cmd) => cmd.executeSize(next));
    }
  } else if (!paintFillControlStyle({ size: next })) {
    run((cmd) => cmd.executeSize(next));
  }
  if (frozenStyle.value) frozenStyle.value = { ...frozenStyle.value, size: next };
}

/** 增大/减小字号；在文本控件内改控件内容的字号 */
function shiftSize(delta: number) {
  const cmd = props.command;
  const current = readActiveControlSize() ?? props.rangeStyle.size;
  if (cmd && current) {
    const options = cmd.getOptions();
    let next = current + delta;
    if (next < options.minSize) next = options.minSize;
    if (next > options.maxSize) next = options.maxSize;
    if (paintFillControlStyle({ size: next })) {
      if (frozenStyle.value) frozenStyle.value = { ...frozenStyle.value, size: next };
      return;
    }
  }
  run((c) => (delta > 0 ? c.executeSizeAdd() : c.executeSizeMinus()));
}

/** 设置文字颜色 */
function setColor(color: string | null) {
  run((cmd) => cmd.executeColor(color));
}

/** 设置高亮色 */
function setHighlight(color: string | null) {
  run((cmd) => cmd.executeHighlight(color));
}

/** 应用 Word 预制页边距 */
function applyMargin(label: string) {
  const hit = MARGIN_PRESETS.find((item) => item.label === label);
  if (!hit) return;
  run((cmd) => cmd.executeSetPaperMargin(hit.value));
}

/** 打开本地图片选择 */
function pickImage() {
  imageInputRef.value?.click();
}

/** 把选中的图片插入文档 */
function onImageFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    const value = String(reader.result || '');
    const img = new Image();
    img.onload = () => {
      let width = img.width;
      let height = img.height;
      const max = 460;
      if (width > max) {
        height = Math.round((height * max) / width);
        width = max;
      }
      run((cmd) => cmd.executeImage({ value, width, height }));
    };
    img.src = value;
  };
  reader.readAsDataURL(file);
}

/** 打开 Tiptap 同口径的插入控件弹窗 */
function openWidget(kind: InsertWidgetKind) {
  widgetKind.value = kind;
  widgetOpen.value = true;
}

/** 弹窗确认后插入填写控件 */
function onWidgetConfirm(result: InsertWidgetResult) {
  const typeMap: Record<InsertWidgetKind, ControlType> = {
    text: ControlType.TEXT,
    number: ControlType.NUMBER,
    select: ControlType.SELECT,
    radio: ControlType.RADIO,
    date: ControlType.DATE,
    checkbox: ControlType.CHECKBOX,
  };
  run((cmd) =>
    cmd.executeInsertControl(
      buildControlElement({
        type: typeMap[result.kind],
        placeholder: result.placeholder || `请在这里填写${result.label}`,
        options: result.options,
      }),
    ),
  );
}

/** 切换页眉 / 正文 / 页脚 */
function setZone(zone: EditorZone) {
  run((cmd) => cmd.executeSetZone(zone));
}
</script>

<template>
  <div
    class="office-ribbon"
    v-bind="{ [EDITOR_COMPONENT]: EditorComponent.MENU }"
    @pointerdown.capture="onRibbonPointerDown"
  >
    <input ref="imageInputRef" class="word-toolbar__file" type="file" accept="image/*" @change="onImageFile" />

    <div class="office-ribbon__scroll">
    <div class="rib-col">
      <div class="rib-col__line">
        <RibbonBtn title="撤销 Ctrl+Z" :disabled="!rangeStyle.undo" @click="run((c) => c.executeUndo())">
          <RibSvg>
            <path d="M3.5 7.5h6.2a3.3 3.3 0 1 1 0 6.6H8" />
            <path d="M3.5 7.5 6.2 4.8M3.5 7.5 6.2 10" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn title="重做 Ctrl+Y" :disabled="!rangeStyle.redo" @click="run((c) => c.executeRedo())">
          <RibSvg>
            <path d="M12.5 7.5H6.3a3.3 3.3 0 1 0 0 6.6H8" />
            <path d="M12.5 7.5 9.8 4.8M12.5 7.5 9.8 10" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn
          title="格式刷"
          :active="rangeStyle.painter"
          @click="run((c) => c.executePainter({ isDblclick: false }))"
        >
          <RibSvg>
            <path d="M3 12.5h4l6-6-2.2-2.2-6 6V12.5Z" />
            <path d="M10.2 4.8 12.5 2.5l1.2 1.2-2.3 2.3" />
          </RibSvg>
        </RibbonBtn>
        <el-select class="rib-select rib-select--font" :model-value="displayFont || undefined" filterable @change="setFont">
          <el-option
            v-for="font in FONT_OPTIONS"
            :key="font.value"
            :label="font.label"
            :value="font.value"
            @click="setFont(font.value)"
          />
        </el-select>
        <el-select
          class="rib-select rib-select--size"
          :model-value="displaySize"
          @visible-change="onSizeVisible"
          @change="setSize"
        >
          <el-option
            v-for="item in FONT_SIZE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            @click="setSize(item.value)"
          />
        </el-select>
        <RibbonBtn title="增大字号" @click="shiftSize(2)">
          <span class="rib-glyph">A+</span>
        </RibbonBtn>
        <RibbonBtn title="减小字号" @click="shiftSize(-2)">
          <span class="rib-glyph">A-</span>
        </RibbonBtn>
        <el-dropdown
          trigger="click"
          @command="(item: (typeof LIST_OPTIONS)[number]) => setList(item.type, item.style)"
        >
          <RibbonBtn title="项目符号与编号">
            <RibSvg>
              <circle cx="4" cy="4.2" r="1" fill="currentColor" stroke="none" />
              <circle cx="4" cy="8" r="1" fill="currentColor" stroke="none" />
              <circle cx="4" cy="11.8" r="1" fill="currentColor" stroke="none" />
              <path d="M7 4.2h6M7 8h6M7 11.8h6" />
            </RibSvg>
          </RibbonBtn>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in LIST_OPTIONS" :key="item.label" :command="item">
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="setRowMargin">
          <RibbonBtn :title="lineSpacingLabel">
            <RibSvg>
              <path d="M4 3.5v9" />
              <path d="M4 3.5 2.4 5.2M4 3.5 5.6 5.2" />
              <path d="M4 12.5 2.4 10.8M4 12.5 5.6 10.8" />
              <path d="M8 5h6M8 8h6M8 11h6" />
            </RibSvg>
          </RibbonBtn>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in LINE_SPACING_PRESETS"
                :key="item.value"
                :command="item.value"
                :class="{ 'is-picked': rangeStyle.rowMargin === item.value }"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="rib-col__line">
        <RibbonBtn title="加粗" :active="rangeStyle.bold" @click="run((c) => c.executeBold())">
          <span class="rib-glyph rib-glyph--bold">B</span>
        </RibbonBtn>
        <RibbonBtn title="斜体" :active="rangeStyle.italic" @click="run((c) => c.executeItalic())">
          <span class="rib-glyph rib-glyph--italic">I</span>
        </RibbonBtn>
        <RibbonBtn title="下划线" :active="rangeStyle.underline" @click="run((c) => c.executeUnderline())">
          <span class="rib-glyph rib-glyph--underline">U</span>
        </RibbonBtn>
        <RibbonBtn title="删除线" :active="rangeStyle.strikeout" @click="run((c) => c.executeStrikeout())">
          <span class="rib-glyph rib-glyph--strike">ab</span>
        </RibbonBtn>
        <RibbonBtn
          title="上标"
          :active="rangeStyle.type === ElementType.SUPERSCRIPT"
          @click="run((c) => c.executeSuperscript())"
        >
          <span class="rib-glyph rib-glyph--super">x²</span>
        </RibbonBtn>
        <RibbonBtn
          title="下标"
          :active="rangeStyle.type === ElementType.SUBSCRIPT"
          @click="run((c) => c.executeSubscript())"
        >
          <span class="rib-glyph rib-glyph--sub">x₂</span>
        </RibbonBtn>
        <div class="rib-color">
          <el-color-picker :model-value="rangeStyle.color || '#000000'" size="small" @change="setColor" />
          <div class="rib-color__face">
            A
            <i class="rib-color__bar" :style="{ background: rangeStyle.color || '#c0392b' }" />
          </div>
        </div>
        <div class="rib-color">
          <el-color-picker :model-value="rangeStyle.highlight || '#ffff00'" size="small" @change="setHighlight" />
          <div class="rib-color__face">
            <RibSvg>
              <path d="M3.5 11.5 8 4.2l3.4 2.4-4.4 7.2H3.5v-2.3Z" />
              <path d="M8.2 4.5 10.5 2.6 13 4.4 10.6 6.4" />
            </RibSvg>
            <i class="rib-color__bar" :style="{ background: rangeStyle.highlight || '#f1c40f' }" />
          </div>
        </div>
        <RibbonBtn title="左对齐" :active="align === RowFlex.LEFT" @click="setAlign(RowFlex.LEFT)">
          <RibSvg>
            <path d="M3 4h10M3 7h7M3 10h10M3 13h6" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn title="居中" :active="align === RowFlex.CENTER" @click="setAlign(RowFlex.CENTER)">
          <RibSvg>
            <path d="M3 4h10M5 7h6M3 10h10M5.5 13h5" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn title="右对齐" :active="align === RowFlex.RIGHT" @click="setAlign(RowFlex.RIGHT)">
          <RibSvg>
            <path d="M3 4h10M6 7h7M3 10h10M7 13h6" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn
          title="两端对齐"
          :active="align === RowFlex.ALIGNMENT || align === RowFlex.JUSTIFY"
          @click="setAlign(RowFlex.ALIGNMENT)"
        >
          <RibSvg>
            <path d="M3 4h10M3 7h10M3 10h10M3 13h10" />
          </RibSvg>
        </RibbonBtn>
      </div>
    </div>

    <span class="rib-vline" aria-hidden="true" />

    <div class="rib-col">
      <div class="rib-col__line">
        <RibbonBtn inline caption="图片" title="插入图片" @click="pickImage">
          <RibSvg>
            <rect x="2.4" y="3.4" width="11.2" height="9.2" rx="1" />
            <circle cx="6" cy="7" r="1.1" />
            <path d="M2.8 11.2 6.4 8.2l2.2 1.8 2.2-2.6 2.4 3.8" />
          </RibSvg>
        </RibbonBtn>
        <el-popover placement="bottom-start" :width="248" trigger="click" @show="syncTableBorder">
          <template #reference>
            <span class="rib-anchor">
              <RibbonBtn inline caption="表格" title="插入表格">
                <RibSvg>
                  <rect x="2.4" y="3.2" width="11.2" height="9.6" rx="0.6" />
                  <path d="M2.4 6.4h11.2M2.4 9.6h11.2M6.1 3.2v9.6M9.9 3.2v9.6" />
                </RibSvg>
              </RibbonBtn>
            </span>
          </template>
          <div class="table-picker">
            <div class="table-picker__hint">插入 {{ tableHover.row }} × {{ tableHover.col }}</div>
            <div class="table-picker__grid">
              <button
                v-for="cell in 64"
                :key="cell"
                type="button"
                class="table-picker__cell"
                :class="{
                  'is-on': Math.ceil(cell / 8) <= tableHover.row && ((cell - 1) % 8) + 1 <= tableHover.col,
                }"
                @mouseenter="
                  tableHover.row = Math.ceil(cell / 8);
                  tableHover.col = ((cell - 1) % 8) + 1;
                "
                @click="insertTable(tableHover.row, tableHover.col)"
              />
            </div>
            <div class="table-picker__ops">
              <button type="button" @click="run((c) => c.executeInsertTableTopRow())">上方插行</button>
              <button type="button" @click="run((c) => c.executeInsertTableBottomRow())">下方插行</button>
              <button type="button" @click="run((c) => c.executeInsertTableLeftCol())">左侧插列</button>
              <button type="button" @click="run((c) => c.executeInsertTableRightCol())">右侧插列</button>
              <button type="button" @click="run((c) => c.executeDeleteTableRow())">删除行</button>
              <button type="button" @click="run((c) => c.executeDeleteTableCol())">删除列</button>
              <button type="button" @click="run((c) => c.executeMergeTableCell())">合并</button>
              <button type="button" @click="run((c) => c.executeCancelMergeTableCell())">拆分</button>
            </div>
            <div class="table-picker__border">
              <div class="table-picker__hint table-picker__hint--sub">当前表格边框</div>
              <p v-if="!latchedTableId" class="table-picker__hint">先把光标放进表格</p>
              <div v-else class="table-picker__border-row">
                <label class="table-picker__field">
                  <span>颜色</span>
                  <input type="color" :value="borderColor" @change="onBorderColor" />
                </label>
                <label class="table-picker__field">
                  <span>磅数</span>
                  <select :value="String(borderPt)" @change="onBorderPt">
                    <option v-for="pt in borderPtOptions" :key="pt" :value="String(pt)">{{ pt }} 磅</option>
                  </select>
                </label>
              </div>
            </div>
          </div>
        </el-popover>
        <RibbonBtn inline caption="分页" title="分页符" @click="run((c) => c.executePageBreak())">
          <RibSvg>
            <path d="M3.5 3.5h9v3.2h-9z" />
            <path d="M3.5 12.5h9v-3.2h-9z" />
            <path d="M3 8h10" stroke-dasharray="1.4 1.4" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn inline caption="文本" title="插入自由文本" @click="openWidget('text')">
          <RibSvg>
            <rect x="2.6" y="3.4" width="10.8" height="9.2" rx="1" />
            <path d="M5 6.2h6M5 8.4h4.4M5 10.6h5.2" />
          </RibSvg>
        </RibbonBtn>
      </div>
      <div class="rib-col__line">
        <RibbonBtn inline caption="下拉" title="插入下拉" @click="openWidget('select')">
          <RibSvg>
            <rect x="2.4" y="4.2" width="11.2" height="7.6" rx="1" />
            <path d="M6 7.2 8 9.4 10 7.2" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn inline caption="单选" title="插入单选" @click="openWidget('radio')">
          <RibSvg>
            <rect x="2.6" y="3.4" width="4.4" height="4.4" rx="0.6" />
            <rect x="2.6" y="8.6" width="4.4" height="4.4" rx="0.6" />
            <path d="M8.2 5.6h5.2M8.2 10.8h5.2" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn inline caption="日期" title="插入日期" @click="openWidget('date')">
          <RibSvg>
            <rect x="2.6" y="3.8" width="10.8" height="9" rx="1" />
            <path d="M2.6 7h10.8M6 3.2v2.2M10 3.2v2.2" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn inline caption="复选" title="插入复选" @click="openWidget('checkbox')">
          <RibSvg>
            <rect x="2.6" y="5.6" width="5" height="5" rx="0.6" />
            <path d="M3.6 8 4.8 9.2 7 6.8" />
            <path d="M9 8h4.4" />
          </RibSvg>
        </RibbonBtn>
      </div>
    </div>

    <span class="rib-vline" aria-hidden="true" />

    <div class="rib-col">
      <div class="rib-col__line">
        <el-dropdown trigger="click" @command="applyMargin">
          <RibbonBtn inline caption="页边距" title="页边距">
            <RibSvg>
              <rect x="3" y="2.8" width="10" height="10.4" />
              <rect x="5" y="5" width="6" height="6" />
            </RibSvg>
          </RibbonBtn>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in MARGIN_PRESETS" :key="item.label" :command="item.label">
                {{ item.label }}
                <span class="rib-hint">{{ item.hint }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="setZone">
          <RibbonBtn inline caption="页眉" title="页眉和页脚">
            <RibSvg>
              <rect x="3" y="2.6" width="10" height="10.8" />
              <path d="M3 5.4h10" />
            </RibSvg>
          </RibbonBtn>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="EditorZone.HEADER">编辑页眉</el-dropdown-item>
              <el-dropdown-item :command="EditorZone.MAIN">回到正文</el-dropdown-item>
              <el-dropdown-item :command="EditorZone.FOOTER">编辑页脚</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-popover placement="bottom-start" :width="280" trigger="click">
          <template #reference>
            <span class="rib-anchor">
              <RibbonBtn inline caption="样式集" title="段落样式">
                <RibSvg>
                  <path d="M4 3.4h8M4 8h5.5M4 12.4h8" />
                </RibSvg>
              </RibbonBtn>
            </span>
          </template>
          <div class="style-gallery">
            <button
              v-for="item in STYLE_GALLERY"
              :key="item.label"
              type="button"
              class="style-chip"
              :class="[item.klass, { 'is-on': titleValue === item.value }]"
              @click="titleValue = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </el-popover>
      </div>
      <div class="rib-col__line">
        <RibbonBtn inline caption="查找替换" title="查找和替换" @click="emit('toggleFind')">
          <RibSvg>
            <circle cx="7" cy="7" r="3.4" />
            <path d="M9.6 9.6 13 13" />
          </RibSvg>
        </RibbonBtn>
        <RibbonBtn
          v-if="!$slots.host && !$slots.print"
          inline
          caption="打印"
          title="打印"
          @click="run((c) => c.executePrint())"
        >
          <RibSvg>
            <path d="M4.4 9.6V3.4h7.2v6.2" />
            <rect x="3" y="8.2" width="10" height="4.6" rx="0.6" />
            <path d="M5.2 11.2h5.6" />
          </RibSvg>
        </RibbonBtn>
      </div>
    </div>
    </div>

    <template v-if="$slots.print || $slots.host">
      <span class="rib-vline" aria-hidden="true" />
      <div class="rib-col rib-col--station">
        <div class="rib-col__line">
          <slot name="print" />
        </div>
        <div class="rib-col__line">
          <slot name="host" />
        </div>
      </div>
    </template>
  </div>
  <InsertWidgetDialog v-model="widgetOpen" :kind="widgetKind" @confirm="onWidgetConfirm" />
</template>
