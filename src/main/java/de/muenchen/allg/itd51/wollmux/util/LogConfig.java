/*-
 * #%L
 * WollMux
 * %%
 * Copyright (C) 2005 - 2020 Landeshauptstadt München
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */
package de.muenchen.allg.itd51.wollmux.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.log4j.Appender;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.config.PropertyPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Mit der LogConfig können die Einstellungen für den Logger aus der Datei log4.j.properties
 * überschrieben werden. Dazu gibt es verschiedene init-Methoden.
 * </p>
 * <p>
 * Zudem können die Einstellungen aus der WollMux-Konfiguration hierüber gesetzt werden.
 * </p>
 */
public class LogConfig
{

  private static final Logger LOGGER = LoggerFactory.getLogger(LogConfig.class);

  /**
   * Wenn ignoreInit==true, wird der nächte init-Aufruf ignoriert.
   */
  private static boolean ignoreInit = false;

  private static final Layout LAYOUT = new EnhancedPatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n");

  private static final org.apache.log4j.Logger ROOT_LOGGER = LogManager.getRootLogger();

  private LogConfig()
  {}

  /**
   * Über die Methode init wird der Logger mit einem PrintStream und einem
   * Logging-Modus initialisiert. Ohne diese Methode schreibt der Logger auf
   * System.err im Modus LOG.
   *
   * @param loggingMode
   *          Der neue Logging-Modus kann über die statischen Felder Logger.MODUS (z.
   *          B. Logger.DEBUG) angegeben werden.
   */
  public static void init(PrintStream outputPrintStream, Level loggingMode)
  {
    if (ignoreInit) {
      return;
    }
    Appender appender = new WriterAppender(LAYOUT, outputPrintStream);
    ROOT_LOGGER.setLevel(loggingMode);
    ROOT_LOGGER.removeAllAppenders();
    ROOT_LOGGER.addAppender(appender);
  }

  /**
   * Über die Methode init wird der Logger mit einer Ausgabedatei und einem
   * Logging-Modus initialisiert. Ohne diese Methode schreibt der Logger auf
   * System.err im Modus LOG.
   *
   * @param outputFile
   *          Datei, in die die Ausgaben geschrieben werden.
   * @param loggingMode
   *          Der neue Logging-Modus kann über die statischen Felder Logger.MODUS (z.
   *          B. Logger.DEBUG) angegeben werden.
   */
  public static void init(File outputFile, Level loggingMode)
  {
    if (ignoreInit) {
      return;
    }
    try {
      Appender appender = new FileAppender(LAYOUT, outputFile.getAbsolutePath(), true);
      ROOT_LOGGER.setLevel(loggingMode);
      ROOT_LOGGER.removeAllAppenders();
      ROOT_LOGGER.addAppender(appender);
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }

  /**
   * Über die Methode init wird der Logger in dem Logging-Modus loggingMode
   * initialisiert, der in Form eines den obigen Konstanten-Namen übereinstimmenden
   * Strings vorliegt. Ohne diese Methode schreibt der Logger auf System.err im Modus
   * LOG.
   *
   * @param loggingMode
   *          Der neue Logging-Modus kann über die statischen Felder Logger.MODUS (z.
   *          B. Logger.DEBUG) angegeben werden.
   */
  public static void init(String loggingMode)
  {
    if (ignoreInit) {
      return;
    }

    if ("NONE".equals(loggingMode)) {
      ROOT_LOGGER.setLevel(Level.OFF);
    }
    Level mode = Level.toLevel(loggingMode, Level.INFO);
    ROOT_LOGGER.setLevel(mode);
  }

  /**
   * Dump the log4j configuration.
   *
   * @param out
   *          The writer to dump to.
   */
  public static void dumpConfiguration(PrintWriter out)
  {
    new PropertyPrinter(out).print(out);
  }

  /**
   * Nach einem Aufruf dieser Methode mit ignoreInit==true werden alle folgenden
   * init-Aufrufe ignoriert.
   */
  public static void setIgnoreInit(boolean ignoreInit)
  {
    LogConfig.ignoreInit = ignoreInit;
  }
}
