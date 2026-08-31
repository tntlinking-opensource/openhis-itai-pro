/**
 * 体检指引单 PrintData（与 peis-guide-print 模板 field 一致）
 */
export interface PeisGuideLine {
  seqText: string;
  deptName: string;
  itemName: string;
  locationText: string;
}

export interface PeisGuidePrintData {
  hospitalName: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  busNo: string;
  packageName: string;
  registeredAtText: string;
  guideLines: PeisGuideLine[];
}

/** printPeisGuide.slip（后端 PeisGuidePrintSlipDto） */
export interface PeisGuidePrintSource {
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  busNo?: string;
  packageName?: string;
  registeredAtText?: string;
  guideLines?: PeisGuideLine[];
  encounterId?: null | number | string;
  patientId?: null | number | string;
  scenarioCode?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapGuideLines(lines: PeisGuideLine[] | undefined): PeisGuideLine[] {
  if (!Array.isArray(lines)) {
    return [];
  }
  return lines.map((line) => ({
    seqText: str(line.seqText),
    deptName: str(line.deptName),
    itemName: str(line.itemName),
    locationText: str(line.locationText),
  }));
}

/** 后端体检指引单 slip → PrintData */
export function buildPeisGuidePrintData(source: PeisGuidePrintSource): PeisGuidePrintData {
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    busNo: str(source.busNo),
    packageName: str(source.packageName),
    registeredAtText: str(source.registeredAtText),
    guideLines: mapGuideLines(source.guideLines),
  };
}
