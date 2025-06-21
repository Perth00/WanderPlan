package com.example.mobiledegreefinalproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class BudgetFragment extends Fragment {

    private TextView totalBudgetText;
    private TextView totalSpentText;
    private TextView remainingText;
    private RecyclerView expensesRecyclerView;
    private FloatingActionButton fabAddExpense;
    
    // Simple expense data structure
    private List<Expense> expenses;
    private ExpenseAdapter expenseAdapter;
    
    // Simple Expense class
    private static class Expense {
        String title;
        double amount;
        long timestamp;
        
        Expense(String title, double amount) {
            this.title = title;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        initializeExpenses();
        setupRecyclerView();
        setupClickListeners();
        updateBudgetDisplay();
    }

    private void initViews(View view) {
        totalBudgetText = view.findViewById(R.id.tv_total_budget);
        totalSpentText = view.findViewById(R.id.tv_total_spent);
        remainingText = view.findViewById(R.id.tv_remaining);
        expensesRecyclerView = view.findViewById(R.id.rv_expenses);
        fabAddExpense = view.findViewById(R.id.fab_add_expense);
    }

    private void initializeExpenses() {
        expenses = new ArrayList<>();
        // Add some sample expenses
        expenses.add(new Expense("Hotel Booking", 250.00));
        expenses.add(new Expense("Flight Tickets", 480.00));
        expenses.add(new Expense("Car Rental", 120.00));
    }
    
    private void setupRecyclerView() {
        expenseAdapter = new ExpenseAdapter(expenses);
        expensesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        expensesRecyclerView.setAdapter(expenseAdapter);
    }

    private void setupClickListeners() {
        fabAddExpense.setOnClickListener(v -> {
            if (getContext() != null) {
                showAddExpenseDialog();
            }
        });
    }

    private void updateBudgetDisplay() {
        double totalSpent = 0.0;
        for (Expense expense : expenses) {
            totalSpent += expense.amount;
        }
        
        double totalBudget = 2000.00; // Example budget
        double remaining = totalBudget - totalSpent;
        
        totalBudgetText.setText(String.format("$%.2f", totalBudget));
        totalSpentText.setText(String.format("$%.2f", totalSpent));
        remainingText.setText(String.format("$%.2f", remaining));
    }
    
    private void showAddExpenseDialog() {
        if (getContext() == null) return;
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Add Expense");
        
        // Create a simple layout for the dialog
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final android.widget.EditText titleInput = new android.widget.EditText(getContext());
        titleInput.setHint("Expense title (e.g., Hotel, Food, Transport)");
        layout.addView(titleInput);
        
        final android.widget.EditText amountInput = new android.widget.EditText(getContext());
        amountInput.setHint("Amount (e.g., 150.00)");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Add", (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            String amountStr = amountInput.getText().toString().trim();
            
            if (title.isEmpty()) {
                android.widget.Toast.makeText(getContext(), "Please enter expense title", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (amountStr.isEmpty()) {
                android.widget.Toast.makeText(getContext(), "Please enter amount", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    android.widget.Toast.makeText(getContext(), "Amount must be greater than 0", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Add expense to list and update UI
                expenses.add(new Expense(title, amount));
                expenseAdapter.notifyDataSetChanged();
                updateBudgetDisplay();
                
                android.widget.Toast.makeText(getContext(), 
                    "Expense '" + title + "' ($" + String.format("%.2f", amount) + ") added!", 
                    android.widget.Toast.LENGTH_SHORT).show();
                    
            } catch (NumberFormatException e) {
                android.widget.Toast.makeText(getContext(), "Please enter a valid amount", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    // Simple RecyclerView Adapter for expenses
    private static class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {
        private List<Expense> expenses;
        
        ExpenseAdapter(List<Expense> expenses) {
            this.expenses = expenses;
        }
        
        @NonNull
        @Override
        public ExpenseViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ExpenseViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
            Expense expense = expenses.get(position);
            holder.titleText.setText(expense.title);
            holder.amountText.setText(String.format("$%.2f", expense.amount));
        }
        
        @Override
        public int getItemCount() {
            return expenses.size();
        }
        
        static class ExpenseViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView amountText;
            
            ExpenseViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                titleText = itemView.findViewById(android.R.id.text1);
                amountText = itemView.findViewById(android.R.id.text2);
            }
        }
    }
} 