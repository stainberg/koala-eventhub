package com.stainberg.koala.eventhub

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.app.Fragment
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Created by Stainberg on 4/22/16.
 */
class EventBus {
    private val queue = ConcurrentLinkedQueue<IEvent>()
    private val subscriptions = ConcurrentLinkedQueue<WeakReference<Subscription<IEvent>>>()
    private val executorService = Executors.newFixedThreadPool(1)
    private val bgExecutor = Executors.newCachedThreadPool()
    private val dispatchExecutor = Executors.newCachedThreadPool()
    private var interrupt = false
    private val lock = java.lang.Object()

    init {
        interrupt = false
        EventThread().start()
    }

    fun destroy() {
        interrupt = true
        synchronized(lock) {
            lock.notifyAll()
            Logger.d("subscriptions notifyAll")
        }
    }

    fun unregister(`object`: Any) {
        executorService.execute {
            for (s in subscriptions) {
                val subscribe = s.get()
                if (subscribe == null) {
                    subscriptions.remove(s)
                    Logger.d("subscriptions.remove 1")
                } else {
                    if (`object` is Subscription<*>) {
                        if (`object`.id == subscribe.id) {
                            s.clear()
                            subscriptions.remove(s)
                            Logger.d("subscriptions.remove 2")
                            break
                        }
                    } else {
                        val tag = subscribe.id!!.substring(0, subscribe.id!!.indexOf("@"))
                        if (subscribe.mode === Mode.standard) {
                            if (tag == `object`.hashCode().toString()) {
                                s.clear()
                                subscriptions.remove(s)
                                Logger.d("subscriptions.remove 3")
                            }
                        } else { //singleTask
                            if (tag == `object`.javaClass.name.toString()) {
                                s.clear()
                                subscriptions.remove(s)
                                Logger.d("subscriptions.remove 4")
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : IEvent> register(subscription: Subscription<T>) {
        executorService.execute {
            for (s in subscriptions) {
                val subscribe = s.get()
                if (subscribe == null) {
                    subscriptions.remove(s)
                    Logger.d("subscriptions remove 1")
                }
            }
            if (subscription.mode == Mode.singleTask) {
                for (s in subscriptions) {
                    val subscribe = s.get()
                    if (subscribe != null) {
                        if (subscribe.id == subscription.id) {
                            s.clear()
                            subscriptions.remove(s)
                            Logger.d("subscriptions remove 2")
                            break
                        }
                    } else {
                        s.clear()
                        subscriptions.remove(s)
                        Logger.d("subscriptions remove 3")
                    }
                }
            }
            subscriptions.offer(WeakReference(subscription as Subscription<IEvent>))
        }
    }

    fun <T : IEvent> post(event: T) {
        queue.offer(event)
        for (subscription in subscriptions) {
            val subscribe = subscription.get()
            if (subscribe == null) {
                subscriptions.remove(subscription)
                Logger.d("subscriptions remove")
            } else {
                if (subscribe.handleType === HandleType.synchronous) {
                    if (subscribe.eventClass!!.name == event.javaClass.name) {
                        dispatchSync(subscribe, event)
                    }
                }
            }
        }
        synchronized(lock) {
            lock.notifyAll()
            Logger.d("subscriptions notifyAll")
        }
    }

    private fun <T : IEvent> dispatchSync(subscribe: Subscription<T>, event: T) {//handler event in the same thread who published
        subscribe.handleMessage(event)
        Logger.d("dispatchSync")
    }

    private fun <T : IEvent> dispatchMain(subscribe: Subscription<T>, event: T) {//handler event in the same thread who published
        handler.post(Runnable {
            try {
                val `object` = getOuterObject(subscribe)
                if (`object` is Fragment) {
                    if (!`object`.isAdded) {
                        return@Runnable
                    }
                }
                if (`object` is android.app.Fragment) {
                    if (!`object`.isAdded) {
                        return@Runnable
                    }
                }
                if (`object` is Activity) {
                    if (`object`.isDestroyed) {
                        return@Runnable
                    }
                }
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
                return@Runnable
            }

            subscribe.handleMessage(event)
            Logger.d("dispatchMain")
        })
    }

    private fun <T : IEvent> dispatchAsync(subscribe: Subscription<T>, event: T) {//handler event in the background thread
        bgExecutor.execute { subscribe.handleMessage(event) }
        Logger.d("dispatchAsync")
    }

    fun dispatch(event: IEvent) {//handler event in the background then publish it to main thread or background thread
        dispatchExecutor.execute {
            for (subscription in subscriptions) {
                val subscribe = subscription.get()
                if (subscribe == null) {
                    subscriptions.remove(subscription)
                    Logger.d("subscriptions remove")
                } else {
                    if (subscribe.handleType === HandleType.main) {
                        if (subscribe.eventClass!!.name == event.javaClass.name) {
                            dispatchMain(subscribe, event)
                        }
                    } else if (subscribe.handleType === HandleType.background) {
                        if (subscribe.eventClass!!.name == event.javaClass.name) {
                            dispatchAsync(subscribe, event)
                        }
                    }
                }
            }
        }
    }

    @Throws(IllegalAccessException::class)
    private fun getOuterObject(`object`: Any): Any {
        val fields = `object`.javaClass.declaredFields
        for (field in fields) {
            if (field.name.contains("this$")) {
                field.isAccessible = true
                val result = field.get(`object`)
                return if (field.name == "this$0") {
                    result
                } else {
                    getOuterObject(result)
                }
            }
        }
        return `object`
    }

    private inner class EventThread internal constructor() : Thread("EventThread-" + SystemClock.elapsedRealtime()) {

        override fun run() {
            while (!interrupt) {
                if (queue.isEmpty()) {
                    try {
                        synchronized(lock) {
                            Logger.d("EventThread wait")
                            lock.wait()
                        }
                        continue
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
                val event = queue.poll()
                Logger.d("queue poll")
                dispatch(event)
            }
            queue.clear()
            subscriptions.clear()
        }
    }

    companion object {
        @Volatile
        private var defaultInstance = EventBus()

        fun getDefault(): EventBus {
            return defaultInstance
        }

        private val handler = Handler(Looper.getMainLooper())

        var DEBUG : Int = LogLevel.LEVEL_E.get()

    }

    enum class LogLevel constructor(private val lv: Int) {
        DISABLE(0),//disable
        LEVEL_E(1),//only error
        LEVEL_W(2),//error and w
        LEVEL_D(3),//2 and debug
        LEVEL_V(4);//v and i

        fun get(): Int {
            return lv
        }
    }

}
