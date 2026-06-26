import type {
  TcmDiagnosisGroupItemRequest,
  TcmDiagnosisGroupRowDto,
} from '#/api/doctor-station/tcm.types';

import type { DoctorStationTcmHttpClient } from '#/api/doctor-station/tcm-api-client';
import {
  createDoctorStationTcmHttpClient,
  createInpatientDoctorStationTcmHttpClient,
} from '#/api/doctor-station/tcm-api-client';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import type { DoctorStationDiagnosisScope } from './encounter-diagnosis.service';
import { formatTcmDiagnosisDisplayName, type TcmDiagnosisPick } from './tcm-diagnosis-display';

const tcmHttpByScope: Record<DoctorStationDiagnosisScope, DoctorStationTcmHttpClient> = {
  outpatient: createDoctorStationTcmHttpClient('/doctorStationTcm'),
  inpatient: createInpatientDoctorStationTcmHttpClient(),
};

/** 按门诊/住院作用域解析中医 Tab HTTP 客户端 */
export function resolveTcmDiagnosisHttp(scope: DoctorStationDiagnosisScope = 'outpatient') {
  return tcmHttpByScope[scope];
}

/** 病证组是否同一对病名+证候目录 */
export function isSameTcmDiagnosisGroupPair(
  row: Pick<TcmDiagnosisGroupRowDto, 'diseaseDefinitionId' | 'syndromeDefinitionId'>,
  diseaseDefinitionId: string,
  syndromeDefinitionId: string,
): boolean {
  return (
    String(row.diseaseDefinitionId ?? '').trim() === diseaseDefinitionId &&
    String(row.syndromeDefinitionId ?? '').trim() === syndromeDefinitionId
  );
}

/** 已保存病证组行 → IF-DOR-32 保存子项 */
export function mapTcmGroupRowToSaveItem(
  row: TcmDiagnosisGroupRowDto,
): TcmDiagnosisGroupItemRequest | null {
  const diseaseDefinitionId = preserveApiIdForRequest(row.diseaseDefinitionId);
  const syndromeDefinitionId = preserveApiIdForRequest(row.syndromeDefinitionId);
  if (!diseaseDefinitionId || !syndromeDefinitionId) return null;
  return {
    tcmSyndromeGroup: preserveApiIdForRequest(row.tcmSyndromeGroup) || undefined,
    diseaseDefinitionId,
    diseaseConditionId: preserveApiIdForRequest(row.diseaseConditionId) || undefined,
    syndromeDefinitionId,
    syndromeConditionId: preserveApiIdForRequest(row.syndromeConditionId) || undefined,
    primaryFlag: row.primaryFlag === 1 ? 1 : 0,
    rank: Number(row.rank ?? 0) > 0 ? Number(row.rank) : 1,
    medicalTypeCode: row.medicalTypeCode ?? undefined,
    diagnosisDesc: row.diagnosisDesc ?? undefined,
  };
}

/** 新建病证组保存子项（组号由后端分配） */
export function buildNewTcmGroupSaveItem(
  disease: TcmDiagnosisPick,
  syndrome: TcmDiagnosisPick,
  options: { primaryFlag: number; rank: number },
): TcmDiagnosisGroupItemRequest {
  return {
    diseaseDefinitionId: preserveApiIdForRequest(disease.conditionDefinitionId) ?? '',
    syndromeDefinitionId: preserveApiIdForRequest(syndrome.conditionDefinitionId) ?? '',
    primaryFlag: options.primaryFlag,
    rank: options.rank,
    diagnosisDesc: formatTcmDiagnosisDisplayName(disease.conditionName, syndrome.conditionName),
  };
}

/**
 * 在已有病证组基础上追加一组（全量提交 IF-DOR-32）。
 * 后端会为病、证各写一条事实，并用 {@code tcmSyndromeGroup} 关联。
 */
export async function appendTcmDiagnosisGroupBatch(
  encounterId: string,
  patientId: string,
  disease: TcmDiagnosisPick,
  syndrome: TcmDiagnosisPick,
  saveAsMainDiagnosis: boolean,
  scope: DoctorStationDiagnosisScope = 'outpatient',
): Promise<'duplicate' | 'saved'> {
  const tcmHttp = resolveTcmDiagnosisHttp(scope);
  const diseaseDefinitionId = preserveApiIdForRequest(disease.conditionDefinitionId) ?? '';
  const syndromeDefinitionId = preserveApiIdForRequest(syndrome.conditionDefinitionId) ?? '';
  if (!diseaseDefinitionId || !syndromeDefinitionId) {
    throw new Error('中医病证目录未选择完整');
  }

  const listRes = await tcmHttp.httpListTcmDiagnosisGroups({ encounterId, patientId });
  const existingRows = listRes.groups ?? [];

  if (
    existingRows.some((row) =>
      isSameTcmDiagnosisGroupPair(row, diseaseDefinitionId, syndromeDefinitionId),
    )
  ) {
    return 'duplicate';
  }

  // 已有组转保存子项，保留组号与事实 id 供全量 upsert
  let groups = existingRows
    .map(mapTcmGroupRowToSaveItem)
    .filter((item): item is TcmDiagnosisGroupItemRequest => item !== null);

  if (saveAsMainDiagnosis) {
    groups = groups.map((item) => ({ ...item, primaryFlag: 0 }));
  }

  const maxRank = groups.reduce((max, item) => Math.max(max, item.rank), 0);
  groups.push(
    buildNewTcmGroupSaveItem(disease, syndrome, {
      rank: maxRank + 1,
      primaryFlag: saveAsMainDiagnosis ? 1 : 0,
    }),
  );

  await tcmHttp.httpSaveTcmDiagnosisGroupsBatch({
    encounterId,
    patientId,
    groups,
  });
  return 'saved';
}
