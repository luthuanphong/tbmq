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
package org.thingsboard.mqtt.broker.service.mqtt.client.disconnect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DisconnectClientCommandQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientSessionActorManager;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisconnectClientCommandProcessor {
    private final ExecutorService consumerExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("disconnect-client-command-consumer"));
    private volatile boolean stopped = false;

    private final DisconnectClientCommandQueueFactory disconnectClientCommandQueueFactory;
    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientSessionActorManager clientSessionActorManager;

    @Value("${queue.disconnect-client-command.poll-interval}")
    private long pollDuration;

    private TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> consumer;

    @PostConstruct
    public void init() {
        this.consumer = disconnectClientCommandQueueFactory.createConsumer();
        this.consumer.subscribe();
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto> msg : msgs) {
                        processClientDisconnect(msg);
                    }
                    consumer.commit();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
            log.info("Disconnect Client Command Consumer stopped.");
        });
    }

    private void processClientDisconnect(TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto> msg) {
        String clientId = msg.getKey();
        QueueProtos.DisconnectClientCommandProto disconnectClientCommandProto = msg.getValue();
        UUID sessionId = new UUID(disconnectClientCommandProto.getSessionIdMSB(), disconnectClientCommandProto.getSessionIdLSB());
        clientSessionActorManager.disconnect(clientId, sessionId, new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS));
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        consumerExecutor.shutdownNow();
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }
}
