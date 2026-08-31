/** 住院患者用药单药品行 */
export interface InpatientOralMedSheetDrugRow {
  drugName: string;
  specText: string;
  /** 单次剂量 */
  doseText: string;
  /** 用法（途径展示文案） */
  usageText: string;
  /** 频次 */
  freqText: string;
  totalQuantityText: string;
  /** 医嘱备注 */
  remarkText: string;
  /**
   * 同方「频次 / 总量」合并行数。
   * 方头 >1 表示 rowspan；续行 0 表示跳过单元格；西药 1。
   */
  tcmGroupRowSpan?: number;
}

/**
 * 住院患者用药单一名患者区块。
 * 版式：患者信息（表外）→ 药品表 → 患者签字；多区块一张连续纸。
 */
export interface InpatientOralMedSheetPatientBlock {
  patientHeadText: string;
  patientSignLabel: string;
  drugRows: InpatientOralMedSheetDrugRow[];
}

/**
 * 住院患者用药单 PrintData（一张连续多人）。
 * 模板用 longText formatter 按 patientBlocks 循环。
 */
export interface InpatientOralMedSheetPrintData {
  hospitalName: string;
  documentTitle: string;
  executeDateText: string;
  patientBlocks: InpatientOralMedSheetPatientBlock[];
}

/** 住院患者用药单 mapper 入参 */
export interface InpatientOralMedSheetPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  executeDateText?: string;
  patientBlocks?: InpatientOralMedSheetPatientBlock[];
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapDrugRow(row: InpatientOralMedSheetDrugRow): InpatientOralMedSheetDrugRow {
  const spanRaw = row.tcmGroupRowSpan;
  const tcmGroupRowSpan = typeof spanRaw === 'number' && Number.isFinite(spanRaw) ? spanRaw : 1;
  return {
    drugName: str(row.drugName),
    specText: str(row.specText),
    doseText: str(row.doseText),
    usageText: str(row.usageText),
    freqText: str(row.freqText),
    totalQuantityText: str(row.totalQuantityText),
    remarkText: str(row.remarkText),
    tcmGroupRowSpan,
  };
}

function mapPatientBlock(
  block: InpatientOralMedSheetPatientBlock,
): InpatientOralMedSheetPatientBlock {
  return {
    patientHeadText: str(block.patientHeadText),
    patientSignLabel: str(block.patientSignLabel) || '患者签字：____________',
    drugRows: (block.drugRows ?? []).map((row) => mapDrugRow(row)),
  };
}

/** 住院患者用药单 → PrintData */
export function buildInpatientOralMedSheetPrintData(
  source: InpatientOralMedSheetPrintSource,
): InpatientOralMedSheetPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '患者用药单',
    executeDateText: str(source.executeDateText),
    patientBlocks: (source.patientBlocks ?? []).map((block) => mapPatientBlock(block)),
  };
}
