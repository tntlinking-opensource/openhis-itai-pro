import type {
  NurseStationNursingRecordPaperHeaderDto,
  NurseStationNursingRecordRowDto,
  SaveNurseStationNursingRecordBatchItemRequest,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.types';

import type {
  NursingRecordPaperHeader,
  NursingRecordRow,
  NursingRecordViewRow,
} from '../components/nursing-record.types';

import { isNursingRecordRowEmpty } from '../components/nursing-record-row-state';

/** 纸质单每日预置录入时点 */
export const PAPER_RECORD_TIMES = ['08:00', '14:00'] as const;

let rowSeed = 0;

/** 生成前端行唯一键 */
function nextClientRowId(): string {
  rowSeed += 1;
  return `record-${rowSeed}`;
}

/** 取本周一作为周起始日 */
export function getWeekStartDate(base = new Date()): Date {
  const date = new Date(base);
  const weekday = date.getDay();
  const offset = weekday === 0 ? -6 : 1 - weekday;
  date.setDate(date.getDate() + offset);
  date.setHours(0, 0, 0, 0);
  return date;
}

/** 格式化为 YYYY-MM-DD */
export function formatDateYmd(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 格式化为 MM-DD */
export function formatDateMd(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${month}-${day}`;
}

/** 获取当前日期与时间（精确到分钟） */
export function getNowDateTime(): { date: string; time: string } {
  const now = new Date();
  return {
    date: formatDateYmd(now),
    time: `${`${now.getHours()}`.padStart(2, '0')}:${`${now.getMinutes()}`.padStart(2, '0')}`,
  };
}

/** 行内时间为空时写入当前时刻（精确到分钟） */
export function applyNowTimeIfEmpty(row: NursingRecordRow): boolean {
  if (row.recordTime?.trim()) {
    return false;
  }
  row.recordTime = getNowDateTime().time;
  return true;
}

/** 计算当周日期闭区间 */
export function resolveCurrentWeekRange(): { from: string; to: string } {
  const weekStart = getWeekStartDate();
  const weekEnd = new Date(weekStart);
  weekEnd.setDate(weekEnd.getDate() + 6);
  return {
    from: formatDateYmd(weekStart),
    to: formatDateYmd(weekEnd),
  };
}

/** 生成一条空白护理记录行 */
export function createEmptyNursingRecordRow(
  partial: Partial<NursingRecordRow> = {},
): NursingRecordRow {
  return {
    id: nextClientRowId(),
    recordDate: '',
    recordTime: '',
    consciousness: '',
    temperature: '',
    heartRate: '',
    pulse: '',
    respiratory: '',
    bloodPressure: '',
    pupilLeft: '',
    pupilRight: '',
    oxygenSaturation: '',
    oxygenRate: '',
    oxygenMode: '',
    oxygenFlow: '',
    intakeName: '',
    intakeAmount: '',
    intakeRoute: '',
    outputName: '',
    outputAmount: '',
    outputColorCharacter: '',
    reservedCol1: '',
    reservedCol2: '',
    skinStatus: '',
    tubeCare: '',
    conditionMeasure: '',
    nurseSignature: '',
    ...partial,
  };
}

/** 生成当周纸质单骨架行（仅预置空行，不填默认日期/时间） */
export function buildWeekSkeletonRows(): NursingRecordViewRow[] {
  const rows: NursingRecordViewRow[] = [];
  const skeletonCount = 7 * PAPER_RECORD_TIMES.length;

  for (let index = 0; index < skeletonCount; index += 1) {
    rows.push({
      ...createEmptyNursingRecordRow(),
      rowType: 'skeleton',
    });
  }

  return rows;
}

/** 接口行转前端展示行 */
export function mapApiNursingRecordRowToViewRow(
  dto: NurseStationNursingRecordRowDto,
  rowType?: NursingRecordViewRow['rowType'],
): NursingRecordViewRow {
  const vitalSignId = dto.vitalSignId?.trim() || undefined;
  return {
    id: vitalSignId ?? nextClientRowId(),
    vitalSignId,
    recordDate: (dto.recordDate ?? '').trim(),
    recordTime: (dto.recordTime ?? '').trim(),
    consciousness: (dto.consciousness ?? '').trim(),
    temperature: (dto.temperature ?? '').trim(),
    heartRate: (dto.heartRate ?? '').trim(),
    pulse: (dto.pulse ?? '').trim(),
    respiratory: (dto.respiratory ?? '').trim(),
    bloodPressure: (dto.bloodPressure ?? '').trim(),
    pupilLeft: (dto.pupilLeft ?? '').trim(),
    pupilRight: (dto.pupilRight ?? '').trim(),
    oxygenSaturation: (dto.oxygenSaturation ?? '').trim(),
    oxygenRate: (dto.oxygenRate ?? '').trim(),
    oxygenMode: (dto.oxygenMode ?? '').trim(),
    oxygenFlow: (dto.oxygenFlow ?? '').trim(),
    intakeName: (dto.intakeName ?? '').trim(),
    intakeAmount: (dto.intakeAmount ?? '').trim(),
    intakeRoute: (dto.intakeRoute ?? '').trim(),
    outputName: (dto.outputName ?? '').trim(),
    outputAmount: (dto.outputAmount ?? '').trim(),
    outputColorCharacter: (dto.outputColorCharacter ?? '').trim(),
    reservedCol1: '',
    reservedCol2: '',
    skinStatus: (dto.skinStatus ?? '').trim(),
    tubeCare: (dto.tubeCare ?? '').trim(),
    conditionMeasure: (dto.conditionMeasure ?? '').trim(),
    nurseSignature: (dto.nurseSignature ?? '').trim(),
    rowType,
    isDirty: false,
  };
}

/** 骨架行与接口已存行合并为纸质单展示列表 */
export function mergePaperNursingRecordRows(
  skeletonRows: NursingRecordViewRow[],
  apiRows: NurseStationNursingRecordRowDto[],
): NursingRecordViewRow[] {
  const mergedSkeleton = skeletonRows.map((row) => ({
    ...row,
    isDirty: false,
  }));

  const sortedApiRows = [...apiRows].toSorted((a, b) => {
    const aTime = new Date(`${a.recordDate ?? ''}T${a.recordTime || '00:00'}:00`).getTime();
    const bTime = new Date(`${b.recordDate ?? ''}T${b.recordTime || '00:00'}:00`).getTime();
    return aTime - bTime;
  });

  const usedSkeletonIndices = new Set<number>();
  const extraRows: NursingRecordViewRow[] = [];

  for (const apiRow of sortedApiRows) {
    const viewRow = mapApiNursingRecordRowToViewRow(apiRow);

    const vitalSignIndex = mergedSkeleton.findIndex(
      (row, index) => !usedSkeletonIndices.has(index) && row.vitalSignId === viewRow.vitalSignId,
    );
    if (vitalSignIndex >= 0) {
      usedSkeletonIndices.add(vitalSignIndex);
      mergedSkeleton[vitalSignIndex] = {
        ...viewRow,
        rowType: 'skeleton',
        isDirty: false,
      };
      continue;
    }

    const slotIndex = mergedSkeleton.findIndex(
      (row, index) =>
        !usedSkeletonIndices.has(index) &&
        row.recordDate === viewRow.recordDate &&
        row.recordTime === viewRow.recordTime &&
        Boolean(viewRow.recordDate),
    );
    if (slotIndex >= 0) {
      usedSkeletonIndices.add(slotIndex);
      mergedSkeleton[slotIndex] = {
        ...viewRow,
        rowType: 'skeleton',
        isDirty: false,
      };
      continue;
    }

    const emptyIndex = mergedSkeleton.findIndex(
      (row, index) =>
        !usedSkeletonIndices.has(index) && isNursingRecordRowEmpty(row) && !row.vitalSignId,
    );
    if (emptyIndex >= 0) {
      usedSkeletonIndices.add(emptyIndex);
      mergedSkeleton[emptyIndex] = {
        ...viewRow,
        rowType: 'skeleton',
        isDirty: false,
      };
      continue;
    }

    extraRows.push({
      ...viewRow,
      rowType: 'extra',
      isDirty: false,
    });
  }

  return [...mergedSkeleton, ...extraRows];
}

/** 接口抬头转纸质单抬头 */
export function mapApiPaperHeaderToView(
  dto?: NurseStationNursingRecordPaperHeaderDto | null,
): NursingRecordPaperHeader | null {
  if (!dto) {
    return null;
  }
  return {
    admitAt: (dto.admitAt ?? '').trim() || undefined,
    age: (dto.age ?? '').trim() || undefined,
    bedNo: (dto.bedNo ?? '').trim() || undefined,
    dischargeAt: (dto.dischargeAt ?? '').trim() || undefined,
    gender: (dto.gender ?? '').trim() || undefined,
    inpatientNo: (dto.inpatientNo ?? '').trim() || undefined,
    name: (dto.name ?? '').trim() || undefined,
    wardName: (dto.wardName ?? '').trim() || undefined,
  };
}

/** 展示行转批量保存请求项 */
export function mapViewRowToBatchSaveItem(
  row: NursingRecordViewRow,
): SaveNurseStationNursingRecordBatchItemRequest | null {
  if (!row.isDirty || isNursingRecordRowEmpty(row)) {
    return null;
  }

  const item: SaveNurseStationNursingRecordBatchItemRequest = {
    vitalSignId: row.vitalSignId,
    recordDate: row.recordDate,
    recordTime: row.recordTime || undefined,
    consciousness: row.consciousness || undefined,
    temperature: row.temperature || undefined,
    heartRate: row.heartRate || undefined,
    pulse: row.pulse || undefined,
    respiratory: row.respiratory || undefined,
    bloodPressure: row.bloodPressure || undefined,
    pupilLeft: row.pupilLeft || undefined,
    pupilRight: row.pupilRight || undefined,
    oxygenSaturation: row.oxygenSaturation || undefined,
    oxygenRate: row.oxygenRate || undefined,
    oxygenMode: row.oxygenMode || undefined,
    oxygenFlow: row.oxygenFlow || undefined,
    intakeName: row.intakeName || undefined,
    intakeAmount: row.intakeAmount || undefined,
    intakeRoute: row.intakeRoute || undefined,
    outputName: row.outputName || undefined,
    outputAmount: row.outputAmount || undefined,
    outputColorCharacter: row.outputColorCharacter || undefined,
    skinStatus: row.skinStatus || undefined,
    tubeCare: row.tubeCare || undefined,
    conditionMeasure: row.conditionMeasure || undefined,
  };

  // 含内容的骨架槽位去掉 skeleton 标记，避免后端批量过滤
  if (row.rowType === 'extra') {
    item.rowType = 'extra';
  }

  return item;
}

/** 批量保存响应行回写前端列表 */
export function applyBatchSavedRowsToViewRows(
  currentRows: NursingRecordViewRow[],
  savedRows: NurseStationNursingRecordRowDto[],
): NursingRecordViewRow[] {
  const savedByVitalSignId = new Map<string, NursingRecordViewRow>();
  const savedBySlot = new Map<string, NursingRecordViewRow>();

  // 建立已保存行索引，便于回写 vitalSignId 与字段
  for (const dto of savedRows) {
    const viewRow = mapApiNursingRecordRowToViewRow(dto);
    if (viewRow.vitalSignId) {
      savedByVitalSignId.set(viewRow.vitalSignId, viewRow);
    }
    savedBySlot.set(`${viewRow.recordDate}|${viewRow.recordTime}`, viewRow);
  }

  return currentRows.map((row) => {
    if (!row.isDirty) {
      return row;
    }
    const matched =
      (row.vitalSignId ? savedByVitalSignId.get(row.vitalSignId) : undefined) ??
      savedBySlot.get(`${row.recordDate}|${row.recordTime}`);
    if (!matched) {
      return { ...row, isDirty: false };
    }
    return {
      ...matched,
      id: matched.vitalSignId ?? row.id,
      rowType: row.rowType,
      isDirty: false,
    };
  });
}
