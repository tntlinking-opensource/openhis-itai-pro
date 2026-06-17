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

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

import {
  ensureOutpatientPrescriptionPrintBindingLoaded,
  getOutpatientPrescriptionTemplateCode,
  OUTPATIENT_PRESCRIPTION_TEMPLATE_OPTIONS,
  setOutpatientPrescriptionTemplateCode,
} from '#/utils/report/outpatient-prescription-print-config';

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
    /** 处方单弹窗：就诊下全部处方块（由父级单独拉全量 slip 后传入） */
    prescriptionSheetBlocks?: DispensePrescriptionBlock[];
    /** 处方打印进行中（preview + Win 出纸） */
    prescriptionPrintLoading?: boolean;
    /** 中西药 tab 角标（处方块数） */
    rxKindBadgeCounts?: DispenseRxKindBadgeCounts;
    selectedPatient: DispenseQueuePatient | null;
  }>(),
  {
    prescriptionSheetBlocks: () => [],
    prescriptionPrintLoading: false,
    rxKindBadgeCounts: () => ({ western: 0, herbal: 0 }),
  },
);

const emit = defineEmits<{
  batchDispense: [];
  batchTraceScan: [];
  printPrescription: [];
  viewPrescription: [];
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
/** 与模板列数一致，供间隔行 colspan */
const RX_TABLE_TOTAL_COLS = 13;

const rxTableRef = ref<null | TableInstance>(null);

/** 发放库房：双击后进入下拉编辑；下拉曾展开再收起则退出编辑 */
const inventoryPickEditLineId = ref<null | string>(null);
const inventoryPickDidOpen = ref(false);
const inventoryPickSelectRef = ref<null | { focus?: () => void }>(null);

/** 纸质处方单在弹窗中展示 */
const prescriptionSheetDialogVisible = ref(false);

const prescriptionTemplateCode = ref(getOutpatientPrescriptionTemplateCode());

onMounted(() => {
  void (async () => {
    await ensureOutpatientPrescriptionPrintBindingLoaded();
    prescriptionTemplateCode.value = getOutpatientPrescriptionTemplateCode();
  })();
});

async function handlePrescriptionTemplateChange(code: string) {
  await setOutpatientPrescriptionTemplateCode(code);
}

watch(
  () => props.selectedPatient?.id,
  () => {
    inventoryPickEditLineId.value = null;
    inventoryPickDidOpen.value = false;
    prescriptionSheetDialogVisible.value = false;
  },
);

watch(activeRxKind, () => {
  inventoryPickEditLineId.value = null;
  inventoryPickDidOpen.value = false;
  prescriptionSheetDialogVisible.value = false;
});

/** 处方组首行且可批量发药：待发 / 部分可发（已发、拦截不默认勾选） */
function isDefaultSelectableRxGroupHead(
  row: DispenseRxTableRow,
): row is DispensePrescriptionItemRow {
  if (isSpacerRow(row) || !row.isFirstInRx) return false;
  return row.status === 'pending' || row.status === 'partial';
}

/** 选中患者且明细加载后：默认勾选所有待发/部分可发处方组（每组仅首行可选） */
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
    table.clearSelection();
    if (disabled || !fp) return;
    for (const row of props.displayTableRows) {
      if (!isDefaultSelectableRxGroupHead(row)) continue;
      table.toggleRowSelection(row, true);
    }
  },
  { flush: 'post' },
);

/** 处方单弹窗内多张处方单数据（每张处方号一块） */
const prescriptionSheetViewItems = computed(() => {
  const patient = props.selectedPatient;
  const blocks = props.prescriptionSheetBlocks;
  if (!patient || blocks.length === 0) return [];
  const dispensingLabel = props.dispenserOptions.find(
    (o) => o.value === selectedDispenserId.value,
  )?.label;
  return blocks.map((block) => ({
    key: block.id,
    data: buildPrescriptionSheetData(patient, block, { dispensingName: dispensingLabel }),
  }));
});

/** 无患者或无处方时用空表数据，表头与表格框架仍展示 */
const rxTableBodyRows = computed<DispenseRxTableRow[]>(() => {
  if (!props.selectedPatient || props.prescriptionRowCount === 0) return [];
  return props.displayTableRows;
});

/** 空表时区分未选患者与已选但无处方 */
const rxTableEmptyText = computed(() => {
  if (!props.selectedPatient) return '请从左侧选择患者';
  return '当前类型下无处方，请切换上方西药/中药';
});

/** 仅中西药均有处方时展示 tab 角标，提示另一侧待发 */
const showRxKindBadges = computed(
  () => props.rxKindBadgeCounts.western > 0 && props.rxKindBadgeCounts.herbal > 0,
);

function formatRxKindTabLabel(base: string, count: number): string {
  if (!showRxKindBadges.value) return base;
  return `${base} (${count})`;
}

/** 父级在切换患者/中西药后清空表格勾选 */
async function clearRxTableSelection() {
  await nextTick();
  rxTableRef.value?.clearSelection();
}

/** 工具栏「处方查看」：打开纸质处方单弹窗（数据由父级先写入 prescriptionSheetBlocks） */
async function openSheetViewMode() {
  // 父级刚写入 sheetViewBlocks 时 props 可能尚未下发到子组件，需等一次 DOM 更新后再判断
  await nextTick();
  if (!props.selectedPatient || props.prescriptionSheetBlocks.length === 0) return;
  prescriptionSheetDialogVisible.value = true;
}

/** 间隔行判定，用于 span 与行 class */
function isSpacerRow(row: DispenseRxTableRow): row is DispenseRxTableSpacerRow {
  return 'rowKind' in row && row.rowKind === 'spacer';
}

/** 取表格勾选处方下的全部发放行主键（跳过间隔行） */
function getSelectedDispenseLineIds(): string[] {
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
    if (rxIds.has(row.rxId)) ids.push(row.id);
  }
  return ids;
}

defineExpose({
  clearRxTableSelection,
  getSelectedDispenseLineIds,
  openSheetViewMode,
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
    if (columnIndex === 0) return [1, RX_TABLE_TOTAL_COLS];
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
  if (row.status === 'completed') return { text: '已发', type: 'info' as const };
  return { text: '待发', type: 'success' as const };
}

/** 更新单行库房选择，避免直接改对象引用导致表格不刷新 */
function setInventoryPick(lineId: string, value: string) {
  inventoryPickByLineId.value = { ...inventoryPickByLineId.value, [lineId]: value };
}

/** 非编辑态：有选中库存项则显示选项文案，否则显示后端库房名 */
function inventoryLineDisplayLabel(row: DispenseRxTableRow): string {
  if (isSpacerRow(row)) return '';
  const pick = inventoryPickByLineId.value[row.id];
  if (!pick || !row.inventoryPickOptions?.length) return row.pharmacy;
  const hit = row.inventoryPickOptions.find((o) => o.value === pick);
  return hit?.label ?? row.pharmacy;
}

function openInventoryPickEditor(row: DispenseRxTableRow) {
  if (isSpacerRow(row) || !row.inventoryPickOptions?.length) return;
  inventoryPickDidOpen.value = false;
  inventoryPickEditLineId.value = row.id;
  void nextTick(() => {
    inventoryPickSelectRef.value?.focus?.();
  });
}

function onInventoryPickVisibleChange(lineId: string, visible: boolean) {
  if (inventoryPickEditLineId.value !== lineId) return;
  if (visible) {
    inventoryPickDidOpen.value = true;
    return;
  }
  if (inventoryPickDidOpen.value) {
    inventoryPickEditLineId.value = null;
    inventoryPickDidOpen.value = false;
  }
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
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-3 pb-2 pt-2">
      <div
        class="dispense-rx-toolbar flex flex-col gap-2 border-b border-[var(--el-border-color-lighter)] pb-2"
      >
        <SectionTitle title="处方列表" class="!mb-0 shrink-0" />
        <div class="mb-2 flex w-full min-w-0 flex-wrap items-center gap-x-2 gap-y-2">
          <div class="flex flex-wrap items-center gap-1">
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
            <ElButton plain :disabled="!props.selectedPatient" @click="emit('viewPrescription')">
              处方查看
            </ElButton>
            <ElSelect
              v-model="prescriptionTemplateCode"
              class="dispense-rx-template-select !w-36 shrink-0"
              size="default"
              :disabled="!props.selectedPatient || props.prescriptionPrintLoading"
              @change="handlePrescriptionTemplateChange"
            >
              <ElOption
                v-for="opt in OUTPATIENT_PRESCRIPTION_TEMPLATE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
            <ElButton
              plain
              :disabled="!props.selectedPatient"
              :loading="props.prescriptionPrintLoading"
              @click="emit('printPrescription')"
            >
              处方打印
            </ElButton>
          </div>
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
              class="dispense-pharmacist-select !w-44 shrink-0"
            >
              <ElOption
                v-for="opt in props.dispenserOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </div>
        </div>
      </div>
      <div class="dispense-rx-body-wrap flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <ElTable
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
            label="处方号"
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
            prop="quantity"
            label="数量"
            width="100"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="spec"
            label="规格"
            width="130"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="batchNo"
            label="批次号"
            width="150"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="amount"
            label="金额"
            width="100"
            align="center"
            header-align="center"
          />
          <ElTableColumn label="发放库房" width="200" align="center" header-align="center">
            <template #default="{ row }">
              <template v-if="!isSpacerRow(row)">
                <div
                  v-if="row.inventoryPickOptions?.length"
                  class="flex min-h-8 min-w-[140px] items-center justify-center"
                  @click.stop
                  @dblclick.stop
                >
                  <ElSelect
                    v-if="inventoryPickEditLineId === row.id"
                    ref="inventoryPickSelectRef"
                    :model-value="inventoryPickByLineId[row.id]"
                    filterable
                    placeholder="选择库房"
                    class="dispense-inv-pick-select !w-full"
                    @update:model-value="(v: string) => setInventoryPick(row.id, v)"
                    @visible-change="(v: boolean) => onInventoryPickVisibleChange(row.id, v)"
                  >
                    <ElOption
                      v-for="opt in row.inventoryPickOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                  <span
                    v-else
                    class="inline-block max-w-full cursor-default truncate text-[var(--el-text-color-regular)]"
                    title="双击选择库房/批号"
                    @dblclick.stop="openInventoryPickEditor(row)"
                  >
                    {{ inventoryLineDisplayLabel(row) }}
                  </span>
                </div>
                <span v-else class="text-[var(--el-text-color-regular)]">{{ row.pharmacy }}</span>
              </template>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="traceCode"
            label="追溯码"
            align="center"
            header-align="center"
            show-overflow-tooltip
          />
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
          v-else-if="!props.selectedPatient"
          class="dispense-rx-empty flex flex-1 flex-col justify-center"
          description="请从左侧选择患者"
          :image-size="72"
        />
        <ElEmpty
          v-else
          class="dispense-rx-empty flex flex-1 flex-col justify-center"
          description="当前类型下无处方，请切换上方西药/中药"
          :image-size="56"
        />
      </div>

      <ElDialog
        v-model="prescriptionSheetDialogVisible"
        title="处方单查看"
        width="min(920px, 94vw)"
        append-to-body
        align-center
        class="dispense-rx-sheet-dialog"
      >
        <div class="dispense-rx-sheet-scroll max-h-[min(720px,78vh)] min-h-0 overflow-auto pb-1">
          <template v-if="prescriptionSheetViewItems.length > 0">
            <div
              v-for="item in prescriptionSheetViewItems"
              :key="item.key"
              class="dispense-rx-sheet-stack-item mb-6 last:mb-0"
            >
              <PrescriptionSheetView :data="item.data" comfortable />
            </div>
          </template>
          <ElEmpty v-else description="暂无处方单数据" :image-size="56" />
        </div>
      </ElDialog>
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

/* 处方单弹窗：垂直居中、正文区无底色（灰色仅在处方单组件内部） */
:deep(.dispense-rx-sheet-dialog.el-dialog) {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}

:deep(.dispense-rx-sheet-dialog .el-dialog__body) {
  padding-top: 8px;
  background-color: transparent;
}

.dispense-rx-sheet-scroll {
  background-color: transparent;
}
</style>
