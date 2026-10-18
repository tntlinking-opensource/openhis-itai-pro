import { PRINT_JSON_REWRITE_ENABLED } from '#/utils/print/print-json-rewrite.config';
import { SERVICE_APPLY_PRINT_REWRITE_SINGLE_COLUMN } from '#/utils/print/service-apply-print-layout.config';

/**
 * 检查/检验/治疗申请单：去掉右侧结果半栏；项目区上方保留一个「项目名称」标题，
 * 明细只列项目名（不逐条带前缀）。字号 12pt，纵向排列，一列排满再向右开列。
 *
 * 仅国基 override 打开 SERVICE_APPLY_PRINT_REWRITE_SINGLE_COLUMN 后才会改 JSON。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: { title?: string; type?: string };
}

const ITEM_FONT_PT = 12;
const PRECAUTION_HEIGHT = 80;
const SECTION_GAP = 8;
const LAYOUT_MARK = 'serviceApplyItemLayout';
const SINGLE_COL_MARK = 'serviceApplySingleColumn';
/** 单栏内容区宽度（与 A4 版心约 538pt、左右内边距对齐） */
const SINGLE_COL_INNER_WIDTH = 518;
const SINGLE_COL_LEFT = 38;

/**
 * hiprint longText formatter。
 * 只打项目名；有部位/标本时括在名称后，不带「项目名称：」前缀。
 */
const SERVICE_APPLY_ITEM_FORMATTER = `function(title,value,options,printData){
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function linesOf(text){return String(text==null?'':text).split(/\\n/).map(function(part){return part.trim();}).filter(function(part){return part.length>0;});}
function textWidth(text,font){var width=0;var raw=String(text||'');for(var i=0;i<raw.length;i++){width+=raw.charCodeAt(i)>255?font:font*0.55;}return width;}
function itemLabel(item){var name=String(item.itemName||'').trim();var detail=String(item.detailText||'').trim();if(name&&detail){return name+'（'+detail+'）';}return name||detail;}
function blockHeight(item,colWidth,font,line,gap){var label=itemLabel(item);var count=Math.max(1,Math.ceil(textWidth(label,font)/Math.max(colWidth,40)));return count*line+gap;}
function readItems(){
  var source=Array.isArray(value)?value:[];
  if(!source.length&&printData&&Array.isArray(printData.applyItemLines)){source=printData.applyItemLines;}
  var items=[];
  for(var i=0;i<source.length;i++){
    var row=source[i]||{};
    var name=String(row.itemName||'').trim();
    var detail=String(row.detailText||'').trim();
    if(!name&&!detail){continue;}
    items.push({itemName:name,detailText:detail});
  }
  if(items.length||!printData){return items;}
  var names=linesOf(printData.serviceItemNameText);
  var details=linesOf(printData.examSiteText||printData.specimenTypeText||printData.detailText);
  var count=Math.max(names.length,details.length);
  for(var n=0;n<count;n++){
    var itemName=names[n]||'';
    var detailText=details[n]||'';
    if(!itemName&&!detailText){continue;}
    items.push({itemName:itemName,detailText:detailText});
  }
  return items;
}
function pack(items,colWidth,areaHeight,font,line,gap,maxCols){
  var heights=[];
  for(var i=0;i<items.length;i++){heights.push(blockHeight(items[i],colWidth,font,line,gap));}
  var columns=[[]];
  var used=0;
  var overflow=false;
  for(var j=0;j<items.length;j++){
    var next=heights[j];
    if(used>0&&used+next>areaHeight&&columns.length<maxCols){columns.push([]);used=0;}
    columns[columns.length-1].push(items[j]);
    used+=next;
    if(used>areaHeight){overflow=true;}
  }
  return {columns:columns,overflow:overflow};
}
var items=readItems();
if(!items.length){return '';}
var areaWidth=Number(options&&options.width)||518;
var areaHeight=Number(options&&options.height)||500;
var font=12;
var line=18;
var gap=4;
var maxCols=Math.max(1,Math.floor(areaWidth/160));
if(maxCols>3){maxCols=3;}
var chosen=null;
var colCount=1;
while(colCount<=maxCols){
  var colWidth=(areaWidth-(colCount-1)*8)/colCount;
  var packed=pack(items,colWidth,areaHeight,font,line,gap,colCount);
  chosen=packed;
  if(!packed.overflow){break;}
  colCount++;
}
if(chosen&&chosen.overflow){
  var guard=0;
  while(chosen.overflow&&line>12&&guard<8){
    line-=1;
    gap=Math.max(2,gap-1);
    var tightWidth=(areaWidth-(maxCols-1)*8)/maxCols;
    chosen=pack(items,tightWidth,areaHeight,font,line,gap,maxCols);
    guard++;
  }
}
var columns=(chosen&&chosen.columns)||[items];
var html='<div style="width:100%;height:100%;overflow:hidden;font-family:SimSun,宋体,serif;font-size:'+font+'pt;line-height:'+line+'pt;color:#000;">';
html+='<table style="width:100%;height:100%;border-collapse:collapse;table-layout:fixed;"><tr>';
for(var c=0;c<columns.length;c++){
  html+='<td style="vertical-align:top;padding:0 '+(c<columns.length-1?'8pt':'0')+' 0 0;">';
  var column=columns[c];
  for(var k=0;k<column.length;k++){
    html+='<div style="margin:0 0 '+gap+'pt 0;word-break:break-all;">'+esc(itemLabel(column[k]))+'</div>';
  }
  html+='</td>';
}
html+='</tr></table></div>';
return html;
}`;

function readField(element: PrintElementLike): string {
  return String(element.options?.field ?? '').trim();
}

function readNumber(value: unknown, fallback = 0): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return parsed;
}

function isItemElement(element: PrintElementLike): boolean {
  const field = readField(element);
  return field === 'serviceItemNameText' || field === 'applyItemLines';
}

function isDetailElement(element: PrintElementLike): boolean {
  const field = readField(element);
  return field === 'examSiteText' || field === 'specimenTypeText' || field === 'detailText';
}

function isRightResultElement(element: PrintElementLike): boolean {
  const field = readField(element);
  return field === 'examResultText' || field === 'labResultText' || field === 'resultText';
}

/** 去掉右侧结果半栏与中缝竖线，科室/签字落到单栏底部 */
function stripRightResultColumn(elements: PrintElementLike[]): PrintElementLike[] {
  const alreadySingle = elements.some(
    (element) => element.options?.[SINGLE_COL_MARK] === 1 || element.options?.[LAYOUT_MARK] === 1,
  );
  const hasRightResult = elements.some((element) => isRightResultElement(element));
  if (alreadySingle && !hasRightResult) {
    return elements;
  }

  const kept = elements.filter((element) => {
    if (isRightResultElement(element)) {
      return false;
    }
    if (element.printElementType?.type === 'vline') {
      return false;
    }
    return true;
  });

  const diagnosis = kept.find((element) => readField(element) === 'diagnosisText');
  if (diagnosis?.options) {
    diagnosis.options.left = SINGLE_COL_LEFT;
    diagnosis.options.width = SINGLE_COL_INNER_WIDTH;
  }

  const itemElement = kept.find((element) => isItemElement(element));
  const precautionElement = kept.find((element) => readField(element) === 'precautionText');
  const deptElement = kept.find(
    (element) =>
      readField(element) === 'examDepartmentName' ||
      readField(element) === 'labDepartmentName' ||
      readField(element) === 'performDepartmentName',
  );
  const signElement = kept.find((element) => readField(element) === 'practitionerSignName');

  const itemTop = readNumber(itemElement?.options?.top, 134);
  let contentBottom = itemTop + 520;
  if (precautionElement?.options) {
    contentBottom =
      readNumber(precautionElement.options.top) + readNumber(precautionElement.options.height, 80);
  }
  const footerLineTop = contentBottom + 6;
  const signTop = footerLineTop + 8;

  for (const element of kept) {
    if (!element.options) {
      continue;
    }
    if (isItemElement(element) || readField(element) === 'precautionText') {
      element.options.left = SINGLE_COL_LEFT;
      element.options.width = SINGLE_COL_INNER_WIDTH;
    }
    if (element.printElementType?.type === 'hline') {
      const lineLeft = readNumber(element.options.left);
      if (lineLeft >= 280) {
        element.options.left = SINGLE_COL_LEFT;
        element.options.width = SINGLE_COL_INNER_WIDTH;
      } else if (Math.abs(lineLeft - SINGLE_COL_LEFT) <= 24) {
        element.options.width = SINGLE_COL_INNER_WIDTH;
      }
    }
  }

  if (deptElement?.options) {
    deptElement.options.top = signTop;
    deptElement.options.left = SINGLE_COL_LEFT;
    deptElement.options.width = 220;
  }
  if (signElement?.options) {
    signElement.options.top = signTop;
    signElement.options.left = 320;
    signElement.options.width = 220;
  }

  const hasFooterLine = kept.some((element) => {
    if (element.printElementType?.type !== 'hline') {
      return false;
    }
    return Math.abs(readNumber(element.options?.top) - footerLineTop) <= 4;
  });
  if (!hasFooterLine) {
    kept.push({
      options: {
        top: footerLineTop,
        left: SINGLE_COL_LEFT,
        width: SINGLE_COL_INNER_WIDTH,
        height: 1,
        borderWidth: 1,
        [SINGLE_COL_MARK]: 1,
      },
      printElementType: { type: 'hline', title: '横线' },
    });
  }

  if (itemElement?.options) {
    itemElement.options[SINGLE_COL_MARK] = 1;
  }
  return kept;
}

/** 把项目名称与部位两块合成项目区，字号加大，排版交给 formatter */
function layoutItemArea(elements: PrintElementLike[]): PrintElementLike[] {
  const itemElement = elements.find((element) => isItemElement(element));
  if (!itemElement) {
    return elements;
  }
  if (!itemElement.options) {
    itemElement.options = {};
  }
  const options = itemElement.options;
  if (options[LAYOUT_MARK] === 1 && options[SINGLE_COL_MARK] === 1) {
    return elements;
  }

  const precautionElement = elements.find((element) => readField(element) === 'precautionText');
  const itemTop = readNumber(options.top, 126);
  const itemLeft = SINGLE_COL_LEFT;
  const itemWidth = SINGLE_COL_INNER_WIDTH;
  let itemHeight = Math.max(readNumber(options.height, 40), 160);
  let nextElements = elements.filter((element) => !isDetailElement(element) || element === itemElement);

  if (precautionElement?.options) {
    const precautionTop = readNumber(precautionElement.options.top, itemTop + 100);
    const precautionBottom = precautionTop + readNumber(precautionElement.options.height, 146);
    itemHeight = Math.max(120, precautionBottom - itemTop - PRECAUTION_HEIGHT - SECTION_GAP);
    precautionElement.options.top = itemTop + itemHeight + SECTION_GAP;
    precautionElement.options.height = PRECAUTION_HEIGHT;
    precautionElement.options.left = itemLeft;
    precautionElement.options.width = itemWidth;
    nextElements = nextElements.filter((element) => {
      if (element.printElementType?.type !== 'hline') {
        return true;
      }
      const lineTop = readNumber(element.options?.top);
      const lineLeft = readNumber(element.options?.left);
      if (Math.abs(lineLeft - itemLeft) > 24 && lineLeft < 280) {
        return true;
      }
      if (lineLeft >= 280) {
        return false;
      }
      return lineTop <= itemTop || lineTop >= precautionTop;
    });
    nextElements.push({
      options: {
        top: itemTop + itemHeight + 2,
        left: itemLeft,
        width: itemWidth,
        height: 1,
        borderWidth: 1,
      },
      printElementType: { type: 'hline', title: '横线' },
    });
  }

  options.top = itemTop;
  options.left = itemLeft;
  options.width = itemWidth;
  options.height = itemHeight;
  options[LAYOUT_MARK] = 1;
  options[SINGLE_COL_MARK] = 1;
  return nextElements;
}

function bindItemFormatter(element: PrintElementLike): void {
  if (!element.options) {
    element.options = {};
  }
  element.options.field = 'applyItemLines';
  element.options.hideTitle = true;
  element.options.fontSize = ITEM_FONT_PT;
  element.options.fontFamily = 'SimSun';
  element.options.title = '项目';
  element.options.formatter = SERVICE_APPLY_ITEM_FORMATTER;
  element.options[LAYOUT_MARK] = 1;
  element.printElementType = { type: 'longText', title: '项目' };
}

/**
 * 检查/检验申请单（治疗复用检查模板）打印前：单栏 + 项目区纵向多列。
 * 主线直接返回原 JSON；国基走改写。
 */
export function normalizeServiceApplyPrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  if (
    PRINT_JSON_REWRITE_ENABLED !== true ||
    SERVICE_APPLY_PRINT_REWRITE_SINGLE_COLUMN !== true
  ) {
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
    const rawElements = (panel as { printElements?: unknown }).printElements;
    if (!Array.isArray(rawElements)) {
      continue;
    }
    const elements = rawElements.filter(
      (element): element is PrintElementLike => !!element && typeof element === 'object',
    );
    const singleColumn = stripRightResultColumn(elements);
    const laidOut = layoutItemArea(singleColumn);
    const itemElement = laidOut.find((element) => isItemElement(element));
    if (itemElement) {
      bindItemFormatter(itemElement);
    }
    (panel as { printElements: PrintElementLike[] }).printElements = laidOut;
  }
  return printJson;
}
