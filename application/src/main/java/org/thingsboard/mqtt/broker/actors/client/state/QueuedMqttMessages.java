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
package org.thingsboard.mqtt.broker.actors.client.state;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

/*
    not thread safe
 */
@Slf4j
public class QueuedMqttMessages {
    private final Queue<MqttMessage> queuedMessages = new LinkedList<>();

    public void process(Consumer<MqttMessage> processor) {
        while (!queuedMessages.isEmpty()) {
            MqttMessage msg = queuedMessages.poll();
            try {
                processor.accept(msg);
            } finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    public void add(MqttMessage msg) {
        queuedMessages.add(msg);
    }

    public void release() {
        log.debug("Releasing {} queued messages.", queuedMessages.size());
        while (!queuedMessages.isEmpty()) {
            MqttMessage msg = queuedMessages.poll();
            ReferenceCountUtil.safeRelease(msg);
        }
    }
}