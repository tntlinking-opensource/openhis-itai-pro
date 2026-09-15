import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { isMsfxUiEnabled } from '#/utils/project-optional-modules';

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
/** 与平台 relation 单批上限对齐，避免一次塞上千码把请求拖死 */
const EXPAND_REQUEST_BATCH_SIZE = 10;
/** 单批仍可能 20～60s；分批后按批计时，给足余量 */
const EXPAND_TIMEOUT_MS = 180_000;

function mapExpandApiItems(
  codes: string[],
  apiItems: ExpandTraceBoxCodesApiItem[],
): TraceBoxExpandItem[] {
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

/** 调用后端箱码展开（msfx query.relation 封装）；按批请求，单批失败软降级为原码 */
async function expandTraceBoxCodesByApi(codes: string[]): Promise<TraceBoxExpandItem[]> {
  const out: TraceBoxExpandItem[] = [];
  for (let from = 0; from < codes.length; from += EXPAND_REQUEST_BATCH_SIZE) {
    const batch = codes.slice(from, from + EXPAND_REQUEST_BATCH_SIZE);
    try {
      const res = await requestClient.post<ApiResponse<ExpandTraceBoxCodesApiResponse>>(
        EXPAND_API,
        { traceNos: batch },
        { timeout: EXPAND_TIMEOUT_MS },
      );
      out.push(...mapExpandApiItems(batch, res.data?.items ?? []));
    } catch {
      for (const sourceCode of batch) {
        out.push({
          sourceCode,
          childCodes: [sourceCode],
          ok: true,
          softFallback: true,
          message: '箱码展开超时或失败；本次已按原码继续',
        });
      }
    }
  }
  return out;
}

/** 将扫码结果展开为小码列表（展开失败时软降级为原码） */
export async function expandTraceBoxCodes(
  codes: string[],
): Promise<{ expanded: string[]; items: TraceBoxExpandItem[] }> {
  if (codes.length === 0) return { expanded: [], items: [] };

  // 院点未开码上放心：不调 msfx 箱码展开、不产生「码上放心可后补」类提示，原码直通
  if (!isMsfxUiEnabled()) {
    const items = codes.map((sourceCode) => ({
      sourceCode,
      childCodes: [sourceCode],
      ok: true,
    }));
    return { expanded: dedupeTraceCodes(codes), items };
  }

  const items = await expandTraceBoxCodesByApi(codes);

  const expanded: string[] = [];
  for (const item of items) {
    if (item.ok) expanded.push(...item.childCodes);
  }

  return { expanded: dedupeTraceCodes(expanded), items };
}
