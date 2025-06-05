// PreferenceEngine.java

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PreferenceEngine:
 * 1) Scans a given directory for CSV files.
 * 2) Parses each line: 
 *      name,tags[,score]
 *    - tags = semicolon‐separated (e.g. "Food;Italian;Comfort")
 *    - score is optional; if missing, defaults to 0.0.
 * 3) Builds a global list of all Items (with their tags and initial score).
 * 4) Builds a Category for every unique tag, and assigns each Item to all matching Categories.
 * 5) Whenever an Item’s score is updated (like/dislike/accept), the corresponding CSV row
 *    is updated and the file is rewritten immediately.
 */
public class PreferenceEngine {
    private final Map<String, Category> categories;      // tag → Category
    private final List<Item> allItems;                   // flat list of every Item
    private final Map<Path, CsvFileData> csvFilesData;   // for each CSV path, its in‐memory data
    private final Map<String, CsvLocation> itemToLocation; // itemName → (which file, which row)

    /**
     * Constructor: expects a directory path (e.g. "data").
     * It will load all "*.csv" files in that directory (non‐recursively).
     */
    public PreferenceEngine(String dataDirectoryPath) {
        this.categories = new HashMap<>();
        this.allItems = new ArrayList<>();
        this.csvFilesData = new HashMap<>();
        this.itemToLocation = new HashMap<>();
        loadFromDirectory(dataDirectoryPath);
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
                            + categories.size() + " tag‐based categories.");
    }

    /**
     * Parse one CSV file at the given Path. Expected format per line:
     *    name,tags[,score]
     *  - tags = semicolon‐separated list (e.g. "Food;Italian;Comfort")
     *  - score is optional; if missing or unparsable, defaults to 0.0
     *
     * If the first non‐empty line starts with "name," (case‐insensitive),
     * we treat it as a header. If the header only has two columns "name","tags",
     * we augment it to ["name","tags","score"]. Otherwise if it already has 
     * three columns and the third is “score”, we keep it.
     *
     * Builds:
     *   1) A CsvFileData object with headerColumns + all rows (each = String[3]).
     *   2) An Item for each row, with its parsed initial score.
     *   3) A mapping itemName → CsvLocation(filePath, rowIndex).
     */
    private void parseCsvFile(Path csvPath) {
        List<String[]> rows = new ArrayList<>();
        String[] headerColumns = null;
        boolean hasHeader = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Check for header on the first non‐empty line
                if (firstLine && line.toLowerCase().startsWith("name,")) {
                    hasHeader = true;
                    String[] rawCols = line.split(",", -1);
                    if (rawCols.length >= 3 && rawCols[2].equalsIgnoreCase("score")) {
                        headerColumns = new String[] { rawCols[0], rawCols[1], rawCols[2] };
                    } else {
                        // Augment two‐column header to include "score"
                        headerColumns = new String[] { rawCols[0], rawCols[1], "score" };
                    }
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                // Split into up to 3 parts: name, tags, optional score
                String[] parts = line.split(",", 3);
                String itemName = parts[0].trim();
                if (itemName.isEmpty()) {
                    continue;
                }

                // Parse tags (must exist)
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    continue; // malformed line, skip
                }
                String tagsPart = parts[1].trim();
                String[] tagTokens = tagsPart.split(";");
                List<String> tagList = new ArrayList<>();
                for (String t : tagTokens) {
                    String tt = t.trim();
                    if (!tt.isEmpty()) {
                        tagList.add(tt);
                    }
                }
                if (tagList.isEmpty()) {
                    continue; // no valid tags, skip
                }

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

                // Record location: this row’s index in 'rows'
                int rowIndex = rows.size() - 1;
                CsvLocation loc = new CsvLocation(csvPath, rowIndex);
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

        // Create CsvFileData and store it
        CsvFileData fileData = new CsvFileData(csvPath, hasHeader, headerColumns, rows);
        csvFilesData.put(csvPath, fileData);
    }

    /**
     * Return an unmodifiable view of all categories (tag → Category).
     */
    public Map<String, Category> getAllCategories() {
        return categories;
    }

    /**
     * Return a Category by tag name (case‐sensitive). Null if not found.
     */
    public Category getCategory(String tagName) {
        return categories.get(tagName);
    }

    /**
     * Update preference (like/dislike/accept) for a given itemName in the given categoryName.
     * 1) Adjust the in‐memory Item’s raw score (via like() or dislike()).
     * 2) Locate the CSV row via itemToLocation, update its score field (column 2), 
     *    then rewrite that CSV file so the new score is saved on disk.
     */
    public void updatePreference(String categoryName, String itemName, boolean liked) {
        // 1) Adjust in‐memory Item
        Category cat = categories.get(categoryName);
        if (cat == null) {
            return;
        }
        Item targetItem = null;
        for (Item it : cat.getItems()) {
            if (it.getName().equalsIgnoreCase(itemName)) {
                targetItem = it;
                break;
            }
        }
        if (targetItem == null) {
            return;
        }

        // Apply like() or dislike()
        if (liked) {
            targetItem.like();
        } else {
            targetItem.dislike();
        }

        // 2) Persist new score back to the correct CSV
        CsvLocation loc = itemToLocation.get(targetItem.getName());
        if (loc == null) {
            return;
        }

        CsvFileData fileData = csvFilesData.get(loc.filePath);
        if (fileData == null) {
            return;
        }

        // Update the in‐memory row’s score column
        int rowIdx = loc.rowIndex;
        String[] row = fileData.rows.get(rowIdx);
        row[2] = Double.toString(targetItem.getScore());

        // Rewrite the entire CSV file
        writeCsvFile(fileData);
    }

    /**
     * Overwrite the CSV file on disk with fileData.headerColumns + fileData.rows.
     */
    private void writeCsvFile(CsvFileData fileData) {
        File outFile = fileData.filePath.toFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, false))) {
            // Write header if present
            if (fileData.hasHeader) {
                writer.write(String.join(",", fileData.headerColumns));
                writer.newLine();
            }

            // Write every row: name,tags,score
            for (String[] row : fileData.rows) {
                writer.write(row[0] + "," + row[1] + "," + row[2]);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Failed to write CSV file " + fileData.filePath.getFileName() + ": " + e.getMessage());
        }
    }


    // ───────────────────────────────────────────────────────────────────────────────
    // Inner classes: CsvFileData & CsvLocation
    // ───────────────────────────────────────────────────────────────────────────────

    /**
     * Holds the in‐memory representation of one CSV file:
     *  - filePath: path on disk
     *  - hasHeader: whether a header was detected
     *  - headerColumns: length‐3 array ["name","tags","score"]
     *  - rows: List<String[3]> where each row = { name, tags, score }
     */
    private static class CsvFileData {
        final Path filePath;
        final boolean hasHeader;
        final String[] headerColumns;
        final List<String[]> rows;

        CsvFileData(Path filePath, boolean hasHeader, String[] headerColumns, List<String[]> rows) {
            this.filePath = filePath;
            this.hasHeader = hasHeader;
            this.headerColumns = headerColumns;
            this.rows = rows;
        }
    }

    /**
     * Associates an itemName to the CSV file and the index in that file's rows
     * where this item lives. Used to update the correct row on disk when score changes.
     */
    private static class CsvLocation {
        final Path filePath;
        final int rowIndex;

        CsvLocation(Path filePath, int rowIndex) {
            this.filePath = filePath;
            this.rowIndex = rowIndex;
        }
    }
}
