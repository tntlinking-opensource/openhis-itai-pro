import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

/** 中药饮片四列网格行（打印/系统内处方单共用） */
export interface TcmHerbGridDisplayRow {
  col1: string;
  note1: string;
  col2: string;
  note2: string;
  col3: string;
  note3: string;
  col4: string;
  note4: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 单味饮片展示：药名 + 单味剂量 */
export function formatTcmHerbDisplayFromSlipRow(
  row: EncounterMedicationDispenseSlipRowDto,
): string {
  const name = str(row.medicationName) || str(row.merchandiseName);
  const doseValue = row.doseValue;
  const doseUnit = str(row.doseUnitText);
  if (name && doseValue !== null && doseValue !== undefined && doseValue !== '') {
    const dose = String(doseValue).trim();
    return doseUnit ? `${name} ${dose}${doseUnit}` : `${name} ${dose}`;
  }
  const left = str(row.medicationDisplayLeft);
  if (left) return left.replace(/\s+X\s+.*$/i, '').trim();
  return name;
}

function formatDecoctionNote(note: string): string {
  return note ? `(${note})` : '';
}

/** slip 行 → 四列饮片网格（与打印模板 tcmHerbRows 一致） */
export function buildTcmHerbGridRowsFromSlipRows(
  rows: EncounterMedicationDispenseSlipRowDto[],
): TcmHerbGridDisplayRow[] {
  const cells = rows.map((row) => ({
    herbDisplay: formatTcmHerbDisplayFromSlipRow(row),
    decoctionNote: str(row.dosageInstructionText),
  }));
  const cols = 4;
  const gridRows: TcmHerbGridDisplayRow[] = [];
  for (let i = 0; i < cells.length; i += cols) {
    const chunk = cells.slice(i, i + cols);
    gridRows.push({
      col1: chunk[0]?.herbDisplay ?? '',
      note1: formatDecoctionNote(chunk[0]?.decoctionNote ?? ''),
      col2: chunk[1]?.herbDisplay ?? '',
      note2: formatDecoctionNote(chunk[1]?.decoctionNote ?? ''),
      col3: chunk[2]?.herbDisplay ?? '',
      note3: formatDecoctionNote(chunk[2]?.decoctionNote ?? ''),
      col4: chunk[3]?.herbDisplay ?? '',
      note4: formatDecoctionNote(chunk[3]?.decoctionNote ?? ''),
    });
  }
  return gridRows;
}

const HERB_MEASURE_UNITS = new Set(['g', '克', 'kg', '千克', 'ml', '毫升', 'mg', '毫克']);

function formatTcmDoseCount(head: EncounterMedicationDispenseSlipRowDto): string {
  const qty = str(head.requestQuantity);
  if (!qty) return '';
  const unit = str(head.unitText);
  if (!unit || HERB_MEASURE_UNITS.has(unit) || HERB_MEASURE_UNITS.has(unit.toLowerCase())) {
    return `${qty}付`;
  }
  if (unit === '付' || unit === '剂') {
    return `${qty}${unit}`;
  }
  return `${qty}${unit}`;
}

/** 付数 + 频次 + 用法（单行） */
export function buildTcmPrescriptionUsageTextFromSlipHead(
  head: EncounterMedicationDispenseSlipRowDto,
): string {
  const parts: string[] = [];
  const doseCount = formatTcmDoseCount(head);
  if (doseCount) parts.push(doseCount);
  const freq = str(head.frequencyText);
  if (freq) parts.push(freq);
  const route = str(head.routeText);
  if (route) parts.push(route);
  return parts.join('  ');
}
