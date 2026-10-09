package com.lzq.shortlink.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lzq.shortlink.cache.ShortLinkLocalCacheValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** 短链接本地缓存配置。 */
@Configuration
public class LocalCacheConfig {

    /**
     * L1 正向缓存只保存热点短链接，短 TTL 限制多实例部署下的状态陈旧窗口。
     */
    @Bean
    public Cache<String, ShortLinkLocalCacheValue> shortLinkLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .recordStats()
                .build();
    }

    /** 负缓存使用更短 TTL，避免新创建短链接被本地空值长期遮蔽。 */
    @Bean
    public Cache<String, Boolean> shortLinkLocalMissingCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(5))
                .recordStats()
                .build();
    }
}
