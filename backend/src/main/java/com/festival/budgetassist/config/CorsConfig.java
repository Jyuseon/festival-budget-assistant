package com.festival.budgetassist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발 환경에서 Next.js(3000) -> Spring Boot(8080) 요청을 허용하기 위한 CORS 설정.
 * 운영 배포 시에는 실제 프론트엔드 도메인으로 allowedOrigins 를 교체해야 한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}