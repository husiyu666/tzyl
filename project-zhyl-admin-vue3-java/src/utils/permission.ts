import { watch } from 'vue'
import { useUserStore } from '@/store'

// 记录所有带 v-hasRole 的元素及其声明的角色名。
// 用 Map 而不是直接操作 DOM，是为了在权限异步到达后能统一重新判定。
const registered = new Map<HTMLElement, string[]>()
let stopWatch: (() => void) | null = null

const grantedRoles = (): string[] =>
  useUserStore().routeNewButton.map((item: any) => item?.name)

// 声明了任意一个角色就放行
const isAllowed = (required: string[], granted: string[]) =>
  required.some((name) => granted.indexOf(name) !== -1)

// 只切换样式，绝不 removeChild。
// 删掉 DOM 后 Vue 的 vnode 仍然存在，下次该组件重渲染时
// 会去 patch/remove 一个已脱离文档的节点，抛 NotFoundError，
// 进而中断整个组件的更新。
const apply = (
  el: HTMLElement,
  required: string[],
  granted: string[] = grantedRoles()
) => {
  el.style.display = isAllowed(required, granted) ? '' : 'none'
}

const applyAll = () => {
  const granted = grantedRoles()
  registered.forEach((required, el) => apply(el, required, granted))
}

// 用来控制权限配置的按钮显示
export const hasRole = {
  install: (app: any) => {
    app.directive('hasRole', {
      mounted(el: HTMLElement, binding: any) {
        const required: string[] = Array.isArray(binding.value)
          ? binding.value
          : []
        registered.set(el, required)
        apply(el, required)
        // 按钮权限是登录后才异步写入 store 的。
        // 若组件先于权限加载挂载，routeNewButton 还是空数组，
        // 这里必须等数据到位后重新判定，否则按钮会一直保持隐藏。
        if (!stopWatch) {
          stopWatch = watch(
            () => useUserStore().routeNewButton,
            applyAll,
            { deep: true }
          )
        }
      },
      updated(el: HTMLElement, binding: any) {
        const required: string[] = Array.isArray(binding.value)
          ? binding.value
          : []
        registered.set(el, required)
        apply(el, required)
      },
      unmounted(el: HTMLElement) {
        registered.delete(el)
        if (registered.size === 0 && stopWatch) {
          stopWatch()
          stopWatch = null
        }
      }
    })
  }
}
