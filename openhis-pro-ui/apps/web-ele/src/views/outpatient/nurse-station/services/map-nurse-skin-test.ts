import type {
  OutpatientSkinTestEnumOptionDto,
  OutpatientSkinTestRowDto,
  SaveOutpatientSkinTestRecordRequest,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 皮试观察时长（分钟）：末次反应时间 = 开始时间 + 该间隔 */
export const SKIN_TEST_OBSERVE_MINUTES = 20;

/** 隐藏「验证状态」后保存仍需传值时的兜底 code */
export const SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE = 'confirmed';

/** 从初始化枚举解析默认验证状态（优先「已确诊」） */
export function resolveSkinTestDefaultVerificationStatusCode(
  options: OutpatientSkinTestEnumOptionDto[] | null | undefined,
): string {
  if (!options?.length) {
    return SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE;
  }
  const confirmed = options.find((opt) => {
    const code = String(opt.code ?? '').trim();
    const label = (opt.displayText ?? '').trim();
    return label === '已确诊' || code === 'confirmed';
  });
  const confirmedCode = String(confirmed?.code ?? '').trim();
  if (confirmedCode) {
    return confirmedCode;
  }
  return String(options[0]?.code ?? '').trim() || SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE;
}

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
  /** 登记/开始时间（cln_allergy_intolerance.recorded_date） */
  recordedDate?: string;
  /** 末次反应时间（cln_allergy_intolerance.last_occurrence，结束时间） */
  lastOccurrence?: string;
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

/** 按开始时间计算末次反应时间（+20 分钟） */
export function calcSkinTestLastOccurrence(startDate: string | undefined): string | undefined {
  const start = (startDate ?? '').trim();
  if (!start) {
    return undefined;
  }
  const parsed = dayjs(start);
  if (!parsed.isValid()) {
    return undefined;
  }
  return parsed.add(SKIN_TEST_OBSERVE_MINUTES, 'minute').format('YYYY-MM-DD HH:mm:ss');
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
  verificationStatusOptions?: OutpatientSkinTestEnumOptionDto[] | null,
): NurseSkinTestRowDraft {
  const practitionerId = preserveApiIdForRequest(row.practitionerId) ?? defaultPractitionerId ?? '';
  const checkPractitionerId =
    preserveApiIdForRequest(row.checkPractitionerId) ?? defaultPractitionerId ?? '';
  const recordedRaw = String(row.recordedDate ?? '').trim();
  const lastOccurrenceRaw = String(row.lastOccurrence ?? '').trim();
  const recordedDate = recordedRaw || buildSkinTestDefaultRecordedDate();
  const verificationFromRow = (row.verificationStatusCode ?? '').trim();
  return {
    serviceRequestId: String(row.serviceRequestId ?? '').trim(),
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: (row.medicationName ?? '').trim() || '—',
    openDoctorName: (row.openDoctorName ?? '').trim() || undefined,
    patientId: String(row.patientId ?? '').trim(),
    allergyRecordId: preserveApiIdForRequest(row.allergyRecordId) ?? undefined,
    lotNumber: resolveSkinTestLotNumberFromRow(row),
    recordedDate,
    lastOccurrence: lastOccurrenceRaw || calcSkinTestLastOccurrence(recordedDate),
    clinicalStatusCode: (row.clinicalStatusCode ?? '').trim(),
    verificationStatusCode:
      verificationFromRow ||
      resolveSkinTestDefaultVerificationStatusCode(verificationStatusOptions),
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
  verificationStatusOptions?: OutpatientSkinTestEnumOptionDto[] | null,
): NurseSkinTestTableRow {
  const draft = mapSkinTestRowToDraft(row, defaultPractitionerId, verificationStatusOptions);
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
  const recordedDate = recordedRaw || buildSkinTestDefaultRecordedDate();
  return {
    serviceRequestId: String(row.serviceRequestId ?? '').trim(),
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: String(row.medicationName ?? '').trim() || '—',
    openDoctorName: row.openDoctorName,
    patientId: String(row.patientId ?? '').trim(),
    allergyRecordId: row.allergyRecordId,
    lotNumber: String(row.lotNumber ?? '').trim() || undefined,
    recordedDate,
    lastOccurrence: row.lastOccurrence ?? calcSkinTestLastOccurrence(recordedDate),
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
  verificationStatusOptions?: OutpatientSkinTestEnumOptionDto[] | null,
): SaveOutpatientSkinTestRecordRequest {
  const verificationStatusCode =
    draft.verificationStatusCode.trim() ||
    resolveSkinTestDefaultVerificationStatusCode(verificationStatusOptions);
  return {
    encounterId: String(encounterId).trim(),
    patientId: String(draft.patientId).trim(),
    serviceRequestId: String(draft.serviceRequestId).trim(),
    medicationRequestId: String(draft.medicationRequestId).trim(),
    medicationId: String(draft.medicationId).trim(),
    allergyRecordId: draft.allergyRecordId,
    lotNumber: draft.lotNumber,
    recordedDate: draft.recordedDate ?? buildSkinTestDefaultRecordedDate(),
    lastOccurrence: draft.lastOccurrence ?? calcSkinTestLastOccurrence(draft.recordedDate),
    clinicalStatusCode: draft.clinicalStatusCode.trim(),
    verificationStatusCode,
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
