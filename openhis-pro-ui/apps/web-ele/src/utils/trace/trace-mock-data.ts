import type { TraceBatchScanLine } from './trace-types';

/** 开发环境演示用明细行（多药品 + 一条已预绑多码） */
export const TRACE_MOCK_SCAN_LINES: TraceBatchScanLine[] = [
  {
    id: 'mock-line-1',
    itemId: '1001',
    itemName: '阿莫西林胶囊',
    spec: '0.25g×24粒',
    lotNumber: '20250101',
    requiredTraceCount: 1,
    traceNo: '',
  },
  {
    id: 'mock-line-2',
    itemId: '1002',
    itemName: '头孢克肟分散片',
    spec: '0.1g×6片',
    lotNumber: '20250215',
    requiredTraceCount: 1,
    traceNo: '',
  },
  {
    id: 'mock-line-3',
    itemId: '1003',
    itemName: '布洛芬缓释胶囊',
    spec: '0.3g×20粒',
    lotNumber: '20250308',
    requiredTraceCount: 2,
    traceNo: '',
  },
  {
    id: 'mock-line-4',
    itemId: '1004',
    itemName: '维生素C片',
    spec: '0.1g×100片',
    lotNumber: '20250401',
    requiredTraceCount: 2,
    traceNo: '82345678901234567890,82345678901234567891',
  },
];

/** 发药场景演示行（含处方号） */
export const TRACE_MOCK_DISPENSE_LINES: TraceBatchScanLine[] = [
  {
    id: 'mock-rx-1',
    itemId: '2001',
    itemName: '阿莫西林胶囊',
    spec: '0.25g×24粒',
    lotNumber: '20250101',
    rxNo: 'RX20250707001',
    requiredTraceCount: 1,
    traceNo: '',
  },
  {
    id: 'mock-rx-2',
    itemId: '2002',
    itemName: '氯雷他定片',
    spec: '10mg×6片',
    lotNumber: '20250601',
    rxNo: 'RX20250707001',
    requiredTraceCount: 1,
    traceNo: '',
  },
  {
    id: 'mock-rx-3',
    itemId: '2003',
    itemName: '蒙脱石散',
    spec: '3g×10袋',
    lotNumber: '20250520',
    rxNo: 'RX20250707002',
    requiredTraceCount: 1,
    traceNo: '',
  },
];

/** 演示追溯码 → 品项批号（本地反查，不请求后端） */
export const TRACE_MOCK_IDENTITY_BY_CODE: Record<string, { itemId: string; lotNumber: string }> = {
  '82345678901234567801': { itemId: '1001', lotNumber: '20250101' },
  '82345678901234567802': { itemId: '1002', lotNumber: '20250215' },
  '82345678901234567803': { itemId: '1003', lotNumber: '20250308' },
  '82345678901234567804': { itemId: '1004', lotNumber: '20250401' },
  '82345678901234567811': { itemId: '2001', lotNumber: '20250101' },
  '82345678901234567812': { itemId: '2002', lotNumber: '20250601' },
  '82345678901234567813': { itemId: '2003', lotNumber: '20250520' },
};

/** 演示箱码 → 展开小码（本地 relation 模拟） */
export const TRACE_MOCK_BOX_EXPAND_BY_CODE: Record<string, string[]> = {
  '82345678909999999901': ['82345678901234567801', '82345678901234567802'],
  '82345678909999999902': ['82345678901234567803'],
};

/** 开发环境可用的演示小码列表 */
export const TRACE_MOCK_SAMPLE_CODES = Object.keys(TRACE_MOCK_IDENTITY_BY_CODE);

/** 开发环境可用的演示箱码列表 */
export const TRACE_MOCK_BOX_SAMPLE_CODES = Object.keys(TRACE_MOCK_BOX_EXPAND_BY_CODE);
