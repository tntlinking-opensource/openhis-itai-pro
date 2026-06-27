<script setup lang="ts">
import { computed, ref } from 'vue';

interface ViewPatientItem {
  id: string;
  bedNo: string;
  name: string;
  gender: string;
  age: number;
  nurseLevel: string;
  /** 费用性质。 */
  feeType?: string;
  /** 主要诊断。 */
  diagnosis?: string;
  wardName: string;
  riskTags: string[];
}

interface OrderViewRow {
  id: string;
  status: string;
  orderType: string;
  startTime: string;
  checkTime: string;
  content: string;
  dosage: string;
  bindText: string;
  executeDept: string;
  doctor: string;
}

interface ExecuteRecordRow {
  id: string;
  planTime: string;
  content: string;
  amount: string;
  executeStatus: string;
  executeTime: string;
  operatorA: string;
  operatorB: string;
}

interface Props {
  patients?: ViewPatientItem[];
}

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

defineOptions({
  name: 'InpatientNurseStationOrderViewModule',
});

const activeOrderType = ref('all');
const cancelledOnly = ref(false);
const queryTime = ref<[string, string]>(['2025-07-29 15:13', '2025-07-30 15:13']);
const orderTypeTabs: Array<{ label: string; value: string }> = [
  { label: '全部', value: 'all' },
  { label: '长期', value: 'long' },
  { label: '临时', value: 'temp' },
];

const orderRows = ref<OrderViewRow[]>([
  {
    id: 'order-view-1',
    status: '',
    orderType: '长期',
    startTime: '2025-07-30 15:04',
    checkTime: '',
    content: '维生素C片【0.1g*100片/瓶】(住院西药房)',
    dosage: '0.1g(1片)',
    bindText: '请选择',
    executeDept: '住院西药房',
    doctor: '',
  },
  {
    id: 'order-view-2',
    status: '',
    orderType: '长期',
    startTime: '2025-07-30 15:04',
    checkTime: '',
    content: '维生素E胶囊【50mg*60粒/瓶】(住院西药房)',
    dosage: '50mg(1粒)',
    bindText: '请选择',
    executeDept: '住院西药房',
    doctor: '',
  },
]);

const executeRows = ref<ExecuteRecordRow[]>([]);

/** 生成右侧患者抬头信息，保持与护士站卡片字段一致。 */
const patientHeaderText = computed(() => {
  const first = props.patients[0];
  if (!first) return '';
  return `${first.bedNo || '--'}床 ${first.name} ${first.gender}/${first.age}岁`;
});
</script>

<template>
  <div class="order-view-module">
    <section class="patient-summary">
      <div class="patient-summary__left">
        <span class="patient-name">{{ patientHeaderText || '未选择患者' }}</span>
        <span class="summary-item">自理</span>
        <span class="summary-item">住院：12天</span>
        <span class="summary-item">入科：2025年07月18日 16:48</span>
        <span class="summary-item">入院时间：2025年07月18日 16:47</span>
        <span class="summary-item">住院号:20250000276</span>
        <span class="summary-item">诊断：脑动脉夹层</span>
      </div>
      <div class="patient-summary__right">
        <span class="summary-fold">三</span>
      </div>
    </section>

    <section class="order-toolbar">
      <div class="order-toolbar__left">
        <span
          v-for="item in orderTypeTabs"
          :key="item.value"
          class="order-tab"
          :class="{ 'is-active': activeOrderType === item.value }"
          @click="activeOrderType = item.value"
        >
          {{ item.label }}
        </span>
        <span class="divider">|</span>
        <span class="time-label">开始时间</span>
        <ElDatePicker
          v-model="queryTime"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm"
          format="YYYY-MM-DD HH:mm"
          class="toolbar-time"
        />
        <ElCheckbox v-model="cancelledOnly" label="作废" />
      </div>
    </section>

    <section class="order-table-wrap">
      <ElTable :data="orderRows" height="100%" border>
        <ElTableColumn prop="status" label="状态" width="70" />
        <ElTableColumn prop="orderType" label="长/临" width="70" />
        <ElTableColumn prop="startTime" label="开始时间" min-width="140" />
        <ElTableColumn prop="checkTime" label="校对时间" min-width="120" />
        <ElTableColumn label="医嘱内容" min-width="380">
          <template #default="{ row }">
            <div class="order-content-cell">
              <p>{{ row.content }}</p>
              <p class="order-dose">{{ row.dosage }}</p>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="bindText" label="补费绑定" min-width="120">
          <template #default="{ row }">
            <ElSelect :model-value="row.bindText" class="w-full">
              <ElOption label="请选择" value="请选择" />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="executeDept" label="执行科室" min-width="120" />
        <ElTableColumn prop="doctor" label="医生" min-width="90" />
      </ElTable>
    </section>

    <section class="execute-record">
      <header class="execute-record__title">执行记录</header>
      <ElTable :data="executeRows" border class="execute-record__table">
        <ElTableColumn prop="planTime" label="预计执行时间" min-width="140" />
        <ElTableColumn prop="content" label="医嘱内容" min-width="260" />
        <ElTableColumn prop="amount" label="数量" width="80" />
        <ElTableColumn prop="executeStatus" label="执行状态" width="90" />
        <ElTableColumn prop="executeTime" label="执行时间" min-width="120" />
        <ElTableColumn prop="operatorA" label="执行人1" min-width="90" />
        <ElTableColumn prop="operatorB" label="执行人2" min-width="90" />
        <template #empty>
          <div class="record-empty">暂无数据</div>
        </template>
      </ElTable>
    </section>
  </div>
</template>

<style scoped>
.order-view-module {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 100%;
  background: #eef9fd;
}

.patient-summary {
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  justify-content: space-between;
  min-height: 38px;
  padding: 0 8px;
}

.patient-summary__left {
  align-items: center;
  color: var(--el-text-color-primary);
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 10px;
  min-width: 0;
}

.patient-name {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.summary-item {
  color: #4b5563;
  white-space: nowrap;
}

.summary-fold {
  color: #94a3b8;
  font-size: 16px;
}

.order-toolbar {
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  gap: 8px;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 8px;
}

.order-toolbar__left {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.order-tab {
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 13px;
}

.order-tab.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.divider {
  color: var(--el-border-color);
}

.time-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.toolbar-time {
  width: 340px;
}

.toolbar-print {
  min-width: 68px;
}

.order-table-wrap {
  flex: 1;
  min-height: 220px;
}

.order-content-cell p {
  margin: 0;
}

.order-dose {
  color: #0f766e;
  font-size: 13px;
  margin-top: 2px !important;
}

.execute-record {
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  min-height: 170px;
}

.execute-record__title {
  align-items: center;
  color: #64748b;
  display: flex;
  font-size: 13px;
  gap: 4px;
  height: 32px;
  padding: 0 10px;
}

.execute-record__table {
  flex: 1;
  min-height: 0;
}

.record-empty {
  align-items: center;
  color: #9ca3af;
  display: flex;
  font-size: 13px;
  height: 70px;
  justify-content: center;
}

.order-table-wrap :deep(.el-table__header-wrapper th.el-table__cell),
.execute-record__table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #edf5fb;
  color: #64748b;
  font-size: 12px;
}

.order-table-wrap :deep(.el-table__row td.el-table__cell),
.execute-record__table :deep(.el-table__row td.el-table__cell) {
  color: #334155;
  font-size: 13px;
  padding: 7px 0;
}
</style>
