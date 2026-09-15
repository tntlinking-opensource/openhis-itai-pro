/**
 * 页眉页脚：与 PrintConfig.header/footer 同源。
 * 画在每页纸的边距带上，不进正文 JSON。
 */

export type EmrPageChromeAlign = 'center' | 'left' | 'right';

export interface EmrPageChromeBand {
  align: EmrPageChromeAlign;
  /** 加粗（整段；屏上生效，PDF 暂仍固定小字号） */
  bold?: boolean;
  enabled: boolean;
  /** CSS font-family；空则用编辑器默认 */
  fontFamily?: string;
  /** CSS font-size，如 `9pt` / `12pt`；空则用页眉默认 9pt */
  fontSize?: string;
  heightMm: number;
  italic?: boolean;
  template: string;
  underline?: boolean;
}

/** 页眉页脚可插入字段（存 `{key}`，编辑时显示中文片） */
export interface EmrPageChromeFieldDef {
  key: string;
  label: string;
}

export const EMR_PAGE_CHROME_FIELDS: readonly EmrPageChromeFieldDef[] = [
  { key: 'patientName', label: '患者姓名' },
  { key: 'gender', label: '性别' },
  { key: 'age', label: '年龄' },
  { key: 'orgName', label: '医院' },
  { key: 'deptName', label: '科室' },
  { key: 'documentTypeName', label: '文书类型' },
  { key: 'documentTitle', label: '文书标题' },
  { key: 'page', label: '页码' },
  { key: 'total', label: '总页数' },
  { key: 'printTime', label: '打印时间' },
];

const CHROME_FIELD_LABEL: Record<string, string> = Object.fromEntries(
  EMR_PAGE_CHROME_FIELDS.map((f) => [f.key, f.label]),
);

export interface EmrPageChrome {
  footer: EmrPageChromeBand;
  header: EmrPageChromeBand;
}

export interface EmrPageChromeContext {
  age: string;
  deptName: string;
  documentTitle: string;
  documentTypeName: string;
  gender: string;
  orgName: string;
  patientName: string;
  printTime: string;
  /** 全部元数据取值；任意 `{fieldKey}` 均可解析 */
  values: Record<string, string>;
}

/** 常用页眉占位（插入字段时用；新建默认不再带内容） */
export const DEFAULT_EMR_HEADER_TEMPLATE = '{patientName}  {gender}  {age}';
/** 常用页脚页码文案（插入页码/打印回退用；新建默认不再带内容） */
export const DEFAULT_EMR_FOOTER_TEMPLATE = '第 {page} 页 (共 {total} 页)';

/** 新建默认：页眉可空；页脚默认带页码，纸面可见 */
export function createDefaultEmrPageChrome(): EmrPageChrome {
  return {
    header: {
      align: 'center',
      enabled: true,
      heightMm: 0,
      template: '',
    },
    footer: {
      align: 'center',
      enabled: true,
      heightMm: 12,
      template: DEFAULT_EMR_FOOTER_TEMPLATE,
    },
  };
}

function cloneBand(src: EmrPageChromeBand): EmrPageChromeBand {
  return {
    align: src.align ?? 'center',
    bold: Boolean(src.bold),
    enabled: src.enabled,
    fontFamily: src.fontFamily?.trim() || undefined,
    fontSize: src.fontSize?.trim() || undefined,
    heightMm: src.heightMm,
    italic: Boolean(src.italic),
    template: src.template,
    underline: Boolean(src.underline),
  };
}

export function cloneEmrPageChrome(src: EmrPageChrome): EmrPageChrome {
  return {
    header: cloneBand(src.header),
    footer: cloneBand(src.footer),
  };
}

/**
 * 兼容旧默认：页脚已开启但模板为空时，回填常用页码，避免纸面无页码。
 * 用户若主动清空页脚内容，可先关闭页脚。
 * 页眉：修复编辑态弄丢 `{documentTitle}` 后留下的空 16pt 行。
 */
export function normalizeEmrPageChrome(src: EmrPageChrome): EmrPageChrome {
  const next = cloneEmrPageChrome(src);
  if (next.footer.enabled && !next.footer.template.trim()) {
    next.footer.template = DEFAULT_EMR_FOOTER_TEMPLATE;
  }
  next.header.template = repairChromeHeaderTitlePlaceholder(next.header.template);
  next.header.template = repairInpatientChromeHeaderLogoInline(next.header.template);
  next.header.template = ensureChromeFloatRowSeparateLine(next.header.template);
  next.header.template = repairInpatientChromeHeaderPatientFloat(next.header.template);
  return next;
}

/**
 * 页眉院徽：与院名同一行；x/y 相对 logo 组微调，整组随标题宽度居中。
 */
export function repairInpatientChromeHeaderLogoInline(template: string): string {
  let t = String(template ?? '');
  if (!t.trim() || !CHROME_IMG_TOKEN_GLOBAL_RE.test(t)) return t;
  t = t.replace(/(\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:[^}]+\}\})\s*\r?\n\s*(<span\b)/gi, '$1$2');
  t = t.replace(/\{\{emrImg((?:\s+(?:w|x|y)=\d+)*)?:([^}]+)\}\}/gi, (full, attrsRaw, src) => {
    const attrs = String(attrsRaw ?? '');
    const hasX = /\bx=\d+/i.test(attrs);
    const hasY = /\by=\d+/i.test(attrs);
    if (hasX && hasY) return full;
    const parts: string[] = [];
    const wMatch = attrs.match(/\bw=(\d+)/i);
    if (wMatch) parts.push(`w=${wMatch[1]}`);
    if (hasX) {
      const m = attrs.match(/\bx=(\d+)/i);
      if (m) parts.push(m[0]);
    } else {
      parts.push('x=0');
    }
    if (hasY) {
      const m = attrs.match(/\by=(\d+)/i);
      if (m) parts.push(m[0]);
    } else {
      parts.push('y=0');
    }
    return `{{emrImg ${parts.join(' ')}:${src}}}`;
  });
  return t;
}

/** 住院页眉第三行金标准（姓名后须有病房、床位） */
export const INPATIENT_CHROME_PATIENT_FLOAT_LINE =
  '<span style="font-size:12pt;float:left">姓名：{patientName}　病房：{wardName}　床位：{bedNo}</span>' +
  '<span style="font-size:12pt;float:right">住院号：{inpatientNo}</span>' +
  '<span style="display:block;clear:both;height:0"></span>';

/** 住院页眉 logo+院名+标题+患者 float 行（与 template.json / 屏上金标准一致） */
export function buildInpatientChromeHeaderTemplate(logoSrc: string): string {
  const src = String(logoSrc ?? '').trim();
  const imgPart = src ? `{{emrImg w=39 x=0 y=0:${src}}}` : '';
  const nl = '\n';
  return (
    `${imgPart}<span style="font-size:22pt;font-weight:700">{orgName}</span>${nl}` +
    `<span style="font-size:16pt">{documentTitle}</span>${nl}` +
    INPATIENT_CHROME_PATIENT_FLOAT_LINE
  );
}

/** 从页眉 template 或正文 JSON 提取院徽 src */
export function extractChromeLogoSrc(raw: string): string | null {
  const s = String(raw ?? '');
  const fromToken = /\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:([^}]+)\}\}/.exec(s);
  if (fromToken?.[1]?.trim()) return fromToken[1].trim();
  const fromBody = /"title"\s*:\s*"hospitalLogo"[\s\S]*?"src"\s*:\s*"([^"]+)"/.exec(s);
  return fromBody?.[1]?.trim() ?? null;
}

export function hasHospitalLogoInContent(raw: string): boolean {
  const s = String(raw ?? '');
  return (
    /\{\{emrImg[\s\S]*?:[^}]+\}\}/.test(s) ||
    /"title"\s*:\s*"hospitalLogo"/.test(s) ||
    /hospitalLogo/.test(s)
  );
}

/** 住院页眉刷成金标准（保留原 logo src） */
export function refreshInpatientChromeHeaderFormat(
  src: EmrPageChrome,
  logoSrc: string | null,
): EmrPageChrome {
  const logo = String(logoSrc ?? extractChromeLogoSrc(src.header.template ?? '') ?? '').trim();
  if (!logo) return normalizeEmrPageChrome(cloneEmrPageChrome(src));
  const next = cloneEmrPageChrome(src);
  next.header.enabled = true;
  next.header.align = 'center';
  next.header.heightMm = 0;
  next.header.template = buildInpatientChromeHeaderTemplate(logo);
  if (!next.footer.template?.trim()) {
    next.footer.template = DEFAULT_EMR_FOOTER_TEMPLATE;
  }
  if (next.footer.heightMm <= 0) {
    next.footer.heightMm = 12;
  }
  return normalizeEmrPageChrome(next);
}

function normalizeHeaderTemplateForCompare(template: string): string {
  return String(template ?? '')
    .replace(/\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:[^}]+\}\}/g, '{{emrImg}}')
    .replace(/\s+/g, ' ')
    .trim();
}

/** 页眉结构与金标准不一致（含乱码标签、缺 x/y、缺 float 行等） */
export function needsInpatientChromeHeaderRefresh(
  template: string,
  logoSrc: string | null,
): boolean {
  const logo = String(logoSrc ?? extractChromeLogoSrc(template) ?? '').trim();
  if (!logo) return false;
  const gold = normalizeHeaderTemplateForCompare(buildInpatientChromeHeaderTemplate(logo));
  const current = normalizeHeaderTemplateForCompare(
    normalizeEmrPageChrome({
      header: { align: 'center', enabled: true, heightMm: 0, template },
      footer: {
        align: 'center',
        enabled: true,
        heightMm: 12,
        template: DEFAULT_EMR_FOOTER_TEMPLATE,
      },
    }).header.template,
  );
  return gold !== current;
}

/**
 * 标题行与患者 float 行必须分行：编辑/序列化后二者粘在同一行时，PDF 会把住院号画到标题行右侧。
 */
export function ensureChromeFloatRowSeparateLine(template: string): string {
  let t = String(template ?? '');
  if (!t.trim()) return t;
  t = t.replace(
    /(<span\b[^>]*\bstyle="[^"]*font-size:\s*16pt[^"]*"[^>]*>[\s\S]*?<\/span>)\s*(<span\b[^>]*\bstyle="[^"]*\bfloat:\s*(?:left|right)\b)/gi,
    '$1\n$2',
  );
  t = t.replace(
    /(\{documentTitle\}<\/span>)\s*(<span\b[^>]*\bstyle="[^"]*\bfloat:\s*(?:left|right)\b)/gi,
    '$1\n$2',
  );
  return t;
}

/**
 * 住院页眉 float 行缺 `{wardName}`/`{bedNo}` 时按金标准重写第三行。
 * 门诊页眉无姓名 float 行则原样返回。
 */
export function repairInpatientChromeHeaderPatientFloat(template: string): string {
  const t = String(template ?? '');
  if (!t.trim()) return t;
  // 无住院号占位 → 非住院金标准页眉（如门诊仅院名+标题）
  if (!/\{inpatientNo\}/.test(t) && !/住院号/.test(t)) return t;
  const hasPatientFloat = /\{wardName\}/.test(t) && /\{bedNo\}/.test(t) && /float:\s*left/i.test(t);
  const hasReadablePatientLabels = /姓名：/.test(t) && /病房：/.test(t) && /床位：/.test(t);
  if (hasPatientFloat && hasReadablePatientLabels) return t;

  const gold = INPATIENT_CHROME_PATIENT_FLOAT_LINE;
  // 已有 float:left 行：整行替换
  if (/float:\s*left/i.test(t)) {
    const replaced = t.replace(
      /<span\b[^>]*\bstyle="[^"]*\bfloat:\s*left[^"]*"[^>]*>[\s\S]*?<\/span>\s*<span\b[^>]*\bstyle="[^"]*\bfloat:\s*right[^"]*"[^>]*>[\s\S]*?<\/span>\s*<span\b[^>]*\bstyle="[^"]*\bclear:\s*both[^"]*"[^>]*>[\s\S]*?<\/span>/i,
      gold,
    );
    if (replaced !== t) return replaced;
  }
  // 有标题行则插在其后，否则追加
  if (/font-size:\s*16pt/i.test(t) || /\{documentTitle\}/.test(t)) {
    return `${t.replace(/\s+$/, '')}\n${gold}`;
  }
  return t.includes('\n') ? `${t}\n${gold}` : `${t}\n${gold}`;
}

/**
 * 页眉编辑曾把 `{documentTitle}` 弄丢，只剩空的 16pt span 或直接接到「姓名」行，
 * 屏上中间一行空白。载入时补回占位，供 context 填类型名/标题。
 */
export function repairChromeHeaderTitlePlaceholder(template: string): string {
  const t = String(template ?? '');
  if (!t.trim()) return t;
  if (/\{documentTitle\}/.test(t) || /\{documentTypeName\}/.test(t)) return t;

  const filledEmpty = t.replace(
    /(<span\b[^>]*\bstyle="[^"]*font-size:\s*16pt[^"]*"[^>]*>)\s*(<\/span>)/gi,
    '$1{documentTitle}$2',
  );
  if (filledEmpty !== t) return filledEmpty;

  // 无标题行：在「姓名」float 行前插入
  if (/float:\s*left/i.test(t) && (/\{orgName\}/.test(t) || /font-size:\s*22pt/i.test(t))) {
    return t.replace(
      /\n(?=<span\b[^>]*\bfloat:\s*left)/i,
      '\n<span style="font-size:16pt">{documentTitle}</span>\n',
    );
  }
  return t;
}

/** 门诊页眉标题行不得绑定主诉/诊断等临床字段 */
const OUTPATIENT_CHROME_TITLE_ALIAS_RE =
  /\{(?:chiefComplaint|tcmDiagnosis|westernDiagnosis|diagnosisText)\}/gi;

/**
 * 门诊病历页眉 16pt 行固定为 `{documentTitle}`。
 * 避免模板把主诉/诊断插进标题，或把病名写死后纸面标题跟着变。
 */
export function pinOutpatientChromeHeaderTitle(template: string): string {
  let t = String(template ?? '');
  if (!t.trim()) return t;
  t = t.replace(
    /(<span\b[^>]*\bstyle="[^"]*font-size:\s*16pt[^"]*"[^>]*>)[\s\S]*?(<\/span>)/i,
    '$1{documentTitle}$2',
  );
  t = t.replace(OUTPATIENT_CHROME_TITLE_ALIAS_RE, '{documentTitle}');
  return t;
}

/** 把整段字体样式写到页眉/页脚 DOM（编辑态与只读态共用） */
export function applyEmrPageChromeBandDomStyle(el: HTMLElement, conf: EmrPageChromeBand) {
  const align = conf.align ?? 'center';
  el.style.textAlign = align;
  // 外层 flex：子块（inner）水平对齐；字段片在 inner 内保持行内
  el.style.justifyContent = '';
  el.style.alignItems = align === 'left' ? 'flex-start' : align === 'right' ? 'flex-end' : 'center';
  el.style.fontFamily = conf.fontFamily?.trim() || '';
  el.style.fontSize = conf.fontSize?.trim() || '';
  el.style.fontWeight = conf.bold ? '700' : '';
  el.style.fontStyle = conf.italic ? 'italic' : '';
  el.style.textDecoration = conf.underline ? 'underline' : '';
}

/**
 * 屏上正文边距：工具栏页边距与 heightMm 缓存（DOM 实测回写）取大。
 * heightMm ≤ 0 表示尚未实测，仅用页边距。
 */
export function resolveScreenChromeBandMarginMm(
  storedHeightMm: number,
  pageMarginMm: number,
): number {
  if (storedHeightMm > 0) {
    return Math.max(pageMarginMm, storedHeightMm);
  }
  return pageMarginMm;
}

/**
 * 屏上分页/边距真源：页边距与 DOM 实测 heightMm 取大。
 * 不用 PDF 模板估算（常到 50mm+，而屏上真实页眉可能只有 ~25mm，会撑出大空白）。
 * heightMm≤0 表示尚未实测，首帧仅用页边距，finalize 实测后回写。
 */
export function resolveScreenChromeBandPaddingMm(
  band: EmrPageChromeBand,
  pageMarginMm: number,
): number {
  if (band.heightMm > 0) {
    return Math.max(pageMarginMm, band.heightMm);
  }
  return pageMarginMm;
}

/** 含 float/绝对定位子节点时，取内容区真实底边（scrollHeight 会漏算浮动行） */
function measureChromeContentBottomPx(container: HTMLElement): number {
  const top = container.getBoundingClientRect().top;
  let bottom = top;
  const visit = (el: HTMLElement) => {
    const r = el.getBoundingClientRect();
    if (r.height > 0.5) bottom = Math.max(bottom, r.bottom);
    for (const child of el.children) {
      if (child instanceof HTMLElement) visit(child);
    }
  };
  visit(container);
  return Math.max(bottom - top, 0);
}

/** 外壳到全部子节点真实绘制底边（overflow:visible 时比 shellH 更准） */
function measureChromeBandVisualHeightPx(bandEl: HTMLElement): number {
  const top = bandEl.getBoundingClientRect().top;
  let bottom = bandEl.getBoundingClientRect().bottom;
  for (const el of bandEl.querySelectorAll<HTMLElement>('*')) {
    const r = el.getBoundingClientRect();
    if (r.height > 0.5) bottom = Math.max(bottom, r.bottom);
  }
  return Math.max(bottom - top, 0);
}

/** DOM 实测页眉/页脚带高度（含外壳 padding；shell min-height 已绑固定页边距，可安全取大） */
export function measureEmrChromeBandHeightPx(bandEl: HTMLElement): number {
  const inner = bandEl.querySelector(':scope > .simple-editor__page-band-inner');
  let innerH = 0;
  if (inner instanceof HTMLElement) {
    const prevMin = inner.style.minHeight;
    const prevMax = inner.style.maxHeight;
    const prevHeight = inner.style.height;
    inner.style.minHeight = '0px';
    inner.style.maxHeight = 'none';
    inner.style.height = 'auto';
    innerH = Math.max(
      inner.scrollHeight,
      inner.offsetHeight,
      measureChromeContentBottomPx(inner),
      28,
    );
    inner.style.minHeight = prevMin;
    inner.style.maxHeight = prevMax;
    inner.style.height = prevHeight;
  } else {
    innerH = Math.max(bandEl.scrollHeight, bandEl.offsetHeight);
  }
  const cs = getComputedStyle(bandEl);
  const pad = (Number.parseFloat(cs.paddingTop) || 0) + (Number.parseFloat(cs.paddingBottom) || 0);
  const shellH = bandEl.getBoundingClientRect().height;
  const visualH = measureChromeBandVisualHeightPx(bandEl);
  return Math.ceil(Math.max(innerH + pad, shellH, visualH));
}

/** 屏上实测高度（mm），含 1mm 安全余量 */
export function measureEmrChromeBandHeightMm(bandEl: HTMLElement): number {
  const px = measureEmrChromeBandHeightPx(bandEl);
  const mm = (px * 25.4) / 96;
  return Math.ceil((mm + CHROME_SCREEN_MEASURE_BUFFER_MM) * 10) / 10;
}

function escapeChromeHtml(text: string): string {
  return text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function escapeChromeAttr(text: string): string {
  return text.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
}

/** 页眉模板内嵌图片：`{{emrImg:src}}` / `{{emrImg w=80 x=10 y=4:src}}`（src 不得含 `}`） */
const CHROME_IMG_TOKEN_RE = /\{\{emrImg(?:\s+w=(\d+))?(?:\s+x=(\d+))?(?:\s+y=(\d+))?:([^}]+)\}\}/;
const CHROME_IMG_TOKEN_GLOBAL_RE = /\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:[^}]+\}\}/g;

/** 去掉页眉 HTML 标签，保留换行与 `{fieldKey}` 占位（页脚等纯文本场景） */
function stripChromeHtmlToPlainText(raw: string): string {
  let s = String(raw ?? '').replace(CHROME_IMG_TOKEN_GLOBAL_RE, '');
  s = s.replace(/<br\s*\/?>/gi, '\n');
  s = s.replace(/<\/div>\s*<div[^>]*>/gi, '\n');
  s = s.replace(/<\/div>/gi, '\n');
  s = s.replace(/<div[^>]*>/gi, '');
  s = s.replace(/<span[^>]*>/gi, '').replace(/<\/span>/gi, '');
  s = s.replace(/<(?:b|strong|i|em|u)[^>]*>/gi, '').replace(/<\/(?:b|strong|i|em|u)>/gi, '');
  s = s.replace(/<[^>]+>/g, '');
  s = s
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"');
  return normalizeChromeTemplateText(s);
}

/**
 * 页脚等纯文本：去图片 token、去 HTML。
 */
export function sanitizeChromeTemplateForPdf(template: string): string {
  return stripChromeHtmlToPlainText(template);
}

const STYLED_SPAN_FOR_PDF_RE = /<span[^>]*style="([^"]*)"[^>]*>([\s\S]*?)<\/span>/gi;

/** 页眉 PDF 需保留的 style 项（字号 + 屏上 float 左右行） */
function pickChromeSpanStyleForPdf(styleAttr: string): string {
  const parts: string[] = [];
  const fontSize = styleAttr.match(/font-size:\s*([^;"']+)/i)?.[1]?.trim();
  if (fontSize) parts.push(`font-size:${fontSize}`);
  const fontWeight = styleAttr.match(/font-weight:\s*([^;"']+)/i)?.[1]?.trim();
  if (fontWeight) parts.push(`font-weight:${fontWeight}`);
  const float = styleAttr.match(/float:\s*(left|right)/i)?.[1]?.toLowerCase();
  if (float) parts.push(`float:${float}`);
  if (/clear:\s*both/i.test(styleAttr)) parts.push('clear:both');
  if (/display:\s*block/i.test(styleAttr) && /clear:\s*both/i.test(styleAttr)) {
    parts.push('display:block', 'height:0');
  }
  return parts.join(';');
}

/**
 * 页眉 PDF：保留 `{{emrImg}}`、字号/字重 span，以及 float:left/right（姓名·病房·床位 / 住院号行）。
 */
export function prepareChromeTemplateForPdf(template: string): string {
  let s = repairInpatientChromeHeaderLogoInline(String(template ?? ''));
  s = repairChromeHeaderTitlePlaceholder(s);
  s = ensureChromeFloatRowSeparateLine(s);
  s = repairInpatientChromeHeaderPatientFloat(s);
  s = s.replace(/<br\s*\/?>/gi, '\n');
  s = s.replace(/<\/div>\s*<div[^>]*>/gi, '\n');
  s = s.replace(/<\/div>/gi, '\n');
  s = s.replace(/<div[^>]*>/gi, '');
  // 与屏上同构：先把 float 标签后的值/占位收进 span，再挑 PDF 样式
  s = s
    .split('\n')
    .map((line) => coalesceChromeFloatAdjacentHtml(line))
    .join('\n');
  const styledSpans: string[] = [];
  s = s.replace(STYLED_SPAN_FOR_PDF_RE, (_, styleRaw, text) => {
    const style = pickChromeSpanStyleForPdf(String(styleRaw ?? ''));
    if (!style) return String(text ?? '');
    const idx = styledSpans.length;
    styledSpans.push(`<span style="${style}">${text}</span>`);
    return `\u0000SPAN${idx}\u0000`;
  });
  s = s.replace(/<(?:b|strong|i|em|u)[^>]*>/gi, '').replace(/<\/(?:b|strong|i|em|u)>/gi, '');
  s = s.replace(/<[^>]+>/g, '');
  s = s.replace(/\u0000SPAN(\d+)\u0000/g, (_, i) => styledSpans[Number(i)] ?? '');
  s = s
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"');
  return normalizeChromeTemplateText(s);
}

const CHROME_BAND_SHELL_PAD_MM = 4;
const CHROME_FLOAT_ROW_EXTRA_MM = 3;
/** 屏上 .simple-editor__page-header padding-top:8px，PDF 页眉内容区须同原点 */
export const CHROME_HEADER_SHELL_TOP_PAD_PX = 8;
/** 院徽 float 时与右侧标题间距（屏/PDF 共用） */
export const CHROME_LOGO_TEXT_GAP_PX = 8;
const PDF_CHROME_TOP_PAD_MM = (CHROME_HEADER_SHELL_TOP_PAD_PX * 25.4) / 96;
/** 屏上 DOM 实测回写时的安全余量（mm），避免亚像素舍入压线 */
/** 页眉含 logo+22pt+16pt+float 行，实测易贴页边距；略加大避免正文顶边压字 */
const CHROME_SCREEN_MEASURE_BUFFER_MM = 5;

/** 解析 CSS 长度到毫米（支持 mm / pt / px） */
export function parseCssLengthToMm(raw: string | undefined, fallbackMm: number): number {
  const s = String(raw ?? '').trim();
  if (!s) return fallbackMm;
  const mm = s.match(/^([\d.]+)\s*mm$/i);
  if (mm) return Number(mm[1]);
  const pt = s.match(/^([\d.]+)\s*pt$/i);
  if (pt) return (Number(pt[1]) * 25.4) / 72;
  const px = s.match(/^([\d.]+)\s*px$/i);
  if (px) return (Number(px[1]) * 25.4) / 96;
  const n = Number(s);
  return Number.isFinite(n) ? n : fallbackMm;
}

function cssFontSizeToLineMm(cssSize: string): number {
  const fontMm = parseCssLengthToMm(cssSize, (9 * 25.4) / 72);
  return fontMm * 1.4;
}

/**
 * PDF 专用：按模板字号、图片、行数估算页眉/页脚带高度（屏上不靠此值撑布局）。
 * 返回值 ≥ pageMarginMm，且不低于 storedHeightMm（若已配置）。
 */
export function resolveChromeBandHeightMm(
  template: string,
  storedHeightMm: number,
  pageMarginMm: number,
): number {
  const t = String(template ?? '').trim();
  if (!t) return Math.max(pageMarginMm, storedHeightMm > 0 ? storedHeightMm : 0);

  let totalMm = 0;
  for (const line of t.split('\n')) {
    if (!line.trim() && !/\{\{emrImg/i.test(line)) continue;
    let lineMm = cssFontSizeToLineMm('9pt');
    for (const m of line.matchAll(/font-size:\s*([^;"']+)/gi)) {
      lineMm = Math.max(lineMm, cssFontSizeToLineMm(m[1] ?? ''));
    }
    const img = line.match(/\{\{emrImg(?:\s+w=(\d+))?/);
    if (img) {
      const imgMm = ((Number(img[1]) || 32) * 25.4) / 96 + 1;
      lineMm = Math.max(lineMm, imgMm);
    }
    if (/float:\s*(left|right)/i.test(line)) {
      lineMm += CHROME_FLOAT_ROW_EXTRA_MM;
    }
    totalMm += lineMm;
  }
  totalMm += CHROME_BAND_SHELL_PAD_MM + PDF_CHROME_TOP_PAD_MM;
  const estimated = Math.max(pageMarginMm, totalMm);
  if (storedHeightMm > 0) {
    return Math.max(storedHeightMm, estimated);
  }
  return estimated;
}

/** 按行数 / 图片估算页眉/页脚带高度（PDF 兼容入口） */
export function estimateChromeBandHeightMmForPdf(template: string, storedHeightMm: number): number {
  const t = template.trim();
  if (!t) return 0;
  return resolveChromeBandHeightMm(t, storedHeightMm, 0);
}

/**
 * 屏上页眉/页脚在 top/bottom 边距带内；PDF 后端 marginTop = topMm + bandHeightMm，此处扣回 band 高度避免双倍留白。
 */
export function adjustPageMarginMmForPdfBand(
  marginMm: number | undefined,
  bandHeightMm: number,
): number | undefined {
  if (marginMm == null) return undefined;
  if (bandHeightMm <= 0) return marginMm;
  return Math.max(2, marginMm - bandHeightMm);
}
const CHROME_LINE_SPLIT_RE = /(\{\{emrImg(?:\s+(?:w|x|y)=\d+)*:[^}]+\}\}|\{\w+\})/g;

export type ChromeFieldLabelOf = (key: string) => string | undefined;

/** 页眉字段片展示名：侧栏元数据 label > 内置 > key */
export function resolveChromeFieldLabel(key: string, labelOf?: ChromeFieldLabelOf): string {
  const fromMeta = labelOf?.(key)?.trim();
  if (fromMeta) return fromMeta;
  return CHROME_FIELD_LABEL[key] ?? key;
}

function chromeTokenToHtml(part: string, labelOf?: ChromeFieldLabelOf): string {
  const imgM = /^\{\{emrImg(?:\s+w=(\d+))?(?:\s+x=(\d+))?(?:\s+y=(\d+))?:([^}]+)\}\}$/.exec(part);
  const widthRaw = imgM?.[1];
  const xRaw = imgM?.[2];
  const yRaw = imgM?.[3];
  const src = imgM?.[4]?.trim();
  if (src) {
    const widthAttr = widthRaw && Number(widthRaw) > 0 ? ` width="${Number(widthRaw)}"` : '';
    const styleParts: string[] = ['position:absolute', 'margin:0'];
    const x = xRaw != null ? Number(xRaw) : 0;
    const y = yRaw != null ? Number(yRaw) : 0;
    if (Number.isFinite(x) && x >= 0) styleParts.push(`left:${Math.round(x)}px`);
    if (Number.isFinite(y) && y >= 0) styleParts.push(`top:${Math.round(y)}px`);
    if (widthRaw && Number(widthRaw) > 0) {
      styleParts.push(
        `width:${Number(widthRaw)}px`,
        'max-width:none',
        'max-height:none',
        'height:auto',
      );
    }
    const styleAttr = ` style="${styleParts.join(';')}"`;
    return (
      `<img class="simple-editor__chrome-img" src="${escapeChromeAttr(src)}"` +
      `${widthAttr}${styleAttr} alt="" draggable="false" />`
    );
  }
  const m = /^\{(\w+)\}$/.exec(part);
  const key = m?.[1];
  if (!key) {
    return escapeChromeHtml(part);
  }
  const label = resolveChromeFieldLabel(key, labelOf);
  return (
    `<span class="simple-editor__chrome-field" data-chrome-field="${key}"` +
    ` contenteditable="false">${escapeChromeHtml(label)}</span>`
  );
}

function chromeLineToHtml(line: string, labelOf?: ChromeFieldLabelOf): string {
  return line
    .split(CHROME_LINE_SPLIT_RE)
    .map((part) => {
      if (!part) return '';
      if (CHROME_IMG_TOKEN_RE.test(part) || /^\{\w+\}$/.test(part)) {
        return chromeTokenToHtml(part, labelOf);
      }
      // 局部格式 span 被 {field} 拆开后，开/闭标签片段须原样保留（否则编辑态露出字面 </span>）
      if (/<\/?span\b/i.test(part)) return part;
      return escapeChromeHtml(part);
    })
    .join('');
}

/** 行首 emrImg 且同行有标题/字段（非患者 float 行） */
function isChromeLineLogoWithText(line: string): boolean {
  const t = line.trim();
  if (!/^\{\{emrImg[\s\S]+\}\}/.test(t)) return false;
  if (/float:\s*(?:left|right)/i.test(t)) return false;
  return t.replace(/^\{\{emrImg[\s\S]+?\}\}/, '').trim().length > 0;
}

interface ChromeLineLogoLead {
  gapPx: number;
  widthPx: number;
}

function parseChromeLineLogoLead(line: string): ChromeLineLogoLead | null {
  const m = /^(\{\{emrImg(?:\s+w=(\d+))?(?:\s+x=(\d+))?(?:\s+y=(\d+))?:[^}]+\}\})([\s\S]*)$/.exec(
    line.trim(),
  );
  if (!m) return null;
  const rest = m[5] ?? '';
  if (!rest.trim()) return null;
  return {
    widthPx: Number(m[2]) > 0 ? Number(m[2]) : 39,
    gapPx: CHROME_LOGO_TEXT_GAP_PX,
  };
}

/** 标题区固定 padding-left（仅 logo 宽+间距），x/y 微调不挤文字 */
function wrapChromeLineAfterLogo(html: string, line: string): string {
  const lead = parseChromeLineLogoLead(line);
  if (!lead) return html;
  const imgMatch = html.match(/<img\b[\s\S]*?\/?>/i);
  if (!imgMatch || imgMatch.index == null) return html;
  const imgHtml = imgMatch[0];
  const after = html.slice(imgMatch.index + imgHtml.length);
  if (!after.trim()) return html;
  const pad = lead.widthPx + lead.gapPx;
  return (
    `${imgHtml}<span class="simple-editor__chrome-line-after-logo"` +
    ` style="display:inline-block;padding-left:${pad}px;box-sizing:border-box">${after}</span>`
  );
}

/** logo+标题包成 inline-block；父级居中时整组随标题宽度移动 */
function wrapChromeLineLogoRow(html: string, line: string): string {
  if (!isChromeLineLogoWithText(line)) return html;
  return (
    `<span class="simple-editor__chrome-logo-row"` +
    ` style="display:inline-block;position:relative;text-align:left;max-width:100%">${html}</span>`
  );
}

function renderChromeLineHtml(line: string, labelOf?: ChromeFieldLabelOf): string {
  let html = coalesceChromeFloatAdjacentHtml(chromeLineToHtml(line, labelOf));
  html = wrapChromeLineAfterLogo(html, line);
  return wrapChromeLineLogoRow(html, line);
}

/**
 * 可并入 float:left/right 标签 span 的后续片段：字段占位、字段片、纯文本。
 * 遇其它标签（含另一侧 float / clear 行）即停。
 */
function matchChromeFloatAbsorbablePrefix(s: string): string | null {
  const chip = s.match(/^<span\b[^>]*\bdata-chrome-field="[^"]*"[^>]*>[\s\S]*?<\/span>/i);
  if (chip) return chip[0];
  const ph = s.match(/^\{(\w+)\}/);
  if (ph) return ph[0];
  const text = s.match(/^[^<{]+/);
  if (text) return text[0];
  return null;
}

/**
 * 将 float:left/right 标签后的相邻正文/字段收进同一 span。
 * 页眉整段多为 text-align:center：只 float 标签、值留在正常流时，姓名/住院号会居中错位。
 */
export function coalesceChromeFloatAdjacentHtml(html: string): string {
  if (!html || !/float:\s*(?:left|right)/i.test(html)) return html;

  const floatSpanRe =
    /<span\b(?=[^>]*\bstyle="[^"]*\bfloat:\s*(?:left|right)\b)[^>]*>[\s\S]*?<\/span>/gi;
  let out = '';
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = floatSpanRe.exec(html))) {
    out += html.slice(last, m.index);
    let span = m[0];
    let cursor = m.index + m[0].length;
    while (true) {
      const piece = matchChromeFloatAbsorbablePrefix(html.slice(cursor));
      if (!piece) break;
      span = span.replace(/<\/span>$/i, `${piece}</span>`);
      cursor += piece.length;
    }
    out += span;
    last = cursor;
    floatSpanRe.lastIndex = cursor;
  }
  out += html.slice(last);
  return out;
}

/**
 * 把模板转成 contenteditable HTML。
 * `标题\n` → `标题<div><br></div>`（与 Chrome 回车结构一致，空行才保得住）。
 * 任意 `{fieldKey}` 都成字段片（含侧栏元数据）。
 */
export function buildChromeEditHtml(template: string, labelOf?: ChromeFieldLabelOf): string {
  const lines = template.split('\n');
  return lines
    .map((line) => {
      if (!line) return '<div><br></div>';
      return `<div>${renderChromeLineHtml(line, labelOf)}</div>`;
    })
    .join('');
}

/** 只读态单行：占位已解析为正文；保留 img token 与 inline span */
function chromeDisplayLineToHtml(line: string): string {
  return renderChromeLineHtml(line);
}

/**
 * 只读态展示：与 buildChromeEditHtml 同构（按行包 div），避免 float 行在展示态与编辑态高度不一致。
 */
export function formatChromeDisplayHtml(text: string): string {
  if (!text) return '';
  const lines = text.split('\n');
  return lines
    .map((line) => {
      if (!line) return '<div><br></div>';
      return `<div>${chromeDisplayLineToHtml(line)}</div>`;
    })
    .join('');
}

function normalizeChromeTemplateText(s: string): string {
  return (
    s
      .replaceAll('\u00a0', ' ')
      .replaceAll('\u200b', '')
      .replaceAll('\r\n', '\n')
      .replaceAll('\r', '\n')
      .replace(/[ \t]+\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n')
      .replace(/[ \t]{2,}/g, ' ')
      // 只去掉首尾空格，保留换行（否则回车空行退出后会丢）
      .replace(/^[ \t]+|[ \t]+$/g, '')
  );
}

function serializeChromeImage(el: HTMLElement): string {
  const src = el.getAttribute('src')?.trim() ?? '';
  if (!src || src.includes('}')) return '';
  const widthAttr = el.getAttribute('width');
  const styleW = el.style.width?.endsWith('px') ? Number.parseFloat(el.style.width) : Number.NaN;
  let width = 0;
  if (widthAttr && Number(widthAttr) > 0) {
    width = Math.round(Number(widthAttr));
  } else if (Number.isFinite(styleW) && styleW > 0) {
    width = Math.round(styleW);
  }
  const parts: string[] = [];
  if (width > 0) parts.push(`w=${width}`);
  const x =
    el.style.position === 'absolute' && el.style.left?.endsWith('px')
      ? Math.round(Number.parseFloat(el.style.left))
      : Number.NaN;
  const y =
    el.style.position === 'absolute' && el.style.top?.endsWith('px')
      ? Math.round(Number.parseFloat(el.style.top))
      : Number.NaN;
  if (Number.isFinite(x) && x >= 0) parts.push(`x=${x}`);
  if (Number.isFinite(y) && y >= 0) parts.push(`y=${y}`);
  if (parts.length) {
    return `{{emrImg ${parts.join(' ')}:${src}}}`;
  }
  return `{{emrImg:${src}}}`;
}

const CHROME_INLINE_STYLE_TAGS = /^(SPAN|B|STRONG|I|EM|U|FONT)$/;

function collectChromeInlineStyle(el: HTMLElement): string {
  const parts: string[] = [];
  const cs = el.style;
  if (cs.fontSize) parts.push(`font-size:${cs.fontSize}`);
  if (cs.fontFamily) parts.push(`font-family:${cs.fontFamily}`);
  const fw = cs.fontWeight;
  if (fw && (fw === '700' || fw === 'bold' || Number(fw) >= 700)) {
    parts.push('font-weight:700');
  }
  if (cs.fontStyle === 'italic') parts.push('font-style:italic');
  const deco = cs.textDecorationLine || cs.textDecoration;
  if (deco?.includes('underline')) parts.push('text-decoration:underline');
  // 姓名/住院号行依赖 float；序列化丢失会导致屏上/PDF 左右错位
  const float = String(cs.cssFloat || cs.float || '').toLowerCase();
  if (float === 'left' || float === 'right') {
    parts.push(`float:${float}`);
  }
  const clear = String(cs.clear || '').toLowerCase();
  if (clear === 'both') parts.push('clear:both');
  if (String(cs.display || '').toLowerCase() === 'block' && clear === 'both') {
    parts.push('display:block');
    const h = String(cs.height || '').trim();
    if (h === '0' || h === '0px') parts.push('height:0');
  }
  const tag = el.tagName;
  if ((tag === 'B' || tag === 'STRONG') && !parts.some((p) => p.startsWith('font-weight'))) {
    parts.push('font-weight:700');
  }
  if ((tag === 'I' || tag === 'EM') && !parts.some((p) => p.startsWith('font-style'))) {
    parts.push('font-style:italic');
  }
  if (tag === 'U' && !parts.some((p) => p.startsWith('text-decoration'))) {
    parts.push('text-decoration:underline');
  }
  return parts.join(';');
}

function serializeChromeStyledElement(el: HTMLElement): string {
  const style = collectChromeInlineStyle(el);
  const inner = serializeChromeInline(el);
  if (!style) return inner;
  return `<span style="${escapeChromeAttr(style)}">${inner}</span>`;
}

function serializeChromeInline(parent: Node): string {
  let s = '';
  parent.childNodes.forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      s += node.textContent ?? '';
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    if (el.classList.contains('simple-editor__chrome-logo-row')) {
      s += serializeChromeInline(el);
      return;
    }
    if (el.classList.contains('simple-editor__chrome-line-after-logo')) {
      s += serializeChromeInline(el);
      return;
    }
    if (el.tagName === 'IMG') {
      s += serializeChromeImage(el);
      return;
    }
    const fieldKey = el.dataset.chromeField;
    if (fieldKey) {
      s += `{${fieldKey}}`;
      return;
    }
    if (el.tagName === 'BR') {
      s += '\n';
      return;
    }
    if (CHROME_INLINE_STYLE_TAGS.test(el.tagName)) {
      s += serializeChromeStyledElement(el);
      return;
    }
    s += serializeChromeInline(el);
  });
  return s;
}

/**
 * 从纸面编辑区收回模板字符串。
 * Chrome 回车常见结构：`标题` + `<div><br></div>` → 须得到 `标题\n`（空行在标题下）。
 */
export function serializeChromeEditHost(host: HTMLElement): string {
  const lines: string[] = [];
  let pendingInline = '';

  const flushPending = () => {
    if (!pendingInline) return;
    const parts = pendingInline.split('\n');
    for (let i = 0; i < parts.length; i++) {
      if (i === 0 && lines.length > 0) {
        lines[lines.length - 1] = `${lines[lines.length - 1] ?? ''}${parts[i]}`;
      } else if (i === 0) {
        lines.push(parts[i] ?? '');
      } else {
        lines.push(parts[i] ?? '');
      }
    }
    pendingInline = '';
  };

  host.childNodes.forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      pendingInline += node.textContent ?? '';
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    if (el.tagName === 'IMG') {
      pendingInline += serializeChromeImage(el);
      return;
    }
    if (el.dataset.chromeField) {
      pendingInline += `{${el.dataset.chromeField}}`;
      return;
    }
    if (el.tagName === 'BR') {
      pendingInline += '\n';
      return;
    }
    if (el.tagName === 'DIV' || el.tagName === 'P') {
      flushPending();
      // <div><br></div> / 空 div = 空行；有字则是新段
      const inner = serializeChromeInline(el).replace(/\n$/g, '');
      lines.push(inner);
      return;
    }
    if (CHROME_INLINE_STYLE_TAGS.test(el.tagName)) {
      pendingInline += serializeChromeStyledElement(el);
      return;
    }
    pendingInline += serializeChromeInline(el);
  });
  flushPending();

  return normalizeChromeTemplateText(lines.join('\n'));
}

/** 创建插入到光标处的字段片（label 缺省时用内置名或 key） */
export function createChromeFieldChip(key: string, label?: string): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = 'simple-editor__chrome-field';
  span.dataset.chromeField = key;
  span.contentEditable = 'false';
  span.textContent = label?.trim() || resolveChromeFieldLabel(key);
  return span;
}

/** 页眉编辑区是否有非折叠选区（用于局部格式，勿改整段 band 样式） */
export function hasChromeTextSelection(host: HTMLElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return false;
  const range = sel.getRangeAt(0);
  return host.contains(range.commonAncestorContainer);
}

/** 对选区应用局部字号/字体（无选区返回 false，由调用方改整段默认） */
export function applyChromeSelectionTextStyle(
  host: HTMLElement,
  style: { fontFamily?: string; fontSize?: string },
): boolean {
  if (!hasChromeTextSelection(host)) return false;
  host.focus();
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  const span = document.createElement('span');
  if (style.fontSize) span.style.fontSize = style.fontSize;
  if (style.fontFamily) span.style.fontFamily = style.fontFamily;
  try {
    range.surroundContents(span);
  } catch {
    const frag = range.extractContents();
    span.appendChild(frag);
    range.insertNode(span);
  }
  range.setStartAfter(span);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
  return true;
}

/** 对选区切换加粗/斜体/下划线（无选区返回 false） */
export function toggleChromeSelectionMark(
  host: HTMLElement,
  mark: 'bold' | 'italic' | 'underline',
): boolean {
  if (!hasChromeTextSelection(host)) return false;
  host.focus();
  if (mark === 'bold') document.execCommand('bold');
  else if (mark === 'italic') document.execCommand('italic');
  else document.execCommand('underline');
  return true;
}

/** 创建插入到页眉/页脚光标处的图片（存模板为 `{{emrImg:...}}`） */
export function createChromeImageEl(src: string, widthPx?: number): HTMLImageElement {
  const img = document.createElement('img');
  img.className = 'simple-editor__chrome-img';
  img.src = src.trim();
  img.alt = '';
  img.draggable = false;
  img.style.position = 'absolute';
  img.style.left = '0px';
  img.style.top = '0px';
  img.style.margin = '0';
  if (widthPx && widthPx > 0) {
    const w = Math.round(widthPx);
    img.setAttribute('width', String(w));
    img.style.width = `${w}px`;
    img.style.maxWidth = 'none';
    img.style.maxHeight = 'none';
    img.style.height = 'auto';
  }
  return img;
}

/** 确保 logo 组可组内绝对定位，并同步标题预留宽 */
export function ensureChromeLogoRowLayout(row: HTMLElement, img: HTMLImageElement) {
  row.classList.add('simple-editor__chrome-logo-row');
  row.style.position = 'relative';
  row.style.display = 'inline-block';
  row.style.textAlign = 'left';
  row.style.maxWidth = '100%';
  row.style.overflow = 'visible';
  img.style.position = 'absolute';
  img.style.margin = '0';
  syncChromeLineAfterLogoPadding(row, img);
}

/** logo 定位参照：优先 logo 组，否则页眉编辑区 */
export function resolveChromeImagePositionHost(
  img: HTMLImageElement,
  fallback: HTMLElement,
): HTMLElement {
  const row = img.closest('.simple-editor__chrome-logo-row');
  return row instanceof HTMLElement ? row : fallback;
}

/** 将页眉图转为组内绝对定位（便于拖拽） */
export function absolutizeChromeImage(img: HTMLImageElement, host: HTMLElement) {
  ensureChromeLogoRowLayout(host, img);
  if (img.style.position === 'absolute' && img.style.left && img.style.top) return;
  const hostRect = host.getBoundingClientRect();
  const imgRect = img.getBoundingClientRect();
  img.style.position = 'absolute';
  img.style.left = `${Math.max(0, Math.round(imgRect.left - hostRect.left + host.scrollLeft))}px`;
  img.style.top = `${Math.max(0, Math.round(imgRect.top - hostRect.top + host.scrollTop))}px`;
  img.style.margin = '0';
}

/** 读取页眉图片组内 left/top（px） */
export function readChromeImagePosition(
  img: HTMLImageElement,
  host: HTMLElement,
): { x: number; y: number } {
  if (
    img.style.position === 'absolute' &&
    img.style.left?.endsWith('px') &&
    img.style.top?.endsWith('px')
  ) {
    return {
      x: Math.max(0, Math.round(Number.parseFloat(img.style.left))),
      y: Math.max(0, Math.round(Number.parseFloat(img.style.top))),
    };
  }
  const hostRect = host.getBoundingClientRect();
  const imgRect = img.getBoundingClientRect();
  return {
    x: Math.max(0, Math.round(imgRect.left - hostRect.left + host.scrollLeft)),
    y: Math.max(0, Math.round(imgRect.top - hostRect.top + host.scrollTop)),
  };
}

/** 设置页眉图片组内 left/top（px） */
export function applyChromeImagePosition(
  img: HTMLImageElement,
  host: HTMLElement,
  x: number,
  y: number,
) {
  ensureChromeLogoRowLayout(host, img);
  img.style.position = 'absolute';
  img.style.left = `${Math.max(0, Math.round(x))}px`;
  img.style.top = `${Math.max(0, Math.round(y))}px`;
  img.style.margin = '0';
}

/** logo 宽度变更后同步标题 padding-left（x/y 微调不触发） */
export function syncChromeLineAfterLogoPadding(host: HTMLElement, img: HTMLImageElement) {
  const wrap = img.nextElementSibling;
  if (
    !(wrap instanceof HTMLElement) ||
    !wrap.classList.contains('simple-editor__chrome-line-after-logo')
  ) {
    return;
  }
  const wAttr = img.getAttribute('width') || img.style.width;
  const w = Number.parseFloat(String(wAttr));
  const widthPx = Number.isFinite(w) && w > 0 ? Math.round(w) : Math.round(img.clientWidth || 39);
  wrap.style.paddingLeft = `${widthPx + CHROME_LOGO_TEXT_GAP_PX}px`;
}

/** 设置页眉图片显示宽度并写回 style/width */
export function applyChromeImageWidth(img: HTMLImageElement, widthPx: number) {
  const w = Math.max(16, Math.round(widthPx));
  img.setAttribute('width', String(w));
  img.style.width = `${w}px`;
  img.style.maxWidth = 'none';
  img.style.maxHeight = 'none';
  img.style.height = 'auto';
}

/**
 * 字段片 contenteditable=false，光标贴在片后时常无法继续输入。
 * 在片后放 ZWSP 文本节点并把光标置于其后。
 */
export function placeCaretAfterChromeChip(chip: Node, host: HTMLElement): void {
  const sel = window.getSelection();
  if (!sel) return;
  let tail = chip.nextSibling;
  if (!tail || tail.nodeType !== Node.TEXT_NODE) {
    tail = document.createTextNode('\u200b');
    if (chip.parentNode) {
      chip.parentNode.insertBefore(tail, chip.nextSibling);
    } else {
      host.appendChild(tail);
    }
  } else if (!(tail.textContent ?? '').includes('\u200b')) {
    tail.textContent = `\u200b${tail.textContent ?? ''}`;
  }
  const offset = Math.min(1, (tail.textContent ?? '').length);
  const range = document.createRange();
  range.setStart(tail, offset);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
}

function asText(v: unknown): string {
  if (v == null) return '';
  return String(v).trim();
}

/** 从元数据 + 宿主字段拼页眉页脚占位符 */
export function buildEmrPageChromeContext(input: {
  documentTitle?: string;
  documentTypeName?: string;
  metadataValues?: Record<string, string>;
  orgName?: string;
  printTime?: string;
}): EmrPageChromeContext {
  const meta = input.metadataValues ?? {};
  const values: Record<string, string> = {};
  for (const [k, v] of Object.entries(meta)) {
    const key = String(k).trim();
    if (!key) continue;
    values[key] = asText(v);
  }
  // 页眉 {inpatientNo} 与患者条 medicalRecordNo（就诊流水号）对齐
  if (!values.inpatientNo && values.medicalRecordNo) {
    values.inpatientNo = values.medicalRecordNo;
  }
  return {
    patientName: asText(meta.patientName),
    gender: asText(meta.gender),
    age: asText(meta.age),
    orgName: asText(input.orgName || meta.orgName || meta.hospitalName),
    deptName: asText(meta.deptName),
    // 与打印口径一致：无自定义标题时回退文书类型名（如「入院记录」）
    documentTitle: asText(
      input.documentTitle || meta.documentTitle || input.documentTypeName || meta.documentTypeName,
    ),
    documentTypeName: asText(input.documentTypeName || meta.documentTypeName),
    printTime: asText(input.printTime),
    values,
  };
}

/**
 * 解析 `{page}` `{total}` `{patientName}` 及任意元数据 `{fieldKey}`。
 * 连续空白收成单空格，避免元数据空时留下大空隙。
 */
export function resolveEmrPageChromeTemplate(
  template: string,
  page: number,
  total: number,
  ctx: EmrPageChromeContext,
): string {
  const map: Record<string, string> = {
    ...(ctx.values ?? {}),
    page: String(page),
    total: String(total),
    patientName: ctx.patientName,
    gender: ctx.gender,
    age: ctx.age,
    orgName: ctx.orgName,
    deptName: ctx.deptName,
    documentTitle: ctx.documentTitle,
    documentTypeName: ctx.documentTypeName,
    printTime: ctx.printTime,
  };
  const resolved = template.replace(/\{(\w+)\}/g, (_, key: string) => map[key] ?? '');
  return normalizeChromeTemplateText(
    resolved.replace(/[ \t]{2,}/g, ' ').replace(/[ \t]*\n[ \t]*/g, '\n'),
  );
}
