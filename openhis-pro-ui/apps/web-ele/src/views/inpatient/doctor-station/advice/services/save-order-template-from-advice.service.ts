import type { AdviceRowItem } from './advice.types';

import type { AdviceSourceLineKeyItem } from '#/api/basic/order-template';

import { flattenAdviceRowsForResourceOps } from './advice-list';
import { resourceKeyFromRow } from './advice-row';

/** 另存组套弹窗候选行（含后端源行键） */
export interface SaveOrderTemplateCandidateRow {
  key: string;
  type: string;
  content: string;
  status: string;
  requestId: string;
  productTableName: string;
  listRowKey?: string;
  selectable: boolean;
}

/** 从当前医嘱列表抽取可另存候选行（成组药品展平为单行） */
export function buildSaveOrderTemplateCandidates(
  rows: readonly AdviceRowItem[],
): SaveOrderTemplateCandidateRow[] {
  const flat = flattenAdviceRowsForResourceOps(rows.filter((row) => !row.isEditor));
  return flat.map((row) => {
    const resourceKey = resourceKeyFromRow(row);
    return {
      key: row.key,
      type: row.type,
      content: row.content,
      status: row.status,
      requestId: resourceKey?.requestId ?? '',
      productTableName: resourceKey?.productTableName ?? '',
      listRowKey: row.key,
      selectable: Boolean(resourceKey),
    };
  });
}

/** 候选行 → IF-BAS-OTL-10 源行键 */
export function toAdviceSourceLineKey(row: SaveOrderTemplateCandidateRow): AdviceSourceLineKeyItem {
  return {
    requestId: row.requestId,
    productTableName: row.productTableName,
    listRowKey: row.listRowKey,
  };
}
