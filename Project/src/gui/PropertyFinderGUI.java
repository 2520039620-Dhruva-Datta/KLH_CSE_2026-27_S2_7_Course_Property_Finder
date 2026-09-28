package gui;

import loader.DatasetLoadResult;
import loader.StateListingsDatasetLoader;
import model.UnifiedProperty;
import service.AdaptiveSearchRouter;
import service.Algorithm;
import service.ExactComparisonResult;
import service.PropertyRepository;
import service.QueryParser;
import service.RouterDecision;
import service.SearchResult;
import service.SearchService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PropertyFinderGUI extends JFrame {

    private static final String AUTO_LABEL = "Auto — Smart Selection";
    private static final String COMPARE_LABEL = "Compare Exact Algorithms";

    private static final String[] ALGORITHM_OPTIONS = {
            AUTO_LABEL,
            Algorithm.KMP.label,
            Algorithm.Z.label,
            Algorithm.RABIN_KARP.label,
            Algorithm.LEVENSHTEIN.label,
            Algorithm.AHO_CORASICK.label,
            Algorithm.SUFFIX_ARRAY.label,
            COMPARE_LABEL
    };

    // Dropdown label -> source id (null = every dataset). Labels are the dataset
    // names: "up", then one entry per state / UT file.
    private static final Map<String, String> DATASET_OPTIONS = new LinkedHashMap<>();
    static {
        DATASET_OPTIONS.put("All Sources", null);
        DATASET_OPTIONS.put("up", "up");
        for (String stateName : StateListingsDatasetLoader.STATE_NAMES) {
            DATASET_OPTIONS.put(stateName, StateListingsDatasetLoader.sourceIdFor(stateName));
        }
        DATASET_OPTIONS.put(StateListingsDatasetLoader.UNASSIGNED_RENT_LABEL,
                StateListingsDatasetLoader.UNASSIGNED_RENT_SOURCE_ID);
    }

    private final PropertyRepository repository = new PropertyRepository();
    private final SearchService searchService = new SearchService();
    private final AdaptiveSearchRouter router = new AdaptiveSearchRouter(searchService);

    private JTextField searchField;
    private JButton searchButton;
    private JButton clearButton;
    private JButton viewDetailsButton;
    private JComboBox<String> algorithmCombo;
    private JComboBox<String> datasetCombo;
    private JTextArea algorithmInfoArea;
    private JButton algorithmsUsedButton;

    private JLabel statPropertiesValue;
    private JLabel statOccurrencesValue;
    private JLabel statTimeValue;
    private JLabel statSourcesValue;

    private JPanel comparisonPanel;
    private JLabel[] cmpPropertiesValues = new JLabel[3];
    private JLabel[] cmpOccurrencesValues = new JLabel[3];
    private JLabel[] cmpTimeValues = new JLabel[3];
    private JLabel cmpIdenticalValue;

    private PropertyTableModel tableModel;
    private JTable table;
    private JLabel statusLabel;

    public PropertyFinderGUI() {
        super("Property Finder");
        buildUI();
        loadDatasetsAsync();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 800);
        setMinimumSize(new Dimension(950, 660));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        setContentPane(root);

        root.add(buildHeaderArea(), BorderLayout.NORTH);
        root.add(buildCenterArea(), BorderLayout.CENTER);

        statusLabel = new JLabel("Starting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 4));
        root.add(statusLabel, BorderLayout.SOUTH);

        setControlsEnabled(false);
    }

    private JPanel buildHeaderArea() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("PROPERTY FINDER");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Smart Search. Better Choices.");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.ITALIC, 13f));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title block is its own centered box, aligned LEFT like every other row.
        // Mixing CENTER- and LEFT-aligned children directly in one BoxLayout
        // shifts the shared alignment point and squeezes the centered title.
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(title);
        titleBlock.add(subtitle);

        container.add(titleBlock);
        container.add(Box.createVerticalStrut(10));
        container.add(buildSearchRow());
        container.add(Box.createVerticalStrut(6));
        container.add(buildControlsRow());
        container.add(Box.createVerticalStrut(6));
        container.add(buildAlgorithmInfoRow());
        container.add(Box.createVerticalStrut(6));
        container.add(buildStatsRow());
        container.add(Box.createVerticalStrut(6));
        container.add(buildSeparator());

        return container;
    }

    private JPanel buildSearchRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel label = new JLabel("Search Location:");
        searchField = new JTextField();
        searchField.addActionListener(e -> onSearch());

        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> onSearch());

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> onClear());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(searchButton);
        buttons.add(clearButton);

        row.add(label, BorderLayout.WEST);
        row.add(searchField, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private JPanel buildControlsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(new JLabel("Algorithm:"));
        algorithmCombo = new JComboBox<>(ALGORITHM_OPTIONS);
        algorithmCombo.setSelectedItem(AUTO_LABEL);
        algorithmCombo.addActionListener(e -> updateAlgorithmInfo());
        row.add(algorithmCombo);

        row.add(Box.createHorizontalStrut(20));

        row.add(new JLabel("Dataset:"));
        datasetCombo = new JComboBox<>(DATASET_OPTIONS.keySet().toArray(new String[0]));
        row.add(datasetCombo);

        return row;
    }

    private JPanel buildAlgorithmInfoRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 122));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        algorithmInfoArea = new JTextArea(6, 40);
        algorithmInfoArea.setEditable(false);
        algorithmInfoArea.setLineWrap(true);
        algorithmInfoArea.setWrapStyleWord(true);
        algorithmInfoArea.setOpaque(false);
        algorithmInfoArea.setFont(algorithmInfoArea.getFont().deriveFont(12.5f));

        algorithmsUsedButton = new JButton("Algorithms Used");
        algorithmsUsedButton.addActionListener(e -> new AlgorithmsUsedDialog(this).setVisible(true));

        row.add(new JScrollPane(algorithmInfoArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        row.add(algorithmsUsedButton, BorderLayout.EAST);
        return row;
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        statPropertiesValue = new JLabel("0");
        statOccurrencesValue = new JLabel("0");
        statTimeValue = new JLabel("--");
        statSourcesValue = new JLabel("--");

        row.add(statBox("Matching Properties", statPropertiesValue));
        row.add(statBox("Pattern Occurrences", statOccurrencesValue));
        row.add(statBox("Search Time", statTimeValue));
        row.add(statBox("Sources Searched", statSourcesValue));
        return row;
    }

    private JPanel statBox(String caption, JLabel valueLabel) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 225, 225)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(captionLabel.getFont().deriveFont(11f));
        captionLabel.setForeground(Color.GRAY);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));
        box.add(captionLabel);
        box.add(valueLabel);
        return box;
    }

    private Component buildSeparator() {
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        return separator;
    }

    private JPanel buildCenterArea() {
        JPanel container = new JPanel(new BorderLayout(0, 8));

        comparisonPanel = buildComparisonPanel();
        comparisonPanel.setVisible(false);
        container.add(comparisonPanel, BorderLayout.NORTH);

        tableModel = new PropertyTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedDetails();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        container.add(scrollPane, BorderLayout.CENTER);

        JPanel tableToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewDetailsButton = new JButton("View Details");
        viewDetailsButton.addActionListener(e -> openSelectedDetails());
        tableToolbar.add(viewDetailsButton);
        tableToolbar.add(new JLabel("  (or double-click a row)"));
        container.add(tableToolbar, BorderLayout.SOUTH);

        return container;
    }

    private JPanel buildComparisonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Compare Exact Algorithms"),
                BorderFactory.createEmptyBorder(6, 10, 10, 10)));

        JPanel grid = new JPanel(new GridLayout(5, 4, 12, 4));

        grid.add(boldLabel("Metric"));
        grid.add(boldLabel("KMP"));
        grid.add(boldLabel("Z Algorithm"));
        grid.add(boldLabel("Rabin–Karp"));

        grid.add(new JLabel("Matching Properties"));
        for (int i = 0; i < 3; i++) {
            cmpPropertiesValues[i] = new JLabel("0");
            grid.add(cmpPropertiesValues[i]);
        }

        grid.add(new JLabel("Pattern Occurrences"));
        for (int i = 0; i < 3; i++) {
            cmpOccurrencesValues[i] = new JLabel("0");
            grid.add(cmpOccurrencesValues[i]);
        }

        grid.add(new JLabel("Search Time"));
        for (int i = 0; i < 3; i++) {
            cmpTimeValues[i] = new JLabel("--");
            grid.add(cmpTimeValues[i]);
        }

        grid.add(new JLabel("Technique"));
        grid.add(new JLabel(Algorithm.KMP.technique));
        grid.add(new JLabel(Algorithm.Z.technique));
        grid.add(new JLabel(Algorithm.RABIN_KARP.technique));

        panel.add(grid, BorderLayout.CENTER);

        cmpIdenticalValue = new JLabel("Results identical: --");
        cmpIdenticalValue.setFont(cmpIdenticalValue.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(cmpIdenticalValue, BorderLayout.SOUTH);

        return panel;
    }

    private JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    // ---- data loading ----

    private void loadDatasetsAsync() {
        statusLabel.setText("Loading datasets...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                repository.loadAll(Path.of("data"));
                return null;
            }

            @Override
            protected void done() {
                onDatasetsLoaded();
            }
        };
        worker.execute();
    }

    private void onDatasetsLoaded() {
        int ok = repository.successfulDatasetCount();
        int total = repository.totalDatasetCount();

        StringBuilder summary = new StringBuilder();
        summary.append(ok).append(" of ").append(total).append(" datasets loaded");
        if (ok < total) {
            summary.append(" -- see terminal for details on the failed dataset(s)");
            for (DatasetLoadResult result : repository.getLoadResults()) {
                if (!result.isSuccessful()) {
                    System.err.println("[Property Finder] Dataset failed to load: "
                            + result.displayName + " (" + result.sourceId + ") -> " + result.fatalError);
                }
            }
        }
        summary.append(". ").append(repository.getAllProperties().size()).append(" properties available. Ready.");

        statusLabel.setText(summary.toString());
        setControlsEnabled(true);
        updateAlgorithmInfo();
    }

    // ---- search ----

    private void onSearch() {
        String pattern = searchField.getText().trim();
        if (pattern.isEmpty()) {
            statusLabel.setText("Please enter a location to search.");
            return;
        }

        String datasetLabel = (String) datasetCombo.getSelectedItem();
        String sourceId = DATASET_OPTIONS.get(datasetLabel);
        boolean allSources = sourceId == null;
        Set<String> filter = allSources ? Set.of() : Set.of(sourceId);
        List<UnifiedProperty> scope = repository.getPropertiesForSources(filter);

        String algoLabel = (String) algorithmCombo.getSelectedItem();

        statusLabel.setText("Searching...");
        setControlsEnabled(false);

        SwingWorker<Object, Void> worker = new SwingWorker<>() {
            @Override
            protected Object doInBackground() {
                if (COMPARE_LABEL.equals(algoLabel)) {
                    return searchService.compareExact(scope, pattern);
                }
                if (AUTO_LABEL.equals(algoLabel)) {
                    return router.route(scope, datasetLabel, allSources, pattern);
                }
                if (Algorithm.LEVENSHTEIN.label.equals(algoLabel)) {
                    return searchService.searchFuzzy(scope, pattern);
                }
                if (Algorithm.AHO_CORASICK.label.equals(algoLabel)) {
                    return searchService.searchMultiPattern(scope, QueryParser.splitPatterns(pattern));
                }
                if (Algorithm.SUFFIX_ARRAY.label.equals(algoLabel)) {
                    return searchService.searchIndexed(scope, datasetLabel, pattern);
                }
                Algorithm exactAlgorithm = Algorithm.Z.label.equals(algoLabel) ? Algorithm.Z
                        : Algorithm.RABIN_KARP.label.equals(algoLabel) ? Algorithm.RABIN_KARP
                        : Algorithm.KMP;
                return router.runExact(scope, pattern, exactAlgorithm, allSources);
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    if (result instanceof ExactComparisonResult comparisonResult) {
                        displayComparison(comparisonResult, scope, datasetLabel, pattern);
                    } else if (result instanceof RouterDecision decision) {
                        displayAutoDecision(decision, scope, datasetLabel, pattern);
                    } else if (result instanceof SearchResult searchResult) {
                        displaySingle(searchResult, scope, datasetLabel, pattern, null);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Search failed: " + e.getMessage());
                } finally {
                    setControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void displayAutoDecision(RouterDecision decision, List<UnifiedProperty> scope, String datasetLabel, String pattern) {
        displaySingle(decision.result, scope, datasetLabel, pattern, decision.explanationText());
    }

    private void displaySingle(SearchResult result, List<UnifiedProperty> scope, String datasetLabel, String pattern,
                               String autoExplanation) {
        comparisonPanel.setVisible(false);
        tableModel.setRows(result.matchingProperties);

        statPropertiesValue.setText(String.valueOf(result.propertyCount()));
        statOccurrencesValue.setText(String.valueOf(result.occurrences));
        statTimeValue.setText(result.elapsedDisplay());
        statSourcesValue.setText(sourcesSearchedLabel(datasetLabel, scope));

        if (autoExplanation != null) {
            setInfoText(autoExplanation + (result.note.isEmpty() ? "" : "\n" + result.note));
        } else {
            String base = result.algorithm.label + "  •  " + result.algorithm.technique + "  •  "
                    + result.algorithm.complexity + "\n" + result.algorithm.purpose;
            setInfoText(result.note.isEmpty() ? base : base + "\n" + result.note);
        }

        statusLabel.setText(result.propertyCount() == 0
                ? "No matching properties found for \"" + pattern + "\""
                : "Ready.");
        revalidate();
        repaint();
    }

    private void displayComparison(ExactComparisonResult comparison, List<UnifiedProperty> scope, String datasetLabel,
                                   String pattern) {
        SearchResult[] results = { comparison.kmpResult, comparison.zResult, comparison.rabinKarpResult };

        tableModel.setRows(comparison.kmpResult.matchingProperties);

        statPropertiesValue.setText(String.valueOf(comparison.kmpResult.propertyCount()));
        statOccurrencesValue.setText(String.valueOf(comparison.kmpResult.occurrences));
        long totalNanos = comparison.kmpResult.elapsedNanos + comparison.zResult.elapsedNanos
                + comparison.rabinKarpResult.elapsedNanos;
        statTimeValue.setText(String.format("%.3f ms (all 3)", totalNanos / 1_000_000.0));
        statSourcesValue.setText(sourcesSearchedLabel(datasetLabel, scope));

        for (int i = 0; i < 3; i++) {
            cmpPropertiesValues[i].setText(String.valueOf(results[i].propertyCount()));
            cmpOccurrencesValues[i].setText(String.valueOf(results[i].occurrences));
            cmpTimeValues[i].setText(results[i].elapsedDisplay());
        }

        if (comparison.identical) {
            cmpIdenticalValue.setText("Results identical: YES");
            cmpIdenticalValue.setForeground(new Color(0, 130, 0));
        } else {
            cmpIdenticalValue.setText("Results identical: NO -- algorithm result mismatch detected.");
            cmpIdenticalValue.setForeground(Color.RED);
        }
        comparisonPanel.setVisible(true);

        updateAlgorithmInfo();

        statusLabel.setText(comparison.kmpResult.propertyCount() == 0
                ? "No matching properties found for \"" + pattern + "\""
                : "Ready.");
        revalidate();
        repaint();
    }

    // A single dataset is shown by its dropdown name ("up", "Delhi", ...); All Sources
    // is qualified with how many datasets (files) that covers.
    private String sourcesSearchedLabel(String datasetLabel, List<UnifiedProperty> scope) {
        if (scope.isEmpty()) {
            return "None";
        }
        return DATASET_OPTIONS.get(datasetLabel) == null
                ? "All Sources (" + repository.successfulDatasetCount() + ")"
                : datasetLabel;
    }

    // Resets the scroll position too: setText leaves the caret at the end, which
    // would otherwise scroll a multi-line explanation past its first line.
    private void setInfoText(String text) {
        algorithmInfoArea.setText(text);
        algorithmInfoArea.setCaretPosition(0);
    }

    private void onClear() {
        searchField.setText("");
        tableModel.clear();
        comparisonPanel.setVisible(false);
        statPropertiesValue.setText("0");
        statOccurrencesValue.setText("0");
        statTimeValue.setText("--");
        statSourcesValue.setText("--");
        statusLabel.setText("Ready.");
        updateAlgorithmInfo();
        revalidate();
        repaint();
    }

    private void updateAlgorithmInfo() {
        String algoLabel = (String) algorithmCombo.getSelectedItem();
        if (AUTO_LABEL.equals(algoLabel)) {
            setInfoText("Adaptive Algorithm Selection Engine (Smart Search Router) — automatically "
                    + "routes your query to the algorithm best suited to it, based on the query's shape and the "
                    + "outcome of an initial attempt. This is deterministic rule-based dispatch, not AI/ML.");
        } else if (COMPARE_LABEL.equals(algoLabel)) {
            setInfoText("Compare Exact Algorithms: runs KMP, Z Algorithm and Rabin–Karp on the exact "
                    + "same query and data, then verifies all three report identical matches.");
        } else {
            Algorithm algorithm = algorithmForLabel(algoLabel);
            setInfoText(algorithm.label + "  •  " + algorithm.technique + "  •  "
                    + algorithm.complexity + "\n" + algorithm.purpose);
        }
    }

    private Algorithm algorithmForLabel(String label) {
        for (Algorithm algorithm : Algorithm.values()) {
            if (algorithm.label.equals(label)) {
                return algorithm;
            }
        }
        return Algorithm.KMP;
    }

    private void openSelectedDetails() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            statusLabel.setText("Select a property first, then click View Details.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        UnifiedProperty property = tableModel.getPropertyAt(modelRow);
        new PropertyDetailsDialog(this, property).setVisible(true);
    }

    private void setControlsEnabled(boolean enabled) {
        searchField.setEnabled(enabled);
        searchButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        algorithmCombo.setEnabled(enabled);
        datasetCombo.setEnabled(enabled);
        viewDetailsButton.setEnabled(enabled);
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // Falls back to the platform default look and feel.
        }

        SwingUtilities.invokeLater(() -> {
            PropertyFinderGUI gui = new PropertyFinderGUI();
            gui.setVisible(true);
        });
    }
}
