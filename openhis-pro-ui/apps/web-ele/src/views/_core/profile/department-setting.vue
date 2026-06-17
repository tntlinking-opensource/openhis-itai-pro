<script setup lang="ts">
import type { BasicOption } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';
import type { Organization } from '#/api';

import { computed, onMounted, ref } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { getAvailableOrganizationUnitsApi } from '#/api';
import { useAuthStore } from '#/store';
import { switchOrganizationUnitSession } from '#/utils/switch-organization-unit-session';

const authStore = useAuthStore();

const departmentOptions = ref<BasicOption[]>([]);
const loading = ref(false);

/** 转换科室列表为下拉选项 */
function transformOrganizationData(apiData: Organization[]): BasicOption[] {
  return apiData.map((item) => ({
    label: item.displayName || item.name || '',
    value: String(item.id),
  }));
}

/** 拉取当前用户可选科室（关联科室 ∩ 岗位管辖） */
async function fetchDepartmentList() {
  try {
    loading.value = true;
    const response = await getAvailableOrganizationUnitsApi();
    const organizationList =
      (response as unknown as { data?: Organization[] }).data || (response as Organization[]);

    if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
      departmentOptions.value = transformOrganizationData(organizationList);
    } else {
      departmentOptions.value = [];
    }
  } catch (error) {
    console.error('Failed to get department list:', error);
    ElMessage.error('获取科室列表失败');
  } finally {
    loading.value = false;
  }
}

const formSchema = computed((): VbenFormSchema[] => {
  const currentOrgId = authStore.getSelectedOrganization();
  return [
    {
      fieldName: 'department',
      component: 'Select',
      label: '科室',
      defaultValue: currentOrgId || undefined,
      componentProps: {
        filterable: true,
        placeholder: '请选择或搜索科室',
        options: departmentOptions.value,
        loading: loading.value,
      },
    },
  ];
});

/** 提交科室切换并同步会话 */
async function handleSubmit(values: { department?: string }) {
  const orgId = values.department ? String(values.department) : '';
  if (!orgId) {
    ElMessage.warning('请选择科室');
    return;
  }

  const selectedDepartment = departmentOptions.value.find((item) => item.value === orgId);
  try {
    await switchOrganizationUnitSession(orgId);
    ElMessage.success(`科室设置成功：${selectedDepartment?.label || orgId}`);
    window.location.reload();
  } catch (error: unknown) {
    const err = error as { message?: string; response?: { data?: { message?: string } } };
    const message =
      err?.response?.data?.message ||
      err?.message ||
      '切换科室失败，请确认该科室在您的岗位管辖范围内';
    ElMessage.error(message);
  }
}

onMounted(() => {
  fetchDepartmentList();
});
</script>
<template>
  <ProfileBaseSetting class="w-1/3" :form-schema="formSchema" @submit="handleSubmit" />
</template>
