///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { mergeMap, Observable, of } from 'rxjs';
import { defaultHttpOptionsFromConfig, RequestConfig } from '../http/http-utils';
import {
  AdminSettings,
  ConnectivitySettings,
  connectivitySettingsKey,
  defaultConnectivitySettings,
  SecuritySettings
} from '@shared/models/settings.models';

@Injectable({
  providedIn: 'root'
})
export class SettingsService {

  private connectivitySettingsValue = {} as ConnectivitySettings;

  constructor(private http: HttpClient) {
  }

  public getGeneralSettings<T>(key: string): Observable<AdminSettings<T>> {
    return this.http.get<AdminSettings<T>>(`/api/admin/settings/${key}`, defaultHttpOptionsFromConfig({ignoreErrors: true, ignoreLoading: true}));
  }

  public saveAdminSettings<T>(adminSettings: AdminSettings<T>,
                              config?: RequestConfig): Observable<AdminSettings<T>> {
    return this.http.post<AdminSettings<T>>('/api/admin/settings', adminSettings, defaultHttpOptionsFromConfig(config));
  }

  public getSecuritySettings(config?: RequestConfig): Observable<SecuritySettings> {
    return this.http.get<SecuritySettings>(`/api/admin/securitySettings`, defaultHttpOptionsFromConfig(config));
  }

  public saveSecuritySettings(securitySettings: SecuritySettings,
                              config?: RequestConfig): Observable<SecuritySettings> {
    return this.http.post<SecuritySettings>('/api/admin/securitySettings', securitySettings,
      defaultHttpOptionsFromConfig(config));
  }

  public updateConnectivitySettings() {
    return this.getGeneralSettings(connectivitySettingsKey).pipe(
      mergeMap(connectivitySettings => {
        this.connectivitySettingsValue = this.transformConnectivitySettings(connectivitySettings.jsonValue as ConnectivitySettings);
        // @ts-ignore
        window.tbmqSettings = this.connectivitySettingsValue;
        return of(this.connectivitySettingsValue);
      })
    );
  }

  public getConnectivitySettings(): ConnectivitySettings {
    return this.connectivitySettingsValue;
  }

  private transformConnectivitySettings(settings: ConnectivitySettings): ConnectivitySettings {
    const connectivitySettings = JSON.parse(JSON.stringify(defaultConnectivitySettings));
    for (const prop of Object.keys(settings)) {
      if (settings[prop]?.enabled) {
        connectivitySettings[prop].enabled = true;
        connectivitySettings[prop].host = settings[prop].host;
        connectivitySettings[prop].port = settings[prop].port;
      }
    }
    return connectivitySettings;
  }
}
