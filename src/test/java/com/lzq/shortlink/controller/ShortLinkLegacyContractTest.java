package com.lzq.shortlink.controller;

import com.lzq.shortlink.dto.CreateShortLinkRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** 旧的无工作空间创建契约应从控制器移除。 */
class ShortLinkLegacyContractTest {

    @Test
    void shouldNotExposeLegacyCreateMethod() {
        assertThrows(
                NoSuchMethodException.class,
                () -> ShortLinkController.class.getDeclaredMethod(
                        "createShortLink",
                        CreateShortLinkRequest.class
                )
        );
    }
}
