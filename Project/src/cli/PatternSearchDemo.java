// .\run.ps1

package cli;

import loader.CorpusLoader;
import model.PropertyRecord;
import search.KMPSearch;
import utils.TableFormatter;
import utils.TableFormatter.Column;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class PatternSearchDemo {

    private static final Path CORPUS_DIR = Path.of("data");
    private static final int PAGE_SIZE = 20;

    private static final List<Column> TABLE_COLUMNS = List.of(
            new Column("No", 3, true),
            new Column("Property", 35),
            new Column("Locality", 13),
            new Column("Type", 16),
            new Column("Config", 7),
            new Column("Price", 15, true),
            new Column("Purpose", 7)
    );

    public static void main(String[] args) {

        // On Windows, the console's codepage is usually a legacy single-byte one (not UTF-8),
        // which garbles multi-byte characters like the rupee symbol even though Java writes
        // correct UTF-8 bytes. Switching it to UTF-8 (65001) up front fixes rendering without
        // requiring the user to run 'chcp 65001' manually before every launch.
        ensureUtf8Console();

        // Force UTF-8 output so the rupee symbol is encoded correctly regardless of the
        // JVM's default stdout encoding.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        System.out.println("==============================================================");
        System.out.println("                     PROPERTY FINDER");
        System.out.println("==============================================================\n");

        CorpusLoader loader = new CorpusLoader(CORPUS_DIR);

        if (!loader.corpusDirectoryExists()) {
            System.out.println("Corpus could not be found.");
            System.out.println("Expected location: " + CORPUS_DIR.toAbsolutePath());
            return;
        }

        Map<String, String> rawDocuments;
        List<PropertyRecord> properties;
        try {
            rawDocuments = loader.loadCorpus();
            properties = loader.loadPropertyRecords();
        } catch (IOException e) {
            System.out.println("Corpus could not be read: " + e.getMessage());
            return;
        }

        if (rawDocuments.isEmpty() || properties.isEmpty()) {
            System.out.println("Corpus contains no searchable text.");
            return;
        }

        System.out.println("Dataset loaded successfully.");
        System.out.println("Properties loaded: " + properties.size());
        System.out.println();
        System.out.println("Search using:");
        System.out.println("Property Name | Locality | Type | Configuration | Amenities");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.print("Enter search pattern (or 'exit' to quit): ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String pattern = scanner.nextLine().trim();

            if (pattern.equalsIgnoreCase("exit") || pattern.equalsIgnoreCase("quit")) {
                break;
            }

            if (pattern.isEmpty()) {
                System.out.println("Pattern cannot be empty.");
                continue;
            }

            Action action = runSearch(scanner, rawDocuments, properties, pattern);
            if (action == Action.QUIT) {
                break;
            }
            // Action.NEW_SEARCH just falls through to the top of this loop.
        }

        System.out.println();
        System.out.println("Exiting Property Finder.");
        System.out.println("Goodbye!");

        scanner.close();
    }

    // Signal returned by the post-result and details loops. BACK only makes sense coming
    // out of detailsLoop (return to the results prompt); the outer loops only ever see
    // NEW_SEARCH or QUIT.
    private enum Action { BACK, NEW_SEARCH, QUIT }

    // Runs one search, prints the table+summary exactly once, then hands off to the
    // post-result loop. Returns the action the user chose there.
    private static Action runSearch(Scanner scanner, Map<String, String> rawDocuments,
                                     List<PropertyRecord> properties, String pattern) {

        System.out.println("\nSearching properties...\n");

        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);

        // Pattern occurrences: raw KMP search across the whole corpus text (unchanged from before).
        int occurrences = 0;
        for (String document : rawDocuments.values()) {
            occurrences += KMPSearch.search(document.toLowerCase(Locale.ROOT), normalizedPattern).size();
        }

        // Matching properties: run KMP once per property record, keep each property at most once.
        List<PropertyRecord> matches = new ArrayList<>();
        for (PropertyRecord property : properties) {
            List<Integer> hits = KMPSearch.search(property.getSearchableTextLower(), normalizedPattern);
            if (!hits.isEmpty()) {
                matches.add(property);
            }
        }

        if (matches.isEmpty()) {
            System.out.println("No properties found for: " + pattern);
            System.out.println();
            printSummary(pattern, occurrences, 0);
            return Action.NEW_SEARCH;
        }

        int[] page = { 0 };
        int totalPages = (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        printPage(matches, page[0], totalPages);
        printSummary(pattern, occurrences, matches.size());

        return postResultLoop(scanner, matches, page, totalPages);
    }

    // Dedicated post-result input loop. Only reprints the table on an explicit page
    // change; every other outcome (including invalid input) just re-shows this prompt.
    private static Action postResultLoop(Scanner scanner, List<PropertyRecord> matches, int[] page, int totalPages) {

        while (true) {
            printPostResultPrompt();

            if (!scanner.hasNextLine()) {
                return Action.QUIT;
            }
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                return Action.QUIT;
            }
            if (input.isEmpty()) {
                return Action.NEW_SEARCH;
            }
            if (totalPages > 1 && (input.equalsIgnoreCase("n") || input.equalsIgnoreCase("next"))) {
                if (page[0] < totalPages - 1) {
                    page[0]++;
                }
                printPage(matches, page[0], totalPages);
                continue;
            }
            if (totalPages > 1 && (input.equalsIgnoreCase("p") || input.equalsIgnoreCase("previous") || input.equalsIgnoreCase("prev"))) {
                if (page[0] > 0) {
                    page[0]--;
                }
                printPage(matches, page[0], totalPages);
                continue;
            }

            int number;
            try {
                number = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                // Non-numeric text (as opposed to an out-of-range number) discards the
                // invalid input and drops straight back to the main search prompt, rather
                // than trapping the user at this property-number prompt.
                System.out.println();
                System.out.println("Unrecognized input.");
                return Action.NEW_SEARCH;
            }

            if (number < 1 || number > matches.size()) {
                System.out.println();
                System.out.println("Invalid property number. Please enter a number between 1 and " + matches.size() + ".");
                continue;
            }

            Action fromDetails = detailsLoop(scanner, matches.get(number - 1));
            if (fromDetails == Action.QUIT) {
                return Action.QUIT;
            }
            if (fromDetails == Action.NEW_SEARCH) {
                return Action.NEW_SEARCH;
            }
            // Action.BACK falls through to re-show this same prompt (no table redraw).
        }
    }

    private static void printPostResultPrompt() {
        System.out.println();
        System.out.println("Enter property number for full details,");
        System.out.println("press Enter for a new search,");
        System.out.print("or type 'exit' to quit: ");
    }

    // Shows one property's details, then loops on its own small prompt until the user
    // chooses to go back, start a new search, or quit.
    private static Action detailsLoop(Scanner scanner, PropertyRecord property) {

        printDetails(property);

        while (true) {
            System.out.println("Press Enter to return to search results,");
            System.out.println("type 'new' for a new search,");
            System.out.print("or type 'exit' to quit: ");

            if (!scanner.hasNextLine()) {
                return Action.QUIT;
            }
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                return Action.QUIT;
            }
            if (input.isEmpty()) {
                return Action.BACK;
            }
            if (input.equalsIgnoreCase("new")) {
                return Action.NEW_SEARCH;
            }

            System.out.println();
            System.out.println("Unrecognized input.");
            System.out.println();
        }
    }

    private static void printPage(List<PropertyRecord> matches, int page, int totalPages) {

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, matches.size());

        if (totalPages > 1) {
            System.out.println("Showing properties " + (start + 1) + "-" + end + " of " + matches.size());
            System.out.println();
        }

        List<String[]> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PropertyRecord property = matches.get(i);
            rows.add(new String[] {
                    String.valueOf(i + 1),
                    property.get("ListingTitle"),
                    property.get("Locality"),
                    property.get("PropertyType"),
                    property.get("Configuration"),
                    formatPrice(property.get("PriceRent")),
                    property.get("Purpose")
            });
        }

        TableFormatter.printTable(TABLE_COLUMNS, rows);
        System.out.println();
    }

    private static void printSummary(String pattern, int occurrences, int matchingProperties) {
        System.out.println("----------------------------------------");
        System.out.println("Search pattern       : " + pattern);
        System.out.println("Pattern occurrences  : " + occurrences);
        System.out.println("Matching properties  : " + matchingProperties);
        System.out.println("----------------------------------------");
    }

    private static void printDetails(PropertyRecord property) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("           PROPERTY DETAILS");
        System.out.println("========================================");
        System.out.println("Property       : " + property.get("ListingTitle"));
        System.out.println("Locality       : " + property.get("Locality"));
        System.out.println("Location       : " + property.get("ProjectFullLocation"));
        System.out.println("Property Type  : " + property.get("PropertyType"));
        System.out.println("Configuration  : " + property.get("Configuration"));
        System.out.println("Purpose        : " + property.get("Purpose"));
        System.out.println("Price          : " + formatPrice(property.get("PriceRent")));
        System.out.println("Area           : " + property.get("BuiltUpArea"));
        System.out.println("Furnishing     : " + property.get("Furnishing"));
        System.out.println("Facing         : " + property.get("Facing"));
        System.out.println("Status         : " + property.get("ConstructionStatus"));
        System.out.println("Amenities      : " + property.get("Amenities"));
        System.out.println("========================================");
        System.out.println();
    }

    // Single source of truth for the rupee symbol used throughout price formatting.
    private static final String RUPEE = "₹";

    // Strips any existing currency prefix (correct or mojibake) before parsing, so a value
    // that already carries a symbol never ends up with two.
    private static final Pattern CURRENCY_PREFIX =
            Pattern.compile("^\\s*(" + RUPEE + "|Γé╣|â‚¹|Rs\\.?|INR|Re)\\s*", Pattern.CASE_INSENSITIVE);

    private static String formatPrice(String raw) {
        if (raw == null || raw.equals("N/A")) {
            return "N/A";
        }
        String cleaned = CURRENCY_PREFIX.matcher(raw).replaceFirst("").replace(",", "").trim();
        try {
            long value = Long.parseLong(cleaned);
            return RUPEE + NumberFormat.getInstance(Locale.of("en", "IN")).format(value);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    // Sets the Windows console output codepage to UTF-8 (65001) so multi-byte characters like
    // the rupee symbol render correctly. No-op on non-Windows platforms, which default to UTF-8.
    private static void ensureUtf8Console() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }
        try {
            new ProcessBuilder("cmd.exe", "/c", "chcp", "65001")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();
        } catch (IOException e) {
            // Non-fatal: output still attempts UTF-8; the user can run 'chcp 65001' manually
            // if the console codepage could not be switched automatically.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
