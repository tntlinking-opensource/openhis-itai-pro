import { ElMessage } from 'element-plus';

import { insuranceSignInApi } from '#/chs/api/insurance-session';
import { resolveSignEndpointForSignIn } from '#/chs/utils/yb-plugin';

export interface EnsureInsuranceSignInOptions {
  /** 改由服务端 sign_mac 签到时是否提示（默认 true） */
  warnOnServerFallback?: boolean;
  /**
   * 仅走服务端签到（不探测本机 Zxplat.Win）。
   * 对账清算等无读卡场景使用；服务端用契约 sign_mac / 已有会话。
   */
  preferServerSignIn?: boolean;
}

/**
 * 调用 9001 签到：浏览器优先 Zxplat.Win 取 MAC/IP，失败则服务端 sign_mac / 插件回退。
 * 需在 1301/1312/9102 等需 sign_no 的接口之前调用。
 */
export async function ensureInsuranceSignIn(
  contractBusNo: string,
  options?: EnsureInsuranceSignInOptions,
): Promise<string> {
  if (options?.preferServerSignIn) {
    return insuranceSignInApi({ contractBusNo });
  }

  const warn = options?.warnOnServerFallback !== false;
  const endpoint = await resolveSignEndpointForSignIn();

  if (!endpoint.fromPlugin) {
    if (warn) {
      const reason = endpoint.fallbackReason ?? '';
      // 服务端 sign_mac 回退仍可成功签到；文案避免误判为「插件连不上」阻断业务
      const message = endpoint.pluginOnline
        ? `本机未取到签到 MAC（${reason}），已改由服务端完成签到。`
        : `本机未取到签到终端信息（${reason}），已改由服务端完成签到。`;
      ElMessage.warning({
        message,
        duration: 8000,
        showClose: true,
      });
    }
    return insuranceSignInApi({ contractBusNo });
  }

  return insuranceSignInApi({
    contractBusNo,
    mac: endpoint.mac,
    ip: endpoint.ip,
  });
}
