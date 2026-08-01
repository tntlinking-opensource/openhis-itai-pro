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

/**
 * 单味饮片展示：药名 + 本发放行数量（slip.quantity，与西药/发药提交同源）。
 * 不用 doseValue：那是单付剂量，FIFO 拆行时会把 80/20 都错显成 50。
 */
export function formatTcmHerbDisplayFromSlipRow(
  row: EncounterMedicationDispenseSlipRowDto,
): string {
  const name = str(row.medicationName) || str(row.merchandiseName);
  const qty = row.quantity;
  // 单位优先 unitText；发药 slip 常缺 unitText，回退 doseUnitText（如「克」）
  const unit = str(row.unitText) || str(row.doseUnitText);
  if (name && qty !== null && qty !== undefined && qty !== '') {
    const q = String(qty).trim();
    if (q) {
      return unit ? `${name} ${q}${unit}` : `${name} ${q}`;
    }
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

/**
 * 付数展示：只用方头 chineseHerbsDoseQuantity。
 * 禁止用 requestQuantity——那是单剂×付数的请求总量（如 100g），会错显成「100付」。
 */
function formatTcmDoseCount(head: EncounterMedicationDispenseSlipRowDto): string {
  const doseCount = str(head.chineseHerbsDoseQuantity);
  if (!doseCount) return '';
  return `${doseCount}付`;
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
