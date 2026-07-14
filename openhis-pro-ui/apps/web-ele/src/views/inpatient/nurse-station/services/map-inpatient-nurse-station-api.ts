import type {
  NurseStationWardTreeNodeDto,
  PatientQueueItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { NURSE_STATION_STATUS_FILTER_SETTLED_DISCHARGED } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { resolveAdministrativeGenderLabel } from '#/utils/gender/resolve-administrative-gender-label';
import { normalizeAdmCondTermCode } from '#/utils/terminology/resolve-adm-cond-code-label';

import type {
  PatientItem,
  PatientQueueScopeFilter,
  PatientStatusFilter,
  RegionItem,
  WardItem,
} from '../types';

/** 任务筛选：前端下拉值与后端 code 一致，空表示全部 */
export function mapStatusFilterToTaskFilter(status: PatientStatusFilter): string | undefined {
  if (status === 'all') {
    return undefined;
  }
  return status;
}

/** 列表范围筛选 → 后端 nurseStationStatusFilter */
export function mapQueueScopeFilterToStatusFilter(
  scope: PatientQueueScopeFilter,
): string | undefined {
  if (scope === 'settled-discharged') {
    return NURSE_STATION_STATUS_FILTER_SETTLED_DISCHARGED;
  }
  return undefined;
}

/** 病区树 → 左侧「病区」勾选项 */
export function mapWardTreeToRegionOptions(wardNodes: NurseStationWardTreeNodeDto[]): RegionItem[] {
  const rows: RegionItem[] = [];
  for (const ward of wardNodes) {
    const id = String(ward.locationId ?? '').trim();
    if (!id) {
      continue;
    }
    rows.push({
      id,
      name: (ward.name ?? '').trim() || '—',
    });
  }
  return rows;
}

/** 病区树 → 左侧「病房」勾选项（病房挂到所属病区） */
export function mapWardTreeToWardOptions(wardNodes: NurseStationWardTreeNodeDto[]): WardItem[] {
  const rows: WardItem[] = [];
  for (const ward of wardNodes) {
    const regionId = String(ward.locationId ?? '').trim();
    if (!regionId) {
      continue;
    }
    const houses = ward.houseNodes ?? [];
    for (const house of houses) {
      const id = String(house.locationId ?? '').trim();
      if (!id) {
        continue;
      }
      rows.push({
        id,
        regionId,
        name: (house.name ?? '').trim() || '—',
      });
    }
  }
  return rows;
}

/** 从年龄展示文案提取卡片上的数字年龄 */
export function parseAgeTextToNumber(ageText: string | undefined): number {
  const raw = (ageText ?? '').trim();
  if (!raw) {
    return 0;
  }
  const matched = raw.match(/\d+/);
  if (!matched) {
    return 0;
  }
  const value = Number.parseInt(matched[0], 10);
  if (!Number.isFinite(value)) {
    return 0;
  }
  return value;
}

/** 读模型仅有病房名时，按病区 + 病房名反查病房位置 id */
export function resolveHouseLocationId(
  wardId: string,
  houseName: string | undefined,
  wardOptions: WardItem[],
): string {
  const normalizedHouseName = (houseName ?? '').trim();
  if (!wardId || !normalizedHouseName) {
    return '';
  }
  for (const option of wardOptions) {
    if (option.regionId === wardId && option.name === normalizedHouseName) {
      return option.id;
    }
  }
  return '';
}

/** 就诊状态 code → 患者列表展示文案（优先读模型 statusEnumText） */
export function encounterStatusToNurseStationLabel(code?: string, statusEnumText?: string): string {
  const text = (statusEnumText ?? '').trim();
  if (text) {
    return text;
  }
  const c = (code ?? '').trim();
  if (c === 'REGISTERED') return '待入科';
  if (c === 'IN_PROGRESS') return '在院';
  if (c === 'ON_HOLD') return '待出院';
  if (c === 'PENDING') return '待结算';
  if (c === 'COMPLETED') return '已结算';
  if (c === 'DISCHARGED') return '已离院';
  return c || '—';
}

/** 队列行 → 护士站患者卡片（保持现有 PatientItem 字段供布局消费） */
export function mapQueueItemToPatientItem(
  dto: PatientQueueItemDto,
  wardOptions: WardItem[],
): PatientItem {
  const regionId = String(dto.wardId ?? '').trim();
  const houseLocationId = resolveHouseLocationId(regionId, dto.houseName, wardOptions);
  const pendingAudit = dto.pendingAuditCount ?? 0;
  const pendingExecute = dto.pendingExecuteCount ?? 0;
  const lowBalanceWarning = dto.lowBalanceWarning === true;
  return {
    id: String(dto.encounterId),
    patientId: preserveApiIdForRequest(dto.patientId),
    encounterBusNo: (dto.encounterBusNo ?? '').trim() || undefined,
    startTime: (dto.startTime ?? '').trim() || undefined,
    admissionTime: (dto.admissionTime ?? dto.startTime ?? '').trim() || undefined,
    endTime: (dto.endTime ?? '').trim() || undefined,
    name: (dto.patientName ?? '').trim() || '—',
    gender: resolveAdministrativeGenderLabel(dto.genderEnumText, dto.genderEnum),
    age: parseAgeTextToNumber(dto.ageText),
    level: (dto.nursingLevelName ?? '').trim(),
    priorityEnum: normalizeAdmCondTermCode(dto.priorityEnum) || undefined,
    feeType: (dto.organizationName ?? '').trim() || '—',
    diagnosis: (dto.diagnosisSummary ?? '').trim() || '—',
    accountId: preserveApiIdForRequest(dto.accountId),
    accountBalance: dto.accountBalance,
    accountBalanceDisplayMode: dto.accountBalanceDisplayMode,
    accountBalanceSummaryText: dto.accountBalanceSummaryText,
    accountBalanceSubText: dto.accountBalanceSubText,
    availableBalance: dto.availableBalance,
    pendingSettlementTotal: dto.pendingSettlementTotal,
    regionId,
    wardId: houseLocationId,
    bedNo: (dto.bedName ?? '').trim(),
    bedLocationId: String(dto.bedId ?? '').trim(),
    statusEnum: (dto.statusEnum ?? '').trim(),
    statusEnumText: (dto.statusEnumText ?? '').trim() || undefined,
    lowBalanceWarning: dto.lowBalanceWarning === true,
    recallEnabled: dto.recallEnabled === true,
    pendingAudit,
    pendingExecute,
    taskTag: '',
    riskText: lowBalanceWarning ? '低余额' : '',
    selected: false,
  };
}
