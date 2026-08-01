/**
 * 公费配置 — POST /api/app/campusGfConfig/<method>
 */
import type { GfChargeMode, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface GfFreePercentDto {
  id?: IdLike;
  classEnum?: string;
  selfPayRatio?: number;
  chargeModeEnum?: GfChargeMode;
}

export function getMasterSwitchApi(params: Record<string, never> = {}) {
  return publicRequestClient.post<{ enabled?: boolean }>('/campusGfConfig/getMasterSwitch', params);
}

export function saveMasterSwitchApi(params: { enabled: boolean }) {
  return publicRequestClient.post<{ enabled?: boolean }>(
    '/campusGfConfig/saveMasterSwitch',
    params,
  );
}

export function listFreePercentsApi(params: { chargeModeEnum: GfChargeMode }) {
  return publicRequestClient.post<{ records?: GfFreePercentDto[] }>(
    '/campusGfConfig/listFreePercents',
    params,
  );
}

export function saveFreePercentsApi(params: {
  chargeModeEnum: GfChargeMode;
  items: Array<{ id?: IdLike; classEnum: string; selfPayRatio: number }>;
}) {
  return publicRequestClient.post<{ records?: GfFreePercentDto[] }>(
    '/campusGfConfig/saveFreePercents',
    params,
  );
}
