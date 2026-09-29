import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export const REGISTRATION_TYPE_ACTIVE_FLAG = {
  ACTIVE: 1,
  INACTIVE: 0,
} as const;

/**
 * 挂号类型列表/回显（AS-BAS-MST-015）。
 *
 * <p>金额为绑定诊查费 AD 当前 SALE 合计（只读 enrich）；无业务写 ybNo。</p>
 */
export interface RegistrationType {
  /** 医疗服务主键（string，防 Long 精度丢失） */
  id: string;
  /** 挂号类型名称 */
  name: string;
  /** 诊查费 ActivityDefinition 主键 CSV */
  activityIds?: string;
  /** 已绑诊查费名称摘要（只读） */
  activityNames?: string;
  /** 业务编码 */
  busNo?: string;
  /** 绑定 AD 售价合计（元，只读） */
  amount?: number | string | null;
  /** 启用标志：1 启用，0 停用 */
  activeFlag?: number;
}

/** 新增/编辑写请求：仅名称 + 诊查费绑定 CSV */
export interface SaveRegistrationTypeParams {
  name: string;
  /** 诊查费 AD 主键 CSV；零元号可空串 */
  activityIds?: string;
}

/** 诊查费候选项（IF-BAS-REG-02） */
export interface ConsultationActivityOption {
  /** AD 主键（string） */
  id: string;
  /** 项目名称 */
  name: string;
  /** 业务编码 */
  busNo?: string;
  /** 当前 SALE 售价（元） */
  salePrice?: number | string | null;
}

/** 判断挂号类型是否启用 */
export function isRegistrationTypeActive(row: Pick<RegistrationType, 'activeFlag'>) {
  return row.activeFlag === REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE;
}

/** 解析 activity_ids CSV 为有序 string 主键列表 */
export function parseRegistrationActivityIds(csv?: string | null): string[] {
  if (!csv || !String(csv).trim()) {
    return [];
  }
  const result: string[] = [];
  const seen = new Set<string>();
  // 按逗号拆分并去重保序
  for (const raw of String(csv).split(',')) {
    const id = raw.trim();
    if (!id || seen.has(id)) {
      continue;
    }
    seen.add(id);
    result.push(id);
  }
  return result;
}

/** 将选中 AD 主键列表拼为 CSV */
export function joinRegistrationActivityIds(ids: string[] | undefined | null): string {
  if (!ids || ids.length === 0) {
    return '';
  }
  // 仅保留非空主键
  return ids
    .map((id) => String(id).trim())
    .filter(Boolean)
    .join(',');
}

export async function getRegistrationTypeListApi() {
  return publicRequestClient.post<RegistrationType[]>('/registrationType/getRegistrationTypeList');
}

/** 诊查费候选项（维护弹窗多选） */
export async function listConsultationActivityOptionsApi(keyword?: string) {
  return publicRequestClient.post<ConsultationActivityOption[]>(
    '/registrationType/listConsultationActivityOptions',
    { keyword: keyword?.trim() || undefined },
  );
}

export async function addRegistrationTypeApi(data: SaveRegistrationTypeParams) {
  return publicRequestClient.post('/registrationType/createRegistrationType', data);
}

export async function updateRegistrationTypeApi(data: SaveRegistrationTypeParams & { id: string }) {
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
