import { ElMessageBox } from 'element-plus';

import { router } from '#/router';

type UnmappedYbNoDetails = {
  action?: string;
  itemName?: string;
  productDefinitionId?: string;
  productTable?: string;
  projectCode?: string;
};

function asRecord(value: unknown): null | Record<string, unknown> {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function pickString(record: null | Record<string, unknown>, key: string): string | undefined {
  const raw = record?.[key];
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim();
  }
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return String(raw);
  }
  return undefined;
}

/** 从接口 errors 解析未映射国家码详情 */
export function parseUnmappedYbNoDetails(errors: unknown): UnmappedYbNoDetails | null {
  const record = asRecord(errors);
  if (!record) {
    return null;
  }
  if (pickString(record, 'action') !== 'SUPPLEMENT_YB_NO') {
    // 兼容仅有项目字段的旧结构
    if (!pickString(record, 'projectCode') && !pickString(record, 'productDefinitionId')) {
      return null;
    }
  }
  return {
    action: pickString(record, 'action'),
    itemName: pickString(record, 'itemName'),
    productDefinitionId: pickString(record, 'productDefinitionId'),
    productTable: pickString(record, 'productTable'),
    projectCode: pickString(record, 'projectCode'),
  };
}

function resolveCatalogRoute(details: UnmappedYbNoDetails | null): {
  path: string;
  query: Record<string, string>;
} {
  const table = details?.productTable ?? '';
  const openId = details?.productDefinitionId;
  const keyword = details?.projectCode ?? details?.itemName ?? '';
  const query: Record<string, string> = {};
  if (openId) {
    query.openId = openId;
  }
  if (keyword) {
    query.keyword = keyword;
  }

  if (table.includes('medication')) {
    return { path: '/basic/medicationcatalog', query };
  }
  if (table.includes('device')) {
    if (openId) {
      return { path: `/basic/devicecatalog/${openId}`, query: {} };
    }
    return { path: '/basic/devicecatalog', query };
  }
  return { path: '/basic/servicecatalog', query };
}

/**
 * 医保门禁：物价项目码未映射国家码 → 友好提示并引导跳转补码。
 * @returns 已处理则 true
 */
export function tryHandleUnmappedYbNoComplianceError(
  errorCode: string | undefined,
  rawMessage: string,
  errors: unknown,
): boolean {
  const msg = String(rawMessage ?? '').trim();
  const isUnmapped =
    errorCode === 'C-PROJECT-UNMAPPED' ||
    msg.includes('无国家码映射') ||
    msg.includes('尚未映射国家医保目录编码');
  if (!isUnmapped) {
    return false;
  }

  const details = parseUnmappedYbNoDetails(errors);
  const title = '无法医保收费：请先补充国家医保编码';
  const message =
    msg ||
    '当前项目仅有物价项目码（01*），尚未映射国家医保目录编码，无法医保收费。请到基础目录补充后重试。';

  void ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: '去补充医保编码',
    cancelButtonText: '关闭',
    closeOnClickModal: false,
    distinguishCancelAndClose: true,
  })
    .then(() => {
      const target = resolveCatalogRoute(details);
      void router.push({ path: target.path, query: target.query });
    })
    .catch(() => {
      // 用户关闭：无需处理
    });

  return true;
}

/** 是否已由全局拦截器弹出「补充医保编码」引导（页面 catch 勿再 toast） */
export function isUnmappedYbNoComplianceError(error: unknown): boolean {
  const err = error as {
    errorCode?: string;
    message?: string;
    response?: { data?: { errorCode?: string; message?: string } };
    data?: { errorCode?: string; message?: string };
  };
  const code = err?.errorCode ?? err?.response?.data?.errorCode ?? err?.data?.errorCode ?? '';
  const msg = String(err?.response?.data?.message ?? err?.data?.message ?? err?.message ?? '');
  return (
    code === 'C-PROJECT-UNMAPPED' ||
    msg.includes('无国家码映射') ||
    msg.includes('尚未映射国家医保目录编码')
  );
}
