// Application Thread
//        |
//        v

//     Logger
//        |
//        v

//  BlockingQueue
//        |
//        v

//  Worker Threads
//        |
//        +----> ConsoleAppender
//        |
//        +----> FileAppender
//        |
//        +----> KafkaAppender
//                |
//                v

//           Formatter
//                |
//                +--> Text
//                +--> JSON



import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

// =========================
// LOG LEVEL
// =========================

enum LogLevel {

    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

// =========================
// LOG MESSAGE
// =========================

class LogMessage {

    private LogLevel level;

    private String message;

    private LocalDateTime timestamp;

    private String threadName;

    public LogMessage(
            LogLevel level,
            String message) {

        this.level = level;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.threadName =
                Thread.currentThread().getName();
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getThreadName() {
        return threadName;
    }
}

// =========================
// FORMATTER STRATEGY
// =========================

interface Formatter {

    String format(LogMessage message);
}

// =========================
// TEXT FORMATTER
// =========================

class TextFormatter
        implements Formatter {

    @Override
    public String format(
            LogMessage message) {

        return message.getTimestamp()
                + " ["
                + message.getLevel()
                + "] ["
                + message.getThreadName()
                + "] "
                + message.getMessage();
    }
}

// =========================
// JSON FORMATTER
// =========================

class JsonFormatter
        implements Formatter {

    @Override
    public String format(
            LogMessage message) {

        return "{"
                + "\"timestamp\":\""
                + message.getTimestamp()
                + "\","
                + "\"level\":\""
                + message.getLevel()
                + "\","
                + "\"thread\":\""
                + message.getThreadName()
                + "\","
                + "\"message\":\""
                + message.getMessage()
                + "\""
                + "}";
    }
}

// =========================
// APPENDER STRATEGY
// =========================

interface Appender {

    void append(LogMessage message);

    LogLevel getThreshold();
}

// =========================
// CONSOLE APPENDER
// =========================

class ConsoleAppender
        implements Appender {

    private Formatter formatter;

    private LogLevel threshold;

    public ConsoleAppender(
            Formatter formatter,
            LogLevel threshold) {

        this.formatter = formatter;
        this.threshold = threshold;
    }

    @Override
    public void append(
            LogMessage message) {

        System.out.println(
                formatter.format(message));
    }

    @Override
    public LogLevel getThreshold() {

        return threshold;
    }
}

// =========================
// FILE APPENDER
// =========================

class FileAppender
        implements Appender {

    private Formatter formatter;

    private LogLevel threshold;

    public FileAppender(
            Formatter formatter,
            LogLevel threshold) {

        this.formatter = formatter;
        this.threshold = threshold;
    }

    @Override
    public synchronized void append(
            LogMessage message) {

        String log =
                formatter.format(message);

        // Simulating file write
        System.out.println(
                "FILE WRITE : " + log);
    }

    @Override
    public LogLevel getThreshold() {

        return threshold;
    }
}
// LOGGER CONFIG
// =========================

class LoggerConfig {

    private LogLevel globalLogLevel;

    private List<Appender> appenders;

    public LoggerConfig(
            LogLevel globalLogLevel,
            List<Appender> appenders) {

        this.globalLogLevel =
                globalLogLevel;

        this.appenders = appenders;
    }

    public LogLevel getGlobalLogLevel() {

        return globalLogLevel;
    }

    public List<Appender> getAppenders() {

        return appenders;
    }
}

// =========================
// LOGGER
// =========================

class AsyncLogger {

    private LoggerConfig config;

    private BlockingQueue<LogMessage>
            queue;

    public AsyncLogger(
            LoggerConfig config) {

        this.config = config;

        this.queue =
                new LinkedBlockingQueue<>();

        startWorkers();
    }

    private void startWorkers() {

        int workers = 2;

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        workers);

        for(int i = 0; i < workers; i++) {

            executorService.submit(
                    new LogWorker(
                            queue,
                            config));
        }
    }

    public void log(
            LogLevel level,
            String message) {

        if(level.ordinal()
                < config.getGlobalLogLevel()
                .ordinal()) {

            return;
        }

        LogMessage logMessage =
                new LogMessage(
                        level,
                        message);

        queue.offer(logMessage);
    }

    public void debug(String message) {

        log(LogLevel.DEBUG, message);
    }

    public void info(String message) {

        log(LogLevel.INFO, message);
    }

    public void warn(String message) {

        log(LogLevel.WARN, message);
    }

    public void error(String message) {

        log(LogLevel.ERROR, message);
    }

    public void fatal(String message) {

        log(LogLevel.FATAL, message);
    }
}

// =========================
// WORKER THREAD
// =========================

class LogWorker
        implements Runnable {

    private BlockingQueue<LogMessage>
            queue;

    private LoggerConfig config;

    public LogWorker(
            BlockingQueue<LogMessage> queue,
            LoggerConfig config) {

        this.queue = queue;
        this.config = config;
    }

    @Override
    public void run() {

        while(true) {

            try {

                LogMessage message =
                        queue.take();

                process(message);

            } catch(Exception e) {

                e.printStackTrace();
            }
        }
    }

    private void process(
            LogMessage message) {

        for(Appender appender :
                config.getAppenders()) {

            if(message.getLevel()
                    .ordinal()
                    >= appender
                    .getThreshold()
                    .ordinal()) {

                appender.append(message);
            }
        }
    }
}

// =========================
// FACTORY
// =========================

class FormatterFactory {

    public static Formatter getFormatter(
            String type) {

        switch(type) {

            case "TEXT":
                return new TextFormatter();

            case "JSON":
                return new JsonFormatter();

            default:
                throw new RuntimeException(
                        "Invalid formatter");
        }
    }
}

// =========================
// APPENDER FACTORY
// =========================

class AppenderFactory {

    public static Appender getAppender(
            String type,
            Formatter formatter,
            LogLevel threshold) {

        switch(type) {

            case "CONSOLE":
                return new ConsoleAppender(
                        formatter,
                        threshold);

            case "FILE":
                return new FileAppender(
                        formatter,
                        threshold);

            default:
                throw new RuntimeException(
                        "Invalid appender");
        }
    }
}

// =========================
// LOGGER MANAGER SINGLETON
// =========================

class LoggerManager {

    private static final
    LoggerManager INSTANCE =
            new LoggerManager();

    private AsyncLogger logger;

    private LoggerManager() {}

    public static LoggerManager
    getInstance() {

        return INSTANCE;
    }

    public void initialize(
            AsyncLogger logger) {

        this.logger = logger;
    }

    public AsyncLogger getLogger() {

        return logger;
    }
}

// =========================
// MAIN
// =========================

public class logger {

    public static void main(
            String[] args)
            throws Exception {

        Formatter textFormatter =
                FormatterFactory
                        .getFormatter(
                                "TEXT");

        Formatter jsonFormatter =
                FormatterFactory
                        .getFormatter(
                                "JSON");

        Appender consoleAppender =
                AppenderFactory
                        .getAppender(
                                "CONSOLE",
                                textFormatter,
                                LogLevel.DEBUG);

        Appender fileAppender =
                AppenderFactory
                        .getAppender(
                                "FILE",
                                jsonFormatter,
                                LogLevel.ERROR);

        List<Appender> appenders =
                new ArrayList<>();

        appenders.add(consoleAppender);

        appenders.add(fileAppender);

        LoggerConfig config =
                new LoggerConfig(
                        LogLevel.DEBUG,
                        appenders);

        AsyncLogger logger =
                new AsyncLogger(config);

        LoggerManager
                .getInstance()
                .initialize(logger);

        AsyncLogger appLogger =
                LoggerManager
                        .getInstance()
                        .getLogger();

        appLogger.debug(
                "Debug log");

        appLogger.info(
                "User logged in");

        appLogger.warn(
                "Memory usage high");

        appLogger.error(
                "Database failed");

        appLogger.fatal(
                "System crash");
    }
}