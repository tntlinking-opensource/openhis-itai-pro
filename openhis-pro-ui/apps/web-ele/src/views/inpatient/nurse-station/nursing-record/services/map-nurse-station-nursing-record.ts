import type {
  NurseStationNursingRecordPaperHeaderDto,
  NurseStationNursingRecordRowDto,
  SaveNurseStationNursingRecordBatchItemRequest,
  SaveNurseStationNursingRecordRequest,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.types';

import type {
  NursingRecordPaperHeader,
  NursingRecordRow,
  NursingRecordViewRow,
} from '../components/nursing-record.types';

import { resolveAdministrativeGenderLabel } from '#/utils/gender/resolve-administrative-gender-label';

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

/** 组合为日期时间选择器绑定值（YYYY-MM-DD HH:mm） */
export function composeNursingRecordDatetime(recordDate?: string, recordTime?: string): string {
  const date = (recordDate ?? '').trim();
  if (!date) {
    return '';
  }
  const time = (recordTime ?? '').trim().slice(0, 5);
  if (!time) {
    return '';
  }
  return `${date} ${time}`;
}

/** 拆分日期时间选择器值为 recordDate / recordTime */
export function splitNursingRecordDatetime(value?: null | string): {
  recordDate: string;
  recordTime: string;
} {
  const raw = (value ?? '').trim();
  if (!raw) {
    return { recordDate: '', recordTime: '' };
  }
  const [datePart = '', timePart = ''] = raw.split(/\s+/);
  return {
    recordDate: datePart,
    recordTime: timePart.slice(0, 5),
  };
}

/** 拆分血压展示串为收缩压/舒张压（如 128/76）。 */
export function splitNursingRecordBloodPressure(bloodPressure?: null | string): {
  diastolic: string;
  systolic: string;
} {
  const raw = (bloodPressure ?? '').trim();
  if (!raw) {
    return { diastolic: '', systolic: '' };
  }
  const slashIndex = raw.indexOf('/');
  if (slashIndex < 0) {
    return { diastolic: '', systolic: raw };
  }
  return {
    systolic: raw.slice(0, slashIndex).trim(),
    diastolic: raw.slice(slashIndex + 1).trim(),
  };
}

/** 组合收缩压/舒张压为血压展示串（对齐后端 BloodPressureParser）。 */
export function composeNursingRecordBloodPressure(
  systolic?: null | string,
  diastolic?: null | string,
): string {
  const sbp = (systolic ?? '').trim();
  const dbp = (diastolic ?? '').trim();
  if (sbp && dbp) {
    return `${sbp}/${dbp}`;
  }
  if (sbp) {
    return sbp;
  }
  if (dbp) {
    return `/${dbp}`;
  }
  return '';
}

/** 展示用 MM-DD（纸质单日期列，不强调年份）。 */
export function formatNursingRecordDateMd(recordDate?: string): string {
  const date = (recordDate ?? '').trim();
  if (!date) {
    return '';
  }
  const parts = date.split('-');
  if (parts.length !== 3) {
    return date;
  }
  return `${parts[1]}-${parts[2]}`;
}

/** MM-DD 输入回写完整日期，保留原年份或当前年。 */
export function syncNursingRecordDateMd(recordDate: string | undefined, mdValue: string): string {
  const year = recordDate?.slice(0, 4) || `${new Date().getFullYear()}`;
  const match = mdValue.trim().match(/^(\d{1,2})-(\d{1,2})$/);
  if (!match) {
    return mdValue.trim();
  }
  const month = match[1]?.padStart(2, '0');
  const day = match[2]?.padStart(2, '0');
  return `${year}-${month}-${day}`;
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

/** 闭区间天数（至少 1 天） */
export function countInclusiveDays(from: string, to: string): number {
  const start = new Date(`${from.trim()}T00:00:00`);
  const end = new Date(`${to.trim()}T00:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end < start) {
    return 7;
  }
  const dayMs = 24 * 60 * 60 * 1000;
  return Math.floor((end.getTime() - start.getTime()) / dayMs) + 1;
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

/** 生成纸质单骨架行（按查询天数 × 预置时点；不填默认日期/时间） */
export function buildWeekSkeletonRows(dayCount = 7): NursingRecordViewRow[] {
  const rows: NursingRecordViewRow[] = [];
  const safeDayCount = Math.max(1, Math.floor(dayCount) || 7);
  const skeletonCount = safeDayCount * PAPER_RECORD_TIMES.length;

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
  const ageRaw = `${dto.age ?? ''}`.trim();
  // ageText 可能已带「岁」，避免模板再拼一次变成「26岁岁」
  const age = ageRaw ? (/岁\s*$/.test(ageRaw) ? ageRaw : `${ageRaw}岁`) : undefined;
  return {
    admitAt: (dto.admitAt ?? '').trim() || undefined,
    age,
    bedNo: (dto.bedNo ?? '').trim() || undefined,
    dischargeAt: (dto.dischargeAt ?? '').trim() || undefined,
    // 后端纸质抬头暂回传 genderEnum code，统一解析为展示文案
    gender: resolveAdministrativeGenderLabel(undefined, dto.gender, '') || undefined,
    inpatientNo: (dto.inpatientNo ?? '').trim() || undefined,
    name: (dto.name ?? '').trim() || undefined,
    wardName: (dto.wardName ?? '').trim() || undefined,
  };
}

/** 展示行字段映射为保存载荷（单条/批量共用） */
function mapViewRowFieldsToSavePayload(row: NursingRecordViewRow) {
  return {
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
    // 护士签名下拉 value 存医务人员 id，提交 recorderId 供后端落库
    recorderId: row.nurseSignature || undefined,
    nurseSignature: undefined,
  };
}

/** 展示行转时间轴单条保存请求 */
export function mapViewRowToSingleSaveRequest(
  row: NursingRecordViewRow,
  context: { encounterId: string; patientId: string },
): SaveNurseStationNursingRecordRequest | null {
  if (!row.recordDate?.trim() || !row.recordTime?.trim()) {
    return null;
  }
  if (isNursingRecordRowEmpty(row)) {
    return null;
  }
  return {
    encounterId: context.encounterId,
    patientId: context.patientId,
    ...mapViewRowFieldsToSavePayload(row),
    recordDate: row.recordDate,
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
    ...mapViewRowFieldsToSavePayload(row),
    recordDate: row.recordDate,
  };

  // 含内容的骨架槽位去掉 skeleton 标记，避免后端批量过滤
  if (row.rowType === 'extra') {
    item.rowType = 'extra';
  }

  return item;
}

/** 将回显姓名对齐为护士下拉 value（医务人员 id）。 */
export function resolveNurseSignatureOptionValue(
  signature: string | undefined,
  options: { label: string; value: string }[],
): string {
  const raw = (signature ?? '').trim();
  if (!raw) {
    return '';
  }
  if (options.some((opt) => opt.value === raw)) {
    return raw;
  }
  const byLabel = options.find((opt) => opt.label === raw);
  return byLabel?.value ?? raw;
}

/** 批量把行内护士签名从姓名对齐为下拉 value。 */
export function normalizeNursingRecordNurseSignatures(
  rows: NursingRecordViewRow[],
  options: { label: string; value: string }[],
): void {
  if (options.length === 0) {
    return;
  }
  for (const row of rows) {
    row.nurseSignature = resolveNurseSignatureOptionValue(row.nurseSignature, options);
  }
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
