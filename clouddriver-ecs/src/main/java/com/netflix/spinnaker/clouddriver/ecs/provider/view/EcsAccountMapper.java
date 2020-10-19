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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class EcsAccountMapper {

  @Lazy final CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Lazy final CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;
  protected final Map<String, String> ecsCredentialsMap;
  protected final Map<String, String> awsCredentialsMap;

  @Autowired
  public EcsAccountMapper(
      @Lazy CredentialsRepository<NetflixECSCredentials> credentialsRepository,
      @Lazy CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository) {
    this.credentialsRepository = credentialsRepository;
    this.compositeCredentialsRepository = compositeCredentialsRepository;

    ecsCredentialsMap = new HashMap<>();
    awsCredentialsMap = new HashMap<>();
  }

  public void addMapEntry(ECSCredentialsConfig.@NotNull Account account) {
    ecsCredentialsMap.put(account.getAwsAccount(), account.getName());
    awsCredentialsMap.put(account.getName(), account.getAwsAccount());
  }

  public void removeMapEntry(String ecsAccountName) {
    ecsCredentialsMap.remove(awsCredentialsMap.get(ecsAccountName));
    awsCredentialsMap.remove(ecsAccountName);
  }

  public NetflixECSCredentials fromAwsAccountNameToEcs(String awsAccountName) {
    return credentialsRepository.getOne(ecsCredentialsMap.get(awsAccountName));
  }

  public NetflixAmazonCredentials fromEcsAccountNameToAws(String ecsAccountName) {
    return (NetflixAmazonCredentials)
        compositeCredentialsRepository.getCredentials(
            awsCredentialsMap.get(ecsAccountName), AmazonCloudProvider.ID);
  }

  public String fromAwsAccountNameToEcsAccountName(String awsAccountName) {
    return ecsCredentialsMap.get(awsAccountName);
  }

  public String fromEcsAccountNameToAwsAccountName(String ecsAccountName) {
    return awsCredentialsMap.get(ecsAccountName);
  }
}
