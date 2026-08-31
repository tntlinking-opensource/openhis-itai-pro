<script setup lang="ts">
import type {
  InpatientReturnMedicineDetailRow,
  InpatientReturnMedicineQueuePatient,
} from './components/types';

import type { InpatientReturnMedicineDetailRowDto } from '#/api/inpatient-pharmacy-return-medicine/inpatient-pharmacy-return-medicine.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';

import {
  httpConfirmInpatientMedicineReturn,
  httpGetInpatientReturnMedicineDetail,
  httpLoadInpatientReturnMedicineInit,
  httpPageInpatientReturnPatientQueue,
} from '#/api/inpatient-pharmacy-return-medicine/inpatient-pharmacy-return-medicine.http';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
  type ChargeType,
} from '#/chs/constants/insurance-contract';
import { parseListItemsFromApiData } from '#/utils/api/unwrap-list-response';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { MSFX_HIS_SCENE } from '#/msfx/constants';

import InpatientReturnMedicineDetailCenter from './components/inpatient-return-medicine-detail-center.vue';
import InpatientReturnMedicineQueuePanel from './components/inpatient-return-medicine-queue-panel.vue';
import {
  mapApiOptionItems,
  mapInpatientReturnDetailRowToUi,
  mapInpatientReturnPatientRowToQueuePatient,
} from './services/map-api-to-return-ui';

defineOptions({
  name: 'PharmacyReturnMedicineInpatient',
});

const keyword = ref('');
const queueScope = ref<'completed' | 'pending'>('pending');
const selectedPatientId = ref<null | string>(null);
const selectedWardId = ref<string | undefined>(undefined);

const detailCenterRef = ref<InstanceType<typeof InpatientReturnMedicineDetailCenter> | null>(null);

const queuePatients = ref<InpatientReturnMedicineQueuePatient[]>([]);
const wardOptions = ref<{ label: string; value: string }[]>([]);

const refundQueueStatus = computed(() =>
  queueScope.value === 'pending' ? 'PENDING_REFUND' : 'REFUNDED',
);
const queueTab = computed(() => (queueScope.value === 'pending' ? 'PENDING' : 'COMPLETED'));

const detailRows = ref<InpatientReturnMedicineDetailRow[]>([]);

const queueLoading = ref(false);
const detailLoading = ref(false);
const initLoading = ref(false);
const submitLoading = ref(false);
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const selectedPatient = computed(() => {
  const id = selectedPatientId.value;
  if (!id) return null;
  return queuePatients.value.find((p) => p.id === id) ?? null;
});

/** 进入页时拉初始化元数据 */
async function loadPageInit() {
  initLoading.value = true;
  try {
    const res = await httpLoadInpatientReturnMedicineInit({ pageNo: 1, pageSize: 10 });
    wardOptions.value = mapApiOptionItems(res.wardOptions);
  } catch {
    wardOptions.value = [];
  } finally {
    initLoading.value = false;
  }
}

/** 按 Tab、病区与关键字拉取退药患者队列 */
async function reloadQueue() {
  queueLoading.value = true;
  try {
    const res = await httpPageInpatientReturnPatientQueue({
      wardId: preserveApiIdForRequest(selectedWardId.value) ?? undefined,
      queueTab: queueTab.value,
      keyword: keyword.value.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const { content: rows } = parsePagedRowsFromApiData(res);
    queuePatients.value = rows.map((r) => mapInpatientReturnPatientRowToQueuePatient(r));
  } catch {
    queuePatients.value = [];
  } finally {
    queueLoading.value = false;
  }
}

const debouncedReloadQueue = useDebounceFn(() => {
  void reloadQueue();
}, 400);

/** 查询当前就诊药品退药明细 */
async function reloadDetail() {
  const enc = selectedPatientId.value;
  if (!enc) {
    detailRows.value = [];
    return;
  }
  detailLoading.value = true;
  try {
    const res = await httpGetInpatientReturnMedicineDetail({
      encounterId: preserveApiIdForRequest(enc) ?? enc,
      refundStatus: refundQueueStatus.value,
    });
    const items = parseListItemsFromApiData<InpatientReturnMedicineDetailRowDto>(res);
    detailRows.value = items.map((row) => mapInpatientReturnDetailRowToUi(row));
  } catch {
    detailRows.value = [];
    ElMessage.warning('退药明细加载失败，请刷新后重试');
  } finally {
    detailLoading.value = false;
  }
}

watch(queuePatients, (list) => {
  const sid = selectedPatientId.value;
  if (!sid) return;
  if (!list.some((p) => p.id === sid)) {
    selectedPatientId.value = null;
  }
});

watch([selectedPatientId, queueScope], () => {
  void reloadDetail();
});

watch([queueScope, selectedWardId], () => {
  void reloadQueue();
});

watch(keyword, () => {
  debouncedReloadQueue();
});

watch(queueScope, () => {
  selectedPatientId.value = null;
  detailRows.value = [];
});

/** 选中左侧患者卡片 */
function handleQueueCardSelect(patient: { id: string }) {
  selectedPatientId.value = patient.id;
  void reloadDetail();
}

/** 勾选行 → 提交 items（仅 dispenseId） */
function buildSubmitItems() {
  const selected = detailCenterRef.value?.getSelectedDetailRows() ?? [];
  return selected.map((row) => ({
    dispenseId: preserveApiIdForRequest(row.dispenseId) ?? row.dispenseId,
  }));
}

/** 确认退药（reason 选填） */
async function handleConfirmReturn() {
  const items = buildSubmitItems();
  if (items.length === 0) {
    ElMessage.warning('请先勾选待退药明细');
    return;
  }
  try {
    await ElMessageBox.confirm('确认对所选药品执行退药并回补库存？', '确认退药', {
      type: 'warning',
    });
  } catch {
    return;
  }
  submitLoading.value = true;
  try {
    const res = await httpConfirmInpatientMedicineReturn({
      items,
      contractBusNo: insuranceContractBusNo.value,
    });
    const message = res.message?.trim() || '退药成功';
    if (message.includes('EX-OPP-207')) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    await reloadDetail();
    await reloadQueue();
    await nextTick(() => {
      detailCenterRef.value?.clearDetailSelection();
    });
  } catch {
    /* 拦截器提示 */
  } finally {
    submitLoading.value = false;
  }
}

onMounted(async () => {
  await loadPageInit();
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(eligible);
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
  await reloadQueue();
});
</script>

<template>
  <div
    v-loading="initLoading"
    class="pharmacy-return-medicine-inpatient-root flex h-[calc(100vh-120px)] min-h-[480px] w-full min-w-0 flex-col overflow-hidden"
  >
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="pharmacy-return-medicine-inpatient min-h-0 min-w-0 flex-1"
      left-track="minmax(228px, 260px)"
      left-track-lg="minmax(228px, 260px)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div class="flex h-full min-h-0 flex-col">
          <div
            v-if="insuranceChargeTypeOptions.length > 0"
            class="border-b border-solid border-[var(--el-border-color-lighter)] px-2 pb-2"
          >
            <div class="flex min-w-0 items-center gap-1.5">
              <span class="shrink-0 text-xs text-[var(--el-text-color-secondary)]">医保契约</span>
              <ElSelect
                v-model="insuranceContractBusNo"
                class="!min-w-0 flex-1"
                placeholder="请选择医保契约"
                filterable
                clearable
              >
                <ElOption
                  v-for="item in insuranceChargeTypeOptions"
                  :key="item.busNo"
                  :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                  :value="item.busNo"
                />
              </ElSelect>
            </div>
          </div>
          <div class="min-h-0 flex-1 overflow-hidden">
            <InpatientReturnMedicineQueuePanel
              v-model:keyword="keyword"
              v-model:queue-scope="queueScope"
              v-model:selected-ward-id="selectedWardId"
              :filtered-patients="queuePatients"
              :loading-queue="queueLoading"
              :selected-patient-id="selectedPatientId"
              :ward-options="wardOptions"
              @refresh="reloadQueue"
              @select-patient="handleQueueCardSelect"
            />
          </div>
        </div>
      </template>

      <template #center>
        <InpatientReturnMedicineDetailCenter
          ref="detailCenterRef"
          :action-disabled="submitLoading"
          :detail-loading="detailLoading"
          :detail-rows="detailRows"
          :refund-queue-status="refundQueueStatus"
          :selected-patient="selectedPatient"
          :msfx-his-scene="MSFX_HIS_SCENE.IP_RETURN"
          @confirm-return="handleConfirmReturn"
        />
      </template>
    </StationTriPaneLayout>
  </div>
</template>
