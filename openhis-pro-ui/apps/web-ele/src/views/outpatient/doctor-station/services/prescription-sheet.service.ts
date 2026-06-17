import type {
  DoctorStationAdviceRowDto,
  PrescriptionScopeTabCode,
} from '#/api/doctor-station/advice.types';
import { mapPrescriptionScopeToAdviceScopeTab } from '#/api/doctor-station/advice.types';

import { fetchDoctorStationAdviceFlatRows } from '../advice/services/history-advice.service';

const PRESCRIPTION_SHEET_PAGE_SIZE = 500;

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

/** 拉取处方单展示用药品医嘱行（pageDoctorStationAdvice） */
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
  return rows.filter((row) => isMedicationAdviceRow(row));
}
