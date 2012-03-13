/*
 * Copyright 2011 Bill La Forge
 *
 * This file is part of AgileWiki and is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License (LGPL) as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 * or navigate to the following url http://www.gnu.org/licenses/lgpl-2.1.txt
 *
 * Note however that only Scala, Java and JavaScript files are being covered by LGPL.
 * All other files are covered by the Common Public License (CPL).
 * A copy of this license is also included and can be
 * found as well at http://www.opensource.org/licenses/cpl1.0.txt
 */
package org.agilewiki.jactor.lpc;

import org.agilewiki.jactor.*;
import org.agilewiki.jactor.apc.*;
import org.agilewiki.jactor.bind.JBActor;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsDestination;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsQueue;
import org.agilewiki.jactor.events.EventQueue;
import org.agilewiki.jactor.stateMachine.ExtendedResponseProcessor;
import org.agilewiki.jactor.stateMachine._SMBuilder;

import java.util.ArrayList;

/**
 * <p>
 * An actor which implements Local Procedure Calls (LPC)
 * and mostly works synchronously.
 * Actors need to implement the processRequest method.
 * </p>
 * <pre>
 * public class Multiply {
 *     public int a;
 *     public int b;
 * }
 *
 * public class Multiplier extends JLPCActor {
 *
 *     public Multiplier(Mailbox mailbox) {
 *         super(mailbox);
 *     }
 *
 *     protected void processRequest(Object req, RP rp)
 *             throws Exception {
 *         Multiply m = (Multiply) req;
 *         rp.process(new Integer(m.a * m.b));
 *     }
 * }
 * </pre>
 */
abstract public class JLPCActor implements Actor, RequestProcessor, RequestSource {
    /**
     * The type of actor.
     */
    private String actorType;

    /**
     * The inbox and outbox of the actor.
     */
    private Mailbox mailbox;

    /**
     * The current exception handler, or null.
     */
    private ExceptionHandler exceptionHandler;

    /**
     * The parent actor, for dependency injection.
     */
    private JBActor parent;

    /**
     * Returns the actor's parent.
     *
     * @return The actor's parent, or null.
     */
    @Override
    final public JBActor getParent() {
        return parent;
    }

    /**
     * Assign the parent actor.
     * Once assigned, it can not be changed.
     *
     * @param parent The parent actor.
     */
    @Override
    final public void setParent(JBActor parent) {
        if (this.parent != null)
            throw new UnsupportedOperationException("The parent can not be changed.");
        this.parent = parent;
    }

    /**
     * Returns the actor type.
     *
     * @return The actor type, or null.
     */
    @Override
    final public String getActorType() {
        return actorType;
    }

    /**
     * Assigns the actorType.
     * Once assigned, it can not be changed.
     *
     * @param actorType The actor type.
     */
    @Override
    final public void setActorType(String actorType) {
        if (this.actorType != null)
            throw new UnsupportedOperationException("The actorType can not be changed");
        this.actorType = actorType;
    }

    /**
     * Returns the exception handler.
     *
     * @return The exception handler.
     */
    @Override
    final public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Assign an exception handler.
     *
     * @param exceptionHandler The exception handler.
     */
    @Override
    final public void setExceptionHandler(final ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * A notification that there are incoming requests and responses that are ready for processing.
     */
    @Override
    final public void haveEvents() {
        mailbox.dispatchEvents();
    }

    /**
     * Process a wrapped request.
     *
     * @param request The wrapped request.
     * @throws Exception An exception thrown while processing the request.
     */
    @Override
    final public void processRequest(final JARequest request) throws Exception {
        if (request.isEvent())
            processRequest(request.getUnwrappedRequest(), request.getResponseProcessor());
        else processRequest(request.getUnwrappedRequest(), new RP() {
            @Override
            public void processResponse(Object unwrappedResponse) {
                JARequest old = mailbox.getCurrentRequest();
                mailbox.setCurrentRequest(request);
                mailbox.response(unwrappedResponse);
                mailbox.setCurrentRequest(old);
            }
        });
    }

    /**
     * Returns the actor's mailbox.
     *
     * @return The actor's mailbox.
     */
    @Override
    final public Mailbox getMailbox() {
        return mailbox;
    }

    /**
     * Enqueues the response in the responder's outbox.
     *
     * @param eventQueue   The responder's outbox.
     * @param japcResponse The wrapped response to be enqueued.
     */
    @Override
    final public void responseFrom(final BufferedEventsQueue<JAMessage> eventQueue,
                                   final JAResponse japcResponse) {
        eventQueue.send(mailbox, japcResponse);
    }

    /**
     * Sends a request to a mailbox.
     *
     * @param destination The mailbox which is to receive the request.
     * @param japcRequest The wrapped request to be sent.
     */
    @Override
    final public void send(final BufferedEventsDestination<JAMessage> destination,
                           final JARequest japcRequest) {
        mailbox.send(destination, japcRequest);
    }

    /**
     * Create a LiteActor
     *
     * @param mailbox A mailbox which may be shared with other actors.
     */
    public JLPCActor(final Mailbox mailbox) {
        if (mailbox == null) throw new IllegalArgumentException("mailbox may not be null");
        this.mailbox = mailbox;
    }

    /**
     * Set the initial capacity for buffered outgoing messages.
     *
     * @param initialBufferCapacity The initial capacity for buffered outgoing messages.
     */
    @Override
    final public void setInitialBufferCapacity(final int initialBufferCapacity) {
        mailbox.setInitialBufferCapacity(initialBufferCapacity);
    }

    /**
     * Wraps and enqueues an unwrapped request in the requester's inbox.
     *
     * @param apcRequestSource The originator of the request.
     * @param request          The request to be sent.
     * @param rp               The request processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    @Override
    final public void acceptRequest(final APCRequestSource apcRequestSource,
                                    final Object request,
                                    final RP rp)
            throws Exception {
        RequestSource rs = (RequestSource) apcRequestSource;
        ExceptionHandler sourceExceptionHandler = rs.getExceptionHandler();
        Mailbox sourceMailbox = rs.getMailbox();
        if (sourceMailbox == mailbox) {
            syncSend(rs, request, rp, sourceExceptionHandler);
            return;
        }
        if (sourceMailbox == null) {
            asyncSend(rs, request, rp, sourceExceptionHandler);
            return;
        }
        EventQueue<ArrayList<JAMessage>> eventQueue = mailbox.getEventQueue();
        EventQueue<ArrayList<JAMessage>> srcController = sourceMailbox.getEventQueue().getController();
        if (eventQueue.getController() == srcController) {
            syncSend(rs, request, rp, sourceExceptionHandler);
            return;
        }
        if (!eventQueue.acquireControl(srcController)) {
            asyncSend(rs, request, rp, sourceExceptionHandler);
            return;
        }
        try {
            syncSend(rs, request, rp, sourceExceptionHandler);
        } finally {
            mailbox.dispatchEvents();
            mailbox.sendPendingMessages();
            eventQueue.relinquishControl();
        }
    }

    /**
     * Process an exception when the response is asynchronous.
     *
     * @param ex            Any exceptions thrown while processing the request or response.
     * @param eh            The exception handler
     * @param sourceMailbox The mailbox of the source actor.
     */
    final private void asyncException(Exception ex, ExceptionHandler eh, Mailbox sourceMailbox) {
        if (eh == null) sourceMailbox.response(ex);
        else try {
            eh.process(ex);
        } catch (Exception ex2) {
            sourceMailbox.response(ex2);
        }
    }

    /**
     * Process a request asynchronously.
     *
     * @param rs                     The source of the request.
     * @param request                The request.
     * @param rp                     Processes the response.
     * @param sourceExceptionHandler Exception handler of the source actor.
     */
    final private void asyncSend(final RequestSource rs,
                                 final Object request,
                                 final RP rp,
                                 final ExceptionHandler sourceExceptionHandler) {
        RP rp1 = rp;
        if (!rp.isEvent()) rp1 = new RP() {
            @Override
            public void processResponse(Object response) throws Exception {
                rs.setExceptionHandler(sourceExceptionHandler);
                if (response != null && response instanceof Exception) {
                    asyncException(
                            (Exception) response,
                            rs.getExceptionHandler(),
                            rs.getMailbox());
                } else try {
                    rp.processResponse(response);
                } catch (Exception ex) {
                    asyncException(ex, rs.getExceptionHandler(), rs.getMailbox());
                }
            }
        };
        final JARequest jaRequest = new JARequest(
                rs,
                this,
                request,
                rp1);
        rs.send(mailbox, jaRequest);
    }

    /**
     * Process a request from another mailbox synchronously.
     *
     * @param rs                     The source of the request.
     * @param request                The request.
     * @param rp                     Processes the response.
     * @param sourceExceptionHandler Exception handler of the source actor.
     */
    final private void syncSend(final RequestSource rs,
                                final Object request,
                                final RP rp,
                                final ExceptionHandler sourceExceptionHandler)
            throws Exception {
        if (rp.isEvent()) {
            try {
                processRequest(request, rp);
            } catch (Exception ex) {
            }
            setExceptionHandler(sourceExceptionHandler);
            return;
        }
        final ExtendedResponseProcessor erp = new ExtendedResponseProcessor() {
            @Override
            public void processResponse(final Object response)
                    throws Exception {
                setExceptionHandler(sourceExceptionHandler);
                if (!async) {
                    sync = true;
                    try {
                        rp.processResponse(response);
                    } catch (Exception e) {
                        throw new TransparentException(e);
                    }
                } else {
                    if (response != null && response instanceof Exception)
                        asyncException((Exception) response, rs.getExceptionHandler(), rs.getMailbox());
                    else try {
                        Mailbox sourceMailbox = rs.getMailbox();
                        EventQueue<ArrayList<JAMessage>> sourceEventQueue = sourceMailbox.getEventQueue();
                        EventQueue<ArrayList<JAMessage>> srcController = sourceEventQueue.getController();
                        EventQueue<ArrayList<JAMessage>> eventQueue = mailbox.getEventQueue();
                        EventQueue<ArrayList<JAMessage>> controller = eventQueue.getController();
                        if (srcController == controller) {
                            rp.processResponse(response);
                        } else if (!eventQueue.acquireControl(srcController)) {
                            asyncResponse(rs, request, response, rp);
                        } else {
                            try {
                                rp.processResponse(response);
                            } finally {
                                mailbox.dispatchEvents();
                                mailbox.sendPendingMessages();
                                eventQueue.relinquishControl();
                            }
                        }
                    } catch (Exception ex) {
                        asyncException(ex, rs.getExceptionHandler(), rs.getMailbox());
                    }
                }
            }
        };
        try {
            processRequest(request, erp);
            if (!erp.sync) erp.async = true;
        } catch (TransparentException t) {
            setExceptionHandler(sourceExceptionHandler);
            throw (Exception) t.getCause();
        } catch (Exception e) {
            setExceptionHandler(sourceExceptionHandler);
            ExceptionHandler eh = getExceptionHandler();
            if (eh == null) throw e;
            eh.process(e);
        }
        setExceptionHandler(sourceExceptionHandler);
    }

    /**
     * Respond asynchronously to a synchronous request.
     *
     * @param rs       The source of the request.
     * @param request  The request.
     * @param response The response.
     * @param rp       Processes the response.
     */
    final private void asyncResponse(RequestSource rs,
                                     Object request,
                                     Object response,
                                     RP rp) {
        final JARequest jaRequest = new JARequest(
                rs,
                this,
                request,
                rp);
        mailbox.setCurrentRequest(jaRequest);
        mailbox.response(response);
    }

    /**
     * Send a request to another actor.
     *
     * @param actor   The target actor.
     * @param request The request.
     * @param rp      The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    final protected void send(final Actor actor,
                              final Object request,
                              final RP rp)
            throws Exception {
        actor.acceptRequest(this, request, rp);
    }

    /**
     * Send a request to another actor and discard any response.
     *
     * @param actor   The target actor.
     * @param request The request.
     */
    final protected void sendEvent(Actor actor, Object request) {
        try {
            send(actor, request, JANoResponse.nrp);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Unexpected exception", ex);
        }
    }

    /**
     * Creates a _SMBuilder.
     */
    final public class SMBuilder extends _SMBuilder {
        @Override
        final public void send(Actor actor, Object request, RP rp)
                throws Exception {
            JLPCActor.this.send(actor, request, rp);
        }
    }

    /**
     * Returns the mailbox factory.
     *
     * @return The mailbox factory.
     */
    final protected MailboxFactory getMailboxFactory() {
        return mailbox.getMailboxFactory();
    }

    /**
     * Returns true when the concurrent data of the actor, or its parent, contains the named data item.
     *
     * @param name The key for the data item.
     * @return True when the concurrent data of the actor, or its parent, contains the named data item.
     */
    @Override
    final public boolean hasDataItem(String name) {
        return false;
    }

    /**
     * The application method for processing requests sent to the actor.
     *
     * @param request A request.
     * @param rp      The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    abstract protected void processRequest(Object request, RP rp)
            throws Exception;
}
