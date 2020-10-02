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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
  @ConditionalOnMissingBean(
      value = NetflixECSCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  CredentialsRepository<NetflixECSCredentials> amazonECSCredentialsRepository(
      CredentialsLifecycleHandler<NetflixECSCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(EcsCloudProvider.ID, eventHandler);
  }

  @Bean
  @DependsOn("amazonCredentialsLoader")
  CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> ecsCredentialsParser(
      Class<? extends NetflixAmazonCredentials> credentialsType,
      AccountCredentialsProvider accountCredentialsProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      AmazonClientProvider amazonClientProvider) {
    return new ECSCredentialsParser<>(
        credentialsType, accountCredentialsProvider, awsCredentialsProvider, amazonClientProvider);
  }

  @Bean
  AbstractCredentialsLoader<NetflixECSCredentials> ecsCredentialsLoader(
      CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials>
          amazonCredentialsParser,
      @Nullable CredentialsDefinitionSource<ECSCredentialsConfig.Account> ecsCredentialsSource,
      CredentialsRepository<NetflixECSCredentials> repository,
      ECSCredentialsConfig ecsCredentialsConfig,
      CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository) {
    compositeCredentialsRepository.registerRepository(repository);
    if (ecsCredentialsSource == null) {
      ecsCredentialsSource = ecsCredentialsConfig::getAccounts;
    }

    return new BasicCredentialsLoader<>(ecsCredentialsSource, amazonCredentialsParser, repository);
  }

  //  @Bean
  //  @DependsOn("amazonCredentialsLoader")
  //  public List<? extends NetflixAmazonCredentials> netflixECSCredentials(
  //      AccountCredentialsRepository accountCredentialsRepository,
  //      AccountCredentialsProvider accountCredentialsProvider,
  //      ECSCredentialsConfig credentialsConfig,
  //      AWSCredentialsProvider awsCredentialsProvider,
  //      AmazonClientProvider amazonClientProvider,
  //      Class<? extends NetflixAmazonCredentials> credentialsType)
  //      throws Throwable {
  //    return synchronizeECSAccounts(
  //        accountCredentialsRepository,
  //        accountCredentialsProvider,
  //        credentialsConfig,
  //        awsCredentialsProvider,
  //        amazonClientProvider,
  //        credentialsType);
  //  }
  //
  //  private List<? extends NetflixAmazonCredentials> synchronizeECSAccounts(
  //      AccountCredentialsRepository
  //          accountCredentialsRepository, // legacy. Dependency needs to be removed
  //      AccountCredentialsProvider accountCredentialsProvider,
  //      ECSCredentialsConfig ecsCredentialsConfig,
  //      AWSCredentialsProvider awsCredentialsProvider,
  //      AmazonClientProvider amazonClientProvider,
  //      Class<? extends NetflixAmazonCredentials> credentialsType)
  //      throws Throwable {
  //
  //    // TODO: add support for mutable accounts.
  //    // List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
  //    // NetflixAmazonCredentials.class, accounts);
  //    List<NetflixAmazonCredentials> credentials = new LinkedList<>();
  //    CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader =
  //        new CredentialsLoader<>(awsCredentialsProvider, amazonClientProvider, credentialsType);
  //
  //    for (AccountCredentials accountCredentials : accountCredentialsProvider.getAll()) {
  //      if (accountCredentials instanceof NetflixAmazonCredentials) {
  //        for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
  //          if (ecsAccount.getAwsAccount().equals(accountCredentials.getName())) {
  //
  //            NetflixAmazonCredentials netflixAmazonCredentials =
  //                (NetflixAmazonCredentials) accountCredentials;
  //
  //            // TODO: accountCredentials should be serializable or somehow cloneable.
  //            CredentialsConfig.Account account =
  //                EcsAccountBuilder.build(netflixAmazonCredentials, ecsAccount.getName(), "ecs");
  //
  //            CredentialsConfig ecsCopy = new CredentialsConfig();
  //            ecsCopy.setAccounts(Collections.singletonList(account));
  //
  //            NetflixECSCredentials ecsCredentials =
  //                new NetflixAssumeRoleEcsCredentials(
  //                    (NetflixAssumeRoleAmazonCredentials) credentialsLoader.load(ecsCopy).get(0),
  //                    ecsAccount.getAwsAccount());
  //            credentials.add(ecsCredentials);
  //
  //            accountCredentialsRepository.save(ecsAccount.getName(), ecsCredentials);
  //            break;
  //          }
  //        }
  //      }
  //    }
  //
  //    return credentials;
  //  }
}
