// axios配置  可自行根据项目进行更改，只需更改该文件即可，其他文件可以不动
import axios from 'axios'
import isString from 'lodash/isString'
import merge from 'lodash/merge'
import type { AxiosTransform, CreateAxiosOptions } from './AxiosTransform'
import { VAxios } from './Axios'
import proxy from '@/config/proxy'
import { joinTimestamp, formatRequestDate, setObjToUrlParams } from './utils'
import { useUserStore } from '@/store'

// 取 token：pinia 内存态优先，localStorage 持久化快照兜底
const getToken = (): string => {
  try {
    const storeToken = useUserStore().token
    if (storeToken) return storeToken
  } catch (e) {
    // pinia 未初始化时退回 localStorage
  }
  const cached = JSON.parse(localStorage.getItem('user') || '{}')
  return cached?.token || ''
}

const env = import.meta.env.MODE || 'development'
// 如果是mock模式 或 没启用直连代理 就不配置host 会走本地Mock拦截 或 Vite 代理
const host = env === 'mock' || !proxy.isRequestProxy ? '' : proxy[env].host

// 数据处理，方便区分多种处理方式
const transform: AxiosTransform = {
  // 处理请求数据。如果数据不是预期格式，可直接抛出错误
  transformRequestHook: (res, options) => {
    // console.log(res, 'resresresres')
    const { isTransformResponse, isReturnNativeResponse } = options

    // 如果204无内容直接返回
    const method = res.config.method?.toLowerCase()
    if (res.status === 204 || method === 'put' || method === 'patch') {
      return res
    }

    // 是否返回原生响应头 比如：需要获取响应头时使用该属性
    if (isReturnNativeResponse) {
      return res
    }
    // 不进行任何处理，直接返回
    // 用于页面代码可能需要直接获取code，data，message这些信息时开启

    if (!isTransformResponse) {
      return res.data
    }

    // 错误的时候返回
    const { data } = res
    if (!data) {
      throw new Error('请求接口错误')
    }

    //  这里 code为 后台统一的字段，需要在 types.ts内修改为项目自己的接口返回格式
    const { code } = data
    if (code === 500) {
      console.log(data)
      return data
    }
    // 这里逻辑可以根据项目进行修改
    const hasSuccess = data && code === 200
    if (hasSuccess) {
      return data
    }

    throw new Error(`请求接口错误, 错误码: ${code}`)
  },

  // 请求前处理配置
  beforeRequestHook: (config, options) => {
    const {
      apiUrl,
      isJoinPrefix,
      urlPrefix,
      joinParamsToUrl,
      formatDate,
      joinTime = true
    } = options
    // 添加接口前缀
    if (isJoinPrefix && urlPrefix && isString(urlPrefix)) {
      config.url = `${urlPrefix}${config.url}`
    }

    // 将baseUrl拼接
    if (apiUrl && isString(apiUrl)) {
      config.url = `${apiUrl}${config.url}`
    }
    const params = config.params || {}
    const data = config.data || false
    if (formatDate && data && !isString(data)) {
      formatRequestDate(data)
    }
    if (config.method?.toUpperCase() === 'GET') {
      if (!isString(params)) {
        // 给 get 请求加上时间戳参数，避免从缓存中拿数据。
        config.params = Object.assign(
          params || {},
          joinTimestamp(joinTime, false)
        )
      } else {
        // 兼容restful风格
        config.url = `${config.url + params}${joinTimestamp(joinTime, true)}`
        config.params = undefined
      }
    } else if (!isString(params)) {
      if (formatDate) {
        formatRequestDate(params)
      }
      if (
        Reflect.has(config, 'data') &&
        config.data &&
        (Object.keys(config.data).length > 0 || data instanceof FormData)
      ) {
        config.data = data
        config.params = params
      } else {
        // 非GET请求如果没有提供data，则将params视为data
        config.data = params
        config.params = undefined
      }
      if (joinParamsToUrl) {
        config.url = setObjToUrlParams(config.url as string, {
          ...config.params,
          ...config.data
        })
      }
    } else {
      // 兼容restful风格
      config.url += params
      config.params = undefined
    }
    return config
  },

  // 请求拦截器处理
  requestInterceptors: (config, options) => {
    // 优先取 pinia 的 token：它是权威来源，登录成功后同步更新。
    // localStorage 只是持久化快照，pinia 持久化是批量异步写入，登录后立刻发请求时可能还是旧值，
    // 后端比对 redis 里"该用户最新 token"不一致就会拒绝（表现为 403）。
    const token = getToken()
    if (!token) {
      // 无 token：不带 Authorization 头发起，由后端返回 401/403 走统一错误处理。
      // 原代码在这里 router.push('/login') 却没有 return，会继续带着空 token 发请求，
      // 必然拿到 403，进而弹出与真实原因无关的提示。
      return config
    }
    // 请求之前处理config
    if ((config as Recordable)?.requestOptions?.withToken !== false) {
      // jwt token
      ;(config as Recordable).headers.Authorization =
        options.authenticationScheme
          ? `${options.authenticationScheme} ${token}`
          : token
    }
    return config
  },

  // 响应拦截器处理
  responseInterceptors: (res) => {
    return res
  },

  // 响应错误处理
  // 这里是所有 HTTP/网络失败的唯一出口：把各类失败归一化后 reject。
  // 绝不能 resolve，否则页面会把失败当成成功（历史 bug：return response 把 500 变成成功，
  // 且无响应时 return undefined，导致下游 res.config 抛 TypeError，错误信息彻底丢失）。
  responseInterceptorsCatch: (error: any) => {
    // 主动取消（重复请求）不是错误：不重试、不提示
    if (axios.isCancel(error)) return Promise.reject(error)

    const { response } = error
    const status = response?.status

    // 无响应：后端未启动 / 连接被拒 / 超时（axios timeout 10s）
    if (!response) {
      return Promise.reject({
        ...error,
        isNetworkError: true,
        code: 0,
        msg:
          error.code === 'ECONNABORTED'
            ? '请求超时，请确认后端服务（端口 9995）是否已启动'
            : '无法连接服务器，请确认后端服务（端口 9995）是否已启动'
      })
    }

    // 有响应：交由 Axios.request 的 catch 按状态码分流
    // 显式带出 url：AxiosError 的部分属性不可靠地出现在展开结果里
    return Promise.reject({
      ...error,
      isNetworkError: false,
      code: status,
      url: error.config?.url,
      msg: response.data?.msg || error.message || `请求失败(${status})`
    })
  }
}

function createAxios(opt?: Partial<CreateAxiosOptions>) {
  return new VAxios(
    merge(
      <CreateAxiosOptions>{
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes
        // 例如: authenticationScheme: 'Bearer'
        authenticationScheme: '',
        // 超时
        timeout: 10 * 1000,
        // 携带Cookie
        withCredentials: true,
        // 头信息
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        // 数据处理方式
        transform,
        // 配置项，下面的选项都可以在独立的接口请求中覆盖
        requestOptions: {
          // 接口地址
          // apiUrl: host,
          apiUrl: host, // 'http://172.16.17.63:9995',
          // 是否自动添加接口前缀
          isJoinPrefix: true,
          // 接口前缀
          // 例如: https://www.baidu.com/api
          urlPrefix: '/api', // '/api',
          // urlPrefix: "",
          // 是否返回原生响应头 比如：需要获取响应头时使用该属性
          isReturnNativeResponse: false,
          // 需要对返回数据进行处理
          isTransformResponse: true,
          // post请求的时候添加参数到url
          joinParamsToUrl: false,
          // 格式化提交参数时间
          formatDate: true,
          // 是否加入时间戳
          joinTime: true,
          // 忽略重复请求
          ignoreRepeatRequest: true,
          // 是否携带token
          withToken: true,
          // 重试
          retry: {
            count: 3,
            delay: 1000
          }
        }
      },
      opt || {}
    )
  )
}
export const request = createAxios()
