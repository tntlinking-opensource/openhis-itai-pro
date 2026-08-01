import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type {
  TemperatureSheetExtraRecordDraft,
  TemperatureSheetFillRowDraft,
} from './map-nurse-station-temperature-sheet';

import type { SaveNurseStationTemperatureSheetAtRecordedTimeRequest } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import { httpGetNurseStationTemperatureSheetData } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  applyFillSlotsToDraft,
  createEmptyExtraRecord,
  createEmptyFillRow,
  createEmptyFillRows,
  mapFillRowToSaveRequest,
  normalizeTemperatureSheetRecordTime,
  syncFillRowChartTimeSlot,
} from './map-nurse-station-temperature-sheet';
import { applyDetailItemsToFillRow } from './temperature-sheet-detail-items';
import {
  syncSpecialEventShortcutFields,
  syncSpecialEventTimesOnSelectionChange,
} from './temperature-sheet-special-events';

/** 并行加载患者体温回显时的并发上限 */
export const TEMPERATURE_SHEET_BATCH_LOAD_CONCURRENCY = 8;

/** 批量保存单批提交上限 */
export const TEMPERATURE_SHEET_BATCH_SAVE_LIMIT = 50;

/** 按时间批量录入：单行患者草稿 */
export interface TemperatureSheetBatchFillRowDraft {
  bedNo: string;
  dayRows: TemperatureSheetFillRowDraft[];
  encounterId: string;
  /** 补充记录（图表槽为 22 时随本条保存，否则另提 22:00） */
  extraRecord: TemperatureSheetExtraRecordDraft;
  gender: string;
  loadError?: string;
  name: string;
  patientId: string;
  slotRow: TemperatureSheetFillRowDraft;
}

export interface TemperatureSheetBatchLoadProgress {
  done: number;
  total: number;
}

/** 补充记录是否有待保存内容 */
export function hasTemperatureSheetExtraRecordContent(
  extraRecord: TemperatureSheetExtraRecordDraft,
): boolean {
  return Object.values(extraRecord).some((value) => value.trim().length > 0);
}

/** 自然排序床号 */
export function compareTemperatureSheetBatchBedNo(a: string, b: string): number {
  return a.trim().localeCompare(b.trim(), 'zh-CN', { numeric: true });
}

/** 当前时分 HH:mm（批量默认录入时间） */
export function resolveDefaultBatchRecordTimeHm(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

/** 按实际时分从回显槽中取行；无精确匹配则建空行 */
function resolveFillRowByRecordTime(
  fillSlots: Parameters<typeof applyFillSlotsToDraft>[0],
  recordTime: string,
): TemperatureSheetFillRowDraft {
  const normalized = normalizeTemperatureSheetRecordTime(recordTime);
  const matchedSlot = (fillSlots ?? []).find(
    (slot) => normalizeTemperatureSheetRecordTime(slot.timeSlot) === normalized,
  );
  const row = createEmptyFillRow();
  row.recordTime = normalized;
  syncFillRowChartTimeSlot(row);
  if (!matchedSlot) {
    return row;
  }
  row.vitalSignId = matchedSlot.vitalSignId?.trim() || undefined;
  applyDetailItemsToFillRow(row, createEmptyExtraRecord(), matchedSlot.detailItems);
  return row;
}

/** 从当日回显中提取目标时分行与补充记录 */
export function resolveBatchSlotRowFromFillSlots(
  fillSlots: Parameters<typeof applyFillSlotsToDraft>[0],
  recordTime: string,
): {
  dayRows: TemperatureSheetFillRowDraft[];
  extraRecord: TemperatureSheetExtraRecordDraft;
  slotRow: TemperatureSheetFillRowDraft;
} {
  const draft = applyFillSlotsToDraft(fillSlots ?? []);
  return {
    dayRows: draft.rows,
    extraRecord: draft.extraRecord,
    slotRow: resolveFillRowByRecordTime(fillSlots ?? [], recordTime),
  };
}

/** 由护士站患者列表构造批量录入行骨架 */
export function buildEmptyBatchFillRow(
  patient: NurseStationModulePatient,
  recordTime: string,
): TemperatureSheetBatchFillRowDraft {
  const dayRows = createEmptyFillRows();
  const slotRow = createEmptyFillRow();
  slotRow.recordTime = normalizeTemperatureSheetRecordTime(recordTime);
  syncFillRowChartTimeSlot(slotRow);
  return {
    encounterId: preserveApiIdForRequest(patient.id) ?? '',
    patientId: preserveApiIdForRequest(patient.patientId ?? patient.id) ?? '',
    bedNo: patient.bedNo?.trim() || '-',
    name: patient.name?.trim() || '-',
    gender: patient.gender?.trim() || '',
    dayRows,
    slotRow,
    extraRecord: createEmptyExtraRecord(),
  };
}

/** 限制并发执行异步任务 */
async function mapWithConcurrency<T, R>(
  items: T[],
  limit: number,
  mapper: (item: T, index: number) => Promise<R>,
  onProgress?: (progress: TemperatureSheetBatchLoadProgress) => void,
): Promise<R[]> {
  if (items.length === 0) {
    return [];
  }
  const results: R[] = new Array(items.length);
  let nextIndex = 0;
  let done = 0;

  async function worker(): Promise<void> {
    while (nextIndex < items.length) {
      const current = nextIndex;
      nextIndex += 1;
      const item = items[current];
      if (item === undefined) {
        continue;
      }
      results[current] = await mapper(item, current);
      done += 1;
      onProgress?.({ done, total: items.length });
    }
  }

  const workerCount = Math.min(limit, items.length);
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  return results;
}

/** 并行加载左侧列表患者在指定日期、时分的回显 */
export async function loadTemperatureSheetBatchFillRows(
  patients: NurseStationModulePatient[],
  recordDate: string,
  recordTime: string,
  onProgress?: (progress: TemperatureSheetBatchLoadProgress) => void,
): Promise<TemperatureSheetBatchFillRowDraft[]> {
  const normalizedTime = normalizeTemperatureSheetRecordTime(recordTime);
  const sortedPatients = [...patients].sort((a, b) =>
    compareTemperatureSheetBatchBedNo(a.bedNo, b.bedNo),
  );

  const rows = await mapWithConcurrency(
    sortedPatients,
    TEMPERATURE_SHEET_BATCH_LOAD_CONCURRENCY,
    async (patient) => {
      const encounterId = preserveApiIdForRequest(patient.id);
      const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
      if (!encounterId || !patientId) {
        return {
          ...buildEmptyBatchFillRow(patient, normalizedTime),
          loadError: '患者标识无效',
        };
      }

      try {
        const response = await httpGetNurseStationTemperatureSheetData({
          encounterId,
          patientId,
          windowRange: '7d',
          pageIndex: 0,
          focusRecordDate: recordDate,
        });
        const { dayRows, extraRecord, slotRow } = resolveBatchSlotRowFromFillSlots(
          response.fillSlots ?? [],
          normalizedTime,
        );
        syncSpecialEventTimesOnSelectionChange(slotRow);
        syncSpecialEventShortcutFields(slotRow);
        return {
          encounterId,
          patientId,
          bedNo: patient.bedNo?.trim() || '-',
          name: patient.name?.trim() || '-',
          gender: patient.gender?.trim() || '',
          dayRows,
          slotRow,
          extraRecord,
        };
      } catch {
        return {
          ...buildEmptyBatchFillRow(patient, normalizedTime),
          encounterId,
          patientId,
          loadError: '加载失败',
        };
      }
    },
    onProgress,
  );

  return rows;
}

/** 判断批量行是否有待保存内容（体征或补充记录） */
export function hasBatchFillRowSaveContent(row: TemperatureSheetBatchFillRowDraft): boolean {
  if (
    mapFillRowToSaveRequest(row.slotRow, row.extraRecord, '', row.encounterId, row.patientId) !==
    null
  ) {
    return true;
  }
  return hasTemperatureSheetExtraRecordContent(row.extraRecord);
}

/** 解析图表 22 槽草稿（非 22 槽时单独提交补充记录） */
function resolveBatchSlot22Row(
  row: TemperatureSheetBatchFillRowDraft,
): TemperatureSheetFillRowDraft {
  const found = row.dayRows.find((item) => item.timeSlot === '22');
  if (found) {
    return found;
  }
  return createEmptyFillRow('22');
}

/** 收集批量保存请求（补充记录挂图表 22 槽） */
export function collectBatchTemperatureSheetSaveRequests(
  rows: TemperatureSheetBatchFillRowDraft[],
  recordDate: string,
): SaveNurseStationTemperatureSheetAtRecordedTimeRequest[] {
  const requests: SaveNurseStationTemperatureSheetAtRecordedTimeRequest[] = [];
  const requestKeys = new Set<string>();

  /** 追加去重后的保存请求 */
  function pushRequest(
    request: SaveNurseStationTemperatureSheetAtRecordedTimeRequest | null,
  ): void {
    if (!request) {
      return;
    }
    const key = `${request.encounterId}|${request.timeSlot}`;
    if (requestKeys.has(key)) {
      return;
    }
    requestKeys.add(key);
    requests.push(request);
  }

  for (const row of rows) {
    // 当前时分落入图表 22 槽时带补充项，其它时次仅体征
    const slotExtra = row.slotRow.timeSlot === '22' ? row.extraRecord : createEmptyExtraRecord();
    pushRequest(
      mapFillRowToSaveRequest(row.slotRow, slotExtra, recordDate, row.encounterId, row.patientId),
    );

    // 非 22 槽但填了补充项：额外提交 22:00（保留当日已有 22 点体征）
    if (row.slotRow.timeSlot !== '22' && hasTemperatureSheetExtraRecordContent(row.extraRecord)) {
      pushRequest(
        mapFillRowToSaveRequest(
          resolveBatchSlot22Row(row),
          row.extraRecord,
          recordDate,
          row.encounterId,
          row.patientId,
        ),
      );
    }
  }
  return requests;
}

/** 批量保存结果摘要 */
export interface TemperatureSheetBatchSaveResult {
  failedPatients: Array<{ bedNo: string; message: string; name: string }>;
  savedCount: number;
}

/** 逐条提交批量保存请求 */
export async function saveTemperatureSheetBatchFillRows(
  requests: SaveNurseStationTemperatureSheetAtRecordedTimeRequest[],
  rowByEncounterId: Map<string, TemperatureSheetBatchFillRowDraft>,
  saveFn: (
    request: SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
  ) => Promise<{ vitalSignId?: null | string }>,
): Promise<TemperatureSheetBatchSaveResult> {
  const failedPatients: Array<{ bedNo: string; message: string; name: string }> = [];
  let savedCount = 0;

  for (const request of requests.slice(0, TEMPERATURE_SHEET_BATCH_SAVE_LIMIT)) {
    try {
      const response = await saveFn(request);
      savedCount += 1;
      const row = rowByEncounterId.get(request.encounterId);
      if (row && response.vitalSignId && request.timeSlot === row.slotRow.recordTime) {
        row.slotRow.vitalSignId = response.vitalSignId.trim() || undefined;
      }
    } catch (error) {
      const row = rowByEncounterId.get(request.encounterId);
      failedPatients.push({
        bedNo: row?.bedNo ?? '-',
        name: row?.name ?? '-',
        message: error instanceof Error ? error.message : '保存失败',
      });
    }
  }

  return { savedCount, failedPatients };
}

/** 判断行是否已有服务端回显（用于表格高亮） */
export function isBatchFillRowPersisted(row: TemperatureSheetBatchFillRowDraft): boolean {
  return Boolean(row.slotRow.vitalSignId?.trim());
}
