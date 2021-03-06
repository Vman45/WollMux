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
package de.muenchen.allg.itd51.wollmux.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.muenchen.allg.itd51.wollmux.config.generator.xml.ConfGenerator;
import de.muenchen.allg.itd51.wollmux.config.generator.xml.XMLGenerator;
import de.muenchen.allg.itd51.wollmux.config.generator.xml.XMLGeneratorException;
import de.muenchen.allg.itd51.wollmux.config.scanner.Scanner;
import de.muenchen.allg.itd51.wollmux.config.scanner.ScannerException;
import de.muenchen.allg.itd51.wollmux.config.scanner.Token;
import de.muenchen.allg.itd51.wollmux.config.scanner.TokenType;

/**
 * A Test class to verify that the scanner works correctly with includes.
 *
 * @author Daniel Sikeler
 */
public class TestWithInclude
{

  /**
   * The tokens expeceted by the scan of includeTest.conf.
   */
  private final Token[] tokens = {
      new Token(getClass().getResource("includeTest.conf").getFile(), TokenType.NEW_FILE),
      new Token("file:inc/includeTest2.conf", TokenType.NEW_FILE),
      new Token("# includeTest2", TokenType.COMMENT),
      new Token("", TokenType.END_FILE),
      new Token("file:../config/inc/includeTest2.conf", TokenType.NEW_FILE),
      new Token("# includeTest2", TokenType.COMMENT),
      new Token("", TokenType.END_FILE),
      new Token("../config/inc/includeTest2.conf", TokenType.NEW_FILE),
      new Token("# includeTest2", TokenType.COMMENT),
      new Token("", TokenType.END_FILE),
      new Token("inc/includeTest3.conf", TokenType.NEW_FILE),
      new Token("includeTest2.conf", TokenType.NEW_FILE),
      new Token("# includeTest2", TokenType.COMMENT),
      new Token("", TokenType.END_FILE), new Token("", TokenType.END_FILE),
      new Token("", TokenType.END_FILE), };

  /**
   * Map to store the file content mapping.
   */
  private final Map<String, String> fileContentMap = new HashMap<>();

  /**
   * Test if the scanner works properly.
   *
   * @throws ScannerException
   *           Scanner problems.
   * @throws MalformedURLException
   *           Couldn't find the file with the configuration.
   */
  @Test
  public void scanWithInclude() throws ScannerException, MalformedURLException
  {
    final Scanner scanner = new Scanner(getClass().getResource("includeTest.conf"));
    int index = 0;
    while (scanner.hasNext())
    {
      final Token token = scanner.next();
      assertFalse(index >= tokens.length, "Tokenstream to long " + token);
      assertEquals(tokens[index++], token, "Token " + index + " is wrong");
    }
    assertFalse(index < tokens.length, "Tokenstream to short");
    scanner.close();
  }

  /**
   * Generate a configuration out of a configuration. Scan it and than write it again.
   *
   * @throws XMLGeneratorException
   *           Generator problems.
   * @throws SAXException
   *           Malformed XML-document generated.
   * @throws IOException
   *           Couldn't read or write.
   * @throws URISyntaxException
   */
  @Test
  public void generateWithInclude() throws XMLGeneratorException, SAXException,
      IOException, URISyntaxException
  {
    final File in = new File(getClass().getResource("scannerTest.conf").toURI());
    final File out = new File(in.getParentFile(), "tmp.conf");
    final File in2 = new File(getClass().getResource("scannerTest2.conf").toURI());
    final File out2 = new File(in2.getParentFile(), "tmp2.conf");
    Files.copy(in.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(in2.toPath(), out2.toPath(), StandardCopyOption.REPLACE_EXISTING);
    final Document doc = new XMLGenerator(out2.toURI().toURL()).generateXML();
    final File schemaFile = new File("src/main/resources/configuration.xsd");
    final SchemaFactory schemaFactory = SchemaFactory
        .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    final Schema schema = schemaFactory.newSchema(schemaFile);
    final Validator validator = schema.newValidator();
    final Source source = new DOMSource(doc);
    validator.validate(source);
    ConfGenerator generator = new ConfGenerator(doc);
    generator.generateConf();
    // Whitespace was replaced
    assertEquals(in2.length(), out2.length(), "Different content length 2");
    // out.delete();
    // out2.delete();
    boolean windowsOS = System.getProperty("os.name").toLowerCase().contains("windows");
    if(windowsOS)
    {
//    hier von 9 auf 8, weil Windows im Unterschied zu Linux (ein LineFeed) die Kombination CarriageReturn und LineFeed verwendet, also 2 Zeichen
      assertEquals(in.length(), out.length() + 8, "Different content length");
      fileContentMap.put(getClass().getResource("tmp2.conf").getFile(), "%include \"tmp.conf\"\r\n\r\n");
      fileContentMap.put(getClass().getResource("tmp.conf").getFile(), "A 'X\"\"Y'\r\nB 'X\"Y'\r\nC \"X''Y\"\r\nD \"X'Y\"\r\nGUI (\r\n  Dialoge (\r\n    Dialog1 (\r\n      (TYPE \"textbox\" LABEL \"Name\")\r\n    )\r\n  )\r\n)\r\nAnredevarianten (\"Herr\", \"Frau\", \"Pinguin\")\r\n(\"Dies\", \"ist\", \"eine\", \"unbenannte\", \"Liste\")\r\nNAME \"WollMux%%%n\" # FARBSCHEMA \"Ekelig\"\r\n\r\n");
    }
    else
    {
      assertEquals(in.length(), out.length() + 9, "Different content length");
      fileContentMap.put(getClass().getResource("tmp2.conf").getFile(), "%include \"tmp.conf\"\n\n");
      fileContentMap.put(getClass().getResource("tmp.conf").getFile(), "A 'X\"\"Y'\nB 'X\"Y'\nC \"X''Y\"\nD \"X'Y\"\nGUI (\n  Dialoge (\n    Dialog1 (\n      (TYPE \"textbox\" LABEL \"Name\")\n    )\n  )\n)\nAnredevarianten (\"Herr\", \"Frau\", \"Pinguin\")\n(\"Dies\", \"ist\", \"eine\", \"unbenannte\", \"Liste\")\nNAME \"WollMux%%%n\" # FARBSCHEMA \"Ekelig\"\n\n");
    }
    Map<String, String> map = generator.generateConfMap("UTF-8");
    assertEquals(fileContentMap.size(), map.size(), "Different number of files");
    for (Entry<String, String> entry : map.entrySet())
    {
      assertTrue(fileContentMap.containsKey(entry.getKey()), "Unknown file " + entry.getKey());
      assertEquals(fileContentMap.get(entry.getKey()), entry.getValue(), "Different content");
    }
  }

}
