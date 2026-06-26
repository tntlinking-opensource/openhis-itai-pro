/** 表格行「库房×批号」选项的 JSON 编码，避免分隔符与业务字符冲突 */

export interface InventoryPickPayload {
  pharmacyOrganizationId: string;
  /** 空字符串表示未指定批号 */
  lotNumber: string;
}

export function encodeInventoryPick(p: {
  lotNumber?: null | string;
  pharmacyOrganizationId: string;
}): string {
  return JSON.stringify({
    pharmacyOrganizationId: String(p.pharmacyOrganizationId),
    lotNumber: p.lotNumber?.trim() ? p.lotNumber : '',
  } satisfies InventoryPickPayload);
}

export function decodeInventoryPick(raw: string): {
  lotNumber?: string;
  pharmacyOrganizationId: string;
} {
  const o = JSON.parse(raw) as InventoryPickPayload;
  const pharmacyOrganizationId = String(o.pharmacyOrganizationId);
  const lot = o.lotNumber?.trim();
  return { pharmacyOrganizationId, lotNumber: lot || undefined };
}
