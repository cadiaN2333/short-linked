package com.lzq.shortlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.lzq.shortlink.config.ShortLinkProperties;
import com.lzq.shortlink.config.ShortLinkBloomProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ShortLinkProperties.class,
        ShortLinkBloomProperties.class
})
@MapperScan({
        "com.lzq.shortlink.mapper",
        "com.lzq.shortlink.workspace"
})
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }

}
