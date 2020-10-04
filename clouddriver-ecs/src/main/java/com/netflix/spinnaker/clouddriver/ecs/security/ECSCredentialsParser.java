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

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
public class ECSCredentialsParser<T extends NetflixAmazonCredentials>
    implements CredentialsParser<ECSCredentialsConfig.ECSAccount, NetflixECSCredentials> {

  private CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;
  private CredentialsParser<CredentialsConfig.Account, NetflixAmazonCredentials> parser;

  @Override
  public NetflixECSCredentials parse(ECSCredentialsConfig.@NotNull ECSAccount credentials) {
    NetflixAmazonCredentials netflixAmazonCredentials;
    try {
      netflixAmazonCredentials =
          (NetflixAmazonCredentials)
              compositeCredentialsRepository.getCredentials(
                  credentials.getAwsAccount(), AmazonCloudProvider.ID);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
      return null;
    }

    CredentialsConfig.Account account =
        EcsAccountBuilder.build(netflixAmazonCredentials, credentials.getName(), EcsProvider.NAME);
    try {
      return new NetflixAssumeRoleEcsCredentials(
          (NetflixAssumeRoleAmazonCredentials) parser.parse(account), credentials.getName());
    } catch (Throwable throwable) {
      throwable.printStackTrace();
      return null;
    }
  }
}
