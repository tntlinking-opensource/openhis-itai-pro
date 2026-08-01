import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export const REGISTRATION_TYPE_ACTIVE_FLAG = {
  ACTIVE: 1,
  INACTIVE: 0,
} as const;

export interface RegistrationType {
  id: string;
  name: string;
  ybNo: string;
  busNo: string;
  amount: number;
  activeFlag?: number;
}

export interface AddRegistrationTypeParams {
  ybNo: string;
  amount: number;
  name: string;
  activeFlag?: number;
}

/** 判断挂号类型是否启用 */
export function isRegistrationTypeActive(row: Pick<RegistrationType, 'activeFlag'>) {
  return row.activeFlag === REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE;
}

export async function getRegistrationTypeListApi() {
  return publicRequestClient.post<RegistrationType[]>('/registrationType/getRegistrationTypeList');
}

export async function addRegistrationTypeApi(data: AddRegistrationTypeParams) {
  return publicRequestClient.post('/registrationType/createRegistrationType', data);
}

export async function updateRegistrationTypeApi(data: RegistrationType & { id: string }) {
  return publicRequestClient.post('/registrationType/updateRegistrationType', data);
}

export async function deleteRegistrationTypeApi(id: number | string) {
  return publicRequestClient.post(
    '/registrationType/deleteRegistrationType',
    preserveApiIdForRequest(id),
  );
}

/** 启用已停用的挂号类型 */
export async function activateRegistrationTypeApi(id: number | string) {
  return publicRequestClient.post(
    '/registrationType/activateRegistrationType',
    preserveApiIdForRequest(id),
  );
}

/** 停用挂号类型 */
export async function deactivateRegistrationTypeApi(id: number | string) {
  return publicRequestClient.post(
    '/registrationType/deactivateRegistrationType',
    preserveApiIdForRequest(id),
  );
}
