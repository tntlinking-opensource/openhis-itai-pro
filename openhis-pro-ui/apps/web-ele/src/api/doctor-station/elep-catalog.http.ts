import type { ElepCatalogPageResultDto, ElepCatalogSearchRequest } from './elep-catalog.types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const PREFIX = '/elepCatalogSearch';

function buildSearchBody(body: ElepCatalogSearchRequest) {
  return {
    contractBusNo: body.contractBusNo,
    contractId: body.contractId ? preserveApiIdForRequest(body.contractId) : undefined,
    keyword: body.keyword?.trim() || undefined,
    aprvno: body.aprvno?.trim() || undefined,
    spec: body.spec?.trim() || undefined,
    page: body.page ?? 0,
    pageSize: body.pageSize ?? 20,
  };
}

export async function httpSearchElepDrugCatalog(
  body: ElepCatalogSearchRequest,
): Promise<ElepCatalogPageResultDto> {
  return publicRequestClient.post<ElepCatalogPageResultDto>(
    `${PREFIX}/searchDrug`,
    buildSearchBody(body),
  );
}

export async function httpSearchElepDiagnosisCatalog(
  body: ElepCatalogSearchRequest,
): Promise<ElepCatalogPageResultDto> {
  return publicRequestClient.post<ElepCatalogPageResultDto>(
    `${PREFIX}/searchDiagnosis`,
    buildSearchBody(body),
  );
}
