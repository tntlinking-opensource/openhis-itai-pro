import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface Frequency {
  thirdCode: string;
  totalExecutionCount: string;
  dayCount: string;
  weekInterval: string;
  memo: string;
  rateCode: string;
  executionPeriodUnit: string;
  weekTimes: string;
  dayTimes: string[];
  name: string;
  weekCycleFlag: string;
  executionPeriod: string;
  dayInterval: string;
  continueFlag: string;
}

export interface FrequencyInitOption {
  label: string;
  value: string;
  sort?: null | string;
  [key: string]: unknown;
}

export interface FrequencyInitDto {
  executionPeriodUnit?: FrequencyInitOption[];
  minutes?: FrequencyInitOption[];
}

/**
 * 获取频次初始化数据
 */
export async function getFrequencyInitDtoApi() {
  return publicRequestClient.post<FrequencyInitDto>('/frequency/getFrequencyInitDto');
}

export async function getFrequencyApi(data: PageParams & { rateCode?: string; keyword?: string }) {
  return publicRequestClient.post<PageApiResponse<Frequency>>('/frequency/getFrequencyPage', data);
}

export async function getFrequencyByIdApi(id: string) {
  return publicRequestClient.post<Frequency>('/frequency/getFrequency', {
    value: id,
  });
}

export async function createFrequencyApi(data: Frequency) {
  return publicRequestClient.post<Frequency>('/frequency/createFrequency', data);
}

export async function updateFrequencyApi(data: Frequency & { id: string }) {
  return publicRequestClient.post<Frequency>('/frequency/editFrequency', data);
}

export async function deleteFrequencyApi(id: string) {
  return publicRequestClient.post<Frequency>('/frequency/deleteFrequency', {
    value: id,
  });
}
