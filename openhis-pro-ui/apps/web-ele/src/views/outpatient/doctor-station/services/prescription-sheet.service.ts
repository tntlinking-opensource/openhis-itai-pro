import type {
  DoctorStationAdviceRowDto,
  PrescriptionScopeTabCode,
} from '#/api/doctor-station/advice.types';

import { mapPrescriptionScopeToAdviceScopeTab } from '#/api/doctor-station/advice.types';

import { fetchDoctorStationAdviceFlatRows } from '../advice/services/history-advice.service';

const PRESCRIPTION_SHEET_PAGE_SIZE = 500;

/** 已签发请求态（与后端 pageOutpatientSignedPrescriptionDrugLines 一致） */
const SIGNED_PRESCRIPTION_STATUS_CODES = new Set(['ACTIVE', 'COMPLETED']);

/** 是否药品医嘱行 */
function isMedicationAdviceRow(row: DoctorStationAdviceRowDto): boolean {
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  if (rt === 'MEDICATION' || rt === 'MED') {
    return true;
  }
  const tbl = String(row.productTableName ?? '').toLowerCase();
  return tbl.includes('medication');
}

/**
 * 是否可进入处方单的已签发药品行。
 * 须 prescriptionNo 非空且 statusEnum∈{ACTIVE,COMPLETED}；排除仅保存的 DRAFT。
 */
export function isSignedPrescriptionMedicationRow(row: DoctorStationAdviceRowDto): boolean {
  const prescriptionNo = String(row.prescriptionNo ?? '').trim();
  if (!prescriptionNo) {
    return false;
  }
  const status = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  return SIGNED_PRESCRIPTION_STATUS_CODES.has(status);
}

/** 拉取处方单展示用已签发药品医嘱行（pageDoctorStationAdvice） */
export async function fetchDoctorStationPrescriptionAdviceRows(params: {
  encounterId: string;
  patientId: string;
  prescriptionScopeTabCode: PrescriptionScopeTabCode;
}): Promise<DoctorStationAdviceRowDto[]> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    return [];
  }
  const rows = await fetchDoctorStationAdviceFlatRows(
    patientId,
    encounterId,
    mapPrescriptionScopeToAdviceScopeTab(params.prescriptionScopeTabCode),
    PRESCRIPTION_SHEET_PAGE_SIZE,
  );
  // 仅药品，且排除未签发/仅保存草稿
  return rows.filter((row) => isMedicationAdviceRow(row) && isSignedPrescriptionMedicationRow(row));
}
