package com.tzyl;

import com.tzyl.config.OssAliyunAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class TzylApplication {

	public static void main(String[] args) {
		SpringApplication.run(TzylApplication.class, args);
	}
}
