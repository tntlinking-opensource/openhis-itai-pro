<script setup>
import { ref, watch } from 'vue';

import { formatDate } from '@vben/utils';

import {
  pageStockInOrderDetailApi,
  pageStockInOrderListApi,
} from '#/api/warehouse/purchase-return';
import { isPurchaseRequestApproved } from '#/utils/warehouse/purchase-request-order-detail';
import { calcMoneyLineTotal, formatMoneyDisplay, scaleMoneyCalc } from '#/utils/format/money-scale';

defineOptions({
  name: 'PurchaseReturnStockInImportDialog',
});

// 属性
const props = defineProps({
  // 对话框x显示
  dialogVisible: {
    type: Boolean,
    default: false,
  },
});
// 调用父组件方法
const emit = defineEmits(['dialogSubmit', 'dialogCancel', 'updateTableData']);
// 本地对话框显示
const localDialogVisible = ref(false);
// 监听属性
watch(
  () => props.dialogVisible,
  (newValue) => {
    localDialogVisible.value = newValue;
  },
);
// 获取当前行
const currentRow = ref({});
// 行点击 双击
const handleRowClick = (row) => {
  // 设置当前行
  currentRow.value = row;
};
// 行点击 双击
const handleRowDBClick = (row) => {
  // 设置当前行
  currentRow.value = row;
  // 提交
  handleSubmit();
};
// 订单列表
const orderList = ref([]);
// 订单列表总条数
const localTableDataTotal = ref(0);
// 当前locationId
const currentLocationId = ref(undefined);
// 查询参数
const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  locationId: undefined,
});
// 分页每页条数
const handleSizeChange = (size) => {
  queryParams.value.pageSize = size;
  queryParams.value.pageNo = 1; // 切换每页条数时重置到第一页
  // 重新获取数据
  if (currentLocationId.value) {
    getOrderList(currentLocationId.value);
  }
};
// 分页当前页
const handleCurrentChange = (page) => {
  queryParams.value.pageNo = page;
  // 重新获取数据
  if (currentLocationId.value) {
    getOrderList(currentLocationId.value);
  }
};

// 获取订货单列表
const getOrderList = async (locationId) => {
  try {
    // 保存当前locationId
    currentLocationId.value = locationId;

    // 构建请求参数
    const requestParams = {
      // 仓库ID
      locationId,
      // 页码
      pageNo: queryParams.value.pageNo,
      // 每页条数
      pageSize: queryParams.value.pageSize,
      // 订单状态 3 ：审核通过
      statusEnum: 3,
      // 是否查询原始单
      originalBusNoFlg: 1,
    };
    const res = await pageStockInOrderListApi(requestParams);
    const records = res?.records ?? [];
    if (records.length > 0) {
      orderList.value = records;
      localTableDataTotal.value = res?.total ?? 0;
    } else {
      orderList.value = [];
      localTableDataTotal.value = 0;
    }
  } catch (error) {
    console.error('获取订货单列表失败:', error);
  }
};
// 重置所有数据
const resetAllData = () => {
  // 重置分页参数
  queryParams.value.pageNo = 1;
  queryParams.value.pageSize = 10;
  currentLocationId.value = undefined;
  orderList.value = [];
  localTableDataTotal.value = 0;
};
// 提交数据
const handleSubmit = async () => {
  // 取消按钮
  handleCancel();
  // 获取当前进货单的订单列表
  await getCurrentOrderDetail();
  // 传递数据给父组件
  emit('updateTableData', {
    currentOrderList: setCurrentOrderList.value,
    currentOrderForm: setCurrentOrderForm.value,
  });
};
// 设置当前进货单的订单列表
const setCurrentOrderList = ref([]);
// 设置当前进货单的订单表单
const setCurrentOrderForm = ref({});

// 获取当前进货单的订单列表
const getCurrentOrderDetail = async () => {
  if (!currentRow.value.supplyBusNo) {
    return;
  }
  const res = await pageStockInOrderDetailApi({ busNo: currentRow.value.supplyBusNo });
  buildEditForm(res?.records ?? []);
};
// 构建编辑后的表单数据
const buildEditForm = (data) => {
  // 构建表格数据
  if (data.length > 0) {
    // 表单数据
    setCurrentOrderForm.value = {
      // 供应商
      supplierId: data[0].supplierId,
      // 供应商联系人
      phone: data[0].phone,
      // 采购员
      practitionerId: data[0].applicantId,
      // 原始单据号
      originalBusNo: data[0].busNo,
    };
    // 表格数据
    data.forEach((item) => {
      // 表格编辑状态
      item.isEditing = true;
      // 表格查看状态
      item.isViewing = false;
      // 判断当前单位是否是最大单位
      buildPrice(item);
      // 构建单位列表
      buildUnitList(item);
    });
    // 更新表格数据
    setCurrentOrderList.value = data;
  }
};
// 构建进货价和销售价
const buildPrice = (item) => {
  // 判断当前单位是最大单位还是最小单位
  const isMaxUnit = item.unitCode === item.maxUnitCode;
  if (isMaxUnit) {
    item.priceMaxUnit = item.price;
    item.priceMinUnit = item.price / Number.parseInt(item.partPercent);

    item.retailPriceMaxUnit = item.retailPrice;
    item.retailPriceMinUnit = formatMoneyDisplay(
      Number.parseFloat(item.retailPrice) / Number.parseInt(item.partPercent),
    );
  } else {
    item.priceMaxUnit = scaleMoneyCalc(
      calcMoneyLineTotal(item.price, Number.parseInt(item.partPercent)),
    );
    item.priceMinUnit = item.price;

    item.retailPriceMaxUnit = scaleMoneyCalc(
      calcMoneyLineTotal(item.retailPrice, Number.parseInt(item.partPercent)),
    );
    item.retailPriceMinUnit = item.retailPrice;
  }
};
//  构建单位列表
const buildUnitList = (item) => {
  // 表格单位列表
  (item.unitList || []).forEach((unit) => {
    // 判断当前单位是最大单位还是最小单位
    const isMaxUnit = unit.value === item.maxUnitCode;
    const isMinUnit = unit.value === item.minUnitCode;

    // // 进货价（假）
    unit.priceMask = item.price;

    // 销售价（假）
    unit.retailPriceMask = item.retailPrice;

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
        item.specificationInventory / Number.parseInt(item.partPercent),
      );
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.maxUnitCode_dictText : '';
      unit.specificationInventoryMin =
        item.specificationInventory % Number.parseInt(item.partPercent);
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';

      // 批次库存
      unit.batchInventoryMax = Math.floor(item.batchInventory / Number.parseInt(item.partPercent));
      unit.batchInventoryMaxUnit = unit.batchInventoryMax > 0 ? item.maxUnitCode_dictText : '';
      unit.batchInventoryMin = item.batchInventory % Number.parseInt(item.partPercent);
      unit.batchInventoryMinUnit = unit.batchInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else if (isMinUnit) {
      // 最小单位：显示为 "X瓶"
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';

      // 批次库存
      unit.batchInventoryMax = item.batchInventory;
      unit.batchInventoryMaxUnit = unit.batchInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.batchInventoryMin = 0;
      unit.batchInventoryMinUnit = unit.batchInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else {
      // 其他单位：显示为 "X瓶"
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';

      // 批次库存
      unit.batchInventoryMax = item.batchInventory;
      unit.batchInventoryMaxUnit = unit.batchInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.batchInventoryMin = 0;
      unit.batchInventoryMinUnit = unit.batchInventoryMin > 0 ? item.minUnitCode_dictText : '';
    }
  });
};
// 取消按钮
const handleCancel = () => {
  emit('dialogCancel', {
    dialogVisible: false,
  });
};
defineExpose({
  getOrderList,
});
</script>

<template>
  <div>
    <el-dialog
      title="订单列表"
      v-model="localDialogVisible"
      width="960px"
      append-to-body
      @close="resetAllData"
    >
      <el-table
        :data="orderList"
        width="100%"
        highlight-current-row
        height="470px"
        @row-dblclick="handleRowDBClick"
        @row-click="handleRowClick"
      >
        <el-table-column label="订单编号" prop="supplyBusNo" width="200" align="center" />
        <el-table-column label="采购员" prop="applicantId_dictText" width="100" align="center" />
        <el-table-column label="供应商" prop="supplierId_dictText" width="220" align="center" />
        <el-table-column label="审核状态" prop="statusEnum_enumText" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="isPurchaseRequestApproved(scope.row)" type="success">
              {{ scope.row.statusEnum_enumText }}
            </el-tag>
            <el-tag v-else type="danger">{{ scope.row.statusEnum_enumText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单据类型" prop="typeEnum_enumText" width="100" align="center" />
        <el-table-column label="单据日期" prop="applyTime" width="200" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.applyTime, 'YYYY-MM-DD HH:mm:ss') }}
          </template>
        </el-table-column>
      </el-table>
      <el-row justify="end" style="margin-top: 10px">
        <el-pagination
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :page-sizes="[10, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="localTableDataTotal"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
          v-show="localTableDataTotal > 0"
        />
      </el-row>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleSubmit"> 确认 </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style></style>
