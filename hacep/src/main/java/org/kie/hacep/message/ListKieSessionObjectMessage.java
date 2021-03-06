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
package org.kie.hacep.message;

import java.io.Serializable;
import java.util.Collection;

import org.kie.remote.message.ResultMessage;

public class ListKieSessionObjectMessage extends AbstractMessage
        implements Serializable,
                   ResultMessage<Collection<? extends Object>> {

    private Collection<? extends Object> objects;

    /* Empty constructor for serialization */
    public ListKieSessionObjectMessage() {
    }

    public ListKieSessionObjectMessage(String id, Collection<? extends Object> objects) {
        super(id);
        this.objects = objects;
    }

    @Override
    public Collection<? extends Object> getResult() {
        return getObjects();
    }

    public Collection<? extends Object> getObjects() {
        return objects;
    }

    @Override
    public String toString() {
        return "ListKieSessionObjectMessage{" +
                "objects=" + objects +
                ", id='" + id + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
