/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedInterceptFromTest extends ManagementTestSupport {

    @Test
    public void testManageWithInterceptFrom() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(1);
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.setExpectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                interceptFrom().onWhen(simple("${header.foo} == '123'")).to("mock:intercepted");
                from("direct:start").to("mock:foo");
            }
        };
    }

}
