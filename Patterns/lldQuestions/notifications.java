import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;


// =========================
// ENUMS
// =========================

enum NotificationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum NotificationType {
    ORDER,
    PROMOTION,
    MESSAGE,
    SOCIAL
}

enum NotificationStatus {
    UNREAD,
    READ,
    SNOOZED
}

enum ChannelType {
    EMAIL,
    SMS,
    PUSH,
    IN_APP
}


// =========================
// NOTIFICATION
// =========================

class Notification {

    private String id;

    private String userId;

    private String title;

    private String message;

    private NotificationPriority priority;

    private NotificationType type;

    private NotificationStatus status;

    public Notification(
            String id,
            String userId,
            String title,
            String message,
            NotificationPriority priority,
            NotificationType type) {

        this.id = id;
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.priority = priority;
        this.type = type;

        this.status = NotificationStatus.UNREAD;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(
            NotificationStatus status) {

        this.status = status;
    }
}


// =========================
// STRATEGY PATTERN
// =========================

interface NotificationSender {

    void send(Notification notification);
}


class EmailSender
        implements NotificationSender {

    @Override
    public void send(
            Notification notification) {

        System.out.println(
                "EMAIL SENT -> "
                        + notification.getTitle()
                        + " | "
                        + notification.getMessage());
    }
}


class SmsSender
        implements NotificationSender {

    @Override
    public void send(
            Notification notification) {

        System.out.println(
                "SMS SENT -> "
                        + notification.getMessage());
    }
}


class PushSender
        implements NotificationSender {

    @Override
    public void send(
            Notification notification) {

        System.out.println(
                "PUSH SENT -> "
                        + notification.getTitle());
    }
}


class InAppSender
        implements NotificationSender {

    @Override
    public void send(
            Notification notification) {

        System.out.println(
                "IN-APP SENT -> "
                        + notification.getTitle());
    }
}


// =========================
// FACTORY PATTERN
// =========================

class SenderFactory {

    private Map<ChannelType,
            NotificationSender> senderMap;

    public SenderFactory() {

        senderMap = new HashMap<>();

        senderMap.put(
                ChannelType.EMAIL,
                new EmailSender());

        senderMap.put(
                ChannelType.SMS,
                new SmsSender());

        senderMap.put(
                ChannelType.PUSH,
                new PushSender());

        senderMap.put(
                ChannelType.IN_APP,
                new InAppSender());
    }

    public NotificationSender getSender(
            ChannelType type) {

        return senderMap.get(type);
    }
}


// =========================
// GROUPING
// =========================

class NotificationGroupKey {

    private String userId;

    private NotificationType type;

    public NotificationGroupKey(
            String userId,
            NotificationType type) {

        this.userId = userId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {

        if(this == o) return true;

        if(o == null ||
                getClass() != o.getClass()) {

            return false;
        }

        NotificationGroupKey that =
                (NotificationGroupKey) o;

        return Objects.equals(userId, that.userId)
                && type == that.type;
    }

    @Override
    public int hashCode() {

        return Objects.hash(userId, type);
    }
}


class NotificationGroup {

    private List<Notification> notifications;

    private NotificationPriority priority;

    public NotificationGroup() {

        notifications = new ArrayList<>();

        priority = NotificationPriority.LOW;
    }

    public synchronized void add(
            Notification notification) {

        notifications.add(notification);

        if(notification.getPriority().ordinal()
                > priority.ordinal()) {

            priority =
                    notification.getPriority();
        }
    }

    public synchronized int size() {

        return notifications.size();
    }

    public synchronized NotificationPriority
    getPriority() {

        return priority;
    }

    public synchronized List<Notification>
    getNotifications() {

        return notifications;
    }
}


class NotificationGroupingService {

    private Map<NotificationGroupKey,
            NotificationGroup> groupMap;

    public NotificationGroupingService() {

        groupMap =
                new ConcurrentHashMap<>();
    }

    public NotificationGroup add(
            Notification notification) {

        NotificationGroupKey key =
                new NotificationGroupKey(
                        notification.getUserId(),
                        notification.getType());

        groupMap.putIfAbsent(
                key,
                new NotificationGroup());

        NotificationGroup group =
                groupMap.get(key);

        group.add(notification);

        return group;
    }
}


// =========================
// PRIORITY TASKS
// =========================

class NotificationGroupTask
        implements Comparable
        <NotificationGroupTask> {

    private NotificationGroup group;

    public NotificationGroupTask(
            NotificationGroup group) {

        this.group = group;
    }

    public NotificationGroup getGroup() {

        return group;
    }

    @Override
    public int compareTo(
            NotificationGroupTask other) {

        return other.group
                .getPriority()
                .ordinal()

                - this.group
                .getPriority()
                .ordinal();
    }
}


// =========================
// PRIORITY QUEUE
// =========================

class NotificationQueue {

    private PriorityBlockingQueue
            <NotificationGroupTask> queue;

    public NotificationQueue() {

        queue =
                new PriorityBlockingQueue<>();
    }

    public void add(
            NotificationGroupTask task) {

        queue.put(task);
    }

    public NotificationGroupTask take()
            throws InterruptedException {

        return queue.take();
    }
}


// =========================
// DELAYED TASK
// =========================

class ScheduledNotificationTask
        implements Delayed {

    private NotificationGroupTask task;

    private long scheduledTime;

    public ScheduledNotificationTask(
            NotificationGroupTask task,
            long delayInMillis) {

        this.task = task;

        this.scheduledTime =
                System.currentTimeMillis()
                        + delayInMillis;
    }

    public NotificationGroupTask getTask() {

        return task;
    }

    @Override
    public long getDelay(
            TimeUnit unit) {

        long diff =
                scheduledTime
                        - System.currentTimeMillis();

        return unit.convert(
                diff,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(
            Delayed other) {

        ScheduledNotificationTask o =
                (ScheduledNotificationTask) other;

        return Long.compare(
                this.scheduledTime,
                o.scheduledTime);
    }
}


// =========================
// DELAY QUEUE
// =========================

class SchedulerQueue {

    private DelayQueue
            <ScheduledNotificationTask> queue;

    public SchedulerQueue() {

        queue = new DelayQueue<>();
    }

    public void add(
            ScheduledNotificationTask task) {

        queue.put(task);
    }

    public ScheduledNotificationTask take()
            throws InterruptedException {

        return queue.take();
    }
}


// =========================
// SERVICES
// =========================

class NotificationService {

    private NotificationGroupingService
            groupingService;

    private NotificationQueue queue;

    public NotificationService(
            NotificationGroupingService
                    groupingService,

            NotificationQueue queue) {

        this.groupingService =
                groupingService;

        this.queue = queue;
    }

    public void send(
            Notification notification) {

        NotificationGroup group =
                groupingService.add(notification);

        NotificationGroupTask task =
                new NotificationGroupTask(group);

        queue.add(task);

        System.out.println(
                "Notification added "
                        + "to queue");
    }
}


class SnoozeService {

    private SchedulerQueue schedulerQueue;

    public SnoozeService(
            SchedulerQueue schedulerQueue) {

        this.schedulerQueue =
                schedulerQueue;
    }

    public void snooze(
            NotificationGroupTask task,
            long delayInMillis) {

        ScheduledNotificationTask
                scheduledTask =

                new ScheduledNotificationTask(
                        task,
                        delayInMillis);

        schedulerQueue.add(scheduledTask);

        System.out.println(
                "Task snoozed for "
                        + delayInMillis
                        + " ms");
    }
}


// =========================
// WORKERS
// =========================

class NotificationWorker
        implements Runnable {

    private NotificationQueue queue;

    private SenderFactory factory;

    public NotificationWorker(
            NotificationQueue queue,
            SenderFactory factory) {

        this.queue = queue;
        this.factory = factory;
    }

    @Override
    public void run() {

        while(true) {

            try {

                NotificationGroupTask task =
                        queue.take();

                NotificationGroup group =
                        task.getGroup();

                Notification notification =
                        buildGroupedNotification(
                                group);

                NotificationSender sender =
                        factory.getSender(
                                ChannelType.EMAIL);

                sender.send(notification);

            } catch(Exception e) {

                e.printStackTrace();
            }
        }
    }

    private Notification
    buildGroupedNotification(
            NotificationGroup group) {

        return new Notification(
                UUID.randomUUID().toString(),
                "user1",
                "Grouped Notification",
                group.size()
                        + " notifications received",
                group.getPriority(),
                NotificationType.PROMOTION
        );
    }
}


class SchedulerWorker
        implements Runnable {

    private SchedulerQueue schedulerQueue;

    private NotificationQueue
            notificationQueue;

    public SchedulerWorker(
            SchedulerQueue schedulerQueue,

            NotificationQueue
                    notificationQueue) {

        this.schedulerQueue =
                schedulerQueue;

        this.notificationQueue =
                notificationQueue;
    }

    @Override
    public void run() {

        while(true) {

            try {

                ScheduledNotificationTask
                        scheduledTask =
                        schedulerQueue.take();

                notificationQueue.add(
                        scheduledTask.getTask());

                System.out.println(
                        "Delayed task moved "
                                + "to processing queue");

            } catch(Exception e) {

                e.printStackTrace();
            }
        }
    }
}


// =========================
// MAIN
// =========================

public class notifications {

    public static void main(String[] args) {

        NotificationQueue
                notificationQueue =
                new NotificationQueue();

        SchedulerQueue schedulerQueue =
                new SchedulerQueue();

        NotificationGroupingService
                groupingService =
                new NotificationGroupingService();

        SenderFactory factory =
                new SenderFactory();

        NotificationService
                notificationService =
                new NotificationService(
                        groupingService,
                        notificationQueue);

        SnoozeService snoozeService =
                new SnoozeService(
                        schedulerQueue);

        Thread worker1 =
                new Thread(
                        new NotificationWorker(
                                notificationQueue,
                                factory));

        Thread worker2 =
                new Thread(
                        new NotificationWorker(
                                notificationQueue,
                                factory));

        Thread schedulerWorker =
                new Thread(
                        new SchedulerWorker(
                                schedulerQueue,
                                notificationQueue));

        worker1.start();

        worker2.start();

        schedulerWorker.start();


        // =========================
        // NORMAL NOTIFICATIONS
        // =========================

        Notification promo1 =
                new Notification(
                        "1",
                        "user1",
                        "Big Sale",
                        "50% OFF",
                        NotificationPriority.LOW,
                        NotificationType.PROMOTION
                );

        Notification promo2 =
                new Notification(
                        "2",
                        "user1",
                        "Mega Sale",
                        "70% OFF",
                        NotificationPriority.LOW,
                        NotificationType.PROMOTION
                );

        Notification otp =
                new Notification(
                        "3",
                        "user1",
                        "OTP",
                        "Your OTP is 1234",
                        NotificationPriority.CRITICAL,
                        NotificationType.ORDER
                );


        notificationService.send(promo1);

        notificationService.send(promo2);

        notificationService.send(otp);


        // =========================
        // SNOOZE FLOW
        // =========================

        Notification reminder =
                new Notification(
                        "4",
                        "user1",
                        "Meeting Reminder",
                        "Meeting at 5 PM",
                        NotificationPriority.HIGH,
                        NotificationType.MESSAGE
                );

        NotificationGroup reminderGroup =
                new NotificationGroup();

        reminderGroup.add(reminder);

        NotificationGroupTask reminderTask =
                new NotificationGroupTask(
                        reminderGroup);

        snoozeService.snooze(
                reminderTask,
                5000);
    }
}