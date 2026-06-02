<!-- 药库损益单：盘盈盘亏/报损等；工作站与采购申请页同构，右侧明细使用 WarehouseOrderDetailEdit（VXE T5）。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Check, Delete, Plus, Search } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  agreeGainLossApprovalApi,
  deleteGainLossApi,
  initGainLossApi,
  pageGainLossDetailApi,
  pageGainLossListApi,
} from '#/api/warehouse/gain-loss';
import GainLossOrderDialog from '#/components/warehouse/gain-loss-order-dialog.vue';
import WarehouseDocOrderQueue from '#/components/warehouse/warehouse-doc-order-queue.vue';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import WarehouseOrderDetailEdit from '#/components/warehouse/warehouse-order-detail-edit.vue';
import { isGainLossApproved, resolveGainLossBusNo } from '#/utils/warehouse/gain-loss-doc';

defineOptions({
  name: 'WarehouseGainLoss',
});

const route = useRoute();
const userStore = useUserStore();

function resolveApplicantId(): number | string | undefined {
  const u = userStore.userInfo as null | Record<string, any>;
  if (!u) return undefined;
  const ext = u.extraProperties ?? {};
  return u.practitionerId ?? ext.PractitionerId ?? ext.practitionerId;
}

function formatQueueApplyTime(time?: unknown): string {
  if (time === undefined || time === null || time === '') return '';
  return formatDateTime(time as Date | number | string);
}

const queryParams = ref<{
  locationId?: number | string;
  keyword?: string;
}>({
  keyword: '',
});

const orderTableRef = ref<null | { resetAllData: () => void }>(null);
const orderDialogRef = ref<null | {
  getOrderList: (locationId?: number | string) => Promise<void>;
}>(null);

const tableForm = ref<Record<string, any>>({
  locationId: undefined,
  busNo: undefined,
  applyTime: undefined,
  applicantId: undefined,
  reason: undefined,
  categoryEnum: undefined,
  supplierId: undefined,
});

const tableData = ref<Record<string, any>[]>([]);

const auditForm = ref<Record<string, any>>({
  approvalTime: undefined,
  statusEnum: undefined,
  statusEnum_enumText: undefined,
  approverId: undefined,
});

const isAddOrEditOrder = ref({
  isAddOrder: false,
  isEditOrder: false,
});

const cabinetListOptions = ref<Record<string, any>[]>([]);
const practitionerOption = ref<ApiOptionItem[]>([]);
const supplierOption = ref<ApiOptionItem[]>([]);
const supplyStatusOptions = ref<ApiOptionItem[]>([]);
const supplyTypeOptions = ref<ApiOptionItem[]>([]);
const supplyCategoryOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, any>[]>([]);
const selectedOrder = ref<null | Record<string, any>>(null);

const GAIN_LOSS_DETAIL_PAGE_SIZE = 2000;

const isAddDisabled = ref(false);
const isEditDisabled = ref(true);
const isCancelDisabled = ref(true);
const isDeleteDisabled = ref(true);
const isAuditDisabled = ref(true);

const buttonShow = ref({
  isAddShow: false,
  isDeleteShow: false,
  isSaveShow: false,
  isEditShow: false,
  isAlertShow: false,
  isOrderImport: false,
});

const dialogVisible = ref(false);

const rules = ref({
  locationId: [{ required: true, message: '请选择库房', trigger: 'blur' }],
});

async function init() {
  try {
    const data = await initGainLossApi();
    cabinetListOptions.value = data.cabinetListOptions ?? [];
    practitionerOption.value = data.practitionerOption ?? [];
    supplierOption.value = data.supplierOption ?? [];
    supplyStatusOptions.value = data.supplyStatusOptions ?? [];
    supplyTypeOptions.value = data.supplyTypeOptions ?? [];
    supplyCategoryOptions.value = data.supplyCategoryOptions ?? [];
  } catch {
    cabinetListOptions.value = [];
    practitionerOption.value = [];
    supplierOption.value = [];
    supplyStatusOptions.value = [];
    supplyTypeOptions.value = [];
    supplyCategoryOptions.value = [];
  }
}

async function getList() {
  try {
    const data = await pageGainLossListApi(queryParams.value);
    const records = data.records ?? [];
    orderList.value = records.map((r) => ({
      ...r,
      supplyBusNo: resolveGainLossBusNo(r),
    }));
  } catch {
    orderList.value = [];
  }
}

function handleAdd() {
  resetFormAndTableData();
  if (!tableForm.value.locationId) {
    ElMessage.warning('新增单据前，请选择库房');
    return;
  }

  tableForm.value = {
    applicantId: resolveApplicantId(),
    applyTime: formatDateTime(new Date()),
    locationId: tableForm.value.locationId,
  };

  isAddOrEditOrder.value = {
    isAddOrder: true,
    isEditOrder: false,
  };

  nextTick(() => {
    handleUpdateButtonState({
      isAuditDisabled: true,
      isCancelDisabled: false,
      isDeleteDisabled: true,
      isAddDisabled: true,
      isEditDisabled: true,
      isAddShow: true,
      isDeleteShow: false,
      isSaveShow: false,
      isEditShow: false,
      isAlertShow: false,
      isOrderImport: true,
    });
  });
}

function resetFormAndTableData() {
  tableData.value = [];
  auditForm.value = {};
}

function buildUnitList(item: Record<string, any>) {
  (item.unitList || []).forEach((unit: Record<string, any>) => {
    const isMaxUnit = unit.value === item.maxUnitCode;
    const isMinUnit = unit.value === item.minUnitCode;

    unit.priceMask = isMaxUnit
      ? item.price
      : item.price / Number.parseInt(String(item.partPercent), 10);

    unit.retailPriceMask = isMaxUnit
      ? item.retailPrice
      : item.retailPrice / Number.parseInt(String(item.partPercent), 10);

    unit.totalPriceMask = (
      Number.parseFloat(item.price) * Number.parseFloat(item.itemQuantity)
    ).toFixed(2);

    unit.totalRetailPriceMask = (
      Number.parseFloat(item.retailPrice) * Number.parseFloat(item.itemQuantity)
    ).toFixed(2);

    unit.specificationInventoryOriginal = item.specificationInventory;

    if (isMaxUnit) {
      unit.specificationInventoryMax = Math.floor(
        item.specificationInventory / Number.parseInt(String(item.partPercent), 10),
      );
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.maxUnitCode_dictText : '';
      unit.specificationInventoryMin =
        item.specificationInventory % Number.parseInt(String(item.partPercent), 10);
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else if (isMinUnit) {
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else {
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    }
  });
}

function buildEditForm(data: Record<string, any>[]) {
  const head = data[0];
  if (!head) return;

  tableForm.value = {
    busNo: head.busNo ?? head.supplyBusNo,
    applyTime: head.applyTime,
    supplierId: head.supplierId,
    applicantId: head.applicantId,
    categoryEnum: head.categoryEnum,
    reason: head.reason,
    locationId: head.purposeLocationId ?? head.locationId,
  };
  data.forEach((item) => {
    item.isEditing = false;
    item.isViewing = true;
    buildUnitList(item);
  });
  tableData.value = data;
  auditForm.value = {
    approvalTime: head.approvalTime,
    statusEnum: head.statusEnum,
    statusEnum_enumText: head.statusEnum_enumText,
    approverId: head.approverId,
  };
  isAddOrEditOrder.value = {
    isAddOrder: false,
    isEditOrder: true,
  };
}

async function handleEdit(order?: null | Record<string, any>) {
  resetFormAndTableData();

  const row = order ?? selectedOrder.value;
  const busNo = resolveGainLossBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  try {
    const detail = await pageGainLossDetailApi({
      busNo,
      pageNo: 1,
      pageSize: GAIN_LOSS_DETAIL_PAGE_SIZE,
    });
    buildEditForm(detail.records ?? []);
  } catch {
    return;
  }

  const isApproved = isGainLossApproved(row);
  if (isApproved) {
    isAddOrEditOrder.value = {
      isAddOrder: false,
      isEditOrder: false,
    };
    handleUpdateButtonState({
      isAuditDisabled: true,
      isCancelDisabled: true,
      isDeleteDisabled: true,
      isAddDisabled: false,
      isEditDisabled: true,
      isAddShow: false,
      isDeleteShow: false,
      isSaveShow: false,
      isEditShow: false,
      isAlertShow: false,
      isOrderImport: false,
    });
    return;
  }

  isAddOrEditOrder.value = {
    isAddOrder: false,
    isEditOrder: true,
  };

  handleUpdateButtonState({
    isAuditDisabled: isApproved,
    isCancelDisabled: false,
    isDeleteDisabled: isApproved,
    isAddDisabled: false,
    isEditDisabled: true,
    isAddShow: !isApproved,
    isDeleteShow: !isApproved,
    isSaveShow: !isApproved,
    isEditShow: !isApproved,
    isAlertShow: false,
    isOrderImport: !isApproved,
  });
}

function handleCancel() {
  handleUpdateButtonState({
    isAuditDisabled: true,
    isCancelDisabled: true,
    isDeleteDisabled: true,
    isAddDisabled: false,
    isEditDisabled: true,
    isAddShow: false,
    isDeleteShow: false,
    isSaveShow: false,
    isEditShow: false,
    isAlertShow: false,
    isOrderImport: false,
  });
  clearForm();
}

function clearForm() {
  const locationId = tableForm.value.locationId;
  const keepLocation = locationId !== undefined && locationId !== null && `${locationId}` !== '';
  tableForm.value = keepLocation ? { locationId } : {};
  tableData.value = [];
  auditForm.value = {};
  orderTableRef.value?.resetAllData();
  isAddOrEditOrder.value = {
    isAddOrder: false,
    isEditOrder: false,
  };
}

async function handleDelete() {
  if (!selectedOrder.value) {
    ElMessage.error('请选择要删除的单据');
    return;
  }

  if (isGainLossApproved(selectedOrder.value)) {
    ElMessage.error('已审批的单据不能删除');
    return;
  }

  try {
    await ElMessageBox.confirm('确认删除该损益单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const deletedBusNo = resolveGainLossBusNo(selectedOrder.value);
    if (!deletedBusNo) {
      ElMessage.error('单据号缺失，无法删除');
      return;
    }
    await deleteGainLossApi(deletedBusNo);
    ElMessage.success('删除成功');
    selectedOrder.value = null;
    clearForm();
    await getList();
    handleUpdateButtonState({
      isAuditDisabled: true,
      isCancelDisabled: true,
      isDeleteDisabled: true,
      isAddDisabled: false,
      isEditDisabled: true,
      isAddShow: false,
      isDeleteShow: false,
      isSaveShow: false,
      isEditShow: false,
      isAlertShow: false,
      isOrderImport: false,
    });
  } catch {
    /* 用户取消 */
  }
}

async function handleAudit() {
  if (!selectedOrder.value) {
    ElMessage.error('请选择要审核的单据');
    return;
  }

  if (isGainLossApproved(selectedOrder.value)) {
    ElMessage.error('该单据已审批，不能重复审批');
    return;
  }

  try {
    await ElMessageBox.confirm('确认同意该损益单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const auditedBusNo = resolveGainLossBusNo(selectedOrder.value);
    if (!auditedBusNo) {
      ElMessage.error('单据号缺失，无法审核');
      return;
    }
    await agreeGainLossApprovalApi(auditedBusNo);
    ElMessage.success('同意审批成功');
    await getList();
    const refreshed = orderList.value.find((o) => resolveGainLossBusNo(o) === auditedBusNo);
    selectedOrder.value = refreshed ?? null;
    if (refreshed) {
      await handleEdit(refreshed);
    } else {
      clearForm();
    }
  } catch {
    /* 用户取消 */
  }
}

function handleOrderClick(order: Record<string, any>) {
  selectedOrder.value = order;
  void handleEdit(order);
}

function isOrderRowSelected(row: Record<string, any>): boolean {
  const selNo = resolveGainLossBusNo(selectedOrder.value);
  const rowNo = resolveGainLossBusNo(row);
  if (!selNo || !rowNo) return false;
  return selNo === rowNo;
}

function handleLocationChange(locationId?: number | string) {
  queryParams.value.locationId = locationId;
  void getList();
}

function handleFormUpdate(newForm: Record<string, any>) {
  tableForm.value = { ...tableForm.value, ...newForm };
}

function handleUpdateButtonState(buttonState: Record<string, any>) {
  isAuditDisabled.value = buttonState.isAuditDisabled;
  isCancelDisabled.value = buttonState.isCancelDisabled;
  isDeleteDisabled.value = buttonState.isDeleteDisabled;
  isAddDisabled.value = buttonState.isAddDisabled;
  isEditDisabled.value = buttonState.isEditDisabled;
  buttonShow.value.isAddShow = buttonState.isAddShow;
  buttonShow.value.isDeleteShow = buttonState.isDeleteShow;
  buttonShow.value.isSaveShow = buttonState.isSaveShow;
  buttonShow.value.isEditShow = buttonState.isEditShow;
  buttonShow.value.isAlertShow = buttonState.isAlertShow ?? false;
  buttonShow.value.isOrderImport = buttonState.isOrderImport ?? false;
}

async function handleOrderImportFromDetail() {
  dialogVisible.value = true;
  await orderDialogRef.value?.getOrderList?.(tableForm.value.locationId as number | string);
}

function handleGainLossImportData(payload: {
  currentOrderForm: Record<string, unknown>;
  currentOrderList: Record<string, unknown>[];
}) {
  tableData.value = [...tableData.value, ...(payload.currentOrderList as Record<string, any>[])];
  tableForm.value = { ...tableForm.value, ...payload.currentOrderForm };
  handleUpdateButtonState({
    isAuditDisabled: true,
    isCancelDisabled: true,
    isDeleteDisabled: true,
    isAddDisabled: false,
    isEditDisabled: true,
    isAddShow: true,
    isDeleteShow: true,
    isSaveShow: true,
    isEditShow: false,
    isAlertShow: false,
    isOrderImport: true,
  });
}

function dialogSubmit(payload: { dialogVisible: boolean }) {
  dialogVisible.value = payload.dialogVisible;
}

function dialogCancel(payload: { dialogVisible: boolean }) {
  dialogVisible.value = payload.dialogVisible;
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    queryParams.value.keyword = busNo;
    await getList();
    const row = orderList.value.find((item) => resolveGainLossBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
    await handleEdit(row);
  },
  { immediate: true },
);

onMounted(() => {
  void init();
});
</script>

<template>
  <WarehouseDocWorkstation queue-title="损益单">
    <template #toolbar>
      <ElButton type="primary" :icon="Plus" :disabled="isAddDisabled" @click="handleAdd">
        新增
      </ElButton>
      <ElButton type="danger" :icon="Delete" :disabled="isDeleteDisabled" @click="handleDelete">
        删除
      </ElButton>
      <ElButton :disabled="isCancelDisabled" @click="handleCancel"> 取消 </ElButton>
      <ElButton type="success" :icon="Check" :disabled="isAuditDisabled" @click="handleAudit">
        审核
      </ElButton>
    </template>

    <template #aside-filter>
      <el-form class="w-full" label-width="64px" :model="tableForm" :rules="rules" @submit.prevent>
        <el-form-item label="库房" prop="locationId">
          <el-select
            v-model="tableForm.locationId"
            class="w-full"
            placeholder="请选择库房"
            clearable
            filterable
            @change="handleLocationChange(tableForm.locationId)"
          >
            <el-option
              v-for="item in cabinetListOptions"
              :key="item.id ?? item.value"
              :label="item.name ?? item.label"
              :value="item.id ?? item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单据号" class="warehouse-doc-aside-search-item">
          <el-input
            v-model="queryParams.keyword"
            placeholder="单据号检索"
            clearable
            @keyup.enter="getList"
          >
            <template #append>
              <ElButton size="small" :icon="Search" tabindex="-1" @click="getList" />
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </template>

    <template #aside-queue>
      <WarehouseDocOrderQueue
        :orders="orderList"
        :resolve-bus-no="resolveGainLossBusNo"
        :is-selected="isOrderRowSelected"
        :is-approved="isGainLossApproved"
        :format-apply-time="formatQueueApplyTime"
        :show-supplier="false"
        @select="handleOrderClick"
        @add="handleAdd"
      />
    </template>

    <WarehouseOrderDetailEdit
      class="min-h-0 flex-1"
      ref="orderTableRef"
      document-module="gain-loss"
      head-panel-title="损益单"
      :form="tableForm"
      :table-data="tableData"
      :audit-form="auditForm"
      :is-add-or-edit-order="isAddOrEditOrder"
      :supplier-option="supplierOption"
      :supply-type-options="supplyTypeOptions"
      :supply-status-options="supplyStatusOptions"
      :supply-category-options="supplyCategoryOptions"
      :practitioner-option="practitionerOption"
      :button-show="buttonShow"
      @update:form="handleFormUpdate"
      @clear-form="clearForm"
      @get-list="getList"
      @update-button-state="handleUpdateButtonState"
      @order-import="handleOrderImportFromDetail"
    />

    <GainLossOrderDialog
      ref="orderDialogRef"
      :dialog-visible="dialogVisible"
      @dialog-submit="dialogSubmit"
      @dialog-cancel="dialogCancel"
      @update-table-data="handleGainLossImportData"
    />
  </WarehouseDocWorkstation>
</template>
