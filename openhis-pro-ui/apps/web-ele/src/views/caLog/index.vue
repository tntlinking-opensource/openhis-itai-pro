<script lang="ts" setup>
import type { AuthApi, Organization } from '#/api';

import { nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationOrganizationList, AuthenticationTenantList } from '@vben/common-ui';
import { useAccessStore, useUserStore } from '@vben/stores';

import { getMenuTreeApi, getOrganizationApiByLoginUser, loginByOrgApi } from '#/api';
import { DEFAULT_HOME_PATH, resolvePostLoginPath } from '#/constants/app';
import { generateAccess } from '#/router/access';
import { accessRoutes } from '#/router/routes';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { convertMenuData } from '#/utils/convert-menu-data';

import CaLoginForm from './components/CaLoginForm.vue';

defineOptions({ name: 'CaLogin' });

const authStore = useAuthStore();
const accessStore = useAccessStore();
const userStore = useUserStore();
const router = useRouter();

const loginLoading = ref(false);
const loginSuccess = ref(false);
const showTenantList = ref(false);
const showOrganizationList = ref(false);
const organizationLoading = ref(false);
const selectedTenantId = ref<number | string>();
const selectedOrganizationId = ref<number | string>();

const tenants = ref<
  Array<{ code?: string; description?: string; id: number; logo?: string; name: string }>
>([]);
const organizations = ref<
  Array<{ code?: string; description?: string; id: number | string; logo?: string; name: string }>
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

async function handleCaLoginSuccess({
  tenants: tenantsList,
  userInfo,
}: {
  tenants: any[];
  userInfo: any;
}) {
  if (tenantsList && tenantsList.length > 0) {
    tenants.value = transformTenantData(tenantsList);

    if (tenantsList.length === 1) {
      const singleTenant = tenants.value[0]!;
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
}

async function handleTenantSelect(tenant: any) {
  if (selectedTenantId.value === tenant.id && showOrganizationList.value) {
    return;
  }

  selectedTenantId.value = tenant.id;
  authStore.setSelectedTenant(tenant.id);
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
      // 无科室数据，跳过科室选择，直接进入系统
      organizations.value = [];
      await proceedToSystem();
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
    // 仅在选择了科室时调用loginByOrgApi，无科室则跳过
    if (selectedOrganizationId.value) {
      await loginByOrgApi({
        organizationUnitId: selectedOrganizationId.value as string,
      });
    } else {
      // 无科室时设置占位值，避免路由守卫拦截
      authStore.setSelectedOrganization('none');
    }

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
    await router.push(resolvePostLoginPath(redirectParam));
  } catch (error) {
    console.error('Failed to proceed to system:', error);
    await router.push(DEFAULT_HOME_PATH);
  }
}

// 如果已有token但未选租户/科室，恢复状态
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
          const organizationList =
            (response as unknown as { data?: Organization[] }).data || (response as Organization[]);

          if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
            organizations.value = transformOrganizationData(organizationList);
          }
        } catch (error) {
          console.error('Failed to get organization list:', error);
        } finally {
          organizationLoading.value = false;
        }
      } else if (!selectedTenant) {
        showTenantList.value = true;
      }
    }
  }
});
</script>

<template>
  <div class="mx-auto flex h-full w-full flex-col justify-center sm:max-w-md">
    <!-- 租户选择 -->
    <template v-if="showTenantList && loginSuccess">
      <div class="mb-8 text-center">
        <h2 class="mb-2 text-2xl font-bold">选择租户</h2>
        <p class="text-muted-foreground">请选择要登录的租户</p>
      </div>
      <AuthenticationTenantList
        v-model="selectedTenantId"
        :loading="false"
        :tenants="tenants"
        :visible="showTenantList"
        @change="handleTenantSelect"
        @back="handleBackFromTenantList"
      />
    </template>

    <!-- 科室选择 -->
    <template v-else-if="showOrganizationList && loginSuccess">
      <div class="mb-8 text-center">
        <h2 class="mb-2 text-2xl font-bold">选择科室</h2>
        <p class="text-muted-foreground">请选择要进入的科室</p>
      </div>
      <AuthenticationOrganizationList
        v-model="selectedOrganizationId"
        :loading="organizationLoading"
        :organizations="organizations"
        :visible="showOrganizationList"
        @change="handleOrganizationChange"
        @back="handleBackFromOrganizationList"
      />
    </template>

    <!-- CA登录表单 -->
    <template v-else>
      <div class="mb-8 text-center">
        <h2 class="mb-2 text-2xl font-bold">CA证书登录</h2>
        <p class="text-muted-foreground">使用数字证书安全登录系统</p>
      </div>

      <CaLoginForm
        :loading="loginLoading"
        @loading-change="(val: boolean) => (loginLoading = val)"
        @login-success="handleCaLoginSuccess"
      />

      <div class="mt-4 text-center">
        <router-link
          class="text-primary hover:text-primary-hover cursor-pointer text-sm"
          to="/auth/login"
        >
          返回账号密码登录
        </router-link>
      </div>
    </template>
  </div>
</template>
