/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.remote.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.kie.remote.RemoteStreamingEntryPoint;
import org.kie.remote.RemoteStreamingKieSession;
import org.kie.remote.TopicsConfig;
import org.kie.remote.impl.consumer.Listener;
import org.kie.remote.impl.producer.Sender;

import static org.kie.remote.impl.RemoteKieSessionImpl.DEFAULT_ENTRY_POINT;

public class RemoteStreamingKieSessionImpl extends RemoteStreamingEntryPointImpl implements RemoteStreamingKieSession {

    private final Map<String, RemoteStreamingEntryPoint> entryPoints = new HashMap<>();

    public RemoteStreamingKieSessionImpl(Properties configuration) {
        this(configuration, TopicsConfig.getDefaultTopicsConfig());
    }

    public RemoteStreamingKieSessionImpl( Properties configuration, TopicsConfig envConfig) {
        super(new Sender(configuration), DEFAULT_ENTRY_POINT, envConfig, new Listener(configuration));
        sender.start();
        // a streaming session is fireUntilHalt by default
        fireUntilHalt();
    }

    @Override
    public void close() {
        sender.stop();
        delegate.stop();
    }

    @Override
    public RemoteStreamingEntryPoint getEntryPoint( String name ) {
        return entryPoints.computeIfAbsent( name, k -> new RemoteStreamingEntryPointImpl(sender, k, topicsConfig, delegate) );
    }

    @Override
    public CompletableFuture<Long> fireAllRules() {
        return delegate.fireAllRules();
    }

    @Override
    public void fireUntilHalt() {
        delegate.fireUntilHalt();
    }

    @Override
    public void halt() {
        delegate.halt();
    }
}
