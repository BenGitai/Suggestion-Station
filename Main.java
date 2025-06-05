// Main.java

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final String DATA_DIR = "data";  // directory containing all CSV files

    public static void main(String[] args) {
        PreferenceEngine engine = new PreferenceEngine(DATA_DIR);

        while (true) {
            System.out.println();
            System.out.println("=== Stuck‐Picker App ===");
            System.out.println("[Type a number to choose a file]   [manage = create/edit lists]   [exit]");
            System.out.println("Available files:");
            List<String> filenames = new ArrayList<>(engine.getAllFilenames());
            Collections.sort(filenames);
            for (int i = 0; i < filenames.size(); i++) {
                System.out.printf("  [%d] %s%n", i + 1, filenames.get(i));
            }
            System.out.print(">> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye!");
                break;
            }
            if (input.equalsIgnoreCase("manage")) {
                manageLists(engine);
                continue;
            }

            String chosenFile = null;
            // Try numeric selection
            try {
                int sel = Integer.parseInt(input) - 1;
                if (sel >= 0 && sel < filenames.size()) {
                    chosenFile = filenames.get(sel);
                }
            } catch (NumberFormatException e) {
                // Not a number; check if it matches a filename directly
                if (engine.getAllFilenames().contains(input)) {
                    chosenFile = input;
                }
            }

            if (chosenFile == null) {
                System.out.println("→ Invalid selection. Please enter a number or filename.");
                continue;
            }

            handleFileLoop(engine, chosenFile);
        }
    }

    /**
     * Let the user create new CSV lists or edit existing ones.
     * After any change, reload the engine so new/edited files are picked up.
     */
    private static void manageLists(PreferenceEngine engine) {
        while (true) {
            System.out.println();
            System.out.println("=== Manage Lists ===");
            System.out.println("[1] Create new list");
            System.out.println("[2] Edit existing list");
            System.out.println("[3] Back");
            System.out.print("Choose an option (1-3): ");
            String choice = scanner.nextLine().trim();

            if (choice.equals("1")) {
                createNewList();
                engine.reload();
            } 
            else if (choice.equals("2")) {
                editExistingList();
                engine.reload();
            } 
            else if (choice.equals("3")) {
                return;
            } 
            else {
                System.out.println("→ Invalid option. Enter 1, 2, or 3.");
            }
        }
    }

    /**
     * Step-by-step: prompt for a new filename (with .csv), then read item/tag pairs
     * from the user, write a new CSV with a header, and save under data/.
     */
    private static void createNewList() {
        System.out.println();
        System.out.println("Enter new list filename (e.g. 'mylist.csv'):");
        String filename = scanner.nextLine().trim();
        if (!filename.toLowerCase().endsWith(".csv")) {
            System.out.println("→ Filename must end with .csv. Aborting.");
            return;
        }

        Path filePath = Paths.get(DATA_DIR, filename);
        if (Files.exists(filePath)) {
            System.out.println("→ File already exists. Aborting.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        System.out.println("Enter items for this list (leave name blank to finish).");
        while (true) {
            System.out.print("Item name: ");
            String itemName = scanner.nextLine().trim();
            if (itemName.isEmpty()) break;

            System.out.print("Tags (semicolon-separated): ");
            String tagsLine = scanner.nextLine().trim();
            if (tagsLine.isEmpty()) {
                System.out.println("→ Must enter at least one tag. Skipping item.");
                continue;
            }
            // Each row = { name, tags, score=0 }
            rows.add(new String[] { itemName, tagsLine, "0" });
        }

        if (rows.isEmpty()) {
            System.out.println("→ No items entered. Aborting file creation.");
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("name,tags,score");
            writer.newLine();
            for (String[] r : rows) {
                writer.write(r[0] + "," + r[1] + "," + r[2]);
                writer.newLine();
            }
            System.out.println("Created " + filename + " with " + rows.size() + " items.");
        } catch (IOException e) {
            System.out.println("Failed to write file: " + e.getMessage());
        }
    }

    /**
     * Let the user pick one existing CSV (by listing data/*.csv),
     * load its lines, then either add new items or remove existing ones.
     */
    private static void editExistingList() {
        File folder = new File(DATA_DIR);
        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            System.out.println("→ No CSV files found to edit.");
            return;
        }

        System.out.println();
        System.out.println("Select a file to edit:");
        for (int i = 0; i < files.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, files[i].getName());
        }
        System.out.print("Enter number (or blank to cancel): ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) return;

        int idx;
        try {
            idx = Integer.parseInt(line) - 1;
        } catch (NumberFormatException e) {
            System.out.println("→ Invalid input. Aborting edit.");
            return;
        }
        if (idx < 0 || idx >= files.length) {
            System.out.println("→ Index out of range. Aborting edit.");
            return;
        }

        Path filePath = files[idx].toPath();
        List<String[]> rows = new ArrayList<>();
        String[] header;

        // Read existing content
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String first = reader.readLine();
            if (first == null) {
                System.out.println("→ File is empty. Aborting.");
                return;
            }
            header = first.split(",");
            if (header.length < 3) {
                header = new String[] { header[0], header[1], "score" };
            }

            String lineRow;
            while ((lineRow = reader.readLine()) != null) {
                String[] parts = lineRow.split(",", 3);
                if (parts.length < 3) continue;
                rows.add(new String[] { parts[0], parts[1], parts[2] });
            }
        } catch (IOException e) {
            System.out.println("Failed to read file: " + e.getMessage());
            return;
        }

        // Edit loop
        while (true) {
            System.out.println();
            System.out.println("Editing: " + filePath.getFileName());
            System.out.println("[1] Add item");
            System.out.println("[2] Remove item");
            System.out.println("[3] View all items");
            System.out.println("[4] Save and return");
            System.out.print("Choose (1-4): ");
            String choice = scanner.nextLine().trim();

            if (choice.equals("1")) {
                System.out.print("Item name: ");
                String newName = scanner.nextLine().trim();
                if (newName.isEmpty()) {
                    System.out.println("→ Name cannot be blank.");
                    continue;
                }
                System.out.print("Tags (semicolon-separated): ");
                String newTags = scanner.nextLine().trim();
                if (newTags.isEmpty()) {
                    System.out.println("→ Must enter at least one tag.");
                    continue;
                }
                rows.add(new String[] { newName, newTags, "0" });
                System.out.println("Added: " + newName);
            }
            else if (choice.equals("2")) {
                if (rows.isEmpty()) {
                    System.out.println("→ No items to remove.");
                    continue;
                }
                System.out.println("Select item to remove:");
                for (int i = 0; i < rows.size(); i++) {
                    String[] r = rows.get(i);
                    System.out.printf("  [%d] %s (tags=%s, score=%s)%n", i + 1, r[0], r[1], r[2]);
                }
                System.out.print("Enter number (or blank to cancel): ");
                String numLine = scanner.nextLine().trim();
                if (numLine.isEmpty()) continue;
                int remIdx;
                try {
                    remIdx = Integer.parseInt(numLine) - 1;
                } catch (NumberFormatException e) {
                    System.out.println("→ Invalid input.");
                    continue;
                }
                if (remIdx < 0 || remIdx >= rows.size()) {
                    System.out.println("→ Index out of range.");
                    continue;
                }
                String removedName = rows.get(remIdx)[0];
                rows.remove(remIdx);
                System.out.println("Removed: " + removedName);
            }
            else if (choice.equals("3")) {
                if (rows.isEmpty()) {
                    System.out.println("→ No items in this list.");
                } else {
                    System.out.println("Current items:");
                    for (String[] r : rows) {
                        System.out.printf("  - %s (tags=%s, score=%s)%n", r[0], r[1], r[2]);
                    }
                }
            }
            else if (choice.equals("4")) {
                try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
                    writer.write(String.join(",", header));
                    writer.newLine();
                    for (String[] r : rows) {
                        writer.write(r[0] + "," + r[1] + "," + r[2]);
                        writer.newLine();
                    }
                    System.out.println("Saved changes to " + filePath.getFileName());
                } catch (IOException e) {
                    System.out.println("Failed to write file: " + e.getMessage());
                }
                break;
            }
            else {
                System.out.println("→ Please enter 1, 2, 3, or 4.");
            }
        }
    }

    private static void handleFileLoop(PreferenceEngine engine, String filename) {
        System.out.println();
        System.out.println("You selected file: " + filename);
        System.out.println("Type 'back' at any time to return to file list.");

        List<Item> itemsInFile = new ArrayList<>(engine.getItemsForFile(filename));
        if (itemsInFile.isEmpty()) {
            System.out.println("→ No items in this file.");
            return;
        }

        // Track skipped items (by name) for this session
        Set<String> skippedNames = new HashSet<>();

        while (true) {
            // Build list of eligible items (exclude skipped)
            List<Item> eligibleItems = itemsInFile.stream()
                    .filter(item -> !skippedNames.contains(item.getName()))
                    .collect(Collectors.toList());

            if (eligibleItems.isEmpty()) {
                System.out.println("No more options available in this file.");
                return;
            }

            // Weighted-random selection among eligible items
            Item suggestion = selectWeightedRandom(eligibleItems);
            System.out.println();
            System.out.println("Suggested: " + suggestion.getName());
            System.out.println("Options: [y = like]  [n = dislike]  [s = skip]  [back = return]");
            String feedback = scanner.nextLine().trim();

            if (feedback.equalsIgnoreCase("back")) {
                return;
            } 
            else if (feedback.equalsIgnoreCase("s")) {
                skippedNames.add(suggestion.getName());
                System.out.println("Skipping '" + suggestion.getName() + "'. Picking another...");
                continue;
            }

            boolean liked;
            if (feedback.equalsIgnoreCase("y")) {
                liked = true;
            } 
            else if (feedback.equalsIgnoreCase("n")) {
                liked = false;
            } 
            else {
                System.out.println("→ Please type 'y', 'n', 's', or 'back'.");
                continue;
            }

            // Adjust scores (primary + shared-tag items) and persist immediately
            engine.updatePreferencesForItem(suggestion.getName(), liked);

            if (!liked) {
                System.out.println("Marked '" + suggestion.getName() + "' as disliked. Picking another...");
                continue;
            }

            // If liked, ask whether to accept or skip
            System.out.println("You liked it. Accept or skip? (accept/skip)   [back to return]");
            String decision = scanner.nextLine().trim();
            if (decision.equalsIgnoreCase("back")) {
                return;
            } 
            else if (decision.equalsIgnoreCase("accept")) {
                System.out.println("Great! Enjoy: " + suggestion.getName());
                return;
            } 
            else if (decision.equalsIgnoreCase("skip")) {
                skippedNames.add(suggestion.getName());
                System.out.println("Skipping '" + suggestion.getName() + "' after liking. Picking another...");
                continue;
            } 
            else {
                System.out.println("→ Please type 'accept', 'skip', or 'back'.");
            }
        }
    }

    /**
     * Perform weighted-random selection over the provided items.
     * Each weight = (item.getScore() + 1.0), clamped to ≥0.1.
     */
    private static Item selectWeightedRandom(List<Item> items) {
        Random random = new Random();
        double totalWeight = 0.0;

        for (Item it : items) {
            double weight = it.getScore() + 1.0;
            if (weight < 0.1) weight = 0.1;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            return items.get(random.nextInt(items.size()));
        }

        double r = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Item it : items) {
            double weight = it.getScore() + 1.0;
            if (weight < 0.1) weight = 0.1;
            cumulative += weight;
            if (r <= cumulative) {
                return it;
            }
        }
        return items.get(items.size() - 1);
    }
}
