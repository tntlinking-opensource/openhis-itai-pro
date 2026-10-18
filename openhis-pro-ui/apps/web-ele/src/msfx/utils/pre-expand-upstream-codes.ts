/** Opensource stub — no mashangfangxin expand. */
import type { MsfxUpstreamBillRowDto } from '#/api/msfx/types';

export type MsfxPreExpandContext = {
  busNo: string;
  supplierEntId: string;
  supplierEntName?: string;
  supplierRefEntId?: string;
  billTime?: string;
  expandSessionKey?: string;
  onProgress?: (text: string) => void;
};

export function cloneMsfxUpstreamDrugRow(
  drug: MsfxUpstreamBillRowDto,
): MsfxUpstreamBillRowDto {
  return { ...drug };
}

export function applyMsfxExpandedCodeItemsToDrug(
  _drug: MsfxUpstreamBillRowDto,
  _items: unknown[],
): void {}

export function msfxUpstreamDrugNeedsPreExpand(
  _drug: MsfxUpstreamBillRowDto,
): boolean {
  return false;
}

export async function expandOneMsfxUpstreamDrugRow(
  drug: MsfxUpstreamBillRowDto,
  _ctx: MsfxPreExpandContext,
): Promise<MsfxUpstreamBillRowDto> {
  return drug;
}

export async function preExpandMsfxUpstreamDrugRows(
  drugs: MsfxUpstreamBillRowDto[],
  _ctx: MsfxPreExpandContext,
): Promise<{ drugs: MsfxUpstreamBillRowDto[]; expanded: boolean }> {
  return { drugs, expanded: false };
}
