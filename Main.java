// Main.java

import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final String DATA_DIR = "data";  // directory containing all CSV files

    public static void main(String[] args) {
        PreferenceEngine engine = new PreferenceEngine(DATA_DIR);

        System.out.println();
        System.out.println("=== Welcome to the Stuck‐Picker App ===");
        System.out.println("Tags (categories) are loaded from CSV files under: " + DATA_DIR);
        System.out.println();

        while (true) {
            System.out.println("Available tags:");
            for (String tagName : engine.getAllCategories().keySet()) {
                System.out.println("  - " + tagName);
            }
            System.out.println("Type a tag name to get suggestions, or 'exit' to quit:");
            String chosenTag = scanner.nextLine().trim();
            if (chosenTag.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye!");
                break;
            }
            if (!engine.getAllCategories().containsKey(chosenTag)) {
                System.out.println("→ Tag not found. Please try again.");
                continue;
            }
            handleTagLoop(engine, chosenTag);
        }
    }

    private static void handleTagLoop(PreferenceEngine engine, String tagName) {
        System.out.println();
        System.out.println("You picked tag: " + tagName);
        System.out.println("Type 'back' at any time to return to tag list.");

        Category category = engine.getCategory(tagName);
        if (category == null) {
            System.out.println("No such category. Returning...");
            return;
        }

        // Keep track of skipped items for this session
        Set<String> skippedNames = new HashSet<>();

        while (true) {
            // Build list of eligible items (exclude skipped)
            List<Item> eligibleItems = category.getItems().stream()
                .filter(item -> !skippedNames.contains(item.getName()))
                .collect(Collectors.toList());

            if (eligibleItems.isEmpty()) {
                System.out.println("No more options available in this category.");
                return;  // return to tag selection
            }

            // Weighted-random selection among eligible items
            Item suggestion = selectWeightedRandom(eligibleItems);
            System.out.println();
            System.out.println("Suggested: " + suggestion.getName());
            System.out.println("Options: [y = like]  [n = dislike]  [s = skip]  [back = return]");
            String feedback = scanner.nextLine().trim();

            if (feedback.equalsIgnoreCase("back")) {
                return;
            } else if (feedback.equalsIgnoreCase("s")) {
                // Mark as skipped for this session
                skippedNames.add(suggestion.getName());
                System.out.println("Skipping '" + suggestion.getName() + "'. Picking another...");
                continue;
            }

            boolean liked;
            if (feedback.equalsIgnoreCase("y")) {
                liked = true;
            } else if (feedback.equalsIgnoreCase("n")) {
                liked = false;
            } else {
                System.out.println("→ Please type 'y', 'n', 's', or 'back'.");
                continue;
            }

            // Adjust raw score for this item and persist immediately
            engine.updatePreference(tagName, suggestion.getName(), liked);

            if (!liked) {
                // If disliked, immediately pick another
                System.out.println("Marked '" + suggestion.getName() + "' as disliked. Picking another...");
                continue;
            }

            // If liked, ask whether to accept or skip
            System.out.println("You liked it. Accept or skip? (accept/skip)   [back to return]");
            String decision = scanner.nextLine().trim();
            if (decision.equalsIgnoreCase("back")) {
                return;
            } else if (decision.equalsIgnoreCase("accept")) {
                System.out.println("Great! Enjoy: " + suggestion.getName());
                return;
            } else if (decision.equalsIgnoreCase("skip")) {
                skippedNames.add(suggestion.getName());
                System.out.println("Skipping '" + suggestion.getName() + "' after liking. Picking another...");
                continue;
            } else {
                System.out.println("→ Please type 'accept', 'skip', or 'back'.");
            }
        }
    }

    /**
     * Perform weighted-random selection over the provided items.
     * Weight = (item.getScore() + 1.0), clamped to minimum 0.1.
     */
    private static Item selectWeightedRandom(List<Item> items) {
        Random random = new Random();
        double totalWeight = 0.0;

        for (Item it : items) {
            double weight = it.getScore() + 1.0;
            if (weight < 0.1) {
                weight = 0.1;
            }
            totalWeight += weight;
        }

        double r = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Item it : items) {
            double weight = it.getScore() + 1.0;
            if (weight < 0.1) {
                weight = 0.1;
            }
            cumulative += weight;
            if (r <= cumulative) {
                return it;
            }
        }

        // Fallback
        return items.get(items.size() - 1);
    }
}
