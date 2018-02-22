# koala-eventhub
like eventbus.

使用：
compile 'com.stainberg.koala:eventhub:1.0.6'

定义消息（以java中使用为例）：

```java
class TimeTestEvent implements IEvent {

        long timestamp = SystemClock.elapsedRealtime();

        public long getTimestampDiff() {
            return SystemClock.elapsedRealtime() - timestamp;
        }
    }
```

定义订阅（为了避免订阅被GC回收，订阅请务必不要使用局部变量）：
```java
private Subscription<TimeTestEvent> subscription = new Subscription<TimeTestEvent>() {
        @Override
        public void handleMessage(@NotNull TimeTestEvent event) {
            Log.d("timetest", String.valueOf(event.getTimestampDiff()));
        }
    };
```

发送消息：
```java
EventBus.Companion.getDefault().post(new TimeTestEvent());
```

以上是java的示例，kotlin的同理。
