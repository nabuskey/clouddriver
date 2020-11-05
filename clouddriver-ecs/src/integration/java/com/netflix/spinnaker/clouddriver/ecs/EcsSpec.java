/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public class EcsSpec {
  protected static final String TEST_OPERATIONS_LOCATION =
      "src/integration/resources/testoperations";
  protected static final String TEST_ARTIFACTS_LOCATION = "src/integration/resources/testartifacts";
  protected static final String ECS_ACCOUNT_NAME = "ecs-account";
  protected static final String AWS_ACCOUNT_NAME = "aws-account";
  protected final String TEST_REGION = "us-west-2";
  protected final int TASK_RETRY_SECONDS = 3;
  protected static final String CREATE_SG_TEST_PATH = "/ecs/ops/createServerGroup";

  @TestConfiguration
  public static class Config {
    @Bean
    @Primary
    public CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository() {
      NetflixAmazonCredentials awsCreds = TestCredential.named(AWS_ACCOUNT_NAME);
      NetflixECSCredentials ecsCreds =
          new NetflixAssumeRoleEcsCredentials(
              TestCredential.assumeRoleNamed(ECS_ACCOUNT_NAME), AWS_ACCOUNT_NAME);
      CompositeCredentialsRepository<AccountCredentials> repo =
          mock(CompositeCredentialsRepository.class);
      when(repo.getCredentials(any(), eq("aws"))).thenReturn(awsCreds);
      when(repo.getCredentials(any(), eq("ecs"))).thenReturn(ecsCreds);
      when(repo.getFirstCredentialsWithName(ECS_ACCOUNT_NAME)).thenReturn(ecsCreds);
      when(repo.getFirstCredentialsWithName(AWS_ACCOUNT_NAME)).thenReturn(awsCreds);
      return repo;
    }

    @Bean("amazonCredentialsParser")
    @Primary
    public CredentialsParser amazonCredentialsParser() {
      NetflixAmazonCredentials awsCreds = TestCredential.assumeRoleNamed(ECS_ACCOUNT_NAME);
      CredentialsParser parser = mock(CredentialsParser.class);
      when(parser.parse(any())).thenReturn(awsCreds);
      return parser;
    }
  }

  @Value("${ecs.enabled}")
  Boolean ecsEnabled;

  @Value("${aws.enabled}")
  Boolean awsEnabled;

  @LocalServerPort private int port;

  @MockBean protected AmazonClientProvider mockAwsProvider;

  @DisplayName(".\n===\n" + "Assert AWS and ECS providers are enabled" + "\n===")
  @Test
  public void configTest() {
    assertTrue(awsEnabled);
    assertTrue(ecsEnabled);
  }

  protected String generateStringFromTestFile(String path) throws IOException {
    return new String(Files.readAllBytes(Paths.get(TEST_OPERATIONS_LOCATION, path)));
  }

  protected String generateStringFromTestArtifactFile(String path) throws IOException {
    return new String(Files.readAllBytes(Paths.get(TEST_ARTIFACTS_LOCATION, path)));
  }

  protected String getTestUrl(String path) {
    return "http://localhost:" + port + path;
  }

  protected DefaultCacheResult buildCacheResult(
      Map<String, Object> attributes, String namespace, String key) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(namespace, dataPoints);

    return new DefaultCacheResult(dataMap);
  }

  protected void retryUntilTrue(BooleanSupplier func, String failMsg, int retrySeconds)
      throws InterruptedException {
    for (int i = 0; i < retrySeconds; i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(1000);
      } else {
        return;
      }
    }
    fail(failMsg);
  }
}
