import type {
  CommonDiagnosisRowDto,
  CommonDiagnosesResponse,
  ConditionDefinitionPageResponse,
  ConditionDefinitionRowResponse,
  DeleteEncounterDiagnosisRequest,
  DeleteEncounterDiagnosisResponse,
  EncounterDiagnosisResponse,
  EncounterDiagnosisRowDto,
  GetConditionDefinitionBusinessClassRequest,
  GetConditionDefinitionMetadataRequest,
  GetEncounterDiagnosisRequest,
  SaveEncounterDiagnosisRequest,
  SaveEncounterDiagnosisResponse,
} from './diagnosis.types';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** 将读模型行映射为前端 DTO */
function mapConditionDefinitionMetadataApiRow(
  raw: Record<string, unknown>,
): ConditionDefinitionRowResponse {
  const defId = raw.conditionDefinitionId ?? raw.definitionId;
  const name = raw.conditionDefinitionName ?? raw.conditionName;
  const kind = raw.diagnosisKind ?? raw.medicineType;
  const optStr = (v: unknown): string | undefined => {
    if (v === null || v === undefined) return undefined;
    const s = String(v).trim();
    return s === '' ? undefined : s;
  };
  const optNum = (v: unknown): number | undefined => {
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (v === null || v === undefined || v === '') return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  };
  return {
    conditionDefinitionId: defId !== undefined && defId !== null ? String(defId) : undefined,
    conditionCode: optStr(raw.conditionCode),
    conditionName: name !== undefined && name !== null ? String(name) : undefined,
    diagnosisKindText: optStr(raw.diagnosisKindText),
    pyStr: optStr(raw.pyStr),
    wbStr: optStr(raw.wbStr),
    medicineType:
      kind !== undefined && kind !== null && String(kind).trim() !== '' ? String(kind) : undefined,
    sourceEnum: optNum(raw.sourceEnum),
    sourceEnumText: optStr(raw.sourceEnumText),
    ybFlag: optNum(raw.ybFlag),
    ybFlagText: optStr(raw.ybFlagText),
    statusEnum: optNum(raw.statusEnum),
  };
}

/** getEncounterDiagnosis：读模型可能返回 conditionDefinitionName，旧版为 conditionName */
function mapEncounterDiagnosisApiRow(raw: Record<string, unknown>): EncounterDiagnosisRowDto {
  const optStr = (v: unknown): string | undefined => {
    if (v === null || v === undefined) return undefined;
    const s = String(v).trim();
    return s === '' ? undefined : s;
  };
  const optNum = (v: unknown): number | undefined => {
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (v === null || v === undefined || v === '') return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  };
  const nameRaw = raw.conditionDefinitionName ?? raw.conditionName;
  return {
    conditionId: optStr(raw.conditionId),
    encounterDiagnosisId: optStr(raw.encounterDiagnosisId),
    conditionDefinitionId: optStr(raw.conditionDefinitionId ?? raw.definitionId),
    conditionCode: optStr(raw.conditionCode),
    conditionDefinitionName:
      nameRaw !== undefined && nameRaw !== null && String(nameRaw).trim() !== ''
        ? String(nameRaw).trim()
        : undefined,
    diagnosisKindText: optStr(raw.diagnosisKindText),
    medicineType: optStr(raw.medicineType),
    verificationStatusEnum: optNum(raw.verificationStatusEnum),
    verificationStatusEnumText: optStr(raw.verificationStatusEnumText),
    mainDiagnosisFlag: optNum(raw.mainDiagnosisFlag),
    diagnosisSortNo: optNum(raw.diagnosisSortNo),
    medicalTypeCode: optStr(raw.medicalTypeCode),
    diagnosisDesc: optStr(raw.diagnosisDesc),
    iptDiseaseTypeCode: optStr(raw.iptDiseaseTypeCode),
    iptDiseaseTypeText: optStr(raw.iptDiseaseTypeText),
  };
}

function mapCommonDiagnosisApiRow(raw: Record<string, unknown>): CommonDiagnosisRowDto {
  const optStr = (v: unknown): string | undefined => {
    if (v === null || v === undefined) return undefined;
    const s = String(v).trim();
    return s === '' ? undefined : s;
  };
  const optNum = (v: unknown): number | undefined => {
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (v === null || v === undefined || v === '') return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  };
  return {
    conditionDefinitionId: optStr(raw.conditionDefinitionId ?? raw.definitionId),
    conditionCode: optStr(raw.conditionCode),
    conditionName: optStr(raw.conditionName),
    medicineType: optStr(raw.medicineType),
    sourceEnum: optNum(raw.sourceEnum),
    sourceEnumText: optStr(raw.sourceEnumText),
  };
}

function mapCommonDiagnosesResponsePayload(payload: unknown): CommonDiagnosesResponse {
  if (!isRecord(payload)) return payload as CommonDiagnosesResponse;
  const mapList = (list: unknown): CommonDiagnosisRowDto[] | null | undefined => {
    if (!Array.isArray(list)) return list as null | undefined;
    return list.filter(isRecord).map((row) => mapCommonDiagnosisApiRow(row));
  };
  return {
    ...payload,
    patientHistoryList: mapList(payload.patientHistoryList) ?? payload.patientHistoryList,
    doctorCommonUseList: mapList(payload.doctorCommonUseList) ?? payload.doctorCommonUseList,
    userPersonalList: mapList(payload.userPersonalList) ?? payload.userPersonalList,
    organizationList: mapList(payload.organizationList) ?? payload.organizationList,
  } as CommonDiagnosesResponse;
}

function mapEncounterDiagnosisResponsePayload(payload: unknown): EncounterDiagnosisResponse {
  if (!isRecord(payload)) return payload as EncounterDiagnosisResponse;
  const mapList = (list: unknown): EncounterDiagnosisRowDto[] | null | undefined => {
    if (!Array.isArray(list)) return list as null | undefined;
    return list.filter(isRecord).map((row) => mapEncounterDiagnosisApiRow(row));
  };
  return {
    ...payload,
    diagnosisList: mapList(payload.diagnosisList) ?? payload.diagnosisList,
    content: mapList(payload.content) ?? payload.content,
  } as EncounterDiagnosisResponse;
}

/** 网关/应用在标准 data 下再包一层 content 指向业务体时剥开 */
function unwrapDiagnosisPayload<T>(raw: unknown): T {
  if (!isRecord(raw)) return raw as T;
  const c = raw.content;
  if (!isRecord(c)) return raw as T;
  if (Array.isArray(c.content) || Array.isArray(c.records) || Array.isArray(c.rows)) {
    return raw as T;
  }
  if (
    'diagnosisList' in c ||
    'patientHistoryList' in c ||
    'doctorCommonUseList' in c ||
    'userPersonalList' in c ||
    'organizationList' in c
  ) {
    return c as T;
  }
  return raw as T;
}

export interface DoctorStationDiagnosisHttpClient {
  httpDelEncounterDiagnosis: (
    body: DeleteEncounterDiagnosisRequest,
  ) => Promise<DeleteEncounterDiagnosisResponse>;
  httpGetConditionDefinitionBusinessClass: (
    body: GetConditionDefinitionBusinessClassRequest,
  ) => Promise<CommonDiagnosesResponse>;
  httpGetConditionDefinitionMetadata: (
    body: GetConditionDefinitionMetadataRequest,
  ) => Promise<ConditionDefinitionPageResponse>;
  httpGetEncounterDiagnosis: (
    body: GetEncounterDiagnosisRequest,
  ) => Promise<EncounterDiagnosisResponse>;
  httpSaveDoctorDiagnosis: (
    body: SaveEncounterDiagnosisRequest,
  ) => Promise<SaveEncounterDiagnosisResponse>;
}

/** 按应用服务前缀生成诊断五接口客户端（门诊/住院方法名一致，仅路径前缀不同） */
export function createDoctorStationDiagnosisHttpClient(
  basePath: string,
): DoctorStationDiagnosisHttpClient {
  const prefix = basePath.replace(/\/$/, '');

  return {
    async httpGetEncounterDiagnosis(body) {
      const raw = await publicRequestClient.post<unknown>(`${prefix}/getEncounterDiagnosis`, body);
      const unwrapped = unwrapDiagnosisPayload<EncounterDiagnosisResponse>(raw);
      return mapEncounterDiagnosisResponsePayload(unwrapped);
    },

    async httpGetConditionDefinitionBusinessClass(body) {
      const raw = await publicRequestClient.post<unknown>(
        `${prefix}/getConditionDefinitionBusinessClass`,
        body,
      );
      const unwrapped = unwrapDiagnosisPayload<CommonDiagnosesResponse>(raw);
      return mapCommonDiagnosesResponsePayload(unwrapped);
    },

    async httpGetConditionDefinitionMetadata(body) {
      const raw = await publicRequestClient.post<unknown>(
        `${prefix}/getConditionDefinitionMetadata`,
        body,
      );
      const unwrapped = unwrapDiagnosisPayload<unknown>(raw);
      const { content, totalCount } = parsePagedRowsFromApiData<Record<string, unknown>>(unwrapped);
      const records = content
        .filter(isRecord)
        .map((row) => mapConditionDefinitionMetadataApiRow(row));
      const base = isRecord(unwrapped) ? unwrapped : {};
      return {
        ...base,
        records,
        total: totalCount,
      } as ConditionDefinitionPageResponse;
    },

    async httpSaveDoctorDiagnosis(body) {
      return publicRequestClient.post<SaveEncounterDiagnosisResponse>(
        `${prefix}/saveDoctorDiagnosis`,
        body,
      );
    },

    async httpDelEncounterDiagnosis(body) {
      return publicRequestClient.post<DeleteEncounterDiagnosisResponse>(
        `${prefix}/delEncounterDiagnosis`,
        body,
      );
    },
  };
}
