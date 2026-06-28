<script setup lang="ts">
import type { BedMatrixRowDto, WardTreeNodeDto } from '#/api/booking/bed-booking';
import type { BookingEntityId } from '#/api/booking/booking-id';
import { toBookingId } from '#/api/booking/booking-id';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { addDaysIso } from '#/api/booking/booking-constants';
import {
  cancelBedReservationApi,
  loadWardTreeApi,
  pageBedMatrixApi,
  reserveBedApi,
} from '#/api/booking/bed-booking';
import SelectTable from '#/components/select-table.vue';
import { pagePatientsForSelectApi } from '#/api/booking/booking-register';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTree,
} from 'element-plus';

defineOptions({ name: 'BookingBed' });

const { organizationId } = useBookingOrganization();
const wardTree = ref<WardTreeNodeDto[]>([]);
const selectedWardId = ref<BookingEntityId>();
const matrixDates = ref<string[]>([]);
const matrixRows = ref<BedMatrixRowDto[]>([]);
const reserveVisible = ref(false);
const reserveTarget = reactive({
  bedLocationId: '' as BookingEntityId,
  bedName: '',
  date: '',
});
const selectedPatientId = ref('');
const reserveComment = ref('');

const range = reactive({
  start: new Date().toISOString().slice(0, 10),
  end: addDaysIso(new Date().toISOString().slice(0, 10), 6),
});

const patientColumns = [
  { prop: 'name', label: '姓名', minWidth: 100 },
  { prop: 'patientBusNo', label: '患者号', minWidth: 120 },
  { prop: 'idCardNoMasked', label: '证件号', minWidth: 140 },
];

const { run: runTree, loading: treeLoading } = useRequest(loadWardTreeApi, {
  manual: true,
  onSuccess: (res) => {
    wardTree.value = res?.wards ?? [];
    if (!selectedWardId.value && wardTree.value.length > 0) {
      const first = toBookingId(wardTree.value[0]?.value);
      if (first) {
        selectedWardId.value = first;
        void loadMatrix();
      }
    }
  },
});

const { run: runMatrix, loading: matrixLoading } = useRequest(pageBedMatrixApi, {
  manual: true,
  onSuccess: (res) => {
    matrixDates.value = res?.dates ?? [];
    matrixRows.value = res?.rows ?? [];
  },
});

const { run: runReserve, loading: reserveLoading } = useRequest(reserveBedApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`床位预留成功：${res?.busNo ?? ''}`);
    reserveVisible.value = false;
    void loadMatrix();
  },
});

async function loadMatrix() {
  if (!organizationId.value || !selectedWardId.value) return;
  await runMatrix({
    organizationId: organizationId.value,
    wardLocationId: selectedWardId.value,
    rangeStart: range.start,
    rangeEnd: range.end,
  });
}

function handleWardClick(node: WardTreeNodeDto) {
  const id = toBookingId(node.value);
  if (!id) return;
  selectedWardId.value = id;
  void loadMatrix();
}

function cellClass(code?: string) {
  if (code === 'RESERVED') return 'is-reserved';
  if (code === 'IN_USE') return 'is-in-use';
  return 'is-free';
}

function cellLabel(cell: {
  displayStatus?: string;
  patientName?: string;
  operationalCode?: string;
}) {
  if (cell.patientName) return cell.patientName;
  return cell.displayStatus || cell.operationalCode || '空';
}

function openReserve(
  row: BedMatrixRowDto,
  date: string,
  cell: NonNullable<BedMatrixRowDto['cells']>[number],
) {
  if (cell.operationalCode !== 'NOT_IN_USE' && cell.operationalCode !== undefined) {
    if (cell.appointmentId) {
      void handleCancelReservation(toBookingId(cell.appointmentId)!);
    }
    return;
  }
  if (!row.bedLocationId) return;
  reserveTarget.bedLocationId = toBookingId(row.bedLocationId)!;
  reserveTarget.bedName = row.bedName ?? String(row.bedLocationId);
  reserveTarget.date = date;
  selectedPatientId.value = '';
  reserveComment.value = '';
  reserveVisible.value = true;
}

async function handleCancelReservation(appointmentId: BookingEntityId) {
  try {
    await cancelBedReservationApi({ appointmentId, reason: '窗口取消预留' });
    ElMessage.success('已取消床位预留');
    await loadMatrix();
  } catch {
    // handled by interceptor
  }
}

async function handleReserve() {
  if (!organizationId.value || !selectedPatientId.value) {
    ElMessage.warning('请选择患者');
    return;
  }
  await runReserve({
    organizationId: organizationId.value,
    bedLocationId: reserveTarget.bedLocationId,
    patientId: toBookingId(selectedPatientId.value)!,
    intendedAdmissionDate: reserveTarget.date,
    commentText: reserveComment.value || undefined,
  });
}

async function queryPatients({
  keyword = '',
  pageNo = 1,
  pageSize = 20,
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const data = await pagePatientsForSelectApi({ keyword, pageNo, pageSize });
  const { content, totalCount } = parsePagedRowsFromApiData(data);
  return {
    content: content.map((row) => ({
      ...row,
      value: String(row.patientId ?? ''),
      label: row.name,
    })),
    totalElements: totalCount,
  };
}

const treeProps = computed(() => ({ label: 'label', children: 'children' }));

onMounted(async () => {
  if (organizationId.value) {
    await runTree({ organizationId: organizationId.value });
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="bed-booking-page">
      <header class="bed-booking-page__toolbar">
        <ElDatePicker
          v-model="range.start"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="开始日期"
          style="width: 160px"
        />
        <ElDatePicker
          v-model="range.end"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束日期"
          style="width: 160px"
        />
        <ElButton type="primary" :loading="matrixLoading" @click="loadMatrix">查询</ElButton>
      </header>

      <div class="bed-booking-page__body">
        <aside v-loading="treeLoading" class="bed-booking-page__tree">
          <div class="bed-booking-page__tree-title">病区</div>
          <ElTree
            :data="wardTree"
            node-key="value"
            highlight-current
            :props="treeProps"
            @node-click="handleWardClick"
          />
        </aside>

        <div v-loading="matrixLoading" class="bed-booking-page__matrix-wrap">
          <table v-if="matrixRows.length" class="bed-booking-page__matrix">
            <thead>
              <tr>
                <th>床位</th>
                <th v-for="date in matrixDates" :key="date">{{ date.slice(5) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in matrixRows" :key="row.bedLocationId">
                <td class="bed-booking-page__bed-name">{{ row.bedName }}</td>
                <td
                  v-for="cell in row.cells"
                  :key="`${row.bedLocationId}-${cell.date}`"
                  class="bed-booking-page__cell"
                  :class="cellClass(cell.operationalCode)"
                  @click="openReserve(row, cell.date ?? '', cell)"
                >
                  {{ cellLabel(cell) }}
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="bed-booking-page__empty">请选择病区并查询床位矩阵</div>
        </div>
      </div>

      <ElDialog v-model="reserveVisible" title="预留床位" width="480px">
        <ElForm label-width="96px">
          <ElFormItem label="床位">
            <span>{{ reserveTarget.bedName }} · {{ reserveTarget.date }}</span>
          </ElFormItem>
          <ElFormItem label="患者" required>
            <SelectTable
              v-model="selectedPatientId"
              :columns="patientColumns"
              value-key="value"
              label-key="label"
              placeholder="搜索患者"
              :remote-method="queryPatients"
            />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="reserveComment" type="textarea" :rows="2" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="reserveVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="reserveLoading" @click="handleReserve"
            >确认预留</ElButton
          >
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.bed-booking-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.bed-booking-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.bed-booking-page__body {
  display: grid;
  flex: 1;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.bed-booking-page__tree {
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.bed-booking-page__tree-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.bed-booking-page__matrix-wrap {
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.bed-booking-page__matrix {
  width: 100%;
  border-collapse: collapse;
}

.bed-booking-page__matrix th,
.bed-booking-page__matrix td {
  padding: 8px 10px;
  text-align: center;
  border: 1px solid var(--el-border-color-lighter);
}

.bed-booking-page__bed-name {
  font-weight: 600;
  white-space: nowrap;
}

.bed-booking-page__cell {
  min-width: 88px;
  cursor: pointer;
  font-size: 12px;
}

.bed-booking-page__cell.is-free {
  background: var(--el-bg-color);
}

.bed-booking-page__cell.is-reserved {
  background: var(--el-color-warning-light-9);
}

.bed-booking-page__cell.is-in-use {
  background: var(--el-color-danger-light-9);
}

.bed-booking-page__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--el-text-color-placeholder);
}
</style>
