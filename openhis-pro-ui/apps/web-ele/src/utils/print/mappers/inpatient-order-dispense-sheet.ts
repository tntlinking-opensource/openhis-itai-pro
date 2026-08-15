import { decimalMul } from '#/utils/format/decimal-util';

/** 医嘱执行摆药单药品行（仅进患者区块内的药品表） */
export interface InpatientOrderDispenseSheetDrugRow {
  drugName: string;
  specText: string;
  doseText: string;
  totalQuantityText: string;
}

/**
 * 医嘱执行摆药单一名患者区块。
 * 版式：患者信息（表外）→ 药品表；多区块一张连续纸（无患者签字）。
 */
export interface InpatientOrderDispenseSheetPatientBlock {
  patientHeadText: string;
  drugRows: InpatientOrderDispenseSheetDrugRow[];
}

/**
 * 医嘱执行摆药单 PrintData（一张连续多人）。
 * 模板用 longText formatter 按 patientBlocks 循环，患者信息不进药品表。
 */
export interface InpatientOrderDispenseSheetPrintData {
  hospitalName: string;
  documentTitle: string;
  executeDateText: string;
  patientBlocks: InpatientOrderDispenseSheetPatientBlock[];
}

/** 医嘱执行摆药单 mapper 入参 */
export interface InpatientOrderDispenseSheetPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  executeDateText?: string;
  patientBlocks?: InpatientOrderDispenseSheetPatientBlock[];
}

/** @deprecated 兼容旧引用：连续行形态已废弃，改用 patientBlocks */
export type InpatientOrderDispenseSheetSheetRow = InpatientOrderDispenseSheetDrugRow & {
  rowType?: string;
  patientGroupKey?: string;
  patientHeadText?: string;
};

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 患者头展示文案（表外展示，由模板绑定 patientHeadText） */
export function formatOrderDispensePatientHeadText(parts: {
  admissionAtText?: null | string;
  ageText?: null | string;
  bedNo?: null | string;
  patientName?: null | string;
  regionName?: null | string;
  roomName?: null | string;
}): string {
  const chunks: string[] = [];
  const regionName = str(parts.regionName);
  const roomName = str(parts.roomName);
  const bedNo = str(parts.bedNo);
  const patientName = str(parts.patientName);
  const ageText = str(parts.ageText);
  const admissionAtText = str(parts.admissionAtText);
  if (regionName) {
    chunks.push(`病区：${regionName}`);
  }
  if (roomName) {
    chunks.push(`病房：${roomName}`);
  }
  if (bedNo) {
    chunks.push(`床号：${bedNo}`);
  }
  if (patientName) {
    chunks.push(`姓名：${patientName}`);
  }
  if (ageText) {
    chunks.push(`年龄：${ageText}`);
  }
  if (admissionAtText) {
    chunks.push(`入院：${admissionAtText}`);
  }
  return chunks.join('　');
}

/** 摆药单总量可选口径（不可拆院点口服长嘱按整包装；临嘱用开立总量） */
export interface OrderDispenseTotalQuantityOptions {
  /**
   * 临嘱/出院带药开立总量文案（如 `1盒`）。
   * 有值时优先回显，避免用每次剂量（dosageDisplay）冒充总量。
   */
  orderTotalQuantityText?: null | string;
  /** true：按「执行次数 × 包装单位」计总量（长嘱一盒一点） */
  packageBatchQuantity?: boolean;
  /** 包装单位展示名（目录）；整包装口径下必填，禁止写死「盒」 */
  packageUnitDisplay?: null | string;
}

/**
 * 摆药单总量。
 * 临嘱/出院带药：优先开立总量；不可拆口服长嘱：执行次数 × 目录包装单位；默认可拆：单次剂量 × 当日执行次数。
 */
export function formatOrderDispenseTotalQuantityText(
  dosageDisplay: null | string | undefined,
  times: number,
  options?: OrderDispenseTotalQuantityOptions,
): string {
  const orderTotal = str(options?.orderTotalQuantityText);
  if (orderTotal) {
    return orderTotal;
  }
  const count = times > 0 ? times : 1;
  const packageUnit = str(options?.packageUnitDisplay);
  if (options?.packageBatchQuantity && packageUnit) {
    return `${count}${packageUnit}`;
  }
  let dose = str(dosageDisplay);
  if (!dose) {
    return String(count);
  }
  dose = dose.replace(/\/次$/u, '').trim();
  const parenIdx = dose.indexOf('(');
  if (parenIdx > 0) {
    dose = dose.slice(0, parenIdx).trim();
  }
  const match = dose.match(/^(\d+(?:\.\d+)?)(.*)$/u);
  if (!match) {
    return count > 1 ? `${dose}×${count}` : dose;
  }
  const qtyRaw = match[1];
  const unit = str(match[2]);
  const qty = Number(qtyRaw);
  if (!Number.isFinite(qty)) {
    return count > 1 ? `${dose}×${count}` : dose;
  }
  const total = decimalMul(qty, count);
  const totalText = String(total).replace(/(\.\d*?[1-9])0+$|\.0+$/u, '$1');
  return unit ? `${totalText}${unit}` : totalText;
}

/**
 * 是否应按整包装计摆药总量（对齐 BR-IP-EXE-035：不可拆 + 长期 + 口服 + 有包装装量 + 目录包装单位）。
 */
export function shouldUsePackageBatchDispenseQuantity(params: {
  packageBatchSiteEnabled: boolean;
  isOral: boolean;
  therapyEnum?: null | string;
  packageSplitRatioText?: null | string;
  packageUnitDisplay?: null | string;
}): boolean {
  if (!params.packageBatchSiteEnabled || !params.isOral) {
    return false;
  }
  const therapy = str(params.therapyEnum).toUpperCase();
  if (therapy !== 'LONG_TERM') {
    return false;
  }
  if (!str(params.packageSplitRatioText)) {
    return false;
  }
  // 无目录包装单位时不走整包装口径，避免写死「盒」
  return Boolean(str(params.packageUnitDisplay));
}

function mapDrugRow(row: InpatientOrderDispenseSheetDrugRow): InpatientOrderDispenseSheetDrugRow {
  return {
    drugName: str(row.drugName),
    specText: str(row.specText),
    doseText: str(row.doseText),
    totalQuantityText: str(row.totalQuantityText),
  };
}

function mapPatientBlock(
  block: InpatientOrderDispenseSheetPatientBlock,
): InpatientOrderDispenseSheetPatientBlock {
  return {
    patientHeadText: str(block.patientHeadText),
    drugRows: (block.drugRows ?? []).map((row) => mapDrugRow(row)),
  };
}

/** 医嘱执行摆药单 → PrintData */
export function buildInpatientOrderDispenseSheetPrintData(
  source: InpatientOrderDispenseSheetPrintSource,
): InpatientOrderDispenseSheetPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '摆药单',
    executeDateText: str(source.executeDateText),
    patientBlocks: (source.patientBlocks ?? []).map((block) => mapPatientBlock(block)),
  };
}
