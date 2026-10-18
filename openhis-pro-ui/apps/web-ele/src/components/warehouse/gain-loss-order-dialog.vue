<!-- 损益单：从已审核订货单导入明细（与采购退货进货导入弹窗同构） -->
<script lang="ts" setup>
import { ref, watch } from 'vue';

import { formatDate } from '@zxplat/utils';

import { ElMessage } from 'element-plus';

import {
  pagePurchaseOrderDetailForGainLossApi,
  pagePurchaseOrderForGainLossApi,
} from '#/api/warehouse/gain-loss';
import { isGainLossApproved } from '#/utils/warehouse/gain-loss-doc';
import { mapDetailRecordToGridRow } from '#/utils/warehouse/purchase-request-order-detail';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({
  name: 'GainLossOrderDialog',
});

const props = defineProps({
  dialogVisible: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['dialog-submit', 'dialog-cancel', 'update-table-data']);

const localDialogVisible = ref(false);

watch(
  () => props.dialogVisible,
  (v) => {
    localDialogVisible.value = v;
  },
  { immediate: true },
);

const currentRow = ref<Record<string, any>>({});
const orderList = ref<Record<string, any>[]>([]);
const localTableDataTotal = ref(0);
const currentLocationId = ref<number | string | undefined>();

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
});

function handleRowClick(row: Record<string, any>) {
  currentRow.value = row;
}

function handleSizeChange(size: number) {
  queryParams.value.pageSize = size;
  queryParams.value.pageNo = 1;
  if (currentLocationId.value !== undefined && currentLocationId.value !== '') {
    void fetchOrderList();
  }
}

function handleCurrentChange(page: number) {
  queryParams.value.pageNo = page;
  if (currentLocationId.value !== undefined && currentLocationId.value !== '') {
    void fetchOrderList();
  }
}

async function fetchOrderList() {
  const locationId = currentLocationId.value;
  if (locationId === undefined || locationId === null || `${locationId}` === '') {
    orderList.value = [];
    localTableDataTotal.value = 0;
    return;
  }
  try {
    const res = await pagePurchaseOrderForGainLossApi({
      locationId,
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      statusEnum: 3,
    });
    const { content: records, totalCount } = parsePagedRowsFromApiData(res);
    orderList.value = records as Record<string, any>[];
    localTableDataTotal.value = totalCount;
  } catch {
    orderList.value = [];
    localTableDataTotal.value = 0;
  }
}

async function getOrderList(locationId?: number | string) {
  currentLocationId.value = locationId;
  queryParams.value.pageNo = 1;
  await fetchOrderList();
}

function resetAllData() {
  queryParams.value = { pageNo: 1, pageSize: 10 };
  currentLocationId.value = undefined;
  orderList.value = [];
  localTableDataTotal.value = 0;
  currentRow.value = {};
}

function handleCancel() {
  emit('dialog-cancel', { dialogVisible: false });
}

function onDialogClose() {
  resetAllData();
  emit('dialog-cancel', { dialogVisible: false });
}

async function handleSubmit() {
  const busNo = currentRow.value.supplyBusNo ?? currentRow.value.busNo;
  if (!busNo) {
    ElMessage.warning('请选择一条已审核的订货单');
    return;
  }
  handleCancel();
  try {
    const res = await pagePurchaseOrderDetailForGainLossApi({
      busNo: String(busNo),
      pageNo: 1,
      pageSize: 2000,
    });
    const { content: raw } = parsePagedRowsFromApiData(res);
    if (raw.length === 0) {
      ElMessage.warning('未获取到订货明细');
      return;
    }
    const head = raw[0] as Record<string, unknown>;
    const currentOrderForm: Record<string, unknown> = {
      supplierId: head.supplierId,
      applicantId: head.applicantId,
      categoryEnum: head.categoryEnum,
      reason: head.reason,
      locationId: head.purposeLocationId ?? head.locationId,
      originalBusNo: head.supplyBusNo ?? head.busNo,
    };
    const currentOrderList = raw.map((line) =>
      mapDetailRecordToGridRow({ ...(line as Record<string, unknown>) }),
    );
    emit('update-table-data', { currentOrderForm, currentOrderList });
  } catch {
    /* 请求拦截器提示 */
  }
}

function handleRowDblClick(row: Record<string, any>) {
  currentRow.value = row;
  void handleSubmit();
}

defineExpose({
  getOrderList,
});
</script>

<template>
  <div>
    <el-dialog
      v-model="localDialogVisible"
      title="订货单列表"
      width="960px"
      append-to-body
      @close="onDialogClose"
    >
      <el-table
        :data="orderList"
        width="100%"
        highlight-current-row
        height="470px"
        @row-click="handleRowClick"
        @row-dblclick="handleRowDblClick"
      >
        <el-table-column label="订单编号" prop="supplyBusNo" width="200" align="center" />
        <el-table-column label="采购员" prop="applicantId_dictText" width="100" align="center" />
        <el-table-column label="供应商" prop="supplierId_dictText" width="220" align="center" />
        <el-table-column label="审核状态" prop="statusEnum_enumText" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="isGainLossApproved(row)" type="success">
              {{ row.statusEnum_enumText }}
            </el-tag>
            <el-tag v-else type="danger">{{ row.statusEnum_enumText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单据类型" prop="typeEnum_enumText" width="100" align="center" />
        <el-table-column label="单据日期" prop="applyTime" width="200" align="center">
          <template #default="{ row }">
            {{ formatDate(row.applyTime, 'YYYY-MM-DD HH:mm:ss') }}
          </template>
        </el-table-column>
      </el-table>
      <el-row v-show="localTableDataTotal > 0" justify="end" style="margin-top: 10px">
        <el-pagination
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :page-sizes="[10, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="localTableDataTotal"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </el-row>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
