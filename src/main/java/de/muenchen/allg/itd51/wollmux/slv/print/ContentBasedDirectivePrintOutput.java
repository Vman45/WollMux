package de.muenchen.allg.itd51.wollmux.slv.print;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.container.XNameAccess;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;
import com.sun.star.text.XTextSection;
import com.sun.star.text.XTextSectionsSupplier;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.AnyConverter;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoHelperException;
import de.muenchen.allg.itd51.wollmux.XPrintModel;
import de.muenchen.allg.itd51.wollmux.core.util.Utils;
import de.muenchen.allg.itd51.wollmux.document.DocumentManager;
import de.muenchen.allg.itd51.wollmux.func.print.PrintFunction;
import de.muenchen.allg.itd51.wollmux.slv.ContentBasedDirectiveItem;
import de.muenchen.allg.itd51.wollmux.slv.ContentBasedDirectiveModel;
import de.muenchen.allg.itd51.wollmux.slv.PrintBlockSignature;
import de.muenchen.allg.itd51.wollmux.slv.dialog.ContentBasedDirectiveSettings;

/**
 * Print function for printing the directives specified in the property {link
 * {@link ContentBasedDirectivePrint#PROP_SLV_SETTINGS}.
 */
public class ContentBasedDirectivePrintOutput extends PrintFunction
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ContentBasedDirectivePrintOutput.class);

  private static final String EXCEPTION_MESSAGE = "Sichtbarkeit konnte nicht geändert werden.";

  private static final String IS_VISIBLE_PROPERTY = "IsVisible";

  /**
   * A {@link PrintFunction} with name "SachleitendeVerfuegungOutput" and order 150.
   */
  public ContentBasedDirectivePrintOutput()
  {
    super("SachleitendeVerfuegungOutput", 150);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void print(XPrintModel printModel)
  {
    List<ContentBasedDirectiveSettings> settings = new ArrayList<>();
    try
    {
      settings = (List<ContentBasedDirectiveSettings>) printModel
          .getPropertyValue(ContentBasedDirectivePrint.PROP_SLV_SETTINGS);
    } catch (java.lang.Exception e)
    {
      LOGGER.error("", e);
    }

    short countMax = 0;
    for (ContentBasedDirectiveSettings v : settings)
      countMax += v.getCopyCount();
    printModel.setPrintProgressMaxValue(countMax);

    short count = 0;
    for (ContentBasedDirectiveSettings v : settings)
    {
      if (printModel.isCanceled())
        return;
      if (v.getCopyCount() > 0)
      {
        printVerfuegungspunkt(printModel, v.directiveId, v.isDraft, v.isOriginal, v.getCopyCount());
      }
      count += v.getCopyCount();
      printModel.setPrintProgressValue(count);
    }
  }

  /**
   * Print the content based directive.
   *
   * @param pmod
   *          The {@link XPrintModel}
   * @param verfPunkt
   *          The number of the content based directive. All following items aren't printed.
   * @param isDraft
   *          If true, all blocks marked as {@link PrintBlockSignature#DRAFT_ONLY} are printed,
   *          otherwise not.
   * @param isOriginal
   *          If true, the number of the first content based directive and all all
   *          {@link PrintBlockSignature#NOT_IN_ORIGINAL} blocks are set invisible. Otherwise they are
   *          visible
   * @param copyCount
   *          The number of copies.
   */
  void printVerfuegungspunkt(XPrintModel pmod, int verfPunkt, boolean isDraft, boolean isOriginal,
      short copyCount)
  {
    XTextDocument doc = pmod.getTextDocument();
    ContentBasedDirectiveModel model = ContentBasedDirectiveModel
        .createModel(DocumentManager.getTextDocumentController(doc));

    // Save current cursor position
    XTextCursor vc = null;
    XTextCursor oldViewCursor = null;
    XTextViewCursorSupplier suppl = UNO
        .XTextViewCursorSupplier(UNO.XModel(pmod.getTextDocument()).getCurrentController());
    if (suppl != null)
      vc = suppl.getViewCursor();
    if (vc != null)
      oldViewCursor = vc.getText().createTextCursorByRange(vc);

    // Initialize counter
    ContentBasedDirectiveItem punkt1 = model.getFirstItem();
    int count = 0;
    if (punkt1 != null)
      count++;

    // Get invisible section
    XTextRange setInvisibleRange = getInvisibleRange(verfPunkt, doc, count);

    // Hide text sections in invisible area and remember their status
    List<XTextSection> hidingSections = getSectionsFromPosition(pmod.getTextDocument(),
        setInvisibleRange);
    HashMap<XTextSection, Boolean> sectionOldState = new HashMap<>();
    hideTextSections(hidingSections, sectionOldState);

    // ensprechende Verfügungspunkte ausblenden
    if (setInvisibleRange != null)
    {
      hideTextRange(setInvisibleRange, true);
    }

    // Show/Hide print blocks
    pmod.setPrintBlocksProps(PrintBlockSignature.DRAFT_ONLY.getName(), isDraft, false);
    pmod.setPrintBlocksProps(PrintBlockSignature.NOT_IN_ORIGINAL.getName(), !isOriginal, false);
    pmod.setPrintBlocksProps(PrintBlockSignature.ORIGINAL_ONLY.getName(), isOriginal, false);
    pmod.setPrintBlocksProps(PrintBlockSignature.ALL_VERSIONS.getName(), true, false);
    pmod.setPrintBlocksProps(PrintBlockSignature.COPY_ONLY.getName(), !isDraft && !isOriginal, false);

    // Show/Hide visibility groups
    pmod.setGroupVisible(PrintBlockSignature.DRAFT_ONLY.getGroupName(), isDraft);
    pmod.setGroupVisible(PrintBlockSignature.NOT_IN_ORIGINAL.getGroupName(), !isOriginal);
    pmod.setGroupVisible(PrintBlockSignature.ORIGINAL_ONLY.getGroupName(), isOriginal);
    pmod.setGroupVisible(PrintBlockSignature.ALL_VERSIONS.getGroupName(), true);
    pmod.setGroupVisible(PrintBlockSignature.COPY_ONLY.getGroupName(), !isDraft && !isOriginal);

    // hide first number if necessary
    setVisibilityFirst(isOriginal, punkt1, true);

    // print
    for (int j = 0; j < copyCount; ++j)
    {
      pmod.printWithProps();
    }

    // revert hiding of first number
    setVisibilityFirst(isOriginal, punkt1, false);

    // Show/Hide visibility groups
    pmod.setGroupVisible(PrintBlockSignature.DRAFT_ONLY.getGroupName(), true);
    pmod.setGroupVisible(PrintBlockSignature.NOT_IN_ORIGINAL.getGroupName(), true);
    pmod.setGroupVisible(PrintBlockSignature.ORIGINAL_ONLY.getGroupName(), true);
    pmod.setGroupVisible(PrintBlockSignature.ALL_VERSIONS.getGroupName(), true);
    pmod.setGroupVisible(PrintBlockSignature.COPY_ONLY.getGroupName(), true);

    // Restore old print block settings
    pmod.setPrintBlocksProps(PrintBlockSignature.DRAFT_ONLY.getName(), true, true);
    pmod.setPrintBlocksProps(PrintBlockSignature.NOT_IN_ORIGINAL.getName(), true, true);
    pmod.setPrintBlocksProps(PrintBlockSignature.ORIGINAL_ONLY.getName(), true, true);
    pmod.setPrintBlocksProps(PrintBlockSignature.ALL_VERSIONS.getName(), true, true);
    pmod.setPrintBlocksProps(PrintBlockSignature.COPY_ONLY.getName(), true, true);

    // Restore state of invisible text sections
    for (XTextSection section : hidingSections)
    {
      Boolean oldState = sectionOldState.get(section);
      if (oldState != null)
        Utils.setProperty(section, IS_VISIBLE_PROPERTY, oldState);
    }

    hideTextRange(setInvisibleRange, false);

    // reset cursor position
    if (vc != null && oldViewCursor != null)
      vc.gotoRange(oldViewCursor, false);
  }

  private void hideTextRange(XTextRange textRange, boolean hide)
  {
    if (textRange != null)
    {
      try
      {
        UNO.hideTextRange(textRange, hide);
      } catch (UnoHelperException e)
      {
        LOGGER.error(EXCEPTION_MESSAGE, e);
      }
    }
  }

  private void setVisibilityFirst(boolean isOriginal, ContentBasedDirectiveItem punkt1,
      boolean hide)
  {
    if (isOriginal && punkt1 != null)
    {
      hideTextRange(punkt1.getZifferOnly(), hide);
    }
  }

  private void hideTextSections(List<XTextSection> hidingSections,
      HashMap<XTextSection, Boolean> sectionOldState)
  {
    for (XTextSection section : hidingSections)
    {
      boolean oldState = AnyConverter.toBoolean(Utils.getProperty(section, IS_VISIBLE_PROPERTY));
      sectionOldState.put(section, oldState);
      Utils.setProperty(section, IS_VISIBLE_PROPERTY, Boolean.FALSE);
    }
  }

  private XTextRange getInvisibleRange(int verfPunkt, XTextDocument doc, int count)
  {
    XTextRange setInvisibleRange = null;
    XParagraphCursor cursor = UNO
        .XParagraphCursor(doc.getText().createTextCursorByRange(doc.getText().getStart()));
    ContentBasedDirectiveItem item = new ContentBasedDirectiveItem(cursor);
    if (cursor != null)
      do
      {
        cursor.gotoEndOfParagraph(true);

        if (item.isItem())
        {
          count++;

          if (count == (verfPunkt + 1))
          {
            cursor.collapseToStart();
            cursor.gotoRange(cursor.getText().getEnd(), true);
            setInvisibleRange = cursor;
          }
        }
      } while (setInvisibleRange == null && cursor.gotoNextParagraph(false));
    return setInvisibleRange;
  }

  /**
   * Get all text sections, which are at the same position or behind.
   *
   * @param doc
   *          The document to check.
   * @param pos
   *          The starting position to collect text sections.
   * @return List of all text sections behind the position. May be an empty list.
   */
  private List<XTextSection> getSectionsFromPosition(XTextDocument doc, XTextRange pos)
  {
    List<XTextSection> sectionList = new ArrayList<>();
    if (pos == null)
      return sectionList;
    XTextRangeCompare comp = UNO.XTextRangeCompare(pos.getText());
    if (comp == null)
      return sectionList;
    XTextSectionsSupplier suppl = UNO.XTextSectionsSupplier(doc);
    if (suppl == null)
      return sectionList;

    XNameAccess sections = suppl.getTextSections();
    String[] names = sections.getElementNames();
    for (int i = 0; i < names.length; i++)
    {
      XTextSection section = null;
      try
      {
        section = UNO.XTextSection(sections.getByName(names[i]));
      } catch (java.lang.Exception e)
      {
        LOGGER.error("", e);
      }

      if (section != null)
      {
        try
        {
          int diff = comp.compareRegionStarts(pos, section.getAnchor());
          if (diff >= 0)
            sectionList.add(section);
        } catch (IllegalArgumentException e)
        {
          // no errer, exception always occurs if the text ranges are in different text objects.
        }
      }
    }
    return sectionList;
  }
}