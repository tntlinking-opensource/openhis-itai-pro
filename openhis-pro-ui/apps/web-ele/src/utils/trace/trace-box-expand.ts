import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

import { dedupeTraceCodes } from './trace-code-parse';
import { TRACE_MOCK_BOX_EXPAND_BY_CODE } from './trace-mock-data';

/** 箱码展开结果（对齐码上放心 query.relation 语义：大/中码 → 小码列表） */
export interface TraceBoxExpandItem {
  sourceCode: string;
  childCodes: string[];
  ok: boolean;
  message?: string;
}

interface ExpandTraceBoxCodesApiItem {
  sourceTraceNo?: string;
  childTraceNos?: string[];
  expandStatusEnum?: string;
  message?: string;
}

interface ExpandTraceBoxCodesApiResponse {
  items?: ExpandTraceBoxCodesApiItem[];
}

const EXPAND_API = '/inventoryWarehouseInboundReceipt/expandTraceBoxCodes';

/** 调用后端箱码展开（query.relation 封装）；未实现时抛错由上层降级 */
async function expandTraceBoxCodesByApi(codes: string[]): Promise<TraceBoxExpandItem[]> {
  const res = await requestClient.post<ApiResponse<ExpandTraceBoxCodesApiResponse>>(EXPAND_API, {
    traceNos: codes,
  });
  const apiItems = res.data?.items ?? [];
  return codes.map((sourceCode) => {
    const hit = apiItems.find((i) => (i.sourceTraceNo ?? '').trim() === sourceCode);
    if (!hit) {
      return { sourceCode, childCodes: [sourceCode], ok: true, message: '小码直扫' };
    }
    const status = String(hit.expandStatusEnum ?? 'OK').trim();
    const childCodes = (hit.childTraceNos ?? [])
      .map((c) => String(c ?? '').trim())
      .filter((c) => c !== '');
    if (status === 'OK' && childCodes.length > 0) {
      return { sourceCode, childCodes, ok: true };
    }
    if (status === 'NOT_BOX' || childCodes.length === 0) {
      return {
        sourceCode,
        childCodes: [sourceCode],
        ok: true,
        message: '小码直扫',
      };
    }
    return {
      sourceCode,
      childCodes: [],
      ok: false,
      message: hit.message?.trim() || '箱码展开失败',
    };
  });
}

function expandTraceBoxCodesByMock(codes: string[]): TraceBoxExpandItem[] {
  return codes.map((sourceCode) => {
    const children = TRACE_MOCK_BOX_EXPAND_BY_CODE[sourceCode];
    if (children?.length) {
      return { sourceCode, childCodes: children, ok: true };
    }
    return { sourceCode, childCodes: [sourceCode], ok: true, message: '小码直扫' };
  });
}

/** 箱码模式下将扫码结果展开为小码列表 */
export async function expandTraceBoxCodes(
  codes: string[],
  options: { useMock?: boolean },
): Promise<{ expanded: string[]; items: TraceBoxExpandItem[] }> {
  if (codes.length === 0) return { expanded: [], items: [] };

  const items = options.useMock
    ? expandTraceBoxCodesByMock(codes)
    : await expandTraceBoxCodesByApi(codes);

  const expanded: string[] = [];
  for (const item of items) {
    if (item.ok) expanded.push(...item.childCodes);
  }

  return { expanded: dedupeTraceCodes(expanded), items };
}
