package com.example.mobiledegreefinalproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class BudgetFragment extends Fragment {

    private TextView totalBudgetText;
    private TextView totalSpentText;
    private TextView remainingText;
    private RecyclerView expensesRecyclerView;
    private FloatingActionButton fabAddExpense;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
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

    private void setupRecyclerView() {
        // TODO: Setup expenses adapter
        // expensesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // expensesRecyclerView.setAdapter(expensesAdapter);
    }

    private void setupClickListeners() {
        fabAddExpense.setOnClickListener(v -> {
            // TODO: Navigate to add expense activity
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), 
                    "Add Expense functionality coming soon!", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBudgetDisplay() {
        // TODO: Calculate and display actual budget values
        totalBudgetText.setText("$0.00");
        totalSpentText.setText("$0.00");
        remainingText.setText("$0.00");
    }
} 