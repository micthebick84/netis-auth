package com.example.authserver.config.security;

import com.example.authserver.service.CustomUserDetails;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * CustomUserDetails를 Jackson 직렬화/역직렬화에서 사용할 수 있도록 하는 모듈
 */
public class CustomUserDetailsModule extends SimpleModule {

    public CustomUserDetailsModule() {
        super(CustomUserDetailsModule.class.getName(), new Version(1, 0, 0, null, null, null));
    }

    @Override
    public void setupModule(SetupContext context) {
        // SecurityJackson2Modules의 allowlist에 CustomUserDetails 클래스 추가
        context.setMixInAnnotations(CustomUserDetails.class, CustomUserDetailsMixin.class);
    }
}
