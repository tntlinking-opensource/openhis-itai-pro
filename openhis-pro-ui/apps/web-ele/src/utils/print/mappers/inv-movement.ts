import {
  buildInvDocumentTitle,
  invPrintAmountText,
  invPrintExpiryDateText,
  invPrintPurchaseUnitPriceText,
  invPrintQtyUnitText,
  invPrintStr,
} from './inv-common';

/** 计价流转单明细行（与 inv-movement-print 模板 lineRows 列一致） */
export interface InvMovementPrintLineRow {
  seqNo: string;
  /** 项目编码（药品/耗材 productBusNo） */
  productBusNo: string;
  productName: string;
  specText: string;
  manufacturerText: string;
  qtyUnitText: string;
  unitPriceText: string;
  amountText: string;
  lotNumber: string;
  /** 有效期（YYYY-MM-DD），不含生产日 */
  expiryText: string;
}

/** 计价流转单 PrintData */
export interface InvMovementPrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  applyTimeText: string;
  itemTypeText: string;
  sourceLocationText: string;
  supplierText: string;
  linkDocText: string;
  destLocationText: string;
  invoiceNoText: string;
  lineRows: InvMovementPrintLineRow[];
  totalAmountText: string;
  /** 单据创建人（底部默认印「负责人」，采购申请单印「采购人」；非当前打印人） */
  preparedByName: string;
  approvedByName: string;
  /**
   * 采购申请单签批栏开关：`'1'` 时底部印「采购人」，并展示「经办人/科室责任人/业务主管院长/院长」；
   * 其它单据传 `''`，底部印「负责人」。
   */
  purchaseRequestSignOff: string;
  /**
   * 耗材出库签批栏开关：`'1'` 时底部印「负责人/领用人/库管」，去掉「审核」；
   * 药品出库及其它单据传 `''`。
   */
  deviceStockOutSignOff: string;
  /**
   * 耗材进货单签批栏开关：`'1'` 时底部印「负责人/验收入库/审核」；
   * 药品进货单及其它单据传 `''`，仍印「采购人/库管/验收人/审核」。
   */
  deviceInboundSignOff: string;
  /**
   * 耗材采购申请单签批：`'1'` 时底部去掉制单人/审核，审核改到单头制单人后；
   * 药品采购申请单及其它单据传 `''`。
   */
  devicePurchaseRequestSignOff: string;
}

/** 明细行入参（展示字段，由页面组装） */
export interface InvMovementPrintLineSource {
  /** 项目编码（药品/耗材 productBusNo） */
  productBusNo?: string;
  productName?: string;
  specText?: string;
  manufacturerText?: string;
  qty?: null | number | string;
  unitLabel?: string;
  /** 已拼好的数量单位，优先于 qty+unitLabel */
  qtyUnitText?: string;
  unitPrice?: null | number | string;
  amount?: null | number | string;
  lotNumber?: string;
  /** @deprecated 打印不再展示生产日；仅兼容旧入参 */
  productionDate?: string;
  expiryDate?: string;
  /** 已拼好的有效期；若含 `生产~效期` 则 mapper 只取效期段 */
  expiryText?: string;
}

/** 计价流转单 mapper 入参 */
export interface InvMovementPrintSource {
  hospitalName?: string;
  /** 完整标题；缺省时用 locationName+docKindLabel 拼 */
  documentTitle?: string;
  locationName?: string;
  docKindLabel?: string;
  docNo?: string;
  applyTimeText?: string;
  itemTypeText?: string;
  sourceLocationText?: string;
  supplierText?: string;
  linkDocText?: string;
  destLocationText?: string;
  invoiceNoText?: string;
  lineRows?: InvMovementPrintLineSource[];
  totalAmountText?: string;
  preparedByName?: string;
  approvedByName?: string;
  /** 采购申请单：true/`'1'` 时底部印「采购人」并展示申请签批栏；其它单据勿传 */
  purchaseRequestSignOff?: boolean | string;
  /** 耗材出库签批栏：true/`'1'` 才展示；其它单据勿传 */
  deviceStockOutSignOff?: boolean | string;
  /** 耗材进货单签批栏：true/`'1'` 才改底部栏；药品进货单勿传 */
  deviceInboundSignOff?: boolean | string;
  /** 耗材采购申请单：true/`'1'` 时审核上移单头；药品申请单勿传 */
  devicePurchaseRequestSignOff?: boolean | string;
}

function mapLine(row: InvMovementPrintLineSource, index: number): InvMovementPrintLineRow {
  const qtyUnitText = invPrintStr(row.qtyUnitText) || invPrintQtyUnitText(row.qty, row.unitLabel);
  const expiryText = invPrintExpiryDateText(row.expiryDate, row.expiryText);
  return {
    seqNo: String(index + 1),
    productBusNo: invPrintStr(row.productBusNo),
    productName: invPrintStr(row.productName),
    specText: invPrintStr(row.specText),
    manufacturerText: invPrintStr(row.manufacturerText),
    qtyUnitText,
    unitPriceText: invPrintPurchaseUnitPriceText(row.unitPrice),
    amountText: invPrintAmountText(row.amount),
    lotNumber: invPrintStr(row.lotNumber),
    expiryText,
  };
}

/** 库存计价流转单 → PrintData */
export function buildInvMovementPrintData(source: InvMovementPrintSource): InvMovementPrintData {
  const lineRows = (source.lineRows ?? []).map((row, index) => mapLine(row, index));
  let totalAmountText = invPrintStr(source.totalAmountText);
  if (!totalAmountText && lineRows.length > 0) {
    let sum = 0;
    let has = false;
    for (const row of lineRows) {
      const n = Number.parseFloat(row.amountText);
      if (Number.isFinite(n)) {
        sum += n;
        has = true;
      }
    }
    if (has) {
      totalAmountText = sum.toFixed(2);
    }
  }
  return {
    hospitalName: invPrintStr(source.hospitalName),
    documentTitle:
      invPrintStr(source.documentTitle) ||
      buildInvDocumentTitle(source.locationName ?? '', source.docKindLabel ?? '流转单'),
    docNo: invPrintStr(source.docNo),
    applyTimeText: invPrintStr(source.applyTimeText),
    itemTypeText: invPrintStr(source.itemTypeText),
    sourceLocationText: invPrintStr(source.sourceLocationText),
    supplierText: invPrintStr(source.supplierText),
    linkDocText: invPrintStr(source.linkDocText),
    destLocationText: invPrintStr(source.destLocationText),
    invoiceNoText: invPrintStr(source.invoiceNoText),
    lineRows,
    totalAmountText,
    preparedByName: invPrintStr(source.preparedByName),
    approvedByName: invPrintStr(source.approvedByName),
    purchaseRequestSignOff:
      source.purchaseRequestSignOff === true || invPrintStr(source.purchaseRequestSignOff) === '1'
        ? '1'
        : '',
    deviceStockOutSignOff:
      source.deviceStockOutSignOff === true || invPrintStr(source.deviceStockOutSignOff) === '1'
        ? '1'
        : '',
    deviceInboundSignOff:
      source.deviceInboundSignOff === true || invPrintStr(source.deviceInboundSignOff) === '1'
        ? '1'
        : '',
    devicePurchaseRequestSignOff:
      source.devicePurchaseRequestSignOff === true ||
      invPrintStr(source.devicePurchaseRequestSignOff) === '1'
        ? '1'
        : '',
  };
}
