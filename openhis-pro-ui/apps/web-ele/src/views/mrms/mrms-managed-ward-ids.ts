import { httpLoadWardTree } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 当前会话已拉过的管辖病区/病房位置主键 */
let cachedManagedWardLocationIds: string[] | null = null;
/** 并发点击共用同一次请求 */
let inflightLoad: null | Promise<string[]> = null;

/**
 * 拉取登录者护士站管辖范围内的病区、病房位置主键。
 * 无护士站权限或接口失败时返回空数组，门禁回落到入院科室。
 */
export async function loadManagedWardLocationIds(): Promise<string[]> {
  if (cachedManagedWardLocationIds) {
    return cachedManagedWardLocationIds;
  }
  if (inflightLoad) {
    return inflightLoad;
  }
  inflightLoad = loadManagedWardLocationIdsFromApi();
  try {
    const ids = await inflightLoad;
    cachedManagedWardLocationIds = ids;
    return ids;
  } finally {
    inflightLoad = null;
  }
}

/** 调护士站病区树，收集病区与病房 locationId */
async function loadManagedWardLocationIdsFromApi(): Promise<string[]> {
  try {
    const res = await httpLoadWardTree({});
    const ids: string[] = [];
    const seen = new Set<string>();
    const wardNodes = res.wardNodes ?? [];
    for (const ward of wardNodes) {
      // 病区位置
      pushUniqueLocationId(ids, seen, ward.locationId);
      const houses = ward.houseNodes ?? [];
      for (const house of houses) {
        // 病房位置
        pushUniqueLocationId(ids, seen, house.locationId);
      }
    }
    return ids;
  } catch {
    return [];
  }
}

/** 规范化后写入去重集合 */
function pushUniqueLocationId(ids: string[], seen: Set<string>, raw: unknown) {
  const id = preserveApiIdForRequest(raw);
  if (!id || seen.has(id)) {
    return;
  }
  seen.add(id);
  ids.push(id);
}
