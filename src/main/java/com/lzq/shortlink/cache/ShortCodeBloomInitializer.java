package com.lzq.shortlink.cache;

import com.lzq.shortlink.config.ShortLinkBloomProperties;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 启动时完整加载历史短码，不发布部分初始化结果。 */
@Component
public class ShortCodeBloomInitializer
        implements ApplicationRunner {

    private final ShortCodeBloomFilter bloomFilter;

    private final ShortLinkBloomProperties properties;

    private final ShortLinkMapper mapper;

    public ShortCodeBloomInitializer(
            ShortCodeBloomFilter bloomFilter,
            ShortLinkBloomProperties properties,
            ShortLinkMapper mapper
    ) {
        this.bloomFilter = bloomFilter;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        bloomFilter.initialize(add -> {
            verifyCollation();

            long lastId = 0;

            while (true) {
                List<ShortLink> batch =
                        mapper.selectShortCodesAfterId(
                                lastId,
                                properties.loadBatchSize()
                        );

                if (batch.isEmpty()) {
                    return;
                }

                for (ShortLink link : batch) {
                    Long id = link.getId();

                    if (id == null || id <= lastId) {
                        throw new IllegalStateException(
                                "历史短码加载游标没有递增"
                        );
                    }

                    add.accept(link.getShortCode());
                    lastId = id;
                }
            }
        });
    }

    private void verifyCollation() {
        String collation = mapper.selectShortCodeCollation();

        if (collation == null) {
            throw new IllegalStateException(
                    "无法确认 short_code 的数据库比较规则"
            );
        }

        String normalized =
                collation.toLowerCase(Locale.ROOT);

        if (!"binary".equals(normalized)
                && !normalized.endsWith("_bin")) {
            throw new IllegalStateException(
                    "当前 short_code 不是二进制比较规则，"
                            + "完成短码一致性验证前不能启用负拦截"
            );
        }
    }
}