import type { PrintConfigDto } from '#/api/emr/print-config.types';

export interface EditorPageMargins {
  top?: string;
  right?: string;
  bottom?: string;
  left?: string;
}

/** CSS 长度转 mm（支持 mm / px） */
export function parseCssLengthToMm(raw?: string): number | undefined {
  const s = (raw ?? '').trim();
  if (!s) return undefined;
  if (s.endsWith('mm')) {
    const n = Number.parseFloat(s);
    return Number.isFinite(n) ? n : undefined;
  }
  if (s.endsWith('px')) {
    const n = Number.parseFloat(s);
    if (!Number.isFinite(n)) return undefined;
    return (n * 25.4) / 96;
  }
  const n = Number.parseFloat(s);
  return Number.isFinite(n) ? n : undefined;
}

export function defaultPrintConfigDto(): PrintConfigDto {
  return {
    paper: { widthMm: 210, heightMm: 297, orientation: 'portrait' },
    margins: { topMm: 25.4, rightMm: 31.8, bottomMm: 25.4, leftMm: 31.8 },
    header: { enabled: false, heightMm: 15, template: '' },
    footer: {
      enabled: true,
      heightMm: 12,
      template: '第 {page} 页',
      showPageNumber: true,
    },
    watermark: { enabled: false, text: '', opacity: 0.15, angle: 45, fontSizePt: 48 },
    font: { family: 'SimSun', defaultSizePt: 12, titleSizePt: 16, lineHeight: 1.5 },
    dateFormat: 'yyyy-MM-dd',
    datetimeFormat: 'yyyy-MM-dd HH:mm',
    showDeletedNodes: false,
    blankPagePolicy: 'hide',
  };
}

/** 将编辑器页边距（L1）同步到 L2 PrintConfig.margins */
export function buildPrintConfigFromEditor(
  pageMargins?: EditorPageMargins,
  overrides?: Partial<PrintConfigDto>,
): PrintConfigDto {
  const base = defaultPrintConfigDto();
  const top = parseCssLengthToMm(pageMargins?.top);
  const right = parseCssLengthToMm(pageMargins?.right);
  const bottom = parseCssLengthToMm(pageMargins?.bottom);
  const left = parseCssLengthToMm(pageMargins?.left);
  if (top != null) base.margins!.topMm = top;
  if (right != null) base.margins!.rightMm = right;
  if (bottom != null) base.margins!.bottomMm = bottom;
  if (left != null) base.margins!.leftMm = left;
  if (overrides?.watermark?.text) {
    base.watermark = { ...base.watermark, enabled: true, text: overrides.watermark.text };
  }
  if (overrides?.header?.template) {
    base.header = { ...base.header, enabled: true, template: overrides.header.template };
  }
  if (overrides?.footer?.template) {
    base.footer = { ...base.footer, enabled: true, template: overrides.footer.template };
  }
  return { ...base, ...overrides, margins: { ...base.margins, ...overrides?.margins } };
}

export function buildPdfMetadataFromPatient(
  patientInfo?: {
    age?: string;
    gender?: string;
    medicalRecordNo?: string;
    name?: string;
  },
  extra?: Record<string, string>,
): Record<string, string> {
  const meta: Record<string, string> = { ...(extra ?? {}) };
  if (patientInfo?.name) meta.patientName = patientInfo.name;
  if (patientInfo?.gender) meta.gender = patientInfo.gender;
  if (patientInfo?.age) meta.age = patientInfo.age;
  if (patientInfo?.medicalRecordNo) meta.medicalRecordNo = patientInfo.medicalRecordNo;
  return meta;
}
