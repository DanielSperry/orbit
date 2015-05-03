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
import com.ea.orbit.actors.ObserverManager;
import com.ea.orbit.actors.runtime.OrbitActor;
import com.ea.orbit.concurrent.Task;
import com.ea.orbit.samples.adventure.utils.LocalizedData;
import com.ea.orbit.samples.adventure.utils.MessageParser;

import javax.inject.Inject;

import static com.ea.orbit.async.Await.await;

public class Session extends OrbitActor<Session.State> implements ISession
{
    public enum SessionState
    {
        UNAUTHED,
        AUTHED
    }

    public static class State
    {
        public ObserverManager<ISessionObserver> observers = new ObserverManager<>();
        public SessionState sessionState = SessionState.UNAUTHED;
        public ICharacter character = null;
    }

    @Inject
    private LocalizedData localized;


    @Override
    public Task activateAsync()
    {
        // We don't care about the result of this
        state().observers.cleanup();

        return super.activateAsync();
    }

    @Override
    public Task processInput(String input)
    {
        MessageParser parser = new MessageParser(input);

        switch (state().sessionState)
        {
            case UNAUTHED:
            {
                if(parser.getVerb().equals("login"))
                {
                    if(parser.getArgumentCount() >= 2)
                    {
                        String username = parser.getPart(1);
                        String password = parser.getPart(2);

                        boolean loginOk = await(IActor.getReference(ICharacter.class, username.toLowerCase()).verifyPassword(password));

                        if(loginOk)
                        {
                            state().sessionState = SessionState.AUTHED;
                            await(writeState());
                            sendMessage("TODO: Enter the game here");
                        }
                        else
                        {
                            await(sendMessage(localized.invalidCredentials));
                        }
                    }
                    else
                    {
                        await(sendMessage(localized.loginHelp));
                    }
                }
                else if(parser.getVerb().equals("register"))
                {
                    if (parser.getArgumentCount() >= 2)
                    {
                        String username = parser.getPart(1);
                        String password = parser.getPart(2);

                        boolean registered = await(IActor.getReference(ICharacter.class, username.toLowerCase()).registerCharacter(username, password));
                        if(registered)
                        {
                            state().sessionState = SessionState.AUTHED;
                            await(writeState());
                            sendMessage("TODO: Enter the game here");
                        }
                        else
                        {

                        }
                    }
                }
            }
            break;

            case AUTHED:
            {

            }
            break;
        }

        return Task.done();
    }

    @Override
    public Task addObserver(ISessionObserver observer)
    {
        state().observers.addObserver(observer);
        return writeState();
    }

    @Override
    public Task beginSession()
    {
        // Send greeting
        await(sendMessage(localized.greetingMessage + "\n"));

        // Login help
        await(sendMessage(localized.loginHelp));

        return Task.done();
    }

    @Override
    public Task endSession()
    {
        return clearState();
    }

    @Override
    public Task sendMessage(String message)
    {
        state().observers.notifyObservers(o -> o.serverMessage(message));
        return Task.done();
    }
}
