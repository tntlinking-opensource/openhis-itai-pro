<script setup lang="ts">
import type { HcBookableDayDto, HcBookableSlotDto } from '#/api/booking/health-checkup-slot-occupy';
import type { HealthCheckPackageDto } from '#/api/booking/health-checkup-booking';
import type { ContractRowDto } from '#/api/peis/group-check';

import { computed, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';

import { toBookingId } from '#/api/booking/booking-id';
import { pagePatientsForSelectApi } from '#/api/booking/booking-register';
import { listIndividualPackagesApi } from '#/api/booking/health-checkup-booking';
import {
  createHcSlotBookingApi,
  listBookableDaysApi,
  listBookableSlotsApi,
} from '#/api/booking/health-checkup-slot-occupy';
import { listValidContractsForPatientApi } from '#/api/peis/group-check';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

defineOptions({ name: 'BookingHealthCheckupRegister' });

const WALK_IN_TYPE = 'WALK_IN';

const { organizationId } = useBookingOrganization({ fallbackToHospital: true });
const selectedPatientId = ref('');
const validContracts = ref<ContractRowDto[]>([]);
const selectedContractId = ref('');
const contractTypeCode = ref(WALK_IN_TYPE);
const packages = ref<HealthCheckPackageDto[]>([]);
const selectedPackageId = ref<number>();
const selectedCampusCode = ref('');
const bookableDays = ref<HcBookableDayDto[]>([]);
const selectedPlanDate = ref('');
const bookableSlots = ref<HcBookableSlotDto[]>([]);
const selectedDaySlotId = ref<number>();

const patientColumns = [
  { prop: 'name', label: '姓名', minWidth: 100 },
  { prop: 'patientBusNo', label: '患者号', minWidth: 120 },
];

const showContractSelect = computed(() => validContracts.value.length > 1);
const showCampusSelect = computed(() => bookableSlots.value.some((slot) => !!slot.campusCode));

const campusOptions = computed(() => {
  const codes = new Set<string>();
  for (const slot of bookableSlots.value) {
    if (slot.campusCode) codes.add(slot.campusCode);
  }
  return [...codes];
});

const filteredSlots = computed(() => {
  if (!selectedCampusCode.value) return bookableSlots.value;
  return bookableSlots.value.filter(
    (slot) => !slot.campusCode || slot.campusCode === selectedCampusCode.value,
  );
});

const selectedContract = computed(() =>
  validContracts.value.find((row) => String(row.contractId ?? '') === selectedContractId.value),
);

const packageOptions = computed(() => {
  const contract = selectedContract.value;
  if (!contract?.packageActivityIds?.length && !contract?.packageActivityId) {
    return packages.value;
  }
  const allowed = new Set(
    (
      contract.packageActivityIds ??
      (contract.packageActivityId ? [contract.packageActivityId] : [])
    ).map((id) => String(id)),
  );
  return packages.value.filter((pkg) => allowed.has(String(pkg.packageId ?? '')));
});

const { run: runBook, loading: bookLoading } = useRequest(createHcSlotBookingApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`预约成功：${res?.busNo ?? ''}`);
    resetBookingSelection();
  },
});

/** 重置日期/时段选择（保留患者与合同上下文）。 */
function resetBookingSelection() {
  selectedPlanDate.value = '';
  bookableSlots.value = [];
  selectedDaySlotId.value = undefined;
  selectedCampusCode.value = '';
}

/** 根据有效合同数量决定进池类型与合同主键。 */
function applyContractContext(contracts: ContractRowDto[]) {
  if (contracts.length === 0) {
    selectedContractId.value = '';
    contractTypeCode.value = WALK_IN_TYPE;
    return;
  }
  if (contracts.length === 1) {
    const only = contracts[0]!;
    selectedContractId.value = String(only.contractId ?? '');
    contractTypeCode.value = only.contractTypeCode ?? WALK_IN_TYPE;
    return;
  }
  selectedContractId.value = '';
  contractTypeCode.value = WALK_IN_TYPE;
}

/** 患者选中后加载有效合同并推断进池类型。 */
async function handlePatientSelected(patientId: string) {
  resetBookingSelection();
  validContracts.value = [];
  selectedContractId.value = '';
  contractTypeCode.value = WALK_IN_TYPE;
  selectedPackageId.value = undefined;
  if (!patientId) return;
  const rows = (await listValidContractsForPatientApi({ patientId })) ?? [];
  validContracts.value = rows;
  applyContractContext(rows);
  await loadPackages();
}

/** 手选合同时同步合同类型。 */
function handleContractChange(contractId: string) {
  const hit = validContracts.value.find((row) => String(row.contractId ?? '') === contractId);
  contractTypeCode.value = hit?.contractTypeCode ?? WALK_IN_TYPE;
  selectedPackageId.value = undefined;
  resetBookingSelection();
  void loadBookableDays();
}

async function loadPackages() {
  if (!organizationId.value) return;
  const res = await listIndividualPackagesApi({ organizationId: organizationId.value });
  packages.value = res?.packages ?? [];
  if (packageOptions.value.length === 1) {
    selectedPackageId.value = packageOptions.value[0]?.packageId;
  }
}

/** 加载未来 60 天可约日期。 */
async function loadBookableDays() {
  if (!organizationId.value || !contractTypeCode.value) return;
  const fromDate = new Date().toISOString().slice(0, 10);
  const to = new Date();
  to.setDate(to.getDate() + 60);
  const toDate = to.toISOString().slice(0, 10);
  const res = await listBookableDaysApi({
    organizationId: organizationId.value,
    contractTypeCode: contractTypeCode.value,
    campusCode: selectedCampusCode.value || undefined,
    fromDate,
    toDate,
  });
  bookableDays.value = (res?.days ?? []).filter((row) => (row.remaining ?? 0) > 0);
  if (!bookableDays.value.some((row) => row.planDate === selectedPlanDate.value)) {
    selectedPlanDate.value = '';
    bookableSlots.value = [];
    selectedDaySlotId.value = undefined;
  }
}

async function loadBookableSlots() {
  if (!organizationId.value || !selectedPlanDate.value || !contractTypeCode.value) return;
  const res = await listBookableSlotsApi({
    organizationId: organizationId.value,
    planDate: selectedPlanDate.value,
    contractTypeCode: contractTypeCode.value,
    campusCode: selectedCampusCode.value || undefined,
  });
  bookableSlots.value = (res?.slots ?? []).filter((row) => (row.remaining ?? 0) > 0);
  selectedDaySlotId.value = undefined;
}

async function handleConfirmBooking() {
  if (!organizationId.value || !selectedPatientId.value || !selectedPackageId.value) {
    ElMessage.warning('请选择患者与套餐');
    return;
  }
  if (!selectedDaySlotId.value) {
    ElMessage.warning('请选择预约时段');
    return;
  }
  await runBook({
    organizationId: organizationId.value,
    patientId: toBookingId(selectedPatientId.value)!,
    packageId: toBookingId(selectedPackageId.value)!,
    daySlotId: toBookingId(selectedDaySlotId.value)!,
    contractId: selectedContractId.value ? toBookingId(selectedContractId.value) : undefined,
    contractTypeCode: contractTypeCode.value,
    originChannel: 'WINDOW',
  });
}

/** 远程搜索患者（SelectTable）。 */
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

function formatTimeLabel(value?: string) {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

watch(selectedPatientId, (id) => {
  void handlePatientSelected(id);
});

watch([contractTypeCode, selectedCampusCode], () => {
  void loadBookableDays();
});

watch(selectedPlanDate, () => {
  void loadBookableSlots();
});

watch(selectedPackageId, () => {
  if (selectedPackageId.value) {
    void loadBookableDays();
  }
});

watch(organizationId, () => {
  if (organizationId.value && selectedPatientId.value) {
    void loadPackages();
  }
});
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never" class="hc-register-page">
      <ElForm label-width="96px" class="hc-register-page__form">
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

        <ElFormItem v-if="showContractSelect" label="团检合同" required>
          <ElSelect
            v-model="selectedContractId"
            placeholder="选择有效合同"
            style="width: 320px"
            @change="handleContractChange"
          >
            <ElOption
              v-for="item in validContracts"
              :key="item.contractId"
              :label="item.contractName || item.contractId"
              :value="String(item.contractId ?? '')"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-else-if="validContracts.length === 1" label="团检合同">
          <span>{{ validContracts[0]?.contractName ?? '—' }}</span>
        </ElFormItem>
        <ElFormItem v-else-if="selectedPatientId" label="进池类型">
          <span>散客（WALK_IN）</span>
        </ElFormItem>

        <ElFormItem label="体检套餐" required>
          <ElSelect
            v-model="selectedPackageId"
            filterable
            placeholder="选择套餐"
            style="width: 320px"
            :disabled="!selectedPatientId"
          >
            <ElOption
              v-for="pkg in packageOptions"
              :key="pkg.packageId"
              :label="pkg.packageName"
              :value="pkg.packageId!"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem v-if="showCampusSelect" label="校区">
          <ElSelect
            v-model="selectedCampusCode"
            clearable
            placeholder="选择校区"
            style="width: 200px"
          >
            <ElOption v-for="code in campusOptions" :key="code" :label="code" :value="code" />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="预约日期" required>
          <ElSelect
            v-model="selectedPlanDate"
            placeholder="选择可约日期"
            style="width: 200px"
            :disabled="!selectedPackageId"
          >
            <ElOption
              v-for="day in bookableDays"
              :key="day.planDate"
              :label="`${day.planDate}（余 ${day.remaining ?? 0}）`"
              :value="day.planDate!"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="预约时段" required>
          <div class="hc-register-page__slots">
            <button
              v-for="slot in filteredSlots"
              :key="slot.daySlotId"
              type="button"
              class="hc-register-page__slot"
              :class="{ 'is-selected': selectedDaySlotId === slot.daySlotId }"
              @click="selectedDaySlotId = slot.daySlotId"
            >
              <span
                >{{ formatTimeLabel(slot.startTime) }} - {{ formatTimeLabel(slot.endTime) }}</span
              >
              <span v-if="slot.campusCode"> {{ slot.campusCode }}</span>
              <span>余 {{ slot.remaining ?? 0 }}</span>
            </button>
          </div>
        </ElFormItem>

        <ElFormItem>
          <ElButton
            type="primary"
            :loading="bookLoading"
            :disabled="!selectedPatientId"
            @click="handleConfirmBooking"
          >
            确认预约
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </Page>
</template>

<style scoped>
.hc-register-page {
  max-width: 720px;
}

.hc-register-page__form {
  margin-top: 8px;
}

.hc-register-page__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 48px;
}

.hc-register-page__slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 108px;
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.hc-register-page__slot.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
</style>
