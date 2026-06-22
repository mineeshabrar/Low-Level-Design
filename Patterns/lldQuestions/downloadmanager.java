import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;


/*
    ==========================================================
                OFFLINE DOWNLOAD MANAGER
    ==========================================================

    FEATURES
    --------
    1. Download files
    2. Pause download
    3. Resume download
    4. Cancel download
    5. Progress tracking
    6. Priority downloads
    7. Thread-safe
    8. Observer Pattern
    9. Strategy Pattern
    10. Producer-Consumer style execution

    Examples:
    ---------
    Netflix offline downloads
    YouTube offline videos
    Spotify downloads
*/


/* ==========================================================
                        ENUMS
   ========================================================== */

enum DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum DownloadPriority {
    LOW,
    MEDIUM,
    HIGH
}


/* ==========================================================
                        FILE METADATA
   ========================================================== */

class FileMetadata {

    private final String fileId;

    private final String fileName;

    private final long fileSize;

    private final String downloadUrl;

    public FileMetadata(
            String fileId,
            String fileName,
            long fileSize,
            String downloadUrl
    ) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.downloadUrl = downloadUrl;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}


/* ==========================================================
                        OBSERVER
   ========================================================== */

interface DownloadObserver {

    void onStatusChanged(
            DownloadTask task
    );
}


/* ==========================================================
                    CONSOLE OBSERVER
   ========================================================== */

class ConsoleDownloadObserver
        implements DownloadObserver {

    @Override
    public void onStatusChanged(
            DownloadTask task
    ) {

        System.out.println(
                "Task "
                        + task.getTaskId()
                        + " -> "
                        + task.getStatus()
                        + " | Progress: "
                        + task.getProgress()
                        + "%"
        );
    }
}


/* ==========================================================
                    DOWNLOAD TASK
   ========================================================== */

class DownloadTask
        implements Comparable<DownloadTask> {

    private final String taskId;

    private final FileMetadata metadata;

    private volatile DownloadStatus status;

    private volatile int progress;

    private final DownloadPriority priority;

    private final List<DownloadObserver>
            observers = new ArrayList<>();

    /*
        THREAD SAFETY

        Lock per task.
    */

    private final ReentrantLock lock =
            new ReentrantLock();

    public DownloadTask(
            String taskId,
            FileMetadata metadata,
            DownloadPriority priority
    ) {

        this.taskId = taskId;

        this.metadata = metadata;

        this.priority = priority;

        this.status = DownloadStatus.QUEUED;

        this.progress = 0;
    }


    public ReentrantLock getLock() {
        return lock;
    }

    public void addObserver(
            DownloadObserver observer
    ) {
        observers.add(observer);
    }

    private void notifyObservers() {

        for(DownloadObserver observer
                : observers) {

            observer.onStatusChanged(this);
        }
    }

    public void setStatus(
            DownloadStatus status
    ) {

        this.status = status;

        notifyObservers();
    }

    public void updateProgress(
            int progress
    ) {

        this.progress = progress;

        notifyObservers();
    }

    /*
        HIGH priority first
    */

    @Override
    public int compareTo(
            DownloadTask other
    ) {

        return other.priority.ordinal()
                - this.priority.ordinal();
    }
}


/* ==========================================================
                DOWNLOAD STRATEGY
   ========================================================== */

interface DownloadStrategy {

void download(
            DownloadTask task
    ) throws InterruptedException;
}


/* ==========================================================
                SIMPLE DOWNLOAD STRATEGY
   ========================================================== */

class SimpleDownloadStrategy
        implements DownloadStrategy {

    @Override
    public void download(
            DownloadTask task
    ) throws InterruptedException {

        task.setStatus(
                DownloadStatus.DOWNLOADING
        );

        /*
            Simulating download chunks
        */

        for(int i = task.getProgress();
                i <= 100;
                i += 10) {

            /*
                THREAD SAFETY
            */

            task.getLock().lock();

            try {

                if(task.getStatus()
                        == DownloadStatus.CANCELLED) {

                    return;
                }

                if(task.getStatus()
                        == DownloadStatus.PAUSED) {

                    return;
                }

                Thread.sleep(300);

                task.updateProgress(i);

            } finally {
                task.getLock().unlock();
            }
        }

        task.setStatus(
                DownloadStatus.COMPLETED
        );
    }
}


/* ==========================================================
                    DOWNLOAD MANAGER
   ========================================================== */

class DownloadManager {

    /*
        Priority Queue
    */

    private final PriorityBlockingQueue<
            DownloadTask
            > taskQueue =
            new PriorityBlockingQueue<>();

    private final Map<String, DownloadTask>
            taskMap =
            new ConcurrentHashMap<>();

    private final ExecutorService
            executorService;

    private final DownloadStrategy
            strategy;

    public DownloadManager(
            int workerThreads,
            DownloadStrategy strategy
    ) {

        this.executorService =
                Executors.newFixedThreadPool(
                        workerThreads
                );

        this.strategy = strategy;

        startWorkers(workerThreads);
    }

    /*
        Producer Consumer Model
    */

    private void startWorkers(
            int workerThreads
    ) {

        for(int i = 0;
                i < workerThreads;
                i++) {

            executorService.submit(() -> {

                while(true) {

                    try {

                        DownloadTask task =
                                taskQueue.take();

                        if(task.getStatus()
                                == DownloadStatus.CANCELLED) {

                            continue;
                        }

                        strategy.download(task);

                        /*
                            Re-add paused tasks later
                        */

                        if(task.getStatus()
                                == DownloadStatus.PAUSED) {

                            taskQueue.offer(task);
                        }

                    } catch(Exception e) {

                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void submitDownload(
            DownloadTask task
    ) {

        taskMap.put(
                task.getTaskId(),
                task
        );

        taskQueue.offer(task);
    }

    public void pauseDownload(
            String taskId
    ) {

        DownloadTask task =
                taskMap.get(taskId);

        if(task == null) {
            return;
        }

        task.getLock().lock();

        try {

            if(task.getStatus()
                    == DownloadStatus.DOWNLOADING) {

                task.setStatus(
                        DownloadStatus.PAUSED
                );
            }

        } finally {
            task.getLock().unlock();
        }
    }

    public void resumeDownload(
            String taskId
    ) {

        DownloadTask task =
                taskMap.get(taskId);

        if(task == null) {
            return;
        }

        task.getLock().lock();

        try {

            if(task.getStatus()
                    == DownloadStatus.PAUSED) {

                task.setStatus(
                        DownloadStatus.QUEUED
                );

                taskQueue.offer(task);
            }

        } finally {
            task.getLock().unlock();
        }
    }

    public void cancelDownload(
            String taskId
    ) {

        DownloadTask task =
                taskMap.get(taskId);

        if(task == null) {
            return;
        }

        task.getLock().lock();

        try {

            task.setStatus(
                    DownloadStatus.CANCELLED
            );

        } finally {
            task.getLock().unlock();
        }
    }
}


/* ==========================================================
                            MAIN
   ========================================================== */

public class Main {

    public static void main(String[] args)
            throws InterruptedException {

        DownloadStrategy strategy =
                new SimpleDownloadStrategy();

        DownloadManager manager =
                new DownloadManager(
                        3,
                        strategy
                );

        DownloadObserver observer =
                new ConsoleDownloadObserver();

        /*
            FILES
        */

        FileMetadata file1 =
                new FileMetadata(
                        "F1",
                        "Movie.mp4",
                        1000,
                        "url1"
                );

        FileMetadata file2 =
                new FileMetadata(
                        "F2",
                        "Song.mp3",
                        500,
                        "url2"
                );

        /*
            TASKS
        */

        DownloadTask task1 =
                new DownloadTask(
                        "T1",
                        file1,
                        DownloadPriority.HIGH
                );

        DownloadTask task2 =
                new DownloadTask(
                        "T2",
                        file2,
                        DownloadPriority.MEDIUM
                );

        task1.addObserver(observer);
        task2.addObserver(observer);

        /*
            SUBMIT
        */

        manager.submitDownload(task1);

        manager.submitDownload(task2);

        Thread.sleep(1200);

        /*
            PAUSE
        */

        manager.pauseDownload("T1");

        Thread.sleep(2000);

        /*
            RESUME
        */

        manager.resumeDownload("T1");
    }
}
```






enums. file class. 

downloadtask. compared. priority blocking queue 

download strategues 

download manager
pq
taskmap = new ConcurrentHashmap<>();

ececutor.service.newfixedThreadpool(wor)

task.

for(int i=0;i<workd;i+) 
executor.submit(() -> {
    while (true)
    {
        download.
    }
}
