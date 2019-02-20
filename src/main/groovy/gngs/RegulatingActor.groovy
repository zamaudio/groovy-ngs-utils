package gngs

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.FromAbstractTypeMethods
import groovy.transform.stc.SimpleType
import groovy.util.logging.Log
import groovyx.gpars.MessagingRunnable
import groovyx.gpars.actor.Actor
import groovyx.gpars.actor.Actors
import groovyx.gpars.actor.DefaultActor
import groovyx.gpars.actor.impl.MessageStream

import java.util.concurrent.atomic.AtomicInteger

import gngs.pair.Shuffler

@CompileStatic
class AcknowledgeableMessage {
    
    AcknowledgeableMessage(Object payload, AtomicInteger counter) {
        this.payload = payload
        this.acknowledgeCounter = counter
    }
    
    Object payload
    
    AtomicInteger acknowledgeCounter
}

/**
 * An Actor that exerts back-pressure to regulate the rate of incoming messages.
 * <p>
 * The regulating actor counts how many messages are pending vs how many it has processed.
 * If the number of pending messages exceeds a soft threshold, it blocks for a short time
 * before queueing the next message. If the number of messages exceeds a hard threshold,
 * it blocks for a long time before queuing the message. This allows effective control over how
 * much memory the actor is using in its queue.
 * <p>
 * The RegulatingActor tracks how many messages are pending by incremenging a counter
 * ({@link #pendingMessages}). A user of a RegulatingActor should therefore send messages to it 
 * using the {@link #sendTo} method, so that this counter can be incremented.
 * <p>
 * <b>Stopping a RegulatingActor</b>
 * Although they can be terminated the usual way (the <code>terminate()</code> method), an 
 * orderly shutdown that enqueues a stop message (poison pill) that is automatically 
 * processed can be initiated by using the {@link #sendStop()} method
 * 
 * <p>
 * Sometimes you may want a 'per-client' pending count; that is you don't want one over-zealous
 * producer to block other producers. This can be especially important if there are dependencies 
 * such that blocking some producers could result in a deadlock. This can be implemented
 * by supplying your own pendingCount instead of using the shared one. To do that, 
 * you can use the {@link #send(AcknowledgeableMessage message)} method, where you construct the
 * {@link AcknowledgeableMessage} yourself and supply it with your own counter.
 * <p>
 * Since chaining together regulated actors is a common pattern, this class provides support for a
 * default "downstream". If the downstream actor is supplied in the constructor, you can send 
 * messages to it that are automatically per-client limited using the {@link #sendDownstream} 
 * method.
 * 
 * @author Simon Sadedin
 *
 * @param <T> the type of the messages to be processed
 */
@Log
abstract class RegulatingActor<T> extends DefaultActor implements Runnable {
    
    private final int softLimit
    
    private final int hardLimit
    
    /**
     * Count of messages that have been sent to this actor but not processed
     */
    final AtomicInteger pendingMessages = new AtomicInteger(0)
    
    boolean stopped = false
    
    /**
     * Count of messages pending in downstream due to this actor
     */
    final AtomicInteger downstreamCounter = new AtomicInteger(0)
    
    public RegulatingActor(RegulatingActor downstream, int softLimit, int hardLimit) {
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
        this.downstream = downstream;
    }
    

    public RegulatingActor(int softLimit, int hardLimit) {
        super();
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
    }

    /**
     * Create a RegulatingActor with basic defaults for the queue sizes that suit many tasks
     */
    public RegulatingActor() {
        this(1000,5000)
    }

    final RegulatingActor downstream
    
    ProgressCounter progress 
    
    boolean verbose = true
    
    final static Object STOP = new Object()
    
    @CompileStatic
    void doTerminate() {
        terminate()
    }
    
    @Override
    public void run() {
        react(new MessagingRunnable<Object>(this) {
            @CompileStatic
            @Override
            final void doRun(final Object msg) {
                if(msg.is(STOP)) {
                    stopped = true
                    RegulatingActor.this.onEnd()
                    if(progress != null)
                        progress.end()
                    doTerminate()
                }
                else {
                    if(progress != null)
                        progress.count()
                    AcknowledgeableMessage am = (AcknowledgeableMessage)msg
                    RegulatingActor.this.process((T)am.payload)
                    pendingMessages.decrementAndGet()
                    am.acknowledgeCounter.decrementAndGet()
                }                
            }
        })
    }
    
    @Override
    @CompileStatic
    void act() {
        loop(this)
    }
    
    void onEnd() {
    }
    
    /**
     * Send a pre-determined STOP message to the actor, which when receieved, will cause
     * it to call terminate().
     * <p>
     * This method will return immediately. To wait for the stop to occur, call the
     * <code>join()</code> method.
     */
    @CompileStatic
    void sendStop() {
        this << RegulatingActor.STOP
    }
    
    abstract void process(T message)
    
    long throttleWarningMs = 0
    
    @CompileStatic
    void sendDownstream(Object message) {
        if(!this.downstream.is(null))
            this.downstream.send(new AcknowledgeableMessage(message, this.downstreamCounter))
    }
    
    @CompileStatic
    public MessageStream sendTo(T o) {
        this.send(new AcknowledgeableMessage(o, this.pendingMessages))
    }
    
    @CompileStatic
    public MessageStream send(AcknowledgeableMessage message) {
        sendLimited(message)
    }
    
    boolean throttled = false
    
    @CompileStatic
    public MessageStream sendLimited(AcknowledgeableMessage message, boolean throttle=true) {
        
        if(message.acknowledgeCounter.get() > softLimit) {
            throttled = true
            long nowMs = System.currentTimeMillis()
            if(nowMs - throttleWarningMs > 30000) {
                if(verbose && (this.downstream == null || !this.downstream.throttled))
                    log.info "Soft throttling messages to $this (pending=$message.acknowledgeCounter/$softLimit))"
                throttleWarningMs = nowMs
            }
            if(throttle)
                Thread.sleep(50)
        }
        else {
            throttled = false
        }
            
        while(message.acknowledgeCounter.get() > hardLimit) {
            long nowMs = System.currentTimeMillis()
            throttled = true
            if(verbose) {
                if(nowMs - throttleWarningMs > 30000) {
                    log.info "Hard throttling messages to $this due to congestion (pending=$message.acknowledgeCounter/$hardLimit))"
                    throttleWarningMs = nowMs
                }
            }
                
            if(throttle)
                Thread.sleep(3000)
        }
        
        message.acknowledgeCounter.incrementAndGet()
        this.pendingMessages.incrementAndGet()
        super.send(message)
    }
    
    @CompileStatic
    static <T> RegulatingActor<T> actor(@ClosureParams(value=FromAbstractTypeMethods) Closure c) {
        RegulatingActor ds = new RegulatingActor<T>() {
            void process(T value) {
                c(value)
            }
        }
        return ds
    }
}
