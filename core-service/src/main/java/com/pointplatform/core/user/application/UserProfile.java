package com.pointplatform.core.user.application;

import com.pointplatform.core.user.domain.UserAccount;

import java.time.OffsetDateTime;

/** 다른 모듈·API 로 내보내는 사용자 정보 (비밀번호 해시 등은 포함하지 않는다) */
public record UserProfile(long id, String email, String name, OffsetDateTime createdAt) {

    static UserProfile of(UserAccount user) {
        return new UserProfile(user.id(), user.email(), user.name(), user.createdAt());
    }
}
