/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// public class ECSCredentialsParser<T extends ECSCredentialsConfig.Account, U extends
// NetflixECSCredentials, V extends NetflixAmazonCredentials>

public class ECSCredentialsParser<T extends NetflixAmazonCredentials>
    implements CredentialsParser<ECSCredentialsConfig.ECSAccount, NetflixECSCredentials> {

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final CredentialsParser<CredentialsConfig.Account, NetflixAmazonCredentials>
      credentialsLoader;

  public ECSCredentialsParser(
      AccountCredentialsProvider accountCredentialsProvider,
      CredentialsParser<CredentialsConfig.Account, NetflixAmazonCredentials> credentialsLoader) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.credentialsLoader = credentialsLoader;
  }

  @Nullable
  @Override
  public NetflixECSCredentials parse(ECSCredentialsConfig.@NotNull ECSAccount credentials) {
    for (AccountCredentials accountCredentials : accountCredentialsProvider.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials
          && credentials.getAwsAccount().equals(accountCredentials.getName())) {

        NetflixAmazonCredentials netflixAmazonCredentials =
            (NetflixAmazonCredentials) accountCredentials;
        CredentialsConfig.Account account =
            EcsAccountBuilder.build(netflixAmazonCredentials, credentials.getName(), "ecs");

        try {
          return new NetflixAssumeRoleEcsCredentials(
              (NetflixAssumeRoleAmazonCredentials) credentialsLoader.parse(account),
              credentials.getName());
        } catch (Throwable throwable) {
          throwable.printStackTrace();
          return null;
        }
      }
    }
    return null;
  }
}
