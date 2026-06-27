<script setup lang="ts">
import { computed, ref } from 'vue';

import { ArrowLeft } from '@vben/icons';
import { $t } from '@vben/locales';

import { Input, VbenButton } from '@vben-core/shadcn-ui';

interface Hospital {
  id: number | string;
  name: string;
  code?: string;
  description?: string;
  logo?: string;
}

interface Props {
  hospitals?: Hospital[];
  modelValue?: number | string;
  visible?: boolean;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  hospitals: () => [],
  modelValue: undefined,
  visible: false,
  loading: false,
});

const emit = defineEmits<{
  back: [];
  change: [hospital: Hospital];
  'update:modelValue': [value: number | string];
}>();

const selectedHospitalId = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (value === props.modelValue) {
      return;
    }
    emit('update:modelValue', value!);
  },
});

const searchKeyword = ref('');

const filteredHospitals = computed(() => {
  if (!searchKeyword.value.trim()) {
    return props.hospitals;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return props.hospitals.filter(
    (hospital) =>
      hospital.name.toLowerCase().includes(keyword) ||
      hospital.code?.toLowerCase().includes(keyword) ||
      hospital.description?.toLowerCase().includes(keyword),
  );
});

function handleSelectHospital(hospital: Hospital) {
  if (selectedHospitalId.value === hospital.id) {
    return;
  }
  selectedHospitalId.value = hospital.id;
  emit('change', hospital);
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
        {{ '医院' }}
      </h3>
      <p class="text-muted-foreground mt-1 text-start text-sm">
        {{ '请选择您要登录的医院' }}
      </p>
    </div>

    <div class="mb-4 w-full flex-shrink-0">
      <Input
        v-model="searchKeyword"
        :placeholder="$t('common.search') || '搜索医院名称、代码...'"
        class="w-full"
      />
    </div>

    <div v-if="loading" class="flex-center flex flex-1 items-center justify-center">
      <div class="text-muted-foreground text-sm">加载中...</div>
    </div>

    <div
      v-else-if="filteredHospitals.length === 0"
      class="flex-center flex flex-1 items-center justify-center"
    >
      <div class="text-muted-foreground text-center text-sm">
        {{ searchKeyword ? '未找到匹配的医院' : '暂无医院' }}
      </div>
    </div>

    <div v-else class="min-h-0 w-full flex-1 space-y-4 overflow-y-auto">
      <div
        v-for="hospital in filteredHospitals"
        :key="hospital.id"
        role="button"
        tabindex="0"
        class="border-border bg-card text-card-foreground hover:border-primary/50 cursor-pointer rounded-xl border p-5 transition-all"
        :class="selectedHospitalId === hospital.id ? 'border-primary bg-primary/5' : ''"
        @click="handleSelectHospital(hospital)"
        @keydown.enter="handleSelectHospital(hospital)"
      >
        <div class="flex items-start gap-4">
          <div v-if="hospital.logo" class="flex-shrink-0">
            <img
              :alt="hospital.name"
              :src="hospital.logo"
              class="size-16 rounded-lg object-cover"
            />
          </div>
          <div v-else class="bg-primary/10 flex-center flex size-16 flex-shrink-0 rounded-lg">
            <span class="text-primary text-2xl font-semibold">
              {{ hospital.name?.charAt(0)?.toUpperCase() || 'H' }}
            </span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-foreground text-lg font-semibold">
              {{ hospital.name }}
            </div>
            <div v-if="hospital.code" class="text-muted-foreground mt-1 text-sm">
              代码：{{ hospital.code }}
            </div>
            <div
              v-if="hospital.description"
              class="text-muted-foreground mt-2 line-clamp-3 text-sm"
            >
              {{ hospital.description }}
            </div>
          </div>
          <div v-if="selectedHospitalId === hospital.id" class="flex-shrink-0">
            <div class="bg-primary size-3 rounded-full"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
