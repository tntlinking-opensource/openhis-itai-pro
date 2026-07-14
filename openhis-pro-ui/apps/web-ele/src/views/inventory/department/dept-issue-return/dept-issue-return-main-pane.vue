<!--
  科室退领单右侧主区：关联原领用单 originalBusNo + 明细。
-->
<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { FormInstance } from 'element-plus';
import type {
  DeptIssueReturnDetailLineDto,
  DeptIssueReturnLineDto,
  LinkableIssueListRowDto,
  LinkableIssueReturnLineDto,
} from '#/api/inventory/department/dept-issue-return';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeDeptIssueReturnApi,
  createDeptIssueReturnApi,
  getDeptIssueReturnDetailApi,
  getLinkableIssueDetailApi,
  pageLinkableIssueDocumentsApi,
  rejectDeptIssueReturnApi,
  removeDeptIssueReturnApi,
  submitDeptIssueReturnApprovalApi,
  updateDeptIssueReturnApi,
  withdrawDeptIssueReturnApprovalApi,
} from '#/api/inventory/department/dept-issue-return';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  INV_DOC_UPSTREAM_HINT,
  hasNonEmptyBusNo,
  warnInventoryDocRestriction,
} from '#/utils/inventory/inventory-doc-restrictions';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
  isPresentItemId,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  type InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  requireAnySpecificInventoryLocationId,
  resolveFirstRealLocationIdFromOptionItems,
  resolveSpecificInventoryLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';

defineOptions({ name: 'InventoryDepartmentDeptIssueReturnMainPane' });

interface ReturnPreviewRow {
  id: string;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  lotNumber: string;
  traceNo: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  deptPharmacyOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  deptPharmacyOptions: () => [],
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

const productPickGateLocationId = computed(() =>
  resolveFirstRealLocationIdFromOptionItems(props.deptPharmacyOptions),
);

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    purposeLocationId: '科室药房',
  }),
);

const consumingOrganizationName = ref('');
const detailStatusEnum = ref<number | string | undefined>(undefined);
const saving = ref(false);

const linkIssueVisible = ref(false);
const linkIssueLoading = ref(false);
const linkIssueQueryParams = ref({ pageNo: 1, pageSize: 20, keyword: '' });
const linkIssueTotal = ref(0);
const linkIssueRecords = ref<LinkableIssueListRowDto[]>([]);

const linkIssueTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '领用单号', minWidth: 160, showOverflowTooltip: true },
      {
        prop: 'consumingOrganizationName',
        label: '领用科室',
        minWidth: 140,
        showOverflowTooltip: true,
      },
      { prop: 'approvalTime', label: '审批时间', minWidth: 160, slot: 'approvalTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '科室药房' },
  ),
);

const linkIssueLocationOptions = computed(() =>
  (props.deptPharmacyOptions ?? []).map((item) => ({
    value: item.value,
    label: item.label,
    locationId: item.value,
    displayName: item.label,
  })),
);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
);
const hasLinkedUpstream = computed(() => hasNonEmptyBusNo(originalBusNo.value));
const linesGridEditable = computed(() => documentEditable.value && hasLinkedUpstream.value);
const canEditOriginalBusNo = computed(() => documentEditable.value && isNewDraft.value);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stockOutStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo: Boolean(headerBusNo.value),
    isDraft: isNewDraft.value,
    status: normalizeStockOutStatusSort(detailStatusEnum.value),
  });
  const upstreamRequired = isNewDraft.value && !hasLinkedUpstream.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || saving.value || upstreamRequired,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || saving.value || upstreamRequired,
  };
});

function createEmptyRow(): ReturnPreviewRow {
  return {
    id: `new-${Date.now()}`,
    productName: '',
    itemQuantity: undefined,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    lotNumber: '',
    traceNo: '',
  };
}

function handleDeptIssueReturnUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
  });
}

function mapLinkableLineToGrid(ln: LinkableIssueReturnLineDto, idx: number): ReturnPreviewRow {
  const name = String(ln.productName ?? '').trim();
  const unitCode = String(ln.unitCode ?? '');
  const unitLabel = String(ln.unitCodeDictText ?? '').trim() || unitCode;
  const row: ReturnPreviewRow = {
    id: `ln-${idx}`,
    itemTable: ln.itemTable,
    itemId: ln.itemId,
    productName: name,
    itemQuantity: ln.returnableQuantity === undefined ? undefined : Number(ln.returnableQuantity),
    unitCode,
    unitCodeDictText: unitLabel,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
  };
  const catalog = (ln as { productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown } })
    .productCatalog;
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode,
    unitCodeDictText: unitLabel,
    productCatalog: catalog,
    unitBands: ln.unitBands,
    unitBandsJson: (ln as { unitBandsJson?: unknown }).unitBandsJson,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyInventoryRowQtyUnitList(row);
  return row;
}

function mapDetailLineToGrid(ln: DeptIssueReturnDetailLineDto, idx: number): ReturnPreviewRow {
  const row: ReturnPreviewRow = {
    id: `ln-${ln.id ?? idx}`,
    itemTable: ln.itemTable,
    itemId: ln.itemId,
    productName: String(ln.productName ?? ''),
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? ln.unitCode ?? ''),
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
  };
  const catalog = (ln as { productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown } })
    .productCatalog;
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: row.unitCodeDictText,
    productCatalog: catalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyInventoryRowQtyUnitList(row);
  return row;
}

function isReturnRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  return Number.isFinite(qty) && qty > 0 && !!String(row.unitCode ?? '').trim();
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  { field: 'productName', title: '项目名称', minWidth: 200, showOverflow: true },
  createInventoryQtyUnitColumn({
    title: '退领数量',
    onUnitChange: (row) => handleDeptIssueReturnUnitBandChanged(row as ReturnPreviewRow),
  }),
  { field: 'lotNumber', title: '批号', width: 120, showOverflow: true },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 120,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 64, clearable: true } },
  },
  {
    slots: { default: 'action' },
    title: '操作',
    align: 'center',
    fixed: 'right',
    field: 'action',
    width: 88,
  },
];

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: returnGridEdit,
  collectGridRows,
  patchGridEditConfig,
} = useInvDocDetailGrid<ReturnPreviewRow>({
  columns,
  documentEditable: () => linesGridEditable.value,
  beforeEditMethod: () => linesGridEditable.value,
  config: {
    rowKey: (row) => row.id,
    productField: 'productName',
    editFieldOrder: ['_qtyUnit', 'traceNo'],
    editableFields: new Set(['_qtyUnit', 'traceNo']),
    verticalNavFields: new Set(['_qtyUnit', 'traceNo']),
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnRowComplete,
  },
});

const { deleteItem } = useInvDocGridRowOps({
  documentEditable: () => linesGridEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: returnGridEdit,
  createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  purposeLocationId.value = undefined;
  consumingOrganizationName.value = '';
  clearInventoryHeadFormValidation(headFormRef.value);
  returnGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  returnGridEdit.resetFormMode();
  returnGridEdit.clearGridEdit();
  try {
    const res = await getDeptIssueReturnDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退领单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    consumingOrganizationName.value = String(data.consumingOrganizationName ?? '').trim();
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): DeptIssueReturnLineDto[] {
  return rows.map((r) => {
    const persisted = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: r.itemQuantity,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const unitCode =
      resolveInboundLinePersistUnitCodeForBands({
        bands: r.unitBands ?? [],
        legacyUnitCode: r.unitCode,
        selectedBandKind: r.selectedBandKind,
      }) ?? String(r.unitCode ?? '').trim();
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
    };
  });
}

async function persistReturn(): Promise<string | undefined> {
  if (!documentEditable.value) return undefined;
  const ob = originalBusNo.value?.trim();
  if (!ob) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.deptIssueReturn);
    return undefined;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return undefined;
  }
  const rows = collectGridRows();
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退领明细');
    return undefined;
  }
  for (const [i, r] of rows.entries()) {
    if (!isReturnRowComplete(r)) {
      ElMessage.warning(`第 ${i + 1} 行请补全项目、数量与单位`);
      return undefined;
    }
    const qty = Number(r.itemQuantity);
    if (!canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], qty, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行数量与单位无法换算，请检查产品单位维护`);
      return undefined;
    }
  }
  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    if (headerBusNo.value) {
      await updateDeptIssueReturnApi({
        busNo: headerBusNo.value,
        purposeLocationId: purposeLocationId.value as number | string,
        lines,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const res = await createDeptIssueReturnApi({
      originalBusNo: ob,
      purposeLocationId: purposeLocationId.value as number | string,
      lines,
    });
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) await loadDetail(no);
    emit('documentSaved', { busNo: no });
    return no;
  } catch {
    return undefined;
  } finally {
    saving.value = false;
  }
}

async function handleLinkIssueQuery(params: Record<string, unknown>) {
  Object.assign(linkIssueQueryParams.value, params);
  linkIssueQueryParams.value.pageNo = 1;
  await fetchLinkableIssues();
}

function handleLinkIssueReset() {
  linkIssueQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  void fetchLinkableIssues();
}

function handleLinkIssuePagination(pagination: { limit: number; page: number }) {
  linkIssueQueryParams.value.pageNo = pagination.page;
  linkIssueQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableIssues();
}

async function openLinkIssueDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联领用单');
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return;
  }
  const pid = requireAnySpecificInventoryLocationId(
    'deptPharmacy',
    purposeLocationId.value,
    productPickGateLocationId.value,
  );
  if (!pid) {
    return;
  }
  linkIssueQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  linkIssueVisible.value = true;
}

async function fetchLinkableIssues() {
  const pid = resolveSpecificInventoryLocationId(
    purposeLocationId.value,
    productPickGateLocationId.value,
  );
  if (!pid) {
    linkIssueRecords.value = [];
    linkIssueTotal.value = 0;
    return;
  }
  linkIssueLoading.value = true;
  try {
    const res = await pageLinkableIssueDocumentsApi({
      purposeLocationId: pid,
      keyword: String(linkIssueQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkIssueQueryParams.value.pageNo,
      pageSize: linkIssueQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<LinkableIssueListRowDto>(res.data);
    linkIssueRecords.value = content;
    linkIssueTotal.value = totalCount;
  } catch {
    linkIssueRecords.value = [];
    linkIssueTotal.value = 0;
  } finally {
    linkIssueLoading.value = false;
  }
}

async function applyLinkableIssue(row: LinkableIssueListRowDto) {
  const issueNo = row.busNo?.trim();
  if (!issueNo) return;
  const pid = resolveSpecificInventoryLocationId(row.purposeLocationId);
  if (!pid) {
    ElMessage.warning('领用单缺少科室药房，无法带入');
    return;
  }
  try {
    const res = await getLinkableIssueDetailApi({
      originalBusNo: issueNo,
      purposeLocationId: pid,
    });
    const data = res.data;
    if (!data?.originalBusNo) {
      ElMessage.warning('未获取到可退领明细');
      return;
    }
    originalBusNo.value = issueNo;
    consumingOrganizationName.value = String(data.consumingOrganizationName ?? '').trim();
    if (data.purposeLocationId) {
      purposeLocationId.value = data.purposeLocationId;
    }
    const gridRows = (data.lines ?? []).map((ln, i) => mapLinkableLineToGrid(ln, i));
    returnGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchGridEditConfig();
    });
    linkIssueVisible.value = false;
    ElMessage.success('已带入可退领明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

async function handleRemoveDoc() {
  if (isNewDraft.value) {
    try {
      await ElMessageBox.confirm('确认放弃该草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    resetDetailForCreate();
    emit('documentRemoved');
    return;
  }
  const no = headerBusNo.value;
  if (!no || !isStockOutDocumentRemovableStatus(detailStatusEnum.value)) return;
  try {
    await ElMessageBox.confirm('确认删除整张退领单？', '提示', { type: 'warning' });
    await removeDeptIssueReturnApi({ busNo: no });
    ElMessage.success('已删除');
    resetDetailForCreate();
    emit('documentRemoved');
  } catch {
    /* handled */
  }
}

interface DeptIssueReturnDraftSnap {
  originalBusNo?: string;
  purposeLocationId?: number | string;
  consumingOrganizationName: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<DeptIssueReturnDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        originalBusNo: originalBusNo.value,
        purposeLocationId: purposeLocationId.value,
        consumingOrganizationName: consumingOrganizationName.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        purposeLocationId.value = snap.purposeLocationId;
        consumingOrganizationName.value = snap.consumingOrganizationName;
        returnGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchGridEditConfig();
      },
      resetForCreate: () => {
        resetDetailForCreate();
      },
      loadByBusNo: async (busNo, gen) => {
        await loadDetail(busNo, gen);
      },
    },
  );
}

watch(
  () => props.resetKey,
  () => {
    parentSync.discardDraftSession();
  },
);

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);
</script>

<template>
  <div class="dept-issue-return-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
            v-bind="docActionFlags"
            @approve="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await agreeDeptIssueReturnApi({
                  busNo: no,
                  approverId: resolveLoginPractitionerIdForRequest(
                    userStore.userInfo as null | Record<string, unknown>,
                  ),
                  approvalClock: formatDateTime(new Date()).replace(' ', 'T'),
                });
                ElMessage.success('审批同意');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
            @reject="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                try {
                  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
                    inputPattern: /\S+/,
                    inputErrorMessage: '原因不能为空',
                  });
                  await rejectDeptIssueReturnApi({
                    busNo: no,
                    rejectReason: value,
                    approverId: resolveLoginPractitionerIdForRequest(
                      userStore.userInfo as null | Record<string, unknown>,
                    ),
                    approvalClock: formatDateTime(new Date()).replace(' ', 'T'),
                  });
                  ElMessage.success('已驳回');
                  await loadDetail(no);
                  emit('documentSaved', { busNo: no });
                } catch (e) {
                  if (e === 'cancel') return;
                }
              }
            "
            @remove-doc="handleRemoveDoc"
            @save="persistReturn"
            @save-and-submit="
              async () => {
                const no = await persistReturn();
                if (no) {
                  await submitDeptIssueReturnApprovalApi({ busNo: no });
                  ElMessage.success('已提交审批');
                  await loadDetail(no);
                  emit('documentSaved', { busNo: no });
                }
              }
            "
            @submit-approval="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await submitDeptIssueReturnApprovalApi({ busNo: no });
                ElMessage.success('已提交审批');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
            @withdraw="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await withdrawDeptIssueReturnApprovalApi({ busNo: no });
                ElMessage.success('已撤回');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
          />
        </div>
      </template>

      <div
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="returnGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <SectionTitle title="科室退领单" />
            <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form mt-1"
          >
            <div class="grid grid-cols-1 gap-x-2 sm:grid-cols-2 lg:grid-cols-4">
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="科室药房" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in deptPharmacyOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="领用科室">
                <ElInput :model-value="consumingOrganizationName" disabled />
              </ElFormItem>
              <ElFormItem label="关联领用" class="lg:col-span-2">
                <div class="flex w-full flex-wrap items-center gap-2">
                  <ElInput
                    :model-value="originalBusNo ?? ''"
                    class="min-w-[200px] flex-1"
                    readonly
                    disabled
                    placeholder="请通过「从领用单选择」关联"
                  />
                  <ElButton :disabled="!canEditOriginalBusNo" @click="openLinkIssueDialog">
                    从领用单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <ElAlert
          v-if="documentEditable && !hasLinkedUpstream"
          type="warning"
          :closable="false"
          show-icon
          class="shrink-0"
          :title="INV_DOC_UPSTREAM_HINT.deptIssueReturn"
        />

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="returnGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                :show-add="false"
                :disabled="!linesGridEditable"
                show-trace-scan
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!linesGridEditable"
                @click="deleteItem(row as ReturnPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </InvDocDetailGridShell>
      </div>
    </InvMainShell>

    <InvDocLinkPickerDialog
      v-model="linkIssueVisible"
      title="选择可退领的领用单"
      :table-data="linkIssueRecords"
      :loading="linkIssueLoading"
      :total="linkIssueTotal"
      :query-params="linkIssueQueryParams"
      :table-columns="linkIssueTableColumns"
      :location-options="linkIssueLocationOptions"
      keyword-placeholder="领用单号/关键字"
      @opened="fetchLinkableIssues"
      @query="handleLinkIssueQuery"
      @reset="handleLinkIssueReset"
      @pagination="handleLinkIssuePagination"
      @select="(row) => applyLinkableIssue(row as LinkableIssueListRowDto)"
    >
      <template #approvalTime="{ row }">
        {{
          (row as LinkableIssueListRowDto).approvalTime
            ? formatDateTime((row as LinkableIssueListRowDto).approvalTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkableIssue(row as LinkableIssueListRowDto)"
        >
          选用
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge ref="traceScanBridgeRef" scene="inventoryOut" />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>
