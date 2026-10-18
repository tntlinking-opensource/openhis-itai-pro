import type {
  PatientSkinTestHistoryItem,
  PatientSkinTestHistorySummary,
} from './patient-skin-test-history.types';

import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';
import type { OutpatientSkinTestRowDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import { formatDateTime } from '@zxplat/utils';

import { httpPageDoctorStationAdvice } from '#/api/doctor-station/advice.http';
import { httpListOutpatientSkinTestRows } from '#/api/outpatient-nurse-station/outpatient-nurse-station.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  collectDoctorStationAdviceFlatRows,
  fetchDoctorStationAdviceFlatRows,
} from '../advice/services/history-advice.service';
import { inferAdviceRowResourceType } from '../advice/services/advice-list';

const HISTORY_PAGE_SIZE = 500;

/** 临床结果是否为阳性（仅认 skinTestOutcomeCode / clinicalStatusCode） */
export function isSkinTestPositiveOutcome(code?: string): boolean {
  const normalizedCode = String(code ?? '')
    .trim()
    .toLowerCase();
  return normalizedCode === 'active' || normalizedCode === 'positive';
}

/** 汇总阳性药品名（去重、保序） */
export function buildPositiveMedicationNameSummary(items: PatientSkinTestHistoryItem[]): string[] {
  const names: string[] = [];
  const seen = new Set<string>();
  for (const item of items) {
    if (!item.positive) {
      continue;
    }
    const name = String(item.medicationName ?? '').trim();
    if (!name || seen.has(name)) {
      continue;
    }
    seen.add(name);
    names.push(name);
  }
  return names;
}

function buildEmptySummary(): PatientSkinTestHistorySummary {
  return {
    records: [],
    positiveRecords: [],
    positiveMedicationNames: [],
    hasPositive: false,
  };
}

function formatRecordedAt(raw?: null | string): string | undefined {
  const value = String(raw ?? '').trim();
  if (!value) {
    return undefined;
  }
  return formatDateTime(value) || value;
}

function resolveAdviceMedicationName(row: DoctorStationAdviceRowDto): string {
  return (
    String(row.adviceDisplayName ?? '').trim() ||
    String(row.contentSummary ?? '').trim() ||
    '未知药品'
  );
}

/** 护士站皮试行 → 历史皮试记录（仅已登记） */
function mapNurseSkinTestRow(row: OutpatientSkinTestRowDto): PatientSkinTestHistoryItem | null {
  const allergyRecordId = preserveApiIdForRequest(row.allergyRecordId);
  if (!allergyRecordId) {
    return null;
  }
  const encounterId = String(row.encounterId ?? '').trim();
  const medicationRequestId = preserveApiIdForRequest(row.medicationRequestId);
  const medicationName = String(row.medicationName ?? '').trim() || '未知药品';
  const clinicalStatusCode = String(row.clinicalStatusCode ?? '').trim() || undefined;
  const clinicalStatusText =
    String(row.clinicalStatusText ?? '').trim() || clinicalStatusCode || '—';
  const recordedAt = formatRecordedAt(row.recordedDate);
  const key = [encounterId, medicationRequestId ?? '', allergyRecordId, recordedAt ?? ''].join('|');
  return {
    key,
    encounterId,
    medicationRequestId,
    medicationName,
    clinicalStatusCode,
    clinicalStatusText,
    recordedAt,
    positive: isSkinTestPositiveOutcome(clinicalStatusCode),
  };
}

/** 医嘱皮试药行 → 历史皮试记录（含跨就诊历史） */
function mapAdviceSkinTestRow(row: DoctorStationAdviceRowDto): PatientSkinTestHistoryItem | null {
  if (row.skinTestFlag !== true) {
    return null;
  }
  const outcomeCode = String(row.skinTestOutcomeCode ?? '').trim();
  const outcomeText = String(row.skinTestOutcomeText ?? '').trim();
  if (!outcomeCode && !outcomeText) {
    return null;
  }
  const encounterId = String(row.encounterId ?? '').trim();
  const medicationRequestId = preserveApiIdForRequest(row.requestId);
  const medicationName = resolveAdviceMedicationName(row);
  const clinicalStatusText = outcomeText || outcomeCode || '—';
  const recordedAt = formatRecordedAt(row.reqOpenedTime);
  const key = [encounterId, medicationRequestId ?? '', outcomeCode, recordedAt ?? ''].join('|');
  return {
    key,
    encounterId,
    medicationRequestId,
    medicationName,
    clinicalStatusCode: outcomeCode || undefined,
    clinicalStatusText,
    recordedAt,
    positive: isSkinTestPositiveOutcome(outcomeCode),
  };
}

/** 合并去重：同就诊同药品 request 保留记录时间较新的一条 */
function mergeSkinTestHistoryRecords(
  records: PatientSkinTestHistoryItem[],
): PatientSkinTestHistoryItem[] {
  const byDedupeKey = new Map<string, PatientSkinTestHistoryItem>();
  for (const record of records) {
    const dedupeKey = `${record.encounterId}|${record.medicationRequestId ?? record.medicationName}`;
    const existing = byDedupeKey.get(dedupeKey);
    if (!existing) {
      byDedupeKey.set(dedupeKey, record);
      continue;
    }
    const existingTime = String(existing.recordedAt ?? '');
    const nextTime = String(record.recordedAt ?? '');
    if (nextTime.localeCompare(existingTime) > 0) {
      byDedupeKey.set(dedupeKey, record);
    }
  }
  return [...byDedupeKey.values()].toSorted((left, right) =>
    String(right.recordedAt ?? '').localeCompare(String(left.recordedAt ?? '')),
  );
}

function buildSummary(records: PatientSkinTestHistoryItem[]): PatientSkinTestHistorySummary {
  const positiveRecords = records.filter((item) => item.positive);
  const positiveMedicationNames = buildPositiveMedicationNameSummary(positiveRecords);
  return {
    records,
    positiveRecords,
    positiveMedicationNames,
    hasPositive: positiveRecords.length > 0,
  };
}

/** 拉取患者历史皮试记录：当前就诊护士登记 + 跨就诊医嘱皮试结果 */
export async function fetchPatientSkinTestHistorySummary(
  patientId: string,
  encounterId: string,
): Promise<PatientSkinTestHistorySummary> {
  const pid = String(patientId ?? '').trim();
  const eid = String(encounterId ?? '').trim();
  if (!pid || !eid) {
    return buildEmptySummary();
  }

  const records: PatientSkinTestHistoryItem[] = [];

  try {
    const skinTestRes = await httpListOutpatientSkinTestRows({
      patientId: pid,
      encounterId: eid,
    });
    for (const row of skinTestRes.rows ?? []) {
      const mapped = mapNurseSkinTestRow(row);
      if (mapped) {
        records.push(mapped);
      }
    }
  } catch {
    // 读侧失败保持空列表，不阻断卡片展示
  }

  const [currentAdviceRows, historyAdviceRows] = await Promise.all([
    fetchDoctorStationAdviceFlatRows(pid, eid, 'CURRENT_ADVICE', HISTORY_PAGE_SIZE),
    fetchDoctorStationAdviceFlatRows(pid, eid, 'HISTORY', HISTORY_PAGE_SIZE),
  ]);

  for (const row of [...currentAdviceRows, ...historyAdviceRows]) {
    if (inferAdviceRowResourceType(row) !== 'MEDICATION') {
      continue;
    }
    const mapped = mapAdviceSkinTestRow(row);
    if (mapped) {
      records.push(mapped);
    }
  }

  // 兜底：若扁平拉取为空，再试一次 page 解析（兼容旧响应结构）
  if (records.length === 0) {
    try {
      const pageRes = await httpPageDoctorStationAdvice({
        patientId: pid,
        encounterId: eid,
        adviceScopeTabCode: 'HISTORY',
        pageNo: 1,
        pageSize: HISTORY_PAGE_SIZE,
      });
      for (const row of collectDoctorStationAdviceFlatRows(pageRes)) {
        if (inferAdviceRowResourceType(row) !== 'MEDICATION') {
          continue;
        }
        const mapped = mapAdviceSkinTestRow(row);
        if (mapped) {
          records.push(mapped);
        }
      }
    } catch {
      // ignore
    }
  }

  return buildSummary(mergeSkinTestHistoryRecords(records));
}

/** 过敏史摘要文案：无记录 / 无阳性 / 阳性药品列表 */
export function formatPatientAllergySkinTestSummary(
  summary: PatientSkinTestHistorySummary,
): string {
  if (summary.records.length === 0) {
    return '-';
  }
  if (!summary.hasPositive) {
    return '无阳性记录';
  }
  return summary.positiveMedicationNames.join('、');
}
