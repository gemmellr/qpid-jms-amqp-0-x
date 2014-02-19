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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;

public class PriorityQueue extends OutOfOrderQueue<PriorityQueueList.PriorityQueueEntry, PriorityQueue, PriorityQueueList>
{

    public static final int DEFAULT_PRIORITY_LEVELS = 10;

    protected PriorityQueue(VirtualHost virtualHost,
                            Map<String, Object> attributes)
    {
        super(virtualHost, attributes, entryList(attributes));
    }

    private static PriorityQueueList.Factory entryList(final Map<String, Object> attributes)
    {
        final Integer priorities = MapValueConverter.getIntegerAttribute(Queue.PRIORITIES, attributes,
                                                                         DEFAULT_PRIORITY_LEVELS);

        return new PriorityQueueList.Factory(priorities);
    }

    public int getPriorities()
    {
        return getEntries().getPriorities();
    }
}
