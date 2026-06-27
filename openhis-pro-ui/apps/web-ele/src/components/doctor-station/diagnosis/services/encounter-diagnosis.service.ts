import type { EncounterDiagnosisTabContext, EncounterDiagnosisTabData } from './diagnosis.types';

import type {
  EncounterDiagnosisRowDto,
  SaveEncounterDiagnosisChildRequest,
} from '#/api/doctor-station/diagnosis.types';

import type { DoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import {
  buildEncounterDiagnosisDisplayList,
  isTcmDiagnosisRow,
} from './merge-encounter-diagnosis-display';
import { resolveTcmDiagnosisHttp } from './tcm-diagnosis-groups.service';

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
  const [enc, biz, tcmGroupsRes] = await Promise.all([
    http.httpGetEncounterDiagnosis({ encounterId, patientId }),
    http.httpGetConditionDefinitionBusinessClass({ patientId }),
    resolveTcmDiagnosisHttp(scope).httpListTcmDiagnosisGroups({ encounterId, patientId }),
  ]);
  const parsedRows = parsePagedRowsFromApiData<EncounterDiagnosisRowDto>(enc);
  const diagnosisList =
    enc.diagnosisList ?? enc.content ?? (parsedRows.content.length > 0 ? parsedRows.content : []);
  return {
    diagnosisList,
    displayDiagnosisList: buildEncounterDiagnosisDisplayList(
      diagnosisList,
      tcmGroupsRes.groups ?? [],
    ),
    businessClass: biz,
  };
}

/**
 * 拉取就诊诊断合并展示行（西医原样 + 中医病证组合并），供医嘱/申请单诊断下拉复用。
 */
export async function fetchEncounterDiagnosisDisplayList(
  ctx: EncounterDiagnosisTabContext,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<EncounterDiagnosisRowDto[]> {
  if (!isEncounterDiagnosisTabContextReady(ctx)) return [];
  const http = resolveDiagnosisHttp(scope);
  const encounterId = ctx.encounterId.trim();
  const patientId = ctx.patientId.trim();
  const [enc, tcmGroupsRes] = await Promise.all([
    http.httpGetEncounterDiagnosis({ encounterId, patientId }),
    resolveTcmDiagnosisHttp(scope).httpListTcmDiagnosisGroups({ encounterId, patientId }),
  ]);
  const parsedRows = parsePagedRowsFromApiData<EncounterDiagnosisRowDto>(enc);
  const diagnosisList =
    enc.diagnosisList ?? enc.content ?? (parsedRows.content.length > 0 ? parsedRows.content : []);
  return buildEncounterDiagnosisDisplayList(diagnosisList, tcmGroupsRes.groups ?? []);
}

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

/** 西医 IF-DOR-08 保存批次：剔除中医行（病证组由 IF-DOR-32 专管，避免全量重存拆组）。 */
export function filterWesternEncounterDiagnosisRowsForSave(
  rows: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto[] {
  return rows.filter((row) => !isTcmDiagnosisRow(row));
}

/** 按当前西医行列表提交就诊诊断保存（中医病证组不在此接口重存）。 */
export async function saveEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
  rows: EncounterDiagnosisRowDto[],
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<void> {
  const westernRows = filterWesternEncounterDiagnosisRowsForSave(rows);
  const http = resolveDiagnosisHttp(scope);
  await http.httpSaveDoctorDiagnosis({
    encounterId,
    patientId,
    diagnosisChildList: buildDiagnosisChildList(westernRows),
  });
}

/**
 * 住院：同步诊断类型（ipt_dise_type_code）。
 * 西医行全量对齐；中医行仅更新诊断类型，病证组结构由领域层保留。
 */
export async function saveInpatientEncounterDiagnosisIptTypeRows(
  encounterId: string,
  patientId: string,
  rows: EncounterDiagnosisRowDto[],
): Promise<void> {
  const westernRows = filterWesternEncounterDiagnosisRowsForSave(rows);
  const tcmRows = rows.filter((row) => isTcmDiagnosisRow(row));
  const http = resolveDiagnosisHttp('inpatient');
  await http.httpSaveDoctorDiagnosis({
    encounterId,
    patientId,
    diagnosisChildList: buildDiagnosisChildList([...westernRows, ...tcmRows]),
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
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<void> {
  const tcmHttp = resolveTcmDiagnosisHttp(scope);
  await tcmHttp.httpDeleteTcmDiagnosisGroup({
    encounterId,
    patientId,
    tcmSyndromeGroup,
  });
}
