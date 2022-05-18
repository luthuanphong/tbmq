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
package org.thingsboard.mqtt.broker.service.mqtt.keepalive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
class KeepAliveServiceImplTest {

    ClientMqttActorManager clientMqttActorManager;
    KeepAliveServiceImpl keepAliveService;

    @BeforeEach
    void setUp() {
        clientMqttActorManager = mock(ClientMqttActorManager.class);
        keepAliveService = spy(new KeepAliveServiceImpl(clientMqttActorManager));
    }

    @Test
    void testIsInactive() {
        var ts = System.currentTimeMillis() - 20000;
        assertTrue(keepAliveService.isInactive(10, ts));
        ts = System.currentTimeMillis() - 10000;
        assertFalse(keepAliveService.isInactive(10, ts));
    }

    @Test
    void testAcknowledgeControlPacket() {
        assertThrows(MqttException.class, () -> keepAliveService.acknowledgeControlPacket(UUID.randomUUID()));
    }

    @Test
    void testKeepAliveLifecycle() throws InterruptedException {
        UUID sessionId1 = UUID.randomUUID();
        keepAliveService.registerSession("clientId1", sessionId1, 10);
        keepAliveService.registerSession("clientId2", UUID.randomUUID(), 20);
        keepAliveService.registerSession("clientId3", UUID.randomUUID(), 30);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        assertEquals(3, keepAliveService.getKeepAliveInfoSize());

        keepAliveService.registerSession("clientId4", UUID.randomUUID(), 0);
        assertEquals(4, keepAliveService.getKeepAliveInfoSize());

        // need to sleep to wait for keepAlive to be expired
        Thread.sleep(5);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
        assertEquals(3, keepAliveService.getKeepAliveInfoSize());

        keepAliveService.unregisterSession(sessionId1);
        assertEquals(2, keepAliveService.getKeepAliveInfoSize());
    }

}
