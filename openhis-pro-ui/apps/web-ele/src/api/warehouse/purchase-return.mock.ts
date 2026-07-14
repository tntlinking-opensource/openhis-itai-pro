/* eslint-disable @typescript-eslint/no-explicit-any -- mock 与后端字段宽松对齐 */
import type { PurchaseReturnInitResponse } from './purchase-return';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

type CabinetOption = import('#/types/api-response').ApiOptionItem & {
  id?: string;
  name?: string;
};

const MOCK_DELAY_MS = 150;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

const PRACTITIONER_LABEL: Record<string, string> = {
  p1: '采购员甲',
  p2: '采购员乙',
};

const SUPPLIER_LABEL: Record<string, string> = {
  s1: '华润医药',
  s2: '国药控股',
};

function mapApplicant(id?: string): string {
  if (!id) return '';
  return PRACTITIONER_LABEL[String(id)] ?? String(id);
}

function mapSupplier(id?: string): string {
  if (!id) return '';
  return SUPPLIER_LABEL[String(id)] ?? String(id);
}

/** 选药品 / 明细共用目录（含退货表格 rebuild 所需字段） */
const MEDICATION_CATALOG: Record<string, any>[] = [
  {
    id: 'med-rt-1',
    busNo: 'YP-RT-001',
    name: '阿莫西林胶囊',
    price: 12.5,
    retailPrice: 18,
    unitCode: 'BOX',
    unitCode_dictText: '盒',
    minUnitCode: 'PCS',
    minUnitCode_dictText: '粒',
    maxUnitCode: 'BOX',
    maxUnitCode_dictText: '盒',
    totalVolume: '0.25g×24粒',
    specificationInventory: 240,
    batchInventory: 48,
    manufacturerText: '华北制药',
    approvalNumber: '国药准字H13000001',
    remake: '',
    partPercent: 24,
    typeEnum: 13,
    supplierIds: ['s1', 's2'],
    locationIds: ['loc-1'],
    productionDate: '2026-01-10',
    expirationDate: '2028-01-09',
    unitList: [
      { value: 'BOX', value_dictText: '盒' },
      { value: 'PCS', value_dictText: '粒' },
    ],
  },
  {
    id: 'med-rt-2',
    busNo: 'YP-RT-002',
    name: '氯化钠注射液',
    price: 3.2,
    retailPrice: 5,
    unitCode: 'BOX',
    unitCode_dictText: '箱',
    minUnitCode: 'BOT',
    minUnitCode_dictText: '瓶',
    maxUnitCode: 'BOX',
    maxUnitCode_dictText: '箱',
    totalVolume: '500ml×20瓶',
    specificationInventory: 100,
    batchInventory: 40,
    manufacturerText: '科伦药业',
    approvalNumber: '国药准字H51000002',
    remake: '',
    partPercent: 20,
    typeEnum: 13,
    supplierIds: ['s1'],
    locationIds: ['loc-1'],
    productionDate: '2025-06-01',
    expirationDate: '2027-05-31',
    unitList: [
      { value: 'BOX', value_dictText: '箱' },
      { value: 'BOT', value_dictText: '瓶' },
    ],
  },
];

type MockOrder = {
  head: Record<string, any>;
  lines: Record<string, any>[];
};

const returnOrders = new Map<string, MockOrder>();
const stockInOrders = new Map<string, MockOrder>();

let busSeq = 2;

function nextBusNo(): string {
  busSeq += 1;
  return `RT-MOCK-${String(busSeq).padStart(5, '0')}`;
}

function headCommon(bus: string, overrides: Record<string, any>): Record<string, any> {
  return {
    supplyBusNo: bus,
    busNo: bus,
    phone: '010-88886666',
    practitionerId: 'p1',
    invoiceNo: '',
    originalBusNo: undefined,
    typeEnum: 20,
    typeEnum_enumText: '采购退货',
    ...overrides,
  };
}

function mergeReturnDetail(
  head: Record<string, any>,
  line: Record<string, any>,
): Record<string, any> {
  const { busNo: _lb, supplyBusNo: _ls, ...lineRest } = line;
  void _lb;
  void _ls;
  return {
    ...head,
    ...lineRest,
    busNo: head.busNo ?? head.supplyBusNo,
    supplyBusNo: head.supplyBusNo,
  };
}

function catalogToDetailLine(
  head: Record<string, any>,
  c: Record<string, any>,
  qty: number,
  extras: Record<string, any>,
): Record<string, any> {
  const base = {
    ...structuredClone(c),
    itemId: c.id,
    itemBusNo: c.busNo,
    itemName: c.name,
    unitCodeDictText: c.unitCode_dictText,
    itemQuantity: qty,
    totalPrice: calcMoneyLineTotal(c.price, qty),
    totalPriceMask: formatMoneyDisplay(calcMoneyLineTotal(c.price, qty)),
    totalRetailPrice: calcMoneyLineTotal(c.retailPrice, qty),
    totalRetailPriceMask: formatMoneyDisplay(calcMoneyLineTotal(c.retailPrice, qty)),
    packagingConditionEnum: 1,
    acceptanceResultEnum: 1,
    lotNumber: `LOT-${c.busNo}-001`,
    occurrenceTime: '2026-12-31',
    endTime: c.expirationDate,
    startTime: c.productionDate,
    traceNo: '',
    traceNoUnitCode: c.unitCode,
    isEditing: false,
    isViewing: true,
    ...extras,
  };
  return mergeReturnDetail(head, base);
}

function seedReturnOrders(): void {
  const h1 = headCommon('RT-MOCK-00001', {
    applicantId: 'p1',
    applicantId_dictText: mapApplicant('p1'),
    supplierId: 's1',
    supplierId_dictText: mapSupplier('s1'),
    statusEnum: 2,
    statusEnum_enumText: '待审核',
    applyTime: '2026-04-28 09:30:00',
    sourceLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '近效期退货',
    approvalTime: undefined,
    approverId: undefined,
  });

  const c0 = MEDICATION_CATALOG[0]!;
  returnOrders.set('RT-MOCK-00001', {
    head: h1,
    lines: [catalogToDetailLine(h1, c0, 10, {})],
  });

  const h2 = headCommon('RT-MOCK-00002', {
    applicantId: 'p2',
    applicantId_dictText: mapApplicant('p2'),
    supplierId: 's2',
    supplierId_dictText: mapSupplier('s2'),
    statusEnum: 3,
    statusEnum_enumText: '同意',
    applyTime: '2026-04-20 14:00:00',
    sourceLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '已审批退货',
    approvalTime: '2026-04-21 10:00:00',
    approverId: 'p1',
  });

  const c1 = MEDICATION_CATALOG[1]!;
  returnOrders.set('RT-MOCK-00002', {
    head: h2,
    lines: [catalogToDetailLine(h2, c1, 4, {})],
  });
}

/** 进货单导入：审核通过的进货单（示意） */
function seedStockInOrders(): void {
  const bus = 'SI-MOCK-00001';
  const h = {
    supplyBusNo: bus,
    busNo: bus,
    applicantId: 'p1',
    applicantId_dictText: mapApplicant('p1'),
    supplierId: 's1',
    supplierId_dictText: mapSupplier('s1'),
    statusEnum: 3,
    statusEnum_enumText: '同意',
    typeEnum: 11,
    typeEnum_enumText: '采购入库',
    applyTime: '2026-04-15 11:00:00',
    sourceLocationId: 'loc-1',
    phone: '021-66668888',
    practitionerId: 'p1',
    categoryEnum: '1',
    reason: '进货',
    invoiceNo: 'INV-SI-001',
    approvalTime: '2026-04-16 09:00:00',
    approverId: 'p2',
  };

  const c0 = MEDICATION_CATALOG[0]!;
  stockInOrders.set(bus, {
    head: h,
    lines: [catalogToDetailLine(h, c0, 20, { originalBusNo: bus })],
  });
}

seedReturnOrders();
seedStockInOrders();

export async function mockInitPurchaseReturn(): Promise<PurchaseReturnInitResponse> {
  const cabinetListOptions: CabinetOption[] = [
    { label: '西药库', value: 'loc-1', id: 'loc-1', name: '西药库' },
    { label: '中药库', value: 'loc-2', id: 'loc-2', name: '中药库' },
  ];
  return delay({
    cabinetListOptions,
    practitionerOption: [
      { label: '采购员甲', value: 'p1' },
      { label: '采购员乙', value: 'p2' },
    ],
    supplierOption: [
      { label: '华润医药', value: 's1', phone: '010-11112222' },
      { label: '国药控股', value: 's2', phone: '021-33334444' },
    ],
    supplyCategoryOptions: [
      { label: '常规采购', value: '1' },
      { label: '紧急采购', value: '2' },
    ],
    supplyStatusOptions: [
      { label: '待审核', value: '2' },
      { label: '同意', value: '3' },
    ],
    supplyTypeOptions: [{ label: '采购退货', value: '20' }],
    packagingConditionOptions: [
      { label: '完好', value: '1' },
      { label: '破损', value: '2' },
    ],
    acceptanceResultoryOptions: [
      { label: '合格', value: '1' },
      { label: '不合格', value: '2' },
    ],
  });
}

export async function mockPagePurchaseReturnList(params?: Record<string, any>): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const locationId = params?.locationId;
  const keyword = String(params?.keyword ?? '')
    .trim()
    .toLowerCase();
  let list = [...returnOrders.values()].map((o) => o.head);
  if (locationId !== undefined && locationId !== null && `${locationId}` !== '') {
    list = list.filter((h) => String(h.sourceLocationId) === String(locationId));
  }
  if (keyword) {
    list = list.filter(
      (h) =>
        String(h.supplyBusNo).toLowerCase().includes(keyword) ||
        String(h.busNo).toLowerCase().includes(keyword),
    );
  }
  list.sort((a, b) => String(b.supplyBusNo).localeCompare(String(a.supplyBusNo)));
  return delay({ records: list, total: list.length });
}

export async function mockPagePurchaseReturnDetail(params?: Record<string, any>): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const busNo = params?.busNo ? String(params.busNo) : '';
  const pageNo = Math.max(1, Number(params?.pageNo ?? 1));
  const pageSize = Number(params?.pageSize ?? 10);
  const order = busNo ? returnOrders.get(busNo) : undefined;
  if (!order) {
    return delay({ records: [], total: 0 });
  }
  const lines = order.lines.map((line) => ({ ...mergeReturnDetail(order.head, line) }));
  const start = (pageNo - 1) * pageSize;
  const slice = lines.slice(start, start + pageSize);
  return delay({ records: slice, total: lines.length });
}

export async function mockPagePurchaseReturnMedication(params?: Record<string, any>): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const supplierId = params?.supplierId;
  const locationId = params?.locationId;
  const keyword = String(params?.keyword ?? '')
    .trim()
    .toLowerCase();
  const pageNo = Math.max(1, Number(params?.pageNo ?? 1));
  const pageSize = Number(params?.pageSize ?? 50);

  let rows = MEDICATION_CATALOG.filter((m) => {
    if (supplierId !== undefined && supplierId !== null && `${supplierId}` !== '') {
      const ids = m.supplierIds as string[];
      if (!ids.includes(String(supplierId))) return false;
    }
    if (locationId !== undefined && locationId !== null && `${locationId}` !== '') {
      const locs = m.locationIds as string[];
      if (!locs.includes(String(locationId))) return false;
    }
    return true;
  });

  if (keyword) {
    rows = rows.filter(
      (m) =>
        String(m.name).toLowerCase().includes(keyword) ||
        String(m.busNo).toLowerCase().includes(keyword),
    );
  }

  const total = rows.length;
  const start = (pageNo - 1) * pageSize;
  const slice = rows.slice(start, start + pageSize).map((m) => ({
    ...structuredClone(m),
    supplierId_dictText: mapSupplier(String((m.supplierIds as string[])[0] ?? '')),
  }));
  return delay({ records: slice, total });
}

export async function mockInitPurchaseReturnBusNo(): Promise<{ busNo: string }> {
  return delay({ busNo: nextBusNo() });
}

export async function mockSavePurchaseReturn(data: Record<string, any>[]): Promise<unknown> {
  const r0 = data[0];
  if (!r0) return delay({});

  const existingKey = String(r0.busNo ?? '');
  const finalBus = existingKey || nextBusNo();
  const existing = returnOrders.get(finalBus);

  const head: Record<string, any> = headCommon(finalBus, {
    applicantId: r0.applicantId,
    applicantId_dictText: mapApplicant(String(r0.applicantId ?? '')),
    supplierId: r0.supplierId,
    supplierId_dictText: mapSupplier(String(r0.supplierId ?? '')),
    applyTime: r0.applyTime,
    sourceLocationId: r0.sourceLocationId,
    categoryEnum: r0.categoryEnum,
    reason: r0.reason,
    phone: r0.phone,
    practitionerId: r0.practitionerId,
    invoiceNo: r0.invoiceNo,
    originalBusNo: r0.originalBusNo,
  });

  if (existing) {
    Object.assign(head, {
      statusEnum: existing.head.statusEnum,
      statusEnum_enumText: existing.head.statusEnum_enumText,
      approvalTime: existing.head.approvalTime,
      approverId: existing.head.approverId,
    });
  } else {
    Object.assign(head, {
      statusEnum: 2,
      statusEnum_enumText: '待审核',
      approvalTime: undefined,
      approverId: undefined,
    });
  }

  const lines = data.map((row) => mergeReturnDetail(head, { ...row, busNo: finalBus }));
  returnOrders.set(finalBus, { head, lines });
  return delay({});
}

export async function mockDeletePurchaseReturnOrder(busNo: string): Promise<unknown> {
  returnOrders.delete(String(busNo));
  return delay({});
}

export async function mockAgreePurchaseReturnApproval(busNo: string): Promise<unknown> {
  const o = returnOrders.get(String(busNo));
  if (o) {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
    o.head.statusEnum = 3;
    o.head.statusEnum_enumText = '同意';
    o.head.approvalTime = ts;
    o.head.approverId = 'p1';
    o.lines = o.lines.map((line) => mergeReturnDetail(o.head, line));
  }
  return delay({});
}

export async function mockPageStockInOrderList(params?: Record<string, any>): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const locationId = params?.locationId;
  const statusEnum = params?.statusEnum;
  let list = [...stockInOrders.values()].map((o) => o.head);
  if (locationId !== undefined && locationId !== null && `${locationId}` !== '') {
    list = list.filter((h) => String(h.sourceLocationId) === String(locationId));
  }
  if (statusEnum !== undefined && statusEnum !== null && `${statusEnum}` !== '') {
    list = list.filter((h) => String(h.statusEnum) === String(statusEnum));
  }
  const pageNo = Math.max(1, Number(params?.pageNo ?? 1));
  const pageSize = Number(params?.pageSize ?? 10);
  const total = list.length;
  const start = (pageNo - 1) * pageSize;
  const slice = list.slice(start, start + pageSize);
  return delay({ records: slice, total });
}

export async function mockPageStockInOrderDetail(params?: Record<string, any>): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const busNo = params?.busNo ? String(params.busNo) : '';
  const order = busNo ? stockInOrders.get(busNo) : undefined;
  if (!order) {
    return delay({ records: [], total: 0 });
  }
  const lines = order.lines.map((line) => ({ ...mergeReturnDetail(order.head, line) }));
  return delay({ records: lines, total: lines.length });
}
