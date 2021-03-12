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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPublishMsgDeliveryService implements PublishMsgDeliveryService {
    private final MqttMessageGenerator mqttMessageGenerator;

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, int packetId, String topic, MqttQoS qos, byte[] payload) {
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(packetId, topic, qos, payload);
        try {
            sessionCtx.getChannel().writeAndFlush(mqttPubMsg);
        } catch (Exception e) {
            if (sessionCtx.getSessionState() == SessionState.CONNECTED) {
                log.debug("[{}][{}] Failed to send publish msg to MQTT client. Reason - {}.",
                        sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
                log.trace("Detailed error:", e);
            }
        }
    }
}