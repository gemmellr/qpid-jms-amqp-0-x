/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.client.transport;

import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.AMQException;

public class AMQNoTransportForProtocolException extends AMQTransportConnectionException
{
    BrokerDetails _details;

    public AMQNoTransportForProtocolException(BrokerDetails details)
    {
        this(details, "No Transport exists for specified broker protocol");
    }

    public AMQNoTransportForProtocolException(BrokerDetails details, String message)
    {
        super(message);

        _details = details;
    }

    public String toString()
    {
        if (_details != null)
        {
            return super.toString() + _details.toString();
        }
        else
        {
            return super.toString();
        }
    }
}
