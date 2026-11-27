package com.pointplatform.core.common;

import com.pointplatform.core.common.error.BusinessException;
import com.pointplatform.core.common.security.AdminApiGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminApiGuardTest {

    @Test
    void 토큰이_설정되지_않으면_항상_거부한다() {
        AdminApiGuard guard = new AdminApiGuard("");
        assertThatThrownBy(() -> guard.check("")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.check(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 같은_토큰만_통과한다() {
        AdminApiGuard guard = new AdminApiGuard("secret");
        assertThatCode(() -> guard.check("secret")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.check("Secret")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.check(null)).isInstanceOf(BusinessException.class);
    }
}
