<script setup lang="ts">
import type {
  DischargePendingExecutionItemDto,
  DischargePendingMedicationItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import type { PatientItem } from '../types';

import { computed, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useRequest } from '#/hooks/use-request';

import { submitClearDischargedBed } from '../services/clear-discharged-bed';
import { submitInpatientDischarge } from '../services/execute-inpatient-discharge';
import {
  listDischargePendingExecutions,
  listDischargePendingMedications,
} from '../services/list-discharge-pending';
import {
  blockingReasonToLabel,
  encounterStatusToDischargeLabel,
  filterDischargeExecutionRows,
  formatDischargeReqTime,
  summarizeMedicationRows,
  therapyEnumToLabel,
} from '../services/map-discharge-pending';

type TherapyFilter = 'all' | 'long' | 'temporary';

interface Props {
  patient?: PatientItem | null;
  onRefresh?: () => void | Promise<void>;
}

defineOptions({
  name: 'InpatientNurseStationDischargePanel',
});

const props = withDefaults(defineProps<Props>(), {
  patient: null,
  onRefresh: undefined,
});

const ENCOUNTER_STATUS_ON_HOLD = 'ON_HOLD';
const ENCOUNTER_STATUS_PENDING = 'PENDING';
const ENCOUNTER_STATUS_COMPLETED = 'COMPLETED';

/** 与后端 BR-IP-ATD-010 出 Tab 可见患者一致 */
const DISCHARGE_TAB_VISIBLE_STATUSES = new Set<string>([
  ENCOUNTER_STATUS_ON_HOLD,
  ENCOUNTER_STATUS_PENDING,
  ENCOUNTER_STATUS_COMPLETED,
]);

const activeTherapyFilter = ref<TherapyFilter>('all');
const executionRows = ref<DischargePendingExecutionItemDto[]>([]);
const medicationRows = ref<DischargePendingMedicationItemDto[]>([]);
const listHint = ref('');

const executionsRequest = useRequest(listDischargePendingExecutions, {
  manual: true,
  showErrorMessage: false,
});
const medicationsRequest = useRequest(listDischargePendingMedications, {
  manual: true,
  showErrorMessage: false,
});
const dischargeRequest = useRequest(submitInpatientDischarge, {
  manual: true,
  showErrorMessage: false,
});
const clearBedRequest = useRequest(submitClearDischargedBed, {
  manual: true,
  showErrorMessage: false,
});

const encounterId = computed(() => String(props.patient?.id ?? '').trim());
const listLoading = computed(
  () => executionsRequest.loading.value || medicationsRequest.loading.value,
);
const actionLoading = computed(
  () => dischargeRequest.loading.value || clearBedRequest.loading.value,
);

/** 出 Tab 已选中床位图内患者 */
const hasDischargePatient = computed(() => encounterId.value.length > 0);

const patientSummary = computed(() => {
  if (!props.patient) {
    return '请从上方床位图选择待出院/待结算患者';
  }
  const status = encounterStatusToDischargeLabel(
    props.patient.statusEnum,
    props.patient.statusEnumText,
  );
  const bed = (props.patient.bedNo ?? '').trim() || '—';
  return `${props.patient.name} · ${bed} · ${status}`;
});

/** 待出院 / 待结算时可办理出院 */
const dischargeEnabled = computed(() => {
  const status = (props.patient?.statusEnum ?? '').trim();
  return status === ENCOUNTER_STATUS_ON_HOLD;
});

/** 已结算待清床时可清床离院 */
const clearBedEnabled = computed(() => {
  const status = (props.patient?.statusEnum ?? '').trim();
  return status === ENCOUNTER_STATUS_COMPLETED;
});

/** 按长临筛选后的待执行医嘱 */
const filteredExecutionRows = computed(() =>
  filterDischargeExecutionRows(executionRows.value, activeTherapyFilter.value),
);

const medicationSummary = computed(() => summarizeMedicationRows(medicationRows.value));

/** 从接口错误体提取用户提示 */
function readRequestErrorMessage(error: unknown, fallback: string): string {
  const err = error as { message?: string; msg?: string };
  const text = String(err?.message ?? err?.msg ?? '').trim();
  return text || fallback;
}

/** 选中患者变化时刷新待办列表（仅出 Tab 可见患者） */
async function loadDischargePending() {
  executionRows.value = [];
  medicationRows.value = [];
  listHint.value = '';
  if (!encounterId.value) {
    return;
  }
  const status = (props.patient?.statusEnum ?? '').trim();
  if (!DISCHARGE_TAB_VISIBLE_STATUSES.has(status)) {
    listHint.value =
      '当前患者不在出 Tab 可见范围，请等待床位图（出）刷新后重新选择待出院/待结算/已结算患者';
    return;
  }
  let execFailed = false;
  let medFailed = false;
  try {
    const execRes = await executionsRequest.run(encounterId.value);
    executionRows.value = execRes?.items ?? [];
  } catch (error) {
    execFailed = true;
    executionRows.value = [];
    listHint.value = readRequestErrorMessage(error, '待执行医嘱加载失败');
  }
  try {
    const medRes = await medicationsRequest.run(encounterId.value);
    medicationRows.value = medRes?.items ?? [];
  } catch (error) {
    medFailed = true;
    medicationRows.value = [];
    const medMsg = readRequestErrorMessage(error, '待发药品加载失败');
    listHint.value = listHint.value ? `${listHint.value}；${medMsg}` : medMsg;
  }
  if (!execFailed && !medFailed) {
    listHint.value = '';
  }
}

/** 办理出院 */
async function handleDischarge() {
  if (!encounterId.value || !dischargeEnabled.value) return;
  try {
    await ElMessageBox.confirm('确认办理出院？系统将校验医嘱与发药待办。', '办理出院', {
      type: 'warning',
      confirmButtonText: '办理出院',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    const res = await dischargeRequest.run(encounterId.value);
    if (res?.succeeded === false) {
      ElMessage.error('办理出院失败，请刷新后重试');
      return;
    }
    ElMessage.success('出院办理成功');
    await props.onRefresh?.();
    await loadDischargePending();
  } catch (error) {
    // 业务错误文案由全局 HTTP 拦截器统一弹出，此处仅刷新待办避免列表与门禁口径不一致
    await loadDischargePending();
  }
}

/** 清床离院 */
async function handleClearBed() {
  if (!encounterId.value) return;
  try {
    await ElMessageBox.confirm('确认清床离院？释床后患者将从护士站列表移除。', '清床离院', {
      type: 'warning',
      confirmButtonText: '清床',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    const res = await clearBedRequest.run(encounterId.value);
    if (res?.succeeded === false) {
      ElMessage.error('清床失败，请检查病床状态');
      return;
    }
    ElMessage.success('清床成功');
    await props.onRefresh?.();
  } catch {
    // 业务错误文案由全局 HTTP 拦截器统一弹出
  }
}

watch(
  encounterId,
  () => {
    void loadDischargePending();
  },
  { immediate: true },
);
</script>

<template>
  <div v-loading="listLoading || actionLoading" class="discharge-panel">
    <ElAlert
      v-if="!hasDischargePatient"
      class="discharge-panel__alert discharge-panel__alert--only"
      type="info"
      :closable="false"
      show-icon
      title="请从上方床位图选择待出院、待结算或已结算占床患者；左侧在院患者不可直接办理出院。"
    />
    <template v-else>
      <ElAlert
        v-if="listHint"
        class="discharge-panel__alert"
        type="warning"
        :closable="false"
        show-icon
        :title="listHint"
      />

      <section class="discharge-toolbar">
        <div class="discharge-toolbar__left">
          <span class="discharge-toolbar__patient">{{ patientSummary }}</span>
          <ElRadioGroup v-model="activeTherapyFilter" size="small">
            <ElRadioButton value="all">全部</ElRadioButton>
            <ElRadioButton value="long">长期</ElRadioButton>
            <ElRadioButton value="temporary">临时</ElRadioButton>
          </ElRadioGroup>
        </div>
        <div class="discharge-toolbar__right">
          <ElButton plain @click="loadDischargePending">刷新待办</ElButton>
          <ElButton type="success" :disabled="!dischargeEnabled" @click="handleDischarge">
            办理出院
          </ElButton>
          <ElButton type="primary" :disabled="!clearBedEnabled" @click="handleClearBed">
            清床离院
          </ElButton>
        </div>
      </section>

      <section class="discharge-section">
        <header class="discharge-section__title">
          待执行医嘱（{{ filteredExecutionRows.length }}）
        </header>
        <div class="discharge-table-wrap">
          <ElTable
            :data="filteredExecutionRows"
            border
            class="discharge-table"
            empty-text="暂无阻塞出院的医嘱"
            height="100%"
          >
            <ElTableColumn label="长临" width="72">
              <template #default="{ row }">
                {{ therapyEnumToLabel(row.therapyEnum) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="开立时间" width="160">
              <template #default="{ row }">
                {{ formatDischargeReqTime(row.reqAuthoredTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="displayName"
              label="医嘱内容"
              min-width="240"
              show-overflow-tooltip
            />
            <ElTableColumn prop="doseText" label="剂量" width="100" show-overflow-tooltip />
            <ElTableColumn prop="rateCode" label="频次" width="88" show-overflow-tooltip />
            <ElTableColumn label="阻塞原因" min-width="140">
              <template #default="{ row }">
                {{ blockingReasonToLabel(row.blockingReason) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>

      <section class="discharge-section discharge-section--med">
        <header class="discharge-section__title">待发/待退药（{{ medicationSummary }}）</header>
        <div class="discharge-table-wrap discharge-table-wrap--med">
          <ElTable
            :data="medicationRows"
            border
            class="discharge-table"
            empty-text="暂无待发/待退药"
            height="100%"
          >
            <ElTableColumn
              prop="medicationName"
              label="药品"
              min-width="180"
              show-overflow-tooltip
            />
            <ElTableColumn label="状态" width="100">
              <template #default="{ row }">
                {{ row.statusEnumText || row.statusEnum || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="quantity" label="数量" width="88" />
            <ElTableColumn prop="unitCode" label="单位" width="72" />
            <ElTableColumn
              prop="performLocationName"
              label="库房"
              min-width="120"
              show-overflow-tooltip
            />
            <ElTableColumn label="计划时间" width="160">
              <template #default="{ row }">
                {{ formatDischargeReqTime(row.plannedDispenseTime) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.discharge-panel {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  background: #f3f7fb;
}

.discharge-panel__alert {
  flex-shrink: 0;
  margin: 8px 8px 0;
}

.discharge-panel__alert--only {
  flex: 1;
  margin: 12px;
}

.discharge-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  padding: 8px 10px;
  flex-shrink: 0;
}

.discharge-toolbar__left,
.discharge-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.discharge-toolbar__patient {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  margin-right: 4px;
}

.discharge-section {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  padding: 6px 8px 0;
}

.discharge-section--med {
  flex: 0 0 38%;
  min-height: 160px;
  padding-bottom: 8px;
}

.discharge-section__title {
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}

.discharge-table-wrap {
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 4px;
}

.discharge-table-wrap--med {
  min-height: 120px;
}

.discharge-table {
  width: 100%;
}

.discharge-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff;
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 500;
  padding: 6px 0;
}

.discharge-table :deep(.el-table__row td.el-table__cell) {
  font-size: 13px;
  padding: 6px 0;
}
</style>
