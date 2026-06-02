/* eslint-disable @typescript-eslint/no-explicit-any -- mock 数据形状与后端宽松契约对齐 */
import type {
  MedicationInfoQuery,
  PurchaseOrderDetailQuery,
  PurchaseOrderInitResponse,
  PurchaseOrderPageQuery,
} from './purchase-request';

import dayjs from 'dayjs';

/** 库房下拉运行时兼容 id/name（模板）与 label/value（选项契约） */
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
  s1: '长春市润禾康医疗器械有限公司',
  s2: '国药控股股份有限公司',
};

/** 药品目录（警戒订货 / 弹层选药共用），含供货与库房维度过滤 */
const MEDICATION_CATALOG: Record<string, any>[] = [
  {
    id: 'med-1',
    busNo: 'YP001',
    name: '阿莫西林胶囊',
    price: 12.5,
    retailPrice: 18,
    unitCode: 'BOX',
    unitCode_dictText: '盒',
    minUnitCode: 'PCS',
    minUnitCode_dictText: '粒',
    maxUnitCode: 'BOX',
    totalVolume: '0.25g×24粒',
    specificationInventory: 240,
    manufacturerText: '华北制药',
    approvalNumber: '国药准字H13000001',
    remake: '',
    partPercent: 24,
    typeEnum: 13,
    supplierIds: ['s1', 's2'],
    locationIds: ['loc-1'],
    unitList: [
      { value: 'BOX', value_dictText: '盒' },
      { value: 'PCS', value_dictText: '粒' },
    ],
  },
  {
    id: 'med-2',
    busNo: 'YP002',
    name: '氯化钠注射液',
    price: 3.2,
    retailPrice: 5,
    unitCode: 'BOX',
    unitCode_dictText: '箱',
    minUnitCode: 'BOT',
    minUnitCode_dictText: '瓶',
    maxUnitCode: 'BOX',
    totalVolume: '500ml×20瓶',
    specificationInventory: 100,
    manufacturerText: '科伦药业',
    approvalNumber: '国药准字H51000002',
    remake: '',
    partPercent: 20,
    typeEnum: 13,
    supplierIds: ['s1'],
    locationIds: ['loc-1'],
    unitList: [
      { value: 'BOX', value_dictText: '箱' },
      { value: 'BOT', value_dictText: '瓶' },
    ],
  },
  {
    id: 'med-3',
    busNo: 'YP003',
    name: '布洛芬缓释胶囊',
    price: 8.6,
    retailPrice: 12.8,
    unitCode: 'BOX',
    unitCode_dictText: '盒',
    minUnitCode: 'PCS',
    minUnitCode_dictText: '粒',
    maxUnitCode: 'BOX',
    totalVolume: '0.3g×20粒',
    specificationInventory: 180,
    manufacturerText: '芬必得制药',
    approvalNumber: '国药准字H10980003',
    remake: '',
    partPercent: 20,
    typeEnum: 13,
    supplierIds: ['s2'],
    locationIds: ['loc-1', 'loc-2'],
    unitList: [
      { value: 'BOX', value_dictText: '盒' },
      { value: 'PCS', value_dictText: '粒' },
    ],
  },
];

type MockOrder = {
  head: Record<string, any>;
  lines: Record<string, any>[];
};

const orders = new Map<string, MockOrder>();

let busSeq = 4;

function nextBusNo(): string {
  busSeq += 1;
  return `PO-MOCK-${String(busSeq).padStart(5, '0')}`;
}

function mapApplicant(id?: string): string {
  if (!id) return '';
  return PRACTITIONER_LABEL[String(id)] ?? String(id);
}

function mapSupplier(id?: string): string {
  if (!id) return '';
  return SUPPLIER_LABEL[String(id)] ?? String(id);
}

function headToDetailFields(head: Record<string, any>): Record<string, any> {
  return {
    busNo: head.supplyBusNo,
    applyTime: head.applyTime,
    supplierId: head.supplierId,
    applicantId: head.applicantId,
    categoryEnum: head.categoryEnum,
    reason: head.reason,
    purposeLocationId: head.purposeLocationId,
    approvalTime: head.approvalTime,
    statusEnum: head.statusEnum,
    statusEnum_enumText: head.statusEnum_enumText,
    approverId: head.approverId,
    typeEnum_enumText: head.typeEnum_enumText,
  };
}

function mergeDetailLine(
  head: Record<string, any>,
  line: Record<string, any>,
): Record<string, any> {
  return {
    ...headToDetailFields(head),
    ...line,
  };
}

/** 本月内日期：从月初起第 dayOffset 天，时刻 hour:minute（超过今天则压到此刻） */
function mockApplyTime(dayOffsetFromMonthStart: number, hour = 9, minute = 0): string {
  const monthStart = dayjs().startOf('month');
  const todayEnd = dayjs().endOf('day');
  let t = monthStart.add(dayOffsetFromMonthStart, 'day').hour(hour).minute(minute).second(0);
  if (t.isAfter(todayEnd)) t = todayEnd;
  return t.format('YYYY-MM-DD HH:mm:ss');
}

function applyTimeToday(hour: number, minute = 0): string {
  return dayjs().hour(hour).minute(minute).second(0).format('YYYY-MM-DD HH:mm:ss');
}

function seedOrders(): void {
  const span = Math.max(0, dayjs().diff(dayjs().startOf('month'), 'day'));

  const h1: Record<string, any> = {
    supplyBusNo: 'PO-MOCK-00001',
    applicantId: 'p1',
    applicantId_dictText: mapApplicant('p1'),
    supplierId: 's1',
    supplierId_dictText: mapSupplier('s1'),
    statusEnum: 2,
    statusEnum_enumText: '待审核',
    typeEnum: 10,
    typeEnum_enumText: '普通订货',
    applyTime: mockApplyTime(0, 9, 30),
    purposeLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '常规补货',
    approvalTime: undefined,
    approverId: undefined,
    /** 列表「总金额」mock：五位数 + 两位小数 */
    totalAmount: 38456.78,
  };

  const c0 = MEDICATION_CATALOG[0]!;
  const lineA = {
    ...structuredClone(c0),
    itemId: c0.id,
    itemBusNo: c0.busNo,
    itemName: c0.name,
    unitCodeDictText: c0.unitCode_dictText,
    itemQuantity: 10,
    totalPrice: '125.00',
    totalPriceMask: '125.00',
    totalRetailPrice: '180.00',
    totalRetailPriceMask: '180.00',
    isEditing: false,
    isViewing: true,
  };
  const c1 = MEDICATION_CATALOG[1]!;
  const lineB = {
    ...structuredClone(c1),
    itemId: c1.id,
    itemBusNo: c1.busNo,
    itemName: c1.name,
    unitCodeDictText: c1.unitCode_dictText,
    itemQuantity: 2,
    totalPrice: '64.00',
    totalPriceMask: '64.00',
    totalRetailPrice: '100.00',
    totalRetailPriceMask: '100.00',
    isEditing: false,
    isViewing: true,
  };

  orders.set('PO-MOCK-00001', {
    head: h1,
    lines: [mergeDetailLine(h1, lineA), mergeDetailLine(h1, lineB)],
  });

  const h2: Record<string, any> = {
    supplyBusNo: 'PO-MOCK-00002',
    applicantId: 'p2',
    applicantId_dictText: mapApplicant('p2'),
    supplierId: 's2',
    supplierId_dictText: mapSupplier('s2'),
    statusEnum: 3,
    statusEnum_enumText: '同意',
    typeEnum: 10,
    typeEnum_enumText: '普通订货',
    /** 列表卡片双库房演示：仅 ID，由前端用 cabinet 选项解析为「中药库 → 西药库」 */
    sourceLocationId: 'loc-2',
    applyTime: mockApplyTime(Math.min(span, Math.max(1, Math.floor(span / 2))), 11, 0),
    purposeLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '本月审批通过',
    approvalTime: dayjs(mockApplyTime(Math.min(span, Math.max(1, Math.floor(span / 2))), 11, 0))
      .add(4, 'hour')
      .format('YYYY-MM-DD HH:mm:ss'),
    approverId: 'p1',
    totalAmount: 91203.45,
  };

  const c2 = MEDICATION_CATALOG[2]!;
  const lineC = {
    ...structuredClone(c2),
    itemId: c2.id,
    itemBusNo: c2.busNo,
    itemName: c2.name,
    unitCodeDictText: c2.unitCode_dictText,
    itemQuantity: 5,
    totalPrice: '43.00',
    totalPriceMask: '43.00',
    totalRetailPrice: '64.00',
    totalRetailPriceMask: '64.00',
    isEditing: false,
    isViewing: true,
  };

  orders.set('PO-MOCK-00002', {
    head: h2,
    lines: [mergeDetailLine(h2, lineC)],
  });

  const h3: Record<string, any> = {
    supplyBusNo: 'PO-MOCK-00003',
    applicantId: 'p1',
    applicantId_dictText: mapApplicant('p1'),
    supplierId: 's2',
    supplierId_dictText: mapSupplier('s2'),
    statusEnum: 2,
    statusEnum_enumText: '待审核',
    typeEnum: 10,
    typeEnum_enumText: '普通订货',
    applyTime: mockApplyTime(Math.min(span, Math.max(0, span - 1)), 10, 15),
    purposeLocationId: 'loc-2',
    categoryEnum: '2',
    reason: '中药库补货',
    approvalTime: undefined,
    approverId: undefined,
    totalAmount: 12880.5,
  };
  const c0b = MEDICATION_CATALOG[0]!;
  const lineD = {
    ...structuredClone(c0b),
    itemId: `${c0b.id}-b`,
    itemBusNo: c0b.busNo,
    itemName: c0b.name,
    unitCodeDictText: c0b.unitCode_dictText,
    itemQuantity: 4,
    totalPrice: '50.00',
    totalPriceMask: '50.00',
    totalRetailPrice: '72.00',
    totalRetailPriceMask: '72.00',
    isEditing: false,
    isViewing: true,
  };
  orders.set('PO-MOCK-00003', {
    head: h3,
    lines: [mergeDetailLine(h3, lineD)],
  });

  const h4: Record<string, any> = {
    supplyBusNo: 'PO-MOCK-00004',
    applicantId: 'p2',
    applicantId_dictText: mapApplicant('p2'),
    supplierId: 's1',
    supplierId_dictText: mapSupplier('s1'),
    statusEnum: 2,
    statusEnum_enumText: '待审核',
    typeEnum: 10,
    typeEnum_enumText: '普通订货',
    applyTime: applyTimeToday(16, 20),
    purposeLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '今日临时加单',
    approvalTime: undefined,
    approverId: undefined,
    totalAmount: 2560.0,
  };
  const c1b = MEDICATION_CATALOG[1]!;
  const lineE = {
    ...structuredClone(c1b),
    itemId: `${c1b.id}-b`,
    itemBusNo: c1b.busNo,
    itemName: c1b.name,
    unitCodeDictText: c1b.unitCode_dictText,
    itemQuantity: 8,
    totalPrice: '25.60',
    totalPriceMask: '25.60',
    totalRetailPrice: '40.00',
    totalRetailPriceMask: '40.00',
    isEditing: false,
    isViewing: true,
  };
  orders.set('PO-MOCK-00004', {
    head: h4,
    lines: [mergeDetailLine(h4, lineE)],
  });
}

seedOrders();

export async function mockInitPurchaseOrder(): Promise<PurchaseOrderInitResponse> {
  const cabinetListOptions: CabinetOption[] = [
    { label: '西药库', value: 'loc-1', id: 'loc-1', name: '西药库' },
    { label: '中药库', value: 'loc-2', id: 'loc-2', name: '中药库' },
  ];
  return delay({
    cabinetListOptions: cabinetListOptions as PurchaseOrderInitResponse['cabinetListOptions'],
    practitionerOption: [
      { label: '采购员甲', value: 'p1' },
      { label: '采购员乙', value: 'p2' },
    ],
    supplierOption: [
      { label: '长春市润禾康医疗器械有限公司', value: 's1' },
      { label: '国药控股股份有限公司', value: 's2' },
    ],
    supplyCategoryOptions: [
      { label: '常规采购', value: '1' },
      { label: '紧急采购', value: '2' },
    ],
    supplyStatusOptions: [
      { label: '待审核', value: '2' },
      { label: '同意', value: '3' },
    ],
    supplyTypeOptions: [{ label: '普通订货', value: '10' }],
  });
}

export async function mockPagePurchaseOrderList(
  params?: PurchaseOrderPageQuery,
): Promise<{ records: Record<string, any>[]; total?: number }> {
  const locationId = params?.locationId;
  const keyword = (params?.keyword ?? '').trim().toLowerCase();
  const begin = (params?.applyTimeBegin ?? '').trim();
  const end = (params?.applyTimeEnd ?? '').trim();
  let list = [...orders.values()].map((o) => o.head);
  if (locationId !== undefined && locationId !== null && `${locationId}` !== '') {
    list = list.filter((h) => String(h.purposeLocationId) === String(locationId));
  }
  if (keyword) {
    list = list.filter((h) => String(h.supplyBusNo).toLowerCase().includes(keyword));
  }
  if (begin || end) {
    const startD = begin ? dayjs(begin).startOf('day') : null;
    const endD = end ? dayjs(end).endOf('day') : null;
    list = list.filter((h) => {
      const t = dayjs(h.applyTime as string);
      if (!t.isValid()) return false;
      if (startD && t.isBefore(startD)) return false;
      if (endD && t.isAfter(endD)) return false;
      return true;
    });
  }
  list.sort((a, b) => String(b.supplyBusNo).localeCompare(String(a.supplyBusNo)));
  return delay({ records: list, total: list.length });
}

export async function mockPagePurchaseOrderDetail(params?: PurchaseOrderDetailQuery): Promise<{
  records: Record<string, any>[];
  total?: number;
}> {
  const busNo = params?.busNo ? String(params.busNo) : '';
  const pageNo = Math.max(1, params?.pageNo ?? 1);
  const pageSize = params?.pageSize ?? 10;
  const order = busNo ? orders.get(busNo) : undefined;
  if (!order) {
    return delay({ records: [], total: 0 });
  }
  const lines = order.lines.map((line) => ({
    ...mergeDetailLine(order.head, line),
  }));
  const start = (pageNo - 1) * pageSize;
  const slice = lines.slice(start, start + pageSize);
  return delay({ records: slice, total: lines.length });
}

export async function mockPagePurchaseOrderMedication(
  params?: MedicationInfoQuery,
): Promise<{ records: Record<string, any>[]; total?: number }> {
  const supplierId = params?.supplierId;
  const locationId = params?.locationId;
  const keyword = (params?.keyword ?? '').trim().toLowerCase();
  const pageNo = Math.max(1, params?.pageNo ?? 1);
  const pageSize = params?.pageSize ?? 50;
  const typeEnum = params?.typeEnum;

  let rows = MEDICATION_CATALOG.filter((m) => {
    if (typeEnum !== undefined && typeEnum !== null && Number(m.typeEnum) !== Number(typeEnum)) {
      return false;
    }
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
  const slice = rows.slice(start, start + pageSize).map((m) => structuredClone(m));
  return delay({ records: slice, total });
}

export async function mockInitPurchaseOrderBusNo(): Promise<{ busNo: string }> {
  return delay({ busNo: nextBusNo() });
}

export async function mockSavePurchaseOrder(data: Record<string, any>[]): Promise<unknown> {
  const r0 = data[0];
  if (!r0) return delay({});

  const busNo = String(r0.busNo ?? '');
  const finalBus = busNo || nextBusNo();
  const existing = orders.get(finalBus);
  const head: Record<string, any> = {
    supplyBusNo: finalBus,
    applicantId: r0.applicantId,
    applicantId_dictText: mapApplicant(String(r0.applicantId ?? '')),
    supplierId: r0.supplierId,
    supplierId_dictText: mapSupplier(String(r0.supplierId ?? '')),
    applyTime: r0.applyTime,
    purposeLocationId: r0.purposeLocationId ?? r0.locationId,
    categoryEnum: r0.categoryEnum,
    reason: r0.reason,
    typeEnum: 10,
    typeEnum_enumText: '普通订货',
  };

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

  const lines = data.map((row) => {
    const merged = mergeDetailLine(head, {
      ...row,
      busNo: finalBus,
    });
    return merged;
  });

  orders.set(finalBus, { head, lines });
  return delay({});
}

export async function mockDeletePurchaseOrder(busNo: string): Promise<unknown> {
  orders.delete(String(busNo));
  return delay({});
}

export async function mockAgreePurchaseOrderApproval(busNo: string): Promise<unknown> {
  const o = orders.get(String(busNo));
  if (o) {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
    o.head.statusEnum = 3;
    o.head.statusEnum_enumText = '同意';
    o.head.approvalTime = ts;
    o.head.approverId = 'p1';
    o.lines = o.lines.map((line) => mergeDetailLine(o.head, line));
  }
  return delay({});
}
