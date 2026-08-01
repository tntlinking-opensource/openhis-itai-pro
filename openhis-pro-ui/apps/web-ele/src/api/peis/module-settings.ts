/**
 * PEIS 系统参数 — {@code POST /api/app/peisModuleSettings/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { PeisPagedResult } from './catalog-config';

export interface PeisConfigDto {
  id?: string | number;
  configKey?: string;
  configValue?: string;
  orgId?: string | number;
}

export interface SearchConfigRequest {
  pageNo: number;
  pageSize: number;
  keyword?: string;
}

export interface SaveConfigRequest {
  id?: number;
  configKey: string;
  configValue: string;
  orgId?: number;
}

/** P0 白名单键（与后端 PeisCatalogConstants 对齐）。 */
export const PEIS_CONFIG_KEY_OPTIONS = [
  { value: 'peis.reg.charge_gate', label: '登记收费门禁 peis.reg.charge_gate' },
  { value: 'peis.reg.auto_dispatch', label: '登记自动分诊 peis.reg.auto_dispatch' },
  { value: 'peis.report.template_code', label: '报告模板码 peis.report.template_code' },
  { value: 'peis.bus_no.rule', label: '业务号规则 peis.bus_no.rule' },
  { value: 'peis.miniapp.home_notice', label: '小程序首页公告 peis.miniapp.home_notice' },
];

export function searchConfigApi(params: SearchConfigRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisConfigDto>>(
    '/peisModuleSettings/searchConfig',
    params,
  );
}

export function saveConfigApi(params: SaveConfigRequest) {
  return publicRequestClient.post<PeisConfigDto>('/peisModuleSettings/saveConfig', params);
}
