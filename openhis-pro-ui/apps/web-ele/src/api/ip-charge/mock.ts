export interface InpatientQueueItem {
  age: number;
  balanceAmount: number;
  bedNo: string;
  department: string;
  gender: '女' | '男';
  id: string;
  inpatientDays: number;
  inpatientNo: string;
  insuranceType: string;
  patientName: string;
  status: '在院' | '欠费预警';
  visitTime: string;
}

export interface PrepayRecordItem {
  amount: number;
  bizType: '收预交金' | '退预交金';
  id: string;
  operatorName: string;
  payMethod: string;
  remark?: string;
  statusText: '已冲正' | '成功';
  ticketNo: string;
  tradeTime: string;
}

export interface PageResult<T> {
  content: T[];
  pageNo: number;
  pageSize: number;
  totalCount: string;
}

const MOCK_DELAY_MS = 180;

function delay<T>(value: T, ms = MOCK_DELAY_MS): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), ms);
  });
}

const queueStore: InpatientQueueItem[] = [
  {
    age: 62,
    balanceAmount: 1320,
    bedNo: '12-08',
    department: '呼吸内科一病区',
    gender: '男',
    id: 'IP001',
    inpatientDays: 5,
    inpatientNo: 'ZY240401',
    insuranceType: '职工医保',
    patientName: '陈建华',
    status: '欠费预警',
    visitTime: '2026-04-21 10:18:00',
  },
  {
    age: 48,
    balanceAmount: 3860,
    bedNo: '11-03',
    department: '骨科病区',
    gender: '女',
    id: 'IP002',
    inpatientDays: 2,
    inpatientNo: 'ZY240402',
    insuranceType: '居民医保',
    patientName: '赵莉',
    status: '在院',
    visitTime: '2026-04-24 08:30:00',
  },
  {
    age: 33,
    balanceAmount: 520,
    bedNo: '12-11',
    department: '呼吸内科一病区',
    gender: '男',
    id: 'IP003',
    inpatientDays: 9,
    inpatientNo: 'ZY240403',
    insuranceType: '自费',
    patientName: '林涛',
    status: '欠费预警',
    visitTime: '2026-04-17 15:40:00',
  },
];

const prepayRecordStore = new Map<string, PrepayRecordItem[]>([
  [
    'IP001',
    [
      {
        amount: 1000,
        bizType: '收预交金',
        id: 'REC001',
        operatorName: '刘芳',
        payMethod: '微信支付',
        statusText: '成功',
        ticketNo: 'PJ20260426001',
        tradeTime: '2026-04-26 09:10:12',
      },
      {
        amount: 500,
        bizType: '收预交金',
        id: 'REC002',
        operatorName: '刘芳',
        payMethod: '现金',
        statusText: '成功',
        ticketNo: 'PJ20260426002',
        tradeTime: '2026-04-26 11:36:02',
      },
    ],
  ],
  ['IP002', []],
  ['IP003', []],
]);

/** 按关键字和入院日期查询在院患者列表。 */
export async function mockPageInpatientQueue(params: {
  date?: string;
  keyword?: string;
}): Promise<InpatientQueueItem[]> {
  const keyword = (params.keyword ?? '').trim();
  const date = (params.date ?? '').trim();
  const content = queueStore.filter((item) => {
    const matchKeyword =
      !keyword ||
      item.inpatientNo.includes(keyword) ||
      item.patientName.includes(keyword) ||
      item.bedNo.includes(keyword);
    if (!matchKeyword) return false;
    if (date && !item.visitTime.startsWith(date)) return false;
    return true;
  });
  return delay(content.map((item) => ({ ...item })));
}

/** 按患者加载预交金流水。 */
export async function mockPagePrepayRecords(patientId: string): Promise<PrepayRecordItem[]> {
  const content = prepayRecordStore.get(patientId) ?? [];
  return delay(content.map((item) => ({ ...item })));
}

/** 收取预交金并回写流水与余额。 */
export async function mockCollectPrepay(params: {
  amount: number;
  operatorName: string;
  patientId: string;
  payMethod: string;
  remark?: string;
}): Promise<PrepayRecordItem> {
  const now = new Date();
  const record: PrepayRecordItem = {
    amount: params.amount,
    bizType: '收预交金',
    id: `REC${now.getTime()}`,
    operatorName: params.operatorName,
    payMethod: params.payMethod,
    remark: params.remark ?? '',
    statusText: '成功',
    ticketNo: `PJ${now.getTime()}`,
    tradeTime: now.toISOString().slice(0, 19).replace('T', ' '),
  };
  const prev = prepayRecordStore.get(params.patientId) ?? [];
  prepayRecordStore.set(params.patientId, [record, ...prev]);
  const patient = queueStore.find((item) => item.id === params.patientId);
  if (patient) {
    patient.balanceAmount += params.amount;
    patient.status = patient.balanceAmount > 1000 ? '在院' : '欠费预警';
  }
  return delay({ ...record });
}
