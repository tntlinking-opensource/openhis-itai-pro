/* eslint-disable @typescript-eslint/no-explicit-any -- mock 数据形状与后端宽松契约对齐 */

import type {
  DocumentManagementInitData,
  DocumentManagementPageQuery,
} from './document-management.types';

const MOCK_DELAY_MS = 150;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

function datePart(dateTime?: string | null): string {
  if (!dateTime) return '';
  return String(dateTime).slice(0, 10);
}

/** 与页面下拉、路由跳转中单据类型含义一致 */
const MOCK_RECORDS: Record<string, any>[] = [
  {
    supplyBusNo: 'DM-PO-202504010001',
    applicantId_dictText: '张三',
    applyTime: '2025-04-01 09:30:00',
    approvalTime: '2025-04-01 14:20:00',
    approverId_dictText: '李主任',
    locationId: 101,
    locationId_dictText: '中心药库',
    locationTypeEnum: 1,
    statusEnum: 3,
    statusEnum_enumText: '同意',
    supplierId_dictText: '华润医药',
    typeEnum: 13,
    typeEnum_enumText: '采购订货',
  },
  {
    supplyBusNo: 'DM-IN-202504020015',
    applicantId_dictText: '王五',
    applyTime: '2025-04-02 10:15:00',
    approvalTime: '',
    approverId_dictText: '',
    locationId: 101,
    locationId_dictText: '中心药库',
    locationTypeEnum: 1,
    statusEnum: 1,
    statusEnum_enumText: '待审核',
    supplierId_dictText: '国药控股',
    typeEnum: 1,
    typeEnum_enumText: '采购入库',
  },
  {
    supplyBusNo: 'DM-RT-202504030008',
    applicantId_dictText: '赵六',
    applyTime: '2025-04-03 11:00:00',
    approvalTime: '2025-04-03 16:00:00',
    approverId_dictText: '周审核',
    locationId: 102,
    locationId_dictText: '门诊药房库',
    locationTypeEnum: 1,
    statusEnum: 4,
    statusEnum_enumText: '驳回',
    supplierId_dictText: '华润医药',
    typeEnum: 5,
    typeEnum_enumText: '采购退货',
  },
  {
    supplyBusNo: 'DM-OUT-202504041100',
    applicantId_dictText: '孙护士',
    applyTime: '2025-04-04 08:40:00',
    approvalTime: '2025-04-04 09:10:00',
    approverId_dictText: '护士长',
    locationId: 201,
    locationId_dictText: '耗材主库',
    locationTypeEnum: 2,
    statusEnum: 3,
    statusEnum_enumText: '同意',
    supplierId_dictText: '—',
    typeEnum: 7,
    typeEnum_enumText: '领用出库',
  },
  {
    supplyBusNo: 'DM-RW-202504050022',
    applicantId_dictText: '钱药师',
    applyTime: '2025-04-05 13:20:00',
    approvalTime: '',
    approverId_dictText: '',
    locationId: 201,
    locationId_dictText: '耗材主库',
    locationTypeEnum: 2,
    statusEnum: 2,
    statusEnum_enumText: '审核中',
    supplierId_dictText: '—',
    typeEnum: 9,
    typeEnum_enumText: '领用退货',
  },
  {
    supplyBusNo: 'DM-PL-202504061030',
    applicantId_dictText: '吴保管',
    applyTime: '2025-04-06 10:30:00',
    approvalTime: '2025-04-06 15:00:00',
    approverId_dictText: '库房主管',
    locationId: 101,
    locationId_dictText: '中心药库',
    locationTypeEnum: 1,
    statusEnum: 3,
    statusEnum_enumText: '同意',
    supplierId_dictText: '—',
    typeEnum: 6,
    typeEnum_enumText: '报损单',
  },
  {
    supplyBusNo: 'DM-ST-202504071800',
    applicantId_dictText: '郑盘点',
    applyTime: '2025-04-07 18:00:00',
    approvalTime: '2025-04-08 09:00:00',
    approverId_dictText: '财务复核',
    locationId: 102,
    locationId_dictText: '门诊药房库',
    locationTypeEnum: 1,
    statusEnum: 3,
    statusEnum_enumText: '同意',
    supplierId_dictText: '—',
    typeEnum: 4,
    typeEnum_enumText: '商品盘点',
  },
  {
    supplyBusNo: 'DM-PO-202504080099',
    applicantId_dictText: '冯采购',
    applyTime: '2025-04-08 09:05:00',
    approvalTime: '',
    approverId_dictText: '',
    locationId: 101,
    locationId_dictText: '中心药库',
    locationTypeEnum: 1,
    statusEnum: 10,
    statusEnum_enumText: '待申请',
    supplierId_dictText: '科伦药业',
    typeEnum: 13,
    typeEnum_enumText: '采购订货',
  },
  {
    supplyBusNo: 'DM-IN-202504090077',
    applicantId_dictText: '陈入库',
    applyTime: '2025-04-09 14:50:00',
    approvalTime: '2025-04-09 17:20:00',
    approverId_dictText: '李主任',
    locationId: 101,
    locationId_dictText: '中心药库',
    locationTypeEnum: 1,
    statusEnum: 9,
    statusEnum_enumText: '已撤回',
    supplierId_dictText: '华润医药',
    typeEnum: 1,
    typeEnum_enumText: '采购入库',
  },
];

export async function mockDocumentManagementInit(): Promise<DocumentManagementInitData> {
  const data: DocumentManagementInitData = {
    warehouseTypeOption: [
      {
        label: '药库',
        value: 1,
        locationOption: [
          { id: 101, name: '中心药库' },
          { id: 102, name: '门诊药房库' },
        ],
        orderOption: [
          { label: '采购订货', value: 13 },
          { label: '采购入库', value: 1 },
          { label: '采购退货', value: 5 },
          { label: '报损单', value: 6 },
          { label: '商品盘点', value: 4 },
        ],
      },
      {
        label: '耗材库',
        value: 2,
        locationOption: [{ id: 201, name: '耗材主库' }],
        orderOption: [
          { label: '领用出库', value: 7 },
          { label: '领用退货', value: 9 },
        ],
      },
    ],
    supplyStatusOptions: [
      { label: '待审核', value: 1 },
      { label: '审核中', value: 2 },
      { label: '同意', value: 3 },
      { label: '驳回', value: 4 },
      { label: '已撤回', value: 9 },
      { label: '待申请', value: 10 },
    ],
  };
  return delay(data);
}

export async function mockDocumentManagementPage(
  params?: DocumentManagementPageQuery,
): Promise<{ records: Record<string, any>[]; total: number }> {
  const pageNo = Math.max(1, Number(params?.pageNo) || 1);
  const pageSize = Math.max(1, Number(params?.pageSize) || 10);

  let list = [...MOCK_RECORDS];

  const locType = params?.locationTypeEnum;
  if (locType !== undefined && locType !== null && `${locType}` !== '') {
    list = list.filter((r) => String(r.locationTypeEnum) === String(locType));
  }

  const locId = params?.locationId;
  if (locId !== undefined && locId !== null && `${locId}` !== '') {
    list = list.filter((r) => String(r.locationId) === String(locId));
  }

  const typeEnum = params?.typeEnum;
  if (typeEnum !== undefined && typeEnum !== null && `${typeEnum}` !== '') {
    list = list.filter((r) => String(r.typeEnum) === String(typeEnum));
  }

  const statusEnum = params?.statusEnum;
  if (statusEnum !== undefined && statusEnum !== null && `${statusEnum}` !== '') {
    list = list.filter((r) => String(r.statusEnum) === String(statusEnum));
  }

  const applyStart = params?.applyTimeSTime;
  const applyEnd = params?.applyTimeETime;
  if (applyStart) {
    list = list.filter((r) => datePart(r.applyTime) >= applyStart);
  }
  if (applyEnd) {
    list = list.filter((r) => datePart(r.applyTime) <= applyEnd);
  }

  const apprStart = params?.approvalTimeSTime;
  const apprEnd = params?.approvalTimeETime;
  if (apprStart) {
    list = list.filter((r) => r.approvalTime && datePart(r.approvalTime) >= apprStart);
  }
  if (apprEnd) {
    list = list.filter((r) => r.approvalTime && datePart(r.approvalTime) <= apprEnd);
  }

  list.sort((a, b) => String(b.supplyBusNo).localeCompare(String(a.supplyBusNo)));

  const total = list.length;
  const start = (pageNo - 1) * pageSize;
  const records = list.slice(start, start + pageSize);

  return delay({ records, total });
}
