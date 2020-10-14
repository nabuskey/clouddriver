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

package com.netflix.spinnaker.clouddriver.aws.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.InstanceCachingAgent
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentialsLifecycleHandler

class AmazonCredentialsLifecycleHandlerSpec extends Specification {
  @Shared
  def awsCleanupProvider = Spy(AwsCleanupProvider) {
    removeAgentsForAccounts(_) >> void
  }
  @Shared
  def awsInfrastructureProvider = Spy(AwsInfrastructureProvider) {
    removeAgentsForAccounts(_) >> void
  }
//  @Shared
//  def awsProvider = Mock(AwsProvider) {
//    removeAgentsForAccounts() >> void
//  }


  def 'it should replace current public image caching agent'() {
    def objectMapper = Mock(ObjectMapper) {
      enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) >> Mock(ObjectMapper)
    }
    def credOne = TestCredential.named('one')
    def credTwo = TestCredential.named('two')
    def imageCachingAgentOne = Mock(ImageCachingAgent) {
      handlesAccount("one") >> true
      getIncludePublicImages() >> true
      getRegion() >> "us-west-2"
    }
    def imageCachingAgentTwo = new ImageCachingAgent(null, credTwo, "us-west-2", objectMapper, null, false, null)
//    def imageCachingAgentTwo = Mock(ImageCachingAgent) {
//      handlesAccount("two") >> true
//      getIncludePublicImages() >> false
//    }
    AwsProvider awsProvider = GroovyMock(global: true) {
      removeAgentsForAccounts(_) >> void
      getAgents() >> [imageCachingAgentOne, imageCachingAgentTwo]
      getProviderName() >> "test"
    }
    def credentialsRepository = Mock(CredentialsRepository) {
      getAll() >> [credOne, credTwo]
    }
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      null, null, null, null, null, null, null, null, null, null, null, null, null, null,
      credentialsRepository)

    when:
    handler.credentialsDeleted(credOne)

    then:
    imageCachingAgentTwo.includePublicImages
  }

}
