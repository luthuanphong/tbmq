/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.ClientType;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class ClientTypeSslMqttCredentials implements Serializable {

    @Serial
    private static final long serialVersionUID = 2116141038835060250L;

    private final ClientType type;
    private final SslMqttCredentials sslMqttCredentials;
    private final String name;

}
