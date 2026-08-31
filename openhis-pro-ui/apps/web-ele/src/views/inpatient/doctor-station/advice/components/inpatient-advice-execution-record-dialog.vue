<script setup lang="ts">
/**
 * 住院医嘱「执行记录」只读弹窗：全部 / 已执行 / 不执行 / 取消执行。
 * 「全部」= 三终态事实行合并（含已被取消对冲的已执行）；传 executionRecordHistory。
 */
import type { NurseStationOrderExecuteTableRow } from '#/views/inpatient/nurse-station/order-execute/services/map-nurse-station-order-execute';

import type { NurseStationOrderExecuteTab } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElButton, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { httpListNurseStationOrderExecute } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import AppDialog from '#/components/app-dialog.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  indexExecuteGroupsByEncounterId,
  mergeExecutePatientGroups,
} from '#/views/inpatient/nurse-station/order-execute/services/map-nurse-station-order-execute';

defineOptions({ name: 'InpatientAdviceExecutionRecordDialog' });

interface Props {
  /** 就诊主键 */
  encounterId?: string;
  /** 患者主键（护士站读 Gate 一致性） */
  patientId?: string;
  /**
   * 当前行医嘱 requestId（成组含成员）；用于收窄执行时点。
   */
  filterRequestIds?: string[];
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  filterRequestIds: () => [],
});

const visible = defineModel<boolean>({ default: false });

/** 终态 Tab（后端无 all；UI「全部」并行拉三终态） */
type ExecutionRecordStatusTab = Extract<
  NurseStationOrderExecuteTab,
  'cancelled' | 'executed' | 'skipped'
>;
/** UI Tab：全部 + 三终态 */
type ExecutionRecordTab = 'all' | ExecutionRecordStatusTab;

interface ExecutionRecordFlatRow {
  id: string;
  orderContent: string;
  orderKindLabel: string;
  therapyLabel: string;
  /** 执行时间展示（优先 recordedTime，缺则回落 planTime） */
  executeTimeText: string;
  operatorName: string;
  slotStatusLabel: string;
  slotStatusCode: ExecutionRecordStatusTab;
}

const STATUS_TABS: ExecutionRecordStatusTab[] = ['executed', 'skipped', 'cancelled'];

const TAB_OPTIONS: Array<{ code: ExecutionRecordTab; label: string }> = [
  { code: 'all', label: '全部' },
  { code: 'executed', label: '已执行' },
  { code: 'skipped', label: '不执行' },
  { code: 'cancelled', label: '取消执行' },
];

const activeTab = ref<ExecutionRecordTab>('all');
const loading = ref(false);
const flatRows = ref<ExecutionRecordFlatRow[]>([]);

const dialogTitle = computed(() => {
  const ids = props.filterRequestIds ?? [];
  if (ids.length > 1) {
    return `执行记录（成组 ${ids.length} 条）`;
  }
  return '执行记录';
});

/**
 * Tab code → 行状态展示文案。
 *
 * @param tab 终态 Tab
 * @return 中文状态
 */
function resolveSlotStatusLabel(tab: ExecutionRecordStatusTab): string {
  if (tab === 'executed') {
    return '已执行';
  }
  if (tab === 'skipped') {
    return '不执行';
  }
  return '取消执行';
}

/**
 * 状态 Tag 类型（用 code 分支，禁止用文案）。
 *
 * @param code 终态 Tab code
 * @return Element Plus Tag type
 */
function resolveSlotStatusTagType(
  code: ExecutionRecordStatusTab,
): 'danger' | 'info' | 'success' | 'warning' {
  if (code === 'executed') {
    return 'success';
  }
  if (code === 'skipped') {
    return 'warning';
  }
  return 'danger';
}

/**
 * 将执行列表行展开为时点扁平行。
 *
 * @param orders 映射后的执行表行
 * @param tab 终态 Tab
 * @return 扁平行
 */
function flattenOrdersToSlotRows(
  orders: NurseStationOrderExecuteTableRow[],
  tab: ExecutionRecordStatusTab,
): ExecutionRecordFlatRow[] {
  const statusLabel = resolveSlotStatusLabel(tab);
  const rows: ExecutionRecordFlatRow[] = [];
  for (const order of orders) {
    const content = String(order.content ?? '').trim() || '—';
    const kind = String(order.orderKindLabel ?? '').trim() || '—';
    const therapy = String(order.therapyLabel ?? '').trim() || '—';
    for (const schedule of order.planSchedules ?? []) {
      for (const slot of schedule.slots ?? []) {
        const plan = String(slot.planTime ?? '').trim();
        // 执行时间：优先实际登记时刻，缺省回落计划时点（取消执行等同构）
        const recorded = String(slot.recordedTime ?? '').trim();
        const rawTime = recorded || plan;
        let executeTimeText = '—';
        if (rawTime && dayjs(rawTime).isValid()) {
          executeTimeText = dayjs(rawTime).format('YYYY-MM-DD HH:mm');
        } else if (rawTime) {
          executeTimeText = rawTime;
        }
        rows.push({
          // procedureId 区分同计划时点的执行/取消事实行
          id: `${order.id}|${String(slot.procedureId ?? '').trim() || plan}|${tab}`,
          orderContent: content,
          orderKindLabel: kind,
          therapyLabel: therapy,
          executeTimeText,
          operatorName: String(slot.operatorName ?? '').trim() || '—',
          slotStatusLabel: statusLabel,
          slotStatusCode: tab,
        });
      }
    }
  }
  return rows;
}

/**
 * 按行医嘱 requestId 收窄执行行。
 *
 * @param orders 执行表行
 * @param requestIds 医嘱主键
 * @return 过滤后行
 */
function filterOrdersByRequestIds(
  orders: NurseStationOrderExecuteTableRow[],
  requestIds: string[],
): NurseStationOrderExecuteTableRow[] {
  if (!requestIds.length) {
    return orders;
  }
  const idSet = new Set(requestIds);
  return orders.filter((order) => {
    const orderId = preserveApiIdForRequest(order.orderId) || '';
    return orderId && idSet.has(orderId);
  });
}

/**
 * 拉取某一终态下西药+中药合并后的医嘱行。
 *
 * @param encounterId 就诊主键
 * @param tab 终态 Tab
 * @param deadline 截止时点
 * @return 映射后的执行表行
 */
async function fetchOrdersForStatusTab(
  encounterId: string,
  tab: ExecutionRecordStatusTab,
  deadline: string,
): Promise<NurseStationOrderExecuteTableRow[]> {
  const [westRes, tcmRes] = await Promise.all([
    httpListNurseStationOrderExecute({
      encounterIds: [encounterId],
      executeTab: tab,
      therapyType: 'all',
      medicineCategoryTab: 'west',
      executionDeadline: deadline,
      executionRecordHistory: true,
    }),
    httpListNurseStationOrderExecute({
      encounterIds: [encounterId],
      executeTab: tab,
      therapyType: 'all',
      medicineCategoryTab: 'tcm',
      executionDeadline: deadline,
      executionRecordHistory: true,
    }),
  ]);
  const mergedGroups = mergeExecutePatientGroups([westRes.groups ?? [], tcmRes.groups ?? []]);
  const byEncounter = indexExecuteGroupsByEncounterId(mergedGroups, tab);
  return byEncounter.get(encounterId) ?? [];
}

/**
 * 加载当前 Tab 的执行时点（「全部」= 三终态合并）。
 */
async function loadExecutionRecords() {
  const encounterId = preserveApiIdForRequest(props.encounterId) || '';
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法查看执行记录');
    flatRows.value = [];
    return;
  }
  void preserveApiIdForRequest(props.patientId);
  const deadline = dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const filterIds = (props.filterRequestIds ?? [])
    .map((id) => preserveApiIdForRequest(id) || '')
    .filter((id) => Boolean(id));
  const tabsToLoad: ExecutionRecordStatusTab[] =
    activeTab.value === 'all' ? STATUS_TABS : [activeTab.value];

  loading.value = true;
  try {
    const chunks = await Promise.all(
      tabsToLoad.map(async (tab) => {
        const orders = await fetchOrdersForStatusTab(encounterId, tab, deadline);
        const scoped = filterOrdersByRequestIds(orders, filterIds);
        return flattenOrdersToSlotRows(scoped, tab);
      }),
    );
    const merged = chunks.flat();
    // 执行时间正序；同时刻按终态稳定序
    const statusOrder: Record<ExecutionRecordStatusTab, number> = {
      executed: 0,
      skipped: 1,
      cancelled: 2,
    };
    merged.sort((left, right) => {
      const byTime = left.executeTimeText.localeCompare(right.executeTimeText);
      if (byTime !== 0) {
        return byTime;
      }
      return statusOrder[left.slotStatusCode] - statusOrder[right.slotStatusCode];
    });
    flatRows.value = merged;
  } catch {
    flatRows.value = [];
  } finally {
    loading.value = false;
  }
}

/** 切换 Tab 后重载 */
function handleTabChange(tab: ExecutionRecordTab) {
  if (activeTab.value === tab) {
    return;
  }
  activeTab.value = tab;
  void loadExecutionRecords();
}

/** 手动刷新当前 Tab */
function handleRefresh() {
  void loadExecutionRecords();
}

watch(visible, (open) => {
  if (!open) {
    return;
  }
  // 打开默认「全部」；每次打开重拉，避免脏缓存
  activeTab.value = 'all';
  void loadExecutionRecords();
});
</script>

<template>
  <AppDialog v-model="visible" :title="dialogTitle" width="920px" destroy-on-close>
    <div class="ip-exec-record">
      <div class="ip-exec-record__toolbar">
        <div class="ip-exec-record__tabs">
          <ElButton
            v-for="tab in TAB_OPTIONS"
            :key="tab.code"
            size="small"
            :type="activeTab === tab.code ? 'primary' : 'default'"
            @click="handleTabChange(tab.code)"
          >
            {{ tab.label }}
          </ElButton>
        </div>
        <ElButton size="small" :loading="loading" @click="handleRefresh">刷新</ElButton>
      </div>

      <ElTable
        v-loading="loading"
        :data="flatRows"
        border
        stripe
        height="420"
        empty-text="暂无执行记录"
      >
        <ElTableColumn prop="slotStatusLabel" label="执行状态" width="100" align="center">
          <template #default="{ row }">
            <ElTag size="small" effect="light" :type="resolveSlotStatusTagType(row.slotStatusCode)">
              {{ row.slotStatusLabel }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="therapyLabel" label="长临" width="88" align="center" />
        <ElTableColumn prop="orderKindLabel" label="类型" width="88" align="center" />
        <ElTableColumn prop="orderContent" label="医嘱内容" min-width="200" show-overflow-tooltip />
        <ElTableColumn prop="executeTimeText" label="执行时间" width="160" align="center" />
        <ElTableColumn prop="operatorName" label="操作人" width="100" align="center" />
      </ElTable>
    </div>
  </AppDialog>
</template>

<style scoped>
.ip-exec-record {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ip-exec-record__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ip-exec-record__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
</style>
