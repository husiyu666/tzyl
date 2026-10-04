import axios, {
  AxiosRequestConfig,
  AxiosInstance,
  AxiosResponse,
  AxiosError
} from 'axios'
import { stringify } from 'qs'
import isFunction from 'lodash/isFunction'
import cloneDeep from 'lodash/cloneDeep'
import { toRaw } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
// import { useRouter } from 'vue-router'
import router from '../../router'
import { useSettingStore, useUserStore } from '@/store'
import { CreateAxiosOptions } from './AxiosTransform'
import { AxiosCanceler } from './AxiosCancel'
import { AxiosRequestConfigRetry, RequestOptions, Result } from '@/types/axios'
import { getUrlParams, uuid } from '@/utils/index'
// token 实际存放在 pinia 持久化的 user store 下（store id 即 key），
// 不是 global.ts 里的 TOKEN_NAME('czri')——后者从未被写入过
const USER_STORE_KEY = 'user'
// 清空登录态：本地持久化 + pinia 内存态，缺一不可（路由守卫读的是内存态 token）
const clearLoginInfo = () => {
  try {
    localStorage.removeItem(USER_STORE_KEY)
    useUserStore().logout()
  } catch (e) {
    // pinia 尚未初始化时忽略
  }
}
// Axios模块
export class VAxios {
  // axios句柄
  private instance: AxiosInstance

  // axios选项
  private readonly options: CreateAxiosOptions

  constructor(options: CreateAxiosOptions) {
    this.options = options
    this.instance = axios.create(options)
    this.setupInterceptors()
  }

  // 创建axios句柄
  private createAxios(config: CreateAxiosOptions): void {
    this.instance = axios.create(config)
  }

  // 获取数据处理
  private getTransform() {
    const { transform } = this.options
    return transform
  }

  // 获取句柄
  getAxios(): AxiosInstance {
    return this.instance
  }

  // 配置 axios
  configAxios(config: CreateAxiosOptions) {
    if (!this.instance) {
      return
    }
    this.createAxios(config)
  }

  // 设置通用头信息
  setHeader(headers: Record<string, string>): void {
    if (!this.instance) {
      return
    }
    Object.assign(this.instance.defaults.headers, headers)
  }

  // 设置拦截器
  private setupInterceptors() {
    const transform = this.getTransform()
    if (!transform) {
      return
    }
    const {
      requestInterceptors,
      requestInterceptorsCatch,
      responseInterceptors,
      responseInterceptorsCatch
    } = transform
    const axiosCanceler = new AxiosCanceler()

    // 请求配置处理
    this.instance.interceptors.request.use((config: any) => {
      // const { headers: any } = config
      // config.headers['Access-Control-Allow-Origin'] = '*'
      // const ignoreRepeat = ignoreRepeatRequest ?? this.options.requestOptions?.ignoreRepeatRequest;
      const ignoreRepeat = ''
      if (!ignoreRepeat) axiosCanceler.addPending(config)

      if (requestInterceptors && isFunction(requestInterceptors)) {
        config = requestInterceptors(config, this.options)
      }

      // get请求参数处理, 解决空格转化为+的Bug
      if (config.method === 'get') {
        let params = '?'
        for (const key in config.params) {
          params += `${key}=${config.params[key]}&`
        }
        params = params.slice(0, -1)
        config.url += params
        config.params = ''
      }
      return config
    }, undefined)

    // 请求错误处理
    if (requestInterceptorsCatch && isFunction(requestInterceptorsCatch)) {
      this.instance.interceptors.request.use(
        undefined,
        requestInterceptorsCatch
      )
    }

    // 响应结果处理
    this.instance.interceptors.response.use((res: AxiosResponse) => {
      if (res) axiosCanceler.removePending(res.config)
      if (responseInterceptors && isFunction(responseInterceptors)) {
        res = responseInterceptors(res)
      }
      return res
    }, undefined)

    // 响应错误处理
    if (responseInterceptorsCatch && isFunction(responseInterceptorsCatch)) {
      this.instance.interceptors.response.use(
        undefined,
        responseInterceptorsCatch
      )
    }
  }

  // 支持Form Data
  supportFormData(config: AxiosRequestConfig) {
    const headers = config.headers || this.options.headers
    const contentType = headers?.['Content-Type'] || headers?.['content-type']

    if (
      contentType !== 'application/x-www-form-urlencoded;charset=UTF-8' ||
      !Reflect.has(config, 'data') ||
      config.method?.toUpperCase() === 'GET'
    ) {
      return config
    }

    return {
      ...config,
      data: stringify(config.data, { arrayFormat: 'brackets' })
    }
  }

  get<T = any>(
    config: AxiosRequestConfig,
    options?: RequestOptions
  ): Promise<T> {
    return this.request({ ...config, method: 'GET' }, options)
  }

  post<T = any>(
    config: AxiosRequestConfig,
    options?: RequestOptions
  ): Promise<T> {
    return this.request({ ...config, method: 'POST' }, options)
  }

  put<T = any>(
    config: AxiosRequestConfig,
    options?: RequestOptions
  ): Promise<T> {
    return this.request({ ...config, method: 'PUT' }, options)
  }

  delete<T = any>(
    config: AxiosRequestConfig,
    options?: RequestOptions
  ): Promise<T> {
    return this.request({ ...config, method: 'DELETE' }, options)
  }

  patch<T = any>(
    config: AxiosRequestConfig,
    options?: RequestOptions
  ): Promise<T> {
    return this.request({ ...config, method: 'PATCH' }, options)
  }

  // 请求
  async request<T = any>(
    config: AxiosRequestConfigRetry,
    options?: RequestOptions
  ): Promise<T> {
    // 放在最上面初始化会报错Cannot access 'useSettingStore' before initialization
    const settingStore = useSettingStore()
    const url = this.options.requestOptions.urlPrefix
      ? window.location.origin +
        this.options.requestOptions.urlPrefix +
        config.url
      : window.location.origin + config.url
    // console.log(this.options, config, config.url.indexOf('?'), '----')
    // 组装所需接口数据
    settingStore.addRequestList({
      uuid: uuid(8, 16),
      url,
      type: config.method,
      headers: this.options.headers,
      body: config.data ? { ...toRaw(config.data) } : {},
      params: getUrlParams(url),
      label:
        config.url.indexOf('?') !== -1 ? config.url.split('?')[0] : config.url
    })
    // 处理掉参数中出现undefined的选项
    config.params = config.params
      ? JSON.parse(JSON.stringify(config.params))
      : config.params
    let conf: CreateAxiosOptions = cloneDeep(config)
    const transform = this.getTransform()

    const { requestOptions } = this.options

    const opt: RequestOptions = { ...requestOptions, ...options }

    const { beforeRequestHook, requestCatchHook, transformRequestHook } =
      transform || {}
    if (beforeRequestHook && isFunction(beforeRequestHook)) {
      conf = beforeRequestHook(conf, opt)
    }
    conf.requestOptions = opt

    conf = this.supportFormData(conf)
    return new Promise((resolve, reject) => {
      this.instance
        .request<any, AxiosResponse<Result>>(!config.retryCount ? conf : config)
        .then((res: AxiosResponse<Result>) => {
          if (transformRequestHook && isFunction(transformRequestHook)) {
            // 将对应接口的返回信息拼装到接口队列中
            settingStore.requestList.forEach((item, index) => {
              if (
                res?.config?.method &&
                item.type.toLowerCase() === res.config.method &&
                res.request?.responseURL?.includes(item.url)
              ) {
                settingStore.addRequestListRes(index, res.data)
              }
            })
            try {
              const data: any = res.data
              // 返回体不符合约定的 {code,msg} 结构（如代理/网关返回 HTML 或空串）
              if (!data || typeof data !== 'object' || data.code === undefined) {
                reject({
                  isNetworkError: true,
                  code: res.status,
                  msg: '服务器返回异常，请确认后端服务是否正常'
                })
                return
              }
              // 后端 GlobalExceptionHandler.handleBaseException 返回的是
              // HTTP 200 + {code,msg}，业务失败走这里而非 .catch。
              // 在此统一提示一次，页面不要再重复弹
              if (data.code !== 200) {
                MessagePlugin.error(data.msg || '请求失败')
              }
              resolve(data)
            } catch (err) {
              reject(err || new Error('请求错误!'))
            }
            return
          }

          resolve(res as unknown as Promise<T>)
        })
        .catch((e: any) => {
          // 主动取消（重复请求）不提示，否则快速连点会弹出一串重复提示
          if (axios.isCancel(e)) {
            reject(e)
            return
          }
          const status = e?.response?.status
          // 后端 JwtAuthorizationManager 拒绝时抛 AccessDeniedException，
          // 被 GlobalExceptionHandler 统一转成 401（token 失效 / 被顶号 / 权限不足）
          if (status === 401) {
            clearLoginInfo()
            MessagePlugin.error('登录已失效，请重新登录')
            router.push('/login')
            reject(e)
            return
          }
          if (status === 403) {
            // Spring Security 的默认 AccessDeniedHandler 返回 403 且响应体为空。
            // JwtAuthorizationManager 有多个拒绝原因（token 缺失/失效、被顶号、
            // resourceRequestPaths 为空、路径无权限），前端无从区分，
            // 因此这里不能谎称"在其他设备登录"，也不能清空登录态——
            // 清空会把任何一次 403 直接变成强制登出。
            MessagePlugin.error(`没有访问权限：${e?.url || e?.config?.url || '未知接口'}`)
            reject(e)
            return
          }
          if (requestCatchHook && isFunction(requestCatchHook)) {
            reject(requestCatchHook(e, opt))
            return
          }
          // 网络错误与后端业务错误在此统一提示一次，页面不要再重复弹
          MessagePlugin.error(e?.msg || '请求失败，请稍后重试')
          reject(e)
        })
    })
  }
}
