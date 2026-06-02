<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';

import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';

import {
  nurseStationDispenseFallbackOrdersMock,
  nurseStationDispenseOrdersByPatientIdMock,
  nurseStationDispenseSummarySheetsMock,
} from '../mock/dispense-summary.mock';

type DispenseDrugCategoryTab = 'chinese' | 'western';
type DispenseDisplayMode = 'detail' | 'summary';
type DispenseOrderType = 'all' | 'long' | 'temp';

interface DispenseScheduleSlot {
  checked: boolean;
  time: string;
}

interface DispenseScheduleDay {
  date: string;
  slots: DispenseScheduleSlot[];
}

interface DispenseOrderItem {
  id: string;
  orderType: Exclude<DispenseOrderType, 'all'>;
  drugCategory: 'chinese' | 'western';
  orderClass: string;
  orderContent: string;
  pickupAmount: string;
  pickupPharmacy: string;
  schedule: DispenseScheduleDay[];
  extension?: string;
  groupItems?: string[];
  procedureContent?: string;
  procedureDosageText?: string;
  procedureExtension?: string;
}

interface DispensePatientCard {
  id: string;
  bedNo: string;
  patientNo: string;
  patientName: string;
  gender: string;
  age: number;
  diagnosis: string;
  attendingDoctor: string;
  estimatedAmount: string;
  orders: DispenseOrderItem[];
}

interface DispenseSummarySheetItem {
  id: string;
  sheetNo: string;
  applyTime: string;
  dispenseTime: string;
  pharmacy: string;
  applicant: string;
  pickupUser: string;
  dispenseStatus: string;
}

interface DispensePatientItem {
  id: string;
  bedNo: string;
  name: string;
  gender?: string;
  age: number;
  nurseLevel: string;
  diagnosis?: string;
}

interface Props {
  patients?: DispensePatientItem[];
}

defineOptions({
  name: 'InpatientNurseStationDispenseSummaryModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const drugCategoryTabs: Array<{ label: string; value: DispenseDrugCategoryTab }> = [
  { label: '西药', value: 'western' },
  { label: '中药', value: 'chinese' },
];

const displayModeTabs: Array<{ label: string; value: DispenseDisplayMode }> = [
  { label: '明细', value: 'detail' },
  { label: '汇总', value: 'summary' },
];

const activeDrugCategoryTab = ref<DispenseDrugCategoryTab>('western');
const activeDisplayMode = ref<DispenseDisplayMode>('detail');
const activeOrderType = ref<DispenseOrderType>('all');
const selectedAll = ref(false);

/** 汇总领药查询截止时间（对接接口时作为 endTime 参数）。 */
const queryDeadline = ref(dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'));

const mockOrdersByPatientId: Record<string, DispenseOrderItem[]> =
  nurseStationDispenseOrdersByPatientIdMock as Record<string, DispenseOrderItem[]>;

const fallbackOrders: DispenseOrderItem[] =
  nurseStationDispenseFallbackOrdersMock as DispenseOrderItem[];

const mockSummarySheets: DispenseSummarySheetItem[] =
  nurseStationDispenseSummarySheetsMock as DispenseSummarySheetItem[];

/** 按当前页签与长临嘱类型筛选领药明细（对接接口后以服务端条件为准）。 */
function filterOrdersForDisplay(
  orders: DispenseOrderItem[],
  drugCategoryTab: DispenseDrugCategoryTab,
  orderType: DispenseOrderType,
): DispenseOrderItem[] {
  return orders.filter((item) => {
    // 按顶部西药/中药页签筛选药品类别。
    const matchCategory =
      drugCategoryTab === 'western'
        ? item.drugCategory === 'western'
        : item.drugCategory === 'chinese';
    if (!matchCategory) return false;
    if (orderType === 'all') return true;
    return item.orderType === orderType;
  });
}

/** 普通场景下主文案：优先 orderContent，缺省回退到类型。 */
function dispenseOrderDisplayedContent(item: DispenseOrderItem): string {
  const c = item.orderContent?.trim();
  if (c && c.length > 0) return item.orderContent;
  return item.orderClass;
}

const visibleCards = computed<DispensePatientCard[]>(() => {
  // 将左侧已选患者转换为汇总领药展示结构，并补齐示例领药医嘱。
  return props.patients.map((patient) => {
    const rawOrders = mockOrdersByPatientId[patient.id] ?? fallbackOrders;
    // 当前页面可见的领药医嘱明细。
    const filteredOrders = filterOrdersForDisplay(
      rawOrders,
      activeDrugCategoryTab.value,
      activeOrderType.value,
    );
    return {
      id: patient.id,
      bedNo: patient.bedNo ? `${patient.bedNo}床` : '未分床',
      patientNo: patient.id.toUpperCase(),
      patientName: patient.name,
      gender: patient.gender || '-',
      age: patient.age,
      diagnosis: patient.diagnosis || '暂无主要诊断',
      attendingDoctor: `责任护士:${patient.nurseLevel}`,
      estimatedAmount: '1121.03',
      orders: filteredOrders,
    };
  });
});

const cardCountText = computed(() => `${visibleCards.value.length}位患者`);
const expandedPatientIds = ref<string[]>([]);
const summaryCountText = computed(() => `${mockSummarySheets.length}条记录`);

/** 患者联动变化时默认展开所有卡片，减少切换后的二次点击。 */
watch(
  visibleCards,
  (cards) => {
    expandedPatientIds.value = cards.map((item) => item.id);
  },
  { immediate: true },
);

/** 触发汇总领药查询（当前为静态示例，预留接口接入）。 */
function handleRunFilterQuery() {
  // 预留：调用汇总领药列表接口，传入 activeDrugCategoryTab、activeDisplayMode、activeOrderType、queryDeadline。
}

/** 批量汇总领药（当前为按钮占位，预留提交领药单动作）。 */
function handleCreateSummaryPickup() {
  // 预留：提交当前勾选明细生成领药单。
}

/** 打印汇总明细（当前为按钮占位，预留打印模板联动）。 */
function handlePrintPickupDetail() {
  // 预留：打开领药明细打印预览。
}

/** 查看汇总单详情（当前为占位，预留跳转详情页）。 */
function handleViewSummarySheet(row: DispenseSummarySheetItem) {
  void row;
}

/** 打印汇总单（当前为占位，预留打印模板联动）。 */
function handlePrintSummarySheet(row: DispenseSummarySheetItem) {
  void row;
}
</script>

<template>
  <div
    class="dispense-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <section
      class="flex w-full flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
        <ElRadioGroup v-model="activeDrugCategoryTab" class="dispense-view-radio shrink-0">
          <ElRadioButton v-for="tab in drugCategoryTabs" :key="tab.value" :label="tab.value">
            {{ tab.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-model="activeDisplayMode"
          class="dispense-display-radio inline-flex shrink-0 flex-wrap items-center"
        >
          <ElRadioButton v-for="tab in displayModeTabs" :key="tab.value" :label="tab.value">
            {{ tab.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">截止时间</span>
        <ElDatePicker
          v-model="queryDeadline"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择截止时间"
          class="dispense-datetime"
        />
        <ElRadioGroup
          v-model="activeOrderType"
          class="dispense-type-radio inline-flex shrink-0 flex-wrap items-center gap-3"
        >
          <ElRadio label="all">全部</ElRadio>
          <ElRadio label="long">长期</ElRadio>
          <ElRadio label="temp">临时</ElRadio>
        </ElRadioGroup>
        <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <span class="mr-1 shrink-0 text-xs text-[var(--el-text-color-secondary)]">{{
          activeDisplayMode === 'summary' ? summaryCountText : cardCountText
        }}</span>
        <label
          class="mr-1 flex shrink-0 items-center gap-1 text-xs text-[var(--el-text-color-regular)]"
        >
          全选
          <ElSwitch v-model="selectedAll" inline-prompt active-text=" " inactive-text=" " />
        </label>
        <ElButton type="primary" @click="handleCreateSummaryPickup">汇总领药</ElButton>
        <ElButton plain @click="handlePrintPickupDetail">打印明细</ElButton>
      </div>
    </section>

    <section
      v-if="activeDisplayMode === 'detail' && visibleCards.length > 0"
      class="min-h-0 flex-1 overflow-auto p-2.5"
    >
      <ElCollapse v-model="expandedPatientIds" class="patient-collapse">
        <ElCollapseItem
          v-for="card in visibleCards"
          :key="card.id"
          :name="card.id"
          class="dispense-card mb-2.5 overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] last:mb-0"
        >
          <template #title>
            <header class="card-head">
              <div class="patient-meta">
                <span class="bed-tag">{{ card.bedNo }}【{{ card.patientNo }}】</span>
                <span class="name-text"
                  >{{ card.patientName }} {{ card.gender }} / {{ card.age }}</span
                >
                <span class="diag-text">{{ card.diagnosis }}</span>
              </div>
              <div class="balance-meta">
                <span>{{ card.attendingDoctor }}</span>
                <span class="money-item">预交金额 {{ card.estimatedAmount }}</span>
              </div>
            </header>
          </template>

          <div class="card-table-wrap">
            <ElTable :data="card.orders" class="order-table" stripe :show-header="false">
              <ElTableColumn type="selection" width="54" />
              <ElTableColumn prop="orderClass" label="类型" min-width="100" />
              <ElTableColumn label="医嘱内容" min-width="380">
                <template #default="{ row }">
                  <div class="dispense-order-content-wrap">
                    <AdviceOrderDisplayCell
                      :row-key="row.id"
                      :group-items="row.groupItems"
                      :content="dispenseOrderDisplayedContent(row)"
                      :dosage-text="row.pickupAmount"
                      :extension="row.extension"
                    />
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="pickupAmount" label="领药数量" min-width="90" />
              <ElTableColumn prop="pickupPharmacy" label="取药药室" min-width="120" />
              <ElTableColumn label="领药时间" min-width="280">
                <template #default="{ row }">
                  <div class="pickup-schedule">
                    <div v-for="day in row.schedule" :key="day.date" class="pickup-schedule__day">
                      <span class="pickup-schedule__date">{{ day.date }}</span>
                      <div class="pickup-schedule__slots">
                        <ElCheckbox
                          v-for="slot in day.slots"
                          :key="`${day.date}-${slot.time}`"
                          v-model="slot.checked"
                          border
                          size="small"
                        >
                          {{ slot.time }}
                        </ElCheckbox>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </section>
    <section
      v-else-if="activeDisplayMode === 'summary'"
      class="min-h-0 min-w-0 flex-1 overflow-hidden bg-[#eef9fd] p-2"
    >
      <ElTable :data="mockSummarySheets" class="summary-sheet-table h-full" stripe>
        <ElTableColumn prop="sheetNo" label="单据号" min-width="170" show-overflow-tooltip />
        <ElTableColumn prop="applyTime" label="申请时间" min-width="165" />
        <ElTableColumn prop="dispenseTime" label="发药时间" min-width="165">
          <template #default="{ row }">
            {{ row.dispenseTime || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="pharmacy" label="药房" min-width="110" />
        <ElTableColumn prop="applicant" label="申请人" min-width="100" />
        <ElTableColumn prop="pickupUser" label="领药人" min-width="100">
          <template #default="{ row }">
            {{ row.pickupUser || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="dispenseStatus" label="发药状态" min-width="100" />
        <ElTableColumn label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="inline-flex items-center gap-2">
              <ElButton link type="primary" @click="handleViewSummarySheet(row)">详情</ElButton>
              <ElButton link type="primary" @click="handlePrintSummarySheet(row)">打印</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者" />
    </section>
  </div>
</template>

<style scoped>
.dispense-datetime {
  width: 220px;
  max-width: 100%;
}

.dispense-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

.patient-collapse {
  border-top: none;
  border-bottom: none;
}

.dispense-card {
  border: 2px solid #e4e7ed !important;
  border-radius: 14px !important;
}

.patient-collapse :deep(.el-collapse-item__header) {
  min-height: 42px;
  border-bottom: none;
  background: var(--el-fill-color-blank);
  line-height: normal;
  align-items: center;
}

.patient-collapse :deep(.el-collapse-item__arrow) {
  order: -1;
  margin-right: 6px;
  margin-left: 10px;
  align-self: center;
}

.patient-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.patient-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.card-head {
  min-height: 42px;
  width: 100%;
  padding: 0 10px 0 2px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.patient-meta,
.balance-meta {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.bed-tag {
  font-size: 16px;
  font-weight: 600;
}

.diag-text {
  color: var(--el-text-color-secondary);
}

.money-item {
  color: var(--el-color-warning);
  font-weight: 600;
}

.order-table {
  width: 100%;
}

.card-table-wrap {
  padding: 8px 10px 10px;
}

.order-table :deep(.el-table__row td.el-table__cell) {
  color: var(--el-text-color-primary);
  font-size: 13px;
  padding: 8px 0;
}

.dispense-order-content-wrap {
  min-width: 0;
}

.pickup-schedule {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pickup-schedule__day {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pickup-schedule__date {
  min-width: 40px;
  color: var(--el-text-color-secondary);
}

.pickup-schedule__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.pickup-schedule__slots :deep(.el-checkbox) {
  margin-right: 0;
}
</style>
