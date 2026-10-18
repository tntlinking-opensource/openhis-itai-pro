<script setup lang="ts">
import type {
  InpatientReturnMedicineDetailRow,
  InpatientReturnMedicineQueuePatient,
} from './components/types';

import type { InpatientReturnMedicineDetailRowDto } from '#/api/inpatient-pharmacy-return-medicine/inpatient-pharmacy-return-medicine.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
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
import { MSFX_HIS_SCENE } from '#/msfx/constants';
import { parseListItemsFromApiData } from '#/utils/api/unwrap-list-response';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  readInpatientReturnAutoPrintPreference,
  writeInpatientReturnAutoPrintPreference,
} from '#/utils/medication/auto-print-inpatient-preference';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import { PRINT_SCENARIO, print } from '#/utils/print/runtime';

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
const printLoading = ref(false);
/** 确认退药成功后是否自动打印本次退药单（按登录用户缓存在本机）。 */
const autoPrintEnabled = ref(readInpatientReturnAutoPrintPreference());
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

/** 自动打印开关变更：写入本机缓存，并提示开/关后的退药行为 */
watch(autoPrintEnabled, (enabled) => {
  writeInpatientReturnAutoPrintPreference(enabled);
  if (enabled) {
    ElMessage.success('已开启退药后自动打印退药单：确认退药成功后将自动打印本次退药单');
    return;
  }
  ElMessage.info('已关闭退药后自动打印退药单：退药成功后需手工打印');
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

/**
 * 打印当前患者一张退药单。
 * @param rows 待打明细
 * @param options.background 自动打印时失败不打断退药成功
 * @param options.refunded 确认退药后打单，状态展示为已退药
 */
async function printReturnSlip(
  rows: InpatientReturnMedicineDetailRow[],
  options?: { background?: boolean; refunded?: boolean },
): Promise<boolean> {
  const patient = selectedPatient.value;
  if (!patient) {
    if (!options?.background) {
      ElMessage.warning('请先选择退药患者');
    }
    return false;
  }
  if (rows.length === 0) {
    if (!options?.background) {
      ElMessage.warning(
        refundQueueStatus.value === 'PENDING_REFUND' ? '请先勾选要打印的退药明细' : '暂无已退药明细',
      );
    }
    return false;
  }
  const gender = patient.genderEnumText.trim();
  const age = patient.ageLabel.trim();
  const genderAgeText = [gender, age].filter((part) => part && part !== '—').join(' / ');
  const statusText = options?.refunded ? '已退药' : patient.refundEnumText;
  const result = await print({
    scenarioCode: PRINT_SCENARIO.ipReturnMedicinePrint,
    jobName: `住院退药单-${patient.patientName}`,
    background: options?.background === true,
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle: '住院退药单',
      patientName: patient.patientName,
      genderAgeText,
      hospitalizationNo: patient.hospitalizationNo,
      wardName: patient.wardName,
      bedName: patient.bedName,
      feeNatureText: patient.feeNatureLabel?.trim() || '自费',
      statusText,
      printedAtText: dayjs().format('YYYY-MM-DD HH:mm'),
      drugRows: rows.map((row, index) => ({
        seqText: String(index + 1),
        drugName: row.itemName,
        lotNumber: row.lotNumber,
        quantityText: row.quantity,
        unitText: row.unitText,
        doctorName: row.doctorName,
        statusText: options?.refunded ? '已退药' : row.refundEnumText,
      })),
    },
    bizRef: {
      encounterId: patient.id,
      hospitalizationNo: patient.hospitalizationNo,
      refundStatus: options?.refunded ? 'REFUNDED' : refundQueueStatus.value,
    },
  });
  return result.ok;
}

/** 当前患者一张退药单：待退药打勾选行，已退药打当前列表 */
async function handlePrintReturn() {
  const rows =
    refundQueueStatus.value === 'PENDING_REFUND'
      ? (detailCenterRef.value?.getSelectedDetailRows() ?? [])
      : detailRows.value;
  printLoading.value = true;
  try {
    const ok = await printReturnSlip(rows);
    if (ok) {
      ElMessage.success('退药单已发送打印');
    }
  } finally {
    printLoading.value = false;
  }
}

/**
 * 确认退药成功后按开关自动打印本次退药单。
 * 打印失败只软提示，不回滚已成功退药。
 */
async function tryAutoPrintReturn(rows: InpatientReturnMedicineDetailRow[]): Promise<void> {
  if (!autoPrintEnabled.value) {
    return;
  }
  if (rows.length === 0) {
    ElMessage.warning('退药成功，但未找到可自动打印的退药明细');
    return;
  }
  try {
    const ok = await printReturnSlip(rows, { background: true, refunded: true });
    if (ok) {
      ElMessage.success('已自动打印退药单');
      return;
    }
    ElMessage.warning('退药成功，但退药单未自动打印，请稍后手工打印');
  } catch {
    ElMessage.warning('退药成功，但退药单未自动打印，请稍后手工打印');
  }
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
  const rowsToPrint = [...(detailCenterRef.value?.getSelectedDetailRows() ?? [])];
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
    await tryAutoPrintReturn(rowsToPrint);
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
          v-model:auto-print-enabled="autoPrintEnabled"
          :action-disabled="submitLoading"
          :detail-loading="detailLoading"
          :detail-rows="detailRows"
          :print-loading="printLoading"
          :refund-queue-status="refundQueueStatus"
          :selected-patient="selectedPatient"
          :msfx-his-scene="MSFX_HIS_SCENE.IP_RETURN"
          @confirm-return="handleConfirmReturn"
          @print-return="handlePrintReturn"
        />
      </template>
    </StationTriPaneLayout>
  </div>
</template>
