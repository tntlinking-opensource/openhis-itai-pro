import type { PatientIdentitySnapshot } from './register-patient-identity.service';

import type { YbEcVoucherQueryInput } from '#/chs/utils/yb-plugin';

import { useUserStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  formatYbPluginError,
  formatYbPluginOfflineMessage,
  getYbEcOrgId,
  getYbEcVoucherUrl,
  invokeYbPlugin,
  isYbPluginOnline,
  parseEcVoucherPlatformError,
  parseEcVoucherReadResult,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';
import { useAuthStore } from '#/store/auth';

import { buildSnapshotFromEcVoucher } from './register-patient-identity.service';

/** 仅读取医保电子凭证（供历史收费刷新预结算，不联动患者/待遇）。 */
export async function readYbEcVoucherSnapshot(options?: {
  officeId?: string;
  officeName?: string;
}): Promise<null | PatientIdentitySnapshot> {
  const url = getYbEcVoucherUrl();
  const orgId = getYbEcOrgId();
  if (!url || !orgId) {
    ElMessage.warning('请配置 VITE_GLOB_YB_EC_VOUCHER_URL 与 VITE_GLOB_YB_EC_ORG_ID');
    return null;
  }
  if (!(await isYbPluginOnline())) {
    ElMessage.warning(formatYbPluginOfflineMessage());
    return null;
  }
  const userStore = useUserStore();
  const authStore = useAuthStore();
  const operatorId = String(
    userStore.userInfo?.userName ?? userStore.userInfo?.username ?? userStore.userInfo?.id ?? '',
  ).trim();
  const operatorName = String(
    userStore.userInfo?.realName ??
      userStore.userInfo?.name ??
      userStore.userInfo?.userName ??
      userStore.userInfo?.username ??
      '',
  ).trim();
  if (!operatorId || !operatorName) {
    ElMessage.warning('请先登录，并确保操作员账号与姓名可用');
    return null;
  }
  const officeId =
    String(options?.officeId ?? authStore.getSelectedOrganization() ?? '01').trim() || '01';
  const officeName = String(options?.officeName ?? officeId).trim() || '门诊';

  try {
    const ecInput: YbEcVoucherQueryInput = {
      FunctionId: YB_PLUGIN_FUNCTION_ID.EC_QUERY,
      url,
      orgId,
      businessType: '01101',
      businessInfo: '01',
      operatorId,
      operatorName,
      officeId,
      officeName,
      deviceType: '',
      IP: DEFAULT_CARD_READER_HOST,
      PORT: DEFAULT_CARD_READER_PORT,
      TIMEOUT: 60,
      LOG_PATH: 'C:/neu_log/',
      SFZ_DRIVER_TYPE: 0,
    };
    const payload = await invokeYbPlugin(ecInput, undefined, 65_000);
    const trimmed = payload.trim();
    if (!trimmed) {
      ElMessage.warning('医保码未返回有效信息');
      return null;
    }
    const ec = parseEcVoucherReadResult(trimmed);
    const snapshot = buildSnapshotFromEcVoucher(ec);
    if (!snapshot) {
      const platformErr = parseEcVoucherPlatformError(trimmed);
      ElMessage.warning(platformErr ? `医保码：${platformErr}` : '医保码未返回有效凭证信息');
    }
    return snapshot;
  } catch (error) {
    ElMessage.error(formatYbPluginError(error));
    return null;
  }
}
