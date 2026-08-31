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
import {
  mapTcmGroupRowToSaveItem,
  resolveTcmDiagnosisHttp,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-groups.service';
import type { TcmDiagnosisGroupItemRequest } from '#/api/doctor-station/tcm.types';

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
  // 慢病备案弱依赖：失败不阻断诊断 Tab；enabled 仍 true，避免把瞬时错误误当成「无 CHS」整支隐藏
  const opspPromise =
    scope === 'outpatient'
      ? http.httpGetPatientOpspFilingDiagnosis({ encounterId, patientId }).catch(() => ({
          opspFilingImportEnabled: true,
          filingList: [],
        }))
      : Promise.resolve({ opspFilingImportEnabled: false, filingList: [] });
  const [enc, biz, tcmGroupsRes, opspFiling] = await Promise.all([
    http.httpGetEncounterDiagnosis({ encounterId, patientId }),
    http.httpGetConditionDefinitionBusinessClass({ patientId }),
    resolveTcmDiagnosisHttp(scope).httpListTcmDiagnosisGroups({ encounterId, patientId }),
    opspPromise,
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
    opspFiling,
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
  options?: { preserveSortNo?: boolean },
): SaveEncounterDiagnosisChildRequest[] {
  const mainIdx = rows.findIndex((r) => r.mainDiagnosisFlag === 1);
  const resolvedMain = mainIdx === -1 ? 0 : mainIdx;
  return rows.map((row, i) => {
    // 默认按下标重排；住院上移/下移需保留展示层写入的 diagnosisSortNo
    const sortNo =
      options?.preserveSortNo && row.diagnosisSortNo != null && row.diagnosisSortNo > 0
        ? row.diagnosisSortNo
        : i + 1;
    const child: SaveEncounterDiagnosisChildRequest = {
      conditionDefinitionId: row.conditionDefinitionId ?? '',
      conditionCode: row.conditionCode ?? '',
      conditionName: row.conditionDefinitionName ?? '',
      verificationStatusEnum: row.verificationStatusEnum ?? undefined,
      mainDiagnosisFlag: i === resolvedMain ? 1 : 0,
      diagnosisSortNo: sortNo,
      medicalTypeCode: row.medicalTypeCode ?? undefined,
      diagnosisDesc: row.diagnosisDesc ?? undefined,
      iptDiseaseTypeCode: row.iptDiseaseTypeCode ?? undefined,
      ybNo: row.ybNo ?? undefined,
    };
    if (row.encounterDiagnosisId) child.encounterDiagnosisId = row.encounterDiagnosisId;
    return child;
  });
}

/** 展示行是否与事实行同一西医诊断（优先就诊诊断主键） */
function isSameWesternDiagnosisRow(
  a: EncounterDiagnosisRowDto,
  b: EncounterDiagnosisRowDto,
): boolean {
  const aEd = String(a.encounterDiagnosisId ?? '').trim();
  const bEd = String(b.encounterDiagnosisId ?? '').trim();
  if (aEd && bEd) {
    return aEd === bEd;
  }
  const aDef = String(a.conditionDefinitionId ?? '').trim();
  const bDef = String(b.conditionDefinitionId ?? '').trim();
  return Boolean(aDef && bDef && aDef === bDef);
}

/** 展示行是否属于同一中医病证组 */
function isSameTcmDisplayGroup(
  displayRow: EncounterDiagnosisRowDto,
  factRow: EncounterDiagnosisRowDto,
): boolean {
  const groupKey = String(displayRow.tcmSyndromeGroup ?? '').trim();
  const factGroup = String(factRow.tcmSyndromeGroup ?? '').trim();
  if (groupKey && factGroup && groupKey === factGroup) {
    return true;
  }
  const diseaseDef = String(displayRow.conditionDefinitionId ?? '').trim();
  const syndromeDef = String(displayRow.syndromeDefinitionId ?? '').trim();
  const factDef = String(factRow.conditionDefinitionId ?? '').trim();
  if (!factDef) {
    return false;
  }
  return factDef === diseaseDef || factDef === syndromeDef;
}

/**
 * 住院：按合并展示列表顺序写回排序号（西医 IF 保存 + 中医病证组 rank）。
 * 西医全量对齐不删中医行；中医走病证组全量提交更新 rank。
 */
export async function saveInpatientEncounterDiagnosisReorder(
  encounterId: string,
  patientId: string,
  orderedDisplayRows: EncounterDiagnosisRowDto[],
  factRows: EncounterDiagnosisRowDto[],
): Promise<void> {
  // 展示序 → 排序号（从 1 起）
  const orderedWithSort = orderedDisplayRows.map((row, index) => ({
    ...row,
    diagnosisSortNo: index + 1,
  }));

  // 事实行挂上对应展示排序号，供西医保存与中医组成员同步
  const nextFactRows = factRows.map((fact) => {
    const matchedDisplay = isTcmDiagnosisRow(fact)
      ? orderedWithSort.find((display) => isSameTcmDisplayGroup(display, fact))
      : orderedWithSort.find(
          (display) => !isTcmDiagnosisRow(display) && isSameWesternDiagnosisRow(display, fact),
        );
    if (!matchedDisplay) {
      return fact;
    }
    return {
      ...fact,
      diagnosisSortNo: matchedDisplay.diagnosisSortNo,
    };
  });

  const westernRows = filterWesternEncounterDiagnosisRowsForSave(nextFactRows);
  const http = resolveDiagnosisHttp('inpatient');
  if (westernRows.length > 0) {
    await http.httpSaveDoctorDiagnosis({
      encounterId,
      patientId,
      diagnosisChildList: buildDiagnosisChildList(westernRows, { preserveSortNo: true }),
    });
  }

  // 有中医展示行时，按展示序回写病证组 rank
  const tcmDisplayRows = orderedWithSort.filter((row) => isTcmDiagnosisRow(row));
  if (tcmDisplayRows.length === 0) {
    return;
  }
  const tcmHttp = resolveTcmDiagnosisHttp('inpatient');
  const listRes = await tcmHttp.httpListTcmDiagnosisGroups({ encounterId, patientId });
  const existingGroups = listRes.groups ?? [];
  if (existingGroups.length === 0) {
    return;
  }

  // 已有组转保存子项，按展示序覆盖 rank
  const groups = existingGroups
    .map((group) => {
      const groupKey = String(group.tcmSyndromeGroup ?? '').trim();
      const matched = tcmDisplayRows.find((display) => {
        const displayKey = String(display.tcmSyndromeGroup ?? '').trim();
        if (groupKey && displayKey && groupKey === displayKey) {
          return true;
        }
        return (
          String(display.conditionDefinitionId ?? '').trim() ===
            String(group.diseaseDefinitionId ?? '').trim() &&
          String(display.syndromeDefinitionId ?? '').trim() ===
            String(group.syndromeDefinitionId ?? '').trim()
        );
      });
      const rank = matched?.diagnosisSortNo;
      if (rank == null || rank <= 0) {
        return mapTcmGroupRowToSaveItem(group);
      }
      return mapTcmGroupRowToSaveItem({ ...group, rank });
    })
    .filter((item): item is TcmDiagnosisGroupItemRequest => item !== null);

  if (groups.length === 0) {
    return;
  }
  await tcmHttp.httpSaveTcmDiagnosisGroupsBatch({
    encounterId,
    patientId,
    groups,
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

/**
 * 门诊：同步诊断类型（med_type / 普通门诊、门诊慢病等）。
 * 西医走就诊诊断保存；中医病证组走专管接口，只改医疗类别。
 */
export async function saveOutpatientEncounterDiagnosisMedicalTypeRows(
  encounterId: string,
  patientId: string,
  rows: EncounterDiagnosisRowDto[],
): Promise<void> {
  const westernRows = filterWesternEncounterDiagnosisRowsForSave(rows);
  const http = resolveDiagnosisHttp('outpatient');
  if (westernRows.length > 0) {
    await http.httpSaveDoctorDiagnosis({
      encounterId,
      patientId,
      diagnosisChildList: buildDiagnosisChildList(westernRows, { preserveSortNo: true }),
    });
  }

  const tcmRows = rows.filter((row) => isTcmDiagnosisRow(row));
  if (tcmRows.length === 0) {
    return;
  }
  const tcmHttp = resolveTcmDiagnosisHttp('outpatient');
  const listRes = await tcmHttp.httpListTcmDiagnosisGroups({ encounterId, patientId });
  const existingGroups = listRes.groups ?? [];
  if (existingGroups.length === 0) {
    return;
  }

  // 已有组转保存子项，按展示行回写 medicalTypeCode
  const groups = existingGroups
    .map((group) => {
      const groupKey = String(group.tcmSyndromeGroup ?? '').trim();
      const matched = tcmRows.find((row) => {
        const displayKey = String(row.tcmSyndromeGroup ?? '').trim();
        if (groupKey && displayKey && groupKey === displayKey) {
          return true;
        }
        return (
          String(row.conditionDefinitionId ?? '').trim() ===
            String(group.diseaseDefinitionId ?? '').trim() &&
          String(row.syndromeDefinitionId ?? '').trim() ===
            String(group.syndromeDefinitionId ?? '').trim()
        );
      });
      if (!matched) {
        return mapTcmGroupRowToSaveItem(group);
      }
      return mapTcmGroupRowToSaveItem({
        ...group,
        medicalTypeCode: matched.medicalTypeCode ?? group.medicalTypeCode,
      });
    })
    .filter((item): item is TcmDiagnosisGroupItemRequest => item !== null);

  if (groups.length === 0) {
    return;
  }
  await tcmHttp.httpSaveTcmDiagnosisGroupsBatch({
    encounterId,
    patientId,
    groups,
  });
}

/** 删除一条就诊诊断 */
export async function deleteEncounterDiagnosisRow(
  encounterId: string,
  encounterDiagnosisId: string,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<void> {
  const http = resolveDiagnosisHttp(scope);
  await http.httpDelEncounterDiagnosis({ encounterDiagnosisId, encounterId });
}

/** 切换就诊主诊断（后端清空其它主诊，保证同就诊唯一） */
export async function switchMainEncounterDiagnosisRow(
  encounterId: string,
  encounterDiagnosisId: string,
  scope: DoctorStationDiagnosisScope = 'outpatient',
  patientId?: string,
): Promise<void> {
  const http = resolveDiagnosisHttp(scope);
  await http.httpSwitchMainEncounterDiagnosis({
    encounterDiagnosisId,
    encounterId,
    ...(patientId ? { patientId } : {}),
  });
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
