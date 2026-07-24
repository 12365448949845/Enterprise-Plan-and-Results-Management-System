<template>
  <section class="page-panel employee-day-plan-edit">
    <EmployeeDayPlanEditor :date="routeDate" @date-change="replaceRouteDate" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmployeeDayPlanEditor from './components/EmployeeDayPlanEditor.vue'

const route = useRoute()
const router = useRouter()
const routeDate = computed(() => {
  const rawDate = Array.isArray(route.query.date) ? route.query.date[0] : route.query.date
  return typeof rawDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(rawDate) ? rawDate : formatDate(new Date())
})

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function replaceRouteDate(date: string) {
  if (routeDate.value === date && route.query.date === date) return
  void router.replace({ path: route.path, query: { ...route.query, date } })
}
</script>
