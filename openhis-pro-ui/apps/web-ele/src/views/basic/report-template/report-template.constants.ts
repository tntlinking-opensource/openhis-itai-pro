export const REPORT_PIPELINE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '分页 (page)', value: 'page' },
  { label: '卷纸 (roll)', value: 'roll' },
] as const;

/** 复制为新模板时可选的纸张管线（不含「全部」） */
export const REPORT_PIPELINE_COPY_OPTIONS = [
  { label: '分页 A4 (page)', value: 'page' },
  { label: '卷纸热敏 (roll)', value: 'roll' },
] as const;

export const REPORT_LAYOUT_MODE_COPY_OPTIONS = [
  { label: '流式 flow', value: 'flow' },
  { label: '套打 overlay', value: 'overlay' },
] as const;

export const REPORT_STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '已发布', value: 'published' },
  { label: '草稿', value: 'draft' },
] as const;

export const REPORT_PRIORITY_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'P1', value: 'P1' },
  { label: 'P2', value: 'P2' },
  { label: 'P3', value: 'P3' },
] as const;

export function priorityTagType(
  priority?: string | null,
): 'danger' | 'info' | 'success' | 'warning' {
  if (priority === 'P1') {
    return 'danger';
  }
  if (priority === 'P2') {
    return 'warning';
  }
  return 'info';
}

export const REPORT_LAYOUT_MODE_LABEL: Record<string, string> = {
  flow: '流式 flow',
  overlay: '套打 overlay',
  roll: '卷纸 roll',
};

export const REPORT_PAGE_SIZE_LABEL: Record<string, string> = {
  A4: 'A4',
  A5: 'A5',
  custom: '自定义',
};

export function formatReportPageSize(
  page?: {
    size?: string | null;
    widthMm?: number | null;
    heightMm?: number | null;
  } | null,
): string {
  if (!page) {
    return '—';
  }
  if (page.size === 'custom' && page.widthMm && page.heightMm) {
    return `${page.widthMm}×${page.heightMm}mm`;
  }
  const code = page.size?.trim();
  if (!code) {
    return '—';
  }
  return REPORT_PAGE_SIZE_LABEL[code] ?? code;
}

export function formatReportLayoutMode(code?: string | null): string {
  if (!code?.trim()) {
    return '—';
  }
  return REPORT_LAYOUT_MODE_LABEL[code.trim()] ?? code;
}

export function formatReportPipeline(code?: string | null): string {
  if (!code?.trim()) {
    return '—';
  }
  const hit = REPORT_PIPELINE_OPTIONS.find((o) => o.value === code.trim());
  return hit?.label ?? code;
}

export function formatReportStatus(code?: string | null): string {
  if (!code?.trim()) {
    return '—';
  }
  const hit = REPORT_STATUS_OPTIONS.find((o) => o.value === code.trim());
  return hit?.label ?? code;
}

export function formatReportSource(source?: string | null): string {
  if (source === 'tenant') {
    return '院点';
  }
  if (source === 'platform') {
    return '平台';
  }
  return '—';
}
