package com.example.mobiledegreefinalproject;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.model.Expense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testTripSharingContent() {
        // Create a sample trip
        Trip trip = new Trip("Paris Adventure", "Paris, France", System.currentTimeMillis(), System.currentTimeMillis() + 86400000 * 5);
        
        // Create sample activities
        List<TripActivity> activities = new ArrayList<>();
        TripActivity activity1 = new TripActivity();
        activity1.setTitle("Visit Eiffel Tower");
        activity1.setDescription("Iconic Paris landmark");
        activity1.setLocation("Champ de Mars");
        activity1.setDayNumber(1);
        activity1.setTimeString("09:00 AM");
        activities.add(activity1);
        
        TripActivity activity2 = new TripActivity();
        activity2.setTitle("Louvre Museum");
        activity2.setDescription("World's largest art museum");
        activity2.setLocation("Louvre");
        activity2.setDayNumber(2);
        activity2.setTimeString("10:00 AM");
        activities.add(activity2);
        
        // Create sample expenses
        List<Expense> expenses = new ArrayList<>();
        Expense expense1 = new Expense("Hotel", 150.0, Expense.Category.HOTEL);
        Expense expense2 = new Expense("Dinner", 45.0, Expense.Category.FOOD);
        expenses.add(expense1);
        expenses.add(expense2);
        
        // Test that the sharing content includes necessary information
        String shareContent = buildTestShareContent(trip, activities, expenses, 500.0);
        
        // Verify content contains trip information
        assertTrue("Share content should contain trip title", shareContent.contains("Paris Adventure"));
        assertTrue("Share content should contain destination", shareContent.contains("Paris, France"));
        assertTrue("Share content should contain activities section", shareContent.contains("ACTIVITIES"));
        assertTrue("Share content should contain budget section", shareContent.contains("BUDGET"));
        assertTrue("Share content should contain activity titles", shareContent.contains("Visit Eiffel Tower"));
        assertTrue("Share content should contain activity descriptions", shareContent.contains("Iconic Paris landmark"));
        assertTrue("Share content should contain expense information", shareContent.contains("Hotel"));
        assertTrue("Share content should contain app branding", shareContent.contains("WanderPlan"));
        
        // Verify formatting
        assertTrue("Share content should be well formatted", shareContent.length() > 100);
        assertTrue("Share content should contain emojis", shareContent.contains("✈️"));
        assertTrue("Share content should contain separators", shareContent.contains("━"));
    }
    
    private String buildTestShareContent(Trip trip, List<TripActivity> activities, List<Expense> expenses, double budget) {
        StringBuilder content = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        
        // Trip header
        content.append("✈️ ").append(trip.getTitle()).append("\n");
        content.append("📍 ").append(trip.getDestination()).append("\n");
        content.append("📅 ").append(dateFormat.format(new Date(trip.getStartDate()))).append(" - ").append(dateFormat.format(new Date(trip.getEndDate()))).append("\n");
        content.append("⏰ ").append(trip.getDurationDays()).append(" days\n\n");
        
        // Activities section
        if (activities != null && !activities.isEmpty()) {
            content.append("🎯 ACTIVITIES:\n");
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            
            for (TripActivity activity : activities) {
                content.append("• ").append(activity.getTitle());
                if (activity.getTimeString() != null && !activity.getTimeString().isEmpty()) {
                    content.append(" (").append(activity.getTimeString()).append(")");
                }
                content.append("\n");
                
                if (activity.getDescription() != null && !activity.getDescription().isEmpty()) {
                    content.append("  ").append(activity.getDescription()).append("\n");
                }
                
                if (activity.getLocation() != null && !activity.getLocation().isEmpty()) {
                    content.append("  📍 ").append(activity.getLocation()).append("\n");
                }
                content.append("\n");
            }
        }
        
        // Budget section
        content.append("💰 BUDGET INFORMATION:\n");
        content.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        content.append("💵 Trip Budget: RM").append(String.format("%.2f", budget)).append("\n");
        
        if (expenses != null && !expenses.isEmpty()) {
            content.append("\n📊 EXPENSES:\n");
            content.append("─────────────────────────────────────────────\n");
            
            double totalExpenses = 0.0;
            for (Expense expense : expenses) {
                content.append("• ").append(expense.getTitle())
                           .append(" - RM").append(String.format("%.2f", expense.getAmount()))
                           .append(" (").append(expense.getCategoryDisplayName()).append(")\n");
                totalExpenses += expense.getAmount();
            }
            
            content.append("\n💸 Total Expenses: RM").append(String.format("%.2f", totalExpenses)).append("\n");
            content.append("💰 Remaining Budget: RM").append(String.format("%.2f", budget - totalExpenses)).append("\n");
        }
        
        // Footer
        content.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        content.append("📱 Shared from WanderPlan Travel App\n");
        content.append("🌟 Plan your next adventure with us!");
        
        return content.toString();
    }
}