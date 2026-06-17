/**
 * C 类报表 — 对接 {@code POST /api/app/reportQuery/<method>}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/reportQuery';

export interface ReportColumnDto {
  field?: string;
  label?: string;
  align?: string;
  width?: number;
}

export interface ReportFilterFieldDto {
  field?: string;
  label?: string;
  type?: 'date' | 'text' | 'select' | 'daterange' | 'month';
}

/** 表头行动态行：text 单行；split 左右分栏 */
export interface ReportHeaderLineDto {
  type?: 'text' | 'split';
  text?: string;
  align?: 'left' | 'center' | 'right';
  left?: string;
  right?: string;
}

export interface ReportHeaderConfigDto {
  title?: string;
  subtitle?: string;
  lines?: ReportHeaderLineDto[];
  /** @deprecated 兼容旧数据，加载时迁移到 lines */
  leftText?: string;
  rightText?: string;
}

export interface ReportDefinitionItemDto {
  id?: number | string;
  reportCode?: string;
  name?: string;
  status?: string;
  sourceType?: string;
  exportPdfEnabled?: boolean;
  pdfTemplateCode?: string;
  allowColumnCustomize?: boolean;
  columns?: ReportColumnDto[];
  querySql?: string;
  headerJson?: string;
  footerJson?: string;
  filterJson?: string;
  remark?: string;
}

export interface ListReportDefinitionsRequest {
  status?: string;
  keyword?: string;
}

export interface CreateReportDefinitionRequest {
  reportCode: string;
  name: string;
  remark?: string;
}

export interface UpdateReportDefinitionRequest {
  id: number | string;
  name?: string;
  remark?: string;
}

export interface SaveReportDesignRequest {
  id: number | string;
  name?: string;
  querySql?: string;
  headerJson?: string;
  footerJson?: string;
  filterJson?: string;
  defaultColumnsJson?: string;
  exportPdfEnabled?: boolean;
  pdfTemplateCode?: string;
}

export interface ReportQueryPageRequest {
  reportCode: string;
  pageNo?: number;
  pageSize?: number;
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  params?: Record<string, unknown>;
  designPreview?: boolean;
}

export interface ReportQueryPageResultDto {
  reportCode?: string;
  reportName?: string;
  columns?: ReportColumnDto[];
  rows?: Array<Record<string, unknown>>;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  summary?: Record<string, unknown>;
  header?: Record<string, unknown>;
  footer?: Record<string, unknown>;
}

export function listReportDefinitionsApi(
  request?: ListReportDefinitionsRequest,
): Promise<ReportDefinitionItemDto[]> {
  return publicRequestClient.post<ReportDefinitionItemDto[]>(
    `${BASE}/listDefinitions`,
    request ?? {},
  );
}

export function getReportDefinitionApi(id: number | string): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/getDefinition`, { id });
}

export function createReportDefinitionApi(
  request: CreateReportDefinitionRequest,
): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/createDefinition`, request);
}

export function updateReportDefinitionApi(
  request: UpdateReportDefinitionRequest,
): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/updateDefinition`, request);
}

export function deleteReportDefinitionApi(id: number | string): Promise<void> {
  return publicRequestClient.post<void>(`${BASE}/deleteDefinition`, { id });
}

export function saveReportDesignApi(
  request: SaveReportDesignRequest,
): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/saveReportDesign`, request);
}

export function publishReportDefinitionApi(id: number | string): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/publishDefinition`, { id });
}

export function unpublishReportDefinitionApi(
  id: number | string,
): Promise<ReportDefinitionItemDto> {
  return publicRequestClient.post<ReportDefinitionItemDto>(`${BASE}/unpublishDefinition`, { id });
}

export function reportQueryPageApi(
  request: ReportQueryPageRequest,
): Promise<ReportQueryPageResultDto> {
  return publicRequestClient.post<ReportQueryPageResultDto>(`${BASE}/queryPage`, request);
}

export interface ReportExportResultDto {
  contentType?: string;
  fileName?: string;
  bodyBase64?: string;
}

export function reportExportExcelApi(
  request: ReportQueryPageRequest,
): Promise<ReportExportResultDto> {
  return publicRequestClient.post<ReportExportResultDto>(`${BASE}/exportExcel`, request);
}

export function downloadReportExportFile(result: ReportExportResultDto): void {
  const base64 = result.bodyBase64?.trim();
  if (!base64) {
    return;
  }
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  const blob = new Blob([bytes], {
    type: result.contentType ?? 'text/csv',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = result.fileName?.trim() || 'report.csv';
  link.click();
  URL.revokeObjectURL(url);
}

export function parseReportFilters(filterJson?: string): ReportFilterFieldDto[] {
  if (!filterJson?.trim()) {
    return [
      { field: 'dateFrom', label: '开始日期', type: 'date' },
      { field: 'dateTo', label: '结束日期', type: 'date' },
    ];
  }
  try {
    const parsed = JSON.parse(filterJson) as ReportFilterFieldDto[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function parseReportHeaderFooter(json?: string): Record<string, unknown> {
  if (!json?.trim()) {
    return {};
  }
  try {
    return (JSON.parse(json) as Record<string, unknown>) ?? {};
  } catch {
    return {};
  }
}

export function parseReportHeaderConfig(json?: string): ReportHeaderConfigDto {
  const raw = parseReportHeaderFooter(json) as ReportHeaderConfigDto;
  const lines = normalizeReportHeaderLines(raw);
  return {
    title: raw.title ?? '',
    subtitle: raw.subtitle ?? '',
    lines,
  };
}

export function normalizeReportHeaderLines(raw: ReportHeaderConfigDto): ReportHeaderLineDto[] {
  if (Array.isArray(raw.lines) && raw.lines.length > 0) {
    return raw.lines.map((line) => ({
      type: line.type === 'split' ? 'split' : 'text',
      text: line.text ?? '',
      align: line.align ?? 'left',
      left: line.left ?? '',
      right: line.right ?? '',
    }));
  }
  if (raw.leftText?.trim() || raw.rightText?.trim()) {
    return [
      {
        type: 'split',
        left: raw.leftText ?? '',
        right: raw.rightText ?? '',
      },
    ];
  }
  return [];
}

export function buildReportHeaderJson(config: ReportHeaderConfigDto): string {
  const lines = (config.lines ?? [])
    .map((line) => {
      if (line.type === 'split') {
        const left = line.left?.trim() ?? '';
        const right = line.right?.trim() ?? '';
        if (!left && !right) {
          return null;
        }
        return { type: 'split', left, right };
      }
      const text = line.text?.trim() ?? '';
      if (!text) {
        return null;
      }
      return {
        type: 'text',
        text,
        align: line.align ?? 'left',
      };
    })
    .filter(Boolean);
  return JSON.stringify({
    title: config.title?.trim() ?? '',
    subtitle: config.subtitle?.trim() ?? '',
    lines,
  });
}

export function resolveReportHeaderLines(header?: Record<string, unknown>): ReportHeaderLineDto[] {
  if (!header) {
    return [];
  }
  const lines = header.lines;
  if (Array.isArray(lines)) {
    return lines.map((line) => {
      const item = line as ReportHeaderLineDto;
      if (item.type === 'split') {
        return { type: 'split', left: String(item.left ?? ''), right: String(item.right ?? '') };
      }
      return {
        type: 'text',
        text: String(item.text ?? ''),
        align: (item.align as ReportHeaderLineDto['align']) ?? 'left',
      };
    });
  }
  if (header.leftText || header.rightText) {
    return [
      {
        type: 'split',
        left: String(header.leftText ?? ''),
        right: String(header.rightText ?? ''),
      },
    ];
  }
  return [];
}
