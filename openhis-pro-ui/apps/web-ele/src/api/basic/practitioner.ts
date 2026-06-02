import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

/** 医务人员主档（岗位工作台 · 医生签名维护） */
export interface PractitionerProfile {
  practitionerId?: string | number;
  name?: string;
  busNo?: string;
  statusEnum?: string;
  signatureImage?: string | null;
  hasSignature?: boolean;
}

export async function getPractitionerProfileApi(practitionerId: string | number) {
  const id = typeof practitionerId === 'string' ? practitionerId : String(practitionerId);
  return requestClient.post<ApiResponse<PractitionerProfile>>('/practitioner/getProfile', {
    practitionerId: id,
  });
}

export async function savePractitionerSignatureApi(payload: {
  practitionerId: string | number;
  signatureImage: string;
}) {
  const practitionerId =
    typeof payload.practitionerId === 'string'
      ? payload.practitionerId
      : String(payload.practitionerId);
  return requestClient.post<ApiResponse<null>>('/practitioner/saveSignature', {
    practitionerId,
    signatureImage: payload.signatureImage,
  });
}
