/** Opensource stub. */
import type { MsfxUpstreamBillRowDto } from '#/api/msfx/types';

export type MsfxGenerateConfirmLine = {
  drug: MsfxUpstreamBillRowDto;
  product: Record<string, unknown>;
  purchaseQty?: number;
  selectedBandKind: string;
};

export function resolveMsfxWriteableTraceCodes(
  _drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): string[] {
  return [];
}

export function countMsfxWriteableTraceCodes(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): number {
  return resolveMsfxWriteableTraceCodes(drug).length;
}

export function isMsfxWriteablePackLevelCodes(
  _drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): boolean {
  return false;
}

export function isMsfxQtyCodeMismatch(_args: unknown): boolean {
  return false;
}

export function resolveMsfxExpectedTraceCount(_args: unknown): number | undefined {
  return undefined;
}

export function resolveMsfxDisplayQtyAsSplit(_args: unknown): number | undefined {
  return undefined;
}

export function parseMsfxPkgToSplitRatioFromSpec(_spec?: string | null): number | undefined {
  return undefined;
}
