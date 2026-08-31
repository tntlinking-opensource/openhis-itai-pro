import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

import { dedupeTraceCodes } from './trace-code-parse';

/** 箱码展开结果（对齐码上放心 query.relation 语义：大/中码 → 小码列表） */
export interface TraceBoxExpandItem {
  sourceCode: string;
  childCodes: string[];
  /** 是否可继续绑定（含软降级） */
  ok: boolean;
  /**
   * 码上放心展开失败后的软降级：仍用原码落库供医保 350x；
   * 药监箱码关联可事后补入库/补传，不挡本次扫码。
   */
  softFallback?: boolean;
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

const EXPAND_API = '/msfxBoxExpand/expandTraceBoxCodes';

/** 调用后端箱码展开（msfx query.relation 封装） */
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
    if (status === 'NOT_BOX' || (status === 'OK' && childCodes.length === 0)) {
      return {
        sourceCode,
        childCodes: [sourceCode],
        ok: true,
        message: hit.message?.trim() || '小码直扫',
      };
    }
    // NEED_INBOUND / FAIL：不挡医保扫码；原码继续落库，码上放心事后补
    const tip = hit.message?.trim() || '箱码展开失败';
    const needInbound = /入库|102|未展开|NEED_INBOUND/i.test(tip);
    const guide = needInbound
      ? '请先完成采购入库 102 并处理成功后再展开（D-12）'
      : '码上放心可后补';
    return {
      sourceCode,
      childCodes: [sourceCode],
      ok: true,
      softFallback: true,
      message: `${tip}；${guide}。本次已按原码继续（医保可先上报）`,
    };
  });
}

/** 将扫码结果展开为小码列表（展开失败时软降级为原码） */
export async function expandTraceBoxCodes(
  codes: string[],
): Promise<{ expanded: string[]; items: TraceBoxExpandItem[] }> {
  if (codes.length === 0) return { expanded: [], items: [] };

  const items = await expandTraceBoxCodesByApi(codes);

  const expanded: string[] = [];
  for (const item of items) {
    if (item.ok) expanded.push(...item.childCodes);
  }

  return { expanded: dedupeTraceCodes(expanded), items };
}
