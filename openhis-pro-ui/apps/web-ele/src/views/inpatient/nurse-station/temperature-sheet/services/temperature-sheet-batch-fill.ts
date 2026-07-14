import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { TemperatureSheetFillRowDraft } from './map-nurse-station-temperature-sheet';

import type {
  SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
  TemperatureSheetTimeSlot,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import { httpGetNurseStationTemperatureSheetData } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  applyFillSlotsToDraft,
  createEmptyExtraRecord,
  createEmptyFillRows,
  mapFillRowToSaveRequest,
} from './map-nurse-station-temperature-sheet';
import {
  syncSpecialEventShortcutFields,
  syncSpecialEventTimesOnSelectionChange,
} from './temperature-sheet-special-events';

/** 并行加载患者体温回显时的并发上限 */
export const TEMPERATURE_SHEET_BATCH_LOAD_CONCURRENCY = 8;

/** 批量保存单批提交上限 */
export const TEMPERATURE_SHEET_BATCH_SAVE_LIMIT = 50;

/** 按时点批量录入：单行患者草稿 */
export interface TemperatureSheetBatchFillRowDraft {
  bedNo: string;
  dayRows: TemperatureSheetFillRowDraft[];
  encounterId: string;
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

/** 自然排序床号 */
export function compareTemperatureSheetBatchBedNo(a: string, b: string): number {
  return a.trim().localeCompare(b.trim(), 'zh-CN', { numeric: true });
}

/** 从当日 6 槽回显中提取目标时次行 */
export function resolveBatchSlotRowFromFillSlots(
  fillSlots: Parameters<typeof applyFillSlotsToDraft>[0],
  timeSlot: TemperatureSheetTimeSlot,
): { dayRows: TemperatureSheetFillRowDraft[]; slotRow: TemperatureSheetFillRowDraft } {
  const draft = applyFillSlotsToDraft(fillSlots ?? []);
  const slotRow = draft.rows.find((row) => row.timeSlot === timeSlot) ?? draft.rows[0];
  return { dayRows: draft.rows, slotRow };
}

/** 由护士站患者列表构造批量录入行骨架 */
export function buildEmptyBatchFillRow(
  patient: NurseStationModulePatient,
  timeSlot: TemperatureSheetTimeSlot,
): TemperatureSheetBatchFillRowDraft {
  const dayRows = createEmptyFillRows();
  const slotRow = dayRows.find((row) => row.timeSlot === timeSlot) ?? dayRows[0];
  return {
    encounterId: preserveApiIdForRequest(patient.id) ?? '',
    patientId: preserveApiIdForRequest(patient.patientId ?? patient.id) ?? '',
    bedNo: patient.bedNo?.trim() || '-',
    name: patient.name?.trim() || '-',
    gender: patient.gender?.trim() || '',
    dayRows,
    slotRow,
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
      results[current] = await mapper(items[current], current);
      done += 1;
      onProgress?.({ done, total: items.length });
    }
  }

  const workerCount = Math.min(limit, items.length);
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  return results;
}

/** 并行加载左侧列表患者在指定日期的目标时次回显 */
export async function loadTemperatureSheetBatchFillRows(
  patients: NurseStationModulePatient[],
  recordDate: string,
  timeSlot: TemperatureSheetTimeSlot,
  onProgress?: (progress: TemperatureSheetBatchLoadProgress) => void,
): Promise<TemperatureSheetBatchFillRowDraft[]> {
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
          ...buildEmptyBatchFillRow(patient, timeSlot),
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
        const { dayRows, slotRow } = resolveBatchSlotRowFromFillSlots(
          response.fillSlots ?? [],
          timeSlot,
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
        };
      } catch {
        return {
          ...buildEmptyBatchFillRow(patient, timeSlot),
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

/** 判断批量行是否有待保存内容 */
export function hasBatchFillRowSaveContent(row: TemperatureSheetBatchFillRowDraft): boolean {
  const extraRecord = createEmptyExtraRecord();
  return (
    mapFillRowToSaveRequest(row.slotRow, extraRecord, '', row.encounterId, row.patientId) !== null
  );
}

/** 收集批量保存请求（仅含已填写行） */
export function collectBatchTemperatureSheetSaveRequests(
  rows: TemperatureSheetBatchFillRowDraft[],
  recordDate: string,
): SaveNurseStationTemperatureSheetAtRecordedTimeRequest[] {
  const extraRecord = createEmptyExtraRecord();
  const requests: SaveNurseStationTemperatureSheetAtRecordedTimeRequest[] = [];
  for (const row of rows) {
    const request = mapFillRowToSaveRequest(
      row.slotRow,
      extraRecord,
      recordDate,
      row.encounterId,
      row.patientId,
    );
    if (request) {
      requests.push(request);
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
      if (row && response.vitalSignId) {
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
