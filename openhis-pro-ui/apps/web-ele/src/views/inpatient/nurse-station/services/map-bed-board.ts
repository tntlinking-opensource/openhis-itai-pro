import type {
  BedBoardItemDto,
  BedBoardOccupantDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import type { WardItem } from '../types';

import { resolveAdministrativeGenderLabel } from '#/utils/gender/resolve-administrative-gender-label';
import { resolveAdmCondCodeLabel } from '#/utils/terminology/resolve-adm-cond-code-label';
import type { TerminologyOptionItem } from '#/utils/terminology/resolve-terminology-option-label';

import { parseAgeTextToNumber } from './map-inpatient-nurse-station-api';

/** 床位图单格展示 */
export interface BedBoardBedCell {
  bedId: string;
  bedName: string;
  operationalEnum: string;
  occupant: BedBoardOccupantCell | null;
}

/** 床位图占床患者摘要 */
export interface BedBoardOccupantCell {
  encounterId: string;
  patientName: string;
  /** 性别展示文案（读模型 genderEnumText） */
  gender: string;
  age: number;
  /** 护理级别展示名（读模型 nursingLevelName；未开立时为空） */
  nursingLevel: string;
  /** 病情 code（分床写路径用） */
  priorityEnum?: string;
  /** 病情展示名（adm_cond_code 字典解析） */
  priorityLabel?: string;
  statusEnum: string;
  statusEnumText?: string;
  diagnosis: string;
  accountBalance?: number | string;
}

/** 按病房分组的床位图 */
export interface BedBoardHouseGroup {
  houseId: string;
  houseName: string;
  regionId: string;
  beds: BedBoardBedCell[];
}

/** 床位图护理级别角标文案（去掉末尾「护理」以适配窄卡片） */
export function formatBedBoardNursingLevelLabel(nursingLevel: string | undefined): string {
  const raw = (nursingLevel ?? '').trim();
  if (!raw) {
    return '';
  }
  if (raw.endsWith('护理')) {
    return raw.slice(0, -2);
  }
  return raw;
}

/** 占床块 → 卡片展示字段 */
function mapOccupantCell(
  dto: BedBoardOccupantDto,
  admCondCodeOptions: TerminologyOptionItem[],
): BedBoardOccupantCell {
  const priorityCode = (dto.priorityEnum ?? '').trim();
  return {
    encounterId: String(dto.encounterId ?? '').trim(),
    patientName: (dto.patientName ?? '').trim() || '—',
    gender: resolveAdministrativeGenderLabel(dto.genderEnumText, dto.genderEnum),
    age: parseAgeTextToNumber(dto.ageText),
    nursingLevel: (dto.nursingLevelName ?? '').trim(),
    priorityEnum: priorityCode || undefined,
    priorityLabel: priorityCode
      ? resolveAdmCondCodeLabel(priorityCode, admCondCodeOptions)
      : undefined,
    statusEnum: (dto.statusEnum ?? '').trim(),
    statusEnumText: (dto.statusEnumText ?? '').trim() || undefined,
    diagnosis: (dto.diagnosisSummary ?? '').trim() || '—',
    accountBalance: dto.accountBalance,
  };
}

/** 床位图卡片第二行：性别 · 年龄 · 病情 */
export function formatBedBoardOccupantMeta(
  occupant: Pick<BedBoardOccupantCell, 'age' | 'gender' | 'priorityLabel'>,
): string {
  const parts: string[] = [];
  const gender = (occupant.gender ?? '').trim();
  if (gender && gender !== '—') {
    parts.push(gender);
  }
  if (occupant.age > 0) {
    parts.push(`${occupant.age}岁`);
  }
  const priorityLabel = (occupant.priorityLabel ?? '').trim();
  if (priorityLabel) {
    parts.push(priorityLabel);
  }
  return parts.join(' ');
}

/** 按病房 id 聚合床位列表行 */
export function isBedOperationalInUse(operationalEnum: string | undefined): boolean {
  return (operationalEnum ?? '').trim() === 'IN_USE';
}

/** 空床判定：以 location.operationalEnum 为准（BR-IP-ATD-001），不得仅凭 occupant 有无。 */
export function isBedVacant(bed: Pick<BedBoardBedCell, 'operationalEnum'>): boolean {
  return !isBedOperationalInUse(bed.operationalEnum);
}

/** 按病房 id 聚合床位列表行 */
export function mapBedBoardItemsToHouseGroups(
  items: BedBoardItemDto[],
  wardOptions: WardItem[],
  admCondCodeOptions: TerminologyOptionItem[] = [],
): BedBoardHouseGroup[] {
  const houseToRegion = new Map<string, string>();
  for (const ward of wardOptions) {
    houseToRegion.set(ward.id, ward.regionId);
  }
  const groupMap = new Map<string, BedBoardHouseGroup>();
  for (const row of items) {
    const loc = row.location;
    if (!loc?.bedId || !loc.houseId) {
      continue;
    }
    const houseId = String(loc.houseId).trim();
    const bedId = String(loc.bedId).trim();
    if (!houseId || !bedId) {
      continue;
    }
    let group = groupMap.get(houseId);
    if (!group) {
      group = {
        houseId,
        houseName: (loc.houseName ?? '').trim() || '—',
        regionId: houseToRegion.get(houseId) ?? '',
        beds: [],
      };
      groupMap.set(houseId, group);
    }
    const occupantRaw = row.occupant;
    group.beds.push({
      bedId,
      bedName: (loc.bedName ?? '').trim() || bedId,
      operationalEnum: (loc.operationalEnum ?? '').trim() || 'NOT_IN_USE',
      occupant: occupantRaw ? mapOccupantCell(occupantRaw, admCondCodeOptions) : null,
    });
  }
  return [...groupMap.values()];
}
