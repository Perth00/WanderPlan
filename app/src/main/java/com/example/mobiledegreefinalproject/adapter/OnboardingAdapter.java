package com.example.mobiledegreefinalproject.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final Context context;
    private final String[] titles;
    private final String[] descriptions;
    private final int[] images;

    public OnboardingAdapter(Context context) {
        this.context = context;
        this.titles = new String[]{
                context.getString(R.string.onboarding_title_1),
                context.getString(R.string.onboarding_title_2),
                context.getString(R.string.onboarding_title_3)
        };
        this.descriptions = new String[]{
                context.getString(R.string.onboarding_desc_1),
                context.getString(R.string.onboarding_desc_2),
                context.getString(R.string.onboarding_desc_3)
        };
        this.images = new int[]{
                R.drawable.ic_trips,
                R.drawable.ic_home,
                R.drawable.ic_budget
        };
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.title.setText(titles[position]);
        holder.description.setText(descriptions[position]);
        holder.image.setImageResource(images[position]);
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView description;

        public OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_onboarding);
            title = itemView.findViewById(R.id.tv_title);
            description = itemView.findViewById(R.id.tv_description);
        }
    }
} 