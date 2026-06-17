<script setup lang="ts">
import type { BookingOptionDto } from '#/api/booking/booking-types';
import type { ScheduleActorDto, ScheduleActorRequest } from '#/api/booking/outpatient-schedule';

import { computed, reactive, watch } from 'vue';

import { BOOKING_ACTOR_TYPE } from '#/api/booking/booking-constants';
import { createScheduleApi, updateScheduleDraftApi } from '#/api/booking/outpatient-schedule';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  organizationId?: number;
  practitioners?: BookingOptionDto[];
  clinicLocations?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
  scheduleId?: number;
  initialName?: string;
  initialActors?: ScheduleActorDto[];
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  saved: [];
}>();

const form = reactive({
  name: '',
  practitionerId: undefined as number | undefined,
  locationId: undefined as number | undefined,
  healthcareServiceId: undefined as number | undefined,
});

const isEdit = computed(() => !!props.scheduleId);

function fillFormFromActors(actors?: ScheduleActorDto[]) {
  form.practitionerId = undefined;
  form.locationId = undefined;
  form.healthcareServiceId = undefined;
  if (!actors?.length) return;
  for (const actor of actors) {
    if (actor.actorType === BOOKING_ACTOR_TYPE.PRACTITIONER) {
      form.practitionerId = actor.actorId;
    } else if (actor.actorType === BOOKING_ACTOR_TYPE.LOCATION) {
      form.locationId = actor.actorId;
    } else if (actor.actorType === BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE) {
      form.healthcareServiceId = actor.actorId;
    }
  }
}

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    form.name = props.initialName ?? '';
    if (isEdit.value) {
      fillFormFromActors(props.initialActors);
    } else {
      form.practitionerId = undefined;
      form.locationId = undefined;
      form.healthcareServiceId = undefined;
    }
  },
);

function buildActors(): ScheduleActorRequest[] {
  const actors: ScheduleActorRequest[] = [];
  if (form.practitionerId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.PRACTITIONER,
      actorId: form.practitionerId,
      sortNo: 1,
    });
  }
  if (form.locationId) {
    actors.push({ actorType: BOOKING_ACTOR_TYPE.LOCATION, actorId: form.locationId, sortNo: 2 });
  }
  if (form.healthcareServiceId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE,
      actorId: form.healthcareServiceId,
      sortNo: 3,
    });
  }
  return actors;
}

const { run: runSave, loading } = useRequest(
  async () => {
    if (!props.organizationId) {
      throw new Error('请先选择登录科室');
    }
    const actors = buildActors();
    if (!actors.some((a) => a.actorType === BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE)) {
      throw new Error('请选择号源类型');
    }
    if (isEdit.value && props.scheduleId) {
      await updateScheduleDraftApi({
        scheduleId: props.scheduleId,
        actors,
      });
      return;
    }
    await createScheduleApi({
      organizationId: props.organizationId,
      name: form.name.trim() || undefined,
      actors,
    });
  },
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success(isEdit.value ? '日程已更新' : '草稿日程已创建');
      emit('saved');
      emit('update:visible', false);
    },
  },
);

async function handleSubmit() {
  try {
    await runSave();
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败');
  }
}
</script>

<template>
  <ElDrawer
    :model-value="visible"
    :title="isEdit ? '编辑草稿日程' : '新建日程'"
    size="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm label-width="88px">
      <ElFormItem label="名称">
        <ElInput v-model="form.name" placeholder="如：内科上午" />
      </ElFormItem>
      <ElFormItem label="医师">
        <ElSelect
          v-model="form.practitionerId"
          clearable
          filterable
          placeholder="选择医师"
          style="width: 100%"
        >
          <ElOption
            v-for="item in practitioners || []"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="诊室">
        <ElSelect
          v-model="form.locationId"
          clearable
          filterable
          placeholder="可选，到院分诊时再定"
          style="width: 100%"
        >
          <ElOption
            v-for="item in clinicLocations || []"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="号源类型" required>
        <ElSelect
          v-model="form.healthcareServiceId"
          clearable
          filterable
          placeholder="必选，如专家号/普通号"
          style="width: 100%"
        >
          <ElOption
            v-for="item in healthcareServices || []"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="handleSubmit">保存</ElButton>
    </template>
  </ElDrawer>
</template>
