<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import type { AuthApi, Organization } from '#/api';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  AuthenticationLogin,
  AuthenticationOrganizationList,
  AuthenticationTenantList,
  z,
} from '@vben/common-ui';
import { $t } from '@vben/locales';
import { useAccessStore, useUserStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { getMenuTreeApi, getOrganizationApiByLoginUser, loginByOrgApi } from '#/api';
import { DEFAULT_HOME_PATH, resolvePostLoginPath } from '#/constants/app';
import { generateAccess } from '#/router/access';
import { accessRoutes } from '#/router/routes';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { convertMenuData } from '#/utils/convert-menu-data';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();
const accessStore = useAccessStore();
const userStore = useUserStore();
const router = useRouter();

const authenticationLoginRef = ref<InstanceType<typeof AuthenticationLogin>>();
const loginSuccess = ref(false);
const showTenantList = ref(false);
const showOrganizationList = ref(false);
const organizationLoading = ref(false);
const selectedTenantId = ref<number | string>();
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

const organizations = ref<
  Array<{
    code?: string;
    description?: string;
    id: number | string;
    logo?: string;
    name: string;
  }>
>([]);

function transformTenantData(apiData: AuthApi.TenantInfo[]) {
  return apiData.map((item) => ({
    id: item.id,
    name: item.name,
    code: item.code,
    description: item.description || undefined,
    logo: undefined,
  }));
}

function transformOrganizationData(apiData: AuthApi.OrganizationInfo[] | Organization[]) {
  return apiData.map((item) => ({
    id: item.id,
    name:
      (item as AuthApi.OrganizationInfo).displayName ||
      (item as Organization).displayName ||
      (item as Organization).name ||
      '',
    code: item.code || '',
    description:
      (item as AuthApi.OrganizationInfo).remark || (item as Organization).remark || undefined,
    logo: undefined,
  }));
}

async function handleLogin(values: any) {
  try {
    await authStore.authLogin(values, async ({ tenants: tenantsList, userInfo, configuration }) => {
      if (tenantsList && tenantsList.length > 0) {
        tenants.value = transformTenantData(tenantsList);

        if (tenantsList.length === 1) {
          const singleTenant = tenants.value[0];
          await handleTenantSelect(singleTenant);
        } else {
          showTenantList.value = true;
        }
      } else {
        showTenantList.value = true;
      }
      if (userInfo) {
        userStore.setUserInfo(userInfo as any);
      }
      loginSuccess.value = true;
      if (configuration?.auth.grantedPolicies) {
        const permissions = configuration.auth.grantedPolicies;
        accessStore.setAccessCodes(Object.keys(permissions).filter((k) => permissions[k]));
      }
    });
  } catch (error: any) {
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
  if (sameTenantId(selectedTenantId.value, tenant.id) && showOrganizationList.value) {
    return;
  }

  selectedTenantId.value = tenant.id;
  authStore.setSelectedTenant(String(tenant.id));
  selectedOrganizationId.value = undefined;
  authStore.setSelectedOrganization('');
  showTenantList.value = false;
  organizationLoading.value = true;

  try {
    const response = await getOrganizationApiByLoginUser();
    const organizationList = unwrapApiList<Organization>(response).filter((item) => !!item);

    if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
      organizations.value = transformOrganizationData(organizationList);
      nextTick(() => {
        showOrganizationList.value = true;
      });
    } else {
      organizations.value = [];
      showOrganizationList.value = true;
    }
  } catch (error) {
    console.error('Failed to get organization list:', error);
    showOrganizationList.value = true;
    await proceedToSystem();
  } finally {
    organizationLoading.value = false;
  }
}

function handleBackFromOrganizationList() {
  showOrganizationList.value = false;
  selectedOrganizationId.value = undefined;
  authStore.setSelectedOrganization('');
  if (tenants.value.length > 1) {
    showTenantList.value = true;
  } else {
    loginSuccess.value = false;
    showTenantList.value = false;
  }
}

function handleBackFromTenantList() {
  showTenantList.value = false;
  selectedTenantId.value = undefined;
  authStore.setSelectedTenant('');
  loginSuccess.value = false;
}

async function handleOrganizationChange(organization: any) {
  selectedOrganizationId.value = organization.id;
  authStore.setSelectedOrganization(organization.id);

  await proceedToSystem();
}

async function proceedToSystem() {
  try {
    await loginByOrgApi({
      organizationUnitId: selectedOrganizationId.value as string,
    });

    const menus = await getMenuTreeApi();
    const menuData = menus || [];

    const convertedMenus = convertMenuData(menuData);

    accessStore.setIsAccessChecked(false);

    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: [],
      router,
      routes: accessRoutes,
      menuData: convertedMenus,
    });

    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    await nextTick();
    const redirectParam = router.currentRoute.value.query?.redirect as string | undefined;
    const targetPath = resolvePostLoginPath(redirectParam);

    await router.push(targetPath);
  } catch (error) {
    console.error('Failed to proceed to system:', error);
    await router.push(DEFAULT_HOME_PATH);
  }
}

onMounted(async () => {
  if (accessStore.accessToken) {
    const selectedTenant = authStore.getSelectedTenant();
    const selectedOrganization = authStore.getSelectedOrganization();
    if (selectedTenant && selectedOrganization) {
      return;
    }

    const storedTenants = authStore.getTenantList();
    if (storedTenants.length > 0) {
      tenants.value = transformTenantData(storedTenants);
      loginSuccess.value = true;

      if (selectedTenant && !selectedOrganization) {
        selectedTenantId.value = selectedTenant;
        try {
          organizationLoading.value = true;
          showOrganizationList.value = true;
          const response = await getOrganizationApiByLoginUser();
          const organizationList = unwrapApiList<Organization>(response);

          if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
            organizations.value = transformOrganizationData(organizationList);
            const storedOrgId = authStore.getSelectedOrganization();
            if (storedOrgId) {
              selectedOrganizationId.value = storedOrgId;
            }
          }
        } catch (error) {
          console.error('Failed to load organization list:', error);
        } finally {
          organizationLoading.value = false;
        }
      } else if (!selectedTenant) {
        if (storedTenants.length === 1) {
          const singleTenant = tenants.value[0];
          await handleTenantSelect(singleTenant);
        } else {
          showTenantList.value = true;
        }
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
      defaultValue: 'admin',
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      defaultValue: 'Admin@123456',
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
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
      <div class="mt-4 flex justify-center">
        <router-link
          class="text-primary hover:text-primary-hover cursor-pointer text-sm"
          to="/auth/caLog"
        >
          CA证书登录
        </router-link>
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

    <div v-else-if="showOrganizationList" class="mx-auto h-full w-full sm:max-w-3xl">
      <AuthenticationOrganizationList
        v-model="selectedOrganizationId"
        :loading="organizationLoading"
        :organizations="organizations"
        :visible="showOrganizationList"
        @back="handleBackFromOrganizationList"
        @change="handleOrganizationChange"
      />
    </div>
  </Transition>
</template>
