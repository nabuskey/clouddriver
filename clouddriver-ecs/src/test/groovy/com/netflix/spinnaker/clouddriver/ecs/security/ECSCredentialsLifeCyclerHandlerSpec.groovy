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

package com.netflix.spinnaker.clouddriver.ecs.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TargetHealthCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper
import spock.lang.Specification

import java.util.stream.Collectors

class ECSCredentialsLifeCyclerHandlerSpec extends Specification {

  EcsProvider ecsProvider
  def objectMapper = new ObjectMapper()
  def registry = new DefaultRegistry()

  def setup() {
    ecsProvider = new EcsProvider()
  }
  def credOne = new NetflixECSCredentials(TestCredential.named('one'))
  def ecsAccountMapper = Mock(EcsAccountMapper)


  def 'it should add agents'() {

    given:
    def handler = new ECSCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)

    when:
    handler.credentialsAdded(credOne)

    then:
    ecsProvider.getAgents().size() == 23 // 2 * 12 - 1 ( One IamRoleCachingAgent per account )
    ecsProvider.getHealthAgents().size() == 4
  }

  def 'it should remove agents'() {

    given:
    ecsProvider.addAgents(Collections.singletonList(new TargetHealthCachingAgent(credOne, "region", null, null, objectMapper)))
    def handler = new ECSCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)

    when:
    handler.credentialsDeleted(credOne)

    then:
    ecsProvider.getAgents().isEmpty()
    ecsProvider.getHealthAgents().isEmpty()
  }

  def 'it should update agents'() {
    given:
    ecsProvider.addAgents(Collections.singletonList(new TargetHealthCachingAgent(credOne, "region", null, null, objectMapper)))
    def handler = new ECSCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)

    when:
    handler.credentialsUpdated(credOne)

    then:
    ecsProvider.getAgents().stream()
      .filter({ agent -> agent instanceof TargetHealthCachingAgent })
      .collect(Collectors.toList())
    .size() == 2
    ecsProvider.getHealthAgents().size() == 4
  }
}
