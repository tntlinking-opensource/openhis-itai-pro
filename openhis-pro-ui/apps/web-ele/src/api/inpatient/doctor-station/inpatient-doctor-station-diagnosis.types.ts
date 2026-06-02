/**
 * 住院医生站 · 诊断录入 · 与 AS-IP-DOR-002 Request/Response 同构（camelCase）。
 * 类型与门诊诊断共用定义，Long 主键在请求体中一律为 string。
 */
export type {
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
  SaveEncounterDiagnosisChildRequest,
  SaveEncounterDiagnosisRequest,
  SaveEncounterDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';
