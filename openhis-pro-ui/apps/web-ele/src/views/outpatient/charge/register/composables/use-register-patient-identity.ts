import type { Ref } from 'vue';

import type { PatientItemDto } from '../services';
import type {
  PatientIdentitySnapshot,
  PatientIdentitySource,
} from '../services/register-patient-identity.service';
import {
  buildFormRecordFromSnapshot,
  buildSnapshotFromEcVoucher,
  buildSnapshotFromIdCard,
  buildSnapshotFromSocialCard,
  PATIENT_IDENTITY_SOURCE_LABEL,
  snapshotHasInsuranceCredential,
  validateSnapshotForInsurance1101,
} from '../services/register-patient-identity.service';
import type { RegisterFormAreaExpose } from '../services/register.types';

import type { YbEcVoucherQueryInput } from '#/chs/utils/yb-plugin';

import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  formatYbPluginError,
  formatYbPluginOfflineMessage,
  getYbEcOrgId,
  getYbEcVoucherUrl,
  invokeSocialCardRead,
  invokeYbPlugin,
  isYbPluginOnline,
  parseEcVoucherPlatformError,
  parseEcVoucherReadResult,
  YB_EC_BUSINESS_TYPE,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';
import { useAuthStore } from '#/store/auth';

import { pageRegisterPatientsForSelect } from '../services/register.service';

export type CardReadAction = PatientIdentitySource;

export interface UseRegisterPatientIdentityOptions {
  registerFormAreaRef: Ref<null | RegisterFormAreaExpose>;
  patientRowById: Ref<Record<string, PatientItemDto>>;
  selectedPatientId: Ref<string>;
  selectedPatientLabel: Ref<string>;
  applyInsuranceSnapshot: (snapshot: PatientIdentitySnapshot) => void;
  fillPatientNameIfEmpty: (name: string) => void;
  resetBenefitState: () => void;
  onPatientSelect: (
    patientId: string,
    options?: { preserveInsuranceRead?: boolean },
  ) => Promise<void>;
  onBootstrapBenefit: () => Promise<void>;
}

export function useRegisterPatientIdentity(options: UseRegisterPatientIdentityOptions) {
  const userStore = useUserStore();
  const authStore = useAuthStore();

  const cardReadAction = ref<CardReadAction | null>(null);

  function isCardReadLoading(kind: CardReadAction): boolean {
    return cardReadAction.value === kind;
  }

  function isCardReadDisabled(kind: CardReadAction): boolean {
    return cardReadAction.value !== null && cardReadAction.value !== kind;
  }

  async function withCardReadTask<T>(kind: CardReadAction, task: () => Promise<T>): Promise<T> {
    cardReadAction.value = kind;
    try {
      return await task();
    } finally {
      if (cardReadAction.value === kind) {
        cardReadAction.value = null;
      }
    }
  }

  function beginIdentityReadSession() {
    options.resetBenefitState();
    options.selectedPatientId.value = '';
    options.selectedPatientLabel.value = '';
  }

  async function readIdCardSnapshot(): Promise<null | PatientIdentitySnapshot> {
    if (!(await isYbPluginOnline())) {
      ElMessage.warning(formatYbPluginOfflineMessage());
      return null;
    }
    try {
      const payload = await invokeYbPlugin({
        FunctionId: YB_PLUGIN_FUNCTION_ID.ID_CARD,
        IP: DEFAULT_CARD_READER_HOST,
        PORT: DEFAULT_CARD_READER_PORT,
        TIMEOUT: 60,
        LOG_PATH: 'C:/neu_log/',
        SFZ_DRIVER_TYPE: 0,
      });
      const idCardNo = payload.trim();
      if (!idCardNo) {
        ElMessage.warning('读卡未返回有效信息');
        return null;
      }
      return buildSnapshotFromIdCard(idCardNo);
    } catch (error) {
      ElMessage.error(formatYbPluginError(error));
      return null;
    }
  }

  async function readSocialCardSnapshot(): Promise<null | PatientIdentitySnapshot> {
    if (!(await isYbPluginOnline())) {
      ElMessage.warning(formatYbPluginOfflineMessage());
      return null;
    }
    try {
      const card = await invokeSocialCardRead({
        FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
        IP: DEFAULT_CARD_READER_HOST,
        PORT: DEFAULT_CARD_READER_PORT,
        TIMEOUT: 60,
        LOG_PATH: 'C:/neu_log/',
        SFZ_DRIVER_TYPE: 0,
      });
      const snapshot = buildSnapshotFromSocialCard(card);
      if (!snapshot) {
        ElMessage.warning('读卡未返回证件号或卡号');
      }
      return snapshot;
    } catch (error) {
      ElMessage.error(formatYbPluginError(error));
      return null;
    }
  }

  async function readEcVoucherSnapshot(): Promise<null | PatientIdentitySnapshot> {
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
    const operatorId = String(userStore.userInfo?.userName ?? userStore.userInfo?.id ?? '').trim();
    const operatorName = String(
      userStore.userInfo?.name ?? userStore.userInfo?.userName ?? '',
    ).trim();
    if (!operatorId || !operatorName) {
      ElMessage.warning('请先登录，并确保操作员账号与姓名可用');
      return null;
    }

    const area = options.registerFormAreaRef.value;
    const values = area ? await area.getSubmitValues() : {};
    const deptId = String(values.departmentId ?? '').trim();
    const officeId = deptId || String(authStore.getSelectedOrganization() ?? '').trim() || '01';
    const officeName = (deptId && area?.getDepartmentLabel(deptId)) || officeId || '门诊';

    try {
      const ecInput: YbEcVoucherQueryInput = {
        FunctionId: YB_PLUGIN_FUNCTION_ID.EC_QUERY,
        url,
        orgId,
        businessType: YB_EC_BUSINESS_TYPE.OUTPATIENT_REGISTER,
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

  async function selectPatientByKeyword(keyword: string): Promise<boolean> {
    const normalized = keyword.trim().toUpperCase();
    if (!normalized) {
      return false;
    }
    const { content } = await pageRegisterPatientsForSelect({
      pageNo: 1,
      pageSize: 20,
      keyword: normalized,
    });
    const next = { ...options.patientRowById.value };
    for (const row of content) {
      next[row.patientId] = row;
    }
    options.patientRowById.value = next;
    if (content.length === 1) {
      const row = content[0];
      if (!row) {
        return true;
      }
      options.fillPatientNameIfEmpty(String(row.name ?? '').trim());
      await options.onPatientSelect(row.patientId, { preserveInsuranceRead: true });
      ElMessage.success('已匹配患者');
      return true;
    }
    if (content.length > 1) {
      ElMessage.warning(`找到 ${content.length} 位患者，请手动选择`);
      return false;
    }
    return false;
  }

  async function resolvePatientIdentity(snapshot: PatientIdentitySnapshot): Promise<void> {
    if (snapshotHasInsuranceCredential(snapshot)) {
      const validationError = validateSnapshotForInsurance1101(snapshot);
      if (validationError) {
        ElMessage.warning(validationError);
        return;
      }
      options.applyInsuranceSnapshot(snapshot);
    }

    const matched = await selectPatientByKeyword(snapshot.patientMatchKeyword);
    if (matched) {
      if (snapshotHasInsuranceCredential(snapshot)) {
        await options.onBootstrapBenefit();
      }
      return;
    }

    const area = options.registerFormAreaRef.value;
    if (!area) {
      if (snapshotHasInsuranceCredential(snapshot)) {
        ElMessage.warning('未找到匹配患者档案，请手动选择患者或先建档');
      }
      return;
    }

    area.applyPatientRecord(buildFormRecordFromSnapshot(snapshot));
    const label = snapshot.sourceLabel || PATIENT_IDENTITY_SOURCE_LABEL[snapshot.source];
    if (snapshot.certno) {
      ElMessage.success(`已读${label}，未找到档案，请补全信息后挂号`);
    } else {
      ElMessage.success(`已读${label}，请补全患者信息后挂号`);
    }
    await area.focusFirstField();

    if (snapshotHasInsuranceCredential(snapshot)) {
      await options.onBootstrapBenefit();
    }
  }

  async function readPatientIdentity(source: PatientIdentitySource): Promise<void> {
    await withCardReadTask(source, async () => {
      beginIdentityReadSession();
      let snapshot: null | PatientIdentitySnapshot = null;
      if (source === 'id') {
        snapshot = await readIdCardSnapshot();
      } else if (source === 'yb-card') {
        snapshot = await readSocialCardSnapshot();
      } else {
        snapshot = await readEcVoucherSnapshot();
      }
      if (!snapshot) {
        return;
      }
      await resolvePatientIdentity(snapshot);
    });
  }

  return {
    cardReadAction,
    isCardReadDisabled,
    isCardReadLoading,
    readPatientIdentity,
  };
}
