// Item.java

import java.util.List;

public class Item {
    private final String name;
    private final List<String> tags;
    private double score;  // preference score; can be negative or positive

    /**
     * Construct a new Item with the given name, tags, and an explicit initial score.
     */
    public Item(String name, List<String> tags, double initialScore) {
        this.name = name;
        this.tags = tags;
        this.score = initialScore;
    }

    /**
     * Construct a new Item with the given name and tags. Initial score defaults to 0.
     */
    public Item(String name, List<String> tags) {
        this(name, tags, 0.0);
    }

    public String getName() {
        return name;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * Returns the raw preference score (can be negative, zero, or positive).
     */
    public double getScore() {
        return score;
    }

    /**
     * Call when user “likes” this item. Increases its raw score by +1.0.
     */
    public void like() {
        this.score += 1.0;
    }

    /**
     * Call when user “dislikes” this item. Decreases its raw score by 1.0,
     * but never lets it drop below -0.9 so that weight = (score + 1.0) never goes to zero or negative.
     */
    public void dislike() {
        this.score = Math.max(this.score - 1.0, -0.9);
    }
}
