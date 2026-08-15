import type {
  DoctorStationAdviceCatalogRowDto,
  PageDoctorStationAdviceCatalogRequest,
  PrepickedProductKeyDto,
} from '#/api/doctor-station/advice.types';

import { httpPageDoctorStationAdviceCatalog } from '#/api/doctor-station/advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 与后端 PRODUCT_DEFINITION_RM_PAGE_SIZE_MAX 对齐 */
const CATALOG_PAGE_SIZE_MAX = 100;

type CatalogPageFn = (body: PageDoctorStationAdviceCatalogRequest) => Promise<unknown>;

export type PrepickedCatalogRequestBase = Omit<
  PageDoctorStationAdviceCatalogRequest,
  'pageNo' | 'pageSize' | 'prepickedProductKeys' | 'productTableNames'
>;

/**
 * 按 prepickedProductKeys 批量拉目录行。
 *
 * 按 productTableName 分组并显式传 productTableNames，走后端单表路径，
 * 避免多表合并时表序拼接/mergeCap 截断导致同表多项丢失（历史复用/组套回填误报未找到）。
 * 单表超过后端页上限时按 100 分片请求。
 */
export async function fetchAdviceCatalogRowsByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  requestBase: PrepickedCatalogRequestBase,
  pageFn: CatalogPageFn = httpPageDoctorStationAdviceCatalog,
): Promise<DoctorStationAdviceCatalogRowDto[]> {
  if (keys.length === 0) {
    return [];
  }

  const uniqueKeys = dedupePrepickedKeys(keys);
  const grouped = groupPrepickedKeysByTable(uniqueKeys);
  const rows: DoctorStationAdviceCatalogRowDto[] = [];

  for (const [productTableName, tableKeys] of grouped) {
    for (let offset = 0; offset < tableKeys.length; offset += CATALOG_PAGE_SIZE_MAX) {
      const chunk = tableKeys.slice(offset, offset + CATALOG_PAGE_SIZE_MAX);
      const res = await pageFn({
        ...requestBase,
        pageNo: 1,
        pageSize: Math.max(chunk.length, 1),
        productTableNames: [productTableName],
        prepickedProductKeys: chunk,
      });
      const { content } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
      rows.push(...content);
    }
  }

  return rows;
}

function dedupePrepickedKeys(keys: PrepickedProductKeyDto[]): PrepickedProductKeyDto[] {
  const seen = new Set<string>();
  const out: PrepickedProductKeyDto[] = [];
  for (const key of keys) {
    const productDefinitionId = String(key.productDefinitionId ?? '').trim();
    const productTableName = String(key.productTableName ?? '').trim();
    if (!productDefinitionId || !productTableName) {
      continue;
    }
    const mapKey = `${productTableName}:${productDefinitionId}`;
    if (seen.has(mapKey)) {
      continue;
    }
    seen.add(mapKey);
    out.push({ productDefinitionId, productTableName });
  }
  return out;
}

function groupPrepickedKeysByTable(
  keys: PrepickedProductKeyDto[],
): Map<string, PrepickedProductKeyDto[]> {
  const grouped = new Map<string, PrepickedProductKeyDto[]>();
  for (const key of keys) {
    const list = grouped.get(key.productTableName);
    if (list) {
      list.push(key);
    } else {
      grouped.set(key.productTableName, [key]);
    }
  }
  return grouped;
}
