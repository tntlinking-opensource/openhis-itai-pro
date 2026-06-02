/**
 * 进货单（IF-WHS-IN-11）选品下拉：列与 {@link InboundProductDefinitionItemDto} 字段 1:1。
 * 进货选品不汇总库存，{@code availableQuantity} 恒为 null，下拉不展示库存/批准文号列。
 */

import {
  inventoryPickStringField,
  unitBandNameByKind,
} from '#/utils/inventory/inventory-product-pick-list';

export const INBOUND_RECEIPT_PRODUCT_PICK_TABLE_COLUMNS = [
  { prop: 'productBusNo', label: '项目编码', width: 140 },
  { prop: 'productName', label: '项目名称', width: 180 },
  { prop: 'manufacturer', label: '厂家/产地', width: 180 },
  { prop: 'purchasePrice', label: '进货价', width: 96 },
  { prop: 'price', label: '零售价', width: 96 },
  { prop: 'packageUnitName', label: '包装单位', width: 96 },
  { prop: 'splitUnitName', label: '最小单位', width: 96 },
  { prop: 'totalVolume', label: '规格', width: 120 },
  { prop: 'categoryCodeDictText', label: '项目种类', width: 108 },
] as const;

/** 进货选品列表行：SelectTable 的 value-key 为 productDefinitionId（写入明细 itemId）。 */
export function mapInboundReceiptProductPickListRow(
  r: Record<string, unknown>,
): Record<string, unknown> {
  const defId = r.productDefinitionId;
  const id = defId === undefined || defId === null ? '' : String(defId);

  return {
    ...r,
    id,
    packageUnitName: unitBandNameByKind(r.unitBands, 'PACKAGE'),
    splitUnitName: unitBandNameByKind(r.unitBands, 'SPLIT'),
  };
}

/** 进货选品格展示：productName + productDefinitionId。 */
export function inboundReceiptPickMedicineCell(
  pick: Record<string, unknown>,
): { id?: number | string; name: string; busNo?: string } | undefined {
  const name = inventoryPickStringField(pick, 'productName');
  const idRaw = pick.productDefinitionId;
  const id = idRaw === undefined || idRaw === null ? undefined : (idRaw as number | string);
  const busNo = inventoryPickStringField(pick, 'productBusNo');
  if (!name && id === undefined) return undefined;
  return { id, name, ...(busNo ? { busNo } : {}) };
}
