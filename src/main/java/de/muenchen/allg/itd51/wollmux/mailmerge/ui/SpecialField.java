package de.muenchen.allg.itd51.wollmux.mailmerge.ui;

import com.sun.star.awt.XComboBox;

import de.muenchen.allg.afid.UNO;

/**
 * A wrapper for adding items to a {@link XComboBox}.
 */
public class SpecialField
{
  private SpecialField()
  {
    // nothing to do
  }

  /**
   * Adds some predefined items. The first one is selected.
   *
   * @param comboBox
   *          The {@link XComboBox}.
   */
  public static void addItems(XComboBox comboBox)
  {
    String[] items = new String[] { "Bitte wählen..", "Gender", "Wenn...Dann...Sonst",
        "Datensatznummer", "Serienbriefnummer", "Nächster Datensatz" };
    addItems(comboBox, items);
  }

  /**
   * Add some items. The first one is selected.
   *
   * @param comboBox
   *          The {@link XComboBox}.
   * @param items
   *          The items to set.
   */
  public static void addItems(XComboBox comboBox, String[] items)
  {
    comboBox.addItems(items, (short) 0);
    UNO.XTextComponent(comboBox).setText(comboBox.getItem((short) 0));
  }
}
