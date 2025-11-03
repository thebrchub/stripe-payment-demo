package com.thebrchub.demo.app.config;

import org.jboss.logging.Logger;

public final class BaseLogger {

    private BaseLogger() {
    }

    public static final Logger LOG = Logger.getLogger(BaseLogger.class);

    public static Logger getLogger() {
        return LOG;
    }
}