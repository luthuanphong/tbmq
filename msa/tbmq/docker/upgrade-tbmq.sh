#!/bin/bash
#
# Copyright © 2016-2023 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CONF_FOLDER="${pkg.installFolder}/conf"
jarfile=${pkg.installFolder}/bin/${pkg.name}.jar
configfile=${pkg.name}.conf
upgradeversion=${DATA_FOLDER}/.upgradeversion

source "${CONF_FOLDER}/${configfile}"

FROM_VERSION=`cat ${upgradeversion}`

echo "Starting TBMQ upgrade ..."

if [[ -z "${FROM_VERSION// }" ]]; then
    # TODO: remove this check in the next version
    if [ "${pkg.upgradeVersion}" == "1.1.0" ]; then
      fromVersion="1.0.1"
    else
      echo "FROM_VERSION variable is invalid or unspecified!"
      exit 1
    fi
else
    fromVersion="${FROM_VERSION// }"
fi

java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.ThingsboardMqttBrokerInstallApplication \
                -Dspring.jpa.hibernate.ddl-auto=none \
                -Dinstall.upgrade=true \
                -Dinstall.upgrade.from_version=${fromVersion} \
                -Dlogging.config=/usr/share/thingsboard-mqtt-broker/bin/install/logback.xml \
                org.springframework.boot.loader.PropertiesLauncher

echo "Saving upgrade version to file..."

echo "${pkg.upgradeVersion}" > ${upgradeversion}

echo "Saved ${pkg.upgradeVersion}"
