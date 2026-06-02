import type { DispensePrescriptionBlock, DispensePrescriptionItemRow } from './types';

/** 将处方块展开为表格行（一张方多行，rxId 归组） */
export function flattenPrescriptionBlocks(
  blocks: DispensePrescriptionBlock[],
): DispensePrescriptionItemRow[] {
  const out: DispensePrescriptionItemRow[] = [];
  blocks.forEach((block, rxGroupIndex) => {
    const n = block.items.length;
    block.items.forEach((line, i) => {
      out.push({
        ...line,
        rxId: block.id,
        rxNo: block.rxNo,
        rxType: block.rxType,
        deptName: block.deptName,
        doctorName: block.doctorName,
        issuedAt: block.issuedAt,
        status: block.status,
        rxGroupIndex,
        rxRowSpan: n,
        isFirstInRx: i === 0,
        isLastInRx: i === n - 1,
      });
    });
  });
  return out;
}

/** 拼接该患者全部处方号，供左侧关键字命中处方号 */
export function collectPatientRxNosText(
  byPatient: Record<string, DispensePrescriptionBlock[]>,
  patientId: string,
): string {
  const blocks = byPatient[patientId] ?? [];
  return blocks.map((b) => b.rxNo).join(' ');
}
