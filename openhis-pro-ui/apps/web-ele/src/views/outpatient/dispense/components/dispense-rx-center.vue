<script setup lang="ts">
import type { TableInstance } from 'element-plus/es/components/table';

import type {
  DispensePrescriptionBlock,
  DispensePrescriptionItemRow,
  DispenseQueuePatient,
  DispenseRxKindBadgeCounts,
  DispenseRxTableRow,
  DispenseRxTableSpacerRow,
} from './types';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { MSFX_HIS_SCENE } from '#/msfx/constants';
import MsfxUploadStatusBar from '#/msfx/components/msfx-upload-status-bar.vue';

import {
  resolveBatchNoByPick,
  resolveRemainingStockTextByPick,
} from '../services/dispense-lot-pick';

import { buildPrescriptionSheetData } from './build-prescription-sheet-data';
import PrescriptionSheetView from './prescription-sheet-view.vue';

defineOptions({
  name: 'DispenseRxCenter',
});

const props = withDefaults(
  defineProps<{
    /** 已发药队列等场景禁止提交 */
    batchDispenseDisabled?: boolean;
    dispenserOptions: { label: string; value: string }[];
    displayTableRows: DispenseRxTableRow[];
    /** 当前 tab 下表格用处方块 */
    prescriptionBlocks: DispensePrescriptionBlock[];
    prescriptionRowCount: number;
    /** 发药单视图：就诊下全部处方块（由父级单独拉全量 slip 后传入） */
    prescriptionSheetBlocks?: DispensePrescriptionBlock[];
    /** 发药单视图：中医主诊断（病证合并） */
    sheetTcmDiagnosisText?: string;
    /** 发药单数据加载中（切换就诊时） */
    sheetLoading?: boolean;
    printPrescriptionsDisabled?: boolean;
    /** 扫码后各发放行追溯码（回显到发药单卡片） */
    traceCodeByLineId?: Record<string, string>;
    /** 中西药 tab 角标（处方块数） */
    rxKindBadgeCounts?: DispenseRxKindBadgeCounts;
    /** 是否启用发药单视图（住院按患者发药关闭，仅列表明细） */
    enableSheetView?: boolean;
    /**
     * 左侧合并列「单号」表头文案。
     * 门诊默认「处方号」；住院按日虚拟发药单传「发药单」。
     */
    groupNoColumnLabel?: string;
    /**
     * 发药单笺面左上角单号标签（传给 PrescriptionSheetView）。
     * 门诊默认「处方号」；住院传「发药单」。
     */
    sheetNoLabel?: string;
    selectedPatient: DispenseQueuePatient | null;
    /** 码上放心 HIS 场景（门诊/住院发药） */
    msfxHisScene?: string;
    /**
     * 是否展示「发药完成自动点评」开关（门诊发药开启；住院等可关）。
     */
    showAutoReviewSwitch?: boolean;
    /**
     * 是否展示「发药后自动打印」开关（门诊发药开启；住院按患者发药开启）。
     */
    showAutoPrintPrescriptionSwitch?: boolean;
    /** 自动打印开关文案。门诊默认「发药后自动打印处方」。 */
    autoPrintSwitchLabel?: string;
  }>(),
  {
    prescriptionSheetBlocks: () => [],
    rxKindBadgeCounts: () => ({ western: 0, herbal: 0 }),
    printPrescriptionsDisabled: false,
    sheetLoading: false,
    traceCodeByLineId: () => ({}),
    enableSheetView: true,
    groupNoColumnLabel: '处方号',
    sheetNoLabel: '处方号',
    msfxHisScene: MSFX_HIS_SCENE.OP_DISPENSE,
    showAutoReviewSwitch: false,
    showAutoPrintPrescriptionSwitch: false,
    autoPrintSwitchLabel: '发药后自动打印处方',
  },
);

/** 发药完成自动点评开关（与父级双向绑定）。 */
const autoReviewEnabled = defineModel<boolean>('autoReviewEnabled', {
  default: false,
});

/** 发药后自动打印处方开关（与父级双向绑定）。 */
const autoPrintPrescriptionEnabled = defineModel<boolean>('autoPrintPrescriptionEnabled', {
  default: false,
});

const emit = defineEmits<{
  batchDispense: [];
  batchTraceScan: [];
  /** 发药单视图：按处方块单独发药 */
  dispensePrescription: [rxId: string];
  /** 发药单视图：按处方块单独打印 */
  printPrescription: [rxId: string];
  printPrescriptions: [];
}>();

const activeRxKind = defineModel<'herbal' | 'western'>('activeRxKind', { required: true });
const selectedDispenserId = defineModel<string | undefined>('selectedDispenserId', {
  required: true,
});
const selectedPrescriptionId = defineModel<null | string>('selectedPrescriptionId', {
  required: true,
});

/** 每行发放明细选中的「库房×批号」编码（与 inventoryPickOptions.value 对应） */
const inventoryPickByLineId = defineModel<Record<string, string>>('inventoryPickByLineId', {
  required: true,
});

/** 左侧合并列数：仅前三列（多选、处方号、状态） */
const RX_TABLE_META_COLS = 3;
/** 西药 tab 列数（与模板列数一致，供间隔行 colspan；含剩余库存列） */
const RX_TABLE_WESTERN_COLS = 14;

const rxTableRef = ref<null | TableInstance>(null);

/** 当前 tab 下列数：中药 tab 多一列「特殊煎法」 */
const rxTableTotalCols = computed(() =>
  activeRxKind.value === 'herbal' ? RX_TABLE_WESTERN_COLS + 1 : RX_TABLE_WESTERN_COLS,
);

/** 右侧工作区：发药单（默认）/ 列表明细；enableSheetView=false 时强制明细 */
const workbenchViewMode = ref<'list' | 'sheet'>(props.enableSheetView ? 'sheet' : 'list');
/** 发药单视图多选的处方块 id（批量发药） */
const selectedSheetRxIds = ref<string[]>([]);

/** 实际展示模式：关闭笺面时始终为列表明细 */
const resolvedViewMode = computed(() => (props.enableSheetView ? workbenchViewMode.value : 'list'));

watch(
  () => props.selectedPatient?.id,
  () => {
    selectedSheetRxIds.value = [];
    // 换患者后：有笺面则回发药单，否则保持明细
    workbenchViewMode.value = props.enableSheetView ? 'sheet' : 'list';
  },
);

watch(
  () => props.enableSheetView,
  (enabled) => {
    if (!enabled) workbenchViewMode.value = 'list';
  },
);

watch(activeRxKind, () => {
  selectedSheetRxIds.value = [];
});
/** 处方组首行且可批量发药：待发 / 已配药 / 部分可发（已发、拦截不默认勾选） */
function isDefaultSelectableRxGroupHead(
  row: DispenseRxTableRow,
): row is DispensePrescriptionItemRow {
  if (isSpacerRow(row) || !row.isFirstInRx) return false;
  return row.status === 'pending' || row.status === 'prepared' || row.status === 'partial';
}

/** 选中患者且明细加载后：默认勾选所有待发/已配药/部分可发处方组（每组仅首行可选） */
watch(
  () => {
    const pid = props.selectedPatient?.id ?? '';
    const n = props.prescriptionRowCount;
    const disabled = props.batchDispenseDisabled ?? false;
    const heads = props.displayTableRows.filter((r) => isDefaultSelectableRxGroupHead(r));
    const fp = heads.map((r) => `${r.rxId}:${r.status}`).join('|');
    return { pid, n, fp, disabled };
  },
  async ({ pid, n, fp, disabled }) => {
    if (!pid || n === 0) return;
    await nextTick();
    await nextTick();
    const table = rxTableRef.value;
    if (!table) return;
    // 已发药队列：不清空表格勾选，允许用户勾选后批量打印
    if (disabled && !fp) {
      return;
    }
    table.clearSelection();
    // 待发提交/加载门控中或无可发行：仅清空不默认勾
    if (disabled || !fp) return;
    for (const row of props.displayTableRows) {
      if (!isDefaultSelectableRxGroupHead(row)) continue;
      table.toggleRowSelection(row, true);
    }
  },
  { flush: 'post' },
);

/** 单张处方是否可单独发药（与表格默认可勾选态一致） */
function isSheetDispensableStatus(status: DispensePrescriptionBlock['status']): boolean {
  return status === 'pending' || status === 'prepared' || status === 'partial';
}

/**
 * 笺面是否可勾选：可发药态；或已发药（仅供批量打印，发药按钮仍由 canDispense 关闭）。
 * 待发队列提交中 `batchDispenseDisabled` 为 true 时仍禁止勾选可发行，避免并发误选。
 */
function isSheetSelectable(item: {
  canDispense: boolean;
  status: DispensePrescriptionBlock['status'];
}): boolean {
  // 已发药：允许勾选以便批量打印（与「禁止发药」解耦）
  if (item.status === 'completed') {
    return true;
  }
  // 可发药态：仅待发队列且未在提交/加载门控中
  if (item.canDispense && !props.batchDispenseDisabled) {
    return true;
  }
  return false;
}

/** 发药单视图：按当前中西药 tab 过滤后的笺面（与列表明细同源切换） */
const prescriptionSheetViewItems = computed(() => {
  const patient = props.selectedPatient;
  if (!patient) return [];
  // 只展示当前西药/中药 tab 下的处方块
  const blocks = props.prescriptionSheetBlocks.filter(
    (block) => block.rxType === activeRxKind.value,
  );
  if (blocks.length === 0) return [];
  return blocks.map((block) => ({
    key: block.id,
    rxId: block.id,
    status: block.status,
    canDispense: isSheetDispensableStatus(block.status),
    data: buildPrescriptionSheetData(patient, block, {
      tcmDiagnosisText: props.sheetTcmDiagnosisText,
      traceCodeByLineId: props.traceCodeByLineId,
    }),
    /** 该发药单下发放行主键（批量/单张发药取 id；排除代煎费；合并行展开 sourceDispenseIds） */
    lineIds: block.items
      .filter((line) => line.lineKind !== 'decoctionFee')
      .flatMap((line) =>
        line.sourceDispenseIds && line.sourceDispenseIds.length > 0
          ? line.sourceDispenseIds
          : [line.id],
      ),
  }));
});

/**
 * 笺面默认勾选的处方块 id（仅可发药态；已发药不默认勾选，需用户显式勾选后打印）。
 */
const selectableSheetRxIds = computed(() =>
  prescriptionSheetViewItems.value.filter((item) => item.canDispense).map((item) => item.rxId),
);

/** 笺面加载后默认勾选全部可发处方（与表格默认勾选一致） */
watch(
  () => {
    const pid = props.selectedPatient?.id ?? '';
    const ids = selectableSheetRxIds.value.join('|');
    const disabled = props.batchDispenseDisabled ?? false;
    return { pid, ids, disabled };
  },
  ({ pid, ids, disabled }) => {
    // 无患者：清空勾选
    if (!pid) {
      selectedSheetRxIds.value = [];
      return;
    }
    // 已发药队列（禁发药且无可发行）：保留用户为批量打印勾选的项；切患者/中西药由其它 watch 清空
    if (disabled && !ids) {
      return;
    }
    // 待发提交/加载门控中，或无可发行：清空，避免误发
    if (disabled || !ids) {
      selectedSheetRxIds.value = [];
      return;
    }
    selectedSheetRxIds.value = ids.split('|').filter(Boolean);
  },
  { immediate: true },
);

/** 更新单张笺面勾选 */
function setSheetSelected(rxId: string, selected: boolean) {
  const set = new Set(selectedSheetRxIds.value);
  if (selected) set.add(rxId);
  else set.delete(rxId);
  selectedSheetRxIds.value = [...set];
}

/** 笺面是否已勾选 */
function isSheetSelected(rxId: string): boolean {
  return selectedSheetRxIds.value.includes(rxId);
}

/** 发药单空态文案 */
const sheetEmptyText = computed(() => {
  if (!props.selectedPatient) return '请从左侧选择患者';
  return `当前类型下无${props.groupNoColumnLabel}，请切换上方西药/中药`;
});

/** 无患者或无处方时用空表数据，表头与表格框架仍展示 */
const rxTableBodyRows = computed<DispenseRxTableRow[]>(() => {
  if (!props.selectedPatient || props.prescriptionRowCount === 0) return [];
  return props.displayTableRows;
});

/** 空表时区分未选患者与已选但无明细 */
const rxTableEmptyText = computed(() => {
  if (!props.selectedPatient) return '请从左侧选择患者';
  return `当前类型下无${props.groupNoColumnLabel}，请切换上方西药/中药`;
});

/** 仅中西药均有处方时展示 tab 角标，提示另一侧待发 */
const showRxKindBadges = computed(
  () => props.rxKindBadgeCounts.western > 0 && props.rxKindBadgeCounts.herbal > 0,
);

function formatRxKindTabLabel(base: string, count: number): string {
  if (!showRxKindBadges.value) return base;
  return `${base} (${count})`;
}

/** 父级在切换患者/中西药后清空表格与笺面勾选 */
async function clearRxTableSelection() {
  selectedSheetRxIds.value = [];
  await nextTick();
  rxTableRef.value?.clearSelection();
}

/** 切到发药单视图（父级在拉完笺面数据后调用；笺面关闭时无效） */
function switchToSheetView() {
  if (!props.enableSheetView) return;
  workbenchViewMode.value = 'sheet';
}

/** 间隔行判定，用于 span 与行 class */
function isSpacerRow(row: DispenseRxTableRow): row is DispenseRxTableSpacerRow {
  return 'rowKind' in row && row.rowKind === 'spacer';
}

/**
 * 取勾选发药单下的全部发放行主键。
 * 发药单视图用笺面多选；列表明细视图用表格多选。
 * 住院同医嘱合并展示行通过 sourceDispenseIds 展开为底层 dispenseId。
 */
function getSelectedDispenseLineIds(): string[] {
  if (resolvedViewMode.value === 'sheet') {
    const rxIds = new Set(selectedSheetRxIds.value);
    if (rxIds.size === 0) return [];
    const ids: string[] = [];
    for (const item of prescriptionSheetViewItems.value) {
      if (!rxIds.has(item.rxId)) continue;
      ids.push(...item.lineIds);
    }
    return ids;
  }
  const selected = (rxTableRef.value?.getSelectionRows() ?? []) as DispenseRxTableRow[];
  if (selected.length === 0) return [];
  const rxIds = new Set<string>();
  for (const row of selected) {
    if (isSpacerRow(row)) continue;
    if (row.isFirstInRx) rxIds.add(row.rxId);
  }
  const ids: string[] = [];
  for (const row of props.displayTableRows) {
    if (isSpacerRow(row)) continue;
    // 代煎费为合成展示行，不参与发药提交
    if (row.lineKind === 'decoctionFee') continue;
    if (!rxIds.has(row.rxId)) continue;
    // 合并展示行：提交须覆盖全部底层发放事实
    if (row.sourceDispenseIds && row.sourceDispenseIds.length > 0) {
      ids.push(...row.sourceDispenseIds);
    } else {
      ids.push(row.id);
    }
  }
  return ids;
}

defineExpose({
  clearRxTableSelection,
  getSelectedDispenseLineIds,
  switchToSheetView,
});

/** 单表 rowspan：前三列按处方合并，间隔行整行合并 */
function rxTableSpanMethod({
  row,
  columnIndex,
}: {
  columnIndex: number;
  row: DispenseRxTableRow;
}): [number, number] | undefined {
  if (isSpacerRow(row)) {
    if (columnIndex === 0) return [1, rxTableTotalCols.value];
    return [0, 0];
  }
  if (columnIndex < RX_TABLE_META_COLS) {
    if (row.isFirstInRx) return [row.rxRowSpan, 1];
    return [0, 0];
  }
  return undefined;
}

/** 仅每组首行可选，与原生多选对齐 */
function rxRowSelectable(row: DispenseRxTableRow) {
  if (isSpacerRow(row)) return false;
  return row.isFirstInRx;
}

/** 处方状态 → 标签 */
function rxStatusTagRow(row: DispensePrescriptionItemRow) {
  if (row.status === 'blocking') return { text: '拦截', type: 'danger' as const };
  if (row.status === 'partial') return { text: '部分可发', type: 'warning' as const };
  if (row.status === 'prepared') return { text: '已配药', type: 'warning' as const };
  if (row.status === 'completed') return { text: '已发', type: 'success' as const };
  return { text: '待发', type: 'success' as const };
}

/** 更新单行批号选择（库房已锁定，选项仅换批号） */
function setInventoryPick(lineId: string, value: string) {
  inventoryPickByLineId.value = { ...inventoryPickByLineId.value, [lineId]: value };
}

/** 仅待发（PREPARATION）可改批号；已配药提交会跳过 savePrepare，改了也不会落库 */
function canChangeLotOnRow(row: DispenseRxTableRow): boolean {
  if (isSpacerRow(row) || row.lineKind === 'decoctionFee') return false;
  if (row.status !== 'pending') return false;
  return Boolean(row.inventoryPickOptions?.length);
}

/** 剩余库存随当前选中批号变化 */
function remainingStockDisplay(row: DispenseRxTableRow): string {
  if (isSpacerRow(row) || row.lineKind === 'decoctionFee') return '—';
  return resolveRemainingStockTextByPick(
    row.inventoryPickOptions,
    inventoryPickByLineId.value[row.id],
    row.remainingStockText,
  );
}

/** 批次号列展示：有选中项取批号字面量，否则行上默认 */
function batchNoDisplay(row: DispenseRxTableRow): string {
  if (isSpacerRow(row) || row.lineKind === 'decoctionFee') return '—';
  return resolveBatchNoByPick(
    row.inventoryPickOptions,
    inventoryPickByLineId.value[row.id],
    row.batchNo,
  );
}
/** 行样式：组块圆角、选中底、间隔行压平 */
function prescriptionTableRowClassName({ row }: { row: DispenseRxTableRow }) {
  if (isSpacerRow(row)) return 'dispense-rx-gap-row';
  const classes = ['dispense-rx-body'];
  if (row.isFirstInRx) classes.push('dispense-rx-body--first');
  if (row.isLastInRx) classes.push('dispense-rx-body--last');
  if (row.rxId === selectedPrescriptionId.value) classes.push('dispense-rx-body--selected');
  return classes.join(' ');
}

/** 点击数据行时高亮整张处方（跳过间隔行） */
function handleRxTableRowClick(row: DispenseRxTableRow) {
  if (isSpacerRow(row)) return;
  selectedPrescriptionId.value = row.rxId;
}

/** 当前选中处方块的发放 busNo（码上放心上传键） */
const msfxDispenseBusNo = computed(() => {
  const id = selectedPrescriptionId.value;
  if (!id) {
    // 未选中：取当前列表首块
    const first = props.prescriptionBlocks[0] ?? props.prescriptionSheetBlocks?.[0];
    return String(first?.dispenseBusNo ?? '').trim();
  }
  const hit =
    props.prescriptionBlocks.find((b) => b.id === id) ??
    props.prescriptionSheetBlocks?.find((b) => b.id === id);
  return String(hit?.dispenseBusNo ?? '').trim();
});
</script>

<template>
  <div class="dispense-rx-center flex h-full min-h-0 flex-col overflow-hidden">
    <div
      class="dispense-rx-center__body flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-3 pb-2 pt-2"
    >
      <div
        class="dispense-rx-toolbar flex flex-col gap-1.5 border-b border-[var(--el-border-color-lighter)] pb-2"
      >
        <SectionTitle title="处方列表" class="!mb-0 shrink-0" />
        <div class="dispense-rx-toolbar-bar">
          <div class="dispense-rx-toolbar-group">
            <ElButton
              type="primary"
              :disabled="!props.selectedPatient || props.batchDispenseDisabled"
              @click="emit('batchDispense')"
            >
              批量发药
            </ElButton>
            <ElButton
              type="primary"
              plain
              :disabled="!props.selectedPatient"
              @click="emit('batchTraceScan')"
            >
              扫追溯码
            </ElButton>
            <ElButton
              type="primary"
              plain
              :disabled="!props.selectedPatient || props.printPrescriptionsDisabled"
              @click="emit('printPrescriptions')"
            >
              处方打印
            </ElButton>
            <MsfxUploadStatusBar
              class="ml-1"
              :bus-no="msfxDispenseBusNo"
              :his-scene-enum="msfxHisScene"
              :need-supplier-ent-id="false"
              compact
            />
          </div>
          <div class="dispense-rx-toolbar-split" aria-hidden="true"></div>
          <div class="dispense-rx-toolbar-group">
            <ElRadioGroup v-model="activeRxKind" class="dispense-rx-kind w-fit shrink-0">
              <ElRadioButton value="western">
                {{ formatRxKindTabLabel('西药 / 成药', props.rxKindBadgeCounts.western) }}
              </ElRadioButton>
              <ElRadioButton value="herbal">
                {{ formatRxKindTabLabel('中药饮片', props.rxKindBadgeCounts.herbal) }}
              </ElRadioButton>
            </ElRadioGroup>
            <div class="flex shrink-0 items-center gap-1.5 whitespace-nowrap">
              <span class="text-xs text-[var(--el-text-color-secondary)]">调配药师</span>
              <ElSelect
                v-model="selectedDispenserId"
                placeholder="请选择调配药师"
                clearable
                filterable
                class="dispense-pharmacist-select !w-40 shrink-0"
              >
                <ElOption
                  v-for="opt in props.dispenserOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>
            <div
              v-if="props.showAutoReviewSwitch"
              class="flex shrink-0 items-center gap-1.5 whitespace-nowrap"
            >
              <span class="text-xs text-[var(--el-text-color-regular)]">发药完成自动点评</span>
              <ElSwitch
                v-model="autoReviewEnabled"
                inline-prompt
                active-text="开"
                inactive-text="关"
              />
            </div>
            <div
              v-if="props.showAutoPrintPrescriptionSwitch"
              class="flex shrink-0 items-center gap-1.5 whitespace-nowrap"
              :title="`开启后，单张或批量发药成功会自动打印本次发出的单据`"
            >
              <span class="text-xs text-[var(--el-text-color-regular)]">{{
                props.autoPrintSwitchLabel
              }}</span>
              <ElSwitch
                v-model="autoPrintPrescriptionEnabled"
                inline-prompt
                active-text="开"
                inactive-text="关"
              />
            </div>
          </div>
          <template v-if="props.enableSheetView">
            <div class="dispense-rx-toolbar-spacer"></div>
            <div class="dispense-rx-toolbar-group dispense-rx-toolbar-group--view">
              <ElRadioGroup
                v-model="workbenchViewMode"
                class="dispense-rx-view-mode w-fit shrink-0"
              >
                <ElRadioButton value="sheet">发药单</ElRadioButton>
                <ElRadioButton value="list">列表明细</ElRadioButton>
              </ElRadioGroup>
            </div>
          </template>
        </div>
      </div>
      <div class="dispense-rx-body-wrap flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <!-- 发药单视图（默认）：居左横向多张；宽度不够换行，纵向滚动；笺内勾选可批量发药 -->
        <div
          v-if="props.enableSheetView"
          v-show="resolvedViewMode === 'sheet'"
          v-loading="props.sheetLoading"
          class="dispense-rx-sheet-pane min-h-0 flex-1 overflow-hidden pb-1"
        >
          <div
            v-if="prescriptionSheetViewItems.length > 0"
            class="dispense-rx-sheet-row flex h-full min-h-0 flex-wrap content-start items-stretch justify-start gap-4 overflow-y-auto overflow-x-hidden"
          >
            <PrescriptionSheetView
              v-for="item in prescriptionSheetViewItems"
              :key="item.key"
              class="dispense-rx-sheet-card h-full min-h-0 shrink-0"
              :data="item.data"
              :prescription-no-label="props.sheetNoLabel"
              v-model:inventory-pick-by-line-id="inventoryPickByLineId"
              comfortable
              workbench-actions
              :selected="isSheetSelected(item.rxId)"
              :selectable="isSheetSelectable(item)"
              :can-dispense="item.canDispense"
              :dispense-disabled="props.batchDispenseDisabled"
              :print-disabled="props.printPrescriptionsDisabled"
              @update:selected="(v) => setSheetSelected(item.rxId, v)"
              @dispense="emit('dispensePrescription', item.rxId)"
              @print="emit('printPrescription', item.rxId)"
            />
          </div>
          <ElEmpty
            v-else
            class="dispense-rx-empty flex h-full flex-col justify-center"
            :description="sheetEmptyText"
            :image-size="72"
          />
        </div>

        <!-- 列表明细：有笺面时 v-show 保持挂载以便切回保留勾选 -->
        <ElTable
          v-show="resolvedViewMode === 'list'"
          v-if="props.selectedPatient && props.prescriptionRowCount > 0"
          ref="rxTableRef"
          :data="rxTableBodyRows"
          :span-method="rxTableSpanMethod"
          :row-class-name="prescriptionTableRowClassName"
          row-key="id"
          size="default"
          height="100%"
          class="dispense-rx-grouped-table w-full min-w-0"
          :empty-text="rxTableEmptyText"
          @row-click="handleRxTableRowClick"
        >
          <ElTableColumn
            type="selection"
            fixed="left"
            width="48"
            align="center"
            header-align="center"
            :selectable="rxRowSelectable"
          />
          <ElTableColumn
            prop="rxNo"
            :label="props.groupNoColumnLabel"
            width="160"
            fixed="left"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn label="状态" width="100" align="center" header-align="center" fixed="left">
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row) && row.isFirstInRx">
                <ElTag :type="rxStatusTagRow(row).type" size="default">
                  {{ rxStatusTagRow(row).text }}
                </ElTag>
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="itemName"
            label="项目名称"
            width="200"
            class-name="dispense-rx-item-name-cell"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="diagnosis"
            label="诊断"
            width="160"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="quantity"
            label="数量"
            width="100"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="remainingStockText"
            label="剩余库存"
            width="120"
            align="center"
            header-align="center"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row)">
                {{ remainingStockDisplay(row) }}
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="spec"
            label="规格"
            width="130"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="packageSplitRatioText"
            label="包装最小单位换算比"
            width="140"
            align="center"
            header-align="center"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row)">
                {{ row.packageSplitRatioText?.trim() || '—' }}
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-if="activeRxKind === 'herbal'"
            prop="dosageInstructionText"
            label="特殊煎法"
            width="110"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="batchNo"
            label="批次号"
            min-width="200"
            align="center"
            header-align="center"
          >
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row)">
                <div
                  v-if="canChangeLotOnRow(row)"
                  class="flex min-h-8 min-w-[160px] items-center justify-center"
                  @click.stop
                >
                  <ElSelect
                    :model-value="inventoryPickByLineId[row.id]"
                    filterable
                    placeholder="选择批号"
                    class="dispense-lot-pick-select !w-full"
                    @update:model-value="(v) => setInventoryPick(row.id, String(v ?? ''))"
                  >
                    <ElOption
                      v-for="opt in row.inventoryPickOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </div>
                <span v-else class="text-[var(--el-text-color-regular)]">
                  {{ batchNoDisplay(row) }}
                </span>
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="amount"
            label="金额"
            width="100"
            align="center"
            header-align="center"
          />
          <ElTableColumn label="发放库房" width="160" align="center" header-align="center">
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row)">
                <span class="text-[var(--el-text-color-regular)]">
                  {{ row.lineKind === 'decoctionFee' ? '—' : row.pharmacy }}
                </span>
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="traceCode" label="追溯码" align="center" header-align="center">
            <template #default="{ row }">
              <TraceCodeTooltip :value="row.traceCode" />
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="factory"
            label="厂家"
            width="200"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
        </ElTable>
        <ElEmpty
          v-show="resolvedViewMode === 'list'"
          v-if="!props.selectedPatient"
          class="dispense-rx-empty flex flex-1 flex-col justify-center"
          description="请从左侧选择患者"
          :image-size="72"
        />
        <ElEmpty
          v-show="resolvedViewMode === 'list'"
          v-else-if="!(props.selectedPatient && props.prescriptionRowCount > 0)"
          class="dispense-rx-empty flex flex-1 flex-col justify-center"
          description="当前类型下无处方，请切换上方西药/中药"
          :image-size="56"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.dispense-rx-grouped-table.el-table) {
  /* 仅成组最外轮廓用强调色；表内网格仍走主题边框 */
  --dispense-rx-group-outer-border: #a0a7af;
  --el-table-header-bg-color: hsl(var(--accent));
  --el-table-header-text-color: hsl(var(--accent-foreground));
  --el-table-text-color: hsl(var(--foreground));
  --el-table-bg-color: hsl(var(--background));
  --el-table-tr-bg-color: hsl(var(--background));
  --el-table-row-hover-bg-color: hsl(var(--accent-hover));
  --el-table-border-color: hsl(var(--border));
  --el-table-current-row-bg-color: hsl(var(--accent) / 35%);
  font-size: var(--el-font-size-base);
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-gap-row) {
  height: 6px !important;
  max-height: 6px !important;
  line-height: 0;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-gap-row td.el-table__cell) {
  height: 6px !important;
  min-height: 0 !important;
  max-height: 6px !important;
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  line-height: 0;
  vertical-align: middle;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-gap-row td.el-table__cell .cell) {
  padding: 0 !important;
  min-height: 0 !important;
  max-height: 0 !important;
  line-height: 0 !important;
  font-size: 0 !important;
  overflow: hidden !important;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-gap-row label.el-checkbox),
:deep(.dispense-rx-grouped-table tr.dispense-rx-gap-row .el-checkbox) {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  margin: 0 !important;
  padding: 0 !important;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body > td.el-table__cell) {
  background-color: hsl(var(--background));
  color: hsl(var(--foreground));
}

/* 表体列间短竖线：不含左侧合并三列（多选、处方号、状态）；固定列区不加 */
:deep(
  .dispense-rx-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.dispense-rx-body--first
    > td.el-table__cell:nth-child(n + 4):not(:last-child)
) {
  position: relative;
}

:deep(
  .dispense-rx-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.dispense-rx-body--first
    > td.el-table__cell:nth-child(n + 4):not(:last-child)::after
) {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 1;
  width: 1px;
  height: 16px;
  background-color: var(--el-border-color);
  transform: translateY(-50%);
  pointer-events: none;
}

:deep(
  .dispense-rx-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.dispense-rx-body:not(.dispense-rx-body--first)
    > td.el-table__cell:not(:last-child)
) {
  position: relative;
}

:deep(
  .dispense-rx-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.dispense-rx-body:not(.dispense-rx-body--first)
    > td.el-table__cell:not(:last-child)::after
) {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 1;
  width: 1px;
  height: 16px;
  background-color: var(--el-border-color);
  transform: translateY(-50%);
  pointer-events: none;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell) {
  border-top: 2px solid var(--dispense-rx-group-outer-border);
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body--last > td.el-table__cell) {
  border-bottom: 2px solid var(--dispense-rx-group-outer-border);
}

:deep(.dispense-rx-grouped-table.el-table--border) {
  border-left: none;
  border-right: none;
}

:deep(.dispense-rx-grouped-table.el-table--border::before),
:deep(.dispense-rx-grouped-table.el-table--border::after) {
  display: none !important;
}

:deep(.dispense-rx-grouped-table .el-table__border-left-patch),
:deep(.dispense-rx-grouped-table .el-table__border-right-patch) {
  display: none !important;
}

:deep(.dispense-rx-grouped-table.el-table--border .el-table__header th.el-table__cell:first-child) {
  border-left: none !important;
}

:deep(.dispense-rx-grouped-table.el-table--border .el-table__header th.el-table__cell:last-child) {
  border-right: none !important;
}

/*
 * 成组外框：合并列只在首行有 td，续行「第一个 td」实为项目名称列，不可用 :first-child 画左边线/左下圆角。
 * 左侧 2px 与左下圆角仅落在首行首格（多行时 rowspan 自然落到组底）。
 */
:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell:first-child) {
  border-left: 2px solid var(--dispense-rx-group-outer-border);
  border-top-left-radius: 6px;
  border-bottom-left-radius: 6px;
}

/* 合并元格列底边与组底对齐（续行不再出现这三列的 td） */
:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell:nth-child(1)),
:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell:nth-child(2)),
:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell:nth-child(3)) {
  border-bottom: 2px solid var(--dispense-rx-group-outer-border);
}

/* 项目名称与合并块之间仍用表内默认色，避免与外框同色 */
:deep(.dispense-rx-grouped-table td.dispense-rx-item-name-cell) {
  border-left: 1px solid hsl(var(--border));
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body > td.el-table__cell:last-child) {
  border-right: 2px solid var(--dispense-rx-group-outer-border);
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body--first > td.el-table__cell:last-child) {
  border-top-right-radius: 6px;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body--last > td.el-table__cell:last-child) {
  border-bottom-right-radius: 6px;
}

:deep(.dispense-rx-grouped-table tr.dispense-rx-body--selected > td.el-table__cell) {
  background-color: hsl(var(--accent) / 35%) !important;
}

:deep(.dispense-rx-grouped-table .el-table__header th.el-table__cell) {
  color: hsl(var(--accent-foreground));
  background-color: hsl(var(--accent));
}

:deep(.dispense-rx-grouped-table .el-table__header .cell) {
  white-space: nowrap;
}

:deep(.dispense-rx-toolbar .section-title) {
  margin-bottom: 0;
}

.dispense-rx-toolbar-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  min-width: 0;
  padding: 0;
}

.dispense-rx-toolbar-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.dispense-rx-toolbar-group--view {
  margin-left: auto;
}

.dispense-rx-toolbar-split {
  flex-shrink: 0;
  width: 1px;
  height: 22px;
  background: var(--el-border-color-lighter);
}

.dispense-rx-toolbar-spacer {
  flex: 1 1 8px;
  min-width: 8px;
}

/** 中西药切换、视图切换：选中用 plain 观感，避免抢主操作按钮 */
:deep(.dispense-rx-kind .el-radio-button__inner),
:deep(.dispense-rx-view-mode .el-radio-button__inner) {
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-blank);
  border-color: var(--el-border-color);
  box-shadow: none !important;
}

:deep(.dispense-rx-kind .el-radio-button.is-active .el-radio-button__inner),
:deep(.dispense-rx-view-mode .el-radio-button.is-active .el-radio-button__inner) {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  box-shadow: none !important;
}

:deep(.dispense-rx-kind .el-radio-button:not(.is-active) .el-radio-button__inner:hover),
:deep(.dispense-rx-view-mode .el-radio-button:not(.is-active) .el-radio-button__inner:hover) {
  color: var(--el-color-primary);
}

.dispense-rx-sheet-pane {
  background-color: transparent;
}

.dispense-rx-sheet-row {
  padding-right: 4px;
  padding-bottom: 4px;
  /* 发药单多时纵向滚动条默认偏浅，加深便于辨认 */
  scrollbar-width: thin;
  scrollbar-color: var(--el-text-color-secondary) var(--el-fill-color);
}

.dispense-rx-sheet-row::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.dispense-rx-sheet-row::-webkit-scrollbar-track {
  background: var(--el-fill-color);
  border-radius: 4px;
}

.dispense-rx-sheet-row::-webkit-scrollbar-thumb {
  background: var(--el-text-color-secondary);
  border-radius: 4px;
}

.dispense-rx-sheet-row::-webkit-scrollbar-thumb:hover {
  background: var(--el-text-color-regular);
}

.dispense-rx-sheet-card {
  max-height: 100%;
}

.dispense-rx-sheet-card :deep(.prescription-dialog-wrapper) {
  width: auto;
  height: 100%;
  max-height: 100%;
  min-height: 0;
}

.dispense-rx-sheet-card :deep(.prescription-container) {
  height: min(660px, 100%);
  max-height: 100%;
}
</style>
