import type { Plugin } from 'vite';

const NEEDLE =
  'this.highlight.recordFillInfo(e, i - d, u + a - o, n.metrics.width + d, s.height - 2 * a + 2 * o, l);';

/** 序列化白名单缺这两项，改了线宽也会在 getValue / updateElementById 时被丢掉 */
const BORDER_ATTR_NEEDLE = 'borderType.borderColor.translateX';
const BORDER_ATTR_REPLACEMENT = 'borderType.borderColor.borderWidth.borderExternalWidth.translateX';

const REPLACEMENT = `{
  const isChrome = this.isPrintMode() && n.control && n.control.highlight && String(l).toLowerCase() === String(n.control.highlight).toLowerCase();
  if (!isChrome) {
    const fontH = (n.metrics.boundingBoxAscent || 0) + (n.metrics.boundingBoxDescent || 0) || n.metrics.height;
    const fontY = u + s.ascent - (n.metrics.boundingBoxAscent || fontH);
    this.highlight.recordFillInfo(e, i - d, fontY, n.metrics.width + d, fontH, l);
  }
}`;

/**
 * 库原始行高 = 8px × 行距倍数，上下各加一份。正文 1.5 倍时单元格一行约 40px，
 * 拖完边框下一次排版会把 minHeight 写回这个高度，所以行高拖不小。
 * 单元格没写 rowMargin 时按单倍排，行高下限用 table.defaultTrMinHeight。
 * 工具栏显式选 1.5 倍会把 rowMargin 写成 1.5（与 defaultRowMargin 相同），必须按 1.5 排。
 */
const ROW_MARGIN_FN =
  /getElementRowMargin\(e\) \{\s*let \{ defaultSize: t, defaultBasicRowMarginHeight: n, defaultRowMargin: r, scale: i \} = this\.options, a = e\.size \|\| t, o = 1;\s*return a < 12 \? o = a \/ 12 : a > 30 && \(o = 1 \+ \(a - 30\) \/ 30\), n \* o \* \(e\.rowMargin \?\? r\) \* i;\s*\}/;

const ROW_MARGIN_REPLACEMENT = [
  'getElementRowMargin(e, fromTable) {',
  '  let { defaultSize: t, defaultBasicRowMarginHeight: n, defaultRowMargin: r, scale: i, defaultFont: fontName } = this.options;',
  '  let size = e.actualSize || e.size || t;',
  '  let multiple = e.rowMargin ?? r;',
  '  let inTable = !!fromTable || !!this._rowMarginInTable;',
  '  if (inTable && e.rowMargin == null) multiple = 1;',
  '  let type = e.type;',
  '  let textLike = !type || type === "text" || type === "hyperlink" || type === "superscript" || type === "subscript" || type === "control" || type === "date" || type === "tab" || type === "title" || type === "list";',
  '  if (!textLike) {',
  '    let o = 1;',
  '    size < 12 ? (o = size / 12) : size > 30 && (o = 1 + (size - 30) / 30);',
  '    return n * o * multiple * i;',
  '  }',
  '  let font = (e.italic ? "italic " : "") + (e.bold ? "bold " : "") + size + "px " + (e.font || fontName);',
  '  if (!this._wordLineInk) this._wordLineInk = new Map();',
  '  let ink = this._wordLineInk.get(font);',
  '  if (ink == null) {',
  '    ink = size;',
  '    if (typeof document !== "undefined") {',
  '      if (!this._wordLineCtx) this._wordLineCtx = document.createElement("canvas").getContext("2d");',
  '      let ctx = this._wordLineCtx;',
  '      if (ctx) {',
  '        ctx.save();',
  '        ctx.font = font;',
  '        let metrics = ctx.measureText("\\u4e2d");',
  '        ctx.restore();',
  '        let box = (metrics.actualBoundingBoxAscent || 0) + (metrics.actualBoundingBoxDescent || 0);',
  '        if (!box) box = (metrics.fontBoundingBoxAscent || 0) + (metrics.fontBoundingBoxDescent || 0);',
  '        if (box) ink = box;',
  '      }',
  '    }',
  '    this._wordLineInk.set(font, ink);',
  '  }',
  '  let singleGap = size * 7 / 16;',
  '  let gap = ink * (multiple - 1) + singleGap * multiple;',
  '  if (gap < 0) gap = 0;',
  '  return gap / 2 * i;',
  '}',
].join('\n');

const TABLE_ROW_FLOOR =
  'o = r + f + c * 2 / p;\n\t\t\t\tfor (let e = 0; e < a.length; e++) {\n\t\t\t\t\tlet t = a[e];\n\t\t\t\t\tt.height = Math.max(o, t.minHeight || 0), t.minHeight = t.height;\n\t\t\t\t}';

const TABLE_ROW_FLOOR_REPLACEMENT =
  'minH = this.options.table.defaultTrMinHeight;\n\t\t\t\tfor (let e = 0; e < a.length; e++) {\n\t\t\t\t\tlet row = a[e];\n\t\t\t\t\tif (!row.minHeight || row.minHeight < minH) row.minHeight = minH;\n\t\t\t\t\trow.height = row.minHeight;\n\t\t\t\t}';

const TABLE_CELL_CALL =
  'let s = n.tdList[o], c = this.computeRowList({\n\t\t\t\t\t\t\tinnerWidth: (s.width - t) * p,\n\t\t\t\t\t\t\telementList: s.value,\n\t\t\t\t\t\t\tisFromTable: !0,\n\t\t\t\t\t\t\tisPagingMode: i\n\t\t\t\t\t\t}), l = c.reduce((e, t) => e + t.height, 0);';

const TABLE_CELL_CALL_REPLACEMENT =
  'let s = n.tdList[o], _prevInTable = this._rowMarginInTable, spanH = 0;\n\t\t\t\t\t\t\tfor (let _ri = 0; _ri < (s.rowspan || 1); _ri++) spanH += (a[e + _ri] || n).height;\n\t\t\t\t\t\t\tthis._rowMarginInTable = !0;\n\t\t\t\t\t\t\tlet c = this.computeRowList({\n\t\t\t\t\t\t\tinnerWidth: (s.width - t) * p,\n\t\t\t\t\t\t\telementList: s.value,\n\t\t\t\t\t\t\tisFromTable: !0,\n\t\t\t\t\t\t\tisPagingMode: i,\n\t\t\t\t\t\t\tmaxLineHeight: Math.max(0, spanH - r) * p\n\t\t\t\t\t\t});\n\t\t\t\t\t\t\tthis._rowMarginInTable = _prevInTable;\n\t\t\t\t\t\t\tlet l = c.reduce((e, t) => e + t.height, 0);';

const TABLE_SPLIT_CALL =
  'e.rowList = this.draw.computeRowList({\n\t\t\t\tinnerWidth: (e.width - (i[1] + i[3])) * r,\n\t\t\t\telementList: e.value,\n\t\t\t\tisFromTable: !0,\n\t\t\t\tisPagingMode: this.draw.getIsPagingMode()\n\t\t\t})';

const TABLE_SPLIT_CALL_REPLACEMENT =
  'e.rowList = (this.draw._rowMarginInTable = !0, this.draw._rowListTmp = this.draw.computeRowList({\n\t\t\t\tinnerWidth: (e.width - (i[1] + i[3])) * r,\n\t\t\t\telementList: e.value,\n\t\t\t\tisFromTable: !0,\n\t\t\t\tisPagingMode: this.draw.getIsPagingMode()\n\t\t\t}), this.draw._rowMarginInTable = !1, this.draw._rowListTmp)';

const ROW_MARGIN_CALL = 'c = this.getElementRowMargin(s), l = {';
const ROW_MARGIN_CALL_REPLACEMENT = 'c = this.getElementRowMargin(s, a), l = {';

const ROW_LIST_SIG =
  'isFromTable: a = !1, startX: s = 0, startY: c = 0, pageHeight: l = 0, surroundElementList: u = [] } = e';
const ROW_LIST_SIG_REPLACEMENT =
  'isFromTable: a = !1, startX: s = 0, startY: c = 0, pageHeight: l = 0, surroundElementList: u = [], maxLineHeight: maxLH = 0 } = e';

/** 小四行盒高出单元格时，把这一行收进当前行高，光标才能落在格子里 */
const LINE_BOX =
  'let ne = !s.hide && !this.traceParticle.isTraceHidden(s) && (s.imgDisplay !== r.INLINE && s.type === H.IMAGE || s.type === H.LATEX) ? l.height + c : l.boundingBoxAscent + c, z = c + l.boundingBoxAscent + l.boundingBoxDescent + c, B = Object.assign(s, {';
const LINE_BOX_REPLACEMENT = `let ne = !s.hide && !this.traceParticle.isTraceHidden(s) && (s.imgDisplay !== r.INLINE && s.type === H.IMAGE || s.type === H.LATEX) ? l.height + c : l.boundingBoxAscent + c, z = c + l.boundingBoxAscent + l.boundingBoxDescent + c;
			if (a && maxLH > 0 && z > maxLH && (!s.type || s.type === H.TEXT || s.type === H.HYPERLINK || s.type === H.SUPERSCRIPT || s.type === H.SUBSCRIPT || s.type === H.CONTROL || s.type === H.DATE || s.type === H.TAB || s.type === H.TITLE || s.type === H.LIST)) {
				let glyph = l.boundingBoxAscent + l.boundingBoxDescent;
				if (glyph >= maxLH) c = 0, z = maxLH, ne = Math.min(l.boundingBoxAscent, maxLH), l.height = Math.min(l.height, maxLH);
				else c = (maxLH - glyph) / 2, z = maxLH, ne = l.boundingBoxAscent + c;
			}
			let B = Object.assign(s, {`;

const CURSOR_BOX =
  'let { metrics: u, coordinate: { leftTop: d, rightTop: f }, ascent: p, pageNo: m } = t, h = this.draw.getZone().isMainActive() ? m : this.draw.getPageNo(), { x: g, y: _ } = this.draw.getPageOffset(h), v = 12 * n, y = Math.min(u.height / 4, v), b = u.height + y * 2, x = this.cursorAgent.getAgentCursorDom();';
const CURSOR_BOX_REPLACEMENT =
  'let { metrics: u, coordinate: { leftTop: d, rightTop: f }, ascent: p, lineHeight: lineH, pageNo: m } = t, h = this.draw.getZone().isMainActive() ? m : this.draw.getPageNo(), { x: g, y: _ } = this.draw.getPageOffset(h), v = 12 * n, y = Math.min(u.height / 4, v), b = u.height + y * 2, x = this.cursorAgent.getAgentCursorDom();';

const CURSOR_TOP =
  'let S = u.boundingBoxDescent < 0 ? 0 : u.boundingBoxDescent, C = d[1] + p + S - (b - y) + _, w = (l ? d[0] : f[0]) + g;';
const CURSOR_TOP_REPLACEMENT = `let S = u.boundingBoxDescent < 0 ? 0 : u.boundingBoxDescent, C = d[1] + p + S - (b - y) + _, w = (l ? d[0] : f[0]) + g;
		if (lineH > 2) {
			let lineTop = d[1] + _;
			if (b > lineH) b = lineH;
			if (C < lineTop) C = lineTop;
			let limit = lineTop + lineH - b;
			if (C > limit) C = limit;
		}`;

/** 画布把 \\u200B 当成换行，选项文字里的换行必须写回 valueSets，下次打开才还在 */
const CHOICE_BREAK = '"\\u200B"';

const CHOICE_HELPERS = `
function choiceBoxIndex(list, index) {
  let cur = list[index];
  let id = cur && cur.controlId;
  if (!id) return -1;
  for (let i = index; i >= 0; i--) {
    let el = list[i];
    if (!el || el.controlId !== id || el.controlComponent === K.PREFIX || el.controlComponent === K.PRE_TEXT) break;
    if (el.controlComponent === K.CHECKBOX || el.controlComponent === K.RADIO) return i;
  }
  return -1;
}
function choiceOptionIndex(list, box) {
  let id = list[box] && list[box].controlId;
  let n = 0;
  for (let i = box - 1; i >= 0; i--) {
    let el = list[i];
    if (!el || el.controlId !== id || el.controlComponent === K.PREFIX || el.controlComponent === K.PRE_TEXT) break;
    if (el.controlComponent === K.CHECKBOX || el.controlComponent === K.RADIO) n++;
  }
  return n;
}
function choiceWriteOption(list, box) {
  let el = list[box];
  let sets = el && el.control && el.control.valueSets;
  if (!sets) return;
  let text = "";
  let id = el.controlId;
  for (let i = box + 1; i < list.length; i++) {
    let item = list[i];
    if (!item || item.controlId !== id) break;
    if (item.controlComponent === K.CHECKBOX || item.controlComponent === K.RADIO || item.controlComponent === K.POSTFIX || item.controlComponent === K.POST_TEXT) break;
    if (item.controlComponent === K.VALUE) text += item.value === ${CHOICE_BREAK} ? "\\n" : (item.value || "");
  }
  let option = sets[choiceOptionIndex(list, box)];
  if (option) option.value = text;
}
function choiceInsertValue(host, data) {
  let list = host.control.getElementList();
  let range = host.control.getRange();
  let start = range.startIndex;
  let end = range.endIndex;
  let cur = list[start];
  if (start !== end || !data || !data.length || !cur) return -1;
  let box = -1;
  if (cur.controlComponent === K.VALUE) box = choiceBoxIndex(list, start);
  else if (cur.controlComponent === K.CHECKBOX || cur.controlComponent === K.RADIO) box = start;
  if (box < 0) return -1;
  let added = data.map((item) => ({
    value: item.value === "\\n" ? ${CHOICE_BREAK} : item.value,
    type: H.CONTROL,
    font: cur.font,
    size: cur.size,
    bold: cur.bold,
    color: cur.color,
    italic: cur.italic,
    controlId: cur.controlId,
    control: cur.control,
    controlComponent: K.VALUE
  }));
  host.control.getDraw().spliceElementList(list, start + 1, 0, added);
  choiceWriteOption(list, box);
  return start + added.length;
}
function choiceGapHit(host, list, index, el, x, left, right) {
  let isRadio = el.type === H.RADIO || el.controlComponent === K.RADIO;
  let gap = ((isRadio ? host.options.radio.gap : host.options.checkbox.gap) || 0) * (host.options.scale || 1);
  let boxLeft = left + gap;
  let boxRight = right - gap;
  if (x >= boxLeft && x <= boxRight) return {
    index: index,
    isDirectHit: !0,
    isCheckbox: !isRadio,
    isRadio: isRadio
  };
  if (x < boxLeft) {
    let prev = index - 1;
    while (prev >= 0) {
      let item = list[prev];
      if (!item) break;
      if (item.controlComponent === K.PREFIX || item.controlComponent === K.PRE_TEXT || item.controlComponent === K.POSTFIX || item.controlComponent === K.POST_TEXT) {
        prev--;
        continue;
      }
      break;
    }
    if (prev < 0) prev = 0;
    let item = list[prev];
    return {
      isDirectHit: !0,
      index: prev,
      isControl: !!(item && item.controlId && item.controlComponent === K.VALUE)
    };
  }
  return {
    isDirectHit: !0,
    index: index,
    isControl: !!el.controlId
  };
}
function choiceDeleteValue(host, index) {
  let list = host.control.getElementList();
  let cur = list[index];
  if (!cur || cur.controlComponent !== K.VALUE) return null;
  let box = choiceBoxIndex(list, index);
  if (box < 0) return null;
  host.control.getDraw().spliceElementList(list, index, 1);
  choiceWriteOption(list, box);
  return Math.max(index - 1, 0);
}
`;

const CHECKBOX_SET_VALUE = `	setValue() {
		return -1;
	}`;

const CHECKBOX_SET_VALUE_REPLACEMENT = `	setValue(data) {
		return choiceInsertValue(this, data);
	}`;

const CHECKBOX_KEYDOWN = `	keydown(e) {
		if (this.control.getIsDisabledControl()) return null;
		let t = this.control.getRange();
		this.control.shrinkBoundary();
		let { startIndex: n, endIndex: r } = t;
		return e.key === Z.Backspace || e.key === Z.Delete ? this.control.removeControl(n) : r;
	}`;

const CHECKBOX_KEYDOWN_REPLACEMENT = `	keydown(e) {
		if (this.control.getIsDisabledControl()) return null;
		let t = this.control.getRange();
		let { startIndex: n, endIndex: r } = t;
		let list = this.control.getElementList();
		if (e.key === Z.Backspace || e.key === Z.Delete) {
			let cur = list[n];
			if (n === r && (cur?.controlComponent === K.CHECKBOX || cur?.controlComponent === K.RADIO)) {
				if (e.key === Z.Delete && list[n + 1]?.controlComponent === K.VALUE && list[n + 1]?.controlId === cur.controlId) {
					choiceDeleteValue(this, n + 1);
				}
				return n;
			}
			if (n === r && e.key === Z.Backspace && cur?.controlComponent === K.VALUE) {
				let next = choiceDeleteValue(this, n);
				if (next !== null) return next;
			}
			if (n === r && e.key === Z.Delete && list[n + 1]?.controlComponent === K.VALUE && list[n + 1]?.controlId === cur?.controlId) {
				choiceDeleteValue(this, n + 1);
				return n;
			}
			this.control.shrinkBoundary();
			return this.control.removeControl(this.control.getRange().startIndex);
		}
		return r;
	}`;

const CHOICE_BOX_HIT = `if (s.type === H.CHECKBOX || s.controlComponent === K.CHECKBOX) return {
					index: i,
					isDirectHit: !0,
					isCheckbox: !0
				};`;

const CHOICE_BOX_HIT_REPLACEMENT = `if (s.type === H.CHECKBOX || s.controlComponent === K.CHECKBOX || s.type === H.RADIO || s.controlComponent === K.RADIO) return choiceGapHit(this, a, i, s, t, f[0], p[0]);`;

/**
 * 选项末尾的空格被当成普通字：点左半边会把光标退到空格前面，贴在文字上。
 * 这个空格就是两个方框之间的落点，点哪里都把光标放在空格后面。
 */
const CHOICE_SPACE_HIT = `if (a[r].value !== "​") {
					let n = p[0] - f[0];
					t < f[0] + n / 2 && (i = e - 1, d && (l = e));
				}`;

const CHOICE_SPACE_HIT_REPLACEMENT = `if (a[r].value !== "​") {
					let n = p[0] - f[0];
					let choiceSpace = s.controlComponent === K.VALUE && (s.control?.type === G.CHECKBOX || s.control?.type === G.RADIO) && !(s.value || "").trim();
					!choiceSpace && t < f[0] + n / 2 && (i = e - 1, d && (l = e));
				}`;

const CHOICE_RADIO_HIT = `if (s.type === H.RADIO || s.controlComponent === K.RADIO) return {
					index: i,
					isDirectHit: !0,
					isRadio: !0
				};`;

const CHOICE_CLICK_PUSH = 'else n?.code && e.push(n.code);';
const CHOICE_CLICK_PUSH_REPLACEMENT =
  'else if (n?.code) { r.max === 1 ? e.splice(0, e.length, n.code) : e.push(n.code); }';

const CHOICE_LABEL_CLICK = `		else if (O.controlComponent === K.VALUE && (O.control?.type === G.CHECKBOX || O.control?.type === G.RADIO)) {
			let e = D;
			for (; e > 0;) {
				let t = T[e];
				if (t.controlComponent === K.CHECKBOX) {
					dr(t, n);
					break;
				}
				if (t.controlComponent === K.RADIO) {
					fr(t, n);
					break;
				}
				e--;
			}
		} else n.render({`;

const CHOICE_LABEL_CLICK_REPLACEMENT = `		else n.render({`;

const REMOVE_CONTROL_TAIL =
  'return s >= n.length && (a = n.length - 1), !~i && !~a ? e : (i = ~i ? i : 0, this.draw.deleteElementList(n, i + 1, a - i), i);';

const REMOVE_CONTROL_TAIL_REPLACEMENT = `if (s >= n.length) a = n.length - 1;
		if (!~i) {
			let b = e;
			while (b > 0 && n[b - 1]?.controlId === r.controlId) b--;
			i = b - 1;
		}
		if (!~i && !~a) return e;
		return (this.draw.deleteElementList(n, i + 1, a - i), i);`;

/**
 * 点进选项文字只落了光标，不会激活控件。
 * 回车和退格在控件未激活时走文档路径：回车因没有光标坐标被丢掉，退格则把整个控件删掉。
 * 与输入法同一规则：范围内先激活，再交给控件自己插入或删除。
 */
const CHOICE_ENTER_INIT =
  'let f = n.getControl(), p = f.getActiveControl();\n\tn.getTraceParticle().markElementListInserted([u]);\n\tlet m;\n\tif (p && f.getIsRangeWithinControl()) m = f.setValue([u]), f.emitControlContentChange();';

const CHOICE_ENTER_INIT_REPLACEMENT =
  'let f = n.getControl(), p = f.getActiveControl();\n\tn.getTraceParticle().markElementListInserted([u]);\n\tlet m;\n\tif (!p && f.getIsRangeWithinControl()) f.initControl(), p = f.getActiveControl();\n\tif (p && f.getIsRangeWithinControl()) m = f.setValue([u]), f.emitControlContentChange();';

const CHOICE_BACKSPACE_INIT =
  '} else if (l.getActiveControl() && l.getIsRangeWithinControl()) u = l.keydown(e), u && l.emitControlContentChange();\n\telse if (s && c[o + 1]?.controlId) u = l.removeControl(o + 1);';

const CHOICE_BACKSPACE_INIT_REPLACEMENT =
  '} else if ((l.getActiveControl() || l.getIsRangeWithinControl() && (l.initControl(), l.getActiveControl())) && l.getIsRangeWithinControl()) u = l.keydown(e), u && l.emitControlContentChange();\n\telse if (s && c[o + 1]?.controlId) u = l.removeControl(o + 1);';

function patchChoiceControls(code: string): string {
  let next = code;
  if (!next.includes('function choiceBoxIndex(') && next.includes('//#region src/editor/core/draw/control/checkbox/CheckboxControl.ts')) {
    next = next.replace(
      '//#region src/editor/core/draw/control/checkbox/CheckboxControl.ts',
      `//#region src/editor/core/draw/control/checkbox/CheckboxControl.ts\n${CHOICE_HELPERS}`,
    );
  }
  if (next.includes(CHECKBOX_SET_VALUE)) next = next.replace(CHECKBOX_SET_VALUE, CHECKBOX_SET_VALUE_REPLACEMENT);
  if (next.includes(CHECKBOX_KEYDOWN)) next = next.replace(CHECKBOX_KEYDOWN, CHECKBOX_KEYDOWN_REPLACEMENT);
  if (next.includes(CHOICE_BOX_HIT)) next = next.replace(CHOICE_BOX_HIT, CHOICE_BOX_HIT_REPLACEMENT);
  if (next.includes(CHOICE_SPACE_HIT)) next = next.replace(CHOICE_SPACE_HIT, CHOICE_SPACE_HIT_REPLACEMENT);
  if (next.includes(CHOICE_RADIO_HIT)) next = next.replace(CHOICE_RADIO_HIT, '');
  if (next.includes(CHOICE_CLICK_PUSH)) next = next.replace(CHOICE_CLICK_PUSH, CHOICE_CLICK_PUSH_REPLACEMENT);
  if (next.includes(CHOICE_LABEL_CLICK)) next = next.replace(CHOICE_LABEL_CLICK, CHOICE_LABEL_CLICK_REPLACEMENT);
  if (next.includes(REMOVE_CONTROL_TAIL)) next = next.replace(REMOVE_CONTROL_TAIL, REMOVE_CONTROL_TAIL_REPLACEMENT);
  if (next.includes(CHOICE_ENTER_INIT)) next = next.replace(CHOICE_ENTER_INIT, CHOICE_ENTER_INIT_REPLACEMENT);
  if (next.includes(CHOICE_BACKSPACE_INIT)) next = next.replace(CHOICE_BACKSPACE_INIT, CHOICE_BACKSPACE_INIT_REPLACEMENT);
  return next;
}

/**
 * 整表选中时 rangeStyle 取的是表格节点，表格没有 size，指示器变成 defaultSize（小四）。
 * 再点小四与 model 相同，字号命令根本不会下发。改成读选中单元格里的文字。
 */
const TABLE_RANGE_STYLE =
  'if (i) a = this.draw.getOriginalElementList()[this.position.getPositionContext().index];\n\t\telse {\n\t\t\tlet e = ~r ? r : 0, t = this.draw.getElementList();\n\t\t\ta = this.getRangeAnchorStyle(t, e);\n\t\t}\n\t\tif (!a) return;\n\t\tlet o = this.getSelection() || [a], s = a.type || H.TEXT, c = a.font || this.options.defaultFont, l = a.size || this.options.defaultSize, u = !~o.findIndex((e) => !e.bold),';

const TABLE_RANGE_STYLE_REPLACEMENT =
  'if (i) {\n\t\t\tlet cells = this.getSelectionElementList();\n\t\t\ta = cells && cells.find((el) => el && el.value !== "​") || this.draw.getOriginalElementList()[this.position.getPositionContext().index];\n\t\t} else {\n\t\t\tlet e = ~r ? r : 0, t = this.draw.getElementList();\n\t\t\ta = this.getRangeAnchorStyle(t, e);\n\t\t}\n\t\tif (!a) return;\n\t\tlet o = (i ? this.getSelectionElementList() : this.getSelection()) || [a], s = a.type || H.TEXT, c = a.font || this.options.defaultFont, l = a.size || this.options.defaultSize;\n\t\tif (o.length) {\n\t\t\tlet firstFont = o[0].font || this.options.defaultFont, firstSize = o[0].size || this.options.defaultSize;\n\t\t\tif (o.every((el) => (el.font || this.options.defaultFont) === firstFont)) c = firstFont;\n\t\t\tif (o.every((el) => (el.size || this.options.defaultSize) === firstSize)) l = firstSize;\n\t\t\telse l = 0;\n\t\t}\n\t\tlet u = !~o.findIndex((e) => !e.bold),';

/**
 * 库把「没有 size 且目标是 defaultSize」当成已经是小四，整表改小四会被跳过。
 * 用户点了小四就写成 16，单元格里原来的四号/五号才能收回去。
 */
const SIZE_APPLY =
  '\t\ts.forEach((t) => {\n\t\t\t!t.size && e === a || t.size && t.size === e || (t.size = e, l = !0);\n\t\t}), l && this.draw.render(o);';

const SIZE_APPLY_REPLACEMENT =
  '\t\ts.forEach((t) => {\n\t\t\tif (t.size !== e) {\n\t\t\t\tt.size = e;\n\t\t\t\tl = !0;\n\t\t\t}\n\t\t}), l && this.draw.render(o);';

/**
 * 排版每个格子都会 new 一块 canvas。页数一多，每个字都是几百次 getContext。
 * 测量只改 font，一块画布反复用即可。
 */
const ROW_MEASURE_CANVAS =
  'b = this.getDefaultBasicRowMarginHeight(), x = document.createElement("canvas").getContext("2d");';
const ROW_MEASURE_CANVAS_REPLACEMENT =
  'b = this.getDefaultBasicRowMarginHeight(), x = this._rowMeasureCtx || (this._rowMeasureCtx = document.createElement("canvas").getContext("2d"));';

/**
 * 每次输入都深拷贝全文进撤销栈。病程页数上去之后，拷贝比绘制更卡。
 * 连续输入合并成一次快照；撤销/重做前先落下这次快照，避免把刚打的字丢掉。
 */
const SUBMIT_HISTORY =
  '\tsubmitHistory(e) {\n\t\tlet t = this.position.getPositionContext(), n = Rn(this.elementList), r = Rn(this.header.getElementList()), i = Rn(this.footer.getElementList()), a = A(this.range.getRange()), o = this.pageNo, s = A(t), c = this.zone.getZone();\n\t\tthis.historyManager.execute(() => {\n\t\t\tthis.zone.setZone(c), this.setPageNo(o), this.position.setPositionContext(A(s)), this.header.setElementList(A(r)), this.footer.setElementList(A(i)), this.elementList = A(n), this.range.replaceRange(A(a)), this.render({\n\t\t\t\tcurIndex: e,\n\t\t\t\tisSubmitHistory: !1,\n\t\t\t\tisSourceHistory: !0\n\t\t\t});\n\t\t});\n\t}';

const SUBMIT_HISTORY_REPLACEMENT =
  '\tsubmitHistory(e) {\n\t\tthis._pendingHistoryIndex = e;\n\t\tif (this._historyTimer) clearTimeout(this._historyTimer);\n\t\tlet commit = () => {\n\t\t\tthis._historyTimer = 0;\n\t\t\tlet e = this._pendingHistoryIndex, t = this.position.getPositionContext(), n = Rn(this.elementList), r = Rn(this.header.getElementList()), i = Rn(this.footer.getElementList()), a = A(this.range.getRange()), o = this.pageNo, s = A(t), c = this.zone.getZone();\n\t\t\tthis.historyManager.execute(() => {\n\t\t\t\tthis.zone.setZone(c), this.setPageNo(o), this.position.setPositionContext(A(s)), this.header.setElementList(A(r)), this.footer.setElementList(A(i)), this.elementList = A(n), this.range.replaceRange(A(a)), this.render({\n\t\t\t\t\tcurIndex: e,\n\t\t\t\t\tisSubmitHistory: !1,\n\t\t\t\t\tisSourceHistory: !0\n\t\t\t\t});\n\t\t\t});\n\t\t};\n\t\tthis._historyCommit = commit, this._historyTimer = setTimeout(commit, 400);\n\t}';

const HISTORY_UNDO =
  '\tundo() {\n\t\tthis.draw.isReadonly() || this.historyManager.undo();\n\t}\n\tredo() {\n\t\tthis.draw.isReadonly() || this.historyManager.redo();';

const HISTORY_UNDO_REPLACEMENT =
  '\tundo() {\n\t\tif (this.draw.isReadonly()) return;\n\t\tthis.draw._historyTimer && (clearTimeout(this.draw._historyTimer), this.draw._historyTimer = 0, this.draw._historyCommit && this.draw._historyCommit());\n\t\tthis.historyManager.undo();\n\t}\n\tredo() {\n\t\tif (this.draw.isReadonly()) return;\n\t\tthis.draw._historyTimer && (clearTimeout(this.draw._historyTimer), this.draw._historyTimer = 0, this.draw._historyCommit && this.draw._historyCommit());\n\t\tthis.historyManager.redo();';

const HISTORY_DESTROY = '\tdestroy() {\n\t\tthis.container.remove(), this.globalEvent.removeEvent()';
const HISTORY_DESTROY_REPLACEMENT =
  '\tdestroy() {\n\t\tthis._historyTimer && (clearTimeout(this._historyTimer), this._historyTimer = 0), this.container.remove(), this.globalEvent.removeEvent()';

function patchTypingCost(code: string): string {
  let next = code;
  if (next.includes(ROW_MEASURE_CANVAS) && !next.includes('this._rowMeasureCtx')) {
    next = next.replace(ROW_MEASURE_CANVAS, ROW_MEASURE_CANVAS_REPLACEMENT);
  }
  if (next.includes(SUBMIT_HISTORY) && !next.includes('_pendingHistoryIndex')) {
    next = next.replace(SUBMIT_HISTORY, SUBMIT_HISTORY_REPLACEMENT);
  }
  if (next.includes(HISTORY_UNDO) && !next.includes('this.draw._historyCommit')) {
    next = next.replace(HISTORY_UNDO, HISTORY_UNDO_REPLACEMENT);
  }
  if (next.includes(HISTORY_DESTROY) && !next.includes('this._historyTimer && (clearTimeout(this._historyTimer)')) {
    next = next.replace(HISTORY_DESTROY, HISTORY_DESTROY_REPLACEMENT);
  }
  return next;
}

const TABLE_DEFAULT_MARGIN_AS_SINGLE =
  'if (inTable && (e.rowMargin == null || e.rowMargin === r)) multiple = 1;';
const TABLE_EXPLICIT_MARGIN = 'if (inTable && e.rowMargin == null) multiple = 1;';

function patchTableRowMetrics(code: string): string {
  let next = code;
  if (ROW_MARGIN_FN.test(next)) next = next.replace(ROW_MARGIN_FN, ROW_MARGIN_REPLACEMENT);
  if (next.includes(TABLE_DEFAULT_MARGIN_AS_SINGLE)) {
    next = next.replace(TABLE_DEFAULT_MARGIN_AS_SINGLE, TABLE_EXPLICIT_MARGIN);
  }
  if (next.includes(ROW_MARGIN_CALL)) next = next.replace(ROW_MARGIN_CALL, ROW_MARGIN_CALL_REPLACEMENT);
  if (next.includes(TABLE_ROW_FLOOR)) next = next.replace(TABLE_ROW_FLOOR, TABLE_ROW_FLOOR_REPLACEMENT);
  if (next.includes(TABLE_CELL_CALL)) next = next.replace(TABLE_CELL_CALL, TABLE_CELL_CALL_REPLACEMENT);
  if (next.includes(TABLE_SPLIT_CALL)) next = next.replace(TABLE_SPLIT_CALL, TABLE_SPLIT_CALL_REPLACEMENT);
  if (next.includes(ROW_LIST_SIG)) next = next.replace(ROW_LIST_SIG, ROW_LIST_SIG_REPLACEMENT);
  if (next.includes(LINE_BOX)) next = next.replace(LINE_BOX, LINE_BOX_REPLACEMENT);
  if (next.includes(CURSOR_BOX)) next = next.replace(CURSOR_BOX, CURSOR_BOX_REPLACEMENT);
  if (next.includes(CURSOR_TOP) && !next.includes('let lineTop = d[1]')) next = next.replace(CURSOR_TOP, CURSOR_TOP_REPLACEMENT);
  if (next.includes(TABLE_RANGE_STYLE) && !next.includes('let firstSize = o[0].size')) {
    next = next.replace(TABLE_RANGE_STYLE, TABLE_RANGE_STYLE_REPLACEMENT);
  }
  if (next.includes(SIZE_APPLY)) next = next.replace(SIZE_APPLY, SIZE_APPLY_REPLACEMENT);
  return next;
}

/** 控件蓝底按字形高度绘制；表格线宽能保存；表格行高按单元格单倍行距 */
export function canvasEditorHighlightPatch(): Plugin {
  return {
    name: 'canvas-editor-highlight-patch',
    enforce: 'pre',
    transform(code, id) {
      if (!id.includes('canvas-editor')) return;
      let next = code;
      let changed = false;
      if (next.includes(NEEDLE)) {
        next = next.replace(NEEDLE, REPLACEMENT);
        changed = true;
      }
      if (next.includes(BORDER_ATTR_NEEDLE)) {
        next = next.replaceAll(BORDER_ATTR_NEEDLE, BORDER_ATTR_REPLACEMENT);
        changed = true;
      }
      const withTable = patchTableRowMetrics(next);
      if (withTable !== next) {
        next = withTable;
        changed = true;
      }
      const withChoice = patchChoiceControls(next);
      if (withChoice !== next) {
        next = withChoice;
        changed = true;
      }
      const withTyping = patchTypingCost(next);
      if (withTyping !== next) {
        next = withTyping;
        changed = true;
      }
      if (!changed) return;
      return { code: next, map: null };
    },
  };
}
