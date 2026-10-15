package com.tzyl.security;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tzyl.constant.SecurityConstant;
import com.tzyl.constant.UserCacheConstant;
import com.tzyl.properties.JwtTokenManagerProperties;
import com.tzyl.utils.EmptyUtil;
import com.tzyl.utils.JwtUtil;
import com.tzyl.utils.UserThreadLocal;
import com.tzyl.vo.UserVo;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *  授权管理器
 */
@Slf4j
@Component
public class JwtAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JwtTokenManagerProperties jwtTokenManagerProperties;


    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext requestAuthorizationContext) {
        //用户当前请求路径
        String method = requestAuthorizationContext.getRequest().getMethod();
        String requestURI = requestAuthorizationContext.getRequest().getRequestURI();
        String targetUrl = (method+requestURI);

        //获得请求中的认证后传递过来的userToken
        String userToken = requestAuthorizationContext.getRequest().getHeader(SecurityConstant.USER_TOKEN);

        //如果userToken为空,则当前请求不合法
        if (EmptyUtil.isNullOrEmpty(userToken)){
            log.warn("[鉴权拒绝] 原因=缺少token, uri={}, method={}", requestURI, method);
            return new AuthorizationDecision(false);
        }

        //通过userToken获取jwtToken
        String jwtTokenKey = UserCacheConstant.JWT_TOKEN+userToken;
        //key:uuid
        String jwtToken = redisTemplate.opsForValue().get(jwtTokenKey);

        //如果jwtToken为空,则当前请求不合法
        if (EmptyUtil.isNullOrEmpty(jwtToken)){
            log.warn("[鉴权拒绝] 原因=redis中无该token的JWT(可能已过期或Redis已重启), uri={}, method={}", requestURI, method);
            return new AuthorizationDecision(false);
        }

        //校验jwtToken是否合法
        Claims cla = JwtUtil.parseJWT(jwtTokenManagerProperties.getBase64EncodedSecretKey(), jwtToken);
        if (ObjectUtil.isEmpty(cla)) {
            //token失效
            log.warn("[鉴权拒绝] 原因=JWT解析失败(密钥或内容异常), uri={}, method={}", requestURI, method);
            return new AuthorizationDecision(false);
        }

        //如果校验jwtToken通过，则获得userVo对象
        UserVo userVo = JSONObject.parseObject(String.valueOf(cla.get("currentUser")),UserVo.class);

        //用户剔除校验:redis中最新的userToken与出入的userToken不符合，则认为当前用户被后续用户剔除
        //key:username  value：uuid
        String currentUserToken = redisTemplate.opsForValue().get(UserCacheConstant.USER_TOKEN + userVo.getUsername());
        if (!userToken.equals(currentUserToken)){
            log.warn("[鉴权拒绝] 原因=token与redis中该用户最新token不一致(被顶号或前端持有旧token), uri={}, method={}, user={}", requestURI, method, userVo.getUsername());
            return new AuthorizationDecision(false);
        }

        //如果当前UserToken存活时间少于10分钟，则进行续期
        Long remainTimeToLive = redisTemplate.opsForValue().getOperations().getExpire(jwtTokenKey);
        if (remainTimeToLive.longValue()<= 600){
            //jwt生成的token也会过期，所以需要重新生成jwttoken
            Map<String, Object> claims = new HashMap<>();
            String userVoJsonString = String.valueOf(cla.get("currentUser"));
            claims.put("currentUser", userVoJsonString);

            //jwtToken令牌颁布
            String newJwtToken = JwtUtil.createJWT(jwtTokenManagerProperties.getBase64EncodedSecretKey(), jwtTokenManagerProperties.getTtl(), claims);
            long ttl = Long.valueOf(jwtTokenManagerProperties.getTtl()) / 1000;

            redisTemplate.opsForValue().set(jwtTokenKey, newJwtToken, ttl, TimeUnit.SECONDS);
            redisTemplate.expire(UserCacheConstant.USER_TOKEN + userVo.getUsername(), ttl, TimeUnit.SECONDS);
        }

        //当前用户资源是否包含当前URL
        //兼容两种存储格式：request_path 为纯路径(/bed/create) 或 带请求方式前缀(POST/bed/create)
        if (ObjectUtil.isEmpty(userVo.getResourceRequestPaths())) {
            log.warn("[鉴权拒绝] 原因=该用户resourceRequestPaths为空(登录时未写入菜单权限), uri={}, method={}, user={}", requestURI, method, userVo.getUsername());
            return new AuthorizationDecision(false);
        }
        for (String resourceRequestPath : userVo.getResourceRequestPaths()) {
            // sys_resource.request_path 里存在"GET /user/current-user"这种在方法名后带空格的写法，
            // 而 targetUrl 是 method + requestURI 拼出来的（不含空格），
            // 导致这类配置无论匹配 requestURI 还是 targetUrl 都失败，接口被永久拒绝(403)。
            // 匹配前统一去掉空白，两种录入格式即可同时兼容。
            String normalizedPath = resourceRequestPath.replaceAll("\\s+", "");
            boolean isMatch = antPathMatcher.match(normalizedPath, requestURI)
                    || antPathMatcher.match(normalizedPath, targetUrl);
            if (isMatch){
                log.info("用户:{}拥有targetUrl权限:{}==========",userVo.getUsername(),targetUrl);
                return new AuthorizationDecision(true);
            }
        }
        log.warn("[鉴权拒绝] 原因=该用户无此接口权限(request_path未匹配), uri={}, method={}, user={}, 可访问数={}", requestURI, method, userVo.getUsername(), userVo.getResourceRequestPaths().size());
        return new AuthorizationDecision(false);
    }

}
