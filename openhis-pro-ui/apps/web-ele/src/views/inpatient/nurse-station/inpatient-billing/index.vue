<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  OrderBillingCategory,
  OrderBillingTableRow,
  OrderChargeDetailRow,
  PricingCatalogCategory,
  PricingConfirmationTableRow,
} from './services/map-nurse-station-inpatient-billing';

import type {
  NurseStationPricingCatalogRowDto,
  NurseStationPricingWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpCancelNurseStationPricing,
  httpConfirmDirectNurseStationPricing,
  httpConfirmSupplementNurseStationPricing,
  httpListNurseStationPricedOrders,
  httpListNurseStationSupplementParentOrders,
  httpPageNurseStationPricingCatalog,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import AppDialog from '#/components/app-dialog.vue';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalMul } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import AutoRollPanel from './components/auto-roll-panel.vue';
import OrderBillingPanel from './components/order-billing-panel.vue';
import PricingConfirmationPanel from './components/pricing-confirmation-panel.vue';
import {
  buildDirectPricingWriteItem,
  buildPricingCatalogRowKey,
  buildSupplementPricingWriteItem,
  filterOrderBillingTree,
  isPricingDeviceCatalogRow,
  listDirectPricingWriteItemMissingFields,
  listSupplementPricingWriteItemMissingFields,
  mapPricedOrderGroupsToTableRows,
  mapPricedOrderRowToChargeDetailRow,
  mapPricingDateRangeToQuery,
  mapSelectedPricingRowsToCancelItems,
  mergeOrderBillingTreeFromApiResponses,
  normalizePricingCatalogRow,
  PRICING_CANCEL_BATCH_LIMIT,
  PRICING_PRODUCT_TABLE_DEVICE,
  PRICING_PRODUCT_TABLE_SERVICE,
} from './services/map-nurse-station-inpatient-billing';

type BillingTab = 'auto-roll' | 'order-billing' | 'pricing-confirmation';

interface SelectedPricingCatalogItem {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
  category: PricingCatalogCategory;
  quantity: number;
}

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientBillingModule',
});
const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const WRITE_BATCH_LIMIT = 50;
const CATALOG_PAGE_SIZE = 20;

const pricingRows = ref<PricingConfirmationTableRow[]>([]);
const selectedPricingRowIds = ref<string[]>([]);
const pricingTableKey = ref(0);
const pricingTimeRange = ref<string[]>([]);
const activeTab = ref<BillingTab>('pricing-confirmation');
const autoRollPanelRef = ref<InstanceType<typeof AutoRollPanel> | null>(null);
const batchDialogVisible = ref(false);
const projectKeyword = ref('');
const projectType = ref<PricingCatalogCategory>('device');
const selectedPricingMap = ref<Map<string, SelectedPricingCatalogItem>>(new Map());
const catalogRows = ref<NurseStationPricingCatalogRowDto[]>([]);
const catalogPageNo = ref(1);
const catalogTotal = ref(0);
const saving = ref(false);
const orderBillingCategory = ref<OrderBillingCategory>('all');
const orderBillingKeyword = ref('');
const orderBillingDept = ref('');
const chargeDetailDialogVisible = ref(false);
const chargeDetailLoading = ref(false);
const activeOrderBillingRow = ref<OrderBillingTableRow | null>(null);
const supplementFeeOrderRow = ref<OrderBillingTableRow | null>(null);
const orderChargeDetailRows = ref<OrderChargeDetailRow[]>([]);

const orderBillingDeptOptions = [{ label: '全部科室', value: '' }];
const orderBillingRows = ref<OrderBillingTableRow[]>([]);
const orderBillingLoading = ref(false);

const listPricedOrdersRequest = useRequest(httpListNurseStationPricedOrders, { manual: true });
const listSupplementParentOrdersRequest = useRequest(httpListNurseStationSupplementParentOrders, {
  manual: true,
});
const catalogRequest = useRequest(httpPageNurseStationPricingCatalog, { manual: true });
const confirmPricingRequest = useRequest(httpConfirmDirectNurseStationPricing, { manual: true });
const confirmSupplementPricingRequest = useRequest(httpConfirmSupplementNurseStationPricing, {
  manual: true,
});
const cancelPricingRequest = useRequest(httpCancelNurseStationPricing, { manual: true });
const authStore = useAuthStore();

const encounterIds = computed(() =>
  props.patients.map((patient) => String(patient.id).trim()).filter((id) => id.length > 0),
);

/** 根据关键字筛选当前页目录（服务端已分页，此处仅做展示层二次过滤）。 */
const filteredCatalogRows = computed(() => {
  const text = projectKeyword.value.trim().toLowerCase();
  if (!text) {
    return catalogRows.value;
  }
  return catalogRows.value.filter((item) => {
    const code = String(item.busNo ?? '').toLowerCase();
    const name = String(item.name ?? '').toLowerCase();
    return code.includes(text) || name.includes(text);
  });
});

/** 将已勾选目录行按插入顺序整理成右侧可编辑清单。 */
const selectedPricingItems = computed(() => [...selectedPricingMap.value.values()]);

/** 汇总已选项目总数量。 */
const selectedQuantityTotal = computed(() =>
  selectedPricingItems.value.reduce((sum, item) => sum + item.quantity, 0),
);

/** 汇总已选项目总金额。 */
const selectedAmountTotal = computed(() =>
  selectedPricingItems.value.reduce(
    (sum, item) => sum + decimalMul(item.catalog.unitPrice ?? 0, item.quantity, 2),
    0,
  ),
);
/** 当前选中医嘱对应的费用明细行。 */
const activeOrderChargeRows = computed(() => orderChargeDetailRows.value);
/** 当前选中医嘱费用明细金额合计。 */
const activeOrderChargeTotal = computed(() =>
  activeOrderChargeRows.value.reduce((sum, item) => sum + item.amount, 0),
);
/** 根据关键字筛选医嘱计费父子树（服务端已筛，此处做展示层二次过滤）。 */
const filteredOrderBillingRows = computed(() =>
  filterOrderBillingTree(orderBillingRows.value, orderBillingKeyword.value),
);
/** 批量划价弹窗是否为医嘱补费模式。 */
const isSupplementFeeDialog = computed(() => supplementFeeOrderRow.value !== null);
/** 批量划价弹窗标题。 */
const batchPricingDialogTitle = computed(() =>
  isSupplementFeeDialog.value ? '医嘱补费' : '批量划价',
);
/** 住院记账要求左侧患者单选，仅单选时才允许查询列表与打开弹窗。 */
const canOperateBilling = computed(() => props.patients.length === 1);
/** 当前用于划价操作的左侧患者。 */
const selectedBillingPatient = computed(() => (canOperateBilling.value ? props.patients[0] : null));
/** 页面顶部吸顶患者信息。 */
const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return null;
  }
  if (!canOperateBilling.value) {
    return {
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后执行计费相关操作）`,
      name: '请选择单一患者',
    };
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  return {
    age: patient.age,
    bedNo: patient.bedNo,
    diagnosis: patient.diagnosis ?? '—',
    gender: patient.gender,
    inpatientNo: patient.id,
    insuranceType: patient.feeType ?? '—',
    name: patient.name,
  };
});

/** 将金额格式化为两位小数金额文案。 */
function formatCurrency(value: number) {
  return `￥${value.toFixed(2)}`;
}

/** 打开批量划价弹窗并重置本次项目选择。 */
function openBatchPricingDialog(orderRow?: OrderBillingTableRow) {
  projectKeyword.value = '';
  projectType.value = 'device';
  clearSelectedPricingItems();
  supplementFeeOrderRow.value = orderRow ?? null;
  batchDialogVisible.value = true;
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  catalogPageNo.value = 1;
  void loadPricingCatalog();
}

/** 并行加载父医嘱与已划价列表，组装二级展开表格。 */
async function loadOrderBillingRows() {
  if (encounterIds.value.length === 0) {
    orderBillingRows.value = [];
    return;
  }
  const orgId = preserveApiIdForRequest(orderBillingDept.value);
  orderBillingLoading.value = true;
  try {
    const [parentRes, pricedRes] = await Promise.all([
      listSupplementParentOrdersRequest.run({
        encounterIds: encounterIds.value,
        therapyType: orderBillingCategory.value,
        keyword: orderBillingKeyword.value.trim() || undefined,
        orgId: orgId || undefined,
      }),
      httpListNurseStationPricedOrders({
        encounterIds: encounterIds.value,
      }),
    ]);
    orderBillingRows.value = mergeOrderBillingTreeFromApiResponses(
      parentRes.groups,
      pricedRes.groups,
    );
  } finally {
    orderBillingLoading.value = false;
  }
}

/** 按筛选条件查询医嘱计费列表。 */
async function handleQueryOrderBillingRows() {
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  await loadOrderBillingRows();
}

/** 加载指定父医嘱下的已划价费用明细。 */
async function loadOrderChargeDetail(parentRow: OrderBillingTableRow) {
  const encounterId = preserveApiIdForRequest(parentRow.encounterId);
  const parentRequestId = resolveSupplementAnchorRequestId(parentRow);
  if (!encounterId || !parentRequestId) {
    orderChargeDetailRows.value = [];
    return;
  }
  chargeDetailLoading.value = true;
  try {
    const res = await listPricedOrdersRequest.run({
      encounterIds: [encounterId],
      parentRequestId,
    });
    const detailRows: OrderChargeDetailRow[] = [];
    for (const group of res.groups ?? []) {
      for (const pricedOrder of group.pricedOrders ?? []) {
        detailRows.push(mapPricedOrderRowToChargeDetailRow(pricedOrder, parentRequestId));
      }
    }
    orderChargeDetailRows.value = detailRows;
  } finally {
    chargeDetailLoading.value = false;
  }
}

/** 打开指定医嘱的费用明细弹窗。 */
async function openOrderChargeDetailDialog(row: OrderBillingTableRow) {
  activeOrderBillingRow.value = row;
  chargeDetailDialogVisible.value = true;
  await loadOrderChargeDetail(row);
}

/** 关闭费用明细弹窗并清理当前医嘱上下文。 */
function closeOrderChargeDetailDialog() {
  chargeDetailDialogVisible.value = false;
  activeOrderBillingRow.value = null;
  orderChargeDetailRows.value = [];
}

/** 补费锚点：子行取 basedOnId，父行取 requestId */
function resolveSupplementAnchorRequestId(row: OrderBillingTableRow): string {
  if (row.isChild) {
    return preserveApiIdForRequest(row.basedOnId) ?? '';
  }
  return preserveApiIdForRequest(row.requestId) ?? '';
}

/** 从医嘱行打开补费弹窗。 */
function openOrderSupplementFeeDialog(row: OrderBillingTableRow) {
  if (row.isChild) {
    return;
  }
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  openBatchPricingDialog(row);
}

/** 当前选品类型对应的物理表名集合。 */
function resolveCatalogProductTableNames(): string[] {
  return projectType.value === 'device'
    ? [PRICING_PRODUCT_TABLE_DEVICE]
    : [PRICING_PRODUCT_TABLE_SERVICE];
}

/** 取当前登录机构，供划价目录解析耗材默认发放库房。 */
function resolvePricingOrganizationUnitId(): string | undefined {
  const orgRaw = authStore.getSelectedOrganization();
  return preserveApiIdForRequest(orgRaw) ?? undefined;
}

/** 加载划价选品目录（分页）。 */
async function loadPricingCatalog() {
  const patient = selectedBillingPatient.value;
  const encounterId = patient ? preserveApiIdForRequest(patient.id) : undefined;
  const patientId = patient ? preserveApiIdForRequest(patient.patientId) : undefined;
  const organizationUnitId = resolvePricingOrganizationUnitId();
  const res = await catalogRequest.run({
    encounterId,
    patientId,
    organizationUnitId,
    keyword: projectKeyword.value.trim() || undefined,
    pageNo: catalogPageNo.value,
    pageSize: CATALOG_PAGE_SIZE,
    productTableNames: resolveCatalogProductTableNames(),
  });
  const parsed = parsePagedRowsFromApiData<NurseStationPricingCatalogRowDto>(res);
  catalogRows.value = parsed.content.map((row) =>
    normalizePricingCatalogRow(row, projectType.value),
  );
  catalogTotal.value = parsed.totalCount;
}

/** 清空已划价列表勾选 */
function clearSelectedPricingRows() {
  selectedPricingRowIds.value = [];
}

/** 加载已划价列表。 */
async function loadPricedOrders() {
  clearSelectedPricingRows();
  if (encounterIds.value.length === 0) {
    pricingRows.value = [];
    pricingTableKey.value += 1;
    return;
  }
  const res = await listPricedOrdersRequest.run({
    encounterIds: encounterIds.value,
    ...mapPricingDateRangeToQuery(pricingTimeRange.value),
  });
  pricingRows.value = mapPricedOrderGroupsToTableRows(res.groups);
  pricingTableKey.value += 1;
}

/** 按时间区间查询已划价列表。 */
async function handleQueryPricingRows() {
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  await loadPricedOrders();
}

/** 关闭批量划价弹窗并清理临时选择状态。 */
function closeBatchPricingDialog() {
  batchDialogVisible.value = false;
  supplementFeeOrderRow.value = null;
}

/** 清空右侧已选项目。 */
function clearSelectedPricingItems() {
  selectedPricingMap.value.clear();
}

/** 点击左侧目录行后加入右侧清单，重复点击时数量加 1。 */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  const category = projectType.value;
  const catalog = normalizePricingCatalogRow(row, category);
  const rowKey = buildPricingCatalogRowKey(catalog, category);
  const existingItem = selectedPricingMap.value.get(rowKey);
  if (existingItem) {
    existingItem.quantity += 1;
    return;
  }
  selectedPricingMap.value.set(rowKey, {
    rowKey,
    catalog,
    category,
    quantity: 1,
  });
}

/** 修改项目数量时兜底修正非法值，保证最小数量为 1。 */
function handleQuantityChange(rowKey: string, value: number | undefined) {
  const target = selectedPricingMap.value.get(rowKey);
  if (!target) {
    return;
  }
  if (!value || value < 1) {
    target.quantity = 1;
    return;
  }
  target.quantity = value;
}

/** 删除右侧单个项目。 */
function removeSelectedPricingItem(rowKey: string) {
  selectedPricingMap.value.delete(rowKey);
}

/** 校验本次批量划价数据，确保至少有一个有效项目。 */
function validatePricingSubmit() {
  if (selectedPricingItems.value.length === 0) {
    ElMessage.warning('请先在左侧选择至少一个耗材或诊疗项目');
    return false;
  }
  if (selectedPricingItems.value.length > WRITE_BATCH_LIMIT) {
    ElMessage.warning(`单次划价最多 ${WRITE_BATCH_LIMIT} 行，请减少已选项目`);
    return false;
  }
  for (const row of selectedPricingItems.value) {
    if (row.quantity < 1) {
      ElMessage.warning(`项目「${row.catalog.name ?? '—'}」数量必须大于 0`);
      return false;
    }
    const unitPrice = Number(row.catalog.unitPrice ?? 0);
    if (unitPrice <= 0) {
      ElMessage.warning(`项目「${row.catalog.name ?? '—'}」单价无效，请检查价格维护`);
      return false;
    }
  }
  return true;
}

/** 划价写行缺失字段提示（发放库房走管理员配置口径）。 */
function formatPricingMissingFieldsMessage(itemName: string, missingFields: string[]): string {
  const name = itemName?.trim() || '—';
  if (missingFields.includes('发放库房')) {
    return `项目「${name}」未配置发放库房，请联系管理员配置`;
  }
  return `项目「${name}」缺少：${missingFields.join('、')}，请补全后重试`;
}

/** 已划价列表勾选变化 */
function handlePricingSelectionChange(ids: string[]) {
  selectedPricingRowIds.value = ids;
}

/** 取消划价：勾选待结算行整行还原（IF-IP-PRC-06） */
async function handleCancelPricing() {
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  const items = mapSelectedPricingRowsToCancelItems(
    pricingRows.value,
    new Set(selectedPricingRowIds.value),
  );
  if (items.length === 0) {
    ElMessage.warning('请先勾选待结算的划价记录');
    return;
  }
  if (items.length > PRICING_CANCEL_BATCH_LIMIT) {
    ElMessage.warning(`单次最多取消 ${PRICING_CANCEL_BATCH_LIMIT} 条划价记录`);
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认取消已选 ${items.length} 条划价记录？取消后费用与耗材发放将整行还原，补费父医嘱不变。`,
      '取消划价',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  const res = await cancelPricingRequest.run({ items });
  const count = res.processedCount ?? items.length;
  ElMessage.success(`已成功取消 ${count} 条划价记录`);
  clearSelectedPricingRows();
  await loadPricedOrders();
  if (activeTab.value === 'order-billing') {
    await loadOrderBillingRows();
  }
}

/** 提交批量划价或医嘱补费。 */
async function submitBatchPricing() {
  if (!canOperateBilling.value) {
    ElMessage.warning('请先在左侧单选 1 位患者后再提交划价');
    return;
  }
  if (!validatePricingSubmit()) {
    return;
  }
  const patient = selectedBillingPatient.value;
  if (!patient) {
    return;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId);
  const accountId = preserveApiIdForRequest(patient.accountId);
  const parentOrder = supplementFeeOrderRow.value;

  const items: NurseStationPricingWriteItemDto[] = [];
  for (const row of selectedPricingItems.value) {
    const writeOptions = {
      encounterId: encounterId ?? '',
      patientId: patientId ?? '',
      accountId: accountId ?? '',
      catalogRow: row.catalog,
      quantity: row.quantity,
      categoryHint: row.category,
    };
    if (parentOrder) {
      const supplementOptions = {
        ...writeOptions,
        parentRequestAnchor: {
          orderType: parentOrder.orderType,
          requestId: parentOrder.requestId,
        },
      };
      const missingFields = listSupplementPricingWriteItemMissingFields(supplementOptions);
      if (missingFields.length > 0) {
        ElMessage.warning(
          formatPricingMissingFieldsMessage(row.catalog.name ?? '—', missingFields),
        );
        return;
      }
      const writeItem = buildSupplementPricingWriteItem(supplementOptions);
      if (!writeItem) {
        return;
      }
      items.push(writeItem);
      continue;
    }

    const missingFields = listDirectPricingWriteItemMissingFields(writeOptions);
    if (missingFields.length > 0) {
      ElMessage.warning(formatPricingMissingFieldsMessage(row.catalog.name ?? '—', missingFields));
      return;
    }
    const writeItem = buildDirectPricingWriteItem(writeOptions);
    if (!writeItem) {
      return;
    }
    items.push(writeItem);
  }

  saving.value = true;
  try {
    const res = parentOrder
      ? await confirmSupplementPricingRequest.run({ items })
      : await confirmPricingRequest.run({ items });
    const count = res.processedCount ?? items.length;
    ElMessage.success(
      parentOrder ? `医嘱补费成功：${count} 个项目` : `批量划价成功：${count} 个项目`,
    );
    closeBatchPricingDialog();
    await loadPricedOrders();
    if (activeTab.value === 'order-billing') {
      await loadOrderBillingRows();
    }
  } finally {
    saving.value = false;
  }
}

/** 兼容 InputNumber 更新事件值类型并同步数量。 */
function onQuantityModelValueUpdate(rowKey: string, value: null | number | undefined) {
  handleQuantityChange(rowKey, value ?? undefined);
}

/** 切换选品类型后回到第一页并重新检索。 */
function handleProjectTypeChange() {
  catalogPageNo.value = 1;
  void loadPricingCatalog();
}

/** 目录翻页变更。 */
function handleCatalogPageChange(page: number) {
  catalogPageNo.value = page;
  void loadPricingCatalog();
}

/** 目录关键字查询。 */
function handleCatalogSearch() {
  catalogPageNo.value = 1;
  void loadPricingCatalog();
}

watch(
  () => props.patients,
  () => {
    pricingTimeRange.value = [];
    if (canOperateBilling.value) {
      void loadPricedOrders();
      if (activeTab.value === 'order-billing') {
        void loadOrderBillingRows();
      }
      if (activeTab.value === 'auto-roll') {
        void autoRollPanelRef.value?.reload();
      }
      return;
    }
    pricingRows.value = [];
    orderBillingRows.value = [];
    void autoRollPanelRef.value?.reload();
  },
  { deep: true, immediate: true },
);

watch(activeTab, (tab) => {
  if (tab === 'order-billing' && canOperateBilling.value) {
    void loadOrderBillingRows();
  }
  if (tab === 'auto-roll' && canOperateBilling.value) {
    void autoRollPanelRef.value?.reload();
  }
});
</script>

<template>
  <div class="inpatient-billing-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />
    <ElTabs v-model="activeTab" class="billing-tabs">
      <ElTabPane label="划价确费" name="pricing-confirmation" />
      <ElTabPane label="医嘱计费" name="order-billing" />
      <ElTabPane label="自动计费" name="auto-roll" />
    </ElTabs>

    <PricingConfirmationPanel
      v-if="activeTab === 'pricing-confirmation'"
      v-model:pricing-time-range="pricingTimeRange"
      :can-operate-billing="canOperateBilling"
      :cancel-loading="cancelPricingRequest.loading.value"
      :loading="listPricedOrdersRequest.loading.value"
      :pricing-rows="pricingRows"
      :selected-row-ids="selectedPricingRowIds"
      :table-key="pricingTableKey"
      @on-batch-pricing-click="openBatchPricingDialog"
      @on-cancel-pricing-click="handleCancelPricing"
      @on-pricing-query="handleQueryPricingRows"
      @selection-change="handlePricingSelectionChange"
    />

    <AutoRollPanel
      v-else-if="activeTab === 'auto-roll'"
      ref="autoRollPanelRef"
      :can-operate-billing="canOperateBilling"
      :patient="selectedBillingPatient"
    />

    <OrderBillingPanel
      v-else
      v-model:category="orderBillingCategory"
      v-model:keyword="orderBillingKeyword"
      v-model:dept="orderBillingDept"
      :dept-options="orderBillingDeptOptions"
      :filtered-rows="filteredOrderBillingRows"
      :loading="orderBillingLoading"
      @on-charge-detail-click="openOrderChargeDetailDialog"
      @on-query="handleQueryOrderBillingRows"
      @on-supplement-fee-click="openOrderSupplementFeeDialog"
    />

    <AppDialog
      v-model="chargeDetailDialogVisible"
      :title="activeOrderBillingRow ? `费用明细 - ${activeOrderBillingRow.content}` : '费用明细'"
      width="920px"
      @closed="closeOrderChargeDetailDialog"
    >
      <div class="order-charge-detail-dialog">
        <ElTable
          v-loading="chargeDetailLoading"
          :data="activeOrderChargeRows"
          border
          stripe
          max-height="420"
          row-key="id"
        >
          <ElTableColumn prop="itemName" label="收费项目" min-width="170" show-overflow-tooltip />
          <ElTableColumn prop="unitPrice" label="单价" min-width="110" />
          <ElTableColumn prop="usageCount" label="使用数量" min-width="80" align="center" />
          <ElTableColumn label="金额" min-width="90" align="right">
            <template #default="{ row }">
              {{ formatCurrency(row.amount) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="statusText" label="退费审核" min-width="90" align="center" />
        </ElTable>
        <div class="order-charge-detail-dialog__footer">
          <span>项目数：{{ activeOrderChargeRows.length }}</span>
          <span>合计：{{ formatCurrency(activeOrderChargeTotal) }}</span>
        </div>
      </div>
      <template #footer>
        <ElButton @click="closeOrderChargeDetailDialog">关闭</ElButton>
      </template>
    </AppDialog>

    <AppDialog
      v-model="batchDialogVisible"
      class="batch-pricing-dialog-wrapper"
      :title="batchPricingDialogTitle"
      width="1480px"
      @closed="closeBatchPricingDialog"
    >
      <div class="batch-pricing-dialog">
        <div class="batch-pricing-dialog__context">
          <ElAlert
            v-if="!canOperateBilling"
            title="当前不是单选患者状态，仅可查看弹窗；请先在左侧只勾选 1 位患者"
            type="warning"
            :closable="false"
            class="w-full"
          />
          <div>
            当前患者：{{
              selectedBillingPatient
                ? `${selectedBillingPatient.bedNo}${selectedBillingPatient.name}`
                : '-'
            }}
          </div>
          <div>模式：{{ isSupplementFeeDialog ? '医嘱补费' : '直接划价' }}</div>
          <div v-if="supplementFeeOrderRow">关联医嘱：{{ supplementFeeOrderRow.content }}</div>
        </div>

        <div class="batch-pricing-dialog__body">
          <section class="batch-pricing-panel batch-pricing-panel--left">
            <header class="batch-pricing-panel__header">项目检索</header>
            <div class="batch-pricing-filter">
              <ElRadioGroup v-model="projectType" @change="handleProjectTypeChange">
                <ElRadioButton label="device">耗材</ElRadioButton>
                <ElRadioButton label="service">诊疗项目</ElRadioButton>
              </ElRadioGroup>
              <ElInput
                v-model="projectKeyword"
                clearable
                placeholder="编码/名称"
                class="batch-pricing-filter__keyword"
                @keyup.enter="handleCatalogSearch"
              />
              <ElButton
                type="primary"
                :loading="catalogRequest.loading.value"
                @click="handleCatalogSearch"
              >
                查询
              </ElButton>
            </div>
            <ElTable
              v-loading="catalogRequest.loading.value"
              :data="filteredCatalogRows"
              border
              height="100%"
              :row-key="
                (row: NurseStationPricingCatalogRowDto) =>
                  buildPricingCatalogRowKey(row, projectType)
              "
              @row-click="handleCatalogRowClick"
            >
              <ElTableColumn prop="busNo" label="编码" min-width="92" />
              <ElTableColumn prop="name" label="项目名称" min-width="170" show-overflow-tooltip />
              <ElTableColumn prop="categoryCodeDictText" label="类别" min-width="80" />
              <ElTableColumn prop="unitName" label="单位" width="60" />
              <ElTableColumn label="单价" min-width="84" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(Number(row.unitPrice ?? 0)) }}
                </template>
              </ElTableColumn>
            </ElTable>
            <div class="batch-pricing-panel__pager">
              <ElPagination
                v-model:current-page="catalogPageNo"
                :page-size="CATALOG_PAGE_SIZE"
                :total="catalogTotal"
                layout="total, prev, pager, next"
                small
                @current-change="handleCatalogPageChange"
              />
            </div>
          </section>

          <section class="batch-pricing-panel batch-pricing-panel--right">
            <header class="batch-pricing-panel__header">
              已选项目
              <ElButton text type="danger" @click="clearSelectedPricingItems">清空</ElButton>
            </header>
            <ElTable :data="selectedPricingItems" border height="100%" row-key="rowKey">
              <ElTableColumn label="项目名称" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.catalog.name ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="单价" min-width="80" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(Number(row.catalog.unitPrice ?? 0)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="数量" width="92" align="center" class-name="qty-column">
                <template #default="{ row }">
                  <ElInputNumber
                    class="batch-pricing-qty-input"
                    :model-value="row.quantity"
                    :min="1"
                    :step="1"
                    :controls="false"
                    @update:model-value="onQuantityModelValueUpdate(row.rowKey, $event)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="金额" min-width="80" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(decimalMul(row.catalog.unitPrice ?? 0, row.quantity, 2)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="70" align="center">
                <template #default="{ row }">
                  <ElButton link type="danger" @click="removeSelectedPricingItem(row.rowKey)">
                    删除
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </section>
        </div>

        <div class="batch-pricing-dialog__footer">
          <div class="batch-pricing-summary">
            <span>已选项目：{{ selectedPricingItems.length }} 项</span>
            <span>总数量：{{ selectedQuantityTotal }}</span>
            <span>金额合计：{{ formatCurrency(selectedAmountTotal) }}</span>
          </div>
          <div class="batch-pricing-actions">
            <ElButton @click="closeBatchPricingDialog">取消</ElButton>
            <ElButton type="primary" :loading="saving" @click="submitBatchPricing">
              {{ isSupplementFeeDialog ? '确认补费' : '确认划价' }}
            </ElButton>
          </div>
        </div>
      </div>
    </AppDialog>
  </div>
</template>

<style scoped>
.inpatient-billing-page {
  background: #fff;
  height: 100%;
  padding: 0 8px 8px;
}

.billing-tabs {
  background: #fff;
  flex: 0 0 auto;
  margin-bottom: 8px;
}

.billing-tabs :deep(.el-tabs__header) {
  background: #fff;
  margin: 0;
}

.billing-tabs :deep(.el-tabs__nav-wrap) {
  background: #fff;
}

.billing-placeholder {
  align-items: center;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex: 1;
  justify-content: center;
}

.order-charge-detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-charge-detail-dialog__footer {
  align-items: center;
  color: var(--el-text-color-secondary);
  display: flex;
  font-size: 12px;
  gap: 14px;
  justify-content: flex-end;
  min-height: 34px;
}

.batch-pricing-dialog-wrapper {
  max-width: calc(100vw - 48px);
}

.batch-pricing-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.batch-pricing-dialog__context {
  align-items: center;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  display: flex;
  font-size: 13px;
  gap: 18px;
  min-height: 38px;
  padding: 0 12px;
}

.batch-pricing-dialog__body {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(520px, 1fr) minmax(640px, 1.35fr);
  height: 480px;
}

.batch-pricing-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.batch-pricing-panel__header {
  align-items: center;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  display: flex;
  font-size: 14px;
  font-weight: 600;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 10px;
}

.batch-pricing-filter {
  align-items: center;
  display: flex;
  gap: 8px;
  min-height: 52px;
  padding: 8px 10px;
}

.batch-pricing-filter__keyword {
  flex: 1;
}

.batch-pricing-panel__pager {
  display: flex;
  justify-content: flex-end;
  padding: 8px 10px 0;
}

.batch-pricing-dialog__footer {
  align-items: center;
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  justify-content: space-between;
  padding-top: 8px;
}

.batch-pricing-summary {
  color: var(--el-text-color-regular);
  display: flex;
  font-size: 13px;
  gap: 16px;
}

.batch-pricing-actions {
  display: flex;
  gap: 8px;
}

:deep(.qty-column .cell) {
  overflow: visible;
  text-overflow: clip;
}

:deep(.batch-pricing-qty-input.el-input-number) {
  width: 64px;
}
</style>
