// Category.java

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Category {
    private final String name;
    private final List<Item> items;
    private final Random random;

    public Category(String name) {
        this.name = name;
        this.items = new ArrayList<>();
        this.random = new Random();
    }

    public String getName() {
        return name;
    }

    public List<Item> getItems() {
        return items;
    }

    public void addItem(Item item) {
        items.add(item);
    }

    /**
     * Return a random Item, where each item's weight = (item.getScore() + 1.0),
     * clamped so that weight ≥ 0.1. This ensures even a negative-scored item
     * still has a small chance.
     */
    public Item selectRandomItem() {
        double totalWeight = 0.0;
        for (Item it : items) {
            double weight = it.getScore() + 1.0;
            if (weight < 0.1) {
                weight = 0.1;
            }
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            int idx = random.nextInt(items.size());
            return items.get(idx);
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
