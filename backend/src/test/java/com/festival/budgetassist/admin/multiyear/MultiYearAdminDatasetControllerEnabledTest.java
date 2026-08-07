package com.festival.budgetassist.admin.multiyear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * festival.admin-ui.enabled=true(테스트 프로필 기본값)일 때 MultiYearAdminDatasetController
 * 빈이 실제로 등록되는지 확인한다. 비활성화 케이스는
 * {@link MultiYearAdminDatasetControllerDisabledTest} 참고.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MultiYearAdminDatasetControllerEnabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerBeanExistsWhenEnabled() {
        assertEquals(1, context.getBeansOfType(MultiYearAdminDatasetController.class).size());
    }
}