<script setup lang="ts">
defineOptions({
  name: 'AuthenticationFormView',
});

defineProps<{
  dataSide?: 'bottom' | 'left' | 'right' | 'top';
}>();
</script>

<template>
  <div
    class="flex-col-start dark:bg-background-deep bg-background relative h-full overflow-y-auto px-6 py-10 lg:flex-initial lg:px-8"
  >
    <slot></slot>
    <RouterView v-slot="{ Component, route }">
      <Transition appear mode="out-in" name="slide-right">
        <KeepAlive :include="['Login']">
          <component
            :is="Component"
            :key="route.fullPath"
            class="side-content h-full w-full sm:mx-auto md:max-w-2xl"
            :data-side="dataSide"
          />
        </KeepAlive>
      </Transition>
    </RouterView>

    <!-- Footer Copyright -->

    <div
      class="text-muted-foreground absolute bottom-3 left-0 right-0 flex w-full justify-center text-center text-xs"
    >
      <slot name="copyright"> </slot>
    </div>
  </div>
</template>
