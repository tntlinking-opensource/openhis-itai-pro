import type {
  OutpatientSkinTestEnumOptionDto,
  OutpatientSkinTestRowDto,
  SaveOutpatientSkinTestRecordRequest,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 皮试列表行编辑草稿（表格内登记用） */
export interface NurseSkinTestRowDraft {
  allergyRecordId?: string;
  checkPractitionerId: string;
  clinicalStatusCode: string;
  criticalityCode?: string;
  description?: string;
  lotNumber?: string;
  medicationId: string;
  medicationRequestId: string;
  medicationName: string;
  openDoctorName?: string;
  patientId: string;
  practitionerId: string;
  recordedDate?: string;
  serviceRequestId: string;
  severityCode?: string;
  verificationStatusCode: string;
}

/** 皮试网格行模型（含只读展示字段；批号仅落库不传 UI） */
export interface NurseSkinTestTableRow extends NurseSkinTestRowDraft {
  registered: boolean;
  clinicalStatusText?: string;
  verificationStatusText?: string;
  practitionerName?: string;
  checkPractitionerName?: string;
}

/** 下拉 code → 展示文案（优先接口回显，再查选项） */
export function resolveSkinTestOptionLabel(
  code: string | undefined,
  options: { label: string; value: string }[],
  displayText?: string,
): string {
  const text = String(displayText ?? '').trim();
  if (text) {
    return text;
  }
  const normalized = String(code ?? '').trim();
  if (!normalized) {
    return '—';
  }
  const hit = options.find((opt) => opt.value === normalized);
  return hit?.label ?? normalized;
}

/** 新建/未登记行默认记录时间为当前时刻 */
export function buildSkinTestDefaultRecordedDate(): string {
  return dayjs().format('YYYY-MM-DD HH:mm:ss');
}

/** 批号直接取后端行字段，不在界面编辑 */
export function resolveSkinTestLotNumberFromRow(row: OutpatientSkinTestRowDto): string | undefined {
  const direct = String(row.lotNumber ?? '').trim();
  if (direct) {
    return direct;
  }
  return undefined;
}

/** 由接口行生成可编辑草稿，缺省双签由调用方注入 */
export function mapSkinTestRowToDraft(
  row: OutpatientSkinTestRowDto,
  defaultPractitionerId?: string,
): NurseSkinTestRowDraft {
  const practitionerId = preserveApiIdForRequest(row.practitionerId) ?? defaultPractitionerId ?? '';
  const checkPractitionerId =
    preserveApiIdForRequest(row.checkPractitionerId) ?? defaultPractitionerId ?? '';
  const recordedRaw = String(row.recordedDate ?? '').trim();
  return {
    serviceRequestId: String(row.serviceRequestId ?? '').trim(),
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: (row.medicationName ?? '').trim() || '—',
    openDoctorName: (row.openDoctorName ?? '').trim() || undefined,
    patientId: String(row.patientId ?? '').trim(),
    allergyRecordId: preserveApiIdForRequest(row.allergyRecordId) ?? undefined,
    lotNumber: resolveSkinTestLotNumberFromRow(row),
    recordedDate: recordedRaw || buildSkinTestDefaultRecordedDate(),
    clinicalStatusCode: (row.clinicalStatusCode ?? '').trim(),
    verificationStatusCode: (row.verificationStatusCode ?? '').trim(),
    practitionerId,
    checkPractitionerId,
    description: (row.description ?? '').trim() || undefined,
    criticalityCode: (row.criticalityCode ?? '').trim() || undefined,
    severityCode: (row.severityCode ?? '').trim() || undefined,
  };
}

/** 接口行 → 皮试网格行 */
export function mapSkinTestRowToTableRow(
  row: OutpatientSkinTestRowDto,
  defaultPractitionerId?: string,
): NurseSkinTestTableRow {
  const draft = mapSkinTestRowToDraft(row, defaultPractitionerId);
  return {
    ...draft,
    registered: Boolean(draft.allergyRecordId),
    clinicalStatusText: String(row.clinicalStatusText ?? '').trim() || undefined,
    verificationStatusText: String(row.verificationStatusText ?? '').trim() || undefined,
    practitionerName: String(row.practitionerName ?? '').trim() || undefined,
    checkPractitionerName: String(row.checkPractitionerName ?? '').trim() || undefined,
  };
}

/** 皮试网格行 → 保存草稿 */
export function mapSkinTestTableRowToDraft(row: NurseSkinTestTableRow): NurseSkinTestRowDraft {
  const recordedRaw = String(row.recordedDate ?? '').trim();
  return {
    serviceRequestId: String(row.serviceRequestId ?? '').trim(),
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: String(row.medicationName ?? '').trim() || '—',
    openDoctorName: row.openDoctorName,
    patientId: String(row.patientId ?? '').trim(),
    allergyRecordId: row.allergyRecordId,
    lotNumber: String(row.lotNumber ?? '').trim() || undefined,
    recordedDate: recordedRaw || buildSkinTestDefaultRecordedDate(),
    clinicalStatusCode: String(row.clinicalStatusCode ?? '').trim(),
    verificationStatusCode: String(row.verificationStatusCode ?? '').trim(),
    criticalityCode: row.criticalityCode,
    severityCode: row.severityCode,
    description: row.description,
    practitionerId: String(row.practitionerId ?? '').trim(),
    checkPractitionerId: String(row.checkPractitionerId ?? '').trim(),
  };
}

/** 草稿转保存请求体 */
export function mapSkinTestDraftToSaveRequest(
  draft: NurseSkinTestRowDraft,
  encounterId: string,
): SaveOutpatientSkinTestRecordRequest {
  return {
    encounterId: String(encounterId).trim(),
    patientId: String(draft.patientId).trim(),
    serviceRequestId: String(draft.serviceRequestId).trim(),
    medicationRequestId: String(draft.medicationRequestId).trim(),
    medicationId: String(draft.medicationId).trim(),
    allergyRecordId: draft.allergyRecordId,
    lotNumber: draft.lotNumber,
    recordedDate: draft.recordedDate ?? buildSkinTestDefaultRecordedDate(),
    clinicalStatusCode: draft.clinicalStatusCode.trim(),
    verificationStatusCode: draft.verificationStatusCode.trim(),
    criticalityCode: draft.criticalityCode,
    severityCode: draft.severityCode,
    description: draft.description,
    practitionerId: String(draft.practitionerId).trim(),
    checkPractitionerId: String(draft.checkPractitionerId).trim(),
  };
}

/** 枚举选项转 ElSelect 用 label/value */
export function mapSkinTestEnumOptions(
  options: null | OutpatientSkinTestEnumOptionDto[] | undefined,
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

/** 临床结果是否为阳性（展示用） */
export function isSkinTestPositiveClinicalCode(code: string): boolean {
  return code.trim() === 'active';
}
