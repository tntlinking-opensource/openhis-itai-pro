<script setup lang="ts">
import { computed } from 'vue';

import { pageProductsForDepartmentRequisitionApi } from '#/api/inventory/department/requisition';
import { pageProductsForDeptIssueApi } from '#/api/inventory/department/dept-issue';
import { pageProductsForDeptGainLossApi } from '#/api/inventory/department/gain-loss';
import { pageProductsForDeptReturnApi } from '#/api/inventory/department/dept-return';
import { pageProductsForDeptStocktakeApi } from '#/api/inventory/department/stocktake';
import { pageProductsForTransferOutApi as pageProductsForDepartmentTransferOutApi } from '#/api/inventory/department/transfer-out';
import { pageProductsForPharmacyGainLossApi } from '#/api/inventory/pharmacy/gain-loss';
import { pageProductsForPharmacyRequisitionApi } from '#/api/inventory/pharmacy/requisition';
import { pageProductsForPharmacyStockInApi } from '#/api/inventory/pharmacy/stock-in';
import { pageProductsForPharmacyStocktakeApi } from '#/api/inventory/pharmacy/stocktake';
import { pageProductsForTransferOutApi as pageProductsForPharmacyTransferOutApi } from '#/api/inventory/pharmacy/transfer-out';
import { pageProductsForGainLossApi } from '#/api/inventory/warehouse/gain-loss';
import { pageProductsForTransferOutApi } from '#/api/inventory/warehouse/transfer-out';
import { pageProductsForInboundReceiptApi } from '#/api/inventory/warehouse/inbound-receipt';
import { pageProductsForWarehouseStocktakeApi } from '#/api/inventory/warehouse/stocktake';
import { pageProductsForPurchaseApi } from '#/api/inventory/warehouse/purchase-order';
import { pageProductsForReturnToSupplierApi } from '#/api/inventory/warehouse/return-to-supplier';
import { pageProductsForReturnToWarehouseApi } from '#/api/inventory/pharmacy/return-to-warehouse';
import { pageProductsForReturnStockInApi } from '#/api/inventory/warehouse/return-stock-in';
import { pageProductsForStockOutApi } from '#/api/inventory/warehouse/stock-out';
import { pageGainLossMedicationApi } from '#/api/warehouse/gain-loss';
import { pagePurchaseOrderMedicationApi } from '#/api/warehouse/purchase-request';
import SelectTable from '#/components/select-table.vue';
import {
  INBOUND_RECEIPT_PRODUCT_PICK_TABLE_COLUMNS,
  mapInboundReceiptProductPickListRow,
} from '#/utils/inventory/inbound-receipt-product-pick-list';
import {
  INVENTORY_PRODUCT_PICK_TABLE_COLUMNS,
  mapInventoryProductPickListRow,
} from '#/utils/inventory/inventory-product-pick-list';
import { STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS } from '#/utils/inventory/stock-out-product-pick-list';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  isSpecificInventoryLocationId,
  preserveLocationIdForRequest,
} from '#/utils/inventory/inventory-workbench-list';

type PurchaseOrderMedicationPickRow = Record<string, unknown> & {
  id?: number | string;
  productName?: string;
};

interface Props {
  modelValue?: null | PurchaseOrderMedicationPickRow;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  pageSize?: number;
  query?: () => Promise<Record<string, unknown>>;
  inputHeight?: number | string;
  listSource?:
    | 'dept-gain-loss'
    | 'dept-issue'
    | 'dept-return'
    | 'dept-stocktake'
    | 'dept-transfer-out'
    | 'department-requisition'
    | 'gain-loss'
    | 'inbound-receipt'
    | 'inventory-products'
    | 'pharmacy-gain-loss'
    | 'pharmacy-requisition'
    | 'pharmacy-stock-in'
    | 'pharmacy-stocktake'
    | 'pharmacy-transfer-out'
    | 'purchase-order'
    | 'purchase-request'
    | 'return-stock-in'
    | 'return-to-supplier'
    | 'return-to-warehouse'
    | 'stock-out'
    | 'warehouse-gain-loss'
    | 'warehouse-stocktake'
    | 'warehouse-transfer-out';
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  placeholder: '请输入关键字检索药品',
  disabled: false,
  clearable: true,
  pageSize: 20,
  query: () => Promise.resolve({}),
  inputHeight: undefined,
  listSource: 'purchase-request',
});

const emit = defineEmits<{
  change: [value: PurchaseOrderMedicationPickRow | undefined];
  'update:modelValue': [value: PurchaseOrderMedicationPickRow | undefined];
}>();

const tableColumns = computed(() => {
  if (props.listSource === 'inbound-receipt' || props.listSource === 'purchase-order') {
    return [...INBOUND_RECEIPT_PRODUCT_PICK_TABLE_COLUMNS];
  }
  if (props.listSource === 'stock-out') {
    return [...STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS];
  }
  return [...INVENTORY_PRODUCT_PICK_TABLE_COLUMNS];
});

function mapInboundReceiptPickListResponse(data: unknown) {
  const { content: records, totalCount } = parsePagedRowsFromApiData<Record<string, unknown>>(data);
  return {
    content: records.map((r) => mapInboundReceiptProductPickListRow(r)),
    totalElements: totalCount,
  };
}

function resolvePurposeLocationId(q: Record<string, unknown>): number | string | undefined {
  const raw = q.purposeLocationId ?? q.locationId;
  if (!isSpecificInventoryLocationId(raw)) return undefined;
  return preserveLocationIdForRequest(raw) ?? String(raw).trim();
}

function resolveSourceLocationId(q: Record<string, unknown>): number | string | undefined {
  const raw = q.sourceLocationId;
  if (!isSpecificInventoryLocationId(raw)) return undefined;
  return preserveLocationIdForRequest(raw) ?? String(raw).trim();
}

function mapInventoryPickListResponse(data: unknown) {
  const { content: records, totalCount } = parsePagedRowsFromApiData<Record<string, unknown>>(data);
  return {
    content: records.map((r) => mapInventoryProductPickListRow(r)),
    totalElements: totalCount,
  };
}

async function handleRemoteMethod(params: { keyword?: string; pageNo: number; pageSize: number }) {
  const q = await props.query();

  if (props.listSource === 'purchase-order' || props.listSource === 'inventory-products') {
    const purposeLocationId = resolvePurposeLocationId(q);
    const productTableName = (q.productTableName as string | undefined)?.trim();
    if (purposeLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForPurchaseApi({
      purposeLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      supplierId: q.supplierId as number | string | undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      activeFlag: true,
    });
    return props.listSource === 'purchase-order'
      ? mapInboundReceiptPickListResponse(res.data)
      : mapInventoryPickListResponse(res.data);
  }

  if (props.listSource === 'inbound-receipt') {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    if (!productTableName) {
      return { content: [], totalElements: 0 };
    }
    const purposeLocationId = resolvePurposeLocationId(q);
    if (purposeLocationId === undefined) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForInboundReceiptApi({
      purposeLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      supplierId: q.supplierId as number | undefined,
      manufacturer: q.manufacturer as string | undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      activeFlag: q.activeFlag !== false,
    });
    return mapInboundReceiptPickListResponse(res.data);
  }

  if (props.listSource === 'stock-out') {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForStockOutApi({
      sourceLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      activeFlag: true,
    });
    return mapInventoryPickListResponse(res.data);
  }

  if (props.listSource === 'return-to-supplier') {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForReturnToSupplierApi({
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      supplierId: q.supplierId as number | undefined,
      includeInventory: true,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      activeFlag: true,
    });
    return mapInventoryPickListResponse(res.data);
  }

  if (
    props.listSource === 'return-to-warehouse' ||
    props.listSource === 'return-stock-in' ||
    props.listSource === 'dept-return'
  ) {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const common = {
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      includeInventory: true,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      activeFlag: true,
    };
    const res =
      props.listSource === 'return-to-warehouse'
        ? await pageProductsForReturnToWarehouseApi(common)
        : props.listSource === 'return-stock-in'
          ? await pageProductsForReturnStockInApi(common)
          : await pageProductsForDeptReturnApi(common);
    return mapInventoryPickListResponse(res.data);
  }

  if (props.listSource === 'pharmacy-stock-in') {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForPharmacyStockInApi({
      sourceLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    });
    return mapInventoryPickListResponse(res.data);
  }

  if (
    props.listSource === 'pharmacy-requisition' ||
    props.listSource === 'department-requisition'
  ) {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const common = {
      sourceLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    };
    const res =
      props.listSource === 'pharmacy-requisition'
        ? await pageProductsForPharmacyRequisitionApi(common)
        : await pageProductsForDepartmentRequisitionApi(common);
    return mapInventoryPickListResponse(res.data);
  }

  if (
    props.listSource === 'pharmacy-transfer-out' ||
    props.listSource === 'warehouse-transfer-out' ||
    props.listSource === 'dept-transfer-out'
  ) {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const sourceLocationId = resolveSourceLocationId(q);
    if (sourceLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const common = {
      sourceLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    };
    const res =
      props.listSource === 'pharmacy-transfer-out'
        ? await pageProductsForPharmacyTransferOutApi(common)
        : props.listSource === 'dept-transfer-out'
          ? await pageProductsForDepartmentTransferOutApi(common)
          : await pageProductsForTransferOutApi(common);
    return mapInventoryPickListResponse(res.data);
  }

  if (props.listSource === 'dept-issue') {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const purposeLocationId = resolvePurposeLocationId(q);
    if (purposeLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res = await pageProductsForDeptIssueApi({
      purposeLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    });
    return mapInventoryPickListResponse(res.data);
  }

  if (
    props.listSource === 'warehouse-gain-loss' ||
    props.listSource === 'pharmacy-gain-loss' ||
    props.listSource === 'dept-gain-loss'
  ) {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const purposeLocationId = resolvePurposeLocationId(q);
    if (purposeLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const common = {
      purposeLocationId,
      productTableName,
      keyword: params.keyword?.trim() || undefined,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    };
    const res =
      props.listSource === 'pharmacy-gain-loss'
        ? await pageProductsForPharmacyGainLossApi(common)
        : props.listSource === 'dept-gain-loss'
          ? await pageProductsForDeptGainLossApi(common)
          : await pageProductsForGainLossApi(common);
    return mapInventoryPickListResponse(res.data);
  }

  if (
    props.listSource === 'warehouse-stocktake' ||
    props.listSource === 'pharmacy-stocktake' ||
    props.listSource === 'dept-stocktake'
  ) {
    const productTableName = (q.productTableName as string | undefined)?.trim();
    const purposeLocationId = resolvePurposeLocationId(q);
    if (purposeLocationId === undefined || !productTableName) {
      return { content: [], totalElements: 0 };
    }
    const res =
      props.listSource === 'pharmacy-stocktake'
        ? await pageProductsForPharmacyStocktakeApi({
            purposeLocationId,
            productTableName,
            keyword: params.keyword?.trim() || undefined,
            pageNo: params.pageNo,
            pageSize: params.pageSize,
          })
        : props.listSource === 'dept-stocktake'
          ? await pageProductsForDeptStocktakeApi({
              purposeLocationId,
              productTableName,
              keyword: params.keyword?.trim() || undefined,
              pageNo: params.pageNo,
              pageSize: params.pageSize,
            })
          : await pageProductsForWarehouseStocktakeApi({
              purposeLocationId,
              productTableName,
              keyword: params.keyword?.trim() || undefined,
              pageNo: params.pageNo,
              pageSize: params.pageSize,
            });
    return mapInventoryPickListResponse(res.data);
  }

  const payload = {
    supplierId: q.supplierId as string | undefined,
    locationId: q.locationId as string | undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    keyword: params.keyword,
    typeEnum: (q.typeEnum as number) ?? 13,
  };
  const raw =
    props.listSource === 'gain-loss'
      ? await pageGainLossMedicationApi(payload)
      : await pagePurchaseOrderMedicationApi(payload);
  const { content, totalCount } = parsePagedRowsFromApiData<Record<string, unknown>>(raw);
  return {
    content: content.map((r) => {
      const productBusNo = r.productBusNo ?? r.busNo;
      return productBusNo === undefined || productBusNo === null
        ? r
        : { ...r, productBusNo: String(productBusNo) };
    }),
    totalElements: totalCount,
  };
}

/** 列表行即明细：选中后直接作为 modelValue（无需再请求详情） */
async function handleBeforeClose(item: Record<string, unknown>) {
  emit('update:modelValue', item as PurchaseOrderMedicationPickRow);
  emit('change', item as PurchaseOrderMedicationPickRow);
  return true;
}

function handleChange(value: null | string | string[] | undefined) {
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', undefined);
    emit('change', undefined);
  }
}

/** 编辑格 modelValue 可能是选品全量行（含 productName）或明细回填的 { id, name }。 */
function selectedProductName(): string {
  const v = props.modelValue;
  if (!v) return '';
  const fromProductName = v.productName;
  if (fromProductName !== undefined && fromProductName !== null) {
    const s = String(fromProductName).trim();
    if (s) return s;
  }
  const fromName = (v as { name?: unknown }).name;
  if (fromName !== undefined && fromName !== null) {
    const s = String(fromName).trim();
    if (s) return s;
  }
  return '';
}
</script>

<template>
  <SelectTable
    :model-value="
      modelValue?.id === undefined || modelValue?.id === null || modelValue.id === ''
        ? undefined
        : String(modelValue.id)
    "
    :remote-method="handleRemoteMethod"
    :columns="tableColumns"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :page-size="pageSize"
    value-key="id"
    label-key="productName"
    :selected-label="selectedProductName()"
    :before-close="handleBeforeClose"
    :input-height="inputHeight"
    :panel-width="1200"
    @update:model-value="handleChange"
    @change="handleChange"
  />
</template>
