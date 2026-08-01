import type { FeeDetailEncounterBlockDto, FeeDetailLineDto } from '#/api/report/fee-detail';

export type FeeDetailTableRow = FeeDetailLineDto & {
  busNo?: string;
  encounterBlockIndex?: number;
  encounterBlockKey?: string;
  encounterDate?: string;
  feeNatureDisplay?: string;
  medTypeDisplay?: string;
  encounterLineCount?: number;
  isFirstLineInEncounter?: boolean;
  patientName?: string;
  registrationDepartmentName?: string;
  rowKey: string;
  rowType: 'line' | 'subtotal';
};

/** 将就诊块平铺为 TableLayout 行（块末插入就诊小计行）。 */
export function flattenEncounterBlocksToTableRows(
  blocks: FeeDetailEncounterBlockDto[],
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
        feeNatureDisplay: block.feeNatureDisplay,
        medTypeDisplay: block.medTypeDisplay,
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
        purchaseAmount: subtotal.purchaseAmountTotal,
        saleAmount: subtotal.saleAmountTotal,
        actualReceivedAmount: subtotal.actualReceivedAmountTotal,
      });
    }
  }
  return rows;
}
