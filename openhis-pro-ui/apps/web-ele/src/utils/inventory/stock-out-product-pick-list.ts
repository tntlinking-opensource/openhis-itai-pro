/**
 * 出库单（IF-WHS-SO-11）选品下拉：列与接口批号级选品字段 1:1。
 * 批号级一行（`inventoryItemId`）；**须含 `lotNumber`（批号）**；不展示零售价、最小单位、批准文号。
 */

export const STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS = [
  { prop: 'productBusNo', label: '项目编码', width: 140 },
  { prop: 'productName', label: '项目名称', width: 180 },
  { prop: 'manufacturer', label: '厂家/产地', width: 180 },
  { prop: 'purchaseUnitPrice', label: '进货价', width: 96 },
  { prop: 'packageUnitName', label: '包装单位', width: 96 },
  { prop: 'stockAvailableDisplay', label: '库存数量', width: 112 },
  { prop: 'lotNumber', label: '批号', width: 120 },
  { prop: 'specification', label: '规格', width: 120 },
] as const;
