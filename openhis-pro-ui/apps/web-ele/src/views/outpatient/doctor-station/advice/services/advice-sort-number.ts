import type { AdviceDrugItem, AdviceRowItem } from './advice.types';

import { flattenAdviceRowsForResourceOps } from './advice-list';
import { inferAdviceLineEditorKind } from './advice-row';

/**
 * 门诊医嘱 sortNumber（BR-ORD-018）
 *
 * **写入（暂存前）**：药 / 诊 / 材 CREATE 均分配 sortNumber，步长 10；
 * 同组药品共用一号；UPDATE 有 requestId 时保留原号。
 * **读取（列表）**：见 advice-list.sortDoctorStationAdviceRowsStable
 * （待签发置顶 → 申请单聚邻 → 有效 sortNumber；派生行可继承锚定药品 sortNumber）。
 *
 * 调用方：buildAdviceDraftLines → assignMedicationSortNumbersBeforeSave。
 */

/** 药品展示排序号步长（留空档便于以后拖序插入） */
export const ADVICE_MEDICATION_SORT_STEP = 10;

function positiveSortNumber(raw: unknown): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
}

function filterListRowsForSortContext(rows: readonly AdviceRowItem[]): AdviceRowItem[] {
  return rows.filter((row) => !row.isEditor);
}

/** 从当前就诊主表取最大 sortNumber（药/诊/材，含成组展开成员） */
export function resolveEncounterMaxMedicationSortNumber(
  existingRows: readonly AdviceRowItem[],
): number {
  let max = 0;
  const flat = flattenAdviceRowsForResourceOps(filterListRowsForSortContext(existingRows));
  for (const row of flat) {
    const sn = positiveSortNumber(row.sortNumber);
    if (sn !== undefined && sn > max) {
      max = sn;
    }
  }
  return max;
}

function collectMedicationGroupSortMap(
  existingRows: readonly AdviceRowItem[],
  items: readonly AdviceDrugItem[],
): Map<string, number> {
  const map = new Map<string, number>();
  const flat = flattenAdviceRowsForResourceOps(filterListRowsForSortContext(existingRows));
  for (const row of flat) {
    if (
      String(row.resourceType ?? '')
        .trim()
        .toUpperCase() !== 'MEDICATION'
    ) {
      continue;
    }
    const gid = String(row.groupId ?? '').trim();
    const sn = positiveSortNumber(row.sortNumber);
    if (gid && sn !== undefined) {
      map.set(gid, sn);
    }
  }
  for (const item of items) {
    if (inferAdviceLineEditorKind(item) !== 'MEDICATION') continue;
    const gid = String(item.groupId ?? '').trim();
    const sn = positiveSortNumber(item.sortNumber);
    if (gid && sn !== undefined) {
      map.set(gid, sn);
    }
  }
  return map;
}

function resolveUpdateLineSortNumber(
  item: AdviceDrugItem,
  flatExisting: AdviceRowItem[],
): number | undefined {
  const fromItem = positiveSortNumber(item.sortNumber);
  if (fromItem !== undefined) return fromItem;
  const rid = String(item.medicationRequestId ?? '').trim();
  if (!rid) return undefined;
  const hit = flatExisting.find((row) => String(row.requestId ?? '').trim() === rid);
  return positiveSortNumber(hit?.sortNumber);
}

/**
 * 暂存前分配 sortNumber：药/诊/材 CREATE 按数组序追加 max+步长；
 * 同组药品共用同一号；UPDATE 保留原号。
 */
export function assignMedicationSortNumbersBeforeSave(
  items: AdviceDrugItem[],
  existingRows: readonly AdviceRowItem[] = [],
): AdviceDrugItem[] {
  const flatExisting = flattenAdviceRowsForResourceOps(filterListRowsForSortContext(existingRows));
  const groupSortByGid = collectMedicationGroupSortMap(existingRows, items);
  const batchGroupSort = new Map<string, number>();
  let nextSort = resolveEncounterMaxMedicationSortNumber(existingRows);

  return items.map((item) => {
    const kind = inferAdviceLineEditorKind(item);
    const rid = String(item.medicationRequestId ?? '').trim();
    if (rid) {
      const preserved = resolveUpdateLineSortNumber(item, flatExisting);
      if (preserved !== undefined) {
        return { ...item, sortNumber: preserved };
      }
      // 历史行缺 sortNumber：按新建追加，避免 UPDATE 保存失败
    }

    if (kind === 'MEDICATION') {
      const gid = String(item.groupId ?? '').trim();
      const existingGroupSn = gid ? groupSortByGid.get(gid) : undefined;
      if (existingGroupSn !== undefined) {
        return { ...item, sortNumber: existingGroupSn };
      }

      const batchGroupSn = gid ? batchGroupSort.get(gid) : undefined;
      if (batchGroupSn !== undefined) {
        return { ...item, sortNumber: batchGroupSn };
      }

      nextSort += ADVICE_MEDICATION_SORT_STEP;
      if (gid) {
        batchGroupSort.set(gid, nextSort);
      }
      return { ...item, sortNumber: nextSort };
    }

    // 诊疗 / 耗材：按导入/编辑数组序单独占号，保证与组套/历史穿插顺序一致
    nextSort += ADVICE_MEDICATION_SORT_STEP;
    return { ...item, sortNumber: nextSort };
  });
}

/** 药品草稿行 sortNumber（须已由 assignMedicationSortNumbersBeforeSave 写入） */
export function resolveMedicationDraftSortNumber(item: AdviceDrugItem): number {
  const sn = positiveSortNumber(item.sortNumber);
  if (sn === undefined) {
    throw new Error('药品草稿缺少排序号，请刷新后重试');
  }
  return sn;
}

/** 诊疗/耗材草稿可选 sortNumber（有则写入 IF-DOR-14） */
export function resolveOptionalDraftSortNumber(item: AdviceDrugItem): number | undefined {
  return positiveSortNumber(item.sortNumber);
}
