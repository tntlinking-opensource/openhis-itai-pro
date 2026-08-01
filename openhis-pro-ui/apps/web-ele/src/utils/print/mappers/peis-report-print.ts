/** 体检报告观察结果行（与模板 observationLines 列 field 一致） */
export interface PeisReportObservationLine {
  itemName: string;
  resultText: string;
  unitText: string;
  refRangeText: string;
}

/** 体检总检报告 PrintData（与 peis-report-print 模板 field 一致） */
export interface PeisReportPrintData {
  hospitalName: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  busNo: string;
  checkDateText: string;
  summaryText: string;
  conclusionText: string;
  observationLines: PeisReportObservationLine[];
}

/** printPeisReport.slip（后端 PeisReportPrintSlipDto） */
export interface PeisReportPrintSource {
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  busNo?: string;
  checkDateText?: string;
  summaryText?: string;
  conclusionText?: string;
  observationLines?: PeisReportObservationLine[];
  encounterId?: null | number | string;
  diagnosticReportId?: null | number | string;
  patientId?: null | number | string;
  scenarioCode?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapObservationLines(
  lines: PeisReportObservationLine[] | undefined,
): PeisReportObservationLine[] {
  if (!Array.isArray(lines)) {
    return [];
  }
  return lines.map((line) => ({
    itemName: str(line.itemName),
    resultText: str(line.resultText),
    unitText: str(line.unitText),
    refRangeText: str(line.refRangeText),
  }));
}

/** 后端体检报告 slip → PrintData */
export function buildPeisReportPrintData(source: PeisReportPrintSource): PeisReportPrintData {
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    busNo: str(source.busNo),
    checkDateText: str(source.checkDateText),
    summaryText: str(source.summaryText),
    conclusionText: str(source.conclusionText),
    observationLines: mapObservationLines(source.observationLines),
  };
}
