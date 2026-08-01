/**
 * 诊疗 / 药品 / 耗材编辑弹窗：按列表查询结果顺序解析「上一个 / 下一个」。
 * 支持跨分页：当前页首尾再翻时由列表页换页后打开相邻记录。
 */

export type CatalogRecordNavDirection = -1 | 1;

export interface CatalogRecordNavPageMeta {
  /** 当前页码（从 1 起） */
  pageNo?: number;
  /** 每页条数 */
  pageSize?: number;
  /** 符合当前筛选条件的总条数 */
  listTotal?: number;
}

/** 规范化 id 列表（去空、转 string） */
export function normalizeCatalogRecordIds(
  recordIds: Array<null | number | string | undefined> | undefined,
): string[] {
  if (!recordIds?.length) {
    return [];
  }
  // 只保留可导航的非空主键
  return recordIds.map((id) => String(id ?? '').trim()).filter(Boolean);
}

/** 当前记录在列表中的位置与可否上下翻（含跨页） */
export function resolveCatalogRecordNavState(
  recordIds: Array<null | number | string | undefined> | undefined,
  currentId: null | number | string | undefined,
  pageMeta?: CatalogRecordNavPageMeta,
): { canNext: boolean; canPrev: boolean; index: number; listTotal: number } {
  const ids = normalizeCatalogRecordIds(recordIds);
  const current = String(currentId ?? '').trim();
  const index = current ? ids.indexOf(current) : -1;
  const pageNo = Math.max(1, Number(pageMeta?.pageNo) || 1);
  const pageSize = Math.max(1, Number(pageMeta?.pageSize) || ids.length || 1);
  const listTotal = Math.max(0, Number(pageMeta?.listTotal) || 0);
  const effectiveTotal = listTotal > 0 ? listTotal : ids.length;

  if (index < 0 || effectiveTotal <= 0) {
    return {
      index,
      listTotal: effectiveTotal,
      canPrev: false,
      canNext: false,
    };
  }

  // 按「当前筛选结果」的全局下标判断能否继续翻
  const globalIndex = (pageNo - 1) * pageSize + index;
  return {
    index,
    listTotal: effectiveTotal,
    canPrev: globalIndex > 0,
    canNext: globalIndex < effectiveTotal - 1,
  };
}

/**
 * 在当前页或相邻页解析目标记录 id。
 * 同页直接返回 id；需翻页时返回 targetPageNo，由调用方拉页后再取首/末条。
 */
export function resolveCatalogRecordNavigation(options: {
  currentId: null | number | string | undefined;
  direction: CatalogRecordNavDirection;
  listTotal: number;
  pageNo: number;
  pageSize: number;
  recordIds: Array<null | number | string | undefined> | undefined;
}):
  | { kind: 'none' }
  | { kind: 'page'; targetPageNo: number; pick: 'first' | 'last' }
  | { kind: 'same-page'; id: string } {
  const ids = normalizeCatalogRecordIds(options.recordIds);
  const current = String(options.currentId ?? '').trim();
  const index = current ? ids.indexOf(current) : -1;
  const pageNo = Math.max(1, Number(options.pageNo) || 1);
  const pageSize = Math.max(1, Number(options.pageSize) || 1);
  const listTotal = Math.max(0, Number(options.listTotal) || 0);
  const totalPages = listTotal > 0 ? Math.max(1, Math.ceil(listTotal / pageSize)) : 1;

  if (index < 0) {
    return { kind: 'none' };
  }

  const nextIndex = index + options.direction;
  if (nextIndex >= 0 && nextIndex < ids.length) {
    const id = ids[nextIndex];
    if (!id) {
      return { kind: 'none' };
    }
    return { kind: 'same-page', id };
  }

  if (options.direction === 1 && pageNo < totalPages) {
    return { kind: 'page', targetPageNo: pageNo + 1, pick: 'first' };
  }
  if (options.direction === -1 && pageNo > 1) {
    return { kind: 'page', targetPageNo: pageNo - 1, pick: 'last' };
  }
  return { kind: 'none' };
}
