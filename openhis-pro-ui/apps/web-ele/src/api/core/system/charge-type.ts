import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface ChargeType {
  id: string;
  name: string;
  /** 业务编码（费用性质编码） */
  busNo?: string;
  code?: string;
  activeFlag?: number;
  displayOrder?: number;
  categoryEnum?: string;
  /** 分类中文名称（后端 ContractDto.categoryEnumText） */
  categoryEnumText?: string;
}

export const CHARGE_TYPE_ACTIVE_FLAG = {
  ACTIVE: 1,
  INACTIVE: 2,
} as const;

export interface CreateChargeTypeParams {
  /** 业务编码（费用性质编码），前端录入 */
  busNo?: string;
  name: string;
  displayOrder: number;
  categoryEnum: string;
}

export interface ChargeTypeInitResponse {
  categoryOptions?: ApiOptionItem[];
}

export async function getChargeTypeListApi() {
  return publicRequestClient.post<ChargeType[]>('/chargeType/getChargeTypeList');
}

export async function initChargeTypeApi() {
  return publicRequestClient.post<ChargeTypeInitResponse>('/chargeType/init');
}

export async function createChargeTypeApi(data: CreateChargeTypeParams) {
  return publicRequestClient.post('/chargeType/createChargeType', data);
}

export async function updateChargeTypeApi(data: CreateChargeTypeParams & { id: string }) {
  return publicRequestClient.post('/chargeType/updateChargeType', data);
}

export async function deleteChargeTypeApi(value: string) {
  return publicRequestClient.post('/chargeType/deleteChargeType', { value });
}
