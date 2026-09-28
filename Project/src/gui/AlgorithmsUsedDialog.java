package gui;

import service.AlgorithmCatalog;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Replaces the old "How It Works" dialog. Lists every algorithm and support
// technique actually used in this project, grouped by category, with its
// real implementation status -- plus the Adaptive Router's routing rules.
public class AlgorithmsUsedDialog extends JDialog {

    private static final String[] CATEGORY_ORDER = {
            "Exact Search", "Fuzzy Search", "Multi-Pattern Search",
            "Indexed Search", "Performance Extensions", "Smart Search Router"
    };

    public AlgorithmsUsedDialog(Frame owner) {
        super(owner, "ALGORITHMS USED IN THIS PROJECT", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel heading = new JLabel("ALGORITHMS USED IN THIS PROJECT");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 17f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(10));

        Map<String, List<AlgorithmCatalog.Entry>> byCategory = new LinkedHashMap<>();
        for (AlgorithmCatalog.Entry entry : AlgorithmCatalog.allEntries()) {
            byCategory.computeIfAbsent(entry.category, k -> new java.util.ArrayList<>()).add(entry);
        }

        for (String category : CATEGORY_ORDER) {
            List<AlgorithmCatalog.Entry> entries = byCategory.get(category);
            if (entries == null) continue;
            content.add(buildCategorySection(category.toUpperCase(), entries));
            content.add(Box.createVerticalStrut(12));
        }

        content.add(buildRoutingRulesSection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(680, 560));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.add(closeButton);
        add(buttonRow, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
    }

    private JPanel buildCategorySection(String category, List<AlgorithmCatalog.Entry> entries) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(70, 110, 180)),
                BorderFactory.createEmptyBorder(4, 10, 4, 4)));

        JLabel categoryLabel = new JLabel(category);
        categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD, 13f));
        categoryLabel.setForeground(new Color(40, 70, 130));
        categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(categoryLabel);
        section.add(Box.createVerticalStrut(6));

        for (AlgorithmCatalog.Entry entry : entries) {
            section.add(buildEntryCard(entry));
            section.add(Box.createVerticalStrut(8));
        }

        return section;
    }

    private JPanel buildEntryCard(AlgorithmCatalog.Entry entry) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 225, 225)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        JLabel nameLabel = new JLabel(entry.name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13.5f));
        card.add(nameLabel, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        addRow(card, gbc, "Purpose", entry.purpose);
        addRow(card, gbc, "Technique", entry.technique);
        addRow(card, gbc, "Complexity", entry.complexity);
        addRow(card, gbc, "Java Class", entry.javaClass);
        addRow(card, gbc, "Status", entry.status);

        return card;
    }

    private void addRow(JPanel card, GridBagConstraints gbc, String label, String value) {
        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.PLAIN, 11.5f));
        labelComponent.setForeground(Color.GRAY);
        gbc.gridx = 0;
        gbc.weightx = 0;
        card.add(labelComponent, gbc);

        JLabel valueComponent = new JLabel("<html><div style='width:395px'>" + escapeHtml(value) + "</div></html>");
        valueComponent.setFont(valueComponent.getFont().deriveFont(11.5f));
        gbc.gridx = 1;
        gbc.weightx = 1;
        card.add(valueComponent, gbc);

        gbc.gridy++;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel buildRoutingRulesSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Adaptive Router: Auto Routing Rules"),
                BorderFactory.createEmptyBorder(4, 8, 6, 8)));

        JTextArea rules = new JTextArea("""
                Comma-separated query (e.g. "Kokapet, Gachibowli, Tellapur")
                    -> Aho-Corasick (multi-pattern search)

                Repeat of an earlier query against the same dataset selection
                    -> Suffix Array Search (reuses its cached index)

                Single-term query, found by exact search
                    -> KMP

                Single-term query, exact search finds nothing (likely a typo)
                    -> Levenshtein Fuzzy Search (spelling-tolerant fallback)

                "All Sources" + a large enough exact search
                    -> also runs Parallel Dataset Search across datasets

                "Compare Exact Algorithms" is always a manual selection:
                    -> runs KMP + Z Algorithm + Rabin-Karp on the same query
                """);
        rules.setEditable(false);
        rules.setOpaque(false);
        rules.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        // Without this the text area's caret sits at the end of its text and drags
        // the enclosing scroll pane down to the bottom the moment the dialog opens.
        ((DefaultCaret) rules.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        rules.setCaretPosition(0);
        section.add(rules, BorderLayout.CENTER);

        return section;
    }
}
