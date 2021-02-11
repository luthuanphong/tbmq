/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultApplicationPersistenceProcessor implements ApplicationPersistenceProcessor {

    private final ConcurrentMap<String, ApplicationPackProcessingContext> processingContextMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Future<?>> processingFutures = new ConcurrentHashMap<>();

    private final ApplicationMsgAcknowledgeStrategyFactory acknowledgeStrategyFactory;
    private final ApplicationSubmitStrategyFactory submitStrategyFactory;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final TbQueueAdmin queueAdmin;


    private final ExecutorService persistedMsgsConsumeExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("application-persisted-msg-consumers"));

    @Value("${queue.application-persisted-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.application-persisted-msg.pack-processing-timeout}")
    private long packProcessingTimeout;
    @Value("${queue.application-persisted-msg.stop-processing-timeout-ms:100}")
    private long stopProcessingTimeout;

    @Override
    public void acknowledgeDelivery(String clientId, int packetId) {
        log.trace("Executing acknowledgeDelivery [{}][{}]", clientId, packetId);
        ApplicationPackProcessingContext processingContext = processingContextMap.get(clientId);
        if (processingContext == null) {
            log.warn("[{}] Cannot find processing context for client. PacketId - {}.", clientId, packetId);
        } else {
            processingContext.onSuccess(packetId);
        }
    }

    @Override
    public void startProcessingPersistedMessages(String clientId, ClientSessionCtx clientSessionCtx) {
        log.trace("[{}] Starting persisted messages processing.", clientId);
        Future<?> future = persistedMsgsConsumeExecutor.submit(() -> {
            processPersistedMessages(clientId, clientSessionCtx);
        });
        processingFutures.put(clientId, future);
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        log.trace("[{}] Stopping persisted messages processing.", clientId);
        Future<?> processingFuture = processingFutures.get(clientId);
        if (processingFuture == null) {
            log.warn("[{}] Cannot find processing future for client.", clientId);
        } else {
            try {
                processingFuture.get(stopProcessingTimeout, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client. Reason - {}.", clientId, e.getMessage());
            }
        }
    }

    private void processPersistedMessages(String clientId, ClientSessionCtx clientSessionCtx) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer = applicationPersistenceMsgQueueFactory.createConsumer(clientId);
        consumer.assignPartition(0);
        long packetOffset = consumer.getOffset(consumer.getTopic(), 0);

        while (clientSessionCtx.isConnected()) {
            try {
                List<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> persistedMessages = consumer.poll(pollDuration);
                if (persistedMessages.isEmpty()) {
                    continue;
                }

                ApplicationAckStrategy ackStrategy = acknowledgeStrategyFactory.newInstance(clientId);
                ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId, offset -> {
                    log.trace("[{}] Committing offset {}.", clientId, offset);
                    consumer.commit(0, offset);
                });

                AtomicLong msgOffset = new AtomicLong(packetOffset);
                List<PublishMsgWithOffset> persistedMessagesWithOffset = persistedMessages.stream()
                        .map(msg -> new PublishMsgWithOffset(msg.getValue(), msgOffset.incrementAndGet()))
                        .collect(Collectors.toList());
                submitStrategy.init(persistedMessagesWithOffset);

                while (clientSessionCtx.isConnected()) {
                    ApplicationPackProcessingContext ctx = new ApplicationPackProcessingContext(submitStrategy);
                    processingContextMap.put(clientId, ctx);
                    submitStrategy.process(msg -> {
                        log.trace("[{}] processing packet: {}", clientId, msg.getPublishMsgProto().getPacketId());
                        QueueProtos.PublishMsgProto publishMsgProto = msg.getPublishMsgProto();
                        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(publishMsgProto.getPacketId(), publishMsgProto.getTopicName(),
                                MqttQoS.valueOf(publishMsgProto.getQos()), publishMsgProto.getPayload().toByteArray());
                        try {
                            clientSessionCtx.getChannel().writeAndFlush(mqttPubMsg);
                        } catch (Exception e) {
                            if (clientSessionCtx.isConnected()) {
                                log.debug("[{}][{}] Failed to send publish msg to MQTT client. Reason - {}.",
                                        clientId, clientSessionCtx.getSessionId(), e.getMessage());
                                log.trace("Detailed error:", e);
                            }
                        }
                    });

                    if (clientSessionCtx.isConnected()) {
                        ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                    }

                    ApplicationProcessingDecision decision = ackStrategy.analyze(ctx);
                    if (decision.isCommit()) {
                        break;
                    } else {
                        submitStrategy.update(decision.getReprocessMap());
                    }
                }

                if (clientSessionCtx.isConnected()) {
                    log.trace("[{}] Committing all read messages.", clientId);
                    consumer.commit();
                }
                packetOffset += persistedMessages.size();
            } catch (Exception e) {
                if (clientSessionCtx.isConnected()) {
                    log.warn("[{}] Failed to process messages from queue.", clientId, e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                    }
                }
            }
        }

        consumer.unsubscribeAndClose();
        log.info("[{}] Application persisted messages consumer stopped.", clientId);

    }

    @Override
    public void clearPersistedMsgs(String clientId) {
        queueAdmin.deleteTopic(applicationPersistenceMsgQueueFactory.getTopic(clientId));
    }
}
