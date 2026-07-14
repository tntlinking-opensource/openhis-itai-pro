import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';

import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { httpListTcmDiagnosisGroups } from '#/api/doctor-station/tcm.http';
import { httpGetInpatientEncounterDiagnosis } from '#/api/inpatient/doctor-station/inpatient-doctor-station-diagnosis.http';
import { httpListTcmDiagnosisGroups as httpListInpatientTcmDiagnosisGroups } from '#/api/inpatient/doctor-station/inpatient-doctor-station-tcm.http';

import type { DoctorStationDiagnosisScope } from './encounter-diagnosis.service';
import { pickMainTcmDiagnosisDisplayName } from './encounter-diagnosis-select';

export interface EncounterTcmDiagnosisSnapshot {
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  tcmDiagnosisGroups: TcmDiagnosisGroupRowDto[];
  displayText: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 拉取就诊中医主诊断展示文案（病证合并，与诊断页一致） */
export async function fetchEncounterTcmDiagnosisSnapshot(
  encounterId: string,
  patientId: string,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<EncounterTcmDiagnosisSnapshot> {
  const encId = str(encounterId);
  const patId = str(patientId);
  if (!encId || !patId) {
    return { encounterDiagnosisRows: [], tcmDiagnosisGroups: [], displayText: '' };
  }
  const getEncounterDiagnosis =
    scope === 'inpatient' ? httpGetInpatientEncounterDiagnosis : httpGetEncounterDiagnosis;
  const listTcmDiagnosisGroups =
    scope === 'inpatient' ? httpListInpatientTcmDiagnosisGroups : httpListTcmDiagnosisGroups;
  const [encRes, tcmGroupsRes] = await Promise.all([
    getEncounterDiagnosis({ encounterId: encId, patientId: patId }),
    listTcmDiagnosisGroups({ encounterId: encId, patientId: patId }),
  ]);
  const encounterDiagnosisRows = encRes.diagnosisList ?? encRes.content ?? [];
  const tcmDiagnosisGroups = tcmGroupsRes.groups ?? [];
  const displayText = pickMainTcmDiagnosisDisplayName(encounterDiagnosisRows, tcmDiagnosisGroups);
  return { encounterDiagnosisRows, tcmDiagnosisGroups, displayText };
}

/** 仅取中医主诊断展示文案 */
export async function fetchEncounterTcmDiagnosisDisplayText(
  encounterId: string,
  patientId: string,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<string> {
  const snapshot = await fetchEncounterTcmDiagnosisSnapshot(encounterId, patientId, scope);
  return snapshot.displayText;
}
