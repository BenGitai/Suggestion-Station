// PreferenceEngine.java

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * PreferenceEngine:
 * 1) Scans a given directory for CSV files.
 * 2) Parses each line: name,tags[,score]
 *    - tags = semicolon-separated list (e.g. "Food;Italian;Comfort")
 *    - score is optional; if missing, defaults to 0.0
 * 3) Builds:
 *    - allItems: List<Item> for every row
 *    - fileToItems: Map<filename, List<Item>> to group items by CSV filename
 *    - nameToItem: Map<itemName, Item> for quick lookup
 *    - itemToLocation: Map<itemName, CsvLocation> for persisting score changes
 *    - categories: Map<tag, Category> as before (for tag-based weights)
 * 4) Whenever an Item’s score is updated (like/dislike/accept), the primary item
 *    is adjusted by ±1.0, and every other item sharing ≥1 tag is adjusted by ±0.2.
 *    All affected CSV files are rewritten immediately to persist new scores.
 * 5) Supports reload() to clear everything and re-scan the data directory.
 */
public class PreferenceEngine {
    private final String dataDirectoryPath;

    // Tag-based categories (for cross-item weight influence)
    private final Map<String, Category> categories;

    // Flat list of every Item
    private final List<Item> allItems;

    // filename → list of Items in that file
    private final Map<String, List<Item>> fileToItems;

    // itemName → Item
    private final Map<String, Item> nameToItem;

    // filename → CsvFileData
    private final Map<String, CsvFileData> csvFilesData;

    // itemName → (which CSV filename, which row index in that file)
    private final Map<String, CsvLocation> itemToLocation;

    /**
     * Constructor: expects a directory path (e.g. "data").
     * It will load all "*.csv" files in that directory (non-recursively).
     */
    public PreferenceEngine(String dataDirectoryPath) {
        this.dataDirectoryPath = dataDirectoryPath;
        this.categories = new HashMap<>();
        this.allItems = new ArrayList<>();
        this.fileToItems = new HashMap<>();
        this.nameToItem = new HashMap<>();
        this.csvFilesData = new HashMap<>();
        this.itemToLocation = new HashMap<>();
        loadFromDirectory(dataDirectoryPath);
    }

    /**
     * Clear all loaded data and re-scan the data directory.
     */
    public void reload() {
        categories.clear();
        allItems.clear();
        fileToItems.clear();
        nameToItem.clear();
        csvFilesData.clear();
        itemToLocation.clear();
        loadFromDirectory(this.dataDirectoryPath);
    }

    /**
     * Scan the given directory for files ending in ".csv".
     * For each file, parse it and build Items + category assignments.
     */
    private void loadFromDirectory(String dirPath) {
        File folder = new File(dirPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("ERROR: Data directory '" + dirPath + "' not found or not a directory.");
            return;
        }

        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            System.out.println("No CSV files found in directory '" + dirPath + "'.");
            return;
        }

        // Step 1: Parse every CSV, populate CsvFileData objects
        for (File f : files) {
            parseCsvFile(f.toPath());
        }

        // Step 2: Build categories for every unique tag
        Set<String> uniqueTags = new HashSet<>();
        for (Item it : allItems) {
            uniqueTags.addAll(it.getTags());
        }
        for (String tag : uniqueTags) {
            categories.put(tag, new Category(tag));
        }

        // Step 3: Assign each Item to all Categories matching its tags
        for (Item it : allItems) {
            for (String tag : it.getTags()) {
                Category cat = categories.get(tag);
                if (cat != null) {
                    cat.addItem(it);
                }
            }
        }

        System.out.println("Loaded " + allItems.size() + " item(s) across " 
                            + categories.size() + " tag-based categories from "
                            + fileToItems.keySet().size() + " files.");
    }

    /**
     * Parse one CSV file at the given Path. Expected format per line:
     *    name,tags[,score]
     *  - tags = semicolon-separated list of tags (e.g. "Food;Italian;Comfort")
     *  - score is optional; if missing or unparsable, defaults to 0.0
     *
     * If the first non-empty line starts with "name," (case-insensitive),
     * we treat it as a header. If the header only has two columns "name","tags",
     * we augment it to ["name","tags","score"]. Otherwise if it already has 
     * three columns and the third is "score", we keep it.
     *
     * Builds:
     *   1) A CsvFileData object with headerColumns + all rows (each = String[3]).
     *   2) An Item for each row, with its parsed initial score.
     *   3) A mapping itemName → CsvLocation(filename, rowIndex).
     *   4) Populates fileToItems entry for this filename.
     */
    private void parseCsvFile(Path csvPath) {
        String filename = csvPath.getFileName().toString();
        List<String[]> rows = new ArrayList<>();
        String[] headerColumns = null;
        boolean hasHeader = false;
        List<Item> itemsInThisFile = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Check for header on the first non-empty line
                if (firstLine && line.toLowerCase().startsWith("name,")) {
                    hasHeader = true;
                    String[] rawCols = line.split(",", -1);
                    if (rawCols.length >= 3 && rawCols[2].equalsIgnoreCase("score")) {
                        headerColumns = new String[] { rawCols[0], rawCols[1], rawCols[2] };
                    } else {
                        // Augment two-column header to include "score"
                        headerColumns = new String[] { rawCols[0], rawCols[1], "score" };
                    }
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                // Split into up to 3 parts: name, tags, optional score
                String[] parts = line.split(",", 3);
                String itemName = parts[0].trim();
                if (itemName.isEmpty()) continue;

                // Parse tags (must exist)
                if (parts.length < 2 || parts[1].trim().isEmpty()) continue; 
                String tagsPart = parts[1].trim();
                String[] tagTokens = tagsPart.split(";");
                List<String> tagList = new ArrayList<>();
                for (String t : tagTokens) {
                    String tt = t.trim();
                    if (!tt.isEmpty()) {
                        tagList.add(tt);
                    }
                }
                if (tagList.isEmpty()) continue;

                // Parse score if present; else default to 0.0
                double scoreVal = 0.0;
                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    try {
                        scoreVal = Double.parseDouble(parts[2].trim());
                    } catch (NumberFormatException ex) {
                        scoreVal = 0.0;
                    }
                }

                // Build row array: [ name, tagsSemicolonString, scoreString ]
                String scoreString = Double.toString(scoreVal);
                rows.add(new String[] { itemName, tagsPart, scoreString });

                // Create Item with initialScore = parsed scoreVal
                Item newItem = new Item(itemName, tagList, scoreVal);
                allItems.add(newItem);
                itemsInThisFile.add(newItem);
                nameToItem.put(itemName, newItem);

                // Record location: this row’s index in 'rows'
                int rowIndex = rows.size() - 1;
                CsvLocation loc = new CsvLocation(filename, rowIndex);
                itemToLocation.put(itemName, loc);
            }
        } catch (IOException e) {
            System.out.println("Failed to parse " + csvPath.getFileName() + ": " + e.getMessage());
            return;
        }

        // If no header was detected, create a default one: ["name","tags","score"]
        if (!hasHeader) {
            headerColumns = new String[] { "name", "tags", "score" };
        }

        // Store CsvFileData
        CsvFileData fileData = new CsvFileData(filename, hasHeader, headerColumns, rows);
        csvFilesData.put(filename, fileData);

        // Store file → items
        fileToItems.put(filename, itemsInThisFile);
    }

    /**
     * Return a list of all available filenames (CSV names) in the data directory.
     */
    public Set<String> getAllFilenames() {
        return Collections.unmodifiableSet(fileToItems.keySet());
    }

    /**
     * Return the list of Items belonging to the given filename (exact match).
     * If filename not found, returns an empty list.
     */
    public List<Item> getItemsForFile(String filename) {
        List<Item> list = fileToItems.get(filename);
        return (list == null) ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /**
     * Update preferences for a primary itemName: 
     *  - Primary item: like   ⇒ +1.0 
     *                  dislike⇒ -1.0 (clamped at -0.9)
     *  - Every other item sharing ≥1 tag: adjust by ±0.2
     * After adjustments, rewrite all affected CSVs on disk.
     */
    public void updatePreferencesForItem(String itemName, boolean liked) {
        Item primary = nameToItem.get(itemName);
        if (primary == null) return;

        // Apply ±1.0 to primary
        if (liked) {
            primary.like();
        } else {
            primary.dislike();
        }

        // Track which files need rewriting
        Set<String> modifiedFiles = new HashSet<>();

        // Update primary row in its file
        CsvLocation primaryLoc = itemToLocation.get(itemName);
        if (primaryLoc != null) {
            CsvFileData primaryData = csvFilesData.get(primaryLoc.filename);
            String[] primaryRow = primaryData.rows.get(primaryLoc.rowIndex);
            primaryRow[2] = Double.toString(primary.getScore());
            modifiedFiles.add(primaryLoc.filename);
        }

        // For every other item, if shares ≥1 tag, adjust by ±0.2
        for (Item other : allItems) {
            if (other.getName().equals(itemName)) continue;
            // Check shared tags
            boolean shared = false;
            for (String t : other.getTags()) {
                if (primary.getTags().contains(t)) {
                    shared = true;
                    break;
                }
            }
            if (!shared) continue;

            // Adjust by ±0.2; clamp to ≥-0.9
            double delta = liked ? 0.2 : -0.2;
            double newScore = other.getScore() + delta;
            newScore = Math.max(newScore, -0.9);
            other.setScore(newScore);  // requires setter or direct access

            // Persist back to that other’s CSV row
            CsvLocation otherLoc = itemToLocation.get(other.getName());
            if (otherLoc != null) {
                CsvFileData otherData = csvFilesData.get(otherLoc.filename);
                String[] otherRow = otherData.rows.get(otherLoc.rowIndex);
                otherRow[2] = Double.toString(other.getScore());
                modifiedFiles.add(otherLoc.filename);
            }
        }

        // Rewrite each modified CSV to disk
        for (String fname : modifiedFiles) {
            CsvFileData data = csvFilesData.get(fname);
            if (data != null) {
                writeCsvFile(data);
            }
        }
    }

    /**
     * Overwrite the CSV file on disk with data.headerColumns + data.rows.
     */
    private void writeCsvFile(CsvFileData data) {
        File outFile = Paths.get(dataDirectoryPath, data.filename).toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, false))) {
            if (data.hasHeader) {
                writer.write(String.join(",", data.headerColumns));
                writer.newLine();
            }
            for (String[] row : data.rows) {
                writer.write(row[0] + "," + row[1] + "," + row[2]);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Failed to write CSV file " + data.filename + ": " + e.getMessage());
        }
    }

    // ───────────────────────────────────────────────────────────────────────────────
    // Inner classes: CsvFileData & CsvLocation
    // ───────────────────────────────────────────────────────────────────────────────

    /**
     * Holds the in-memory representation of one CSV file:
     *  - filename: e.g. "movies.csv"
     *  - hasHeader: whether a header was detected
     *  - headerColumns: length-3 array ["name","tags","score"]
     *  - rows: List<String[3]> where each row = { name, tags, score }
     */
    private static class CsvFileData {
        final String filename;
        final boolean hasHeader;
        final String[] headerColumns;
        final List<String[]> rows;

        CsvFileData(String filename, boolean hasHeader, String[] headerColumns, List<String[]> rows) {
            this.filename = filename;
            this.hasHeader = hasHeader;
            this.headerColumns = headerColumns;
            this.rows = rows;
        }
    }

    /**
     * Associates an itemName to the CSV filename and row index in that file.
     * Used to update the correct row on disk when score changes.
     */
    private static class CsvLocation {
        final String filename;
        final int rowIndex;

        CsvLocation(String filename, int rowIndex) {
            this.filename = filename;
            this.rowIndex = rowIndex;
        }
    }
}
