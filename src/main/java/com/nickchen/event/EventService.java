package com.nickchen.event;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 事件系统
 * 1. 注册事件
 * 2. 执行事件 handler
 * 3. 执行事件完成 callback
 */
public class EventService {

    private static EventService instance = new EventService();

    static {
        init();
    }
    private ConcurrentHashMap<String, List<EventHandler>> eventHandlers = new ConcurrentHashMap<>();;

    private ConcurrentHashMap<String, EventConsumedCallback> eventCallbacks = new ConcurrentHashMap<>();

    private ConcurrentLinkedDeque<String> events = new ConcurrentLinkedDeque<>();

    private ThreadPoolExecutor callbackExecutePool = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1024), new NamedThreadFactory("event-callback-%d"));

    private ThreadPoolExecutor handlersExecutePool = new ThreadPoolExecutor(5, 10,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024), new NamedThreadFactory("event-handler-%d"));

    private Thread consumeEventThread;

    private volatile boolean stopConsumeEvent;


    private final AtomicReference<ServiceStatus> status = new AtomicReference<>(ServiceStatus.NOT_START);

    /**
     * TODO 可以修改线程池配置
     */
    public static void init() {
        Preconditions.checkState(instance.status.get() == ServiceStatus.NOT_START, "EventService already start.");
        if (!instance.status.compareAndSet(ServiceStatus.NOT_START, ServiceStatus.START)) {
            throw new IllegalStateException("EventService already started.");
        }
        instance.consumeEventThread = new Thread(() -> {
            instance.consumeEvent();
        });

        instance.consumeEventThread.start();


        System.out.println("init");
    }

    /**
     * 循环扫描事件队列
     */
    private void consumeEvent() {
        while (!stopConsumeEvent) {
            if (!events.isEmpty()) {
                String event = events.poll();
                List<EventHandler> eventHandlers = this.eventHandlers.getOrDefault(event, new ArrayList<>());
                ArrayList<Future<?>> futures = new ArrayList<>(eventHandlers.size());
                for (EventHandler handler : eventHandlers) {
                    FutureTask<Integer> res = new FutureTask<>(Executors.callable(handler::resolve, 1));
                    futures.add(handlersExecutePool.submit(res));
                }
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        //
                    }
                }
                EventConsumedCallback eventConsumedCallback = this.eventCallbacks.get(event);
                if (eventConsumedCallback != null) {
                    callbackExecutePool.execute(eventConsumedCallback::callback);
                }
            }
        }
    }

    /**
     * 注册事件监听
     * @param event event name
     * @param eventHandler event handler
     */
    public static void registerEventHandler(String event, EventHandler eventHandler) {
        Preconditions.checkState(instance.status.get() == ServiceStatus.START, "EventService not start.");
        registerEventHandler(event, eventHandler, null);
    }

    /**
     * 注册事件监听
     * @param event event name
     * @param eventHandler event handler
     * @param consumedCallback event consumed callback
     */
    public static void registerEventHandler(String event, EventHandler eventHandler, EventConsumedCallback consumedCallback) {
        Preconditions.checkState(instance.status.get() == ServiceStatus.START, "EventService not start.");
        instance.eventHandlers.compute(event, (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(eventHandler);
            return v;
        });
        if (consumedCallback != null) {
            instance.eventCallbacks.put(event, consumedCallback);
        }
    }

    /**
     * 触发事件
     *
     * @param event event name
     */
    public static void emitEvent(String event) {
        Preconditions.checkState(instance.status.get() == ServiceStatus.START, "EventService not start.");
        instance.events.add(event);
    }

    /**
     * 销毁
     */
    public static void destroy() throws InterruptedException {
        Preconditions.checkState(instance.status.get() != ServiceStatus.STOP, "EventService already stop.");
        if (!instance.status.compareAndSet(ServiceStatus.START, ServiceStatus.STOP)) {
            throw new IllegalStateException("EventService not start.");
        }
        instance.stopConsumeEvent = true;
        instance.callbackExecutePool.shutdown();
        instance.handlersExecutePool.shutdown();
        int time = 0;
        while (true) {
            if (instance.callbackExecutePool.isTerminated() && instance.handlersExecutePool.isTerminated()) {
                instance.eventCallbacks = null;
                instance.eventHandlers = null;
                instance.events = null;
                break;
            }
            Thread.sleep(1000);
            time += 1;
            System.out.println("wait pool close. time: " + time);
        }


        System.out.println("destroy");
    }

    private enum ServiceStatus {
        NOT_START, START, STOP
    }

}
