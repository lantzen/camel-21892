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
package org.apache.camel.processor.throttle;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionHalfOpenHandler;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x (see CAMEL-21438)")
public class ThrottlingExceptionRoutePolicyHalfOpenHandlerSedaTest extends ContextTestSupport {
    private static final Logger log = LoggerFactory.getLogger(ThrottlingExceptionRoutePolicyHalfOpenHandlerSedaTest.class);

    private final String url = "seda:foo?concurrentConsumers=2";
    private MockEndpoint result;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");

        context.getShutdownStrategy().setTimeout(1);
    }

    @Test
    public void testHalfOpenCircuit() throws Exception {
        result.expectedMessageCount(2);
        List<String> bodies = Arrays.asList("Message One", "Message Two");
        result.expectedBodiesReceivedInAnyOrder(bodies);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                String msg = exchange.getIn().getBody(String.class);
                exchange.setException(new ThrottlingException(msg));
            }
        });

        // send two messages which will fail
        sendMessage("Message One");
        sendMessage("Message Two");

        final ServiceSupport consumer = (ServiceSupport) context.getRoute("foo").getConsumer();

        // wait long enough to have the consumer suspended
        await().atMost(2, TimeUnit.SECONDS).until(consumer::isSuspended);

        // send more messages
        // but should get there (yet)
        // due to open circuit
        // SEDA will queue it up
        log.debug("sending message three");
        sendMessage("Message Three");

        assertMockEndpointsSatisfied();

        result.reset();
        result.expectedMessageCount(2);
        bodies = Arrays.asList("Message Three", "Message Four");
        result.expectedBodiesReceivedInAnyOrder(bodies);

        // wait long enough to have the consumer resumed
        await().atMost(2, TimeUnit.SECONDS).until(consumer::isStarted);

        // send message
        // should get through
        log.debug("sending message four");
        sendMessage("Message Four");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                int threshold = 2;
                long failureWindow = 30;
                long halfOpenAfter = 250;
                ThrottlingExceptionRoutePolicy policy
                        = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null);
                policy.setHalfOpenHandler(new AlwaysCloseHandler());

                from(url).routeId("foo").routePolicy(policy).log("${body}").to("log:foo?groupSize=10").to("mock:result");
            }
        };
    }

    public static class AlwaysCloseHandler implements ThrottlingExceptionHalfOpenHandler {

        @Override
        public boolean isReadyToBeClosed() {
            return true;
        }

    }

    protected void sendMessage(String bodyText) {
        try {
            template.sendBody(url, bodyText);
        } catch (Exception e) {
            log.debug("Error sending: {}", e.getCause().getMessage());
        }
    }
}
