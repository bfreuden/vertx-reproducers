import io.vertx.ext.mongo.impl.PublisherAdapter;
import io.vertx.core.streams.impl.InboundBuffer;

public aspect PublisherAdapterAspect {

    int printLimit = 100;
    boolean lastMessageWritten = false;

    pointcut handleIn() :
     call(void PublisherAdapter.handleIn(Object));

    before() : handleIn() {
        if (printLimit-- > 0)
            System.out.println("[PublisherAdapter] handleIn called");
        else if (!lastMessageWritten) {
            lastMessageWritten = true;
            System.out.println("[PublisherAdapter] stopping logging handleIn & requestMore calls");
        }
    }

    pointcut handleOut() :
     call(void PublisherAdapter.handleOut(Object));

    before() : handleOut() {
        if (printLimit-- > 0)
            System.out.println("[PublisherAdapter] handleOut called");
        else if (!lastMessageWritten) {
            lastMessageWritten = true;
            System.out.println("[PublisherAdapter] stopping logging handleIn & requestMore calls");
        }
    }

    pointcut inboundBufferFetch() :
     within(PublisherAdapter) && call(boolean InboundBuffer.fetch(long));

    after() returning(boolean result): inboundBufferFetch() {
        if (result)
            System.out.println("[PublisherAdapter] InboundBuffer fetch drained!!");
    }

    pointcut inboundBufferPause() :
     within(PublisherAdapter) && call(InboundBuffer InboundBuffer.pause());

    before(): inboundBufferPause() {
        System.out.println("[PublisherAdapter] InboundBuffer pause");
    }

    pointcut inboundBufferResume() :
     within(PublisherAdapter) && call(InboundBuffer InboundBuffer.resume());

    before(): inboundBufferResume() {
        System.out.println("[PublisherAdapter] InboundBuffer resume");
    }

    pointcut requestMore() :
     call(void PublisherAdapter.requestMore());

    before() : requestMore() {
        if (printLimit-- > 0)
            System.out.println("[PublisherAdapter] requestMore called");
        else if (!lastMessageWritten) {
            lastMessageWritten = true;
            System.out.println("[PublisherAdapter] stopping logging handleIn & requestMore calls");
        }
    }



}