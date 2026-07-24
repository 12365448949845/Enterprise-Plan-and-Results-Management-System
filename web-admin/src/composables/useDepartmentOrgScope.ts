import { computed, ref } from 'vue'
import { getDepartmentOrgTreeApi } from '@/api/department'
import type { OrgNode } from '@/api/performance'

interface DepartmentOrgOption {
  value: number
  label: string
}

function flattenOrgTree(nodes: OrgNode[], parents: string[] = []): DepartmentOrgOption[] {
  return nodes.flatMap((node) => {
    const path = [...parents, node.label]
    return [
      { value: node.id, label: path.join(' / ') },
      ...flattenOrgTree(node.children || [], path),
    ]
  })
}

export function useDepartmentOrgScope(selectFirst = true) {
  const orgTree = ref<OrgNode[]>([])
  const selectedOrgId = ref<number>()
  const orgLoading = ref(false)
  const orgOptions = computed(() => flattenOrgTree(orgTree.value))

  async function loadOrgScope() {
    orgLoading.value = true
    try {
      orgTree.value = await getDepartmentOrgTreeApi()
      if (!orgOptions.value.some((item) => item.value === selectedOrgId.value)) {
        selectedOrgId.value = selectFirst ? orgOptions.value[0]?.value : undefined
      }
      return selectedOrgId.value
    } finally {
      orgLoading.value = false
    }
  }

  function resetOrgScope() {
    selectedOrgId.value = selectFirst ? orgOptions.value[0]?.value : undefined
  }

  return { orgTree, orgOptions, selectedOrgId, orgLoading, loadOrgScope, resetOrgScope }
}
