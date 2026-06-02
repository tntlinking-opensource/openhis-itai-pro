<script setup lang="ts">
import type { BasicOption } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';
import type { Organization } from '#/api';

import { computed, onMounted, ref } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { getOrganizationApiByLoginUser } from '#/api';

// 科室选项数据
const departmentOptions = ref<BasicOption[]>([]);
const loading = ref(false);

// 转换科室数据格式
function transformOrganizationData(apiData: Organization[]): BasicOption[] {
  return apiData.map((item) => ({
    label: item.displayName || item.name || '',
    value: String(item.id),
  }));
}

// 获取科室列表
async function fetchDepartmentList() {
  try {
    loading.value = true;
    const response = await getOrganizationApiByLoginUser({});
    const organizationList =
      (response as unknown as { data?: Organization[] }).data || (response as Organization[]);

    if (organizationList && Array.isArray(organizationList) && organizationList.length > 0) {
      departmentOptions.value = transformOrganizationData(organizationList);
    }
  } catch (error) {
    console.error('Failed to get department list:', error);
    ElMessage.error('获取科室列表失败');
  } finally {
    loading.value = false;
  }
}

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'department',
      component: 'Select',
      label: '科室',
      componentProps: {
        filterable: true,
        placeholder: '请选择或搜索科室',
        options: departmentOptions.value,
        loading: loading.value,
      },
    },
  ];
});

function handleSubmit(values: any) {
  const selectedDepartment = departmentOptions.value.find(
    (item) => item.value === values.department,
  );
  ElMessage.success(`科室设置成功：${selectedDepartment?.label || values.department}`);
}

onMounted(() => {
  fetchDepartmentList();
});
</script>
<template>
  <ProfileBaseSetting class="w-1/3" :form-schema="formSchema" @submit="handleSubmit" />
</template>
