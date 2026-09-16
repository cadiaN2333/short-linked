package com.lzq.shortlink.message;

import com.lzq.shortlink.config.RabbitMqConfig;
import com.lzq.shortlink.entity.ShortLinkVisitEvent;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.mapper.ShortLinkVisitEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @RabbitListener(queues = RabbitMqConfig.VISIT_QUEUE)
    @Transactional
    public void consume(VisitEvent visitEvent) {
        ShortLinkVisitEvent shortLinkVisitEvent = new ShortLinkVisitEvent();
        shortLinkVisitEvent.setEventId(visitEvent.eventId());
        shortLinkVisitEvent.setShortLinkId(visitEvent.shortLinkId());
        shortLinkVisitEvent.setVisitedAt(visitEvent.visitedAt());

        int insertedRows = shortLinkVisitEventMapper.insertIgnore(
                shortLinkVisitEvent
        );

        if (insertedRows == 0) {
            log.info("重复访问事件已忽略，eventId={}", visitEvent.eventId());
            return;
        }

        shortLinkDailyStatMapper.incrementPv(
                visitEvent.shortLinkId(),
                visitEvent.visitedAt().toLocalDate()
        );
    }
}