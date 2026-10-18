import { IP_INFUSION_LABEL_REWRITE_COMPACT } from '#/utils/print/ip-infusion-label-layout.config';
import { PRINT_JSON_REWRITE_ENABLED } from '#/utils/print/print-json-rewrite.config';

/**
 * 住院输液贴：打印前改成固定高度的紧凑贴面。
 * 仅国基 override 打开 IP_INFUSION_LABEL_REWRITE_COMPACT 后才会改 JSON。
 * 红旗沿用库模板（姓名/年龄/入院时间，表头无「剂量」列）。
 */

/** hiprint 坐标：1mm = 72/25.4 pt */
const PT_PER_MM = 72 / 25.4;

interface PrintPanelLike {
  width?: number;
  height?: number;
  paperFooter?: number;
  paperHeader?: number;
  paperNumberDisabled?: boolean;
  printElements?: unknown[];
}

/**
 * 药品表 formatter。
 * 按贴面剩余高度选字号，保证最多按两行药名排，行高固定，避免 hiprint 表格把第三支药打到下一张。
 */
const INFUSION_LABEL_MEDS_FORMATTER = `function(title,value,options,printData){
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
var rows=Array.isArray(value)?value:[];
if(!rows.length&&printData&&Array.isArray(printData.medicationLines)){rows=printData.medicationLines;}
var clean=[];
for(var i=0;i<rows.length;i++){
  var row=rows[i]||{};
  var name=String(row.drugName||'').trim();
  if(!name&&!String(row.specText||'').trim()&&!String(row.doseText||'').trim()){continue;}
  clean.push(row);
}
if(!clean.length){return '';}
var boxH=Number(options&&options.height)||80;
var boxW=Number(options&&options.width)||160;
var nameW=Math.max(36, boxW*0.38-2);
function nameLines(name,font){
  var width=0;
  var lines=1;
  var raw=String(name||'');
  for(var c=0;c<raw.length;c++){
    var cw=raw.charCodeAt(c)>255?font:font*0.55;
    if(width>0&&width+cw>nameW){lines++;width=cw;}
    else{width+=cw;}
  }
  return lines;
}
function planOf(font,maxNameLines){
  var line=Math.round(font*1.15*10)/10;
  var header=line+1;
  var row=line*maxNameLines+1;
  return {font:font,line:line,header:header,row:row,total:header+clean.length*row,maxNameLines:maxNameLines};
}
var font=9;
var maxNameLines=2;
var plan=planOf(font,maxNameLines);
var guard=0;
while(guard<12){
  var longest=1;
  for(var k=0;k<clean.length;k++){
    var need=nameLines(clean[k].drugName,font);
    if(need>longest){longest=need;}
  }
  var useLines=longest>2?2:longest;
  plan=planOf(font,useLines);
  if(plan.total<=boxH&&longest<=2){break;}
  if(font<=6){plan=planOf(6,2);break;}
  font-=0.5;
  guard++;
}
var line=plan.line;
var html='<div style="width:100%;height:100%;overflow:hidden;font-family:SimSun,宋体,sans-serif;color:#000;font-size:'+plan.font+'pt;line-height:'+line+'pt;">';
html+='<table style="width:100%;border-collapse:collapse;table-layout:fixed;">';
html+='<colgroup><col style="width:38%"><col style="width:22%"><col style="width:14%"><col style="width:12%"><col style="width:14%"></colgroup>';
html+='<thead><tr style="height:'+plan.header+'pt;">';
function th(text,align){return '<th style="border-bottom:0.6pt solid #000;padding:0 1pt;font-weight:normal;text-align:'+align+';white-space:nowrap;overflow:hidden;">'+text+'</th>';}
html+=th('药品名称','left');
html+=th('规格','left');
html+=th('剂量','center');
html+=th('总量','center');
html+=th('用法','center');
html+='</tr></thead><tbody style="font-family:SimHei,黑体,sans-serif;font-weight:bold;">';
for(var r=0;r<clean.length;r++){
  var item=clean[r]||{};
  html+='<tr style="height:'+plan.row+'pt;">';
  html+='<td style="padding:0 1pt;vertical-align:middle;overflow:hidden;font-family:SimHei,黑体,sans-serif;font-weight:bold;"><div style="max-height:'+(line*plan.maxNameLines)+'pt;overflow:hidden;word-break:break-all;line-height:'+line+'pt;font-family:SimHei,黑体,sans-serif;font-weight:bold;">'+esc(item.drugName)+'</div></td>';
  html+='<td style="padding:0 1pt;vertical-align:middle;white-space:nowrap;overflow:hidden;text-align:left;font-family:SimHei,黑体,sans-serif;font-weight:bold;">'+esc(item.specText)+'</td>';
  html+='<td style="padding:0 1pt;vertical-align:middle;white-space:nowrap;overflow:hidden;text-align:center;font-family:SimHei,黑体,sans-serif;font-weight:bold;">'+esc(item.doseText)+'</td>';
  html+='<td style="padding:0 1pt;vertical-align:middle;white-space:nowrap;overflow:hidden;text-align:center;font-family:SimHei,黑体,sans-serif;font-weight:bold;">'+esc(item.totalQuantityText)+'</td>';
  html+='<td style="padding:0 1pt;vertical-align:middle;white-space:nowrap;overflow:hidden;text-align:center;font-family:SimHei,黑体,sans-serif;font-weight:bold;">'+esc(item.usageText)+'</td>';
  html+='</tr>';
}
html+='</tbody></table></div>';
return html;
}`;

function textElement(
  field: string,
  title: string,
  top: number,
  left: number,
  width: number,
  height: number,
  fontSize: number,
): Record<string, unknown> {
  return {
    options: {
      top,
      left,
      field,
      title,
      width,
      height,
      fontSize,
      textAlign: 'left',
      fontWeight: 'normal',
    },
    printElementType: { type: 'text', title },
  };
}

function hlineElement(top: number, left: number, width: number): Record<string, unknown> {
  return {
    options: {
      top,
      left,
      width,
      height: 1,
      borderWidth: 0.6,
    },
    printElementType: { type: 'hline', title: '横线' },
  };
}

function roundPt(value: number): number {
  return Math.round(value * 10) / 10;
}

/** 把一张住院输液贴面板收成：姓名/性别/床位、频次、定高药品表、滴速、横线、执行人/时间 */
function applyCompactInfusionLabelPanel(panel: PrintPanelLike): void {
  const widthMm = Number(panel.width);
  const heightMm = Number(panel.height);
  const pageW = (Number.isFinite(widthMm) && widthMm > 0 ? widthMm : 80) * PT_PER_MM;
  const pageH = (Number.isFinite(heightMm) && heightMm > 0 ? heightMm : 60) * PT_PER_MM;
  const left = 3;
  const contentW = roundPt(Math.max(80, pageW - left * 2));
  const narrow = pageW < 200;
  const headFont = narrow ? 8 : 9;
  const headH = narrow ? 11 : 12;
  const footFont = narrow ? 7.5 : 8.5;
  const footH = narrow ? 11 : 12;

  let y = 2;
  const nameFont = headFont + 1;
  const nameH = headH + 1;
  const nameW = roundPt(contentW * 0.4);
  const genderW = roundPt(contentW * 0.22);
  const bedW = roundPt(contentW - nameW - genderW);
  const elements: Record<string, unknown>[] = [
    textElement('patientName', '姓名', y, left, nameW, nameH, nameFont),
    textElement('genderEnumText', '性别', y, left + nameW, genderW, nameH, headFont),
    textElement('wardBedText', '床位', y, left + nameW + genderW, bedW, nameH, headFont),
  ];
  y = roundPt(y + nameH);
  elements.push(textElement('freqText', '频次', y, left, contentW, headH, headFont));
  y = roundPt(y + headH + 1);
  elements.push(hlineElement(y, left, contentW));
  y = roundPt(y + 2);

  const execTop = roundPt(pageH - footH - 1);
  const footLineTop = roundPt(execTop - 2);
  // 滴速放在下边横线之上，和药品同一侧；执行人、执行时间留在横线下面
  const speedTop = roundPt(footLineTop - footH - 1);
  const medH = roundPt(Math.max(36, speedTop - y - 1));
  elements.push({
    options: {
      top: y,
      left,
      field: 'medicationLines',
      title: '药品',
      width: contentW,
      height: medH,
      fontSize: headFont,
      hideTitle: true,
      fontFamily: 'SimSun',
      formatter: INFUSION_LABEL_MEDS_FORMATTER,
    },
    printElementType: { type: 'longText', title: '药品' },
  });
  elements.push(textElement('speedCode', '滴速', speedTop, left, contentW, footH, footFont));
  elements.push(hlineElement(footLineTop, left, contentW));
  const execW = roundPt(contentW * 0.34);
  elements.push(
    textElement('executorName', '执行人', execTop, left, execW, footH, footFont),
    textElement(
      'executeTimeText',
      '执行时间',
      execTop,
      left + execW,
      roundPt(contentW - execW),
      footH,
      footFont,
    ),
  );

  panel.printElements = elements;
  panel.paperHeader = 0;
  panel.paperFooter = roundPt(pageH);
  panel.paperNumberDisabled = true;
}

interface InfusionTableColumn {
  field?: string;
}

/**
 * 门诊瓶签只改剂量单位，不动纸张和坐标。
 * 模板若只有「总量」列，该列改绑医生开立剂量；已有剂量列则保持原列。
 */
export function rebindOutpatientInfusionLabelDoseColumn(
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
    const elements = (panel as { printElements?: unknown[] }).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const raw of elements) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const options = (raw as { options?: { columns?: unknown; field?: string } }).options;
      if (!options || options.field !== 'medicationLines' || !Array.isArray(options.columns)) {
        continue;
      }
      for (const row of options.columns) {
        if (!Array.isArray(row)) {
          continue;
        }
        const columns = row as InfusionTableColumn[];
        const hasDoseColumn = columns.some((column) => column?.field === 'doseText');
        if (hasDoseColumn) {
          continue;
        }
        for (const column of columns) {
          if (column?.field === 'totalQuantityText') {
            column.field = 'doseText';
          }
        }
      }
    }
  }
  return printJson;
}

/**
 * 住院输液贴打印前收紧版式，使三支药落在原标签纸内。
 * 纸张宽高沿用库模板（60×50 / 80×60 等），只重排贴面内容。
 */
export function normalizeIpInfusionLabelPrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  if (PRINT_JSON_REWRITE_ENABLED !== true || IP_INFUSION_LABEL_REWRITE_COMPACT !== true) {
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
    applyCompactInfusionLabelPanel(panel as PrintPanelLike);
  }
  return printJson;
}
