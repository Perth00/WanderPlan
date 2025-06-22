package com.example.mobiledegreefinalproject.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Expense {
    public enum Category {
        FOOD("Food", "🍔"),
        TRANSPORT("Transport", "🚗"),
        HOTEL("Hotel", "🏨"),
        ACTIVITIES("Activities", "🎟️"),
        SHOPPING("Shopping", "🛍️"),
        OTHER("Other", "📝");

        private final String displayName;
        private final String emoji;

        Category(String displayName, String emoji) {
            this.displayName = displayName;
            this.emoji = emoji;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmoji() {
            return emoji;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private String id;
    private String title;
    private double amount;
    private Category category;
    private long timestamp;
    private String note;

    public Expense() {
        this.id = generateId();
        this.timestamp = System.currentTimeMillis();
        this.category = Category.OTHER;
    }

    public Expense(String title, double amount, Category category) {
        this();
        this.title = title;
        this.amount = amount;
        this.category = category;
    }

    public Expense(String title, double amount, Category category, String note) {
        this(title, amount, category);
        this.note = note;
    }

    private String generateId() {
        return "expense_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    // Utility methods
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedAmount() {
        return String.format(Locale.getDefault(), "RM%.2f", amount);
    }

    public String getCategoryEmoji() {
        return category.getEmoji();
    }

    public String getCategoryDisplayName() {
        return category.getDisplayName();
    }

    @Override
    public String toString() {
        return "Expense{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", amount=" + amount +
                ", category=" + category +
                ", timestamp=" + timestamp +
                '}';
    }
}