import type {
  BedBoardItemDto,
  BedBoardOccupantDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import type { WardItem } from '../types';

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
  gender: string;
  age: number;
  level: string;
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

/** 占床块 → 卡片展示字段 */
function mapOccupantCell(dto: BedBoardOccupantDto): BedBoardOccupantCell {
  return {
    encounterId: String(dto.encounterId ?? '').trim(),
    patientName: (dto.patientName ?? '').trim() || '—',
    gender: (dto.genderEnum ?? '').trim() || '—',
    age: parseAgeTextToNumber(dto.ageText),
    level: (dto.priorityEnum ?? '').trim() || '—',
    statusEnum: (dto.statusEnum ?? '').trim(),
    statusEnumText: (dto.statusEnumText ?? '').trim() || undefined,
    diagnosis: (dto.diagnosisSummary ?? '').trim() || '—',
    accountBalance: dto.accountBalance,
  };
}

/** 按病房 id 聚合床位列表行 */
export function mapBedBoardItemsToHouseGroups(
  items: BedBoardItemDto[],
  wardOptions: WardItem[],
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
      occupant: occupantRaw ? mapOccupantCell(occupantRaw) : null,
    });
  }
  return [...groupMap.values()];
}
