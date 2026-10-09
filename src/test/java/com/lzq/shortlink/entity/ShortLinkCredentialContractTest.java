package com.lzq.shortlink.entity;

import com.lzq.shortlink.dto.CreateShortLinkResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** 已退场的短链接管理凭证不得再出现在实体或创建响应中。 */
class ShortLinkCredentialContractTest {

    @Test
    void shouldNotExposeManagementTokenInEntityOrCreateResponse() {
        assertFalse(hasManagementTokenField(ShortLink.class));
        assertFalse(hasManagementTokenField(CreateShortLinkResponse.class));
    }

    private boolean hasManagementTokenField(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> "manageToken".equals(field.getName()));
    }
}
