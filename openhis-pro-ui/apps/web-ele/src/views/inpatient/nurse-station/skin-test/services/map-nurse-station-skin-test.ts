import type {
  NurseStationSkinTestEnumOptionDto,
  NurseStationSkinTestRowDto,
  SaveNurseStationSkinTestRecordRequest,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 住院皮试列表行编辑草稿 */
export interface InpatientSkinTestRowDraft {
  accountId: string;
  allergyRecordId?: string;
  checkPractitionerId: string;
  clinicalStatusCode: string;
  criticalityCode?: string;
  description?: string;
  lotNumber?: string;
  medicationId: string;
  medicationName: string;
  medicationRequestId: string;
  openDoctorName?: string;
  patientId: string;
  practitionerId: string;
  pricedFlag: boolean;
  recordedDate?: string;
  severityCode?: string;
  verificationStatusCode: string;
}

/** 由接口行生成可编辑草稿 */
export function mapInpatientSkinTestRowToDraft(
  row: NurseStationSkinTestRowDto,
  accountId: string,
  defaultPractitionerId?: string,
): InpatientSkinTestRowDraft {
  const practitionerId = preserveApiIdForRequest(row.practitionerId) ?? defaultPractitionerId ?? '';
  const checkPractitionerId =
    preserveApiIdForRequest(row.checkPractitionerId) ?? defaultPractitionerId ?? '';
  return {
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: (row.medicationName ?? '').trim() || '—',
    openDoctorName: (row.openDoctorName ?? '').trim() || undefined,
    patientId: String(row.patientId ?? '').trim(),
    accountId: String(accountId).trim(),
    allergyRecordId: preserveApiIdForRequest(row.allergyRecordId) ?? undefined,
    lotNumber: (row.lotNumber ?? '').trim() || undefined,
    recordedDate: (row.recordedDate ?? '').trim() || undefined,
    clinicalStatusCode: (row.clinicalStatusCode ?? '').trim(),
    verificationStatusCode: (row.verificationStatusCode ?? '').trim(),
    practitionerId,
    checkPractitionerId,
    description: (row.description ?? '').trim() || undefined,
    criticalityCode: (row.criticalityCode ?? '').trim() || undefined,
    severityCode: (row.severityCode ?? '').trim() || undefined,
    pricedFlag: row.pricedFlag === true,
  };
}

/** 草稿转保存请求体 */
export function mapInpatientSkinTestDraftToSaveRequest(
  draft: InpatientSkinTestRowDraft,
  encounterId: string,
): SaveNurseStationSkinTestRecordRequest {
  return {
    encounterId: String(encounterId).trim(),
    patientId: String(draft.patientId).trim(),
    accountId: String(draft.accountId).trim(),
    medicationRequestId: String(draft.medicationRequestId).trim(),
    medicationId: String(draft.medicationId).trim(),
    allergyRecordId: draft.allergyRecordId,
    lotNumber: draft.lotNumber,
    recordedDate: draft.recordedDate,
    clinicalStatusCode: draft.clinicalStatusCode.trim(),
    verificationStatusCode: draft.verificationStatusCode.trim(),
    criticalityCode: draft.criticalityCode,
    severityCode: draft.severityCode,
    description: draft.description,
    practitionerId: String(draft.practitionerId).trim(),
    checkPractitionerId: String(draft.checkPractitionerId).trim(),
  };
}

/** 枚举选项转下拉 label/value */
export function mapInpatientSkinTestEnumOptions(
  options: NurseStationSkinTestEnumOptionDto[] | null | undefined,
): { label: string; value: string }[] {
  if (!options?.length) {
    return [];
  }
  return options
    .map((opt) => ({
      value: String(opt.code ?? '').trim(),
      label: (opt.displayText ?? '').trim() || String(opt.code ?? ''),
    }))
    .filter((opt) => opt.value.length > 0);
}

/** 临床结果是否为阳性（卡片高亮） */
export function isInpatientSkinTestPositiveClinicalCode(code: string): boolean {
  return code.trim() === 'active';
}

/** 行是否允许删除（未划价且已有过敏记录） */
export function canDeleteInpatientSkinTestRow(row: NurseStationSkinTestRowDto): boolean {
  const allergyRecordId = String(row.allergyRecordId ?? '').trim();
  if (!allergyRecordId) {
    return false;
  }
  return row.pricedFlag !== true;
}
