/** Stale 跨角色写失败统一提示（SYS-IMP-007 E1/E2） */
export const STALE_CROSS_ROLE_REFRESH_MESSAGE = '数据已变更，请刷新后重试';

/** 后端未返回可读文案时的 errorCode 兜底（优先仍用 response.message） */
const STALE_CROSS_ROLE_ERROR_MESSAGES: Record<string, string> = {
  'EX-OPC-411': '费用状态已变更，无法结算，请刷新列表后重试',
  'EX-IP-ORD-103': '医嘱已有执行/发药/费用记录，无法继续操作，请刷新列表后重试',
  'EX-IP-ORD-107': '患者已办理出院或已结算，不可变更医嘱及费用，请刷新列表后重试',
  'EX-IP-CXL-202': '该计划点不可取消（可能已被取消或状态已变更），请刷新列表后重试',
  'EX-IP-CXL-203': '费用已结算，无法取消执行，请刷新列表后重试',
  'EX-IP-CXL-204': '药品正在配药中，无法取消执行，请刷新列表后重试',
  'EX-IP-CXL-206': '患者已办理出院或已结算，不可变更医嘱及费用，请刷新列表后重试',
  'EX-IP-PRC-406': '患者已办理出院或已结算，不可变更医嘱及费用，请刷新列表后重试',
  'EX-IP-STK-208': '患者已办理出院或已结算，不可变更医嘱及费用，请刷新列表后重试',
  'EX-IP-EXE-301': '该计划点已执行或已登记不执行，请刷新列表后重试',
  'EX-IP-EXE-303': '仅已校对或已停嘱医嘱允许执行；停嘱后的计划点不可执行，请刷新列表后重试',
  'EX-IP-EXE-307': '患者已办理出院或已结算，不可变更医嘱及费用，请刷新列表后重试',
  'EX-IP-ATD-301': '长期医嘱需要先停嘱，请处理后重试',
  'EX-IP-ATD-302': '存在未执行或未完成的临时医嘱，请处理后重试',
  'EX-IP-ATD-303': '患者有待取或待退药品，请处理后重试',
  'EX-IP-ATD-307': '长期医嘱仍有待执行时点，请执行或不执行登记后重试',
  'EX-IP-SUM-201': '领药汇总状态已变更，请刷新列表后重试',
  'EX-IPS-201': '汇总单核发状态已变更，请刷新列表后重试',
  'EX-IP-ATD-201': '床位状态已变更，请刷新床位图后重试',
  'EX-IP-ATD-207': '床位已被预约占用，请刷新床位图后重试',
  'EX-IP-ATD-304': '患者出院状态已变更，请刷新后重试',
  'EX-IP-ATD-402': '患者召回状态已变更，请刷新后重试',
  'EX-IP-ATD-504': '清床状态已变更，请刷新后重试',
  'EX-IP-REG-003': '入院登记状态已变更，请刷新后重试',
  MEDICATION_DISPENSE_INVALID_STATUS: '发药单状态已变更，请刷新列表后重试',
  MEDICATION_DISPENSE_NOT_FOUND: '发药单不存在或已撤销，请刷新列表后重试',
  APPOINTMENT_RULE_CONFLICT: '预约规则冲突，请刷新后重试',
  SLOT_003: '号源状态已变更，请刷新后重试',
  'REG-BIZ-409': '当日已有有效预约，请刷新后重试',
};

/**
 * 跨角色 Stale / 源态冲突业务码（Gate 矩阵 §1～§3）。
 * 命中时前端统一文案，并触发列表 invalidate（E3）。
 */
export const STALE_CROSS_ROLE_ERROR_CODES = new Set<string>([
  // 门诊收费 / 签退
  'EX-OPC-411',
  // 住院签退
  'EX-IP-ORD-103',
  'EX-IP-ORD-107',
  // 取消执行
  'EX-IP-CXL-202',
  'EX-IP-CXL-203',
  'EX-IP-CXL-204',
  'EX-IP-CXL-206',
  // 护士划价
  'EX-IP-PRC-406',
  // 皮试划价
  'EX-IP-STK-208',
  // 护士执行
  'EX-IP-EXE-301',
  'EX-IP-EXE-303',
  'EX-IP-EXE-307',
  'EX-IP-ATD-301',
  'EX-IP-ATD-302',
  'EX-IP-ATD-303',
  'EX-IP-ATD-307',
  // 汇总 / 核发
  'EX-IP-SUM-201',
  'EX-IPS-201',
  // 入出转 / 登记
  'EX-IP-ATD-201',
  'EX-IP-ATD-207',
  'EX-IP-ATD-304',
  'EX-IP-ATD-402',
  'EX-IP-ATD-504',
  'EX-IP-REG-003',
  // 发药
  'MEDICATION_DISPENSE_INVALID_STATUS',
  'MEDICATION_DISPENSE_NOT_FOUND',
  // 预约 / 号源
  'APPOINTMENT_RULE_CONFLICT',
  'SLOT_003',
  'REG-BIZ-409',
]);

/** 从请求异常解析后端 errorCode */
export function extractRequestErrorCode(error: unknown): string | undefined {
  const err = error as {
    errorCode?: string;
    response?: { data?: { errorCode?: string } };
    data?: { errorCode?: string };
  };
  const code = err?.errorCode ?? err?.response?.data?.errorCode ?? err?.data?.errorCode;
  return typeof code === 'string' && code.trim().length > 0 ? code.trim() : undefined;
}

/** 从请求异常解析后端 message（兼容 axios / 业务包装） */
export function extractRequestErrorMessage(error: unknown): string | undefined {
  const err = error as {
    message?: string;
    msg?: string;
    response?: { data?: { message?: string; error?: string } };
    data?: { message?: string; error?: string };
  };
  const text = String(
    err?.response?.data?.message ??
      err?.response?.data?.error ??
      err?.data?.message ??
      err?.data?.error ??
      err?.message ??
      err?.msg ??
      '',
  ).trim();
  return text.length > 0 ? text : undefined;
}

function isUserFacingStaleBackendMessage(message: string): boolean {
  if (/^Request failed with status code|^Network Error|^timeout of \d+ms exceeded/i.test(message)) {
    return false;
  }
  if (/^EX-[A-Z0-9-]+$/i.test(message)) {
    return false;
  }
  return true;
}

/** 将 Stale 业务码转为用户可读文案（优先后端 message） */
export function resolveStaleCrossRoleUserMessage(
  errorCode: string,
  backendMessage?: string,
): string {
  const text = String(backendMessage ?? '').trim();
  if (text && isUserFacingStaleBackendMessage(text)) {
    if (/刷新/.test(text)) {
      return text;
    }
    return `${text}，请刷新列表后重试`;
  }
  return STALE_CROSS_ROLE_ERROR_MESSAGES[errorCode] ?? STALE_CROSS_ROLE_REFRESH_MESSAGE;
}

/** 从请求异常解析 Stale 用户提示（页面手动处理 stale 时用） */
export function getStaleCrossRoleUserMessage(error: unknown): string | null {
  const code = extractRequestErrorCode(error);
  if (!code || !STALE_CROSS_ROLE_ERROR_CODES.has(code)) {
    return null;
  }
  return resolveStaleCrossRoleUserMessage(code, extractRequestErrorMessage(error));
}

/** 是否为 Stale 跨角色 / 源态冲突错误（含 message 内嵌 errorCode 兜底） */
export function isStaleCrossRoleError(error: unknown): boolean {
  const code = extractRequestErrorCode(error);
  if (code && STALE_CROSS_ROLE_ERROR_CODES.has(code)) {
    return true;
  }
  const message =
    error instanceof Error
      ? error.message
      : typeof error === 'object' && error !== null && 'message' in error
        ? String((error as { message?: unknown }).message ?? '')
        : String(error ?? '');
  for (const staleCode of STALE_CROSS_ROLE_ERROR_CODES) {
    if (message.includes(staleCode)) {
      return true;
    }
  }
  return false;
}

/**
 * 全局错误拦截器用：命中 Stale 码则返回可读文案，否则 null 走默认展示。
 * 优先展示后端业务 message（如「计划点晚于停嘱时间，不可执行」），避免笼统「数据已变更」。
 */
export function formatStaleCrossRoleError(
  errorCode: string | undefined,
  message?: string,
): string | null {
  if (errorCode && STALE_CROSS_ROLE_ERROR_CODES.has(errorCode)) {
    return resolveStaleCrossRoleUserMessage(errorCode, message);
  }
  return null;
}

/**
 * Stale 写失败时刷新本地列表/床位图（E3）。
 * @returns 是否已识别为 Stale 并完成 refresh
 */
export async function refreshOnStaleCrossRoleError(
  error: unknown,
  refresh: () => void | Promise<void>,
): Promise<boolean> {
  if (!isStaleCrossRoleError(error)) {
    return false;
  }
  await refresh();
  return true;
}
