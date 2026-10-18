/**
 * 护士站床号自然序（按展示名，不按 busNo/id）。
 * 例：504-4 < 504-5 < 505-4；505-5 < 505-5（加床） < 505-6；未分床垫底。
 *
 * 排序键在映射队列行时预计算，列表热路径只比数字。
 */

/** 未分床 / 无数字时的垫底房号 */
export const NURSE_STATION_BED_SORT_UNASSIGNED = Number.MAX_SAFE_INTEGER;

/** 床号排序键：房号 → 床号 → 是否加床 */
export interface NurseStationBedNoSortKey {
  /** 加床：0=普通，1=加床（同号时普通在前） */
  extra: number;
  /** 连字符后床位号 */
  bed: number;
  /** 连字符前房号 */
  room: number;
}

/** 从床位展示名解析排序键（空串视为未分床） */
export function parseNurseStationBedNoSortKey(
  raw: string | undefined,
): NurseStationBedNoSortKey {
  const trimmed = (raw ?? '').trim();
  if (!trimmed) {
    return {
      room: NURSE_STATION_BED_SORT_UNASSIGNED,
      bed: NURSE_STATION_BED_SORT_UNASSIGNED,
      extra: 0,
    };
  }
  const extra = trimmed.includes('加床') ? 1 : 0;
  const cleaned = extra
    ? trimmed.replace(/[（(]?\s*加床\s*[）)]?/g, '').trim()
    : trimmed;
  const roomBed = cleaned.match(/(\d+)\s*[-－—]\s*(\d+)/);
  if (roomBed) {
    return { room: Number(roomBed[1]), bed: Number(roomBed[2]), extra };
  }
  const single = cleaned.match(/(\d+)/);
  if (single) {
    return { room: Number(single[1]), bed: 0, extra };
  }
  return { room: NURSE_STATION_BED_SORT_UNASSIGNED - 1, bed: 0, extra };
}

/** 可挂床号排序键的患者行 */
export type PatientBedSortable = {
  bedNo?: string;
  bedSortBed?: number;
  bedSortExtra?: number;
  bedSortRoom?: number;
  name?: string;
};

/** 按 bedNo 写回预计算排序键（映射 / 换床时调用） */
export function applyNurseStationBedSortKeys(item: PatientBedSortable): void {
  const key = parseNurseStationBedNoSortKey(item.bedNo);
  item.bedSortRoom = key.room;
  item.bedSortBed = key.bed;
  item.bedSortExtra = key.extra;
}

/**
 * 按预计算键比较（热路径只比数字）；缺键视为未分床垫底。
 * 同床号再比姓名。
 */
export function comparePatientItemsByBedNo(
  a: PatientBedSortable,
  b: PatientBedSortable,
): number {
  const ar = a.bedSortRoom ?? NURSE_STATION_BED_SORT_UNASSIGNED;
  const br = b.bedSortRoom ?? NURSE_STATION_BED_SORT_UNASSIGNED;
  if (ar !== br) {
    return ar - br;
  }
  const ab = a.bedSortBed ?? NURSE_STATION_BED_SORT_UNASSIGNED;
  const bb = b.bedSortBed ?? NURSE_STATION_BED_SORT_UNASSIGNED;
  if (ab !== bb) {
    return ab - bb;
  }
  const ae = a.bedSortExtra ?? 0;
  const be = b.bedSortExtra ?? 0;
  if (ae !== be) {
    return ae - be;
  }
  const an = a.name ?? '';
  const bn = b.name ?? '';
  if (an === bn) {
    return 0;
  }
  return an < bn ? -1 : 1;
}
