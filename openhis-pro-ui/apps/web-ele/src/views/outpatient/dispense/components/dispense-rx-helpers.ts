import type { DispensePrescriptionBlock, DispensePrescriptionItemRow } from './types';

/** 将处方块展开为表格行（一张方多行，rxId 归组；中药代煎费追加为合成行） */
export function flattenPrescriptionBlocks(
  blocks: DispensePrescriptionBlock[],
): DispensePrescriptionItemRow[] {
  const out: DispensePrescriptionItemRow[] = [];
  blocks.forEach((block, rxGroupIndex) => {
    // 中药且勾选代煎或已有代煎金额时，明细末尾追加代煎费展示行
    const showDecoction =
      block.rxType === 'herbal' &&
      (block.sufferingFlag === true || Boolean(block.decoctionChargeAmountLabel?.trim()));
    const medCount = block.items.length;
    const rowSpan = showDecoction ? medCount + 1 : medCount;
    block.items.forEach((line, i) => {
      const isLastMed = i === medCount - 1;
      out.push({
        ...line,
        lineKind: line.lineKind ?? 'medication',
        rxId: block.id,
        rxNo: block.rxNo,
        rxType: block.rxType,
        deptName: block.deptName,
        doctorName: block.doctorName,
        issuedAt: block.issuedAt,
        status: block.status,
        rxGroupIndex,
        rxRowSpan: rowSpan,
        isFirstInRx: i === 0,
        // 有代煎费行时，药材末行不再是组内最后一行
        isLastInRx: showDecoction ? false : isLastMed,
      });
    });
    if (showDecoction) {
      out.push(buildDecoctionFeeTableRow(block, rxGroupIndex, rowSpan));
    }
  });
  return out;
}

/** 组装中药代煎费合成明细行（不参与发药提交） */
function buildDecoctionFeeTableRow(
  block: DispensePrescriptionBlock,
  rxGroupIndex: number,
  rxRowSpan: number,
): DispensePrescriptionItemRow {
  const name = block.decoctionActivityName?.trim() || '代煎中药';
  return {
    id: `decoction-fee-${block.id}`,
    lineKind: 'decoctionFee',
    itemName: name,
    quantity: block.decoctionChargeQuantityLabel?.trim() || '—',
    spec: '—',
    traceCode: '—',
    batchNo: '—',
    amount: block.decoctionChargeAmountLabel?.trim() || '—',
    unitPrice: '—',
    pharmacy: '—',
    remainingStockText: '—',
    factory: '—',
    diagnosis: '—',
    freq: '—',
    dosageInstructionText: '—',
    rxId: block.id,
    rxNo: block.rxNo,
    rxType: block.rxType,
    deptName: block.deptName,
    doctorName: block.doctorName,
    issuedAt: block.issuedAt,
    status: block.status,
    rxGroupIndex,
    rxRowSpan,
    isFirstInRx: false,
    isLastInRx: true,
  };
}

/** 拼接该患者全部处方号，供左侧关键字命中处方号 */
export function collectPatientRxNosText(
  byPatient: Record<string, DispensePrescriptionBlock[]>,
  patientId: string,
): string {
  const blocks = byPatient[patientId] ?? [];
  return blocks.map((b) => b.rxNo).join(' ');
}
