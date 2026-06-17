<script setup lang="ts">
import { computed, ref } from 'vue';

import { ArrowLeft } from '@vben/icons';
import { $t } from '@vben/locales';

import { Input, VbenButton } from '@vben-core/shadcn-ui';

interface Tenant {
  id: number | string;
  name: string;
  code?: string;
  description?: string;
  logo?: string;
}

interface Props {
  /** 租户列表数据 */
  tenants?: Tenant[];
  /** 当前选中的租户ID */
  modelValue?: number | string;
  /** 是否显示 */
  visible?: boolean;
  /** 是否加载中 */
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  tenants: () => [],
  modelValue: undefined,
  visible: false,
  loading: false,
});

const emit = defineEmits<{
  back: [];
  change: [tenant: Tenant];
  'update:modelValue': [value: number | string];
}>();

const selectedTenantId = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (value === props.modelValue) {
      return; // 避免重复触发
    }
    emit('update:modelValue', value!);
  },
});

const searchKeyword = ref('');

// 过滤后的租户列表
const filteredTenants = computed(() => {
  if (!searchKeyword.value.trim()) {
    return props.tenants;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return props.tenants.filter(
    (tenant) =>
      tenant.name.toLowerCase().includes(keyword) ||
      tenant.code?.toLowerCase().includes(keyword) ||
      tenant.description?.toLowerCase().includes(keyword),
  );
});

/** 勿命名为 sameId/cn 等短名，避免与 shadcn Card 内部 cn() 在部分打包产物中冲突 */
function tenantIdsEqual(a: number | string | undefined, b: number | string | undefined): boolean {
  if (a === undefined || b === undefined) {
    return false;
  }
  return String(a) === String(b);
}

function handleSelectTenant(tenant: Tenant) {
  if (tenantIdsEqual(selectedTenantId.value, tenant.id)) {
    return;
  }
  selectedTenantId.value = tenant.id;
  emit('change', tenant);
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-y-auto py-6">
    <div class="mb-6 w-full flex-shrink-0">
      <div class="mb-4 flex items-center gap-2">
        <VbenButton class="flex-shrink-0" size="sm" variant="ghost" @click="emit('back')">
          <ArrowLeft class="mr-1 size-4" />
          {{ '返回' }}
        </VbenButton>
      </div>
      <h3 class="text-foreground text-start text-xl font-semibold">
        {{ '租户' }}
      </h3>
      <p class="text-muted-foreground mt-1 text-start text-sm">
        {{ '请选择您要登录的租户' }}
      </p>
    </div>

    <div class="mb-4 w-full flex-shrink-0">
      <Input
        v-model="searchKeyword"
        :placeholder="$t('common.search') || '搜索租户名称、代码...'"
        class="w-full"
      />
    </div>

    <div v-if="loading" class="flex-center flex flex-1 items-center justify-center">
      <div class="text-muted-foreground text-sm">
        {{ $t('common.loading') || '加载中...' }}
      </div>
    </div>

    <div
      v-else-if="filteredTenants.length === 0"
      class="flex-center flex flex-1 items-center justify-center"
    >
      <div class="text-muted-foreground text-center text-sm">
        {{ searchKeyword ? '未找到匹配的租户' : $t('page.auth.noTenant') || '暂无租户' }}
      </div>
    </div>

    <div v-else class="min-h-0 w-full flex-1 space-y-4 overflow-y-auto">
      <div
        v-for="tenant in filteredTenants"
        :key="tenant.id"
        role="button"
        tabindex="0"
        class="border-border bg-card text-card-foreground hover:border-primary/50 cursor-pointer rounded-xl border p-5 transition-all"
        :class="tenantIdsEqual(selectedTenantId, tenant.id) ? 'border-primary bg-primary/5' : ''"
        @click="handleSelectTenant(tenant)"
        @keydown.enter="handleSelectTenant(tenant)"
      >
        <div class="flex items-start gap-4">
          <div v-if="tenant.logo" class="flex-shrink-0">
            <img :alt="tenant.name" :src="tenant.logo" class="size-16 rounded-lg object-cover" />
          </div>
          <div v-else class="bg-primary/10 flex-center flex size-16 flex-shrink-0 rounded-lg">
            <span class="text-primary text-2xl font-semibold">
              {{ tenant.name?.charAt(0)?.toUpperCase() || 'T' }}
            </span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-foreground text-lg font-semibold">
              {{ tenant.name }}
            </div>
            <div v-if="tenant.code" class="text-muted-foreground mt-1 text-sm">
              代码：{{ tenant.code }}
            </div>
            <div v-if="tenant.description" class="text-muted-foreground mt-2 line-clamp-3 text-sm">
              {{ tenant.description }}
            </div>
          </div>
          <div v-if="tenantIdsEqual(selectedTenantId, tenant.id)" class="flex-shrink-0">
            <div class="bg-primary size-3 rounded-full"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
