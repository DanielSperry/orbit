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

package com.ea.orbit.samples.adventure;

import com.ea.orbit.actors.IActor;
import com.ea.orbit.concurrent.Task;

import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@ServerEndpoint("/adventure")
public class AdventureWebSocket
{
    private ISession adventureSession;
    private ISessionObserver sessionObserver;

    @OnOpen
    public void onWebSocketConnect(Session session)
    {
        // Create a random new session
        adventureSession = IActor.getReference(ISessionManager.class).createNewSession().join();

        // Create the observer
        sessionObserver = new ISessionObserver()
        {
            @Override
            public Task serverMessage(final String message)
            {
                JsonObject jsonObject = Json.createObjectBuilder()
                        .add("message", message)
                        .build();

                session.getAsyncRemote().sendObject(jsonObject.toString());
                return Task.done();
            }
        };

        adventureSession.addObserver(sessionObserver).join();

        // Begin session
        adventureSession.beginSession().join();
    }

    @OnMessage
    public void onWebSocketText(String jsonMessage, Session session)
    {
        JsonObject jsonObject = Json.createReader(new StringReader(jsonMessage)).readObject();
        String useInput = jsonObject.getString("userInput");
        adventureSession.processInput(useInput);
    }

    @OnClose
    public void onWebSocketClose(CloseReason reason)
    {
        IActor.getReference(ISessionManager.class).destroySession(adventureSession);
    }

    @OnError
    public void onWebSocketError(Throwable cause)
    {
        IActor.getReference(ISessionManager.class).destroySession(adventureSession);
    }
}