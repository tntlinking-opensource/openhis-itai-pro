/* eslint-disable @typescript-eslint/no-explicit-any -- mock 与后端契约宽松对齐 */
import type {
  CreatePurchaseParams,
  InitPharmacyWarehouseSuplyResponse,
  PageWarehousePurchaseParams,
  PageWarehousePurchaseResponse,
  PurchaseDetailResponse,
  UpdatePurchaseParams,
  WarehousePurchaseItem,
} from './purchase';

import type { GetInfoParams } from '#/types/api-response';

const MOCK_DELAY_MS = 150;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

/** 与 init 下拉一致：按仓库类型过滤（采购 edit 切换类型时拉仓） */
const WAREHOUSE_BY_TYPE: Record<string, any[]> = {
  '1': [
    { label: '一号库', value: 'loc-1', id: 'loc-1', name: '一号库' },
    { label: '二号库', value: 'loc-2', id: 'loc-2', name: '二号库' },
  ],
  '2': [{ label: '病区辅料库', value: 'loc-3', id: 'loc-3', name: '病区辅料库' }],
};

let busSeq = 4;

function nextBusNo(): string {
  const n = busSeq;
  busSeq += 1;
  return `PI-MOCK-${String(n).padStart(5, '0')}`;
}

function emptyPage(content: WarehousePurchaseItem[]): PageWarehousePurchaseResponse {
  const len = content.length;
  return {
    content,
    pageable: {
      offset: 0,
      paged: true,
      pageNumber: 1,
      pageSize: len || 20,
      sort: { empty: true, sorted: false, unsorted: true },
      unpaged: false,
    },
    last: true,
    totalCount: len,
    totalPages: 1,
    size: len || 20,
    number: 0,
    sort: { empty: true, sorted: false, unsorted: true },
    first: true,
    numberOfElements: len,
    empty: len === 0,
  };
}

function supplyLineTemplate(itemId: string, overrides: Record<string, any> = {}): any {
  const base =
    itemId === 'med-2'
      ? {
          name: '氯化钠注射液',
          unitCode: 'BOX',
          unitCode_dictText: '箱',
          minUnitCode: 'BOT',
          minUnitCode_dictText: '瓶',
          totalVolume: '500ml×20瓶',
          manufacturerText: '科伦药业',
          price: 3.2,
          itemQuantity: 5,
        }
      : {
          name: '阿莫西林胶囊',
          unitCode: 'BOX',
          unitCode_dictText: '盒',
          minUnitCode: 'PCS',
          minUnitCode_dictText: '粒',
          totalVolume: '0.25g×24粒',
          manufacturerText: '华北制药',
          price: 12.5,
          itemQuantity: 10,
        };
  const qty = Number(overrides.itemQuantity ?? base.itemQuantity);
  const price = Number(overrides.price ?? base.price);
  const totalPrice = qty * price;
  const totalSalePrice = totalPrice;
  return {
    id: overrides.id ?? `line-${itemId}-${Math.random().toString(36).slice(2, 8)}`,
    busNo: overrides.busNo ?? `SUB-${itemId}`,
    itemId,
    itemTable: '',
    lotNumber: overrides.lotNumber ?? 'LOT-MOCK-001',
    startTime: overrides.startTime ?? '2026-01-01',
    endTime: overrides.endTime ?? '2028-12-31',
    startDate: overrides.startDate ?? '2026-01-01',
    expirationDate: overrides.expirationDate ?? '2028-12-31',
    remake: '',
    defUnitCode: base.unitCode,
    ...base,
    ...overrides,
    itemQuantity: qty,
    price,
    totalPrice,
    totalSalePrice,
    locationStoreName: '',
  };
}

/** 列表行 + 详情共用内存 */
type StoredOrder = {
  detail: Record<string, any>;
  listRow: WarehousePurchaseItem;
};

const orders = new Map<string, StoredOrder>();

/** 列表卡片展示：明细进价合计（无明细则为空） */
function sumDetailSupplyTotalPrice(detail: Record<string, any>): number | undefined {
  const list = detail.supplyList;
  if (!Array.isArray(list) || list.length === 0) return undefined;
  const n = list.reduce((acc, line) => acc + Number(line?.totalPrice ?? 0), 0);
  return Number.isFinite(n) ? n : undefined;
}

function listRowFromDetail(detail: Record<string, any>): WarehousePurchaseItem {
  const statusEnum = String(detail.statusEnum ?? '1');
  const statusText =
    statusEnum === '3'
      ? '同意'
      : statusEnum === '2'
        ? '审核中'
        : statusEnum === '9'
          ? '已撤回'
          : '待审核';
  const totalPrice = sumDetailSupplyTotalPrice(detail);
  return {
    supplyBusNo: detail.supplyBusNo,
    practitionerId: detail.practitionerId ?? null,
    applyTime: detail.applyTime,
    supplierId: detail.supplierId,
    typeEnum: Number(detail.categoryEnum ?? detail.typeEnum ?? 13),
    locationTypeEnum: Number(detail.locationTypeEnum ?? 1),
    createTime: detail.applyTime,
    statusEnum,
    statusEnumText: statusText,
    ...(totalPrice !== undefined ? { totalPrice } : {}),
    typeEnum_enumText: detail.category_enumText ?? detail.typeEnum_enumText ?? '',
  } as WarehousePurchaseItem;
}

function seed() {
  const now = '2026-04-28 10:00:00';

  const d1: Record<string, any> = {
    supplyBusNo: 'PI-MOCK-00001',
    practitionerId: 'p1',
    applyTime: now,
    supplierId: 's1',
    categoryEnum: '13',
    category_enumText: '西药',
    locationTypeEnum: '1',
    locationType_enumText: '中心药库',
    locationId: 'loc-1',
    locationText: '一号库',
    statusEnum: '1',
    statusEnumText: '待审核',
    statusEnum_enumText: '待审核',
    supplyList: [supplyLineTemplate('med-1', { id: 'ln-1', busNo: 'SUB-1' })],
    startTime: '',
    endTime: '',
    invoiceNo: '',
    traceNo: '',
    approverId: '',
    approvalTime: '',
  };

  const d2: Record<string, any> = {
    supplyBusNo: 'PI-MOCK-00002',
    practitionerId: 'p2',
    applyTime: '2026-04-27 15:30:00',
    supplierId: 's2',
    categoryEnum: '13',
    category_enumText: '西药',
    locationTypeEnum: '1',
    locationType_enumText: '中心药库',
    locationId: 'loc-2',
    locationText: '二号库',
    statusEnum: '2',
    statusEnumText: '审核中',
    statusEnum_enumText: '审核中',
    supplyList: [supplyLineTemplate('med-2', { id: 'ln-2', busNo: 'SUB-2' })],
    startTime: '',
    endTime: '',
    invoiceNo: '',
    traceNo: '',
    approverId: '',
    approvalTime: '',
  };

  const d3: Record<string, any> = {
    supplyBusNo: 'PI-MOCK-00003',
    practitionerId: 'p1',
    applyTime: '2026-04-26 09:00:00',
    supplierId: 's1',
    categoryEnum: '13',
    category_enumText: '西药',
    locationTypeEnum: '1',
    locationType_enumText: '中心药库',
    locationId: 'loc-1',
    locationText: '一号库',
    statusEnum: '3',
    statusEnumText: '同意',
    statusEnum_enumText: '同意',
    supplyList: [
      supplyLineTemplate('med-1', { id: 'ln-3a', itemQuantity: 20 }),
      supplyLineTemplate('med-2', { id: 'ln-3b', itemQuantity: 8 }),
    ],
    startTime: '',
    endTime: '',
    invoiceNo: '',
    traceNo: '',
    approverId: 'p2',
    approverId_dictText: '采购员乙',
    approvalTime: '2026-04-26 14:00:00',
  };

  for (const d of [d1, d2, d3]) {
    const row = listRowFromDetail(d) as WarehousePurchaseItem & { locationId?: string };
    row.locationId = d.locationId;
    orders.set(d.supplyBusNo, { detail: { ...d }, listRow: row });
  }
}

seed();

export async function mockInitPharmacyWarehouseSuplyApi(): Promise<InitPharmacyWarehouseSuplyResponse> {
  return delay({
    statusOptions: [
      { label: '待审核', value: '1' },
      { label: '审核中', value: '2' },
      { label: '同意', value: '3' },
      { label: '已撤回', value: '9' },
    ],
    warehouseTypeOptions: [
      { label: '中心药库', value: '1' },
      { label: '二级库', value: '2' },
    ],
    warehouseOptions: [...(WAREHOUSE_BY_TYPE['1'] ?? [])],
    supplierOptions: [
      { label: '华润医药', value: 's1' },
      { label: '国药控股', value: 's2' },
    ],
    practitionerOptions: [
      { label: '采购员甲', value: 'p1' },
      { label: '采购员乙', value: 'p2' },
    ],
    categoryOptions: [
      { label: '西药', value: '13' },
      { label: '中成药', value: '14' },
    ],
  });
}

export async function mockPageWarehousePurchaseApi(
  params: PageWarehousePurchaseParams,
): Promise<PageWarehousePurchaseResponse> {
  const search = (params.keyword ?? '').trim().toLowerCase();
  const loc = params.locationId;

  let rows = [...orders.values()].map((o) => {
    const ext = o.listRow as WarehousePurchaseItem & { locationId?: string };
    return {
      ...ext,
      applicantId_dictText: ext.practitionerId === 'p2' ? '采购员乙' : '采购员甲',
      supplierId_dictText: ext.supplierId === 's2' ? '国药控股' : '华润医药',
      supplierName: ext.supplierId === 's2' ? '国药控股' : '华润医药',
      locationId: ext.locationId,
    } as WarehousePurchaseItem;
  });

  if (search) {
    rows = rows.filter((r) => r.supplyBusNo.toLowerCase().includes(search));
  }
  if (loc !== undefined && loc !== null && `${loc}` !== '') {
    const ls = String(loc);
    rows = rows.filter((r) => String((r as any).locationId ?? '') === ls);
  }

  rows.sort((a, b) => (a.supplyBusNo < b.supplyBusNo ? 1 : -1));
  return delay(emptyPage(rows));
}

export async function mockGetPurchaseById(params: GetInfoParams): Promise<PurchaseDetailResponse> {
  const busNo = params.busNo ?? params.id ?? '';
  const hit = orders.get(String(busNo));
  if (!hit) {
    throw new Error('单据不存在');
  }
  return delay({ ...(hit.detail as PurchaseDetailResponse) });
}

export async function mockCreatePurchase(params: CreatePurchaseParams): Promise<{ busNo: string }> {
  const busNo = nextBusNo();
  const applyTime = params.applyTime ?? '2026-05-01 10:00:00';
  const formCategory = (params as CreatePurchaseParams & { categoryEnum?: string }).categoryEnum;
  const categoryEnum = String(formCategory ?? params.typeEnum ?? '13');
  const locationTypeEnum = String(params.locationTypeEnum ?? '1');
  const detail: Record<string, any> = {
    supplyBusNo: busNo,
    practitionerId: params.practitionerId ?? 'p1',
    applyTime,
    supplierId: params.supplierId ?? 's1',
    categoryEnum,
    category_enumText: '西药',
    locationTypeEnum,
    locationType_enumText: '中心药库',
    locationId: params.locationId ?? 'loc-1',
    locationText: '一号库',
    statusEnum: '1',
    statusEnumText: '待审核',
    statusEnum_enumText: '待审核',
    supplyList: (params.supplyList ?? []).map((line: any, i: number) =>
      supplyLineTemplate(String(line.itemId ?? `med-${i + 1}`), {
        id: line.id ?? `new-${i}`,
        itemQuantity: line.itemQuantity,
        price: line.price,
        lotNumber: line.lotNumber,
      }),
    ),
    startTime: '',
    endTime: '',
    invoiceNo: '',
    traceNo: '',
    approverId: '',
    approvalTime: '',
  };
  if (detail.supplyList.length === 0) {
    detail.supplyList = [supplyLineTemplate('med-1', { id: `new-line-${busNo}` })];
  }

  const row = listRowFromDetail(detail) as WarehousePurchaseItem & { locationId?: string };
  row.locationId = detail.locationId;
  orders.set(busNo, { detail, listRow: row });
  return delay({ busNo });
}

export async function mockUpdatePurchase(params: UpdatePurchaseParams): Promise<unknown> {
  const hit = orders.get(params.supplyBusNo);
  if (!hit) {
    throw new Error('单据不存在');
  }
  const mergedLines = (params.supplyList ?? []).map((line: any, i: number) => ({
    ...supplyLineTemplate(String(line.itemId ?? 'med-1'), {}),
    ...line,
    id: line.id ?? `ln-u-${i}`,
  }));
  hit.detail = {
    ...hit.detail,
    practitionerId: params.practitionerId,
    applyTime: params.applyTime,
    supplierId: params.supplierId,
    categoryEnum: params.categoryEnum,
    locationTypeEnum: params.locationTypeEnum,
    locationId: params.locationId,
    supplyList: mergedLines.length > 0 ? mergedLines : hit.detail.supplyList,
    ...(params.isSubmit
      ? {
          statusEnum: '2',
          statusEnumText: '审核中',
          statusEnum_enumText: '审核中',
        }
      : {}),
  };
  hit.listRow = listRowFromDetail(hit.detail) as WarehousePurchaseItem & { locationId?: string };
  hit.listRow.locationId = hit.detail.locationId;
  orders.set(params.supplyBusNo, hit);
  return delay({});
}

export async function mockDeleteWarehousePurchaseApi(id: string): Promise<unknown> {
  orders.delete(String(id));
  return delay({});
}

/** 与工作站「提交审批 / 撤销审批」联动列表态（参数同 submitForReview purchase） */
export async function mockSubmitPurchaseForReview(params: {
  supplyBusNo: string;
  type: string;
}): Promise<unknown> {
  const hit = orders.get(params.supplyBusNo);
  if (!hit) {
    throw new Error('单据不存在');
  }
  if (params.type === 'submit') {
    hit.detail.statusEnum = '2';
    hit.detail.statusEnumText = '审核中';
    hit.detail.statusEnum_enumText = '审核中';
  } else {
    hit.detail.statusEnum = '9';
    hit.detail.statusEnumText = '已撤回';
    hit.detail.statusEnum_enumText = '已撤回';
    hit.detail.approverId = '';
    hit.detail.approvalTime = '';
  }
  hit.listRow = listRowFromDetail(hit.detail) as WarehousePurchaseItem & { locationId?: string };
  hit.listRow.locationId = hit.detail.locationId;
  return delay({});
}

/** requestClient + ApiResponse：切换仓库类型 */
export async function mockGetWarehouseByTypeForPurchase(type: string) {
  const data = WAREHOUSE_BY_TYPE[String(type)] ?? WAREHOUSE_BY_TYPE['1'] ?? [];
  return delay({
    code: 200,
    msg: 'ok',
    data,
  });
}
