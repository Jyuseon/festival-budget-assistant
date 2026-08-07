package com.festival.budgetassist.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트엔드(Next.js) - 백엔드(Spring Boot) 연동 확인용 헬스체크 API.
 * DB 연결까지 포함한 상세 상태는 /actuator/health 를 사용한다.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "OK",
                "service", "budget-assist",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}