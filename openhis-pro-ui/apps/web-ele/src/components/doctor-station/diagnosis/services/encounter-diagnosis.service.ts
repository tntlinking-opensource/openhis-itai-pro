import type { EncounterDiagnosisTabContext, EncounterDiagnosisTabData } from './diagnosis.types';

import type {
  EncounterDiagnosisRowDto,
  SaveEncounterDiagnosisChildRequest,
} from '#/api/doctor-station/diagnosis.types';

import type { DoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import { httpDeleteTcmDiagnosisGroup } from '#/api/doctor-station/tcm.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { mergeEncounterDiagnosisListForDisplay } from './merge-encounter-diagnosis-display';

/** 诊断接口作用域：门诊医生站 / 住院医生站 */
export type DoctorStationDiagnosisScope = 'inpatient' | 'outpatient';

const diagnosisHttpByScope: Record<DoctorStationDiagnosisScope, DoctorStationDiagnosisHttpClient> =
  {
    outpatient: createDoctorStationDiagnosisHttpClient('/doctorStationDiagnosis'),
    inpatient: createDoctorStationDiagnosisHttpClient('/inpatientDoctorStationDiagnosis'),
  };

function resolveDiagnosisHttp(scope: DoctorStationDiagnosisScope = 'outpatient') {
  return diagnosisHttpByScope[scope];
}

/** 是否具备调用诊断列表接口的条件 */
export function isEncounterDiagnosisTabContextReady(ctx: EncounterDiagnosisTabContext): boolean {
  return Boolean(ctx.encounterId?.trim() && ctx.patientId?.trim());
}

/**
 * 拉取当前就诊已保存诊断 + 侧栏四类常用候选。
 * 供页签激活、工具栏刷新、保存/删除后复用；无上下文时返回 null，由调用方清空 UI。
 */
export async function fetchEncounterDiagnosisTabData(
  ctx: EncounterDiagnosisTabContext,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<EncounterDiagnosisTabData | null> {
  if (!isEncounterDiagnosisTabContextReady(ctx)) return null;
  const http = resolveDiagnosisHttp(scope);
  const encounterId = ctx.encounterId.trim();
  const patientId = ctx.patientId.trim();
  const [enc, biz] = await Promise.all([
    http.httpGetEncounterDiagnosis({ encounterId, patientId }),
    http.httpGetConditionDefinitionBusinessClass({ patientId }),
  ]);
  const parsedRows = parsePagedRowsFromApiData<EncounterDiagnosisRowDto>(enc);
  const diagnosisList =
    enc.diagnosisList ?? enc.content ?? (parsedRows.content.length > 0 ? parsedRows.content : []);
  return {
    diagnosisList,
    displayDiagnosisList: mergeEncounterDiagnosisListForDisplay(diagnosisList),
    businessClass: biz,
  };
}

/** 将当前就诊诊断行转为保存接口子项（主诊唯一：无则默认首条） */
function buildDiagnosisChildList(
  rows: EncounterDiagnosisRowDto[],
): SaveEncounterDiagnosisChildRequest[] {
  const mainIdx = rows.findIndex((r) => r.mainDiagnosisFlag === 1);
  const resolvedMain = mainIdx === -1 ? 0 : mainIdx;
  return rows.map((row, i) => {
    const child: SaveEncounterDiagnosisChildRequest = {
      conditionDefinitionId: row.conditionDefinitionId ?? '',
      conditionCode: row.conditionCode ?? '',
      conditionName: row.conditionDefinitionName ?? '',
      verificationStatusEnum: row.verificationStatusEnum ?? undefined,
      mainDiagnosisFlag: i === resolvedMain ? 1 : 0,
      diagnosisSortNo: i + 1,
      medicalTypeCode: row.medicalTypeCode ?? undefined,
      diagnosisDesc: row.diagnosisDesc ?? undefined,
      iptDiseaseTypeCode: row.iptDiseaseTypeCode ?? undefined,
    };
    if (row.conditionId) child.conditionId = row.conditionId;
    if (row.encounterDiagnosisId) child.encounterDiagnosisId = row.encounterDiagnosisId;
    return child;
  });
}

/** 按当前行列表提交就诊诊断保存 */
export async function saveEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
  rows: EncounterDiagnosisRowDto[],
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<void> {
  const http = resolveDiagnosisHttp(scope);
  await http.httpSaveDoctorDiagnosis({
    encounterId,
    patientId,
    diagnosisChildList: buildDiagnosisChildList(rows),
  });
}

/** 删除一条就诊诊断 */
export async function deleteEncounterDiagnosisRow(
  encounterId: string,
  conditionId: string,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<void> {
  const http = resolveDiagnosisHttp(scope);
  await http.httpDelEncounterDiagnosis({ conditionId, encounterId });
}

/** 删除中医病证组（整组病+证） */
export async function deleteTcmDiagnosisGroupRow(
  encounterId: string,
  patientId: string,
  tcmSyndromeGroup: string,
): Promise<void> {
  await httpDeleteTcmDiagnosisGroup({
    encounterId,
    patientId,
    tcmSyndromeGroup,
  });
}
