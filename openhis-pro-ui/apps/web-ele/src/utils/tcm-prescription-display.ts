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

/** 护士站折叠中药方：药名与单次量一一对应的展示单元 */
export interface TcmCollapsedHerbPair {
  name: string;
  dose: string;
}

/** 护士站折叠中药方解析结果（药名/剂量空格并列文案 → 配对网格） */
export interface TcmCollapsedHerbDisplay {
  /** 味数一致时可配对；否则为空，调用方回落纵向堆叠 */
  pairs: TcmCollapsedHerbPair[];
  /** 从剂量串末尾拆出的「N付」，避免与各味剂量混淆 */
  doseCountText: string;
  /** 去掉付数后的剂量原文（配对失败时纵向展示） */
  dosageWithoutCount: string;
}

/**
 * 压缩护士站中药单味剂量文案：同单位重复括号与末尾「/次」去掉，便于扫读。
 * 例：`30克(30.000克)/次` → `30克`；单位不同则保留括号。
 */
export function simplifyTcmHerbDoseDisplay(dose: string): string {
  const raw = str(dose);
  if (!raw) return '';
  const matched = raw.match(
    /^(\d+(?:\.\d+)?)([^\d(（]+?)[(（](\d+(?:\.\d+)?)([^\d)）]+?)[)）](?:\s*\/\s*次)?$/u,
  );
  if (matched) {
    const primary = matched[1] ?? '';
    const primaryUnit = str(matched[2]);
    const secondary = matched[3] ?? '';
    const secondaryUnit = str(matched[4]);
    if (primaryUnit === secondaryUnit && Number(primary) === Number(secondary)) {
      return `${primary}${primaryUnit}`;
    }
  }
  return raw.replace(/\s*\/\s*次$/u, '');
}

/**
 * 解析护士站中药方折叠行展示文案。
 * 后端 adviceDisplayName / dosageDisplay 均为空格分隔且同序；前端常在剂量末追加「N付」。
 */
export function parseTcmCollapsedHerbDisplay(
  content: null | string | undefined,
  dosageText: null | string | undefined,
): TcmCollapsedHerbDisplay {
  const names = str(content).split(/\s+/).filter(Boolean);
  let dosageWithoutCount = str(dosageText);
  let doseCountText = '';
  // 剂量串末尾「N付」是方级付数，不是某一味的剂量
  const fuMatch = dosageWithoutCount.match(/^(.*?)(?:\s+(\d+(?:\.\d+)?)\s*付)\s*$/u);
  if (fuMatch) {
    dosageWithoutCount = str(fuMatch[1]);
    const fuNum = str(fuMatch[2]);
    doseCountText = fuNum ? `${fuNum}付` : '';
  }
  const doses = dosageWithoutCount.split(/\s+/).filter(Boolean);
  // 味数不一致时不强行配对，避免药名/剂量错位
  if (names.length === 0 || doses.length === 0 || names.length !== doses.length) {
    return {
      pairs: [],
      doseCountText,
      dosageWithoutCount: doses.map(simplifyTcmHerbDoseDisplay).filter(Boolean).join(' '),
    };
  }
  const pairs: TcmCollapsedHerbPair[] = [];
  for (let i = 0; i < names.length; i += 1) {
    pairs.push({
      name: names[i] ?? '',
      dose: simplifyTcmHerbDoseDisplay(doses[i] ?? ''),
    });
  }
  return { pairs, doseCountText, dosageWithoutCount };
}
