import type { ApiOptionItem } from '#/types/api-response';

import type {
  GetInboundReceiptDetailResponse,
  InboundReceiptListRowDto,
  LoadInboundReceiptFormInitResponse,
  OptionDto,
} from '#/api/inventory/warehouse/inbound-receipt';

import {
  getInboundReceiptDetailApi,
  loadInboundReceiptFormInitApi,
  pageInboundReceiptsApi,
  WHS_IN_SUPPLY_TYPE_CODE,
} from '#/api/inventory/warehouse/inbound-receipt';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_WAREHOUSES_LABEL,
  coerceWorkbenchListLocationIdForApi,
  mapInitOptionsToCabinetRowsWithAll,
} from '#/utils/inventory/inventory-workbench-list';

function optionDtosToSupplierItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

function optionDtosToStatusItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

function mergePractitionerOptions(a?: OptionDto[], b?: OptionDto[]): ApiOptionItem[] {
  const out: ApiOptionItem[] = [];
  const seen = new Set<string>();
  for (const list of [a, b]) {
    for (const o of list ?? []) {
      const v = String(o.value ?? '').trim();
      if (!v || seen.has(v)) continue;
      seen.add(v);
      out.push({ label: String(o.label ?? ''), value: v });
    }
  }
  return out;
}

function mapInitFromInboundReceipt(
  data?: LoadInboundReceiptFormInitResponse,
): PurchaseInboundInitResponse {
  const warehouses = data?.warehouseLocationOptions ?? [];
  const cabinetListOptions = mapInitOptionsToCabinetRowsWithAll(
    warehouses.map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_WAREHOUSES_LABEL,
  );
  return {
    cabinetListOptions,
    practitionerOption: mergePractitionerOptions(
      data?.pharmacistOptions,
      data?.warehouseKeeperOptions,
    ),
    supplierOption: optionDtosToSupplierItems(data?.supplierOptions),
    supplyCategoryOptions: [],
    supplyStatusOptions: optionDtosToStatusItems(data?.statusOptions),
    supplyTypeOptions: [{ label: '药库进货', value: WHS_IN_SUPPLY_TYPE_CODE }],
    inboundItemTableTypeOptions: optionDtosToStatusItems(data?.inboundItemTableTypeOptions),
  };
}

function supplyStatusLabel(code?: string): string {
  if (!code) return '';
  const m: Record<string, string> = {
    PENDING_APPROVAL: '待审核',
    APPROVAL: '审核中',
    AGREE: '同意',
    REJECT: '驳回',
    WITHDRAWN: '已撤回',
    CANCELLED: '已作废',
    DRAFT: '草稿',
  };
  return m[code] ?? code;
}

function mapInboundListRow(r: InboundReceiptListRowDto): Record<string, unknown> {
  return {
    ...r,
    busNo: r.busNo,
    supplyBusNo: r.busNo,
    supplierId_dictText: r.supplierName ?? '',
    statusEnum_enumText: supplyStatusLabel(r.statusEnum),
    applicantId_dictText: r.creatorName ?? '',
    approverId_dictText: r.approverName ?? '',
    totalPrice: r.totalAmount,
    totalAmount: r.totalAmount,
  };
}

function pickDetailHead(d: GetInboundReceiptDetailResponse): Record<string, unknown> {
  return {
    busNo: d.busNo,
    supplyBusNo: d.busNo,
    originalBusNo: d.originalBusNo,
    typeEnum: d.typeEnum,
    statusEnum: d.statusEnum,
    statusEnum_enumText: supplyStatusLabel(d.statusEnum),
    supplierId: d.supplierId,
    sourceLocationId: d.sourceLocationId,
    purposeLocationId: d.purposeLocationId,
    locationId: d.purposeLocationId,
    sourceTypeEnum: d.sourceTypeEnum,
    purposeTypeEnum: d.purposeTypeEnum,
    categoryEnum: d.categoryEnum,
    rowVersion: d.rowVersion,
    applyTime: d.applyTime,
    applicantId: d.applicantId,
    approvalTime: d.approvalTime,
    reason: d.reason,
  };
}

function expandInboundDetailToRecords(
  d?: GetInboundReceiptDetailResponse,
): Record<string, unknown>[] {
  if (!d?.busNo) return [];
  const lines = d.lines ?? [];
  const head = pickDetailHead(d);
  if (lines.length === 0) {
    return [{ ...head }];
  }
  return lines.map((line) => ({ ...head, ...line }));
}

export interface PurchaseInboundInitResponse {
  cabinetListOptions: Record<string, unknown>[];
  practitionerOption: ApiOptionItem[];
  supplierOption: ApiOptionItem[];
  supplyCategoryOptions: ApiOptionItem[];
  supplyStatusOptions: ApiOptionItem[];
  supplyTypeOptions: ApiOptionItem[];
  inboundItemTableTypeOptions: ApiOptionItem[];
}

export interface PurchaseInboundPageQuery {
  locationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}

export interface PurchaseInboundPageRecords {
  records: Record<string, any>[];
  total?: number;
}

export interface PurchaseInboundDetailQuery {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}

export async function initPurchaseInboundApi() {
  const res = await loadInboundReceiptFormInitApi({});
  return mapInitFromInboundReceipt(res.data);
}

export async function pagePurchaseInboundListApi(params?: PurchaseInboundPageQuery) {
  const sid = coerceWorkbenchListLocationIdForApi(params?.locationId);
  const res = await pageInboundReceiptsApi({
    sourceLocationId: sid,
    keyword: params?.keyword?.trim() || undefined,
    pageNo: 1,
    pageSize: 200,
    typeEnum: WHS_IN_SUPPLY_TYPE_CODE,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<InboundReceiptListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const keywordFiltered = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params?.keyword,
  );
  const records = keywordFiltered.map((r) => mapInboundListRow(r as InboundReceiptListRowDto));
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

export async function pagePurchaseInboundDetailApi(params?: PurchaseInboundDetailQuery) {
  const busNo = params?.busNo?.trim();
  if (!busNo) {
    return { records: [], total: 0 };
  }
  const res = await getInboundReceiptDetailApi({ busNo });
  const records = expandInboundDetailToRecords(res.data);
  return { records, total: records.length };
}
