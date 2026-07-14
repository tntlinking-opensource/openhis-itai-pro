import type { DoctorStationPatient } from '../doctorPatientStore';

import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';
import type { PrescriptionSheetData } from '#/views/outpatient/dispense/components/prescription-sheet.types';
import type {
  DispensePrescriptionBlock,
  DispensePrescriptionLine,
  DispenseQueuePatient,
} from '#/views/outpatient/dispense/components/types';

import { formatDateTime } from '@vben/utils';

import { buildPrescriptionSheetData } from '#/views/outpatient/dispense/components/build-prescription-sheet-data';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';

/** 是否中药医嘱行 */
function isTcmAdviceRow(row: DoctorStationAdviceRowDto): boolean {
  if (row.tcmFlag === true) return true;
  if (row.tcmFlag === false) return false;
  const categoryCode = String(row.catalogCategoryCode ?? row.categoryCode ?? '').trim();
  return categoryCode === HERBAL_MEDICATION_CATEGORY_CODE;
}

function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 医嘱行数量展示 */
function formatAdviceQuantityLabel(row: DoctorStationAdviceRowDto): string {
  const q = scalarToTrimmedString(row.quantity);
  const u = String(row.unitName ?? row.unitCode ?? '').trim();
  if (!q && !u) {
    return '—';
  }
  if (!u) {
    return q || '—';
  }
  if (!q) {
    return u;
  }
  return `${q}${u}`;
}

/** 医嘱行用法用量展示 */
function formatAdviceFreqLabel(row: DoctorStationAdviceRowDto): string {
  const parts: string[] = [];
  const dose = scalarToTrimmedString(row.dose);
  const doseUnit = String(row.doseUnitName ?? row.doseUnitCode ?? '').trim();
  if (dose) {
    parts.push(`${dose}${doseUnit}`);
  }
  const method = String(row.methodName ?? row.methodCode ?? '').trim();
  if (method) {
    parts.push(method);
  }
  const rate = String(row.rateName ?? row.rateCode ?? '').trim();
  if (rate) {
    parts.push(rate);
  }
  const days = scalarToTrimmedString(row.dispensePerDuration);
  if (days) {
    parts.push(`${days}天`);
  }
  return parts.join('，') || '—';
}

/** 医嘱行 → 处方明细行 */
function mapAdviceRowToPrescriptionLine(row: DoctorStationAdviceRowDto): DispensePrescriptionLine {
  const amountRaw = row.lineDisplayTotalPrice ?? row.lineTotalAmount;
  const amount =
    amountRaw !== null && amountRaw !== undefined && scalarToTrimmedString(amountRaw) !== ''
      ? String(amountRaw)
      : '—';
  return {
    id: String(row.requestId ?? row.listRowKey ?? '').trim() || `row-${Date.now()}`,
    itemName: String(row.adviceDisplayName ?? row.contentSummary ?? '').trim() || '—',
    quantity: formatAdviceQuantityLabel(row),
    spec: String(row.specificationText ?? '').trim() || '—',
    traceCode: '—',
    batchNo: '—',
    amount,
    pharmacy: '—',
    factory: '—',
    diagnosis:
      String(
        row.conditionDefinitionName ?? row.diagnosisSummary ?? row.boundConditionName ?? '',
      ).trim() || '—',
    freq: formatAdviceFreqLabel(row),
  };
}

/** 医嘱药品行 → 处方块（按 prescriptionNo 分块） */
export function mapAdviceRowsToPrescriptionBlocks(
  rows: readonly DoctorStationAdviceRowDto[],
): DispensePrescriptionBlock[] {
  const groups = new Map<string, DoctorStationAdviceRowDto[]>();
  for (const row of rows) {
    const tcm = isTcmAdviceRow(row);
    const prescriptionNo = String(row.prescriptionNo ?? '').trim();
    const requestId = String(row.requestId ?? '').trim();
    const key = prescriptionNo
      ? `${prescriptionNo}|${tcm ? 't' : 'w'}`
      : `request|${requestId || row.listRowKey || groups.size}`;
    const list = groups.get(key);
    if (list) {
      list.push(row);
    } else {
      groups.set(key, [row]);
    }
  }

  const blocks: DispensePrescriptionBlock[] = [];
  for (const [, groupRows] of groups) {
    groupRows.sort((a, b) =>
      String(a.authoredAt ?? a.signedAt ?? '').localeCompare(
        String(b.authoredAt ?? b.signedAt ?? ''),
      ),
    );
    const head = groupRows[0];
    if (!head) {
      continue;
    }
    const tcm = isTcmAdviceRow(head);
    const rxNo = String(head.prescriptionNo ?? '').trim() || '—';
    const issuedRaw = String(head.authoredAt ?? head.signedAt ?? '').trim();
    const issued = issuedRaw ? formatDateTime(issuedRaw) || issuedRaw : '—';
    blocks.push({
      id: `block-${String(head.requestId ?? head.listRowKey ?? rxNo)}-${rxNo}`,
      rxNo,
      rxType: tcm ? 'herbal' : 'western',
      deptName: '—',
      doctorName: String(head.authoredByName ?? '').trim() || '—',
      issuedAt: issued,
      status: 'completed',
      items: groupRows.map((line) => mapAdviceRowToPrescriptionLine(line)),
    });
  }
  blocks.sort((a, b) => a.issuedAt.localeCompare(b.issuedAt));
  return blocks;
}

/** 医生站患者卡片 → 处方单展示用队列患者模型（与门诊药房一致） */
export function mapDoctorStationPatientToDispenseQueuePatient(
  patient: DoctorStationPatient,
): DispenseQueuePatient {
  const ageRaw = (patient.ageText ?? '').trim();
  const ageLabel = ageRaw.includes('岁') ? ageRaw : ageRaw ? `${ageRaw}岁` : '—';
  const start = patient.startTime ? formatDateTime(patient.startTime) : '';
  return {
    id: patient.encounterId,
    patientName: (patient.patientName ?? '').trim() || '—',
    genderLabel: (patient.genderEnumText ?? '').trim() || '',
    genderEnum: patient.genderEnum ?? undefined,
    ageLabel,
    visitNo: (patient.encounterBusNo ?? '').trim() || (patient.encounterId ?? '').trim() || '—',
    visitTimeLabel: start || '—',
    rxCount: 0,
  };
}

/** 历史 Tab 门诊号：优先就诊 id 展示 */
function resolveHistoryOutpatientNo(
  patientVisitNo: string,
  headRow: DoctorStationAdviceRowDto | undefined,
): string {
  const enc = String(headRow?.encounterId ?? '').trim();
  if (enc) {
    return enc;
  }
  return patientVisitNo.trim() || '—';
}

/** 历史 Tab 下按就诊 id 覆盖门诊号展示 */
function resolveOutpatientNoForBlock(
  patient: DispenseQueuePatient,
  headRow: DoctorStationAdviceRowDto | undefined,
  useHistoryEncounterNo: boolean,
): string {
  if (useHistoryEncounterNo) {
    return resolveHistoryOutpatientNo(patient.visitNo, headRow);
  }
  return patient.visitNo.trim() || '—';
}

/** 处方块 + 患者上下文 → 纸质处方单展示数据 */
export function buildDoctorStationPrescriptionSheetData(
  patient: DispenseQueuePatient,
  block: DispensePrescriptionBlock,
  headRow: DoctorStationAdviceRowDto | undefined,
  options?: { useHistoryEncounterNo?: boolean },
): PrescriptionSheetData {
  const dispensingName = String(headRow?.authoredByName ?? '').trim() || '—';
  const patientForSheet: DispenseQueuePatient = {
    ...patient,
    visitNo: resolveOutpatientNoForBlock(patient, headRow, options?.useHistoryEncounterNo === true),
  };
  return buildPrescriptionSheetData(patientForSheet, block, {
    dispensingName,
    deliverName: dispensingName,
  });
}

/** 按处方块首行 requestId 定位组首行医嘱 */
export function findHeadAdviceRowForBlock(
  block: DispensePrescriptionBlock,
  rows: readonly DoctorStationAdviceRowDto[],
): DoctorStationAdviceRowDto | undefined {
  const headRequestId = String(block.items[0]?.id ?? '').trim();
  if (headRequestId) {
    return rows.find((row) => String(row.requestId ?? '').trim() === headRequestId);
  }
  return rows[0];
}
