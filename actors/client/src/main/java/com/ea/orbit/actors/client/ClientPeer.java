/*
 Copyright (C) 2015 Electronic Arts Inc.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1.  Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2.  Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
     its contributors may be used to endorse or promote products derived
     from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.ea.orbit.actors.client;

import com.ea.orbit.actors.client.streams.ClientSideStreamProxyImpl;
import com.ea.orbit.actors.extensions.DefaultLoggerExtension;
import com.ea.orbit.actors.extensions.LoggerExtension;
import com.ea.orbit.actors.peer.Peer;
import com.ea.orbit.actors.runtime.BasicRuntime;
import com.ea.orbit.actors.runtime.DefaultHandlers;
import com.ea.orbit.actors.runtime.Messaging;
import com.ea.orbit.actors.runtime.RemoteClient;
import com.ea.orbit.actors.runtime.SerializationHandler;
import com.ea.orbit.actors.streams.AsyncStream;
import com.ea.orbit.concurrent.Task;
import com.ea.orbit.container.Startable;

import org.slf4j.Logger;

import static com.ea.orbit.async.Await.await;

/**
 * This works as a bridge to perform calls between the server and a client.
 */
public class ClientPeer extends Peer implements BasicRuntime, Startable, RemoteClient
{
    private Messaging messaging;
    private ClientSideStreamProxyImpl clientSideStreamProxy;
    private LoggerExtension loggerExtension;
    private Logger logger;

    public ClientPeer()
    {
        bind();
    }

    public Task<Void> cleanup()
    {
        await(messaging.cleanup());
        return Task.done();
    }

    @Override
    public Task<Void> start()
    {
        if (loggerExtension == null)
        {
            loggerExtension = getFirstExtension(LoggerExtension.class);
            if (loggerExtension == null)
            {
                loggerExtension = new DefaultLoggerExtension();
            }
        }
        logger = loggerExtension.getLogger(this);
        ClientPeerExecutor executor = new ClientPeerExecutor();
        executor.setObjects(objects);
        executor.setRuntime(this);
        getPipeline().addLast(DefaultHandlers.EXECUTION, executor);
        messaging = new Messaging();
        messaging.setRuntime(this);
        clientSideStreamProxy = new ClientSideStreamProxyImpl();
        clientSideStreamProxy.setRuntime(this);

        getPipeline().addLast(DefaultHandlers.MESSAGING, messaging);
        getPipeline().addLast(DefaultHandlers.SERIALIZATION, new SerializationHandler(this, getMessageSerializer()));
        getPipeline().addLast(DefaultHandlers.NETWORK, getNetwork());
        installPipelineExtensions();

        await(getPipeline().connect(null));
        await(clientSideStreamProxy.start());
        return Task.done();
    }

    @Override
    public Task<Void> stop()
    {
        await(clientSideStreamProxy.stop());
        await(getPipeline().close());
        return Task.done();
    }

    @Override
    public <T> AsyncStream<T> getStream(final String provider, final Class<T> dataClass, final String streamId)
    {
        return clientSideStreamProxy.getStream(provider, dataClass, streamId);
    }

    @Override
    public Logger getLogger(final Object target)
    {
        return loggerExtension.getLogger(target);
    }

    @Override
    public String toString()
    {
        return "ClientPeer{localIdentity=" + localIdentity + "}";
    }
}
