package org.agilewiki.jactor.pubsub.publish;

import junit.framework.TestCase;
import org.agilewiki.jactor.*;
import org.agilewiki.jactor.lpc.Request;
import org.agilewiki.jactor.pubsub.actorName.SetActorName;
import org.agilewiki.jactor.pubsub.publisher.*;
import org.agilewiki.jactor.pubsub.subscriber.JASubscriber;
import org.agilewiki.jactor.pubsub.subscriber.Subscriber;

/**
 * Test code.
 */
public class PublishTest extends TestCase {
    public void test() {
        MailboxFactory mailboxFactory = JAMailboxFactory.newMailboxFactory(1);
        try {
            JAFuture future = new JAFuture();
            Mailbox mailbox = mailboxFactory.createMailbox();
            Req req = new Req();
            Publish publish = new Publish(req);
            int c = 0;
            Subscriber s = new Sub(mailbox);
            (new SetActorName("foo")).call(s);
            Publisher p = new JAPublisher(mailbox);
            c = publish.send(future, p);
            assertEquals(0, c);
            (new Subscribe(s)).send(future, p);
            c = publish.send(future, p);
            assertEquals(1, c);
            (new Unsubscribe("foo")).send(future, p);
            c = publish.send(future, p);
            assertEquals(0, c);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mailboxFactory.close();
        }
    }
}

class Sub extends JASubscriber {
    Sub(Mailbox mailbox) {
        super(mailbox);
    }

    @Override
    protected void processRequest(Object request, RP rp) throws Exception {
        if (request.getClass() == Req.class) {
            rp.processResponse(null);
            return;
        }

        super.processRequest(request, rp);
    }
}

class Req extends Request<Object, Sub> {

    @Override
    public boolean isTargetType(Actor targetActor) {
        return targetActor instanceof Sub;
    }
}