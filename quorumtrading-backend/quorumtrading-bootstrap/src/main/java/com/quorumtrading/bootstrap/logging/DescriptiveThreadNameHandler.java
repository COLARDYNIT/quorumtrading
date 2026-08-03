package com.quorumtrading.bootstrap.logging;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.WriterHandler;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Startup bean that installs a thread-name translating log handler.
 *
 * <p>This replaces cryptic thread names like "vert.x-eventloop-thread-2"
 * with human-readable names like "eventloop-io 3/8" in log output.</p>
 *
 * <p>Thread name translation happens at log-publish time, preserving
 * the original thread name in the actual thread for debugging purposes.</p>
 *
 * @see ThreadNameTranslator
 */
@ApplicationScoped
@Startup
public class DescriptiveThreadNameHandler {

    @PostConstruct
    void init() {
        // Install our translating handler wrapper on the root logger
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();

        for (Handler handler : handlers) {
            if (handler instanceof ConsoleHandler || handler instanceof WriterHandler) {
                // Wrap the existing handler with our translator
                TranslatingHandler wrapper = new TranslatingHandler(handler);
                rootLogger.removeHandler(handler);
                rootLogger.addHandler(wrapper);
            }
        }
    }

    /**
     * Handler wrapper that translates thread names before delegating to the wrapped handler.
     */
    private static class TranslatingHandler extends Handler {
        private final Handler delegate;

        TranslatingHandler(Handler delegate) {
            this.delegate = delegate;
            setLevel(delegate.getLevel());
            setFilter(delegate.getFilter());
            setFormatter(delegate.getFormatter());
            setErrorManager(delegate.getErrorManager());
        }

        @Override
        public void publish(LogRecord record) {
            if (record instanceof ExtLogRecord extRecord) {
                // Translate the thread name for display
                String originalName = extRecord.getThreadName();
                String translatedName = ThreadNameTranslator.translate(originalName);

                // Create a new record with the translated name
                // (We don't want to modify the original as it may be used elsewhere)
                ExtLogRecord translatedRecord = new ExtLogRecord(extRecord);
                translatedRecord.setThreadName(translatedName);

                delegate.publish(translatedRecord);
            } else {
                delegate.publish(record);
            }
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() throws SecurityException {
            delegate.close();
        }
    }
}
