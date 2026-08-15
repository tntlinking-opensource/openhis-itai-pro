/** 输液巡视卡明细行（与 ip-patrolCard-print 模板 patrolRows 列一致） */
export interface InpatientPatrolCardRow {
  groupNo: string;
  drugName: string;
  specText: string;
  doseText: string;
  /** 滴速：回显医嘱 speedDisplay（组内共享）；无则留空可手写 */
  dripRate: string;
  /** 巡视时间：打印留空，护士手写 */
  timeText: string;
  /** 签名：打印留空，护士手写 */
  signText: string;
}

/** 输液巡视卡 PrintData（与 ip-patrolCard-print 模板 field 一致） */
export interface InpatientPatrolCardPrintData {
  hospitalName: string;
  documentTitle: string;
  wardName: string;
  bedNo: string;
  patientName: string;
  ageText: string;
  executeDateText: string;
  totalGroupsText: string;
  familySignLabel: string;
  patrolRows: InpatientPatrolCardRow[];
}

/** 输液巡视卡 mapper 入参 */
export interface InpatientPatrolCardPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  wardName?: string;
  bedNo?: string;
  patientName?: string;
  ageText?: string;
  executeDateText?: string;
  totalGroupsText?: string;
  familySignLabel?: string;
  patrolRows?: InpatientPatrolCardRow[];
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapPatrolRow(row: InpatientPatrolCardRow): InpatientPatrolCardRow {
  return {
    groupNo: str(row.groupNo),
    drugName: str(row.drugName),
    specText: str(row.specText),
    doseText: str(row.doseText),
    dripRate: str(row.dripRate),
    timeText: str(row.timeText),
    signText: str(row.signText),
  };
}

/** 输液巡视卡 → PrintData */
export function buildInpatientPatrolCardPrintData(
  source: InpatientPatrolCardPrintSource,
): InpatientPatrolCardPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '输液巡视卡',
    wardName: str(source.wardName),
    bedNo: str(source.bedNo),
    patientName: str(source.patientName),
    ageText: str(source.ageText),
    executeDateText: str(source.executeDateText),
    totalGroupsText: str(source.totalGroupsText),
    familySignLabel: str(source.familySignLabel) || '患者家属签字：____________',
    patrolRows: (source.patrolRows ?? []).map((row) => mapPatrolRow(row)),
  };
}
