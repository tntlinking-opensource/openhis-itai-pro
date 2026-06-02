<script setup lang="ts">
import { computed, ref } from 'vue';

import { ArrowLeft } from '@vben/icons';
import { $t } from '@vben/locales';

import { Card, Input, VbenButton } from '@vben-core/shadcn-ui';

interface Organization {
  id: number | string;
  name: string;
  code?: string;
  description?: string;
  logo?: string;
}

interface Props {
  /** 科室列表数据 */
  organizations?: Organization[];
  /** 当前选中的科室ID */
  modelValue?: number | string;
  /** 是否显示 */
  visible?: boolean;
  /** 是否加载中 */
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  organizations: () => [],
  modelValue: undefined,
  visible: false,
  loading: false,
});

const emit = defineEmits<{
  back: [];
  change: [organization: Organization];
  'update:modelValue': [value: number | string];
}>();

const selectedOrganizationId = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (value === props.modelValue) {
      return; // 避免重复触发
    }
    emit('update:modelValue', value!);
  },
});

const searchKeyword = ref('');

const filteredOrganizations = computed(() => {
  if (!searchKeyword.value.trim()) {
    return props.organizations;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return props.organizations.filter(
    (organization) =>
      organization.name.toLowerCase().includes(keyword) ||
      organization.code?.toLowerCase().includes(keyword) ||
      organization.description?.toLowerCase().includes(keyword),
  );
});

function handleSelectOrganization(organization: Organization) {
  if (selectedOrganizationId.value === organization.id) {
    return; // 避免重复选择
  }
  selectedOrganizationId.value = organization.id;
  emit('change', organization);
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden py-6">
    <div class="mb-6 w-full flex-shrink-0">
      <div class="mb-4 flex items-center gap-2">
        <VbenButton class="flex-shrink-0" size="sm" variant="ghost" @click="emit('back')">
          <ArrowLeft class="mr-1 size-4" />
          {{ '返回' }}
        </VbenButton>
      </div>
      <h3 class="text-foreground text-start text-xl font-semibold">
        {{ '科室' }}
      </h3>
      <p class="text-muted-foreground mt-1 text-start text-sm">
        {{ '请选择您要登录的科室' }}
      </p>
    </div>

    <div class="mb-4 w-full flex-shrink-0">
      <Input
        v-model="searchKeyword"
        :placeholder="$t('common.search') || '搜索科室名称、代码...'"
        class="w-full"
      />
    </div>

    <div v-if="loading" class="flex-center flex flex-1 items-center justify-center">
      <div class="text-muted-foreground text-sm">
        {{ $t('common.loading') || '加载中...' }}
      </div>
    </div>

    <div
      v-else-if="filteredOrganizations.length === 0"
      class="flex-center flex flex-1 items-center justify-center"
    >
      <div class="text-muted-foreground text-center text-sm">
        {{ searchKeyword ? '未找到匹配的科室' : '暂无科室' }}
      </div>
    </div>

    <div v-else class="min-h-0 w-full flex-1 space-y-4 overflow-y-auto">
      <Card
        v-for="organization in filteredOrganizations"
        :key="organization.id"
        :class="{
          'border-primary bg-primary/5': selectedOrganizationId === organization.id,
        }"
        class="hover:border-primary/50 cursor-pointer p-5 transition-all"
        @click="handleSelectOrganization(organization)"
      >
        <div class="flex items-start gap-4">
          <div v-if="organization.logo" class="flex-shrink-0">
            <img
              :alt="organization.name"
              :src="organization.logo"
              class="size-16 rounded-lg object-cover"
            />
          </div>
          <div v-else class="bg-primary/10 flex-center flex size-16 flex-shrink-0 rounded-lg">
            <span class="text-primary text-2xl font-semibold">
              {{ organization.name?.charAt(0)?.toUpperCase() || 'O' }}
            </span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-foreground text-lg font-semibold">
              {{ organization.name }}
            </div>
            <div v-if="organization.code" class="text-muted-foreground mt-1 text-sm">
              代码：{{ organization.code }}
            </div>
            <div
              v-if="organization.description"
              class="text-muted-foreground mt-2 line-clamp-3 text-sm"
            >
              {{ organization.description }}
            </div>
          </div>
          <div v-if="selectedOrganizationId === organization.id" class="flex-shrink-0">
            <div class="bg-primary size-3 rounded-full"></div>
          </div>
        </div>
      </Card>
    </div>
  </div>
</template>
