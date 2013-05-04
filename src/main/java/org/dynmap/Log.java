package org.dynmap;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {
    private static Logger log = Logger.getLogger("Dynmap");
    private static String prefix = "";
    public static boolean verbose = false;
    public static void setLogger(Logger logger, String pre) {
        log = logger;
        if((pre != null) && (pre.length() > 0))
            prefix = pre + " ";
        else
            prefix = "";
    }
    public static void setLoggerParent(Logger parent) {
        log.setParent(parent);
    }
    public static void info(String msg) {
        log.log(Level.INFO, prefix + msg);
    }
    public static void verboseinfo(String msg) {
        if(verbose)
            log.log(Level.INFO, prefix + msg);
    }
    public static void severe(Throwable e) {
        log.log(Level.SEVERE, prefix + "Exception occured: ", e);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, prefix + msg);
    }
    public static void severe(String msg, Throwable e) {
        log.log(Level.SEVERE, prefix + msg, e);
    }
    public static void warning(String msg) {
        log.log(Level.WARNING, prefix + msg);
    }
    public static void warning(String msg, Throwable e) {
        log.log(Level.WARNING, prefix + msg, e);
    }
}
