<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { NurseStationStickyPatientInfo } from '../../components/build-nurse-station-sticky-patient-info';
import type {
  InfusionTherapyFilter,
  OrderEntrustFilter,
  TempOrderSurgeryAnesthFilter,
} from '../../print-center/doc-registry';
import type { PrintPendingRow } from '../../print-center/print-center.types';

import { computed, nextTick, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import { buildNurseStationStickyPatientInfoFromModulePatient } from '../../components/build-nurse-station-sticky-patient-info';
import {
  filterInfusionRowsByTherapy,
  filterOrderRowsByEntrust,
  filterTempOrderRowsBySurgeryAnesth,
  needsOrderEntrustFilter,
  needsTempOrderSurgeryAnesthFilter,
} from '../../print-center/doc-registry';
import { loadPrintPendingRows } from '../../print-center/load-print-pending';
import { PRINT_CENTER_HIDDEN_DOC_TYPES } from '#/views/inpatient/nurse-station/print-center/print-center-doc.config';
import { printInpatientPrintCenter } from '../../print-center/print-center-print';

/** 执行页可打单据（与工具栏原下拉一致） */
export type OrderExecutePrintDocType =
  | 'exam_apply'
  | 'infusion_label'
  | 'lab_apply'
  | 'long_order'
  | 'oral_med_label'
  | 'oral_med_sheet'
  | 'order_dispense_sheet'
  | 'patrol_card'
  | 'specimen_barcode'
  | 'temp_order';

defineOptions({
  name: 'OrderExecutePrintDrawer',
});

interface Props {
  /** 左侧已勾选患者 */
  patients?: NurseStationModulePatient[];
  /** 执行截止时间（贴签/巡视按截止日所在自然日） */
  executionDeadline?: string;
}

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  executionDeadline: '',
});

const visible = defineModel<boolean>('visible', { default: false });

/** 单据选项（面板内选，不占工具栏） */
const DOC_TYPE_OPTIONS: Array<{
  description: string;
  label: string;
  singlePatient: boolean;
  value: OrderExecutePrintDocType;
}> = [
  {
    value: 'infusion_label',
    label: '输液贴',
    singlePatient: false,
    description: '按日查询已校对输液医嘱，同组合并打贴',
  },
  {
    value: 'patrol_card',
    label: '输液巡视卡',
    singlePatient: false,
    description: '按患者、按日打印；默认当天，含待执行/已执行输液药品',
  },
  {
    value: 'oral_med_label',
    label: '口服药贴',
    singlePatient: false,
    description: '按查询日列出已校对口服医嘱，逐条打贴',
  },
  {
    value: 'oral_med_sheet',
    label: '患者用药单',
    singlePatient: false,
    description:
      '按日列出当日已执行全部药品，勾选后一张连续打印多人（患者信息→药品表含剂量/用法/频次/备注→患者签字→下一患者）；院点一盒一点时口服长嘱总量按包装单位',
  },
  {
    value: 'order_dispense_sheet',
    label: '摆药单',
    singlePatient: false,
    description:
      '按日列出当日已执行全部药品（含口服）；勾选后一张连续打印多人（患者信息在表外→药品表→下一患者），无患者签字',
  },
  {
    value: 'exam_apply',
    label: '检查单',
    singlePatient: false,
    description: '按患者合并打印检查申请单',
  },
  {
    value: 'lab_apply',
    label: '检验单',
    singlePatient: false,
    description: '按患者合并打印检验申请单',
  },
  {
    value: 'specimen_barcode',
    label: '采血贴',
    singlePatient: false,
    description: '按检验医嘱开立数量打对应贴数（如 2次→2 张）',
  },
  {
    value: 'long_order',
    label: '长期医嘱',
    singlePatient: true,
    description: '打印已校对长期医嘱；按开嘱日区间筛选；可筛嘱托',
  },
  {
    value: 'temp_order',
    label: '临时医嘱',
    singlePatient: true,
    description: '打印已校对临时医嘱；可筛手术 / 麻醉 / 嘱托（互斥）',
  },
];

const hiddenPrintDocTypeSet = new Set<string>(PRINT_CENTER_HIDDEN_DOC_TYPES);

const visibleDocTypeOptions = computed(() =>
  DOC_TYPE_OPTIONS.filter((opt) => !hiddenPrintDocTypeSet.has(opt.value)),
);

const activeDocType = ref<OrderExecutePrintDocType>('infusion_label');
/** 长/临医嘱查询区间（默认入院日 ~ 今天；按开嘱日） */
const orderExecDateRange = ref<[string, string]>([
  dayjs().format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
]);
/** 输液贴 / 巡视卡 / 口服药贴：单日查询，每次打开或切到该类型默认当天 */
const dayQueryDate = ref(dayjs().format('YYYY-MM-DD'));
/** 患者用药单筛选：all 全部 / infusion 输液药 / non_infusion 非输液药 */
const oralMedInfusionFilter = ref<'all' | 'infusion' | 'non_infusion'>('all');
/** 临时医嘱筛选：all 全部 / surgery 手术 / anesthesia 麻醉 / entrust 嘱托（互斥） */
const tempOrderSurgeryAnesthFilter = ref<TempOrderSurgeryAnesthFilter>('all');
/** 长期/临时医嘱筛选：all 全部 / entrust 嘱托 */
const orderEntrustFilter = ref<OrderEntrustFilter>('all');
/** 输液巡视卡长临筛选：all 全部 / long 长期 / temp 临时 */
const infusionTherapyFilter = ref<InfusionTherapyFilter>('all');
const pendingRows = ref<PrintPendingRow[]>([]);
const selectedRowIds = ref<string[]>([]);
const loadingPending = ref(false);
const printing = ref(false);
const pendingTableRef = ref<{
  clearSelection: () => void;
  toggleRowSelection: (row: PrintPendingRow, selected?: boolean) => void;
} | null>(null);

/** 执行区间默认：勾选患者最早入院日 → 今天（无入院日则今天） */
function resolveOrderExecDateRangeFromAdmission(
  patients: NurseStationModulePatient[],
): [string, string] {
  const today = dayjs().format('YYYY-MM-DD');
  let fromDay = today;
  for (const patient of patients) {
    const raw = String(patient.admissionAt ?? '').trim();
    if (!raw) {
      continue;
    }
    const parsed = dayjs(raw);
    if (!parsed.isValid()) {
      continue;
    }
    const day = parsed.format('YYYY-MM-DD');
    if (day < fromDay) {
      fromDay = day;
    }
  }
  return [fromDay, today];
}

/** 按当前患者重写执行区间默认值 */
function resetOrderExecDateRangeFromAdmission() {
  orderExecDateRange.value = resolveOrderExecDateRangeFromAdmission(props.patients);
}

/** 当前单据配置 */
const activeDocOption = computed(() =>
  DOC_TYPE_OPTIONS.find((item) => item.value === activeDocType.value),
);

/** 是否为长/临执行记录单据 */
const isOrderExecDoc = computed(
  () => activeDocType.value === 'long_order' || activeDocType.value === 'temp_order',
);

/** 是否为单日查询单据（输液贴 / 巡视卡 / 口服药贴 / 摆药单 / 患者用药单） */
const isDayQueryDoc = computed(
  () =>
    activeDocType.value === 'infusion_label' ||
    activeDocType.value === 'patrol_card' ||
    activeDocType.value === 'oral_med_label' ||
    activeDocType.value === 'order_dispense_sheet' ||
    activeDocType.value === 'oral_med_sheet',
);

/** 摆药单 / 患者用药单：列表列与患者列宽同构 */
const isMedSheetDoc = computed(
  () => activeDocType.value === 'order_dispense_sheet' || activeDocType.value === 'oral_med_sheet',
);

/** 是否患者用药单 */
const isOralMedSheetDoc = computed(() => activeDocType.value === 'oral_med_sheet');

/** 当前单据是否展示临时医嘱的手术/麻醉筛选 */
const showTempOrderSurgeryAnesthFilter = computed(() =>
  needsTempOrderSurgeryAnesthFilter(activeDocType.value),
);

/** 当前单据是否展示长/临嘱托筛选 */
const showOrderEntrustFilter = computed(() => needsOrderEntrustFilter(activeDocType.value));

/** 输液巡视卡：全部 / 长期 / 临时（输液贴沿用打印中心，本抽屉不重复展开） */
const showPatrolTherapyFilter = computed(() => activeDocType.value === 'patrol_card');

/** 列表展示行（患者用药单按输液筛选；临时医嘱按手术/麻醉筛选；长/临按嘱托筛选） */
const displayRows = computed(() => {
  let rows = pendingRows.value;
  if (isOralMedSheetDoc.value) {
    const filter = oralMedInfusionFilter.value;
    // 输液药
    if (filter === 'infusion') {
      rows = pendingRows.value.filter((row) => row.infusionFlag === true);
    } else if (filter === 'non_infusion') {
      // 非输液药（输液判定取反）
      rows = pendingRows.value.filter((row) => row.infusionFlag !== true);
    }
  }
  if (showTempOrderSurgeryAnesthFilter.value) {
    rows = filterTempOrderRowsBySurgeryAnesth(rows, tempOrderSurgeryAnesthFilter.value);
  }
  if (showOrderEntrustFilter.value) {
    rows = filterOrderRowsByEntrust(rows, orderEntrustFilter.value);
  }
  if (showPatrolTherapyFilter.value) {
    rows = filterInfusionRowsByTherapy(rows, infusionTherapyFilter.value);
  }
  return rows;
});

/** 单日查询日：默认当天，可切换 */
const queryDate = computed(() => {
  if (isDayQueryDoc.value) {
    return dayQueryDate.value || dayjs().format('YYYY-MM-DD');
  }
  const deadline = props.executionDeadline || dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
  return dayjs(deadline).format('YYYY-MM-DD');
});

/** 实际查询起止日 */
const effectiveDateFrom = computed(() => {
  if (isOrderExecDoc.value) {
    return orderExecDateRange.value[0] || dayjs().format('YYYY-MM-DD');
  }
  return queryDate.value;
});

/** 实际查询截止日 */
const effectiveDateTo = computed(() => {
  if (isOrderExecDoc.value) {
    return orderExecDateRange.value[1] || dayjs().format('YYYY-MM-DD');
  }
  return queryDate.value;
});

/** 列表/打印用截止时间 */
const effectiveDeadline = computed(() => {
  if (isOrderExecDoc.value) {
    return dayjs(effectiveDateTo.value).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  }
  if (isDayQueryDoc.value) {
    return dayjs(queryDate.value).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  }
  return props.executionDeadline || dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
});

/** 患者范围是否满足当前单据 */
const patientScopeOk = computed(() => {
  const count = props.patients.length;
  if (count < 1) {
    return false;
  }
  if (activeDocOption.value?.singlePatient) {
    return count === 1;
  }
  return true;
});

/** 范围提示文案 */
const scopeHint = computed(() => {
  const patientCount = props.patients.length;
  if (patientCount < 1) {
    return '请先在左侧勾选患者';
  }
  if (activeDocOption.value?.singlePatient && patientCount !== 1) {
    return `打印「${activeDocOption.value.label}」请只勾选 1 位患者（当前 ${patientCount} 人）`;
  }
  return `基于左侧已勾选 ${patientCount} 位患者`;
});

/** 抬头日期胶囊文案 */
const dateMetaText = computed(() => {
  if (isOrderExecDoc.value) {
    return `执行区间 ${effectiveDateFrom.value} ~ ${effectiveDateTo.value}`;
  }
  return `查询日 ${queryDate.value}`;
});

/** 单日查询重置为当天 */
function resetDayQueryDateToToday() {
  dayQueryDate.value = dayjs().format('YYYY-MM-DD');
}

/** 单人时完整患者信息（对齐护士站顶部信息条字段） */
const singlePatientInfo = computed<NurseStationStickyPatientInfo | null>(() => {
  if (props.patients.length !== 1 || !props.patients[0]) {
    return null;
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(props.patients[0]);
});

/** 单人抬头：床号 + 姓名 */
const singlePatientIdentity = computed(() => {
  const info = singlePatientInfo.value;
  if (!info) {
    return '';
  }
  return [info.bedNo, info.name].filter((part) => String(part ?? '').trim()).join(' ');
});

/** 多人时简要名单（仅床号+姓名） */
const multiPatientSummaries = computed(() => {
  return props.patients.map((patient) => {
    const info = buildNurseStationStickyPatientInfoFromModulePatient(patient);
    const identity = [info.bedNo, info.name].filter((part) => String(part ?? '').trim()).join(' ');
    return {
      id: patient.id,
      identity: identity || patient.name || '—',
      admitAt: String(info.admitAt ?? '').trim(),
    };
  });
});

/** 待打行患者展示：仅床号+姓名 */
function resolvePendingPatientDisplay(row: PrintPendingRow): string {
  const encounterId = String(row.encounterId ?? '').trim();
  const matched = props.patients.find(
    (item) =>
      String(item.id).trim() === encounterId ||
      (row.patientId && String(item.patientId ?? '').trim() === String(row.patientId).trim()),
  );
  if (matched) {
    const info = buildNurseStationStickyPatientInfoFromModulePatient(matched);
    return [info.bedNo, info.name].filter((part) => String(part ?? '').trim()).join(' ');
  }
  return [row.bedNo, row.patientName].filter((part) => String(part ?? '').trim()).join(' ');
}

/** 已勾选待打行 */
const selectedRows = computed(() => {
  const idSet = new Set(selectedRowIds.value);
  return displayRows.value.filter((row) => idSet.has(row.id));
});

/** 空态文案 */
const emptyPendingDescription = computed(() => {
  if (isOralMedSheetDoc.value) {
    if (oralMedInfusionFilter.value === 'infusion') {
      return `暂无「患者用药单」可打印数据（无已执行输液药品）`;
    }
    if (oralMedInfusionFilter.value === 'non_infusion') {
      return `暂无「患者用药单」可打印数据（无已执行非输液药品）`;
    }
  }
  if (showTempOrderSurgeryAnesthFilter.value && tempOrderSurgeryAnesthFilter.value === 'surgery') {
    return '暂无「临时医嘱」可打印的手术';
  }
  if (
    showTempOrderSurgeryAnesthFilter.value &&
    tempOrderSurgeryAnesthFilter.value === 'anesthesia'
  ) {
    return '暂无「临时医嘱」可打印的麻醉';
  }
  if (showTempOrderSurgeryAnesthFilter.value && tempOrderSurgeryAnesthFilter.value === 'entrust') {
    return '暂无「临时医嘱」可打印的嘱托医嘱';
  }
  if (showOrderEntrustFilter.value && orderEntrustFilter.value === 'entrust') {
    return `暂无「${resolveDocLabel(activeDocType.value)}」可打印的嘱托医嘱`;
  }
  if (showPatrolTherapyFilter.value && infusionTherapyFilter.value === 'long') {
    return '暂无「输液巡视卡」可打印的长期输液';
  }
  if (showPatrolTherapyFilter.value && infusionTherapyFilter.value === 'temp') {
    return '暂无「输液巡视卡」可打印的临时输液';
  }
  return `暂无「${resolveDocLabel(activeDocType.value)}」可打印数据`;
});

/** 是否可确认打印 */
const canConfirmPrint = computed(
  () =>
    patientScopeOk.value &&
    !loadingPending.value &&
    !printing.value &&
    selectedRows.value.length > 0,
);

/** 打印单据文案 */
function resolveDocLabel(docType: OrderExecutePrintDocType): string {
  return DOC_TYPE_OPTIONS.find((item) => item.value === docType)?.label ?? '单据';
}

/** 按 id 同步表格勾选 */
async function syncTableSelection(ids: string[]) {
  selectedRowIds.value = ids;
  await nextTick();
  const table = pendingTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  // 默认勾选传入的待打行
  for (const row of displayRows.value) {
    if (ids.includes(row.id)) {
      table.toggleRowSelection(row, true);
    }
  }
}

/** 默认全选当前可见清单 */
function selectAllPendingRows() {
  void syncTableSelection(displayRows.value.map((row) => row.id));
}

/** 拉取当前单据待打清单并默认全选 */
async function refreshPendingRows() {
  if (!patientScopeOk.value) {
    pendingRows.value = [];
    selectedRowIds.value = [];
    return;
  }
  loadingPending.value = true;
  try {
    const rows = await loadPrintPendingRows({
      docType: activeDocType.value,
      patients: props.patients,
      dateFrom: effectiveDateFrom.value,
      dateTo: effectiveDateTo.value,
      executionDeadline: effectiveDeadline.value,
      tempOrderSurgeryAnesthFilter: tempOrderSurgeryAnesthFilter.value,
    });
    pendingRows.value = rows;
    await nextTick();
    selectAllPendingRows();
  } finally {
    loadingPending.value = false;
  }
}

/** 切换单据类型并重载清单 */
function handleDocTypeChange() {
  // 切到长/临执行记录时，区间回到入院日~今天
  if (isOrderExecDoc.value) {
    resetOrderExecDateRangeFromAdmission();
  }
  // 切到单日单据时，默认当天
  if (isDayQueryDoc.value) {
    resetDayQueryDateToToday();
  }
  // 切离患者用药单时复位筛选
  if (!isOralMedSheetDoc.value) {
    oralMedInfusionFilter.value = 'all';
  }
  // 切离临时医嘱时复位手术/麻醉筛选
  if (!showTempOrderSurgeryAnesthFilter.value) {
    tempOrderSurgeryAnesthFilter.value = 'all';
  }
  // 切离长/临医嘱时复位嘱托筛选
  if (!showOrderEntrustFilter.value) {
    orderEntrustFilter.value = 'all';
  }
  if (!showPatrolTherapyFilter.value) {
    infusionTherapyFilter.value = 'all';
  }
  void refreshPendingRows();
}

/** 长/临执行区间变更后重载 */
function handleOrderExecDateChange() {
  if (!isOrderExecDoc.value) {
    return;
  }
  void refreshPendingRows();
}

/** 单日查询日变更后重载 */
function handleDayQueryDateChange() {
  if (!isDayQueryDoc.value) {
    return;
  }
  void refreshPendingRows();
}

/** 表格勾选变化 */
function handleSelectionChange(rows: PrintPendingRow[]) {
  selectedRowIds.value = rows.map((row) => row.id);
}

/** 清空勾选 */
function handleClearSelection() {
  void syncTableSelection([]);
}

/** 确认打印已勾选条目 */
async function handleConfirmPrint() {
  if (!canConfirmPrint.value) {
    if (!patientScopeOk.value) {
      ElMessage.warning(scopeHint.value);
      return;
    }
    if (selectedRows.value.length <= 0) {
      ElMessage.warning('请先勾选要打印的项目');
    }
    return;
  }
  if (printing.value) {
    return;
  }
  const docType = activeDocType.value;
  const label = resolveDocLabel(docType);
  printing.value = true;
  try {
    const result = await printInpatientPrintCenter({
      docType,
      patients: props.patients,
      selectedRows: selectedRows.value,
      dateFrom: effectiveDateFrom.value,
      dateTo: effectiveDateTo.value,
      executionDeadline: effectiveDeadline.value,
      tempOrderSurgeryAnesthFilter: tempOrderSurgeryAnesthFilter.value,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：${label}，共 ${result.count} 份`);
      visible.value = false;
    }
  } finally {
    printing.value = false;
  }
}

/** 关闭抽屉 */
function handleClose() {
  visible.value = false;
}

// 打开抽屉：执行区间按入院日重置，单日查询默认当天，再加载并默认全选；关闭时清空勾选态
watch(visible, (open) => {
  if (open) {
    resetOrderExecDateRangeFromAdmission();
    resetDayQueryDateToToday();
    oralMedInfusionFilter.value = 'all';
    tempOrderSurgeryAnesthFilter.value = 'all';
    orderEntrustFilter.value = 'all';
    infusionTherapyFilter.value = 'all';
    void refreshPendingRows();
    return;
  }
  pendingRows.value = [];
  selectedRowIds.value = [];
});

/** 用药单筛选变化时仅重勾选可见行 */
watch(oralMedInfusionFilter, () => {
  if (!isOralMedSheetDoc.value) {
    return;
  }
  selectAllPendingRows();
});

/** 长/临嘱托筛选变化时仅重勾选可见行 */
watch(orderEntrustFilter, () => {
  if (!showOrderEntrustFilter.value) {
    return;
  }
  selectAllPendingRows();
});

/** 巡视卡长临筛选变化时仅重勾选可见行 */
watch(infusionTherapyFilter, () => {
  if (!showPatrolTherapyFilter.value) {
    return;
  }
  selectAllPendingRows();
});

/** 临时医嘱手术/麻醉筛选变化：麻醉与正常取数口径不同，需重新拉列表 */
watch(tempOrderSurgeryAnesthFilter, () => {
  if (!showTempOrderSurgeryAnesthFilter.value) {
    return;
  }
  void refreshPendingRows();
});
</script>

<template>
  <ElDrawer
    v-model="visible"
    class="order-execute-print-drawer"
    size="820px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="true"
  >
    <template #header>
      <div class="print-drawer__head">
        <div class="print-drawer__head-main">
          <span class="print-drawer__title">打印</span>
          <ElTag size="small" effect="plain" type="primary" round>
            {{ activeDocOption?.label ?? '单据' }}
          </ElTag>
          <span class="print-drawer__meta-chip">{{ dateMetaText }}</span>
        </div>

        <div v-if="singlePatientInfo" class="print-drawer__patient">
          <div class="print-drawer__patient-identity">
            <span class="print-drawer__patient-name">{{ singlePatientIdentity }}</span>
            <span v-if="singlePatientInfo.admitAt" class="print-drawer__patient-admit">
              入院 {{ singlePatientInfo.admitAt }}
            </span>
          </div>
        </div>

        <div
          v-else-if="patients.length > 1"
          class="print-drawer__patient print-drawer__patient--multi"
        >
          <div class="print-drawer__patient-identity">
            <span class="print-drawer__patient-name">{{ scopeHint }}</span>
          </div>
          <div class="print-drawer__patient-multi-list">
            <div
              v-for="item in multiPatientSummaries"
              :key="item.id"
              class="print-drawer__patient-multi-item"
            >
              <span class="print-drawer__patient-multi-identity">{{ item.identity }}</span>
              <span v-if="item.admitAt" class="print-drawer__patient-multi-admit">
                入院 {{ item.admitAt }}
              </span>
            </div>
          </div>
        </div>

        <div v-else class="print-drawer__head-meta">
          <span class="print-drawer__meta-chip">{{ scopeHint }}</span>
        </div>
      </div>
    </template>

    <div class="print-drawer__body">
      <section class="print-drawer__card print-drawer__card--type">
        <div class="print-drawer__card-label">单据类型</div>
        <ElRadioGroup
          v-model="activeDocType"
          class="print-drawer__doc-segment"
          size="small"
          @change="handleDocTypeChange"
        >
          <ElRadioButton v-for="opt in visibleDocTypeOptions" :key="opt.value" :label="opt.value">
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <p class="print-drawer__desc">{{ activeDocOption?.description }}</p>

        <div v-if="isOrderExecDoc" class="print-drawer__range">
          <span class="print-drawer__range-label">执行区间</span>
          <ElDatePicker
            v-model="orderExecDateRange"
            type="daterange"
            size="default"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="print-drawer__range-picker"
            :clearable="false"
            @change="handleOrderExecDateChange"
          />
        </div>

        <div v-else-if="isDayQueryDoc" class="print-drawer__range">
          <span class="print-drawer__range-label">查询日</span>
          <ElDatePicker
            v-model="dayQueryDate"
            type="date"
            size="default"
            value-format="YYYY-MM-DD"
            placeholder="查询日期"
            class="print-drawer__day-picker"
            :clearable="false"
            @change="handleDayQueryDateChange"
          />
        </div>

        <div v-if="isOralMedSheetDoc" class="print-drawer__range">
          <ElRadioGroup v-model="oralMedInfusionFilter" size="small">
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="infusion">输液药</ElRadio>
            <ElRadio value="non_infusion">非输液药</ElRadio>
          </ElRadioGroup>
        </div>

        <div v-if="showTempOrderSurgeryAnesthFilter" class="print-drawer__range">
          <ElRadioGroup v-model="tempOrderSurgeryAnesthFilter" size="small">
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="surgery">手术</ElRadio>
            <ElRadio value="anesthesia">麻醉</ElRadio>
            <ElRadio value="entrust">嘱托</ElRadio>
          </ElRadioGroup>
        </div>

        <div v-if="showOrderEntrustFilter" class="print-drawer__range">
          <ElRadioGroup v-model="orderEntrustFilter" size="small">
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="entrust">嘱托</ElRadio>
          </ElRadioGroup>
        </div>

        <div v-if="showPatrolTherapyFilter" class="print-drawer__range">
          <ElRadioGroup v-model="infusionTherapyFilter" size="small">
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="long">长期</ElRadio>
            <ElRadio value="temp">临时</ElRadio>
          </ElRadioGroup>
        </div>
      </section>

      <section class="print-drawer__card print-drawer__card--list">
        <div class="print-drawer__list-head">
          <div class="print-drawer__list-title-wrap">
            <span class="print-drawer__list-title">
              {{ isOrderExecDoc ? '执行记录' : '待打清单' }}
            </span>
            <span class="print-drawer__list-count">
              共 {{ displayRows.length }} 条 · 已选 {{ selectedRowIds.length }}
            </span>
          </div>
          <div class="print-drawer__list-actions">
            <ElButton
              link
              type="primary"
              :disabled="displayRows.length < 1"
              @click="selectAllPendingRows"
            >
              全选
            </ElButton>
            <ElButton link :disabled="selectedRowIds.length < 1" @click="handleClearSelection">
              清空
            </ElButton>
            <ElButton
              link
              type="primary"
              :loading="loadingPending"
              :disabled="!patientScopeOk"
              @click="refreshPendingRows"
            >
              刷新
            </ElButton>
          </div>
        </div>

        <div v-if="loadingPending" class="print-drawer__state">
          <ElEmpty :image-size="72" description="正在加载可打印项目…" />
        </div>
        <div v-else-if="!patientScopeOk" class="print-drawer__state">
          <ElEmpty :image-size="72" :description="scopeHint" />
        </div>
        <div v-else-if="displayRows.length === 0" class="print-drawer__state">
          <ElEmpty :image-size="72" :description="emptyPendingDescription" />
        </div>
        <div v-else class="print-drawer__table-wrap">
          <ElTable
            ref="pendingTableRef"
            class="print-drawer__table"
            :data="displayRows"
            stripe
            size="default"
            row-key="id"
            :max-height="520"
            @selection-change="handleSelectionChange"
          >
            <ElTableColumn type="selection" width="52" align="center" header-align="center" />
            <ElTableColumn
              label="患者"
              :width="isMedSheetDoc ? 88 : undefined"
              :min-width="isMedSheetDoc ? undefined : 200"
              align="center"
              header-align="center"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ resolvePendingPatientDisplay(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="title"
              :label="isMedSheetDoc ? '药品' : '内容'"
              min-width="220"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
            <ElTableColumn
              v-if="showPatrolTherapyFilter"
              prop="therapyTypeText"
              label="长临"
              width="72"
              align="center"
              header-align="center"
            />
            <ElTableColumn
              v-if="isMedSheetDoc"
              prop="subtitle"
              label="规格"
              min-width="140"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
            <ElTableColumn
              v-if="isOrderExecDoc || isMedSheetDoc"
              prop="pageHint"
              :label="isMedSheetDoc ? '总量' : '执行次数'"
              width="100"
              align="center"
              header-align="center"
            />
            <ElTableColumn
              prop="timeText"
              :label="isOrderExecDoc || isMedSheetDoc ? '最近执行' : '时间'"
              width="130"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
          </ElTable>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="print-drawer__footer">
        <div class="print-drawer__footer-left">
          <span class="print-drawer__footer-label">已选</span>
          <strong class="print-drawer__footer-num">{{ selectedRowIds.length }}</strong>
          <span class="print-drawer__footer-label">份</span>
        </div>
        <div class="print-drawer__footer-actions">
          <ElButton @click="handleClose">取消</ElButton>
          <ElButton
            type="primary"
            :loading="printing"
            :disabled="!canConfirmPrint"
            @click="handleConfirmPrint"
          >
            确认打印
          </ElButton>
        </div>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped lang="scss">
.print-drawer__head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 28px;
}

.print-drawer__head-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.print-drawer__title {
  color: var(--el-text-color-primary);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.3;
}

.print-drawer__head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.print-drawer__meta-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
}

.print-drawer__patient {
  margin-top: 2px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
}

.print-drawer__patient-identity {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 0;
}

.print-drawer__patient-name {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.print-drawer__patient-admit {
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.print-drawer__patient--multi .print-drawer__patient-identity {
  margin-bottom: 6px;
}

.print-drawer__patient-multi-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 96px;
  overflow: auto;
}

.print-drawer__patient-multi-item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px 16px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--el-border-color-extra-light);
  font-size: 12px;
}

.print-drawer__patient-multi-item:last-child {
  border-bottom: none;
}

.print-drawer__patient-multi-identity {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.print-drawer__patient-multi-admit {
  color: var(--el-text-color-regular);
}

.print-drawer__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.print-drawer__card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.print-drawer__card--type {
  flex-shrink: 0;
  padding: 14px 16px 12px;
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-bg-color) 72%);
}

.print-drawer__card-label {
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.print-drawer__doc-segment {
  display: flex;
  flex-wrap: wrap;
  width: 100%;
}

.print-drawer__doc-segment :deep(.el-radio-button) {
  flex: 1 1 auto;
  min-width: 0;
}

.print-drawer__doc-segment :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 8px 10px;
  border-radius: 0 !important;
  font-weight: 500;
  box-shadow: none !important;
}

.print-drawer__doc-segment :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 8px 0 0 8px !important;
}

.print-drawer__doc-segment :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 8px 8px 0 !important;
}

.print-drawer__doc-segment :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: var(--el-color-white);
}

.print-drawer__desc {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.print-drawer__range {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.print-drawer__range-label {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.print-drawer__range-picker {
  width: 280px;
  max-width: 100%;
}

.print-drawer__day-picker {
  width: 180px;
  max-width: 100%;
}

.print-drawer__card--list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 12px 10px;
  overflow: hidden;
}

.print-drawer__list-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding: 0 4px;
}

.print-drawer__list-title-wrap {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 8px;
}

.print-drawer__list-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.print-drawer__list-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.print-drawer__list-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
}

.print-drawer__state {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  border-radius: 10px;
  background: var(--el-fill-color-blank);
}

.print-drawer__table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.print-drawer__table {
  width: 100%;
}

.print-drawer__table :deep(.el-table__header th) {
  height: 44px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 600;
  text-align: center !important;
}

.print-drawer__table :deep(.el-table__header .cell),
.print-drawer__table :deep(.el-table__body .cell) {
  justify-content: center;
  text-align: center !important;
}

.print-drawer__table :deep(.el-table__row td) {
  height: 48px;
  color: var(--el-text-color-primary);
}

.print-drawer__table :deep(.el-table__body tr:hover > td) {
  background: var(--el-color-primary-light-9) !important;
}

.print-drawer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.print-drawer__footer-left {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.print-drawer__footer-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.print-drawer__footer-num {
  color: var(--el-color-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.print-drawer__footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

<style lang="scss">
.order-execute-print-drawer.el-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 16px 20px 12px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }

  .el-drawer__body {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: 14px 16px;
    background: var(--el-fill-color-blank);
  }

  .el-drawer__footer {
    padding: 12px 16px 16px;
    border-top: 1px solid var(--el-border-color-extra-light);
    background: var(--el-bg-color);
  }
}
</style>
