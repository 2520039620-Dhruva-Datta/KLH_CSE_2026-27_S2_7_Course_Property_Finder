# Property Finder

A terminal-based property search application built with Data Structures and Algorithms for Hyderabad real-estate data.

## Phase 1 — KMP Property Pattern Search

Implemented Knuth-Morris-Pratt pattern searching in Java.

The algorithm searches property records derived from the Hyderabad Property Finder dataset (`data/hyderabad_recent_properties.txt`, a CSV-formatted `.txt` corpus).

Features:
- Java implementation
- Manual LPS construction
- KMP string matching
- Searches property records
- Case-insensitive matching
- Displays matching properties
- Formatted terminal table
- Unique-property detection
- Pattern-occurrence count
- Paginated results and a per-property details view

### Structure

```
data/
  hyderabad_recent_properties.txt   Phase 1 CSV-formatted text corpus
src/
  search/KMPSearch.java             Manual KMP + LPS implementation
  loader/CorpusLoader.java          Reads .txt files; parses CSV rows into property records
  model/PropertyRecord.java         Field-map wrapper around one property row
  utils/TableFormatter.java         Bordered terminal table renderer
  cli/PatternSearchDemo.java        Terminal entry point
```

### Compile

```powershell
javac -d out src\model\PropertyRecord.java src\utils\TableFormatter.java src\search\KMPSearch.java src\loader\CorpusLoader.java src\cli\PatternSearchDemo.java
```

### Run

```powershell
java -cp out cli.PatternSearchDemo
```
