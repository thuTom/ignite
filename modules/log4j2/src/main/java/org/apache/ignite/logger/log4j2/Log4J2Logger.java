/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.logger.log4j2;

import org.apache.ignite.*;
import org.apache.ignite.internal.util.*;
import org.apache.ignite.internal.util.tostring.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.logger.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.layout.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Log4j2-based implementation for logging. This logger should be used by loaders that have prefer <a target=_new
 * href="http://logging.apache.org/log4j/2.x/">log4j2</a>-based logging. <p> Here is a typical example of configuring
 * log4j logger in Ignite configuration file:
 *
 * <pre name="code" class="xml"> &lt;property name="gridLogger"&gt; &lt;bean class="org.apache.ignite.grid.logger.log4j.GridLog4J2Logger"&gt;
 * &lt;constructor-arg type="java.lang.String" value="config/ignite-log4j2.xml"/&gt; &lt;/bean> &lt;/property&gt;
 * </pre>
 *
 * and from your code:
 *
 * <pre name="code" class="java"> IgniteConfiguration cfg = new IgniteConfiguration(); ... URL xml =
 * U.resolveIgniteUrl("config/custom-log4j.xml"); IgniteLogger log = new Log4J2Logger(xml); ... cfg.setGridLogger(log);
 * </pre>
 *
 * Please take a look at <a target=_new href="http://logging.apache.org/log4j/2.x/">Apache Log4j 2.7</a> for additional
 * information. <p> It's recommended to use Ignite logger injection instead of using/instantiating logger apacin your
 * task/job code. See {@link org.apache.ignite.resources.LoggerResource} annotation about logger injection.
 */
public class Log4J2Logger implements IgniteLogger, LoggerNodeIdAware {
    /** Appenders. */
    private static final Collection<FileAppender> fileAppenders = new GridConcurrentHashSet<>();

    /** */
    private static volatile boolean inited;

    /** */
    private static volatile boolean quiet0;

    /** */
    private static final Object mux = new Object();

    /** Logger implementation. */
    @GridToStringExclude
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private Logger impl;

    /** Path to configuration file. */
    private final String path;

    /** Quiet flag. */
    private final boolean quiet;

    /** Node ID. */
    private UUID nodeId;

    /**
     * Creates new logger and automatically detects if root logger already
     * has appenders configured. If it does not, the root logger will be
     * configured with default appender (analogous to calling
     * {@link #Log4J2Logger(boolean) Log4J2Logger(boolean)}
     * with parameter {@code true}, otherwise, existing appenders will be used (analogous
     * to calling {@link #Log4J2Logger(boolean) Log4J2Logger(boolean)}
     * with parameter {@code false}).
     */
    public Log4J2Logger() {
        this(!isConfigured());
    }

    /**
     * Creates new logger with given implementation.
     *
     * @param impl Log4j implementation to use.
     */
    public Log4J2Logger(final Logger impl) {
        assert impl != null;

        path = null;

        addConsoleAppenderIfNeeded(null, new C1<Boolean, Logger>() {
            @Override public Logger apply(Boolean init) {
                return impl;
            }
        });

        quiet = quiet0;
    }

    /**
     * Creates new logger. If initialize parameter is {@code true} the Log4j
     * logger will be initialized with default console appender and {@code INFO}
     * log level.
     *
     * @param init If {@code true}, then a default console appender with
     *      following pattern layout will be created: {@code %d{ABSOLUTE} %-5p [%c{1}] %m%n}.
     *      If {@code false}, then no implicit initialization will take place,
     *      and {@code Log4j} should be configured prior to calling this
     *      constructor.
     */
    public Log4J2Logger(boolean init) {
        impl = LogManager.getRootLogger();

        if (init) {
            // Implementation has already been inited, passing NULL.
            addConsoleAppenderIfNeeded(Level.INFO, null);

            quiet = quiet0;
        }
        else
            quiet = true;

        path = null;
    }

    /**
     * Creates new logger with given configuration {@code cfgFile}.
     *
     * @param cfgFile Log4j configuration XML file.
     * @throws IgniteCheckedException Thrown in case logger can't be created.
     */
    public Log4J2Logger(File cfgFile) throws IgniteCheckedException {
        if (cfgFile == null)
            throw new IgniteCheckedException("Configuration XML file for Log4j must be specified.");

        if (!cfgFile.exists() || cfgFile.isDirectory())
            throw new IgniteCheckedException("Log4j configuration path was not found or is a directory: " + cfgFile);

        path = cfgFile.getAbsolutePath();

        final String uri;

        try {
            uri = cfgFile.toURI().toURL().toString();
        }
        catch (MalformedURLException e) {
            throw new IgniteCheckedException(e.toString());
        }

        addConsoleAppenderIfNeeded(null, new C1<Boolean, Logger>() {
            @Override public Logger apply(Boolean init) {
                if (init)
                    Configurator.initialize("Log4J2Logger", uri);

                return LogManager.getRootLogger();
            }
        });

        quiet = quiet0;
    }

    /**
     * Creates new logger with given configuration {@code path}.
     *
     * @param path Path to log4j2 configuration XML file.
     * @throws IgniteCheckedException Thrown in case logger can't be created.
     */
    public Log4J2Logger(String path) throws IgniteCheckedException {
        if (path == null)
            throw new IgniteCheckedException("Configuration XML file for Log4j must be specified.");

        this.path = path;

        final URL cfgUrl = U.resolveIgniteUrl(path);

        if (cfgUrl == null)
            throw new IgniteCheckedException("Log4j configuration path was not found: " + path);

        addConsoleAppenderIfNeeded(null, new C1<Boolean, Logger>() {
            @Override public Logger apply(Boolean init) {
                if (init)
                    Configurator.initialize("Log4J2Logger", cfgUrl.toString());

                return LogManager.getRootLogger();
            }
        });

        quiet = quiet0;
    }

    /**
     * Creates new logger with given configuration {@code cfgUrl}.
     *
     * @param cfgUrl URL for Log4j configuration XML file.
     * @throws IgniteCheckedException Thrown in case logger can't be created.
     */
    public Log4J2Logger(final URL cfgUrl) throws IgniteCheckedException {
        if (cfgUrl == null)
            throw new IgniteCheckedException("Configuration XML file for Log4j must be specified.");

        path = null;

        addConsoleAppenderIfNeeded(null, new C1<Boolean, Logger>() {
            @Override public Logger apply(Boolean init) {
                if (init)
                    Configurator.initialize("Log4J2Logger", cfgUrl.toString());

                return LogManager.getRootLogger();
            }
        });

        quiet = quiet0;
    }

    /**
     * Checks if Log4j2 is already configured within this VM or not.
     *
     * @return {@code True} if log4j2 was already configured, {@code false} otherwise.
     */
    public static boolean isConfigured() {
        return LogManager.getLogger("Log4J2Logger") != null;
    }

    /**
     * Sets level for internal log4j implementation.
     *
     * @param level Log level to set.
     */
    public void setLevel(Level level) {
        LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
        Configuration conf = ctx.getConfiguration();
        conf.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(level);
        ctx.updateLoggers(conf);
    }

    /** {@inheritDoc} */
    @Nullable @Override public String fileName() {
        // TODO
        // It was.
//        FileAppender fapp = F.first(fileAppenders);
//
//        return fapp != null ? fapp.getFile() : null;

        // New logic.
        // TODO cast, RollingFileAppender and etc.
        org.apache.logging.log4j.core.Logger loggerImpl = (org.apache.logging.log4j.core.Logger)impl;

        Collection<Appender> appenders = loggerImpl.getAppenders().values();

        for (Appender a : appenders) {
            if (a instanceof FileAppender)
                return ((FileAppender)a).getFileName();

            if (a instanceof RollingFileAppender)
                return ((RollingFileAppender)a).getFileName();
        }

        return null;
    }

    /**
     * Adds console appender when needed with some default logging settings.
     *
     * @param logLevel Optional log level.
     * @param implInitC Optional log implementation init closure.
     */
    private void addConsoleAppenderIfNeeded(@Nullable Level logLevel,
        @Nullable IgniteClosure<Boolean, Logger> implInitC) {
        if (inited) {
            if (implInitC != null)
                // Do not init.
                impl = implInitC.apply(false);

            return;
        }

        synchronized (mux) {
            if (inited) {
                if (implInitC != null)
                    // Do not init.
                    impl = implInitC.apply(false);

                return;
            }

            if (implInitC != null)
                // Init logger impl.
                impl = implInitC.apply(true);

            // TODO review
            // use the local quite instead of this
            boolean quiet = isQuiet();

            // here added a console logger if not exists, programmatically
            // the implementations is more easy with new API
            // Log4j2 has always a console logger by default
            Logger clog = LogManager.getLogger("CONSOLE");
            if (clog == null) {
                ConsoleAppender console = ConsoleAppender.createAppender(PatternLayout.createDefaultLayout(), null,
                    "CONSOLE", "console", null, null);

                final LoggerContext ctx = new LoggerContext("console");

                final Configuration config = ctx.getConfiguration();

                config.addAppender(console);

                AppenderRef ref = AppenderRef.createAppenderRef("console",logLevel, null);

                AppenderRef[] refs = new AppenderRef[] {ref};

                LoggerConfig logCfg = LoggerConfig.createLogger("false",logLevel, "org.apache.logging.log4j", "true",
                    refs, null, config, null);

                logCfg.addAppender(console, null, null);

                config.addLogger("org.apache.logging.log4j", logCfg);

                ctx.updateLoggers();
            }

            quiet0 = quiet;
            inited = true;
        }
    }

    /**
     * Adds file appender.
     *
     * @param a Appender.
     */
    // TODO we really need these methods.
    public static void addAppender(FileAppender a) {
        A.notNull(a, "a");

        fileAppenders.add(a);
    }

    /**
     * Removes file appender.
     *
     * @param a Appender.
     */
    public static void removeAppender(FileAppender a) {
        A.notNull(a, "a");

        fileAppenders.remove(a);
    }

    /** {@inheritDoc} */
    @Override public UUID getNodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @Override public void setNodeId(UUID nodeId) {
        A.notNull(nodeId, "nodeId");
        // TODO
        // Old impl
//
//        this.nodeId = nodeId;
//
//        updateFilePath(new Log4j2NodeIdFilePath(nodeId));

        // New impl.
        String uid = U.id8(nodeId);

        // set up the new thread context
        ThreadContext.put("nodeidmsg", ">>> Local Node [ID: " + uid + "]");
        ThreadContext.put("ROUTINGKEY", uid);

        ((LoggerContext)LogManager.getContext(false)).reconfigure();

        this.nodeId = nodeId;
    }

    /** {@inheritDoc} */
    @Override public void trace(String msg) {
        if (!impl.isTraceEnabled())
            warning("Logging at TRACE level without checking if TRACE level is enabled: " + msg);

        impl.trace(msg);
    }

    /** {@inheritDoc} */
    @Override public void debug(String msg) {
        if (!impl.isDebugEnabled())
            warning("Logging at DEBUG level without checking if DEBUG level is enabled: " + msg);

        impl.debug(msg);
    }

    /** {@inheritDoc} */
    @Override public void info(String msg) {
        if (!impl.isInfoEnabled())
            warning("Logging at INFO level without checking if INFO level is enabled: " + msg);

        impl.info(msg);
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg) {
        impl.warn(msg);
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg, @Nullable Throwable e) {
        impl.warn(msg, e);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg) {
        impl.error(msg);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg, @Nullable Throwable e) {
        impl.error(msg, e);
    }

    /** {@inheritDoc} */
    @Override public boolean isTraceEnabled() {
        return impl.isTraceEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isDebugEnabled() {
        return impl.isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isInfoEnabled() {
        return impl.isInfoEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isQuiet() {
        return !isInfoEnabled() && !isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(Log4J2Logger.class, this);
    }

    /**
     * Gets files for all registered file appenders.
     *
     * @return List of files.
     */
    public static Collection<String> logFiles() {
        Collection<String> res = new ArrayList<>(fileAppenders.size());

        for (FileAppender a : fileAppenders)
            res.add(a.getFileName());

        return res;
    }

    /**
     * Gets {@link org.apache.ignite.IgniteLogger} wrapper around log4j logger for the given
     * category. If category is {@code null}, then root logger is returned. If
     * category is an instance of {@link Class} then {@code (Class)ctgr).getName()}
     * is used as category name.
     *
     * @param ctgr {@inheritDoc}
     * @return {@link org.apache.ignite.IgniteLogger} wrapper around log4j logger.
     */
    @Override public Log4J2Logger getLogger(Object ctgr) {
        if (ctgr == null)
            return new Log4J2Logger(LogManager.getRootLogger());

        if (ctgr instanceof Class)
            return new Log4J2Logger(LogManager.getLogger(((Class<?>)ctgr).getName()));

        return new Log4J2Logger(LogManager.getLogger(ctgr.toString()));
    }
}
