<!-- 药库退库入库：对退库药品办理入库登记；流程与订货申请页同构，沿用工作站交互。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Check, Delete, Plus, Search } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  agreeReturnInboundApprovalApi,
  deleteReturnInboundApi,
  initReturnInboundApi,
  pageReturnInboundDetailApi,
  pageReturnInboundListApi,
} from '#/api/warehouse/return-inbound';
import WarehouseDocOrderQueue from '#/components/warehouse/warehouse-doc-order-queue.vue';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import WarehouseOrderDetailEdit from '#/components/warehouse/warehouse-order-detail-edit.vue';
import { isPurchaseRequestApproved } from '#/utils/warehouse/purchase-request-order-detail';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'WarehouseReturnInbound',
});

const route = useRoute();
const userStore = useUserStore();

function resolveApplicantId(): number | string | undefined {
  const u = userStore.userInfo as null | Record<string, any>;
  if (!u) return undefined;
  const ext = u.extraProperties ?? {};
  return u.practitionerId ?? ext.PractitionerId ?? ext.practitionerId;
}

/** 左侧查询（含库房） */
const queryParams = ref<{
  locationId?: number | string;
  keyword?: string;
}>({
  keyword: '',
});

const orderTableRef = ref<null | { resetAllData: () => void }>(null);

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

const RETURN_INBOUND_DETAIL_PAGE_SIZE = 2000;

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
});

const rules = ref({
  locationId: [{ required: true, message: '请选择库房', trigger: 'blur' }],
});

/** 列表接口可能返回 supplyBusNo 或 busNo，统一解析。 */
function resolveReturnInboundBusNo(row?: null | Record<string, any>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo, row.purchaseBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

/** 队列卡片「单据日期」格式化 */
function formatQueueApplyTime(time?: unknown): string {
  if (time === undefined || time === null || time === '') return '';
  return formatDateTime(time as Date | number | string);
}

async function init() {
  try {
    const data = await initReturnInboundApi();
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
    const data = await pageReturnInboundListApi(queryParams.value);
    const records = data.records ?? [];
    orderList.value = records.map((r) => ({
      ...r,
      supplyBusNo: resolveReturnInboundBusNo(r),
    }));
  } catch {
    orderList.value = [];
  }
}

/** 新增按钮 */
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
      isAlertShow: true,
    });
  });
}

function resetFormAndTableData() {
  tableData.value = [];
  auditForm.value = {};
}

async function handleEdit(order?: null | Record<string, any>) {
  resetFormAndTableData();
  const row = order ?? selectedOrder.value;
  const busNo = resolveReturnInboundBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  try {
    const detail = await pageReturnInboundDetailApi({
      busNo,
      pageNo: 1,
      pageSize: RETURN_INBOUND_DETAIL_PAGE_SIZE,
    });
    buildEditForm(detail.records ?? []);
  } catch {
    return;
  }

  const isApproved = isPurchaseRequestApproved(row);
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
    isAlertShow: !isApproved,
  });
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
    unit.totalPriceMask = formatMoneyDisplay(calcMoneyLineTotal(item.price, item.itemQuantity));
    unit.totalRetailPriceMask = formatMoneyDisplay(
      calcMoneyLineTotal(item.retailPrice, item.itemQuantity),
    );
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
  if (isPurchaseRequestApproved(selectedOrder.value)) {
    ElMessage.error('已审批的单据不能删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该退库入库单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const deletedBusNo = resolveReturnInboundBusNo(selectedOrder.value);
    if (!deletedBusNo) {
      ElMessage.error('单据号缺失，无法删除');
      return;
    }
    await deleteReturnInboundApi(deletedBusNo);
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
  if (isPurchaseRequestApproved(selectedOrder.value)) {
    ElMessage.error('该单据已审批，不能重复审批');
    return;
  }

  try {
    await ElMessageBox.confirm('确认同意该退库入库单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const auditedBusNo = resolveReturnInboundBusNo(selectedOrder.value);
    if (!auditedBusNo) {
      ElMessage.error('单据号缺失，无法审核');
      return;
    }
    await agreeReturnInboundApprovalApi(auditedBusNo);
    ElMessage.success('同意审批成功');
    await getList();
    const refreshed = orderList.value.find((o) => resolveReturnInboundBusNo(o) === auditedBusNo);
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
  const selNo = resolveReturnInboundBusNo(selectedOrder.value);
  const rowNo = resolveReturnInboundBusNo(row);
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
  buttonShow.value.isAlertShow = buttonState.isAlertShow;
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    queryParams.value.keyword = busNo;
    await getList();
    const row = orderList.value.find((item) => resolveReturnInboundBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
    await handleEdit(row);
  },
  { immediate: true },
);

onMounted(() => {
  void init();
  void getList();
});
</script>

<template>
  <WarehouseDocWorkstation queue-title="退库入库单">
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
        applicant-role-label="制单人"
        :resolve-bus-no="resolveReturnInboundBusNo"
        :is-selected="isOrderRowSelected"
        :is-approved="isPurchaseRequestApproved"
        :format-apply-time="formatQueueApplyTime"
        @select="handleOrderClick"
        @add="handleAdd"
      />
    </template>

    <WarehouseOrderDetailEdit
      class="min-h-0 flex-1"
      ref="orderTableRef"
      head-panel-title="退库入库单"
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
    />
  </WarehouseDocWorkstation>
</template>
