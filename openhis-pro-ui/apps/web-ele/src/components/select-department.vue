<script setup lang="ts">
import type { Organization } from '#/api';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { ElInput, ElMessage, ElMessageBox, ElPopover, ElTree } from 'element-plus';

import { getMenuTreeApi, getOrganizationApiByLoginUser, loginByOrgApi } from '#/api';
import { DEFAULT_HOME_PATH, resolvePostLoginPath } from '#/constants/app';
import { generateAccess } from '#/router/access';
import { accessRoutes } from '#/router/routes';
import { useAuthStore } from '#/store';
import { convertMenuData } from '#/utils/convert-menu-data';

defineOptions({
  name: 'DepartmentSelector',
});

const authStore = useAuthStore();
const accessStore = useAccessStore();
const router = useRouter();

interface OrganizationNode {
  id: number | string;
  displayName: string;
  code?: string;
  remark?: null | string;
  children?: OrganizationNode[];
}

const organizations = ref<OrganizationNode[]>([]);
const loading = ref(false);
const selectedOrganizationId = ref(authStore.getSelectedOrganization());
const popoverVisible = ref(false);
const searchKeyword = ref('');

// 递归查找组织
function findOrganizationInTree(
  nodes: OrganizationNode[],
  id: number | string,
): null | OrganizationNode {
  for (const node of nodes) {
    if (String(node.id) === String(id)) {
      return node;
    }
    if (node.children && node.children.length > 0) {
      const found = findOrganizationInTree(node.children, id);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

// 递归扁平化树形数据用于搜索
function flattenTree(nodes: OrganizationNode[]): OrganizationNode[] {
  const result: OrganizationNode[] = [];
  for (const node of nodes) {
    result.push(node);
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children));
    }
  }
  return result;
}

const currentOrgName = computed(() => {
  const org = findOrganizationInTree(organizations.value, selectedOrganizationId.value);
  return org?.displayName || '请选择科室';
});

const filteredOrganizations = computed(() => {
  if (!searchKeyword.value) {
    return organizations.value;
  }
  const keyword = searchKeyword.value.toLowerCase();
  const flatList = flattenTree(organizations.value);
  const matchedIds = new Set(
    flatList
      .filter((org) => org.displayName.toLowerCase().includes(keyword))
      .map((org) => String(org.id)),
  );

  // 递归过滤树，保留匹配的节点及其父节点
  function filterTree(nodes: OrganizationNode[]): OrganizationNode[] {
    const result: OrganizationNode[] = [];
    for (const node of nodes) {
      const hasMatch = matchedIds.has(String(node.id));
      const filteredChildren =
        node.children && node.children.length > 0 ? filterTree(node.children) : [];

      if (hasMatch || filteredChildren.length > 0) {
        result.push({
          ...node,
          children: filteredChildren.length > 0 ? filteredChildren : node.children,
        });
      }
    }
    return result;
  }

  return filterTree(organizations.value);
});

async function proceedToSystem() {
  try {
    await loginByOrgApi({
      organizationUnitId: selectedOrganizationId.value as string,
    });

    // 获取菜单数据
    const menus = await getMenuTreeApi();
    const menuData = menus || [];

    // 转换菜单数据格式
    const convertedMenus = convertMenuData(menuData);

    // 重置路由检查状态，确保路由守卫会重新生成路由
    accessStore.setIsAccessChecked(false);

    // 生成动态路由和菜单
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: [],
      router,
      routes: accessRoutes,
      menuData: convertedMenus,
    });

    // 保存菜单和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    // 等待路由注册完成
    await nextTick();

    const redirectParam = router.currentRoute.value.query?.redirect as string | undefined;
    await router.push(resolvePostLoginPath(redirectParam));
  } catch (error) {
    console.error('Failed to proceed to system:', error);
    await router.push(DEFAULT_HOME_PATH);
  }
}

async function fetchOrganizations() {
  try {
    loading.value = true;
    const response = await getOrganizationApiByLoginUser();
    const organizationList =
      (response as unknown as { data?: Organization[] }).data || (response as Organization[]);

    if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
      // 转换 Organization[] 格式为树形格式，保留 children
      const transformToTree = (items: Organization[]): OrganizationNode[] => {
        return items.map((item) => ({
          id: item.id,
          displayName: item.displayName || item.name || '',
          code: item.code,
          remark: item.remark,
          children:
            item.children && item.children.length > 0 ? transformToTree(item.children) : undefined,
        }));
      };
      organizations.value = transformToTree(organizationList);

      await nextTick();

      const currentSelected = authStore.getSelectedOrganization();
      if (currentSelected !== null && currentSelected !== undefined) {
        const matchedOrg = findOrganizationInTree(organizations.value, currentSelected);
        if (matchedOrg) {
          selectedOrganizationId.value = String(matchedOrg.id);
        } else if (organizations.value.length > 0) {
          const firstOrgId = organizations.value[0]?.id;
          if (firstOrgId) {
            const orgIdStr = String(firstOrgId);
            selectedOrganizationId.value = orgIdStr;
            authStore.setSelectedOrganization(orgIdStr);
          }
        }
      } else if (organizations.value.length > 0 && organizations.value[0]?.id) {
        const firstOrgId = organizations.value[0].id;
        const orgIdStr = String(firstOrgId);
        selectedOrganizationId.value = orgIdStr;
        authStore.setSelectedOrganization(orgIdStr);
      }
    }
  } catch (error) {
    console.error('Failed to get organization list:', error);
  } finally {
    loading.value = false;
  }
}

async function handleChange(value: string) {
  const oldValue = authStore.getSelectedOrganization()?.toString();

  if (oldValue === value) {
    return;
  }

  const targetOrg = findOrganizationInTree(organizations.value, value);
  const orgName = targetOrg?.displayName || '该科室';

  try {
    await ElMessageBox.confirm(`是否切换到${orgName}？`, '切换科室', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });

    selectedOrganizationId.value = value;
    authStore.setSelectedOrganization(value);
    popoverVisible.value = false;

    await proceedToSystem();

    ElMessage.success(`已切换到${orgName}`);
  } catch (error: any) {
    if (error === 'cancel' || error?.action === 'cancel') {
      selectedOrganizationId.value = oldValue;
      popoverVisible.value = false;
    } else {
      console.error('Failed to switch department:', error);
      selectedOrganizationId.value = oldValue;
      authStore.setSelectedOrganization(oldValue);
      popoverVisible.value = false;
    }
  }
}

watch(
  () => authStore.getSelectedOrganization(),
  (newValue) => {
    if (newValue !== selectedOrganizationId.value) {
      selectedOrganizationId.value = newValue;
    }
  },
);

onMounted(() => {
  fetchOrganizations();
});
</script>

<template>
  <ElPopover
    v-if="organizations.length > 0"
    v-model:visible="popoverVisible"
    placement="bottom-start"
    :width="200"
    trigger="click"
    @show="searchKeyword = ''"
  >
    <template #reference>
      <span
        class="inline-flex cursor-pointer select-none items-center text-sm text-inherit transition-colors duration-300 hover:text-blue-500"
        :class="{ 'opacity-60': loading }"
      >
        <span class="mr-[10px] inline-block h-4 w-px bg-current opacity-20"></span>
        {{ currentOrgName }}
        <span class="ml-[10px] inline-block h-4 w-px bg-current opacity-20"></span>
      </span>
    </template>
    <div class="flex max-h-[300px] flex-col">
      <ElInput v-model="searchKeyword" placeholder="搜索科室" clearable class="mb-2" />
      <div class="min-h-[100px] flex-1 overflow-y-auto">
        <div v-if="loading" class="py-5 text-center text-sm text-gray-500">加载中...</div>
        <div
          v-else-if="filteredOrganizations.length === 0"
          class="py-5 text-center text-sm text-gray-500"
        >
          暂无数据
        </div>
        <ElTree
          v-else
          :data="filteredOrganizations"
          :props="{
            children: 'children',
            label: 'displayName',
          }"
          :default-expand-all="false"
          :highlight-current="true"
          node-key="id"
          :current-node-key="selectedOrganizationId"
          class="organization-tree"
          @node-click="(data: OrganizationNode) => handleChange(String(data.id))"
        >
          <template #default="{ data }">
            <span
              class="flex items-center text-sm"
              :class="{
                'font-medium text-blue-600': String(data.id) === selectedOrganizationId,
              }"
            >
              {{ data.displayName }}
            </span>
          </template>
        </ElTree>
      </div>
    </div>
  </ElPopover>
</template>

<style scoped>
.organization-tree :deep(.el-tree-node__content) {
  height: 32px;
  padding: 0 8px;
  cursor: pointer;
}

.organization-tree :deep(.el-tree-node__content:hover) {
  background-color: rgb(243 244 246);
}

.organization-tree :deep(.el-tree-node__content.is-current) {
  background-color: rgb(239 246 255);
}
</style>
