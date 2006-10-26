/*
 * Dateiname: TextDocumentModel.java
 * Projekt  : WollMux
 * Funktion : Repr�sentiert ein aktuell ge�ffnetes TextDokument.
 * 
 * Copyright: Landeshauptstadt M�nchen
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 15.09.2006 | LUT | Erstellung als TextDocumentModel
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import com.sun.star.awt.DeviceInfo;
import com.sun.star.awt.PosSize;
import com.sun.star.awt.XWindow;
import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.DispatchDescriptor;
import com.sun.star.frame.FrameSearchFlag;
import com.sun.star.frame.XController;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.frame.XDispatchProviderInterceptor;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.frame.XStatusListener;
import com.sun.star.lang.EventObject;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.URL;
import com.sun.star.util.XCloseListener;
import com.sun.star.util.XURLTransformer;
import com.sun.star.view.DocumentZoomType;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.DocumentCommand.SetPrintFunction;
import de.muenchen.allg.itd51.wollmux.former.FormularMax4000;

/**
 * Diese Klasse repr�sentiert das Modell eines aktuell ge�ffneten TextDokuments
 * und erm�glicht den Zugriff auf alle interessanten Aspekte des TextDokuments.
 * 
 * @author christoph.lutz
 * 
 */
public class TextDocumentModel
{
  /**
   * Enth�lt die Referenz auf das XTextDocument-interface des eigentlichen
   * TextDocument-Services der zugeh�rigen UNO-Komponente.
   */
  public final XTextDocument doc;

  /**
   * Erm�glicht den Zugriff auf einen Vector aller FormField-Objekte in diesem
   * TextDokument �ber den Namen der zugeordneten ID.
   */
  private HashMap idToFormFields;

  /**
   * Falls es sich bei dem Dokument um ein Formular handelt, wird das zugeh�rige
   * FormModel hier gespeichert und beim dispose() des TextDocumentModels mit
   * geschlossen.
   */
  private FormModel formModel;

  /**
   * In diesem Feld wird der CloseListener gespeichert, nachdem die Methode
   * registerCloseListener() aufgerufen wurde.
   */
  private XCloseListener closeListener;

  /**
   * Enth�lt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000.
   */
  private FormularMax4000 currentMax4000;

  /**
   * Dieses Feld stellt ein Zwischenspeicher f�r Fragment-Urls dar. Es wird dazu
   * benutzt, im Fall eines openTemplate-Befehls die urls der �bergebenen
   * frag_id-Liste tempor�r zu speichern.
   */
  private String[] fragUrls;

  /**
   * Enth�lt das SetPrintFunction-Dokumentkommando falls in diesem Dokument eine
   * Druckfunktion definiert ist oder null, wenn keine Druckfunktion definiert
   * ist.
   */
  private DocumentCommand.SetPrintFunction printFunction;

  /**
   * Enth�lt den Namen der Druckfunktion, die vor dem letzten Aufruf der Methode
   * setPrintFunction(...) gesetzt war oder null wenn noch keine Druckfunktion
   * gesetzt war.
   */
  private String formerPrintFunctionName;

  /**
   * Enth�lt das zu diesem TextDocumentModel zugeh�rige XPrintModel.
   */
  private XPrintModel printModel = new PrintModel();

  /**
   * Baum aller im Dokument enthaltenen Dokumentkommandos.
   */
  private DocumentCommandTree docCmdTree;

  /**
   * Enth�lt die Formularbeschreibung falls es sich bei dem Dokument um ein
   * Formular handelt und wird jedoch erst mit
   * DocumentCommandInterpreter.executeNormalCommands() gesetzt.
   */
  private FormDescriptor formDescriptor = null;

  /**
   * Enth�lt das setType-Dokumentkommando dieses Dokuments falls eines vorhanden
   * ist.
   */
  private DocumentCommand.SetType setTypeCommand = null;

  /**
   * Enth�lt einen Vector aller notInOrininal-Dokumentkommandos des Dokuments,
   * die f�r die Ein/Ausblendungen in Sachleitenden Verf�gungen ben�tigt werden.
   */
  private Vector notInOriginalBlocks;

  /**
   * Enth�lt einen Vector aller draftOnly-Dokumentkommandos des Dokuments, die
   * f�r die Ein/Ausblendungen in Sachleitenden Verf�gungen ben�tigt werden.
   */
  private Vector draftOnlyBlocks;

  /**
   * �ber die Methode registerWollMuxDispatchInterceptor() wird hier der aktuell
   * auf dem Frame registrierte WollMuxDispatchInterceptor abgelegt, der f�r das
   * Abfangen von Dispatches wie z.B. dem .uno:Print erforderlich ist.
   */
  private WollMuxDispatchInterceptor dispatchInterceptor;

  /**
   * Erzeugt ein neues TextDocumentModel zum XTextDocument doc und sollte nie
   * direkt aufgerufen werden, da neue TextDocumentModels �ber das
   * WollMuxSingletonie (siehe WollMuxSingleton.getTextDocumentModel()) erzeugt
   * und verwaltet werden.
   * 
   * @param doc
   */
  public TextDocumentModel(XTextDocument doc)
  {
    this.doc = doc;
    this.idToFormFields = new HashMap();
    this.fragUrls = new String[] {};
    this.currentMax4000 = null;
    this.closeListener = null;
    this.printFunction = null;
    this.docCmdTree = new DocumentCommandTree(UNO.XBookmarksSupplier(doc));
    this.notInOriginalBlocks = new Vector();
    this.draftOnlyBlocks = new Vector();
    this.dispatchInterceptor = null;

    registerCloseListener();
    registerWollMuxDispatchInterceptor();
  }

  /**
   * Wird derzeit vom DocumentCommandInterpreter aufgerufen und gibt dem
   * TextDocumentModel alle FormFields bekannt, die beim Durchlauf des
   * FormScanners gefunden wurden.
   * 
   * @param idToFormFields
   */
  public void setIDToFormFields(HashMap idToFormFields)
  {
    this.idToFormFields = idToFormFields;
  }

  /**
   * Erm�glicht den Zugriff auf einen Vector aller FormField-Objekte in diesem
   * TextDokument �ber den Namen der zugeordneten ID.
   * 
   * @return Eine HashMap, die unter dem Schl�ssel ID einen Vector mit den
   *         entsprechenden FormFields bereith�lt.
   */
  public HashMap getIDToFormFields()
  {
    return idToFormFields;
  }

  /**
   * Speichert das FormModel formModel im TextDocumentModel und wird derzeit vom
   * DocumentCommandInterpreter aufgerufen, wenn er ein FormModel erzeugt.
   * 
   * @param formModel
   */
  public void setFormModel(FormModel formModel)
  {
    this.formModel = formModel;
  }

  /**
   * Liefert das zuletzt per setFormModel() gesetzte FormModel zur�ck.
   * 
   * @return
   */
  public FormModel getFormModel()
  {
    return this.formModel;
  }

  /**
   * Der DocumentCommandInterpreter liest sich die Liste der setFragUrls()
   * gespeicherten Fragment-URLs hier aus, wenn die Dokumentkommandos
   * insertContent ausgef�hrt werden.
   * 
   * @return
   */
  public String[] getFragUrls()
  {
    return fragUrls;
  }

  /**
   * �ber diese Methode kann der openDocument-Eventhandler die Liste der mit
   * einem insertContent-Kommando zu �ffnenden frag-urls speichern.
   */
  public void setFragUrls(String[] fragUrls)
  {
    this.fragUrls = fragUrls;
  }

  /**
   * Setzt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000.
   * 
   * @param max
   */
  public void setCurrentFormularMax4000(FormularMax4000 max)
  {
    currentMax4000 = max;
  }

  /**
   * Liefert die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000 zur�ck, oder null, falls kein FormularMax gestartet wurde.
   * 
   * @return
   */
  public FormularMax4000 getCurrentFormularMax4000()
  {
    return currentMax4000;
  }

  /**
   * Liefert den Baum der Dokumentkommandos zu diesem Dokuments in einem nicht
   * zwangsweise aktualisierten Zustand. Der Zustand kann �ber die
   * update()-Methode des zur�ckgegebenen DocumentCommandTrees aktualisiert
   * werden.
   * 
   * @return Baum der Dokumentkommandos dieses Dokuments.
   */
  public DocumentCommandTree getDocumentCommandTree()
  {
    return docCmdTree;
  }

  /**
   * Liefert true, wenn das Dokument eine Vorlage ist oder wie eine Vorlage
   * behandelt werden soll, ansonsten false.
   * 
   * @return true, wenn das Dokument eine Vorlage ist oder wie eine Vorlage
   *         behandelt werden soll, ansonsten false.
   */
  public boolean isTemplate()
  {
    if (setTypeCommand != null)
    {
      if (setTypeCommand.getType().equalsIgnoreCase("normalTemplate"))
        return true;
      else if (setTypeCommand.getType().equalsIgnoreCase("templateTemplate"))
        return false;
      else if (setTypeCommand.getType().equalsIgnoreCase("formDocument"))
        return false;
    }

    // Das Dokument ist automatisch eine Vorlage, wenn es keine zugeh�rige URL
    // gibt (dann steht ja in der Fenster�berschrift auch "Unbenannt1" statt
    // einem konkreten Dokumentnamen).
    return !hasURL();
  }

  /**
   * liefert true, wenn das Dokument eine URL besitzt, die die Quelle des
   * Dokuments beschreibt und es sich damit um ein in OOo im "Bearbeiten"-Modus
   * ge�ffnetes Dokument handelt oder false, wenn das Dokument keine URL besitzt
   * und es sich damit um eine Vorlage handelt.
   * 
   * @return liefert true, wenn das Dokument eine URL besitzt, die die Quelle
   *         des Dokuments beschreibt und es sich damit um ein in OOo im
   *         "Bearbeiten"-Modus ge�ffnetes Dokument handelt oder false, wenn das
   *         Dokument keine URL besitzt und es sich damit um eine Vorlage
   *         handelt.
   */
  public boolean hasURL()
  {
    return doc.getURL() != null && !doc.getURL().equals("");
  }

  /**
   * Liefert true, wenn das Dokument vom Typ formDocument ist ansonsten false.
   * ACHTUNG: Ein Dokument k�nnte theoretisch mit einem WM(CMD'setType'
   * TYPE'formDocument') Kommandos als Formulardokument markiert seine, OHNE
   * eine g�ltige Formularbeschreibung zu besitzen. Dies kann mit der Methode
   * hasFormDescriptor() gepr�ft werden.
   * 
   * @return Liefert true, wenn das Dokument vom Typ formDocument ist ansonsten
   *         false.
   */
  public boolean isFormDocument()
  {
    return (setTypeCommand != null && setTypeCommand.getType()
        .equalsIgnoreCase("formDocument"));
  }

  /**
   * Liefert true, wenn das Dokument ein Formular mit einer g�ltigen
   * Formularbeschreibung enth�lt und damit die Dokumentkommandos des
   * Formularsystems bearbeitet werden sollen.
   * 
   * @return true, wenn das Dokument ein Formular mit einer g�ltigen
   *         Formularbeschreibung ist, ansonsten false.
   */
  public boolean hasFormDescriptor()
  {
    if (formDescriptor != null) return !formDescriptor.isEmpty();

    return false;
  }

  /**
   * Setzt das setTypeCommand dieses Dokuments, falls ein solches existiert.
   * 
   * @param setTypeCommand
   */
  public void setTypeCommand(DocumentCommand.SetType setTypeCommand)
  {
    this.setTypeCommand = setTypeCommand;
  }

  /**
   * Setzt die zu diesem Dokument zugeh�rige Formularbeschreibung auf
   * formDescriptor.
   * 
   * @param formDescriptor
   */
  public void setFormDescriptor(FormDescriptor formDescriptor)
  {
    this.formDescriptor = formDescriptor;
  }

  /**
   * Diese Methode setzt die diesem TextDocumentModel zugeh�rige Druckfunktion
   * auf den Wert functionName, der ein g�ltiger Funktionsbezeichner sein muss
   * oder setzt eine Druckfunktion auf den davor zuletzt gesetzten Wert zur�ck,
   * wenn functionName der Leerstring ist und fr�her bereits eine Druckfunktion
   * registriert war. War fr�her noch keine Druckfunktion registriert, so wird
   * das entsprechende setPrintFunction-Dokumentkommando aus dem Dokument
   * gel�scht.
   * 
   * @param printFunctionName
   *          der Name der Druckfunktion (zum setzen) oder der Leerstring (zum
   *          l�schen bzw. zur�cksetzen). Der zu setzende Name muss ein g�ltiger
   *          Funktionsbezeichner sein und in einem Abschnitt "Druckfunktionen"
   *          in der wollmux.conf definiert sein.
   */
  public void setPrintFunctionName(String printFunctionName)
  {
    // Bei null oder Leerstring: Name der vorhergehenden Druckfunktion
    // verwenden.
    if (printFunctionName == null || printFunctionName.equals(""))
      printFunctionName = formerPrintFunctionName;

    // Name der bisherigen Druckfunktion in formerPrintFunctionName sichern.
    formerPrintFunctionName = null;
    if (printFunction != null)
      formerPrintFunctionName = printFunction.getFunctionName();

    if (printFunction == null && printFunctionName != null)
    {
      // Neues Dokumentkommando anlegen wenn noch nicht definiert.
      printFunction = new SetPrintFunction(doc, printFunctionName);
    }
    else if (printFunction != null && printFunctionName != null)
    {
      // ansonsten den Namen auf printFunctionName �ndern.
      printFunction.setFunctionName(printFunctionName);
      printFunction.updateBookmark(false);
    }
    else if (printFunction != null && printFunctionName == null)
    {
      // Bestehendes Dokumentkommando l�schen
      printFunction.setDoneState(true);
      printFunction.updateBookmark(false);
      printFunction = null;
    }
  }

  /**
   * Wird vom DocumentCommandInterpreter beim parsen des Dokumentkommandobaumes
   * aufgerufen, wenn das Dokument ein setPrintFunction-Kommando enth�lt - das
   * entsprechende Kommando cmd wird im Model abgespeichert und die relevante
   * Information kann sp�ter �ber getPrintFunctionName() erfragt werden.
   * 
   * @param cmd
   *          Das gefundene setPrintFunction-Dokumentkommando.
   */
  public void setPrintFunction(SetPrintFunction cmd)
  {
    // Name der bisherigen Druckfunktion in formerPrintFunctionName sichern.
    formerPrintFunctionName = null;
    if (printFunction != null)
      formerPrintFunctionName = printFunction.getFunctionName();

    printFunction = cmd;
  }

  /**
   * Liefert den Namen der aktuellen Druckfunktion, falls das Dokument ein
   * entsprechendes Dokumentkomando enth�lt oder eine Druckfunktion mit
   * setPrintFunctionName()-Methode gesetz wurde; ist keine Druckfunktion
   * definiert, so wird null zur�ck geliefert.
   */
  public String getPrintFunctionName()
  {
    if (printFunction != null) return printFunction.getFunctionName();
    return null;
  }

  /**
   * F�gt der Liste der NotInOriginal-Kommandos dieses Dokuments ein weiteres
   * Dokumentkommando cmd dieses Typs hinzu.
   * 
   * @param cmd
   *          das hinzuzuf�gende Dokumentkommando
   */
  public void addNotInOriginalBlock(DocumentCommand.NotInOriginal cmd)
  {
    notInOriginalBlocks.add(cmd);
  }

  /**
   * F�gt der Liste der DraftOnly-Kommandos dieses Dokuments ein weiteres
   * Dokumentkommando cmd dieses Typs hinzu.
   * 
   * @param cmd
   *          das hinzuzuf�gende Dokumentkommando
   */
  public void addDraftOnlyBlock(DocumentCommand.DraftOnly cmd)
  {
    draftOnlyBlocks.add(cmd);
  }

  /**
   * Liefert einen Iterator zur�ck, der die Iteration aller
   * NotInOrininal-Dokumentkommandos dieses Dokuments erm�glicht.
   * 
   * @return ein Iterator, der die Iteration aller
   *         NotInOrininal-Dokumentkommandos dieses Dokuments erm�glicht. Der
   *         Iterator kann auch keine Elemente enthalten.
   */
  public Iterator getNotInOrininalBlocksIterator()
  {
    return notInOriginalBlocks.iterator();
  }

  /**
   * Liefert einen Iterator zur�ck, der die Iteration aller
   * DraftOnly-Dokumentkommandos dieses Dokuments erm�glicht.
   * 
   * @return ein Iterator, der die Iteration aller DraftOnly-Dokumentkommandos
   *         dieses Dokuments erm�glicht. Der Iterator kann auch keine Elemente
   *         enthalten.
   */
  public Iterator getDraftOnlyBlocksIterator()
  {
    return draftOnlyBlocks.iterator();
  }

  /**
   * Liefert den ViewCursor des aktuellen Dokuments oder null, wenn kein
   * Controller (oder auch kein ViewCursor) f�r das Dokument verf�gbar ist.
   * 
   * @return Liefert den ViewCursor des aktuellen Dokuments oder null, wenn kein
   *         Controller (oder auch kein ViewCursor) f�r das Dokument verf�gbar
   *         ist.
   */
  public XTextCursor getViewCursor()
  {
    if (UNO.XModel(doc) == null) return null;
    XTextViewCursorSupplier suppl = UNO.XTextViewCursorSupplier(UNO.XModel(doc)
        .getCurrentController());
    if (suppl != null) return suppl.getViewCursor();
    return null;
  }

  /**
   * Setzt das Fensters des TextDokuments auf Sichtbar (visible==true) oder
   * unsichtbar (visible == false).
   * 
   * @param visible
   */
  public void setWindowVisible(boolean visible)
  {
    XModel xModel = UNO.XModel(doc);
    if (xModel != null)
    {
      XFrame frame = xModel.getCurrentController().getFrame();
      if (frame != null)
      {
        frame.getContainerWindow().setVisible(visible);
      }
    }
  }

  /**
   * Diese Methode setzt den DocumentModified-Status auf state.
   * 
   * @param state
   */
  public void setDocumentModified(boolean state)
  {
    try
    {
      UNO.XModifiable(doc).setModified(state);
    }
    catch (java.lang.Exception x)
    {
    }
  }

  /**
   * Diese Methode blockt/unblocked die Contoller, die f�r das Rendering der
   * Darstellung in den Dokumenten zust�ndig sind, jedoch nur, wenn nicht der
   * debug-modus gesetzt ist.
   * 
   * @param state
   */
  public void setLockControllers(boolean lock)
  {
    try
    {
      if (WollMuxSingleton.getInstance().isDebugMode() == false
          && UNO.XModel(doc) != null)
      {
        if (lock)
          UNO.XModel(doc).lockControllers();
        else
          UNO.XModel(doc).unlockControllers();
      }
    }
    catch (java.lang.Exception e)
    {
    }
  }

  /**
   * Setzt die Position des Fensters auf die �bergebenen Koordinaten, wobei die
   * Nachteile der UNO-Methode setWindowPosSize greifen, bei der die
   * Fensterposition nicht mit dem �usseren Fensterrahmen beginnt, sondern mit
   * der grauen Ecke links �ber dem File-Men�.
   * 
   * @param docX
   * @param docY
   * @param docWidth
   * @param docHeight
   */
  public void setWindowPosSize(int docX, int docY, int docWidth, int docHeight)
  {
    try
    {
      UNO.XModel(doc).getCurrentController().getFrame().getContainerWindow()
          .setPosSize(docX, docY, docWidth, docHeight, PosSize.POSSIZE);
    }
    catch (java.lang.Exception e)
    {
    }
  }

  /**
   * Diese Methode setzt den ZoomTyp bzw. den ZoomValue der Dokumentenansicht
   * des Dokuments auf den neuen Wert zoom, der entwender eine ganzzahliger
   * Prozentwert (ohne "%"-Zeichen") oder einer der Werte "Optimal",
   * "PageWidth", "PageWidthExact" oder "EntirePage" ist.
   * 
   * @param zoom
   * @throws ConfigurationErrorException
   */
  private void setDocumentZoom(String zoom) throws ConfigurationErrorException
  {
    Short zoomType = null;
    Short zoomValue = null;

    if (zoom != null)
    {
      // ZOOM-Argument auswerten:
      if (zoom.equalsIgnoreCase("Optimal"))
        zoomType = new Short(DocumentZoomType.OPTIMAL);

      if (zoom.equalsIgnoreCase("PageWidth"))
        zoomType = new Short(DocumentZoomType.PAGE_WIDTH);

      if (zoom.equalsIgnoreCase("PageWidthExact"))
        zoomType = new Short(DocumentZoomType.PAGE_WIDTH_EXACT);

      if (zoom.equalsIgnoreCase("EntirePage"))
        zoomType = new Short(DocumentZoomType.ENTIRE_PAGE);

      if (zoomType == null)
      {
        try
        {
          zoomValue = new Short(zoom);
        }
        catch (NumberFormatException e)
        {
        }
      }
    }

    // ZoomType bzw ZoomValue setzen:
    Object viewSettings = null;
    try
    {
      viewSettings = UNO.XViewSettingsSupplier(doc.getCurrentController())
          .getViewSettings();
    }
    catch (java.lang.Exception e)
    {
    }
    if (zoomType != null)
      UNO.setProperty(viewSettings, "ZoomType", zoomType);
    else if (zoomValue != null)
      UNO.setProperty(viewSettings, "ZoomValue", zoomValue);
    else
      throw new ConfigurationErrorException("Ung�ltiger ZOOM-Wert '"
                                            + zoom
                                            + "'");
  }

  /**
   * Diese Methode liest die (optionalen) Attribute X, Y, WIDTH, HEIGHT und ZOOM
   * aus dem �bergebenen Konfigurations-Abschnitt settings und setzt die
   * Fenstereinstellungen des Dokuments entsprechend um. Bei den P�rchen X/Y
   * bzw. SIZE/WIDTH m�ssen jeweils beide Komponenten im Konfigurationsabschnitt
   * angegeben sein.
   * 
   * @param settings
   *          der Konfigurationsabschnitt, der X, Y, WIDHT, HEIGHT und ZOOM als
   *          direkte Kinder enth�lt.
   */
  public void setWindowViewSettings(ConfigThingy settings)
  {
    // Fenster holen (zum setzen der Fensterposition und des Zooms)
    XWindow window = null;
    try
    {
      window = UNO.XModel(doc).getCurrentController().getFrame()
          .getContainerWindow();
    }
    catch (java.lang.Exception e)
    {
    }

    // Insets bestimmen (Rahmenma�e des Windows)
    int insetLeft = 0, insetTop = 0, insetRight = 0, insetButtom = 0;
    if (UNO.XDevice(window) != null)
    {
      DeviceInfo di = UNO.XDevice(window).getInfo();
      insetButtom = di.BottomInset;
      insetTop = di.TopInset;
      insetRight = di.RightInset;
      insetLeft = di.LeftInset;
    }

    // Position setzen:
    try
    {
      int xPos = new Integer(settings.get("X").toString()).intValue();
      int yPos = new Integer(settings.get("Y").toString()).intValue();
      if (window != null)
      {
        window.setPosSize(xPos + insetLeft, yPos + insetTop, 0, 0, PosSize.POS);
      }
    }
    catch (java.lang.Exception e)
    {
    }
    // Dimensions setzen:
    try
    {
      int width = new Integer(settings.get("WIDTH").toString()).intValue();
      int height = new Integer(settings.get("HEIGHT").toString()).intValue();
      if (window != null)
        window.setPosSize(
            0,
            0,
            width - insetLeft - insetRight,
            height - insetTop - insetButtom,
            PosSize.SIZE);
    }
    catch (java.lang.Exception e)
    {
    }

    // Zoom setzen:
    setDocumentZoom(settings);
  }

  /**
   * Diese Methode setzt den ZoomTyp bzw. den ZoomValue der Dokumentenansicht
   * des Dokuments auf den neuen Wert den das ConfigThingy conf im Knoten ZOOM
   * angibt, der entwender eine ganzzahliger Prozentwert (ohne "%"-Zeichen")
   * oder einer der Werte "Optimal", "PageWidth", "PageWidthExact" oder
   * "EntirePage" ist.
   * 
   * @param zoom
   * @throws ConfigurationErrorException
   */
  public void setDocumentZoom(ConfigThingy conf)
  {
    try
    {
      setDocumentZoom(conf.get("ZOOM").toString());
    }
    catch (NodeNotFoundException e)
    {
      // ZOOM ist optional
    }
    catch (ConfigurationErrorException e)
    {
      Logger.error(e);
    }
  }

  /**
   * Druckt das Dokument mit der Anzahl von Ausfertigungen numberOfCopies auf
   * dem aktuell eingestellten Drucker aus.
   * 
   * @param numberOfCopies
   */
  public void print(short numberOfCopies)
  {
    if (UNO.XPrintable(doc) != null)
    {
      PropertyValue[] args = new PropertyValue[] {
                                                  new PropertyValue(),
                                                  new PropertyValue() };
      args[0].Name = "CopyCount";
      args[0].Value = new Short(numberOfCopies);
      args[1].Name = "Wait";
      args[1].Value = Boolean.TRUE;

      try
      {
        UNO.XPrintable(doc).print(args);
      }
      catch (java.lang.Exception e)
      {
        // FIXME: evtl. Exception werfen?
        Logger.error("Drucken schlug fehl!", e);
      }
    }
  }

  /**
   * Schliesst das XTextDocument, das diesem Model zugeordnet ist. Ist der
   * closeListener registriert (was WollMuxSingleton bereits bei der Erstellung
   * des TextDocumentModels standardm�ig macht), so wird nach dem close() auch
   * automatisch die dispose()-Methode aufgerufen.
   */
  public void close()
  {
    // Damit OOo vor dem Schlie�en eines ver�nderten Dokuments den
    // save/dismiss-Dialog anzeigt, muss die suspend()-Methode aller
    // XController gestartet werden, die das Model der Komponente enthalten.
    // Man bekommt alle XController �ber die Frames, die der Desktop liefert.
    Object desktop = UNO.createUNOService("com.sun.star.frame.Desktop");
    if (UNO.XFramesSupplier(desktop) != null)
    {
      XFrame[] frames = UNO.XFramesSupplier(desktop).getFrames().queryFrames(
          FrameSearchFlag.ALL);
      for (int i = 0; i < frames.length; i++)
      {
        XController c = frames[i].getController();
        if (c != null && UnoRuntime.areSame(c.getModel(), doc))
          c.suspend(true);
      }
    }

    // Hier das eigentliche Schlie�en:
    try
    {
      if (UNO.XCloseable(doc) != null) UNO.XCloseable(doc).close(true);
    }
    catch (CloseVetoException e)
    {
    }
  }

  /**
   * Ruft die Dispose-Methoden von allen aktiven, dem TextDocumentModel
   * zugeordneten Dialogen auf und gibt den Speicher des TextDocumentModels
   * frei.
   */
  public void dispose()
  {
    if (currentMax4000 != null) currentMax4000.dispose();
    currentMax4000 = null;

    if (formModel != null) formModel.dispose();
    formModel = null;

    // L�scht das TextDocumentModel von doc aus dem WollMux-Singleton.
    WollMuxSingleton.getInstance().disposedTextDocument(doc);
  }

  /**
   * Registriert genau einen XCloseListener in der Komponente des
   * XTextDocuments, so dass beim Schlie�en des Dokuments die entsprechenden
   * WollMuxEvents ausgef�hrt werden - ist in diesem TextDocumentModel bereits
   * ein XCloseListener registriert, so wird nichts getan.
   */
  private void registerCloseListener()
  {
    if (closeListener == null && UNO.XCloseable(doc) != null)
    {
      closeListener = new XCloseListener()
      {
        public void disposing(EventObject arg0)
        {
          WollMuxEventHandler.handleTextDocumentClosed(doc);
        }

        public void notifyClosing(EventObject arg0)
        {
          WollMuxEventHandler.handleTextDocumentClosed(doc);
        }

        public void queryClosing(EventObject arg0, boolean arg1)
            throws CloseVetoException
        {
        }
      };
      UNO.XCloseable(doc).addCloseListener(closeListener);
    }
  }

  /**
   * Registriert einen WollMuxDispatchProvider im Frame des Dokuments genau
   * einmal und merkt sich den registrierten WollMuxDispatchProvider im model.
   */
  private void registerWollMuxDispatchInterceptor()
  {
    XFrame frame = null;
    try
    {
      frame = UNO.XModel(doc).getCurrentController().getFrame();
    }
    catch (java.lang.Exception e)
    {
    }

    if (dispatchInterceptor == null
        && UNO.XDispatchProviderInterception(frame) != null)
    {
      Logger.debug("Register WollMuxDispatchInterceptor for doc #"
                   + doc.hashCode()
                   + " with frame #"
                   + frame.hashCode());
      dispatchInterceptor = new WollMuxDispatchInterceptor();
      UNO.XDispatchProviderInterception(frame)
          .registerDispatchProviderInterceptor(dispatchInterceptor);
    }
  }

  /**
   * Diese Klasse erm�glicht es dem WollMux, dispatch-Kommandos wie z.B.
   * .uno:Print abzufangen und statt dessen eigene Aktionen durchzuf�hren.
   * 
   * @author christoph.lutz
   */
  public static class WollMuxDispatchInterceptor implements
      XDispatchProviderInterceptor
  {

    private XDispatchProvider slave = null;

    private XDispatchProvider master = null;

    public XDispatchProvider getSlaveDispatchProvider()
    {
      return slave;
    }

    public void setSlaveDispatchProvider(XDispatchProvider slave)
    {
      this.slave = slave;
    }

    public XDispatchProvider getMasterDispatchProvider()
    {
      return master;
    }

    public void setMasterDispatchProvider(XDispatchProvider master)
    {
      this.master = master;
    }

    public XDispatch queryDispatch(com.sun.star.util.URL url, String frame,
        int frameSearchFlags)
    {
      String urlStr = url.Complete;

      // Logger.debug2("queryDispatch: '" + urlStr + "'");

      // Ab hier kommen Dispatches, die die URL in irgend einer Form
      // umschreiben:

      // -------------- Dispatch f�r wollmux:defaultUNOPrint --------------
      if (urlStr.equals("wollmux:defaultUnoPrint"))
      {
        Logger.debug("queryDispatch: '" + urlStr + "'");
        final URL unoPrintUrl = getParsedUNOUrl(".uno:Print");
        final XDispatch origDisp = slave.queryDispatch(
            unoPrintUrl,
            frame,
            frameSearchFlags);
        if (origDisp == null) return null;

        return new XDispatch()
        {
          public void removeStatusListener(XStatusListener arg0, URL arg1)
          {
            if (origDisp != null)
              origDisp.removeStatusListener(arg0, unoPrintUrl);
          }

          public void addStatusListener(XStatusListener arg0, URL arg1)
          {
            if (origDisp != null)
              origDisp.addStatusListener(arg0, unoPrintUrl);
          }

          public void dispatch(URL arg0, PropertyValue[] arg1)
          {
            if (origDisp != null) origDisp.dispatch(unoPrintUrl, arg1);
          }
        };
      }

      // ----------- Dispatch f�r wollmux:defaultUNOPrintDefault -----------
      if (urlStr.equals("wollmux:defaultUnoPrint"))
      {
        Logger.debug("queryDispatch: '" + urlStr + "'");
        final URL unoPrintUrl = getParsedUNOUrl(".uno:PrintDefault");
        final XDispatch origDisp = slave.queryDispatch(
            unoPrintUrl,
            frame,
            frameSearchFlags);
        if (origDisp == null) return null;

        return new XDispatch()
        {
          public void removeStatusListener(XStatusListener arg0, URL arg1)
          {
            if (origDisp != null)
              origDisp.removeStatusListener(arg0, unoPrintUrl);
          }

          public void addStatusListener(XStatusListener arg0, URL arg1)
          {
            if (origDisp != null)
              origDisp.addStatusListener(arg0, unoPrintUrl);
          }

          public void dispatch(URL arg0, PropertyValue[] arg1)
          {
            if (origDisp != null) origDisp.dispatch(unoPrintUrl, arg1);
          }
        };
      }

      // Ab hier kommen Dispatches, die die URL unver�ndert �bernehmen:
      final XDispatch origDisp = slave.queryDispatch(
          url,
          frame,
          frameSearchFlags);
      if (origDisp == null) return null;

      // ------------------ Dispatch f�r .uno:Print -------------------
      if (urlStr.equals(".uno:Print"))
      {
        Logger.debug("queryDispatch: '" + urlStr + "'");

        return new XDispatch()
        {
          public void dispatch(com.sun.star.util.URL arg0, PropertyValue[] arg1)
          {
            XTextDocument doc = UNO.XTextDocument(UNO.desktop
                .getCurrentComponent());
            if (doc != null) WollMuxEventHandler.handlePrintButtonPressed(doc);
          }

          public void removeStatusListener(XStatusListener arg0,
              com.sun.star.util.URL arg1)
          {
            if (origDisp != null) origDisp.removeStatusListener(arg0, arg1);
          }

          public void addStatusListener(XStatusListener arg0,
              com.sun.star.util.URL arg1)
          {
            if (origDisp != null) origDisp.addStatusListener(arg0, arg1);
          }
        };
      }

      // ------------------ Dispatch f�r .uno:PrintDefault -------------------
      if (urlStr.equals(".uno:PrintDefault"))
      {
        Logger.debug("queryDispatch: '" + urlStr + "'");

        return new XDispatch()
        {
          public void dispatch(com.sun.star.util.URL arg0, PropertyValue[] arg1)
          {
            XTextDocument doc = UNO.XTextDocument(UNO.desktop
                .getCurrentComponent());
            if (doc != null) WollMuxEventHandler.handlePrintButtonPressed(doc);
          }

          public void removeStatusListener(XStatusListener arg0,
              com.sun.star.util.URL arg1)
          {
            if (origDisp != null) origDisp.removeStatusListener(arg0, arg1);
          }

          public void addStatusListener(XStatusListener arg0,
              com.sun.star.util.URL arg1)
          {
            if (origDisp != null) origDisp.addStatusListener(arg0, arg1);
          }
        };
      }

      // Anfrage an das urspr�ngliche DispatchObjekt weiterleiten.
      boolean debug = true;
      return (debug) ? new ForwardDispatch(origDisp) : origDisp;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.star.frame.XDispatchProvider#queryDispatches(com.sun.star.frame.DispatchDescriptor[])
     */
    public XDispatch[] queryDispatches(DispatchDescriptor[] seqDescripts)
    {
      int nCount = seqDescripts.length;
      XDispatch[] lDispatcher = new XDispatch[nCount];

      for (int i = 0; i < nCount; ++i)
        lDispatcher[i] = queryDispatch(
            seqDescripts[i].FeatureURL,
            seqDescripts[i].FrameName,
            seqDescripts[i].SearchFlags);

      return lDispatcher;
    }
  }

  /**
   * Liefert eine vorgeparste UNO-URL von urlStr.
   * 
   * @param urlStr
   * @return vorgeparste UNO-URL von urlStr.
   */
  private static URL getParsedUNOUrl(String urlStr)
  {
    URL[] unoURL = new URL[] { new URL() };
    unoURL[0].Complete = urlStr;

    XURLTransformer trans = UNO.XURLTransformer(UNO
        .createUNOService("com.sun.star.util.URLTransformer"));
    if (trans != null) trans.parseStrict(unoURL);

    return unoURL[0];
  }

  /**
   * Der ForwardDispatch ist ein Dispatch-Handler f�r Testzwecke, der jede
   * Dispatch-Anfrag auf den Logger protokolliert, die Anfragen aber ansonsten
   * unver�ndert an den urspr�nglichen dispatch-Handler weiterreicht.
   * 
   * @author christoph.lutz
   * 
   */
  private static class ForwardDispatch implements XDispatch
  {
    private XDispatch orig;

    public ForwardDispatch(XDispatch orig)
    {
      this.orig = orig;
    }

    public void dispatch(com.sun.star.util.URL url, PropertyValue[] args)
    {
      Logger.debug2(ForwardDispatch.class.getName()
                    + ".dispatch('"
                    + url.Complete
                    + "')");
      orig.dispatch(url, args);
    }

    public void addStatusListener(XStatusListener listener,
        com.sun.star.util.URL url)
    {
      Logger.debug2(ForwardDispatch.class.getName()
                    + ".addStatusListener('"
                    + listener.hashCode()
                    + "')");
      orig.addStatusListener(listener, url);
    }

    public void removeStatusListener(XStatusListener listener,
        com.sun.star.util.URL url)
    {
      Logger.debug2(ForwardDispatch.class.getName()
                    + ".removeStatusListener('"
                    + listener.hashCode()
                    + "')");
      orig.removeStatusListener(listener, url);
    }
  }

  /**
   * Liefert das zu diesem TextDocumentModel zugeh�rige XPrintModel.
   */
  public XPrintModel getPrintModel()
  {
    return printModel;
  }

  /**
   * Das XPrintModel ist Bestandteil der Komfortdruckfunktionen, wobei jede
   * Druckfunktion ein XPrintModel �bergeben bekommt, das das Drucken aus der
   * Komfortdruckfunktion heraus erleichtern soll. Da die einzelnen
   * Druckfunktionen in eigenen Threads laufen, muss jede Druckfunktion sicher
   * stellen, dass die zu erledigenden Aktionen mit dem
   * WollMuxEventHandler-Thread synchronisiert werden. Dies geschieht �ber einen
   * lock-wait-callback-Mechanismus. Vor dem Einstellen des Action-Ereignisses
   * in den WollMuxEventHandler wird ein lock gesetzt. Nach dem Einstellen des
   * Ereignisses wird so lange gewartet, bis der WollMuxEventHandler die
   * �bergebene Callback-Methode aufruft.
   * 
   * @author christoph.lutz
   */
  public class PrintModel implements XPrintModel
  {
    /**
     * Das lock-Flag, das vor dem Einstellen eines WollMuxEvents auf true
     * gesetzt werden muss und signalisiert, ob das WollMuxEvent erfolgreich
     * abgearbeitet wurde.
     */
    private boolean[] lock = new boolean[] { true };

    /**
     * Liefert das XTextDocument mit dem die Druckfunktion aufgerufen wurde.
     * 
     * @see de.muenchen.allg.itd51.wollmux.XPrintModel#getTextDocument()
     */
    public XTextDocument getTextDocument()
    {
      return doc;
    }

    /**
     * Druckt das TextDocument mit numberOfCopies Ausfertigungen auf dem aktuell
     * eingestellten Drucker aus.
     * 
     * @see de.muenchen.allg.itd51.wollmux.XPrintModel#print(short)
     */
    public void print(short numberOfCopies)
    {
      setLock();
      WollMuxEventHandler
          .handlePrint(doc, numberOfCopies, unlockActionListener);
      waitForUnlock();
    }

    /**
     * Falls das TextDocument Sachleitende Verf�gungen enth�lt, ist es mit
     * dieser Methode m�glich, den Verf�gungspunkt mit der Nummer verfPunkt
     * auszudrucken, wobei alle darauffolgenden Verf�gungspunkte ausgeblendet
     * werden.
     * 
     * @param verfPunkt
     *          Die Nummer des auszuduruckenden Verf�gungspunktes, wobei alle
     *          folgenden Verf�gungspunkte ausgeblendet werden.
     * @param numberOfCopies
     *          Die Anzahl der Ausfertigungen, in der verfPunkt ausgedruckt
     *          werden soll.
     * @param isDraft
     *          wenn isDraft==true, werden alle draftOnly-Bl�cke eingeblendet,
     *          ansonsten werden sie ausgeblendet.
     * @param isOriginal
     *          wenn isOriginal, wird die Ziffer des Verf�gungspunktes I
     *          ausgeblendet und alle notInOriginal-Bl�cke ebenso. Andernfalls
     *          sind Ziffer und notInOriginal-Bl�cke eingeblendet.
     * @see de.muenchen.allg.itd51.wollmux.XPrintModel#printVerfuegungspunkt(short,
     *      short, boolean, boolean)
     */
    public void printVerfuegungspunkt(short verfPunkt, short numberOfCopies,
        boolean isDraft, boolean isOriginal)
    {
      setLock();
      WollMuxEventHandler.handlePrintVerfuegungspunkt(
          doc,
          verfPunkt,
          numberOfCopies,
          isDraft,
          isOriginal,
          unlockActionListener);
      waitForUnlock();
    }

    /**
     * Zeigt den PrintSettings-Dialog an, �ber den der aktuelle Drucker
     * ausgew�hlt und ge�ndert werden kann.
     * 
     * @param onlyOnce
     *          Gibt an, dass der Dialog nur beim ersten Aufruf der Methode
     *          angezeigt wird. Wurde bereits vor dem Aufruf ein
     *          PrintSetup-Dialog gestartet, so �ffnet sich der Dialog nicht und
     *          die Methode endet ohne Aktion.
     * @see de.muenchen.allg.itd51.wollmux.XPrintModel#showPrintSettingsOnce()
     */
    public void showPrintSetup(boolean onlyOnce)
    {
      XDispatchProvider dispProv = null;
      try
      {
        dispProv = UNO.XDispatchProvider(UNO.XModel(doc).getCurrentController()
            .getFrame());
      }
      catch (java.lang.Exception e)
      {
      }

      if (dispProv != null)
      {
        URL url = getParsedUNOUrl(".uno:PrinterSetup");
        XDispatch disp = dispProv.queryDispatch(
            url,
            "_self",
            com.sun.star.frame.FrameSearchFlag.SELF);

        Logger.debug2("Dispatch .uno:PrinterSetup");

        if (disp != null) disp.dispatch(url, new PropertyValue[] {});

        Logger.debug2("Dispatch .uno:PrinterSetup returned");
      }
    }

    /**
     * Setzt einen lock, der in Verbindung mit setUnlock und der
     * waitForUnlock-Methode verwendet werden kann, um eine Synchronisierung mit
     * dem WollMuxEventHandler-Thread zu realisieren. setLock() sollte stets vor
     * dem Absetzen des WollMux-Events erfolgen, nach dem Absetzen des
     * WollMux-Events folgt der Aufruf der waitForUnlock()-Methode. Das
     * WollMuxEventHandler-Event erzeugt bei der Beendigung ein ActionEvent, das
     * daf�r sorgt, dass setUnlock aufgerufen wird.
     */
    protected void setLock()
    {
      lock[0] = true;
    }

    /**
     * Macht einen mit setLock() gesetzten Lock r�ckg�ngig und bricht damit eine
     * evtl. wartende waitForUnlock()-Methode ab.
     */
    protected void setUnlock()
    {
      synchronized (lock)
      {
        lock[0] = false;
        lock.notifyAll();
      }
    }

    /**
     * Wartet so lange, bis der vorher mit setLock() gesetzt lock mit der
     * Methode setUnlock() aufgehoben wird. So kann die Synchronisierung mit
     * Events aus dem WollMuxEventHandler-Thread realisiert werden. setLock()
     * sollte stets vor dem Aufruf des Events erfolgen, nach dem Aufruf des
     * Events folgt der Aufruf der waitForUnlock()-Methode. Das Event erzeugt
     * bei der Beendigung ein ActionEvent, das daf�r sorgt, dass setUnlock
     * aufgerufen wird.
     */
    protected void waitForUnlock()
    {
      try
      {
        synchronized (lock)
        {
          while (lock[0] == true)
            lock.wait();
        }
      }
      catch (InterruptedException e)
      {
      }
    }

    /**
     * Dieser ActionListener kann WollMuxHandler-Events �bergeben werden und
     * sorgt in Verbindung mit den Methoden setLock() und waitForUnlock() daf�r,
     * dass eine Synchronisierung mit dem WollMuxEventHandler-Thread realisiert
     * werden kann.
     */
    protected UnlockActionListener unlockActionListener = new UnlockActionListener();

    protected class UnlockActionListener implements ActionListener
    {
      public ActionEvent actionEvent = null;

      public void actionPerformed(ActionEvent arg0)
      {
        setUnlock();
        actionEvent = arg0;
      }
    }

  }

}
