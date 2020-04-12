package org.micromanager.internal.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableCellEditor;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public final class ChannelCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = -8374637422965302637L;
   private JTextField text_ = new JTextField();
   private JComboBox<String> channelSelect_ = new JComboBox<>();
   private JCheckBox checkBox_ = new JCheckBox();
   boolean checkBoxValue_ = false;
   private JLabel colorLabel_ = new JLabel();
   private int editCol_ = -1;
   private int editRow_ = -1;
   private ChannelSpec channel_ = null;
   private final CheckBoxChangeListener checkBoxChangeListener_;
   private AcquisitionEngine acqEng_;

   public ChannelCellEditor(AcquisitionEngine engine) {
      acqEng_ = engine;
      checkBoxChangeListener_ = new CheckBoxChangeListener(this);
   }

   // This method is called when a cell value is edited by the user.
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
           boolean isSelected, int rowIndex, int colIndex) {

      ChannelTableModel model = (ChannelTableModel) table.getModel();
      ArrayList<ChannelSpec> channels = model.getChannels();
      final ChannelSpec channel = channels.get(rowIndex);
      channel_ = channel;

      colIndex = table.convertColumnIndexToModel(colIndex);

      // Configure the component with the specified value
      editRow_ = rowIndex;
      editCol_ = colIndex;
      if (colIndex == 0) {
         checkBox_.removeChangeListener(checkBoxChangeListener_);
         checkBoxValue_ = (Boolean) value;
         checkBox_.setSelected(checkBoxValue_);
         checkBox_.addChangeListener(checkBoxChangeListener_);
         return checkBox_;
      } else if (colIndex == 2 || colIndex == 3) {
         // exposure and z offset
         text_.setText(NumberUtils.doubleToDisplayString((Double)value));
         return text_;
      } else if (colIndex == 4) {
         checkBox_.setSelected((Boolean) value);
         return checkBox_;
      } else if (colIndex == 5) {
         // skip
         text_.setText(NumberUtils.intToDisplayString((Integer) value));
         return text_;
      } else if (colIndex == 1) {
         // channel
         channelSelect_.removeAllItems();

         // remove old listeners
         ActionListener[] listeners = channelSelect_.getActionListeners();
         for (ActionListener listener : listeners) {
            channelSelect_.removeActionListener(listener);
         }
         channelSelect_.removeAllItems();

         // Only allow channels that aren't already selected in a different
         // row.
         HashSet<String> usedChannels = new HashSet<>();
         for (int i = 0; i < model.getChannels().size(); ++i) {
            if (i != editRow_) {
               usedChannels.add((String) model.getValueAt(i, 1));
            }
         }
         String[] configs = model.getAvailableChannels();
         for (String config : configs) {
            if (!usedChannels.contains(config)) {
               channelSelect_.addItem(config);
            }
         }
         channelSelect_.setSelectedItem(channel.config);
         
         // end editing on selection change
         channelSelect_.addActionListener(e -> {
            // Our fallback color is the colorblind-friendly color for our
            // current row index.
            channel_.color = new Color(AcqControlDlg.getChannelColor(
                    MMStudio.getInstance(),
                  acqEng_.getChannelGroup(),
                  (String) channelSelect_.getSelectedItem(),
                  ColorPalettes.getFromDefaultPalette(editRow_).getRGB()));
            channel_.exposure = AcqControlDlg.getChannelExposure(
               acqEng_.getChannelGroup(),
               (String) channelSelect_.getSelectedItem(), 10.0);
            fireEditingStopped();
         });

         // Return the configured component
         return channelSelect_;
      } else {
         // ColorEditor takes care of this
         return colorLabel_;
      }
   }

   /** 
    * This method is called when editing is completed.
    * It must return the new value to be stored in the cell.
    */
   @Override
   public Object getCellEditorValue() {
      // TODO: if content of column does not match type we get an exception
      try {
         if (editCol_ == 0) {
            return checkBox_.isSelected();
         } else if (editCol_ == 1) {
            // As a side effect, change to the color and exposure of the new
            // channel. If no color is available, use the "next" colorblind-
            // friendly color, based on our row index.
            channel_.color = new Color(AcqControlDlg.getChannelColor(
                    MMStudio.getInstance(),
                     acqEng_.getChannelGroup(),
                     (String) channelSelect_.getSelectedItem(),
                     ColorPalettes.getFromDefaultPalette(editRow_).getRGB()));
            channel_.exposure = AcqControlDlg.getChannelExposure(
                  acqEng_.getChannelGroup(), channel_.config, 10.0);
            return channelSelect_.getSelectedItem();
         } else if (editCol_ == 2 || editCol_ == 3) {
            return NumberUtils.displayStringToDouble(text_.getText());
         } else if (editCol_ == 4) {
            return checkBox_.isSelected();
         } else if (editCol_ == 5) {
            return NumberUtils.displayStringToInt(text_.getText());
         } else if (editCol_ == 6) {
            return colorLabel_.getBackground();
         } else {
            return "Internal error: unknown column";
         }
      } catch (ParseException p) {
         ReportingUtils.showError(p);
      }
      return "Internal error: unknown column";
   }

   private class CheckBoxChangeListener implements ChangeListener {
      private final ChannelCellEditor cce_;
      public CheckBoxChangeListener(ChannelCellEditor cce) {
         cce_ = cce;
      }
      @Override
      public void stateChanged(ChangeEvent e) {
         if (checkBox_.isSelected() != checkBoxValue_) {
            cce_.fireEditingStopped();
            // avoid calling fireEditingStopped multiple times:
            checkBoxValue_ = checkBox_.isSelected();
         }
      }
   }
}
