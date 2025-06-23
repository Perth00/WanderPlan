package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.mobiledegreefinalproject.adapter.ModernExpenseAdapter;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.WanderPlanDatabase;
import com.example.mobiledegreefinalproject.database.TripDao;
import com.example.mobiledegreefinalproject.model.Expense;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.example.mobiledegreefinalproject.repository.BudgetRepository;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BudgetFragment extends Fragment implements ModernExpenseAdapter.OnExpenseActionListener {

    private static final String TAG = "BudgetFragment";

    // UI Components
    private TextView totalBudgetText;
    private TextView totalSpentText;
    private TextView remainingText;
    private RecyclerView expensesRecyclerView;
    private FloatingActionButton fabAddExpense;
    private PieChart pieChart;
    private ChipGroup chipGroupCategories;
    private LinearLayout emptyStateLayout;
    private ImageView editBudgetIcon;
    
    // Trip Selector Components
    private LinearLayout tripSelectorLayout;
    private TextView selectedTripText;
    private android.widget.ImageView dropdownArrow;
    
    // Data
    private List<Expense> expenses;
    private ModernExpenseAdapter expenseAdapter;
    private double totalBudget = 2000.00; // Default budget
    
    // Trip-based Data
    private List<Trip> availableTrips;
    private Trip selectedTrip;
    private Map<Integer, Double> tripBudgets; // Trip ID -> Budget
    private Map<Integer, List<Expense>> tripExpenses; // Trip ID -> Expenses
    
    // Database and Firebase
    private TripRepository tripRepository;
    private BudgetRepository budgetRepository;
    private UserManager userManager;
    
    // Persistence
    private SharedPreferences sharedPreferences;
    private Gson gson;
    
    // Animation and Sound
    private MediaPlayer successSound;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
        initViews(view);
            initializeData();
        setupRecyclerView();
            setupPieChart();
            setupCategoryFilter();
        setupClickListeners();
        updateBudgetDisplay();
            updateChartData();
            // Ensure FAB is always visible from the start
            updateFabVisibility();
            // Update edit budget icon visibility
            updateEditBudgetIconVisibility();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            showErrorToast("Error initializing budget page");
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh trips when user returns to this fragment
        // This handles cases where user created/deleted trips in other screens
        if (tripRepository != null) {
            refreshTrips();
        }
        
        // Refresh budget data from Firebase for logged-in users
        if (userManager != null && userManager.isLoggedIn() && budgetRepository != null) {
            refreshBudgetDataFromFirebase();
        }
        
        // Ensure the default filter is properly initialized when navigating to this fragment
        // This fixes the issue where expenses don't show when clicking the navigation button
        if (chipGroupCategories != null && expenseAdapter != null) {
            chipGroupCategories.postDelayed(() -> {
                initializeDefaultFilter();
                updateBudgetDisplay();
                updateChartData();
                updateEmptyState();
            }, 100); // Small delay to ensure data is fully loaded
        }
    }

    private void initViews(View view) {
        totalBudgetText = view.findViewById(R.id.tv_total_budget);
        totalSpentText = view.findViewById(R.id.tv_total_spent);
        remainingText = view.findViewById(R.id.tv_remaining);
        expensesRecyclerView = view.findViewById(R.id.rv_expenses);
        fabAddExpense = view.findViewById(R.id.fab_add_expense);
        pieChart = view.findViewById(R.id.pie_chart_expenses);
        chipGroupCategories = view.findViewById(R.id.chip_group_categories);
        emptyStateLayout = view.findViewById(R.id.layout_empty_state);
        editBudgetIcon = view.findViewById(R.id.iv_edit_budget);
        
        // Trip selector components
        tripSelectorLayout = view.findViewById(R.id.layout_trip_selector);
        selectedTripText = view.findViewById(R.id.tv_selected_trip);
        dropdownArrow = view.findViewById(R.id.iv_dropdown_arrow);
    }

    private void initializeData() {
        expenses = new ArrayList<>();
        
        // Initialize trip-based data structures
        availableTrips = new ArrayList<>();
        tripBudgets = new HashMap<>();
        tripExpenses = new HashMap<>();
        
        // Initialize persistence
        Context context = getContext();
        if (context != null) {
            sharedPreferences = context.getSharedPreferences("BudgetFragment", Context.MODE_PRIVATE);
            gson = new Gson();
            tripRepository = TripRepository.getInstance(context);
            budgetRepository = BudgetRepository.getInstance(context);
            userManager = UserManager.getInstance(context);
            
            // Load saved data (Firebase or local depending on login status)
            loadBudgetData();
        }
        
        // Create default "All Trips" option
        selectedTrip = null; // null means "All Trips"
        
        // Load real trips from database
        loadUserTrips();
        
        Log.d(TAG, "Initialized with " + expenses.size() + " expenses and " + availableTrips.size() + " trips");
    }
    
    private void setupRecyclerView() {
        try {
            expenseAdapter = new ModernExpenseAdapter(expenses);
            expenseAdapter.setOnExpenseActionListener(this);
        expensesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        expensesRecyclerView.setAdapter(expenseAdapter);
            
            // Ensure the adapter starts with all expenses visible (no filter applied)
            expenseAdapter.clearFilter();
            
            updateEmptyState();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView", e);
            showErrorToast("Error setting up expense list");
        }
    }

    private void setupPieChart() {
        try {
            if (pieChart != null) {
                pieChart.setUsePercentValues(true);
                pieChart.getDescription().setEnabled(false);
                pieChart.setDragDecelerationFrictionCoef(0.95f);
                pieChart.setDrawHoleEnabled(true);
                pieChart.setHoleColor(Color.WHITE);
                pieChart.setTransparentCircleRadius(61f);
                pieChart.setHoleRadius(45f);
                pieChart.setDrawCenterText(true);
                pieChart.setCenterText("💰\nExpense\nBreakdown");
                pieChart.setCenterTextSize(14f);
                pieChart.setCenterTextColor(ContextCompat.getColor(getContext(), R.color.navy));
                pieChart.setRotationAngle(0);
                pieChart.setRotationEnabled(true);
                pieChart.setHighlightPerTapEnabled(true);
                pieChart.getLegend().setEnabled(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up pie chart", e);
            // Continue without pie chart if there's an error
        }
    }

    private void setupCategoryFilter() {
        try {
            if (chipGroupCategories != null) {
                chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
                    if (checkedIds.isEmpty()) {
                        return;
                    }
                    
                    int checkedId = checkedIds.get(0);
                    Chip selectedChip = group.findViewById(checkedId);
                    
                    if (selectedChip != null) {
                        String chipText = selectedChip.getText().toString();
                        filterExpensesByCategory(chipText);
                    }
                });
                
                // Initialize with "All" filter - ensure all expenses are visible by default
                initializeDefaultFilter();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up category filter", e);
        }
    }
    
    /**
     * Initialize the default filter state to show all expenses
     */
    private void initializeDefaultFilter() {
        try {
            // Clear any existing filter on the adapter
            if (expenseAdapter != null) {
                expenseAdapter.clearFilter();
            }
            
            // Post a runnable to ensure the UI is fully loaded before setting the default chip
            if (chipGroupCategories != null) {
                chipGroupCategories.post(() -> {
                    try {
                        // Find and check the "All" chip if it exists
                        if (chipGroupCategories.getChildCount() > 0) {
                            for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
                                View child = chipGroupCategories.getChildAt(i);
                                if (child instanceof Chip) {
                                    Chip chip = (Chip) child;
                                    if ("All".equals(chip.getText().toString())) {
                                        chip.setChecked(true);
                                        break;
                                    }
                                }
                            }
                        }
                        
                        Log.d(TAG, "Initialized default filter to show all expenses");
                    } catch (Exception e) {
                        Log.e(TAG, "Error in posted initialization", e);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing default filter", e);
        }
    }

    private void filterExpensesByCategory(String category) {
        try {
            if (expenseAdapter != null) {
                if ("All".equals(category)) {
                    expenseAdapter.clearFilter();
                } else {
                    Expense.Category expenseCategory = getCategoryFromString(category);
                    if (expenseCategory != null) {
                        expenseAdapter.filterByCategory(expenseCategory);
                    }
                }
                updateEmptyState();
                
                // Update chart and budget display to reflect the filtered data
                updateChartData();
                updateBudgetDisplay();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error filtering expenses", e);
        }
    }

    private Expense.Category getCategoryFromString(String categoryString) {
        try {
            for (Expense.Category category : Expense.Category.values()) {
                if (category.getDisplayName().equals(categoryString)) {
                    return category;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting category from string", e);
        }
        return null;
    }

    private void setupClickListeners() {
        if (fabAddExpense != null) {
        fabAddExpense.setOnClickListener(v -> {
                try {
                    // Check if "All Trips" is selected
                    if (selectedTrip == null) {
                        // Show trip selector to let user choose a trip
                        if (availableTrips.isEmpty()) {
                            showErrorToast("Create a trip first to add expenses");
                        } else {
                        showErrorToast("Please select a specific trip to add expenses");
                            // Optionally, automatically open trip selector
                            new android.os.Handler().postDelayed(() -> {
                                showTripSelectorDialog();
                            }, 1000);
                        }
                        return;
                    }
                showAddExpenseDialog();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing add expense dialog", e);
                    showErrorToast("Error opening add expense dialog");
                }
            });
        }
        
        // Allow clicking on budget to change it
        if (totalBudgetText != null) {
            totalBudgetText.setOnClickListener(v -> showBudgetSetupDialog());
        }
        
        // Edit budget icon click listener
        if (editBudgetIcon != null) {
            editBudgetIcon.setOnClickListener(v -> showBudgetSetupDialog());
        }
        
        // Trip selector click listener
        if (tripSelectorLayout != null) {
            tripSelectorLayout.setOnClickListener(v -> showTripSelectorDialog());
        }
    }

    private void updateBudgetDisplay() {
        try {
            // If "All Trips" is selected, always recalculate the total budget
            if (selectedTrip == null) {
                totalBudget = calculateTotalBudgetAllTrips();
            }
            
            double totalSpent = calculateTotalSpent();
            double remaining = totalBudget - totalSpent;
            
            if (totalBudgetText != null) {
                totalBudgetText.setText(String.format(Locale.getDefault(), "RM%.2f", totalBudget));
            }
            if (totalSpentText != null) {
                totalSpentText.setText(String.format(Locale.getDefault(), "RM%.2f", totalSpent));
            }
            if (remainingText != null) {
                remainingText.setText(String.format(Locale.getDefault(), "RM%.2f", remaining));
                
                // Update text color based on remaining budget
                Context context = getContext();
                if (context != null) {
                    if (remaining < 0) {
                        remainingText.setTextColor(ContextCompat.getColor(context, R.color.error));
                    } else if (remaining < totalBudget * 0.2) {
                        remainingText.setTextColor(ContextCompat.getColor(context, R.color.warning));
                    } else {
                        remainingText.setTextColor(ContextCompat.getColor(context, R.color.success));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating budget display", e);
        }
    }

    private double calculateTotalSpent() {
        double total = 0.0;
        try {
            // Use the same filtered expenses that are shown in the list and chart
            List<Expense> expensesToCalculate = getFilteredExpensesFromAdapter();
            if (expensesToCalculate != null) {
                for (Expense expense : expensesToCalculate) {
                    total += expense.getAmount();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating total spent", e);
        }
        return total;
    }

    private void updateChartData() {
        try {
            if (pieChart == null) return;
            
            Map<Expense.Category, Double> categoryTotals = new HashMap<>();
            
            // Get the same filtered expenses that the adapter is showing
            List<Expense> expensesToChart = expenses;
            if (expenseAdapter != null) {
                // Create a method to get filtered expenses from adapter
                expensesToChart = getFilteredExpensesFromAdapter();
            }
            
            // Check if a specific category filter is active
            Expense.Category activeFilter = getCurrentActiveFilter();
            
            List<PieEntry> entries = new ArrayList<>();
            Context context = getContext();
            if (context == null) return;
            
            int[] colors = {
                ContextCompat.getColor(context, R.color.primary),
                ContextCompat.getColor(context, R.color.secondary),
                ContextCompat.getColor(context, R.color.warning),
                ContextCompat.getColor(context, R.color.success),
                ContextCompat.getColor(context, R.color.lavender),
                ContextCompat.getColor(context, R.color.medium_grey)
            };
            
            if (activeFilter != null) {
                // Specific category filter active - show individual expenses within that category
                if (expensesToChart != null && !expensesToChart.isEmpty()) {
                    int colorIndex = 0;
                    for (Expense expense : expensesToChart) {
                        if (expense.getCategory() == activeFilter && expense.getAmount() > 0) {
                            entries.add(new PieEntry((float)expense.getAmount(), expense.getTitle()));
                            colorIndex++;
                        }
                    }
                    
                    // Calculate category total for center text
                    double categoryTotal = 0.0;
                    for (Expense expense : expensesToChart) {
                        if (expense.getCategory() == activeFilter) {
                            categoryTotal += expense.getAmount();
                        }
                    }
                    categoryTotals.put(activeFilter, categoryTotal);
                }
            } else {
                // No specific filter or "All" selected - show category breakdown (original logic)
                if (expensesToChart != null && !expensesToChart.isEmpty()) {
                    for (Expense expense : expensesToChart) {
                        Expense.Category category = expense.getCategory();
                        categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + expense.getAmount());
                    }
                }
                
                // Add only categories with expenses > 0
                for (Map.Entry<Expense.Category, Double> entry : categoryTotals.entrySet()) {
                    double value = entry.getValue();
                    if (value > 0) {  // Only show categories with actual expenses
                        entries.add(new PieEntry((float)value, entry.getKey().getDisplayName()));
                    }
                }
            }
            
            if (entries.isEmpty()) {
                pieChart.setNoDataText("💡 Add expenses to see breakdown");
                pieChart.clear();
                return;
            }
            
            PieDataSet dataSet = new PieDataSet(entries, activeFilter != null ? "Individual Expenses" : "Expense Categories");
            dataSet.setColors(colors);
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueTextSize(11f);
            dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format("%.1f%%", value);
                }
            });
            
            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            
            // Update center text based on active filter
            updateChartCenterText(categoryTotals);
            
            pieChart.animateY(1000);
            pieChart.invalidate();
            
            String chartType = activeFilter != null ? "individual expenses for " + activeFilter.getDisplayName() : "categories";
            Log.d(TAG, "Chart updated with " + entries.size() + " " + chartType + " from " + expensesToChart.size() + " filtered expenses");
        } catch (Exception e) {
            Log.e(TAG, "Error updating chart data", e);
        }
    }

    /**
     * Helper method to get the same filtered expenses that the adapter is currently showing
     */
    private List<Expense> getFilteredExpensesFromAdapter() {
        List<Expense> filteredExpenses = new ArrayList<>();
        
        if (expenseAdapter != null && expenses != null) {
            // Check if there's an active filter by looking at the current category chips
            Expense.Category activeFilter = getCurrentActiveFilter();
            
            if (activeFilter == null) {
                // No filter active, return all expenses
                filteredExpenses.addAll(expenses);
            } else {
                // Filter active, return only expenses matching the category
                for (Expense expense : expenses) {
                    if (expense.getCategory() == activeFilter) {
                        filteredExpenses.add(expense);
                    }
                }
            }
        }
        
        return filteredExpenses;
    }

    /**
     * Helper method to determine which category filter is currently active
     */
    private Expense.Category getCurrentActiveFilter() {
        if (chipGroupCategories != null) {
            int checkedId = chipGroupCategories.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip checkedChip = chipGroupCategories.findViewById(checkedId);
                if (checkedChip != null) {
                    String categoryText = checkedChip.getText().toString();
                    if (!"All".equals(categoryText)) {
                        return getCategoryFromString(categoryText);
                    }
                }
            }
        }
        return null; // No filter or "All" is selected
    }
    
    /**
     * Update the chart's center text to show category total when a filter is active
     */
    private void updateChartCenterText(Map<Expense.Category, Double> categoryTotals) {
        try {
            if (pieChart == null) return;
            
            Expense.Category activeFilter = getCurrentActiveFilter();
            
            if (activeFilter == null) {
                // No filter active - show default breakdown text
                pieChart.setCenterText("💰\nExpense\nBreakdown");
            } else {
                // Specific category filter active - show that category's total
                double categoryTotal = categoryTotals.getOrDefault(activeFilter, 0.0);
                String categoryEmoji = activeFilter.getEmoji();
                String categoryName = activeFilter.getDisplayName();
                String formattedAmount = String.format(Locale.getDefault(), "RM%.2f", categoryTotal);
                
                // Create multi-line center text showing category and total
                String centerText = String.format("%s\n%s\n%s", categoryEmoji, categoryName, formattedAmount);
                pieChart.setCenterText(centerText);
                
                Log.d(TAG, "Chart center text updated for " + categoryName + ": " + formattedAmount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating chart center text", e);
        }
    }

    private void updateEmptyState() {
        try {
            if (expenseAdapter != null && emptyStateLayout != null && expensesRecyclerView != null) {
                boolean isEmpty = expenseAdapter.getItemCount() == 0;
                emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                expensesRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating empty state", e);
        }
    }
    
    private void showAddExpenseDialog() {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null, cannot show dialog");
            return;
        }
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Add New Expense");
            
            // Create custom layout for the dialog
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
            // Title input - using regular EditText instead of TextInputEditText
            EditText titleInput = new EditText(context);
            titleInput.setHint("Expense title (e.g., Dinner, Taxi, Hotel)");
        layout.addView(titleInput);
        
            // Amount input
            EditText amountInput = new EditText(context);
            amountInput.setHint("Amount (RM)");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountInput);
        
            // Category spinner
            TextView categoryLabel = new TextView(context);
            categoryLabel.setText("Category:");
            categoryLabel.setPadding(0, 20, 0, 8);
            layout.addView(categoryLabel);
            
            Spinner categorySpinner = new Spinner(context);
            ArrayAdapter<Expense.Category> spinnerAdapter = new ArrayAdapter<>(
                context, 
                android.R.layout.simple_spinner_item, 
                Expense.Category.values()
            );
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(spinnerAdapter);
            layout.addView(categorySpinner);
            
            // Date picker button
            TextView dateLabel = new TextView(context);
            dateLabel.setText("Date:");
            dateLabel.setPadding(0, 20, 0, 8);
            layout.addView(dateLabel);
            
            TextView dateButton = new TextView(context);
            dateButton.setText(new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(new Date()));
            // Use ContextCompat for getting drawables and colors
            dateButton.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_button_primary));
            dateButton.setTextColor(ContextCompat.getColor(context, R.color.white));
            dateButton.setPadding(32, 16, 32, 16);
            dateButton.setClickable(true);
            
            final Calendar selectedDate = Calendar.getInstance();
            dateButton.setOnClickListener(v -> {
                try {
                    DatePickerDialog datePickerDialog = new DatePickerDialog(
                        context,
                        (view, year, month, dayOfMonth) -> {
                            selectedDate.set(year, month, dayOfMonth);
                            dateButton.setText(new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
                                .format(selectedDate.getTime()));
                        },
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)
                    );
                    datePickerDialog.show();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing date picker", e);
                    showErrorToast("Error opening date picker");
                }
            });
            layout.addView(dateButton);
            
            builder.setView(layout);
            
            builder.setPositiveButton("Add Expense", (dialog, which) -> {
                try {
                    String title = titleInput.getText().toString().trim();
                    String amountStr = amountInput.getText().toString().trim();
                    Expense.Category category = (Expense.Category) categorySpinner.getSelectedItem();
                    
                    if (validateInput(title, amountStr)) {
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                                showErrorToast("Amount must be greater than 0");
                    return;
                }
                
                            // Create new expense with tripId for Firebase sync
                            Expense newExpense;
                            if (selectedTrip != null) {
                                newExpense = new Expense(title, amount, category, null, selectedTrip.getId());
                            } else {
                                newExpense = new Expense(title, amount, category);
                            }
                            newExpense.setTimestamp(selectedDate.getTimeInMillis());
                            
                            // Add to the specific trip's expense list (selectedTrip is guaranteed to be not null here)
                            if (selectedTrip != null) {
                                List<Expense> tripExpenseList = tripExpenses.get(selectedTrip.getId());
                                if (tripExpenseList == null) {
                                    tripExpenseList = new ArrayList<>();
                                    tripExpenses.put(selectedTrip.getId(), tripExpenseList);
                                }
                                tripExpenseList.add(newExpense);
                                
                                // Update the current expenses list to match the selected trip
                                expenses = tripExpenseList;
                                
                                // For logged-in users, sync to Firebase
                                if (userManager != null && userManager.isLoggedIn()) {
                                    syncExpenseToFirebase(newExpense);
                                }
                            }
                            
                            // Save data to persistence
                            saveBudgetData();
                            
                            Log.d(TAG, "Added expense: " + newExpense.getTitle() + " - RM" + newExpense.getAmount());
                            Log.d(TAG, "Total expenses now: " + expenses.size());
                            
                            // Update adapter with the correct expense list
                            if (expenseAdapter != null) {
                                expenseAdapter.updateExpenses(expenses);
                                Log.d(TAG, "Updated adapter with " + expenses.size() + " expenses");
                            }
                updateBudgetDisplay();
                            updateChartData();
                            updateEmptyState();
                
                            // Show success animation - but safely
                            showSuccessAnimationSafe();
                    
            } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid number format", e);
                            showErrorToast("Please enter a valid amount");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error adding expense", e);
                    showErrorToast("Error adding expense");
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating add expense dialog", e);
            showErrorToast("Error creating dialog");
        }
    }

    private boolean validateInput(String title, String amount) {
        if (title.isEmpty()) {
            showErrorToast("Please enter expense title");
            return false;
        }
        if (amount.isEmpty()) {
            showErrorToast("Please enter amount");
            return false;
        }
        return true;
    }

    private void showSuccessAnimationSafe() {
        Context context = getContext();
        if (context == null) return;
        
        try {
            // Create the success animation dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_success_animation, null);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            dialog.setCancelable(true);
            // Remove the transparent background so our dark overlay shows
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            // Find views in the dialog
            com.airbnb.lottie.LottieAnimationView animationView = dialogView.findViewById(R.id.animation_success);
            TextView messageView = dialogView.findViewById(R.id.tv_success_message);
            
            // Set custom message
            if (messageView != null) {
                messageView.setText(getString(R.string.expense_added_successfully));
            }
            
            // Auto dismiss after animation completes
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing animation dialog", e);
                }
            }, 2500); // 2.5 seconds
            
            dialog.show();
            playSuccessSound();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing success animation", e);
            // Fallback to simple toast
            Toast.makeText(context, "✅ Expense added successfully!", Toast.LENGTH_SHORT).show();
            playSuccessSound();
        }
    }

    private void showEditSuccessAnimationSafe() {
        Context context = getContext();
        if (context == null) return;
        
        try {
            // Create the success animation dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_success_animation, null);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            dialog.setCancelable(true);
            // Remove the transparent background so our dark overlay shows
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            // Find views in the dialog
            com.airbnb.lottie.LottieAnimationView animationView = dialogView.findViewById(R.id.animation_success);
            TextView messageView = dialogView.findViewById(R.id.tv_success_message);
            
            // Set custom message for edit success
            if (messageView != null) {
                messageView.setText(getString(R.string.expense_updated_successfully));
            }
            
            // Auto dismiss after animation completes
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing edit animation dialog", e);
                }
            }, 2500); // 2.5 seconds
            
            dialog.show();
            playSuccessSound();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing edit success animation", e);
            // Fallback to simple toast
            Toast.makeText(context, "✅ " + getString(R.string.expense_updated_successfully), Toast.LENGTH_SHORT).show();
            playSuccessSound();
        }
    }

    private void playSuccessSound() {
        try {
            Context context = getContext();
            if (context == null) return;
            
            if (successSound != null) {
                successSound.release();
            }
            
            // Try to use custom success sound first
            successSound = MediaPlayer.create(context, R.raw.success_sound);
            if (successSound != null) {
                successSound.setOnCompletionListener(mediaPlayer -> {
                    try {
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MediaPlayer", e);
                    }
                });
                successSound.start();
            } else {
                Log.w(TAG, "Custom success sound not available, using default notification sound");
                // Fallback to default notification sound
                successSound = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                if (successSound != null) {
                    successSound.start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing success sound", e);
            // Handle sound playback error silently
        }
    }

    private void showErrorToast(String message) {
        try {
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast", e);
        }
    }

    @Override
    public void onEditExpense(Expense expense) {
        if (selectedTrip == null) {
            showErrorToast("Please select a specific trip to edit expenses");
            return;
        }
        showEditExpenseDialog(expense);
    }

        @Override
    public void onDeleteExpense(Expense expense) {
        if (selectedTrip == null) {
            showErrorToast("Please select a specific trip to delete expenses");
            return;
        }
        showDeleteConfirmationDialog(expense);
    }

    @Override
    public void onExpenseClick(Expense expense) {
        // Show expense details or edit dialog
        if (selectedTrip == null) {
            showErrorToast("Please select a specific trip to view expense details");
            return;
        }
        showExpenseDetailsDialog(expense);
    }

    private void showEditExpenseDialog(Expense expense) {
        Context context = getContext();
        if (context == null || expense == null) return;
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("✏️ Edit Expense");
            
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);
            
            // Title input
            TextView titleLabel = new TextView(context);
            titleLabel.setText("Expense Title:");
            titleLabel.setPadding(0, 0, 0, 8);
            layout.addView(titleLabel);
            
            EditText titleInput = new EditText(context);
            titleInput.setHint("Enter expense title");
            titleInput.setText(expense.getTitle()); // Pre-fill with current title
            layout.addView(titleInput);
            
            // Amount input
            TextView amountLabel = new TextView(context);
            amountLabel.setText("Amount (RM):");
            amountLabel.setPadding(0, 20, 0, 8);
            layout.addView(amountLabel);
            
            EditText amountInput = new EditText(context);
            amountInput.setHint("Enter amount");
            amountInput.setText(String.valueOf(expense.getAmount())); // Pre-fill with current amount
            amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            layout.addView(amountInput);
            
            // Category spinner
            TextView categoryLabel = new TextView(context);
            categoryLabel.setText("Category:");
            categoryLabel.setPadding(0, 20, 0, 8);
            layout.addView(categoryLabel);
            
            Spinner categorySpinner = new Spinner(context);
            ArrayAdapter<Expense.Category> categoryAdapter = new ArrayAdapter<>(context, 
                android.R.layout.simple_spinner_item, Expense.Category.values());
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(categoryAdapter);
            
            // Set current category as selected
            for (int i = 0; i < Expense.Category.values().length; i++) {
                if (Expense.Category.values()[i] == expense.getCategory()) {
                    categorySpinner.setSelection(i);
                    break;
                }
            }
            layout.addView(categorySpinner);
            
            // Note input
            TextView noteLabel = new TextView(context);
            noteLabel.setText("Note (Optional):");
            noteLabel.setPadding(0, 20, 0, 8);
            layout.addView(noteLabel);
            
            EditText noteInput = new EditText(context);
            noteInput.setHint("Add a note (optional)");
            noteInput.setText(expense.getNote() != null ? expense.getNote() : ""); // Pre-fill with current note
            noteInput.setMaxLines(3);
            layout.addView(noteInput);
            
            // Date selection
            TextView dateLabel = new TextView(context);
            dateLabel.setText("Date:");
            dateLabel.setPadding(0, 20, 0, 8);
            layout.addView(dateLabel);
            
            TextView dateDisplay = new TextView(context);
            dateDisplay.setText(expense.getFormattedDate()); // Show current date
            dateDisplay.setPadding(16, 16, 16, 16);
            dateDisplay.setBackgroundResource(R.drawable.bg_rounded_card);
            dateDisplay.setClickable(true);
            dateDisplay.setFocusable(true);
            
            // Store the selected timestamp (initially the current expense timestamp)
            final long[] selectedTimestamp = {expense.getTimestamp()};
            
            dateDisplay.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selectedTimestamp[0]);
                
                DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedDate = Calendar.getInstance();
                        selectedDate.set(year, month, dayOfMonth);
                        selectedTimestamp[0] = selectedDate.getTimeInMillis();
                        
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
                        dateDisplay.setText(sdf.format(selectedDate.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
                
                datePickerDialog.show();
            });
            
            layout.addView(dateDisplay);
            
            builder.setView(layout);
            
            builder.setPositiveButton("Update", (dialog, which) -> {
                try {
                    String title = titleInput.getText().toString().trim();
                    String amountStr = amountInput.getText().toString().trim();
                    String note = noteInput.getText().toString().trim();
                    
                    if (validateInput(title, amountStr)) {
                        double amount = Double.parseDouble(amountStr);
                        Expense.Category category = (Expense.Category) categorySpinner.getSelectedItem();
                        
                        // Update the expense with new values
                        expense.setTitle(title);
                        expense.setAmount(amount);
                        expense.setCategory(category);
                        expense.setNote(note.isEmpty() ? null : note);
                        expense.setTimestamp(selectedTimestamp[0]);
                        
                        // Update in the adapter
                        if (expenseAdapter != null) {
                            expenseAdapter.updateExpense(expense);
                        }
                        
                        // Save the data
                        saveBudgetData();
                        
                        // For logged-in users, sync updated expense to Firebase
                        if (userManager != null && userManager.isLoggedIn() && selectedTrip != null) {
                            expense.setTripId(selectedTrip.getId()); // Ensure tripId is set
                            syncExpenseToFirebase(expense);
                        }
                        
                        // Update UI
                        updateBudgetDisplay();
                        updateChartData();
                        
                        showEditSuccessAnimationSafe();
                    }
                } catch (NumberFormatException e) {
                    showErrorToast("Please enter a valid amount");
                } catch (Exception e) {
                    Log.e(TAG, "Error updating expense", e);
                    showErrorToast("Error updating expense");
                }
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing edit expense dialog", e);
            showErrorToast("Error showing edit dialog");
        }
    }
    
    private void showExpenseDetailsDialog(Expense expense) {
        Context context = getContext();
        if (context == null || expense == null) return;
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("💰 Expense Details");
            
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 40);
            
            // Create detail rows
            addDetailRow(layout, context, "Title:", expense.getTitle());
            addDetailRow(layout, context, "Amount:", expense.getFormattedAmount());
            addDetailRow(layout, context, "Category:", expense.getCategoryEmoji() + " " + expense.getCategoryDisplayName());
            addDetailRow(layout, context, "Date:", expense.getFormattedDate());
            
            if (expense.getNote() != null && !expense.getNote().trim().isEmpty()) {
                addDetailRow(layout, context, "Note:", expense.getNote());
            }
            
            builder.setView(layout);
            
            builder.setPositiveButton("Edit", (dialog, which) -> {
                showEditExpenseDialog(expense);
            });
            
            builder.setNegativeButton("Close", null);
            
            builder.setNeutralButton("Delete", (dialog, which) -> {
                showDeleteConfirmationDialog(expense);
            });
            
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing expense details dialog", e);
            showErrorToast("Error showing expense details");
        }
    }
    
    private void addDetailRow(LinearLayout parent, Context context, String label, String value) {
        LinearLayout rowLayout = new LinearLayout(context);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 12, 0, 12);
        
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        
        rowLayout.addView(labelView);
        rowLayout.addView(valueView);
        parent.addView(rowLayout);
    }

    private void showDeleteConfirmationDialog(Expense expense) {
        Context context = getContext();
        if (context == null || expense == null) return;
        
        try {
            String message = "Are you sure you want to delete \"" + expense.getTitle() + "\"?\n\n" +
                           "This action cannot be undone.";
            
            DeleteDialogHelper.showDeleteDialog(
                context,
                "Delete Expense",
                message,
                () -> { // On confirm delete
                    try {
                        // CRITICAL FIX: Also delete from Firebase if user is logged in
                        if (userManager != null && userManager.isLoggedIn() && budgetRepository != null) {
                            // Get the trip ID for this expense
                            int tripId = expense.getTripId();
                            if (tripId <= 0 && selectedTrip != null) {
                                tripId = selectedTrip.getId();
                            }
                            
                            if (tripId > 0) {
                                budgetRepository.deleteExpenseFromFirebase(expense, tripId, new BudgetRepository.OnBudgetOperationListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Expense deleted from Firebase successfully: " + expense.getTitle());
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        Log.w(TAG, "Failed to delete expense from Firebase: " + error);
                                        // Don't show error to user - expense is still deleted locally
                                    }
                                });
                            }
                        }
                        
                        // Remove from current expenses list
                        expenses.remove(expense);
                        
                        // Also remove from the specific trip's expense list
                        if (selectedTrip != null) {
                            List<Expense> tripExpenseList = tripExpenses.get(selectedTrip.getId());
                            if (tripExpenseList != null) {
                                tripExpenseList.remove(expense);
                            }
                        } else {
                            // If "All Trips" is selected, find and remove from the correct trip's list
                            for (List<Expense> tripExpenseList : tripExpenses.values()) {
                                tripExpenseList.remove(expense);
                            }
                        }
                        
                        // Update adapter with the updated list
                        if (expenseAdapter != null) {
                            expenseAdapter.updateExpenses(expenses);
                        }
                        
                        // Save data to persistence
                        saveBudgetData();
                        
                        updateBudgetDisplay();
                        updateChartData();
                        updateEmptyState();
                        showErrorToast("Expense deleted");
                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting expense", e);
                        showErrorToast("Error deleting expense");
                    }
                }, // End of on confirm delete
                null // On cancel (no action needed)
            );
        } catch (Exception e) {
            Log.e(TAG, "Error showing delete confirmation dialog", e);
            showErrorToast("Error showing delete dialog");
        }
    }

    private void showBudgetSetupDialog() {
        Context context = getContext();
        if (context == null) return;
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            if (selectedTrip == null) {
                builder.setTitle("💰 Total Budget Overview");
                builder.setMessage("This shows the combined budget from all your trips. To edit budgets, select a specific trip.");
            } else {
                builder.setTitle("💰 Set Trip Budget");
                builder.setMessage("Set the budget for \"" + selectedTrip.getTitle() + "\" and add default categories");
            }
            
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);
            
            // Budget input
            TextView budgetLabel = new TextView(context);
            if (selectedTrip == null) {
                budgetLabel.setText("Total Budget (All Trips) - Read Only:");
            } else {
                budgetLabel.setText("Total Budget (RM):");
            }
            budgetLabel.setPadding(0, 0, 0, 8);
            layout.addView(budgetLabel);
            
            EditText budgetInput = new EditText(context);
            
            if (selectedTrip == null) {
                // "All Trips" selected - make budget read-only and show sum of all trip budgets
                double calculatedTotal = calculateTotalBudgetAllTrips();
                budgetInput.setText(String.format("%.0f (Sum of all trip budgets)", calculatedTotal));
                budgetInput.setEnabled(false); // Make it read-only
                budgetInput.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                budgetInput.setBackgroundColor(ContextCompat.getColor(context, R.color.background_light));
            } else {
                // Specific trip selected - allow editing
                budgetInput.setHint("Enter your budget (e.g., 2000)");
                budgetInput.setText(String.valueOf((int)totalBudget));
                budgetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            
            layout.addView(budgetInput);
            
            // Add default categories checkbox - ONLY for specific trips, NOT for "All Trips"
            android.widget.CheckBox addDefaultsCheckbox = null;
            
            if (selectedTrip != null) {
                // Only show default categories option when a specific trip is selected
            TextView categoriesLabel = new TextView(context);
            categoriesLabel.setText("\nDefault Categories:");
            categoriesLabel.setPadding(0, 20, 0, 8);
            layout.addView(categoriesLabel);
            
                addDefaultsCheckbox = new android.widget.CheckBox(context);
                addDefaultsCheckbox.setText("Add default categories with RM 0 (⚠️ Will replace existing records)");
                addDefaultsCheckbox.setChecked(false); // Unchecked by default for safety
                
                // Create final reference for lambda
                final android.widget.CheckBox finalCheckbox = addDefaultsCheckbox;
                
                // Show warning immediately when user checks the box
                addDefaultsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // Check if there are existing expenses
                        boolean hasExistingExpenses = expenses != null && !expenses.isEmpty() && 
                            expenses.stream().anyMatch(expense -> expense.getAmount() > 0);
                        
                        if (hasExistingExpenses) {
                            // Show immediate strong warning
                            new AlertDialog.Builder(context)
                                .setTitle("🚨 CRITICAL WARNING: Data Will Be Lost!")
                                .setMessage("⚠️ DANGER: This action will PERMANENTLY DELETE all your current budget records!\n\n" +
                                    "✗ ALL your existing expenses will be erased\n" +
                                    "✗ This action CANNOT be undone\n" +
                                    "✗ You will lose all your current budget data\n\n" +
                                    "⚡ Only check this if you want to start fresh with default categories.\n\n" +
                                    "Are you absolutely sure you want to REPLACE ALL your budget records?")
                                .setPositiveButton("⚠️ Yes, DELETE ALL my records", null)
                                .setNegativeButton("❌ No, Keep my data", (dialog, which) -> {
                                    finalCheckbox.setChecked(false);
                                })
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setCancelable(false)
                                .show();
                        } else {
                            // No existing data, show informational message
                            new AlertDialog.Builder(context)
                                .setTitle("ℹ️ Add Default Categories")
                                .setMessage("This will add 6 default budget categories:\n\n" +
                                    "• Food & Dining (RM 0)\n" +
                                    "• Transportation (RM 0)\n" +
                                    "• Accommodation (RM 0)\n" +
                                    "• Activities & Tours (RM 0)\n" +
                                    "• Shopping (RM 0)\n" +
                                    "• Miscellaneous (RM 0)\n\n" +
                                    "You can edit these amounts later.")
                                .setPositiveButton("✅ Add Categories", null)
                                .setNegativeButton("Cancel", (dialog, which) -> {
                                    finalCheckbox.setChecked(false);
                                })
                                .show();
                        }
                    }
                });
                
            layout.addView(addDefaultsCheckbox);
            } else {
                // When "All Trips" is selected, show info that default categories are not available
                TextView infoLabel = new TextView(context);
                infoLabel.setText("\nℹ️ Default categories can only be added to specific trips.\nSelect a specific trip to add default categories.");
                infoLabel.setPadding(0, 20, 0, 8);
                infoLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                infoLabel.setTextSize(14);
                layout.addView(infoLabel);
            }
            
            builder.setView(layout);
            
            // Make the checkbox final so it can be accessed in the lambda
            final android.widget.CheckBox finalAddDefaultsCheckbox = addDefaultsCheckbox;
            
            String buttonText = selectedTrip == null ? "OK" : "Set Budget";
            builder.setPositiveButton(buttonText, (dialog, which) -> {
                try {
                    if (selectedTrip == null) {
                        // "All Trips" selected - budget is read-only, just show info message
                        showErrorToast("Total budget is automatically calculated from all trip budgets");
                        return;
                    }
                    
                    // Only allow budget editing for specific trips
                    String budgetStr = budgetInput.getText().toString().trim();
                    if (!budgetStr.isEmpty()) {
                        totalBudget = Double.parseDouble(budgetStr);
                        
                        // Save the budget to that trip's budget map
                        tripBudgets.put(selectedTrip.getId(), totalBudget);
                        
                        // Always save the budget first
                        saveBudgetData();
                        
                        // For logged-in users, sync budget to Firebase
                        if (userManager != null && userManager.isLoggedIn()) {
                            syncBudgetToFirebase(selectedTrip.getId(), totalBudget);
                        }
                        
                        // Add default categories if checkbox exists and is checked - with warning
                        if (finalAddDefaultsCheckbox != null && finalAddDefaultsCheckbox.isChecked()) {
                            // This is a specific trip and user wants default categories
                            showDefaultCategoriesWarning(() -> {
                                addDefaultCategories();
                                // Save budget data after adding categories
                                saveBudgetData();
                        updateBudgetDisplay();
                        updateChartData();
                        updateEmptyState();
                                showErrorToast("Budget set to RM" + String.format("%.0f", totalBudget) + " with default categories");
                            });
                        } else {
                            // Either "All Trips" is selected OR user didn't check default categories
                            updateBudgetDisplay();
                            updateChartData();
                            updateEmptyState();
                            
                            // Specific trip budget updated without default categories
                            showErrorToast("Budget set to RM" + String.format("%.0f", totalBudget) + " for " + selectedTrip.getTitle());
                        }
                    }
                } catch (NumberFormatException e) {
                    showErrorToast("Please enter a valid budget amount");
                } catch (Exception e) {
                    Log.e(TAG, "Error setting budget", e);
                    showErrorToast("Error setting budget");
                }
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing budget setup dialog", e);
            showErrorToast("Error opening budget setup");
        }
    }
    
    private void addDefaultCategories() {
        try {
            expenses.clear(); // Clear any existing expenses
            
            // Add default categories with RM 0
            expenses.add(new Expense("Food & Dining", 0.0, Expense.Category.FOOD));
            expenses.add(new Expense("Transportation", 0.0, Expense.Category.TRANSPORT));
            expenses.add(new Expense("Accommodation", 0.0, Expense.Category.HOTEL));
            expenses.add(new Expense("Activities & Tours", 0.0, Expense.Category.ACTIVITIES));
            expenses.add(new Expense("Shopping", 0.0, Expense.Category.SHOPPING));
            expenses.add(new Expense("Miscellaneous", 0.0, Expense.Category.OTHER));
            
            // Update adapter
            if (expenseAdapter != null) {
                expenseAdapter.updateExpenses(expenses);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding default categories", e);
        }
    }

    /**
     * Show warning dialog before adding default categories
     */
    private void showDefaultCategoriesWarning(Runnable onConfirm) {
        Context context = getContext();
        if (context == null) {
            // If context is null, just proceed without warning
            if (onConfirm != null) {
                onConfirm.run();
            }
            return;
        }
        
        try {
            // Check if there are existing expenses
            boolean hasExistingExpenses = expenses != null && !expenses.isEmpty() && 
                expenses.stream().anyMatch(expense -> expense.getAmount() > 0);
            
            if (!hasExistingExpenses) {
                // No existing expenses, proceed without warning
                if (onConfirm != null) {
                    onConfirm.run();
                }
                return;
            }
            
            new AlertDialog.Builder(context)
                .setTitle("🚨 FINAL WARNING: Data Deletion Confirmed!")
                .setMessage("🔥 THIS IS YOUR LAST CHANCE TO SAVE YOUR DATA!\n\n" +
                    "⚠️ CRITICAL ACTION: You are about to PERMANENTLY DELETE:\n\n" +
                    "🗑️ ALL your current budget records\n" +
                    "🗑️ ALL your expense entries\n" +
                    "🗑️ ALL your spending history\n\n" +
                    "❌ This action is IRREVERSIBLE\n" +
                    "❌ Your data will be GONE FOREVER\n\n" +
                    "💡 If you're not 100% sure, click 'Cancel' to keep your existing data.\n\n" +
                    "Are you completely certain you want to DELETE ALL your budget records?")
                .setPositiveButton("🔥 YES, DELETE EVERYTHING", (dialog, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton("🛡️ CANCEL - Keep my data", (dialog, which) -> {
                    // User cancelled, just update budget without adding categories
                    saveBudgetData();
                    updateBudgetDisplay();
                    updateChartData();
                    updateEmptyState();
                    showErrorToast("✅ Budget updated safely - your expense records are preserved");
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .show();
                
        } catch (Exception e) {
            Log.e(TAG, "Error showing default categories warning", e);
            // On error, proceed without warning
            if (onConfirm != null) {
                onConfirm.run();
            }
        }
    }

    private void loadUserTrips() {
        try {
            if (tripRepository == null) {
                Log.w(TAG, "Trip repository not initialized");
                return;
            }
            
            // Load trips asynchronously
            new Thread(() -> {
                try {
                    List<Trip> userTrips = tripRepository.getAllTripsSync();
                    
                    // Update UI on main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            try {
                                availableTrips.clear();
                                availableTrips.addAll(userTrips);
                                
                                // Initialize budgets and expenses for each trip
                                for (Trip trip : userTrips) {
                                    // Set default budget if not already set
                                    if (!tripBudgets.containsKey(trip.getId())) {
                                        tripBudgets.put(trip.getId(), 2000.0); // Default RM 2000
                                    }
                                    
                                    // Initialize empty expense list if not already set
                                    if (!tripExpenses.containsKey(trip.getId())) {
                                        tripExpenses.put(trip.getId(), new ArrayList<>());
                                    }
                                }
                                
                                // CRITICAL FIX: Clean up budget data for deleted trips
                                cleanupDeletedTripsData(userTrips);
                                
                                // Restore selected trip from saved data
                                restoreSelectedTrip();
                                
                                // Save updated budget data
                                saveBudgetData();
                                
                                // Update UI
                                updateTripSelector();
                                updateBudgetDisplay();
                                updateChartData();
                                updateFabVisibility(); // Ensure FAB is shown correctly after loading trips
                                
                                // Ensure adapter is updated with current expenses and filter is reset
                                if (expenseAdapter != null) {
                                    expenseAdapter.updateExpenses(expenses);
                                    expenseAdapter.clearFilter();
                                    
                                    // Initialize default filter after UI is ready
                                    if (chipGroupCategories != null) {
                                        chipGroupCategories.postDelayed(() -> {
                                            initializeDefaultFilter();
                                            updateEmptyState();
                                        }, 100);
                                    }
                                }
                                
                                Log.d(TAG, "Loaded " + userTrips.size() + " trips from database");
                                
                                // If no trips available, show message
                                if (userTrips.isEmpty()) {
                                    selectedTripText.setText("📝 Create your first trip!");
                                    showErrorToast("Create a trip first to start budgeting");
                                }
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating UI with trips", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading trips from database", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            showErrorToast("Error loading trips");
                        });
                    }
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting trip loading", e);
        }
    }
    
    /**
     * CRITICAL METHOD: Clean up budget data for trips that no longer exist in the database
     * This fixes the issue where deleted trips still appear in budget data
     */
    private void cleanupDeletedTripsData(List<Trip> currentTrips) {
        try {
            Log.d(TAG, "=== CLEANING UP DELETED TRIPS DATA ===");
            
            // Get list of current trip IDs from database
            Set<Integer> currentTripIds = new HashSet<>();
            for (Trip trip : currentTrips) {
                currentTripIds.add(trip.getId());
            }
            Log.d(TAG, "Current trips in database: " + currentTripIds);
            
            // Find orphaned budget data (trip IDs in SharedPreferences but not in database)
            Set<Integer> budgetTripIds = new HashSet<>(tripBudgets.keySet());
            Set<Integer> expenseTripIds = new HashSet<>(tripExpenses.keySet());
            
            Log.d(TAG, "Budget data exists for trips: " + budgetTripIds);
            Log.d(TAG, "Expense data exists for trips: " + expenseTripIds);
            
            boolean dataChanged = false;
            
            // Remove budget data for deleted trips
            Iterator<Integer> budgetIterator = tripBudgets.keySet().iterator();
            while (budgetIterator.hasNext()) {
                Integer tripId = budgetIterator.next();
                if (!currentTripIds.contains(tripId)) {
                    Log.d(TAG, "Removing orphaned budget data for deleted trip ID: " + tripId);
                    budgetIterator.remove();
                    dataChanged = true;
                }
            }
            
            // Remove expense data for deleted trips
            Iterator<Integer> expenseIterator = tripExpenses.keySet().iterator();
            while (expenseIterator.hasNext()) {
                Integer tripId = expenseIterator.next();
                if (!currentTripIds.contains(tripId)) {
                    List<Expense> orphanedExpenses = tripExpenses.get(tripId);
                    Log.d(TAG, "Removing orphaned expense data for deleted trip ID: " + tripId + 
                        " (had " + (orphanedExpenses != null ? orphanedExpenses.size() : 0) + " expenses)");
                    expenseIterator.remove();
                    dataChanged = true;
                }
            }
            
            // Check if currently selected trip was deleted
            if (selectedTrip != null && !currentTripIds.contains(selectedTrip.getId())) {
                Log.d(TAG, "Currently selected trip was deleted, switching to All Trips");
                selectedTrip = null;
                dataChanged = true;
            }
            
            if (dataChanged) {
                Log.d(TAG, "Budget data was cleaned up, saving changes and refreshing UI");
                
                // Save the cleaned data
                saveBudgetData();
                
                // Refresh the current view
                if (selectedTrip == null) {
                    // Viewing "All Trips", refresh aggregated data
                    totalBudget = calculateTotalBudgetAllTrips();
                    expenses = getAllExpenses();
                } else {
                    // Viewing specific trip, get that trip's data
                    totalBudget = tripBudgets.getOrDefault(selectedTrip.getId(), 2000.0);
                    expenses = tripExpenses.getOrDefault(selectedTrip.getId(), new ArrayList<>());
                }
                
                Log.d(TAG, "After cleanup - Budget: RM" + totalBudget + ", Expenses: " + expenses.size());
            } else {
                Log.d(TAG, "No orphaned data found, no cleanup needed");
            }
            
            Log.d(TAG, "=== CLEANUP COMPLETE ===");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during deleted trips cleanup", e);
        }
    }
    
    private void showTripSelectorDialog() {
        Context context = getContext();
        if (context == null) return;
        
        try {
            // Check if user has any trips
            if (availableTrips.isEmpty()) {
                showErrorToast("Create a trip first to start budgeting!");
                return;
            }
            
            // Create list of trip options
            List<String> tripOptions = new ArrayList<>();
            tripOptions.add("🌟 All Trips Budget");
            
            for (Trip trip : availableTrips) {
                String budgetInfo = "";
                if (tripBudgets.containsKey(trip.getId())) {
                    budgetInfo = String.format(" (RM%.0f)", tripBudgets.get(trip.getId()));
                }
                tripOptions.add(trip.getTitle() + budgetInfo);
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("🧳 Select Trip Budget");
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, 
                android.R.layout.simple_list_item_1, tripOptions);
            
            builder.setAdapter(adapter, (dialog, which) -> {
                try {
                    // Animate arrow rotation
                    animateDropdownArrow();
                    
                    if (which == 0) {
                        // "All Trips" selected
                        selectedTrip = null;
                        selectedTripText.setText("🌟 All Trips Budget");
                        totalBudget = calculateTotalBudgetAllTrips();
                        expenses = getAllExpenses();
                    } else {
                        // Specific trip selected
                        Trip trip = availableTrips.get(which - 1);
                        selectedTrip = trip;
                        selectedTripText.setText(trip.getTitle());
                        totalBudget = tripBudgets.getOrDefault(trip.getId(), 2000.0);
                        
                        // Get or create expense list for this trip
                        List<Expense> tripExpenseList = tripExpenses.get(trip.getId());
                        if (tripExpenseList == null) {
                            tripExpenseList = new ArrayList<>();
                            tripExpenses.put(trip.getId(), tripExpenseList);
                        }
                        expenses = tripExpenseList;
                    }
                    
                    // Save selection to persistence
                    saveBudgetData();
                    
                    // Update UI
                    if (expenseAdapter != null) {
                        expenseAdapter.updateExpenses(expenses);
                        // Reset filter to "All" when switching trips
                        expenseAdapter.clearFilter();
                        initializeDefaultFilter();
                    }
                    updateBudgetDisplay();
                    updateChartData();
                    updateEmptyState();
                    updateEditBudgetIconVisibility();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error selecting trip", e);
                    showErrorToast("Error selecting trip");
                }
            });
            
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing trip selector", e);
            showErrorToast("Error opening trip selector");
        }
    }
    
    private void animateDropdownArrow() {
        if (dropdownArrow != null) {
            dropdownArrow.animate()
                .rotation(dropdownArrow.getRotation() + 180f)
                .setDuration(300)
                .start();
        }
    }
    
    private double calculateTotalBudgetAllTrips() {
        double total = 0.0;
        for (Double budget : tripBudgets.values()) {
            total += budget;
        }
        return total;
    }
    
    private List<Expense> getAllExpenses() {
        List<Expense> allExpenses = new ArrayList<>();
        for (List<Expense> tripExpenseList : tripExpenses.values()) {
            allExpenses.addAll(tripExpenseList);
        }
        return allExpenses;
    }
    
    private void updateTripSelector() {
        try {
            if (selectedTripText != null) {
                if (availableTrips.isEmpty()) {
                    selectedTripText.setText("📝 Create your first trip!");
                } else if (selectedTrip == null) {
                    selectedTripText.setText("🌟 All Trips Budget");
                } else {
                    selectedTripText.setText(selectedTrip.getTitle());
                }
            }
            
            // Update FAB visibility - hide when "All Trips" is selected
            updateFabVisibility();
            // Update edit budget icon visibility
            updateEditBudgetIconVisibility();
        } catch (Exception e) {
            Log.e(TAG, "Error updating trip selector", e);
        }
    }
    
    private void updateFabVisibility() {
        try {
            if (fabAddExpense != null) {
                // ALWAYS show the FAB - user requested it to always appear
                    fabAddExpense.setVisibility(View.VISIBLE);
                Log.d(TAG, "FAB set to ALWAYS VISIBLE as requested by user");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating FAB visibility", e);
        }
    }
    
    private void updateEditBudgetIconVisibility() {
        try {
            if (editBudgetIcon != null) {
                if (selectedTrip == null) {
                    // "All Trips" selected - hide edit icon (budget is read-only)
                    editBudgetIcon.setVisibility(View.GONE);
                } else {
                    // Specific trip selected - show edit icon (budget is editable)
                    editBudgetIcon.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "Edit budget icon visibility: " + (selectedTrip == null ? "HIDDEN" : "VISIBLE"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating edit budget icon visibility", e);
        }
    }
    
    // Call this method when a trip is deleted to clean up budget data
    public void onTripDeleted(int tripId) {
        try {
            Log.d(TAG, "=== TRIP DELETION CLEANUP START ===");
            Log.d(TAG, "Deleting trip ID: " + tripId);
            Log.d(TAG, "Before deletion - Available trips: " + availableTrips.size());
            Log.d(TAG, "Before deletion - Trip budgets: " + tripBudgets.size());
            Log.d(TAG, "Before deletion - Trip expenses: " + tripExpenses.size());
            
            // Remove budget and expenses for deleted trip
            Double removedBudget = tripBudgets.remove(tripId);
            List<Expense> removedExpenses = tripExpenses.remove(tripId);
            
            Log.d(TAG, "Removed budget: " + removedBudget);
            Log.d(TAG, "Removed expenses count: " + (removedExpenses != null ? removedExpenses.size() : 0));
            
            // Remove from available trips list
            boolean tripRemoved = availableTrips.removeIf(trip -> trip.getId() == tripId);
            Log.d(TAG, "Trip removed from available list: " + tripRemoved);
            
            // If the deleted trip was selected, switch to "All Trips"
            if (selectedTrip != null && selectedTrip.getId() == tripId) {
                Log.d(TAG, "Deleted trip was currently selected, switching to All Trips");
                selectedTrip = null;
                totalBudget = calculateTotalBudgetAllTrips();
                expenses = getAllExpenses();
                
                Log.d(TAG, "New total budget: " + totalBudget);
                Log.d(TAG, "New expenses count: " + expenses.size());
                
                if (expenseAdapter != null) {
                    expenseAdapter.updateExpenses(expenses);
                    // Reset filter to "All" when trip is deleted
                    expenseAdapter.clearFilter();
                    initializeDefaultFilter();
                }
            } else {
                Log.d(TAG, "Deleted trip was not currently selected");
                // Still need to refresh "All Trips" data if that's what's currently shown
                if (selectedTrip == null) {
                    Log.d(TAG, "Currently showing All Trips, refreshing data");
                    expenses = getAllExpenses();
                    totalBudget = calculateTotalBudgetAllTrips();
                    
                    if (expenseAdapter != null) {
                        expenseAdapter.updateExpenses(expenses);
                        expenseAdapter.clearFilter();
                        initializeDefaultFilter();
                    }
                }
            }
            
            // Save changes to persistence
            saveBudgetData();
            
            // Update UI
            updateTripSelector();
            updateBudgetDisplay();
            updateChartData();
            updateEmptyState();
            updateFabVisibility(); // Ensure FAB stays visible
            
            Log.d(TAG, "After deletion - Available trips: " + availableTrips.size());
            Log.d(TAG, "After deletion - Trip budgets: " + tripBudgets.size());
            Log.d(TAG, "After deletion - Trip expenses: " + tripExpenses.size());
            Log.d(TAG, "=== TRIP DELETION CLEANUP COMPLETE ===");
            
            showErrorToast("Trip budget data removed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up deleted trip data", e);
            showErrorToast("Error cleaning up trip data: " + e.getMessage());
        }
    }
    
    // Call this method to refresh trips (e.g., when returning from trip creation)
    public void refreshTrips() {
        loadUserTrips(); // This will automatically call cleanupDeletedTripsData()
    }
    
    private void loadBudgetData() {
        if (userManager != null && userManager.isLoggedIn()) {
            // For logged-in users, load from Firebase (when implemented)
            // For now, load from local storage and let sync handle it
            loadLocalBudgetData();
            Log.d(TAG, "Loaded budget data for logged-in user from local storage");
        } else {
            // For guest users, load from local storage only
            loadLocalBudgetData();
            Log.d(TAG, "Loaded budget data for guest user from local storage");
        }
    }
    
    private void loadLocalBudgetData() {
        try {
            if (budgetRepository == null) {
                Log.w(TAG, "BudgetRepository is null, using legacy method");
                loadSavedBudgetDataLegacy();
                return;
            }
            
            BudgetRepository.BudgetData data = budgetRepository.loadBudgetDataLocally();
            
            tripBudgets.putAll(data.tripBudgets);
            tripExpenses.putAll(data.tripExpenses);
            totalBudget = data.totalBudget;
            
            Log.d(TAG, "Loaded budget data using BudgetRepository: " + tripBudgets.size() + " trip budgets, " + 
                  tripExpenses.size() + " trip expenses");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading budget data", e);
            loadSavedBudgetDataLegacy(); // Fallback to legacy method
        }
    }
    
    private void loadSavedBudgetDataLegacy() {
        try {
            if (sharedPreferences == null || gson == null) return;
            
            // Load trip budgets
            String budgetsJson = sharedPreferences.getString("trip_budgets", "{}");
            Type budgetType = new TypeToken<Map<Integer, Double>>(){}.getType();
            Map<Integer, Double> savedBudgets = gson.fromJson(budgetsJson, budgetType);
            if (savedBudgets != null) {
                tripBudgets.putAll(savedBudgets);
            }
            
            // Load trip expenses
            String expensesJson = sharedPreferences.getString("trip_expenses", "{}");
            Type expenseType = new TypeToken<Map<Integer, List<Expense>>>(){}.getType();
            Map<Integer, List<Expense>> savedExpenses = gson.fromJson(expensesJson, expenseType);
            if (savedExpenses != null) {
                tripExpenses.putAll(savedExpenses);
            }
            
            // Load selected trip ID
            int selectedTripId = sharedPreferences.getInt("selected_trip_id", -1);
            if (selectedTripId != -1) {
                // Will be set properly when trips are loaded
                Log.d(TAG, "Saved selected trip ID: " + selectedTripId);
            }
            
            // Load total budget
            totalBudget = Double.longBitsToDouble(sharedPreferences.getLong("total_budget", 
                Double.doubleToLongBits(2000.0)));
            
            Log.d(TAG, "Loaded budget data (legacy): " + tripBudgets.size() + " trip budgets, " + 
                  tripExpenses.size() + " trip expenses");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved budget data (legacy)", e);
        }
    }
    
    private void saveBudgetData() {
        try {
            if (budgetRepository == null) {
                Log.w(TAG, "BudgetRepository is null, using legacy save method");
                saveBudgetDataLegacy();
                return;
            }
            
            int selectedTripId = selectedTrip != null ? selectedTrip.getId() : -1;
            budgetRepository.saveBudgetDataLocally(tripBudgets, tripExpenses, selectedTripId, totalBudget);
            
            Log.d(TAG, "Saved budget data using BudgetRepository");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving budget data", e);
            saveBudgetDataLegacy(); // Fallback to legacy method
        }
    }
    
    private void saveBudgetDataLegacy() {
        try {
            if (sharedPreferences == null || gson == null) return;
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Save trip budgets
            String budgetsJson = gson.toJson(tripBudgets);
            editor.putString("trip_budgets", budgetsJson);
            
            // Save trip expenses
            String expensesJson = gson.toJson(tripExpenses);
            editor.putString("trip_expenses", expensesJson);
            
            // Save selected trip ID
            if (selectedTrip != null) {
                editor.putInt("selected_trip_id", selectedTrip.getId());
            } else {
                editor.putInt("selected_trip_id", -1);
            }
            
            // Save total budget
            editor.putLong("total_budget", Double.doubleToLongBits(totalBudget));
            
            editor.apply();
            
            Log.d(TAG, "Saved budget data successfully (legacy)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving budget data (legacy)", e);
        }
    }
    
    private void restoreSelectedTrip() {
        try {
            if (sharedPreferences == null) return;
            
            int savedTripId = sharedPreferences.getInt("selected_trip_id", -1);
            
            if (savedTripId == -1) {
                // "All Trips" was selected
                selectedTrip = null;
                totalBudget = calculateTotalBudgetAllTrips();
                expenses = getAllExpenses();
            } else {
                // Find and select the specific trip
                for (Trip trip : availableTrips) {
                    if (trip.getId() == savedTripId) {
                        selectedTrip = trip;
                        totalBudget = tripBudgets.getOrDefault(trip.getId(), 2000.0);
                        
                        // Get or create expense list for this trip
                        List<Expense> tripExpenseList = tripExpenses.get(trip.getId());
                        if (tripExpenseList == null) {
                            tripExpenseList = new ArrayList<>();
                            tripExpenses.put(trip.getId(), tripExpenseList);
                        }
                        expenses = tripExpenseList;
                        break;
                    }
                }
                
                // If trip not found, default to "All Trips"
                if (selectedTrip == null) {
                    totalBudget = calculateTotalBudgetAllTrips();
                    expenses = getAllExpenses();
                }
            }
            
            // Update adapter
            if (expenseAdapter != null) {
                expenseAdapter.updateExpenses(expenses);
                // Ensure filter is reset to "All" when restoring trip selection
                expenseAdapter.clearFilter();
                // Initialize default filter after a short delay to ensure UI is ready
                if (chipGroupCategories != null) {
                    chipGroupCategories.postDelayed(() -> {
                        initializeDefaultFilter();
                    }, 50);
                }
            }
            
            // Update FAB visibility after restoring trip selection
            updateFabVisibility();
            // Update edit budget icon visibility
            updateEditBudgetIconVisibility();
            
            Log.d(TAG, "Restored selected trip: " + (selectedTrip != null ? selectedTrip.getTitle() : "All Trips"));
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring selected trip", e);
        }
    }

    private void syncExpenseToFirebase(Expense expense) {
        if (budgetRepository == null || userManager == null || !userManager.isLoggedIn()) {
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Log.w(TAG, "User email not available for Firebase sync");
            return;
        }
        
        Log.d(TAG, "Syncing expense to Firebase: " + expense.getTitle());
        
        // Use BudgetRepository to sync the expense
        budgetRepository.syncExpenseToFirebase(expense, userEmail, new BudgetRepository.OnBudgetOperationListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Expense synced to Firebase successfully: " + expense.getTitle());
                expense.setSynced(true);
                saveBudgetData(); // Save the updated sync status locally
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to sync expense to Firebase: " + error);
                // Don't show error to user - expense is saved locally
            }
        });
    }
    
    private void syncBudgetToFirebase(int tripId, double budget) {
        if (budgetRepository == null || userManager == null || !userManager.isLoggedIn()) {
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Log.w(TAG, "User email not available for Firebase sync");
            return;
        }
        
        Log.d(TAG, "Syncing budget to Firebase for trip " + tripId + ": RM" + budget);
        
        // Use BudgetRepository to sync the budget
        budgetRepository.syncTripBudgetToFirebase(tripId, budget, userEmail, new BudgetRepository.OnBudgetOperationListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Budget synced to Firebase successfully for trip " + tripId);
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to sync budget to Firebase: " + error);
                // Don't show error to user - budget is saved locally
            }
        });
    }
    
    private void refreshBudgetDataFromFirebase() {
        if (budgetRepository == null) {
            return;
        }
        
        Log.d(TAG, "Refreshing budget data from Firebase");
        
        budgetRepository.fetchBudgetDataFromFirebase(new BudgetRepository.OnBudgetFetchListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully refreshed budget data from Firebase");
                
                // Reload the budget data and update UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            // Reload budget data
                            loadBudgetData();
                            
                            // Restore selected trip
                            restoreSelectedTrip();
                            
                            // Update UI
                            updateBudgetDisplay();
                            updateChartData();
                            updateEmptyState();
                            
                            // Update adapter
                            if (expenseAdapter != null) {
                                expenseAdapter.updateExpenses(expenses);
                                expenseAdapter.clearFilter();
                                initializeDefaultFilter();
                            }
                            
                            Log.d(TAG, "UI updated after Firebase budget data refresh");
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating UI after budget data refresh", e);
                        }
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to refresh budget data from Firebase: " + error);
                // Continue with local data - don't disrupt user experience
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            // Release MediaPlayer resources from DeleteDialogHelper
            DeleteDialogHelper.releaseMediaPlayer();
            
            if (successSound != null) {
                successSound.release();
                successSound = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
} 