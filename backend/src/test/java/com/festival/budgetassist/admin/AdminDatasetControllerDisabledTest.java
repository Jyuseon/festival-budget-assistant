package com.festival.budgetassist.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * festival.admin-ui.enabled=false일 때 AdminDatasetController 빈이 아예 등록되지 않는지
 * 확인한다 - 이 값이 곧 "운영 환경에서는 관리자 API가 비활성화된다"는 보증이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "festival.admin-ui.enabled=false")
class AdminDatasetControllerDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerBeanDoesNotExistWhenDisabled() {
        assertTrue(context.getBeansOfType(AdminDatasetController.class).isEmpty());
    }
}