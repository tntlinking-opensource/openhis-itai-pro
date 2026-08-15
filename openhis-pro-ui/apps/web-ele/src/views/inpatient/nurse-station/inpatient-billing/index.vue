<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  OrderBillingCategory,
  OrderBillingTableRow,
  OrderChargeDetailRow,
  PricingCatalogCategory,
  PricingConfirmationTableRow,
} from './services/map-nurse-station-inpatient-billing';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';
import type {
  NurseStationPricingCancelItemDto,
  NurseStationPricingCatalogRowDto,
  NurseStationPricingWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, h, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElDatePicker, ElMessage, ElMessageBox } from 'element-plus';

import {
  httpCancelNurseStationPricing,
  httpChangeNurseStationPricingBusinessTime,
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
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE,
  isEncounterClinicalOrderMutationBlocked,
} from '#/utils/inpatient/encounter-clinical-order-gate';
import { validateChangeOpenedBusinessTime } from '#/utils/inpatient/encounter-order-time-bounds';

import {
  INPATIENT_BILLING_DEFAULT_TAB,
  INPATIENT_BILLING_TABS,
} from '#/views/inpatient/nurse-station/inpatient-billing/inpatient-billing-tab.config';

import AutoRollPanel from './components/auto-roll-panel.vue';
import BatchPricingDialog from './components/batch-pricing-dialog.vue';
import OrderBillingPanel from './components/order-billing-panel.vue';
import PricingConfirmationPanel from './components/pricing-confirmation-panel.vue';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  buildDirectPricingWriteItem,
  buildPricingCatalogRowKey,
  buildSupplementPricingWriteItem,
  convertPricingQtyBetweenBandKinds,
  filterOrderBillingTree,
  listDirectPricingWriteItemMissingFields,
  listSupplementPricingWriteItemMissingFields,
  mapPricedOrderGroupsToTableRows,
  mapPricedOrderRowToChargeDetailRow,
  mapPricingDateRangeToQuery,
  mapPricingTableRowToCancelItem,
  mapSelectedPricingRowsToCancelItems,
  mergeOrderBillingTreeFromApiResponses,
  normalizePricingCatalogRow,
  PRICING_CANCEL_BATCH_LIMIT,
  PRICING_PRODUCT_TABLE_DEVICE,
  PRICING_PRODUCT_TABLE_SERVICE,
  resolvePricingDefaultBandKind,
  resolvePricingDisplayUnitCode,
  resolvePricingDisplayUnitName,
  resolvePricingDisplayUnitPrice,
} from './services/map-nurse-station-inpatient-billing';
import {
  applyPricingOrderTemplateToSelectedItems,
  PricingOrderTemplateApplyError,
} from './services/pricing-order-template-apply.service';

interface SelectedPricingCatalogItem {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
  category: PricingCatalogCategory;
  quantity: number;
  /** 已选单位槽：耗材默认 SPLIT，可切 PACKAGE */
  selectedBandKind: string;
  /** 与 selectedBandKind 同口径的展示单价 */
  displayUnitPrice: number;
  displayUnitCode: string;
  displayUnitName: string;
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
const CATALOG_PAGE_SIZE = 30;

const pricingRows = ref<PricingConfirmationTableRow[]>([]);
const selectedPricingRowIds = ref<string[]>([]);
const pricingTableKey = ref(0);
const pricingTimeRange = ref<string[]>([]);
const activeTab = ref(INPATIENT_BILLING_DEFAULT_TAB);
const inpatientBillingTabs = INPATIENT_BILLING_TABS;
const autoRollPanelRef = ref<InstanceType<typeof AutoRollPanel> | null>(null);
const batchDialogVisible = ref(false);
const pricingOrderTemplateApplying = ref(false);
const projectKeyword = ref('');
const projectType = ref<PricingCatalogCategory>('device');
/** 批量划价弹窗：划价客户可见时刻（默认打开时取当前） */
const batchPricingTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'));
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
const changePricingTimeRequest = useRequest(httpChangeNurseStationPricingBusinessTime, {
  manual: true,
});
const authStore = useAuthStore();

const encounterIds = computed(() =>
  props.patients.map((patient) => String(patient.id).trim()).filter((id) => id.length > 0),
);

/** 将已勾选目录行按插入顺序整理成右侧可编辑清单。 */
const selectedPricingItems = computed(() => [...selectedPricingMap.value.values()]);

/** 汇总已选项目总数量。 */
const selectedQuantityTotal = computed(() =>
  selectedPricingItems.value.reduce((sum, item) => sum + item.quantity, 0),
);

/** 汇总已选项目总金额（6 位加总后 2 位展示）。 */
const selectedAmountTotal = computed(() =>
  sumMoneyThenSettle(
    selectedPricingItems.value.map((item) =>
      calcMoneyLineTotal(item.displayUnitPrice, item.quantity),
    ),
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
/** 批量划价弹窗模式文案。 */
const batchPricingModeLabel = computed(() =>
  isSupplementFeeDialog.value ? '医嘱补费' : '直接划价',
);
/** 住院记账要求左侧患者单选。 */
const billingPatientSingleSelected = computed(() => props.patients.length === 1);
/** 单选患者后可查看住院记账（不受出院结算轨限制）。 */
const canViewBilling = computed(() => billingPatientSingleSelected.value);
/** 选中患者处于出院结算轨：禁止划价/补费/冲销等写操作。 */
const isEncounterChargeMutationBlocked = computed(() =>
  billingPatientSingleSelected.value
    ? isEncounterClinicalOrderMutationBlocked(props.patients[0]?.statusEnum)
    : false,
);
/** 单选患者且未处于出院结算轨时可操作住院记账写路径。 */
const canOperateBilling = computed(
  () => billingPatientSingleSelected.value && !isEncounterChargeMutationBlocked.value,
);
/** 当前选中患者。 */
const selectedBillingPatient = computed(() =>
  billingPatientSingleSelected.value ? (props.patients[0] ?? null) : null,
);
/** 批量划价弹窗患者文案。 */
const batchPricingPatientLabel = computed(() =>
  selectedBillingPatient.value
    ? `${selectedBillingPatient.value.bedNo}${selectedBillingPatient.value.name}`
    : '-',
);
/** 页面顶部吸顶患者信息。 */
const stickyPatientInfo = computed(() => {
  if (!selectedBillingPatient.value) {
    if (props.patients.length === 0) {
      return null;
    }
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后查看计费）`,
      name: '请选择单一患者',
    });
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(selectedBillingPatient.value);
});

/** 将金额格式化为两位小数金额文案。 */
function formatCurrency(value: number) {
  return `￥${formatMoneyDisplay(value)}`;
}

/** 打开批量划价弹窗并重置本次项目选择（目录在 @opened 后再拉）。 */
function openBatchPricingDialog(orderRow?: OrderBillingTableRow) {
  if (!canOperateBilling.value) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  projectKeyword.value = '';
  projectType.value = 'device';
  // 每次打开弹窗默认当前时刻，允许护士改选后整批落库
  batchPricingTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss');
  clearSelectedPricingItems();
  supplementFeeOrderRow.value = orderRow ?? null;
  catalogPageNo.value = 1;
  catalogRows.value = [];
  catalogTotal.value = 0;
  batchDialogVisible.value = true;
}

/** 弹窗打开后加载选品目录。 */
function handleBatchPricingDialogOpened() {
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
  if (!canViewBilling.value) {
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
    if (isEncounterChargeMutationBlocked.value) {
      ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
      return;
    }
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  openBatchPricingDialog(row);
}

/** 当前选品类型对应的物理表名集合。 */
function resolveCatalogProductTableNames() {
  return projectType.value === 'device'
    ? [PRICING_PRODUCT_TABLE_DEVICE]
    : [PRICING_PRODUCT_TABLE_SERVICE];
}

/** 取当前患者入院科室，供划价目录按取药模板解析耗材房。 */
function resolvePricingOrganizationUnitId(): string | undefined {
  const patientOrg = preserveApiIdForRequest(selectedBillingPatient.value?.organizationId);
  if (patientOrg) {
    return patientOrg;
  }
  // 无患者科室时回落登录机构（诊疗选品/组套兜底）
  const orgRaw = authStore.getSelectedOrganization();
  return preserveApiIdForRequest(orgRaw) ?? undefined;
}

/** 加载划价选品目录（分页，默认 30 条）。 */
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
  if (!canViewBilling.value) {
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

/** 套用划价组套：预览结构 → prepicked 目录 → 写入右侧已选清单 */
async function handlePricingOrderTemplateSelect(header: OrderTemplateHeaderItem) {
  if (!canOperateBilling.value) {
    if (isEncounterChargeMutationBlocked.value) {
      ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
      return;
    }
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  if (pricingOrderTemplateApplying.value) {
    return;
  }
  const orderTemplateId = preserveApiIdForRequest(header.orderTemplateId);
  if (!orderTemplateId) {
    ElMessage.warning('组套无效，无法套用');
    return;
  }
  pricingOrderTemplateApplying.value = true;
  try {
    const result = await applyPricingOrderTemplateToSelectedItems({
      orderTemplateId,
      organizationUnitId: resolvePricingOrganizationUnitId(),
      encounterId: preserveApiIdForRequest(selectedBillingPatient.value?.id) ?? undefined,
      existingSelected: selectedPricingItems.value,
    });
    // 整表替换，保证 Map 变更触发已选清单重算
    selectedPricingMap.value = new Map(
      result.items.map((item) => [
        item.rowKey,
        {
          rowKey: item.rowKey,
          catalog: item.catalog,
          category: item.category,
          quantity: item.quantity,
          selectedBandKind: item.selectedBandKind,
          displayUnitPrice: item.displayUnitPrice,
          displayUnitCode: item.displayUnitCode,
          displayUnitName: item.displayUnitName,
        },
      ]),
    );
    if (result.missingProductNames.length > 0) {
      const previewNames = result.missingProductNames.slice(0, 3).join('、');
      const more =
        result.missingProductNames.length > 3 ? `等 ${result.missingProductNames.length} 项` : '';
      ElMessage.warning(
        `已套用组套「${result.templateName}」（${result.appliedLineCount} 项）；未找到：${previewNames}${more}`,
      );
    } else {
      ElMessage.success(`已套用组套「${result.templateName}」（${result.appliedLineCount} 项）`);
    }
  } catch (error) {
    if (error instanceof PricingOrderTemplateApplyError) {
      ElMessage.error(error.message);
      return;
    }
    ElMessage.error('组套套用失败，请稍后重试');
  } finally {
    pricingOrderTemplateApplying.value = false;
  }
}

/** 清空已选项目（整表替换以触发视图）。 */
function clearSelectedPricingItems() {
  selectedPricingMap.value = new Map();
}

/** 点击目录行后加入已选清单，重复点击时数量加 1。 */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  const category = projectType.value;
  const catalog = normalizePricingCatalogRow(row, category);
  const rowKey = buildPricingCatalogRowKey(catalog, category);
  // 不可变更新 Map，避免原地改 quantity 不触发 computed
  const nextMap = new Map(selectedPricingMap.value);
  const existingItem = nextMap.get(rowKey);
  if (existingItem) {
    nextMap.set(rowKey, {
      ...existingItem,
      quantity: existingItem.quantity + 1,
    });
    selectedPricingMap.value = nextMap;
    return;
  }
  const selectedBandKind = resolvePricingDefaultBandKind(catalog, category);
  nextMap.set(rowKey, {
    rowKey,
    catalog,
    category,
    quantity: 1,
    selectedBandKind,
    displayUnitPrice: resolvePricingDisplayUnitPrice(catalog, selectedBandKind),
    displayUnitCode: resolvePricingDisplayUnitCode(catalog, selectedBandKind),
    displayUnitName: resolvePricingDisplayUnitName(catalog, selectedBandKind),
  });
  selectedPricingMap.value = nextMap;
}

/** 切换已选行单位槽：换算数量并刷新展示单价。 */
function handleSelectedBandKindChange(rowKey: string, bandKind: string) {
  const target = selectedPricingMap.value.get(rowKey);
  if (!target) {
    return;
  }
  const nextKind = String(bandKind ?? '')
    .trim()
    .toUpperCase();
  if (!nextKind || nextKind === target.selectedBandKind) {
    return;
  }
  // 数量随单位槽换算，避免切换包装/拆零后数量口径错位
  const nextQty = convertPricingQtyBetweenBandKinds(
    target.quantity,
    target.selectedBandKind,
    nextKind,
    target.catalog,
  );
  const nextMap = new Map(selectedPricingMap.value);
  nextMap.set(rowKey, {
    ...target,
    quantity: nextQty,
    selectedBandKind: nextKind,
    displayUnitPrice: resolvePricingDisplayUnitPrice(target.catalog, nextKind),
    displayUnitCode: resolvePricingDisplayUnitCode(target.catalog, nextKind),
    displayUnitName: resolvePricingDisplayUnitName(target.catalog, nextKind),
  });
  selectedPricingMap.value = nextMap;
}

/** 修改项目数量时兜底修正非法值，保证最小数量为 1。 */
function handleQuantityChange(rowKey: string, value: number | undefined) {
  const target = selectedPricingMap.value.get(rowKey);
  if (!target) {
    return;
  }
  const nextQty = !value || value < 1 ? 1 : value;
  const nextMap = new Map(selectedPricingMap.value);
  nextMap.set(rowKey, {
    ...target,
    quantity: nextQty,
  });
  selectedPricingMap.value = nextMap;
}

/** 删除已选单个项目。 */
function removeSelectedPricingItem(rowKey: string) {
  const nextMap = new Map(selectedPricingMap.value);
  nextMap.delete(rowKey);
  selectedPricingMap.value = nextMap;
}

/** 校验本次批量划价数据，确保至少有一个有效项目。 */
function validatePricingSubmit() {
  if (selectedPricingItems.value.length === 0) {
    ElMessage.warning('请先选择至少一个耗材或诊疗项目');
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
    const unitPrice = Number(row.displayUnitPrice ?? 0);
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

/** 冲销：勾选待结算行整行冲销留痕 */
async function handleCancelPricing() {
  if (!canOperateBilling.value) {
    if (isEncounterChargeMutationBlocked.value) {
      ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
      return;
    }
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
    ElMessage.warning(`单次最多冲销 ${PRICING_CANCEL_BATCH_LIMIT} 条划价记录`);
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认冲销已选 ${items.length} 条划价记录？冲销后费用与耗材发放将整行对冲留痕，补费父医嘱不变。`,
      '冲销',
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
  ElMessage.success(`已成功冲销 ${count} 条划价记录`);
  clearSelectedPricingRows();
  await loadPricedOrders();
  if (activeTab.value === 'order-billing') {
    await loadOrderBillingRows();
  }
}

/** 将界面时间规范为后端 LocalDateTime（YYYY-MM-DD HH:mm:ss） */
function normalizePricingBusinessTime(raw: string): null | string {
  const s = String(raw ?? '')
    .trim()
    .replace(/\//g, '-');
  if (!s) {
    return null;
  }
  const matched = s.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})(?::(\d{2}))?$/);
  if (!matched) {
    return null;
  }
  const sec = matched[3] || '00';
  return `${matched[1]} ${matched[2]}:${sec}`;
}

/** 取当前患者入院/出院边界（缺字段时校验工具会跳过） */
function resolvePricingTimeBounds(): {
  admissionTime?: string;
  dischargeTime?: string;
} {
  const patient = selectedBillingPatient.value;
  return {
    admissionTime: String(patient?.admissionAt ?? '').trim() || undefined,
    dischargeTime: String(patient?.dischargeAt ?? '').trim() || undefined,
  };
}

/** 校验新划价时间非空，并按入院/出院边界提示 */
function validatePricingNewTime(raw: string): null | string {
  const newTime = normalizePricingBusinessTime(raw);
  if (!newTime) {
    ElMessage.warning('请选择有效的划价时间');
    return null;
  }
  const bounds = resolvePricingTimeBounds();
  const boundErr = validateChangeOpenedBusinessTime(newTime, bounds);
  if (boundErr) {
    // 工具文案为「开嘱」，划价场景替换为「划价」
    ElMessage.warning(boundErr.replace(/开嘱时间/g, '划价时间'));
    return null;
  }
  return newTime;
}

/** 勾选批量改划价时间：弹窗选同一时刻 */
async function promptBatchPricingBusinessTime(): Promise<null | string> {
  let picked = dayjs().format('YYYY-MM-DD HH:mm');
  try {
    await ElMessageBox({
      title: '批量改划价时间',
      message: h({
        setup() {
          const value = ref(picked);
          return () =>
            h('div', { class: 'billing-batch-pricing-time-prompt' }, [
              h(
                'p',
                { style: 'margin: 0 0 12px; color: var(--el-text-color-regular)' },
                '所选划价将统一改为以下时间（不得早于入院、不得晚于出院）。',
              ),
              h(ElDatePicker, {
                modelValue: value.value,
                'onUpdate:modelValue': (v: unknown) => {
                  const next = String(v ?? '').trim();
                  value.value = next;
                  picked = next;
                },
                type: 'datetime',
                valueFormat: 'YYYY-MM-DD HH:mm',
                format: 'YYYY-MM-DD HH:mm',
                timeFormat: 'HH:mm',
                clearable: false,
                teleported: true,
                style: 'width: 100%',
              }),
            ]);
        },
      }),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
    });
    return picked.trim() || null;
  } catch {
    return null;
  }
}

/** 提交改划价时间（批量 / 单条共用） */
async function submitChangePricingBusinessTime(
  items: NurseStationPricingCancelItemDto[],
  newTime: string,
) {
  const res = await changePricingTimeRequest.run({ items, newTime });
  if (res.success === false) {
    ElMessage.error(String(res.message ?? '').trim() || '修改划价时间失败');
    return;
  }
  ElMessage.success(String(res.message ?? '').trim() || '已修改划价时间');
  clearSelectedPricingRows();
  await loadPricedOrders();
  if (activeTab.value === 'order-billing') {
    await loadOrderBillingRows();
  }
}

/** 工具栏：勾选待结算行批量改划价时间 */
async function handleChangePricingTime() {
  if (!canOperateBilling.value) {
    if (isEncounterChargeMutationBlocked.value) {
      ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
      return;
    }
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
    ElMessage.warning(`单次最多修改 ${PRICING_CANCEL_BATCH_LIMIT} 条划价时间`);
    return;
  }
  const picked = await promptBatchPricingBusinessTime();
  if (!picked) {
    return;
  }
  const newTime = validatePricingNewTime(picked);
  if (!newTime) {
    return;
  }
  await submitChangePricingBusinessTime(items, newTime);
}

/** 表格「划价时间」就地改时：单条提交后刷新列表 */
async function handleInlineChangePricingTime(payload: {
  row: PricingConfirmationTableRow;
  newTime: string;
}) {
  if (!canOperateBilling.value) {
    if (isEncounterChargeMutationBlocked.value) {
      ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
      return;
    }
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  if (!payload.row.canCancel) {
    ElMessage.warning('仅待结算划价可修改时间');
    return;
  }
  const item = mapPricingTableRowToCancelItem(payload.row);
  if (!item) {
    ElMessage.warning('划价行信息不完整，请刷新后重试');
    return;
  }
  const newTime = validatePricingNewTime(payload.newTime);
  if (!newTime) {
    return;
  }
  await submitChangePricingBusinessTime([item], newTime);
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
  // 整批划价时刻：空则拒绝；入院/出院边界与改时同口径
  const pricingTime = validatePricingNewTime(batchPricingTime.value);
  if (!pricingTime) {
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
      selectedBandKind: row.selectedBandKind,
      displayUnitPrice: row.displayUnitPrice,
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
      ? await confirmSupplementPricingRequest.run({ items, pricingTime })
      : await confirmPricingRequest.run({ items, pricingTime });
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

/** 切换选品类型后写回类型、回到第一页并重新检索。 */
function handleProjectTypeChange(nextType: PricingCatalogCategory) {
  projectType.value = nextType;
  catalogPageNo.value = 1;
  void loadPricingCatalog();
}

/** 目录翻页变更。 */
function handleCatalogPageChange(page: number) {
  catalogPageNo.value = page;
  void loadPricingCatalog();
}

/** 目录关键字查询（回车 / 查询按钮 / 清空）。 */
function handleCatalogSearch() {
  catalogPageNo.value = 1;
  void loadPricingCatalog();
}

watch(
  () => props.patients,
  () => {
    pricingTimeRange.value = [];
    if (canViewBilling.value) {
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
  if (tab === 'pricing-confirmation' && canViewBilling.value) {
    void loadPricedOrders();
  }
  if (tab === 'order-billing' && canViewBilling.value) {
    void loadOrderBillingRows();
  }
  if (tab === 'auto-roll' && canViewBilling.value) {
    void autoRollPanelRef.value?.reload();
  }
});
</script>

<template>
  <div class="inpatient-billing-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />
    <ElAlert
      v-if="isEncounterChargeMutationBlocked"
      class="mx-2 mt-2 shrink-0"
      type="warning"
      :closable="false"
      show-icon
      :title="`${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（仅可查看，不可划价、补费或冲销）`"
    />
    <ElTabs v-model="activeTab" class="billing-tabs">
      <ElTabPane
        v-for="tab in inpatientBillingTabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
      />
    </ElTabs>

    <PricingConfirmationPanel
      v-if="activeTab === 'pricing-confirmation'"
      v-model:pricing-time-range="pricingTimeRange"
      :can-view-billing="canViewBilling"
      :can-operate-billing="canOperateBilling"
      :charge-mutation-blocked="isEncounterChargeMutationBlocked"
      :cancel-loading="cancelPricingRequest.loading.value"
      :change-pricing-time-loading="changePricingTimeRequest.loading.value"
      :loading="listPricedOrdersRequest.loading.value"
      :pricing-rows="pricingRows"
      :selected-row-ids="selectedPricingRowIds"
      :table-key="pricingTableKey"
      @on-batch-pricing-click="openBatchPricingDialog"
      @on-cancel-pricing-click="handleCancelPricing"
      @on-change-pricing-time-click="handleChangePricingTime"
      @on-inline-change-pricing-time="handleInlineChangePricingTime"
      @on-pricing-query="handleQueryPricingRows"
      @selection-change="handlePricingSelectionChange"
    />

    <AutoRollPanel
      v-else-if="activeTab === 'auto-roll'"
      ref="autoRollPanelRef"
      :can-view-billing="canViewBilling"
      :can-operate-billing="canOperateBilling"
      :patient="selectedBillingPatient"
    />

    <OrderBillingPanel
      v-else-if="activeTab === 'order-billing'"
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

    <BatchPricingDialog
      v-model="batchDialogVisible"
      :title="batchPricingDialogTitle"
      :can-operate-billing="canOperateBilling"
      :patient-label="batchPricingPatientLabel"
      :mode-label="batchPricingModeLabel"
      :supplement-order-content="supplementFeeOrderRow?.content ?? ''"
      :project-type="projectType"
      v-model:project-keyword="projectKeyword"
      v-model:batch-pricing-time="batchPricingTime"
      :catalog-rows="catalogRows"
      :catalog-loading="catalogRequest.loading.value"
      :catalog-page-no="catalogPageNo"
      :catalog-page-size="CATALOG_PAGE_SIZE"
      :catalog-total="catalogTotal"
      :selected-items="selectedPricingItems"
      :selected-quantity-total="selectedQuantityTotal"
      :selected-amount-total="selectedAmountTotal"
      :saving="saving"
      :template-applying="pricingOrderTemplateApplying"
      @opened="handleBatchPricingDialogOpened"
      @closed="closeBatchPricingDialog"
      @catalog-search="handleCatalogSearch"
      @catalog-page-change="handleCatalogPageChange"
      @project-type-change="handleProjectTypeChange"
      @catalog-row-click="handleCatalogRowClick"
      @quantity-change="onQuantityModelValueUpdate"
      @band-kind-change="handleSelectedBandKindChange"
      @remove-item="removeSelectedPricingItem"
      @clear-selected="clearSelectedPricingItems"
      @submit="submitBatchPricing"
      @cancel="closeBatchPricingDialog"
      @template-select="handlePricingOrderTemplateSelect"
    />
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
</style>
