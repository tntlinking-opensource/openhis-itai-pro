import type {
  CancelMedicalTechDevicePricingRequest,
  CancelMedicalTechDevicePricingResponse,
  ConfirmMedicalTechDevicePricingRequest,
  ConfirmMedicalTechDevicePricingResponse,
  ListMedicalTechPricedDevicesRequest,
  ListMedicalTechPricedDevicesResponse,
  LoadMedicalTechChargingInitRequest,
  LoadMedicalTechChargingInitResponse,
  PageMedicalTechDeviceCatalogRequest,
  PageMedicalTechDeviceCatalogResponse,
} from './medical-tech-charging.types';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/medicalTechCharging';

/** 加载医技计费初始化（耗材房下拉） */
export async function httpLoadMedicalTechChargingInit(
  body: LoadMedicalTechChargingInitRequest,
): Promise<LoadMedicalTechChargingInitResponse> {
  return publicRequestClient.post<LoadMedicalTechChargingInitResponse>(
    `${BASE}/loadMedicalTechChargingInit`,
    body,
  );
}

/** 分页查询医技耗材选品目录 */
export async function httpPageMedicalTechDeviceCatalog(
  body: PageMedicalTechDeviceCatalogRequest,
): Promise<PageMedicalTechDeviceCatalogResponse> {
  const data = await publicRequestClient.post<unknown>(
    `${BASE}/pageMedicalTechDeviceCatalog`,
    body,
  );
  const parsed =
    parsePagedRowsFromApiData<PageMedicalTechDeviceCatalogResponse['content'][number]>(data);
  return {
    content: parsed.content,
    totalCount: parsed.totalCount,
    pageNo: parsed.pageNo,
    pageSize: parsed.pageSize,
  };
}

/** 确认医技耗材划价 */
export async function httpConfirmMedicalTechDevicePricing(
  body: ConfirmMedicalTechDevicePricingRequest,
): Promise<ConfirmMedicalTechDevicePricingResponse> {
  return publicRequestClient.post<ConfirmMedicalTechDevicePricingResponse>(
    `${BASE}/confirmMedicalTechDevicePricing`,
    body,
  );
}

/** 查询已划价耗材列表 */
export async function httpListMedicalTechPricedDevices(
  body: ListMedicalTechPricedDevicesRequest,
): Promise<ListMedicalTechPricedDevicesResponse> {
  return publicRequestClient.post<ListMedicalTechPricedDevicesResponse>(
    `${BASE}/listMedicalTechPricedDevices`,
    body,
  );
}

/** 取消医技耗材划价 */
export async function httpCancelMedicalTechDevicePricing(
  body: CancelMedicalTechDevicePricingRequest,
): Promise<CancelMedicalTechDevicePricingResponse> {
  return publicRequestClient.post<CancelMedicalTechDevicePricingResponse>(
    `${BASE}/cancelMedicalTechDevicePricing`,
    body,
  );
}
