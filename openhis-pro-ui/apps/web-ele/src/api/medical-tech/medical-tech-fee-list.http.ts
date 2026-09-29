import type {
  ConfirmMedicalTechFeesRequest,
  ConfirmMedicalTechFeesResponse,
  ListMedicalTechFeeItemsRequest,
  ListMedicalTechFeeItemsResponse,
  PrintMedicalTechFeeListRequest,
  PrintMedicalTechFeeListResponse,
} from './medical-tech-fee-list.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/medicalTechFeeList';

/** 按就诊加载本科相关费用清单 */
export async function httpListMedicalTechFeeItems(
  body: ListMedicalTechFeeItemsRequest,
): Promise<ListMedicalTechFeeItemsResponse> {
  return publicRequestClient.post<ListMedicalTechFeeItemsResponse>(
    `${BASE}/listMedicalTechFeeItems`,
    body,
  );
}

/** 一键确费：本科未确费行批量打戳 */
export async function httpConfirmMedicalTechFees(
  body: ConfirmMedicalTechFeesRequest,
): Promise<ConfirmMedicalTechFeesResponse> {
  return publicRequestClient.post<ConfirmMedicalTechFeesResponse>(
    `${BASE}/confirmMedicalTechFees`,
    body,
  );
}

/** 触发医技费用清单打印准备 */
export async function httpPrintMedicalTechFeeList(
  body: PrintMedicalTechFeeListRequest,
): Promise<PrintMedicalTechFeeListResponse> {
  return publicRequestClient.post<PrintMedicalTechFeeListResponse>(
    `${BASE}/printMedicalTechFeeList`,
    body,
  );
}
