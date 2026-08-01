import type { DoctorStationPatient } from '#/views/outpatient/doctor-station/doctorPatientStore';
import type { PrescriptionSheetData } from '#/views/outpatient/dispense/components/prescription-sheet.types';
import type { PrescriptionScopeTabCode } from '#/api/doctor-station/advice.types';

import { fetchEncounterTcmDiagnosisSnapshot } from '#/components/doctor-station/diagnosis/services/fetch-encounter-tcm-diagnosis-text';
import { pickMainTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildDoctorStationPrescriptionSheetData,
  findHeadAdviceRowForBlock,
  mapAdviceRowsToPrescriptionBlocks,
  mapDoctorStationPatientToDispenseQueuePatient,
} from '#/views/outpatient/doctor-station/services/map-doctor-station-prescription-sheet';
import { fetchDoctorStationPrescriptionAdviceRows } from '#/views/outpatient/doctor-station/services/prescription-sheet.service';

import { PRINT_SCENARIO } from './constants/print-scenario';
import {
  buildTcmPrintDataFromDoctorSheet,
  buildWesternPrintDataFromDoctorSheet,
  isDoctorSheetHerbal,
} from './mappers/doctor-station-prescription-sheet';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { print, type PrintResult } from './runtime';

export interface DoctorStationPrescriptionSheetPrintItem {
  data: PrescriptionSheetData;
  /** 历史 Tab 跨就诊时用于中药诊断拉取 */
  encounterId?: string;
}

export interface PrintDoctorStationPrescriptionOptions {
  encounterId: string;
  patientId: string;
  /** 已有笺面数据时直接打印（不依赖发药 slip / 收费） */
  sheetItems?: DoctorStationPrescriptionSheetPrintItem[];
  /** 无 sheetItems 时用于组装笺面 */
  patient?: DoctorStationPatient | null;
  /** 无 sheetItems 时拉取医嘱范围；默认本次就诊 */
  prescriptionScopeTabCode?: PrescriptionScopeTabCode;
  jobName?: string;
}

export interface PrintDoctorStationPrescriptionResult {
  ok: boolean;
  count: number;
}

/** 给笺面补机构名（医生站构建默认可能为「医疗机构」） */
function withPrintHospitalName(sheet: PrescriptionSheetData): PrescriptionSheetData {
  const current = String(sheet.hospitalName ?? '').trim();
  if (current && current !== '医疗机构' && current !== '—') {
    return sheet;
  }
  return {
    ...sheet,
    hospitalName: resolvePrintHospitalName() || current || '',
  };
}

/** 中药方头诊断：笺面已有则用，否则按就诊拉诊断快照 */
async function resolveTcmConditionSummary(
  sheet: PrescriptionSheetData,
  encounterId: string,
  patientId: string,
): Promise<string> {
  const fromSheet = String(sheet.diagnosis ?? '').trim();
  if (fromSheet && fromSheet !== '—') {
    return fromSheet;
  }
  if (!encounterId || !patientId) {
    return '';
  }
  const snapshot = await fetchEncounterTcmDiagnosisSnapshot(encounterId, patientId);
  return pickMainTcmDiagnosisDisplayName(
    snapshot.encounterDiagnosisRows,
    snapshot.tcmDiagnosisGroups,
  );
}

/** 单张笺面 → 中西药场景静默打印 */
async function printOneDoctorSheet(options: {
  sheet: PrescriptionSheetData;
  encounterId: string;
  patientId: string;
  jobName: string;
}): Promise<PrintResult> {
  const sheet = withPrintHospitalName(options.sheet);
  const prescriptionNo = String(sheet.prescriptionNo ?? '').trim();
  const bizRef = {
    encounterId: options.encounterId || undefined,
    prescriptionNo: prescriptionNo || undefined,
  };

  if (isDoctorSheetHerbal(sheet)) {
    const conditionSummaryText = await resolveTcmConditionSummary(
      sheet,
      options.encounterId,
      options.patientId,
    );
    return print({
      scenarioCode: PRINT_SCENARIO.outpatientTcmRx,
      printData: buildTcmPrintDataFromDoctorSheet(sheet, conditionSummaryText),
      bizRef,
      jobName: options.jobName,
    });
  }

  return print({
    scenarioCode: PRINT_SCENARIO.outpatientRx,
    printData: buildWesternPrintDataFromDoctorSheet(sheet),
    bizRef,
    jobName: options.jobName,
  });
}

/**
 * 无现成笺面时：拉已签发药品医嘱并组装与弹窗同源的处方笺数据。
 */
async function buildSheetItemsFromAdvice(options: {
  encounterId: string;
  patientId: string;
  patient: DoctorStationPatient;
  prescriptionScopeTabCode: PrescriptionScopeTabCode;
}): Promise<DoctorStationPrescriptionSheetPrintItem[]> {
  const rows = await fetchDoctorStationPrescriptionAdviceRows({
    encounterId: options.encounterId,
    patientId: options.patientId,
    prescriptionScopeTabCode: options.prescriptionScopeTabCode,
  });
  if (rows.length === 0) {
    return [];
  }
  const dispensePatient = mapDoctorStationPatientToDispenseQueuePatient(options.patient);
  const isHistory = options.prescriptionScopeTabCode === 'HISTORY';
  const blocks = mapAdviceRowsToPrescriptionBlocks(rows);
  return blocks.map((block) => {
    const headRow = findHeadAdviceRowForBlock(block, rows);
    const data = buildDoctorStationPrescriptionSheetData(dispensePatient, block, headRow, {
      useHistoryEncounterNo: isHistory,
    });
    return {
      data: {
        ...withPrintHospitalName(data),
        hospitalName: resolvePrintHospitalName() || data.hospitalName,
      },
      encounterId: preserveApiIdForRequest(headRow?.encounterId) || options.encounterId,
    };
  });
}

/**
 * 门诊医生站打印处方：直接用处方单笺面数据对接 op-rx-print / op-tcm-rx-print。
 * 已签发即可打，不依赖发药 slip、不依赖是否已收费。
 */
export async function printDoctorStationPrescriptions(
  options: PrintDoctorStationPrescriptionOptions,
): Promise<PrintDoctorStationPrescriptionResult> {
  const encounterId = preserveApiIdForRequest(options.encounterId);
  const patientId = preserveApiIdForRequest(options.patientId);
  if (!encounterId || !patientId) {
    return { ok: false, count: 0 };
  }

  const jobName = options.jobName ?? '门诊医生站处方';
  let sheetItems = options.sheetItems ?? [];

  if (sheetItems.length === 0) {
    if (!options.patient) {
      return { ok: false, count: 0 };
    }
    sheetItems = await buildSheetItemsFromAdvice({
      encounterId,
      patientId,
      patient: options.patient,
      prescriptionScopeTabCode: options.prescriptionScopeTabCode ?? 'CURRENT_ENCOUNTER',
    });
  }

  if (sheetItems.length === 0) {
    return { ok: false, count: 0 };
  }

  let printedCount = 0;
  for (const item of sheetItems) {
    const sheetEncounterId = preserveApiIdForRequest(item.encounterId) || encounterId;
    const result = await printOneDoctorSheet({
      sheet: item.data,
      encounterId: sheetEncounterId,
      patientId,
      jobName,
    });
    if (result.ok) {
      printedCount += 1;
    }
  }

  return { ok: printedCount > 0, count: printedCount };
}
