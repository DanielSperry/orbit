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

import javax.inject.Inject;

import static com.ea.orbit.async.Await.await;

public class Session extends OrbitActor<Session.State> implements ISession
{
    public enum SessionState
    {
        LOCKED,
        CHOOSE_NAME,
        VERIFY_OWNER,
        PROVIDE_PASSWORD,
        LOGGED_IN
    }

    public static class State
    {
        public ObserverManager<ISessionObserver> observers = new ObserverManager<>();
        public SessionState sessionState = SessionState.LOCKED;
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
        switch(state().sessionState)
        {
            case CHOOSE_NAME:
            {
                await(sendMessage("\033[1;34m" + input));
                ICharacter character = IActor.getReference(ICharacter.class, input.toLowerCase());
                state().character = character;
                boolean registeredName = await(character.registerName(input));

                System.out.print(registeredName);


                if(registeredName)
                {
                    await(sendMessage(localized.nameRegistered));
                    state().sessionState = SessionState.PROVIDE_PASSWORD;
                }
                else
                {
                    await(sendMessage(localized.verifyOwnership));
                    state().sessionState = SessionState.VERIFY_OWNER;
                }

                return writeState();
            }

            case VERIFY_OWNER:
            {
                await(sendMessage("\033[1;34m" + input));
                if(input.equals("yes"))
                {
                    state().sessionState = SessionState.PROVIDE_PASSWORD;
                    await(sendMessage(localized.enterPassword));
                }
                else if(input.equals("no"))
                {
                    state().sessionState = SessionState.CHOOSE_NAME;
                    await(sendMessage(localized.chooseName));
                }

                return writeState();
            }

            case PROVIDE_PASSWORD:
            {
                boolean validPassword = await(state().character.verifyPassword(input));
                if(validPassword)
                {
                    state().sessionState = SessionState.LOGGED_IN;
                    await(sendMessage("login was ok"));
                }
                else
                {
                    await(sendMessage("\033[1;31m" + localized.invalidPassword));
                    state().sessionState = SessionState.CHOOSE_NAME;
                    await(sendMessage(localized.chooseName));
                }

                return writeState();
            }
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

        // Choose name
        state().sessionState = SessionState.CHOOSE_NAME;
        await(writeState());
        await(sendMessage(localized.chooseName));

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
