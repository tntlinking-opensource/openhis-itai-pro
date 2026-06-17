/**
 * 院点打印模板绑定 — 对接 {@code POST /api/app/reportPrintBinding/<method>}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/reportPrintBinding';

export interface PrintBindingTemplateOptionDto {
  code?: string;
  name?: string;
  pipeline?: string;
  layoutMode?: string;
  status?: string;
  source?: string;
}

export interface PrintBindingItemDto {
  bindingCode?: string;
  bindingName?: string;
  bizModule?: string;
  scene?: string;
  defaultTemplateCode?: string;
  templateCode?: string;
  tenantOverride?: boolean;
  candidates?: PrintBindingTemplateOptionDto[];
}

export interface PrintBindingConfigDto {
  items?: PrintBindingItemDto[];
}

export interface ResolvePrintBindingResultDto {
  bindingCode?: string;
  templateCode?: string;
  defaultTemplateCode?: string;
  tenantOverride?: boolean;
}

export async function getPrintBindingConfigApi() {
  return publicRequestClient.post<PrintBindingConfigDto>(`${BASE}/getConfig`, {});
}

export async function savePrintBindingConfigApi(bindings: Record<string, string>) {
  return publicRequestClient.post<PrintBindingConfigDto>(`${BASE}/saveConfig`, { bindings });
}

export async function resolvePrintBindingApi(bindingCode: string) {
  return publicRequestClient.post<ResolvePrintBindingResultDto>(`${BASE}/resolve`, { bindingCode });
}
