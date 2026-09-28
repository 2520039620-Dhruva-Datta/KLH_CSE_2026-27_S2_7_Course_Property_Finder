# Property Finder

A Java Swing desktop application for searching Indian real-estate datasets by
location, built for a Data Structures and Algorithms course project. Every
string-matching algorithm in it is implemented by hand (no `contains()`,
`indexOf()`, `matches()` or regex is used for actual matching), and an
**Adaptive Algorithm Selection Engine** (a deterministic smart search router,
not AI/ML) picks the right algorithm for each query.

## Features

- Java Swing desktop GUI (Nimbus look and feel, resizable)
- Multi-dataset property search: `up` (pan-India plots) plus one dataset per state / union territory (36), which include the merged-in Delhi housing, Hyderabad and Indian house rent listings
- Algorithm dropdown, default **Auto — Smart Selection**:
  Auto, KMP, Z Algorithm, Rabin–Karp, Levenshtein Fuzzy Search,
  Aho–Corasick, Suffix Array Search, Compare Exact Algorithms
- Auto mode visibly explains its choice (algorithm, reason, technique, complexity)
- Compare Exact Algorithms: KMP vs Z vs Rabin–Karp on the same query/data, with real timings and an identical-results check
- "Algorithms Used" dialog listing every algorithm, its category, complexity, Java class and honest implementation status
- Location-based search (locality / city / state only, not free-text descriptions), case-insensitive
- Property details dialog with full source data
- ₹ (rupee) formatting, including Lakh/Crore-denominated prices
- Background loading and searching (`SwingWorker`), so the window never freezes
- Terminal CLI (original Phase 1 tool) still available for demonstration/debugging

## Algorithms Used

| Algorithm | Category | Purpose | Technique / structure | Complexity | Java class | Status |
|---|---|---|---|---|---|---|
| KMP | Exact search | Single exact location search | LPS array (prefix function) | O(n + m) | `search.KMPSearch` | Implemented, used by Auto, comparison algorithm |
| Z Algorithm | Exact search | Second linear-time exact search, cross-checks KMP | Z array + Z-box `[L, R]`, int-sentinel separator | O(n + m) | `search.ZAlgorithm` | Implemented, comparison algorithm |
| Rabin–Karp | Exact search | Exact search via rolling hash | Rolling hash + character verification on every hash hit | O(n + m) average, O(n·m) worst | `search.RabinKarpSearch` | Implemented, comparison algorithm |
| Levenshtein / Wagner–Fischer | Fuzzy search | Finds matches despite spelling mistakes (`Gachibowlii` → `Gachibowli`) | Dynamic-programming edit distance (two-row table) | O(n·m) per pair | `search.LevenshteinSearch` | Implemented, used by Auto |
| Aho–Corasick | Multi-pattern search | Several locations in one pass (`Kokapet, Gachibowli, Tellapur`) | Trie + failure links (BFS-built automaton) | O(n + m + z) | `search.AhoCorasick` | Implemented, used by Auto |
| Suffix Array Search | Indexed search | Repeated exact search over a static corpus | Suffix array (prefix doubling with counting sort) + binary search | O(n log n) build, O(m log n) per query | `search.SuffixArraySearch` | Implemented, used by Auto |
| Kasai LCP | Indexed search (support) | Longest-common-prefix array for the suffix array | Rank array + previous-match-length reuse | O(n) | `search.SuffixArraySearch#kasaiLcp` | Support algorithm (not a dropdown option) |
| Parallel Dataset Search | Performance extension | Speeds up exact search across All Sources | Thread pool, equal contiguous chunks, ordered merge | O(n / p + m) per thread | `service.ParallelDatasetSearch` | Performance layer (not a string-matching algorithm) |
| Randomized Hashing | Performance extension | Avoids a fixed, attackable Rabin–Karp modulus | Modulus drawn at random from a prime set per search call | same as Rabin–Karp | `search.RabinKarpSearch#searchRandomized` | Implemented (optional extension) |
| Adaptive Algorithm Selection Engine | Smart search router | Chooses the algorithm for each query | Deterministic rules on query shape and first-attempt outcome | cost of the chosen algorithm | `service.AdaptiveSearchRouter` | Implemented |

### Auto routing rules

Auto mode is plain rule-based dispatch (`service.AdaptiveSearchRouter`):

| Query situation | Algorithm chosen |
|---|---|
| Comma-separated query (multiple locations) | Aho–Corasick |
| Same query repeated for the same dataset selection in this session | Suffix Array Search (cached index) |
| Single term, exact search finds matches | KMP |
| Single term, exact search finds nothing (likely typo) | Levenshtein fuzzy fallback |
| Dataset = All Sources and an exact algorithm runs on 5,000+ properties | Parallel Dataset Search is also applied |

Compare Exact Algorithms is always a manual choice: it runs KMP, Z and
Rabin–Karp on identical input (sequentially, after an untimed warm-up pass so
the first algorithm isn't penalised for JIT compilation) and reports
`Results identical: YES/NO`. Levenshtein, Aho–Corasick and Suffix Array
solve different problems and are deliberately not part of that comparison.

All search timings use `System.nanoTime()` around the algorithm work only;
dataset loading and GUI rendering are never included.

## Datasets

The GUI loads 38 files (321,470 properties), and each is one entry in the
Dataset dropdown, named after the dataset: `up`, one entry per state / union
territory (`Delhi`, `Telangana`, `West Bengal`, ...), and
`Indian House Rent (unassigned)`.

| Dataset (dropdown name) | File | Rows | Notes |
|---|---|---|---|
| `up` | `data/up.txt` | 269,418 | Plots/properties across 22 states; prices as `90 L` / `1.2 Cr`. Kept separate because it spans many states |
| one per state / UT (36) | `data/<State_Name>.txt`, e.g. `Goa.txt`, `Tamil_Nadu.txt` | 1,171 each, plus merged rows (below) | Tab-separated |
| `Indian House Rent (unassigned)` | `data/Indian_House_Rent_Unassigned.txt` | 655 | Rent listings whose city is blank or unreadable |

### Older datasets merged into the state files

Three earlier datasets were folded into the state files. Each merged row keeps
its original dataset name in the `Source` column, so a state's results still
show where every row came from (e.g. `Delhi Housing (Indian_housing_Delhi_data.txt)`).

| Original file | Rows | Merged into |
|---|---|---|
| `Indian_housing_Delhi_data.txt` (Delhi rental housing) | 5,000 | `Delhi.txt` |
| `hyderabad_recent_properties.txt` (Hyderabad listings) | 150 | `Telangana.txt` |
| `Indian_House_Rent_Dataset.txt` (rentals in six cities) | 4,746 | routed row by row by its city: Kolkata 444 → `West_Bengal.txt`, Mumbai 829 → `Maharashtra.txt`, Bangalore 761 → `Karnataka.txt`, Chennai 767 → `Tamil_Nadu.txt`, Hyderabad 763 → `Telangana.txt`, Delhi 527 → `Delhi.txt`; the 655 with no readable city → `Indian_House_Rent_Unassigned.txt` |

The rent dataset is not West Bengal data (only 444 of its rows are Kolkata),
so it is split by city rather than placed in one state file, and rows with no
readable city are not guessed at.

The three original files stay in `data/` untouched: they are the input to the
merge, and `hyderabad_recent_properties.txt` is still what the CLI reads. The
GUI does not load them directly, since that would count every row twice.

| State file | Original rows | Merged rows | Total |
|---|---|---|---|
| `Delhi.txt` | 1,171 | 5,527 (5,000 housing + 527 rent) | 6,698 |
| `Telangana.txt` | 1,171 | 913 (150 Hyderabad + 763 rent) | 2,084 |
| `Tamil_Nadu.txt` | 1,171 | 767 rent | 1,938 |
| `Karnataka.txt` | 1,171 | 761 rent | 1,932 |
| `Maharashtra.txt` | 1,171 | 829 rent | 2,000 |
| `West_Bengal.txt` | 1,171 | 444 rent | 1,615 |
| the other 30 state files | 1,171 | none | 1,171 |

All files are read as-is; malformed individual rows are skipped (counted),
and a missing/unreadable file only disables that one dataset. That holds for
the state files too: each one is loaded by its own `StateListingsDatasetLoader`
instance, so a missing `Goa.txt` does not affect the other 37. The state name
is part of every listing's searchable location text, so searching `Goa` in
`Goa` (or in All Sources) matches exactly the Goa file's rows.

### State file format

Plain tab-separated text, one row per line, no quoting (a `"` is just a
character). Columns: `No.`, `Source`, `Property`, `Location`, `City / State`,
`Type / Config`, `Area`, `Price / Rent`, `Furnishing`, `Bathrooms`, `Tennants`
(sic), `Listing Type`, and, in the files that received merged rows, a trailing
`Description`. `City / State` and `Type / Config` are split on ` / ` into
separate city, state, type and configuration fields; `Bathrooms` and
`Tennants` are shown in the details dialog's description, followed by the
`Description` column when there is one; `Listing Type` (`Rent` / `Sale`)
becomes the purpose. Prices are `₹32,500/month` for rent and `₹63 L` /
`₹1.85 Cr` for sale.

### Regenerating the merge

The merge is a re-runnable tool, `tools.MergeIntoStateFiles`. It reuses the
original loaders to read the three source files, rebuilds each affected state
file from its own rows plus the freshly merged ones (so running it twice never
duplicates anything), and writes nothing at all if a source file can't be read.

```powershell
java -cp out tools.MergeIntoStateFiles          # after compiling; defaults to ./data
```

## Structure

```
data/                                   up.txt, 36 state files, the unassigned-rent file, and the three original files kept as merge input
src/
  search/KMPSearch.java                 Manual KMP + LPS implementation
  search/ZAlgorithm.java                Manual Z Algorithm (Z-array + Z-box)
  search/RabinKarpSearch.java           Rolling-hash exact search (+ randomized modulus variant)
  search/LevenshteinSearch.java         Wagner-Fischer edit distance
  search/AhoCorasick.java               Multi-pattern trie automaton with failure links
  search/SuffixArraySearch.java         Suffix array construction/query + Kasai LCP
  model/PropertyRecord.java             Hyderabad-specific field-map (used by the CLI)
  model/UnifiedProperty.java            Cross-dataset property model (used by the GUI)
  loader/CsvParser.java                 Whole-file CSV/TSV parser (configurable delimiter; quoted/multiline fields optional)
  loader/CorpusLoader.java              Hyderabad-only loader (CLI)
  loader/AbstractCsvDatasetLoader.java  Shared load loop for the GUI's dataset loaders
  loader/UpDatasetLoader.java           up.txt -> UnifiedProperty
  loader/StateListingsDatasetLoader.java  One state/UT listing file (tab-separated) -> UnifiedProperty; used for the 36 state files and the unassigned-rent file
  loader/HyderabadDatasetLoader.java    Hyderabad original file -> UnifiedProperty (read by the merge tool only)
  loader/DelhiHousingDatasetLoader.java Delhi housing original file -> UnifiedProperty (read by the merge tool only)
  loader/IndianRentDatasetLoader.java   Indian house rent original file -> UnifiedProperty (read by the merge tool only)
  tools/MergeIntoStateFiles.java        Merges the three older datasets into the state files (see Datasets)
  service/PropertyRepository.java       Loads and holds every dataset (up + 36 state files + unassigned rent)
  service/SearchService.java            Runs every algorithm against property data; caches suffix-array indexes
  service/AdaptiveSearchRouter.java     Auto-mode routing rules
  service/QueryParser.java              Splits "A, B, C" queries into individual patterns
  service/ParallelDatasetSearch.java    Chunked concurrent exact search
  service/Algorithm.java                Algorithm enum + display metadata
  service/AlgorithmCatalog.java         Full algorithm inventory for the dialog
  service/SearchResult.java             One search's outcome
  service/ExactComparisonResult.java    KMP / Z / Rabin-Karp comparison outcome
  service/RouterDecision.java           What Auto chose and why
  utils/PriceFormatter.java             ₹ / Lakh / Crore / "per month" price parsing & display
  utils/TableFormatter.java             Bordered CLI table renderer
  gui/PropertyFinderGUI.java            Main Swing window
  gui/PropertyTableModel.java           JTable model over UnifiedProperty
  gui/PropertyDetailsDialog.java        Property details dialog
  gui/AlgorithmsUsedDialog.java         "Algorithms Used" dialog
  cli/PatternSearchDemo.java            Terminal entry point (Hyderabad only, KMP)
```

## Z Algorithm: separator strategy

A naive `pattern + "$" + text` join is unsafe because `$` could theoretically
appear in real data. Instead, `ZAlgorithm` builds an `int[]` of character
codes with a numeric sentinel (`-1`) between pattern and text. Since Java
`char` values only range `0..65535`, `-1` can never collide with real input.
`SuffixArraySearch` uses the same idea to separate documents in its corpus.

## Suffix array indexing

The suffix array is built once per dataset selection, on first use, and then
reused for every later query (that is what makes it "indexed"). Location
strings repeat heavily (about 4,800 distinct strings across ~321,000
properties), so the index is built over the *distinct* strings and each match
is mapped back to every property sharing it. Construction uses prefix doubling
with counting sort (O(n log n)), not a comparison sort.

## Run (GUI)

```powershell
.\run.ps1
```

Recompiles every file under `src/` and launches the Swing GUI
(`gui.PropertyFinderGUI`).

## Run (CLI)

```powershell
.\run-cli.ps1
```

or, after compiling:

```powershell
java -cp out cli.PatternSearchDemo
```

The CLI searches only the original Hyderabad dataset with KMP and is unchanged
from Phase 1.

## Known limitations

- **Fuzzy search tolerance is deliberately small**: edit distance up to 1
  (patterns of 4 characters or fewer), 2 (up to 8) or 3 (longer), measured
  against each locality / city / state value and against runs of the same
  number of consecutive words inside it (so `Gachibowlii` finds `Gachibowli
  Phase 3`). Heavier misspellings or transposed words are not found, and a
  short pattern can match a different name that is one edit away. Edit
  distances are computed once per distinct location string and reused.
- **"Repeated query" detection is in-memory and per session**, keyed by
  dataset selection plus the lower-cased query; it resets when the app restarts.
- **Parallel Dataset Search** only applies to the exact single-pattern
  algorithms (KMP, Z, Rabin–Karp) on All Sources with 5,000+ properties, and
  the speedup depends on core count (roughly 3.6–6.5x measured on a 16-core
  machine). Compare mode runs sequentially on purpose, for fair timings.
- **Aho–Corasick under Auto** is only chosen when the query contains a comma;
  a single term goes to KMP.
- **First search after launch** can be slower than later ones because the JVM
  is still compiling hot code (JIT warm-up).
- **Suffix array index** is built lazily: the first Suffix Array search on
  All Sources takes a few hundred milliseconds, then queries take well under
  a millisecond.
- The CLI is intentionally unchanged: Hyderabad only, KMP only.

## Known data quality issues

- `up.txt`: despite the filename, records span 22 states, not just Uttar Pradesh.
- Hyderabad rows (now in `Telangana.txt`): some have an internal `Purpose`
  column of `Rent` on listings whose title/URL say "for sale" (or vice
  versa) — inconsistent in the source data itself and preserved as-is. The
  source's own state text was `Andhra Pradesh` (or just `Hyderabad`); in
  `Telangana.txt` these rows are filed under Telangana, as requested.
- Indian house rent rows: the source `City` values contain stray characters
  (e.g. `India-! Kolkata`). Rows are routed by the city name found inside that
  text and written with the clean city name; `Tenant Preferred` values are
  kept as they were, stray characters included (e.g. `Bach^elors/Fam.ily`).
  The 655 rows with a blank city are in `Indian_House_Rent_Unassigned.txt`,
  including a few whose locality mentions Kolkata: they are not re-assigned
  by guesswork.
- Merged Delhi housing descriptions had line breaks (64 of 5,000 rows); the
  state files are one row per line, so those breaks became spaces.
- The rent dataset's `BHK` (`2.0`) is written as `2 BHK`, and rent prices
  given as a bare amount are written as `₹10,000/month`.
- State listing files: the `Area` column is a bare number with no unit in the
  source, so it is shown as-is (not labelled "sqft"); the `Tennants` header is
  misspelled in every file and is read under that spelling. Delhi housing rows
  and the state file's own Delhi rows (and likewise Hyderabad/Telangana) can
  describe the same neighbourhoods; they are separate rows from separate
  datasets and are not de-duplicated.
- Any row that cannot be mapped to a property (e.g. a fully blank line) is
  skipped and counted, not fabricated.
