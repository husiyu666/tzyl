package com.tzyl.config;

import com.tzyl.intercept.AutoFillInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  webMvc高级配置
 */
@Configuration
public class MybatisConfig {

    /***
     *  自动填充拦截器
     */
    @Bean
    public AutoFillInterceptor autoFillInterceptor(){
        return new AutoFillInterceptor();
    }

}
