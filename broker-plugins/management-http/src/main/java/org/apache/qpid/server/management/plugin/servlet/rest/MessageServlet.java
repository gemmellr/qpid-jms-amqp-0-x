/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageDeletedException;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.ClearQueueTransaction;
import org.apache.qpid.server.queue.DeleteMessagesTransaction;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;

public class MessageServlet extends AbstractServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageServlet.class);

    public MessageServlet()
    {
        super();
    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        String[] pathInfoElements = getPathInfoElements(request);
        if(pathInfoElements != null && pathInfoElements.length > 2)
        {
            getMessageContent(request, response);
        }
        else
        {
            getMessageList(request, response);
        }

    }

    private void getMessageContent(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        Queue queue = getQueueFromRequest(request);
        String path[] = getPathInfoElements(request);
        MessageFinder messageFinder = new MessageFinder(Long.parseLong(path[2]));
        queue.visit(messageFinder);

        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);
        response.setContentType("application/json");

        writeObjectToResponse(messageFinder.getMessageObject(), request, response);
    }

    private void getMessageList(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        Queue queue = getQueueFromRequest(request);

        int first = -1;
        int last = -1;
        String range = request.getHeader("Range");
        if(range != null)
        {
            String[] boundaries = range.split("=")[1].split("-");
            first = Integer.parseInt(boundaries[0]);
            last = Integer.parseInt(boundaries[1]);
        }
        final MessageCollector messageCollector = new MessageCollector(first, last);
        queue.visit(messageCollector);

        response.setContentType("application/json");
        final List<Map<String, Object>> messages = messageCollector.getMessages();
        int queueSize = (int) queue.getQueueDepthMessages();
        String min = messages.isEmpty() ? "0" : messages.get(0).get("position").toString();
        String max = messages.isEmpty() ? "0" : messages.get(messages.size()-1).get("position").toString();
        response.setHeader("Content-Range", (min + "-" + max + "/" + queueSize));
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        writeObjectToResponse(messages, request, response);
    }

    private Queue<?> getQueueFromRequest(HttpServletRequest request)
    {
        // TODO - validation that there is a vhost and queue and only those in the path

        String[] pathInfoElements = getPathInfoElements(request);
        if(pathInfoElements == null || pathInfoElements.length < 2)
        {
            throw new IllegalArgumentException("Invalid path is specified");
        }
        String vhostName = pathInfoElements[0];
        String queueName = pathInfoElements[1];

        VirtualHost<?,?,?> vhost = getBroker().findVirtualHostByName(vhostName);
        if (vhost == null)
        {
            throw new IllegalArgumentException("Could not find virtual host with name '" + vhostName + "'");
        }

        Queue queueFromVirtualHost = getQueueFromVirtualHost(queueName, vhost);
        if (queueFromVirtualHost == null)
        {
            throw new IllegalArgumentException("Could not find queue with name '" + queueName  + "' on virtual host '" + vhost.getName() + "'");
        }
        return queueFromVirtualHost;
    }

    private Queue getQueueFromVirtualHost(String queueName, VirtualHost<?,?,?> vhost)
    {
        Queue queue = null;

        for(Queue<?> q : vhost.getQueues())
        {

            if(q.getName().equals(queueName))
            {
                queue = q;
                break;
            }
        }
        return queue;
    }


    private class MessageCollector implements QueueEntryVisitor
    {
        private final int _first;
        private final int _last;
        private int _position = -1;
        private final List<Map<String, Object>> _messages = new ArrayList<Map<String, Object>>();

        private MessageCollector(int first, int last)
        {
            _first = first;
            _last = last;
        }


        public boolean visit(QueueEntry entry)
        {

            _position++;
            if((_first == -1 || _position >= _first) && (_last == -1 || _position <= _last))
            {
                final Map<String, Object> messageObject = convertToObject(entry, false);
                messageObject.put("position", _position);
                _messages.add(messageObject);
            }
            return _last != -1 && _position > _last;
        }

        public List<Map<String, Object>> getMessages()
        {
            return _messages;
        }
    }


    private class MessageFinder implements QueueEntryVisitor
    {
        private final long _messageNumber;
        private Map<String, Object> _messageObject;

        private MessageFinder(long messageNumber)
        {
            _messageNumber = messageNumber;
        }


        public boolean visit(QueueEntry entry)
        {
            ServerMessage message = entry.getMessage();
            if(message != null)
            {
                if(_messageNumber == message.getMessageNumber())
                {
                    try
                    {
                        MessageReference reference = message.newReference();
                        try
                        {
                            _messageObject = convertToObject(entry, true);
                        }
                        finally
                        {
                            reference.release();
                        }
                        return true;
                    }
                    catch (MessageDeletedException e)
                    {
                        // ignore - the message has been deleted before we got a chance to look at it
                    }
                }
            }
            return false;
        }

        public Map<String, Object> getMessageObject()
        {
            return _messageObject;
        }
    }

    private Map<String, Object> convertToObject(QueueEntry entry, boolean includeContent)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("size", entry.getSize());
        object.put("deliveryCount", entry.getDeliveryCount());
        object.put("state",entry.isAvailable()
                                   ? "Available"
                                   : entry.isAcquired()
                                             ? "Acquired"
                                             : "");
        final ConsumerImpl deliveredConsumer = entry.getDeliveredConsumer();
        object.put("deliveredTo", deliveredConsumer == null ? null : deliveredConsumer.getConsumerNumber());
        ServerMessage message = entry.getMessage();

        if(message != null)
        {
            convertMessageProperties(object, message);
            if(includeContent)
            {
                convertMessageHeaders(object, message);
            }
        }

        return object;
    }

    private void convertMessageProperties(Map<String, Object> object, ServerMessage message)
    {
        object.put("id", message.getMessageNumber());
        object.put("arrivalTime",message.getArrivalTime());
        object.put("persistent", message.isPersistent());

        final AMQMessageHeader messageHeader = message.getMessageHeader();
        if(messageHeader != null)
        {
            addIfPresent(object, "messageId", messageHeader.getMessageId());
            addIfPresentAndNotZero(object, "expirationTime", messageHeader.getExpiration());
            addIfPresent(object, "applicationId", messageHeader.getAppId());
            addIfPresent(object, "correlationId", messageHeader.getCorrelationId());
            addIfPresent(object, "encoding", messageHeader.getEncoding());
            addIfPresent(object, "mimeType", messageHeader.getMimeType());
            addIfPresent(object, "priority", messageHeader.getPriority());
            addIfPresent(object, "replyTo", messageHeader.getReplyTo());
            addIfPresentAndNotZero(object, "timestamp", messageHeader.getTimestamp());
            addIfPresent(object, "type", messageHeader.getType());
            addIfPresent(object, "userId", messageHeader.getUserId());
        }

    }

    private void addIfPresentAndNotZero(Map<String, Object> object, String name, Object property)
    {
        if(property instanceof Number)
        {
            Number value = (Number)property;
            if (value.longValue() != 0)
            {
                object.put(name, property);
            }
        }
    }

    private void addIfPresent(Map<String, Object> object, String name, Object property)
    {
        if(property != null)
        {
            object.put(name, property);
        }
    }

    private void convertMessageHeaders(Map<String, Object> object, ServerMessage message)
    {
        final AMQMessageHeader messageHeader = message.getMessageHeader();
        if(messageHeader != null)
        {
            Map<String, Object> headers = new HashMap<String,Object>();
            for(String headerName : messageHeader.getHeaderNames())
            {
                headers.put(headerName, messageHeader.getHeader(headerName));
            }
            object.put("headers", headers);
        }
    }

    /*
     * POST moves or copies messages to the given queue from a queue specified in the posted JSON data
     */
    @Override
    protected void doPostWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {

        try
        {
            final Queue<?> sourceQueue = getQueueFromRequest(request);

            ObjectMapper mapper = new ObjectMapper();

            @SuppressWarnings("unchecked")
            Map<String,Object> providedObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);

            String destQueueName = (String) providedObject.get("destinationQueue");
            Boolean move = (Boolean) providedObject.get("move");

            final VirtualHost<?,?,?> vhost = sourceQueue.getParent(VirtualHost.class);

            boolean isMoveTransaction = move != null && Boolean.valueOf(move);

            final Queue destinationQueue = getQueueFromVirtualHost(destQueueName, vhost);
            final List<Long> messageIds = new ArrayList<Long>((List<Long>) providedObject.get("messages"));

            if(isMoveTransaction)
            {
                sourceQueue.moveMessages(destinationQueue, messageIds);
            }
            else
            {
                sourceQueue.copyMessages(destinationQueue, messageIds);
            }

            response.setStatus(HttpServletResponse.SC_OK);

        }
        catch(AccessControlException e)
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Failure to perform message operation", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /*
     * DELETE removes specified messages from, or clears the queue
     */
    @Override
    protected void doDeleteWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        final Queue<?> queue = getQueueFromRequest(request);

        final VirtualHost<?,?,?> vhost = queue.getParent(VirtualHost.class);
        boolean clearQueue = Boolean.parseBoolean(request.getParameter("clear"));

        try
        {
            if (clearQueue)
            {
                clearQueue(queue, vhost);
            }
            else
            {
                final List<Long> messageIds = new ArrayList<>();
                for(String idStr : request.getParameterValues("id"))
                {
                    messageIds.add(Long.valueOf(idStr));
                }

                deleteMessages(queue, vhost, messageIds);
            }
            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch (AccessControlException e)
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

    }

    private void deleteMessages(final Queue<?> queue, final VirtualHost<?, ?, ?> vhost, final List<Long> messageIds)
    {
        // FIXME: added temporary authorization check until we introduce management layer
        // and review current ACL rules to have common rules for all management interfaces
        authorizeMethod("deleteMessages", vhost);
        vhost.executeTransaction(new DeleteMessagesTransaction(queue, messageIds));
    }

    private void clearQueue(final Queue<?> queue, final VirtualHost<?, ?, ?> vhost)
    {
        // FIXME: added temporary authorization check until we introduce management layer
        // and review current ACL rules to have common rules for all management interfaces
        authorizeMethod("clearQueue", vhost);
        vhost.executeTransaction(new ClearQueueTransaction(queue));
    }

    private void authorizeMethod(String methodName, VirtualHost<?,?,?> vhost)
    {
        SecurityManager securityManager = getBroker().getSecurityManager();
        securityManager.authoriseMethod(Operation.UPDATE, "VirtualHost.Queue", methodName, vhost.getName());
    }

}
