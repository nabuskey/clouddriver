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
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;

public class ECSBasicCredentialsLoader<
        T extends ECSCredentialsConfig.Account, U extends NetflixAmazonCredentials>
    extends BasicCredentialsLoader<T, U> {

  public ECSBasicCredentialsLoader(
      CredentialsDefinitionSource<T> definitionSource,
      CredentialsParser<T, U> parser,
      CredentialsRepository<U> credentialsRepository) {
    super(definitionSource, parser, credentialsRepository);
  }
}
