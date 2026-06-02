/* eslint-disable @typescript-eslint/no-explicit-any -- mock 与后端契约宽松对齐 */
import type {
  GainLossDetailQuery,
  GainLossInitResponse,
  GainLossPageQuery,
  MedicationInfoQuery,
} from './gain-loss.types';

const MOCK_DELAY_MS = 150;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

type CabinetOption = import('#/types/api-response').ApiOptionItem & {
  id?: string;
  name?: string;
};

const PRACTITIONER_LABEL: Record<string, string> = {
  p1: '经办员甲',
  p2: '经办员乙',
};

const SUPPLIER_LABEL: Record<string, string> = {
  s1: '华润医药',
  s2: '国药控股',
};

/** 药品目录（选药弹层），结构与订货 mock 对齐便于换算演示 */
const MEDICATION_CATALOG: Record<string, any>[] = [
  {
    id: 'gl-med-1',
    busNo: 'YP-GL001',
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
    batchInventory: 200,
    manufacturerText: '华北制药',
    supplierId_dictText: '华润医药',
    supplierId: 's1',
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
    productionDate: '2025-01-01',
    expirationDate: '2027-01-01',
  },
  {
    id: 'gl-med-2',
    busNo: 'YP-GL002',
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
    batchInventory: 80,
    manufacturerText: '科伦药业',
    supplierId_dictText: '国药控股',
    supplierId: 's1',
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
    productionDate: '2025-06-01',
    expirationDate: '2028-06-01',
  },
];

type MockOrder = {
  head: Record<string, any>;
  lines: Record<string, any>[];
};

const orders = new Map<string, MockOrder>();

let busSeq = 2;

function nextBusNo(): string {
  busSeq += 1;
  return `GL-MOCK-${String(busSeq).padStart(5, '0')}`;
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
    practitionerId: head.practitionerId,
    phone: head.phone,
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

function seedOrders(): void {
  const h1: Record<string, any> = {
    supplyBusNo: 'GL-MOCK-00001',
    applicantId: 'p1',
    applicantId_dictText: mapApplicant('p1'),
    supplierId: 's1',
    supplierId_dictText: mapSupplier('s1'),
    statusEnum: 2,
    statusEnum_enumText: '待审核',
    typeEnum: 21,
    typeEnum_enumText: '损益调整',
    applyTime: '2026-04-28 09:30:00',
    purposeLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '盘点盈余入账',
    approvalTime: undefined,
    approverId: undefined,
    practitionerId: 'p1',
    phone: '',
  };

  const c0 = MEDICATION_CATALOG[0]!;
  const lineA = {
    ...structuredClone(c0),
    itemId: c0.id,
    itemBusNo: c0.busNo,
    itemName: c0.name,
    unitCodeDictText: c0.unitCode_dictText,
    itemQuantity: 10,
    lotNumber: 'LN-A001',
    totalPrice: '125.00',
    totalPriceMask: '125.00',
    totalRetailPrice: '180.00',
    totalRetailPriceMask: '180.00',
    profitLoss: '-',
    isEditing: false,
    isViewing: true,
    traceNo: '',
    traceNoUnitCode: c0.unitCode,
    traceNoUnitCode_dictText: c0.unitCode_dictText,
  };

  const c1 = MEDICATION_CATALOG[1]!;
  const lineB = {
    ...structuredClone(c1),
    itemId: c1.id,
    itemBusNo: c1.busNo,
    itemName: c1.name,
    unitCodeDictText: c1.unitCode_dictText,
    itemQuantity: 2,
    lotNumber: 'LN-B002',
    totalPrice: '64.00',
    totalPriceMask: '64.00',
    totalRetailPrice: '100.00',
    totalRetailPriceMask: '100.00',
    profitLoss: '-',
    isEditing: false,
    isViewing: true,
    traceNo: '',
    traceNoUnitCode: c1.unitCode,
    traceNoUnitCode_dictText: c1.unitCode_dictText,
  };

  orders.set('GL-MOCK-00001', {
    head: h1,
    lines: [mergeDetailLine(h1, lineA), mergeDetailLine(h1, lineB)],
  });

  const h2: Record<string, any> = {
    supplyBusNo: 'GL-MOCK-00002',
    applicantId: 'p2',
    applicantId_dictText: mapApplicant('p2'),
    supplierId: 's2',
    supplierId_dictText: mapSupplier('s2'),
    statusEnum: 3,
    statusEnum_enumText: '同意',
    typeEnum: 21,
    typeEnum_enumText: '损益调整',
    applyTime: '2026-04-20 14:00:00',
    purposeLocationId: 'loc-1',
    categoryEnum: '1',
    reason: '审批完成的损益单',
    approvalTime: '2026-04-21 10:00:00',
    approverId: 'p1',
    practitionerId: 'p2',
    phone: '',
  };

  const lineC = {
    ...structuredClone(c0),
    itemId: c0.id,
    itemBusNo: c0.busNo,
    itemName: c0.name,
    unitCodeDictText: c0.unitCode_dictText,
    itemQuantity: 5,
    lotNumber: 'LN-C003',
    totalPrice: '62.50',
    totalPriceMask: '62.50',
    totalRetailPrice: '90.00',
    totalRetailPriceMask: '90.00',
    profitLoss: '-',
    isEditing: false,
    isViewing: true,
    traceNo: '',
    traceNoUnitCode: c0.unitCode,
    traceNoUnitCode_dictText: c0.unitCode_dictText,
  };

  orders.set('GL-MOCK-00002', {
    head: h2,
    lines: [mergeDetailLine(h2, lineC)],
  });
}

seedOrders();

export async function mockInitGainLoss(): Promise<GainLossInitResponse> {
  const cabinetListOptions: CabinetOption[] = [
    { label: '西药库', value: 'loc-1', id: 'loc-1', name: '西药库' },
    { label: '中药库', value: 'loc-2', id: 'loc-2', name: '中药库' },
  ];
  return delay({
    cabinetListOptions: cabinetListOptions as GainLossInitResponse['cabinetListOptions'],
    practitionerOption: [
      { label: '经办员甲', value: 'p1' },
      { label: '经办员乙', value: 'p2' },
    ],
    supplierOption: [
      { label: '华润医药', value: 's1' },
      { label: '国药控股', value: 's2' },
    ],
    supplyCategoryOptions: [
      { label: '日常损益', value: '1' },
      { label: '盘点损益', value: '2' },
    ],
    supplyStatusOptions: [
      { label: '待审核', value: '2' },
      { label: '同意', value: '3' },
    ],
    supplyTypeOptions: [{ label: '损益调整', value: '21' }],
    packagingConditionOptions: [
      { label: '完好', value: '1' },
      { label: '破损', value: '2' },
    ],
    acceptanceResultoryOptions: [
      { label: '合格', value: '1' },
      { label: '不合格', value: '2' },
    ],
    pharmacyListOptions: [
      { label: '住院药房', value: 'pha-1' },
      { label: '门诊药房', value: 'pha-2' },
    ],
  });
}

export async function mockPageGainLossList(
  params?: GainLossPageQuery,
): Promise<{ records: Record<string, any>[]; total?: number }> {
  const locationId = params?.locationId;
  const keyword = (params?.keyword ?? '').trim().toLowerCase();
  let list = [...orders.values()].map((o) => o.head);
  if (locationId !== undefined && locationId !== null && `${locationId}` !== '') {
    list = list.filter((h) => String(h.purposeLocationId) === String(locationId));
  }
  if (keyword) {
    list = list.filter((h) => String(h.supplyBusNo).toLowerCase().includes(keyword));
  }
  list.sort((a, b) => String(b.supplyBusNo).localeCompare(String(a.supplyBusNo)));
  return delay({ records: list, total: list.length });
}

export async function mockPageGainLossDetail(params?: GainLossDetailQuery): Promise<{
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

export async function mockPageGainLossMedication(
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

export async function mockInitGainLossBusNo(): Promise<{ busNo: string }> {
  return delay({ busNo: nextBusNo() });
}

export async function mockSaveGainLoss(data: Record<string, any>[]): Promise<unknown> {
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
    practitionerId: r0.practitionerId,
    phone: r0.phone ?? '',
    typeEnum: 21,
    typeEnum_enumText: '损益调整',
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

  const lines = data.map((row) =>
    mergeDetailLine(head, {
      ...row,
      busNo: finalBus,
    }),
  );

  orders.set(finalBus, { head, lines });
  return delay({});
}

export async function mockDeleteGainLoss(busNo: string): Promise<unknown> {
  orders.delete(String(busNo));
  return delay({});
}

export async function mockAgreeGainLossApproval(busNo: string): Promise<unknown> {
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
