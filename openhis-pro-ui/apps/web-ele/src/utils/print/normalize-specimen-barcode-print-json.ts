import { PRINT_JSON_REWRITE_ENABLED } from '#/utils/print/print-json-rewrite.config';

/**
 * 采血贴打印前校正。仅国基打开 PRINT_JSON_REWRITE_ENABLED 后才改 JSON；红旗沿用库模板。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: { type?: string };
}

/**
 * 住院采血贴 longText formatter：条码用 printData.barcode（SR.busNo）。
 * 保持模块宽度，禁止 none 拉伸压扁；JsBarcode 留 quiet zone。
 */
const IP_SPECIMEN_BARCODE_FORMATTER = `function(title,value,options,printData){var d=printData||{};function e(k){var x=d[k];return x==null?'':String(x);}function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}function barcodeSvg(text){var t=String(text||'').trim();if(!t)return '';var JB=typeof JsBarcode==='function'?JsBarcode:(typeof window!=='undefined'?window.JsBarcode:undefined);if(typeof JB==='function'&&typeof document!=='undefined'){try{var svg=document.createElementNS('http://www.w3.org/2000/svg','svg');JB(svg,t,{format:'CODE128',displayValue:false,fontSize:8,width:1.4,height:28,margin:2});svg.setAttribute('width','100%');svg.setAttribute('height','10mm');svg.setAttribute('preserveAspectRatio','xMidYMid meet');return svg.outerHTML;}catch(err){}}var C='212222,222122,222221,121223,121322,131222,122213,122312,132212,221213,221312,231212,112232,122132,122231,113222,123122,123221,223211,221132,221231,213212,223112,312131,311222,321122,321221,312212,322112,322211,212123,212321,232121,111323,131123,131321,112313,132113,132311,211313,231113,231311,112133,112331,132131,113123,113321,133121,313121,211331,231131,213113,213311,213131,311123,311321,331121,312113,312311,332111,314111,221411,431111,111224,111422,121124,121421,141122,141221,112214,112412,122114,122411,142112,142211,241211,221114,413111,241112,134111,111242,121142,121241,114212,124112,124211,411212,421112,421211,212141,214121,412121,111143,111341,131141,114113,114311,411113,411311,113141,114131,311141,411131,211412,211214,211232,2331112'.split(',');var codes=[104];for(var i=0;i<t.length;i++){var v=t.charCodeAt(i)-32;if(v<0||v>94)v=16;codes.push(v);}var sum=104;for(var j=1;j<codes.length;j++)sum+=codes[j]*j;codes.push(sum%103);codes.push(106);var x=10,rects='',h=28;for(var k=0;k<codes.length;k++){var p=C[codes[k]]||'';for(var n=0;n<p.length;n++){var w=p.charCodeAt(n)-48;if(n%2===0)rects+='<rect x="'+x+'" y="0" width="'+w+'" height="'+h+'"/>';x+=w;}}x+=10;return '<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="10mm" viewBox="0 0 '+x+' '+h+'" preserveAspectRatio="xMidYMid meet">'+rects+'</svg>';}var code=e('barcode');return '<div style="width:100%;height:100%;overflow:visible;position:relative;"><div style="width:48mm;height:26mm;transform:rotate(90deg) translate(0,-100%);transform-origin:top left;font-family:SimHei,黑体,SimSun,宋体,sans-serif;color:#000;box-sizing:border-box;padding:1mm 1.2mm;display:flex;flex-direction:column;"><div style="font-size:9pt;font-weight:700;white-space:nowrap;overflow:hidden;">'+esc(e('bedNo'))+'　'+esc(e('patientName'))+'　'+esc(e('genderEnumText'))+'　'+esc(e('ageText'))+'</div><div style="font-size:8pt;font-weight:700;margin-top:0.8mm;white-space:nowrap;overflow:hidden;">'+esc(e('itemName'))+'</div><div style="flex:1;min-height:12mm;display:flex;flex-direction:column;justify-content:flex-end;">'+barcodeSvg(code)+'<div style="font-size:7pt;text-align:center;margin-top:0.3mm;white-space:nowrap;overflow:hidden;">'+esc(code)+'</div></div></div></div>';}`;

/** 住院采血贴 body（field=labelBody） */
function isIpSpecimenBarcodeBodyElement(element: PrintElementLike): boolean {
  if (element.printElementType?.type !== 'longText') {
    return false;
  }
  return String(element.options?.field ?? '').trim() === 'labelBody';
}

/** 门诊采血贴原生 barcode 元素 */
function isOpSpecimenBarcodeElement(element: PrintElementLike): boolean {
  const options = element.options;
  if (!options) {
    return false;
  }
  if (String(options.field ?? '').trim() !== 'barcode') {
    return false;
  }
  return String(options.textType ?? '').trim() === 'barcode';
}

/**
 * 采血贴打印前：住院覆盖 formatter（禁止条码被压扁）；门诊开启 barAutoWidth。
 */
export function normalizeSpecimenBarcodePrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  if (PRINT_JSON_REWRITE_ENABLED !== true) {
    return printJson;
  }
  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    return printJson;
  }
  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as { printElements?: unknown }).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const raw of elements) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const element = raw as PrintElementLike;
      if (!element.options) {
        element.options = {};
      }
      // 住院：整段替换 formatter，字母 busNo 条码保持模块宽度可扫
      if (isIpSpecimenBarcodeBodyElement(element)) {
        element.options.formatter = IP_SPECIMEN_BARCODE_FORMATTER;
        continue;
      }
      // 门诊：字母/连字符 busNo 须自动撑满，禁止固定 barWidth 裁切
      if (isOpSpecimenBarcodeElement(element)) {
        element.options.barAutoWidth = true;
        element.options.barcodeMode = 'CODE128';
      }
    }
  }
  return printJson;
}
