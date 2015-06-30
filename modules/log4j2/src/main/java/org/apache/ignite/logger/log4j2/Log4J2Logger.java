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
import org.apache.logging.log4j.core.layout.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static org.apache.ignite.IgniteSystemProperties.*;

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
 *
 * Porting for the Log4j2
 *
 * @author Gianfranco Murador
 */
public class Log4J2Logger implements IgniteLogger, LoggerNodeIdAware, Log4j2FileAware {
    /** Appenders. */
    private static Collection<FileAppender> fileAppenders = new GridConcurrentHashSet<>();

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
     * {@link #Log4J2Logger(boolean) Log4JLogger(boolean)}
     * with parameter {@code true}, otherwise, existing appenders will be used (analogous
     * to calling {@link #Log4J2Logger(boolean) Log4JLogger(boolean)}
     * with parameter {@code false}).
     */
    public Log4J2Logger() {
        this(!isConfigured());
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
        impl = Logger.getRootLogger();

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
     * Creates new logger with given configuration {@code path}.
     *
     * @param path Path to log4j configuration XML file.
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
                    DOMConfigurator.configure(cfgUrl);

                return Logger.getRootLogger();
            }
        });

        quiet = quiet0;
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

        addConsoleAppenderIfNeeded(null, new C1<Boolean, Logger>() {
            @Override public Logger apply(Boolean init) {
                if (init)
                    DOMConfigurator.configure(path);

                return Logger.getRootLogger();
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
                    DOMConfigurator.configure(cfgUrl);

                return Logger.getRootLogger();
            }
        });

        quiet = quiet0;
    }

    /**
     * Checks if Log4j is already configured within this VM or not.
     *
     * @return {@code True} if log4j was already configured, {@code false} otherwise.
     */
    public static boolean isConfigured() {
        return Logger.getRootLogger().getAllAppenders().hasMoreElements();
    }

    /**
     * Sets level for internal log4j implementation.
     *
     * @param level Log level to set.
     */
    public void setLevel(Level level) {
        impl.setLevel(level);
    }

    /** {@inheritDoc} */
    @Nullable @Override public String fileName() {
        FileAppender fapp = F.first(fileAppenders);

        return fapp != null ? fapp.getFile() : null;
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

            boolean quiet = Boolean.valueOf(System.getProperty(IGNITE_QUIET, "true"));

            boolean consoleAppenderFound = false;
            Category rootCategory = null;
            ConsoleAppender errAppender = null;

            for (Category l = impl; l != null; ) {
                if (!consoleAppenderFound) {
                    for (Enumeration appenders = l.getAllAppenders(); appenders.hasMoreElements(); ) {
                        Appender appender = (Appender)appenders.nextElement();

                        if (appender instanceof ConsoleAppender) {
                            if ("CONSOLE_ERR".equals(appender.getName())) {
                                // Treat CONSOLE_ERR appender as a system one and don't count it.
                                errAppender = (ConsoleAppender)appender;

                                continue;
                            }

                            consoleAppenderFound = true;

                            break;
                        }
                    }
                }

                if (l.getParent() == null) {
                    rootCategory = l;

                    break;
                }
                else
                    l = l.getParent();
            }

            if (consoleAppenderFound && quiet)
                // User configured console appender, but log is quiet.
                quiet = false;

            if (!consoleAppenderFound && !quiet && Boolean.valueOf(System.getProperty(IGNITE_CONSOLE_APPENDER, "true"))) {
                // Console appender not found => we've looked through all categories up to root.
                assert rootCategory != null;

                // User launched ignite in verbose mode and did not add console appender with INFO level
                // to configuration and did not set IGNITE_CONSOLE_APPENDER to false.
                if (errAppender != null) {
                    rootCategory.addAppender(createConsoleAppender(Level.INFO));

                    if (errAppender.getThreshold() == Level.ERROR)
                        errAppender.setThreshold(Level.WARN);
                }
                else
                    // No error console appender => create console appender with no level limit.
                    rootCategory.addAppender(createConsoleAppender(Level.OFF));

                if (logLevel != null)
                    impl.setLevel(logLevel);
            }

            quiet0 = quiet;
            inited = true;
        }
    }

    /**
     * Creates console appender with some reasonable default logging settings.
     *
     * @param maxLevel Max logging level.
     * @return New console appender.
     */
    private Appender createConsoleAppender(Level maxLevel) {
        String fmt = "[%d{ABSOLUTE}][%-5p][%t][%c{1}] %m%n";

        // Configure output that should go to System.out
        Appender app = new ConsoleAppender(new PatternLayout(fmt), ConsoleAppender.SYSTEM_OUT);

        LevelRangeFilter lvlFilter = new LevelRangeFilter();

        lvlFilter.setLevelMin(Level.TRACE);
        lvlFilter.setLevelMax(maxLevel);

        app.addFilter(lvlFilter);

        return app;
    }

    /**
     * Adds file appender.
     *
     * @param a Appender.
     */
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
    @Override public void setNodeId(UUID nodeId) {
        A.notNull(nodeId, "nodeId");

        this.nodeId = nodeId;

        updateFilePath(new Log4j2NodeIdFilePath(nodeId));
    }

    /** {@inheritDoc} */
    @Override public UUID getNodeId() {
        return nodeId;
    }

    /**
     * Gets files for all registered file appenders.
     *
     * @return List of files.
     */
    public static Collection<String> logFiles() {
        Collection<String> res = new ArrayList<>(fileAppenders.size());

        for (FileAppender a : fileAppenders)
            res.add(a.getFile());

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
        return new Log4J2Logger(ctgr == null ? Logger.getRootLogger() :
            ctgr instanceof Class ? Logger.getLogger(((Class<?>)ctgr).getName()) :
                Logger.getLogger(ctgr.toString()));
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
        return quiet;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(Log4J2Logger.class, this);
    }

    /** {@inheritDoc} */
    @Override public void updateFilePath(IgniteClosure<String, String> filePathClos) {
        A.notNull(filePathClos, "filePathClos");

        for (FileAppender a : fileAppenders) {
            if (a instanceof Log4j2FileAware) {
                ((Log4j2FileAware)a).updateFilePath(filePathClos);

                a.activateOptions();
            }
        }
    }
}
