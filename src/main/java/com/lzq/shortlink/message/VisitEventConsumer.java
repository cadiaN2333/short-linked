package com.lzq.shortlink.message;

import com.lzq.shortlink.config.RabbitMqConfig;
import com.lzq.shortlink.entity.ShortLinkVisitEvent;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.mapper.ShortLinkVisitEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接访问事件消费者。
 */
@Slf4j
@Service
public class VisitEventConsumer {

    private final ShortLinkVisitEventMapper shortLinkVisitEventMapper;

    private final ShortLinkDailyStatMapper shortLinkDailyStatMapper;

    public VisitEventConsumer(
            ShortLinkVisitEventMapper shortLinkVisitEventMapper,
            ShortLinkDailyStatMapper shortLinkDailyStatMapper
    ) {
        this.shortLinkVisitEventMapper = shortLinkVisitEventMapper;
        this.shortLinkDailyStatMapper = shortLinkDailyStatMapper;
    }

    /**
     * 消费一次访问事件，并按日期累计 PV。
     *
     * @param visitEvent 访问事件
     */
    @Transactional
    public void consume(VisitEvent visitEvent) {
        int insertedRows = insertVisitEvent(visitEvent);

        if (insertedRows == 0) {
            log.info("重复访问事件已忽略，eventId={}", visitEvent.eventId());
            return;
        }

        shortLinkDailyStatMapper.incrementPv(
                visitEvent.shortLinkId(),
                visitEvent.visitedAt().toLocalDate()
        );
    }

    /**
     * 批量消费访问事件，按短链接和日期聚合 PV 更新。
     *
     * <p>事件明细仍逐条执行幂等插入，只有真正插入成功的事件才会进入
     * PV 聚合，避免重复投递导致统计重复累计。</p>
     *
     * @param visitEvents 一批访问事件
     */
    @RabbitListener(
            queues = RabbitMqConfig.VISIT_QUEUE,
            containerFactory = "visitBatchListenerContainerFactory"
    )
    @Transactional
    public void consumeBatch(List<VisitEvent> visitEvents) {
        if (visitEvents == null || visitEvents.isEmpty()) {
            return;
        }

        Map<DailyStatKey, Long> pvIncrements = new LinkedHashMap<>();
        int duplicateCount = 0;

        for (VisitEvent visitEvent : visitEvents) {
            if (insertVisitEvent(visitEvent) == 0) {
                duplicateCount++;
                continue;
            }

            DailyStatKey key = new DailyStatKey(
                    visitEvent.shortLinkId(),
                    visitEvent.visitedAt().toLocalDate()
            );
            pvIncrements.merge(key, 1L, Long::sum);
        }

        pvIncrements.forEach((key, increment) ->
                shortLinkDailyStatMapper.incrementPvBy(
                        key.shortLinkId(),
                        key.statDate(),
                        increment
                )
        );

        log.debug(
                "批量访问事件处理完成，batchSize={}, newEventCount={}, duplicateEventCount={}, statGroupCount={}",
                visitEvents.size(),
                visitEvents.size() - duplicateCount,
                duplicateCount,
                pvIncrements.size()
        );
    }

    /** 写入单条访问明细，并返回实际新增行数。 */
    private int insertVisitEvent(VisitEvent visitEvent) {
        ShortLinkVisitEvent shortLinkVisitEvent = new ShortLinkVisitEvent();
        shortLinkVisitEvent.setEventId(visitEvent.eventId());
        shortLinkVisitEvent.setShortLinkId(visitEvent.shortLinkId());
        shortLinkVisitEvent.setVisitedAt(visitEvent.visitedAt());
        return shortLinkVisitEventMapper.insertIgnore(shortLinkVisitEvent);
    }

    /** 每日 PV 聚合键。 */
    private record DailyStatKey(Long shortLinkId, LocalDate statDate) {
    }
}
