package com.stainberg.koala.eventhub;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.app.Fragment;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Stainberg on 4/22/16.
 */
public class EventBus {
    ConcurrentLinkedQueue<IEvent> queue;
    ConcurrentLinkedQueue<WeakReference<Subscription>> subscriptions;
    private ExecutorService executorService;
    private ExecutorService bgExecutor;
    private ExecutorService dispatchExecutor;
    private static volatile EventBus defaultInstance;
    private boolean interrupt = false;

    public static EventBus getDefault() {//default eventbus
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus(new Builder());
                }
            }
        }
        return defaultInstance;
    }

    EventBus(Builder builder) {
        queue = builder.queue;
        subscriptions = builder.subscriptions;
        executorService = builder.executorService;
        dispatchExecutor = Executors.newCachedThreadPool();
        bgExecutor = builder.bgExecutor;
        interrupt = false;
        new EventThread().start();
    }

    public void destroy() {
        interrupt = true;
        synchronized (EventBus.this) {
            EventBus.this.notifyAll();
        }
        defaultInstance = null;
    }

    public void unregister(final Object object) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                for (WeakReference<Subscription> s : subscriptions) {
                    Subscription subscribe = s.get();
                    if (subscribe == null) {
                        subscriptions.remove(s);
                    } else {
                        if (object instanceof Subscription) {
                            Subscription subscription = (Subscription) object;
                            if (subscription.id.equals(subscribe.id)) {
                                s.clear();
                                subscriptions.remove(s);
                                break;
                            }
                        } else {
                            String tag = subscribe.id.substring(0, subscribe.id.indexOf("@"));
                            if (subscribe.mode == Mode.standard) {
                                if(tag.equals(String.valueOf(object.hashCode()))) {
                                    s.clear();
                                    subscriptions.remove(s);
                                }
                            } else { //singleTask
                                if(tag.equals(String.valueOf(object.getClass().getName()))) {
                                    s.clear();
                                    subscriptions.remove(s);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    public void register(final Subscription subscription) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                for (WeakReference<Subscription> s : subscriptions) {
                    Subscription subscribe = s.get();
                    if (subscribe == null) {
                        subscriptions.remove(s);
                    }
                }
                Class<?> name = subscription.getEventClass();
                Object parent = subscription.getInvokeObject();
                if (name == null) {
                    throw new IllegalArgumentException("Could not parse Generic parameter!");
                }
                if (subscription.mode == Mode.standard) {
                    subscription.id = parent.hashCode() + "@" + subscription.getClass().getName();
                } else { //singleTask
                    subscription.id = parent.getClass().getName() + "@" + subscription.getClass().getName();
                    for (WeakReference<Subscription> s : subscriptions) {
                        Subscription subscribe = s.get();
                        if (subscribe != null) {
                            if (subscribe.id.equals(subscription.id)) {
                                s.clear();
                                subscriptions.remove(s);
                                break;
                            }
                        } else {
                            s.clear();
                            subscriptions.remove(s);
                        }
                    }
                }
                subscriptions.offer(new WeakReference<>(subscription));
            }
        });
    }

    public <T extends IEvent> void post(T event) {
        queue.offer(event);
        for(WeakReference<Subscription> subscription : subscriptions) {
            Subscription subscribe = subscription.get();
            if(subscribe == null) {
                subscriptions.remove(subscription);
            } else {
                if(subscribe.handleType == HandleType.synchronous) {
                    if(subscribe.getEventClass().getName().equals(event.getClass().getName())) {
                        dispatchSync(subscribe, event);
                    }
                }
            }
        }
        synchronized (EventBus.this) {
            EventBus.this.notifyAll();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends IEvent> void dispatchSync(Subscription subscribe, T event) {//handler event in the same thread who published
        subscribe.handleMessage(event);
    }

    @SuppressWarnings("unchecked")
    private <T extends IEvent> void dispatchMain(final Subscription subscribe, final T event) {//handler event in the same thread who published
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Object object = getOuterObject(subscribe);
                    if(object instanceof Fragment) {
                        if(!((Fragment) object).isAdded()) {
                            return;
                        }
                    }
                    if(object instanceof android.app.Fragment) {
                        if(!((android.app.Fragment) object).isAdded()) {
                            return;
                        }
                    }
                    if(object instanceof Activity) {
                        if(((Activity) object).isDestroyed()) {
                            return;
                        }
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return;
                }

                subscribe.handleMessage(event);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends IEvent> void dispatchAsync(final Subscription subscribe, final T event) {//handler event in the background thread
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                subscribe.handleMessage(event);
            }
        });
    }

    public void dispatch(final IEvent event) {//handler event in the background then publish it to main thread or background thread
        dispatchExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(WeakReference<Subscription> subscription : subscriptions) {
                    Subscription subscribe = subscription.get();
                    if(subscribe == null) {
                        subscriptions.remove(subscription);
                    } else {
                        if (subscribe.handleType == HandleType.main) {
                            if (subscribe.getEventClass().getName().equals(event.getClass().getName())) {
                                dispatchMain(subscribe, event);
                            }
                        } else if (subscribe.handleType == HandleType.background) {
                            if (subscribe.getEventClass().getName().equals(event.getClass().getName())) {
                                dispatchAsync(subscribe, event);
                            }
                        }
                    }
                }
            }
        });
    }

    private Object getOuterObject(Object object) throws IllegalAccessException {
        Field[] fields = object.getClass().getDeclaredFields();
        for (Field field : fields) {
            if(field.getName().contains("this$")) {
                field.setAccessible(true);
                Object result = field.get(object);
                if(field.getName().equals("this$0")) {
                    return result;
                } else {
                    return getOuterObject(result);
                }
            }
        }
        return object;
    }

    private final static Handler handler = new Handler(Looper.getMainLooper());

    private class EventThread extends Thread {

        EventThread() {
            super("EventThread-" + SystemClock.elapsedRealtime());
        }

        @Override
        public void run() {
            while (!interrupt) {
                if(queue.isEmpty()) {
                    try {
                        synchronized (EventBus.this) {
                            EventBus.this.wait();
                        }
                        continue;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                IEvent event = queue.poll();
                dispatch(event);
            }
            queue.clear();
            subscriptions.clear();
        }
    }

    public static class Builder {
        ConcurrentLinkedQueue<IEvent> queue;
        ConcurrentLinkedQueue<WeakReference<Subscription>> subscriptions;
        ExecutorService executorService;
        ExecutorService bgExecutor;

        public Builder() {
            queue = new ConcurrentLinkedQueue<>();
            subscriptions = new ConcurrentLinkedQueue<>();
            executorService = Executors.newFixedThreadPool(1);
            bgExecutor = Executors.newCachedThreadPool();
        }

        public EventBus build() {
            return new EventBus(this);
        }

    }

}
