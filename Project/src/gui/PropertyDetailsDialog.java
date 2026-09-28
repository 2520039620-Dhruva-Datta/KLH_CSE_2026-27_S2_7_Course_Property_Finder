package gui;

import model.UnifiedProperty;

import javax.swing.*;
import java.awt.*;

public class PropertyDetailsDialog extends JDialog {

    public PropertyDetailsDialog(Frame owner, UnifiedProperty property) {
        super(owner, "Property Details", true);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 12);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        int row = 0;
        row = addField(fields, gbc, row, "Source", property.getSourceDisplayName());
        row = addField(fields, gbc, row, "Property", property.getTitle());
        row = addField(fields, gbc, row, "Locality", property.getLocality());
        row = addField(fields, gbc, row, "City", property.getCity());
        row = addField(fields, gbc, row, "State", property.getState());
        row = addField(fields, gbc, row, "Type", property.getPropertyType());
        row = addField(fields, gbc, row, "Configuration", property.getConfiguration());
        row = addField(fields, gbc, row, "Purpose", property.getPurpose());
        row = addField(fields, gbc, row, "Area", property.getArea());
        row = addField(fields, gbc, row, "Price / Rent", property.getPriceDisplay());
        row = addField(fields, gbc, row, "Furnishing", property.getFurnishing());

        content.add(fields, BorderLayout.NORTH);

        JTextArea description = new JTextArea(property.getDescription().isBlank()
                ? "No additional description available." : property.getDescription());
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setEditable(false);
        description.setFont(description.getFont().deriveFont(13f));
        description.setBackground(content.getBackground());
        description.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Description"),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        JScrollPane descScroll = new JScrollPane(description);
        descScroll.setPreferredSize(new Dimension(480, 220));
        content.add(descScroll, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(close);
        content.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(520, 480));
        setLocationRelativeTo(owner);
    }

    private static int addField(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD));
        panel.add(labelComponent, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(value), gbc);

        return row + 1;
    }
}
