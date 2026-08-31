/**
 * 住院患者用药单：按患者分块画表；同方频次/总量用 rowspan 合并。
 * 打印前覆盖 longText formatter，避免库模板仍按逐行空单元格渲染。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: { type?: string };
}

/** hiprint longText formatter：patientBlocks 循环，tcmGroupRowSpan 控制频次/总量合并 */
const ORAL_MED_SHEET_BLOCKS_FORMATTER = `function(title,value,options,printData){
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function cell(text,align){return '<td style="border:1px solid #000;padding:4pt 3pt;text-align:'+(align||'center')+';word-break:break-all;vertical-align:middle;">'+esc(text)+'</td>';}
function mergeCell(text,span){return '<td rowspan="'+span+'" style="border:1px solid #000;padding:4pt 3pt;text-align:center;word-break:break-all;vertical-align:middle;">'+esc(text)+'</td>';}
var blocks=Array.isArray(value)?value:((printData&&printData.patientBlocks)||[]);
if(!blocks.length&&printData){
  var topDrugs=printData.drugRows;
  if(Array.isArray(topDrugs)&&topDrugs.length){
    blocks=[{patientHeadText:printData.patientHeadText||'',patientSignLabel:printData.patientSignLabel||'患者签字：____________',drugRows:topDrugs}];
  }
}
var html='';
for(var i=0;i<blocks.length;i++){
  var b=blocks[i]||{};
  var head=esc(b.patientHeadText||'');
  var sign=esc(b.patientSignLabel||'患者签字：____________');
  var drugs=Array.isArray(b.drugRows)?b.drugRows:[];
  html+='<div style="margin:0 0 14pt 0;">';
  html+='<div style="font-size:10pt;font-weight:bold;line-height:1.5;padding:2pt 0 6pt 0;">'+head+'</div>';
  html+='<table style="width:100%;border-collapse:collapse;font-size:9pt;table-layout:fixed;">';
  html+='<thead><tr>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:24%;text-align:left;">药品名称</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:12%;text-align:left;">规格</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:12%;text-align:center;">剂量</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:12%;text-align:center;">用法</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:14%;text-align:center;">频次</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:12%;text-align:center;">总量</th>';
  html+='<th style="border:1px solid #000;background:#f0f0f0;padding:4pt 3pt;width:14%;text-align:left;">备注</th>';
  html+='</tr></thead><tbody>';
  for(var j=0;j<drugs.length;j++){
    var d=drugs[j]||{};
    var span=Number(d.tcmGroupRowSpan);
    if(!isFinite(span)||span<0){span=1;}
    html+='<tr>';
    html+=cell(d.drugName,'left');
    html+=cell(d.specText,'left');
    html+=cell(d.doseText,'center');
    html+=cell(d.usageText,'center');
    if(span>1){
      html+=mergeCell(d.freqText,span);
      html+=mergeCell(d.totalQuantityText,span);
    }else if(span!==0){
      html+=cell(d.freqText,'center');
      html+=cell(d.totalQuantityText,'center');
    }
    html+=cell(d.remarkText,'left');
    html+='</tr>';
  }
  html+='</tbody></table>';
  html+='<div style="font-size:10pt;text-align:right;padding:8pt 2pt 0 0;">'+sign+'</div>';
  html+='</div>';
}
return html;
}`;

/** 是否用药单明细 longText（库模板可能绑 patientBlocks 或旧版 drugRows） */
function isOralMedSheetBodyElement(element: PrintElementLike): boolean {
  if (element.printElementType?.type !== 'longText') {
    return false;
  }
  const field = String(element.options?.field ?? '').trim();
  return field === 'patientBlocks' || field === 'drugRows';
}

/**
 * 用药单打印前：明细区改绑 patientBlocks，并用 rowspan 合并同方频次/总量。
 */
export function normalizeOralMedSheetPrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
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
      if (!isOralMedSheetBodyElement(element)) {
        continue;
      }
      if (!element.options || typeof element.options !== 'object') {
        element.options = {};
      }
      element.options.field = 'patientBlocks';
      element.options.formatter = ORAL_MED_SHEET_BLOCKS_FORMATTER;
    }
  }
  return printJson;
}
