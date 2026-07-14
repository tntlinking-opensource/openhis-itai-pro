<!-- 请领申请：药房/科室向药库发起物资请领（前置单据，生效后由药库出库联动）；工作站交互与采购申请页同构，当前接口暂复用 purchase-request。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Check, Delete, Plus, Search } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  agreePurchaseOrderApprovalApi,
  deletePurchaseOrderApi,
  initPurchaseOrderApi,
  pagePurchaseOrderDetailApi,
  pagePurchaseOrderListApi,
} from '#/api/warehouse/purchase-request';
import WarehouseDocOrderQueue from '#/components/warehouse/warehouse-doc-order-queue.vue';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import WarehouseOrderDetailEdit from '#/components/warehouse/warehouse-order-detail-edit.vue';
import { isPurchaseRequestApproved } from '#/utils/warehouse/purchase-request-order-detail';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'WarehouseRequisitionRequest',
});

const route = useRoute();
const userStore = useUserStore();

function resolveApplicantId(): number | string | undefined {
  const u = userStore.userInfo as null | Record<string, any>;
  if (!u) return undefined;
  const ext = u.extraProperties ?? {};
  return u.practitionerId ?? ext.PractitionerId ?? ext.practitionerId;
}

/** 队列卡片「单据日期」：兼容接口 unknown */
function formatQueueApplyTime(time?: unknown): string {
  if (time === undefined || time === null || time === '') return '';
  return formatDateTime(time as Date | number | string);
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

/** 请领申请明细与其它库房编辑页一致：一次拉取，底部不使用独立分页组件 */
const PURCHASE_ORDER_DETAIL_PAGE_SIZE = 2000;

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

/** 列表接口可能对单据号返回 supplyBusNo 或 busNo（及其它别名），统一解析避免选中后无法拉明细 */
function resolvePurchaseOrderBusNo(row?: null | Record<string, any>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo, row.purchaseBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

async function init() {
  try {
    const data = await initPurchaseOrderApi();
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
    const data = await pagePurchaseOrderListApi(queryParams.value);
    const records = data.records ?? [];
    orderList.value = records.map((r) => ({
      ...r,
      supplyBusNo: resolvePurchaseOrderBusNo(r),
    }));
  } catch {
    orderList.value = [];
  }
}

// 新增按钮
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

  // 订单编辑还是新增
  isAddOrEditOrder.value = {
    isAddOrder: true,
    isEditOrder: false,
  };

  nextTick(() => {
    // 更新按钮状态为编辑模式
    handleUpdateButtonState({
      // 审核 按钮禁用
      isAuditDisabled: true,
      // 取消 按钮禁用
      isCancelDisabled: false,
      // 删除 按钮禁用
      isDeleteDisabled: true,
      // 添加 按钮禁用
      isAddDisabled: true,
      // 编辑 按钮禁用
      isEditDisabled: true,
      // 添加行 按钮显示
      isAddShow: true,
      // 删除行 按钮显示
      isDeleteShow: false,
      // 保存行 按钮显示
      isSaveShow: false,
      // 编辑行 按钮显示
      isEditShow: false,
      // 警戒订货 按钮显示
      isAlertShow: true,
    });
  });
}

function resetFormAndTableData() {
  // 清空右侧表格数据
  tableData.value = [];
  // 清空审核表单数据
  auditForm.value = {};
}

async function handleEdit(order?: null | Record<string, any>) {
  resetFormAndTableData();

  const row = order ?? selectedOrder.value;
  const busNo = resolvePurchaseOrderBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  try {
    const detail = await pagePurchaseOrderDetailApi({
      busNo,
      pageNo: 1,
      pageSize: PURCHASE_ORDER_DETAIL_PAGE_SIZE,
    });
    buildEditForm(detail.records ?? []);
  } catch {
    return;
  }
  // 根据订单状态更新按钮状态
  const isApproved = isPurchaseRequestApproved(row);
  if (isApproved) {
    // 设置编辑状态
    isAddOrEditOrder.value = {
      isAddOrder: false,
      isEditOrder: false,
    };
    // 更新按钮状态为初始状态
    handleUpdateButtonState({
      // 审核 按钮禁用
      isAuditDisabled: true,
      // 取消 按钮禁用
      isCancelDisabled: true,
      // 删除 按钮禁用
      isDeleteDisabled: true,
      // 添加 按钮禁用
      isAddDisabled: false,
      // 编辑 按钮禁用
      isEditDisabled: true,
      // 添加行 按钮显示
      isAddShow: false,
      // 删除行 按钮显示
      isDeleteShow: false,
      // 保存行 按钮显示
      isSaveShow: false,
      // 编辑行 按钮显示
      isEditShow: false,
      // 警戒订货 按钮显示
      isAlertShow: false,
    });
    return;
  }
  // 设置编辑状态
  isAddOrEditOrder.value = {
    isAddOrder: false,
    isEditOrder: true,
  };

  // 更新按钮状态
  handleUpdateButtonState({
    // 审核 按钮禁用
    isAuditDisabled: isApproved,
    // 取消 按钮禁用
    isCancelDisabled: false,
    // 删除 按钮禁用
    isDeleteDisabled: isApproved,
    // 添加 按钮禁用
    isAddDisabled: false,
    // 编辑 按钮禁用
    isEditDisabled: true,
    // 添加行 按钮显示
    isAddShow: !isApproved,
    // 删除行 按钮显示
    isDeleteShow: !isApproved,
    // 保存行 按钮显示
    isSaveShow: !isApproved,
    // 编辑行 按钮显示
    isEditShow: !isApproved,
    // 警戒订货 按钮显示
    isAlertShow: !isApproved,
  });
}

function buildUnitList(item: Record<string, any>) {
  // 表格单位列表
  (item.unitList || []).forEach((unit: Record<string, any>) => {
    // 判断当前单位是最大单位还是最小单位
    const isMaxUnit = unit.value === item.maxUnitCode;
    const isMinUnit = unit.value === item.minUnitCode;

    // 进货价（假）
    unit.priceMask = isMaxUnit
      ? item.price
      : item.price / Number.parseInt(String(item.partPercent), 10);

    // 销售价（假）
    unit.retailPriceMask = isMaxUnit
      ? item.retailPrice
      : item.retailPrice / Number.parseInt(String(item.partPercent), 10);

    // 进价金额（假）
    unit.totalPriceMask = formatMoneyDisplay(calcMoneyLineTotal(item.price, item.itemQuantity));

    // 销售金额（假）
    unit.totalRetailPriceMask = formatMoneyDisplay(
      calcMoneyLineTotal(item.retailPrice, item.itemQuantity),
    );

    // 规格库存
    unit.specificationInventoryOriginal = item.specificationInventory;

    // 规格库存显示逻辑
    if (isMaxUnit) {
      // 最大单位：显示为 "X盒"
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
      // 最小单位：显示为 "X瓶"
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else {
      // 其他单位：显示为 "X瓶"
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
  // 更新按钮状态为初始状态
  handleUpdateButtonState({
    // 审核 按钮禁用
    isAuditDisabled: true,
    // 取消 按钮禁用
    isCancelDisabled: true,
    // 删除 按钮禁用
    isDeleteDisabled: true,
    // 添加 按钮禁用
    isAddDisabled: false,
    // 编辑 按钮禁用
    isEditDisabled: true,
    // 添加行 按钮显示
    isAddShow: false,
    // 删除行 按钮显示
    isDeleteShow: false,
    // 保存行 按钮显示
    isSaveShow: false,
    // 编辑行 按钮显示
    isEditShow: false,
    // 警戒订货 按钮显示
    isAlertShow: false,
  });

  // 清空表单
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
    await ElMessageBox.confirm('确认删除该请领申请单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const deletedBusNo = resolvePurchaseOrderBusNo(selectedOrder.value);
    if (!deletedBusNo) {
      ElMessage.error('单据号缺失，无法删除');
      return;
    }
    await deletePurchaseOrderApi(deletedBusNo);
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
    /* 用户取消或接口错误（错误已由拦截器提示） */
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
    await ElMessageBox.confirm('确认同意该请领申请单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const auditedBusNo = resolvePurchaseOrderBusNo(selectedOrder.value);
    if (!auditedBusNo) {
      ElMessage.error('单据号缺失，无法审核');
      return;
    }
    await agreePurchaseOrderApprovalApi(auditedBusNo);
    ElMessage.success('同意审批成功');
    await getList();
    const refreshed = orderList.value.find((o) => resolvePurchaseOrderBusNo(o) === auditedBusNo);
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

/** 左侧列表选中态：按单据号比较，避免对象引用不一致导致高亮丢失 */
function isOrderRowSelected(row: Record<string, any>): boolean {
  const selNo = resolvePurchaseOrderBusNo(selectedOrder.value);
  const rowNo = resolvePurchaseOrderBusNo(row);
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
    const row = orderList.value.find((item) => resolvePurchaseOrderBusNo(item) === String(busNo));
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
  <WarehouseDocWorkstation queue-title="请领申请单">
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
        :resolve-bus-no="resolvePurchaseOrderBusNo"
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
      head-panel-title="请领申请单"
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
