import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import type { InpatientDoctorStationPatientDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-encounter.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 读侧 Long 主键入页面前统一为 string */
function toIdString(raw: unknown): string {
  return preserveApiIdForRequest(raw) ?? '';
}

/** 可选主键/外键：空则 undefined */
function toOptionalIdString(raw: unknown): string | undefined {
  const s = preserveApiIdForRequest(raw);
  return s || undefined;
}

/** IF-IP-DOR-01 队列行 → 医生站侧栏/上下文患者模型 */
export function mapDoctorStationPatientQueueRow(
  dto: InpatientDoctorStationPatientDto,
): InpatientDoctorStationPatient {
  return {
    accountBalance: dto.accountBalance,
    accountBalanceDisplayMode: dto.accountBalanceDisplayMode,
    accountBalanceSubText: dto.accountBalanceSubText,
    accountBalanceSummaryText: dto.accountBalanceSummaryText,
    availableBalance: dto.availableBalance,
    admissionTime: dto.admissionTime,
    ageText: dto.ageText,
    arrearsAmount: dto.arrearsAmount,
    attendingDoctorName: dto.attendingDoctorName,
    bedId: toOptionalIdString(dto.bedId),
    bedName: dto.bedName,
    contractDisplay: dto.contractDisplay,
    insutype: dto.insutype,
    diagnosisList: dto.diagnosisList,
    diagnosisSummary: dto.diagnosisSummary,
    encounterBusNo: dto.encounterBusNo,
    encounterId: toIdString(dto.encounterId),
    genderEnum: dto.genderEnum,
    genderEnumText: dto.genderEnumText,
    houseName: dto.houseName,
    idCard: dto.idCard,
    phone: dto.phone,
    lowBalanceWarning: dto.lowBalanceWarning === true,
    nursingLevelActivityId: toOptionalIdString(dto.nursingLevelActivityId),
    nursingLevelName: dto.nursingLevelName,
    nursingLevelServiceRequestId: toOptionalIdString(dto.nursingLevelServiceRequestId),
    nursingLevelStatusEnum: dto.nursingLevelStatusEnum,
    organizationId: toOptionalIdString(dto.organizationId),
    organizationName: dto.organizationName,
    paidAmount: dto.paidAmount,
    patientId: toIdString(dto.patientId),
    patientName: dto.patientName ?? '',
    pinyinCode: dto.pinyinCode,
    prepayNetBalance: dto.prepayNetBalance,
    primaryNurseName: dto.primaryNurseName,
    priorityEnum: dto.priorityEnum,
    pendingSettlementTotal: dto.pendingSettlementTotal,
    startTime: dto.startTime,
    endTime: dto.endTime,
    statusEnum: dto.statusEnum,
    statusEnumText: dto.statusEnumText,
    totalAmount: dto.totalAmount,
    wardId: toOptionalIdString(dto.wardId),
    wardName: dto.wardName,
    wubiCode: dto.wubiCode,
  };
}
