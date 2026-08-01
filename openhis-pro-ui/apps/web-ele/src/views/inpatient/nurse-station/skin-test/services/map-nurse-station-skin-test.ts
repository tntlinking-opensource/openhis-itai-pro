import type {
  NurseStationSkinTestEnumOptionDto,
  NurseStationSkinTestRowDto,
  SaveNurseStationSkinTestRecordRequest,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.types';

import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 皮试观察时长（分钟）：结束时间 = 开始时间 + 该间隔 */
export const INPATIENT_SKIN_TEST_OBSERVE_MINUTES = 20;

/** 隐藏「验证状态」后保存仍需传值时的兜底 code */
export const INPATIENT_SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE = 'confirmed';

/** 从初始化枚举解析默认验证状态（优先「已确诊」） */
export function resolveInpatientSkinTestDefaultVerificationStatusCode(
  options: NurseStationSkinTestEnumOptionDto[] | null | undefined,
): string {
  if (!options?.length) {
    return INPATIENT_SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE;
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
  return (
    String(options[0]?.code ?? '').trim() || INPATIENT_SKIN_TEST_DEFAULT_VERIFICATION_STATUS_CODE
  );
}

/** 住院皮试列表行编辑草稿（按 companion SR 分行） */
export interface InpatientSkinTestRowDraft {
  accountId?: string;
  allergyRecordId?: string;
  checkPractitionerId: string;
  clinicalStatusCode: string;
  criticalityCode?: string;
  description?: string;
  /** 皮试检查是否已执行（可保存） */
  executedFlag: boolean;
  lotNumber?: string;
  medicationId: string;
  medicationName: string;
  medicationRequestId: string;
  openDoctorName?: string;
  patientId: string;
  practitionerId: string;
  /** 已登记只读 */
  readOnlyFlag: boolean;
  /** 登记/开始时间（cln_allergy_intolerance.recorded_date） */
  recordedDate?: string;
  /** 末次反应时间（cln_allergy_intolerance.last_occurrence，结束时间） */
  lastOccurrence?: string;
  severityCode?: string;
  /** companion 皮试检查 SR id（行主键） */
  skinTestServiceRequestId?: string;
  verificationStatusCode: string;
}

/** 按开始时间计算末次反应时间（+20 分钟） */
export function calcInpatientSkinTestLastOccurrence(
  startDate: string | undefined,
): string | undefined {
  const start = (startDate ?? '').trim();
  if (!start) {
    return undefined;
  }
  const parsed = dayjs(start);
  if (!parsed.isValid()) {
    return undefined;
  }
  return parsed.add(INPATIENT_SKIN_TEST_OBSERVE_MINUTES, 'minute').format('YYYY-MM-DD HH:mm:ss');
}

/** 当前时刻作为皮试开始时间 */
export function createInpatientSkinTestStartDateNow(): string {
  return dayjs().format('YYYY-MM-DD HH:mm:ss');
}

/** 列表行主键：优先 companion SR id，缺省回退皮试药 id */
export function resolveInpatientSkinTestRowKey(row: NurseStationSkinTestRowDto): string {
  const srId = String(row.skinTestServiceRequestId ?? '').trim();
  if (srId) {
    return srId;
  }
  return String(row.medicationRequestId ?? '').trim();
}

/** 由接口行生成可编辑草稿 */
export function mapInpatientSkinTestRowToDraft(
  row: NurseStationSkinTestRowDto,
  accountId: string,
  defaultPractitionerId?: string,
  verificationStatusOptions?: NurseStationSkinTestEnumOptionDto[] | null,
): InpatientSkinTestRowDraft {
  const practitionerId = preserveApiIdForRequest(row.practitionerId) ?? defaultPractitionerId ?? '';
  const checkPractitionerId =
    preserveApiIdForRequest(row.checkPractitionerId) ?? defaultPractitionerId ?? '';
  const recordedFromRow = (row.recordedDate ?? '').trim();
  const lastOccurrenceFromRow = (row.lastOccurrence ?? '').trim();
  // 未登记行默认开始时间为当前；已登记回显 cln_allergy_intolerance.recorded_date
  const recordedDate = recordedFromRow || createInpatientSkinTestStartDateNow();
  // 末次反应时间：优先接口回显，否则按开始时间 +20 分钟
  const lastOccurrence = lastOccurrenceFromRow || calcInpatientSkinTestLastOccurrence(recordedDate);
  const verificationFromRow = (row.verificationStatusCode ?? '').trim();
  const allergyRecordId = preserveApiIdForRequest(row.allergyRecordId) ?? undefined;
  // 后端 readOnlyFlag 优先；缺省时按已有过敏记录推断
  const readOnlyFlag = row.readOnlyFlag === true || Boolean(allergyRecordId);
  return {
    medicationRequestId: String(row.medicationRequestId ?? '').trim(),
    skinTestServiceRequestId: preserveApiIdForRequest(row.skinTestServiceRequestId) ?? undefined,
    medicationId: String(row.medicationId ?? '').trim(),
    medicationName: (row.medicationName ?? '').trim() || '—',
    openDoctorName: (row.openDoctorName ?? '').trim() || undefined,
    patientId: String(row.patientId ?? '').trim(),
    accountId: String(accountId ?? '').trim() || undefined,
    allergyRecordId,
    lotNumber: (row.lotNumber ?? '').trim() || undefined,
    recordedDate,
    lastOccurrence,
    clinicalStatusCode: (row.clinicalStatusCode ?? '').trim(),
    verificationStatusCode:
      verificationFromRow ||
      resolveInpatientSkinTestDefaultVerificationStatusCode(verificationStatusOptions),
    practitionerId,
    checkPractitionerId,
    description: (row.description ?? '').trim() || undefined,
    criticalityCode: (row.criticalityCode ?? '').trim() || undefined,
    severityCode: (row.severityCode ?? '').trim() || undefined,
    executedFlag: row.executedFlag === true,
    readOnlyFlag,
  };
}

/** 草稿转保存请求体 */
export function mapInpatientSkinTestDraftToSaveRequest(
  draft: InpatientSkinTestRowDraft,
  encounterId: string,
  verificationStatusOptions?: NurseStationSkinTestEnumOptionDto[] | null,
): SaveNurseStationSkinTestRecordRequest {
  const verificationStatusCode =
    draft.verificationStatusCode.trim() ||
    resolveInpatientSkinTestDefaultVerificationStatusCode(verificationStatusOptions);
  return {
    encounterId: String(encounterId).trim(),
    patientId: String(draft.patientId).trim(),
    accountId: draft.accountId ? String(draft.accountId).trim() : undefined,
    medicationRequestId: String(draft.medicationRequestId).trim(),
    skinTestServiceRequestId: draft.skinTestServiceRequestId
      ? String(draft.skinTestServiceRequestId).trim()
      : undefined,
    medicationId: String(draft.medicationId).trim(),
    allergyRecordId: draft.allergyRecordId,
    lotNumber: String(draft.lotNumber ?? '').trim(),
    recordedDate: draft.recordedDate,
    lastOccurrence: draft.lastOccurrence ?? calcInpatientSkinTestLastOccurrence(draft.recordedDate),
    clinicalStatusCode: draft.clinicalStatusCode.trim(),
    verificationStatusCode,
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

/** 临床结果是否为阴性（左侧条绿色） */
export function isInpatientSkinTestNegativeClinicalCode(code: string): boolean {
  return code.trim() === 'inactive';
}

/** 行是否允许删除（已有过敏记录即可；费用由执行轨管理） */
export function canDeleteInpatientSkinTestRow(row: NurseStationSkinTestRowDto): boolean {
  return Boolean(String(row.allergyRecordId ?? '').trim());
}

/**
 * 行是否允许保存结果：已执行、未只读、且已选批号（BR-IP-STK-013）。
 * 草稿批号由调用方传入时优先；否则看行上已回显批号。
 */
export function canSaveInpatientSkinTestRow(
  row: NurseStationSkinTestRowDto,
  draftLotNumber?: string,
): boolean {
  if (row.executedFlag !== true) {
    return false;
  }
  const allergyId = String(row.allergyRecordId ?? '').trim();
  if (row.readOnlyFlag === true || allergyId) {
    return false;
  }
  const lotFromDraft = (draftLotNumber ?? '').trim();
  const lotFromRow = (row.lotNumber ?? '').trim();
  return Boolean(lotFromDraft || lotFromRow);
}
