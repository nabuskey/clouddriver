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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent;
import com.netflix.spinnaker.clouddriver.aws.provider.config.ProviderHelpers;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
@Slf4j
@RequiredArgsConstructor
public class AmazonCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<NetflixAmazonCredentials> {
  protected final AwsCleanupProvider awsCleanupProvider;
  protected final AwsInfrastructureProvider awsInfrastructureProvider;
  protected final AwsProvider awsProvider;
  protected final AmazonCloudProvider amazonCloudProvider;
  protected final AmazonClientProvider amazonClientProvider;
  protected final AmazonS3DataProvider amazonS3DataProvider;
  protected final CatsModule catsModule;

  protected final AwsConfigurationProperties awsConfigurationProperties;
  protected final ObjectMapper objectMapper;
  protected final @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper;
  protected final EddaApiFactory eddaApiFactory;
  protected final ApplicationContext ctx;
  protected final Registry registry;
  protected final Optional<ExecutorService> reservationReportPool;
  protected final Optional<Collection<AgentProvider>> agentProviders;
  protected final EddaTimeoutConfig eddaTimeoutConfig;
  protected final DynamicConfigService dynamicConfigService;
  protected final DeployDefaults deployDefaults;
  protected final CredentialsRepository<NetflixAmazonCredentials>
      accountCredentialsRepository; // Circular dependency.
  private Set<String> publicRegions = new HashSet<>();
  private Set<String> awsInfraRegions = new HashSet<>();

  @Override
  public void credentialsAdded(@NotNull NetflixAmazonCredentials credentials) {
    scheduleAgents(credentials);
    synchronizeReservationReportCachingAgentAccounts(credentials, true);
  }

  @Override
  public void credentialsUpdated(@NotNull NetflixAmazonCredentials credentials) {
    ProviderUtils.unscheduleAndDeregisterAgents(
        Collections.singleton(credentials.getName()), catsModule);
    scheduleAgents(credentials);
    synchronizeReservationReportCachingAgentAccounts(credentials, true);
  }

  @Override
  public void credentialsDeleted(NetflixAmazonCredentials credentials) {
    ProviderUtils.unscheduleAndDeregisterAgents(
        Collections.singleton(credentials.getName()), catsModule);
    synchronizeReservationReportCachingAgentAccounts(credentials, false);
  }

  private void scheduleAgents(NetflixAmazonCredentials credentials) {
    scheduleAWSProviderAgents(credentials);
    scheduleAwsInfrastructureProviderAgents(credentials);
    scheduleAwsCleanupAgents(credentials);
  }

  private void scheduleAwsInfrastructureProviderAgents(NetflixAmazonCredentials credentials) {
    ProviderHelpers.BuildResult result =
        ProviderHelpers.buildAwsInfrastructureAgents(
            credentials,
            awsInfrastructureProvider,
            accountCredentialsRepository,
            amazonClientProvider,
            amazonObjectMapper,
            registry,
            eddaTimeoutConfig,
            this.awsInfraRegions);
    if (awsInfrastructureProvider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(awsInfrastructureProvider, result.agents);
    }
    awsInfrastructureProvider.getAgents().addAll(result.agents);
    this.awsInfraRegions.addAll(result.regionsToAdd);
  }

  private void scheduleAWSProviderAgents(NetflixAmazonCredentials credentials) {
    // parallel safe?
    ProviderHelpers.BuildResult buildResult =
        ProviderHelpers.buildAwsProviderAgents(
            credentials,
            accountCredentialsRepository,
            amazonClientProvider,
            objectMapper,
            registry,
            eddaTimeoutConfig,
            awsProvider,
            amazonCloudProvider,
            dynamicConfigService,
            eddaApiFactory,
            reservationReportPool,
            agentProviders,
            ctx,
            amazonS3DataProvider,
            publicRegions);
    if (awsProvider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(awsProvider, buildResult.agents);
    }
    awsProvider.getAgents().addAll(buildResult.agents);
    this.publicRegions.addAll(buildResult.regionsToAdd);
    awsProvider.synchronizeHealthAgents();
  }

  private void scheduleAwsCleanupAgents(NetflixAmazonCredentials credentials) {
    List<Agent> newlyAddedAgents =
        ProviderHelpers.buildAwsCleanupAgents(
            credentials,
            accountCredentialsRepository,
            amazonClientProvider,
            awsCleanupProvider,
            deployDefaults,
            awsConfigurationProperties);
    if (awsCleanupProvider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(awsCleanupProvider, newlyAddedAgents);
    }
    awsCleanupProvider.getAgents().addAll(newlyAddedAgents);
  }

  // This needs to be moved else where.
  private void synchronizeReservationReportCachingAgentAccounts(
      NetflixAmazonCredentials credentials, boolean add) {
    ReservationReportCachingAgent reservationReportCachingAgent =
        awsProvider.getAgents().stream()
            .filter(agent -> agent instanceof ReservationReportCachingAgent)
            .map(ReservationReportCachingAgent.class::cast)
            .findFirst()
            .orElse(null);
    if (reservationReportCachingAgent != null) {
      Collection<NetflixAmazonCredentials> reservationReportAccounts =
          reservationReportCachingAgent.getAccounts();
      reservationReportAccounts.removeIf(it -> it.getName().equals(credentials.getName()));
      if (add) {
        reservationReportAccounts.add(credentials);
      }
    }
  }
}
