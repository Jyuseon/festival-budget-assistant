package com.festival.budgetassist.admin.multiyear;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * festival.admin-ui.enabled=false일 때 MultiYearAdminDatasetController 빈이 아예 등록되지
 * 않는지 확인한다 - 운영 환경에서는 이 경로가 항상 404라는 보증이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "festival.admin-ui.enabled=false")
class MultiYearAdminDatasetControllerDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerBeanDoesNotExistWhenDisabled() {
        assertTrue(context.getBeansOfType(MultiYearAdminDatasetController.class).isEmpty());
    }
}