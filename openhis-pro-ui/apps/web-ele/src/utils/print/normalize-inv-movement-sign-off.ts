/**
 * 耗材进货/出库/采购申请签批：打印前按 PrintData 开关改写种子 formatter。
 * 药品单据保持原栏；库模板已迁时片段不匹配则跳过。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
}

/** 种子模板里进货单四栏签批 */
const OLD_INBOUND_SIGN_OFF_LABELS =
  "if(t.indexOf('进货单')>=0)labels=['采购人','库管','验收人','审核'];";

/** 耗材进货单三栏；药品进货单仍四栏 */
const NEW_INBOUND_SIGN_OFF_LABELS =
  "if(t.indexOf('进货单')>=0){" +
  "if(String((printData&&printData.deviceInboundSignOff)||'').trim()==='1')" +
  "{labels=['负责人','验收入库','审核'];}" +
  "else{labels=['采购人','库管','验收人','审核'];}}";

/** 种子模板里出库单四栏（含审核） */
const OLD_STOCK_OUT_SIGN_OFF_LABELS =
  "else if(t.indexOf('出库单')>=0)labels=['负责人','领用人','库管','审核'];";

/** 耗材出库去掉审核；药品出库仍四栏 */
const NEW_STOCK_OUT_SIGN_OFF_LABELS =
  "else if(t.indexOf('出库单')>=0){" +
  "if(String((printData&&printData.deviceStockOutSignOff)||'').trim()==='1')" +
  "{labels=['负责人','领用人','库管'];}" +
  "else{labels=['负责人','领用人','库管','审核'];}}";

/** 把进货出库签批 formatter 改成耗材/药品分流（可就地改 prepare 后的深拷贝） */
export function applyDeviceInboundSignOffFormatter(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  return replaceFormatterSnippets(printJson, [
    [OLD_INBOUND_SIGN_OFF_LABELS, NEW_INBOUND_SIGN_OFF_LABELS],
  ]);
}

/** 种子单头：有值才输出制单人 */
const OLD_HEADER_ITEMS_LOOP =
  "for(var i=0;i<defs.length;i++){if(nz(defs[i][1]))items.push(defs[i][0]+'：'+String(defs[i][1]).trim());}";

/** 耗材采购申请：制单人后固定带审核栏（可空，供手签） */
const NEW_HEADER_ITEMS_LOOP =
  OLD_HEADER_ITEMS_LOOP +
  "if(String((d.devicePurchaseRequestSignOff)||'').trim()==='1')" +
  "{items.push('审核：'+String(d.approvedByName||'').trim());}";

/** 底部制单人/采购人：进货出库已隐藏 */
const OLD_PREPARED_FOOTER =
  "if(t.indexOf('进货单')>=0||t.indexOf('出库单')>=0)return '';" +
  "var v=value==null?'':String(value).trim();" +
  "if(String((printData&&printData.purchaseRequestSignOff)||'').trim()==='1')" +
  "return '采购人：'+v;";

/** 耗材采购申请：底部不再印制单人/采购人 */
const NEW_PREPARED_FOOTER =
  "if(t.indexOf('进货单')>=0||t.indexOf('出库单')>=0)return '';" +
  "if(String((printData&&printData.devicePurchaseRequestSignOff)||'').trim()==='1')" +
  "return '';" +
  "var v=value==null?'':String(value).trim();" +
  "if(String((printData&&printData.purchaseRequestSignOff)||'').trim()==='1')" +
  "return '采购人：'+v;";

/** 底部审核：进货出库已隐藏 */
const OLD_APPROVED_FOOTER =
  "if(t.indexOf('进货单')>=0||t.indexOf('出库单')>=0)return '';" +
  "if(String((printData&&printData.deviceStockOutSignOff)||'').trim()==='1')return '';";

/** 耗材采购申请：底部审核上移单头后不再印 */
const NEW_APPROVED_FOOTER =
  "if(t.indexOf('进货单')>=0||t.indexOf('出库单')>=0)return '';" +
  "if(String((printData&&printData.devicePurchaseRequestSignOff)||'').trim()==='1')" +
  "return '';" +
  "if(String((printData&&printData.deviceStockOutSignOff)||'').trim()==='1')return '';";

/** 耗材采购申请单：审核上移单头、底部去掉制单人/审核 */
export function applyDevicePurchaseRequestSignOffFormatter(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  return replaceFormatterSnippets(printJson, [
    [OLD_HEADER_ITEMS_LOOP, NEW_HEADER_ITEMS_LOOP],
    [OLD_PREPARED_FOOTER, NEW_PREPARED_FOOTER],
    [OLD_APPROVED_FOOTER, NEW_APPROVED_FOOTER],
  ]);
}

/** 打印前一次性套上耗材进货/出库/采购申请签批改写 */
export function applyInvMovementDeviceSignOffFormatters(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  applyDeviceInboundSignOffFormatter(printJson);
  replaceFormatterSnippets(printJson, [
    [OLD_STOCK_OUT_SIGN_OFF_LABELS, NEW_STOCK_OUT_SIGN_OFF_LABELS],
  ]);
  applyDevicePurchaseRequestSignOffFormatter(printJson);
  return printJson;
}

/** 在各元素 formatter 上按片段替换（已改过则跳过） */
function replaceFormatterSnippets(
  printJson: Record<string, unknown>,
  pairs: ReadonlyArray<readonly [string, string]>,
): Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    return printJson;
  }
  for (const rawPanel of panels) {
    if (!rawPanel || typeof rawPanel !== 'object') {
      continue;
    }
    const elements = (rawPanel as { printElements?: unknown }).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const raw of elements) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const options = (raw as PrintElementLike).options;
      if (!options || typeof options !== 'object') {
        continue;
      }
      const formatter = options.formatter;
      if (typeof formatter !== 'string') {
        continue;
      }
      let next = formatter;
      for (const [oldSnippet, newSnippet] of pairs) {
        if (next.includes(oldSnippet)) {
          next = next.replace(oldSnippet, newSnippet);
        }
      }
      options.formatter = next;
    }
  }
  return printJson;
}
