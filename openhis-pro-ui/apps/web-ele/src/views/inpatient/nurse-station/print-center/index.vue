<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintDocTypeOption, PrintPendingRow } from './print-center.types';

import { computed, nextTick, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, type TableInstance } from 'element-plus';

import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { PRINT_CENTER_HIDDEN_DOC_TYPES } from '#/views/inpatient/nurse-station/print-center/print-center-doc.config';

import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import { loadPrintPendingRows } from './load-print-pending';
import { PRINT_CENTER_WIRED_DOC_TYPES, printInpatientPrintCenter } from './print-center-print';
import { findPrintDocType, PRINT_DOC_TYPES, resolvePrintScopeLabel } from './print-center.types';

import './print-center.css';

/** 院点可配置隐藏的单据 code（须 #/ 导入以走 override） */
const hiddenPrintDocTypeSet = new Set<PrintDocType>(PRINT_CENTER_HIDDEN_DOC_TYPES);

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationPrintCenterModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

/** 主类型：T1 复杂工作站（打印中心壳）；附加：T3 待打列表勾选 */

const activeDocType = ref<PrintDocType>('patrol_card');
/** 有时间条件的单据：单日查询，默认当天，配合上一天/下一天 */
const dayQueryDate = ref(dayjs().format('YYYY-MM-DD'));
const orderPrintMode = ref<'all' | 'unprinted' | 'continue'>('unprinted');
const continueFromRow = ref(1);
/** 患者用药单筛选：all 全部 / infusion 输液药 / non_infusion 非输液药 */
const oralMedInfusionFilter = ref<'all' | 'infusion' | 'non_infusion'>('all');
const copies = ref(1);
const selectedRowIds = ref<string[]>([]);
const pendingTableRef = ref<TableInstance>();
const printing = ref(false);
const loadingPending = ref(false);
const pendingRows = ref<PrintPendingRow[]>([]);
const docPendingCounts = ref<Partial<Record<PrintDocType, number>>>({});

/** 当前是否为长/临执行记录单据 */
function isOrderExecDocType(docType: PrintDocType): boolean {
  return docType === 'long_order' || docType === 'temp_order';
}

/**
 * 当前是否为带时间查询条件的单据（统一单日 + 左右切换）。
 * 无日期条件的单据（腕带/床头卡/口服药贴等）不展示日期控件。
 */
function isDayQueryDocType(docType: PrintDocType): boolean {
  return (
    docType === 'infusion_label' ||
    docType === 'patrol_card' ||
    docType === 'order_dispense_sheet' ||
    docType === 'oral_med_sheet' ||
    docType === 'long_order' ||
    docType === 'temp_order' ||
    docType === 'temp_sheet' ||
    docType === 'nursing_record' ||
    docType === 'fee_list' ||
    docType === 'fee_detail' ||
    docType === 'dispense_summary' ||
    docType === 'dispense_sheet' ||
    docType === 'exam_apply' ||
    docType === 'lab_apply' ||
    docType === 'specimen_barcode'
  );
}

/** 医嘱/巡视等截止时间：有日期条件锚定查询日末，否则今天末 */
const executionDeadline = computed(() => {
  if (isDayQueryDocType(activeDocType.value)) {
    return `${dayQueryDate.value} 23:59:59`;
  }
  return `${dayjs().format('YYYY-MM-DD')} 23:59:59`;
});

/** 当前单据是否已对接真实打印模板 */
const isDocWired = computed(() => PRINT_CENTER_WIRED_DOC_TYPES.includes(activeDocType.value));

/** 单据 Tab 列表（角标用实时待打数量；过滤院点隐藏项） */
const docTypeOptions = computed(() =>
  PRINT_DOC_TYPES.filter((doc) => !hiddenPrintDocTypeSet.has(doc.code)).map((doc) => ({
    ...doc,
    pendingCount: docPendingCounts.value[doc.code],
  })),
);

/** 当前单据配置 */
const activeDoc = computed<PrintDocTypeOption | undefined>(() =>
  findPrintDocType(activeDocType.value),
);

/** 面板标题：当前单据名 */
const panelTitle = computed(() => activeDoc.value?.label ?? '待打清单');

/** 患者选择是否满足当前单据范围 */
const patientScopeOk = computed(() => {
  const scope = activeDoc.value?.scope;
  const count = props.patients.length;
  if (!scope) {
    return false;
  }
  if (scope === 'ward') {
    return true;
  }
  if (scope === 'single') {
    return count === 1;
  }
  return count >= 1;
});

/** 顶部患者信息条 */
const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请选择患者',
      diagnosis: '左侧勾选患者后可打印',
    });
  }
  if (activeDoc.value?.scope === 'single' && props.patients.length !== 1) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请选择单一患者',
      diagnosis: `当前已选 ${props.patients.length} 人，本单据仅支持单选`,
    });
  }
  if (activeDoc.value?.scope === 'multiple' && props.patients.length === 0) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请勾选患者',
      diagnosis: '本单据支持多选批量打印',
    });
  }
  if (props.patients.length === 1 && props.patients[0]) {
    return buildNurseStationStickyPatientInfoFromModulePatient(props.patients[0]);
  }
  return buildNurseStationStickyPatientInfoPlaceholder({
    name: `已选 ${props.patients.length} 位患者`,
    diagnosis: props.patients
      .slice(0, 4)
      .map((p) => `${p.bedNo || '—'} ${p.name}`)
      .join('、')
      .concat(props.patients.length > 4 ? '…' : ''),
  });
});

/** 列表展示行（医嘱续打 / 未打 / 患者用药单输液筛选） */
const displayRows = computed(() => {
  if (
    (activeDocType.value === 'long_order' || activeDocType.value === 'temp_order') &&
    orderPrintMode.value === 'continue'
  ) {
    const from = Math.max(1, continueFromRow.value) - 1;
    return pendingRows.value.slice(from);
  }
  if (
    (activeDocType.value === 'long_order' || activeDocType.value === 'temp_order') &&
    orderPrintMode.value === 'unprinted'
  ) {
    // 未打：尚无独立「已打印」字段；医嘱待打行 statusCode=executable（已执行待打纸）
    return pendingRows.value.filter(
      (row) =>
        row.statusCode === 'pending' ||
        row.statusCode === 'executable' ||
        row.statusCode === 'ready',
    );
  }
  if (activeDocType.value === 'oral_med_sheet') {
    const filter = oralMedInfusionFilter.value;
    // 输液药
    if (filter === 'infusion') {
      return pendingRows.value.filter((row) => row.infusionFlag === true);
    }
    // 非输液药（输液判定取反）
    if (filter === 'non_infusion') {
      return pendingRows.value.filter((row) => row.infusionFlag !== true);
    }
  }
  return pendingRows.value;
});

/** 已勾选行 */
const selectedRows = computed(() =>
  displayRows.value.filter((row) => selectedRowIds.value.includes(row.id)),
);

/** 预估页数 */
const estimatedPages = computed(() => {
  const n = selectedRows.value.length || displayRows.value.length;
  if (activeDoc.value?.previewKind === 'label') {
    return Math.max(1, Math.ceil(n / 8));
  }
  if (activeDoc.value?.previewKind === 'paper') {
    return Math.max(1, n);
  }
  return Math.max(1, Math.ceil(n / 20));
});

/** 是否可打印 */
const canPrint = computed(
  () =>
    isDocWired.value &&
    patientScopeOk.value &&
    (selectedRows.value.length > 0 || displayRows.value.length > 0),
);

/** 空态文案 */
const emptyDescription = computed(() => {
  if (!patientScopeOk.value) {
    const scope = activeDoc.value?.scope;
    if (scope === 'single') {
      return '请在左侧只勾选 1 位患者';
    }
    if (scope === 'multiple') {
      return '请在左侧至少勾选 1 位患者';
    }
  }
  if (loadingPending.value) {
    return '正在加载待打数据…';
  }
  if (activeDocType.value === 'temp_sheet') {
    return '体温单后续纯前端打印，暂不在此处理';
  }
  if (activeDocType.value === 'patrol_card') {
    return `暂无待打数据：${dayQueryDate.value} 没有可打印的输液药品（含待执行/已执行）`;
  }
  if (activeDocType.value === 'infusion_label') {
    return `暂无待打数据：${dayQueryDate.value} 没有已校对的输液药品医嘱（含已执行/未执行）`;
  }
  if (activeDocType.value === 'order_dispense_sheet') {
    return `暂无待打数据：${dayQueryDate.value} 没有已执行的药品医嘱`;
  }
  if (activeDocType.value === 'oral_med_sheet') {
    if (oralMedInfusionFilter.value === 'infusion') {
      return `暂无待打数据：${dayQueryDate.value} 没有已执行的输液药品`;
    }
    if (oralMedInfusionFilter.value === 'non_infusion') {
      return `暂无待打数据：${dayQueryDate.value} 没有已执行的非输液药品`;
    }
    return `暂无待打数据：${dayQueryDate.value} 没有已执行的药品医嘱`;
  }
  if (activeDocType.value === 'exam_apply') {
    return `暂无待打数据：${dayQueryDate.value} 没有开立的检查申请`;
  }
  if (activeDocType.value === 'lab_apply') {
    return `暂无待打数据：${dayQueryDate.value} 没有开立的检验申请`;
  }
  if (activeDocType.value === 'specimen_barcode') {
    return `暂无待打数据：${dayQueryDate.value} 没有可打印的采血贴（检验申请）`;
  }
  return '暂无待打数据';
});

/** 按 id 列表同步表格勾选态 */
async function syncTableSelection(ids: string[]) {
  selectedRowIds.value = ids;
  await nextTick();
  const table = pendingTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  for (const row of displayRows.value) {
    if (ids.includes(row.id)) {
      table.toggleRowSelection(row, true);
    }
  }
}

/** 默认勾选当前可见待打行 */
function selectDefaultPendingRows() {
  const ids = displayRows.value
    .filter(
      (row) =>
        row.statusCode === 'pending' ||
        row.statusCode === 'ready' ||
        row.statusCode === 'executable',
    )
    .map((row) => row.id);
  const fallbackIds = ids.length > 0 ? ids : displayRows.value.map((row) => row.id);
  void syncTableSelection(fallbackIds);
}

/** 拉取当前单据待打列表（无数据为空态，不弹错误） */
async function refreshPendingRows(options?: { silent?: boolean }) {
  if (!patientScopeOk.value && activeDoc.value?.scope !== 'ward') {
    pendingRows.value = [];
    selectedRowIds.value = [];
    docPendingCounts.value = {
      ...docPendingCounts.value,
      [activeDocType.value]: 0,
    };
    return;
  }

  loadingPending.value = true;
  try {
    // 带时间条件：dateFrom/dateTo 同为查询日；无日期单据亦传当天（加载侧忽略）
    const dayDate = dayQueryDate.value || dayjs().format('YYYY-MM-DD');
    const rows = await loadPrintPendingRows({
      docType: activeDocType.value,
      patients: props.patients,
      dateFrom: dayDate,
      dateTo: dayDate,
      executionDeadline: executionDeadline.value,
    });
    pendingRows.value = rows;
    docPendingCounts.value = {
      ...docPendingCounts.value,
      [activeDocType.value]: rows.length,
    };
    await nextTick();
    selectDefaultPendingRows();
    if (!options?.silent) {
      ElMessage.success(`已刷新，共 ${rows.length} 条`);
    }
  } finally {
    loadingPending.value = false;
  }
}

/** 切换打印单据 */
function handleDocTypeChange(docType: PrintDocType) {
  if (activeDocType.value === docType) {
    return;
  }
  activeDocType.value = docType;
  // 带时间条件的单据：切到该类型时默认当天
  if (isDayQueryDocType(docType)) {
    dayQueryDate.value = dayjs().format('YYYY-MM-DD');
  }
  // 切离患者用药单时复位筛选
  if (docType !== 'oral_med_sheet') {
    oralMedInfusionFilter.value = 'all';
  }
}

/** 表格勾选变化时同步已选 id */
function handleSelectionChange(rows: PrintPendingRow[]) {
  selectedRowIds.value = rows.map((row) => row.id);
}

/** 查询：按当前条件重新拉业务数据 */
function handleQuery() {
  void refreshPendingRows();
}

/** 单日单据：切换到上一天并刷新 */
function handleDayQueryPrevDay() {
  dayQueryDate.value = dayjs(dayQueryDate.value).subtract(1, 'day').format('YYYY-MM-DD');
}

/** 单日单据：切换到下一天并刷新 */
function handleDayQueryNextDay() {
  dayQueryDate.value = dayjs(dayQueryDate.value).add(1, 'day').format('YYYY-MM-DD');
}

/** 全选当前可见待打行 */
function handleSelectAllPending() {
  selectDefaultPendingRows();
}

/** 清空勾选 */
function handleClearSelection() {
  void syncTableSelection([]);
}

/** 发送打印 */
async function handlePrint() {
  if (!isDocWired.value) {
    ElMessage.warning(`${activeDoc.value?.label ?? '该单据'}打印模板尚未对接`);
    return;
  }
  if (!canPrint.value) {
    ElMessage.warning(emptyDescription.value);
    return;
  }
  if (selectedRowIds.value.length === 0) {
    selectDefaultPendingRows();
  }
  if (printing.value) {
    return;
  }
  printing.value = true;
  try {
    const dayDate = dayQueryDate.value || dayjs().format('YYYY-MM-DD');
    const result = await printInpatientPrintCenter({
      docType: activeDocType.value,
      patients: props.patients,
      selectedRows: selectedRows.value.length > 0 ? selectedRows.value : displayRows.value,
      copies: copies.value,
      dateFrom: dayDate,
      dateTo: dayDate,
      executionDeadline: executionDeadline.value,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：${activeDoc.value?.label ?? '单据'}，共 ${result.count} 份`);
    }
  } finally {
    printing.value = false;
  }
}

/** 患者 / 单据 / 查询日变化时重新加载 */
watch(
  [() => props.patients.map((p) => p.id).join(','), activeDocType, dayQueryDate],
  () => {
    void refreshPendingRows({ silent: true });
  },
  { immediate: true },
);

/** 医嘱打印模式 / 用药单是否输液变化时仅重勾选，不重新请求 */
watch([orderPrintMode, continueFromRow, oralMedInfusionFilter], () => {
  selectDefaultPendingRows();
});
</script>

<template>
  <div class="print-center-page bg-background-deep flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />

    <div class="print-center-shell bg-background flex min-h-0 flex-1 flex-col">
      <!-- 单据类型切换（单层） -->
      <div class="print-center-docs" role="tablist" aria-label="打印单据类型">
        <button
          v-for="doc in docTypeOptions"
          :key="doc.code"
          type="button"
          role="tab"
          class="print-center-docs__item"
          :class="{ 'is-active': activeDocType === doc.code }"
          :aria-selected="activeDocType === doc.code"
          @click="handleDocTypeChange(doc.code)"
        >
          <span class="print-center-docs__label">{{ doc.label }}</span>
          <span v-if="doc.pendingCount" class="print-center-docs__badge">{{
            doc.pendingCount
          }}</span>
        </button>
      </div>

      <!-- 条件工具栏：筛选靠左，操作靠右 -->
      <div class="print-center-filters">
        <div class="print-center-filters__left flex flex-wrap items-center gap-2">
          <template v-if="isOrderExecDocType(activeDocType)">
            <ElRadioGroup v-model="orderPrintMode" size="small">
              <ElRadioButton value="all">全部</ElRadioButton>
              <ElRadioButton value="unprinted">未打</ElRadioButton>
              <ElRadioButton value="continue">续打</ElRadioButton>
            </ElRadioGroup>
            <template v-if="orderPrintMode === 'continue'">
              <span class="print-center-filters__hint">从第</span>
              <ElInputNumber
                v-model="continueFromRow"
                :min="1"
                :max="99"
                size="small"
                controls-position="right"
              />
              <span class="print-center-filters__hint">行起</span>
            </template>
          </template>

          <template v-if="isDayQueryDocType(activeDocType)">
            <ElButton size="small" plain @click="handleDayQueryPrevDay">上一天</ElButton>
            <ElDatePicker
              v-model="dayQueryDate"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              placeholder="查询日期"
              class="print-center-date-single"
            />
            <ElButton size="small" plain @click="handleDayQueryNextDay">下一天</ElButton>
          </template>

          <ElRadioGroup
            v-if="activeDocType === 'oral_med_sheet'"
            v-model="oralMedInfusionFilter"
            size="small"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="infusion">输液药</ElRadio>
            <ElRadio value="non_infusion">非输液药</ElRadio>
          </ElRadioGroup>
          <span v-if="activeDoc?.description" class="print-center-filters__desc">
            {{ activeDoc.description }}
          </span>
        </div>

        <div class="print-center-filters__right flex shrink-0 items-center gap-2">
          <ElButton size="small" plain @click="handleSelectAllPending">全选未打</ElButton>
          <ElButton size="small" plain @click="handleClearSelection">清空</ElButton>
          <ElButton type="primary" size="small" @click="handleQuery">刷新</ElButton>
        </div>
      </div>

      <!-- 主区：待打清单 -->
      <div class="print-center-body min-h-0 flex-1">
        <StationTriPaneLayout
          :show-left="false"
          :show-right="false"
          :show-footer="true"
          center-track="minmax(0, 1fr)"
        >
          <template #center>
            <div class="print-center-panel flex min-h-0 flex-1 flex-col">
              <div class="print-center-panel__head">
                <SectionTitle :title="panelTitle" />
                <ElTag size="small" effect="plain" type="info" class="print-center-panel__scope">
                  {{ resolvePrintScopeLabel(activeDoc?.scope ?? 'single') }}
                </ElTag>
                <span v-if="displayRows.length > 0" class="print-center-panel__count">
                  共 {{ displayRows.length }} 条
                </span>
              </div>
              <div
                v-if="loadingPending"
                class="print-center-panel__empty flex flex-1 items-center justify-center"
              >
                <ElEmpty description="正在加载待打数据…" />
              </div>
              <div
                v-else-if="displayRows.length === 0"
                class="print-center-panel__empty flex flex-1 items-center justify-center"
              >
                <ElEmpty :description="emptyDescription" />
              </div>
              <div v-else class="print-center-panel__table min-h-0 flex-1">
                <ElTable
                  ref="pendingTableRef"
                  class="print-center-table"
                  :data="displayRows"
                  height="100%"
                  stripe
                  size="small"
                  row-key="id"
                  v-loading="loadingPending"
                  @selection-change="handleSelectionChange"
                >
                  <ElTableColumn type="selection" width="48" align="center" />
                  <ElTableColumn
                    prop="bedNo"
                    label="床号"
                    width="80"
                    align="center"
                    header-align="center"
                  />
                  <ElTableColumn
                    prop="patientName"
                    label="患者"
                    width="96"
                    align="center"
                    header-align="center"
                  />
                  <ElTableColumn
                    prop="title"
                    :label="
                      activeDocType === 'order_dispense_sheet' || activeDocType === 'oral_med_sheet'
                        ? '药品'
                        : '内容'
                    "
                    min-width="200"
                    show-overflow-tooltip
                    header-align="center"
                  />
                  <ElTableColumn
                    prop="subtitle"
                    :label="
                      activeDocType === 'order_dispense_sheet' || activeDocType === 'oral_med_sheet'
                        ? '规格'
                        : '说明'
                    "
                    width="110"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="timeText"
                    :label="
                      activeDocType === 'order_dispense_sheet' || activeDocType === 'oral_med_sheet'
                        ? '最近执行'
                        : '时间'
                    "
                    width="160"
                    align="center"
                    header-align="center"
                  />
                  <ElTableColumn
                    prop="statusText"
                    label="状态"
                    width="96"
                    align="center"
                    header-align="center"
                  >
                    <template #default="{ row }">
                      <ElTag
                        size="small"
                        :type="
                          row.statusCode === 'pending' ||
                          row.statusCode === 'ready' ||
                          row.statusCode === 'executable'
                            ? 'warning'
                            : 'info'
                        "
                      >
                        {{ row.statusText }}
                      </ElTag>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="pageHint"
                    :label="
                      activeDocType === 'order_dispense_sheet' || activeDocType === 'oral_med_sheet'
                        ? '总量'
                        : '页/签'
                    "
                    width="80"
                    align="center"
                    header-align="center"
                  />
                </ElTable>
              </div>
            </div>
          </template>

          <template #footer>
            <div class="print-center-footer flex w-full items-center justify-between gap-3">
              <div class="print-center-footer__meta flex flex-wrap items-center gap-x-3 gap-y-1">
                <span>
                  已勾选
                  <strong>{{ selectedRows.length }}</strong>
                  项
                </span>
                <span class="print-center-footer__sep" aria-hidden="true">·</span>
                <span>
                  预估
                  <strong>{{ estimatedPages }}</strong>
                  页
                </span>
                <span class="print-center-footer__sep" aria-hidden="true">·</span>
                <span class="print-center-footer__copies inline-flex items-center gap-1.5">
                  份数
                  <ElInputNumber
                    v-model="copies"
                    :min="1"
                    :max="9"
                    size="small"
                    controls-position="right"
                    class="print-center-copies"
                  />
                </span>
                <ElTag size="small" :type="isDocWired ? 'success' : 'info'" effect="plain">
                  {{ isDocWired ? '已对接模板' : '模板未对接' }}
                </ElTag>
              </div>
              <div class="flex shrink-0 items-center gap-2">
                <ElButton
                  type="primary"
                  :disabled="!canPrint"
                  :loading="printing"
                  class="print-center-footer__print"
                  @click="handlePrint"
                >
                  打印
                </ElButton>
              </div>
            </div>
          </template>
        </StationTriPaneLayout>
      </div>
    </div>
  </div>
</template>
