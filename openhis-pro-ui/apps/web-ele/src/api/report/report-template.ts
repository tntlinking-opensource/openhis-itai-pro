/**
 * 打印模板管理 — 对接 {@code POST /api/app/reportTemplate/<method>}
 */
import type { LayoutDocument } from '#/types/report-layout';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

const BASE = '/reportTemplate';

export interface TemplateDesignDto {
  id?: number | string;
  code?: string;
  name?: string;
  pipeline?: string;
  datasetCode?: string;
  status?: string;
  version?: number;
  tenantId?: number | string;
  editable?: boolean;
  source?: string;
  layoutJson?: LayoutDocument | Record<string, unknown>;
}

export async function getTemplateDesignApi(templateCode: string) {
  return publicRequestClient.post<TemplateDesignDto>(`${BASE}/getForDesign`, { templateCode });
}

export async function forkTemplateDraftApi(templateCode: string) {
  return publicRequestClient.post<TemplateDesignDto>(`${BASE}/forkDraft`, { templateCode });
}

export interface CopyTemplateAsNewRequest {
  sourceTemplateCode: string;
  newTemplateCode: string;
  newTemplateName?: string;
  layoutMode: 'flow' | 'overlay';
  pipeline?: 'page' | 'roll';
}

export async function copyTemplateAsNewApi(payload: CopyTemplateAsNewRequest) {
  return publicRequestClient.post<TemplateDesignDto>(`${BASE}/copyAsNew`, payload);
}

export async function saveTemplateDraftApi(templateId: number | string, layoutJson: string) {
  return publicRequestClient.post<TemplateDesignDto>(`${BASE}/saveDraft`, {
    templateId: preserveApiIdForRequest(templateId),
    layoutJson,
  });
}

export async function publishTemplateApi(templateId: number | string) {
  return publicRequestClient.post<TemplateDesignDto>(`${BASE}/publish`, {
    templateId: preserveApiIdForRequest(templateId),
  });
}
