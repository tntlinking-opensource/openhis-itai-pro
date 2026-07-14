import type {
  OutpatientFeeDetailEncounterBlockDto,
  OutpatientFeeDetailLineDto,
} from '#/api/outpatient/report/fee-detail';

export type FeeDetailTableRow = OutpatientFeeDetailLineDto & {
  busNo?: string;
  encounterBlockIndex?: number;
  encounterBlockKey?: string;
  encounterDate?: string;
  encounterLineCount?: number;
  isFirstLineInEncounter?: boolean;
  patientName?: string;
  registrationDepartmentName?: string;
  rowKey: string;
  rowType: 'line' | 'subtotal';
};

/** 将就诊块平铺为 TableLayout 行（块末插入就诊小计行）。 */
export function flattenEncounterBlocksToTableRows(
  blocks: OutpatientFeeDetailEncounterBlockDto[],
): FeeDetailTableRow[] {
  const rows: FeeDetailTableRow[] = [];
  // 按块顺序展开明细行，块间插入小计行便于列表内分组阅读。
  for (const [blockIndex, block] of blocks.entries()) {
    const encounterKey = String(block.encounterId ?? block.busNo ?? rows.length);
    const lines = block.lines ?? [];
    const lineCount = lines.length;
    lines.forEach((line, lineIndex) => {
      rows.push({
        ...line,
        rowType: 'line',
        rowKey: `line-${encounterKey}-${String(line.chargeItemId ?? 'na')}-${String(line.paymentId ?? line.tradeDirection ?? line.billDate ?? lineIndex)}`,
        encounterBlockKey: encounterKey,
        encounterBlockIndex: blockIndex,
        encounterLineCount: lineCount,
        isFirstLineInEncounter: lineIndex === 0,
        patientName: block.patientName,
        busNo: block.busNo,
        registrationDepartmentName: block.registrationDepartmentName,
        encounterDate: block.encounterDate,
      });
    });
    const subtotal = block.subtotal;
    if (lines.length > 0 && subtotal) {
      rows.push({
        rowType: 'subtotal',
        rowKey: `subtotal-${encounterKey}`,
        encounterBlockKey: encounterKey,
        encounterBlockIndex: blockIndex,
        patientName: '就诊小计',
        baseAmount: subtotal.baseAmountTotal,
        discountAmount: subtotal.discountAmountTotal,
        receivableAmount: subtotal.receivableAmountTotal,
        discountedAmount: subtotal.discountedAmountTotal,
        lineAmount: subtotal.lineAmountTotal,
        actualReceivedAmount: subtotal.actualReceivedAmountTotal,
        insuranceAmount: subtotal.insuranceAmountTotal,
        selfPayAmount: subtotal.selfPayAmountTotal,
      });
    }
  }
  return rows;
}
