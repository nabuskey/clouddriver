/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.AmazonCredentialsParser;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class EcsCredentialsInitializer {

  @Bean
  @ConfigurationProperties("ecs")
  public ECSCredentialsConfig ecsCredentialsConfig() {
    return new ECSCredentialsConfig();
  }

  @Bean
  @DependsOn("amazonCredentialsLoader")
  public List<? extends NetflixAmazonCredentials> netflixECSCredentials(
      CredentialsRepository<NetflixAmazonCredentials> accountCredentialsRepository,
      CredentialsParser<CredentialsConfig.Account, ? extends NetflixAmazonCredentials>
          amazonCredentialsParser,
      ECSCredentialsConfig credentialsConfig)
      throws Throwable {
    return synchronizeECSAccounts(
        accountCredentialsRepository,
        (AmazonCredentialsParser) amazonCredentialsParser,
        credentialsConfig);
  }

  private List<? extends NetflixAmazonCredentials> synchronizeECSAccounts(
      CredentialsRepository<NetflixAmazonCredentials> accountCredentialsRepository,
      AmazonCredentialsParser<CredentialsConfig.Account, ? extends NetflixAmazonCredentials>
          amazonCredentialsParser,
      ECSCredentialsConfig ecsCredentialsConfig)
      throws Throwable {

    // TODO: add support for mutable accounts.
    // List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
    // NetflixAmazonCredentials.class, accounts);
    List<NetflixAmazonCredentials> credentials = new LinkedList<>();

    for (AccountCredentials accountCredentials : accountCredentialsRepository.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials) {
        for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
          if (ecsAccount.getAwsAccount().equals(accountCredentials.getName())) {

            NetflixAmazonCredentials netflixAmazonCredentials =
                (NetflixAmazonCredentials) accountCredentials;

            // TODO: accountCredentials should be serializable or somehow cloneable.
            CredentialsConfig.Account account =
                EcsAccountBuilder.build(netflixAmazonCredentials, ecsAccount.getName(), "ecs");

            CredentialsConfig ecsCopy = new CredentialsConfig();
            ecsCopy.setAccounts(Collections.singletonList(account));

            NetflixECSCredentials ecsCredentials =
                new NetflixAssumeRoleEcsCredentials(
                    (NetflixAssumeRoleAmazonCredentials)
                        amazonCredentialsParser.load(ecsCopy).get(0),
                    ecsAccount.getAwsAccount());
            credentials.add(ecsCredentials);

            accountCredentialsRepository.save(ecsCredentials);
            break;
          }
        }
      }
    }

    return credentials;
  }
}
