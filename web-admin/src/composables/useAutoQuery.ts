import { onBeforeUnmount, ref, watch, type WatchSource } from 'vue'

interface UseAutoQueryOptions {
  debounce?: number
  enabled?: () => boolean
}

type AutoQuerySource = WatchSource<unknown> | WatchSource<unknown>[]

export function useAutoQuery(
  source: AutoQuerySource,
  query: () => unknown | Promise<unknown>,
  options: UseAutoQueryOptions = {},
) {
  const active = ref(false)
  const debounce = options.debounce ?? 350
  let timer: ReturnType<typeof window.setTimeout> | undefined

  function clearTimer() {
    if (timer == null) return
    window.clearTimeout(timer)
    timer = undefined
  }

  function canRun() {
    return active.value && options.enabled?.() !== false
  }

  function schedule() {
    if (!canRun()) return
    clearTimer()
    timer = window.setTimeout(() => {
      timer = undefined
      if (canRun()) void query()
    }, debounce)
  }

  const stop = watch(source, schedule)

  function pause() {
    active.value = false
    clearTimer()
  }

  function resume() {
    active.value = true
  }

  async function runNow() {
    clearTimer()
    if (options.enabled?.() === false) return
    await query()
  }

  onBeforeUnmount(() => {
    clearTimer()
    stop()
  })

  return { active, pause, resume, runNow }
}
