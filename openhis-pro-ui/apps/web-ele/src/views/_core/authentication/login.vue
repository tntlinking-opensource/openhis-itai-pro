<script lang="ts" setup>
import type { VbenFormSchema } from '@zxplat-core/form-ui';

import type { AuthApi } from '#/api/core/auth';
import type { LoginScopeHospital, LoginScopeOrganization } from '#/utils/auth/login-scope-flow';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  AuthenticationHospitalList,
  AuthenticationLogin,
  AuthenticationOrganizationList,
  AuthenticationTenantList,
} from '@zxplat/common-ui/es/authentication';
import { $t } from '@zxplat/locales';
import { useAccessStore, useUserStore } from '@zxplat/stores';

import { Loading } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElMessage } from 'element-plus';
import { z } from 'zod';

import { loginByOrgApi } from '#/api/core/auth';
import { getMenuTreeByLoginUserApi } from '#/api/core/system/menu';
import { resolvePostLoginPath } from '#/constants/app';
import { useAuthStore } from '#/store';
import { convertMenuData } from '#/utils/auth/convert-menu-data';
import {
  LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE,
  LOGIN_NO_DEPARTMENT_MESSAGE,
  resolveDepartmentOutcomeFromList,
  resolveDepartmentsAfterHospital,
  resolveLoginScopeAfterTenant,
} from '#/utils/auth/login-scope-flow';
import { requireLoginScopeReady } from '#/utils/auth/require-login-scope-ready';

defineOptions({ name: 'Login' });

interface QuickLoginAccount {
  label: string;
  userName: string;
  password: string;
}

/** 登录页快捷账号：拼音用户名，统一密码 123456 */
const QUICK_LOGIN_ACCOUNTS: QuickLoginAccount[] = [
  { label: '医生', userName: 'yisheng', password: '123456' },
  { label: '住院医生', userName: 'zyyisheng', password: '123456' },
  { label: '护士', userName: 'hushi', password: '123456' },
  { label: '收费', userName: 'shoufei', password: '123456' },
  { label: '药房', userName: 'yaofang', password: '123456' },
  { label: '库管', userName: 'kuguan', password: '123456' },
  { label: '财务', userName: 'caiwu', password: '123456' },
];

/**
 * 开发辅助登录（admin 预填 + 快捷角色）：主线开发/打包、院点开发均开启；
 * 仅院点生产打包（PROD + VITE_ZXPLAT_PROJECT）关闭。
 */
const showDevLoginAids =
  !import.meta.env.PROD || !String(import.meta.env.VITE_ZXPLAT_PROJECT ?? '').trim();

const authStore = useAuthStore();
const accessStore = useAccessStore();
const userStore = useUserStore();
const router = useRouter();

const authenticationLoginRef = ref<InstanceType<typeof AuthenticationLogin>>();
const loginSuccess = ref(false);
const showTenantList = ref(false);
const showHospitalList = ref(false);
const showOrganizationList = ref(false);
const organizationLoading = ref(false);
const selectedTenantId = ref<number | string>();
const selectedHospitalId = ref<number | string>();
const selectedOrganizationId = ref<number | string>();

const tenants = ref<
  Array<{
    code?: string;
    description?: string;
    id: number;
    logo?: string;
    name: string;
  }>
>([]);

const hospitals = ref<LoginScopeHospital[]>([]);

const organizations = ref<LoginScopeOrganization[]>([]);
/** 科室步骤为空或失败时的说明，展示在科室列表上方 */
const departmentStepHint = ref('');
/** 登录范围解析过渡页文案（自动选租户/医院/科室时不展示具体步骤面板） */
const scopeLoadingMessage = ref('正在进入系统...');

/** 已登录但尚未展示任一步骤面板（租户/医院/科室），用于避免步骤切换间隙白屏 */
const showScopeLoading = computed(
  () =>
    loginSuccess.value &&
    !showTenantList.value &&
    !showHospitalList.value &&
    !showOrganizationList.value,
);

function transformTenantData(apiData: AuthApi.TenantInfo[]) {
  return apiData.map((item) => ({
    id: item.id,
    name: item.name,
    code: item.code,
    description: item.description || undefined,
    logo: undefined,
  }));
}

/** 无科室或拉取失败时回退：多医院回医院列表，否则回租户或登录页 */
function revertAfterNoDepartment(options?: { fromHospital?: boolean }) {
  selectedOrganizationId.value = undefined;
  authStore.setSelectedOrganization('');
  showOrganizationList.value = false;

  if (options?.fromHospital && hospitals.value.length > 1) {
    selectedHospitalId.value = undefined;
    authStore.setSelectedHospital('');
    showHospitalList.value = true;
    return;
  }

  showHospitalList.value = false;
  if (tenants.value.length > 1) {
    showTenantList.value = true;
    return;
  }

  selectedTenantId.value = undefined;
  authStore.setSelectedTenant('');
  authStore.setSelectedHospital('');
  loginSuccess.value = false;
}

/** 按科室步骤结果：空=医院级、单=自动进、多=选科面板 */
async function applyDepartmentOutcome(
  outcome: import('#/utils/auth/login-scope-flow').LoginDepartmentStepOutcome,
) {
  if (outcome.kind === 'auto') {
    await handleOrganizationChange(outcome.department);
    return;
  }
  if (outcome.kind === 'pick') {
    loginSuccess.value = true;
    organizations.value = outcome.departments;
    showOrganizationList.value = true;
    await nextTick();
    return;
  }
  organizations.value = [];
  if (outcome.kind === 'empty') {
    await proceedToSystemWithoutOrganization();
    return;
  }
  loginSuccess.value = true;
  showOrganizationList.value = true;
  departmentStepHint.value = outcome.message || LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE;
  ElMessage.error(departmentStepHint.value);
}

/** 点击快捷账号：回填表单并走与普通登录相同的提交流程 */
async function handleQuickLogin(account: QuickLoginAccount) {
  if (authStore.loginLoading) {
    return;
  }

  const formApi = authenticationLoginRef.value?.getFormApi?.();
  if (formApi) {
    await formApi.setFieldValue('userName', account.userName);
    await formApi.setFieldValue('password', account.password);
  }

  await handleLogin({
    userName: account.userName,
    password: account.password,
  });
}

async function handleLogin(values: any) {
  try {
    await authStore.authLogin(values, async ({ tenants: tenantsList, userInfo, configuration }) => {
      if (userInfo) {
        userStore.setUserInfo({
          ...userInfo,
          roles: configuration?.currentUser?.roles,
        } as any);
      }
      if (configuration?.currentUser?.roles?.length) {
        userStore.setUserRoles(configuration.currentUser.roles);
      }
      if (configuration?.auth.grantedPolicies) {
        const permissions = configuration.auth.grantedPolicies;
        accessStore.setAccessCodes(Object.keys(permissions).filter((k) => permissions[k]));
      }

      // 多租户才切到选租户面板；单租户自动进系统时保持登录表单 loading，不插中间转圈页
      if (tenantsList && tenantsList.length > 0) {
        tenants.value = transformTenantData(tenantsList);
        if (tenantsList.length === 1) {
          await handleTenantSelect(tenants.value[0]);
          return;
        }
        loginSuccess.value = true;
        showTenantList.value = true;
        return;
      }
      loginSuccess.value = true;
      showTenantList.value = true;
    });
  } catch (error: any) {
    loginSuccess.value = false;
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      '登录失败，请稍后重试';
    ElMessage.error(errorMessage);
    await nextTick();
    const formApi = authenticationLoginRef.value?.getFormApi?.();
    if (formApi) {
      formApi.resetForm?.();
      await nextTick();
      const captchaComponent = formApi.getFieldComponentRef<{ resume?: () => void }>('captcha');
      if (captchaComponent?.resume) {
        captchaComponent.resume();
      }
    }
    throw error;
  }
}

function sameTenantId(a: number | string | undefined, b: number | string | undefined): boolean {
  if (a === undefined || b === undefined) return false;
  return String(a) === String(b);
}

async function handleTenantSelect(tenant: any) {
  if (
    sameTenantId(selectedTenantId.value, tenant.id) &&
    (showHospitalList.value || showOrganizationList.value)
  ) {
    return;
  }

  selectedTenantId.value = tenant.id;
  authStore.setSelectedTenant(String(tenant.id));
  selectedHospitalId.value = undefined;
  authStore.setSelectedHospital('');
  selectedOrganizationId.value = undefined;
  authStore.setSelectedOrganization('');
  authStore.setOrganizationUnitSkipped(false);
  showTenantList.value = false;
  showHospitalList.value = false;
  showOrganizationList.value = false;
  organizationLoading.value = true;

  try {
    // 一次拉齐医院+（单院）科室，避免 getHospitals→selectHospital→getDepts 三连慢
    const scope = await resolveLoginScopeAfterTenant(tenant.id);
    hospitals.value = scope.hospitals;
    if (scope.hospitals.length > 1) {
      loginSuccess.value = true;
      await nextTick();
      showHospitalList.value = true;
      return;
    }
    if (scope.hospitals.length === 1) {
      selectedHospitalId.value = scope.hospitals[0]!.id;
      authStore.setSelectedHospital(
        String(scope.hospitals[0]!.id),
        String(scope.hospitals[0]!.name ?? '').trim(),
      );
    }
    await applyDepartmentOutcome(resolveDepartmentOutcomeFromList(scope.departments));
  } catch (error: unknown) {
    console.error('Failed to resolve hospitals:', error);
    const message =
      error instanceof Error
        ? error.message
        : (error as { message?: string })?.message || '获取医院列表失败，请稍后重试';
    ElMessage.error(message);
    if (tenants.value.length > 1) {
      loginSuccess.value = true;
      showTenantList.value = true;
    } else {
      selectedTenantId.value = undefined;
      authStore.setSelectedTenant('');
      loginSuccess.value = false;
    }
  } finally {
    organizationLoading.value = false;
  }
}

/** 用户选定医院后写入会话，再拉取该医院下可登录科室 */
async function handleHospitalSelect(hospital: LoginScopeHospital) {
  selectedHospitalId.value = hospital.id;
  authStore.setSelectedHospital(String(hospital.id), String(hospital.name ?? '').trim());
  showHospitalList.value = false;
  organizationLoading.value = true;
  try {
    const outcome = await resolveDepartmentsAfterHospital(hospital.id);
    await applyDepartmentOutcome(outcome);
  } catch (error: unknown) {
    console.error('Failed to select hospital:', error);
    const message =
      error instanceof Error
        ? error.message
        : (error as { message?: string })?.message || '选择医院失败，请稍后重试';
    ElMessage.error(message);
    showOrganizationList.value = false;
    showHospitalList.value = true;
  } finally {
    organizationLoading.value = false;
  }
}

function handleBackFromHospitalList() {
  showHospitalList.value = false;
  selectedHospitalId.value = undefined;
  authStore.setSelectedHospital('');
  if (tenants.value.length > 1) {
    showTenantList.value = true;
  } else {
    loginSuccess.value = false;
  }
}

/** 步骤切换加载中返回：优先回到上一级选择，否则回到登录表单 */
function handleScopeLoadingBack() {
  organizationLoading.value = false;
  departmentStepHint.value = '';
  scopeLoadingMessage.value = '正在进入系统...';
  if (hospitals.value.length > 1) {
    showHospitalList.value = true;
    return;
  }
  if (tenants.value.length > 1) {
    showTenantList.value = true;
    return;
  }
  handleBackToLoginForm();
}

/** 重置登录范围状态并回到账号密码登录 */
function handleBackToLoginForm() {
  showTenantList.value = false;
  showHospitalList.value = false;
  showOrganizationList.value = false;
  organizationLoading.value = false;
  departmentStepHint.value = '';
  scopeLoadingMessage.value = '正在进入系统...';
  selectedTenantId.value = undefined;
  selectedHospitalId.value = undefined;
  selectedOrganizationId.value = undefined;
  authStore.setSelectedTenant('');
  authStore.setSelectedHospital('');
  authStore.setSelectedOrganization('');
  authStore.setOrganizationUnitSkipped(false);
  authStore.setPlatformAdmin(false);
  loginSuccess.value = false;
}

function handleBackFromOrganizationList() {
  showOrganizationList.value = false;
  departmentStepHint.value = '';
  selectedOrganizationId.value = undefined;
  authStore.setSelectedOrganization('');
  if (hospitals.value.length > 1) {
    showHospitalList.value = true;
  } else if (tenants.value.length > 1) {
    showTenantList.value = true;
  } else {
    loginSuccess.value = false;
  }
}

function handleBackFromTenantList() {
  showTenantList.value = false;
  selectedTenantId.value = undefined;
  authStore.setSelectedTenant('');
  loginSuccess.value = false;
}

async function handleOrganizationChange(organization: LoginScopeOrganization) {
  selectedOrganizationId.value = organization.id;
  try {
    await proceedToSystem(String(organization.id));
  } catch {
    selectedOrganizationId.value = undefined;
    authStore.setSelectedOrganization('');
    showOrganizationList.value = true;
  }
}

async function proceedToSystem(organizationId: string) {
  if (!organizationId) {
    ElMessage.warning(LOGIN_NO_DEPARTMENT_MESSAGE);
    revertAfterNoDepartment();
    return;
  }

  try {
    await loginByOrgApi({
      organizationUnitId: organizationId,
    });
    authStore.setSelectedOrganization(organizationId);
    authStore.setOrganizationUnitSkipped(false);
    await finishLoginAndNavigate();
  } catch (error) {
    console.error('Failed to proceed to system:', error);
    authStore.setSelectedOrganization('');
    const message =
      error instanceof Error
        ? error.message
        : (error as { message?: string })?.message || '选择科室失败，请重试';
    ElMessage.error(message);
    throw error;
  }
}

/** 无可登录科室时以医院级进入：选租户/医院后会话已无科室，不再打 skip（省一轮 OU 校验） */
async function proceedToSystemWithoutOrganization() {
  try {
    authStore.setOrganizationUnitSkipped(true);
    authStore.setSelectedOrganization('');
    await finishLoginAndNavigate();
  } catch (error) {
    console.error('Failed to bootstrap login without organization:', error);
    authStore.setOrganizationUnitSkipped(false);
    const message =
      error instanceof Error
        ? error.message
        : (error as { message?: string })?.message || '进入系统失败，请稍后重试';
    ElMessage.error(message);
    revertAfterNoDepartment();
    throw error;
  }
}

/** 登录范围就绪后拉菜单并跳转；配置刷新后台进行，不挡进首页 */
async function finishLoginAndNavigate() {
  const menus = await getMenuTreeByLoginUserApi();
  // 权限配置与进首页解耦，避免医院级登录多等一轮
  void authStore.refreshApplicationConfiguration();
  const menuData = menus || [];

  const convertedMenus = convertMenuData(menuData);

  accessStore.setIsAccessChecked(false);

  const [{ generateAccess }, { getAccessRoutes }] = await Promise.all([
    import('#/router/access'),
    import('#/router/routes'),
  ]);

  const { accessibleMenus, accessibleRoutes } = await generateAccess({
    roles: [],
    router,
    routes: await getAccessRoutes(),
    menuData: convertedMenus,
  });

  accessStore.setAccessMenus(accessibleMenus);
  accessStore.setAccessRoutes(accessibleRoutes);
  accessStore.setIsAccessChecked(true);

  void import('#/utils/print/runtime/hiprint-client').then(({ schedulePrintClientWarmup }) => {
    schedulePrintClientWarmup();
  });

  await nextTick();
  const redirectParam = router.currentRoute.value.query?.redirect as string | undefined;
  const targetPath = resolvePostLoginPath(redirectParam);

  await router.push(targetPath);
}

onMounted(async () => {
  if (!accessStore.accessToken) {
    return;
  }

  const selectedTenant = authStore.getSelectedTenant();
  const selectedOrganization = authStore.getSelectedOrganization();
  if (selectedTenant && (selectedOrganization || authStore.isOrganizationUnitSkipped())) {
    try {
      await requireLoginScopeReady();
      return;
    } catch {
      authStore.setSelectedOrganization('');
      authStore.setOrganizationUnitSkipped(false);
    }
  }

  const storedTenants = authStore.getTenantList();
  if (storedTenants.length > 0) {
    tenants.value = transformTenantData(storedTenants);
    loginSuccess.value = true;

    if (selectedTenant && !authStore.getSelectedOrganization()) {
      selectedTenantId.value = selectedTenant;
      await handleTenantSelect({ id: selectedTenant });
    } else if (!selectedTenant) {
      if (storedTenants.length === 1) {
        await handleTenantSelect(tenants.value[0]);
      } else {
        showTenantList.value = true;
      }
    }
  }
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'userName',
      label: $t('authentication.username'),
      defaultValue: showDevLoginAids ? 'admin' : undefined,
      // 院点生产无预填时值为 undefined，须配 required_error，否则 Zod 默认英文 Required
      rules: z
        .string({ required_error: $t('authentication.usernameTip') })
        .min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      defaultValue: showDevLoginAids ? 'Admin@123456' : undefined,
      rules: z
        .string({ required_error: $t('authentication.passwordTip') })
        .min(1, { message: $t('authentication.passwordTip') }),
    },
    // {
    //   component: markRaw(SliderCaptcha),
    //   fieldName: 'captcha',
    //   rules: z.boolean().refine((value) => value, {
    //     message: $t('authentication.verifyRequiredTip'),
    //   }),
    // },
  ];
});
</script>

<template>
  <Transition
    enter-active-class="animate-in fade-in duration-300"
    leave-active-class="animate-out fade-out duration-300"
    mode="out-in"
  >
    <div
      v-if="!loginSuccess"
      class="mx-auto flex h-full w-full flex-col justify-center sm:max-w-md"
    >
      <AuthenticationLogin
        ref="authenticationLoginRef"
        :form-schema="formSchema"
        :loading="authStore.loginLoading"
        @submit="handleLogin"
      />

      <div v-if="showDevLoginAids" class="mt-2 flex items-center justify-center gap-5">
        <ElButton
          v-for="account in QUICK_LOGIN_ACCOUNTS"
          :key="account.userName"
          :disabled="authStore.loginLoading"
          link
          type="primary"
          @click="handleQuickLogin(account)"
        >
          {{ account.label }}
        </ElButton>
      </div>
    </div>

    <div v-else-if="showTenantList" class="mx-auto h-full w-full sm:max-w-3xl">
      <AuthenticationTenantList
        v-model="selectedTenantId"
        :loading="false"
        :tenants="tenants"
        :visible="showTenantList"
        @back="handleBackFromTenantList"
        @change="handleTenantSelect"
      />
    </div>

    <div v-else-if="showHospitalList" class="mx-auto h-full w-full sm:max-w-3xl">
      <AuthenticationHospitalList
        v-model="selectedHospitalId"
        :hospitals="hospitals"
        :loading="organizationLoading"
        :visible="showHospitalList"
        @back="handleBackFromHospitalList"
        @change="handleHospitalSelect"
      />
    </div>

    <div v-else-if="showOrganizationList" class="mx-auto h-full w-full sm:max-w-3xl">
      <p
        v-if="departmentStepHint"
        class="mb-4 text-sm leading-relaxed"
        style="color: var(--el-color-warning)"
      >
        {{ departmentStepHint }}
      </p>
      <AuthenticationOrganizationList
        v-model="selectedOrganizationId"
        :loading="organizationLoading"
        :organizations="organizations"
        :visible="true"
        @back="handleBackFromOrganizationList"
        @change="handleOrganizationChange"
      />
    </div>

    <div
      v-else-if="showScopeLoading"
      class="mx-auto flex h-full w-full flex-col items-center justify-center gap-4 sm:max-w-3xl"
    >
      <ElIcon :size="28" class="is-loading" style="color: var(--el-color-primary)">
        <Loading />
      </ElIcon>
      <div class="text-sm" style="color: var(--el-text-color-regular)">
        {{ scopeLoadingMessage }}
      </div>
      <ElButton link type="primary" @click="handleScopeLoadingBack">返回</ElButton>
    </div>

    <div
      v-else-if="loginSuccess"
      class="mx-auto flex h-full w-full flex-col items-center justify-center gap-4 sm:max-w-3xl"
    >
      <div class="text-sm" style="color: var(--el-text-color-regular)">
        登录步骤异常，请返回重试
      </div>
      <ElButton link type="primary" @click="handleBackToLoginForm">返回登录</ElButton>
    </div>
  </Transition>
</template>
