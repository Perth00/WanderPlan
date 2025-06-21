package com.example.mobiledegreefinalproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobiledegreefinalproject.R;
import com.example.mobiledegreefinalproject.database.TripActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TripTimelineAdapter extends RecyclerView.Adapter<TripTimelineAdapter.DayViewHolder> {

    public interface OnActivityClickListener {
        void onActivityClick(TripActivity activity);

        void onActivityLongClick(TripActivity activity);
    }

    private final OnActivityClickListener clickListener;
    private List<DayData> daysList = new ArrayList<>();

    public TripTimelineAdapter(OnActivityClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submitData(Map<Integer, List<TripActivity>> groupedActivities) {
        daysList.clear();
        for (Map.Entry<Integer, List<TripActivity>> entry : groupedActivities.entrySet()) {
            daysList.add(new DayData(entry.getKey(), entry.getValue()));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DayData dayData = daysList.get(position);
        holder.bind(dayData, clickListener, position == daysList.size() - 1);
    }

    @Override
    public int getItemCount() {
        return daysList.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        private final TextView dayNumber;
        private final View timelineDot;
        private final View timelineLine;
        private final RecyclerView activitiesRecycler;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayNumber = itemView.findViewById(R.id.day_number);
            timelineDot = itemView.findViewById(R.id.timeline_dot);
            timelineLine = itemView.findViewById(R.id.timeline_line);
            activitiesRecycler = itemView.findViewById(R.id.activities_recycler);
        }

        public void bind(DayData dayData, OnActivityClickListener clickListener, boolean isLastDay) {
            dayNumber.setText("Day " + dayData.dayNumber);

            // Hide timeline line for last day
            timelineLine.setVisibility(isLastDay ? View.INVISIBLE : View.VISIBLE);

            // Setup activities recycler
            ActivitiesAdapter activitiesAdapter = new ActivitiesAdapter(clickListener);
            activitiesRecycler.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            activitiesRecycler.setAdapter(activitiesAdapter);
            activitiesRecycler.setNestedScrollingEnabled(false);

            activitiesAdapter.submitList(dayData.activities);
        }
    }

    // Inner adapter for activities within each day
    static class ActivitiesAdapter extends RecyclerView.Adapter<ActivitiesAdapter.ActivityViewHolder> {

        private final OnActivityClickListener clickListener;
        private List<TripActivity> activities = new ArrayList<>();

        public ActivitiesAdapter(OnActivityClickListener clickListener) {
            this.clickListener = clickListener;
        }

        public void submitList(List<TripActivity> activities) {
            this.activities = new ArrayList<>(activities);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_timeline_activity, parent, false);
            return new ActivityViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
            TripActivity activity = activities.get(position);
            holder.bind(activity, clickListener);
        }

        @Override
        public int getItemCount() {
            return activities.size();
        }

        static class ActivityViewHolder extends RecyclerView.ViewHolder {
            private final TextView timeText;
            private final TextView titleText;
            private final TextView descriptionText;
            private final ImageView activityImage;

            public ActivityViewHolder(@NonNull View itemView) {
                super(itemView);
                timeText = itemView.findViewById(R.id.activity_time);
                titleText = itemView.findViewById(R.id.activity_title);
                descriptionText = itemView.findViewById(R.id.activity_description);
                activityImage = itemView.findViewById(R.id.activity_image);
            }

            public void bind(TripActivity activity, OnActivityClickListener clickListener) {
                timeText.setText(activity.getTimeString());
                titleText.setText(activity.getTitle());

                // Show description or location
                String description = activity.getDescription();
                if (description == null || description.trim().isEmpty()) {
                    description = activity.getLocation();
                }

                if (description != null && !description.trim().isEmpty()) {
                    descriptionText.setText(description);
                    descriptionText.setVisibility(View.VISIBLE);
                } else {
                    descriptionText.setVisibility(View.GONE);
                }

                // Load activity image if available
                if (activity.hasImage()) {
                    activityImage.setVisibility(View.VISIBLE);
                    String imagePath = activity.getDisplayImagePath();

                    Glide.with(itemView.getContext())
                            .load(imagePath)
                            .placeholder(R.drawable.ic_trips)
                            .error(R.drawable.ic_trips)
                            .centerCrop()
                            .into(activityImage);
                } else {
                    activityImage.setVisibility(View.GONE);
                }

                // Set click listener for editing
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onActivityClick(activity);
                    }
                });

                // Set long click listener for deleting
                itemView.setOnLongClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onActivityLongClick(activity);
                    }
                    return true;
                });
            }
        }
    }

    // Data class for grouping
    private static class DayData {
        final int dayNumber;
        final List<TripActivity> activities;

        DayData(int dayNumber, List<TripActivity> activities) {
            this.dayNumber = dayNumber;
            this.activities = activities;
        }
    }
}