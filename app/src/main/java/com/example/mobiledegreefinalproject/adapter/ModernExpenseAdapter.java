package com.example.mobiledegreefinalproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.DeleteDialogHelper;
import com.example.mobiledegreefinalproject.R;
import com.example.mobiledegreefinalproject.model.Expense;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class ModernExpenseAdapter extends RecyclerView.Adapter<ModernExpenseAdapter.ExpenseViewHolder> {
    
    public interface OnExpenseActionListener {
        void onEditExpense(Expense expense);
        void onDeleteExpense(Expense expense);
        void onExpenseClick(Expense expense);
    }

    private List<Expense> expenses;
    private List<Expense> filteredExpenses;
    private OnExpenseActionListener listener;
    private Expense.Category filterCategory = null;

    public ModernExpenseAdapter(List<Expense> expenses) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
        this.filteredExpenses = new ArrayList<>(this.expenses);
    }

    public void setOnExpenseActionListener(OnExpenseActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense_modern, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = filteredExpenses.get(position);
        holder.bind(expense);
    }

    @Override
    public int getItemCount() {
        return filteredExpenses.size();
    }

    public void updateExpenses(List<Expense> newExpenses) {
        this.expenses.clear();
        if (newExpenses != null) {
            this.expenses.addAll(newExpenses);
        }
        filterExpenses();
    }

    public void filterByCategory(Expense.Category category) {
        this.filterCategory = category;
        filterExpenses();
    }

    public void clearFilter() {
        this.filterCategory = null;
        filterExpenses();
    }

    private void filterExpenses() {
        filteredExpenses.clear();
        if (filterCategory == null) {
            filteredExpenses.addAll(expenses);
        } else {
            for (Expense expense : expenses) {
                if (expense.getCategory() == filterCategory) {
                    filteredExpenses.add(expense);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
        filterExpenses();
    }

    public void removeExpense(String expenseId) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId().equals(expenseId)) {
                expenses.remove(i);
                break;
            }
        }
        filterExpenses();
    }

    public void updateExpense(Expense updatedExpense) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId().equals(updatedExpense.getId())) {
                expenses.set(i, updatedExpense);
                break;
            }
        }
        filterExpenses();
    }

    class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private ImageView categoryIcon;
        private TextView expenseTitle;
        private TextView expenseAmount;
        private Chip categoryChip;
        private TextView expenseDate;
        private ImageButton editButton;
        private ImageButton deleteButton;

        public ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryIcon = itemView.findViewById(R.id.iv_category_icon);
            expenseTitle = itemView.findViewById(R.id.tv_expense_title);
            expenseAmount = itemView.findViewById(R.id.tv_expense_amount);
            categoryChip = itemView.findViewById(R.id.chip_category);
            expenseDate = itemView.findViewById(R.id.tv_expense_date);
            editButton = itemView.findViewById(R.id.btn_edit_expense);
            deleteButton = itemView.findViewById(R.id.btn_delete_expense);
        }

        public void bind(Expense expense) {
            expenseTitle.setText(expense.getTitle());
            expenseAmount.setText(expense.getFormattedAmount());
            expenseDate.setText(expense.getFormattedDate());
            categoryChip.setText(expense.getCategoryDisplayName());

            // Set category icon and colors
            setCategoryAppearance(expense.getCategory());

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpenseClick(expense);
                }
            });

            editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditExpense(expense);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteExpense(expense);
                }
            });
        }

        private void setCategoryAppearance(Expense.Category category) {
            int iconRes, colorRes, bgColorRes;
            
            switch (category) {
                case FOOD:
                    iconRes = R.drawable.ic_food;
                    colorRes = R.color.primary;
                    bgColorRes = R.color.primary_light;
                    break;
                case TRANSPORT:
                    iconRes = R.drawable.ic_transport;
                    colorRes = R.color.secondary;
                    bgColorRes = R.color.secondary_variant;
                    break;
                case HOTEL:
                    iconRes = R.drawable.ic_accommodation;
                    colorRes = R.color.navy;
                    bgColorRes = R.color.info;
                    break;
                case ACTIVITIES:
                    iconRes = R.drawable.ic_budget;
                    colorRes = R.color.dark_grey;
                    bgColorRes = R.color.warning;
                    break;
                case SHOPPING:
                    iconRes = R.drawable.ic_shopping;
                    colorRes = R.color.navy;
                    bgColorRes = R.color.lavender;
                    break;
                default: // OTHER
                    iconRes = R.drawable.ic_settings;
                    colorRes = R.color.white;
                    bgColorRes = R.color.medium_grey;
                    break;
            }

            categoryIcon.setImageResource(iconRes);
            categoryIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), colorRes));
            
            // Set background color for the icon container
            View iconContainer = (View) categoryIcon.getParent();
            if (iconContainer instanceof androidx.cardview.widget.CardView) {
                ((androidx.cardview.widget.CardView) iconContainer).setCardBackgroundColor(
                    ContextCompat.getColor(itemView.getContext(), bgColorRes)
                );
            }
        }
    }
} 