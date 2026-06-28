import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

/** 输液瓶签药品行（与 op-infusionLabel-print 模板 medicationLines 列一致） */
export interface OutpatientInfusionLabelMedicationLine {
  drugName: string;
  specText: string;
  totalQuantityText: string;
  usageText: string;
}

/** 输液瓶签 PrintData（与 op-infusionLabel-print 模板 field 一致） */
export interface OutpatientInfusionLabelPrintData {
  patientName: string;
  genderEnumText: string;
  ageText: string;
  registerTimeText: string;
  medicationLines: OutpatientInfusionLabelMedicationLine[];
  executorName: string;
  executeTimeText: string;
}

/** 瓶签 mapper 入参（P：护士站当前就诊 + 输液类药品行） */
export interface OutpatientInfusionLabelPrintSource {
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  registerTimeText?: string;
  medicationLines?: NurseStationTreatmentLineDto[];
  executorName?: string;
  executeTimeText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function buildUsageText(row: NurseStationTreatmentLineDto): string {
  return str(row.methodName) || str(row.methodCode);
}

function formatTotalQuantityText(row: NurseStationTreatmentLineDto): string {
  const qtyRaw = row.quantity;
  if (qtyRaw === null || qtyRaw === undefined || String(qtyRaw).trim() === '') {
    return '';
  }
  const qty = typeof qtyRaw === 'number' ? qtyRaw : Number(String(qtyRaw).trim());
  const qtyText = Number.isFinite(qty) ? String(qty) : String(qtyRaw).trim();
  const unit = str(row.unitName) || str(row.unitCode);
  return unit ? `${qtyText}${unit}` : qtyText;
}

function mapMedicationLine(
  row: NurseStationTreatmentLineDto,
): OutpatientInfusionLabelMedicationLine {
  return {
    drugName: str(row.itemName),
    specText: str(row.specText),
    totalQuantityText: formatTotalQuantityText(row),
    usageText: buildUsageText(row),
  };
}

function infusionLineSortSeq(raw: NurseStationTreatmentLineDto['sortNumber']): number {
  if (raw === null || raw === undefined) {
    return Number.MAX_SAFE_INTEGER;
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER;
}

function formatDateTimeLikeNurseStation(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  const second = `${date.getSeconds()}`.padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

function parseExecuteTimeText(value: string): Date | null {
  const text = value.trim();
  if (!text) {
    return null;
  }
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4] ?? '0');
  const minute = Number(match[5] ?? '0');
  const second = Number(match[6] ?? '0');
  const parsed = new Date(year, month - 1, day, hour, minute, second);
  if (
    parsed.getFullYear() !== year ||
    parsed.getMonth() !== month - 1 ||
    parsed.getDate() !== day
  ) {
    return null;
  }
  return parsed;
}

/** 输液组用药天数（取组内最大值；无效时默认 1 天） */
export function resolveInfusionLabelPrintDayCount(
  medicationLines: NurseStationTreatmentLineDto[],
): number {
  let maxDays = 0;
  for (const row of medicationLines) {
    const raw = row.dispensePerDuration;
    if (raw === null || raw === undefined) {
      continue;
    }
    const days = typeof raw === 'number' ? raw : Number(String(raw).trim());
    if (Number.isFinite(days) && days > maxDays) {
      maxDays = Math.trunc(days);
    }
  }
  return maxDays > 0 ? maxDays : 1;
}

/** 按用药天序（0 起）偏移执行时间；无法解析时原样返回 */
export function buildExecuteTimeTextForInfusionDay(
  baseExecuteTimeText: string,
  dayIndex: number,
): string {
  if (dayIndex <= 0) {
    return str(baseExecuteTimeText);
  }
  const parsed = parseExecuteTimeText(baseExecuteTimeText);
  if (!parsed) {
    return str(baseExecuteTimeText);
  }
  parsed.setDate(parsed.getDate() + dayIndex);
  return formatDateTimeLikeNurseStation(parsed);
}

/** 输液瓶签打印组：同 groupId 的药品打到同一张瓶贴 */
export interface InfusionLabelPrintGroup {
  groupId?: string;
  medicationLines: NurseStationTreatmentLineDto[];
}

/** 筛选输液类药品并按 groupId 分组（无组号则每条单独一组） */
export function groupInfusionMedicationLinesForPrint(
  lines: NurseStationTreatmentLineDto[],
): InfusionLabelPrintGroup[] {
  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of lines) {
    if (!isInfusionMedicationLine(row)) {
      continue;
    }
    const requestId = str(row.requestId);
    if (!requestId) {
      continue;
    }
    const groupId = str(row.groupId);
    const groupKey = groupId ? `g:${groupId}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: groupId || undefined,
      medicationLines: [],
    };
    if (!existing.medicationLines.some((line) => str(line.requestId) === requestId)) {
      existing.medicationLines.push(row);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()].map((group) => ({
    ...group,
    medicationLines: [...group.medicationLines].toSorted(
      (left, right) => infusionLineSortSeq(left.sortNumber) - infusionLineSortSeq(right.sortNumber),
    ),
  }));
}

/** 判断是否为输液类药品行（由后端 infusionFlag 判定，含药品定义 inject_flag 回落） */
export function isInfusionMedicationLine(row: NurseStationTreatmentLineDto): boolean {
  return row.infusionFlag === true;
}

/** 护士站输液药品行 → PrintData */
export function buildOutpatientInfusionLabelPrintData(
  source: OutpatientInfusionLabelPrintSource,
): OutpatientInfusionLabelPrintData {
  const rows = (source.medicationLines ?? []).filter((line) => isInfusionMedicationLine(line));
  return {
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    registerTimeText: str(source.registerTimeText),
    medicationLines: rows.map((line) => mapMedicationLine(line)),
    executorName: str(source.executorName),
    executeTimeText: str(source.executeTimeText),
  };
}
