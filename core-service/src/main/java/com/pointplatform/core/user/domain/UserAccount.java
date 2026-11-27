package com.pointplatform.core.user.domain;

import java.time.OffsetDateTime;

/** users.app_user 한 행 */
public record UserAccount(long id, String email, String name, OffsetDateTime createdAt) {
}
