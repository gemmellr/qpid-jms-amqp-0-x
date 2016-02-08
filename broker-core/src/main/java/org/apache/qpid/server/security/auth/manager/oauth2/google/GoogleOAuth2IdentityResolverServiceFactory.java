/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.server.security.auth.manager.oauth2.google;

import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2AuthenticationProvider;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2IdentityResolverService;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2IdentityResolverServiceFactory;

@PluggableService
public class GoogleOAuth2IdentityResolverServiceFactory implements OAuth2IdentityResolverServiceFactory
{
    public static final String TYPE = "GoogleUserInfo";

    @Override
    public OAuth2IdentityResolverService createIdentityResolverService(final OAuth2AuthenticationProvider authenticationProvider)
    {
        return new GoogleOAuth2IdentityResolverService(authenticationProvider);
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
